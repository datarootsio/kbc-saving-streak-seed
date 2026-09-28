package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
 * A fixed-amount rule the current account cannot cover moves nothing at all, records how far short
 * the account was, is never retried — and the next day it falls due on fires as if nothing had
 * happened.
 *
 * <p>User stories 14, 26 and 27, which are one behaviour read three ways. A standing order is all or
 * nothing, so a rule asking for more than is there does not move what it can; the occurrence is
 * settled with the shortfall on it; and it is settled <em>for good</em>, because a transfer firing on
 * a day the customer did not choose is a worse surprise than one that did not fire.
 *
 * <p><strong>The three things this asserts are the three ways a shortfall is got wrong.</strong> An
 * implementation that moved what it could would leave the balance down and the occurrence looking
 * like a success; one that recorded the failure without the figure would leave the customer with
 * "something did not happen" and nothing to act on; and one that left the day unsettled would move
 * the money on some later morning, which is precisely the thing this feature promises never to do.
 * The middle run — days passing with the job running and no occurrence due — is what catches the
 * third: a rule that retried would fire it there.
 *
 * <p><strong>Nothing here reads the real system date.</strong> Every day it names is derived from the
 * clock the application itself reports, and the only arithmetic done to it is adding whole days,
 * which is the same on the 29th, the 30th and the 31st as on any other. The rule is weekly rather
 * than monthly for exactly that reason: a monthly rule left standing on the 31st would be a test
 * whose truth depended on which month the suite happened to run in.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class AFixedAmountTheAccountCannotCoverMovesNothingAndSaysWhatItWasShortApiTest
        extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    /**
     * How much more than the account holds the rule asks for, which is therefore exactly what the
     * account will be short. A round figure, so that the assertion on the shortfall is a figure this
     * test states rather than one it recomputes the way the application does.
     */
    private static final BigDecimal MORE_THAN_IT_HOLDS = new BigDecimal("100.00");

    /** What the rule is changed to before its second week, and well under what the account holds. */
    private static final String AN_AMOUNT_IT_CAN_COVER = "25.00";

    /** Between the failure and the next day the rule falls due, with the job running in between. */
    private static final int DAYS_UNTIL_THE_JOB_IS_RUN_AGAIN = 3;

    private static final int DAYS_IN_A_WEEK = 7;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-a-fixed-amount-the-account-cannot-cover"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void it_moves_nothing_records_the_shortfall_is_never_retried_and_the_next_week_fires() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        BigDecimal held = app.currentAccountBalanceOf(ANKE);
        BigDecimal saved = app.balancesOf(savingsAccount).moneyBalance();
        BigDecimal moreThanItHolds = held.add(MORE_THAN_IT_HOLDS);

        LocalDate theDayItFallsDue = app.theDateTheClockReads().plusDays(2);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "A hundred euros more than there is", theDayItFallsDue.getDayOfWeek().name(),
                        moreThanItHolds.toPlainString()));

        app.daysPass(2);
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> afterItFellDue = app.historyOf(savingsAccount, rule.id());
        assertThat(afterItFellDue)
                .as("the day fell due, and a day that fell due is recorded whether money moved or "
                        + "not — that is the half a deposits ledger can never hold")
                .hasSize(1);
        RuleOccurrenceView theOneThatFailed = afterItFellDue.get(0);
        assertThat(theOneThatFailed.dueOn()).isEqualTo(theDayItFallsDue);
        assertThat(theOneThatFailed.outcome())
                .as("a fixed amount the account cannot cover is a failure the customer is owed a "
                        + "word about, and not the arithmetic a sweep reports as NOTHING_TO_MOVE")
                .isEqualTo("NOT_ENOUGH_MONEY");
        assertThat(theOneThatFailed.amount())
                .as("a standing order moves all of itself or none of it, so nothing moved")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(theOneThatFailed.shortfall())
                .as("and the record says how much more the account would have needed, which is the "
                        + "one figure the customer can do anything with")
                .isEqualByComparingTo(MORE_THAN_IT_HOLDS);
        assertThat(theOneThatFailed.depositId())
                .as("nothing moved, so there is no deposit to name")
                .isNull();

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("the current account is exactly as it was: not a cent of a partial transfer")
                .isEqualByComparingTo(held);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .as("and nothing arrived in the savings account either")
                .isEqualByComparingTo(saved);

        // Days passing with the job running on each of them, and no day of this rule's falling due
        // among them. A rule that retried what it could not honour would fire it here.
        for (int day = 0; day < DAYS_UNTIL_THE_JOB_IS_RUN_AGAIN; day++) {
            app.daysPass(1);
            app.runJob(THE_JOB);
        }

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("the occurrence is settled rather than still owed: no later morning fires it, "
                        + "because money leaving on a day the customer did not choose is a worse "
                        + "surprise than money that did not leave")
                .hasSize(1);
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and three mornings of the job running move nothing out of the account")
                .isEqualByComparingTo(held);

        // The rule is brought within reach and the week is completed, so that the next day it falls
        // due on is the next day it falls due on and nothing else has changed.
        app.changeRule(savingsAccount, rule.id(), Map.of("amount", AN_AMOUNT_IT_CAN_COVER));
        app.daysPass(DAYS_IN_A_WEEK - DAYS_UNTIL_THE_JOB_IS_RUN_AGAIN);
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> afterTheNextWeek = app.historyOf(savingsAccount, rule.id());
        assertThat(afterTheNextWeek)
                .as("one row for the week that could not be honoured and one for the week that "
                        + "could, and no second row for the first of them")
                .hasSize(2);
        RuleOccurrenceView theNextWeek = afterTheNextWeek.get(0);
        assertThat(theNextWeek.dueOn())
                .as("newest first, so the week that has just fallen is at the front")
                .isEqualTo(theDayItFallsDue.plusWeeks(1));
        assertThat(theNextWeek.outcome())
                .as("a rule that could not be honoured once is not a rule that has stopped: the "
                        + "next day it falls due on fires as if nothing had happened")
                .isEqualTo("MOVED");
        assertThat(theNextWeek.amount()).isEqualByComparingTo(new BigDecimal(AN_AMOUNT_IT_CAN_COVER));
        assertThat(theNextWeek.shortfall())
                .as("nothing was short of anything, so there is no shortfall to report — null "
                        + "rather than nought, because the two say different things")
                .isNull();
        assertThat(theNextWeek.depositId()).isNotNull();

        RuleOccurrenceView theFailureAsItStillReads = afterTheNextWeek.get(1);
        assertThat(theFailureAsItStillReads.id())
                .as("the settled occurrence is the same row it always was, not one rewritten by a "
                        + "later run")
                .isEqualTo(theOneThatFailed.id());
        assertThat(theFailureAsItStillReads.outcome()).isEqualTo("NOT_ENOUGH_MONEY");
        assertThat(theFailureAsItStillReads.shortfall())
                .as("and it still says what it was short, although the rule's own amount has since "
                        + "been changed — what happened on a day is what happened on that day")
                .isEqualByComparingTo(MORE_THAN_IT_HOLDS);

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("only the week that could be honoured took anything out of the account")
                .isEqualByComparingTo(held.subtract(new BigDecimal(AN_AMOUNT_IT_CAN_COVER)));
    }
}
