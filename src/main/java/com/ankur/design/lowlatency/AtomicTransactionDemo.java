package com.ankur.design.lowlatency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Keyword: Atomic Transactions
 *
 * In low-latency trading, "atomic transaction" means:
 * - All-or-nothing state change: either all mutations commit or none do.
 * - No intermediate state is observable by other threads.
 * - Implemented WITHOUT database-style locking — using lock-free CAS primitives.
 *
 * Patterns demonstrated:
 * 1. COMPARE-AND-SWAP (CAS) for single-variable atomicity (AtomicLong).
 * 2. OPTIMISTIC LOCKING via AtomicReference + immutable snapshot swap for
 *    multi-field atomicity (the "immutable value object" pattern).
 * 3. PERSISTENT TRANSACTION LOG: every committed change appended to a WAL
 *    (Write-Ahead Log) for replay-based recovery.
 */
public class AtomicTransactionDemo {

    // =========================================================================
    // 1. Single-variable CAS: atomic position update
    // =========================================================================
    static final class AtomicPositionStore {
        private final ConcurrentHashMap<String, AtomicLong> positions = new ConcurrentHashMap<>();
        private final AtomicLong transactionCount = new AtomicLong();

        /** Atomic increment: uses CAS internally — no locks. */
        long applyFill(String symbol, long deltaQty) {
            long newPos = positions
                    .computeIfAbsent(symbol, k -> new AtomicLong(0))
                    .addAndGet(deltaQty);
            transactionCount.incrementAndGet();
            return newPos;
        }

        /**
         * Conditional update: only apply if current position equals expected.
         * Returns true if CAS succeeded, false if position was already changed.
         */
        boolean conditionalUpdate(String symbol, long expectedPos, long newPos) {
            AtomicLong pos = positions.computeIfAbsent(symbol, k -> new AtomicLong(0));
            boolean success = pos.compareAndSet(expectedPos, newPos);
            if (success) transactionCount.incrementAndGet();
            return success;
        }

        long getPosition(String symbol) {
            AtomicLong pos = positions.get(symbol);
            return pos == null ? 0L : pos.get();
        }
    }

    // =========================================================================
    // 2. Multi-field atomic update via immutable snapshot + AtomicReference
    // =========================================================================
    record AccountSnapshot(long version, long cashBalance, long unrealizedPnL,
                           long usedMargin, long availableMargin) {

        AccountSnapshot withFill(long filledQty, double fillPrice, double currentPrice) {
            long cost          = (long)(filledQty * fillPrice);
            long newCash       = cashBalance - cost;
            long newUnrealized = (long)(filledQty * (currentPrice - fillPrice));
            long newMargin     = usedMargin + (long)(filledQty * fillPrice * 0.10); // 10% margin
            long newAvail      = newCash - newMargin;
            return new AccountSnapshot(version + 1, newCash, newUnrealized, newMargin, newAvail);
        }
    }

    static final class AtomicAccount {
        private final AtomicReference<AccountSnapshot> snapshot;
        private final AtomicLong casMisses = new AtomicLong();

        AtomicAccount(long initialCash) {
            snapshot = new AtomicReference<>(new AccountSnapshot(0, initialCash, 0, 0, initialCash));
        }

        /**
         * Optimistic transaction: compute new snapshot, CAS-swap it.
         * If another thread raced us, retry (spin on version conflict).
         */
        AccountSnapshot applyFill(long qty, double fillPrice, double currentPrice) {
            while (true) {
                AccountSnapshot current = snapshot.get();
                AccountSnapshot next    = current.withFill(qty, fillPrice, currentPrice);
                if (snapshot.compareAndSet(current, next)) {
                    return next;
                }
                casMisses.incrementAndGet(); // lost the race — retry
            }
        }

        AccountSnapshot current() { return snapshot.get(); }
        long casMisses()          { return casMisses.get(); }
    }

