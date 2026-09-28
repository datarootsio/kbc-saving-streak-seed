package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The deposit an automatic rule makes earns exactly what a manual deposit of the same amount earns,
 * at the streak rate, and counts toward the week the same way.
 *
 * <p>User stories 19 and 20, and the sentence the feature's argument rests on: an automatic deposit
 * <em>is</em> a deposit. There is no special case in points, streaks or the week — "a weekly rule at
 * or above the weekly minimum keeps a streak alive by itself, and that is the product rather than a
 * side effect."
 *
 * <p><strong>Asserted as an equality against a manual deposit rather than against figures written
 * out here</strong>, because the claim is parity and not arithmetic. Numbers copied into a test go
 * on passing after the ladder they came from has changed, and they would say nothing at all about
 * the two paths being the same path.
 *
 * <p>Both customers are given the identical history — one manual deposit at the weekly minimum, then
 * a week — so that the only difference left between them on the second week is which door the money
 * came through. The rate is asserted to be above the ordinary one as well, because two deposits both
 * paid the base rate would satisfy an equality while proving nothing about the streak.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class WhatARuleDepositsEarnsWhatAManualDepositWouldApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    /** The weekly minimum, so that each week this test stages is a week that was secured. */
    private static final String FIFTY_EUROS = "50.00";

    private static final BigDecimal THE_ORDINARY_RATE = new BigDecimal("1.00");

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestCounts() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-automatic-deposit-earns"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void an_automatic_deposit_and_a_manual_one_of_the_same_amount_earn_the_same_thing() {
        long ankes = app.savingsAccountOf(ANKE);
        long brams = app.savingsAccountOf(BRAM);

        // Week one, by hand for both of them, so that the run of weeks behind the second week is
        // the same run for each. Anke's rule is left standing on the same day, which is a week away.
        app.deposit(ankes, ANKE, FIFTY_EUROS);
        app.deposit(brams, BRAM, FIFTY_EUROS);
        LocalDate theDayItMovesOn = app.theDateTheClockReads().plusWeeks(1);
        SavingRuleView rule = app.leaveARuleStanding(ankes,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "Fifty a week", theDayItMovesOn.getDayOfWeek().name(), FIFTY_EUROS));

        app.aWeekPasses();
        assertThat(app.theDateTheClockReads())
                .as("the clock reads the rule's day, a week on from the deposits above")
                .isEqualTo(theDayItMovesOn);

        // Week two: hers by the rule, his by hand, on the same day of the same week.
        app.runJob(THE_JOB);
        DepositView byHand = app.deposit(brams, BRAM, FIFTY_EUROS);

        Long depositTheRuleMade = app.historyOf(ankes, rule.id()).get(0).depositId();
        DepositView automatically = Arrays.stream(app.depositsInto(ankes))
                .filter(deposit -> deposit.id().equals(depositTheRuleMade))
                .findFirst()
                .orElseThrow();

        assertThat(automatically.multiplierApplied())
                .as("the automatic deposit was paid at the rate the run of weeks pays, which is the "
                        + "same rate the manual one was paid at")
                .isEqualByComparingTo(byHand.multiplierApplied());
        assertThat(automatically.multiplierApplied())
                .as("and that rate is a streak rate rather than the ordinary one, or this "
                        + "comparison would be two deposits agreeing about nothing in particular")
                .isGreaterThan(THE_ORDINARY_RATE);
        assertThat(automatically.pointsEarned())
                .as("so it earned exactly what the manual deposit of the same amount earned")
                .isEqualTo(byHand.pointsEarned());
        assertThat(automatically.basePoints()).isEqualTo(byHand.basePoints());
        assertThat(automatically.streakBonusPoints())
                .as("including the streak bonus, which is the half an automatic deposit would lose "
                        + "if it were treated as a special case anywhere")
                .isEqualTo(byHand.streakBonusPoints());
        assertThat(automatically.newSavings())
                .as("and all of it was new saving, exactly as the manual one was")
                .isEqualByComparingTo(byHand.newSavings());

        BalancesView hers = app.balancesOf(ankes);
        BalancesView his = app.balancesOf(brams);
        assertThat(hers.newSavingsThisWeek())
                .as("the automatic deposit counted toward the week, which is what lets a weekly "
                        + "rule hold a streak together on its own")
                .isEqualByComparingTo(his.newSavingsThisWeek());
        assertThat(hers.newSavingsThisWeek())
                .isEqualByComparingTo(new BigDecimal(FIFTY_EUROS));
        assertThat(hers.currentStreakWeeks())
                .as("and carried the run of weeks along with it, as the manual one did")
                .isEqualTo(his.currentStreakWeeks());
        assertThat(hers.currentStreakWeeks())
                .as("two consecutive weeks, each secured by one deposit at the weekly minimum")
                .isEqualTo(2);
    }
}
