package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RuleOccurrenceView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A saving rule left standing on an account the customer has since closed moves nothing when it
 * falls due, and the night writes down why.
 *
 * <p><strong>The third way money gets into a savings account, and it goes through the same
 * door.</strong> A deposit somebody presses for is refused by Deposits in a sentence naming the day
 * the account closed; a rule is refused by the same module in the same sentence. What differs is
 * only how the refusal is met: the nightly run is one transaction over every standing rule in the
 * application, so a refusal thrown across it would take every other customer's transfers down with
 * it. The run therefore asks first — exactly as it already asks whether the current account can
 * cover a fixed amount — and records the answer.
 *
 * <p><strong>Recorded rather than merely logged, and settled rather than left due.</strong> The
 * occurrence is written down the way every other occurrence is, because "this rule fell due and
 * moved nothing" is the half of the history a deposits ledger can never hold. It is settled for the
 * reason a shortfall is: a transfer that fires on a morning the customer did not choose is a worse
 * surprise than one that did not fire — and here there is no morning it could ever fire on, because
 * an account cannot be reopened, so a day left due would be retried every night for ever. The
 * second run below, three mornings later, is what asserts that.
 *
 * <p><strong>Nothing here reads the real system date.</strong> Every day it names comes from the
 * clock the application reports, and the rule is weekly so that adding whole days is the same
 * arithmetic in February as in August.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: a test that
 * winds a clock cannot share one with tests that do not.
 */
class ASavingRuleThatFiresIntoAClosedAccountMovesNothingApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    /** Well inside what a new customer's current account holds, so the balance is never the reason. */
    private static final String AN_AMOUNT_THE_ACCOUNT_CAN_EASILY_COVER = "25.00";

    /** Mornings the job runs on with no day of this rule's falling due among them. */
    private static final int MORNINGS_THE_JOB_RUNS_AGAIN = 3;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseDaysThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-a-rule-firing-into-a-closed-account"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void it_moves_nothing_says_the_account_is_closed_and_is_never_retried() {
        String saver = app.aCustomerOfItsOwn("a rule on an account since closed");
        long savingsAccount = app.savingsAccountOf(saver);
        BigDecimal held = app.currentAccountBalanceOf(saver);
        LocalDate theDayItFallsDue = app.theDateTheClockReads().plusDays(2);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                aFixedAmountEveryWeekOn(app.currentAccountOf(saver), theDayItFallsDue));
        // Closed after the rule was left standing, which is the only order this can happen in: an
        // account is emptied and closed long after somebody automated saving into it.
        AnAgreementView closed = app.closeTheSavingsAccount(savingsAccount);

        app.daysPass(2);
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> afterItFellDue = app.historyOf(savingsAccount, rule.id());
        assertThat(afterItFellDue)
                .as("the day fell due and was dealt with, and a day that fell due is written down "
                        + "whether money moved or not")
                .hasSize(1);
        RuleOccurrenceView theOneThatCouldNotHappen = afterItFellDue.get(0);
        assertThat(theOneThatCouldNotHappen.dueOn()).isEqualTo(theDayItFallsDue);
        assertThat(theOneThatCouldNotHappen.outcome())
                .as("a rule pointing at a closed account is a refusal of its own, and not the "
                        + "NOT_ENOUGH_MONEY that would send its holder off to top up an account "
                        + "that was never the problem")
                .isEqualTo("THE_ACCOUNT_IS_CLOSED");
        assertThat(theOneThatCouldNotHappen.amount())
                .as("nothing moved, because there was nowhere for it to move to")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(theOneThatCouldNotHappen.shortfall())
                .as("and no shortfall, because the current account was never short of anything")
                .isNull();
        assertThat(theOneThatCouldNotHappen.depositId())
                .as("nothing moved, so there is no deposit to name")
                .isNull();

        assertThat(app.currentAccountBalanceOf(saver))
                .as("not a cent left the current account")
                .isEqualByComparingTo(held);
        assertThat(app.moneyBalanceOf(savingsAccount))
                .as("and the closed account still holds nothing")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(app.theAgreementOf(savingsAccount).closedOn())
                .as("the agreement is exactly as the closing left it")
                .isEqualTo(closed.closedOn());

        // Mornings passing with the job running on each of them. A day left due rather than settled
        // would be fired again on one of these, and would go on being fired for ever, because an
        // account that has been closed can never be opened again.
        for (int morning = 0; morning < MORNINGS_THE_JOB_RUNS_AGAIN; morning++) {
            app.daysPass(1);
            app.runJob(THE_JOB);
        }

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("one row for the day that fell due and no second row for it: the occurrence is "
                        + "settled, not owed")
                .hasSize(1);
        assertThat(app.currentAccountBalanceOf(saver))
                .as("and three mornings of the job running move nothing")
                .isEqualByComparingTo(held);
    }

    /**
     * A fixed amount every week on the day this test is about, as somebody would type it.
     *
     * <p>Written here rather than borrowed from the Automation tests' own fixture, which is
     * package-private to them. Four fields and a name is a small thing to restate, and restating it
     * keeps this file readable as the one story it tells.
     */
    private static Map<String, Object> aFixedAmountEveryWeekOn(long fromCurrentAccountId,
                                                               LocalDate day) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("name", "Into the account I closed");
        rule.put("fromCurrentAccountId", fromCurrentAccountId);
        rule.put("trigger", "WEEKLY");
        rule.put("dayOfWeek", day.getDayOfWeek().name());
        rule.put("howMuchMoves", "A_FIXED_AMOUNT");
        rule.put("amount", AN_AMOUNT_THE_ACCOUNT_CAN_EASILY_COVER);
        return rule;
    }
}
