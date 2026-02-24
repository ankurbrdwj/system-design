package com.ankur.design.disruptor;

import com.ankur.design.hft.disruptor.SpscRingBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SpscRingBuffer, grouped by the five concepts explained in
 * LockFreeDisruptorDemo.md:
 *
 *   1. Initial State      — producerSeq = -1, consumerSeq = -1
 *   2. publish()          — back-pressure, lazySet, & MASK index wrap
 *   3. consume()          — spin-wait, load-acquire, lazySet slot release
 *   4. Memory Ordering    — data written before lazySet is visible after consume()
 *   5. Concurrent         — producer and consumer on separate threads, zero data loss
 */
class RingBufferTest {

    private SpscRingBuffer ring;

    @BeforeEach
    void setUp() {
        ring = new SpscRingBuffer();
    }

    // =========================================================================
    // 1. INITIAL STATE
    //
    //    "Both start at -1, meaning nothing published / consumed yet."
    //                                          — LockFreeDisruptorDemo.md
    // =========================================================================
    @Nested
    class InitialState {

        @Test
        void producerSeq_startsAtMinusOne() {
            assertEquals(-1L, ring.producerSeq(),
                    "producerSeq must be -1 before anything is published");
        }

        @Test
        void consumerSeq_startsAtMinusOne() {
            assertEquals(-1L, ring.consumerSeq(),
                    "consumerSeq must be -1 before anything is consumed");
        }

        @Test
        void size_isPowerOfTwo_1024() {
            // Power-of-2 enables the '& MASK' trick — 1 cycle vs ~40 for modulo.
            assertEquals(1024, SpscRingBuffer.SIZE);
            assertEquals(1023, SpscRingBuffer.MASK);
            assertTrue(Integer.bitCount(SpscRingBuffer.SIZE) == 1,
                    "SIZE must be a power of 2 for bitwise masking");
        }
    }

    // =========================================================================
    // 2. publish()
    //
    //    "Producer: claim next slot, write value, then publish with lazySet."
    //
    //    Back-pressure rule (from the code):
    //      if (nextSeq - SIZE > consumerSeq) return false   ← ring full
    //
    //    lazySet rule (from the markdown):
    //      data write THEN lazySet — store-release ordering.
    // =========================================================================
    @Nested
    class Publish {

        @Test
        void publish_returnsTrue_whenRingHasSpace() {
            assertTrue(ring.publish(42L), "publish must succeed when ring is empty");
        }

        @Test
        void publish_advancesProducerSeq() {
            ring.publish(1L);
            assertEquals(0L, ring.producerSeq(),
                    "producerSeq must advance to 0 after first publish");

            ring.publish(2L);
            assertEquals(1L, ring.producerSeq(),
                    "producerSeq must advance to 1 after second publish");
        }

        @Test
        void publish_doesNotChangeConsumerSeq() {
            ring.publish(99L);
            assertEquals(-1L, ring.consumerSeq(),
                    "consumerSeq must remain -1 — only consumer advances it");
        }

        @Test
        void publish_returnsFalse_whenRingIsFull() {
            // Fill all SIZE slots — consumer has NOT advanced (consumerSeq = -1).
            // nextSeq for slot SIZE would be SIZE, and SIZE - SIZE = 0 > -1, so full.
            for (int i = 0; i < SpscRingBuffer.SIZE; i++) {
                assertTrue(ring.publish(i), "publish must succeed for slot " + i);
            }

            assertFalse(ring.publish(999L),
                    "publish must return false when ring is full (back-pressure)");
        }

        @Test
        void publish_returnsTrue_afterConsumerFreesASlot() {
            // Fill ring completely.
            for (int i = 0; i < SpscRingBuffer.SIZE; i++) ring.publish(i);

            // Ring is full — next publish must fail.
            assertFalse(ring.publish(999L));

            // Consumer reads one slot — frees it.
            ring.consume();

            // Now one slot is free; publish must succeed.
            assertTrue(ring.publish(999L),
                    "publish must succeed after consumer freed one slot");
        }

