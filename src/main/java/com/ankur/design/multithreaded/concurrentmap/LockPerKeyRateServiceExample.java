package com.ankur.design.multithreaded.concurrentmap;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Example showing how to fix RateServiceImpl using lock-per-key pattern
 * Demonstrates the difference between global lock and per-key lock for currency rates
 */
public class LockPerKeyRateServiceExample {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Currency Rate Service: Global Lock vs Lock-Per-Key ===\n");

        // Simulate 100 concurrent requests for different currency pairs
        testGlobalLockService();
        System.out.println();
        testLockPerKeyService();
        System.out.println();
        testComputeIfAbsentService();
    }

    /**
     * BAD: Original RateServiceImpl approach - Global lock
     */
    static void testGlobalLockService() throws InterruptedException {
        System.out.println("1. GLOBAL LOCK (Original RateServiceImpl):");
        GlobalLockRateService service = new GlobalLockRateService();

        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(100);
        long start = System.currentTimeMillis();

        // 100 threads requesting different currency pairs
        for (int i = 0; i < 100; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    String from = "USD";
                    String to = getCurrency(idx % 10); // 10 different target currencies
                    double rate = service.getRate(from, to);
                    // System.out.println("Thread-" + idx + " got rate for " + from + "_" + to + ": " + rate);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        long duration = System.currentTimeMillis() - start;

        System.out.println("   Time: " + duration + "ms");
        System.out.println("   Problem: Threads fetching USD→EUR block threads fetching USD→JPY!");
        System.out.println("   Only 1 thread can fetch at a time, regardless of currency pair\n");
    }

    /**
     * GOOD: Lock-per-key approach
     */
    static void testLockPerKeyService() throws InterruptedException {
        System.out.println("2. LOCK-PER-KEY:");
        LockPerKeyRateService service = new LockPerKeyRateService();

        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(100);
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    String from = "USD";
                    String to = getCurrency(idx % 10);
                    double rate = service.getRate(from, to);
                    // System.out.println("Thread-" + idx + " got rate for " + from + "_" + to + ": " + rate);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        long duration = System.currentTimeMillis() - start;

        System.out.println("   Time: " + duration + "ms");
        System.out.println("   Solution: Each currency pair has its own lock!");
        System.out.println("   Multiple currency pairs can be fetched concurrently\n");
    }

    /**
     * BEST: ConcurrentHashMap.computeIfAbsent
     */
    static void testComputeIfAbsentService() throws InterruptedException {
        System.out.println("3. COMPUTEIFABSENT (Recommended):");
        ComputeIfAbsentRateService service = new ComputeIfAbsentRateService();

        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(100);
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    String from = "USD";
                    String to = getCurrency(idx % 10);
                    double rate = service.getRate(from, to);
                    // System.out.println("Thread-" + idx + " got rate for " + from + "_" + to + ": " + rate);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        long duration = System.currentTimeMillis() - start;

        System.out.println("   Time: " + duration + "ms");
        System.out.println("   Best: Cleanest code with automatic per-key locking!");
        System.out.println("   No manual lock management needed\n");
    }

    static String getCurrency(int idx) {
        String[] currencies = {"EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "CNY", "INR", "KRW", "MXN"};
        return currencies[idx];
    }

    // ============================================================================
    // Service Implementations
    // ============================================================================

    /**
     * BAD: Global lock - same as original RateServiceImpl
     */
    static class GlobalLockRateService {
        private final Map<String, Double> ratesCache = new ConcurrentHashMap<>();
        private final ReentrantLock updateLock = new ReentrantLock();

        public double getRate(String fromCurrency, String toCurrency) {
            String cacheKey = fromCurrency + "_" + toCurrency;

            // Fast path
            Double cached = ratesCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            // PROBLEM: Global lock blocks ALL currency pairs
            updateLock.lock();
            try {
                // Double-check
                cached = ratesCache.get(cacheKey);
                if (cached != null) {
                    return cached;
                }

                // Simulate API call
                double rate = fetchFromApi(fromCurrency, toCurrency);
                ratesCache.put(cacheKey, rate);
                return rate;
            } finally {
                updateLock.unlock();
            }
        }

        private double fetchFromApi(String from, String to) {
            try {
                Thread.sleep(50); // Simulate network delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Math.random() * 2;
        }
    }

    /**
     * GOOD: Lock per currency pair
     */
    static class LockPerKeyRateService {
        private final Map<String, Double> ratesCache = new ConcurrentHashMap<>();
        private final Map<String, Lock> keyLocks = new ConcurrentHashMap<>();

        public double getRate(String fromCurrency, String toCurrency) {
            String cacheKey = fromCurrency + "_" + toCurrency;

            // Fast path
            Double cached = ratesCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            // Get lock for THIS specific currency pair only
            Lock keyLock = keyLocks.computeIfAbsent(cacheKey, k -> new ReentrantLock());

            keyLock.lock();
            try {
                // Double-check
                cached = ratesCache.get(cacheKey);
                if (cached != null) {
                    return cached;
                }

                // Simulate API call
                double rate = fetchFromApi(fromCurrency, toCurrency);
                ratesCache.put(cacheKey, rate);
                return rate;
            } finally {
                keyLock.unlock();
            }
        }

        private double fetchFromApi(String from, String to) {
            try {
                Thread.sleep(50); // Simulate network delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Math.random() * 2;
        }
    }

    /**
     * BEST: ConcurrentHashMap.computeIfAbsent does lock-per-key automatically
     */
    static class ComputeIfAbsentRateService {
        private final Map<String, Double> ratesCache = new ConcurrentHashMap<>();

        public double getRate(String fromCurrency, String toCurrency) {
            String cacheKey = fromCurrency + "_" + toCurrency;

            // computeIfAbsent handles per-key locking automatically!
            return ratesCache.computeIfAbsent(cacheKey, k -> {
                // This function is called only if key is absent
                // and ConcurrentHashMap ensures only one thread per key executes this
                return fetchFromApi(fromCurrency, toCurrency);
            });
        }

        private double fetchFromApi(String from, String to) {
            try {
                Thread.sleep(50); // Simulate network delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Math.random() * 2;
        }
    }
}