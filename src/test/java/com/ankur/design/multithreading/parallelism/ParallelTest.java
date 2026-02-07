package com.ankur.design.multithreading.parallelism;

public class ParallelTest {

    public static void main(String[] args) throws InterruptedException {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("Available cores: " + cores);

        // Create 1000 threads (way more than 8 cores!)
        for (int i = 0; i < 1000; i++) {
            final int threadNum = i;
            Thread t = new Thread(() -> {
                System.out.println("Thread " + threadNum +
                        " running on core (approx): " +
                        Thread.currentThread().getId() % cores);

                try {
                    Thread.sleep(5000); // Sleep 5 seconds
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            t.start();
        }

        System.out.println("Created 1000 threads on " + cores + " cores!");

        // Check active threads
        Thread.sleep(1000);
        System.out.println("Active threads: " + Thread.activeCount());
    }
}
