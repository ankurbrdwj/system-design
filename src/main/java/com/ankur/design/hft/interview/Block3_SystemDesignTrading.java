package com.ankur.design.hft.interview;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Block 3: System Design for Trading
 *
 * Interview questions answered here:
 *   Q1. "Walk me through the trade lifecycle from order submission to settlement."
 *   Q2. "How would you design a fault-tolerant order routing system?"
 *   Q3. "How do you ensure exactly-once order submission to an exchange?"
 *   Q4. "How would you handle 100,000 market data updates per second in Java?"
 *
 * Sections:
 *   1. ORDER STATE MACHINE  — enum + valid transition table; prevents illegal states
 *   2. OMS PRE-TRADE RISK   — position limit, notional limit, rate check before routing
 *   3. CIRCUIT BREAKER      — CLOSED → OPEN → HALF_OPEN; Resilience4j pattern
 *   4. BULKHEAD PATTERN     — separate thread pools per venue; blast radius isolation
 */
public class Block3_SystemDesignTrading {

    // =========================================================================
    // 1. ORDER STATE MACHINE
    //
    // Q: "Walk me through the trade lifecycle from order submission to settlement."
    //
    // Every order follows a deterministic state machine. The exchange sends back
    // execution reports (FIX 4.2 ExecType field) that drive state transitions.
    // Using an enum with allowed-transition enforcement prevents the system from
    // entering an illegal state (e.g., cannot fill an already-cancelled order).
    //
    // State flow:
    //
    //   NEW ──────────────────────────────── (submitted by client)
    //    │
    //    ▼
    //   PENDING_ACK ─── REJECTED            (exchange refused: bad symbol, etc.)
    //    │
    //    ▼
    //   ACKNOWLEDGED ── PENDING_CANCEL ─── CANCELLED
    //    │
    //    ▼
    //   PARTIAL_FILL ── PENDING_CANCEL ─── CANCELLED
    //    │
    //    ▼
    //   FILLED                               (terminal — all qty executed)
    //
    // In FIX protocol: ExecType=0 (NEW), 1 (PARTIAL FILL), 2 (FILL), 4 (CANCELLED),
    //                  8 (REJECTED), F (TRADE, for fills)
    // =========================================================================

    enum OrderStatus {
        NEW, PENDING_ACK, ACKNOWLEDGED, PARTIAL_FILL, FILLED, PENDING_CANCEL, CANCELLED, REJECTED;

        boolean canTransitionTo(OrderStatus next) {
            return switch (this) {
                case NEW            -> next == PENDING_ACK;
                case PENDING_ACK    -> next == ACKNOWLEDGED || next == REJECTED;
                case ACKNOWLEDGED   -> next == PARTIAL_FILL || next == PENDING_CANCEL || next == FILLED;
                case PARTIAL_FILL   -> next == PARTIAL_FILL  || next == FILLED || next == PENDING_CANCEL;
                case PENDING_CANCEL -> next == CANCELLED || next == FILLED;    // fill can race the cancel
                case FILLED, CANCELLED, REJECTED -> false;                     // terminal states
            };
        }
    }

    static final class Order {
        final long        orderId;
        final String      symbol;
        final double      price;
        final long        totalQty;
        volatile long     filledQty;
        volatile OrderStatus status;

        Order(long id, String sym, double px, long qty) {
            this.orderId  = id;
            this.symbol   = sym;
            this.price    = px;
            this.totalQty = qty;
            this.status   = OrderStatus.NEW;
        }

        synchronized boolean transition(OrderStatus next) {
            if (!status.canTransitionTo(next)) {
                System.out.printf("  [StateMachine] ILLEGAL %s → %s for order %d%n",
                        status, next, orderId);
                return false;
            }
            System.out.printf("  [StateMachine] Order %d: %s → %s%n", orderId, status, next);
            status = next;
            return true;
        }

        synchronized void applyFill(long fillQty) {
            filledQty += fillQty;
            if (filledQty >= totalQty) transition(OrderStatus.FILLED);
            else                       transition(OrderStatus.PARTIAL_FILL);
        }
    }

