package io.dataroots.savingstreak.rewards;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seventy-two hours themselves, at the rule rather than over HTTP.
 *
 * <p>Beside {@link VoucherShelfLifeTest} and for the same reason it gives at length: the only way
 * to move time in this application is {@code POST /api/dev/clock/advance}, which takes whole days
 * and refuses to go backwards, so no request an API test can make lands anywhere near a boundary.
 * The API test for a hold winds the clock a comfortable number of days past the deadline
 * precisely so that it is about the sweep working rather than about which hour of which day the
 * suite happens to run on — which is exactly what makes it blind to the two things asserted here:
 * that the span is seventy-two hours to the second, and that it is seventy-two hours of real time
 * rather than three turns of a calendar.
 *
 * <p>The spec allows one exception of this kind and the voucher's shelf life already took it;
 * this is the same exception applied to the same sort of arithmetic in the same module, and it
 * knows nothing about a hold, a row or the database.
 */
class TheShelfLifeOfAHoldTest {

    private static final ZoneId BRUSSELS = SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN;

    /** Seventy-two hours after the moment it was taken, to the second. */
    @Test
    void a_hold_lapses_seventy_two_hours_after_it_was_taken() {
        Instant takenAt = brussels(2026, 3, 10, 14, 25);

        assertThat(TheShelfLifeOfAHold.theMomentItLapses(takenAt))
                .isEqualTo(takenAt.plus(Duration.ofHours(72)));
    }

    /** And the number in the sentence a customer reads is the number the arithmetic uses. */
    @Test
    void the_span_is_the_one_the_constant_names() {
        Instant takenAt = brussels(2026, 6, 1, 8, 0);

        assertThat(Duration.between(takenAt, TheShelfLifeOfAHold.theMomentItLapses(takenAt)))
                .isEqualTo(Duration.ofHours(TheShelfLifeOfAHold.THE_HOURS_A_HOLD_LASTS));
    }

    /**
     * <strong>Hours and not calendar days, which is the whole reason this file exists.</strong>
     *
     * <p>The last Sunday in March is twenty-three hours long in Brussels. A hold taken on the
     * Friday evening before it and measured in three calendar days would lapse an hour early; a
     * customer who took one the week before would have had an hour more, for a reason nobody
     * could explain to them. Seventy-two hours is seventy-two hours for everybody, which is what
     * the spec promises and what this pins.
     */
    @Test
    void seventy_two_hours_is_seventy_two_hours_across_a_clock_change() {
        // The spring change in 2026 is on Sunday the 29th of March, so this hold spans it.
        Instant takenAt = brussels(2026, 3, 27, 18, 0);

        Instant lapsesAt = TheShelfLifeOfAHold.theMomentItLapses(takenAt);

        assertThat(Duration.between(takenAt, lapsesAt))
                .as("a real seventy-two hours, whatever the calendar did in the middle of them")
                .isEqualTo(Duration.ofHours(72));
        assertThat(lapsesAt.atZone(BRUSSELS).toLocalDateTime())
                .as("which reads locally as an hour later than the moment it started, "
                        + "because an hour of that weekend did not happen")
                .isEqualTo(LocalDateTime.of(2026, 3, 30, 19, 0));
    }

    /**
     * <strong>The number is seventy-two rather than something a trainer could not demonstrate.
     * </strong>
     *
     * <p>An afternoon would be the better business rule and it is ruled out by the only clock
     * this application can move: {@code MovableClock} advances in whole calendar days. Anything
     * shorter than a day could never be wound past in a training session, so it would be a rule
     * nobody could ever show working. This asserts the floor that argument implies, which is the
     * thing somebody shortening the constant would break.
     */
    @Test
    void a_hold_lasts_longer_than_one_wind_of_the_clock() {
        assertThat(TheShelfLifeOfAHold.THE_HOURS_A_HOLD_LASTS)
                .as("shorter than a day cannot be demonstrated, because the clock moves in days")
                .isGreaterThan(24);
    }

    private static Instant brussels(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(BRUSSELS).toInstant();
    }
}
