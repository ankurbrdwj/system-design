package com.ankur.design.hft.parallelism;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * CLAIM: Arrays and array-backed structures split efficiently (O(1) split at midpoint).
 *        LinkedLists split poorly — must traverse to find the midpoint each time.
 *
 * Proof:
 *   ArrayList  (array-backed) → parallel is faster than sequential
 *   LinkedList (node-based)   → parallel may be SLOWER than sequential
 *
 * Why LinkedList is bad:
 *   Spliterator for LinkedList has no SUBSIZED characteristic.
 *   fork/join cannot split it at midpoint without traversal → serialises.
 *   Also: each node is a separate heap object → poor cache locality → cache misses.
 *
 *   Array layout:  [1][2][3][4][5][6][7][8]  ← contiguous, one cache line holds many elements
 *   LinkedList:    node→node→node→node        ← each node is a random heap address
 */
public class P3_DataSplitting {

    static final int N = 2_000_000;

    static long sumArrayList() {
        var list = LongStream.rangeClosed(1, N).boxed()
                .collect(Collectors.toCollection(ArrayList::new));
        return list.parallelStream().mapToLong(Long::longValue).sum();
    }

    static long sumLinkedList() {
        var list = LongStream.rangeClosed(1, N).boxed()
                .collect(Collectors.toCollection(LinkedList::new));
        return list.parallelStream().mapToLong(Long::longValue).sum();
    }

    static long sumArray() {
        long[] arr = LongStream.rangeClosed(1, N).toArray();
        return java.util.Arrays.stream(arr).parallel().sum();
    }

    static long time(String label, java.util.function.LongSupplier fn) {
        // warm up
        fn.getAsLong(); fn.getAsLong();
        long t = System.nanoTime();
        long result = fn.getAsLong();
        long ms = (System.nanoTime() - t) / 1_000_000;
        System.out.printf("%-30s result=%d  time=%d ms%n", label, result, ms);
        return ms;
    }

    public static void main(String[] args) {
        long arr  = time("long[] parallel (best)",        P3_DataSplitting::sumArray);
        long al   = time("ArrayList parallel (good)",     P3_DataSplitting::sumArrayList);
        long ll   = time("LinkedList parallel (bad)",     P3_DataSplitting::sumLinkedList);
        System.out.printf("%nLinkedList is %.1fx slower than array%n", (double) ll / Math.max(arr, 1));
    }
}