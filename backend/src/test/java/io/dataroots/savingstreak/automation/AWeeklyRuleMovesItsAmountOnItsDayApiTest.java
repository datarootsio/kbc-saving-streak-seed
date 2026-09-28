package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
 * A weekly rule moves its amount on its day, out of the current account and into the savings
 * account, and the occurrence it wrote says so.
 *
 * <p>User stories 1 and 11, and the tracer bullet the whole feature is strung along: everything
 * after this is a different trigger, a different amount or a different way of failing.
 *
 * <p><strong>Both directions in one test, because only the pair is the claim.</strong> That a rule
 * fires on its day is worth nothing unless it also stays quiet on the days that are not its day —
 * a job that moved the money on every run would satisfy half of this and be a standing order that
 * fires whenever somebody presses a button.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test
 * winds the clock, and the file the rest of the run shares is not one to leave wound forward.
 */
class AWeeklyRuleMovesItsAmountOnItsDayApiTest extends ApiIntegrationTest {

    /** The name a trainer types into the development jobs endpoint. */
    private static final String THE_JOB = "fireSavingRulesDue";

    /** At the weekly minimum, which is what a rule left standing to hold a streak together says. */
    private static final String FIFTY_EUROS = "50.00";

    /**
     * How far off the rule's day is set, so that the run before it is a run on a day that is
     * plainly not the rule's. Three rather than one, so the "not yet" half of this test is not
     * standing on a boundary.
     */
    private static final int DAYS_UNTIL_IT_MOVES = 3;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeekThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-weekly-rule-moves"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_rule_stays_quiet_until_its_day_and_then_moves_its_amount() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayItMovesOn = app.theDateTheClockReads().plusDays(DAYS_UNTIL_IT_MOVES);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "Fifty a week", theDayItMovesOn.getDayOfWeek().name(), FIFTY_EUROS));
        BigDecimal inTheCurrentAccount = app.currentAccountBalanceOf(ANKE);
        BigDecimal inTheSavingsAccount = app.balancesOf(savingsAccount).moneyBalance();

        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("the rule's day has not come, and a rule that moved money on a day its holder "
                        + "did not choose is a standing order nobody could plan around")
                .isEqualByComparingTo(inTheCurrentAccount);
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .isEqualByComparingTo(inTheSavingsAccount);
        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("nothing fell due, so there is nothing in the rule's history either")
                .isEmpty();

        app.daysPass(DAYS_UNTIL_IT_MOVES);
        assertThat(app.theDateTheClockReads())
                .as("the clock now reads the day the customer said their money moves on")
                .isEqualTo(theDayItMovesOn);

        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("the money left the current account the rule draws from")
                .isEqualByComparingTo(inTheCurrentAccount.subtract(new BigDecimal(FIFTY_EUROS)));
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .as("and landed in the savings account the rule feeds")
                .isEqualByComparingTo(inTheSavingsAccount.add(new BigDecimal(FIFTY_EUROS)));

        List<RuleOccurrenceView> history = app.historyOf(savingsAccount, rule.id());
        assertThat(history)
                .as("one day fell due, so the rule's history holds one occurrence")
                .hasSize(1);
        RuleOccurrenceView occurrence = history.get(0);
        assertThat(occurrence.dueOn())
                .as("recorded against the day it was due, which is the day the customer named")
                .isEqualTo(theDayItMovesOn);
        assertThat(occurrence.outcome()).isEqualTo("MOVED");
        assertThat(occurrence.amount()).isEqualByComparingTo(new BigDecimal(FIFTY_EUROS));
        assertThat(occurrence.settledAt())
                .as("and against the moment it was actually dealt with, which is what makes "
                        + "lateness a thing this history can report")
                .isNotNull();
        assertThat(occurrence.depositId())
                .as("an occurrence that moved money names the deposit it made, so that the history "
                        + "and the money can be read against each other")
                .isNotNull();
        assertThat(app.depositsInto(savingsAccount))
                .as("and that deposit is in the account's deposit history like any other")
                .anyMatch(deposit -> deposit.id().equals(occurrence.depositId()));
    }
}
