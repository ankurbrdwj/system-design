package com.ankur.design.hft.optimizingjvm;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * LESSON 6 — Lambda allocation: capturing vs non-capturing
 *
 * The talk distinguishes two lambda categories:
 *
 * NON-CAPTURING lambda (stateless):
 *   Does not reference any variable from the enclosing scope.
 *   JVM creates ONE singleton instance for the entire JVM lifetime.
 *   Zero allocation per call.
 *
 *   example:  list.forEach(s -> System.out.println(s))
 *             list.forEach(System.out::println)    // method reference, same rule
 *
 * CAPTURING lambda (closes over a variable):
 *   References a local variable or instance field from the enclosing scope.
 *   JVM must create a new object to hold the captured state.
 *   One allocation per lambda creation site evaluation.
 *
 *   example:  String prefix = ">>>";
 *             list.forEach(s -> System.out.println(prefix + s))  // captures prefix
 *
 * SUBTLETY the talk highlights — method reference vs capturing lambda:
 *
 *   System.out::println
 *     Captures the `out` PrintStream object once at the call site.
 *     BUT: each time this expression is evaluated, a new lambda object is created
 *     wrapping `out`. The talk notes this can be surprising.
 *
 *   s -> System.out.println(s)
 *     Non-capturing (System.out is a static field access, not a local variable).
 *     JVM can cache this as a singleton.
 *
 * HOW TO VERIFY:
 *   Use -XX:+PrintGCDetails or async-profiler's alloc mode to see lambda allocations.
 *   Or use the trick below: System.identityHashCode() to detect new vs same object.
 *
 * PRACTICAL RULE for hot paths:
 *   - Never create lambdas inside a tight loop
 *   - Assign the lambda to a final field once, reuse it
 *   - Prefer non-capturing lambdas — they become singletons
 */
public class OPT6_LambdaAllocation {

    static final List<Integer> DATA = IntStream.range(0, 5).boxed().toList();

    // ── Case 1: non-capturing — JVM makes a singleton ─────────────────────────
    static void nonCapturing() {
        System.out.println("\n--- Non-capturing lambda (singleton) ---");
        Consumer<Integer> c1 = i -> System.out.print(i + " ");
        Consumer<Integer> c2 = i -> System.out.print(i + " ");
        // same lambda expression text → JVM may share the same instance (indy bootstrap)
        System.out.println("c1 identity: " + System.identityHashCode(c1));
        System.out.println("c2 identity: " + System.identityHashCode(c2));
        System.out.println("same instance: " + (c1 == c2));   // often true after JIT
        DATA.forEach(c1);
        System.out.println();
    }

    // ── Case 2: capturing — new object each time ──────────────────────────────
    static void capturing() {
        System.out.println("\n--- Capturing lambda (new object each evaluation) ---");
        String prefix = "item-";
        // captures `prefix` from enclosing scope → new lambda object
        Consumer<Integer> c1 = i -> System.out.print(prefix + i + " ");
        Consumer<Integer> c2 = i -> System.out.print(prefix + i + " ");
        System.out.println("c1 identity: " + System.identityHashCode(c1));
        System.out.println("c2 identity: " + System.identityHashCode(c2));
        System.out.println("same instance: " + (c1 == c2));   // always false
        DATA.forEach(c1);
        System.out.println();
    }

    // ── Case 3: lambda in a loop — allocation per iteration ───────────────────
    static void lambdaInLoop() {
        System.out.println("\n--- Lambda in loop (BAD: allocation per iteration) ---");
        int[] count = {0};
        int   limit = 5;
        int prevHash = -1;
        for (int i = 0; i < limit; i++) {
            final int captured = i;
            // captures `captured` — new lambda per iteration
            Consumer<Integer> c = x -> System.out.print(captured + ":" + x + " ");
            int hash = System.identityHashCode(c);
            System.out.printf("  iter=%d  lambda@%d  new=%b%n",
                    i, hash, hash != prevHash);
            prevHash = hash;
            count[0]++;
        }
        System.out.println("  " + count[0] + " lambda objects created in loop");
    }

    // ── Case 4: hoist lambda out of loop (GOOD) ───────────────────────────────
    static void hoistedLambda() {
        System.out.println("\n--- Hoisted lambda (GOOD: zero allocation in loop) ---");
        // non-capturing: create ONCE, reuse every iteration
        Consumer<Integer> printer = x -> System.out.print(x + " ");
        int hash = System.identityHashCode(printer);
        for (int i = 0; i < 5; i++) {
            System.out.printf("  iter=%d  lambda@%d  (same every time)%n",
                    i, System.identityHashCode(printer));
            DATA.forEach(printer);
            System.out.println();
        }
    }

    // ── Case 5: method reference subtlety ────────────────────────────────────
    static void methodReferenceSurprise() {
        System.out.println("\n--- Method reference: System.out::println ---");
        // Each evaluation captures `System.out` → new lambda wrapper object
        Consumer<Integer> r1 = System.out::println;
        Consumer<Integer> r2 = System.out::println;
        System.out.println("r1 identity: " + System.identityHashCode(r1));
        System.out.println("r2 identity: " + System.identityHashCode(r2));
        System.out.println("same: " + (r1 == r2) + "  (false — new wrapper each time)");
        System.out.println("Fix: assign to a static final field to get one instance");
    }

    public static void main(String[] args) {
        System.out.println("=== Lambda Allocation Patterns ===");
        nonCapturing();
        capturing();
        lambdaInLoop();
        hoistedLambda();
        methodReferenceSurprise();

        System.out.println("\n=== Summary ===");
        System.out.println("Non-capturing lambda  → JVM singleton, zero alloc");
        System.out.println("Capturing lambda      → new object per evaluation");
        System.out.println("Lambda in loop        → allocation per iteration — hoist it out");
        System.out.println("Method reference      → new wrapper per evaluation site");
        System.out.println("Hot path rule         → assign to final field, reuse everywhere");
    }
}