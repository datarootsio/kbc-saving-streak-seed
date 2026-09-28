package io.dataroots.savingstreak.timeline;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two days a bar is drawn between, asserted directly, because the HTTP seam cannot reach the
 * cases that matter.
 *
 * <p>The same bargain {@code PointsExpiryTest} and {@code LoyaltyAnniversaryTest} strike, and for
 * the same reason: the only way to move time in this application is the development clock endpoint,
 * which takes whole days and refuses to go backwards. No request an API test can make lands the
 * window's opening day on 29 February, or an hour either side of the clocks going forward, so the
 * API tests wind a comfortable number of days and are about the bar working rather than about which
 * day of which month the run happened on — which is exactly what makes them blind to the boundary.
 *
 * <p>These are the assertions that fail when twelve calendar months are turned into 365 days, when
 * the window is added to the moment rather than to the day, and when the day is read in UTC. The API
 * tests survive all three.
 */
class TimelineHorizonTest {

    /** Half past eleven at night in Brussels on 1 March, which is the small hours of 1 March in UTC. */
    private static final Instant LATE_ON_THE_FIRST_OF_MARCH = Instant.parse("2027-03-01T22:30:00Z");

    @Test
    void the_window_opens_on_the_day_the_clock_reads_in_the_zone_calendars_are_counted_in() {
        assertThat(TimelineHorizon.opensOn(Instant.parse("2026-09-11T09:00:00Z")))
                .isEqualTo(LocalDate.of(2026, 9, 11));
    }

    @Test
    void a_moment_late_at_night_opens_the_window_on_the_day_it_is_in_brussels_not_in_utc() {
        // 22:30 UTC on 1 March is half past eleven at night on 1 March in Brussels in winter — the
        // same day. An hour later it would be the 2nd here and still the 1st in UTC, which is the
        // half-day every year in which a customer would be shown a window opening yesterday.
        assertThat(TimelineHorizon.opensOn(LATE_ON_THE_FIRST_OF_MARCH))
                .isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(TimelineHorizon.opensOn(Instant.parse("2027-03-01T23:30:00Z")))
                .as("half past midnight in Brussels is already the next day, whatever UTC says")
                .isEqualTo(LocalDate.of(2027, 3, 2));
    }

    @Test
    void the_window_closes_twelve_calendar_months_after_it_opens() {
        assertThat(TimelineHorizon.closesOn(LocalDate.of(2026, 9, 11)))
                .isEqualTo(LocalDate.of(2027, 9, 11));
    }

    @Test
    void a_window_opening_on_a_leap_day_closes_on_the_twenty_eighth() {
        // The clamp a calendar gives, and the same one both rules on the bar apply to their own
        // anniversaries. Counted as days instead, this would close on 28 February too — and then be
        // a day out in the three years out of four that are not leap years, which the next case pins.
        assertThat(TimelineHorizon.closesOn(LocalDate.of(2024, 2, 29)))
                .isEqualTo(LocalDate.of(2025, 2, 28));
    }

    @Test
    void a_window_is_twelve_months_rather_than_three_hundred_and_sixty_five_days() {
        // 2024 is a leap year, so the twelve months from 1 March 2023 are 366 days. A horizon
        // counted in days would close this window on 29 February 2024, a day early.
        assertThat(TimelineHorizon.closesOn(LocalDate.of(2023, 3, 1)))
                .isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(LocalDate.of(2023, 3, 1).plusDays(365))
                .as("which is not the same day, and is the answer a count of days would have given")
                .isEqualTo(LocalDate.of(2024, 2, 29));
    }
}
