package com.ankur.design.hft.optimizingjvm;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

/**
 * LESSON 4 — JMH: why System.nanoTime() benchmarks lie
 *
 * THREE ways naive benchmarks are wrong:
 *
 * 1. JVM WARM-UP
 *    HotSpot interprets bytecode first (~2000 invocations),
 *    then compiles with C1 (~10,000), then C2 (~100,000).
 *    System.nanoTime() measured before C2 kicks in shows
 *    interpreter speed — 10-100x slower than production.
 *    JMH runs @Warmup iterations and discards them automatically.
 *
 * 2. DEAD CODE ELIMINATION (DCE)
 *    If JIT sees your computed result is never used, it deletes the loop.
 *    Your benchmark shows 0ns — you measured nothing.
 *
 *    BAD:
 *      long sum = 0;
 *      for (long i = 0; i < N; i++) sum += i;  // JIT: result unused → delete
 *
 *    JMH fix: pass result to Blackhole.consume() — JIT cannot prove it's unused.
 *
 * 3. CONSTANT FOLDING
 *    If inputs are compile-time constants, JIT pre-computes the answer.
 *    Benchmark shows near-zero time — you measured the constant, not the code.
 *
 *    JMH fix: @State fields are opaque to JIT — inputs can't be folded.
 *
 * How to run:
 *   ./gradlew build
 *   java -jar build/libs/*.jar "OPT4_JmhExample" -rf json -rff results.json
 *
 *   Or via runMain task (set mainClass = "org.openjdk.jmh.Main" in build.gradle).
 *
 * Benchmark below compares:
 *   - sequential stream sum
 *   - parallel stream sum
 *   - for-loop sum
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class OPT4_JmhExample {

    // @State field — opaque to JIT, prevents constant folding
    @Param({"100000", "1000000"})
    long N;

    // ── Benchmark 1: for-loop ─────────────────────────────────────────────────
    @Benchmark
    public void forLoop(Blackhole bh) {
        long sum = 0;
        for (long i = 1; i <= N; i++) sum += i;
        bh.consume(sum);    // Blackhole: prevents DCE — JIT cannot delete this
    }

    // ── Benchmark 2: sequential stream ───────────────────────────────────────
    @Benchmark
    public void sequentialStream(Blackhole bh) {
        long sum = LongStream.rangeClosed(1, N).sum();
        bh.consume(sum);
    }

    // ── Benchmark 3: parallel stream ─────────────────────────────────────────
    @Benchmark
    public void parallelStream(Blackhole bh) {
        long sum = LongStream.rangeClosed(1, N).parallel().sum();
        bh.consume(sum);
    }

    // ── Benchmark 4: naive StringBuilder (what Java 8 concat compiles to) ────
    @Benchmark
    public void stringConcatLoop(Blackhole bh) {
        String result = "";
        for (int i = 0; i < 100; i++) {
            result = result + i;    // Java 8: new StringBuilder each iteration
        }
        bh.consume(result);
    }

    // ── Benchmark 5: StringBuilder reuse (correct pattern) ───────────────────
    @Benchmark
    public void stringBuilderLoop(Blackhole bh) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append(i);
        }
        bh.consume(sb.toString());
    }

    // ── Main: run the benchmark from gradle runMain ───────────────────────────
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(OPT4_JmhExample.class.getSimpleName())
                .warmupIterations(3)
                .measurementIterations(5)
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}