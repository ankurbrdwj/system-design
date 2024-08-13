package com.ankur.design.training.java8.concurrency.lockcondition;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/*
/Custom blocking queue implementation
 */
public class MyBlockingQueue<E> {
    private Queue<E> queue;
    private int max = 5;
    private ReentrantLock lock = new ReentrantLock(true);
    private final Condition notEmpty;
    private final Condition notFull;
    public MyBlockingQueue(int size) {
        this.notEmpty = lock.newCondition();
        this.notFull = lock.newCondition();
        this.queue = new LinkedList<>();
        this.max = size;
    }

    public void put(E e) {
        lock.lock();
        try {
            if(queue.size() == max){
                System.out.printf("blocking the queue while the size is max %d\n", queue.size());
            }
            queue.add(e);
        } finally {
            lock.unlock();
        }
    }

    public E take() {
        lock.lock();
        try {
            if(queue.size() == 0){
                System.out.printf("blocking the queue while the size is empty %d\n", queue.size());
            }
            E item = queue.remove();
            return item;
        } finally {
            lock.unlock();
        }
    }
}
