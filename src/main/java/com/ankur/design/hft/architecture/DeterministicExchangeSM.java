package com.ankur.design.hft.architecture;

import java.util.*;

/**
 * Deterministic State Machine Model for a Modern Exchange
 *
 * Coinbase (coinbaseexchange.md):
 *   "Modern exchange systems benefit greatly from a deterministic state machine model,
 *    single-threaded market processing, and replicated state for fault tolerance."
 *
 * ---------------------------------------------------------------------------------
 * WHAT "DETERMINISTIC" MEANS HERE
 * ---------------------------------------------------------------------------------
 * Given the SAME sequence of input events, the exchange ALWAYS produces the
 * SAME output (fills, rejections, order states) — no matter when or where it runs.
 *
 * This property enables three critical capabilities:
 *
 *   1. REPLAY     — re-process the event log → identical final state
 *                   (debugging: reproduce any production incident exactly)
 *
 *   2. REPLICATION — run identical state machines on 3 nodes (Aeron Cluster / Raft)
 *                   → all three stay in sync → leader failover with zero data loss
 *
 *   3. TESTING    — feed deterministic inputs → assert deterministic outputs
 *                   (no flaky tests from race conditions or timing)
 *
 * ---------------------------------------------------------------------------------
 * ARCHITECTURE (single market e.g. BTC/USD)
 * ---------------------------------------------------------------------------------
 *
 *   Client A ──┐
 *   Client B ──┤──→  [ Event Log ]  ──→  [ Exchange State Machine ]  ──→  Fills
 *   Client C ──┘      (ordered,              (single-threaded,              Market data
 *                      durable)               no locks needed)
 *
 *   Event log is the source of truth. The state machine is just a pure function:
 *     f(currentState, event) → (newState, outputs)
 *
 *   Aeron Cluster ensures all replicas see events in the SAME ORDER → same state.
 *
 * ---------------------------------------------------------------------------------
 * SECTIONS
 * ---------------------------------------------------------------------------------
 *   1. Events        — every input is an immutable event (sealed hierarchy)
 *   2. Order         — mutable state with strict lifecycle transitions
 *   3. OrderBook     — bids/asks sorted by price-time priority; matching logic
 *   4. ExchangeSM    — the state machine: process(event) → deterministic output
 *   5. Snapshot      — capture state for fast recovery without full log replay
 *   6. Demo          — prove determinism by replaying the same log twice
 */
public class DeterministicExchangeSM {

    // =========================================================================
    // 1. EVENTS — immutable inputs to the state machine
    //
    // Every action a client can take is modelled as an immutable event.
    // The event log is append-only. The state machine never modifies events.
    //
    // This is the key design insight: SEPARATE the event (what happened)
    // from the state (current snapshot). The state can be rebuilt at any
    // time by replaying events from the beginning (or from a snapshot).
    // =========================================================================

    enum Side { BUY, SELL }

    sealed interface Event permits
            PlaceOrderEvent, CancelOrderEvent, MarketCloseEvent {}

    record PlaceOrderEvent(
            long   eventId,      // monotonically increasing — enforces ordering
            long   orderId,
            String clientId,
            Side   side,
            long   priceRaw,     // price × 100 fixed-point (no Double allocation)
            long   quantity
    ) implements Event {}

    record CancelOrderEvent(
            long eventId,
            long orderId,
            String clientId
    ) implements Event {}

    record MarketCloseEvent(long eventId) implements Event {}

    // =========================================================================
    // 2. ORDER — mutable state with strict lifecycle
    //
    //   NEW ──→ OPEN ──→ PARTIAL_FILL ──→ FILLED    (terminal)
    //                 └──→ CANCELLED                 (terminal)
    //                 └──→ REJECTED                  (terminal)
    //
    // State transitions are the ONLY way to change an order.
    // No setters — forces all changes through explicit transition methods.
    // =========================================================================

    enum OrderStatus { NEW, OPEN, PARTIAL_FILL, FILLED, CANCELLED, REJECTED }

    static final class Order {
        final long        orderId;
        final String      clientId;
        final Side        side;
        final long        priceRaw;
        final long        totalQty;
        final long        eventId;       // sequence number when placed
        long              remainingQty;
        OrderStatus       status;

        Order(long orderId, String clientId, Side side,
              long priceRaw, long qty, long eventId) {
            this.orderId      = orderId;
            this.clientId     = clientId;
            this.side         = side;
            this.priceRaw     = priceRaw;
            this.totalQty     = qty;
            this.remainingQty = qty;
            this.eventId      = eventId;
            this.status       = OrderStatus.NEW;
        }

        void open()    { status = OrderStatus.OPEN; }
        void cancel()  { status = OrderStatus.CANCELLED; }
        void reject()  { status = OrderStatus.REJECTED; }

