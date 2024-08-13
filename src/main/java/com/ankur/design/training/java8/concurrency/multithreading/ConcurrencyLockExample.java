/**
 *
 */
package com.ankur.design.training.java8.concurrency.multithreading;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author ankurbrdwj
 *
 */
public class ConcurrencyLockExample implements Runnable {
    private Resource resource;
    private Lock lock;

    /**
     *
     */
    public ConcurrencyLockExample() {
        // TODO Auto-generated constructor stub
    }

    public ConcurrencyLockExample(Resource r) {
        this.resource = r;
        this.lock = new ReentrantLock();
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        // TODO Auto-generated method stub
        Resource r = new Resource();
        ConcurrencyLockExample cle = new ConcurrencyLockExample(r);
        Thread t1 = new Thread(cle, "Thread 1");
        Thread t2 = new Thread(cle, "Thread 2");
        System.out.println(t1.getName() + "Started Running");
        t1.start();
        System.out.println(t2.getName() + "Strated Running");
        t2.start();
    }

    public void run() {
        // TODO Auto-generated method stub
        try {
            if (lock.tryLock(10, TimeUnit.SECONDS)) {
                resource.doSomething();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //release lock
            lock.unlock();
        }
        resource.doLogging();
    }
}


