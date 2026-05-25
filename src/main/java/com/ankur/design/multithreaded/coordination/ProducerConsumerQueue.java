package com.ankur.design.multithreaded.coordination;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Classic producer/consumer using BlockingQueue.
 *
 * BlockingQueue handles all coordination:
 *   - put()  blocks producer when queue is full
 *   - take() blocks consumer when queue is empty
 *   No explicit wait/notify needed.
 *
 * Shutdown: producer sets a volatile flag; consumer checks it after each take().
 */
public class ProducerConsumerQueue {

    private static final int CAPACITY = 5;

    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(CAPACITY);
    private volatile boolean done = false;

    class Producer implements Runnable {
        @Override
        public void run() {
            try {
                for (int i = 1; i <= 10; i++) {
                    String item = "item-" + i;
                    queue.put(item);                          // blocks if full
                    System.out.println("[P] produced: " + item + "  queue size: " + queue.size());
                    Thread.sleep(100);
                }
                done = true;
                System.out.println("[P] done");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    class Consumer implements Runnable {
        @Override
        public void run() {
            try {
                while (!done || !queue.isEmpty()) {
                    String item = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (item == null) continue;               // timed out, re-check done
                    System.out.println("[C] consumed: " + item + "  queue size: " + queue.size());
                    Thread.sleep(200);                        // consumer slower than producer
                }
                System.out.println("[C] done");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    void run() throws InterruptedException {
        Thread producer = new Thread(new Producer(), "producer");
        Thread consumer = new Thread(new Consumer(), "consumer");

        consumer.start();
        producer.start();

        producer.join();
        consumer.join();
        System.out.println("Done.");
    }

    public static void main(String[] args) throws InterruptedException {
        new ProducerConsumerQueue().run();
    }
}