        void fill(long qty) {
            remainingQty -= qty;
            status = remainingQty == 0 ? OrderStatus.FILLED : OrderStatus.PARTIAL_FILL;
        }

        boolean isActive() {
            return status == OrderStatus.OPEN || status == OrderStatus.PARTIAL_FILL;
        }

        double price() { return priceRaw / 100.0; }

        @Override public String toString() {
            return String.format("Order{id=%d client=%s side=%s px=%.2f qty=%d/%d %s}",
                    orderId, clientId, side, price(), totalQty - remainingQty, totalQty, status);
        }
    }

    // =========================================================================
    // 3. ORDER BOOK — bids and asks sorted by price-time priority
    //
    // Price-time priority (standard exchange rule):
    //   1. Best PRICE first  (highest bid wins; lowest ask wins)
    //   2. For same price: earliest order first (eventId as tiebreaker)
    //
    // Data structure:
    //   TreeMap<Long, Queue<Order>>
    //     key   = priceRaw
    //     value = FIFO queue of orders at that price level
    //
    //   Bids: TreeMap in DESCENDING order → firstKey() = best bid
    //   Asks: TreeMap in ASCENDING order  → firstKey() = best ask
    //
    // Matching algorithm:
    //   When a BUY arrives at priceRaw P:
    //     while best ask <= P and incoming qty > 0:
    //       match with oldest order at best ask level
    //       generate Fill for both sides
    //       advance to next ask level if exhausted
    //
    //   Symmetric for SELL orders.
    // =========================================================================

    record Fill(
            long makerOrderId,   // resting order (was in book)
            long takerOrderId,   // incoming order (caused the match)
            long priceRaw,       // execution price (maker's price — price-time priority)
            long quantity,
            Side takerSide
    ) {
        @Override public String toString() {
            return String.format("FILL  maker=%d  taker=%d  px=%.2f  qty=%d  takerSide=%s",
                    makerOrderId, takerOrderId, priceRaw / 100.0, quantity, takerSide);
        }
    }

    static final class OrderBook {
        // Bids: highest price first
        private final TreeMap<Long, ArrayDeque<Order>> bids =
                new TreeMap<>(Collections.reverseOrder());
        // Asks: lowest price first
        private final TreeMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();

        // All orders by ID for O(1) cancel lookup
        private final Map<Long, Order> allOrders = new HashMap<>();

        List<Fill> add(Order incoming) {
            allOrders.put(incoming.orderId, incoming);
            incoming.open();

            List<Fill> fills = new ArrayList<>();

            if (incoming.side == Side.BUY) {
                matchAgainstAsks(incoming, fills);
            } else {
                matchAgainstBids(incoming, fills);
            }

            // If not fully filled, rest in book
            if (incoming.isActive()) {
                restInBook(incoming);
            }

            return fills;
        }

        private void matchAgainstAsks(Order buy, List<Fill> fills) {
            while (buy.isActive() && !asks.isEmpty()) {
                long bestAskPrice = asks.firstKey();

                // Buy price must be >= ask price for a match
                if (buy.priceRaw < bestAskPrice) break;

                ArrayDeque<Order> level = asks.get(bestAskPrice);
                Order sell = level.peekFirst();

                long matchQty = Math.min(buy.remainingQty, sell.remainingQty);
                fills.add(new Fill(sell.orderId, buy.orderId, bestAskPrice, matchQty, Side.BUY));

                buy.fill(matchQty);
                sell.fill(matchQty);

                if (!sell.isActive()) {
                    level.pollFirst();
                    if (level.isEmpty()) asks.remove(bestAskPrice);
                }
            }
        }

        private void matchAgainstBids(Order sell, List<Fill> fills) {
            while (sell.isActive() && !bids.isEmpty()) {
                long bestBidPrice = bids.firstKey();

                // Sell price must be <= bid price for a match
                if (sell.priceRaw > bestBidPrice) break;

                ArrayDeque<Order> level = bids.get(bestBidPrice);
                Order buy = level.peekFirst();

                long matchQty = Math.min(sell.remainingQty, buy.remainingQty);
                fills.add(new Fill(buy.orderId, sell.orderId, bestBidPrice, matchQty, Side.SELL));

                sell.fill(matchQty);
                buy.fill(matchQty);

                if (!buy.isActive()) {
                    level.pollFirst();
                    if (level.isEmpty()) bids.remove(bestBidPrice);
                }
            }
        }

        private void restInBook(Order order) {
            var map = order.side == Side.BUY ? bids : asks;
            map.computeIfAbsent(order.priceRaw, k -> new ArrayDeque<>()).add(order);
        }

        boolean cancel(long orderId) {
            Order order = allOrders.get(orderId);
            if (order == null || !order.isActive()) return false;

            var map = order.side == Side.BUY ? bids : asks;
            ArrayDeque<Order> level = map.get(order.priceRaw);
            if (level != null) {
                level.remove(order);
                if (level.isEmpty()) map.remove(order.priceRaw);
            }
            order.cancel();
            return true;
        }

