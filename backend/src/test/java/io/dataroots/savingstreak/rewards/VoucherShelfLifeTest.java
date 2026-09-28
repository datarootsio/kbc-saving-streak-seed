package io.dataroots.savingstreak.rewards;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shelf-life arithmetic itself, at the rule rather than over HTTP.
 *
 * <p>The second test in this repo that is not an API test, and it is here for the reason the first
 * one gives at length: the only way to move time in this application is
 * {@code POST /api/dev/clock/advance}, which takes whole days and refuses to go backwards, so no
 * request a test can make lands on a boundary. The API test for a shelf life winds the clock a
 * comfortable number of days past a deadline precisely so that it is about the sweep working
 * rather than about which hour of which day the run happens on — which is exactly what makes it
 * blind to the two things asserted here: that the last day is inclusive, and that the day is read
 * in the zone this application counts calendars in rather than in the machine's.
 *
 * <p>The spec allows exactly this one exception, "a pure unit test over window and shelf-life date
 * arithmetic, which would duplicate the existing pure unit test over points expiry", and this is
 * that duplicate. Nothing here knows anything about a claim, a voucher or the database.
 */
class VoucherShelfLifeTest {

    private static final ZoneId BRUSSELS = SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN;

    /**
     * A shelf life of one day is the day it was claimed on, and nothing after it.
     *
     * <p>The boundary the whole reading rests on. If the day it runs out were the first day it is
     * <em>not</em> good, this would answer the 16th, and a customer told "runs out on the 16th"
     * would be turned away at a counter on the 16th.
     */
    @Test
    void a_voucher_good_for_one_day_runs_out_on_the_day_it_was_claimed() {
        assertThat(VoucherShelfLife.theDayItRunsOutOn(brussels(2026, 3, 15, 9, 30), 1))
                .isEqualTo(LocalDate.of(2026, 3, 15));
    }

    /**
     * And a week is that day and the six after it, which is what anybody handed a voucher "good
     * for a week" on a Sunday would tell you: it is good next Saturday and not the Sunday after.
     */
    @Test
    void a_week_is_the_day_it_was_claimed_and_the_six_after_it() {
        assertThat(VoucherShelfLife.theDayItRunsOutOn(brussels(2026, 3, 15, 9, 30), 7))
                .isEqualTo(LocalDate.of(2026, 3, 21));
    }

    /**
     * Over the end of a month and over the end of a year, because a count of days that was
     * quietly doing arithmetic on the day-of-month number would pass everything above.
     */
    @Test
    void the_count_runs_over_the_end_of_a_month_and_the_end_of_a_year() {
        assertThat(VoucherShelfLife.theDayItRunsOutOn(brussels(2026, 1, 20, 12, 0), 30))
                .isEqualTo(LocalDate.of(2026, 2, 18));
        assertThat(VoucherShelfLife.theDayItRunsOutOn(brussels(2026, 12, 28, 12, 0), 14))
                .isEqualTo(LocalDate.of(2027, 1, 10));
        // Through a leap day, which the calendar has and a count of thirty-day months does not.
        assertThat(VoucherShelfLife.theDayItRunsOutOn(brussels(2024, 2, 20, 12, 0), 14))
                .isEqualTo(LocalDate.of(2024, 3, 4));
    }

    /**
     * The day is the day in Brussels, whatever the machine running this is set to.
     *
     * <p>A claim made at half past midnight on the 15th in Brussels is the 14th in UTC, and a
     * shelf life counted off the UTC day would tell that customer their voucher runs out a day
     * earlier than the counter thinks it does. This is the assertion that fails if the zone is
     * dropped, and it is written as an instant rather than as a local time so that it says so.
     */
    @Test
    void the_day_is_counted_in_the_zone_this_application_counts_calendars_in() {
        Instant halfPastMidnightInBrussels = Instant.parse("2026-03-14T23:30:00Z");

        assertThat(VoucherShelfLife.theDayItRunsOutOn(halfPastMidnightInBrussels, 1))
                .as("half past midnight on the 15th in Brussels is still the 14th in UTC")
                .isEqualTo(LocalDate.of(2026, 3, 15));
    }

    /**
     * And it holds across the night the clocks go forward, which is the one night a day is
     * twenty-three hours long. A shelf life counted in fixed twenty-four-hour stretches from the
     * moment of the claim would land an hour short and, for a claim made late in the evening,
     * on the wrong date.
     */
    @Test
    void the_count_holds_over_the_night_the_clocks_change() {
        // The clocks go forward in Brussels in the small hours of Sunday 29 March 2026.
        assertThat(VoucherShelfLife.theDayItRunsOutOn(brussels(2026, 3, 28, 23, 30), 2))
                .isEqualTo(LocalDate.of(2026, 3, 29));
        // And back again on 25 October 2026, the twenty-five-hour day.
        assertThat(VoucherShelfLife.theDayItRunsOutOn(brussels(2026, 10, 24, 23, 30), 2))
                .isEqualTo(LocalDate.of(2026, 10, 25));
    }

    private static Instant brussels(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(BRUSSELS).toInstant();
    }
}
