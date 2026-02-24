package com.ankur.design.hft.interview;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * Block 1: Core Java & Concurrency
 *
 * Interview questions answered here:
 *   Q1. "How does ConcurrentHashMap achieve thread safety without a global lock?"
 *   Q2. "How would you implement a thread-safe LRU cache in Java?"
 *   Q3. "Explain happens-before in the Java Memory Model with an example."
 *   Q4. "What is the difference between wait()/notify() and Condition?"
 *   Q5. "What are the risks of Executors.newFixedThreadPool()?"
 *   Q6. "How would you fan-out a price request to 3 exchanges simultaneously?"
 *
 * Sections:
 *   1. LRU Cache         — LinkedHashMap access-order + removeEldestEntry
 *   2. ConcurrentHashMap — CAS + bin-level locking; lock-per-key pattern
 *   3. Volatile vs Atomic — visibility-only vs atomic read-modify-write
 *   4. CountDownLatch vs CyclicBarrier — one-shot vs reusable barrier
 *   5. Semaphore         — connection pool / rate-limit permit model
 *   6. ReentrantLock + ReadWriteLock — tryLock, read-heavy cache
 *   7. CompletableFuture — thenApply, thenCompose, allOf for multi-venue fan-out
 */
public class Block1_CoreJavaConcurrency {

    // =========================================================================
    // 1. LRU CACHE
    //
    // Q: "How would you implement a thread-safe LRU cache in Java?"
    //
    // LinkedHashMap in ACCESS-ORDER mode (true as 3rd constructor arg) moves
    // the most-recently-accessed entry to the tail on every get().
    // Override removeEldestEntry() to evict the head when over capacity.
    // Wrap in Collections.synchronizedMap() for thread safety, or use
    // a read-write lock for better concurrent read throughput.
    //
    // Real use: instrument reference-data cache (symbol → metadata),
    // exchange session cache, market data snapshot cache.
    // =========================================================================

    static final class LRUCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxCapacity;

        LRUCache(int maxCapacity) {
            // accessOrder=true: get() and put() move entry to tail
            super(maxCapacity, 0.75f, true);
            this.maxCapacity = maxCapacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            // Called after every put(); evict LRU entry (head) when over limit
            return size() > maxCapacity;
        }

