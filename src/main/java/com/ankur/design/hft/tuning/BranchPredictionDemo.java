package com.ankur.design.hft.tuning;

import java.util.Arrays;
import java.util.Random;

/**
 * TOPIC: Branch Misprediction — CPU pipeline flush costs ~15 cycles.
 *
 * Modern CPUs use OUT-OF-ORDER, PIPELINED execution:
 *   - The CPU starts executing instruction N+1 before N completes (pipeline depth ~15 stages)
 *   - At a branch (if/while), the CPU GUESSES which path to take (branch predictor)
 *   - If the guess is WRONG: the pipeline must be FLUSHED — ~15 cycles wasted
 *   - If the guess is RIGHT: no penalty — branch is "free"
 *
 * BRANCH PREDICTOR learns patterns:
 *   - "always taken" or "never taken" → perfect prediction
 *   - Sorted array: first half = false, second half = true → 1 misprediction total
 *   - Random array: ~50% misprediction rate → ~7.5 wasted cycles per iteration
 *
 * For 100,000,000 iterations at 3 GHz:
 *   - 50% misprediction: 50M * 15 cycles / 3GHz = 250ms wasted in flush alone
 *   - Sorted array: 1 misprediction → essentially 0 waste
 *   - Branchless: 0 mispredictions → 0 waste
 *
 * BRANCHLESS arithmetic: replace branch with arithmetic that computes the same result.
 * The CPU executes BOTH paths speculatively and selects the result — no flush.
 */
public class BranchPredictionDemo {

    static final int SIZE = 100_000_000;
    static final int THRESHOLD = 128;

    // -------------------------------------------------------------------------
    // BAD: Unsorted array with branch
    // -------------------------------------------------------------------------

    // BAD: Random order means the branch outcome is unpredictable.
    // CPU's branch predictor cannot learn a pattern from random data.
    // Result: ~50% misprediction rate → pipeline flush on every other iteration.
    static long sumWithBranch(int[] data) {
        long sum = 0;
        for (int i = 0; i < data.length; i++) {
            // BAD: branch on random data — unpredictable
            if (data[i] > THRESHOLD) {
                sum += data[i];
            }
        }
        return sum;
    }

    // -------------------------------------------------------------------------
    // FIX 1: Sorted array — branch becomes predictable
    // -------------------------------------------------------------------------

    // GOOD: After sorting, all values <= THRESHOLD come first, then > THRESHOLD.
    // The branch is false for the first half, true for the second half.
    // The predictor transitions once — only 1 misprediction for all N iterations.
    // Caveat: sorting has O(N log N) cost — only worth it if you loop many times.
    static long sumWithBranchSorted(int[] data) {
        long sum = 0;
        for (int i = 0; i < data.length; i++) {
            // GOOD: predictable branch — false, false, ..., true, true, ...
            if (data[i] > THRESHOLD) {
                sum += data[i];
            }
        }
        return sum;
    }

    // -------------------------------------------------------------------------
    // FIX 2: Branchless — no branch at all
    // -------------------------------------------------------------------------

    // GOOD: No branch → no misprediction → no pipeline flush.
    //
    // How it works:
    //   (data[i] > THRESHOLD) evaluates to Java boolean: true or false
    //   Cast to int: true=1, false=0
    //   Negate (bitwise NOT + add 1 in two's complement): -1 = 0xFFFFFFFF, 0 = 0x00000000
    //   AND with data[i]: passes data[i] through when condition is true, gives 0 otherwise
    //
    //   mask = -(condition ? 1 : 0)
    //   if condition is true:  mask = -1 = 0xFFFFFFFF → data[i] & 0xFFFFFFFF = data[i]
    //   if condition is false: mask =  0 = 0x00000000 → data[i] & 0x00000000 = 0
    //
    // The ternary (condition ? 1 : 0) IS still a branch in Java source, but
    // JIT compilers often emit a CMOV (conditional move) instruction for this pattern,
    // which does not flush the pipeline.
    static long sumBranchless(int[] data) {
        long sum = 0;
        for (int i = 0; i < data.length; i++) {
            // GOOD: branchless — compute mask from condition, AND with value
            int mask = -(data[i] > THRESHOLD ? 1 : 0); // -1 (all ones) or 0
            sum += data[i] & mask;  // passes value through if mask=-1, zero if mask=0
        }
        return sum;
    }

