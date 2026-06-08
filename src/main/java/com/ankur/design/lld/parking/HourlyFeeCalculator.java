package com.ankur.design.lld.parking;

import java.math.BigDecimal;

public class HourlyFeeCalculator implements FeeCalculator {

    private final long ratePerHour;

    public HourlyFeeCalculator(long ratePerHour) {
        this.ratePerHour = ratePerHour;
    }

    @Override
    public BigDecimal calculate(Ticket ticket) {
        long elapsedMs   = System.currentTimeMillis() - ticket.entryTimeMs;
        long hoursParked = Math.max(1, (long) Math.ceil(elapsedMs / 3_600_000.0));
        return BigDecimal.valueOf(hoursParked * ratePerHour);
    }
}