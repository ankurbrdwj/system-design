package com.ankur.design.hft.tuning;

import java.util.ArrayList;
import java.util.List;

/**
 * TOPIC: Spatial and Temporal Locality — cache-friendly vs cache-unfriendly memory access.
 *
 * Modern CPUs operate at ~3 GHz but DRAM latency is ~100ns (300 CPU cycles).
 * The CPU uses a cache hierarchy to bridge this gap:
 *   L1 cache:  ~4 cycles  (32 KB, per-core)
 *   L2 cache:  ~12 cycles (256 KB, per-core)
 *   L3 cache:  ~40 cycles (shared, several MB)
 *   RAM:       ~200 cycles
 *
 * A CACHE LINE is 64 bytes — the unit of transfer between RAM and cache.
 * When you read one int, the CPU loads the surrounding 64 bytes into cache.
 *
 * SPATIAL LOCALITY:  Access memory sequentially → prefetcher loads next cache line early.
 * TEMPORAL LOCALITY: Access same memory repeatedly → it stays warm in L1/L2.
 *
 * POINTER CHASING (linked list): each node is at a random heap address.
 * Every node access is likely a cache miss → 200 cycle penalty per element.
 *
 * ARRAY ACCESS: elements are contiguous. After loading the first element,
 * the next 15 ints are already in the same cache line — free.
 */
public class LocalityDemo {

    // -------------------------------------------------------------------------
    // EXAMPLE 1: Linked list vs array traversal
    // -------------------------------------------------------------------------

    // BAD: Linked list node — each node is independently heap-allocated.
    // Nodes are scattered across the heap in random memory locations.
    // Traversal = pointer chasing = cache miss per node.
    static class LinkedNode {
        int value;
        LinkedNode next;

        LinkedNode(int value) {
            this.value = value;
        }
    }

    // BAD: Build a linked list and sum all values.
    // Each node.next dereference is likely a cache miss.
    // For N nodes: ~N * 200 cycles (RAM latency) just for pointer loads.
    static long sumLinkedList(LinkedNode head) {
        long sum = 0;
        LinkedNode current = head;
        while (current != null) {
            sum += current.value;
            // BAD: current.next is a random pointer — CPU has no idea where to prefetch
            current = current.next;
        }
        return sum;
    }

    // GOOD: Array traversal — all elements are contiguous in memory.
    // CPU hardware prefetcher detects the sequential pattern and loads
    // upcoming cache lines BEFORE they are needed.
    // For N elements: ~N/16 cache misses (16 ints per 64-byte cache line).
    static long sumArray(int[] arr) {
        long sum = 0;
        // GOOD: arr[i] and arr[i+1] are adjacent in memory — already in the same cache line
        for (int i = 0; i < arr.length; i++) {
            sum += arr[i];
        }
        return sum;
    }

    // -------------------------------------------------------------------------
    // EXAMPLE 2: 2D array — column-major vs row-major access
    // -------------------------------------------------------------------------

    // Java (like C) stores 2D arrays in ROW-MAJOR order:
    //   matrix[0][0], matrix[0][1], matrix[0][2], ..., matrix[0][N-1]  ← contiguous
    //   matrix[1][0], matrix[1][1], ...                                 ← contiguous
    //
    // Each row is a separate array object on the heap.
    // Accessing matrix[row][col] sequentially across a row = cache-friendly.
    // Accessing matrix[row][col] sequentially down a column = cache-unfriendly (jumps to new row).

    static final int MATRIX_SIZE = 1000;

    // BAD: Column-by-column access on a row-major array.
    // For each col increment, we jump to a completely different row (different memory region).
    // This thrashes the cache — every inner-loop step is likely a cache miss.
    static long sumColumnMajor(int[][] matrix) {
        long sum = 0;
        // BAD: outer loop is column, inner is row — anti-pattern for row-major storage
        for (int col = 0; col < MATRIX_SIZE; col++) {
            for (int row = 0; row < MATRIX_SIZE; row++) {
                sum += matrix[row][col];  // BAD: jumps across rows — cache unfriendly
            }
        }
        return sum;
    }

    // GOOD: Row-by-row access — follows Java's row-major memory layout.
    // Each inner loop step reads the next int in the same cache line.
    // Prefetcher loads the next cache line ahead of time — near-zero cache misses.
    static long sumRowMajor(int[][] matrix) {
        long sum = 0;
        // GOOD: outer loop is row, inner is column — matches row-major storage
        for (int row = 0; row < MATRIX_SIZE; row++) {
            for (int col = 0; col < MATRIX_SIZE; col++) {
                sum += matrix[row][col];  // GOOD: sequential within a row — cache friendly
            }
        }
        return sum;
    }

    // -------------------------------------------------------------------------
    // Setup helpers
    // -------------------------------------------------------------------------

    static LinkedNode buildLinkedList(int size) {
        LinkedNode head = new LinkedNode(1);
        LinkedNode current = head;
        for (int i = 2; i <= size; i++) {
            current.next = new LinkedNode(i);
            current = current.next;
        }
        return head;
    }

