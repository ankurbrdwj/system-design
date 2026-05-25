package com.ankur.design.hft.tuning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * TOPIC: API Design — rigid allocation-heavy API vs caller-flexible zero-allocation API.
 *
 * A well-designed API in HFT systems has these properties:
 *   1. ZERO allocation on the hot path — no List<>, no arrays, no boxing
 *   2. Caller controls iteration — push vs pull model
 *   3. Lifecycle management is internal — caller cannot forget to close/cleanup
 *   4. Filter-early — avoid materializing data the caller will discard
 *
 * Poor API design forces allocation even when the caller doesn't need a collection.
 * Example: findTrades() returning List<Trade> — always allocates a List, even if
 * the caller only needs to check one field and discard the rest.
 *
 * The forEach(Predicate, Consumer) pattern:
 *   - Repository iterates its internal data structure
 *   - Predicate filters BEFORE adding to any collection
 *   - Consumer receives one matching item at a time — no collection needed
 *   - Zero allocation on the repository side
 */
public class ApiDesignDemo {

    // -------------------------------------------------------------------------
    // Domain object
    // -------------------------------------------------------------------------

    static class Trade {
        final String symbol;
        final double price;
        final int quantity;
        final String side; // "BUY" or "SELL"
        final long timestampNs;

        Trade(String symbol, double price, int quantity, String side, long timestampNs) {
            this.symbol = symbol;
            this.price = price;
            this.quantity = quantity;
            this.side = side;
            this.timestampNs = timestampNs;
        }

        @Override
        public String toString() {
            return String.format("Trade{%s %s %d@%.2f}", side, symbol, quantity, price);
        }
    }

    // -------------------------------------------------------------------------
    // BAD: API that forces allocation
    // -------------------------------------------------------------------------

    // BAD: findTrades() returns List<Trade>.
    // Problems:
    //   1. Allocates a new ArrayList every call — GC pressure on hot path
    //   2. Must fully materialize ALL matching trades into the list before returning
    //   3. Caller may only need the first match, but we find all of them
    //   4. Caller may filter further after receiving the list — double work
    //   5. List can be large — heap pressure, long GC pauses
    static class BadTradeRepository {
        private final Trade[] internalStorage;

        BadTradeRepository(Trade[] trades) {
            this.internalStorage = trades;
        }

        // BAD: allocates List<Trade> every call, even if caller only needs 1 trade
        public List<Trade> findTrades(String symbol) {
            List<Trade> result = new ArrayList<>();  // BAD: allocation on every call
            for (Trade trade : internalStorage) {
                if (trade.symbol.equals(symbol)) {
                    result.add(trade);  // BAD: copies reference into new collection
                }
            }
            return result;  // BAD: forces full materialization before any processing
        }

        // BAD: caller forced to chain allocations to get a filtered view
        public List<Trade> findBuys(String symbol) {
            List<Trade> bySymbol = findTrades(symbol);   // allocation 1
            List<Trade> buys = new ArrayList<>();         // allocation 2
            for (Trade t : bySymbol) {
                if ("BUY".equals(t.side)) {
                    buys.add(t);
                }
            }
            return buys;
        }
    }

    // -------------------------------------------------------------------------
    // GOOD: Zero-allocation push API
    // -------------------------------------------------------------------------

    // GOOD: forEach(filter, action) — no collection created, no allocation.
    //   - Repository iterates its own storage (it always would anyway)
    //   - Predicate decides whether to pass the item to the Consumer
    //   - Consumer receives each matching item one at a time
    //   - Total allocations for the repository: ZERO (lambda captures are typically stack-allocated or cached)
    //   - Caller can accumulate into their own pre-allocated buffer if needed
    static class GoodTradeRepository {
        private final Trade[] internalStorage;

        GoodTradeRepository(Trade[] trades) {
            this.internalStorage = trades;
        }

        // GOOD: zero allocation in the repository — caller provides filter + action
        public void forEach(Predicate<Trade> filter, Consumer<Trade> action) {
            for (int i = 0; i < internalStorage.length; i++) {
                Trade trade = internalStorage[i];
                if (filter.test(trade)) {
                    action.accept(trade);  // GOOD: no collection, no copy, no allocation
                }
            }
        }

        // GOOD: count-only operation — caller never needs a list at all
        public int count(Predicate<Trade> filter) {
            int count = 0;
            for (int i = 0; i < internalStorage.length; i++) {
                if (filter.test(internalStorage[i])) count++;
            }
            return count;
        }
    }

    // -------------------------------------------------------------------------
    // BAD: Lifecycle API that forces a protocol on the caller
    // -------------------------------------------------------------------------

    // BAD: caller must call open(), then process(), then close() in the right order.
    // If caller forgets close() → resource leak.
    // If caller calls process() before open() → undefined behaviour.
    // API cannot enforce correct usage — it depends on documentation + discipline.
    static class BadDataFeed {
        private boolean opened = false;
        private String[] data = {"tick1", "tick2", "tick3"};

