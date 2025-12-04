package com.ankur.design.multithreaded.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ExecutorServiceExample {

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<String>> futureList = new ArrayList<>();

        System.out.println("Submitting 10 Callable tasks...");
        for (int i = 0; i < 10; i++) {
            final int taskNumber = i + 1;
            Callable<String> callableTask = () -> {
                System.out.println("Task " + taskNumber + " executing on thread: " + Thread.currentThread().getName());
                TimeUnit.SECONDS.sleep(2); // Simulate a long-running operation
                return "Task " + taskNumber + "'s result";
            };
            Future<String> future = executor.submit(callableTask);
            futureList.add(future);
        }

        System.out.println("All tasks have been submitted. Main thread waiting for results.");

        try {
            for (Future<String> future : futureList) {
                // Block and wait for each future to complete
                String result = future.get(); // This is a blocking call
                System.out.println("Result received: " + result);
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            // Shut down the executor
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException interruptedException) {
                executor.shutdownNow();
            }
            System.out.println("Executor service shut down.");
        }
    }
}
