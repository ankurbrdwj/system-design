package com.ankur.design.lld.doctor;

import com.ankur.design.lld.doctor.model.AppointmentRequest;
import com.ankur.design.lld.doctor.service.AppointmentService;
import com.ankur.design.lld.doctor.service.AtomicAppointmentService;
import com.ankur.design.lld.doctor.service.SemaphoreAppointmentService;
import com.ankur.design.lld.doctor.service.SynchronizedAppointmentService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests — run against all three strategies.
 * Same structure as RoomDatabaseAccessServiceTest.
 */
class AppointmentServiceTest {

    static Stream<AppointmentService> allServices() {
        return Stream.of(
                setUp(new SynchronizedAppointmentService()),
                setUp(new AtomicAppointmentService()),
                setUp(new SemaphoreAppointmentService())
        );
    }

    /** Shared setup: Dr Smith with 3 morning slots, Dr Jones with 2 afternoon slots. */
    static AppointmentService setUp(AppointmentService service) {
        service.addDoctor("dr-smith", "Dr Smith",
                List.of("09:00", "10:00", "11:00"));
        service.addDoctor("dr-jones", "Dr Jones",
                List.of("14:00", "15:00"));
        return service;
    }

    // -------------------------------------------------------------------------
    // 1. Availability after init
    // -------------------------------------------------------------------------
    @Nested
    class Initialization {

        @ParameterizedTest
        @MethodSource("com.ankur.design.lld.doctor.AppointmentServiceTest#allServices")
        void allSlotsAreFreeAfterInit(AppointmentService service) {
            Map<String, Boolean> avail = service.availability("dr-smith");
            assertEquals(3, avail.size());
            avail.values().forEach(booked -> assertFalse(booked));
        }

        @ParameterizedTest
        @MethodSource("com.ankur.design.lld.doctor.AppointmentServiceTest#allServices")
        void unknownDoctorReturnsEmptyAvailability(AppointmentService service) {
            assertTrue(service.availability("dr-nobody").isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // 2. Basic booking
    // -------------------------------------------------------------------------
    @Nested
    class SingleThreadedBooking {

        @ParameterizedTest
        @MethodSource("com.ankur.design.lld.doctor.AppointmentServiceTest#allServices")
        void bookingFreeSlotSucceeds(AppointmentService service) {
            assertTrue(service.book(req("alice", "dr-smith", "09:00")));
        }

        @ParameterizedTest
        @MethodSource("com.ankur.design.lld.doctor.AppointmentServiceTest#allServices")
        void bookingAlreadyBookedSlotFails(AppointmentService service) {
            service.book(req("alice", "dr-smith", "09:00"));
            assertFalse(service.book(req("bob", "dr-smith", "09:00")));
        }

        @ParameterizedTest
        @MethodSource("com.ankur.design.lld.doctor.AppointmentServiceTest#allServices")
        void bookedSlotAppearsInAvailability(AppointmentService service) {
            service.book(req("alice", "dr-smith", "10:00"));
            assertTrue(service.availability("dr-smith").get("10:00"));
            assertFalse(service.availability("dr-smith").get("09:00")); // untouched
        }

        @ParameterizedTest
        @MethodSource("com.ankur.design.lld.doctor.AppointmentServiceTest#allServices")
        void bookingUnknownDoctorReturnsFalse(AppointmentService service) {
            assertFalse(service.book(req("alice", "dr-nobody", "09:00")));
        }

        @ParameterizedTest
        @MethodSource("com.ankur.design.lld.doctor.AppointmentServiceTest#allServices")
        void bookingUnknownSlotReturnsFalse(AppointmentService service) {
            assertFalse(service.book(req("alice", "dr-smith", "23:59")));
        }

        @ParameterizedTest
        @MethodSource("com.ankur.design.lld.doctor.AppointmentServiceTest#allServices")
        void bookingOneSlotDoesNotAffectOtherSlots(AppointmentService service) {
            service.book(req("alice", "dr-smith", "09:00"));
            assertFalse(service.availability("dr-smith").get("10:00"));
            assertFalse(service.availability("dr-smith").get("11:00"));
        }

        @ParameterizedTest
        @MethodSource("com.ankur.design.lld.doctor.AppointmentServiceTest#allServices")
        void bookingOneDoctoesNotAffectOtherDoctor(AppointmentService service) {
            service.book(req("alice", "dr-smith", "09:00"));
            service.book(req("bob",   "dr-smith", "10:00"));
            service.book(req("carol", "dr-smith", "11:00"));
            // dr-jones slots untouched
            service.availability("dr-jones")
                   .values()
                   .forEach(booked -> assertFalse(booked));
        }
    }

    // -------------------------------------------------------------------------
    // 3. Cancel
    // -------------------------------------------------------------------------
    @Nested
    class Cancellation {

        @ParameterizedTest
        @MethodSource("com.ankur.design.lld.doctor.AppointmentServiceTest#allServices")
        void cancellingBookedSlotSucceeds(AppointmentService service) {
            service.book(req("alice", "dr-smith", "09:00"));
            assertTrue(service.cancel(req("alice", "dr-smith", "09:00")));
        }

        @ParameterizedTest
        @MethodSource("com.ankur.design.lld.doctor.AppointmentServiceTest#allServices")
        void cancelledSlotBecomesBookableAgain(AppointmentService service) {
            service.book(req("alice", "dr-smith", "09:00"));
            service.cancel(req("alice", "dr-smith", "09:00"));
            assertTrue(service.book(req("bob", "dr-smith", "09:00")));
        }

        @ParameterizedTest
        @MethodSource("com.ankur.design.lld.doctor.AppointmentServiceTest#allServices")
        void cancellingFreeSlotReturnsFalse(AppointmentService service) {
            assertFalse(service.cancel(req("alice", "dr-smith", "09:00")));
        }

        @ParameterizedTest
        @MethodSource("com.ankur.design.lld.doctor.AppointmentServiceTest#allServices")
        void cancelledSlotShowsAsFreeInAvailability(AppointmentService service) {
            service.book(req("alice", "dr-smith", "09:00"));
            service.cancel(req("alice", "dr-smith", "09:00"));
            assertFalse(service.availability("dr-smith").get("09:00"));
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------
    static AppointmentRequest req(String patient, String doctor, String slot) {
        return new AppointmentRequest(patient, doctor, slot);
    }
}