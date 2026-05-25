package com.ankur.design.hft.tuning;

import java.util.Arrays;
import java.util.Random;

/**
 * TOPIC: Performance measurement — averages are misleading. Use percentiles.
 *
 * The MEAN (average) is the wrong metric for latency-sensitive systems because:
 *   - It is sensitive to outliers (one 50ms GC pause inflates the average)
 *   - It hides the DISTRIBUTION of latencies
 *   - A user experiencing p99 latency doesn't care that the average is fast
 *
 * In HFT and financial systems, SLAs are expressed as PERCENTILES:
 *   p50  (median): 50% of requests complete within this time
 *   p95:           95% of requests complete within this time
 *   p99:           99% of requests complete within this time
 *   p99.9:         99.9% of requests complete within this time
 *
 * Example problem: 99,900 requests take 150ns, 100 requests (GC pauses) take 50ms.
 *   Average = (99900 * 150ns + 100 * 50ms) / 100000 = ~50us (looks OK!)
 *   p99     = 50ms (catastrophic for HFT!)
 *
 * HISTOGRAM: bucketing latencies into ranges shows the distribution shape.
 * This instantly reveals bimodal distributions (fast path + GC spikes).
 *
 * Tools used in production: HdrHistogram, JMH, async-profiler.
 * This demo uses only JDK arrays to show the concept.
 */
public class MeasurementDemo {

    // -------------------------------------------------------------------------
    // Simulate a system with mostly fast responses + occasional GC pauses
    // -------------------------------------------------------------------------

    // Simulates a "handler" that usually takes 100-200ns but occasionally takes 50ms (GC).
    // In a real system: this could be a market data parser, order acknowledgement handler, etc.
    static long[] simulateLatencies(int sampleCount, int gcPauseEveryN, long gcPauseNs) {
        Random rnd = new Random(42);
        long[] latencies = new long[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            if (i % gcPauseEveryN == 0 && i > 0) {
                // Simulated GC pause — rare but catastrophic
                latencies[i] = gcPauseNs;
            } else {
                // Normal fast response: 100-200ns (simulated with rnd, not actual timing)
                latencies[i] = 100 + rnd.nextInt(100); // 100-199 ns
            }
        }
        return latencies;
    }

    // -------------------------------------------------------------------------
    // BAD: Report only the mean
    // -------------------------------------------------------------------------

    // BAD: The average looks fine but completely hides the GC spike distribution.
    // A single 50ms pause among 1000 fast requests inflates the average by 50us
    // but the p99 reveals the real story.
    static void badMeasurement(long[] latencies) {
        System.out.println("--- BAD: Report only the average ---");

        long sum = 0;
        for (long l : latencies) sum += l;
        double average = (double) sum / latencies.length;

        // BAD: reporting only average — hides the tail latency problem completely
        System.out.printf("  Sample count: %,d%n", latencies.length);
        System.out.printf("  Average latency: %.1f ns  (%.3f ms)%n", average, average / 1_000_000);
        System.out.println("  Looks great! ... but is it?");
        System.out.println();
        System.out.println("  Problem: the average is skewed by GC pauses but still 'looks OK'.");
        System.out.println("  A trader experiencing the p99 tail doesn't care about the average.");
    }

    // -------------------------------------------------------------------------
    // GOOD: Compute and report percentiles
    // -------------------------------------------------------------------------

    // GOOD: Sort the latency array and compute percentile values.
    // Percentile p of N values = value at index (p/100 * N).
    static void goodMeasurement(long[] latencies) {
        System.out.println("--- GOOD: Report percentiles ---");

        long[] sorted = Arrays.copyOf(latencies, latencies.length);
        Arrays.sort(sorted); // O(N log N) — done once offline, not on hot path

        int n = sorted.length;
        long sum = 0;
        for (long l : sorted) sum += l;
        double average = (double) sum / n;

        long p50  = sorted[(int) (n * 0.50)];
        long p75  = sorted[(int) (n * 0.75)];
        long p90  = sorted[(int) (n * 0.90)];
        long p95  = sorted[(int) (n * 0.95)];
        long p99  = sorted[(int) (n * 0.99)];
        long p999 = sorted[(int) (n * 0.999)];
        long min  = sorted[0];
        long max  = sorted[n - 1];

        System.out.printf("  Sample count: %,d%n", n);
        System.out.printf("  Min:   %,8d ns%n", min);
        System.out.printf("  p50:   %,8d ns  (50%% of requests faster than this)%n", p50);
        System.out.printf("  p75:   %,8d ns%n", p75);
        System.out.printf("  p90:   %,8d ns%n", p90);
        System.out.printf("  p95:   %,8d ns%n", p95);
        System.out.printf("  p99:   %,8d ns  ← tail latency — THIS is what matters in HFT%n", p99);
        System.out.printf("  p99.9: %,8d ns%n", p999);
        System.out.printf("  Max:   %,8d ns%n", max);
        System.out.printf("  Mean:  %,8.1f ns  ← average looks fine but hides the p99 problem%n", average);
        System.out.println();
        System.out.printf("  p99 / p50 ratio: %.0fx  ← this shows the problem the average hides%n",
                (double) p99 / p50);
    }

