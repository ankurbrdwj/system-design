package com.ankur.design.lld.doctor;

import com.ankur.design.lld.doctor.model.AppointmentRequest;
import com.ankur.design.lld.doctor.service.AppointmentService;
import com.ankur.design.lld.doctor.service.AtomicAppointmentService;
import com.ankur.design.lld.doctor.service.SemaphoreAppointmentService;
import com.ankur.design.lld.doctor.service.SynchronizedAppointmentService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests — verifies exactly 1 booking succeeds per slot
 * regardless of how many threads race for it.
 *
 * Each service is run 5× to catch races that only surface under certain thread interleavings.
 * Note: @RepeatedTest and @ParameterizedTest cannot be combined in JUnit 5 —
 * we achieve repetition by supplying each service 5 times in the stream.
 */
class ConcurrentAppointmentTest {

    static Stream<AppointmentService> allServices() {
        List<Supplier<AppointmentService>> factories = List.of(
                () -> AppointmentServiceTest.setUp(new SynchronizedAppointmentService()),
                () -> AppointmentServiceTest.setUp(new AtomicAppointmentService()),
                () -> AppointmentServiceTest.setUp(new SemaphoreAppointmentService())
        );
        // 5 repetitions × 3 services = 15 parameterized runs
        return IntStream.range(0, 5)
                .boxed()
                .flatMap(i -> factories.stream().map(Supplier::get));
    }

    /**
     * 10 threads race to book the same slot — exactly 1 must succeed.
     * This is the core correctness guarantee for appointment booking.
     */
    @ParameterizedTest
    @MethodSource("allServices")
    void exactlyOneBookingSucceedsPerSlot(AppointmentService service) throws Exception {
        int racingThreads = 10;
        AtomicInteger successCount = new AtomicInteger();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(racingThreads);

        ExecutorService executor = Executors.newFixedThreadPool(racingThreads);
        for (int i = 0; i < racingThreads; i++) {
            final String patient = "patient-" + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    if (service.book(new AppointmentRequest(patient, "dr-smith", "09:00"))) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Timed out");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly 1 booking must succeed — slot capacity is 1");
        assertTrue(service.availability("dr-smith").get("09:00"), "Slot must show as booked");
    }

    /**
     * Multiple threads race across different slots concurrently.
     * Each slot must still have at most 1 successful booking.
     */
    @ParameterizedTest
    @MethodSource("allServices")
    void concurrentBookingsAcrossDifferentSlotsDoNotInterfere(AppointmentService service) throws Exception {
        List<String> slots = List.of("09:00", "10:00", "11:00");
        int threadsPerSlot = 5;
        int totalThreads = slots.size() * threadsPerSlot;

        AtomicInteger totalSuccess = new AtomicInteger();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(totalThreads);

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        for (int i = 0; i < totalThreads; i++) {
            String slot = slots.get(i % slots.size());
            String patient = "patient-" + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    if (service.book(new AppointmentRequest(patient, "dr-smith", slot))) {
                        totalSuccess.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Timed out");
        executor.shutdown();

        // 3 slots × 1 booking each = exactly 3 successes
        assertEquals(slots.size(), totalSuccess.get(),
                "Each slot allows exactly 1 booking — total must equal number of slots");

        // Every slot must show as booked
        service.availability("dr-smith")
               .values()
               .forEach(booked -> assertTrue(booked, "All slots should be booked"));
    }
}