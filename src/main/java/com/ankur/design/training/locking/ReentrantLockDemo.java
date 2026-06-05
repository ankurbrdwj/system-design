package com.ankur.design.training.locking;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ReentrantLock: every API, the lock/object relationship, and antipatterns.
 *
 * KEY MENTAL MODEL:
 *   synchronized   → lock lives ON the object (every Java object has a monitor)
 *   ReentrantLock  → lock IS an object; you pick what it guards by convention
 *
 * The lock does NOT protect "the object". It protects the CODE SECTION
 * between lock() and unlock(). It is YOUR job to call both consistently.
 */
public class ReentrantLockDemo {

    // ─────────────────────────────────────────────────────────────────
    // 1. BASIC LOCK / UNLOCK
    //    When: default choice when synchronized can't do the job
    //    Rule: ALWAYS unlock() in a finally block — no exceptions
    // ─────────────────────────────────────────────────────────────────
    static class Counter {
        private final ReentrantLock lock = new ReentrantLock();
        private int count = 0;

        public void increment() {
            lock.lock();          // blocks until acquired; NOT interruptible
            try {
                count++;          // critical section
            } finally {
                lock.unlock();    // MUST be here — exception still runs finally
            }
        }

        public int get() { return count; }
    }


    // ─────────────────────────────────────────────────────────────────
    // 2. tryLock() — non-blocking
    //    When: you'd rather skip work than wait (e.g. metrics sampling,
    //          progress bar updates, optional cache refresh)
    //    Returns: true if acquired, false if contended RIGHT NOW
    // ─────────────────────────────────────────────────────────────────
    static class OptionalUpdater {
        private final ReentrantLock lock = new ReentrantLock();
        private int cache = 0;

        public boolean tryRefreshCache(int newValue) {
            if (lock.tryLock()) {           // non-blocking — returns immediately
                try {
                    cache = newValue;
                    return true;
                } finally {
                    lock.unlock();          // only unlock if tryLock returned true
                }
            }
            return false;                   // someone else holds it — skip this cycle
        }
    }


    // ─────────────────────────────────────────────────────────────────
    // 3. tryLock(timeout, unit) — timed wait
    //    When: you want to wait, but not forever (e.g. DB connection pool,
    //          resource acquisition with SLA)
    //    Throws: InterruptedException if interrupted while waiting
    // ─────────────────────────────────────────────────────────────────
    static class TimedResource {
        private final ReentrantLock lock = new ReentrantLock();

        public boolean use(long timeoutMs) throws InterruptedException {
            if (lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
                try {
                    System.out.println("Got the resource, doing work");
                    return true;
                } finally {
                    lock.unlock();
                }
            }
            System.out.println("Timeout — gave up after " + timeoutMs + "ms");
            return false;
        }
    }


    // ─────────────────────────────────────────────────────────────────
    // 4. lockInterruptibly()
    //    When: long-running wait that must be cancellable (e.g. shutdown hooks,
    //          task cancellation, responsive UI)
    //    Difference from lock(): lock() IGNORES interrupt; this one throws
    // ─────────────────────────────────────────────────────────────────
    static class CancellableTask {
        private final ReentrantLock lock = new ReentrantLock();

