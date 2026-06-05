package com.ankur.design.training.exceptions;

import java.io.*;
import java.util.List;

/**
 * Java Exception Philosophy — 6 rules with demos.
 *
 * RULE 1: Checked   = caller CAN reasonably recover  (IOException, SQLException)
 * RULE 2: Unchecked = programmer error / bug          (NPE, IllegalArgumentException)
 * RULE 3: Fail fast — validate at the boundary, throw early
 * RULE 4: Exception translation — wrap low-level noise into domain meaning
 * RULE 5: Never swallow — empty catch blocks hide bugs silently
 * RULE 6: try-with-resources — always close, never leak
 */
public class ExceptionPhilosophyDemo {

    public static void main(String[] args) {
        rule1_checkedVsUnchecked();
        rule3_failFast();
        rule4_exceptionTranslation();
        rule5_neverSwallow();
        rule6_tryWithResources();
        multicatch();
    }

    // ── RULE 1: Checked vs Unchecked ─────────────────────────────────────────
    //
    // Checked   → extends Exception
    //   Compiler forces caller to handle it.
    //   Use when: external system failure — caller might retry, fallback, alert.
    //   e.g. reading a file, network call, DB query.
    //
    // Unchecked → extends RuntimeException
    //   Compiler ignores it. Program should fix the bug, not catch it.
    //   Use when: invalid argument, null, bad state — always a bug.
    //   e.g. passing null where non-null expected.

    static class InsufficientFundsException extends Exception {       // CHECKED
        private final double shortfall;
        InsufficientFundsException(double shortfall) {
            super("Insufficient funds, short by: " + shortfall);
            this.shortfall = shortfall;
        }
        double getShortfall() { return shortfall; }
    }

    static class InvalidOrderException extends RuntimeException {     // UNCHECKED
        InvalidOrderException(String msg) { super(msg); }
    }

    static void rule1_checkedVsUnchecked() {
        System.out.println("=== RULE 1: Checked vs Unchecked ===");

        // Checked — compiler forces you to handle. Caller can recover (retry, fallback).
        try {
            withdraw(100.0, 50.0);
        } catch (InsufficientFundsException e) {
            System.out.println("Handled: " + e.getMessage()); // can show UI, retry, etc.
        }

        // Unchecked — no try/catch needed. This is a bug. Fix the caller, not catch it.
        try {
            placeOrder(null);
        } catch (InvalidOrderException e) {
            System.out.println("Bug caught: " + e.getMessage());
        }
    }

    static void withdraw(double balance, double amount) throws InsufficientFundsException {
        if (amount > balance) throw new InsufficientFundsException(amount - balance);
        System.out.println("Withdrew " + amount);
    }

    static void placeOrder(String item) {
        if (item == null) throw new InvalidOrderException("Order item must not be null");
        System.out.println("Order placed: " + item);
    }

    // ── RULE 3: Fail Fast ─────────────────────────────────────────────────────
    //
    // Validate at system boundaries (public API, controller, service entry).
    // Throw immediately — don't let bad data travel deep into the call stack.
    // Easier to debug: stack trace points at the source, not a side-effect 10 calls later.

    static void rule3_failFast() {
        System.out.println("\n=== RULE 3: Fail Fast ===");

        try {
            processOrder(-1, "LAPTOP");
        } catch (InvalidOrderException e) {
            System.out.println("Caught at boundary: " + e.getMessage());
        }
    }

    static void processOrder(int qty, String item) {
        // validate HERE — at the public entry point, before any logic runs
        if (qty <= 0)   throw new InvalidOrderException("qty must be > 0, got: " + qty);
        if (item == null || item.isBlank()) throw new InvalidOrderException("item is blank");
        // ... rest of logic runs only with valid data
        System.out.println("Processing " + qty + " x " + item);
    }

    // ── RULE 4: Exception Translation ────────────────────────────────────────
    //
    // Low-level exceptions (SQLException, IOException) leak implementation details.
    // Wrap them in domain exceptions so callers see meaningful errors.
    // Always chain with initCause / constructor arg — never lose the original stack trace.

    static class UserRepositoryException extends RuntimeException {   // UNCHECKED domain ex
        UserRepositoryException(String msg, Throwable cause) {
            super(msg, cause);  // cause preserved — never lose the original stack trace
        }
    }