        double bestBid() { return bids.isEmpty() ? 0 : bids.firstKey() / 100.0; }
        double bestAsk() { return asks.isEmpty() ? 0 : asks.firstKey() / 100.0; }
        int    bidLevels() { return bids.size(); }
        int    askLevels() { return asks.size(); }
        Order  getOrder(long id) { return allOrders.get(id); }
    }

    // =========================================================================
    // 4. EXCHANGE STATE MACHINE
    //
    // The state machine is a pure function:
    //   process(event) → mutates internal state, returns outputs (fills, rejections)
    //
    // SINGLE-THREADED by design — no locks needed.
    // Coinbase: "each market is handled independently in a single-threaded manner
    //           to preserve order consistency."
    //
    // All events enter through a single ordered sequence (the event log).
    // The last processed eventId guards against duplicate/out-of-order replay.
    // =========================================================================

    static final class ExchangeStateMachine {
        private final String      symbol;
        private final OrderBook   book          = new OrderBook();
        private final List<Fill>  fillHistory   = new ArrayList<>();
        private final List<String> eventLog     = new ArrayList<>(); // human-readable audit
        private long              lastEventId   = -1;
        private boolean           marketOpen    = true;

        ExchangeStateMachine(String symbol) { this.symbol = symbol; }

        List<Fill> process(Event event) {
            // DETERMINISM GUARD: reject out-of-order or duplicate events
            long eventId = switch (event) {
                case PlaceOrderEvent  e -> e.eventId();
                case CancelOrderEvent e -> e.eventId();
                case MarketCloseEvent e -> e.eventId();
            };

            if (eventId <= lastEventId) {
                log("SKIP   eventId=" + eventId + " already processed (lastEventId=" + lastEventId + ")");
                return List.of();
            }
            lastEventId = eventId;

            return switch (event) {
                case PlaceOrderEvent  e -> handlePlace(e);
                case CancelOrderEvent e -> handleCancel(e);
                case MarketCloseEvent e -> handleMarketClose(e);
            };
        }

        private List<Fill> handlePlace(PlaceOrderEvent e) {
            if (!marketOpen) {
                log("REJECT orderId=" + e.orderId() + " market closed");
                return List.of();
            }

            Order order = new Order(e.orderId(), e.clientId(),
                    e.side(), e.priceRaw(), e.quantity(), e.eventId());

            List<Fill> fills = book.add(order);
            fillHistory.addAll(fills);

            String matchInfo = fills.isEmpty() ? "resting in book"
                    : fills.size() + " fill(s)";
            log(String.format("PLACE  %s px=%.2f qty=%d  → %s",
                    order, e.priceRaw() / 100.0, e.quantity(), matchInfo));
            fills.forEach(f -> log("  " + f));

            return fills;
        }

        private List<Fill> handleCancel(CancelOrderEvent e) {
            boolean cancelled = book.cancel(e.orderId());
            log("CANCEL orderId=" + e.orderId() + " → " + (cancelled ? "OK" : "NOT FOUND / already terminal"));
            return List.of();
        }

        private List<Fill> handleMarketClose(MarketCloseEvent e) {
            marketOpen = false;
            log("MARKET CLOSE — no further orders accepted");
            return List.of();
        }

        private void log(String msg) { eventLog.add(msg); }

        // ----- Snapshot for fault-tolerant recovery -----
        // In production (Aeron Cluster): snapshot is written to durable storage.
        // On restart: load snapshot → replay only events AFTER snapshot.sequenceId
        // instead of replaying from event 0. Drastically reduces recovery time.
        Snapshot takeSnapshot() {
            return new Snapshot(lastEventId,
                    book.bestBid(), book.bestAsk(),
                    book.bidLevels(), book.askLevels(),
                    fillHistory.size());
        }

        record Snapshot(long lastEventId, double bestBid, double bestAsk,
                        int bidLevels, int askLevels, int totalFills) {
            @Override public String toString() {
                return String.format(
                        "Snapshot{lastEventId=%d bestBid=%.2f bestAsk=%.2f "
                        + "bidLevels=%d askLevels=%d totalFills=%d}",
                        lastEventId, bestBid, bestAsk, bidLevels, askLevels, totalFills);
            }
        }

        void printEventLog() { eventLog.forEach(l -> System.out.println("  " + l)); }
        OrderBook book()      { return book; }
        int       totalFills(){ return fillHistory.size(); }
    }

    // =========================================================================
    // 5. HELPERS — build a reusable event log for the demo
    // =========================================================================

