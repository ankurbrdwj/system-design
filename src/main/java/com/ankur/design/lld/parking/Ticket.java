package com.ankur.design.lld.parking;

import java.util.concurrent.locks.ReentrantLock;

// pure data object — holds state, no business logic
class Ticket {
    final String ticketId;
    final String vehicleNumber;
    final String spotId;
    final long   entryTimeMs;

    TicketState state = TicketState.ACTIVE;
    final ReentrantLock lock = new ReentrantLock();

    Ticket(String ticketId, String vehicleNumber, String spotId) {
        this.ticketId      = ticketId;
        this.vehicleNumber = vehicleNumber;
        this.spotId        = spotId;
        this.entryTimeMs   = System.currentTimeMillis();
    }
}