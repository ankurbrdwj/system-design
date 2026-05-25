package com.ankur.design.hft.ringbuffer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * CONCEPT 7 — Full Trade Processing Data Pipeline
 *
 * Combines everything into one realistic HFT pipeline:
 *
 *   Producer  →  [ring buffer]  →  Unmarshal  →  Risk Check
 *                                             →  Journal    → Replicator
 *                                             →  Business Logic
 *
 * Pipeline stages:
 *   Stage 1: Unmarshal   — deserialise raw bytes into Trade object
 *   Stage 2a: Journal    — write to durable log (depends on Unmarshal)
 *   Stage 2b: RiskCheck  — validate trade limits  (depends on Unmarshal)
 *   Stage 3: Business    — execute trade logic (depends on Journal + RiskCheck)
 *
 * Barriers:
 *   unmarshalBarrier  = min(publishedSeq)            ← layer 1 reads from producer
 *   journalBarrier    = min(unmarshalSeq)             ← layer 2 reads from Unmarshal
 *   riskBarrier       = min(unmarshalSeq)             ← layer 2 reads from Unmarshal
 *   businessBarrier   = min(journalSeq, riskSeq)      ← layer 3 reads from layer 2
 *
 * Diagram:
 *
 *   [Producer]
 *       │
 *       ▼
 *   [Unmarshal]──────────────────┐
 *       │                        │
 *       ▼                        ▼
 *   [Journal]              [RiskCheck]
 *       │                        │
 *       └──────────┬─────────────┘
 *                  ▼
 *            [Business Logic]
 */
public class RB7_DataPipeline {

    static final int  CAPACITY = 16;
    static final int  MASK     = CAPACITY - 1;
    static final long TRADES   = 8;

    // ── Event slot in the ring buffer ─────────────────────────────────────────

    static class TradeEvent {
        long   seq;
        long   rawPrice;       // set by producer
        String instrument;     // set by producer
        double unmarshaledPrice; // set by Unmarshal stage
        boolean riskApproved;    // set by RiskCheck stage
        boolean journaled;       // set by Journal stage
        String  result;          // set by Business stage
    }

    static final TradeEvent[] buffer = new TradeEvent[CAPACITY];
    static {
        for (int i = 0; i < CAPACITY; i++) buffer[i] = new TradeEvent();
    }

    static final AtomicLong publishedSeq  = new AtomicLong(-1);
    static final AtomicLong unmarshalSeq  = new AtomicLong(-1);
    static final AtomicLong journalSeq    = new AtomicLong(-1);
    static final AtomicLong riskSeq       = new AtomicLong(-1);
    static final AtomicLong businessSeq   = new AtomicLong(-1);

    // ── Barrier helper ────────────────────────────────────────────────────────

    static long waitFor(long seq, AtomicLong... upstreams) throws InterruptedException {
        while (true) {
            long min = Long.MAX_VALUE;
            for (AtomicLong u : upstreams) min = Math.min(min, u.get());
            if (min >= seq) return min;
            Thread.onSpinWait();
            if (Thread.interrupted()) throw new InterruptedException();
        }
    }

    // ── Stage runners ─────────────────────────────────────────────────────────

    static Thread producer() {
        return new Thread(() -> {
            try {
                for (long seq = 0; seq < TRADES; seq++) {
                    int idx = (int)(seq & MASK);
                    buffer[idx].seq        = seq;
                    buffer[idx].rawPrice   = 10000 + seq * 50;
                    buffer[idx].instrument = "BTC-USD";
                    publishedSeq.set(seq);
                    System.out.printf("[Producer  ] seq=%d  rawPrice=%d%n",
                            seq, buffer[idx].rawPrice);
                    Thread.sleep(15);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Producer");
    }

    static Thread unmarshal() {
        return new Thread(() -> {
            try {
                for (long seq = 0; seq < TRADES; seq++) {
                    waitFor(seq, publishedSeq);
                    int idx = (int)(seq & MASK);
                    // simulate: convert raw long price (fixed point) to double
                    buffer[idx].unmarshaledPrice = buffer[idx].rawPrice / 100.0;
                    System.out.printf("[Unmarshal ] seq=%d  price=%.2f%n",
                            seq, buffer[idx].unmarshaledPrice);
                    unmarshalSeq.set(seq);
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Unmarshal");
    }

    static Thread journal() {
        return new Thread(() -> {
            try {
                for (long seq = 0; seq < TRADES; seq++) {
                    waitFor(seq, unmarshalSeq);
                    int idx = (int)(seq & MASK);
                    // simulate: append to write-ahead log
                    buffer[idx].journaled = true;
                    System.out.printf("[Journal   ] seq=%d  journaled%n", seq);
                    journalSeq.set(seq);
                    Thread.sleep(20);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Journal");
    }

    static Thread riskCheck() {
        return new Thread(() -> {
            try {
                for (long seq = 0; seq < TRADES; seq++) {
                    waitFor(seq, unmarshalSeq);
                    int idx = (int)(seq & MASK);
                    // simulate: check price is within risk limits
                    buffer[idx].riskApproved = buffer[idx].unmarshaledPrice < 200.0;
                    System.out.printf("[RiskCheck ] seq=%d  approved=%b%n",
                            seq, buffer[idx].riskApproved);
                    riskSeq.set(seq);
                    Thread.sleep(12);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "RiskCheck");
    }

    static Thread businessLogic() {
        return new Thread(() -> {
            try {
                for (long seq = 0; seq < TRADES; seq++) {
                    // must wait for BOTH journal AND risk to finish
                    waitFor(seq, journalSeq, riskSeq);
                    int idx = (int)(seq & MASK);
                    TradeEvent e = buffer[idx];
                    e.result = e.riskApproved && e.journaled ? "EXECUTED" : "REJECTED";
                    System.out.printf("[Business  ] seq=%d  price=%.2f  %s%n",
                            seq, e.unmarshaledPrice, e.result);
                    businessSeq.set(seq);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Business");
    }

    // ── Main ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Full Trade Pipeline: Producer → Unmarshal → [Journal|Risk] → Business ===\n");

        Thread p  = producer();
        Thread u  = unmarshal();
        Thread j  = journal();
        Thread r  = riskCheck();
        Thread b  = businessLogic();

        // start all concurrently — barriers enforce the ordering
        p.start(); u.start(); j.start(); r.start(); b.start();
        p.join();  u.join();  j.join();  r.join();  b.join();

        System.out.printf("%nPipeline complete. %d trades processed. Last business seq=%d%n",
                TRADES, businessSeq.get());
    }
}