    // -------------------------------------------------------------------------
    // Benchmark
    // -------------------------------------------------------------------------

    static long[] buildRandomData(int size) {
        // Use int[] for compact storage, values 0-255
        Random rnd = new Random(42);
        long[] data = new long[size];
        for (int i = 0; i < size; i++) {
            data[i] = rnd.nextInt(256);
        }
        return data;
    }

    public static void main(String[] args) {
        System.out.println("=== BranchPredictionDemo ===");
        System.out.println("Pipeline depth: ~15 stages. Misprediction cost: ~15 cycles.");
        System.out.printf("Array size: %,d elements, threshold: %d%n%n", SIZE, THRESHOLD);

        // Build data — use int[] for cache efficiency
        System.out.println("Building data arrays...");
        int[] random = new int[SIZE];
        Random rnd = new Random(42);
        for (int i = 0; i < SIZE; i++) {
            random[i] = rnd.nextInt(256);
        }

        // Sorted copy
        int[] sorted = Arrays.copyOf(random, SIZE);
        Arrays.sort(sorted);

        System.out.println("Data ready. Running benchmarks...");
        System.out.println();

        // Warmup (let JIT compile hot methods)
        System.out.println("Warming up JIT...");
        for (int w = 0; w < 3; w++) {
            sumWithBranch(random);
            sumWithBranchSorted(sorted);
            sumBranchless(random);
        }

        final int REPS = 3;

        // BAD: unsorted with branch
        long badTotal = 0;
        long badSum = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            badSum = sumWithBranch(random);
            badTotal += System.nanoTime() - t0;
        }
        long badTime = badTotal / REPS;

        // GOOD: sorted with branch
        long sortedTotal = 0;
        long sortedSum = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            sortedSum = sumWithBranchSorted(sorted);
            sortedTotal += System.nanoTime() - t0;
        }
        long sortedTime = sortedTotal / REPS;

        // GOOD: branchless
        long branchlessTotal = 0;
        long branchlessSum = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            branchlessSum = sumBranchless(random);
            branchlessTotal += System.nanoTime() - t0;
        }
        long branchlessTime = branchlessTotal / REPS;

        System.out.printf("BAD  (unsorted  + branch):    %,6d ms   sum=%,d%n",
                badTime / 1_000_000, badSum);
        System.out.printf("GOOD (sorted    + branch):    %,6d ms   sum=%,d%n",
                sortedTime / 1_000_000, sortedSum);
        System.out.printf("GOOD (unsorted  + branchless):%,6d ms   sum=%,d%n",
                branchlessTime / 1_000_000, branchlessSum);

        System.out.println();
        System.out.printf("Sorted speedup vs unsorted:     %.1fx%n", (double) badTime / sortedTime);
        System.out.printf("Branchless speedup vs unsorted: %.1fx%n", (double) badTime / branchlessTime);

        System.out.println();
        System.out.println("Key insights:");
        System.out.println("  1. Sorting data before a repeated branch = 1 misprediction total.");
        System.out.println("     Only worthwhile if you iterate the data many times.");
        System.out.println("  2. Branchless arithmetic eliminates the branch entirely.");
        System.out.println("     JIT may emit CMOV instruction — no pipeline flush possible.");
        System.out.println("  3. In HFT: branchless is preferred for hot path comparisons");
        System.out.println("     (e.g., checking if a price crosses a threshold on every tick).");
        System.out.println("  4. Note: JIT is smart. Measure before optimizing.");
        System.out.println("     Modern JIT often converts predictable branches to branchless itself.");
    }
}