    // Build the list, then shuffle node order in memory by storing references
    // out of order — simulates real heap fragmentation after GC moves.
    static LinkedNode buildScatteredLinkedList(int size) {
        // First build all nodes, store in array (allocated sequentially)
        LinkedNode[] nodes = new LinkedNode[size];
        for (int i = 0; i < size; i++) {
            nodes[i] = new LinkedNode(i + 1);
        }
        // Now wire them up in reverse order to break sequential allocation
        // The linked list traversal will jump backwards through memory
        for (int i = size - 1; i > 0; i--) {
            nodes[i].next = nodes[i - 1];
        }
        return nodes[size - 1]; // head is the last-allocated node
    }

    static int[][] buildMatrix() {
        int[][] matrix = new int[MATRIX_SIZE][MATRIX_SIZE];
        for (int r = 0; r < MATRIX_SIZE; r++) {
            for (int c = 0; c < MATRIX_SIZE; c++) {
                matrix[r][c] = r + c;
            }
        }
        return matrix;
    }

    public static void main(String[] args) {
        final int LIST_SIZE = 500_000;
        final int WARMUP_REPS = 3;
        final int BENCH_REPS = 5;

        System.out.println("=== LocalityDemo ===");
        System.out.println("Cache line size: 64 bytes = 16 ints = 8 longs");
        System.out.println();

        // ---- Example 1: Linked list vs array ----
        System.out.println("--- Example 1: Pointer chasing (LinkedList) vs Sequential (int[]) ---");
        System.out.printf("Data size: %,d elements%n%n", LIST_SIZE);

        int[] arr = new int[LIST_SIZE];
        for (int i = 0; i < LIST_SIZE; i++) arr[i] = i + 1;

        LinkedNode head = buildScatteredLinkedList(LIST_SIZE);

        // Warmup
        for (int i = 0; i < WARMUP_REPS; i++) {
            sumArray(arr);
            sumLinkedList(head);
        }

        // Bench array
        long t0 = System.nanoTime();
        long arraySum = 0;
        for (int r = 0; r < BENCH_REPS; r++) arraySum = sumArray(arr);
        long arrayTime = (System.nanoTime() - t0) / BENCH_REPS;

        // Bench linked list
        t0 = System.nanoTime();
        long listSum = 0;
        for (int r = 0; r < BENCH_REPS; r++) listSum = sumLinkedList(head);
        long listTime = (System.nanoTime() - t0) / BENCH_REPS;

        System.out.printf("BAD  (LinkedList, pointer chasing): %,8d ns  (sum=%d)%n", listTime, listSum);
        System.out.printf("GOOD (int[] array, sequential):     %,8d ns  (sum=%d)%n", arrayTime, arraySum);
        if (arrayTime > 0) {
            System.out.printf("Speedup: %.1fx%n", (double) listTime / arrayTime);
        }
        System.out.println();
        System.out.println("  Why? int[] is contiguous. One cache line load = 16 ints for free.");
        System.out.println("  LinkedList node.next = random pointer = cache miss per node (~200 cycles each).");

        // ---- Example 2: 2D array access patterns ----
        System.out.println();
        System.out.println("--- Example 2: 2D Array — Column-major (BAD) vs Row-major (GOOD) ---");
        System.out.printf("Matrix: %dx%d = %,d elements%n%n", MATRIX_SIZE, MATRIX_SIZE,
                (long) MATRIX_SIZE * MATRIX_SIZE);

        int[][] matrix = buildMatrix();

        // Warmup
        for (int i = 0; i < WARMUP_REPS; i++) {
            sumRowMajor(matrix);
            sumColumnMajor(matrix);
        }

        // Bench row-major
        t0 = System.nanoTime();
        long rowSum = 0;
        for (int r = 0; r < BENCH_REPS; r++) rowSum = sumRowMajor(matrix);
        long rowTime = (System.nanoTime() - t0) / BENCH_REPS;

        // Bench column-major
        t0 = System.nanoTime();
        long colSum = 0;
        for (int r = 0; r < BENCH_REPS; r++) colSum = sumColumnMajor(matrix);
        long colTime = (System.nanoTime() - t0) / BENCH_REPS;

        System.out.printf("BAD  (column-major traversal): %,8d ns  (sum=%d)%n", colTime, colSum);
        System.out.printf("GOOD (row-major traversal):    %,8d ns  (sum=%d)%n", rowTime, rowSum);
        if (rowTime > 0) {
            System.out.printf("Speedup: %.1fx%n", (double) colTime / rowTime);
        }
        System.out.println();
        System.out.println("  Why? Java stores matrix[row] as a contiguous int[].");
        System.out.println("  Row-by-row: matrix[row][col] and matrix[row][col+1] share a cache line.");
        System.out.println("  Col-by-col: matrix[row][col] and matrix[row+1][col] are in different");
        System.out.println("             row objects — likely different cache lines or cache sets.");
        System.out.println();
        System.out.println("Key insight for HFT: Structure your data so the hot loop");
        System.out.println("accesses memory sequentially. Use arrays, not linked structures.");
        System.out.println("Align your data access pattern with your storage layout.");
    }
}