package com.ankur.design.hft.interview;

import java.io.*;
import java.lang.reflect.Constructor;

/**
 * Singleton patterns — four approaches, why each exists.
 *
 * The problem: guarantee exactly one instance across all threads.
 *
 * BROKEN without volatile:
 *   Thread A: instance = new Singleton()
 *     step 1: allocate memory
 *     step 2: write reference to `instance`   ← CPU may reorder 2 before 3
 *     step 3: call constructor
 *   Thread B: sees instance != null (step 2 done) but object not yet constructed (step 3 pending)
 *   → Thread B uses a half-constructed object → undefined behaviour
 *
 * volatile fix:
 *   volatile write has store-release semantics — step 3 (constructor) is
 *   guaranteed to complete BEFORE step 2 (reference visible to other threads).
 *   Thread B's volatile read has load-acquire — sees fully constructed object.
 */
public class SingletonPatterns {

    // ── 1. Eager initialisation — simplest, always correct ────────────────────
    // JVM class-loading is thread-safe: static fields initialised once, atomically.
    // Downside: created even if never used.
    static class EagerSingleton {
        private static final EagerSingleton INSTANCE = new EagerSingleton();
        private EagerSingleton() {}
        static EagerSingleton getInstance() { return INSTANCE; }
    }

    // ── 2. Double-checked locking — lazy + thread-safe ────────────────────────
    //
    // WHY double-checked:
    //   First check (outside sync): avoids acquiring the lock on every call
    //   after the instance is already created — the common case is free.
    //   Second check (inside sync): guards against two threads both passing
    //   the first check before either creates the instance.
    //
    // WHY volatile is REQUIRED:
    //   Without volatile, the CPU/compiler can reorder:
    //     instance = <ref>   (publish reference)  BEFORE
    //     <constructor runs> (initialise fields)
    //   Another thread sees instance != null but fields are zeroed.
    //
    //   volatile write:  happens-before any subsequent volatile read
    //   → constructor completes → volatile write → thread B's volatile read
    //   → thread B sees fully initialised object
    //
    // WHY synchronized on the CLASS object:
    //   synchronized(LazyDclSingleton.class) uses the Class object as the monitor.
    //   It is a stable, always-available lock (RULE 7 from old MyCode).
    //   All threads share the same Class object → mutual exclusion guaranteed.
    static class LazyDclSingleton {
        private static volatile LazyDclSingleton instance;  // volatile is MANDATORY

        private final String config;

        private LazyDclSingleton() {
            this.config = "initialised";   // constructor work visible after volatile write
        }

        static LazyDclSingleton getInstance() {
            if (instance == null) {                         // check 1: no lock, fast path
                synchronized (LazyDclSingleton.class) {    // lock: only first N threads
                    if (instance == null) {                 // check 2: inside lock, safe
                        instance = new LazyDclSingleton(); // volatile write — happens-after constructor
                    }
                }
            }
            return instance;                               // volatile read — sees full object
        }

        String getConfig() { return config; }
    }

    // ── 3. Initialisation-on-demand holder — lazy, no volatile, no sync ───────
    //
    // Exploits JVM class-loading guarantee:
    //   A class is initialised exactly once, by exactly one thread,
    //   before any thread can access its static fields.
    //   All other threads block until initialisation is complete.
    //
    // Holder class is NOT loaded until getInstance() is first called.
    // → lazy: zero cost until first use.
    // → thread-safe: JVM class-loader provides the lock.
    // → no volatile, no synchronized on the hot path.
    //
    // This is the PREFERRED pattern when eager init is not acceptable.
    static class HolderSingleton {
        private HolderSingleton() {}

        private static class Holder {
            // loaded when Holder is first accessed — JVM guarantees single init
            static final HolderSingleton INSTANCE = new HolderSingleton();
        }

        static HolderSingleton getInstance() {
            return Holder.INSTANCE;   // triggers Holder class-load on first call only
        }
    }

    // ── 5. Synchronized method — simplest thread-safe lazy init ──────────────
    //
    // HOW IT WORKS:
    //   `static synchronized` locks on the Class object (same monitor as
    //   synchronized(SynchronizedMethodSingleton.class)).
    //   Guarantees only one thread executes getInstance() at a time.
    //
    // WHY IT'S SLOW:
    //   Every call acquires the lock — even after the instance is created.
    //   Hot path: lock → null check → unlock.  DCL avoids this after first init.
    //   Under 10 threads hammering it, throughput drops vs DCL.
    //
    // CORRECT but not production-grade for high-frequency access.
    static class SynchronizedMethodSingleton {
        private static SynchronizedMethodSingleton instance;

        private SynchronizedMethodSingleton() {}

        static synchronized SynchronizedMethodSingleton getInstance() {
            if (instance == null) {
                instance = new SynchronizedMethodSingleton();
            }
            return instance;
        }
    }

