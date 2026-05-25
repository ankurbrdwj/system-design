package com.ankur.design.multithreaded.jmm;

/**
 * CONCEPT 1 — Thread Stack vs Heap
 *
 * Thread Stack (private per thread):
 *   - primitive local variables
 *   - references to objects (the pointer, not the object itself)
 *   - method call frames
 *   Never visible to another thread.
 *
 * Heap (shared by all threads):
 *   - every object created with `new`
 *   - instance fields of those objects
 *   - static fields
 *
 * Diagram:
 *
 *   Thread-1 Stack          Thread-2 Stack
 *   ┌─────────────┐         ┌─────────────┐
 *   │ localA = 5  │         │ localA = 9  │  ← same name, different slot, never shared
 *   │ ref ──────────────┐   │ ref ────────────┐
 *   └─────────────┘     │   └─────────────┘  │
 *                        │                    │
 *   ─────────────────── HEAP ──────────────────
 *                        ▼                    ▼
 *                   ┌─────────┐         ┌─────────┐
 *                   │ obj1    │         │ obj2    │   ← separate objects
 *                   └─────────┘         └─────────┘
 *
 *   OR both refs point to the same heap object → shared state → concurrency risk
 */
public class A_ThreadStackVsHeap {

    // lives on the HEAP — shared if multiple threads reach the same instance
    private int instanceField = 0;

    void demonstrate() throws InterruptedException {
        // ── local primitive — lives on EACH thread's stack, never shared ──────
        int localPrimitive = 42;   // Thread-1 has its own copy, Thread-2 has its own

        // ── object on heap — ref is on stack, object body is on heap ──────────
        StringBuilder localObj = new StringBuilder();  // ref on stack, body on heap
                                                        // but no other thread holds this ref
                                                        // so it's effectively thread-local

        // ── shared object — two threads, one object ───────────────────────────
        Counter shared = new Counter();   // one Counter on the heap

        Thread t1 = new Thread(() -> {
            int myLocal = 10;           // ← stack of t1, invisible to t2
            shared.value += myLocal;    // ← heap write, visible to t2 (unsynchronized!)
        });

        Thread t2 = new Thread(() -> {
            int myLocal = 20;           // ← stack of t2, invisible to t1
            shared.value += myLocal;    // ← heap write, races with t1
        });

        t1.start(); t2.start();
        t1.join();  t2.join();

        // expected 30, but may be 10 or 20 due to race — demonstrated in later examples
        System.out.println("shared.value = " + shared.value + "  (expected 30, may differ)");
        System.out.println("localPrimitive = " + localPrimitive + "  (always 42 — stack)");
    }

    static class Counter { int value = 0; }

    public static void main(String[] args) throws InterruptedException {
        new A_ThreadStackVsHeap().demonstrate();
    }
}