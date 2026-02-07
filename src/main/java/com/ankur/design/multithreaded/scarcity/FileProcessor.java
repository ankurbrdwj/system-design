package com.ankur.design.multithreaded.scarcity;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class FileProcessor {

    // Limit: Only 100 files open at once
    private final Semaphore fileHandleSemaphore = new Semaphore(100);

    public void processFile(String filename) {
        try {
            // Acquire permit (blocks if 100 files already open)
            fileHandleSemaphore.acquire();

            try {
                // Now safe to open file
                FileReader reader = new FileReader(filename);
                BufferedReader buffered = new BufferedReader(reader);

                String line;
                while ((line = buffered.readLine()) != null) {
                    // Process line
                    System.out.println(line);
                }

                buffered.close();

            } finally {
                // ALWAYS release permit!
                fileHandleSemaphore.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        FileProcessor processor = new FileProcessor();
        ExecutorService executor = Executors.newFixedThreadPool(200);

        // Process 1000 files with 200 threads
        for (int i = 0; i < 1000; i++) {
            final int fileNum = i;
            executor.submit(() ->
                    processor.processFile("file" + fileNum + ".txt")
            );
        }

        executor.shutdown();
    }
}
