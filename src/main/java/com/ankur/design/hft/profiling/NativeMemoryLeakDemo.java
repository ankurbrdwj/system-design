package com.ankur.design.hft.profiling;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.lang.management.*;
import java.util.*;

/**
 * CONCEPT 7: Native Memory Leak Detection
 *
 * ReadMe: "Native Memory Leak Detection: profiling native malloc and mmap
 *          calls revealed unclosed streams causing memory leaks."
 *
 * async-profiler native memory mode:
 *   asprof -e malloc -d 10 <pid>    (intercepts malloc/free)
 *   asprof -e mmap   -d 10 <pid>    (intercepts mmap syscall)
 *
 * Unlike heap memory (visible to GC), native/direct memory is allocated
 * outside the JVM heap and is NOT collected automatically.
 *
 * Sources of native memory leaks in Java:
 *   A. Unclosed InputStream / FileChannel  → OS file descriptor leak
 *   B. ByteBuffer.allocateDirect()         → off-heap memory never freed
 *   C. ZipFile / JarFile not closed        → native zlib structures leak
 *
 * async-profiler shows the CALL STACK of the malloc/mmap that was never freed,
 * pointing directly at the unclosed resource.
 */
public class NativeMemoryLeakDemo {

    // =========================================================================
    // A. File Descriptor Leak — stream opened but never closed
    //    OS has a per-process limit (ulimit -n, typically 1024-65535).
    //    When the limit is reached: java.io.IOException: Too many open files
    // =========================================================================
    static class FileDescriptorLeaker {
        private final List<InputStream> leaked = new ArrayList<>();

        void openWithoutClosing(Path file) throws IOException {
            // BAD: stream opened but never closed — fd held until GC finalises
            InputStream stream = Files.newInputStream(file);
            leaked.add(stream);   // prevents GC from finalising it
        }

        void openCorrectly(Path file) throws IOException {
            // GOOD: try-with-resources guarantees close() even on exception
            try (InputStream stream = Files.newInputStream(file)) {
                stream.read(); // minimal read — just to use it
            }
        }

        int leakedCount() { return leaked.size(); }

        void closeAll() throws IOException {
            for (InputStream s : leaked) s.close();
            leaked.clear();
        }
    }

    // =========================================================================
    // B. Direct ByteBuffer Leak — off-heap memory allocated but never freed
    //    ByteBuffer.allocateDirect() → mmap/malloc outside JVM heap.
    //    GC only triggers Cleaner.clean() when the ByteBuffer is GC'd,
    //    but if you hold a strong reference, memory is pinned forever.
    // =========================================================================
    static class DirectBufferLeaker {
        private final List<ByteBuffer> leaked = new ArrayList<>();

        void allocateWithoutReleasing(int bytes) {
            // BAD: direct buffer held in a list — never GC'd, never freed
            ByteBuffer buf = ByteBuffer.allocateDirect(bytes);
            leaked.add(buf); // strong ref prevents GC → memory pinned
        }

        long leakedBytes() {
            return leaked.stream().mapToLong(ByteBuffer::capacity).sum();
        }

        void release() {
            // GOOD: null the reference so GC can finalise the Cleaner
            leaked.clear();
            System.gc(); // hint GC to run Cleaner callbacks
        }
    }

    // =========================================================================
    // C. Correct resource management — try-with-resources
    // =========================================================================
    static long readWithTryWithResources(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(4096);
            long total = 0;
            int bytesRead;
            while ((bytesRead = channel.read(buf)) != -1) {
                total += bytesRead;
                buf.clear();
            }
            return total;
        } // channel.close() guaranteed here — fd returned to OS
    }

    // =========================================================================
    // Measure off-heap memory via MemoryMXBean / BufferPoolMXBean
    // =========================================================================
    static long directMemoryUsedMB() {
        return ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean.class)
                .stream()
                .filter(p -> p.getName().equals("direct"))
                .mapToLong(java.lang.management.BufferPoolMXBean::getMemoryUsed)
                .sum() / (1024 * 1024);
    }

    static int openFileDescriptors() {
        try {
            com.sun.management.UnixOperatingSystemMXBean os =
                    (com.sun.management.UnixOperatingSystemMXBean)
                            ManagementFactory.getOperatingSystemMXBean();
            return (int) os.getOpenFileDescriptorCount();
        } catch (ClassCastException e) {
            return -1; // not available on non-Unix
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("====================================================");
        System.out.println("  Native Memory Leak Demo");
        System.out.println("====================================================\n");

        // Create a temp file to work with
        Path tmpFile = Files.createTempFile("leak-demo-", ".txt");
        Files.writeString(tmpFile, "x".repeat(10_000));

        // ---- A. File Descriptor leak ----
        System.out.println("--- A. File Descriptor Leak ---");
        int fdBefore = openFileDescriptors();
        FileDescriptorLeaker fdLeaker = new FileDescriptorLeaker();

        for (int i = 0; i < 50; i++) fdLeaker.openWithoutClosing(tmpFile);
        int fdDuring = openFileDescriptors();

        System.out.printf("  FDs before leak : %d%n", fdBefore);
        System.out.printf("  FDs after 50 opens (no close): %d  (leaked=%d)%n",
                fdDuring, fdLeaker.leakedCount());

        fdLeaker.closeAll(); // fix
        int fdAfter = openFileDescriptors();
        System.out.printf("  FDs after closeAll()         : %d%n", fdAfter);
        System.out.println("  async-profiler -e malloc shows: openWithoutClosing → FileInputStream.<init>");

        // ---- B. Direct Buffer leak ----
        System.out.println("\n--- B. Direct ByteBuffer Leak ---");
        long directBefore = directMemoryUsedMB();
        DirectBufferLeaker bufLeaker = new DirectBufferLeaker();

        for (int i = 0; i < 20; i++) bufLeaker.allocateWithoutReleasing(5 * 1024 * 1024); // 5 MB each
        long directDuring = directMemoryUsedMB();

        System.out.printf("  Direct memory before : %d MB%n", directBefore);
        System.out.printf("  Direct memory (leaked 100 MB): %d MB%n", directDuring);
        System.out.printf("  Leaked bytes tracked : %,d bytes%n", bufLeaker.leakedBytes());

        bufLeaker.release(); // null refs + hint GC
        Thread.sleep(100);
        long directAfter = directMemoryUsedMB();
        System.out.printf("  Direct memory after release  : %d MB%n", directAfter);
        System.out.println("  async-profiler -e mmap shows: allocateWithoutReleasing → allocateDirect");

        // ---- C. Correct pattern ----
        System.out.println("\n--- C. Correct: try-with-resources ---");
        int fdC1 = openFileDescriptors();
        for (int i = 0; i < 100; i++) readWithTryWithResources(tmpFile);
        int fdC2 = openFileDescriptors();
        System.out.printf("  FD count unchanged: before=%d after=%d (delta=%d)%n",
                fdC1, fdC2, fdC2 - fdC1);
        System.out.println("  async-profiler shows NO unmatched malloc/mmap for this path");

        Files.deleteIfExists(tmpFile);

        System.out.println("\nSummary:");
        System.out.println("  Leak type        | async-profiler event | Root cause in flame graph");
        System.out.println("  File descriptor  | -e malloc            | FileInputStream.<init>");
        System.out.println("  Direct buffer    | -e mmap              | ByteBuffer.allocateDirect");
        System.out.println("  ZipFile          | -e malloc            | ZipFile.<init> → inflateInit");
        System.out.println("\nFix: always use try-with-resources for Closeable resources.");
    }
}