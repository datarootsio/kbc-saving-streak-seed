package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rule that has been ended fires nothing, while the rule standing beside it fires as it always
 * did.
 *
 * <p>User story 33. Ending a rule is a closing rather than a deletion — its record stays readable —
 * and this is the half of that which is about money: an instruction that stopped existing must stop
 * moving euros, on a night when an identical instruction beside it is moving them.
 *
 * <p><strong>Two rules rather than one, and that is the both-directions of it.</strong> A job that
 * fired nothing at all would satisfy "the ended rule fired nothing" and be entirely broken; the live
 * rule firing on the same morning is what says the night ran.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class AnEndedRuleFiresNothingApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String TEN_EUROS = "10.00";

    private static final String TWENTY_EUROS = "20.00";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeekThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-ended-rule-fires-nothing"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_ended_rule_moves_nothing_on_a_morning_the_standing_one_moves_its_amount() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayTheyBothMoveOn = app.theDateTheClockReads().plusDays(2);
        String itsDay = theDayTheyBothMoveOn.getDayOfWeek().name();
        long currentAccount = app.currentAccountOf(ANKE);

        SavingRuleView ended = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(
                        currentAccount, "The one she stopped", itsDay, TEN_EUROS));
        SavingRuleView standing = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(
                        currentAccount, "The one she kept", itsDay, TWENTY_EUROS));
        // A first morning while both of them are standing, so that the rule about to be ended has a
        // history of its own — which is what makes "it keeps its record" a thing to assert rather
        // than an empty list that would look the same either way.
        app.daysPass(2);
        app.runJob(THE_JOB);
        assertThat(app.historyOf(savingsAccount, ended.id()))
                .as("both rules fired on the first morning, while both were standing")
                .hasSize(1);

        app.endRule(savingsAccount, ended.id());
        BigDecimal beforeTheSecondMorning = app.currentAccountBalanceOf(ANKE);

        app.aWeekPasses();
        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("only the rule still standing moved money on the second morning: the ended "
                        + "one's ten euros stayed exactly where they were")
                .isEqualByComparingTo(beforeTheSecondMorning.subtract(new BigDecimal(TWENTY_EUROS)));
        assertThat(app.historyOf(savingsAccount, ended.id()))
                .as("an ended rule is a record rather than an instruction: it records nothing new, "
                        + "and what it did while it was standing is still readable — which is the "
                        + "whole reason ending one is a closing rather than a deletion")
                .hasSize(1);
        assertThat(app.historyOf(savingsAccount, standing.id()))
                .as("while the rule beside it fired again, which is what says this night ran at all")
                .hasSize(2);
        assertThat(app.depositsInto(savingsAccount))
                .as("two rules on the first morning and one on the second is three deposits")
                .hasSize(3);
    }
}
