package com.ankur.design.lld.doctor.service;

import com.ankur.design.lld.doctor.model.AppointmentRequest;
import com.ankur.design.lld.doctor.model.Doctor;
import com.ankur.design.lld.doctor.model.TimeSlot;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy: synchronized(slot) — lock per slot object, not a global lock.
 *
 * Two threads booking different slots of the same doctor never block each other.
 * Only threads competing for the *same* slot contend.
 *
 * Simplest correct solution — good starting point in an interview.
 * JVM can apply biased locking at low contention (~1 ns overhead).
 */
public class SynchronizedAppointmentService implements AppointmentService {

    private final Map<String, Doctor> doctors = new HashMap<>();

    @Override
    public void addDoctor(String doctorId, String name, List<String> slotIds) {
        Doctor doctor = new Doctor(doctorId, name);
        slotIds.forEach(doctor::addSlot);
        doctors.put(doctorId, doctor);
    }

    @Override
    public boolean book(AppointmentRequest request) {
        TimeSlot slot = resolve(request);
        if (slot == null) return false;

        synchronized (slot) {
            if (slot.isBooked()) return false;
            slot.setBooked(true);
            return true;
        }
    }

    @Override
    public boolean cancel(AppointmentRequest request) {
        TimeSlot slot = resolve(request);
        if (slot == null) return false;

        synchronized (slot) {
            if (!slot.isBooked()) return false;
            slot.setBooked(false);
            return true;
        }
    }

    @Override
    public Map<String, Boolean> availability(String doctorId) {
        Doctor doctor = doctors.get(doctorId);
        if (doctor == null) return Map.of();
        Map<String, Boolean> result = new LinkedHashMap<>();
        doctor.getAllSlots().forEach(s -> result.put(s.getSlotId(), s.isBooked()));
        return result;
    }

    private TimeSlot resolve(AppointmentRequest request) {
        Doctor doctor = doctors.get(request.getDoctorId());
        if (doctor == null) return null;
        return doctor.getSlot(request.getSlotId());
    }
}