package com.ankur.design.lld.doctor.service;

import com.ankur.design.lld.doctor.model.AppointmentRequest;
import com.ankur.design.lld.doctor.model.Doctor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Strategy: AtomicBoolean.compareAndSet() per slot — lock-free CAS.
 *
 * book:   compareAndSet(false, true)  — succeeds only for the one thread
 *         that sees false first; all others get false atomically.
 * cancel: compareAndSet(true, false)
 *
 * No thread ever blocks. Best throughput under sustained high contention.
 * Perfect fit for binary (free/booked) state — simpler than AtomicInteger CAS loop.
 *
 * Compare to AtomicRoomService which loops: here a single CAS is enough
 * because there is no "count > 1" to worry about.
 */
public class AtomicAppointmentService implements AppointmentService {

    private final Map<String, Doctor> doctors = new HashMap<>();
    // key = "doctorId:slotId" -> AtomicBoolean (false=free, true=booked)
    private final Map<String, AtomicBoolean> slotState = new ConcurrentHashMap<>();

    @Override
    public void addDoctor(String doctorId, String name, List<String> slotIds) {
        Doctor doctor = new Doctor(doctorId, name);
        slotIds.forEach(slotId -> {
            doctor.addSlot(slotId);
            slotState.put(key(doctorId, slotId), new AtomicBoolean(false));
        });
        doctors.put(doctorId, doctor);
    }

    @Override
    public boolean book(AppointmentRequest request) {
        AtomicBoolean state = slotState.get(key(request.getDoctorId(), request.getSlotId()));
        if (state == null) return false;
        return state.compareAndSet(false, true);
    }

    @Override
    public boolean cancel(AppointmentRequest request) {
        AtomicBoolean state = slotState.get(key(request.getDoctorId(), request.getSlotId()));
        if (state == null) return false;
        return state.compareAndSet(true, false);
    }

    @Override
    public Map<String, Boolean> availability(String doctorId) {
        Doctor doctor = doctors.get(doctorId);
        if (doctor == null) return Map.of();
        Map<String, Boolean> result = new LinkedHashMap<>();
        doctor.getAllSlots().forEach(slot -> {
            AtomicBoolean state = slotState.get(key(doctorId, slot.getSlotId()));
            result.put(slot.getSlotId(), state != null && state.get());
        });
        return result;
    }

    private String key(String doctorId, String slotId) {
        return doctorId + ":" + slotId;
    }
}