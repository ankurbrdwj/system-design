package com.ankur.design.multithreaded.concurrentmap;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LOCK-PER-KEY Pattern with 100 Threads
 *
 * Key Concept: Each cache key gets its own lock
 * - Threads accessing DIFFERENT keys can work concurrently
 * - Threads accessing the SAME key will wait for each other
 */
public class ConcurrentHashMapDemo {

    private static final int NUM_THREADS = 100;
    private static final int NUM_KEYS = 10; // 10 different keys shared by 100 threads

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== LOCK-PER-KEY Pattern Demo ===");
        System.out.println("Threads: " + NUM_THREADS);
        System.out.println("Different keys: " + NUM_KEYS);
        System.out.println();

        Map<String, String> cache = new ConcurrentHashMap<>();

        // The magic: Map of locks, ONE lock per key
        Map<String, Lock> lockPerKey = new ConcurrentHashMap<>();

        AtomicInteger activeThreads = new AtomicInteger(0);
        AtomicInteger maxConcurrentThreads = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch latch = new CountDownLatch(NUM_THREADS);

        long startTime = System.currentTimeMillis();

        // Launch 100 threads
        for (int i = 0; i < NUM_THREADS; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Each thread picks a key (10 threads will compete for each key)
                    String key = "key_" + (threadId % NUM_KEYS);

                    System.out.println("Thread-" + threadId + " wants key: " + key);

                    // Check cache first (fast path, no lock needed)
                    String value = cache.get(key);
                    if (value != null) {
                        System.out.println("Thread-" + threadId + " found cached value for: " + key);
                        return;
                    }

                    // Get the lock for THIS specific key only
                    Lock keyLock = lockPerKey.computeIfAbsent(key, k -> new ReentrantLock());

                    System.out.println("Thread-" + threadId + " trying to acquire lock for: " + key);
                    keyLock.lock();
                    try {
                        System.out.println("  → Thread-" + threadId + " LOCKED " + key + " - computing...");

                        // Track how many threads are working concurrently
                        int current = activeThreads.incrementAndGet();
                        maxConcurrentThreads.updateAndGet(max -> Math.max(max, current));

                        // Double-check cache (another thread might have filled it)
                        value = cache.get(key);
                        if (value == null) {
                            // Simulate expensive computation (e.g., API call, DB query)
                            Thread.sleep(100);
                            cache.put(key, "value_" + key);
                            System.out.println("  ← Thread-" + threadId + " COMPUTED and cached " + key);
                        } else {
                            System.out.println("  ← Thread-" + threadId + " found it cached by another thread");
                        }

                        activeThreads.decrementAndGet();
                    } finally {
                        keyLock.unlock();
                        System.out.println("Thread-" + threadId + " UNLOCKED " + key);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });

            // Slight delay between thread starts to see the effect
            Thread.sleep(10);
        }

        latch.await();
        executor.shutdown();
        long duration = System.currentTimeMillis() - startTime;

        System.out.println();
        System.out.println("=== Results ===");
        System.out.println("Total time: " + duration + "ms");
        System.out.println("Max concurrent threads working: " + maxConcurrentThreads.get());
        System.out.println("Cache entries: " + cache.size());
        System.out.println();
        System.out.println("Key Insight:");
        System.out.println("  ✓ Up to " + NUM_KEYS + " threads worked concurrently (one per key)");
        System.out.println("  ✓ Threads competing for the SAME key waited for each other");
        System.out.println("  ✓ Threads working on DIFFERENT keys ran in parallel");
    }
}