package com.ankur.design.lld.doctor.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a doctor and their available time slots.
 */
public class Doctor {

    private final String doctorId;
    private final String name;
    private final Map<String, TimeSlot> slots = new LinkedHashMap<>();

    public Doctor(String doctorId, String name) {
        this.doctorId = doctorId;
        this.name = name;
    }

    public void addSlot(String slotId) {
        slots.put(slotId, new TimeSlot(slotId));
    }

    public TimeSlot getSlot(String slotId) { return slots.get(slotId); }
    public Collection<TimeSlot> getAllSlots() { return slots.values(); }
    public String getDoctorId() { return doctorId; }
    public String getName()     { return name; }

    @Override
    public String toString() {
        return "Doctor{id='" + doctorId + "', name='" + name + "', slots=" + slots.size() + "}";
    }
}