    // -------------------------------------------------------------------------
    // GOOD: Print a simple ASCII histogram
    // -------------------------------------------------------------------------

    // GOOD: A histogram immediately shows the bimodal distribution:
    //   [ fast cluster at 100-200ns ] ... [ sparse GC spike at 50ms ]
    // This is impossible to see from an average alone.
    static void printHistogram(long[] latencies) {
        System.out.println("--- GOOD: Histogram (log-scale buckets) ---");

        // Buckets: <200ns, <500ns, <1us, <10us, <100us, <1ms, <10ms, <100ms, >=100ms
        String[] labels = {
            "<200ns  ", "<500ns  ", "<1us    ", "<10us   ",
            "<100us  ", "<1ms    ", "<10ms   ", "<100ms  ", ">=100ms "
        };
        long[] bounds = {200, 500, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000, Long.MAX_VALUE};
        int[] counts = new int[bounds.length];

        for (long l : latencies) {
            for (int b = 0; b < bounds.length; b++) {
                if (l < bounds[b]) {
                    counts[b]++;
                    break;
                }
            }
        }

        int total = latencies.length;
        int maxCount = 0;
        for (int c : counts) maxCount = Math.max(maxCount, c);

        System.out.println();
        for (int b = 0; b < bounds.length; b++) {
            if (counts[b] == 0) continue;
            double pct = 100.0 * counts[b] / total;
            int barLen = maxCount > 0 ? (int) (50.0 * counts[b] / maxCount) : 0;
            String bar = "#".repeat(Math.max(1, barLen));
            System.out.printf("  %s [%s%s] %,6d (%.2f%%)%n",
                    labels[b], bar, " ".repeat(50 - bar.length()), counts[b], pct);
        }
        System.out.println();
        System.out.println("  The histogram reveals the BIMODAL distribution:");
        System.out.println("  Fast cluster (100-200ns) + rare GC spikes (50ms).");
        System.out.println("  The average sits between the two peaks — represents NEITHER.");
    }

    // -------------------------------------------------------------------------
    // Demonstrate how GC pauses skew the average
    // -------------------------------------------------------------------------

    static void showAverageSkew() {
        System.out.println("--- Analytical: How 1 GC pause skews the average ---");
        System.out.println();

        int fastCount = 999;
        long fastNs = 150;       // 150ns typical
        long gcPauseNs = 50_000_000; // 50ms GC pause

        long totalNs = fastCount * fastNs + gcPauseNs;
        int totalCount = fastCount + 1;
        double avgNs = (double) totalNs / totalCount;

        System.out.printf("  %,d fast requests at %dns each + 1 GC pause at %,dns%n",
                fastCount, fastNs, gcPauseNs);
        System.out.printf("  Average = (%,d * %dns + %,dns) / %,d = %.0f ns = %.2f ms%n",
                fastCount, fastNs, gcPauseNs, totalCount, avgNs, avgNs / 1_000_000);
        System.out.printf("  p99.9 = %,d ns = %.0f ms  ← 333,000x worse than average!%n",
                gcPauseNs, (double) gcPauseNs / 1_000_000);
        System.out.println();
        System.out.printf("  A %.2fms average 'looks acceptable' to someone unfamiliar with HFT.%n",
                avgNs / 1_000_000);
        System.out.printf("  But p99.9 = 50ms means 1 in 1000 messages is catastrophically delayed.%n");
        System.out.println("  In FX trading: at 1000 trades/sec, that's 1 bad trade per second.");
        System.out.println();
    }

    public static void main(String[] args) {
        final int SAMPLE_COUNT = 100_000;
        final int GC_EVERY_N = 1_000;       // simulate GC pause every 1000 requests
        final long GC_PAUSE_NS = 50_000_000; // 50ms GC pause

        System.out.println("=== MeasurementDemo ===");
        System.out.println();
        System.out.printf("Simulating %,d requests: %dns normal, 50ms GC pause every %,d requests%n%n",
                SAMPLE_COUNT, 150, GC_EVERY_N);

        showAverageSkew();

        long[] latencies = simulateLatencies(SAMPLE_COUNT, GC_EVERY_N, GC_PAUSE_NS);

        badMeasurement(latencies);
        goodMeasurement(latencies);
        printHistogram(latencies);

        System.out.println("Key rules for performance measurement:");
        System.out.println("  1. NEVER report only the mean. Always report p50, p95, p99, p99.9.");
        System.out.println("  2. Plot a histogram to understand the distribution shape.");
        System.out.println("  3. Bimodal distribution = two distinct code paths (fast + slow).");
        System.out.println("  4. Use HdrHistogram in production — atomic, lock-free, low overhead.");
        System.out.println("  5. Measure with warmup: first N iterations are JIT compilation, not real latency.");
        System.out.println("  6. Measure the MAX too — it tells you the worst-case your users saw.");
    }
}