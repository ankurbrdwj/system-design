package com.ankur.design.hft.trading;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * POSIX Threading → java.util.concurrent
 *
 * Direct mapping from the README:
 *
 *   pthread_create          → new Thread() / ExecutorService
 *   pthread_mutex_t         → ReentrantLock  or  synchronized
 *   Condition variables     → Condition  (lock.newCondition())
 *   std::atomic<int>        → AtomicInteger
 *   memory_order_seq_cst    → volatile  (full sequential consistency)
 *   memory_order_release    → AtomicLong.lazySet()  (store-release)
 *
 * The Java Memory Model (JMM) is modelled after the C++ memory model —
 * concepts translate directly, only the syntax differs.
 */
public class PosixThreadingToJUCDemo {

    // =========================================================================
    // 1. pthread_create → new Thread() / ExecutorService
    // =========================================================================
    static void threadCreationDemo() throws InterruptedException {
        System.out.println("--- pthread_create → Thread / ExecutorService ---");

        // Raw thread (like pthread_create)
        Thread t = new Thread(() -> System.out.println("  [raw thread] running"), "worker-1");
        t.start();
        t.join(); // pthread_join

        // Preferred in HFT: fixed thread pool (threads pre-created, avoid creation overhead)
        ExecutorService pool = Executors.newFixedThreadPool(4,
                r -> { Thread th = new Thread(r); th.setDaemon(true); return th; });

        CountDownLatch done = new CountDownLatch(4);
        for (int i = 0; i < 4; i++) {
            int id = i;
            pool.submit(() -> {
                System.out.printf("  [pool thread-%d] running on %s%n", id, Thread.currentThread().getName());
                done.countDown();
            });
        }
        done.await();
        pool.shutdown();
        System.out.println();
    }

    // =========================================================================
    // 2. pthread_mutex_t → ReentrantLock  vs  synchronized
    //    ReentrantLock gives you try-lock, timed-lock, interruptible-lock —
    //    closer to C++ std::unique_lock with try_lock_for().
    // =========================================================================
    static void mutexDemo() throws InterruptedException {
        System.out.println("--- pthread_mutex_t → ReentrantLock ---");

        ReentrantLock mutex = new ReentrantLock();
        long[] sharedCounter = {0};

        Runnable task = () -> {
            for (int i = 0; i < 100_000; i++) {
                mutex.lock();           // pthread_mutex_lock
                try {
                    sharedCounter[0]++;
                } finally {
                    mutex.unlock();     // pthread_mutex_unlock
                }
            }
        };

        Thread t1 = new Thread(task, "mutex-t1");
        Thread t2 = new Thread(task, "mutex-t2");
        t1.start(); t2.start();
        t1.join();  t2.join();

        System.out.printf("  ReentrantLock: counter=%,d (expected %,d) correct=%b%n%n",
                sharedCounter[0], 200_000L, sharedCounter[0] == 200_000L);
    }

    // =========================================================================
    // 3. Condition variables → Condition (lock.newCondition())
    //    C++: std::condition_variable cv; cv.wait(lock, predicate); cv.notify_one()
    //    Java: Condition cv = lock.newCondition(); cv.await(); cv.signal()
    // =========================================================================
    static void conditionVariableDemo() throws InterruptedException {
        System.out.println("--- Condition variables → Condition ---");

        ReentrantLock lock   = new ReentrantLock();
        Condition     hasData = lock.newCondition();
        Condition     hasRoom = lock.newCondition();

        final int CAPACITY = 5;
        ArrayDeque<Integer> buffer = new ArrayDeque<>(CAPACITY);
        CountDownLatch done = new CountDownLatch(1);

        // Producer: signal hasData after each put; wait on hasRoom when full
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    lock.lock();
                    try {
                        while (buffer.size() == CAPACITY) hasRoom.await(); // pthread_cond_wait
                        buffer.offer(i);
                        System.out.printf("  [producer] put %2d  (buffer size=%d)%n", i, buffer.size());
                        hasData.signal(); // pthread_cond_signal
                    } finally { lock.unlock(); }
                    Thread.sleep(20);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "producer");

        // Consumer: wait on hasData; signal hasRoom after each take
        Thread consumer = new Thread(() -> {
            try {
                int consumed = 0;
                while (consumed < 10) {
                    lock.lock();
                    try {
                        while (buffer.isEmpty()) hasData.await(); // pthread_cond_wait
                        int v = buffer.poll();
                        System.out.printf("  [consumer] got %2d  (buffer size=%d)%n", v, buffer.size());
                        hasRoom.signal(); // pthread_cond_signal
                        consumed++;
                    } finally {
                        lock.unlock();
                    }
                }
                done.countDown();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "consumer");

        producer.start();
        consumer.start();
        done.await();
        producer.join();
        consumer.join();
        System.out.println();
    }

    // =========================================================================
    // 4. std::atomic<int> → AtomicInteger
    //    AtomicInteger wraps CAS (compare-and-swap) CPU instructions (LOCK CMPXCHG on x86)
    //    No mutex needed for single-variable updates.
    // =========================================================================
    static void atomicDemo() throws InterruptedException {
        System.out.println("--- std::atomic<int> → AtomicInteger ---");

        AtomicInteger counter = new AtomicInteger(0);

        // 10 threads each increment 100k times — should yield exactly 1,000,000
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100_000; j++) counter.incrementAndGet(); // LOCK XADD
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();

        System.out.printf("  AtomicInteger: count=%,d (expected 1,000,000) correct=%b%n%n",
                counter.get(), counter.get() == 1_000_000);
    }

    // =========================================================================
    // 5. volatile: Java's equivalent of std::atomic with memory_order_seq_cst
    //    Guarantees visibility across threads (no CPU/compiler reordering).
    //    lazySet() on AtomicLong ≈ memory_order_release (weaker, faster).
    // =========================================================================
    static void volatileDemo() throws InterruptedException {
        System.out.println("--- volatile / lazySet  →  memory ordering ---");

        // volatile flag: visible across threads immediately after write
        // (Without volatile the reader thread might cache-register the value)
        volatile_example_inner();

        // lazySet: ordered store without full fence — used in ring buffer producers
        AtomicLong cursor = new AtomicLong(-1);
        long[] ring = new long[8];
        ring[0] = 42L;
        // memory_order_release: guarantees ring[0]=42 is visible before cursor=0
        cursor.lazySet(0);  // cheaper than set() — no StoreLoad fence
        System.out.printf("  lazySet cursor: %d  ring[0]=%d%n%n", cursor.get(), ring[0]);
    }

    private static volatile boolean stopFlag = false;

    private static void volatile_example_inner() throws InterruptedException {
        Thread reader = new Thread(() -> {
            int spins = 0;
            while (!stopFlag) spins++;  // volatile read on every iteration
            System.out.printf("  volatile flag observed after %,d spins%n", spins);
        });
        reader.start();
        Thread.sleep(10);
        stopFlag = true;  // volatile write: immediately visible to reader
        reader.join();
    }

    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("====================================================");
        System.out.println("  POSIX Threading → java.util.concurrent");
        System.out.println("====================================================\n");
        threadCreationDemo();
        mutexDemo();
        conditionVariableDemo();
        atomicDemo();
        volatileDemo();
    }
}