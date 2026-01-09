package com.ankur.design.ppro.service;

import com.ankur.design.ppro.BillingPeriod;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BillingPeriodService {

    public BillingPeriod getPeriodForDate(LocalDate date) {
        int year = date.getYear();
        List<LocalDate> periodStarts = calculatePeriodStarts(year);

        for (int i = 0; i < periodStarts.size(); i++) {
            LocalDate periodStart = periodStarts.get(i);
            LocalDate periodEnd = (i < periodStarts.size() - 1)
                ? periodStarts.get(i + 1).minusDays(1)
                : LocalDate.of(year, 12, 31);

            if (!date.isBefore(periodStart) && !date.isAfter(periodEnd)) {
                return new BillingPeriod(i + 1, periodStart, periodEnd);
            }
        }

        return null;
    }

    private List<LocalDate> calculatePeriodStarts(int year) {
        List<LocalDate> starts = new ArrayList<>();
        LocalDate current = LocalDate.of(year, 1, 1);
        LocalDate endOfYear = LocalDate.of(year, 12, 31);

        starts.add(current);
        current = current.plusDays(1);

        while (!current.isAfter(endOfYear)) {
            if (current.getDayOfWeek() == DayOfWeek.SATURDAY || current.getDayOfMonth() == 1) {
                starts.add(current);
            }
            current = current.plusDays(1);
        }

        return starts;
    }
}