package io.dataroots.savingstreak.products;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The monthly calendar an account is paid on, at the rule rather than over HTTP.
 *
 * <p>Here for the reason {@code LoyaltyAnniversaryTest} is: the only way to move time in this
 * application is a development endpoint that takes whole days and refuses to go backwards, so no
 * sequence of requests opens an account on the 31st of January and walks it through a February. The
 * API tests wind a comfortable number of days past each period end precisely so that they are about
 * interest working rather than about which day of which month the run happens on — which is exactly
 * what makes them blind to the clamping.
 *
 * <p>{@link TheMonthlyPeriodsOfAnAccount} argues for one decision above all: a month multiplied by
 * the ordinal and added once, never added a month at a time. Undo it and the test below fails, in
 * the case it was written for.
 */
class TheMonthlyPeriodsOfAnAccountTest {

    /**
     * An account opened on the 31st is clamped in the short months and is back on the 31st in the
     * long ones — because every period end is counted from the day it was opened.
     *
     * <p>This is the case repeated addition gets wrong, and it gets it wrong permanently: the 31st
     * of January plus a month is the 28th of February, and the 28th plus a month is the 28th of
     * March, so an account that adds a month at a time is paid on the 28th for the rest of its
     * life. Multiplied out from the opening day, the clamp applies to the one addition and undoes
     * itself the next time the calendar has room for it.
     */
    @Test
    void an_account_opened_on_the_thirty_first_is_clamped_only_where_the_month_is_short() {
        LocalDate openedOn = LocalDate.of(2026, 1, 31);

        assertThat(TheMonthlyPeriodsOfAnAccount.endOf(openedOn, 1))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(TheMonthlyPeriodsOfAnAccount.endOf(openedOn, 2))
                .isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(TheMonthlyPeriodsOfAnAccount.endOf(openedOn, 3))
                .isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(TheMonthlyPeriodsOfAnAccount.endOf(openedOn, 4))
                .isEqualTo(LocalDate.of(2026, 5, 31));
        // Twelve months on is the day it was opened, a year later, whatever happened in between.
        assertThat(TheMonthlyPeriodsOfAnAccount.endOf(openedOn, 12))
                .isEqualTo(LocalDate.of(2027, 1, 31));
    }

    /** And an account opened on the 29th of February is clamped to the 28th in ordinary years. */
    @Test
    void an_account_opened_on_a_leap_day_is_clamped_to_the_twenty_eighth_until_the_next_one() {
        LocalDate openedOn = LocalDate.of(2024, 2, 29);

        assertThat(TheMonthlyPeriodsOfAnAccount.endOf(openedOn, 1))
                .isEqualTo(LocalDate.of(2024, 3, 29));
        assertThat(TheMonthlyPeriodsOfAnAccount.endOf(openedOn, 12))
                .isEqualTo(LocalDate.of(2025, 2, 28));
        // And back on the leap day when the calendar next has one.
        assertThat(TheMonthlyPeriodsOfAnAccount.endOf(openedOn, 48))
                .isEqualTo(LocalDate.of(2028, 2, 29));
    }

    /**
     * One period ends on the day the next begins, so no day belongs to two periods and none belongs
     * to neither.
     */
    @Test
    void every_period_begins_on_the_day_the_one_before_it_ended() {
        LocalDate openedOn = LocalDate.of(2026, 1, 31);

        for (int ordinal = 1; ordinal <= 24; ordinal++) {
            assertThat(TheMonthlyPeriodsOfAnAccount.beginningOf(openedOn, ordinal + 1))
                    .isEqualTo(TheMonthlyPeriodsOfAnAccount.endOf(openedOn, ordinal));
        }
    }

    /**
     * A period that ends exactly today has gone by, and one that ends tomorrow has not.
     *
     * <p>The boundary a nightly sweep lands on every time it pays anything, so it is asserted on
     * both sides. An account opened on the 20th of January is owed its first month on the 20th of
     * February, which is also the first day of its second month — it is never paid twice for that
     * day, because a period does not cover the day it ends on.
     */
    @Test
    void a_period_that_ends_today_has_gone_by_and_one_that_ends_tomorrow_has_not() {
        LocalDate openedOn = LocalDate.of(2026, 1, 20);

        assertThat(TheMonthlyPeriodsOfAnAccount.periodsGoneBy(openedOn, LocalDate.of(2026, 2, 19)))
                .isZero();
        assertThat(TheMonthlyPeriodsOfAnAccount.periodsGoneBy(openedOn, LocalDate.of(2026, 2, 20)))
                .isEqualTo(1);
        assertThat(TheMonthlyPeriodsOfAnAccount.periodsGoneBy(openedOn, LocalDate.of(2026, 3, 19)))
                .isEqualTo(1);
        assertThat(TheMonthlyPeriodsOfAnAccount.periodsGoneBy(openedOn, LocalDate.of(2027, 1, 20)))
                .isEqualTo(12);
    }

    /**
     * Counted by walking the periods rather than by counting the months between two dates, which is
     * wrong in exactly the case the clamping exists for: the 31st of January to the 28th of
     * February is twenty-eight days and no whole month to any month-counting arithmetic, while the
     * period ending that day is plainly the account's first and has plainly ended.
     */
    @Test
    void a_clamped_period_that_has_ended_is_counted_as_gone_by() {
        LocalDate openedOn = LocalDate.of(2026, 1, 31);

        assertThat(TheMonthlyPeriodsOfAnAccount.periodsGoneBy(openedOn, LocalDate.of(2026, 2, 28)))
                .isEqualTo(1);
        assertThat(TheMonthlyPeriodsOfAnAccount.periodsGoneBy(openedOn, LocalDate.of(2026, 2, 27)))
                .isZero();
    }

    /** An account dated after the day being asked about has passed no periods at all. */
    @Test
    void an_account_dated_after_today_has_passed_no_periods() {
        assertThat(TheMonthlyPeriodsOfAnAccount.periodsGoneBy(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1))).isZero();
    }

    /** A period before the first is a mistake in the caller rather than anything a customer did. */
    @Test
    void a_period_before_the_first_is_refused_to_the_caller() {
        assertThatThrownBy(() -> TheMonthlyPeriodsOfAnAccount.beginningOf(
                LocalDate.of(2026, 1, 1), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("first interest period is its first");
    }
}
