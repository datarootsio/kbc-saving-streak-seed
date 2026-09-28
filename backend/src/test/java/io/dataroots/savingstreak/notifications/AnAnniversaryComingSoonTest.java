package io.dataroots.savingstreak.notifications;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The window itself, at the rule rather than over HTTP.
 *
 * <p>Beside {@link TheBalanceRungsTest} and here for the same reason: the boundaries of a
 * thirty-day window cannot be reached at the HTTP seam without a deposit, a wound clock and an
 * application per case, and the interesting cases are the day either side of the boundary. A test
 * that wound the clock to a day exactly thirty days before an anniversary would also be asserting on
 * the anniversary arithmetic that decides that day, which belongs to Loyalty and is tested there.
 *
 * <p>So the window is asserted here, and the HTTP tests are about the rule that uses it: that a
 * deposit near its anniversary is announced with the reason its place in the withdrawal queue earns
 * it, and that one further off is not announced at all.
 *
 * <p><strong>The window's length is written out here rather than read off the rule.</strong> It was
 * a constant in {@code AnAnniversaryComingSoon} and this class had a test asserting that the
 * constant was thirty days; the constant is gone, because the bank publishes the figure now, and
 * that test went with it — what thirty days is worth arguing about is now argued where version 1 of
 * the scheme is seeded, and pinned by the test of that seed. What is left here is the reading of a
 * window whose length arrives as an argument, asserted at the length the application has always
 * used because those are the boundaries a reader can check against the sentences above.
 */
class AnAnniversaryComingSoonTest {

    /** The zone every calendar rule in this application is decided in. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    /**
     * A moment in the middle of a Brussels day, so that nothing here is accidentally asserting on
     * how a boundary behaves at midnight. Summer time, an hour and a bit before which the UTC
     * instant and the Brussels day disagree — which is exactly the disagreement the zone is here to
     * settle.
     */
    private static final Instant A_MOMENT = LocalDate.of(2027, 6, 15)
            .atTime(14, 30).atZone(BRUSSELS).toInstant();

    /** What day that moment is, in the zone the rule reads it in. */
    private static final LocalDate THAT_DAY = LocalDate.of(2027, 6, 15);

    /**
     * The window version 1 of the scheme publishes, written out rather than read off a rule that no
     * longer holds it: thirty days, which is what a sweep is handed on any night nobody has
     * repriced.
     */
    private static final int THIRTY_DAYS = 30;

    @Test
    void an_anniversary_exactly_thirty_days_off_is_worth_saying_and_one_day_further_is_not() {
        assertThat(AnAnniversaryComingSoon.isWorthSayingAsAt(
                THAT_DAY.plusDays(30), A_MOMENT, THIRTY_DAYS))
                .as("thirty days out is thirty days out, not thirty-one")
                .isTrue();
        assertThat(AnAnniversaryComingSoon.isWorthSayingAsAt(
                THAT_DAY.plusDays(31), A_MOMENT, THIRTY_DAYS))
                .as("a day past the window, which is the only reason the window passes a deposit "
                        + "over")
                .isFalse();
        assertThat(AnAnniversaryComingSoon.theLastDayWorthSayingAsAt(A_MOMENT, THIRTY_DAYS))
                .as("the boundary the sweep quotes when it explains a deposit it passed over")
                .isEqualTo(THAT_DAY.plusDays(30));
    }

    @Test
    void an_anniversary_today_or_still_outstanding_is_worth_saying() {
        assertThat(AnAnniversaryComingSoon.isWorthSayingAsAt(THAT_DAY, A_MOMENT, THIRTY_DAYS))
                .as("an anniversary falling today is as near as an anniversary gets")
                .isTrue();
        assertThat(AnAnniversaryComingSoon.isWorthSayingAsAt(
                THAT_DAY.minusDays(1), A_MOMENT, THIRTY_DAYS))
                .as("a day just gone is an anniversary the sweep that pays has not settled yet, so "
                        + "the money is exposed right now; saying nothing because the day has "
                        + "technically passed is the one silence a customer would call a bug")
                .isTrue();
        assertThat(AnAnniversaryComingSoon.isWorthSayingAsAt(
                THAT_DAY.minusYears(1), A_MOMENT, THIRTY_DAYS))
                .as("the same reading, however long it has been outstanding — Loyalty never "
                        + "reports a day that has been settled")
                .isTrue();
    }

    @Test
    void the_window_is_measured_from_the_brussels_day_and_not_from_the_utc_one() {
        // Half past midnight in Brussels on 1 January is still half past eleven on 31 December in
        // UTC, so a window measured off the UTC day would reach one day short of this one.
        Instant justAfterMidnightInBrussels = LocalDate.of(2028, 1, 1)
                .atTime(0, 30).atZone(BRUSSELS).toInstant();

        assertThat(AnAnniversaryComingSoon.theLastDayWorthSayingAsAt(
                justAfterMidnightInBrussels, THIRTY_DAYS))
                .as("the day it is in the zone this application counts calendars in, which is the "
                        + "zone the anniversary day itself came out of")
                .isEqualTo(LocalDate.of(2028, 1, 31));
    }
}
