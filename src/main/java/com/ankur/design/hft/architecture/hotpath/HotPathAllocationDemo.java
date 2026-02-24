package com.ankur.design.hft.architecture.hotpath;

/**
 * Keyword: Hot Path Allocation Avoidance
 *
 * Coinbase Exchange Architecture (coinbaseexchange.md):
 *   "Avoid allocating objects on the hot code path; reuse objects heavily."
 *   "Use primitive-friendly data structures (arrays, no boxing in Java)."
 *   "Strings encoded as fixed-size longs to avoid string allocation overhead."
 *
 * The "hot path" is the tight loop that runs on every market event — order
 * arrival, market tick, fill notification. At Coinbase's 300,000 req/sec,
 * even one new Object() per event creates millions of heap objects per second,
 * driving GC pauses that blow through microsecond latency SLAs.
 *
 * Five techniques demonstrated (each removes one allocation source):
 *
 *   1. FLYWEIGHT PATTERN     — one mutable object reused per call vs new per call
 *   2. THREAD-LOCAL REUSE   — per-thread pre-allocated instance, no synchronisation
 *   3. PRIMITIVE ARRAYS     — long[]/double[] vs ArrayList<Long> (zero boxing)
 *   4. SYMBOL AS LONG       — 8-char ticker packed into a single primitive long
 *   5. PRE-ALLOCATED OUTPUT — caller-supplied fill buffer; matching engine never
 *                             returns new Fill[] on the hot path
 *
 * Run with: -verbose:gc
 * Expect: zero GC events for techniques 2-5; GC events only during naive section.
 */
public class HotPathAllocationDemo {

    static final int BUY  = 1;
    static final int SELL = -1;

    // =========================================================================
    // 1. FLYWEIGHT PATTERN vs NAIVE new() PER EVENT
    //
    // Naive: every incoming market tick creates a new MarketTick object.
    //        At 300k events/sec → 300k objects/sec on the heap → GC pressure.
    //
    // Flyweight: a single MarketTick instance is reset() and reused each call.
    //            Zero allocation on the hot path; safe for single-threaded
    //            matching engines (one thread per market, as Coinbase does).
    // =========================================================================

    /**
     * Mutable value object — designed to be reset() and reused, not discarded.
     * Price is stored as a fixed-point long (price * 1_000_000) to avoid
     * Double boxing and floating-point allocation.
     */
    static final class MarketTick {
        long symbol;    // packed ASCII long — see technique 4
        long sequence;
        long priceRaw;  // price * 1_000_000 fixed-point; no Double object
        long quantity;
        int  side;      // BUY=1 / SELL=-1

        void reset(long sym, long seq, long px, long qty, int side) {
            this.symbol   = sym;
            this.sequence = seq;
            this.priceRaw = px;
            this.quantity = qty;
            this.side     = side;
        }

        double price() { return priceRaw / 1_000_000.0; }
    }

    // Single shared flyweight instance for single-threaded hot path
    private static final MarketTick FLYWEIGHT_TICK = new MarketTick();

    /** NAIVE: allocates a new MarketTick on every call — triggers GC over time. */
    static MarketTick naiveCreateTick(long sym, long seq, long px, long qty, int side) {
        MarketTick t = new MarketTick();   // ← allocation on hot path
        t.reset(sym, seq, px, qty, side);
        return t;
    }

    /** FLYWEIGHT: resets and returns the same instance — ZERO allocation. */
    static MarketTick flyweightTick(long sym, long seq, long px, long qty, int side) {
        FLYWEIGHT_TICK.reset(sym, seq, px, qty, side);
        return FLYWEIGHT_TICK;             // same reference every time
    }

    // =========================================================================
    // 2. THREAD-LOCAL REUSE
    //
    // Coinbase runs one thread per market (BTC/USD on thread-1, ETH/USD on
    // thread-2, etc.). ThreadLocal gives each thread its own pre-allocated
    // object — no lock, no contention, zero allocation after the first call.
    //
    // Use this when you cannot use a single shared flyweight (e.g., an inbound
    // gateway where multiple threads each handle one market feed).
    // =========================================================================

    private static final ThreadLocal<MarketTick> THREAD_LOCAL_TICK =
            ThreadLocal.withInitial(MarketTick::new);  // allocates once per thread

    static MarketTick threadLocalTick(long sym, long seq, long px, long qty, int side) {
        MarketTick t = THREAD_LOCAL_TICK.get();   // ← no allocation after first call
        t.reset(sym, seq, px, qty, side);
        return t;
    }