        // Thread-safe wrapper used in production
        static <K, V> Map<K, V> threadSafe(int capacity) {
            return Collections.synchronizedMap(new LRUCache<>(capacity));
        }
    }

    // =========================================================================
    // 2. CONCURRENTHASHMAP
    //
    // Q: "How does ConcurrentHashMap achieve thread safety without a global lock?"
    //
    // Java 8+ internals:
    //   - Reads:  lock-free (volatile array slots)
    //   - Writes: CAS on empty bins, synchronized on the bin's first node for
    //             collisions — only ONE bucket locked at a time, not the whole map.
    //   - No global lock → many threads can write different buckets concurrently.
    //   - At 8 collisions in a bin: linked list → red-black tree (O(log n) lookup).
    //
    // Interview trap: Hashtable locks the ENTIRE map on every read AND write.
    //   ConcurrentHashMap is always the right answer for concurrent code.
    //
    // Lock-per-key pattern: one lock per trading symbol — much finer granularity
    // than one global lock. Used in rate service (see rate/Readme.md).
    // =========================================================================

    static final class OrderRouter {
        // One lock per symbol — threads for different symbols never contend
        private final ConcurrentHashMap<String, Object> symbolLocks = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Long>   positions   = new ConcurrentHashMap<>();

        Object lockFor(String symbol) {
            // computeIfAbsent is atomic — safe even if two threads call simultaneously
            return symbolLocks.computeIfAbsent(symbol, k -> new Object());
        }

        void updatePosition(String symbol, long deltaQty) {
            synchronized (lockFor(symbol)) {
                // Only threads for the SAME symbol block each other
                positions.merge(symbol, deltaQty, Long::sum);
            }
        }

        long getPosition(String symbol) {
            return positions.getOrDefault(symbol, 0L);
        }
    }

    // =========================================================================
    // 3. VOLATILE vs AtomicLong — Java Memory Model happens-before
    //
    // Q: "Explain happens-before in the Java Memory Model with an example."
    //
    // volatile:
    //   - Guarantees VISIBILITY: every write is flushed to main memory;
    //     every read fetches from main memory (not CPU cache).
    //   - Does NOT guarantee atomicity for compound operations (i++ is 3 steps:
    //     read, increment, write — another thread can interleave).
    //   - Correct use: single-writer flag, stop signal, published reference.
    //   - happens-before: a write to a volatile V happens-before every
    //     subsequent read of V. This is the formal JMM guarantee.
    //
    // AtomicLong:
    //   - Wraps a volatile long + CAS (Compare-And-Swap) CPU instruction.
    //   - incrementAndGet() is atomic: no interleaving possible.
    //   - Use for counters, sequence generators, stats — anything with
    //     multiple threads doing read-modify-write.
    // =========================================================================

    static volatile boolean running = true;           // flag: single-writer OK
    static final AtomicLong orderCount = new AtomicLong(0); // counter: many writers

    static void demonstrateMemoryModel() throws InterruptedException {
        // Correct: volatile flag written by one thread, read by another
        Thread reader = new Thread(() -> {
            while (running) Thread.onSpinWait();  // spin until flag cleared
            System.out.println("  [volatile] Reader saw running=false — happens-before guaranteed");
        });
        reader.start();
        Thread.sleep(10);
        running = false;  // single write — volatile is correct here
        reader.join();

        // Correct: AtomicLong for concurrent increment
        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 10_000; i++) pool.submit(() -> orderCount.incrementAndGet());
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
        System.out.printf("  [AtomicLong] Final count: %,d (expected 10,000)%n", orderCount.get());

        // WRONG (do not do this): volatile long counter; counter++  — NOT atomic
        // Two threads can both read the same value and both write value+1 → lost update
    }

    // =========================================================================
    // 4. CountDownLatch vs CyclicBarrier
    //
    // CountDownLatch: one-time, cannot be reset. One or more threads wait
    //   until a count reaches zero. Main use: wait for N services to start up.
    //
    // CyclicBarrier: reusable. All N threads must arrive before any proceeds.
    //   Main use: parallel simulation rounds — all worker threads finish
    //   round N before any starts round N+1 (e.g. market simulation phases).
    // =========================================================================

    static void demonstrateLatches() throws InterruptedException, BrokenBarrierException {
        int VENUES = 3;

        // --- CountDownLatch: wait for N exchange connections to be established ---
        CountDownLatch connected = new CountDownLatch(VENUES);
        for (int i = 0; i < VENUES; i++) {
            final String venue = "VENUE-" + i;
            new Thread(() -> {
                try { Thread.sleep(10); } catch (InterruptedException e) {}
                System.out.println("  [Latch] " + venue + " connected");
                connected.countDown();  // decrement; never resets
            }).start();
        }
        connected.await();  // blocks until count == 0
        System.out.println("  [Latch] All venues connected — trading can start");

        // --- CyclicBarrier: synchronise N threads between processing rounds ---
        CyclicBarrier barrier = new CyclicBarrier(VENUES, () ->
                System.out.println("  [Barrier] All venues finished round — advancing to next"));

        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < VENUES; i++) {
                final int v = i;
                new Thread(() -> {
                    try {
                        Thread.sleep(5 + v * 3);  // simulate varying work
                        barrier.await();           // wait for all threads
                    } catch (Exception e) {}
                }).start();
            }
            Thread.sleep(60);  // give threads time to finish for demo output
        }
    }

    // =========================================================================
    // 5. SEMAPHORE — connection pool / rate limiting
    //
    // Semaphore maintains N permits. acquire() blocks if 0 permits remain;
    // release() returns a permit. Natural fit for:
    //   - Connection pool (max N simultaneous exchange connections)
    //   - Rate limiting  (max N requests in flight at any time)
    //   - Throttling order submission to comply with exchange throttle rules
    // =========================================================================

    static final class ExchangeConnectionPool {
        private final Semaphore permits;
        private final String    exchangeName;

        ExchangeConnectionPool(String name, int maxConnections) {
            this.exchangeName = name;
            this.permits      = new Semaphore(maxConnections, true); // fair
        }

        void sendOrder(String order) throws InterruptedException {
            permits.acquire();          // block if all connections busy
            try {
                // simulate network round-trip
                Thread.sleep(2);
                System.out.println("  [Semaphore] " + exchangeName + " sent: " + order
                        + "  (available=" + permits.availablePermits() + ")");
            } finally {
                permits.release();      // always release even if exception
            }
        }
    }

    // =========================================================================
    // 6. ReentrantLock + ReadWriteLock
    //
    // ReentrantLock advantages over synchronized:
    //   tryLock()              — non-blocking attempt; avoids deadlock
    //   tryLock(timeout, unit) — give up after a deadline
    //   lockInterruptibly()    — thread can be interrupted while waiting
    //   fairness flag          — FIFO ordering prevents starvation
    //
    // ReadWriteLock: multiple threads can hold the READ lock simultaneously;
    //   the WRITE lock is exclusive. Ideal for a market-data cache where
    //   many threads read the latest price but only one feed thread writes.
    // =========================================================================

    static final class MarketDataCache {
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
        private final Map<String, Double> prices = new HashMap<>();

        // Feed thread: exclusive write — blocks all readers momentarily
        void updatePrice(String symbol, double price) {
            rwLock.writeLock().lock();
            try {
                prices.put(symbol, price);
            } finally {
                rwLock.writeLock().unlock();  // always in finally
            }
        }

        // Strategy threads: concurrent reads — no blocking between readers
        double getPrice(String symbol) {
            rwLock.readLock().lock();
            try {
                return prices.getOrDefault(symbol, Double.NaN);
            } finally {
                rwLock.readLock().unlock();
            }
        }
    }

    static final class OrderSubmitter {
        private final ReentrantLock lock = new ReentrantLock();

        // tryLock prevents indefinite blocking — critical on a hot trading path
        boolean trySubmit(String order) {
            if (lock.tryLock()) {   // returns immediately if lock unavailable
                try {
                    System.out.println("  [tryLock] Submitted: " + order);
                    return true;
                } finally {
                    lock.unlock();
                }
            }
            System.out.println("  [tryLock] Lock busy — dropped: " + order);
            return false;
        }
    }

    // =========================================================================
    // 7. COMPLETABLEFUTURE
    //
    // Q: "How would you fan-out a price request to 3 exchanges simultaneously?"
    //
    // thenApply(fn)       — transform result synchronously on the same thread
    // thenApplyAsync(fn)  — transform result on a new thread from common pool
    // thenCompose(fn)     — chain a future that returns another future (flatMap)
    // thenCombine(f, fn)  — combine two independent futures when both complete
    // allOf(f1, f2, f3)   — wait for ALL futures; no result (use join() per future)
    // anyOf(f1, f2, f3)   — return as soon as the FASTEST future completes
    //
    // Real use: fan-out best-execution price check across LSE, NYSE, NASDAQ
    // simultaneously, pick the best price, submit to the winning venue.
    // =========================================================================

    static double fetchPrice(String venue, double basePrice) {
        try { Thread.sleep(10 + (long)(Math.random() * 20)); } catch (InterruptedException e) {}
        return basePrice + (Math.random() - 0.5) * 0.02;  // simulate slight spread
    }

    static void demonstrateCompletableFuture() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();

        // Fan-out: 3 simultaneous price requests to 3 venues
        CompletableFuture<Double> lse    = CompletableFuture.supplyAsync(() -> fetchPrice("LSE",    100.0), pool);
        CompletableFuture<Double> nyse   = CompletableFuture.supplyAsync(() -> fetchPrice("NYSE",   100.0), pool);
        CompletableFuture<Double> nasdaq = CompletableFuture.supplyAsync(() -> fetchPrice("NASDAQ", 100.0), pool);

        // allOf waits for all three, then pick best (lowest ask price)
        CompletableFuture<Double> bestPrice = CompletableFuture
                .allOf(lse, nyse, nasdaq)
                .thenApply(v -> {
                    double best = Math.min(lse.join(), Math.min(nyse.join(), nasdaq.join()));
                    System.out.printf("  [CF allOf] LSE=%.4f NYSE=%.4f NASDAQ=%.4f → best=%.4f%n",
                            lse.join(), nyse.join(), nasdaq.join(), best);
                    return best;
                });

        bestPrice.get();  // block for demo output

        // thenCompose: fetch price THEN submit order (sequential dependency)
        CompletableFuture<String> orderResult = CompletableFuture
                .supplyAsync(() -> fetchPrice("PRIMARY", 100.0), pool)
                .thenCompose(price -> CompletableFuture.supplyAsync(
                        () -> "Order submitted at " + String.format("%.4f", price), pool));

        System.out.println("  [CF thenCompose] " + orderResult.get());

        pool.shutdown();
    }

    // =========================================================================
    // MAIN
    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("=== Block 1: Core Java & Concurrency ===");
        System.out.println();

        // 1. LRU Cache
        System.out.println("--- 1. LRU Cache (LinkedHashMap, access-order) ---");
        Map<String, Double> lruCache = LRUCache.threadSafe(3);
        lruCache.put("AAPL", 150.0);
        lruCache.put("MSFT", 300.0);
        lruCache.put("GOOG", 140.0);
        lruCache.get("AAPL");             // AAPL accessed → moves to tail (MRU)
        lruCache.put("TSLA", 250.0);      // capacity=3 → MSFT evicted (LRU head)
        System.out.println("  After eviction: " + lruCache.keySet());
        System.out.println("  (MSFT evicted — least recently used)");
        System.out.println();

        // 2. ConcurrentHashMap lock-per-key
        System.out.println("--- 2. ConcurrentHashMap — lock-per-key pattern ---");
        OrderRouter router = new OrderRouter();
        router.updatePosition("AAPL", 1000);
        router.updatePosition("AAPL", -200);
        router.updatePosition("GOOG", 500);
        System.out.printf("  AAPL position: %,d  GOOG position: %,d%n",
                router.getPosition("AAPL"), router.getPosition("GOOG"));
        System.out.println();

        // 3. Volatile vs AtomicLong
        System.out.println("--- 3. Volatile (visibility) vs AtomicLong (atomicity) ---");
        running = true;
        orderCount.set(0);
        demonstrateMemoryModel();
        System.out.println();

        // 4. CountDownLatch vs CyclicBarrier
        System.out.println("--- 4. CountDownLatch (one-shot) vs CyclicBarrier (reusable) ---");
        demonstrateLatches();
        Thread.sleep(200);
        System.out.println();

        // 5. Semaphore
        System.out.println("--- 5. Semaphore — max 2 concurrent exchange connections ---");
        ExchangeConnectionPool exchPool = new ExchangeConnectionPool("LSE", 2);
        ExecutorService svcPool = Executors.newFixedThreadPool(5);
        for (int i = 1; i <= 5; i++) {
            final int n = i;
            svcPool.submit(() -> {
                try { exchPool.sendOrder("ORD-" + n); } catch (InterruptedException e) {}
            });
        }
        svcPool.shutdown();
        svcPool.awaitTermination(3, TimeUnit.SECONDS);
        System.out.println();

        // 6. ReentrantLock + ReadWriteLock
        System.out.println("--- 6. ReentrantLock (tryLock) + ReadWriteLock (market data cache) ---");
        MarketDataCache cache = new MarketDataCache();
        cache.updatePrice("AAPL", 151.25);
        cache.updatePrice("GOOG", 141.50);
        System.out.printf("  AAPL: %.2f  GOOG: %.2f%n",
                cache.getPrice("AAPL"), cache.getPrice("GOOG"));
        OrderSubmitter submitter = new OrderSubmitter();
        submitter.trySubmit("BUY 100 AAPL @ 151.25");
        System.out.println();

        // 7. CompletableFuture
        System.out.println("--- 7. CompletableFuture — multi-venue best execution ---");
        demonstrateCompletableFuture();
        System.out.println();

        // Key interview trap summary
        System.out.println("=== Key Interview Traps ===");
        System.out.println("  Executors.newFixedThreadPool(n) uses an UNBOUNDED LinkedBlockingQueue");
        System.out.println("  → submit() never blocks → OOM under sustained load");
        System.out.println("  Fix: new ThreadPoolExecutor(n, n, 0, SECONDS,");
        System.out.println("             new ArrayBlockingQueue<>(1000),        // bounded");
        System.out.println("             new CallerRunsPolicy())                 // back-pressure");
        System.out.println();
        System.out.println("  volatile i++ is NOT atomic: read(i), increment, write(i)");
        System.out.println("  → two threads can both read the same i and write i+1 → lost update");
        System.out.println("  Fix: AtomicLong.incrementAndGet() — single CAS instruction");
    }
}