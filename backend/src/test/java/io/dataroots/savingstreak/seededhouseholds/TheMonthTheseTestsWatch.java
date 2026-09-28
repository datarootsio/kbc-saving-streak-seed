package io.dataroots.savingstreak.seededhouseholds;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where in the calendar a test of the seeded households starts watching, and how long a month is
 * from there.
 *
 * <p><strong>Why any of this is needed.</strong> A test runs on whatever day somebody happens to run
 * it, and the seed's whole shape is days of the month: a salary on the 25th, a rent on the 1st. A
 * test that simply wound a month on from "now" would be watching a different stretch of calendar
 * every day of the year — sometimes with two of Bram's paydays in it and sometimes with none of
 * Anke's bills — and would pass or fail by the date rather than by the application. So the clock is
 * first wound to a chosen day of the month, and the month watched is counted from there.
 *
 * <p><strong>Why the 26th.</strong> It is the one day that puts the two households on opposite sides
 * of their own paydays: Anke's has just gone by and her next is a month away, while Bram's is two
 * days off. That is exactly the difference the seed was written to have — his rent falls four days
 * after his salary, hers a week after hers and with a month of bills already paid in between — and
 * it is what lets one sweep rule starve one household and leave the other whole.
 *
 * <p>It also makes the month that follows a clean one. From the 26th to the 26th is a calendar
 * month, so every day of the month the seed names — the 1st, the 5th, the 12th, the 20th, the 25th
 * and the 28th — falls in it exactly once, whatever the length of the months involved. A window of
 * "thirty-one days" does not have that property: thirty-one days across a February contains two 28ths
 * and two of Bram's salaries.
 *
 * <p>Shared by the three tests that watch a month rather than copied into each, for the reason the
 * fixtures it builds on give: three copies of "where does a month start" are three chances to
 * disagree about it.
 */
final class TheMonthTheseTestsWatch {

    /** The day of the month a watched month starts and ends on, and the paragraphs above say why. */
    private static final int THE_TWENTY_SIXTH = 26;

    private TheMonthTheseTestsWatch() {
    }

    /**
     * Winds the clock to the next 26th and settles everything that fell due on the way, so that the
     * month about to be watched starts from a household that is up to date rather than from one
     * carrying whatever the seeding day happened to leave behind.
     *
     * <p>The three runs are fired once each rather than night by night, which is right here and
     * wrong afterwards: no saving rule stands yet, so there is no rule for a bill to meet the wrong
     * side of, and every bill in the stretch is presented against an account that has had its
     * salary. Nothing goes unpaid, which is asserted rather than assumed — a test whose starting
     * point already owed something would be measuring the wrong month.
     *
     * @return the day the clock now reads, which is the day the watched month starts on
     */
    static LocalDate startsOnTheTwentySixth(AnApplicationWithAClockToMove app, String... households) {
        LocalDate today = app.theDateTheClockReads();
        LocalDate theTwentySixth = today.getDayOfMonth() < THE_TWENTY_SIXTH
                ? today.withDayOfMonth(THE_TWENTY_SIXTH)
                : today.plusMonths(1).withDayOfMonth(THE_TWENTY_SIXTH);
        app.daysPass(ChronoUnit.DAYS.between(today, theTwentySixth));
        app.runJob("creditMonthlyIncome");
        app.runJob("fireSavingRulesDue");
        app.runJob("takeBillsDue");

        assertThat(app.theDateTheClockReads()).isEqualTo(theTwentySixth);
        for (String household : households) {
            assertThat(app.arrearsOf(household))
                    .as("catching up to " + theTwentySixth + " left " + household + " owing "
                            + "nothing, which is where the month this test is about starts")
                    .isEmpty();
        }
        return theTwentySixth;
    }

    /** How many nights a calendar month is, counted from the day the watching started. */
    static int nightsInTheMonthFrom(LocalDate start) {
        return (int) ChronoUnit.DAYS.between(start, start.plusMonths(1));
    }
}
