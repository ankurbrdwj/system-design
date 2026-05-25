package com.ankur.design.hft.tuning;

import java.util.ArrayList;
import java.util.List;

/**
 * TOPIC: Batching — amortize expensive I/O operations over many records.
 *
 * Every I/O call (DB write, network send, disk flush) has a FIXED OVERHEAD:
 *   - TCP packet header: ~40 bytes overhead per packet
 *   - Database round-trip: ~1ms network + connection pool overhead
 *   - Disk seek time: ~1-5ms per random seek
 *   - System call overhead: context switch + kernel entry
 *
 * If you write N records one at a time: cost = N * (fixed_overhead + data_cost)
 * If you batch B records per call:      cost = (N/B) * (fixed_overhead + B * data_cost)
 *
 * When fixed_overhead >> data_cost (common in I/O), batching gives ~B× speedup.
 *
 * Real-world HFT examples:
 *   - Batch order acknowledgments before flushing TCP
 *   - Batch DB inserts with PreparedStatement.executeBatch()
 *   - Batch disk writes with BufferedWriter
 *   - Batch market data snapshots (send delta every 10ms instead of per-tick)
 */
public class BatchingDemo {

    // -------------------------------------------------------------------------
    // Simulated "database" with artificial I/O latency
    // -------------------------------------------------------------------------

    static class SimulatedDatabase {
        private final List<String> records = new ArrayList<>();
        private final long ioLatencyNs; // simulated I/O overhead per call
        private int callCount = 0;

        SimulatedDatabase(long ioLatencyNs) {
            this.ioLatencyNs = ioLatencyNs;
        }

        // Simulates a single-record write: 1 round-trip overhead per record.
        // In real DBs: connection pool check, network RTT, parse, execute, ack.
        public void writeOne(String record) {
            simulateIo();
            records.add(record);
            callCount++;
        }

        // Simulates a batch write: 1 round-trip overhead for ALL records in the batch.
        // In real DBs: one PreparedStatement with N bound param sets, one execute.
        public void writeBatch(List<String> batch) {
            simulateIo();  // one round-trip for the whole batch
            records.addAll(batch);
            callCount++;
        }

        private void simulateIo() {
            // Busy spin to simulate I/O latency without thread scheduling noise
            long end = System.nanoTime() + ioLatencyNs;
            while (System.nanoTime() < end) {
                Thread.onSpinWait();
            }
        }

        public int getCallCount()    { return callCount; }
        public int getRecordCount()  { return records.size(); }

        public void reset() {
            records.clear();
            callCount = 0;
        }
    }

    // -------------------------------------------------------------------------
    // BAD: Write one record at a time
    // -------------------------------------------------------------------------

    // BAD: N records = N I/O calls = N * (fixed_overhead + data_cost).
    // Every call pays the full round-trip cost independently.
    // This is the most common mistake when first integrating with a DB or message queue.
    static long writeBad(SimulatedDatabase db, List<String> records) {
        long start = System.nanoTime();

        // BAD: one DB call per record — overhead multiplied by N
        for (String record : records) {
            db.writeOne(record);  // BAD: pays full I/O overhead for each record
        }

        return System.nanoTime() - start;
    }

    // -------------------------------------------------------------------------
    // GOOD: Collect into batches, flush once
    // -------------------------------------------------------------------------

    // GOOD: Collect N records, then ONE I/O call for the whole batch.
    // Total cost = ceil(N / BATCH_SIZE) * (fixed_overhead + BATCH_SIZE * data_cost).
    // When fixed_overhead >> data_cost: speedup ≈ BATCH_SIZE.
    static long writeGood(SimulatedDatabase db, List<String> records, int batchSize) {
        long start = System.nanoTime();

        List<String> batch = new ArrayList<>(batchSize);

        for (String record : records) {
            batch.add(record);  // accumulate without I/O

            // GOOD: only flush when batch is full
            if (batch.size() >= batchSize) {
                db.writeBatch(batch);  // one call for batchSize records
                batch.clear();
            }
        }

        // Flush any remaining records (partial last batch)
        if (!batch.isEmpty()) {
            db.writeBatch(batch);
        }

        return System.nanoTime() - start;
    }

    // -------------------------------------------------------------------------
    // EXAMPLE 2: Showing the math directly
    // -------------------------------------------------------------------------

    // Demonstrate the cost model formula analytically
    static void showCostModel() {
        System.out.println("--- Cost Model (analytical) ---");
        System.out.println();
        System.out.println("Assumptions:");
        System.out.println("  N = 1,000 records");
        System.out.println("  I/O fixed overhead per call = 1,000,000 ns (1ms, simulates DB round-trip)");
        System.out.println("  Data cost per record = 1,000 ns (1us, simulates serialization)");
        System.out.println();

        long N = 1_000;
        long ioOverhead = 1_000_000; // 1ms
        long dataPerRecord = 1_000;  // 1us

        // BAD: one call per record
        long badCost = N * (ioOverhead + dataPerRecord);
        System.out.printf("BAD  (1 record/call):    %,d ms total  (%,d calls)%n",
                badCost / 1_000_000, N);

        int[] batchSizes = {10, 50, 100, 500, 1000};
        for (int bs : batchSizes) {
            long calls = (N + bs - 1) / bs; // ceil(N/bs)
            long goodCost = calls * (ioOverhead + (long) bs * dataPerRecord);
            double speedup = (double) badCost / goodCost;
            System.out.printf("GOOD (batch size %4d):  %,d ms total  (%,d calls)  speedup=%.1fx%n",
                    bs, goodCost / 1_000_000, calls, speedup);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        final int RECORD_COUNT = 500;
        final long IO_LATENCY_NS = 200_000; // 200 microseconds per I/O call
        final int BATCH_SIZE = 50;

        System.out.println("=== BatchingDemo ===");
        System.out.println();
        showCostModel();

        System.out.printf("--- Live Benchmark: %,d records, I/O latency=%,d us/call ---%n",
                RECORD_COUNT, IO_LATENCY_NS / 1000);
        System.out.println();

        // Generate test records
        List<String> records = new ArrayList<>(RECORD_COUNT);
        for (int i = 0; i < RECORD_COUNT; i++) {
            records.add("ORDER-" + i + ":BUY:AAPL:100@150.25");
        }

        SimulatedDatabase db = new SimulatedDatabase(IO_LATENCY_NS);

        // BAD: one record at a time
        db.reset();
        long badTime = writeBad(db, records);
        int badCalls = db.getCallCount();
        System.out.printf("BAD  (1 record/call):        %,6d ms   calls=%,d   records=%,d%n",
                badTime / 1_000_000, badCalls, db.getRecordCount());

        // GOOD: batched
        db.reset();
        long goodTime = writeGood(db, records, BATCH_SIZE);
        int goodCalls = db.getCallCount();
        System.out.printf("GOOD (batch size %3d):       %,6d ms   calls=%,d    records=%,d%n",
                BATCH_SIZE, goodTime / 1_000_000, goodCalls, db.getRecordCount());

        System.out.printf("%nSpeedup: %.1fx%n", (double) badTime / goodTime);
        System.out.printf("Call reduction: %dx -> %dx  (%.1fx fewer I/O calls)%n",
                badCalls, goodCalls, (double) badCalls / goodCalls);

        System.out.println();
        System.out.println("Key insight:");
        System.out.println("  Batching amortizes fixed I/O overhead across many records.");
        System.out.println("  Ideal batch size: large enough to amortize overhead,");
        System.out.println("  small enough to bound latency (in HFT: often time-based batching).");
        System.out.println("  Time-based flush: 'send everything accumulated in the last 100 microseconds'.");
    }
}