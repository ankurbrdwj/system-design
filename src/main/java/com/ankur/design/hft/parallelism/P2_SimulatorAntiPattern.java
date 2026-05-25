package com.ankur.design.hft.parallelism;

import java.util.List;
import java.util.stream.IntStream;

/**
 * CLAIM: Shared mutable accumulators ("simulator anti-pattern") break parallelism.
 *        Proper parallel code divides data, processes independently, then combines.
 *
 * Proof:
 *   BROKEN  — forEach mutates a shared int[] accumulator → race condition, wrong result
 *   CORRECT — reduce() / sum() — no shared mutable state, always correct
 */
public class P2_SimulatorAntiPattern {

    static final List<Integer> DATA = IntStream.rangeClosed(1, 1_000_000)
            .boxed().toList();

    // ── BROKEN: shared mutable accumulator ───────────────────────────────────
    // Each parallel thread reads and writes the same array slot — race condition.
    // Result will be less than 500,000,500,000 and changes every run.
    static long brokenSum() {
        long[] acc = {0};                              // shared across all threads
        DATA.parallelStream().forEach(n -> acc[0] += n);  // race on acc[0]
        return acc[0];
    }

    // ── CORRECT: stateless reduction ──────────────────────────────────────────
    // Each thread processes its own chunk → fork/join combines partial sums.
    // No shared mutable state → always correct.
    static long correctSum() {
        return DATA.parallelStream().mapToLong(Integer::longValue).sum();
    }

    static final long EXPECTED = (long) 1_000_000 * 1_000_001 / 2;  // Gauss formula

    public static void main(String[] args) {
        System.out.println("Expected  = " + EXPECTED);
        System.out.println("BROKEN    = " + brokenSum()  + "  (wrong — race condition)");
        System.out.println("CORRECT   = " + correctSum() + "  (always right)");
    }
}