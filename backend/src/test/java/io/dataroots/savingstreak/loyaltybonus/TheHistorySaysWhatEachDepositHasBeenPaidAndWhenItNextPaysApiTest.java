package io.dataroots.savingstreak.loyaltybonus;

import java.time.LocalDate;
import java.util.Arrays;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a deposit says about its loyalty: the bonus it has been paid across every anniversary it has
 * survived, the day it next pays, and what that day is worth at what it holds today.
 *
 * <p>The next-anniversary figure is the point of the whole scheme made visible. Everything else this
 * application reports is a record of the past; this is a promise about the future, and it is the only
 * thing that tells a customer what leaving the money alone is going to pay them — and, because it is
 * worked out from the euros still in the deposit, what taking it out would cost. So the test drives
 * a deposit through both: it is paid twice for staying, and the figure falls the moment half of it
 * leaves.
 *
 * <p>Three answers rather than three tests, on one clock, because the clock only ever goes forward:
 * two accounts of one customer carry a deposit that keeps paying, a deposit that is emptied, and a
 * deposit too small to be worth a point, and the whole of the contract is read off the histories at
 * the end.
 *
 * <p>The clock is read either side of the sweep, because "when it next pays" is a promise about a
 * payment rather than about a calendar. An anniversary counts as arrived the moment it falls and the
 * sweep runs overnight, so a deposit spends hours owed a bonus nobody has paid it; through that
 * window the anniversary reported as coming is the one outstanding, and only once it is paid does
 * the promise move on to the year after.
 *
 * <p>What the deposit earned <em>when it landed</em> is asserted throughout as well, unchanged: the
 * base points and the streak bonus of a deposit made two years ago are the same figures they were on
 * the day, and only the total moves. That is the invariant this ticket is easiest to break.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives and then some:
 * this test winds the clock two years forward, which nothing sharing a database could survive.
 */
class TheHistorySaysWhatEachDepositHasBeenPaidAndWhenItNextPaysApiTest extends ApiIntegrationTest {

    /** The job by the name a trainer types into the development jobs endpoint. */
    private static final String THE_SWEEP = "payLoyaltyBonuses";

    /**
     * A fortnight past a year. Comfortably the far side of a first anniversary whatever day of
     * whatever month the run happens on, and comfortably short of a second.
     */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    /** A further year, which takes the fortnight-past-a-year clock past a second anniversary. */
    private static final int DAYS_IN_A_FURTHER_YEAR = 365;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-history-says-when-it-next-pays"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_deposit_reports_what_loyalty_has_paid_it_and_what_its_next_anniversary_is_worth() {
        long leftAlone = app.savingsAccountOf(ANKE);
        long emptied = app.otherSavingsAccountOf(ANKE);

        // A deposit just made has earned no loyalty and has a first anniversary twelve months out:
        // the date it has this moment started counting towards, and what that date is worth if the
        // money is still there — a tenth of its 500 euros.
        DepositView paidIn = app.deposit(leftAlone, ANKE, "500.00");
        assertThat(paidIn.loyaltyBonusPoints()).as("nothing yet; it has not had an anniversary").isZero();
        assertThat(paidIn.nextAnniversaryOn())
                .as("the first anniversary of the day the money landed")
                .isEqualTo(anniversaryOf(paidIn, 1));
        assertThat(paidIn.nextAnniversaryPoints())
                .as("a tenth of the 500 euros it holds, which is what staying is worth")
                .isEqualTo(50);
        assertThat(theEntryFor(leftAlone, paidIn)).satisfies(entry -> {
            assertThat(entry.loyaltyBonusPoints()).isZero();
            assertThat(entry.nextAnniversaryOn())
                    .as("looked back at, a deposit says the same as it said when it was made")
                    .isEqualTo(paidIn.nextAnniversaryOn());
            assertThat(entry.nextAnniversaryPoints()).isEqualTo(paidIn.nextAnniversaryPoints());
        });

        DepositView toBeEmptied = app.deposit(emptied, ANKE, "500.00");
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(1000);

        // A year and a fortnight on, and before the sweep has run. The first anniversary has arrived
        // and nobody has paid it, so it is the anniversary the deposit reports as coming: the
        // customer is owed it, and a page that had skipped to the second would have shown them
        // nothing earned beside a date twelve months out and then paid them a year late.
        app.daysPass(DAYS_WELL_PAST_A_YEAR);

        assertThat(theEntryFor(leftAlone, paidIn)).satisfies(entry -> {
            assertThat(entry.loyaltyBonusPoints())
                    .as("the sweep has not run, so nothing has been paid yet")
                    .isZero();
            assertThat(entry.nextAnniversaryOn())
                    .as("the anniversary that pays next is the one already owed, not next year's")
                    .isEqualTo(anniversaryOf(paidIn, 1));
            assertThat(entry.nextAnniversaryPoints())
                    .as("worth the tenth it is about to pay")
                    .isEqualTo(50);
        });

