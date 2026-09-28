package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AllocationsView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole loop, over HTTP, in one test: save too hard, miss the rent, find that the goal will not
 * give the money back, give up the goal, withdraw, and watch the arrear clear.
 *
 * <p>User stories 17, 35 and 36, and the reason this is one test rather than five. The loop
 * <em>is</em> the user story — every step of it exists only because of the step before — and nothing
 * else in this feature covers it end to end. A suite that asserted each step against a database it
 * had set up by hand would never find out that the way out of an arrear is built entirely of
 * machinery this application already had.
 *
 * <p><strong>The way out needs no new code, and this test is the claim that it does not.</strong>
 * Withdrawal from savings already exists and money a goal has spoken for already refuses to move.
 * Put together, a customer who committed their savings to goals has genuinely tied their own hands,
 * and the knot is untied by a decision they make rather than by anything the application does for
 * them.
 *
 * <p><strong>The refusal is asserted in the words the customer would read.</strong> Every refusal in
 * this application answers in one shape carrying its reason, and a refusal nobody can read is a
 * refusal nobody can act on — so this asserts the sentence names what is actually free, which is the
 * figure the customer needs in order to decide what to give up.
 *
 * <p>One test, because the clock only goes forward and the loop is a sequence. The claim is asserted
 * after every step instead.
 */
class OverSavingMissingTheRentAndClimbingBackOutWorksEndToEndApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";
    private static final String THE_SAVING_RULES_JOB = "fireSavingRulesDue";
    private static final String THE_RENT = "900.00";
    private static final int THE_TWELFTH = 12;

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-climbing-out-of-arrears"));
        theCustomer = app.aCustomerOfItsOwn("climbing out of arrears");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_customer_who_saved_too_hard_gives_up_a_goal_to_pay_the_rent_they_missed() {
        long savingsAccount = app.savingsAccountOf(theCustomer);
        RecurringBillView rent = app.declareABillFor(theCustomer, "Rent", THE_TWELFTH, THE_RENT);
        app.leaveARuleStanding(savingsAccount,
                aWeeklySweepDownToNothingFrom(app.currentAccountOf(theCustomer)));

        // Saving too hard: the sweep empties the current account, and the rent half an hour later
        // meets nothing at all. That is the whole feature, and it is where the loop starts.
        LocalDate theRentsDay = theNextTwelfthAfter(app.theDateTheClockReads());
        windTo(theRentsDay);
        app.runJob(THE_SAVING_RULES_JOB);
        app.runJob(THE_BILLS_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(app.arrearsOf(theCustomer))
                .as("the rent stays owed rather than being waived")
                .singleElement()
                .satisfies(owed -> {
                    assertThat(owed.billName()).isEqualTo("Rent");
                    assertThat(owed.dueOn()).isEqualTo(theRentsDay);
                    assertThat(owed.amount()).isEqualByComparingTo(new BigDecimal(THE_RENT));
                });

        // And the money that could pay it has been spoken for. A goal is a real commitment rather
        // than a label, which is exactly what makes this hurt.
        AllocationsView held = app.allocationsOn(savingsAccount);
        GoalView kitchen = app.openAGoal(savingsAccount, "New kitchen",
                held.balance().toPlainString());
        app.allocate(savingsAccount, kitchen.id(), held.unallocated().toPlainString());
        assertThat(app.allocationsOn(savingsAccount).unallocated())
                .as("nothing at all is free any more, which is the customer's own doing")
                .isEqualByComparingTo(BigDecimal.ZERO);

        ResponseEntity<JsonNode> refused = app.tryToWithdraw(savingsAccount, theCustomer, THE_RENT);
        assertThat(refused.getStatusCode())
                .as("money a goal has spoken for refuses to come back, arrears or no arrears — an "
                        + "application that let an unpaid bill override a goal would make a goal a "
                        + "label rather than a commitment")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("and it says so in words the customer can act on, naming what is actually free")
                .contains("not claimed by a goal")
                .contains("0.00");
        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("nothing moved on the refusal")
                .isEqualByComparingTo(BigDecimal.ZERO);

        app.runJob(THE_BILLS_JOB);
        assertThat(app.arrearsOf(theCustomer))
                .as("and a run against an account nobody has put anything into settles nothing: "
                        + "the arrear is still there and is still exactly the rent")
                .singleElement()
                .satisfies(owed -> assertThat(owed.amount())
                        .isEqualByComparingTo(new BigDecimal(THE_RENT)));

        // The way out, and it is a decision rather than a mechanism: give up the goal, take the
        // money back, and let the night settle what is owed.
        app.abandonGoal(savingsAccount, kitchen.id());
        app.withdraw(savingsAccount, theCustomer, THE_RENT);
        assertThat(app.currentAccountBalanceOf(theCustomer))
                .isEqualByComparingTo(new BigDecimal(THE_RENT));

        app.runJob(THE_BILLS_JOB);

        assertThat(app.arrearsOf(theCustomer))
                .as("the hole is closed")
                .isEmpty();
        assertThat(app.arrearsTheAccountItselfReports(theCustomer))
                .as("and the page's own read says so too, which is what makes the still-owed "
                        + "section absent rather than empty")
                .isEmpty();
        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("the rent left the account, to the cent and no more")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(app.historyOfBill(theCustomer, rent.billId()))
                .as("one row for that due date, now paid, still owed from the day it was owed from")
                .singleElement()
                .satisfies(presented -> {
                    assertThat(presented.outcome()).isEqualTo("PAID");
                    assertThat(presented.dueOn()).isEqualTo(theRentsDay);
                    assertThat(presented.amount())
                            .isEqualByComparingTo(new BigDecimal(THE_RENT));
                    assertThat(presented.daysLate())
                            .as("and the record says how late it finally was, because the history "
                                    + "does not rewrite when something was owed")
                            .isEqualTo(ChronoUnit.DAYS.between(theRentsDay,
                                    app.theDateTheClockReads()));
                });
    }

    /**
     * A weekly sweep taking everything above nothing, as a customer's form would send it — the
     * sharpest version of "I saved too hard": everything in the account goes, and the rent afterwards
     * meets nothing at all.
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
