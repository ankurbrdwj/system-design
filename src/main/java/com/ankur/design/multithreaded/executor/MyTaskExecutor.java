package com.ankur.design.multithreaded.executor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MyTaskExecutor implements Executor {
    private static final int NTHREADS = 10;
    private static final ThreadPoolExecutor exec = (ThreadPoolExecutor) Executors.newFixedThreadPool(NTHREADS);

    @Override
    public void execute(Runnable command) {
        exec.execute(command);
    }

    public static void main(String[] args) {
        MyTaskExecutor myTaskExecutor = new MyTaskExecutor();

        System.out.println("Submitting 10 tasks...");
        for (int i = 0; i < NTHREADS; i++) {
            final int taskNumber = i + 1;
            myTaskExecutor.execute(() -> {
                System.out.println("Task " + taskNumber + " starting on thread " + Thread.currentThread().getName());
                try {
                    Thread.sleep(2000); // Simulate a long-running task
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("Task " + taskNumber + " finished.");
            });
        }

        // Give tasks a moment to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\nActive threads executing tasks: " + exec.getActiveCount());
        System.out.println("Total threads in pool: " + exec.getPoolSize() + "\n");

        try {
            exec.shutdown();
            if (!exec.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.println("Tasks did not finish in time, forcing shutdown");
                exec.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("Shutdown interrupted");
            exec.shutdownNow();
        } finally {
            System.out.println("Executor service shut down");
        }
    }
}
