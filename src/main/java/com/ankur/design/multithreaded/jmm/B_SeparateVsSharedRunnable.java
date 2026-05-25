package com.ankur.design.multithreaded.jmm;

/**
 * CONCEPT 2 — Separate Runnable vs Shared Runnable
 *
 * Case A — each thread has its OWN Runnable instance
 *   → each has its own `count` field on the heap
 *   → no sharing, no race, always correct
 *
 * Case B — both threads share ONE Runnable instance
 *   → both threads write to the same `count` field on the heap
 *   → race condition: final count will be less than 200,000
 *
 * Diagram for Case B:
 *
 *   Thread-1 Stack          Thread-2 Stack
 *   ┌───────────────┐       ┌───────────────┐
 *   │ runnableRef ──────┐   │ runnableRef ──────┐
 *   └───────────────┘   │   └───────────────┘   │
 *                        └──────► SharedTask ◄──┘
 *                                 count = ???    ← both threads write here
 */
public class B_SeparateVsSharedRunnable {

    static class CountingTask implements Runnable {
        int count = 0;                      // instance field — on the heap

        @Override
        public void run() {
            for (int i = 0; i < 100_000; i++) {
                count++;                    // read-increment-write: NOT atomic
            }
        }
    }

    // ── Case A: separate instances ────────────────────────────────────────────
    static void caseA() throws InterruptedException {
        CountingTask task1 = new CountingTask();   // separate object on heap
        CountingTask task2 = new CountingTask();   // separate object on heap

        Thread t1 = new Thread(task1);
        Thread t2 = new Thread(task2);
        t1.start(); t2.start();
        t1.join();  t2.join();

        System.out.println("Case A (separate): task1.count=" + task1.count
                + "  task2.count=" + task2.count
                + "  (each always 100,000 — no sharing)");
    }

    // ── Case B: shared instance ───────────────────────────────────────────────
    static void caseB() throws InterruptedException {
        CountingTask shared = new CountingTask();   // ONE object, shared by both threads

        Thread t1 = new Thread(shared);
        Thread t2 = new Thread(shared);
        t1.start(); t2.start();
        t1.join();  t2.join();

        System.out.println("Case B (shared):   shared.count=" + shared.count
                + "  (expected 200,000 — actual is less due to race)");
    }

    // ── Case C: object created INSIDE run() ──────────────────────────────────
    // Even with a shared Runnable, objects newed inside run() are local to each thread.
    static class TaskWithLocalObject implements Runnable {
        @Override
        public void run() {
            StringBuilder sb = new StringBuilder();   // new object per run() call
            sb.append(Thread.currentThread().getName());
            // sb lives on the heap but no other thread holds a reference to it
            // so it is effectively thread-confined — safe to use without sync
            System.out.println("Case C local object: " + sb);
        }
    }

    static void caseC() throws InterruptedException {
        TaskWithLocalObject shared = new TaskWithLocalObject();
        Thread t1 = new Thread(shared, "T1");
        Thread t2 = new Thread(shared, "T2");
        t1.start(); t2.start();
        t1.join();  t2.join();
    }

    public static void main(String[] args) throws InterruptedException {
        caseA();
        caseB();
        caseC();
    }
}