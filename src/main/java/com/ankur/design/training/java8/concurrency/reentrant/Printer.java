package com.ankur.design.training.java8.concurrency.reentrant;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Printer {
    private ReentrantLock lock = new ReentrantLock(true);
    private final Condition printOdd = lock.newCondition();
    private final Condition printEven = lock.newCondition();
    private AtomicInteger counter = new AtomicInteger(1);

    void printEven() throws InterruptedException {
        while (counter.intValue() <= 10) {
            try {
                // Getting lock for EVEN block
                lock.lock();
                System.out.println("EVEN : " + counter.getAndIncrement());
                // signaling to ODD condition
                printOdd.signal();
                /*
                 * Just stopping await once reach counter to 10.
                 * Not to even thread to await indefinitely
                 */
                if (counter.intValue() < 10) {
                    printEven.await();
                }
            } finally {
                lock.unlock();
            }
        }
    }

    void printOdd() throws InterruptedException {
        while (counter.intValue() < 10) {
            try {
                // Getting lock for ODD block
                lock.lock();
                System.out.println("ODD : " + counter.getAndIncrement());
                // signaling to EVEN condition
                printEven.signal();
                /*
                 * Just stopping await once reach counter to 10.
                 * Not to odd thread to await indefinitely
                 */
                if (counter.intValue() < 10) {
                    printOdd.await();
                }
            } finally {
                lock.unlock();
            }
        }

    }
}
