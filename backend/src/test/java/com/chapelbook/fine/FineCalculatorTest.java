package com.chapelbook.fine;

import static org.assertj.core.api.Assertions.assertThat;

import com.chapelbook.config.LibraryProperties;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FineCalculatorTest {

    private static final Instant DUE = Instant.parse("2030-03-01T12:00:00Z");
    private static final BigDecimal PER_DAY = new BigDecimal("0.25");
    private static final BigDecimal CAP = new BigDecimal("10.00");

    private static BigDecimal fine(Instant returned) {
        return FineCalculator.fineFor(DUE, returned, PER_DAY, CAP);
    }

    @Test
    void noFineWhenReturnedEarlyOrExactlyOnTime() {
        assertThat(fine(DUE.minusSeconds(3600))).isEqualByComparingTo("0.00");
        assertThat(fine(DUE)).isEqualByComparingTo("0.00");
        assertThat(FineCalculator.daysOverdue(DUE, DUE)).isZero();
    }

    @Test
    void aStartedDayCountsAsAWholeDay() {
        assertThat(FineCalculator.daysOverdue(DUE, DUE.plusSeconds(1))).isEqualTo(1);
        assertThat(fine(DUE.plusSeconds(1))).isEqualByComparingTo("0.25");
    }

    @Test
    void exactlyWholeDaysAreNotRoundedUp() {
        assertThat(FineCalculator.daysOverdue(DUE, DUE.plusSeconds(86_400))).isEqualTo(1);
        assertThat(FineCalculator.daysOverdue(DUE, DUE.plusSeconds(3 * 86_400))).isEqualTo(3);
        assertThat(fine(DUE.plusSeconds(3 * 86_400))).isEqualByComparingTo("0.75");
    }

    @Test
    void oneSecondPastAWholeDayStartsTheNextDay() {
        assertThat(FineCalculator.daysOverdue(DUE, DUE.plusSeconds(86_401))).isEqualTo(2);
        assertThat(fine(DUE.plusSeconds(86_401))).isEqualByComparingTo("0.50");
    }

    @Test
    void theFineIsCappedPerLoan() {
        assertThat(fine(DUE.plusSeconds(100 * 86_400L))).isEqualByComparingTo("10.00");
        assertThat(fine(DUE.plusSeconds(40 * 86_400L))).isEqualByComparingTo("10.00");
        assertThat(fine(DUE.plusSeconds(39 * 86_400L))).isEqualByComparingTo("9.75");
    }

    @Test
    void moneyAlwaysHasTwoDecimalPlaces() {
        assertThat(fine(DUE.plusSeconds(86_400)).scale()).isEqualTo(2);
        assertThat(FineCalculator.fineFor(DUE, DUE.plusSeconds(86_400), new BigDecimal("0.333"), CAP))
                .isEqualByComparingTo("0.33");
    }

    @Test
    void policyDefaultsApplyWhenNothingIsConfigured() {
        LibraryProperties defaults = new LibraryProperties(null, null, null, null, null, null, null, null, null,
                null, null, null);

        assertThat(defaults.reservationHoldDays()).isEqualTo(3);
        assertThat(defaults.maxActiveReservations()).isEqualTo(5);
        assertThat(defaults.loanDays()).isEqualTo(14);
        assertThat(defaults.maxLoansPerStudent()).isEqualTo(5);
        assertThat(defaults.maxRenewals()).isEqualTo(2);
        assertThat(defaults.renewalDays()).isEqualTo(14);
        assertThat(defaults.finePerDay()).isEqualByComparingTo("0.25");
        assertThat(defaults.maxFinePerLoan()).isEqualByComparingTo("10.00");
        assertThat(defaults.borrowingBlockedAbove()).isEqualByComparingTo("5.00");
        assertThat(defaults.seatMaxAdvanceDays()).isEqualTo(14);
        assertThat(defaults.seatMinMinutes()).isEqualTo(15);
        assertThat(defaults.maxUpcomingSeatBookings()).isEqualTo(3);
    }

    @Test
    void nonsenseValuesFallBackToTheDefaults() {
        LibraryProperties bad = new LibraryProperties(0, -1, 0, 0, -3, 0, new BigDecimal("-1"), new BigDecimal("-5"),
                new BigDecimal("-2"), 0, 0, 0);

        assertThat(bad.loanDays()).isEqualTo(14);
        assertThat(bad.maxRenewals()).isEqualTo(2);
        assertThat(bad.finePerDay()).isEqualByComparingTo("0.25");
        assertThat(bad.maxFinePerLoan()).isEqualByComparingTo("10.00");
    }

    @Test
    void zeroRenewalsIsAValidPolicy() {
        LibraryProperties noRenewals = new LibraryProperties(null, null, null, null, 0, null, null, null, null, null,
                null, null);

        assertThat(noRenewals.maxRenewals()).isZero();
    }
}
