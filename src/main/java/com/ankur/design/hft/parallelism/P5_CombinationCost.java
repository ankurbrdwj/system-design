package com.ankur.design.hft.parallelism;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.LongStream;
import java.util.stream.IntStream;

/**
 * CLAIM: Cheap associative combination (sum) parallelises well.
 *        Expensive combination (merging HashMaps) negates the parallel benefit.
 *
 * Proof:
 *   parallel sum         → fast   (each partial result is one long, merge is +)
 *   parallel groupingBy  → slow   (each partial result is a HashMap, merge copies all entries)
 *
 * Why HashMap merge is expensive:
 *   With 8 threads, fork/join produces 8 partial HashMaps.
 *   Merging them means iterating and inserting all entries into one map repeatedly.
 *   The merge work grows as O(N) — same cost as doing it sequentially.
 *
 *   sum merge:      8 longs   → one addition  (O(1) per merge)
 *   hashmap merge:  8 maps    → iterate+insert all entries (O(N/threads) per merge)
 */
public class P5_CombinationCost {

    static final int N = 2_000_000;

    // cheap combination — merging longs is O(1)
    static long cheapCombine() {
        return LongStream.rangeClosed(1, N).parallel().sum();
    }

    // expensive combination — merging two HashMaps is O(smaller map size)
    static Map<Integer, Long> expensiveCombine() {
        return IntStream.rangeClosed(1, N)
                .parallel()
                .boxed()
                .collect(java.util.stream.Collectors.groupingBy(
                        n -> n % 100,                  // 100 buckets
                        java.util.stream.Collectors.summingLong(Integer::longValue)
                ));
    }

    static Map<Integer, Long> expensiveCombineSequential() {
        return IntStream.rangeClosed(1, N)
                .boxed()
                .collect(java.util.stream.Collectors.groupingBy(
                        n -> n % 100,
                        java.util.stream.Collectors.summingLong(Integer::longValue)
                ));
    }

    static void time(String label, Runnable fn) {
        fn.run(); // warm up
        long t = System.nanoTime();
        fn.run();
        long ms = (System.nanoTime() - t) / 1_000_000;
        System.out.printf("%-50s time=%d ms%n", label, ms);
    }

    public static void main(String[] args) {
        time("cheap  combine: parallel sum             ", () -> cheapCombine());
        time("expensive combine: sequential groupingBy ", () -> expensiveCombineSequential());
        time("expensive combine: parallel groupingBy   ", () -> expensiveCombine());
        System.out.println("\n→ parallel groupingBy is often NOT faster — merge cost dominates");
    }
}