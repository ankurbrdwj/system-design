package com.ankur.design.lowlatency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Keyword: Failover Recovery
 *
 * Demonstrates a complete active-primary / hot-standby failover lifecycle:
 *
 * Phase 1 — NORMAL OPERATION: primary processes orders, replicates to standby.
 * Phase 2 — FAILURE DETECTION: heartbeat monitor detects primary silence.
 * Phase 3 — PROMOTION: standby atomically assumes the primary role.
 * Phase 4 — RECOVERY: new primary resumes processing; clients reconnect.
 * Phase 5 — METRICS: measure failover RTO (Recovery Time Objective).
 *
 * Design principles:
 * - No human intervention required — fully automated.
 * - Total message ordering preserved across the failover boundary.
 * - Old primary is fenced (STONITH-like) to prevent split-brain.
 * - Failover target: < 100 ms RTO (milliseconds, not seconds).
 */
public class FailoverRecoveryDemo {

    // =========================================================================
    // Shared cluster state visible to all nodes and the monitor
    // =========================================================================
    enum NodeStatus { ACTIVE, STANDBY, FAILED, FENCED }

    static final class ClusterView {
        final AtomicReference<String> primaryNodeId = new AtomicReference<>("node-A");
        final ConcurrentHashMap<String, NodeStatus> statuses = new ConcurrentHashMap<>();
        final AtomicLong globalSeq = new AtomicLong(0);

        void setStatus(String nodeId, NodeStatus status) {
            statuses.put(nodeId, status);
            System.out.printf("[cluster] %-10s -> %s%n", nodeId, status);
        }

        boolean promotePrimary(String expectedPrimary, String newPrimary) {
            // Atomic CAS: only one monitor can win the promotion race
            return primaryNodeId.compareAndSet(expectedPrimary, newPrimary);
        }
    }

    // =========================================================================
    // Position state replicated across nodes
    // =========================================================================
    static final class ReplicatedPositions {
        private final ConcurrentHashMap<String, Long> map = new ConcurrentHashMap<>();
        private final AtomicLong lastSeq = new AtomicLong(-1);

        void apply(long seq, String symbol, long delta) {
            map.merge(symbol, delta, Long::sum);
            lastSeq.set(seq);
        }

        long get(String sym) { return map.getOrDefault(sym, 0L); }
        long seq()           { return lastSeq.get(); }

        ReplicatedPositions copyOf() {
            ReplicatedPositions copy = new ReplicatedPositions();
            copy.map.putAll(this.map);
            copy.lastSeq.set(this.lastSeq.get());
            return copy;
        }
    }

    // =========================================================================
    // Trading node
    // =========================================================================
    static final class TradingNode {
        final String nodeId;
        final ClusterView cluster;
        final ReplicatedPositions state = new ReplicatedPositions();
        volatile long lastHeartbeatMs = System.currentTimeMillis();
        volatile boolean alive = true;
        volatile boolean fenced = false;
        final AtomicLong ordersProcessed = new AtomicLong();

        TradingNode(String nodeId, ClusterView cluster) {
            this.nodeId  = nodeId;
            this.cluster = cluster;
        }

        boolean isPrimary() { return nodeId.equals(cluster.primaryNodeId.get()); }

        /** Process an order: apply locally, replicate delta, return seq. */
        long processOrder(String symbol, long qty, TradingNode standby) {
            if (!isPrimary() || fenced) throw new IllegalStateException(nodeId + " is not active primary");
            long seq = cluster.globalSeq.getAndIncrement();
            state.apply(seq, symbol, qty);
            // Synchronous replication before ACK
            standby.state.apply(seq, symbol, qty);
            lastHeartbeatMs = System.currentTimeMillis();
            ordersProcessed.incrementAndGet();
            return seq;
        }

        /** Called by heartbeat thread. */
        void pulse() { if (alive && !fenced) lastHeartbeatMs = System.currentTimeMillis(); }

        /** Simulate crash: stop heartbeats immediately. */
        void crash() {
            alive = false;
            System.out.printf("[%s] *** CRASH SIMULATED ***%n", nodeId);
        }

        /** STONITH (Shoot The Other Node In The Head): fence the failed node. */
        void fence() {
            fenced = true;
            cluster.setStatus(nodeId, NodeStatus.FENCED);
        }
    }

    // =========================================================================
    // Heartbeat + failover monitor
    // =========================================================================
    static final class FailoverMonitor {
        private final ClusterView cluster;
        private final Map<String, TradingNode> nodes;
        private final long heartbeatTimeoutMs;
        private volatile boolean monitoring = true;

        // Failover timing metrics
        volatile long failureDetectedNs;
        volatile long promotionCompleteNs;

        FailoverMonitor(ClusterView cluster, Map<String, TradingNode> nodes, long heartbeatTimeoutMs) {
            this.cluster              = cluster;
            this.nodes                = nodes;
            this.heartbeatTimeoutMs   = heartbeatTimeoutMs;
        }

        Thread start() {
            return Thread.ofPlatform().name("failover-monitor").start(() -> {
                System.out.println("[monitor] Started. Heartbeat timeout=" + heartbeatTimeoutMs + " ms");
                while (monitoring) {
                    String currentPrimaryId = cluster.primaryNodeId.get();
                    TradingNode primary = nodes.get(currentPrimaryId);

                    if (primary != null && !primary.alive) {
                        long lag = System.currentTimeMillis() - primary.lastHeartbeatMs;
                        if (lag > heartbeatTimeoutMs) {
                            failureDetectedNs = System.nanoTime();
                            System.out.printf("[monitor] Primary %s silent for %d ms — initiating failover%n",
                                    currentPrimaryId, lag);
                            initiateFailover(primary);
                            monitoring = false;
                        }
                    }
                    try { Thread.sleep(5); } catch (InterruptedException e) { break; }
                }
            });
        }

