package com.ankur.design.hft.ringbuffer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * CONCEPT 6 — Sequence Barrier
 *
 * A sequence barrier answers: "what is the highest seq I am allowed to process?"
 * For a consumer with upstream dependencies, the answer is:
 *
 *   barrier.availableSeq() = min(upstream consumer sequences)
 *
 * This ensures: downstream never reads an event that an upstream processor
 * hasn't finished enriching yet.
 *
 * From the video's pipeline table:
 *
 *   Process  Depends on   Seq processed   Max allowed
 *   A        (none)            6              7
 *   B        (none)            5              7
 *   C        A, B              2           min(6,5)=5
 *   D        A, B              4           min(6,5)=5
 *   E        A, B              3           min(6,5)=5
 *   F        D, E              ?           min(4,3)=3
 *
 * Barrier for C/D/E = min(seqA, seqB)
 * Barrier for F     = min(seqD, seqE)
 *
 * This lets each stage run at its own pace with no locks.
 * The barrier is just a min() over AtomicLong reads.
 */
public class RB6_SequenceBarrier {

    // ── Barrier: wraps one or more upstream sequence cursors ─────────────────

    static class SequenceBarrier {
        private final AtomicLong[] upstreams;

        SequenceBarrier(AtomicLong... upstreams) {
            this.upstreams = upstreams;
        }

        /**
         * Highest sequence the holder of this barrier is allowed to process.
         * Spins until at least one new event is available.
         */
        long waitFor(long wantSeq) throws InterruptedException {
            long available;
            while ((available = min()) < wantSeq) {
                Thread.onSpinWait();
                if (Thread.interrupted()) throw new InterruptedException();
            }
            return available;
        }

        long min() {
            long m = Long.MAX_VALUE;
            for (AtomicLong u : upstreams) m = Math.min(m, u.get());
            return m;
        }
    }

    // ── Ring buffer (simplified) ──────────────────────────────────────────────

    static final int   CAPACITY = 16;
    static final int   MASK     = CAPACITY - 1;
    static final long  EVENTS   = 10;

    static final long[]     buffer      = new long[CAPACITY];
    static final AtomicLong publishedSeq = new AtomicLong(-1);  // producer cursor

    // consumer cursors — each stage updates its own
    static final AtomicLong seqA = new AtomicLong(-1);
    static final AtomicLong seqB = new AtomicLong(-1);
    static final AtomicLong seqC = new AtomicLong(-1);
    static final AtomicLong seqD = new AtomicLong(-1);
    static final AtomicLong seqE = new AtomicLong(-1);
    static final AtomicLong seqF = new AtomicLong(-1);

    // barriers
    // A, B read directly from producer
    static final SequenceBarrier producerBarrier = new SequenceBarrier(publishedSeq);
    // C, D, E must wait for BOTH A and B
    static final SequenceBarrier abBarrier       = new SequenceBarrier(seqA, seqB);
    // F must wait for BOTH D and E
    static final SequenceBarrier deBarrier       = new SequenceBarrier(seqD, seqE);

    // ── Stage runner ──────────────────────────────────────────────────────────

    static Thread runStage(String name, AtomicLong myCursor,
                           SequenceBarrier barrier, long sleepMs) {
        Thread t = new Thread(() -> {
            try {
                for (long next = 0; next < EVENTS; next++) {
                    long available = barrier.waitFor(next);  // wait for upstream
                    // can process next..available in a batch; we process one at a time here
                    long value = buffer[(int)(next & MASK)];
                    System.out.printf("[%-3s] processed seq=%-3d  value=%-4d  "
                            + "barrier_min=%-3d%n", name, next, value, available);
                    Thread.sleep(sleepMs);
                    myCursor.set(next);   // advance own cursor AFTER processing
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, name);
        t.start();
        return t;
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Sequence Barrier Pipeline: A,B → C,D,E → F ===");
        System.out.println("Each stage can only process up to min(upstream cursors)\n");

        // start all stages
        Thread tA = runStage("A", seqA, producerBarrier, 20);
        Thread tB = runStage("B", seqB, producerBarrier, 40);  // B is slower than A
        Thread tC = runStage("C", seqC, abBarrier,       10);
        Thread tD = runStage("D", seqD, abBarrier,       15);
        Thread tE = runStage("E", seqE, abBarrier,       30);  // E is slowest in layer 2
        Thread tF = runStage("F", seqF, deBarrier,       10);

        // producer publishes events
        Thread producer = new Thread(() -> {
            try {
                for (long seq = 0; seq < EVENTS; seq++) {
                    buffer[(int)(seq & MASK)] = seq * 10;   // store value
                    publishedSeq.set(seq);                  // signal all of layer 1
                    System.out.printf("[P ] published seq=%-3d  value=%d%n", seq, seq * 10);
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "P");

        producer.start();
        producer.join();
        tA.join(); tB.join(); tC.join(); tD.join(); tE.join(); tF.join();

        System.out.println("\nAll stages complete. F processed last: " + seqF.get());
    }
}