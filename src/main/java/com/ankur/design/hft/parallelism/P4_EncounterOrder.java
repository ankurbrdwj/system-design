package com.ankur.design.hft.parallelism;

import java.util.List;
import java.util.stream.LongStream;

/**
 * CLAIM: Order-sensitive operations (limit, findFirst, skip) force parallelism
 *        to re-serialise results, eliminating any speed benefit.
 *        Calling .unordered() relaxes this and restores parallel speed.
 *
 * Proof:
 *   parallel + limit(N)            → slow  (must maintain encounter order across threads)
 *   parallel + unordered + limit(N)→ fast  (each thread can return first N it finds)
 *
 * Why ordered limit is slow:
 *   Thread-1 processes elements 0..249,999
 *   Thread-2 processes elements 250,000..499,999
 *   Thread-3 processes elements 500,000..749,999
 *   ...
 *   To honour encounter order, the result MUST come from Thread-1's prefix.
 *   So all other threads' work is thrown away — no real parallelism.
 *
 * With .unordered():
 *   Any thread can contribute any element to the limit — true parallel race.
 */
public class P4_EncounterOrder {

    static final long N = 50_000_000L;
    static final long LIMIT = 1_000_000L;

    static long orderedLimit() {
        return LongStream.rangeClosed(1, N)
                .parallel()
                .limit(LIMIT)       // must preserve original order → slow
                .sum();
    }

    static long unorderedLimit() {
        return LongStream.rangeClosed(1, N)
                .parallel()
                .unordered()        // drop order constraint → fast
                .limit(LIMIT)
                .sum();
    }

    static long sequential() {
        return LongStream.rangeClosed(1, LIMIT).sum();  // baseline
    }

    static long time(String label, java.util.function.LongSupplier fn) {
        fn.getAsLong(); // warm up
        long t = System.nanoTime();
        long r = fn.getAsLong();
        long ms = (System.nanoTime() - t) / 1_000_000;
        System.out.printf("%-40s result=%15d  time=%d ms%n", label, r, ms);
        return ms;
    }

    public static void main(String[] args) {
        // Note: unordered result differs from ordered (different elements sum),
        // both are valid "any 1M elements from the stream" answers.
        time("sequential limit (baseline)",        P4_EncounterOrder::sequential);
        time("parallel + limit (ordered, SLOW)",   P4_EncounterOrder::orderedLimit);
        time("parallel + unordered + limit (FAST)",P4_EncounterOrder::unorderedLimit);
    }
}