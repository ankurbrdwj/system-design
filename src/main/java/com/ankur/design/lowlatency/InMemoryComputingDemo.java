package com.ankur.design.lowlatency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keyword: In-Memory Computing (IMC)
 *
 * Demonstrates a tiered in-memory cache that models the CPU memory hierarchy
 * (L1 -> L2 -> L3 -> Main memory). Each tier has a capacity and evicts to the
 * next tier on overflow, mimicking the principle of "data gravity" — keeping
 * hot data close to the CPU.
 *
 * In a real low-latency system this maps to:
 *   L1 cache  => thread-local ring buffer / per-core structures
 *   L2/L3     => NUMA-node local heap objects
 *   Main mem  => off-heap DirectByteBuffer regions
 *   Disk/Net  => avoided entirely for the hot path
 */
public class InMemoryComputingDemo {

    private static final int L1_CAPACITY = 8;
    private static final int L2_CAPACITY = 64;
    private static final int L3_CAPACITY = 512;

    // Simplified LRU-like tiers using LinkedHashMap wrapped for concurrency
    private final Map<String, Double> l1 = new java.util.LinkedHashMap<>(L1_CAPACITY, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Double> e) { return size() > L1_CAPACITY; }
    };
    private final Map<String, Double> l2 = new java.util.LinkedHashMap<>(L2_CAPACITY, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Double> e) { return size() > L2_CAPACITY; }
    };
    private final Map<String, Double> l3 = new ConcurrentHashMap<>(L3_CAPACITY);

    // Track tier hits for latency simulation
    private long l1Hits, l2Hits, l3Hits, misses;

    /** Store a price into L1 (hottest tier). */
    public synchronized void put(String key, double price) {
        l1.put(key, price);
    }

    /**
     * Read with tier-cascade: L1 -> L2 -> L3 -> miss.
     * Returns null on miss (would trigger a network/DB fetch in production).
     */
    public synchronized Double get(String key) {
        Double v = l1.get(key);
        if (v != null) { l1Hits++; return v; }

        v = l2.get(key);
        if (v != null) { l2Hits++; l1.put(key, v); return v; }  // promote to L1

        v = l3.get(key);
        if (v != null) { l3Hits++; l2.put(key, v); l1.put(key, v); return v; }

        misses++;
        return null; // cache miss — fetch from slow path
    }

    /** Simulate bulk pre-loading (system warm-up / pre-heating phase). */
    public void warmUp(int entries) {
        for (int i = 0; i < entries; i++) {
            String key = "SYM-" + i;
            double price = 100.0 + i * 0.01;
            // Seed all tiers so hot data is immediately accessible
            l3.put(key, price);
            if (i < L2_CAPACITY) { synchronized (this) { l2.put(key, price); } }
            if (i < L1_CAPACITY) { synchronized (this) { l1.put(key, price); } }
        }
    }

    public void printStats() {
        long total = l1Hits + l2Hits + l3Hits + misses;
        System.out.println("=== In-Memory Computing Tier Hit Stats ===");
        System.out.printf("L1 hits : %d (%.1f%%)%n", l1Hits, pct(l1Hits, total));
        System.out.printf("L2 hits : %d (%.1f%%)%n", l2Hits, pct(l2Hits, total));
        System.out.printf("L3 hits : %d (%.1f%%)%n", l3Hits, pct(l3Hits, total));
        System.out.printf("Misses  : %d (%.1f%%)%n", misses, pct(misses, total));
        System.out.printf("Total   : %d reads%n", total);
    }

    private double pct(long n, long total) { return total == 0 ? 0 : 100.0 * n / total; }

    public static void main(String[] args) {
        InMemoryComputingDemo imc = new InMemoryComputingDemo();
        imc.warmUp(600); // seed L3 with 600 symbols, L2 with 64, L1 with 8

        // Simulate access pattern: 80% hits on hot symbols (L1/L2), 20% cold
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < 100_000; i++) {
            // Hot path: symbol in [0,7] — should be in L1
            String key = rng.nextDouble() < 0.8
                    ? "SYM-" + rng.nextInt(8)           // hot
                    : "SYM-" + rng.nextInt(600);         // cold

            Double price = imc.get(key);
            if (price == null) {
                // Simulate slow fetch and promote into cache
                imc.put(key, 99.99);
            }
        }

        imc.printStats();

        // Demonstrate latency difference between tiers (simulated via nanoTime)
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) imc.get("SYM-0");  // always in L1
        long l1AvgNs = (System.nanoTime() - start) / 10_000;

        start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) imc.get("SYM-500");  // L3 then promoted
        long l3AvgNs = (System.nanoTime() - start) / 10_000;

        System.out.printf("%nL1 avg read: %d ns | L3 avg read: %d ns%n", l1AvgNs, l3AvgNs);
    }
}