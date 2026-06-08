package com.ankur.design.lld.parking;

import java.math.BigDecimal;

// WHAT the parking system does — callers depend on this, not on ParkingLotService
// Change: swap in-memory impl for DB-backed impl without touching callers
public interface ParkingService {
    Ticket     enter(String vehicleNumber) throws InterruptedException;
    BigDecimal pay(String ticketId);
    void       exit(String ticketId);
    int        availableSpots();
}