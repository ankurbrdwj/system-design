package com.ankur.design.multithreaded.coordination;

import java.util.concurrent.*;

/**
 * Producer-consumer pipeline using poison-pill shutdown.
 *
 * 3 producers × 100 trades → queue (cap 50) → 2 consumers.
 *
 * Shutdown protocol:
 *   1. producers.shutdown() + awaitTermination  — all puts are done
 *   2. main thread puts exactly numConsumers poison pills into the queue
 *   3. each consumer receives one pill → exits its loop
 *   4. consumers.shutdown() + awaitTermination  — clean exit
 *
 * Why numConsumers pills, not 1?
 *   Each pill is taken by exactly one consumer (queue.take() is exclusive).
 *   One pill → one consumer exits, the rest spin forever.
 */
public class TradeProcessingPipeline {

    record Trade(int producerId, int sequence, String symbol) {}

    // identity sentinel — consumers check with == not equals()
    private static final Trade POISON = new Trade(-1, -1, "POISON");

    private final BlockingQueue<Trade> queue       = new LinkedBlockingQueue<>(50);
    private final int                 numProducers = 3;
    private final int                 numConsumers = 2;

    public void run() throws InterruptedException {
        ExecutorService producers = Executors.newFixedThreadPool(numProducers);
        ExecutorService consumers = Executors.newFixedThreadPool(numConsumers);

        for (int i = 0; i < numProducers; i++) {
            final int id = i;
            producers.submit(() -> {
                try {
                    for (int t = 0; t < 100; t++) {
                        queue.put(new Trade(id, t, "BTC"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        for (int i = 0; i < numConsumers; i++) {
            consumers.submit(() -> {
                try {
                    while (true) {
                        Trade t = queue.take();   // blocks — no timeout needed
                        if (t == POISON) break;   // identity check, not equals()
                        process(t);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // wait for all producers to finish, then signal each consumer once
        producers.shutdown();
        boolean clean = producers.awaitTermination(30, TimeUnit.SECONDS);
        if (!clean) {
            consumers.shutdownNow();
            throw new IllegalStateException("Producers did not finish in time");
        }

        for (int i = 0; i < numConsumers; i++) {
            queue.put(POISON);   // one pill per consumer
        }

        consumers.shutdown();
        consumers.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("Pipeline complete. Remaining in queue: " + queue.size());
    }

    private void process(Trade trade) {
        System.out.printf("[%s] producer=%d seq=%d symbol=%s%n",
                Thread.currentThread().getName(),
                trade.producerId(), trade.sequence(), trade.symbol());
    }

    public static void main(String[] args) throws InterruptedException {
        new TradeProcessingPipeline().run();
    }
}