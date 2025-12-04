package com.ankur.design.multithreaded.executor;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
@Slf4j
public class StreamExecutorServiceExample {

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        log.info("Submitting 10 Callable tasks using Java 8 Streams...");

        // Use IntStream to create and submit 10 tasks
        List<Future<String>> futureList = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> (Callable<String>) () -> {
                    log.info("Task " + i + " executing on thread: " + Thread.currentThread().getName());
                    TimeUnit.SECONDS.sleep(2); // Simulate a long-running operation
                    return "Task " + i + "'s result";
                })
                .map(executor::submit) // Submit each task to the executor
                .collect(Collectors.toList());

        log.info("All tasks have been submitted. Main thread waiting for results.");

        // Process the results using a stream
        futureList.forEach(future -> {
            try {
                String result = future.get(); // Block and wait for the result
                log.info("Result received: " + result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Shut down the executor
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        log.info("Executor service shut down.");
    }
}
