package com.ankur.design.lld.parking;

import java.util.concurrent.atomic.AtomicBoolean;

class ParkingSpot {
    final String id;
    final AtomicBoolean occupied = new AtomicBoolean(false);

    ParkingSpot(String id) { this.id = id; }

    // CAS: only one thread wins this spot — returns true if this thread claimed it
    boolean tryOccupy() { return occupied.compareAndSet(false, true); }

    void release() { occupied.set(false); }

    boolean isOccupied() { return occupied.get(); }
}