    // =========================================================================
    // 2. OMS PRE-TRADE RISK CHECKS
    //
    // Q: "How would you design a fault-tolerant order routing system?"
    // (Risk angle — your Market Risk background is a differentiator here)
    //
    // Every order MUST pass pre-trade risk checks before leaving the OMS:
    //
    //   1. Position limit  — max net position per instrument (e.g. ±10,000 shares)
    //   2. Notional limit  — max value per single order (e.g. $500,000)
    //   3. Rate limit      — max orders per second (exchange throttle rules)
    //   4. Fat-finger      — price not more than X% from last known price
    //
    // In the real world (Market Risk at DB): these are the same checks the risk
    // system validates post-trade — catch them pre-trade and you avoid breach.
    //
    // Exactly-once submission:
    //   Assign a client-order-id (ClOrdID in FIX) that is unique and persistent.
    //   On network timeout, query the exchange by ClOrdID before re-submitting.
    //   Never assume a timeout means rejection — the order may have been received.
    // =========================================================================

    static final class PreTradeRiskEngine {
        private final ConcurrentHashMap<String, AtomicLong> positions = new ConcurrentHashMap<>();
        private final long maxPositionPerSymbol;
        private final long maxNotionalPerOrder;
        private final AtomicLong ordersThisSecond = new AtomicLong();
        private volatile long currentSecond = System.currentTimeMillis() / 1000;
        private final int maxOrdersPerSecond;

        PreTradeRiskEngine(long maxPosition, long maxNotional, int maxOps) {
            this.maxPositionPerSymbol = maxPosition;
            this.maxNotionalPerOrder  = maxNotional;
            this.maxOrdersPerSecond   = maxOps;
        }

        enum RejectionReason { APPROVED, POSITION_LIMIT, NOTIONAL_LIMIT, RATE_LIMIT, FAT_FINGER }

        RejectionReason check(String symbol, double price, long qty, double lastKnownPrice) {
            // 1. Rate limit
            long nowSec = System.currentTimeMillis() / 1000;
            if (nowSec != currentSecond) {
                currentSecond = nowSec;
                ordersThisSecond.set(0);
            }
            if (ordersThisSecond.incrementAndGet() > maxOrdersPerSecond)
                return RejectionReason.RATE_LIMIT;

            // 2. Notional limit
            long notional = (long)(price * qty);
            if (notional > maxNotionalPerOrder)
                return RejectionReason.NOTIONAL_LIMIT;

            // 3. Fat-finger: price > 5% from last known
            if (lastKnownPrice > 0 && Math.abs(price - lastKnownPrice) / lastKnownPrice > 0.05)
                return RejectionReason.FAT_FINGER;

            // 4. Position limit
            AtomicLong pos = positions.computeIfAbsent(symbol, k -> new AtomicLong(0));
            long newPos = pos.get() + qty;
            if (Math.abs(newPos) > maxPositionPerSymbol)
                return RejectionReason.POSITION_LIMIT;

            pos.addAndGet(qty);  // tentatively update; roll back on exchange reject
            return RejectionReason.APPROVED;
        }

        long getPosition(String symbol) {
            AtomicLong pos = positions.get(symbol);
            return pos == null ? 0 : pos.get();
        }
    }

    // =========================================================================
    // 3. CIRCUIT BREAKER
    //
    // Q: "How would you design a fault-tolerant order routing system?"
    //
    // Problem: if Exchange A is down, a naive system keeps hammering it with
    //          retries → thread pool exhaustion → cascading failure.
    //
    // Circuit breaker states:
    //
    //   CLOSED  — normal operation; failures counted
    //              → opens if failures >= THRESHOLD within a time window
    //
    //   OPEN    — all calls fail immediately (no network attempt)
    //              → after TIMEOUT_MS, transitions to HALF_OPEN to probe
    //
    //   HALF_OPEN — let ONE request through to test if downstream recovered
    //              → success: back to CLOSED
    //              → failure: back to OPEN
    //
    // Production: use Resilience4j (CircuitBreaker.of(...)) — mention the library.
    //             This implementation is for interview illustration.
    // =========================================================================

    static final class CircuitBreaker {
        enum State { CLOSED, OPEN, HALF_OPEN }

