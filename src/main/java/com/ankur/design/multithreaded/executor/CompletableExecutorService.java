package com.ankur.design.multithreaded.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class CompletableExecutorService {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<String>> futureList = new ArrayList<>();

        System.out.println("Submitting 10 tasks using CompletableFuture...");

        for (int i = 0; i < 10; i++) {
            final int taskNumber = i + 1;

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                System.out.println("Task " + taskNumber + " executing on thread: "
                        + Thread.currentThread().getName());
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "Task " + taskNumber + "'s result";
            }, executor);

            futureList.add(future);
        }

        System.out.println("All tasks submitted. Processing results asynchronously...");

        // Process results NON-BLOCKING with callbacks
        for (CompletableFuture<String> future : futureList) {
            future.thenAccept(result -> {
                System.out.println("Result received: " + result);
            });
        }

        // Wait for ALL to complete
        CompletableFuture<Void> allDone = CompletableFuture.allOf(
                futureList.toArray(new CompletableFuture[0])
        );

        try {
            allDone.get(); // Block only here (wait for all)
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        System.out.println("All tasks completed.");

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        System.out.println("Executor service shut down.");
    }
}