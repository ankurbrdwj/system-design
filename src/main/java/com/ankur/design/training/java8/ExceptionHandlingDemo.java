package com.ankur.design.training.java8;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * MIT 6.031 — Software Construction: Exception Handling in Functional & Async Java
 *
 * Lecture: "From Imperative Pain to Functional Pipelines"
 * Professor: (adapted from Venkat Subramaniam's talk)
 *
 * ─────────────────────────────────────────────────────────────
 *  TOPIC MAP
 * ─────────────────────────────────────────────────────────────
 *  §1  Imperative vs Functional Style
 *  §2  Lazy Evaluation — why pipelines don't build extra lists
 *  §3  The Checked-Exception Problem in Streams
 *  §4  Anti-patterns (three hall-of-shame patterns)
 *  §5  The Try Monad — treating error AS data
 *  §6  CompletableFuture Railway-Track Model
 *  §7  Putting it all together — real pipeline with recovery
 * ─────────────────────────────────────────────────────────────
 */
public class ExceptionHandlingDemo {

    // ════════════════════════════════════════════════════════════
    // §1  IMPERATIVE  vs  FUNCTIONAL  STYLE
    //
    //  Imperative: tell the machine WHAT + HOW (explicit loop, mutation)
    //  Functional: tell the machine WHAT, compose higher-order functions
    // ════════════════════════════════════════════════════════════

    static void section1_imperativeVsFunctional() {
        System.out.println("\n══════ §1 Imperative vs Functional ══════");

        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        // ── IMPERATIVE ──────────────────────────────────────────
        // Problem: not cohesive — the "what" and "how" are tangled.
        // Every future reader must mentally execute the loop.
        System.out.println("Imperative — doubles of evens:");
        for (int n : numbers) {
            if (n % 2 == 0) {          // filtering embedded in loop
                System.out.print(n * 2 + " ");  // transformation embedded too
            }
        }
        System.out.println();

        // ── FUNCTIONAL (declarative pipeline) ───────────────────
        // Each stage declares its intent: filter → map → forEach
        // No temporary collection is built between stages (see §2).
        System.out.println("Functional — doubles of evens:");
        numbers.stream()
               .filter(n -> n % 2 == 0)   // keeps 2,4,6,8,10
               .map(n -> n * 2)            // 4,8,12,16,20
               .forEach(n -> System.out.print(n + " "));
        System.out.println();
    }

    // ════════════════════════════════════════════════════════════
    // §2  LAZY EVALUATION
    //
    //  Key insight: the Stream pipeline does NOT build intermediate
    //  collections.  Elements are pulled one-by-one from the source
    //  only when a *terminal* operation demands them (forEach, findFirst…).
    //
    //  Proof: we instrument each stage with a print statement.
    // ════════════════════════════════════════════════════════════

    static void section2_lazyEvaluation() {
        System.out.println("\n══════ §2 Lazy Evaluation ══════");

        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        // Watch: filter is called for 1 and 2, map only for 2,
        // then findFirst() stops — elements 3-10 are NEVER touched.
        Optional<Integer> firstDouble = numbers.stream()
            .filter(n -> {
                System.out.println("  filter called for: " + n);
                return n % 2 == 0;
            })
            .map(n -> {
                System.out.println("  map   called for: " + n);
                return n * 2;
            })
            .findFirst();   // ← terminal: demand one element, then stop

        System.out.println("First doubled even: " + firstDouble.orElse(-1));
        // Output shows filter(1)→reject, filter(2)→pass, map(2)→4, STOP.
        // Elements 3–10 are never evaluated. That is lazy evaluation.
    }

    // ════════════════════════════════════════════════════════════
    // §3  THE CHECKED-EXCEPTION PROBLEM IN STREAMS
    //
    //  Stream.map() signature: <R> Stream<R> map(Function<T,R> mapper)
    //  Function.apply() does NOT declare throws — so checked exceptions
    //  cannot propagate through the pipeline.  The compiler enforces this.
    // ════════════════════════════════════════════════════════════

    // Simulates a checked-exception-throwing operation (e.g., network call)
    static String fetchAirportName(String code) throws Exception {
        // In reality: HTTP request to an airport-lookup API
        if (code.equalsIgnoreCase("INVALID")) {
            throw new Exception("Unknown airport code: " + code);
        }
        // Simulated DB
        return switch (code.toUpperCase()) {
            case "IAH" -> "George Bush Intercontinental, Houston";
            case "AUS" -> "Austin-Bergstrom International";
            case "DAL" -> "Dallas Love Field";
            case "SAT" -> "San Antonio International";
            default    -> throw new Exception("Airport not found: " + code);
        };
    }

    static void section3_checkedExceptionProblem() {
        System.out.println("\n══════ §3 Checked Exception Problem in Streams ══════");

        List<String> codes = Arrays.asList("IAH", "AUS", "DAL", "SAT");

        // ── IMPERATIVE: works perfectly ──────────────────────────
        System.out.println("Imperative (handles exceptions gracefully):");
        for (String code : codes) {
            try {
                System.out.println("  " + fetchAirportName(code));
            } catch (Exception e) {
                System.out.println("  ERROR: " + e.getMessage());
            }
        }

        // ── FUNCTIONAL: CANNOT use fetchAirportName directly ─────
        // The following would NOT compile:
        //   codes.stream().map(c -> fetchAirportName(c))...
        //
        // Because fetchAirportName throws a checked Exception, but
        // Function<T,R>.apply() has no 'throws' clause.
        // The compiler says: "unreported exception must be caught or declared."
        System.out.println("\n(Functional pipeline with checked exception requires workaround — see §4/§5)");
    }

    // ════════════════════════════════════════════════════════════
    // §4  THREE ANTI-PATTERNS (Hall of Shame)
    //
    //  These are common "fixes" developers reach for.
    //  All three are considered bad practice.
    // ════════════════════════════════════════════════════════════

    static void section4_antiPatterns() {
        System.out.println("\n══════ §4 Anti-Patterns ══════");

        List<String> codes = Arrays.asList("IAH", "INVALID", "SAT");

        // ── ANTI-PATTERN 1: Swallow exception inside lambda ──────
        // Called the "Act of Desperation" pattern.
        // Silently swallows errors; the pipeline continues but you lose info.
        System.out.println("Anti-pattern 1 — swallow exception inside lambda:");
        codes.stream()
            .map(code -> {
                try {
                    return fetchAirportName(code);
                } catch (Exception e) {
                    return "ERROR: " + e.getMessage();  // swallowed — bad
                }
            })
            .forEach(result -> System.out.println("  " + result));

        // ── ANTI-PATTERN 2: Wrap in RuntimeException ─────────────
        // Called the "Curl Up in a Corner and Cry" pattern.
        // The pipeline blows up on first failure; remaining items are lost.
        System.out.println("\nAnti-pattern 2 — wrap in RuntimeException (DON'T DO THIS):");
        try {
            codes.stream()
                .map(code -> {
                    try {
                        return fetchAirportName(code);
                    } catch (Exception e) {
                        throw new RuntimeException(e);  // ← ANTI-PATTERN
                    }
                })
                .forEach(result -> System.out.println("  " + result));
        } catch (RuntimeException e) {
            System.out.println("  Pipeline blew up! Lost remaining items. Cause: " + e.getCause().getMessage());
        }

        // ── ANTI-PATTERN 3: Wrapper method that converts to Runtime ──
        // Generalising the bad idea — now it's reusable badness.
        // (demonstrated conceptually — we don't run it since it's the same outcome)
        System.out.println("\nAnti-pattern 3 — generic wrapper (even worse, scales the badness):");
        System.out.println("  wrappedFetch() would still crash the pipeline on first failure.");
        System.out.println("  DO NOT use: codes.stream().map(ExceptionHandlingDemo::wrappedFetch)...");
    }

    // The wrapper that converts checked → unchecked (anti-pattern 3)
    static String wrappedFetch(String code) {
        try {
            return fetchAirportName(code);
        } catch (Exception e) {
            throw new RuntimeException(e);   // ← loses the remaining elements
        }
    }

    // ════════════════════════════════════════════════════════════
    // §5  THE TRY MONAD — treating error AS data
    //
    //  Insight from Haskell's "Maybe monad" and Scala's Try[T]:
    //  Instead of throwing, wrap the result in a union type that can
    //  hold EITHER a value (Success) OR an error (Failure).
    //
    //  Rule: "Treat error as data and deal with it downstream."
    //         — keep the pipeline flowing FORWARD, never reverse.
    //
    //  This is ALSO what Java's Optional does for null:
    //    Optional<T> = present(value) | empty
    //  Try<T> extends that idea to:
    //    Try<T>      = success(value) | failure(exception)
    // ════════════════════════════════════════════════════════════

    // ── Try<T> interface ─────────────────────────────────────────
    sealed interface Try<T> permits Try.Success, Try.Failure {

        // Factory: run a Supplier<T>; catch any exception into Failure
        static <T> Try<T> of(Supplier<T> supplier) {
            try {
                return new Success<>(supplier.get());
            } catch (Exception e) {
                return new Failure<>(e);
            }
        }

        // Transform the value if Success; pass Failure through unchanged
        <R> Try<R> map(Function<T, R> f);

        // Extract: apply valueMapper on Success, errorMapper on Failure
        <R> R fold(Function<T, R> valueMapper, Function<Exception, R> errorMapper);

        boolean isSuccess();

        // ── Success ───────────────────────────────────────────────
        record Success<T>(T value) implements Try<T> {
            @Override
            public <R> Try<R> map(Function<T, R> f) {
                try {
                    return new Success<>(f.apply(value));
                } catch (Exception e) {
                    return new Failure<>(e);   // transformation itself may fail
                }
            }

            @Override
            public <R> R fold(Function<T, R> valueMapper, Function<Exception, R> errorMapper) {
                return valueMapper.apply(value);
            }

            @Override public boolean isSuccess() { return true; }
        }

        // ── Failure ───────────────────────────────────────────────
        record Failure<T>(Exception exception) implements Try<T> {
            @Override
            @SuppressWarnings("unchecked")
            public <R> Try<R> map(Function<T, R> f) {
                // Error travels forward unchanged — no transformation applied
                return (Try<R>) this;
            }

            @Override
            public <R> R fold(Function<T, R> valueMapper, Function<Exception, R> errorMapper) {
                return errorMapper.apply(exception);
            }

            @Override public boolean isSuccess() { return false; }
        }
    }

    static void section5_tryMonad() {
        System.out.println("\n══════ §5 Try Monad — error as data ══════");

        List<String> codes = Arrays.asList("IAH", "INVALID", "SAT", "AUS", "XXX");

        // ── Step 1: lift fetchAirportName into Try ────────────────
        // The lambda is now a total function: no checked exceptions escape.
        // Failure is captured as data, not thrown.

        codes.stream()
            // wrap each call in Try.of() — checked exception → Failure object
            .map(code -> Try.of(() -> {
                try { return fetchAirportName(code); }
                catch (Exception e) { throw new RuntimeException(e); }
            }))
            // transform value if present (Success only — Failure passes through)
            .map(t -> t.map(String::toUpperCase))
            // fold: extract value or error message — pipeline stays clean
            .map(t -> t.fold(
                name  -> "✓ " + name,
                error -> "✗ " + error.getCause().getMessage()
            ))
            .forEach(result -> System.out.println("  " + result));

        // ── Step 2: filter only successes ─────────────────────────
        System.out.println("\nSuccessful lookups only:");
        codes.stream()
            .map(code -> Try.of(() -> {
                try { return fetchAirportName(code); }
                catch (Exception e) { throw new RuntimeException(e); }
            }))
            .filter(Try::isSuccess)
            .map(t -> t.fold(v -> v, e -> ""))
            .forEach(name -> System.out.println("  " + name));
    }

    // ════════════════════════════════════════════════════════════
    // §6  COMPLETABLEFUTURE — RAILWAY TRACK MODEL
    //
    //  Think of two parallel railway tracks:
    //    Top track    = Happy path  (data flows here when all is well)
    //    Bottom track = Error path  (exception flows here after failure)
    //
    //  thenApply()   → transforms data on the HAPPY track
    //  thenAccept()  → consumes data on the HAPPY track (terminal)
    //  exceptionally()→ catches the exception on the ERROR track,
    //                   can RECOVER (return value) to get back on top track
    //
    //  State machine of a CompletableFuture:
    //    PENDING → RESOLVED (success) | REJECTED (failure)
    //  Once RESOLVED or REJECTED, state is terminal and immutable.
    // ════════════════════════════════════════════════════════════

    static void section6_completableFuture() throws Exception {
        System.out.println("\n══════ §6 CompletableFuture Railway Track ══════");

        // ── Happy path ─────────────────────────────────────────────
        System.out.println("Happy path (2 → ×10 → +1 → print):");
        CompletableFuture.supplyAsync(() -> 2)           // PENDING → RESOLVED(2)
            .thenApply(n -> n * 10)                      // top track: 20
            .thenApply(n -> n + 1)                       // top track: 21
            .thenAccept(n -> System.out.println("  Result: " + n))  // print 21
            .get();  // block for demo purposes

        // ── Failure path → recovery via exceptionally ──────────────
        System.out.println("\nError path with recovery:");
        CompletableFuture.supplyAsync(() -> {
                if (Math.random() > -1) throw new RuntimeException("Simulated failure");
                return 2;
            })
            .thenApply(n -> n * 10)          // skipped — already on error track
            .thenApply(n -> n + 1)           // skipped
            .exceptionally(ex -> {           // ← catches the error, RECOVERS
                System.out.println("  Caught: " + ex.getMessage());
                return -1;                   // sentinel value → back on happy track
            })
            .thenAccept(n -> System.out.println("  Recovered value: " + n))
            .get();

        // ── Error path → no recovery (re-throw) ───────────────────
        System.out.println("\nError path without recovery:");
        CompletableFuture.supplyAsync(() -> {
                throw new RuntimeException("Network timeout");
            })
            .thenApply((Object n) -> n)       // skipped
            .exceptionally(ex -> {
                System.out.println("  Fatal error: " + ex.getMessage());
                return null;                  // null = signal downstream to skip
            })
            .thenAccept(n -> {
                if (n != null) System.out.println("  Final: " + n);
                else System.out.println("  Pipeline ended with error — no value.");
            })
            .get();

        // ── Chained stages with individual recovery ────────────────
        System.out.println("\nAirport lookup via CompletableFuture:");
        CompletableFuture
            .supplyAsync(() -> "IAH")
            .thenApplyAsync(code -> {
                try { return fetchAirportName(code); }
                catch (Exception e) { throw new RuntimeException(e); }
            })
            .thenApply(String::toUpperCase)
            .exceptionally(ex -> "UNKNOWN AIRPORT")
            .thenAccept(name -> System.out.println("  Airport: " + name))
            .get();

        // Same but with an invalid code
        CompletableFuture
            .supplyAsync(() -> "INVALID")
            .thenApplyAsync(code -> {
                try { return fetchAirportName(code); }
                catch (Exception e) { throw new RuntimeException(e); }
            })
            .thenApply(String::toUpperCase)
            .exceptionally(ex -> "UNKNOWN AIRPORT [" + ex.getCause().getMessage() + "]")
            .thenAccept(name -> System.out.println("  Airport: " + name))
            .get();
    }

    // ════════════════════════════════════════════════════════════
    // §7  PUTTING IT ALL TOGETHER
    //     Real pipeline: batch airport lookup using Try + parallel streams
    //
    //  Design principles applied:
    //  1. Pure functions — each lambda is a total function (no throw)
    //  2. Errors as data  — Try[T] keeps all results in the pipeline
    //  3. Late error handling — deal with errors at the BOUNDARY
    //  4. No exception in the hot path — Try.map never throws
    // ════════════════════════════════════════════════════════════

    static void section7_fullPipeline() {
        System.out.println("\n══════ §7 Full Pipeline — batch lookup with error recovery ══════");

        List<String> codes = Arrays.asList("IAH", "AUS", "INVALID", "DAL", "SAT", "XYZ", "XXX");

        System.out.printf("  %-10s %-45s %s%n", "CODE", "AIRPORT NAME", "STATUS");
        System.out.println("  " + "─".repeat(65));

        codes.stream()
            .parallel()    // parallel safe because each Try is independent
            .map(code -> new Object[]{
                code,
                Try.of(() -> {
                    try { return fetchAirportName(code); }
                    catch (Exception e) { throw new RuntimeException(e); }
                })
            })
            .map(pair -> new Object[]{
                pair[0],
                ((Try<?>) pair[1]).map(v -> ((String) v).toUpperCase()),
            })
            .sequential()  // restore order for readable output
            .sorted((a, b) -> ((String) a[0]).compareTo((String) b[0]))
            .forEach(pair -> {
                String code = (String) pair[0];
                Try<?> result = (Try<?>) pair[1];
                String name   = result.fold(v -> (String) v, e -> "(not found)");
                String status = result.isSuccess() ? "OK" : "FAIL: " + result.fold(v -> "", e -> e.getCause().getMessage());
                System.out.printf("  %-10s %-45s %s%n", code, name, status);
            });

        // ── Summary counts ─────────────────────────────────────────
        long successes = codes.stream()
            .map(code -> Try.of(() -> {
                try { return fetchAirportName(code); }
                catch (Exception e) { throw new RuntimeException(e); }
            }))
            .filter(Try::isSuccess)
            .count();

        System.out.printf("%n  Total: %d/%d succeeded%n", successes, codes.size());
    }

    // ════════════════════════════════════════════════════════════
    //  MAIN — runs all sections in order
    // ════════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  MIT 6.031 — Exception Handling: Functional & Async Java ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        section1_imperativeVsFunctional();
        section2_lazyEvaluation();
        section3_checkedExceptionProblem();
        section4_antiPatterns();
        section5_tryMonad();
        section6_completableFuture();
        section7_fullPipeline();

        System.out.println("\n═══ End of Demo ═══");
    }
}