        // And now the sweep pays both deposits their first anniversary.
        app.runJob(THE_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(1000 + 50 + 50);

        assertThat(theEntryFor(leftAlone, paidIn)).satisfies(entry -> {
            assertThat(entry.loyaltyBonusPoints()).as("the anniversary it has just been paid")
                    .isEqualTo(50);
            assertThat(entry.pointsEarned())
                    .as("what this deposit has been worth altogether, which has grown")
                    .isEqualTo(550);
            assertThat(entry.basePoints()).as("what it earned when it landed, unchanged").isEqualTo(500);
            assertThat(entry.streakBonusPoints()).as("and unchanged").isZero();
            assertThat(entry.nextAnniversaryOn())
                    .as("the clock recurs, so the promise moves on to the second anniversary")
                    .isEqualTo(anniversaryOf(paidIn, 2));
            assertThat(entry.nextAnniversaryPoints())
                    .as("worth the same again, because all 500 euros are still there")
                    .isEqualTo(50);
        });

        // A second year, and a second anniversary paid on the same deposit. What it has been paid in
        // loyalty is every anniversary added up rather than the last of them.
        app.daysPass(DAYS_IN_A_FURTHER_YEAR);
        app.runJob(THE_SWEEP);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(1000 + 100 + 100);

        DepositView afterTwoAnniversaries = theEntryFor(leftAlone, paidIn);
        assertThat(afterTwoAnniversaries.loyaltyBonusPoints())
                .as("both anniversaries, added up, on the deposit that earned them")
                .isEqualTo(100);
        assertThat(afterTwoAnniversaries.pointsEarned()).isEqualTo(600);
        assertThat(afterTwoAnniversaries.nextAnniversaryOn()).isEqualTo(anniversaryOf(paidIn, 3));
        assertThat(afterTwoAnniversaries.nextAnniversaryPoints()).isEqualTo(50);

        // Half the money leaves. The next anniversary is worth half of what it was a moment ago,
        // which is the cost of the withdrawal, said in the only place the application says it.
        app.withdraw(leftAlone, ANKE, "250.00");

        assertThat(theEntryFor(leftAlone, paidIn)).satisfies(entry -> {
            assertThat(entry.nextAnniversaryPoints())
                    .as("a tenth of the 250 euros left in it, down from the 50 it promised before "
                            + "the withdrawal")
                    .isEqualTo(25);
            assertThat(entry.nextAnniversaryOn())
                    .as("the same day, though: a withdrawal costs the bonus and does not reset the clock")
                    .isEqualTo(afterTwoAnniversaries.nextAnniversaryOn());
            assertThat(entry.loyaltyBonusPoints())
                    .as("the anniversaries already paid are the customer's for good")
                    .isEqualTo(100);
            assertThat(entry.pointsEarned()).isEqualTo(600);
        });

        // And a deposit emptied has no promise left to make at all, while keeping every point the
        // anniversaries it did survive paid it.
        app.withdraw(emptied, ANKE, "500.00");

        assertThat(theEntryFor(emptied, toBeEmptied)).satisfies(entry -> {
            assertThat(entry.nextAnniversaryOn())
                    .as("no next anniversary: the money has gone, so there is nothing to promise")
                    .isNull();
            assertThat(entry.nextAnniversaryPoints())
                    .as("and nothing it could be worth, which is not the same as being worth nothing")
                    .isNull();
            assertThat(entry.loyaltyBonusPoints())
                    .as("the two anniversaries it did survive are not clawed back")
                    .isEqualTo(100);
            assertThat(entry.pointsEarned()).isEqualTo(600);
        });

        // A deposit of nine euros is worth no bonus, and says so with a date rather than by leaving
        // the promise out: rounding down is a rule the customer can see.
        DepositView underTenEuros = app.deposit(emptied, ANKE, "9.00");
        assertThat(underTenEuros.nextAnniversaryOn())
                .as("its own first anniversary, twelve months from today")
                .isEqualTo(anniversaryOf(underTenEuros, 1));
        assertThat(underTenEuros.nextAnniversaryPoints())
                .as("a tenth of nine euros rounds down to nothing, and nothing is said out loud")
                .isZero();
        assertThat(theEntryFor(emptied, underTenEuros)).satisfies(entry -> {
            assertThat(entry.nextAnniversaryOn()).isEqualTo(underTenEuros.nextAnniversaryOn());
            assertThat(entry.nextAnniversaryPoints()).isZero();
            assertThat(entry.loyaltyBonusPoints()).isZero();
        });

        // Whatever any of them has been through, the three parts of an entry add up to its total.
        for (DepositView entry : app.depositsInto(leftAlone)) {
            thePartsAddUp(entry);
        }
        for (DepositView entry : app.depositsInto(emptied)) {
            thePartsAddUp(entry);
        }

        // And none of it is on the overview. The bonus is shown on the deposits that earned it,
        // where the next anniversary means something; the account's summary gains nothing.
        assertThat(app.theAccountOverviewAsItIsSent(leftAlone).toLowerCase())
                .as("no loyalty figure and no anniversary on the account's own summary")
                .doesNotContain("loyalty")
                .doesNotContain("anniversary");
    }

    /**
     * The three parts of an entry against its total, which is the invariant this feature widened: a
     * deposit's total is its euros, the uplift its run of weeks paid, and every anniversary since.
     */
    private static void thePartsAddUp(DepositView entry) {
        assertThat(entry.basePoints() + entry.streakBonusPoints() + entry.loyaltyBonusPoints())
                .as("the three parts of deposit " + entry.id() + " add up to its total")
                .isEqualTo(entry.pointsEarned());
    }

    /**
     * The day this deposit's <em>n</em>-th anniversary falls on, worked out from the moment the API
     * said the money landed rather than from the day the test happens to run: a test that read the
     * clock instead would be wrong for anything made either side of midnight.
     *
     * <p>Twelve months multiplied out and added once, which is the rule stated in the test's own
     * terms — the calendar's answer, clamping and all, rather than a count of days.
     */
    private static LocalDate anniversaryOf(DepositView deposit, int ordinal) {
        return deposit.depositedAt()
                .atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                .toLocalDate()
                .plusMonths(12L * ordinal);
    }

    /** The entry for one deposit, insisted on: a deposit missing from its own history is the failure. */
    private static DepositView theEntryFor(long savingsAccountId, DepositView deposit) {
        return Arrays.stream(app.depositsInto(savingsAccountId))
                .filter(entry -> deposit.id().equals(entry.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "deposit " + deposit.id() + " is not in the history of savings account "
                                + savingsAccountId));
    }
}
