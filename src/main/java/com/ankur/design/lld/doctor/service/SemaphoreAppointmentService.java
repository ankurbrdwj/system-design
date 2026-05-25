package com.ankur.design.lld.doctor.service;

import com.ankur.design.lld.doctor.model.AppointmentRequest;
import com.ankur.design.lld.doctor.model.Doctor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Strategy: Semaphore(1) per slot.
 *
 * book:   tryAcquire() — non-blocking; grabs the single permit or returns false
 * cancel: release()    — returns the permit
 *
 * Most semantically expressive for slot semantics: "one permit = one appointment".
 * Compare to SemaphoreRoomService which uses Semaphore(capacity) for rooms > 1.
 *
 * Trade-off vs CAS: Semaphore carries slightly more overhead (internal AQS state),
 * but reads more naturally in an interview — tryAcquire() IS the booking.
 */
public class SemaphoreAppointmentService implements AppointmentService {

    private final Map<String, Doctor> doctors = new HashMap<>();
    // key = "doctorId:slotId" -> Semaphore(1)
    private final Map<String, Semaphore> slotSemaphores = new ConcurrentHashMap<>();

    @Override
    public void addDoctor(String doctorId, String name, List<String> slotIds) {
        Doctor doctor = new Doctor(doctorId, name);
        slotIds.forEach(slotId -> {
            doctor.addSlot(slotId);
            slotSemaphores.put(key(doctorId, slotId), new Semaphore(1));
        });
        doctors.put(doctorId, doctor);
    }

    @Override
    public boolean book(AppointmentRequest request) {
        Semaphore semaphore = slotSemaphores.get(key(request.getDoctorId(), request.getSlotId()));
        if (semaphore == null) return false;
        return semaphore.tryAcquire();
    }

    @Override
    public boolean cancel(AppointmentRequest request) {
        Semaphore semaphore = slotSemaphores.get(key(request.getDoctorId(), request.getSlotId()));
        if (semaphore == null) return false;
        // Only release if the slot is actually booked (0 permits means booked).
        // Note: there's a slight TOCTOU window between check and release —
        // acceptable for an interview; production would use CAS or synchronized.
        if (semaphore.availablePermits() == 1) return false; // already free
        semaphore.release();
        return true;
    }

    @Override
    public Map<String, Boolean> availability(String doctorId) {
        Doctor doctor = doctors.get(doctorId);
        if (doctor == null) return Map.of();
        Map<String, Boolean> result = new LinkedHashMap<>();
        doctor.getAllSlots().forEach(slot -> {
            Semaphore semaphore = slotSemaphores.get(key(doctorId, slot.getSlotId()));
            // 0 permits = booked, 1 permit = free
            result.put(slot.getSlotId(), semaphore != null && semaphore.availablePermits() == 0);
        });
        return result;
    }

    private String key(String doctorId, String slotId) {
        return doctorId + ":" + slotId;
    }
}