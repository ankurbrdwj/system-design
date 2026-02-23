package com.ankur.design.lowlatency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Keyword: Data Gravity
 *
 * "Data gravity" means co-locating computation with the data rather than
 * shipping data across the network to a remote compute node.
 *
 * This demo contrasts two approaches:
 *
 *   REMOTE MODEL (anti-pattern): A central data store returns raw data to a
 *   caller who then computes on it. Each call crosses a simulated network hop.
 *
 *   CO-LOCATED MODEL (data gravity): A compute function is pushed INTO the
 *   data partition so that the result — not the raw data — travels over the
 *   wire. Reduces data movement dramatically.
 *
 * In production: Chronicle Map / Hazelcast IMap.executeOnKey() / Infinispan
 * compute-on-server all implement this principle.
 */
public class DataGravityDemo {

    // ---------------------------------------------------------------------------
    // Simulated market-data partition (represents one NUMA-local shard)
    // ---------------------------------------------------------------------------
    static class MarketDataPartition {
        private final String partitionId;
        private final Map<String, double[]> tickData = new ConcurrentHashMap<>();
        private final long simulatedNetworkLatencyNs;

        MarketDataPartition(String partitionId, long simulatedNetworkLatencyNs) {
            this.partitionId = partitionId;
            this.simulatedNetworkLatencyNs = simulatedNetworkLatencyNs;
        }

        void addTicks(String symbol, double[] prices) {
            tickData.put(symbol, prices);
        }

        /** REMOTE MODEL: returns raw tick array to caller (expensive data transfer). */
        double[] fetchRawTicks(String symbol) {
            simulateLatency(simulatedNetworkLatencyNs);
            return tickData.getOrDefault(symbol, new double[0]);
        }

        /**
         * DATA GRAVITY MODEL: caller pushes a compute function into the partition.
         * Only the scalar result travels back across the simulated wire.
         */
        <R> R computeOnData(String symbol, Function<double[], R> fn) {
            double[] ticks = tickData.getOrDefault(symbol, new double[0]);
            R result = fn.apply(ticks);           // compute happens WHERE the data lives
            simulateLatency(simulatedNetworkLatencyNs / 10); // only result sent back
            return result;
        }

        private void simulateLatency(long nanos) {
            long end = System.nanoTime() + nanos;
            while (System.nanoTime() < end) Thread.onSpinWait();
        }
    }

    // ---------------------------------------------------------------------------
    // Computations we want to run on tick data
    // ---------------------------------------------------------------------------
    static double vwap(double[] prices) {
        if (prices.length == 0) return 0;
        double sum = 0;
        for (double p : prices) sum += p;
        return sum / prices.length;
    }

    static double volatility(double[] prices) {
        if (prices.length < 2) return 0;
        double mean = vwap(prices);
        double variance = 0;
        for (double p : prices) variance += (p - mean) * (p - mean);
        return Math.sqrt(variance / (prices.length - 1));
    }

    // ---------------------------------------------------------------------------

    public static void main(String[] args) {
        // Seed partition with AAPL ticks
        MarketDataPartition partition = new MarketDataPartition("partition-0", 5_000); // 5µs simulated hop
        double[] ticks = new double[10_000];
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < ticks.length; i++) ticks[i] = 150.0 + rng.nextGaussian();
        partition.addTicks("AAPL", ticks);

        int rounds = 500;

        // -----------------------------------------------------------------------
        // APPROACH 1: Remote model — fetch data then compute locally
        // -----------------------------------------------------------------------
        long startRemote = System.nanoTime();
        double vwapRemote = 0, volRemote = 0;
        for (int i = 0; i < rounds; i++) {
            double[] raw = partition.fetchRawTicks("AAPL");   // full array crosses "network"
            vwapRemote = vwap(raw);
            volRemote  = volatility(raw);
        }
        long remoteNs = System.nanoTime() - startRemote;

        // -----------------------------------------------------------------------
        // APPROACH 2: Data gravity — push compute into partition
        // -----------------------------------------------------------------------
        long startLocal = System.nanoTime();
        double vwapLocal = 0, volLocal = 0;
        for (int i = 0; i < rounds; i++) {
            vwapLocal = partition.computeOnData("AAPL", DataGravityDemo::vwap);
            volLocal  = partition.computeOnData("AAPL", DataGravityDemo::volatility);
        }
        long localNs = System.nanoTime() - startLocal;

        System.out.println("=== Data Gravity Benchmark ===");
        System.out.printf("Remote model   : %6d ms  VWAP=%.4f  Vol=%.4f%n",
                remoteNs / 1_000_000, vwapRemote, volRemote);
        System.out.printf("Co-located     : %6d ms  VWAP=%.4f  Vol=%.4f%n",
                localNs / 1_000_000, vwapLocal, volLocal);
        System.out.printf("Speed-up       : %.1fx%n", (double) remoteNs / localNs);
        System.out.println();
        System.out.println("Key insight: only a scalar result (8 bytes) travelled in the co-located model");
        System.out.printf("vs %d bytes of raw tick data in the remote model.%n", ticks.length * 8);
    }
}