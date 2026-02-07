package com.ankur.design.multithreaded.correctness;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MovieTheater {
    /**
     * MOVIE TICKET BOOKING SYSTEM
     * Problem: Multiple threads can book the same seat
     * Problem 1: Check-Then-Act (Race Condition)
     * The Broken Code
     */
    private Set<String> bookedSeats = new HashSet<>();
    private final Lock lock = new ReentrantLock();

    /**
     * ❌ BROKEN: Race condition
     * Thread 1: checks seat A1 available
     * Thread 2: checks seat A1 available (before Thread 1 books)
     * Thread 1: books seat A1
     * Thread 2: books seat A1 (DOUBLE BOOKING!)
     */
    public boolean bookSeat(String seatNumber) {
        // CHECK
        lock.lock(); // Acquire lock
        try {
            if (bookedSeats.contains(seatNumber)) {
                return false; // Already booked
            }

            // ⚠️ GAP: Another thread can sneak in here!

            // ACT
            bookedSeats.add(seatNumber);
            return true;
        } finally {
            lock.unlock(); // ALWAYS release in finally!
        }
    }
}
