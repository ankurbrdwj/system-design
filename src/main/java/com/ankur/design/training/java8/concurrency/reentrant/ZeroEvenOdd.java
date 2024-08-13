package com.ankur.design.training.java8.concurrency.reentrant;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntConsumer;

public class ZeroEvenOdd{

    private int n;
        private final int ZERO = 0;
        private final int EVEN = 1;
        private final int ODD = 2;

        private volatile int state = ZERO;

        private Lock lock = new ReentrantLock();
        private Condition zero = lock.newCondition();
        private Condition even = lock.newCondition();
        private Condition odd = lock.newCondition();

        public ZeroEvenOdd(int n) {
            this.n = n;
        }

        public void zero(IntConsumer printNumber) throws InterruptedException {
            for (int i = 0; i < n; i++) {
                lock.lock();
                try {
                    if (state != ZERO) {
                        zero.await();
                    }
                    printNumber.accept(0);
                    if (i % 2 == 0) {
                        state = ODD;
                        odd.signal();
                    } else {
                        state = EVEN;
                        even.signal();
                    }
                } finally {
                    lock.unlock();
                }
            }
        }

        public void even(IntConsumer printNumber) throws InterruptedException {
            for (int i = 2; i <= n; i += 2) {
                lock.lock();
                try {
                    if (state != EVEN) {
                        even.await();
                    }
                    state = ZERO;
                    printNumber.accept(i);
                    zero.signal();
                } finally {
                    lock.unlock();
                }
            }
        }

        public void odd(IntConsumer printNumber) throws InterruptedException {
            for (int i = 1; i <= n; i += 2) {
                lock.lock();
                try {
                    if (state != ODD) {
                        odd.await();
                    }
                    state = ZERO;
                    printNumber.accept(i);
                    zero.signal();
                } finally {
                    lock.unlock();
                }
            }
        }

    public static void main(String[] args) {
        ZeroEvenOdd zeo = new ZeroEvenOdd(10);
        PrintConsumer p =new PrintConsumer();
        try {
            zeo.zero(p);
            zeo.odd(p);
            zeo.even(p);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
