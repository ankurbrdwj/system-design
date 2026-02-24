package com.ankur.design.hft.trading;

import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TCP/IP & Sockets → Java NIO / Netty
 *
 * From the README:
 *   "Raw POSIX sockets in C++ = java.nio (non-blocking I/O with Selector, SocketChannel).
 *    epoll (Linux) is what Java NIO uses under the hood."
 *   "TCP_NODELAY (disable Nagle's algorithm) is set the same way in Java:
 *    socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)."
 *
 * This demo implements a self-contained non-blocking NIO echo server and client
 * in the same JVM (server on one thread, client on another).
 *
 * Key NIO concepts:
 *   ServerSocketChannel   → bind + accept (non-blocking)
 *   SocketChannel         → read/write (non-blocking)
 *   Selector              → epoll/kqueue — multiplexes many channels on one thread
 *   SelectionKey          → registered interest (OP_ACCEPT, OP_READ, OP_WRITE)
 *   TCP_NODELAY           → disables Nagle — critical for low latency
 *   ByteBuffer            → zero-copy I/O buffer (can be direct/off-heap)
 */
public class JavaNioTcpDemo {

    private static final int PORT        = 19871;
    private static final int MSG_COUNT   = 10_000;
    private static final int MSG_BYTES   = 64;   // fixed-size market data frame

    // Shared latency tracking
    private static final long[] latencies = new long[MSG_COUNT];
    private static final AtomicLong msgIdx = new AtomicLong();

    // =========================================================================
    // NIO Echo Server — single thread multiplexes all connections via Selector
    // =========================================================================
    static void runServer(CountDownLatch serverReady, CountDownLatch allDone) {
        try (ServerSocketChannel ssc   = ServerSocketChannel.open();
             Selector             sel   = Selector.open()) {

            ssc.bind(new InetSocketAddress("127.0.0.1", PORT));
            ssc.configureBlocking(false);          // non-blocking accept
            ssc.register(sel, SelectionKey.OP_ACCEPT);

            serverReady.countDown();               // signal client to start

            long echoed = 0;
            while (echoed < MSG_COUNT && !Thread.currentThread().isInterrupted()) {
                sel.select(5);                     // epoll_wait equivalent
                Iterator<SelectionKey> it = sel.selectedKeys().iterator();

                while (it.hasNext()) {
                    SelectionKey key = it.next(); it.remove();

                    if (key.isAcceptable()) {
                        // New connection: configure TCP_NODELAY (disable Nagle)
                        SocketChannel sc = ssc.accept();
                        sc.configureBlocking(false);
                        sc.setOption(StandardSocketOptions.TCP_NODELAY, true); // <<< critical
                        sc.setOption(StandardSocketOptions.SO_RCVBUF, 65536);
                        sc.register(sel, SelectionKey.OP_READ, ByteBuffer.allocateDirect(MSG_BYTES));
                        System.out.println("[server] Client connected: " + sc.getRemoteAddress());

                    } else if (key.isReadable()) {
                        SocketChannel sc  = (SocketChannel) key.channel();
                        ByteBuffer    buf = (ByteBuffer) key.attachment();

                        int n = sc.read(buf);
                        if (n == -1) { key.cancel(); sc.close(); continue; }

                        if (!buf.hasRemaining()) { // full frame received
                            buf.flip();
                            sc.write(buf);          // echo back (write on same thread)
                            buf.clear();
                            echoed++;
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (!e.getMessage().contains("closed")) e.printStackTrace();
        } finally {
            allDone.countDown();
        }
    }

    // =========================================================================
    // NIO Blocking Client — for simplicity uses blocking mode to measure RTT
    // =========================================================================
    static void runClient(CountDownLatch serverReady) throws Exception {
        serverReady.await();                        // wait for server to bind

        try (SocketChannel sc = SocketChannel.open()) {
            sc.setOption(StandardSocketOptions.TCP_NODELAY, true);  // disable Nagle on client too
            sc.setOption(StandardSocketOptions.SO_SNDBUF, 65536);
            sc.connect(new InetSocketAddress("127.0.0.1", PORT));

            ByteBuffer send = ByteBuffer.allocateDirect(MSG_BYTES);
            ByteBuffer recv = ByteBuffer.allocateDirect(MSG_BYTES);

            // Warm-up: send 1000 messages first
            for (int i = 0; i < 1000; i++) {
                send.clear(); send.put(new byte[MSG_BYTES]); send.flip();
                sc.write(send);
                recv.clear();
                while (recv.hasRemaining()) sc.read(recv);
            }

            System.out.println("[client] Warm-up done. Measuring " + MSG_COUNT + " round-trips...");

            // Measured phase
            for (int i = 0; i < MSG_COUNT; i++) {
                send.clear(); send.putLong(System.nanoTime()); // embed timestamp
                while (send.hasRemaining()) send.put((byte) 0);
                send.flip();

                long t0 = System.nanoTime();
                sc.write(send);

                recv.clear();
                while (recv.hasRemaining()) sc.read(recv);
                latencies[(int) msgIdx.getAndIncrement()] = System.nanoTime() - t0;
            }
        }
    }

    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("====================================================");
        System.out.println("  TCP/IP & Sockets → Java NIO (Selector / epoll)");
        System.out.println("====================================================\n");

        CountDownLatch serverReady = new CountDownLatch(1);
        CountDownLatch allDone     = new CountDownLatch(1);

        // Server thread
        Thread serverThread = Thread.ofPlatform().name("nio-server").start(() ->
                runServer(serverReady, allDone));

        // Client on main thread
        runClient(serverReady);

        serverThread.interrupt();
        allDone.await();

        // Statistics
        java.util.Arrays.sort(latencies);
        long sum = 0;
        for (long l : latencies) sum += l;
        int n = (int) msgIdx.get();

        System.out.printf("%n[NIO Echo RTT — %,d messages of %d bytes]%n", n, MSG_BYTES);
        System.out.printf("  Avg RTT  : %,d µs%n", sum / n / 1000);
        System.out.printf("  p50 RTT  : %,d µs%n", latencies[n / 2] / 1000);
        System.out.printf("  p99 RTT  : %,d µs%n", latencies[(int)(n * 0.99)] / 1000);
        System.out.printf("  Max RTT  : %,d µs%n", latencies[n - 1] / 1000);
        System.out.println();
        System.out.println("Key NIO concepts demonstrated:");
        System.out.println("  • Selector.select()   → epoll_wait (multiplexes N connections on 1 thread)");
        System.out.println("  • configureBlocking(false) → O_NONBLOCK socket");
        System.out.println("  • TCP_NODELAY=true    → disables Nagle's algorithm (critical for HFT)");
        System.out.println("  • DirectByteBuffer    → zero-copy between NIC and JVM");
    }
}