    // ── HOW TO BREAK a synchronized-method singleton ─────────────────────────
    //
    // Two classic attacks (both work against ANY singleton except enum):
    //
    //  ATTACK 1 — Reflection:
    //    Constructor is private but reflection ignores access modifiers.
    //    setAccessible(true) bypasses the access check → new instance created.
    //    Fix: throw IllegalStateException inside the constructor if instance != null.
    //
    //  ATTACK 2 — Serialization:
    //    ObjectInputStream.readObject() bypasses the constructor entirely;
    //    it allocates a new object directly via JVM internals.
    //    Every deserialize() call produces a new instance.
    //    Fix: add `readResolve()` — ObjectInputStream calls it after deserialization
    //    and uses its return value instead of the freshly-allocated object.
    static class BreakableSingleton implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private static BreakableSingleton instance;

        private BreakableSingleton() {}

        static synchronized BreakableSingleton getInstance() {
            if (instance == null) instance = new BreakableSingleton();
            return instance;
        }

        // ── uncomment to FIX the serialization break ──────────────────────────
        // @Serial
        // protected Object readResolve() { return getInstance(); }
    }

    // ── 4. Enum singleton — Josh Bloch's recommendation ──────────────────────
    // JVM guarantees enum constants are singletons — serialisation-safe too.
    // Cannot extend a class (but can implement interfaces).
    enum EnumSingleton {
        INSTANCE;
        private final String config = "enum-singleton";
        String getConfig() { return config; }
    }

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("=== Singleton Patterns ===\n");

        // ── verify DCL returns same instance under concurrency ─────────────────
        System.out.println("--- Double-Checked Locking: 10 threads racing ---");
        Thread[] threads = new Thread[10];
        int[] identities = new int[10];

        for (int i = 0; i < 10; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                LazyDclSingleton s = LazyDclSingleton.getInstance();
                identities[idx] = System.identityHashCode(s);
                System.out.printf("  [T%-2d] instance@%d  config=%s%n",
                        idx, identities[idx], s.getConfig());
            });
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        long distinct = java.util.Arrays.stream(identities).distinct().count();
        System.out.println("  Distinct instances: " + distinct + "  (must be 1)\n");

        // ── verify holder pattern ──────────────────────────────────────────────
        System.out.println("--- Holder (init-on-demand): same check ---");
        HolderSingleton h1 = HolderSingleton.getInstance();
        HolderSingleton h2 = HolderSingleton.getInstance();
        System.out.println("  h1 == h2: " + (h1 == h2) + "\n");

        // ── enum ──────────────────────────────────────────────────────────────
        System.out.println("--- Enum singleton ---");
        System.out.println("  " + EnumSingleton.INSTANCE.getConfig());

        // ── ATTACK 1: Reflection break ────────────────────────────────────────
        System.out.println("\n--- ATTACK 1: Reflection breaks synchronized-method singleton ---");
        BreakableSingleton normal = BreakableSingleton.getInstance();

        Constructor<BreakableSingleton> ctor =
                BreakableSingleton.class.getDeclaredConstructor();
        ctor.setAccessible(true);                        // bypass private
        BreakableSingleton reflected = ctor.newInstance();

        System.out.println("  normal   @" + System.identityHashCode(normal));
        System.out.println("  reflected@" + System.identityHashCode(reflected));
        System.out.println("  same instance? " + (normal == reflected));   // false — BROKEN
        System.out.println("  FIX: throw IllegalStateException in constructor if instance != null");

        // ── ATTACK 2: Serialization break ─────────────────────────────────────
        System.out.println("\n--- ATTACK 2: Serialization breaks synchronized-method singleton ---");
        BreakableSingleton original = BreakableSingleton.getInstance();

        // serialize to bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(original);

        // deserialize — bypasses constructor, allocates a new object
        BreakableSingleton deserialized = (BreakableSingleton)
                new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray())).readObject();

        System.out.println("  original    @" + System.identityHashCode(original));
        System.out.println("  deserialized@" + System.identityHashCode(deserialized));
        System.out.println("  same instance? " + (original == deserialized));  // false — BROKEN
        System.out.println("  FIX: add `protected Object readResolve() { return getInstance(); }`");

        // ── comparison ────────────────────────────────────────────────────────
        System.out.println("\n=== When to use which ===");
        System.out.println("  Eager     → always safe, use when startup cost is acceptable");
        System.out.println("  DCL       → lazy, volatile mandatory, good for interview demos");
        System.out.println("  Holder    → lazy, cleanest, preferred in production");
        System.out.println("  Enum      → serialisation-safe, simplest, Bloch's recommendation");
        System.out.println("  Sync mthd → simplest lazy+safe, but slow (lock on every call)");
    }
}