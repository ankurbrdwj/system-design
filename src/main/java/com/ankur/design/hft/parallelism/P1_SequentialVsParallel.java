package com.ankur.design.hft.parallelism;

import java.util.stream.LongStream;

/**
 * CLAIM: Parallelism is strictly an optimisation — it only helps when work per element
 * is large enough to outweigh thread coordination overhead.
 *
 * Proof:
 *   Small N  → parallel is SLOWER (overhead dominates)
 *   Large N  → parallel is FASTER (work dominates)
 */
public class P1_SequentialVsParallel {

    static long sumSequential(long n) {
        return LongStream.rangeClosed(1, n).sum();
    }

    static long sumParallel(long n) {
        return LongStream.rangeClosed(1, n).parallel().sum();
    }

    static void benchmark(String label, long n) {
        // warm up JIT
        for (int i = 0; i < 5; i++) { sumSequential(n); sumParallel(n); }

        long t0 = System.nanoTime();
        long seqResult = sumSequential(n);
        long seqMs = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        long parResult = sumParallel(n);
        long parMs = (System.nanoTime() - t0) / 1_000_000;

        assert seqResult == parResult;
        System.out.printf("%-20s N=%-12d  seq=%3d ms  par=%3d ms  speedup=%.1fx%n",
                label, n, seqMs, parMs, (double) seqMs / Math.max(parMs, 1));
    }

    public static void main(String[] args) {
        benchmark("tiny (no benefit)",   10_000L);
        benchmark("medium",              10_000_000L);
        benchmark("large (parallel wins)", 100_000_000L);
    }
}