        // BAD: forces caller to manage lifecycle
        public void open() {
            System.out.println("  [BadDataFeed] open()  — caller must remember to call this first");
            opened = true;
        }

        public String[] process() {
            if (!opened) throw new IllegalStateException("Must call open() first!");
            System.out.println("  [BadDataFeed] process() — reading data");
            return data;
        }

        public void close() {
            System.out.println("  [BadDataFeed] close() — caller must remember this or: resource leak");
            opened = false;
        }
    }

    // GOOD: lifecycle is managed internally — caller just provides a callback.
    // The resource is always properly opened and closed, regardless of exceptions.
    // Caller cannot forget to close — they don't control the lifecycle.
    static class GoodDataFeed {
        private String[] data = {"tick1", "tick2", "tick3"};

        // GOOD: callback-based API — lifecycle managed internally
        public void process(Consumer<String> onTick) {
            System.out.println("  [GoodDataFeed] opening internally...");
            try {
                System.out.println("  [GoodDataFeed] processing ticks...");
                for (String tick : data) {
                    onTick.accept(tick);  // caller gets each item — no resource management needed
                }
            } finally {
                System.out.println("  [GoodDataFeed] closing internally — always happens, even on exception");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Demo
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        Trade[] trades = {
            new Trade("AAPL", 150.25, 100, "BUY",  1_000_000L),
            new Trade("TSLA", 220.00, 50,  "SELL", 1_000_001L),
            new Trade("AAPL", 150.30, 200, "SELL", 1_000_002L),
            new Trade("AAPL", 150.20, 75,  "BUY",  1_000_003L),
            new Trade("MSFT", 380.00, 30,  "BUY",  1_000_004L),
            new Trade("AAPL", 150.35, 150, "BUY",  1_000_005L),
        };

        System.out.println("=== ApiDesignDemo ===");
        System.out.println();

        // ---- Example 1: Trade repository ----
        System.out.println("--- Example 1: TradeRepository API ---");
        System.out.println();

        BadTradeRepository badRepo = new BadTradeRepository(trades);
        GoodTradeRepository goodRepo = new GoodTradeRepository(trades);

        // BAD: returns full list
        System.out.println("BAD: findTrades(\"AAPL\") returns List<Trade>:");
        List<Trade> aaplTrades = badRepo.findTrades("AAPL");
        aaplTrades.forEach(t -> System.out.println("  " + t));
        System.out.println("  Allocated: new ArrayList + " + aaplTrades.size() + " references copied");

        System.out.println();

        // GOOD: forEach with filter
        System.out.println("GOOD: forEach(filter, action) — zero allocation in repository:");
        goodRepo.forEach(
            t -> "AAPL".equals(t.symbol),      // filter: only AAPL
            t -> System.out.println("  " + t)  // action: print each match
        );

        System.out.println();

        // GOOD: count without materializing a list
        int buyCount = goodRepo.count(t -> "AAPL".equals(t.symbol) && "BUY".equals(t.side));
        System.out.println("GOOD: count(AAPL BUY trades) = " + buyCount + " — no list allocated");

        System.out.println();

        // GOOD: accumulate into pre-allocated buffer if collection needed
        System.out.println("GOOD: accumulate into pre-allocated buffer (caller controls allocation):");
        List<Trade> preallocated = new ArrayList<>(trades.length); // caller allocates ONCE
        goodRepo.forEach(
            t -> "AAPL".equals(t.symbol),
            preallocated::add
        );
        System.out.println("  Found " + preallocated.size() + " AAPL trades in caller's pre-allocated list");

        System.out.println();
        System.out.println("  BAD  repo API: allocates List<Trade> on EVERY call");
        System.out.println("  GOOD repo API: ZERO allocations — caller decides what to do with each match");
        System.out.println();

        // ---- Example 2: Lifecycle API ----
        System.out.println("--- Example 2: Lifecycle Management ---");
        System.out.println();

        System.out.println("BAD: caller must manage open/process/close protocol:");
        BadDataFeed badFeed = new BadDataFeed();
        badFeed.open();  // BAD: easy to forget
        String[] ticks = badFeed.process();
        System.out.println("  Received " + ticks.length + " ticks");
        badFeed.close(); // BAD: easy to forget — resource leak if exception between open/process

        System.out.println();
        System.out.println("GOOD: callback API — lifecycle always correct:");
        GoodDataFeed goodFeed = new GoodDataFeed();
        goodFeed.process(tick -> System.out.println("  Received tick: " + tick));
        // GOOD: no open/close for caller — impossible to leak or misuse

        System.out.println();
        System.out.println("Key API design principles:");
        System.out.println("  - Prefer zero-allocation push APIs (forEach + Consumer)");
        System.out.println("  - Let callers control collection strategy (pre-allocate once, reuse)");
        System.out.println("  - Manage lifecycle internally — callers can't forget or misorder");
        System.out.println("  - Filter early (Predicate inside repository) to avoid materializing discards");
    }
}