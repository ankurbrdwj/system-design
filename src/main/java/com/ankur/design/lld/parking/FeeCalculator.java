package com.ankur.design.lld.parking;

import java.math.BigDecimal;

// WHAT fee calculation does — swap hourly/flat/weekend without touching ParkingLotService
// This is the OCP in action: new pricing = new class, zero edits to existing code
public interface FeeCalculator {
    BigDecimal calculate(Ticket ticket);
}