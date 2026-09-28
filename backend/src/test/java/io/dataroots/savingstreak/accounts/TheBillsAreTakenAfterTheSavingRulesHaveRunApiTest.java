package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RecurringBillView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bills are presented against the balance the saving rules left behind: a sweep that empties the
 * current account leaves that night's bill unpaid.
 *
 * <p>User story 11, and the reason the whole feature exists. The night runs the saving rules at two
 * and the bills at half past, and the half hour between them is load-bearing rather than decorative:
 * put the bills first and a sweep would only ever move the surplus that survived the rent, no bill
 * could ever fail, nothing would ever be owed, and the application would be back to teaching that
 * saving is free. The lesson is that a customer who swept their balance into savings on payday finds
 * the rent cannot be paid, and it is made entirely of these two jobs running in this order.
 *
 * <p><strong>Asserted directly rather than inferred from the cron expressions.</strong> That the two
 * schedules read 02:00 and 02:30 is asserted where the jobs are listed; what is asserted here is the
 * consequence, by running the night in the order the night runs it and watching the money. A test
 * that only compared two strings would keep passing against a bills run that read a balance from
 * before the sweep.
 *
 * <p>A sweep with a floor of nothing, which is legal and is the sharpest version of the case: it
 * takes the account to zero, so the bill afterwards meets an account with nothing at all in it and
 * the refusal cannot be a rounding argument.
 */
class TheBillsAreTakenAfterTheSavingRulesHaveRunApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";
    private static final String THE_SAVING_RULES_JOB = "fireSavingRulesDue";
    private static final String THE_RENT = "900.00";
    private static final int THE_TWELFTH = 12;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseNightThisTestRuns() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-bills-after-the-rules"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_sweep_that_empties_the_account_leaves_that_nights_bill_unpaid() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        RecurringBillView rent = app.declareABillFor(ANKE, "Rent", THE_TWELFTH, THE_RENT);
        SavingRuleView sweep = app.leaveARuleStanding(savingsAccount,
                aWeeklySweepDownToNothingFrom(app.currentAccountOf(ANKE)));
        assertThat(sweep.howMuchMoves())
                .as("a rule that was not the sweep this test needs would leave every assertion "
                        + "below about a night that never emptied anything")
                .isEqualTo("EVERYTHING_ABOVE");

        BigDecimal before = app.currentAccountBalanceOf(ANKE);
        assertThat(before)
                .as("there has to be enough to pay the rent before the sweep runs, or the bill "
                        + "would have failed for a reason that has nothing to do with the order")
                .isGreaterThan(new BigDecimal(THE_RENT));

        LocalDate theDayTheRentFallsDue = theNextTwelfthAfter(app.theDateTheClockReads());
        windTo(theDayTheRentFallsDue);

        // The night, in the order the night runs it: the rules at two, the bills at half past.
        app.runJob(THE_SAVING_RULES_JOB);
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("the sweep swept the account down to its floor of nothing, which is what the "
                        + "customer asked for")
                .isEqualByComparingTo(BigDecimal.ZERO);

        app.runJob(THE_BILLS_JOB);

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and the rent met an empty account, took nothing at all, and did not push the "
                        + "balance below zero")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(app.historyOfBill(ANKE, rent.billId()))
                .as("the rent fell due that morning and could not be paid, which is the whole "
                        + "point of presenting the bills after the rules rather than before them")
                .anySatisfy(presented -> {
                    assertThat(presented.dueOn()).isEqualTo(theDayTheRentFallsDue);
                    assertThat(presented.outcome()).isEqualTo("UNPAID");
                    assertThat(presented.amount()).isEqualByComparingTo(new BigDecimal(THE_RENT));
                });
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .as("the money is not lost — it is in savings, which is exactly the trade-off this "
                        + "application exists to make visible")
                .isGreaterThanOrEqualTo(before);
    }

    /**
     * A weekly sweep taking everything above nothing, as a customer's form would send it.
     *
     * <p>Weekly rather than monthly so that one falls inside any stretch this test winds through,
     * and the day of the week is today's — the rule's cursor starts where it is written, so the
     * first one it fires on is the next of that weekday, which is inside the month this test walks.
     *
     * <p>A floor of nothing is legal and says the sharpest version of "I saved too hard": everything
     * in the account goes, and the bill afterwards meets nothing at all.
     */
    private static Map<String, Object> aWeeklySweepDownToNothingFrom(long fromCurrentAccountId) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("name", "Everything, every week");
        rule.put("fromCurrentAccountId", fromCurrentAccountId);
        rule.put("trigger", "WEEKLY");
        rule.put("dayOfWeek", app.theDateTheClockReads().getDayOfWeek().name());
        rule.put("howMuchMoves", "EVERYTHING_ABOVE");
        rule.put("floor", "0.00");
        return rule;
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

    /** The first twelfth that begins after the given day. */
    private static LocalDate theNextTwelfthAfter(LocalDate today) {
        YearMonth month = YearMonth.from(today);
        LocalDate thisMonths = month.atDay(THE_TWELFTH);
        return thisMonths.isAfter(today) ? thisMonths : month.plusMonths(1).atDay(THE_TWELFTH);
    }
}