        private volatile State       state          = State.CLOSED;
        private final    AtomicLong  failures       = new AtomicLong(0);
        private volatile long        openedAtMs     = 0;
        private final    int         failureThreshold;
        private final    long        resetTimeoutMs;

        CircuitBreaker(int failureThreshold, long resetTimeoutMs) {
            this.failureThreshold = failureThreshold;
            this.resetTimeoutMs   = resetTimeoutMs;
        }

        /** Returns true if the call should be allowed through. */
        boolean allowRequest() {
            if (state == State.CLOSED) return true;

            if (state == State.OPEN) {
                if (System.currentTimeMillis() - openedAtMs > resetTimeoutMs) {
                    state = State.HALF_OPEN;
                    System.out.println("  [CircuitBreaker] OPEN → HALF_OPEN (probing recovery)");
                    return true;   // let one request through
                }
                return false;      // still open — fail fast
            }

            return state == State.HALF_OPEN;  // let the probe through
        }

        void recordSuccess() {
            if (state == State.HALF_OPEN) {
                failures.set(0);
                state = State.CLOSED;
                System.out.println("  [CircuitBreaker] HALF_OPEN → CLOSED (recovered)");
            }
        }

        void recordFailure() {
            long f = failures.incrementAndGet();
            if (state == State.HALF_OPEN) {
                state      = State.OPEN;
                openedAtMs = System.currentTimeMillis();
                System.out.println("  [CircuitBreaker] HALF_OPEN → OPEN (still failing)");
            } else if (f >= failureThreshold && state == State.CLOSED) {
                state      = State.OPEN;
                openedAtMs = System.currentTimeMillis();
                System.out.println("  [CircuitBreaker] CLOSED → OPEN (failures=" + f + ")");
            }
        }

        State getState() { return state; }
    }

    // =========================================================================
    // 4. BULKHEAD PATTERN
    //
    // Q: "How would you design a fault-tolerant order routing system?"
    //
    // Problem: if Exchange A is slow, its thread pool fills up → can't route
    //          to Exchange B, C either → total outage from one slow venue.
    //
    // Solution: give EACH exchange connector its OWN bounded thread pool.
    //           Exchange A being slow only exhausts A's pool, never B or C.
    //
    // In Resilience4j: Bulkhead.of("exchange-a", config) limits concurrent calls.
    // Here: separate ThreadPoolExecutor per venue with bounded queue + CallerRuns.
    //
    // Combined with circuit breaker: if A's CB opens, route to B or C immediately.
    // =========================================================================

    static final class VenueRouter {
        private final ConcurrentHashMap<String, ExecutorService> venuePools = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, CircuitBreaker>  venueBreakers = new ConcurrentHashMap<>();

        void registerVenue(String venue, int maxThreads, int queueDepth) {
            // Separate bounded pool per venue — the bulkhead
            venuePools.put(venue, new ThreadPoolExecutor(
                    maxThreads, maxThreads, 0L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(queueDepth),
                    new ThreadPoolExecutor.CallerRunsPolicy()  // back-pressure, no silent drops
            ));
            venueBreakers.put(venue, new CircuitBreaker(3, 500));
        }

        void routeOrder(String venue, Runnable orderTask) {
            CircuitBreaker cb = venueBreakers.get(venue);
            if (cb == null || !cb.allowRequest()) {
                System.out.println("  [Bulkhead] " + venue + " circuit OPEN — order rejected fast");
                return;
            }
            ExecutorService pool = venuePools.get(venue);
            if (pool == null) return;

            pool.submit(() -> {
                try {
                    orderTask.run();
                    cb.recordSuccess();
                } catch (Exception e) {
                    cb.recordFailure();
                    System.out.println("  [Bulkhead] " + venue + " task failed: " + e.getMessage());
                }
            });
        }

        void shutdown() {
            venuePools.values().forEach(ExecutorService::shutdown);
        }
    }

    // =========================================================================
    // MAIN
    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("=== Block 3: System Design for Trading ===");
        System.out.println();