        @Test
        void maskIndexing_slotsWrapAroundCorrectly() {
            // Publish SIZE values and consume them all.
            for (int i = 0; i < SpscRingBuffer.SIZE; i++) ring.publish(i * 10L);
            for (int i = 0; i < SpscRingBuffer.SIZE; i++) ring.consume();

            // Second lap: the ring reuses the same array slots.
            // Value at slot 0 (second lap) must NOT bleed through from the first lap.
            ring.publish(777L);
            long secondLapValue = ring.consume();

            assertEquals(777L, secondLapValue,
                    "Second-lap slot 0 must hold 777, not the first-lap value (& MASK wraps correctly)");
        }
    }

    // =========================================================================
    // 3. consume()
    //
    //    "Consumer: spin-wait for next sequence; returns the value.
    //     consumerSeq.lazySet(nextConsumed) — releases the slot back to producer."
    //                                          — LockFreeDisruptorDemo.md
    // =========================================================================
    @Nested
    class Consume {

        @Test
        void consume_returnsExactValuePublished() {
            ring.publish(12345L);
            assertEquals(12345L, ring.consume(),
                    "consume must return exactly the value that was published");
        }

        @Test
        void consume_advancesConsumerSeq() {
            ring.publish(1L);
            ring.consume();
            assertEquals(0L, ring.consumerSeq(),
                    "consumerSeq must advance to 0 after first consume");
        }

        @Test
        void multiplePublishAndConsume_valuesAreInOrder() {
            long[] values = {10L, 20L, 30L, 40L, 50L};
            for (long v : values) ring.publish(v);

            for (long expected : values) {
                assertEquals(expected, ring.consume(),
                        "Values must be consumed in the same order they were published (FIFO)");
            }
        }

        @Test
        void consume_blocksUntilPublishHappens() throws InterruptedException {
            // Consumer thread starts before producer publishes.
            // It must block (spin-wait) and only return once the value is published.
            AtomicLong received  = new AtomicLong(Long.MIN_VALUE);
            AtomicBoolean consuming = new AtomicBoolean(false);

            Thread consumer = new Thread(() -> {
                consuming.set(true);
                received.set(ring.consume()); // must spin here
            });
            consumer.setDaemon(true);
            consumer.start();

            // Wait until the consumer thread is definitely in its spin loop.
            while (!consuming.get()) Thread.onSpinWait();
            Thread.sleep(30);

            // Consumer must still be spinning — nothing published yet.
            assertEquals(Long.MIN_VALUE, received.get(),
                    "consume must block — no value has been published yet");

            // Now publish — consumer must unblock immediately.
            ring.publish(42L);
            consumer.join(500);

            assertEquals(42L, received.get(),
                    "consume must return 42 once publish has happened");
        }

        @Test
        void consumerSeqLazySet_freesSlotForProducer() {
            // Fill ring, consume one slot, verify producer can now publish again.
            for (int i = 0; i < SpscRingBuffer.SIZE; i++) ring.publish(i);
            assertFalse(ring.publish(-1L)); // ring full

            ring.consume();                    // consumerSeq.lazySet frees slot 0
            assertTrue(ring.publish(-1L),
                    "Producer must be unblocked after consumerSeq.lazySet frees a slot");
        }
    }

    // =========================================================================
    // 4. MEMORY ORDERING  (lazySet = store-release)
    //
    //    "lazySet guarantees that the data write in step (3) is visible to the
    //     consumer before the sequence number update."
    //                                          — LockFreeDisruptorDemo.md
    //
    //    We verify the happens-before contract:
    //    every value written into data[] before lazySet must equal
    //    the value returned by consume() after producerSeq advances.
    // =========================================================================
    @Nested
    class MemoryOrdering {

        @Test
        void valueMustEqualExactlyWhatWasWritten_acrossAllSlots() {
            // Write distinct values into every slot and read them back.
            // Any store-release / load-acquire violation would cause a mismatch.
            int count = SpscRingBuffer.SIZE;
            for (int i = 0; i < count; i++) ring.publish(i * 31L);
            for (int i = 0; i < count; i++) {
                assertEquals(i * 31L, ring.consume(),
                        "Slot " + i + ": consume returned wrong value — ordering violated");
            }
        }

