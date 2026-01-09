package com.ankur.design.ppro;

import java.time.LocalDate;

public class BillingPeriod {
    private final int periodNumber;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public BillingPeriod(int periodNumber, LocalDate startDate, LocalDate endDate) {
        this.periodNumber = periodNumber;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public int getPeriodNumber() {
        return periodNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
