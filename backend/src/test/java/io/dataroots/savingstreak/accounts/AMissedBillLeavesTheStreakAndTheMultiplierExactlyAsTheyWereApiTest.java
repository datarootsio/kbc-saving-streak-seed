package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A bill that could not be paid leaves the streak, the best run and the multiplier exactly where
 * they were — and carrying the arrear for another month leaves them there too.
 *
 * <p>User story 20. A week is secured by the weekly minimum of new saving and by nothing else. An
 * unpaid bill is a fact about a current account, not a judgement on the saver, and reaching from the
 * billing run into the streak would give the streak a second definition and punish one mistake
 * twice.
 *
 * <p><strong>Asserted around the run rather than across the whole test.</strong> The figures are
 * read immediately before the bills job and immediately after it, so that what is being claimed is
 * that <em>this run</em> changed nothing — a comparison across a stretch of weeks would be satisfied
 * by a streak that had been broken and rebuilt, and would be satisfied by an application that never
 * had a streak at all.
 *
 * <p>Which is why a real streak is secured first, with a deposit of exactly the weekly minimum, and
 * why the figures are insisted on as non-zero before anything is missed. A test that watched nought
 * stay nought would pass against code that reset the streak on every missed bill.
 *
 * <p>A bill worth one cent more than the account holds, rather than a sweep: the point here is a
 * bill that cannot be paid, and the shortest route to one keeps the saving that built the streak out
 * of the way of the run being watched.
 */
class AMissedBillLeavesTheStreakAndTheMultiplierExactlyAsTheyWereApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";
    private static final int THE_TWENTIETH = 20;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-missed-bill-and-the-streak"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_run_of_weeks_and_what_it_pays_are_untouched_by_a_bill_that_could_not_be_paid() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // A week secured the way every week is secured: by new saving reaching the weekly minimum.
        app.deposit(savingsAccount, ANKE, app.balancesOf(savingsAccount).weeklyMinimum()
                .toPlainString());
        BalancesView secured = app.balancesOf(savingsAccount);
        assertThat(secured.currentStreakWeeks())
                .as("there has to be a run to leave untouched, or this test would watch nought "
                        + "stay nought and pass against anything")
                .isPositive();

        BigDecimal balance = app.currentAccountBalanceOf(ANKE);
        RecurringBillView rent = app.declareABillFor(ANKE, "Rent", THE_TWENTIETH,
                balance.add(new BigDecimal("0.01")).toPlainString());
        windTo(theNextTwentiethAfter(app.theDateTheClockReads()));

        BalancesView before = app.balancesOf(savingsAccount);
        app.runJob(THE_BILLS_JOB);
        assertThat(app.arrearsOf(ANKE))
                .as("the bill really did go unpaid, or there is no missed bill to claim anything "
                        + "about")
                .singleElement()
                .satisfies(owed -> assertThat(owed.billId()).isEqualTo(rent.billId()));

        assertTheSavingIsExactlyWhereItWas(before, app.balancesOf(savingsAccount));

        // And carrying the arrear is not a second punishment either: another month, another run,
        // another date owed, and the run of weeks is still nobody's business but the saver's.
        windTo(theNextTwentiethAfter(app.theDateTheClockReads()));
        BalancesView beforeTheSecondRun = app.balancesOf(savingsAccount);
        app.runJob(THE_BILLS_JOB);
        assertThat(app.arrearsOf(ANKE))
                .as("two dates owed by now, which is the arrears piling up")
                .hasSize(2);

        assertTheSavingIsExactlyWhereItWas(beforeTheSecondRun, app.balancesOf(savingsAccount));
    }

    /**
     * Every figure a week is judged by, and every figure it pays out in, compared side by side. The
     * points balance is in here as well as the streak: points come from euros moved into savings, and
     * a bills run that touched either would be the second definition this application refuses to give
     * the streak.
     */
    private static void assertTheSavingIsExactlyWhereItWas(BalancesView before, BalancesView after) {
        assertThat(after.currentStreakWeeks())
                .as("a week is secured by the weekly minimum of new saving and by nothing else")
                .isEqualTo(before.currentStreakWeeks());
        assertThat(after.bestStreakWeeks())
                .as("and the best run ever managed is a fact about weeks that already happened")
                .isEqualTo(before.bestStreakWeeks());
        assertThat(after.currentMultiplier())
                .as("so what the run pays per whole euro cannot have moved either")
                .isEqualByComparingTo(before.currentMultiplier());
        assertThat(after.newSavingsThisWeek())
                .as("nothing about this week's saving changed: no money went into savings and none "
                        + "came out")
                .isEqualByComparingTo(before.newSavingsThisWeek());
        assertThat(after.pointsBalance())
                .as("and no points were taken away, because a missed bill is not a judgement on "
                        + "the saver")
                .isEqualTo(before.pointsBalance());
    }

    /**
     * Winds the clock to that day, insisting on a move forwards: the clock only goes one way and
     * refuses a move of no days at all.
     */
    private static void windTo(LocalDate day) {
        long days = ChronoUnit.DAYS.between(app.theDateTheClockReads(), day);
        assertThat(days)
                .as("this test has to reach " + day + " and the clock cannot be wound backwards")
                .isPositive();
        app.daysPass(days);
    }

    /** The first twentieth that begins after the given day. */
    private static LocalDate theNextTwentiethAfter(LocalDate today) {
        YearMonth month = YearMonth.from(today);
        LocalDate thisMonths = month.atDay(THE_TWENTIETH);
        return thisMonths.isAfter(today) ? thisMonths : month.plusMonths(1).atDay(THE_TWENTIETH);
    }
}
