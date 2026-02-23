package com.ankur.design.lowlatency;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Keyword: High Availability (HA)
 *
 * Demonstrates an active-primary / hot-standby failover model:
 *
 * - Primary node processes orders and synchronously replicates state to standby.
 * - A heartbeat monitor detects primary failure within milliseconds.
 * - Standby promotes itself to primary automatically.
 * - Failover preserves all committed state — no message loss.
 *
 * Key design decisions:
 * - State is replicated BEFORE the primary acknowledges the order.
 * - Promotion is atomic (CAS on a shared role reference).
 * - Total ordering of messages is maintained via a global sequence counter.
 */
public class HighAvailabilityDemo {

    enum NodeRole { PRIMARY, STANDBY, FAILED }

    // Replicated state: a simple position map (symbol -> net position)
    static final class ReplicatedState {
        private final ConcurrentHashMap<String, Long> positions = new ConcurrentHashMap<>();
        private final AtomicLong lastAppliedSeq = new AtomicLong(-1);

        /** Apply a position delta; must be called in sequence order. */
        void applyDelta(long seq, String symbol, long delta) {
            if (seq != lastAppliedSeq.get() + 1)
                throw new IllegalStateException("Sequence gap! expected " + (lastAppliedSeq.get() + 1) + " got " + seq);
            positions.merge(symbol, delta, Long::sum);
            lastAppliedSeq.set(seq);
        }

        long getPosition(String symbol) { return positions.getOrDefault(symbol, 0L); }
        long lastSeq() { return lastAppliedSeq.get(); }

        ReplicatedState snapshot() {
            ReplicatedState copy = new ReplicatedState();
            copy.positions.putAll(this.positions);
            copy.lastAppliedSeq.set(this.lastAppliedSeq.get());
            return copy;
        }
    }

    // -------------------------------------------------------------------------
    static class TradingNode {
        final String nodeId;
        volatile NodeRole role;
        final ReplicatedState state = new ReplicatedState();
        volatile boolean alive = true;
        volatile long lastHeartbeatMs = System.currentTimeMillis();

        TradingNode(String nodeId, NodeRole initialRole) {
            this.nodeId = nodeId;
            this.role   = initialRole;
        }

        void heartbeat() { lastHeartbeatMs = System.currentTimeMillis(); }
        boolean isHealthy(long timeoutMs) { return alive && (System.currentTimeMillis() - lastHeartbeatMs) < timeoutMs; }
        void fail() { alive = false; role = NodeRole.FAILED; System.out.println("[" + nodeId + "] SIMULATED CRASH"); }
    }

    // -------------------------------------------------------------------------
    static class HACluster {
        private final TradingNode primary;
        private final TradingNode standby;
        private final AtomicLong globalSeq = new AtomicLong(0);
        private final AtomicReference<TradingNode> activePrimary;
        private volatile boolean monitorRunning = true;

        HACluster(TradingNode primary, TradingNode standby) {
            this.primary = primary;
            this.standby = standby;
            this.activePrimary = new AtomicReference<>(primary);
        }

        /**
         * Process an order: apply to primary state, replicate to standby,
         * then acknowledge. Returns the assigned sequence number.
         */
        long processOrder(String symbol, long quantity) {
            TradingNode p = activePrimary.get();
            if (p == null || p.role != NodeRole.PRIMARY) {
                throw new IllegalStateException("No primary available");
            }

            long seq = globalSeq.getAndIncrement();
            // 1. Apply to primary
            p.state.applyDelta(seq, symbol, quantity);
            // 2. Synchronously replicate to standby BEFORE acknowledging
            if (standby.role == NodeRole.STANDBY) {
                standby.state.applyDelta(seq, symbol, quantity);
            }
            // 3. Acknowledge to client (order confirmed)
            return seq;
        }

        /** Heartbeat sender — runs on the primary. */
        Thread startHeartbeat() {
            return Thread.ofPlatform().daemon(true).name("heartbeat").start(() -> {
                while (monitorRunning) {
                    TradingNode p = activePrimary.get();
                    if (p != null && p.alive) p.heartbeat();
                    try { Thread.sleep(10); } catch (InterruptedException e) { break; }
                }
            });
        }

        /** Failure monitor — promotes standby if primary heartbeat times out. */
        Thread startMonitor(long timeoutMs) {
            return Thread.ofPlatform().name("ha-monitor").start(() -> {
                while (monitorRunning) {
                    TradingNode p = activePrimary.get();
                    if (p != null && !p.isHealthy(timeoutMs)) {
                        long failoverStart = System.nanoTime();
                        // CAS promotion: only one monitor wins the race
                        if (activePrimary.compareAndSet(p, standby)) {
                            standby.role = NodeRole.PRIMARY;
                            long failoverMs = (System.nanoTime() - failoverStart) / 1_000_000;
                            System.out.printf("[ha-monitor] PRIMARY %s FAILED — promoted %s in %d ms%n",
                                    p.nodeId, standby.nodeId, failoverMs);
                            System.out.printf("[ha-monitor] Standby last seq: %d (no data loss)%n",
                                    standby.state.lastSeq());
                        }
                    }
                    try { Thread.sleep(5); } catch (InterruptedException e) { break; }
                }
            });
        }

        void stop() { monitorRunning = false; }
    }

    // -------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        TradingNode primary = new TradingNode("node-A", NodeRole.PRIMARY);
        TradingNode standby = new TradingNode("node-B", NodeRole.STANDBY);
        HACluster cluster   = new HACluster(primary, standby);

        cluster.startHeartbeat();
        cluster.startMonitor(100); // failover if heartbeat silent > 100 ms

        System.out.println("=== HA Cluster Started: primary=" + primary.nodeId + " standby=" + standby.nodeId);

        // Process 50 orders on the primary
        for (int i = 0; i < 50; i++) {
            long seq = cluster.processOrder("AAPL", (i % 2 == 0) ? 100 : -50);
            Thread.sleep(5);
        }

        System.out.printf("After 50 orders — primary AAPL position: %d (seq=%d)%n",
                primary.state.getPosition("AAPL"), primary.state.lastSeq());
        System.out.printf("Standby mirrors   AAPL position: %d (seq=%d)%n",
                standby.state.getPosition("AAPL"), standby.state.lastSeq());

        // Simulate primary crash
        System.out.println("\n--- Simulating primary crash ---");
        primary.fail();
        Thread.sleep(300); // let monitor detect and promote standby

        // Process orders on new primary (promoted standby)
        System.out.println("\n--- Processing orders on new primary ---");
        for (int i = 0; i < 10; i++) {
            long seq = cluster.processOrder("AAPL", 200);
            Thread.sleep(5);
        }

        System.out.printf("%nFinal AAPL position on new primary: %d%n",
                standby.state.getPosition("AAPL"));

        cluster.stop();
    }
}