package com.ankur.design.lld.doctor.model;

/**
 * Represents a single appointment slot for a doctor.
 *
 * `booked` is intentionally a plain boolean (not thread-safe) —
 * the service layer is responsible for safe concurrent access.
 * This mirrors Room.java in the Kayak module.
 */
public class TimeSlot {

    private final String slotId;
    private boolean booked;

    public TimeSlot(String slotId) {
        this.slotId = slotId;
        this.booked = false;
    }

    public String getSlotId() { return slotId; }
    public boolean isBooked()  { return booked; }
    public void setBooked(boolean booked) { this.booked = booked; }

    @Override
    public String toString() {
        return "TimeSlot{id='" + slotId + "', booked=" + booked + "}";
    }
}