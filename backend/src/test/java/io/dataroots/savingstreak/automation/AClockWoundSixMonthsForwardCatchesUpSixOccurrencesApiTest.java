package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RuleOccurrenceView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A clock wound six months forward and one run of the job fires six monthly occurrences, oldest
 * first, each one recorded against the day it was due and the moment it was actually made — and a
 * second run straight after fires nothing.
 *
 * <p>User stories 36, 37, 38 and 40. Downtime is the application's fault and a fortnight of it does
 * not cost a customer a fortnight of saving; on a wound clock catching up is not even a nicety but
 * the <em>only</em> way an occurrence past today ever fires, because {@code MovableClock} moves in
 * whole calendar days and the cron never fires for the days it skipped.
 *
 * <p><strong>Each occurrence says how late it was, and that is asserted day by day.</strong> The
 * money is deliberately not back-dated — {@code DepositsService} prices points off the streak run as
 * it stands when a deposit is counted, so a deposit inserted five months ago would mis-price every
 * manual deposit made after it — so six transfers all made this morning is exactly what a customer
 * sees. What makes that a history rather than a mystery is that each one carries the day it was due
 * as well, and the gap said out loud. A run that reported every occurrence as nought days late would
 * satisfy "six transfers happened" and would have thrown the interesting half away.
 *
 * <p><strong>A fixed amount rather than a sweep.</strong> A sweep that fired too many times finds the
 * account already at its floor and moves nothing, so a miscount hides behind balances that look
 * right; a fixed amount moves the whole of itself every time it fires, which is what makes a
 * seventh firing visible as money.
 *
 * <p>The day of the month is taken from wherever the clock happens to stand and clamped to the 28th,
 * so that this test is about catching up rather than about the month-end clamp — which
 * {@link AMonthlyRuleFiresOnItsDateAndOnTheLastDayOfFebruaryApiTest} and
 * {@link WhichOccurrencesAreDueTest} own between them. The day the application comes back on is
 * that same clamped day six months on, rather than six months on from today, so that what this
 * asserts does not quietly depend on which day of the month the suite is run: on the 29th, 30th or
 * 31st a return on the calendar day would be one to three days past the occurrence that is due, and
 * the last one would not be the nought days late this test says it is.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class AClockWoundSixMonthsForwardCatchesUpSixOccurrencesApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String TEN_EUROS = "10.00";

    /** Six months of downtime, which is what the ticket asks about. */
    private static final int MONTHS_OF_DOWNTIME = 6;

    /**
     * The latest day of the month every month has. Taken so that the rule's day is never clamped,
     * because a clamped day is a different test.
     */
    private static final int A_DAY_EVERY_MONTH_HAS = 28;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseDowntimeThisTestStages() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-six-months-of-downtime"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void six_months_of_downtime_are_caught_up_in_one_run_oldest_first_and_then_there_is_nothing_left() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayItWasLeftStanding = app.theDateTheClockReads();
        int dayOfMonth = Math.min(theDayItWasLeftStanding.getDayOfMonth(), A_DAY_EVERY_MONTH_HAS);
        BigDecimal savedBefore = app.balancesOf(savingsAccount).moneyBalance();
        BigDecimal heldBefore = app.currentAccountBalanceOf(ANKE);

        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryMonth(app.currentAccountOf(ANKE),
                        "Ten a month, through the outage", String.valueOf(dayOfMonth), TEN_EUROS));

        // Nobody runs anything for six months: the application is down, and the cron it would have
        // run under never fires for the days a wound clock skips either.
        // Back on the rule's own day of the month, and not merely six months on from wherever the
        // real calendar happens to stand: on the 29th, 30th or 31st the rule's day is clamped to the
        // 28th, so a return six months later to the day would land two days past the last occurrence
        // and the closing assertion — that the last one was not late at all — would be true three
        // weeks in four and a calendar-dependent time bomb on the rest.
        LocalDate theDayItCameBack = theDayItWasLeftStanding.plusMonths(MONTHS_OF_DOWNTIME)
                .withDayOfMonth(dayOfMonth);
        windTo(theDayItCameBack);
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> caughtUp = oldestFirst(app.historyOf(savingsAccount, rule.id()));

        assertThat(caughtUp)
                .as("six months of downtime is six occurrences and not one: the days the "
                        + "application was not there for are still days the customer asked to save on")
                .hasSize(MONTHS_OF_DOWNTIME);
        assertThat(caughtUp)
                .extracting(RuleOccurrenceView::dueOn)
                .as("the day of the month its holder named, in each of the six months that went by")
                .containsExactlyElementsOf(theSixDaysThatFellBetween(theDayItWasLeftStanding,
                        dayOfMonth));
        assertThat(caughtUp)
                .extracting(RuleOccurrenceView::id)
                .as("oldest first: the occurrences were written in the order the days actually "
                        + "fell, which is the order a history has to read in to be reconcilable")
                .isSorted();

        assertThat(caughtUp)
                .allSatisfy(occurrence -> {
                    assertThat(occurrence.outcome()).isEqualTo("MOVED");
                    assertThat(occurrence.amount()).isEqualByComparingTo(new BigDecimal(TEN_EUROS));
                    assertThat(occurrence.depositId())
                            .as("an occurrence that moved money names the deposit it made")
                            .isNotNull();
                    assertThat(dayOf(occurrence.settledAt()))
                            .as("the money is dated when it was actually moved rather than "
                                    + "back-dated to the day it was due, which is the whole reason "
                                    + "the lateness has to be reported instead")
                            .isEqualTo(theDayItCameBack);
                });

        assertThat(caughtUp)
                .extracting(RuleOccurrenceView::daysLate)
                .as("and each one says how late it was, to the day: the oldest is nearly six "
                        + "months late and the last is not late at all")
                .containsExactlyElementsOf(caughtUp.stream()
                        .map(occurrence -> ChronoUnit.DAYS.between(occurrence.dueOn(), theDayItCameBack))
                        .toList());
        assertThat(caughtUp.get(0).daysLate())
                .as("the first occurrence fell five months before the application came back, so "
                        + "its lateness is the number of days in those five months")
                .isEqualTo(ChronoUnit.DAYS.between(caughtUp.get(0).dueOn(), theDayItCameBack))
                .isGreaterThan(120);
        assertThat(caughtUp.get(MONTHS_OF_DOWNTIME - 1).daysLate())
                .as("and the last of them fell on the very day the application came back, so it "
                        + "was not late at all — a catch-up that called everything equally late "
                        + "would be as useless as one that called nothing late")
                .isZero();

        BigDecimal sixTransfers = new BigDecimal(TEN_EUROS).multiply(BigDecimal.valueOf(MONTHS_OF_DOWNTIME));
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .as("six transfers of ten euros is sixty euros of real money in the savings account")
                .isEqualByComparingTo(savedBefore.add(sixTransfers));
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and sixty euros fewer in the current account they came out of")
                .isEqualByComparingTo(heldBefore.subtract(sixTransfers));

        // The trainer runs it again, which is the ordinary thing to do to a job you have just typed
        // the name of.
        app.runJob(THE_JOB);

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("running the job again straight after a catch-up fires nothing: every day it "
                        + "would look at has a row under it, and the cursor is already past them")
                .hasSize(MONTHS_OF_DOWNTIME);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .as("and no euro moved a second time")
                .isEqualByComparingTo(savedBefore.add(sixTransfers));
        assertThat(app.currentAccountBalanceOf(ANKE))
                .isEqualByComparingTo(heldBefore.subtract(sixTransfers));
    }

    /** The history as the days fell, because the endpoint reports it newest first. */
    private static List<RuleOccurrenceView> oldestFirst(List<RuleOccurrenceView> history) {
        return history.stream()
                .sorted(Comparator.comparing(RuleOccurrenceView::id))
                .toList();
    }

    /**
     * The six days that day of the month falls on in the six months after the rule was written —
     * worked out here from the calendar the test itself can see, so that this asserts on days rather
     * than on however many rows happened to be written.
     */
    private static List<LocalDate> theSixDaysThatFellBetween(LocalDate theDayItWasLeftStanding,
                                                             int dayOfMonth) {
        return IntStream.rangeClosed(1, MONTHS_OF_DOWNTIME)
                .mapToObj(month -> theDayItWasLeftStanding.plusMonths(month).withDayOfMonth(dayOfMonth))
                .toList();
    }

    /** The day a moment falls on, in the calendar this application counts every day in. */
    private static LocalDate dayOf(Instant moment) {
        return moment.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
    }

    /** The clock forward to that day, through the endpoint a trainer would use. */
    private static void windTo(LocalDate day) {
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), day));
        assertThat(app.theDateTheClockReads()).isEqualTo(day);
    }
}