        public void run() throws InterruptedException {
            lock.lockInterruptibly();   // throws if Thread.interrupt() called while waiting
            try {
                System.out.println("Running cancellable task");
            } finally {
                lock.unlock();
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────
    // 5. REENTRANCY — same thread can re-acquire its own lock
    //    Hold count increments each lock(), must decrement each unlock()
    //    When: calling a locked method from another locked method in same class
    // ─────────────────────────────────────────────────────────────────
    static class ReentrantExample {
        private final ReentrantLock lock = new ReentrantLock();

        public void outer() {
            lock.lock();                      // holdCount = 1
            try {
                System.out.println("outer, holdCount=" + lock.getHoldCount());
                inner();                      // same thread re-enters
            } finally {
                lock.unlock();                // holdCount back to 0 → released
            }
        }

        public void inner() {
            lock.lock();                      // holdCount = 2 — NOT blocked (same thread)
            try {
                System.out.println("inner, holdCount=" + lock.getHoldCount());
            } finally {
                lock.unlock();                // holdCount = 1 — still held by outer
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────
    // 6. CONDITION VARIABLES — fine-grained wait/notify
    //    When: producer/consumer, bounded buffer, state machine gates
    //    Advantage over synchronized: multiple conditions on ONE lock
    // ─────────────────────────────────────────────────────────────────
    static class BoundedBuffer {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull  = lock.newCondition();   // condition 1
        private final Condition notEmpty = lock.newCondition();   // condition 2
        private final int[] buf = new int[10];
        private int head, tail, count;

        public void put(int item) throws InterruptedException {
            lock.lock();
            try {
                while (count == buf.length) notFull.await();  // releases lock while waiting
                buf[tail++ % buf.length] = item;
                count++;
                notEmpty.signal();   // wake ONE waiting consumer
            } finally {
                lock.unlock();
            }
        }

        public int take() throws InterruptedException {
            lock.lock();
            try {
                while (count == 0) notEmpty.await();
                int item = buf[head++ % buf.length];
                count--;
                notFull.signal();
                return item;
            } finally {
                lock.unlock();
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────
    // 7. FAIR vs UNFAIR (constructor arg)
    //    new ReentrantLock()       → unfair (default) — barging allowed
    //    new ReentrantLock(true)   → fair — strict FIFO queue
    //    Cost: fair lock ~10x lower throughput due to OS scheduling overhead
    //    Use fair ONLY when: starvation is a real measured problem
    // ─────────────────────────────────────────────────────────────────
    static final ReentrantLock UNFAIR = new ReentrantLock();       // fast, default
    static final ReentrantLock FAIR   = new ReentrantLock(true);   // FIFO, slow


    // ─────────────────────────────────────────────────────────────────
    // 8. QUERY / DIAGNOSTIC METHODS
    //    These are for monitoring and debugging, NOT for control flow
    // ─────────────────────────────────────────────────────────────────
    static void queryAPIs(ReentrantLock lock) {
        lock.isLocked();              // any thread holds it? (snapshot — stale by the time you act)
        lock.isHeldByCurrentThread(); // I hold it? — safe to call anytime
        lock.getHoldCount();          // how many times current thread locked it
        lock.getQueueLength();        // estimated waiters (approximate)
        lock.hasQueuedThreads();      // anyone waiting?
        lock.isFair();                // was it constructed fair?
    }


    // ═════════════════════════════════════════════════════════════════
    //                        A N T I P A T T E R N S
    // ═════════════════════════════════════════════════════════════════

    // ─────────────────────────────────────────────────────────────────
    // ANTIPATTERN 1: No finally → lock held forever on exception
    // ─────────────────────────────────────────────────────────────────
    static class BadNoFinally {
        private final ReentrantLock lock = new ReentrantLock();

        public void bad() {
            lock.lock();
            int x = 1 / 0;   // exception thrown → unlock() never called → DEADLOCK
            lock.unlock();    // unreachable
        }

        public void good() {
            lock.lock();
            try {
                int x = 1 / 0;
            } finally {
                lock.unlock(); // always runs
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────
    // ANTIPATTERN 2: Lock created inside method — new lock per call
    //               Every invocation gets its OWN lock → zero mutual exclusion
    // ─────────────────────────────────────────────────────────────────
    static class BadLocalLock {
        private int count = 0;

        public void bad() {
            ReentrantLock lock = new ReentrantLock(); // ← BUG: local, unique per call
            lock.lock();
            try { count++; } finally { lock.unlock(); }
            // Two threads each have a DIFFERENT lock — no protection at all
        }

        private final ReentrantLock lock = new ReentrantLock(); // ← correct: shared field
        public void good() {
            lock.lock();
            try { count++; } finally { lock.unlock(); }
        }
    }


    // ─────────────────────────────────────────────────────────────────
    // ANTIPATTERN 3: tryLock — unlock in finally without acquired check
    //               Calling unlock() when lock was never acquired → IllegalMonitorStateException
    // ─────────────────────────────────────────────────────────────────
    static class BadTryLockFinally {
        private final ReentrantLock lock = new ReentrantLock();

        public void bad() {
            boolean acquired = lock.tryLock();
            try {
                if (acquired) { /* do work */ }
            } finally {
                lock.unlock(); // ← BUG: crashes when acquired=false
            }
        }

        public void good() {
            boolean acquired = lock.tryLock();
            if (acquired) {
                try {
                    // do work
                } finally {
                    lock.unlock(); // only inside the acquired branch
                }
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────
    // ANTIPATTERN 4: isLocked() TOCTOU race — check-then-act
    //               Between isLocked()=false and lock(), another thread grabs it
    // ─────────────────────────────────────────────────────────────────
    static class BadTOCTOU {
        private final ReentrantLock lock = new ReentrantLock();

        public void bad() {
            if (!lock.isLocked()) {   // ← BUG: race condition; state may change before lock()
                lock.lock();          // another thread may have locked it in between
                try { /* work */ } finally { lock.unlock(); }
            }
        }

        public void good() {
            lock.lock();              // just acquire it — lock() handles the race atomically
            try { /* work */ } finally { lock.unlock(); }
        }
    }


    // ─────────────────────────────────────────────────────────────────
    // ANTIPATTERN 5: Deadlock — inconsistent lock ordering
    //               Thread A: locks L1 then L2
    //               Thread B: locks L2 then L1 → circular wait → deadlock
    // ─────────────────────────────────────────────────────────────────
    static final ReentrantLock L1 = new ReentrantLock();
    static final ReentrantLock L2 = new ReentrantLock();

    static void threadA_bad() {
        L1.lock(); try { L2.lock(); try { /* */ } finally { L2.unlock(); } } finally { L1.unlock(); }
    }
    static void threadB_bad() {
        L2.lock(); try { L1.lock(); try { /* */ } finally { L1.unlock(); } } finally { L2.unlock(); }
        // ↑ DEADLOCK: A holds L1 waiting for L2; B holds L2 waiting for L1
    }

    static void threadA_good() {
        L1.lock(); try { L2.lock(); try { /* */ } finally { L2.unlock(); } } finally { L1.unlock(); }
    }
    static void threadB_good() {
        L1.lock(); try { L2.lock(); try { /* */ } finally { L2.unlock(); } } finally { L1.unlock(); }
        // ↑ SAME ORDER always: L1 → L2 → no circular wait
    }


    // ─────────────────────────────────────────────────────────────────
    // ANTIPATTERN 6: Condition from a DIFFERENT lock
    //               await()/signal() must be called while holding the lock
    //               that produced the Condition — otherwise IllegalMonitorStateException
    // ─────────────────────────────────────────────────────────────────
    static class BadCondition {
        private final ReentrantLock lockA = new ReentrantLock();
        private final ReentrantLock lockB = new ReentrantLock();
        private final Condition condFromB = lockB.newCondition(); // ← belongs to lockB

        public void bad() throws InterruptedException {
            lockA.lock();
            try {
                condFromB.await(); // ← BUG: IllegalMonitorStateException — wrong lock
            } finally {
                lockA.unlock();
            }
        }

        public void good() throws InterruptedException {
            lockB.lock();
            try {
                condFromB.await(); // ← correct: holding the lock that owns the condition
            } finally {
                lockB.unlock();
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────
    // ANTIPATTERN 7: Unbalanced reentrancy — lock N times, unlock N-1 times
    //               Lock stays held after method returns → every other thread blocked
    // ─────────────────────────────────────────────────────────────────
    static class BadReentrantCount {
        private final ReentrantLock lock = new ReentrantLock();

        public void bad() {
            lock.lock();         // holdCount = 1
            lock.lock();         // holdCount = 2
            try {
                // work
            } finally {
                lock.unlock();   // holdCount = 1 — lock still held! other threads starve
            }
        }

        public void good() {
            lock.lock();
            lock.lock();
            try {
                // work
            } finally {
                lock.unlock();   // holdCount = 1
                lock.unlock();   // holdCount = 0 — released
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────
    // ANTIPATTERN 8: Fair lock used by default "to be safe"
    //               Fair = threads queue in arrival order = OS park/unpark for every acquire
    //               Result: ~10x throughput drop with no starvation benefit in most workloads
    // ─────────────────────────────────────────────────────────────────
    static class BadDefaultFair {
        private final ReentrantLock lock = new ReentrantLock(true); // ← BUG: premature fairness
        // Use fair ONLY after measuring actual starvation, not "just in case"
    }


    // ─────────────────────────────────────────────────────────────────
    // SUMMARY: which API to reach for
    //
    //  lock()                 → default; need guaranteed acquisition, not cancellable
    //  tryLock()              → skip-if-busy; optional work, metrics, cache refresh
    //  tryLock(t, unit)       → wait with deadline; SLA-bound resource acquisition
    //  lockInterruptibly()    → cancellable wait; shutdown hooks, task cancellation
    //  newCondition()         → producer/consumer, bounded buffer, state gates
    //  new ReentrantLock(true)→ ONLY when you've measured starvation
    //
    //  isHeldByCurrentThread()→ assertions / guard checks in recursive code
    //  getHoldCount()         → debug reentrancy bugs
    //  isLocked()/getQueueLength() → monitoring only, NEVER for control flow
    // ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        Counter c = new Counter();
        for (int i = 0; i < 5; i++) c.increment();
        System.out.println("count=" + c.get());

        ReentrantExample re = new ReentrantExample();
        re.outer();

        TimedResource tr = new TimedResource();
        tr.use(100);
    }
}