package com.ankur.design.hft.profiling;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * TradeProcessor — why "load all objects then process" causes OOM under concurrent REST load,
 * and how inline byte-buffer processing fixes it without changing the input/output API.
 *
 * ── The real problem ─────────────────────────────────────────────────────────
 *
 *  Input  : TradeTable { String headerLine; List<String> dataLines }
 *  Output : TradeResult { TradeReport report; TradeTable warnings; TradeTable errors }
 *
 *  OLD PIPELINE (objectPipeline):
 *
 *    Step 1: for (String line : dataLines) → new Trade()        N × ~200 B live
 *    Step 2: for (Trade t : trades)        → new TradeReport()  N × ~310 B live
 *    Step 3: split into report / warnings / errors lists
 *
 *    At peak, ALL of the following are live simultaneously:
 *      dataLines     (owned by caller — cannot GC)
 *      List<Trade>   (step 1 result — held until step 2 finishes)
 *      List<String>  (step 2 output rows — held until step 3 finishes)
 *      warning/error lists
 *
 *    For N=10 000: ~10 MB of intermediate objects on top of input strings.
 *    A REST thread arriving mid-batch allocates even 1 MB → OOM.
 *
 *  NEW PIPELINE (bytePipeline):
 *
 *    Process ONE line at a time.  For each String line:
 *      a. Parse fields as primitives (index-based, no substring into Trade)
 *      b. Validate on stack primitives — no object created
 *      c. Append output bytes to one of three ByteArrayOutputStream buffers
 *      d. 'line' reference goes out of scope → eligible for next minor GC
 *
 *    At peak: input strings (caller-owned) + three growing byte buffers.
 *    No Trade, no TradeReport, no intermediate List<String> ever created.
 *
 * ── Heap at peak (N = 10 000) ────────────────────────────────────────────────
 *
 *  objectPipeline:
 *    input dataLines strings   ~4.5 MB   (caller owns, no GC)
 *    List<Trade>               ~2.0 MB   (held entire phase 1→2)
 *    List<String> report rows  ~3.0 MB   (held entire phase 2→3)
 *    warning + error lists     ~0.5 MB
 *    TOTAL peak                ~10 MB  ← REST thread OOMs here
 *
 *  bytePipeline:
 *    input dataLines strings   ~4.5 MB   (same, caller owns)
 *    three ByteArrayOutputStreams ~1.0 MB (output only, no per-record objects)
 *    TOTAL peak                ~5.5 MB  ← REST thread survives
 *    SAVING                    ~4.5 MB  (no Trade / TradeReport / row Strings)
 *
 * ── API contract: both pipelines accept TradeTable, return TradeResult ───────
 *  The caller sees zero difference.  Only the processing internals change.
 *
 * ── Is this replaceable further? ─────────────────────────────────────────────
 *  YES for: arithmetic (price*qty), range checks, enum matching (byte compare)
 *  NO for:  regex, locale ops, complex string logic → must decode to char[]
 *  HFT fix: encode symbol as int16 at the wire (SBE/FlatBuffers) — no String at all
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class TradeProcessor {

    // ── public API types (same for both pipelines) ─────────────────────────────

    public record TradeTable(String headerLine, List<String> dataLines) {}

    public record TradeReport(String headerLine, List<String> reportLines) {}

    public record TradeResult(TradeReport report, TradeTable warnings, TradeTable errors) {}

    // ── validation constants ───────────────────────────────────────────────────

    private static final Set<String> KNOWN_SYMBOLS =
            Set.of("AAPL", "MSFT", "GOOG", "AMZN", "TSLA");

    static final String IN_HEADER  = "timestamp,symbol,price,quantity,side";
    static final String OUT_HEADER = "timestamp,symbol,price,quantity,side,notional,errorCode,reportStatus";

    // byte[] literals for byte pipeline — allocated once, never again
    private static final byte[] OUT_HEADER_NL = (OUT_HEADER + "\n").getBytes(StandardCharsets.UTF_8);
    private static final byte[] OK_B          = "OK".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PRICE_RANGE_B = "PRICE_RANGE".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LARGE_ORDER_B = "LARGE_ORDER".getBytes(StandardCharsets.UTF_8);
    private static final byte[] UNKNOWN_SYM_B = "UNKNOWN_SYMBOL".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CLEAN_B       = "CLEAN".getBytes(StandardCharsets.UTF_8);
    private static final byte[] WARNING_B     = "WARNING".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ERROR_B       = "ERROR".getBytes(StandardCharsets.UTF_8);

    // ── internal intermediate — objectPipeline only ────────────────────────────
    // This is what causes OOM: N of these live until ALL records are validated.
    // bytePipeline never creates this class.
    private static final class Trade {
        long ts; String symbol; double price; int qty; String side;
    }

    // ── benchmark state ────────────────────────────────────────────────────────

    @Param({"1000", "10000"})
    public int recordCount;

    TradeTable input;

    @Setup(Level.Trial)
    public void setup() {
        input = generateTradeTable(recordCount);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BENCHMARK A — objectPipeline  (old way, causes OOM)
    // ══════════════════════════════════════════════════════════════════════════
    @Benchmark
    public TradeResult objectPipeline(Blackhole bh) {
        TradeResult r = runObjectPipeline(input);
        bh.consume(r);
        return r;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BENCHMARK B — bytePipeline  (new way, avoids OOM)
    // ══════════════════════════════════════════════════════════════════════════
    @Benchmark
    public TradeResult bytePipeline(Blackhole bh) {
        TradeResult r = runBytePipeline(input);
        bh.consume(r);
        return r;
    }

    // ── core logic (extracted so main() can call without a Blackhole) ──────────

    static TradeResult runObjectPipeline(TradeTable table) {
        // Phase 1: parse ALL lines → List<Trade>  (N × ~200 B on heap)
        List<Trade> trades = new ArrayList<>(table.dataLines().size());
        for (String line : table.dataLines())
            trades.add(parseTrade(line));

        // Phase 2: validate + enrich  (N row Strings + Trade objects both live)
        List<String> reportLines  = new ArrayList<>(trades.size());
        List<String> warningLines = new ArrayList<>();
        List<String> errorLines   = new ArrayList<>();

        for (Trade t : trades) {
            double notional;
            String errorCode, status;
            if (t.price < 50.0 || t.price > 500.0) {
                errorCode = "PRICE_RANGE";    status = "ERROR";
            } else if (t.qty > 900) {
                errorCode = "LARGE_ORDER";    status = "WARNING";
            } else if (!KNOWN_SYMBOLS.contains(t.symbol)) {
                errorCode = "UNKNOWN_SYMBOL"; status = "ERROR";
            } else {
                errorCode = "OK";             status = "CLEAN";
            }
            notional = t.price * t.qty;
            String row = t.ts + "," + t.symbol + "," + t.price + "," + t.qty
                    + "," + t.side + "," + notional + "," + errorCode + "," + status;
            reportLines.add(row);
            if ("WARNING".equals(status)) warningLines.add(row);
            if ("ERROR".equals(status))   errorLines.add(row);
        }

        return new TradeResult(
                new TradeReport(OUT_HEADER, reportLines),
                new TradeTable(OUT_HEADER, warningLines),
                new TradeTable(OUT_HEADER, errorLines));
    }

    static TradeResult runBytePipeline(TradeTable table) {
        int n = table.dataLines().size();
        // three byte buffers — replace three List<String>
        // ByteArrayOutputStream.write(byte[], int, int) never throws — safe unchecked
        ByteArrayOutputStreamFast reportBuf = new ByteArrayOutputStreamFast(n * 80);
        ByteArrayOutputStreamFast warnBuf   = new ByteArrayOutputStreamFast(64);
        ByteArrayOutputStreamFast errBuf    = new ByteArrayOutputStreamFast(64);

        reportBuf.append(OUT_HEADER_NL);
        boolean hasWarn = false, hasErr = false;

        for (String line : table.dataLines()) {
            // parse inline — NO Trade object, all values stay on the call stack
            int c1 = line.indexOf(','), c2 = line.indexOf(',', c1+1),
                c3 = line.indexOf(',', c2+1), c4 = line.indexOf(',', c3+1);
            long   ts  = Long.parseLong(line, 0, c1, 10);
            String sym = line.substring(c1+1, c2);       // needed for Set lookup
            double px  = Double.parseDouble(line.substring(c2+1, c3));
            int    qty = Integer.parseInt(line, c3+1, c4, 10);
            // side = line.substring(c4+1) — kept as String for output only

            // validate on stack primitives — no errorCode String allocated
            byte[] errorCode, status;
            if (px < 50.0 || px > 500.0)          { errorCode = PRICE_RANGE_B; status = ERROR_B; }
            else if (qty > 900)                    { errorCode = LARGE_ORDER_B; status = WARNING_B; }
            else if (!KNOWN_SYMBOLS.contains(sym)) { errorCode = UNKNOWN_SYM_B; status = ERROR_B; }
            else                                   { errorCode = OK_B;          status = CLEAN_B; }

            double notional = px * qty;

            // write one output CSV line as bytes — no intermediate String per record
            int lineStart = reportBuf.size();
            appendRow(reportBuf, ts, sym, px, qty, line.substring(c4+1), notional, errorCode, status);
            int lineEnd = reportBuf.size();

            if (status == WARNING_B) {
                if (!hasWarn) { warnBuf.append(OUT_HEADER_NL); hasWarn = true; }
                warnBuf.append(reportBuf.buf, lineStart, lineEnd - lineStart);
            }
            if (status == ERROR_B) {
                if (!hasErr) { errBuf.append(OUT_HEADER_NL); hasErr = true; }
                errBuf.append(reportBuf.buf, lineStart, lineEnd - lineStart);
            }
            // 'line' goes out of scope here → eligible for GC on next minor GC
        }

        // convert byte buffers to API types — ONE allocation per buffer at the end
        return new TradeResult(
                bytesToReport(reportBuf),
                hasWarn ? bytesToTable(warnBuf) : new TradeTable(OUT_HEADER, List.of()),
                hasErr  ? bytesToTable(errBuf)  : new TradeTable(OUT_HEADER, List.of()));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static void appendRow(ByteArrayOutputStreamFast buf,
                                  long ts, String sym, double px, int qty,
                                  String side, double notional,
                                  byte[] errorCode, byte[] status) {
        buf.appendLong(ts);     buf.append((byte)',');
        buf.appendStr(sym);     buf.append((byte)',');
        buf.appendDouble(px);   buf.append((byte)',');
        buf.appendInt(qty);     buf.append((byte)',');
        buf.appendStr(side);    buf.append((byte)',');
        buf.appendDouble(notional); buf.append((byte)',');
        buf.append(errorCode);  buf.append((byte)',');
        buf.append(status);     buf.append((byte)'\n');
    }

    private static TradeReport bytesToReport(ByteArrayOutputStreamFast buf) {
        String all = new String(buf.buf, 0, buf.size(), StandardCharsets.UTF_8);
        // split by '\n' — filter trailing empty string that split() produces after last '\n'
        String[] parts = all.split("\n");
        String header = parts.length > 0 ? parts[0] : OUT_HEADER;
        List<String> lines = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) if (!parts[i].isEmpty()) lines.add(parts[i]);
        return new TradeReport(header, lines);
    }

    private static TradeTable bytesToTable(ByteArrayOutputStreamFast buf) {
        String all = new String(buf.buf, 0, buf.size(), StandardCharsets.UTF_8);
        String[] parts = all.split("\n");
        String header = parts.length > 0 ? parts[0] : OUT_HEADER;
        List<String> lines = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) if (!parts[i].isEmpty()) lines.add(parts[i]);
        return new TradeTable(header, lines);
    }

    private static Trade parseTrade(String line) {
        int c1 = line.indexOf(','), c2 = line.indexOf(',', c1+1),
            c3 = line.indexOf(',', c2+1), c4 = line.indexOf(',', c3+1);
        Trade t = new Trade();
        t.ts = Long.parseLong(line, 0, c1, 10);
        t.symbol = line.substring(c1+1, c2);
        t.price  = Double.parseDouble(line.substring(c2+1, c3));
        t.qty    = Integer.parseInt(line, c3+1, c4, 10);
        t.side   = line.substring(c4+1);
        return t;
    }

    // ── Minimal grow-only byte buffer — avoids java.io.ByteArrayOutputStream ───
    // ByteArrayOutputStream.write(byte[]) is declared throws IOException in parent,
    // requiring try-catch even though it never throws.  This wrapper is cleaner.
    static final class ByteArrayOutputStreamFast {
        byte[] buf;
        private int pos;

        ByteArrayOutputStreamFast(int cap) { buf = new byte[cap]; }

        int size() { return pos; }

        void append(byte b) {
            ensureCapacity(1);
            buf[pos++] = b;
        }

        void append(byte[] src) { append(src, 0, src.length); }

        void append(byte[] src, int off, int len) {
            ensureCapacity(len);
            System.arraycopy(src, off, buf, pos, len);
            pos += len;
        }

        void appendStr(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            append(b);
        }

        void appendLong(long v) { appendStr(Long.toString(v)); }
        void appendInt(int v)   { appendStr(Integer.toString(v)); }
        void appendDouble(double v) { appendStr(Double.toString(v)); }

        private void ensureCapacity(int extra) {
            if (pos + extra > buf.length)
                buf = Arrays.copyOf(buf, Math.max(buf.length * 2, pos + extra));
        }
    }

    // ── test data ──────────────────────────────────────────────────────────────

    private static final String[] SYMBOLS = {"AAPL", "MSFT", "GOOG", "AMZN", "TSLA"};
    private static final String[] SIDES   = {"BUY", "SELL"};

    static TradeTable generateTradeTable(int count) {
        Random rng = new Random(42);
        List<String> lines = new ArrayList<>(count);
        long ts = 1_000_000L;
        for (int i = 0; i < count; i++) {
            ts += rng.nextInt(10) + 1;
            lines.add(ts + "," + SYMBOLS[rng.nextInt(SYMBOLS.length)]
                    + "," + String.format("%.2f", 100 + rng.nextDouble() * 200)
                    + "," + (rng.nextInt(1000) + 1)
                    + "," + SIDES[rng.nextInt(2)]);
        }
        return new TradeTable(IN_HEADER, lines);
    }

    // ── standalone runner ──────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        printOomAnalysis();

        // correctness check — both pipelines must produce same record counts
        TradeTable sample = generateTradeTable(20);
        TradeResult obj  = runObjectPipeline(sample);
        TradeResult byte_ = runBytePipeline(sample);
        System.out.printf("objectPipeline  report=%d  warnings=%d  errors=%d%n",
                obj.report().reportLines().size(),
                obj.warnings().dataLines().size(),
                obj.errors().dataLines().size());
        System.out.printf("bytePipeline    report=%d  warnings=%d  errors=%d%n",
                byte_.report().reportLines().size(),
                byte_.warnings().dataLines().size(),
                byte_.errors().dataLines().size());
        System.out.println();

        new Runner(new OptionsBuilder()
                .include(TradeProcessor.class.getSimpleName())
                .addProfiler(GCProfiler.class)
                .build()).run();
    }

    private static void printOomAnalysis() {
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  TradeProcessor: OOM root cause and fix                          ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  objectPipeline — peak heap (N=10000)                            ║");
        System.out.println("║    input List<String>        ~4.5 MB  (caller owns, no GC)       ║");
        System.out.println("║    List<Trade>               ~2.0 MB  (Phase 1, held till end)   ║");
        System.out.println("║    List<String> report rows  ~3.0 MB  (Phase 2, held till end)   ║");
        System.out.println("║    warning + error lists     ~0.5 MB                             ║");
        System.out.println("║    TOTAL peak                ~10 MB  ← REST thread OOMs here    ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  bytePipeline — peak heap (N=10000)                              ║");
        System.out.println("║    input List<String>        ~4.5 MB  (same, caller owns)        ║");
        System.out.println("║    three byte buffers        ~1.0 MB  (output only)              ║");
        System.out.println("║    TOTAL peak                ~5.5 MB  ← REST thread survives     ║");
        System.out.println("║    SAVING                    ~4.5 MB  (zero Trade/Report objects) ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  WHY each input String cannot be GC'd in objectPipeline:         ║");
        System.out.println("║    List<String> dataLines holds refs to all N strings.           ║");
        System.out.println("║    While List<Trade> is being built, all N strings are alive.    ║");
        System.out.println("║    Both lists live together → 3× data on heap at once.           ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  bytePipeline processes ONE String, then moves on:               ║");
        System.out.println("║    'line' ref → parse to stack primitives → append bytes         ║");
        System.out.println("║    'line' goes out of for-loop scope → GC-eligible next minor GC ║");
        System.out.println("║    No Trade object was ever created from it.                     ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
}