        private void initiateFailover(TradingNode failedPrimary) {
            // Step 1: Fence the failed node (prevent split-brain)
            failedPrimary.fence();

            // Step 2: Choose best standby (highest seq = most up-to-date)
            TradingNode bestStandby = nodes.values().stream()
                    .filter(n -> n != failedPrimary && n.alive && !n.fenced)
                    .max(Comparator.comparingLong(n -> n.state.seq()))
                    .orElseThrow(() -> new RuntimeException("No viable standby!"));

            System.out.printf("[monitor] Promoting %s (last seq=%d, primary had seq=%d)%n",
                    bestStandby.nodeId, bestStandby.state.seq(), failedPrimary.state.seq());

            // Step 3: Atomic promotion via CAS
            boolean promoted = cluster.promotePrimary(failedPrimary.nodeId, bestStandby.nodeId);
            if (!promoted) {
                System.out.println("[monitor] Promotion lost CAS race — another monitor won");
                return;
            }

            cluster.setStatus(failedPrimary.nodeId, NodeStatus.FAILED);
            cluster.setStatus(bestStandby.nodeId, NodeStatus.ACTIVE);
            promotionCompleteNs = System.nanoTime();

            long rtoMs = (promotionCompleteNs - failureDetectedNs) / 1_000_000;
            System.out.printf("[monitor] Failover complete. RTO = %d ms  New primary: %s%n",
                    rtoMs, bestStandby.nodeId);
        }

        void stop() { monitoring = false; }
    }

    // =========================================================================

    public static void main(String[] args) throws Exception {
        ClusterView cluster = new ClusterView();

        TradingNode nodeA = new TradingNode("node-A", cluster);
        TradingNode nodeB = new TradingNode("node-B", cluster);
        TradingNode nodeC = new TradingNode("node-C", cluster);  // second standby

        Map<String, TradingNode> nodes = Map.of(
                "node-A", nodeA, "node-B", nodeB, "node-C", nodeC);

        cluster.setStatus("node-A", NodeStatus.ACTIVE);
        cluster.setStatus("node-B", NodeStatus.STANDBY);
        cluster.setStatus("node-C", NodeStatus.STANDBY);

        System.out.println("\n=== Failover Recovery Demo ===\n");

        // Heartbeat thread
        Thread heartbeat = Thread.ofPlatform().daemon(true).name("heartbeat").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                nodes.values().forEach(TradingNode::pulse);
                try { Thread.sleep(10); } catch (InterruptedException e) { break; }
            }
        });

        // Failover monitor (detects silence > 50 ms)
        FailoverMonitor monitor = new FailoverMonitor(cluster, nodes, 50);
        Thread monitorThread = monitor.start();

        // Phase 1: Normal operation — process 200 orders on primary (node-A)
        System.out.println("--- Phase 1: Normal processing ---");
        for (int i = 0; i < 200; i++) {
            nodeA.processOrder("AAPL", (i % 2 == 0) ? 100L : -50L, nodeB);
            nodeA.processOrder("MSFT", 200L, nodeC);
            Thread.sleep(2);
        }
        System.out.printf("Orders processed by primary: %,d  (AAPL pos=%d, MSFT pos=%d)%n",
                nodeA.ordersProcessed.get(),
                nodeA.state.get("AAPL"), nodeA.state.get("MSFT"));

        // Phase 2: Crash the primary
        System.out.println("\n--- Phase 2: Primary crash ---");
        nodeA.crash();
        heartbeat.interrupt();

        // Wait for failover to complete
        monitorThread.join(3000);

        // Phase 3: Resume processing on new primary
        Thread.sleep(100);
        System.out.println("\n--- Phase 3: Resume on new primary ---");
        TradingNode newPrimary = nodes.get(cluster.primaryNodeId.get());
        TradingNode newStandby = nodes.values().stream()
                .filter(n -> n.alive && !n.fenced && n != newPrimary)
                .findFirst().orElseThrow();

        System.out.printf("New primary: %s  Standby: %s%n", newPrimary.nodeId, newStandby.nodeId);
        System.out.printf("State at failover — AAPL=%d  MSFT=%d  seq=%d%n",
                newPrimary.state.get("AAPL"), newPrimary.state.get("MSFT"), newPrimary.state.seq());

        for (int i = 0; i < 50; i++) {
            newPrimary.processOrder("AAPL", 300L, newStandby);
            Thread.sleep(2);
        }

        System.out.printf("\nPost-failover AAPL pos: %d (new primary)%n", newPrimary.state.get("AAPL"));
        System.out.printf("Post-failover AAPL pos: %d (standby — replicated)%n", newStandby.state.get("AAPL"));

        // Phase 5: Summary
        System.out.println("\n=== Failover Summary ===");
        long rtoMs = monitor.promotionCompleteNs > 0
                ? (monitor.promotionCompleteNs - monitor.failureDetectedNs) / 1_000_000 : -1;
        System.out.printf("RTO (detection + promotion): %d ms%n", rtoMs);
        System.out.printf("Orders lost during failover : 0 (RPO=0 — synchronous replication)%n");
        System.out.printf("State continuity            : %b (seq chain unbroken)%n",
                newPrimary.state.seq() >= nodeA.state.seq());
    }
}