        // 1. Order State Machine
        System.out.println("--- 1. Order State Machine ---");
        Order order = new Order(1001, "AAPL", 150.25, 500);
        order.transition(OrderStatus.PENDING_ACK);
        order.transition(OrderStatus.ACKNOWLEDGED);
        order.applyFill(200);                              // partial fill
        order.applyFill(300);                              // fully filled
        order.transition(OrderStatus.CANCELLED);           // illegal — already FILLED
        System.out.println();

        // 2. Pre-Trade Risk Checks
        System.out.println("--- 2. Pre-Trade Risk Checks (OMS) ---");
        PreTradeRiskEngine risk = new PreTradeRiskEngine(
                10_000,   // max ±10,000 shares per symbol
                500_000,  // max $500,000 notional per order
                100       // max 100 orders per second
        );

        String[][] orders = {
            {"AAPL", "150.0", "100",  "150.1"},   // OK
            {"AAPL", "150.0", "5000", "150.1"},   // OK — position now 5100
            {"AAPL", "150.0", "6000", "150.1"},   // POSITION LIMIT exceeded
            {"MSFT", "300.0", "2000", "300.0"},   // NOTIONAL $600k — exceeds $500k
            {"GOOG", "200.0", "10",   "100.0"},   // FAT FINGER (100% away from last)
        };
        for (String[] o : orders) {
            var result = risk.check(o[0], Double.parseDouble(o[1]),
                    Long.parseLong(o[2]), Double.parseDouble(o[3]));
            System.out.printf("  %s qty=%-5s px=%-6s → %s%n", o[0], o[2], o[1], result);
        }
        System.out.printf("  AAPL net position after checks: %,d%n", risk.getPosition("AAPL"));
        System.out.println();

        // 3. Circuit Breaker
        System.out.println("--- 3. Circuit Breaker (CLOSED → OPEN → HALF_OPEN → CLOSED) ---");
        CircuitBreaker cb = new CircuitBreaker(3, 200 /*ms*/);

        System.out.println("  Simulating 3 consecutive failures:");
        for (int i = 1; i <= 3; i++) {
            if (cb.allowRequest()) cb.recordFailure();
        }
        System.out.println("  Circuit is now: " + cb.getState());

        boolean allowed = cb.allowRequest();
        System.out.println("  Request allowed while OPEN: " + allowed);

        System.out.println("  Waiting 200ms for reset timeout...");
        Thread.sleep(210);

        System.out.println("  Simulating successful probe:");
        if (cb.allowRequest()) cb.recordSuccess();
        System.out.println("  Circuit is now: " + cb.getState());
        System.out.println();

        // 4. Bulkhead
        System.out.println("--- 4. Bulkhead — separate thread pool per exchange ---");
        VenueRouter router = new VenueRouter();
        router.registerVenue("LSE",    2, 10);
        router.registerVenue("NYSE",   2, 10);
        router.registerVenue("NASDAQ", 2, 10);

        // Simulate LSE failing — should NOT affect NYSE / NASDAQ
        for (int i = 1; i <= 5; i++) {
            final int n = i;
            // LSE: always fails
            router.routeOrder("LSE", () -> { throw new RuntimeException("LSE timeout"); });
            // NYSE and NASDAQ: work fine
            router.routeOrder("NYSE",   () -> System.out.println("  [Bulkhead] NYSE order " + n + " OK"));
            router.routeOrder("NASDAQ", () -> System.out.println("  [Bulkhead] NASDAQ order " + n + " OK"));
        }
        Thread.sleep(300);
        router.shutdown();
        System.out.println();

        System.out.println("=== System Design Summary ===");
        System.out.println("  Order lifecycle: NEW→PENDING_ACK→ACKNOWLEDGED→PARTIAL_FILL→FILLED");
        System.out.println("  Pre-trade risk : position limit + notional limit + fat-finger + rate");
        System.out.println("  Fault tolerance: circuit breaker (Resilience4j) + bulkhead (per-venue pool)");
        System.out.println("  Exactly-once   : unique ClOrdID; query by ClOrdID before retry on timeout");
        System.out.println("  100k msg/sec   : LMAX Disruptor (in-process) or Aeron (inter-service)");
        System.out.println("                   partition Kafka by instrument for ordered processing");
    }
}