package com.ankur.design.hft.parallelism;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * CLAIM: Divide-and-conquer with fork/join is the right parallel decomposition pattern.
 *        Split until small enough to solve sequentially, then combine results.
 *
 * This is exactly what parallel streams do internally.
 * Writing it explicitly shows the mechanics.
 *
 * Tree of tasks for N=8, threshold=2:
 *
 *              sum(0..7)
 *             /          \
 *        sum(0..3)      sum(4..7)      ← forked in parallel
 *        /      \        /      \
 *    sum(0..1) sum(2..3) sum(4..5) sum(6..7)   ← solved sequentially
 *
 * Combine phase walks back up the tree adding partial results.
 */
public class P6_ForkJoinDivideConquer {

    static final int SEQUENTIAL_THRESHOLD = 10_000;

    static class SumTask extends RecursiveTask<Long> {
        private final long[] data;
        private final int    from, to;

        SumTask(long[] data, int from, int to) {
            this.data = data;
            this.from = from;
            this.to   = to;
        }

        @Override
        protected Long compute() {
            int size = to - from;

            // base case — small enough, solve sequentially
            if (size <= SEQUENTIAL_THRESHOLD) {
                long sum = 0;
                for (int i = from; i < to; i++) sum += data[i];
                return sum;
            }

            // divide: split at midpoint, fork left half
            int mid = from + size / 2;
            SumTask left  = new SumTask(data, from, mid);
            SumTask right = new SumTask(data, mid,  to);

            left.fork();                    // submit left to fork/join pool
            long rightResult = right.compute();  // compute right on current thread
            long leftResult  = left.join();      // wait for left

            return leftResult + rightResult;     // combine
        }
    }

    public static void main(String[] args) {
        int N = 10_000_000;
        long[] data = new long[N];
        for (int i = 0; i < N; i++) data[i] = i + 1;

        long expected = (long) N * (N + 1) / 2;

        // warm up
        ForkJoinPool pool = ForkJoinPool.commonPool();
        pool.invoke(new SumTask(data, 0, N));

        long t0 = System.nanoTime();
        long seqSum = 0;
        for (long v : data) seqSum += v;
        long seqMs = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        long forkSum = pool.invoke(new SumTask(data, 0, N));
        long forkMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("Expected         = %d%n", expected);
        System.out.printf("Sequential sum   = %d  time=%d ms%n", seqSum,  seqMs);
        System.out.printf("ForkJoin sum     = %d  time=%d ms%n", forkSum, forkMs);
        System.out.printf("Speedup          = %.1fx  (cores=%d)%n",
                (double) seqMs / Math.max(forkMs, 1),
                Runtime.getRuntime().availableProcessors());
    }
}