package com.ankur.design.multithreaded.coordination;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class QueuePrinter {

    private final BlockingQueue<String> queueA = new ArrayBlockingQueue<>(1);
    private final BlockingQueue<String> queueB = new ArrayBlockingQueue<>(1);

    public void printA() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            queueA.take();
            System.out.println(" A ");
            queueB.put("go");
        }
    }

    public void printB() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            queueB.take();  // Wait for permission
            System.out.print("B ");
            queueA.put("go");  // Give A permission
        }
    }

    public static void main(String[] args) {
        QueuePrinter printer = new QueuePrinter();

        // Give A first permission
        printer.queueA.offer("go");

        Thread t1 = new Thread(() -> {
            try {
                printer.printA();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                printer.printB();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        t1.start();
        t2.start();
    }
}