    // =========================================================================
    // 3. PRIMITIVE ARRAYS vs BOXED COLLECTIONS
    //
    // Storing N price levels in a top-of-book ladder:
    //
    //   ArrayList<Double>  → each Double is a heap object (16-byte header + 8 bytes)
    //                        → 24 bytes per price, GC-visible, pointer chasing
    //
    //   double[]           → contiguous 8 bytes per price, cache-friendly,
    //                        GC-invisible body (array header allocated once)
    //
    // Rule: never use Integer/Long/Double on a hot path — always int/long/double.
    //       Generics force boxing; use specialised primitive arrays instead.
    // =========================================================================

    /**
     * Top-of-book ladder backed by parallel primitive arrays.
     * No boxing, no object headers per entry, tight memory layout.
     * Lookup is O(1) by pre-computed slot index in production; linear scan here
     * for clarity.
     */
    static final class PrimitivePriceLadder {
        private final long[]   symbols;  // packed ASCII symbol per slot
        private final double[] bids;     // best bid price per slot
        private final double[] asks;     // best ask price per slot
        private final long[]   bidQtys;  // bid quantity per slot
        private final long[]   askQtys;  // ask quantity per slot
        private final int      capacity;
        private int            size;

        PrimitivePriceLadder(int capacity) {
            this.capacity = capacity;
            this.symbols  = new long[capacity];
            this.bids     = new double[capacity];
            this.asks     = new double[capacity];
            this.bidQtys  = new long[capacity];
            this.askQtys  = new long[capacity];
        }

        /** Update or insert a price level — zero allocation. */
        boolean update(long symbol, double bid, double ask, long bQty, long aQty) {
            for (int i = 0; i < size; i++) {
                if (symbols[i] == symbol) {           // primitive == comparison
                    bids[i] = bid;  asks[i] = ask;
                    bidQtys[i] = bQty;  askQtys[i] = aQty;
                    return true;
                }
            }
            if (size < capacity) {
                symbols[size] = symbol;
                bids[size] = bid;  asks[size] = ask;
                bidQtys[size] = bQty;  askQtys[size] = aQty;
                size++;
                return true;
            }
            return false;
        }

        double getBid(int i) { return bids[i]; }
        double getAsk(int i) { return asks[i]; }
        int    size()        { return size; }
    }

    // =========================================================================
    // 4. SYMBOL AS LONG  (String → packed ASCII long)
    //
    // Coinbase: "Strings encoded as fixed-size longs to avoid string allocation."
    //
    // "AAPL" → 4 ASCII bytes packed into a long → 0x000000004141504C
    // "BTCUSD" → 6 ASCII bytes                 → 0x0000425443555344
    //
    // Benefits on the hot path:
    //   • No String object, no char[] backing array — zero heap allocation
    //   • Map key comparison is == (primitive), not String.equals() with char loop
    //   • Fits in a CPU register; no pointer dereference to compare
    //   • Can be stored in long[] without boxing
    //
    // symbolToLong() is called ONCE at startup; the long constant lives in a
    // static final field and is used directly on the hot path.
    // =========================================================================

    static long symbolToLong(String s) {
        long result = 0;
        int  len    = Math.min(s.length(), 8);   // max 8 ASCII chars in a long
        for (int i = 0; i < len; i++) {
            result = (result << 8) | (s.charAt(i) & 0xFF);
        }
        return result;
    }

    static String longToSymbol(long encoded) {
        // Only called for display/logging — NOT on the hot path
        StringBuilder sb = new StringBuilder(8);
        for (int shift = 56; shift >= 0; shift -= 8) {
            byte b = (byte) ((encoded >> shift) & 0xFF);
            if (b != 0) sb.append((char) b);
        }
        return sb.toString();
    }

    // Pre-compute at class load — zero cost on the hot path
    static final long SYM_AAPL   = symbolToLong("AAPL");
    static final long SYM_BTCUSD = symbolToLong("BTCUSD");
    static final long SYM_ETHUSD = symbolToLong("ETHUSD");

    // =========================================================================
    // 5. PRE-ALLOCATED OUTPUT BUFFER  (caller-supplied fill buffer)
    //
    // Naive matching engine:  Fill[] match(Order incoming) { return new Fill[n]; }
    //   → allocates a new Fill[] and n Fill objects on every match — hot path!
    //
    // Zero-allocation matching engine: match(Order incoming, FillBuffer out)
    //   → caller allocates FillBuffer once at startup
    //   → matching engine writes fills into it, resets the count each call
    //   → ZERO allocation per match event, no matter how many fills are generated
    //
    // Same pattern used for: result sets, serialised message buffers, log records.
    // =========================================================================