    static void rule4_exceptionTranslation() {
        System.out.println("\n=== RULE 4: Exception Translation ===");

        try {
            findUser(42);
        } catch (UserRepositoryException e) {
            System.out.println("Domain error: " + e.getMessage());
            System.out.println("Root cause:   " + e.getCause().getClass().getSimpleName());
        }
    }

    static String findUser(int id) {
        try {
            simulateDatabaseCall(id);   // throws raw IOException
            return "user";
        } catch (IOException e) {
            // TRANSLATE: caller gets UserRepositoryException, not raw IOException
            // BAD:  throw e;                          — leaks infra detail
            // BAD:  throw new UserRepositoryException("error", null); — loses stack trace
            throw new UserRepositoryException("Failed to load user id=" + id, e); // GOOD
        }
    }

    static void simulateDatabaseCall(int id) throws IOException {
        throw new IOException("Connection refused to db:5432");
    }

    // ── RULE 5: Never Swallow ─────────────────────────────────────────────────
    //
    // An empty catch block is the most dangerous pattern in Java.
    // It hides failures silently — the system looks fine but is broken.
    // Minimum: log it. Better: rethrow. Best: translate and rethrow.

    static void rule5_neverSwallow() {
        System.out.println("\n=== RULE 5: Never Swallow ===");

        // BAD — silent failure, hides bugs
        // try {
        //     riskyOp();
        // } catch (Exception e) { }  // ← NEVER DO THIS

        // GOOD — at minimum log; usually rethrow or translate
        try {
            riskyOp();
        } catch (IOException e) {
            System.err.println("[ERROR] riskyOp failed: " + e.getMessage()); // log
            // rethrow as unchecked so it propagates — don't hide it
            throw new UncheckedIOException(e);
        }
    }

    static void riskyOp() throws IOException {
        throw new IOException("disk full");
    }

    // ── RULE 6: try-with-resources ────────────────────────────────────────────
    //
    // Any class implementing AutoCloseable should be opened in try-with-resources.
    // close() is GUARANTEED to run even if an exception is thrown inside the block.
    // Replaces verbose try/catch/finally patterns.

    static void rule6_tryWithResources() {
        System.out.println("\n=== RULE 6: try-with-resources ===");

        // BAD — finally block, easy to miss close() on exception path
        // InputStream is = null;
        // try { is = new FileInputStream("f"); ... }
        // finally { if (is != null) is.close(); }

        // GOOD — close() called automatically, even on exception
        try (FakeResource res = new FakeResource("db-connection")) {
            res.use();
            // close() runs here even if use() threw
        } catch (Exception e) {
            System.out.println("Caught: " + e.getMessage());
        }

        // multiple resources — closed in reverse order (res2 first, then res1)
        try (FakeResource r1 = new FakeResource("r1");
             FakeResource r2 = new FakeResource("r2")) {
            r1.use();
            r2.use();
        } catch (Exception e) {
            System.out.println("Caught: " + e.getMessage());
        }
    }

    static class FakeResource implements AutoCloseable {
        private final String name;
        FakeResource(String name) {
            this.name = name;
            System.out.println("  Opened: " + name);
        }
        void use() { System.out.println("  Using: " + name); }

        @Override
        public void close() {
            System.out.println("  Closed: " + name); // always called
        }
    }

    // ── BONUS: multi-catch (Java 7+) ─────────────────────────────────────────
    //
    // Catch multiple unrelated exception types in one block.
    // Use when handling is identical — avoids duplicate code.
    // Cannot use with exception types that are in the same hierarchy.

    static void multicatch() {
        System.out.println("\n=== BONUS: multi-catch ===");

        List<String> inputs = List.of("42", "abc", null);

        for (String input : inputs) {
            try {
                int val = Integer.parseInt(input);   // NumberFormatException
                int result = 100 / val;              // ArithmeticException
                System.out.println("Result: " + result);
            } catch (NumberFormatException | ArithmeticException e) {
                // same handling for both — single block, no duplication
                System.out.println("Bad input '" + input + "': " + e.getMessage());
            } catch (NullPointerException e) {
                System.out.println("Null input — fail fast should have caught this earlier");
            }
        }
    }
}