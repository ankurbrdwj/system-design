package com.ankur.design.hft.ringbuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CONCEPT 5 — Log (Persistent / Append-Only Storage)
 *
 * A ring buffer is fixed-size and overwrites old events.
 * A LOG is the natural extension: append-only, events never overwritten,
 * consumers can subscribe at ANY position (replay from beginning or from now).
 *
 * Ring Buffer vs Log:
 * ┌─────────────────┬──────────────────────────────────────────┐
 * │ Ring Buffer     │ Fixed size, overwrites old, in-memory    │
 * │ Log             │ Unbounded, append-only, may be on disk   │
 * └─────────────────┴──────────────────────────────────────────┘
 *
 * Key log properties:
 *   - Producer appends; sequence number = offset in the log
 *   - Consumers maintain their own read position
 *   - Late consumers can replay from any past offset
 *   - Log rotation: split into segments when a threshold is reached
 *   - GC/deletion: drop segments older than min(all consumer offsets)
 *
 * This is exactly how Kafka works at the storage layer.
 *
 * Segment rotation diagram:
 *
 *   segment-0.log  [0..999]      ← can delete when all consumers past seq 999
 *   segment-1.log  [1000..1999]
 *   segment-2.log  [2000..]      ← active segment, appending here
 */
public class RB5_Log {

    // ── Log Segment: a slice of the full sequence space ───────────────────────

    static class Segment {
        final long         baseSeq;     // first sequence number in this segment
        final List<String> entries = new ArrayList<>();

        Segment(long baseSeq) { this.baseSeq = baseSeq; }

        void append(String event) { entries.add(event); }
        long endSeq()             { return baseSeq + entries.size() - 1; }
        boolean contains(long seq){ return seq >= baseSeq && seq <= endSeq(); }

        String get(long seq) { return entries.get((int)(seq - baseSeq)); }
    }

    // ── Log ───────────────────────────────────────────────────────────────────

    static class Log {
        static final int SEGMENT_SIZE = 5;   // rotate every 5 events for demo

        private final List<Segment>     segments    = new ArrayList<>();
        private final AtomicLong        nextSeq     = new AtomicLong(0);
        private final CopyOnWriteArrayList<AtomicLong> consumerOffsets = new CopyOnWriteArrayList<>();

        Log() { segments.add(new Segment(0)); }

        // consumers register their current offset so log knows what's safe to delete
        AtomicLong registerConsumer(long startOffset) {
            AtomicLong offset = new AtomicLong(startOffset);
            consumerOffsets.add(offset);
            return offset;
        }

        // producer appends — never blocks, never overwrites
        synchronized long append(String event) {
            long seq = nextSeq.getAndIncrement();

            Segment active = segments.get(segments.size() - 1);
            if (active.entries.size() >= SEGMENT_SIZE) {
                active = new Segment(seq);
                segments.add(active);
                System.out.printf("[log] rotated to new segment at seq=%d%n", seq);
                deleteOldSegments();
            }

            active.append(event);
            System.out.printf("[log] appended seq=%-3d  event='%s'  segment=%d%n",
                    seq, event, segments.size() - 1);
            return seq;
        }

        // read a specific sequence number from any segment
        synchronized String read(long seq) {
            for (Segment s : segments)
                if (s.contains(seq)) return s.get(seq);
            throw new IllegalArgumentException("seq=" + seq + " has been deleted");
        }

        // delete segments older than the slowest consumer
        private void deleteOldSegments() {
            long minOffset = consumerOffsets.stream()
                    .mapToLong(AtomicLong::get).min().orElse(Long.MAX_VALUE);

            segments.removeIf(s -> s.endSeq() < minOffset && segments.size() > 1);
            System.out.printf("[log] GC: min consumer offset=%d, segments remaining=%d%n",
                    minOffset, segments.size());
        }
    }

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        Log log = new Log();

        // consumer A starts from the beginning (replay all events)
        AtomicLong consumerAOffset = log.registerConsumer(0);
        // consumer B subscribes LATE — only reads from seq 8 onwards
        AtomicLong consumerBOffset = log.registerConsumer(8);

        System.out.println("=== Log: append-only, consumers replay from any offset ===\n");

        // producer appends 12 events (triggers 2 rotations at size=5)
        for (int i = 0; i < 12; i++) {
            log.append("trade-" + i);
            Thread.sleep(10);
        }

        System.out.println("\n--- Consumer A replaying from seq 0 ---");
        for (long seq = 0; seq < 12; seq++) {
            System.out.printf("[consumerA] seq=%-3d  event='%s'%n", seq, log.read(seq));
            consumerAOffset.set(seq);
        }

        System.out.println("\n--- Consumer B replaying from seq 8 (late subscriber) ---");
        for (long seq = 8; seq < 12; seq++) {
            System.out.printf("[consumerB] seq=%-3d  event='%s'%n", seq, log.read(seq));
            consumerBOffset.set(seq);
        }

        System.out.println("\nLog extends the ring buffer idea to infinite, replayable storage.");
    }
}