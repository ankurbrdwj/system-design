package com.ankur.design.multithreaded.executor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hibernate.query.sqm.tree.SqmNode.log;


public class StreamCompletableExecutorService {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        log.info("Submitting 10 tasks using CompletableFuture...");

        // Create CompletableFutures
        List<CompletableFuture<String>> futures = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    log.info("Task " + i + " executing on thread: "
                            + Thread.currentThread().getName());
                    try {
                        TimeUnit.SECONDS.sleep(2);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "Task " + i + "'s result";
                }, executor))  // Use your executor
                .collect(Collectors.toList());

        log.info("All tasks submitted. Processing results asynchronously...");

        // Process results asynchronously (NON-BLOCKING!)
        futures.forEach(future ->
                future.thenAccept(result ->
                        log.info("Result received: " + result)
                )
        );

        // Wait for all to complete
        CompletableFuture<Void> allDone = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        // Block only at the end
        allDone.join();

        log.info("All tasks completed. Shutting down...");

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        log.info("Executor service shut down.");
    }
}
