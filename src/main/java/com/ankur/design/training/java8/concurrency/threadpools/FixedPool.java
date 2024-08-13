package com.ankur.design.training.java8.concurrency.threadpools;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FixedPool {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService service= Executors.newFixedThreadPool(8);
        ExecutorService service1= Executors.newCachedThreadPool();
        ExecutorService service2= Executors.newScheduledThreadPool(8);

        for(int i= 0;i<100;i++){
        service.submit(new Task());
            service.submit(new Task());
            service1.submit(new Task());
            service2.submit(new Task());
        }
        service.shutdown();
        service1.shutdown();
        service2.shutdown();
    }
}

class Task implements Runnable {

    @Override
    public void run() {
        System.out.println("Inside runnable task");
    }
}