    static final class Fill {
        long orderId;
        long quantity;
        long priceRaw;

        void set(long id, long qty, long px) {
            orderId  = id;
            quantity = qty;
            priceRaw = px;
        }
    }

    /** Pre-allocated ring of Fill objects; reset() reuses all of them. */
    static final class FillBuffer {
        private final Fill[] fills;
        private int count;

        FillBuffer(int maxFillsPerEvent) {
            fills = new Fill[maxFillsPerEvent];
            for (int i = 0; i < maxFillsPerEvent; i++) fills[i] = new Fill();
        }

        void reset()  { count = 0; }
        int  count()  { return count; }
        Fill get(int i) { return fills[i]; }

        boolean addFill(long orderId, long qty, long priceRaw) {
            if (count >= fills.length) return false;
            fills[count++].set(orderId, qty, priceRaw);
            return true;
        }
    }

    /**
     * Hot path matching engine call — zero allocation.
     * FillBuffer is passed in by the caller; no Fill[] returned.
     */
    static void matchOrder(long orderId, long qty, long priceRaw, FillBuffer out) {
        out.reset();
        // Simulate two partial fills (e.g., split across two resting orders)
        out.addFill(orderId,     qty / 2,       priceRaw);
        out.addFill(orderId + 1, qty - qty / 2, priceRaw);
    }

    // =========================================================================
    // BENCHMARK
    // =========================================================================

    public static void main(String[] args) throws Exception {
        final int WARMUP     = 100_000;
        final int ITERS      = 1_000_000;
        final long SYM       = SYM_AAPL;
        final long PRICE_RAW = 150_500_000L;  // 150.50 * 1_000_000 fixed-point

        System.out.println("=== Hot Path Allocation Avoidance Demo ===");
        System.out.println("Source: Coinbase Exchange Architecture (coinbaseexchange.md)");
        System.out.println();

        // --- warm up all code paths so JIT compiles before timing ---
        System.out.println("[warm-up] " + WARMUP + " iterations across all techniques...");
        for (int i = 0; i < WARMUP; i++) {
            naiveCreateTick(SYM, i, PRICE_RAW, 100, BUY);
            flyweightTick(SYM, i, PRICE_RAW, 100, BUY);
            threadLocalTick(SYM, i, PRICE_RAW, 100, BUY);
        }
        System.gc();
        Thread.sleep(200);
        System.out.println("[warm-up] Done.\n");

        // ---- 1a. NAIVE: new MarketTick() per event ----
        long chk1 = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) {
            MarketTick tick = naiveCreateTick(SYM, i, PRICE_RAW + i, 100, BUY);
            chk1 += tick.priceRaw;  // prevent dead-code elimination
        }
        long naiveNs = System.nanoTime() - t0;

