package com.ankur.design.lowlatency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keyword: NUMA Node Optimization
 *
 * NUMA (Non-Uniform Memory Access): in multi-socket servers, accessing memory
 * attached to a remote CPU socket is 2-3x slower than local (NUMA-local) access.
 *
 * Optimisation strategy:
 * - Partition the workload by NUMA node: each node owns a disjoint set of symbols.
 * - Each NUMA-local thread pool only touches its own partition (no cross-node traffic).
 * - State that is read-shared is replicated once per NUMA node (not centralised).
 *
 * Java does not expose NUMA topology directly; in production:
 *   - JVM flag: -XX:+UseNUMA enables NUMA-aware heap allocation.
 *   - OS affinity: taskset / numactl pins threads to specific cores.
 *   - Libraries: Chronicle Map supports NUMA-aware off-heap allocation.
 *
 * This demo simulates the logical partitioning benefit with thread pools
 * that represent NUMA nodes and measures cross-node vs local latency.
 */
public class NumaNodeOptimizationDemo {

    static final int NUMA_NODES = 2;   // simulate a 2-socket server
    static final int CORES_PER_NODE = 4;

    // -------------------------------------------------------------------------
    // Simulated NUMA-local state: each node has its own data partition
    // -------------------------------------------------------------------------
    static final class NumaNode {
        final int nodeId;
        // NUMA-local order book: partitioned by symbol group
        final Map<String, Long> localPositions = new ConcurrentHashMap<>();
        // Thread pool pinned to this NUMA node
        final ExecutorService executor;
        final AtomicLong localOps  = new AtomicLong();
        final AtomicLong remoteOps = new AtomicLong();

        NumaNode(int nodeId) {
            this.nodeId   = nodeId;
            this.executor = Executors.newFixedThreadPool(CORES_PER_NODE,
                    r -> {
                        Thread t = new Thread(r, "numa-" + nodeId + "-worker");
                        t.setDaemon(true);
                        return t;
                    });
        }

        /** Local access: thread runs on this node, data lives on this node. */
        long localRead(String symbol) {
            localOps.incrementAndGet();
            // Simulate NUMA-local memory latency (~100 ns)
            busySpin(100);
            return localPositions.getOrDefault(symbol, 0L);
        }

        /** Remote access: thread on this node reads data from another node. */
        long remoteRead(NumaNode remoteNode, String symbol) {
            remoteOps.incrementAndGet();
            // Simulate NUMA-remote memory latency (~300 ns — 3x local)
            busySpin(300);
            return remoteNode.localPositions.getOrDefault(symbol, 0L);
        }

        void update(String symbol, long delta) {
            localPositions.merge(symbol, delta, Long::sum);
        }

        void shutdown() { executor.shutdownNow(); }

        private void busySpin(long nanos) {
            long end = System.nanoTime() + nanos;
            while (System.nanoTime() < end) Thread.onSpinWait();
        }
    }

    // -------------------------------------------------------------------------
    // Partitioner: assigns each symbol to a NUMA node
    // -------------------------------------------------------------------------
    static class SymbolPartitioner {
        private final NumaNode[] nodes;

        SymbolPartitioner(NumaNode[] nodes) { this.nodes = nodes; }

        NumaNode nodeFor(String symbol) {
            // Consistent hash: same symbol always maps to the same node
            int hash = Math.abs(symbol.hashCode()) % nodes.length;
            return nodes[hash];
        }
    }

    // -------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        NumaNode[] nodes = new NumaNode[NUMA_NODES];
        for (int i = 0; i < NUMA_NODES; i++) nodes[i] = new NumaNode(i);

        SymbolPartitioner partitioner = new SymbolPartitioner(nodes);

        // Seed symbols into their NUMA-local partitions
        String[] symbols = {"AAPL", "MSFT", "GOOG", "AMZN", "TSLA",
                             "META", "NVDA", "NFLX", "BABA", "INTC"};
        for (String sym : symbols) {
            partitioner.nodeFor(sym).update(sym, 1000L);
        }

        System.out.println("=== Symbol → NUMA Node Assignment ===");
        for (String sym : symbols) {
            System.out.printf("  %-6s -> numa-node-%d%n", sym, partitioner.nodeFor(sym).nodeId);
        }

        int OPS = 10_000;
        List<Future<Long>> futures = new ArrayList<>();

        // ---- SCENARIO A: NUMA-AWARE (local reads only) ----
        long startAware = System.nanoTime();
        for (int i = 0; i < OPS; i++) {
            String sym = symbols[i % symbols.length];
            NumaNode owner = partitioner.nodeFor(sym);
            // Submit work to the owning node's executor (simulates CPU affinity)
            futures.add(owner.executor.submit(() -> owner.localRead(sym)));
        }
        for (Future<Long> f : futures) f.get();
        long awareNs = System.nanoTime() - startAware;
        futures.clear();

        // ---- SCENARIO B: NUMA-UNAWARE (random cross-node reads) ----
        Random rng = new Random(42);
        long startUnaware = System.nanoTime();
        for (int i = 0; i < OPS; i++) {
            String sym = symbols[i % symbols.length];
            NumaNode owner   = partitioner.nodeFor(sym);
            // Deliberately run on the OTHER node (simulates unaware placement)
            NumaNode executor = nodes[(owner.nodeId + 1) % NUMA_NODES];
            futures.add(executor.executor.submit(() -> executor.remoteRead(owner, sym)));
        }
        for (Future<Long> f : futures) f.get();
        long unawareNs = System.nanoTime() - startUnaware;

        System.out.println("\n=== NUMA Optimization Results ===");
        System.out.printf("NUMA-aware   (local reads)  : %,8d ms%n", awareNs / 1_000_000);
        System.out.printf("NUMA-unaware (remote reads) : %,8d ms%n", unawareNs / 1_000_000);
        System.out.printf("Speed-up from NUMA affinity : %.1fx%n", (double) unawareNs / awareNs);
        System.out.println();
        System.out.println("Node stats:");
        for (NumaNode n : nodes) {
            System.out.printf("  numa-node-%d  local ops=%,d  remote ops=%,d%n",
                    n.nodeId, n.localOps.get(), n.remoteOps.get());
            n.shutdown();
        }
    }
}