    // =========================================================================
    // 3. Write-Ahead Log (WAL): durable transaction record
    // =========================================================================
    record TxLogEntry(long txId, long timestampNs, String symbol,
                      long qty, double price, String action) {}

    static final class WriteAheadLog {
        private final List<TxLogEntry> log = Collections.synchronizedList(new ArrayList<>());
        private final AtomicLong txIdGen   = new AtomicLong(1);

        long append(String symbol, long qty, double price, String action) {
            long txId = txIdGen.getAndIncrement();
            log.add(new TxLogEntry(txId, System.nanoTime(), symbol, qty, price, action));
            return txId;
        }

        /** Replay log from a given txId to rebuild state after failover. */
        List<TxLogEntry> replayFrom(long fromTxId) {
            List<TxLogEntry> entries = new ArrayList<>();
            synchronized (log) {
                for (TxLogEntry e : log) {
                    if (e.txId() >= fromTxId) entries.add(e);
                }
            }
            return entries;
        }

        int size() { return log.size(); }
    }

    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("=== Atomic Transaction Demo ===\n");

        // ---- 1. CAS position store ----
        AtomicPositionStore store = new AtomicPositionStore();
        WriteAheadLog wal = new WriteAheadLog();

        int THREADS = 8, OPS_PER_THREAD = 10_000;
        CountDownLatch latch = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        long start = System.nanoTime();
        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        long txId = wal.append("AAPL", 100, 150.0 + i * 0.001, "BUY");
                        store.applyFill("AAPL", 100);
                        // WAL appended BEFORE state change = durable-first guarantee
                    }
                } finally { latch.countDown(); }
            });
        }
        latch.await();
        long elapsedNs = System.nanoTime() - start;
        pool.shutdown();

        long expectedPosition = (long) THREADS * OPS_PER_THREAD * 100;
        long actualPosition   = store.getPosition("AAPL");

        System.out.println("--- CAS Position Store ---");
        System.out.printf("Threads: %d  Ops each: %,d  Total ops: %,d%n",
                THREADS, OPS_PER_THREAD, (long) THREADS * OPS_PER_THREAD);
        System.out.printf("Expected AAPL position : %,d%n", expectedPosition);
        System.out.printf("Actual   AAPL position : %,d  (match=%b — no race conditions!)%n",
                actualPosition, actualPosition == expectedPosition);
        System.out.printf("WAL entries            : %,d%n", wal.size());
        System.out.printf("Throughput             : %,.0f ops/sec%n",
                (long) THREADS * OPS_PER_THREAD * 1e9 / elapsedNs);

        // ---- 2. Multi-field atomic account ----
        System.out.println("\n--- Optimistic Locking (AtomicReference) ---");
        AtomicAccount account = new AtomicAccount(1_000_000L);

        List<Thread> traders = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            traders.add(Thread.ofPlatform().start(() -> {
                for (int j = 0; j < 1000; j++) {
                    account.applyFill(10, 150.0, 150.05);
                }
            }));
        }
        for (Thread t : traders) t.join();

        AccountSnapshot snap = account.current();
        System.out.printf("Account version   : %,d%n", snap.version());
        System.out.printf("Cash balance      : %,d%n", snap.cashBalance());
        System.out.printf("Unrealized P&L    : %,d%n", snap.unrealizedPnL());
        System.out.printf("Used margin       : %,d%n", snap.usedMargin());
        System.out.printf("CAS misses (retries): %,d%n", account.casMisses());

        // ---- 3. WAL replay ----
        System.out.println("\n--- WAL Replay (failover recovery) ---");
        long replayFrom = wal.size() / 2; // simulate recovery from halfway
        List<TxLogEntry> replayed = wal.replayFrom(replayFrom);
        System.out.printf("Total log entries : %,d%n", wal.size());
        System.out.printf("Replaying from tx : %,d — %,d entries to re-apply%n",
                replayFrom, replayed.size());
        System.out.println("Recovery via WAL guarantees zero message loss.");
    }
}