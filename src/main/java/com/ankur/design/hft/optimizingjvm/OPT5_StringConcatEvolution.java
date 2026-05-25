package com.ankur.design.hft.optimizingjvm;

/**
 * LESSON 5 — String concatenation: Java 8 vs Java 9 invokedynamic vs StringBuilder
 *
 * HOW JAVA 8 COMPILES  "a + b + c":
 *
 *   javac generates:
 *     new StringBuilder()
 *     .append(a)
 *     .append(b)
 *     .append(c)
 *     .toString()
 *
 *   One heap allocation per concatenation expression.
 *   In a loop: N allocations of StringBuilder + N Strings = 2N objects = GC pressure.
 *
 * HOW JAVA 9+ COMPILES  "a + b + c":
 *
 *   javac generates an invokedynamic call site:
 *     indy StringConcatFactory.makeConcatWithConstants(...)
 *
 *   At runtime, the JVM generates a specialised concat method for this exact
 *   signature (e.g., int + String + double). No intermediate StringBuilder.
 *   ~2x faster than Java 8 for simple expressions.
 *   Still one String allocation — but no StringBuilder in between.
 *
 *   You can verify this by decompiling:
 *     javap -c -verbose OPT5_StringConcatEvolution.class
 *   Look for: invokedynamic #N:makeConcatWithConstants
 *
 * WHY StringBuilder STILL WINS IN LOOPS:
 *
 *   Java 9 indy creates a new String per concatenation.
 *   In a loop: still N String allocations.
 *
 *   StringBuilder reuse across iterations: one object, one final toString().
 *   ~20x faster than loop concatenation regardless of Java version.
 *
 * COMPACT STRINGS (Java 9+):
 *   Strings containing only ASCII (Latin-1) are stored as byte[] not char[].
 *   Halves memory for typical English strings.
 *   Transparent — no API change needed.
 *   Check with: -XX:+PrintStringTableStatistics
 *
 * Performance summary:
 * ┌──────────────────────────────┬────────────────────────────────────┐
 * │ Method                       │ Cost                               │
 * ├──────────────────────────────┼────────────────────────────────────┤
 * │ a + b  (Java 8)              │ StringBuilder + String allocation  │
 * │ a + b  (Java 9+ indy)        │ ~2x faster — no StringBuilder      │
 * │ loop: s = s + i  (any ver.)  │ N allocations — slow               │
 * │ StringBuilder in loop        │ 1 allocation — ~20x faster         │
 * └──────────────────────────────┴────────────────────────────────────┘
 */
public class OPT5_StringConcatEvolution {

    static final int ITERATIONS = 50_000;
    static final int LOOP_SIZE  = 100;

    // ── simple concat — Java 9+ uses invokedynamic ────────────────────────────
    static String simpleConcat(String a, int b, double c) {
        return a + b + c;   // javap shows: invokedynamic makeConcatWithConstants on Java 9+
    }

    // ── BAD loop: string concatenation ───────────────────────────────────────
    // Each += creates a new String — N objects per loop
    static String badLoop(int n) {
        String result = "";
        for (int i = 0; i < n; i++) {
            result += i;   // BAD: new String each iteration
        }
        return result;
    }

    // ── GOOD loop: StringBuilder reuse ───────────────────────────────────────
    // One object, one toString() at the end
    static String goodLoop(int n) {
        StringBuilder sb = new StringBuilder(n * 3);   // pre-size to avoid resize
        for (int i = 0; i < n; i++) {
            sb.append(i);
        }
        return sb.toString();
    }

    // ── naive nanoTime benchmark (shows the concept — use JMH for real numbers)
    static void measure(String label, Runnable fn) {
        // warm up
        for (int i = 0; i < 1000; i++) fn.run();

        long t = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) fn.run();
        long ns = (System.nanoTime() - t) / ITERATIONS;

        System.out.printf("%-35s avg=%,5d ns/op%n", label, ns);
    }

    public static void main(String[] args) {
        System.out.println("=== String Concatenation Performance ===\n");

        System.out.println("--- Simple single concat ---");
        measure("simpleConcat (indy on Java9+)",  () -> simpleConcat("order-", 42, 99.5));

        System.out.println("\n--- Loop concatenation ---");
        measure("badLoop  (string +=)",            () -> badLoop(LOOP_SIZE));
        measure("goodLoop (StringBuilder reuse)",  () -> goodLoop(LOOP_SIZE));

        System.out.println("\n--- Verify invokedynamic at bytecode level ---");
        System.out.println("Run: javap -c -verbose " +
                OPT5_StringConcatEvolution.class.getName().replace('.', '/') + ".class");
        System.out.println("Look for: invokedynamic #N, 0:makeConcatWithConstants");

        System.out.println("\n--- Compact Strings (Java 9+) ---");
        String ascii  = "hello world";          // stored as byte[] — 11 bytes
        String unicode = "héllo wörld";         // stored as char[] — 22 bytes
        System.out.println("ASCII string class:   " + ascii.getClass().getSimpleName()
                + "  (internal: byte[] on Java9+)");
        System.out.println("Unicode string class: " + unicode.getClass().getSimpleName()
                + "  (internal: byte[] with UTF16 flag on Java9+)");
        System.out.println("Transparent to API — no code change needed to benefit.");
    }
}