package io.dataroots.savingstreak.seededhouseholds;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.seededhouseholds.TheMonthTheseTestsWatch.nightsInTheMonthFrom;
import static io.dataroots.savingstreak.seededhouseholds.TheMonthTheseTestsWatch.startsOnTheTwentySixth;
import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A month passes over the seeded households with nothing else standing on them: both salaries are
 * credited, every declared bill is taken on its day, and neither household owes a cent at the end
 * of it.
 *
 * <p>User story 40 — somebody trying the demo moves the clock forward a month and watches a whole
 * cycle run — and the half of 38 that a list of declarations cannot prove on its own. A seeded
 * household that looked right but could not survive its own first month would be worse than no seed
 * at all: the first thing a trainer would demonstrate is an application that cannot pay the rent it
 * wrote itself.
 *
 * <p>It is also the control the two tests beside it are read against. Arrears here would mean the
 * seed is too tight, and a household that fails whatever its holder does teaches that saving is
 * impossible rather than that it is a judgement. The balances are asserted as the exact arithmetic
 * of one salary against one month of bills, so that a bill quietly taken twice, or one never
 * presented at all, shows up as a number rather than as an absence.
 *
 * <p>Night by night rather than in one wind, because the two are different months — see
 * {@code AnApplicationWithAClockToMove.aNightPasses}. Nothing here would notice the difference,
 * having no saving rule to come second to, but a control that ran the clock differently from the
 * tests it is a control for would not be one.
 */
class AMonthOverTheSeededHouseholdsPaysBothSalariesAndTakesEveryBillApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseMonthThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-month-of-the-households"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void both_households_are_paid_pay_everything_they_owe_and_end_the_month_owing_nothing() {
        LocalDate startOfTheMonth = startsOnTheTwentySixth(app, ANKE, BRAM);
        BigDecimal ankeHeld = app.currentAccountBalanceOf(ANKE);
        BigDecimal bramHeld = app.currentAccountBalanceOf(BRAM);

        app.nightsPass(nightsInTheMonthFrom(startOfTheMonth));
        assertThat(app.theDateTheClockReads())
                .as("a whole calendar month, so every day the seed names fell in it exactly once")
                .isEqualTo(startOfTheMonth.plusMonths(1));

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("one salary of 2600.00 in and four bills totalling 1165.00 out, which is the "
                        + "1435.00 of room this household was written to have")
                .isEqualByComparingTo(ankeHeld.add(new BigDecimal("1435.00")));
        assertThat(app.currentAccountBalanceOf(BRAM))
                .as("one salary of 1750.00 in and three bills totalling 945.00 out: 805.00 of room, "
                        + "real but narrow")
                .isEqualByComparingTo(bramHeld.add(new BigDecimal("805.00")));

        assertThat(app.arrearsOf(ANKE))
                .as("nothing is owed by a household nobody has over-committed")
                .isEmpty();
        assertThat(app.arrearsOf(BRAM))
                .as("and nothing by the tight one either — his failure has to be something he does, "
                        + "not something the seed does to him")
                .isEmpty();

        everyBillWasTakenInTheMonthEnding(startOfTheMonth.plusMonths(1), ANKE);
        everyBillWasTakenInTheMonthEnding(startOfTheMonth.plusMonths(1), BRAM);
    }

    /**
     * Every standing bill of that household left the account on its own day inside the month just
     * watched.
     *
     * <p>Asserted as well as the balance, because a balance is a sum and sums hide things: a rent
     * taken twice and an insurance never presented would land on a figure only 890.00 away from the
     * right one, and nothing in a total says which bill moved.
     */
    private static void everyBillWasTakenInTheMonthEnding(LocalDate endOfTheMonth, String household) {
        for (RecurringBillView bill : app.billsOf(household)) {
            assertThat(bill.lastTakenOn())
                    .as(household + "'s " + bill.name() + " left the account on the "
                            + bill.dayOfMonth() + " of the month just watched")
                    .isNotNull()
                    .isAfter(endOfTheMonth.minusMonths(1))
                    .isBeforeOrEqualTo(endOfTheMonth);
            assertThat(bill.lastTakenOn().getDayOfMonth())
                    .as(household + "'s " + bill.name() + " left on the day it was declared for")
                    .isEqualTo(bill.dayOfMonth());
        }
    }
}
