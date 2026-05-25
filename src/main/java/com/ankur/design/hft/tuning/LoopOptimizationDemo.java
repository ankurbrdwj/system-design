package com.ankur.design.hft.tuning;

import java.util.ArrayList;
import java.util.List;

/**
 * TOPIC: Loop optimization — keep loops small, tight, and invariant-free.
 *
 * Tight loops are the hottest code in any system. Optimizing the loop body
 * by even a few nanoseconds compounds across millions of iterations.
 *
 * Key principles:
 *   1. DO ONE THING PER LOOP: small loops fit in the instruction cache (L1I).
 *      A bloated loop body may overflow the L1I (~32KB), causing instruction cache misses.
 *   2. HOIST LOOP INVARIANTS: if a value doesn't change across iterations,
 *      compute it ONCE before the loop, not N times inside.
 *   3. AVOID OBJECT CREATION IN LOOPS: new objects → GC pressure → pauses.
 *   4. SPLIT VALIDATION FROM PROCESSING: validate first in a clean pass,
 *      then process in a tight loop with no branching for null/type checks.
 */
public class LoopOptimizationDemo {

    // -------------------------------------------------------------------------
    // Example data
    // -------------------------------------------------------------------------

    static class Order {
        final String id;
        final double price;
        final int quantity;
        final String type; // "LIMIT" or "MARKET"

        Order(String id, double price, int quantity, String type) {
            this.id = id;
            this.price = price;
            this.quantity = quantity;
            this.type = type;
        }
    }

    // -------------------------------------------------------------------------
    // EXAMPLE 1: Bloated loop vs split passes
    // -------------------------------------------------------------------------

    // BAD: One loop doing 5 different things.
    // Problems:
    //   - Large instruction footprint — may not fit in L1 instruction cache (32KB)
    //   - Null checks and type checks force branch mispredictions on valid data
    //   - Exception handling code (try/catch) prevents JIT vectorization
    //   - Logging inside loop creates String objects on every iteration
    //   - CPU cannot pipeline efficiently — many different operations interleaved
    static long badBloatedLoop(List<Order> orders, double feeRate) {
        long totalQuantity = 0;
        StringBuilder log = new StringBuilder(); // BAD: shared buffer but still reset each loop run

        for (int i = 0; i < orders.size(); i++) {  // BAD: orders.size() called each iteration (see example 3)
            Order order = orders.get(i);

            // BAD: null check inside loop — every iteration pays for this branch
            if (order == null) {
                continue;
            }

            // BAD: type check inside loop — unpredictable branch
            if (!(order instanceof Order)) {
                continue;
            }

            // BAD: try/catch inside tight loop prevents JIT scalar replacement + vectorization
            try {
                // BAD: string concatenation inside loop → new String + StringBuilder every iteration
                String logLine = "Processing order: " + order.id + " qty=" + order.quantity;
                log.append(logLine).append("\n");

                // BAD: conditional inside compute step — mixes validation with computation
                if ("LIMIT".equals(order.type)) {
                    totalQuantity += order.quantity;
                } else if ("MARKET".equals(order.type)) {
                    totalQuantity += order.quantity;
                }

                // BAD: fee calculation using double boxing (autoboxing if assigned to Double)
                double fee = order.price * order.quantity * feeRate;

            } catch (Exception e) {
                // BAD: exception handling pollutes the loop — JIT won't optimize a loop with try/catch
                System.err.println("Error: " + e.getMessage());
            }
        }
        return totalQuantity;
    }

    // GOOD: Split into focused passes.
    //   Pass 1: validate (find nulls, invalid types) — a separate tight loop
    //   Pass 2: process (compute total) — a clean tight loop with no null checks
    //   Pass 3: log — separate concern, separate loop (or async off hot path)
    //
    // Each loop body is small → fits in L1I cache → no instruction cache misses.
    // No exception handling in the compute loop → JIT can vectorize (SIMD).
    static long goodSplitLoop(List<Order> orders, double feeRate) {
        // Pass 1: validate — find all valid orders (compact into a tight array)
        // This loop is tiny: just a null check and add
        Order[] valid = new Order[orders.size()];
        int validCount = 0;
        for (int i = 0; i < orders.size(); i++) {
            Order o = orders.get(i);
            if (o != null) {
                valid[validCount++] = o;  // GOOD: null-free array for next pass
            }
        }

        // Pass 2: compute — tight loop with zero null checks, zero type checks
        // JIT can potentially auto-vectorize this with SIMD instructions
        long totalQuantity = 0;
        for (int i = 0; i < validCount; i++) {
            totalQuantity += valid[i].quantity;  // GOOD: no branching, no null check, predictable
        }

        return totalQuantity;
    }

    // -------------------------------------------------------------------------
    // EXAMPLE 2: Loop invariant hoisting
    // -------------------------------------------------------------------------

    // BAD: Calling list.size() on every iteration.
    // list.size() is O(1) but still:
    //   - It's a method call → may not be inlined by JIT at lower optimization tiers
    //   - The JIT must prove list is not modified inside the loop to optimize it
    //   - On interpreted code: it's a virtual method dispatch every iteration
    //
    // BAD: Computing `threshold` from a sqrt inside the loop — result never changes.
    static long badInvariant(List<Integer> list, int base) {
        long sum = 0;
        for (int i = 0; i < list.size(); i++) {        // BAD: list.size() called every iteration
            double threshold = Math.sqrt(base) * 100;  // BAD: sqrt() computed every iteration — never changes
            if (list.get(i) > threshold) {
                sum += list.get(i);
            }
        }
        return sum;
    }