        // ---- 1b. FLYWEIGHT: reset and reuse one instance ----
        long chk2 = 0;
        long t1 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) {
            MarketTick tick = flyweightTick(SYM, i, PRICE_RAW + i, 100, BUY);
            chk2 += tick.priceRaw;
        }
        long flyweightNs = System.nanoTime() - t1;

        // ---- 2. THREAD-LOCAL REUSE ----
        long chk3 = 0;
        long t2 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) {
            MarketTick tick = threadLocalTick(SYM, i, PRICE_RAW + i, 100, BUY);
            chk3 += tick.priceRaw;
        }
        long tlNs = System.nanoTime() - t2;

        System.out.println("--- Technique 1+2: Flyweight & ThreadLocal vs Naive ---");
        System.out.printf("  Naive    (new per call)   : %,8d ns total | %3d ns/call"
                + "  [allocates %,d objects]%n", naiveNs, naiveNs / ITERS, ITERS);
        System.out.printf("  Flyweight (reuse 1 object): %,8d ns total | %3d ns/call"
                + "  [ZERO alloc on hot path]%n", flyweightNs, flyweightNs / ITERS);
        System.out.printf("  ThreadLocal (per-thread)  : %,8d ns total | %3d ns/call"
                + "  [ZERO alloc after init]%n", tlNs, tlNs / ITERS);
        System.out.printf("  Flyweight speedup vs naive: %.2fx%n", (double) naiveNs / flyweightNs);
        System.out.printf("  Checksums (must match): %d | %d | %d%n%n", chk1, chk2, chk3);

        // ---- 3. PRIMITIVE ARRAYS ----
        int LEVELS = 200;
        PrimitivePriceLadder ladder = new PrimitivePriceLadder(LEVELS);
        for (int i = 0; i < LEVELS; i++) {
            // Pre-populate with synthetic symbols
            long sym = symbolToLong("S" + String.format("%03d", i));
            ladder.update(sym, 100.0 + i, 100.1 + i, 500, 500);
        }

        long chk4 = 0;
        long t3 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) {
            int idx = i % LEVELS;
            ladder.update(SYM_BTCUSD, 150.0 + idx * 0.01, 150.01 + idx * 0.01, 1000, 1000);
            chk4 += Double.doubleToRawLongBits(ladder.getBid(0));
        }
        long primitiveNs = System.nanoTime() - t3;

        System.out.println("--- Technique 3: Primitive arrays (no boxing, contiguous memory) ---");
        System.out.printf("  %,d ladder updates : %,d ns total | %d ns/update%n",
                ITERS, primitiveNs, primitiveNs / ITERS);
        System.out.printf("  Memory layout     : double[%d] = %,d bytes"
                + " (no object headers, no GC pointer scanning)%n%n",
                LEVELS, LEVELS * 8L);

        // ---- 4. SYMBOL AS LONG ----
        System.out.println("--- Technique 4: Symbol encoded as long ---");
        System.out.printf("  %-8s -> 0x%016X -> decoded: '%s'%n",
                "AAPL",   SYM_AAPL,   longToSymbol(SYM_AAPL));
        System.out.printf("  %-8s -> 0x%016X -> decoded: '%s'%n",
                "BTCUSD", SYM_BTCUSD, longToSymbol(SYM_BTCUSD));
        System.out.printf("  %-8s -> 0x%016X -> decoded: '%s'%n",
                "ETHUSD", SYM_ETHUSD, longToSymbol(SYM_ETHUSD));
        System.out.printf("  Equality is primitive ==, not String.equals():%n");
        System.out.printf("    SYM_AAPL == SYM_BTCUSD -> %b  (different symbols)%n",
                SYM_AAPL == SYM_BTCUSD);
        System.out.printf("    SYM_AAPL == symbolToLong(\"AAPL\") -> %b  (same symbol)%n%n",
                SYM_AAPL == symbolToLong("AAPL"));

        // ---- 5. PRE-ALLOCATED OUTPUT BUFFER ----
        FillBuffer fillBuf = new FillBuffer(16);  // allocated once at startup
        long chk5 = 0;
        long t4 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) {
            matchOrder(i, 200, PRICE_RAW, fillBuf);    // no allocation inside
            for (int f = 0; f < fillBuf.count(); f++) {
                chk5 += fillBuf.get(f).quantity;       // read from pre-allocated Fill
            }
        }
        long fillNs = System.nanoTime() - t4;

        System.out.println("--- Technique 5: Pre-allocated output buffer (FillBuffer) ---");
        System.out.printf("  %,d match events  : %,d ns total | %d ns/event"
                + "  [ZERO Fill allocation]%n", ITERS, fillNs, fillNs / ITERS);
        System.out.printf("  Total qty (checksum): %,d%n%n", chk5);

        // ---- SUMMARY ----
        System.out.println("=== Summary: Hot Path Allocation Sources & Fixes ===");
        System.out.println();
        System.out.printf("  %-35s | %-6s | %s%n",
                "Technique", "Alloc?", "Notes");
        System.out.println("  " + "-".repeat(75));
        System.out.printf("  %-35s | %-6s | %s%n",
                "Naive new() per event",         "YES",  "GC pressure, latency spikes");
        System.out.printf("  %-35s | %-6s | %s%n",
                "1. Flyweight (shared instance)", "NONE", "single-threaded hot path safe");
        System.out.printf("  %-35s | %-6s | %s%n",
                "2. ThreadLocal reuse",           "NONE", "multi-thread safe, no lock");
        System.out.printf("  %-35s | %-6s | %s%n",
                "3. Primitive arrays (no boxing)","NONE", "cache-friendly, GC-invisible");
        System.out.printf("  %-35s | %-6s | %s%n",
                "4. Symbol as long",              "NONE", "== comparison, fits register");
        System.out.printf("  %-35s | %-6s | %s%n",
                "5. Pre-allocated output buffer", "NONE", "caller owns buffer lifetime");
        System.out.println();
        System.out.println("  Coinbase result: ~1 microsecond matching engine processing time.");
    }
}