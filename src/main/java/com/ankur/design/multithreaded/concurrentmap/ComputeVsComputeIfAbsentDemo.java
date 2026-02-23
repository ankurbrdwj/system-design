package com.ankur.design.multithreaded.concurrentmap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demonstrates the CRITICAL difference between computeIfAbsent and compute
 * for implementing TTL (Time To Live) cache
 */
public class ComputeVsComputeIfAbsentDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== computeIfAbsent vs compute for TTL Cache ===\n");

        demonstrateComputeIfAbsentProblem();
        System.out.println("\n" + "=".repeat(60) + "\n");
        demonstrateComputeSolution();
    }

    /**
     * PROBLEM: computeIfAbsent CANNOT replace expired values!
     */
    static void demonstrateComputeIfAbsentProblem() throws InterruptedException {
        System.out.println("1. Using computeIfAbsent (BROKEN for TTL):");
        System.out.println("   computeIfAbsent(key, k -> newValue)");
        System.out.println();

        Map<String, CachedValue> cache = new ConcurrentHashMap<>();

        // First call - cache is empty, so it computes
        System.out.println("T=0s: First call to getRate()");
        CachedValue value1 = cache.computeIfAbsent("USD_EUR", k -> {
            System.out.println("  → Computing new value (rate=1.10)");
            return new CachedValue(1.10, System.currentTimeMillis());
        });
        System.out.println("  ← Got rate: " + value1.rate);
        System.out.println();

        // Wait 2 seconds (simulate expiration)
        System.out.println("⏰ Waiting 2 seconds for value to expire...");
        Thread.sleep(2000);
        System.out.println();

        // Second call - value exists but is EXPIRED
        System.out.println("T=2s: Second call to getRate() - value is EXPIRED");
        CachedValue value2 = cache.computeIfAbsent("USD_EUR", k -> {
            System.out.println("  → Computing new value (rate=1.20)");
            return new CachedValue(1.20, System.currentTimeMillis());
        });
        System.out.println("  ← Got rate: " + value2.rate);
        System.out.println();

        System.out.println("❌ PROBLEM: computeIfAbsent returned OLD expired rate: " + value2.rate);
        System.out.println("   It did NOT recompute because key was NOT absent!");
        System.out.println("   The lambda was NEVER called!");
        System.out.println();
        System.out.println("   Why? computeIfAbsent means:");
        System.out.println("   - IF key is ABSENT (null) → run the function");
        System.out.println("   - IF key EXISTS (even if expired) → return existing value");
    }

    /**
     * SOLUTION: compute CAN replace expired values!
     */
    static void demonstrateComputeSolution() throws InterruptedException {
        System.out.println("2. Using compute (CORRECT for TTL):");
        System.out.println("   compute(key, (k, existingValue) -> { check if expired... })");
        System.out.println();

        Map<String, CachedValue> cache = new ConcurrentHashMap<>();

        // First call - cache is empty
        System.out.println("T=0s: First call to getRate()");
        CachedValue value1 = cache.compute("USD_EUR", (key, existing) -> {
            System.out.println("  → Function called with existing=" + existing);
            if (existing != null && !existing.isExpired(2000)) {
                System.out.println("  → Returning existing value");
                return existing;
            }
            System.out.println("  → Computing new value (rate=1.10)");
            return new CachedValue(1.10, System.currentTimeMillis());
        });
        System.out.println("  ← Got rate: " + value1.rate);
        System.out.println();

        // Wait 2 seconds (simulate expiration)
        System.out.println("⏰ Waiting 2 seconds for value to expire...");
        Thread.sleep(2000);
        System.out.println();

        // Second call - value exists but is EXPIRED
        System.out.println("T=2s: Second call to getRate() - value is EXPIRED");
        CachedValue value2 = cache.compute("USD_EUR", (key, existing) -> {
            System.out.println("  → Function called with existing=" + existing);
            if (existing != null && !existing.isExpired(2000)) {
                System.out.println("  → Returning existing value");
                return existing;
            }
            System.out.println("  → Existing value is EXPIRED, computing new value (rate=1.20)");
            return new CachedValue(1.20, System.currentTimeMillis());
        });
        System.out.println("  ← Got rate: " + value2.rate);
        System.out.println();

        System.out.println("✅ SUCCESS: compute returned FRESH rate: " + value2.rate);
        System.out.println("   It detected expiration and recomputed!");
        System.out.println();
        System.out.println("   Why? compute ALWAYS runs the function:");
        System.out.println("   - Receives BOTH key AND existing value");
        System.out.println("   - Can check if existing is null/expired");
        System.out.println("   - Can decide whether to keep or replace");
    }

    static class CachedValue {
        final double rate;
        final long timestamp;

        CachedValue(double rate, long timestamp) {
            this.rate = rate;
            this.timestamp = timestamp;
        }

        boolean isExpired(long ttlMs) {
            return (System.currentTimeMillis() - timestamp) > ttlMs;
        }

        @Override
        public String toString() {
            return "CachedValue{rate=" + rate + ", age=" +
                   (System.currentTimeMillis() - timestamp) + "ms}";
        }
    }
}