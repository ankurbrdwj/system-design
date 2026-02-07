package com.ankur.design.multithreaded.concurrentmap;

import java.util.Map;
import java.util.concurrent.*;

public class ConcurrentHashMapDemo {
    public static void main(String[] args) throws InterruptedException {
        // Shared map
        Map<String, Integer> inventory = new ConcurrentHashMap<>();

        // Preload the map
        for (int i = 1; i <= 100; i++) {
            inventory.put("item-" + i, i);
        }

        // 4 worker threads
        ExecutorService executor = Executors.newFixedThreadPool(4);

        Runnable reader = () -> {
            for (int i = 1; i <= 100; i++) {
                String key = "item-" + i;
                Integer val = inventory.get(key);
                // pretend to do work
                if (val != null) {
                    System.out.println(Thread.currentThread().getName() +
                            " read: " + key + "=" + val);
                }
            }
        };

        // Submit same job to 4 threads
        executor.submit(reader);
        executor.submit(reader);
        executor.submit(reader);
        executor.submit(reader);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("Done");
    }
}
