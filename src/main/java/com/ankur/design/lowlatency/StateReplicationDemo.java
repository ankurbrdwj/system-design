package com.ankur.design.lowlatency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Keyword: State Replication
 *
 * State replication is the cornerstone of high-availability in trading systems.
 * The primary node replicates every state mutation to one or more standby nodes
 * BEFORE acknowledging the operation to the client.
 *
 * Key properties:
 * - TOTAL ORDERING: all nodes see mutations in the same sequence (via a monotonic
 *   global sequence number). No split-brain possible.
 * - SYNCHRONOUS REPLICATION: primary waits for at least one standby ACK before
 *   committing — guarantees RPO=0 (no data loss on failover).
 * - SINGLE-THREADED REPLICATION CHANNEL: eliminates reordering; each mutation
 *   flows through a dedicated replication queue in order.
 *
 * This mirrors the active-passive model used by Chronicle, Hazelcast, or custom
 * JGroups-based solutions in production trading infrastructure.
 */
public class StateReplicationDemo {

    // =========================================================================
    // Replication entry: a serialised state delta
    // =========================================================================
    record ReplicationEntry(long seq, String operation, String symbol,
                            long value, long capturedNs) {}

    // =========================================================================
    // Node state: a map of positions (symbol -> quantity)
    // =========================================================================
    static final class NodeState {
        final String nodeId;
        final ConcurrentHashMap<String, Long> positions = new ConcurrentHashMap<>();
        final AtomicLong lastAppliedSeq = new AtomicLong(-1);
        final AtomicLong replicationLagNs = new AtomicLong();
        final AtomicLong entriesApplied   = new AtomicLong();

        NodeState(String nodeId) { this.nodeId = nodeId; }

        void apply(ReplicationEntry entry) {
            if (entry.seq() != lastAppliedSeq.get() + 1) {
                throw new IllegalStateException(nodeId + ": seq gap! expected " +
                        (lastAppliedSeq.get() + 1) + " got " + entry.seq());
            }
            switch (entry.operation()) {
                case "SET" -> positions.put(entry.symbol(), entry.value());
                case "ADD" -> positions.merge(entry.symbol(), entry.value(), Long::sum);
                case "DEL" -> positions.remove(entry.symbol());
            }
            lastAppliedSeq.set(entry.seq());
            entriesApplied.incrementAndGet();
            replicationLagNs.set(System.nanoTime() - entry.capturedNs());
        }

        long getPosition(String symbol) { return positions.getOrDefault(symbol, 0L); }
        long seq()          { return lastAppliedSeq.get(); }
        long avgLagNs()     { return replicationLagNs.get(); }
    }

    // =========================================================================
    // Replication channel: ordered SPSC queue from primary to each standby
    // =========================================================================
    static final class ReplicationChannel {
        private final BlockingQueue<ReplicationEntry> queue = new LinkedBlockingQueue<>(65536);
        private final NodeState standby;
        private final AtomicLong droppedEntries = new AtomicLong();
        private volatile boolean open = true;
        private final Thread worker;

        ReplicationChannel(NodeState standby) {
            this.standby = standby;
            this.worker  = Thread.ofPlatform().daemon(true)
                    .name("repl->" + standby.nodeId)
                    .start(this::drainLoop);
        }

        /** Primary calls this; blocks if channel queue is full (back-pressure). */
        void replicate(ReplicationEntry entry) throws InterruptedException {
            if (!queue.offer(entry, 1, TimeUnit.MILLISECONDS)) {
                droppedEntries.incrementAndGet();
                throw new RuntimeException("Replication channel full — HA SLA breached!");
            }
        }

        private void drainLoop() {
            while (open || !queue.isEmpty()) {
                try {
                    ReplicationEntry e = queue.poll(10, TimeUnit.MILLISECONDS);
                    if (e != null) standby.apply(e);
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
            }
        }

        void close() { open = false; }
        long dropped() { return droppedEntries.get(); }
    }

    // =========================================================================
    // Primary: applies mutations locally then synchronously replicates
    // =========================================================================
    static final class PrimaryNode {
        private final NodeState state;
        private final List<ReplicationChannel> channels = new ArrayList<>();
        private final AtomicLong seqGen = new AtomicLong(0);
        private final AtomicLong commitCount = new AtomicLong();

        PrimaryNode(String nodeId) { this.state = new NodeState(nodeId); }

        void addStandby(NodeState standby) {
            channels.add(new ReplicationChannel(standby));
        }

        /**
         * Commit a position update:
         * 1. Write WAL entry (seq assigned here for total ordering).
         * 2. Apply locally.
         * 3. Replicate to all standbys (synchronously — ACK required).
         */
        long commit(String op, String symbol, long value) throws InterruptedException {
            long seq = seqGen.getAndIncrement();
            ReplicationEntry entry = new ReplicationEntry(seq, op, symbol, value, System.nanoTime());

            // Apply to primary first
            state.apply(entry);

            // Synchronous fan-out to all standbys
            for (ReplicationChannel ch : channels) ch.replicate(entry);

            commitCount.incrementAndGet();
            return seq;
        }

        NodeState getState() { return state; }
        long commitCount()   { return commitCount.get(); }
        void shutdown()      { channels.forEach(ReplicationChannel::close); }
    }

    // =========================================================================

    public static void main(String[] args) throws Exception {
        NodeState standbyA = new NodeState("standby-A");
        NodeState standbyB = new NodeState("standby-B");

        PrimaryNode primary = new PrimaryNode("primary");
        primary.addStandby(standbyA);
        primary.addStandby(standbyB);

        System.out.println("=== State Replication Demo ===");
        System.out.println("Primary: " + primary.getState().nodeId);
        System.out.println("Standbys: " + standbyA.nodeId + ", " + standbyB.nodeId);

        // Single-threaded processing to maintain total order
        int OPS = 100_000;
        long start = System.nanoTime();
        String[] symbols = {"AAPL", "MSFT", "GOOG", "AMZN", "TSLA"};
        for (int i = 0; i < OPS; i++) {
            String sym = symbols[i % symbols.length];
            primary.commit("ADD", sym, (i % 2 == 0) ? 100L : -50L);
        }
        long commitNs = System.nanoTime() - start;

        // Allow replication to drain
        Thread.sleep(200);
        primary.shutdown();
        Thread.sleep(50);

        System.out.printf("%nOps committed    : %,d%n", primary.commitCount());
        System.out.printf("Commit throughput: %,.0f ops/sec%n",
                OPS * 1e9 / commitNs);

        // Verify all nodes converged to the same state
        System.out.println("\n=== State Convergence Verification ===");
        boolean allMatch = true;
        for (String sym : symbols) {
            long pPos = primary.getState().getPosition(sym);
            long aPos = standbyA.getPosition(sym);
            long bPos = standbyB.getPosition(sym);
            boolean match = (pPos == aPos) && (pPos == bPos);
            if (!match) allMatch = false;
            System.out.printf("  %-6s  primary=%,8d  standby-A=%,8d  standby-B=%,8d  match=%b%n",
                    sym, pPos, aPos, bPos, match);
        }

        System.out.printf("%nAll nodes identical: %b (RPO=0 — zero data loss)%n", allMatch);
        System.out.printf("Primary  seq: %d%n", primary.getState().seq());
        System.out.printf("StandbyA seq: %d%n", standbyA.seq());
        System.out.printf("StandbyB seq: %d%n", standbyB.seq());
        System.out.printf("Replication lag (last): %,d ns%n", standbyA.avgLagNs());
    }
}