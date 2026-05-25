package com.ankur.design.lld.doctor.service;

import com.ankur.design.lld.doctor.model.AppointmentRequest;

import java.util.List;
import java.util.Map;

/**
 * Common contract for all appointment booking strategies.
 *
 * Key difference from RoomBookingService: each slot has capacity = 1
 * (binary: free / booked), not capacity > 1. So the concurrency primitive
 * is simpler — AtomicBoolean CAS is a perfect fit.
 */
public interface AppointmentService {

    /** Register a doctor with a set of available slot IDs. */
    void addDoctor(String doctorId, String name, List<String> slotIds);

    /**
     * Attempt to book a slot.
     * @return true if booked, false if slot already taken or unknown
     */
    boolean book(AppointmentRequest request);

    /**
     * Cancel a previously booked slot.
     * @return true if cancelled, false if slot was not booked or unknown
     */
    boolean cancel(AppointmentRequest request);

    /**
     * Current slot status for a doctor: slotId -> true (booked) / false (free).
     */
    Map<String, Boolean> availability(String doctorId);
}