        @Test
        void producerAndConsumerOnSeparateThreads_noValueCorruption()
                throws InterruptedException {
            // 10,000 messages sent from one thread to another.
            // Any memory ordering violation will cause a value mismatch.
            int MSG = 10_000;
            long[] received = new long[MSG];
            CountDownLatch done = new CountDownLatch(1);

            Thread consumer = new Thread(() -> {
                for (int i = 0; i < MSG; i++) received[i] = ring.consume();
                done.countDown();
            });
            consumer.start();

            for (int i = 0; i < MSG; i++) {
                while (!ring.publish(i * 7L)) Thread.onSpinWait();
            }

            assertTrue(done.await(5, TimeUnit.SECONDS), "Consumer timed out");

            for (int i = 0; i < MSG; i++) {
                assertEquals(i * 7L, received[i],
                        "Message " + i + " corrupted — lazySet happens-before violated");
            }
        }
    }

    // =========================================================================
    // 5. CONCURRENT  (producer + consumer on separate threads)
    //
    //    "The timestamp is the value itself — the producer embeds
    //     System.nanoTime() and the consumer subtracts it on receipt."
    //                                          — LockFreeDisruptorDemo.md
    // =========================================================================
    @Nested
    class Concurrent {

        @Test
        void twoMillionMessages_zeroLoss_zeroCorruption() throws InterruptedException {
            // Mirrors the benchmark in LockFreeDisruptorDemo.main() but asserts
            // correctness rather than measuring latency.
            int EVENTS = 2_000_000;
            AtomicLong consumed = new AtomicLong(0);
            AtomicLong checksum = new AtomicLong(0);
            CountDownLatch done  = new CountDownLatch(1);

            // Consumer thread — matches the pattern from LockFreeDisruptorDemo
            Thread consumer = Thread.ofPlatform().name("ring-consumer").start(() -> {
                for (int i = 0; i < EVENTS; i++) {
                    checksum.addAndGet(ring.consume());
                    consumed.incrementAndGet();
                }
                done.countDown();
            });

            Thread.sleep(20); // let consumer reach its spin loop

            // Producer thread — embeds sequence as value for easy checksum
            Thread producer = Thread.ofPlatform().name("ring-producer").start(() -> {
                for (int i = 0; i < EVENTS; i++) {
                    while (!ring.publish(i)) Thread.onSpinWait();
                }
            });

            assertTrue(done.await(30, TimeUnit.SECONDS), "Consumer did not finish in time");
            producer.join(1_000);

            assertEquals(EVENTS, consumed.get(),
                    "Every published message must be consumed — zero loss");

            // Expected checksum: sum of 0..EVENTS-1 = EVENTS*(EVENTS-1)/2
            long expectedChecksum = (long) EVENTS * (EVENTS - 1) / 2;
            assertEquals(expectedChecksum, checksum.get(),
                    "Checksum mismatch — at least one message was corrupted or lost");
        }

        @Test
        void backPressure_producerSpinsAndDoesNotLoseData() throws InterruptedException {
            // Producer publishes 2*SIZE messages; consumer reads them all in batches.
            // Back-pressure (publish returning false) must not cause any data loss.
            int TOTAL  = SpscRingBuffer.SIZE * 2;
            long[] received = new long[TOTAL];
            CountDownLatch done = new CountDownLatch(1);

            Thread consumer = Thread.ofPlatform().name("slow-consumer").start(() -> {
                for (int i = 0; i < TOTAL; i++) {
                    received[i] = ring.consume();
                }
                done.countDown();
            });

            // Slow producer: sleep briefly every SIZE messages to let consumer drain
            for (int i = 0; i < TOTAL; i++) {
                while (!ring.publish(i * 3L)) Thread.onSpinWait();
            }

            assertTrue(done.await(5, TimeUnit.SECONDS), "Consumer timed out");

            for (int i = 0; i < TOTAL; i++) {
                assertEquals(i * 3L, received[i],
                        "Slot " + i + " lost or corrupted under back-pressure");
            }
        }
    }
}