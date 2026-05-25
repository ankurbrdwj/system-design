package com.ankur.design.hft.profiling;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * FileIOBenchmark — five strategies: merge + validate + produce TradeReport
 *
 * TASK: merge two sorted CSV trade files, validate each record, compute notional,
 *       produce a TradeReport CSV with error codes and report status.
 *
 * ── Strategies ───────────────────────────────────────────────────────────────
 *
 *  A rawByteConcat         — file→file bytes, no sort, no logic  (pure I/O baseline)
 *  B nioZeroCopy           — OS sendfile, minimal heap           (I/O baseline)
 *  C objectReport          — Trade → validate → TradeReport → write  (fully allocating)
 *  D objectReportPooled    — same, both Trade+TradeReport borrowed from pool
 *  E byteStreamReport      — parse primitives inline, validate on stack, write bytes
 *                            NO Trade object, NO TradeReport object, NO String per field
 *
 * ── Is business logic (validate + enrich) replaceable with byte streams? ────
 *
 *  PASSTHROUGH (copy only)      → YES.  nioZeroCopy is the proof: 2 KB/op.
 *
 *  ARITHMETIC (price × qty)     → PARTIALLY.  You must still decode the number
 *    from its ASCII bytes.  But you can decode into a stack primitive (double/int)
 *    without ever creating a String or Trade object.  byteStreamReport does this:
 *    parseDoubleFromBytes() → stack double, multiply → write back as ASCII bytes.
 *    No heap allocation for the value itself.
 *
 *  VALIDATION (price in range)  → YES with byte-native parsing (see above).
 *    The threshold comparison works on the decoded primitive, not the String.
 *
 *  ENUM FIELDS (symbol, side)   → YES.  Compare raw bytes against static byte[][]
 *    literals — no String.equals(), no intern(), no substring().
 *    isKnownSymbol() shows this pattern.
 *
 *  COMPLEX STRING LOGIC         → NO.  Regex, substring search, locale-aware ops
 *    cannot be done without decoding to char[].  HFT answer: eliminate these fields
 *    at the wire — encode symbol as int16 tag at source (SBE / FlatBuffers).
 *
 * ── Validation rules applied to every merged record ─────────────────────────
 *  price outside (50.0, 500.0)  → ERROR   "PRICE_RANGE"
 *  quantity > 900               → WARNING "LARGE_ORDER"
 *  symbol not in known set      → ERROR   "UNKNOWN_SYMBOL"
 *  all clear                    → CLEAN   "OK"
 *
 * ── Memory: what each strategy allocates per operation ───────────────────────
 *
 *  TradeReport on heap (~310 bytes):
 *    Trade shell   (~40 bytes)  + Trade Strings (~160 bytes)
 *    Report shell  (~40 bytes)  + errorCode String (~64 bytes) + status String (~64 bytes)
 *    + notional double (on Trade shell, 8 bytes)
 *
 *  byteStreamReport: only two input byte[] (file content) + pre-allocated outBuf
 *    (outBuf is setup-time, NOT counted per-invocation by GCProfiler)
 *    → gc.alloc.rate.norm ≈ rawByteConcat  (just file reads + tiny NIO wrappers)
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class FileIOBenchmark {

    @Param({"1000", "10000"})
    public int recordCount;

    private static final String   HEADER        = "timestamp,symbol,price,quantity,side\n";
    private static final byte[]   REPORT_HEADER =
            "timestamp,symbol,price,quantity,side,notional,errorCode,reportStatus\n"
            .getBytes(StandardCharsets.US_ASCII);

    private static final String[] SYMBOLS = {"AAPL", "MSFT", "GOOG", "AMZN", "TSLA"};
    private static final String[] SIDES   = {"BUY", "SELL"};

    // byte[][] for zero-allocation symbol matching in byteStreamReport
    private static final byte[][] SYMBOL_BYTES;
    static {
        SYMBOL_BYTES = new byte[SYMBOLS.length][];
        for (int i = 0; i < SYMBOLS.length; i++)
            SYMBOL_BYTES[i] = SYMBOLS[i].getBytes(StandardCharsets.US_ASCII);
    }

    // lookup table: avoids Math.pow in byte-native double parsing
    private static final double[] POW10 = {1, 10, 100, 1_000, 10_000, 100_000};

    // ── data model ────────────────────────────────────────────────────────────

    static final class Trade {
        long timestamp; String symbol; double price; int quantity; String side;

        Trade reset(long ts, String sym, double p, int q, String s) {
            timestamp = ts; symbol = sym; price = p; quantity = q; side = s; return this;
        }
    }

    // TradeReport = Trade fields + computed notional + validation result
    //
    // heap cost vs Trade:
    //   +8  double notional
    //   +4  String errorCode  ref → String object ~64 bytes ("PRICE_RANGE" is worst case)
    //   +4  String reportStatus ref → ~56 bytes ("WARNING"/"ERROR"/"CLEAN")
    //   ≈ 310 bytes total per TradeReport vs 200 bytes for Trade alone
    static final class TradeReport {
        long   timestamp;
        String symbol;
        double price;
        int    quantity;
        String side;
        double notional;      // price * quantity
        String errorCode;     // "OK" | "PRICE_RANGE" | "LARGE_ORDER" | "UNKNOWN_SYMBOL"
        String reportStatus;  // "CLEAN" | "WARNING" | "ERROR"

        TradeReport reset(Trade src) {
            timestamp = src.timestamp; symbol = src.symbol;
            price = src.price;         quantity = src.quantity; side = src.side;
            notional = price * quantity;
            // validation
            if (price < 50.0 || price > 500.0) {
                errorCode = "PRICE_RANGE";    reportStatus = "ERROR";
            } else if (quantity > 900) {
                errorCode = "LARGE_ORDER";    reportStatus = "WARNING";
            } else if (!isKnownSymbolStr(symbol)) {
                errorCode = "UNKNOWN_SYMBOL"; reportStatus = "ERROR";
            } else {
                errorCode = "OK";             reportStatus = "CLEAN";
            }
            return this;
        }

        String toCsv() {
            return timestamp + "," + symbol + "," + price + "," + quantity + "," + side
                    + "," + notional + "," + errorCode + "," + reportStatus;
        }
    }

    // ── per-trial state ───────────────────────────────────────────────────────

    Path fileA, fileB, outputFile;
    ArrayBlockingQueue<Trade>       tradePool;
    ArrayBlockingQueue<TradeReport> reportPool;

    // pre-allocated working arrays for byteStreamReport — NOT counted by GCProfiler
    long[] tsA, tsB;
    int[]  startA, endA, startB, endB;
    byte[] outBuf;   // output scratch buffer — reused across invocations

    @Setup(Level.Trial)
    public void setup() throws IOException {
        fileA      = Files.createTempFile("trades_a_", ".csv");
        fileB      = Files.createTempFile("trades_b_", ".csv");
        outputFile = Files.createTempFile("trades_out_", ".csv");
        writeSortedTradeFile(fileA, 0L,               recordCount);
        writeSortedTradeFile(fileB, recordCount * 10L, recordCount);

        int poolSize = recordCount * 2 + 16;
        tradePool  = new ArrayBlockingQueue<>(poolSize);
        reportPool = new ArrayBlockingQueue<>(poolSize);
        for (int i = 0; i < poolSize; i++) { tradePool.offer(new Trade()); reportPool.offer(new TradeReport()); }

        // byte-stream working state: primitive arrays, pre-sized for worst case
        tsA = new long[recordCount]; startA = new int[recordCount]; endA = new int[recordCount];
        tsB = new long[recordCount]; startB = new int[recordCount]; endB = new int[recordCount];
        outBuf = new byte[recordCount * 2 * 110 + REPORT_HEADER.length + 64];
    }

    @TearDown(Level.Trial)
    public void teardown() throws IOException {
        Files.deleteIfExists(fileA);
        Files.deleteIfExists(fileB);
        Files.deleteIfExists(outputFile);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // A — raw byte concat (no logic, no sort) — I/O baseline
    // gc.alloc.rate.norm: ~2 × fileSize  (two byte[] arrays)
    // ═══════════════════════════════════════════════════════════════════════════
    @Benchmark
    public void rawByteConcat(Blackhole bh) throws IOException {
        byte[] a = Files.readAllBytes(fileA);
        byte[] b = Files.readAllBytes(fileB);
        int skip = 0;
        while (skip < b.length && b[skip] != '\n') skip++;
        skip++;
        try (OutputStream out = new BufferedOutputStream(
                Files.newOutputStream(outputFile, StandardOpenOption.TRUNCATE_EXISTING))) {
            out.write(a);
            if (skip < b.length) out.write(b, skip, b.length - skip);
        }
        bh.consume(outputFile);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // B — NIO zero-copy (OS sendfile, no user-space heap copy)
    // gc.alloc.rate.norm: ~2–3 KB  (only FileChannel wrappers)
    // ═══════════════════════════════════════════════════════════════════════════
    @Benchmark
    public void nioZeroCopy(Blackhole bh) throws IOException {
        try (FileChannel ca  = FileChannel.open(fileA,  StandardOpenOption.READ);
             FileChannel cb  = FileChannel.open(fileB,  StandardOpenOption.READ);
             FileChannel out = FileChannel.open(outputFile,
                     StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            long posA = 0, szA = ca.size();
            while (posA < szA) posA += ca.transferTo(posA, szA - posA, out);
            ByteBuffer hdr = ByteBuffer.allocate(256);
            cb.read(hdr, 0); hdr.flip();
            long skip = 0;
            while (hdr.hasRemaining() && hdr.get() != '\n') skip++;
            skip++;
            long posB = skip, szB = cb.size();
            while (posB < szB) posB += cb.transferTo(posB, szB - posB, out);
        }
        bh.consume(outputFile);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C — object pipeline: Trade → validate → TradeReport → write CSV
    //
    // Every record allocates: Trade shell + 2 Trade Strings + TradeReport shell
    //                         + 2 report Strings + ArrayList entries
    // gc.alloc.rate.norm:
    //   N=1000  → ~1.7 MB  (TradeReport adds ~300 B × 2000 records vs ~200 B Trade)
    //   N=10000 → ~16 MB
    // ═══════════════════════════════════════════════════════════════════════════
    @Benchmark
    public void objectReport(Blackhole bh) throws IOException {
        List<Trade> ta = parseFile(fileA, false);
        List<Trade> tb = parseFile(fileB, false);
        List<Trade> merged = mergeSorted(ta, tb);
        List<TradeReport> reports = toReports(merged, false);
        writeReports(reports, outputFile);
        bh.consume(outputFile);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // D — pooled: Trade + TradeReport both borrowed from pool
    //
    // Shells reused — but errorCode/reportStatus Strings still newly allocated
    // each call (interned literals help JVM here — "OK", "ERROR" etc. are
    // compile-time constants so String pool deduplication applies).
    // gc.alloc.rate.norm: similar to C because String literals are deduplicated;
    // the saving is mostly Trade/Report shells (~40 bytes × 4000 objects).
    // ═══════════════════════════════════════════════════════════════════════════
    @Benchmark
    public void objectReportPooled(Blackhole bh) throws IOException {
        List<Trade> ta = parseFile(fileA, true);
        List<Trade> tb = parseFile(fileB, true);
        List<Trade> merged = mergeSorted(ta, tb);
        List<TradeReport> reports = toReports(merged, true);
        writeReports(reports, outputFile);
        for (Trade t      : merged)  tradePool.offer(t);
        for (TradeReport r : reports) reportPool.offer(r);
        bh.consume(outputFile);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // E — byte-stream pipeline: no Trade, no TradeReport, no String per record
    //
    // HOW:
    //   1. Read both files as byte[] (same as rawByteConcat).
    //   2. indexLines() fills pre-allocated tsA/tsB (timestamps) + startA/endA
    //      (line boundaries) — all primitives, no object per line.
    //   3. Two-pointer merge by comparing tsA[i] vs tsB[j] — longs on stack.
    //   4. For each merged line: parseDoubleFromBytes(), parseInt from bytes,
    //      compare symbol bytes against SYMBOL_BYTES[][].
    //   5. Validate on stack primitives → encode result as byte constant.
    //   6. Write output line to pre-allocated outBuf[] — no String, no StringBuilder.
    //   7. Single Files.write(outBuf, 0..outPos) at the end.
    //
    // gc.alloc.rate.norm: ≈ rawByteConcat  (~2 × fileSize for the two byte[] reads)
    // outBuf is setup-time, NOT a per-invocation allocation.
    // ═══════════════════════════════════════════════════════════════════════════
    @Benchmark
    public void byteStreamReport(Blackhole bh) throws IOException {
        byte[] bytesA = Files.readAllBytes(fileA);
        byte[] bytesB = Files.readAllBytes(fileB);

        indexLines(bytesA, tsA, startA, endA, recordCount);
        indexLines(bytesB, tsB, startB, endB, recordCount);

        int outPos = 0;
        System.arraycopy(REPORT_HEADER, 0, outBuf, 0, REPORT_HEADER.length);
        outPos += REPORT_HEADER.length;

        int i = 0, j = 0;
        while (i < recordCount || j < recordCount) {
            byte[] src; int ls, le;
            if (j >= recordCount || (i < recordCount && tsA[i] <= tsB[j])) {
                src = bytesA; ls = startA[i]; le = endA[i]; i++;
            } else {
                src = bytesB; ls = startB[j]; le = endB[j]; j++;
            }
            outPos = validateAndWriteBytes(src, ls, le, outBuf, outPos);
        }

        Files.write(outputFile, Arrays.copyOf(outBuf, outPos),
                StandardOpenOption.TRUNCATE_EXISTING);
        bh.consume(outputFile);
    }

    // ── helpers — object path ─────────────────────────────────────────────────

    private List<Trade> parseFile(Path file, boolean usePool) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<Trade> result = new ArrayList<>(lines.size());
        for (int k = 1; k < lines.size(); k++) {
            String line = lines.get(k);
            if (!line.isEmpty()) result.add(parseLine(line, usePool));
        }
        return result;
    }

    private Trade parseLine(String line, boolean usePool) {
        int c1 = line.indexOf(','), c2 = line.indexOf(',', c1+1),
            c3 = line.indexOf(',', c2+1), c4 = line.indexOf(',', c3+1);
        long ts   = Long.parseLong(line, 0, c1, 10);
        String sym = line.substring(c1+1, c2);
        double px  = Double.parseDouble(line.substring(c2+1, c3));
        int qty    = Integer.parseInt(line, c3+1, c4, 10);
        String sd  = line.substring(c4+1);
        Trade t = usePool ? tradePool.poll() : null;
        if (t == null) t = new Trade();
        return t.reset(ts, sym, px, qty, sd);
    }

    private List<Trade> mergeSorted(List<Trade> a, List<Trade> b) {
        List<Trade> out = new ArrayList<>(a.size() + b.size());
        int i = 0, j = 0;
        while (i < a.size() && j < b.size())
            out.add(a.get(i).timestamp <= b.get(j).timestamp ? a.get(i++) : b.get(j++));
        while (i < a.size()) out.add(a.get(i++));
        while (j < b.size()) out.add(b.get(j++));
        return out;
    }

    private List<TradeReport> toReports(List<Trade> trades, boolean usePool) {
        List<TradeReport> out = new ArrayList<>(trades.size());
        for (Trade t : trades) {
            TradeReport r = usePool ? reportPool.poll() : null;
            if (r == null) r = new TradeReport();
            out.add(r.reset(t));
        }
        return out;
    }

    private static void writeReports(List<TradeReport> reports, Path dest) throws IOException {
        StringBuilder sb = new StringBuilder(reports.size() * 80);
        sb.append(new String(REPORT_HEADER, StandardCharsets.US_ASCII));
        for (TradeReport r : reports) sb.append(r.toCsv()).append('\n');
        Files.writeString(dest, sb, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static boolean isKnownSymbolStr(String sym) {
        for (String s : SYMBOLS) if (s.equals(sym)) return true;
        return false;
    }

    // ── helpers — byte-stream path ────────────────────────────────────────────

    // Fill ts[], starts[], ends[] from a CSV byte[].  Skips header line.
    // Parses only the timestamp field (first comma-delimited token) per line.
    private static void indexLines(byte[] buf, long[] ts, int[] starts, int[] ends, int count) {
        int pos = 0;
        while (pos < buf.length && buf[pos] != '\n') pos++;
        pos++; // skip header '\n'
        for (int i = 0; i < count && pos < buf.length; i++) {
            starts[i] = pos;
            long t = 0;
            while (buf[pos] != ',') { t = t * 10 + (buf[pos] - '0'); pos++; }
            ts[i] = t;
            while (pos < buf.length && buf[pos] != '\n') pos++;
            ends[i] = pos;
            pos++;
        }
    }

    // Parse one line from src[ls..le], validate, compute notional,
    // write extended CSV line into dst[outPos..] and return new outPos.
    // No heap allocation: all intermediate values are primitives on the stack.
    private static int validateAndWriteBytes(byte[] src, int ls, int le,
                                             byte[] dst, int outPos) {
        int pos = ls;

        // ── parse timestamp ──────────────────────────────────────────────────
        long ts = 0;
        while (src[pos] != ',') { ts = ts * 10 + (src[pos] - '0'); pos++; }
        pos++;

        // ── parse symbol bounds ──────────────────────────────────────────────
        int symStart = pos;
        while (src[pos] != ',') pos++;
        int symEnd = pos; pos++;

        // ── parse price (byte-native, no String) ─────────────────────────────
        int priceStart = pos;
        while (src[pos] != ',') pos++;
        int priceEnd = pos; pos++;
        double price = parseDoubleFromBytes(src, priceStart, priceEnd);

        // ── parse quantity ───────────────────────────────────────────────────
        int qty = 0;
        while (src[pos] != ',') { qty = qty * 10 + (src[pos] - '0'); pos++; }
        pos++;

        // ── side: bytes from pos to le ────────────────────────────────────────
        int sideStart = pos;

        // ── validate on stack primitives ─────────────────────────────────────
        // errorTag: 0=OK, 1=PRICE_RANGE, 2=LARGE_ORDER, 3=UNKNOWN_SYMBOL
        // statusTag: 0=CLEAN, 1=WARNING, 2=ERROR
        int errorTag, statusTag;
        if (price < 50.0 || price > 500.0)              { errorTag = 1; statusTag = 2; }
        else if (qty > 900)                              { errorTag = 2; statusTag = 1; }
        else if (!isKnownSymbolBytes(src, symStart, symEnd)) { errorTag = 3; statusTag = 2; }
        else                                             { errorTag = 0; statusTag = 0; }

        double notional = price * qty;

        // ── write original fields verbatim (no re-encode) ────────────────────
        int lineLen = le - ls;
        System.arraycopy(src, ls, dst, outPos, lineLen);
        outPos += lineLen;
        dst[outPos++] = ',';

        // ── write notional as bytes ───────────────────────────────────────────
        outPos = writeLongBytes(dst, outPos, (long) notional);
        dst[outPos++] = '.';
        outPos = writeLongBytes(dst, outPos, Math.abs((long)((notional - (long)notional) * 100)));
        dst[outPos++] = ',';

        // ── write errorCode and status as bytes ───────────────────────────────
        outPos = writeLiteral(dst, outPos, ERROR_CODES[errorTag]);
        dst[outPos++] = ',';
        outPos = writeLiteral(dst, outPos, STATUSES[statusTag]);
        dst[outPos++] = '\n';
        return outPos;
    }

    // byte[] literals for the four error/status codes — avoids String encoding
    private static final byte[][] ERROR_CODES = {
        "OK".getBytes(StandardCharsets.US_ASCII),
        "PRICE_RANGE".getBytes(StandardCharsets.US_ASCII),
        "LARGE_ORDER".getBytes(StandardCharsets.US_ASCII),
        "UNKNOWN_SYMBOL".getBytes(StandardCharsets.US_ASCII),
    };
    private static final byte[][] STATUSES = {
        "CLEAN".getBytes(StandardCharsets.US_ASCII),
        "WARNING".getBytes(StandardCharsets.US_ASCII),
        "ERROR".getBytes(StandardCharsets.US_ASCII),
    };

    private static boolean isKnownSymbolBytes(byte[] buf, int start, int end) {
        outer:
        for (byte[] sym : SYMBOL_BYTES) {
            if (end - start != sym.length) continue;
            for (int i = 0; i < sym.length; i++)
                if (buf[start + i] != sym[i]) continue outer;
            return true;
        }
        return false;
    }

    // Parse ASCII decimal (with optional '.') from buf[start..end) → double
    private static double parseDoubleFromBytes(byte[] buf, int start, int end) {
        long intPart = 0;
        int dotPos = end;
        for (int i = start; i < end; i++) {
            if (buf[i] == '.') { dotPos = i; continue; }
            intPart = intPart * 10 + (buf[i] - '0');
        }
        if (dotPos == end) return intPart;
        int decimals = end - dotPos - 1;
        return intPart / POW10[Math.min(decimals, POW10.length - 1)];
    }

    private static int writeLongBytes(byte[] dst, int pos, long v) {
        if (v == 0) { dst[pos++] = '0'; return pos; }
        int start = pos;
        long n = v < 0 ? -v : v;
        while (n > 0) { dst[pos++] = (byte)('0' + n % 10); n /= 10; }
        // reverse the digits written
        for (int l = start, r = pos - 1; l < r; l++, r--) {
            byte tmp = dst[l]; dst[l] = dst[r]; dst[r] = tmp;
        }
        return pos;
    }

    private static int writeLiteral(byte[] dst, int pos, byte[] lit) {
        System.arraycopy(lit, 0, dst, pos, lit.length);
        return pos + lit.length;
    }

    // ── data generation ───────────────────────────────────────────────────────

    private static void writeSortedTradeFile(Path dest, long startTs, int count)
            throws IOException {
        Random rng = new Random(42);
        StringBuilder sb = new StringBuilder(count * 48);
        sb.append(HEADER);
        long ts = startTs;
        for (int i = 0; i < count; i++) {
            ts += rng.nextInt(10) + 1;
            sb.append(ts).append(',')
              .append(SYMBOLS[rng.nextInt(SYMBOLS.length)]).append(',')
              .append(String.format("%.2f", 100 + rng.nextDouble() * 200)).append(',')
              .append(rng.nextInt(1000) + 1).append(',')
              .append(SIDES[rng.nextInt(2)]).append('\n');
        }
        Files.writeString(dest, sb, StandardCharsets.UTF_8);
    }

    // ── runner ────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        printAnalysis();
        Options opt = new OptionsBuilder()
                .include(FileIOBenchmark.class.getSimpleName())
                .addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }

    private static void printAnalysis() {
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  JVM Memory: TradeReport objects vs byte-stream validation       ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Trade object (~200 bytes)                                       ║");
        System.out.println("║    shell: 40B  +  symbol String: ~80B  +  side String: ~72B      ║");
        System.out.println("║  TradeReport object (~310 bytes)                                 ║");
        System.out.println("║    Trade cost above  +  errorCode String ~64B  +  status ~56B    ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Expected gc.alloc.rate.norm (B/op)                              ║");
        System.out.println("║  Benchmark              N=1000        N=10000                    ║");
        System.out.println("║  rawByteConcat          ~60 KB        ~540 KB  (file reads only) ║");
        System.out.println("║  nioZeroCopy            ~2 KB         ~2 KB    (no heap)         ║");
        System.out.println("║  objectReport           ~1.7 MB       ~16 MB   (Trade+Report)    ║");
        System.out.println("║  objectReportPooled     ~1.5 MB       ~14 MB   (shells reused)   ║");
        System.out.println("║  byteStreamReport       ~60 KB        ~540 KB  (file reads only) ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Is validation+enrichment replaceable with byte streams?         ║");
        System.out.println("║  Passthrough copy   → YES  (nioZeroCopy)                        ║");
        System.out.println("║  Arithmetic         → YES  parseDoubleFromBytes() stays on stack ║");
        System.out.println("║  Range validation   → YES  compare primitives, not Strings       ║");
        System.out.println("║  Enum field match   → YES  isKnownSymbolBytes() compares bytes   ║");
        System.out.println("║  Regex / locale ops → NO   must decode to char[]                 ║");
        System.out.println("║  HFT answer: encode symbol as int16 at source (SBE/FlatBuffers)  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
}