    static List<Event> buildEventLog() {
        //  Scenario: BTC/USD market
        //  Client A places a BUY  at $50,100 for 2 BTC
        //  Client B places a BUY  at $50,050 for 1 BTC
        //  Client C places a SELL at $50,200 for 1 BTC  (no match — ask > best bid)
        //  Client D places a SELL at $50,100 for 3 BTC  (partial match with A, rests remainder)
        //  Client A cancels remaining on their order
        //  Client E places a BUY  at $50,200 for 1 BTC  (matches the resting SELL from D)

        return List.of(
                new PlaceOrderEvent (1, 101, "ClientA", Side.BUY,  5_010_000L, 2), // $50,100.00
                new PlaceOrderEvent (2, 102, "ClientB", Side.BUY,  5_005_000L, 1), // $50,050.00
                new PlaceOrderEvent (3, 103, "ClientC", Side.SELL, 5_020_000L, 1), // $50,200.00
                new PlaceOrderEvent (4, 104, "ClientD", Side.SELL, 5_010_000L, 3), // $50,100.00
                new CancelOrderEvent(5, 101, "ClientA"),                            // cancel A's remainder
                new PlaceOrderEvent (6, 105, "ClientE", Side.BUY,  5_020_000L, 1)  // $50,200.00
        );
    }

    // =========================================================================
    // MAIN — prove determinism by running the SAME event log twice
    //        and asserting identical final state
    // =========================================================================

    public static void main(String[] args) {
        List<Event> log = buildEventLog();

        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println("  Deterministic Exchange State Machine — BTC/USD");
        System.out.println("  Coinbase: 'deterministic state machine model,");
        System.out.println("            single-threaded market processing'");
        System.out.println("════════════════════════════════════════════════════════════");

        // ---- RUN 1: first execution ----
        System.out.println("\n--- RUN 1: Processing event log ---\n");
        ExchangeStateMachine sm1 = new ExchangeStateMachine("BTC/USD");
        for (Event e : log) sm1.process(e);
        sm1.printEventLog();

        ExchangeStateMachine.Snapshot snap1 = sm1.takeSnapshot();
        System.out.println("\n  Snapshot after RUN 1:");
        System.out.println("  " + snap1);

        // ---- RUN 2: replay the IDENTICAL event log ----
        System.out.println("\n--- RUN 2: Replaying the SAME event log (proving determinism) ---\n");
        ExchangeStateMachine sm2 = new ExchangeStateMachine("BTC/USD");
        for (Event e : log) sm2.process(e);
        sm2.printEventLog();

        ExchangeStateMachine.Snapshot snap2 = sm2.takeSnapshot();
        System.out.println("\n  Snapshot after RUN 2:");
        System.out.println("  " + snap2);

        // ---- Determinism assertion ----
        System.out.println("\n════════════════════════════════════════════════════════════");
        System.out.println("  Determinism check:");
        boolean same =  snap1.lastEventId() == snap2.lastEventId()
                     && snap1.bestBid()     == snap2.bestBid()
                     && snap1.bestAsk()     == snap2.bestAsk()
                     && snap1.bidLevels()   == snap2.bidLevels()
                     && snap1.askLevels()   == snap2.askLevels()
                     && snap1.totalFills()  == snap2.totalFills();

        System.out.println("  RUN1 == RUN2: " + same + " ← must be true");
        System.out.println();

        // ---- Demonstrate duplicate/out-of-order protection ----
        System.out.println("--- RUN 3: Sending duplicate events (must be silently skipped) ---\n");
        ExchangeStateMachine sm3 = new ExchangeStateMachine("BTC/USD");
        for (Event e : log) sm3.process(e);   // first pass
        int fillsAfterFirst = sm3.totalFills();

        // Replay the same events again — state machine must ignore them
        for (Event e : log) sm3.process(e);   // duplicate pass
        int fillsAfterDuplicate = sm3.totalFills();

        System.out.println("  Fills after first pass   : " + fillsAfterFirst);
        System.out.println("  Fills after duplicate pass: " + fillsAfterDuplicate);
        System.out.println("  Duplicates ignored: " + (fillsAfterFirst == fillsAfterDuplicate));

        // ---- Order status summary ----
        System.out.println("\n--- Final Order Status ---\n");
        long[] orderIds = {101, 102, 103, 104, 105};
        for (long id : orderIds) {
            Order o = sm1.book().getOrder(id);
            if (o != null) System.out.println("  " + o);
        }

        System.out.println("\n════════════════════════════════════════════════════════════");
        System.out.println("  Why determinism matters:");
        System.out.println("  1. REPLAY    — reproduce any bug by replaying its event log");
        System.out.println("  2. REPLICATE — 3 nodes process same log → same state → failover");
        System.out.println("  3. TEST      — no flaky tests; same input always → same output");
        System.out.println("  4. AUDIT     — append-only log is the legal trade record");
        System.out.println("════════════════════════════════════════════════════════════");
    }
}