    // GOOD: Hoist all loop-invariant computations above the loop.
    static long goodInvariant(List<Integer> list, int base) {
        int n = list.size();                         // GOOD: hoisted — computed once
        double threshold = Math.sqrt(base) * 100;   // GOOD: hoisted — sqrt computed once
        long sum = 0;
        for (int i = 0; i < n; i++) {               // GOOD: loop bound is a local int — register-held
            if (list.get(i) > threshold) {
                sum += list.get(i);
            }
        }
        return sum;
    }

    // -------------------------------------------------------------------------
    // EXAMPLE 3: Unnecessary object creation inside loop
    // -------------------------------------------------------------------------

    // BAD: Creating a new object inside the loop on every iteration.
    // In HFT: GC stop-the-world pauses are measured in microseconds to milliseconds.
    // Every `new` on the hot path increases GC frequency.
    static long badObjectCreation(int[] prices) {
        long sum = 0;
        for (int i = 0; i < prices.length; i++) {
            // BAD: new StringBuilder created on every iteration — heap allocation in loop
            StringBuilder sb = new StringBuilder();
            sb.append("price=").append(prices[i]);
            // Simulate using the string (in practice this might be logged or sent)
            sum += sb.length(); // use it to prevent dead-code elimination
        }
        return sum;
    }

    // GOOD: Pre-allocate once, reset/reuse inside the loop.
    static long goodObjectCreation(int[] prices) {
        long sum = 0;
        StringBuilder sb = new StringBuilder(32); // GOOD: allocated ONCE before the loop
        for (int i = 0; i < prices.length; i++) {
            sb.setLength(0);               // GOOD: reset without allocation — no new object
            sb.append("price=").append(prices[i]);
            sum += sb.length();
        }
        return sum;
    }

    // -------------------------------------------------------------------------
    // Demo
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        final int SIZE = 1_000_000;
        final int REPS = 5;

        System.out.println("=== LoopOptimizationDemo ===");
        System.out.println();

        // ---- Example 1: Bloated vs split loops ----
        System.out.println("--- Example 1: Bloated loop vs split passes ---");

        List<Order> orders = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            orders.add(new Order("ORD-" + i, 100.0 + i * 0.01, 10 + (i % 50),
                    i % 2 == 0 ? "LIMIT" : "MARKET"));
        }

        // Warmup
        for (int w = 0; w < 3; w++) {
            badBloatedLoop(orders, 0.001);
            goodSplitLoop(orders, 0.001);
        }

        long badTotal = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            badBloatedLoop(orders, 0.001);
            badTotal += System.nanoTime() - t0;
        }

        long goodTotal = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            goodSplitLoop(orders, 0.001);
            goodTotal += System.nanoTime() - t0;
        }

        System.out.printf("BAD  (bloated single loop):  %,6d ms%n", badTotal / REPS / 1_000_000);
        System.out.printf("GOOD (split passes):         %,6d ms%n", goodTotal / REPS / 1_000_000);
        System.out.println("  Split loops: smaller body = fits in L1I, JIT can vectorize compute pass");

        // ---- Example 2: Invariant hoisting ----
        System.out.println();
        System.out.println("--- Example 2: Loop invariant hoisting ---");

        List<Integer> intList = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) intList.add(i % 10000);

        for (int w = 0; w < 3; w++) {
            badInvariant(intList, 1000);
            goodInvariant(intList, 1000);
        }

        long badInvTotal = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            badInvariant(intList, 1000);
            badInvTotal += System.nanoTime() - t0;
        }

        long goodInvTotal = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            goodInvariant(intList, 1000);
            goodInvTotal += System.nanoTime() - t0;
        }

        System.out.printf("BAD  (list.size() + sqrt inside loop): %,6d ms%n", badInvTotal / REPS / 1_000_000);
        System.out.printf("GOOD (hoisted size and threshold):     %,6d ms%n", goodInvTotal / REPS / 1_000_000);
        System.out.println("  Note: JIT may hoist these automatically at high optimization tier,");
        System.out.println("  but explicit hoisting is always safe and aids readability.");

        // ---- Example 3: Object creation inside loop ----
        System.out.println();
        System.out.println("--- Example 3: Object creation inside vs outside loop ---");

        int[] prices = new int[SIZE];
        for (int i = 0; i < SIZE; i++) prices[i] = 100 + (i % 900);

        for (int w = 0; w < 3; w++) {
            badObjectCreation(prices);
            goodObjectCreation(prices);
        }

        long badObjTotal = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            badObjectCreation(prices);
            badObjTotal += System.nanoTime() - t0;
        }

        long goodObjTotal = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            goodObjectCreation(prices);
            goodObjTotal += System.nanoTime() - t0;
        }

        System.out.printf("BAD  (new StringBuilder per iteration): %,6d ms  — GC pressure%n",
                badObjTotal / REPS / 1_000_000);
        System.out.printf("GOOD (reuse StringBuilder, setLength(0)): %,4d ms  — zero allocation%n",
                goodObjTotal / REPS / 1_000_000);

        System.out.println();
        System.out.println("Summary of loop optimizations:");
        System.out.println("  1. Split loops: one responsibility per loop — small body = L1I friendly");
        System.out.println("  2. Hoist invariants: compute once before the loop, not N times inside");
        System.out.println("  3. Preallocate: create objects outside the loop, reset and reuse inside");
        System.out.println("  4. No try/catch in hot loops — it prevents JIT vectorization");
        System.out.println("  5. Validate first, then compute — clean compute loop = JIT can auto-vectorize");
    }
}