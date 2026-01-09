package com.ankur.design.ppro;

import com.ankur.design.ppro.service.BillingPeriodService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BillingPeriodTest {

    private BillingPeriodService billingPeriodService;

    @BeforeEach
    void setUp() {
        billingPeriodService = new BillingPeriodService();
    }

    @Test
    void testFirstPeriodStartsOnJanuary1st() {
        // Given
        int year = 2019;
        LocalDate january1st = LocalDate.of(year, 1, 1);

        // When
        BillingPeriod period = billingPeriodService.getPeriodForDate(january1st);

        // Then
        assertNotNull(period);
        assertEquals(1, period.getPeriodNumber());
        assertEquals(january1st, period.getStartDate());
    }

    @Test
    void testSaturdayStartsNewPeriod() {
        // Given - Jan 1, 2019 is Tuesday, so Jan 5, 2019 is the first Saturday
        LocalDate firstSaturday = LocalDate.of(2019, 1, 5);

        // When
        BillingPeriod period = billingPeriodService.getPeriodForDate(firstSaturday);

        // Then
        assertNotNull(period);
        assertEquals(2, period.getPeriodNumber());
        assertEquals(firstSaturday, period.getStartDate());
    }

    @Test
    void testFirstOfMonthStartsNewPeriod() {
        // Given - Feb 1, 2019 (after Jan has 4 Saturdays: 5, 12, 19, 26)
        // Expected: Period 1 (Jan 1), Period 2 (Jan 5), Period 3 (Jan 12),
        //           Period 4 (Jan 19), Period 5 (Jan 26), Period 6 (Feb 1)
        LocalDate february1st = LocalDate.of(2019, 2, 1);

        // When
        BillingPeriod period = billingPeriodService.getPeriodForDate(february1st);

        // Then
        assertNotNull(period);
        assertEquals(6, period.getPeriodNumber());
        assertEquals(february1st, period.getStartDate());
    }

    @Test
    void testPeriodsAreNumberedConsecutively() {
        // Given - various dates throughout January 2019
        LocalDate jan1 = LocalDate.of(2019, 1, 1);   // Period 1
        LocalDate jan5 = LocalDate.of(2019, 1, 5);   // Period 2 (Saturday)
        LocalDate jan12 = LocalDate.of(2019, 1, 12); // Period 3 (Saturday)
        LocalDate jan19 = LocalDate.of(2019, 1, 19); // Period 4 (Saturday)
        LocalDate jan26 = LocalDate.of(2019, 1, 26); // Period 5 (Saturday)
        LocalDate feb1 = LocalDate.of(2019, 2, 1);   // Period 6 (1st of month)

        // When/Then - verify consecutive numbering
        assertEquals(1, billingPeriodService.getPeriodForDate(jan1).getPeriodNumber());
        assertEquals(2, billingPeriodService.getPeriodForDate(jan5).getPeriodNumber());
        assertEquals(3, billingPeriodService.getPeriodForDate(jan12).getPeriodNumber());
        assertEquals(4, billingPeriodService.getPeriodForDate(jan19).getPeriodNumber());
        assertEquals(5, billingPeriodService.getPeriodForDate(jan26).getPeriodNumber());
        assertEquals(6, billingPeriodService.getPeriodForDate(feb1).getPeriodNumber());
    }

    @Test
    void testFindPeriodContainingGivenDate() {
        // Given - dates within periods (not start dates)
        // Period 2 runs from Jan 5 to Jan 11 (before Jan 12 Saturday)
        LocalDate jan8 = LocalDate.of(2019, 1, 8);   // Wednesday in Period 2
        LocalDate jan11 = LocalDate.of(2019, 1, 11); // Friday in Period 2

        // Period 4 runs from Jan 19 to Jan 25 (before Jan 26 Saturday)
        LocalDate jan23 = LocalDate.of(2019, 1, 23); // Wednesday in Period 4

        // When
        BillingPeriod period2ForJan8 = billingPeriodService.getPeriodForDate(jan8);
        BillingPeriod period2ForJan11 = billingPeriodService.getPeriodForDate(jan11);
        BillingPeriod period4ForJan23 = billingPeriodService.getPeriodForDate(jan23);

        // Then
        assertNotNull(period2ForJan8);
        assertEquals(2, period2ForJan8.getPeriodNumber());
        assertEquals(LocalDate.of(2019, 1, 5), period2ForJan8.getStartDate());
        assertEquals(LocalDate.of(2019, 1, 11), period2ForJan8.getEndDate());

        assertNotNull(period2ForJan11);
        assertEquals(2, period2ForJan11.getPeriodNumber());

        assertNotNull(period4ForJan23);
        assertEquals(4, period4ForJan23.getPeriodNumber());
        assertEquals(LocalDate.of(2019, 1, 19), period4ForJan23.getStartDate());
        assertEquals(LocalDate.of(2019, 1, 25), period4ForJan23.getEndDate());
    }
}
