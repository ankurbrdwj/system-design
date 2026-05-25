package com.ankur.design.multithreaded.coordination;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded blocking queue built from scratch.
 *
 * Internals:
 *   - Circular array (ring buffer) — O(1) enqueue and dequeue, no node allocation
 *   - One ReentrantLock guards both head and tail
 *   - Two Conditions: notFull (producer waits here), notEmpty (consumer waits here)
 *
 * Why two Conditions on one lock?
 *   A single lock protects the shared array.
 *   Two Conditions let us wake ONLY the right party:
 *     - after put()  → signal notEmpty  (wake a waiting consumer)
 *     - after take() → signal notFull   (wake a waiting producer)
 *   With one Condition we'd broadcast to everyone and they'd re-check + re-sleep — wasteful.
 *
 * Why a circular array, not LinkedList?
 *   - No per-element node allocation → less GC pressure
 *   - Contiguous memory → better cache locality
 *   - head/tail move by index arithmetic, never pointer chasing
 */
public class BoundedBlockingQueue<T> {

    private final Object[]      items;       // circular ring buffer
    private final int           capacity;

    private int head = 0;                    // next dequeue index
    private int tail = 0;                    // next enqueue index
    private int count = 0;                   // current number of elements

    private final ReentrantLock lock     = new ReentrantLock();
    private final Condition     notFull  = lock.newCondition();  // producer waits here
    private final Condition     notEmpty = lock.newCondition();  // consumer waits here

    public BoundedBlockingQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        this.items    = new Object[capacity];
    }

    // ── Core blocking API ────────────────────────────────────────────────────

    /**
     * Inserts element, blocking until space is available.
     */
    public void put(T item) throws InterruptedException {
        if (item == null) throw new NullPointerException();
        lock.lockInterruptibly();
        try {
            // while — not if — re-check after spurious wakeup
            while (count == capacity) {
                notFull.await();
            }
            enqueue(item);
            notEmpty.signal();   // wake exactly one waiting consumer
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes head, blocking until an element is available.
     */
    public T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (count == 0) {
                notEmpty.await();
            }
            T item = dequeue();
            notFull.signal();    // wake exactly one waiting producer
            return item;
        } finally {
            lock.unlock();
        }
    }

    // ── Timed variants ───────────────────────────────────────────────────────

    /**
     * Inserts element, waiting up to timeout. Returns false if timed out.
     */
    public boolean offer(T item, long timeout, TimeUnit unit) throws InterruptedException {
        if (item == null) throw new NullPointerException();
        long remainingNanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (count == capacity) {
                if (remainingNanos <= 0) return false;
                remainingNanos = notFull.awaitNanos(remainingNanos); // returns remaining
            }
            enqueue(item);
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves head, waiting up to timeout. Returns null if timed out.
     */
    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        long remainingNanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (count == 0) {
                if (remainingNanos <= 0) return null;
                remainingNanos = notEmpty.awaitNanos(remainingNanos);
            }
            T item = dequeue();
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    // ── Non-blocking variants ────────────────────────────────────────────────

    /** Inserts if space available immediately, else returns false. */
    public boolean offer(T item) {
        if (item == null) throw new NullPointerException();
        lock.lock();
        try {
            if (count == capacity) return false;
            enqueue(item);
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Removes head if available immediately, else returns null. */
    public T poll() {
        lock.lock();
        try {
            if (count == 0) return null;
            T item = dequeue();
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    /** Retrieves head without removing. Returns null if empty. */
    public T peek() {
        lock.lock();
        try {
            return count == 0 ? null : itemAt(head);
        } finally {
            lock.unlock();
        }
    }

    // ── State queries ────────────────────────────────────────────────────────

    public int size()              { lock.lock(); try { return count; }    finally { lock.unlock(); } }
    public boolean isEmpty()       { lock.lock(); try { return count == 0; } finally { lock.unlock(); } }
    public int remainingCapacity() { lock.lock(); try { return capacity - count; } finally { lock.unlock(); } }

    // ── Internal ring buffer mechanics ───────────────────────────────────────

    /** Must be called with lock held. */
    private void enqueue(T item) {
        items[tail] = item;
        tail = advance(tail);   // wrap around
        count++;
    }

    /** Must be called with lock held. */
    @SuppressWarnings("unchecked")
    private T dequeue() {
        T item = (T) items[head];
        items[head] = null;     // help GC — don't hold stale reference
        head = advance(head);
        count--;
        return item;
    }

    @SuppressWarnings("unchecked")
    private T itemAt(int index) { return (T) items[index]; }

    /** Increment index, wrapping at capacity. Equivalent to (i + 1) % capacity. */
    private int advance(int index) {
        return (index + 1 == capacity) ? 0 : index + 1;
    }

    @Override
    public String toString() {
        lock.lock();
        try {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0, idx = head; i < count; i++, idx = advance(idx)) {
                if (i > 0) sb.append(", ");
                sb.append(items[idx]);
            }
            return sb.append(']').toString();
        } finally {
            lock.unlock();
        }
    }

    // ── Demo ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        BoundedBlockingQueue<Integer> q = new BoundedBlockingQueue<>(3);

        // Producer: puts 6 items into a queue of capacity 3 — will block after 3
        Thread producer = Thread.ofPlatform().name("producer").start(() -> {
            try {
                for (int i = 1; i <= 6; i++) {
                    System.out.println("[P] putting " + i);
                    q.put(i);
                    System.out.println("[P] put done: " + i + "  size=" + q.size());
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        Thread.sleep(50); // let producer fill and block

        // Consumer: drains slowly
        Thread consumer = Thread.ofPlatform().name("consumer").start(() -> {
            try {
                for (int i = 1; i <= 6; i++) {
                    Thread.sleep(100);
                    Integer val = q.take();
                    System.out.println("[C] took " + val + "  size=" + q.size());
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        producer.join();
        consumer.join();
        System.out.println("Done. Queue: " + q);
    }
}