package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two rules falling due on one morning move their money in the order their customer wrote them.
 *
 * <p>Deterministic, reconstructable from the log, and it needs no priority field anybody would have
 * to maintain. It is also the order the second rule of a morning finds whatever the first one left,
 * which is what makes a shortfall explainable rather than arbitrary.
 *
 * <p><strong>The rule written first is deliberately the one that would come second under every other
 * ordering this could accidentally be.</strong> It is named last in the alphabet, it moves the
 * larger amount, and it is the one with the lower identifier; a firing order that had quietly become
 * alphabetical, or by amount, would fail here rather than pass by coincidence.
 *
 * <p>Read back through the deposit identifiers, which the API hands out in the order the rows were
 * written: the rule written first made the deposit with the lower identifier, and that is the whole
 * claim, observable without reaching past the HTTP seam.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class TwoRulesDueOnOneMorningFireInTheOrderTheyWereCreatedApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String THIRTY_EUROS = "30.00";

    private static final String TEN_EUROS = "10.00";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseMorningThisTestStages() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-two-rules-on-one-morning"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_rule_written_first_moves_its_money_first() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        long currentAccount = app.currentAccountOf(ANKE);
        LocalDate theMorning = app.theDateTheClockReads().plusDays(1);
        String itsDay = theMorning.getDayOfWeek().name();

        SavingRuleView writtenFirst = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(
                        currentAccount, "Zolder renovation", itsDay, THIRTY_EUROS));
        SavingRuleView writtenSecond = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(
                        currentAccount, "Ardennes weekend", itsDay, TEN_EUROS));

        app.daysPass(1);
        app.runJob(THE_JOB);

        Long byTheFirst = app.historyOf(savingsAccount, writtenFirst.id()).get(0).depositId();
        Long byTheSecond = app.historyOf(savingsAccount, writtenSecond.id()).get(0).depositId();
        assertThat(byTheFirst)
                .as("the deposit the first-written rule made was written before the other, which "
                        + "is the order the customer left them standing in — and not the "
                        + "alphabetical order of their names, nor the order of their amounts")
                .isLessThan(byTheSecond);

        DepositView[] deposits = app.depositsInto(savingsAccount);
        assertThat(deposits)
                .as("one morning, two rules, two deposits")
                .hasSize(2);
        assertThat(deposits[deposits.length - 1].id())
                .as("the deposit history reads newest first, so the oldest entry in it is the one "
                        + "the first-written rule made")
                .isEqualTo(byTheFirst);
        assertThat(deposits[deposits.length - 1].amount())
                .isEqualByComparingTo(new BigDecimal(THIRTY_EUROS));
    }
}
