package io.dataroots.savingstreak.challenges;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ChallengeView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The asymmetry, asserted on rather than only written down: one withdrawal stops a holding challenge
 * dead and leaves a saving challenge exactly where it was.
 *
 * <p>That is not an inconsistency between two challenges, and this test exists to say so in
 * executable form. {@code HOLD_A_THOUSAND_FOR_NINETY_DAYS} asks whether the money <em>is there</em>,
 * so money leaving is the challenge's subject and the count starts again. {@code SAVE_TWO_THOUSAND}
 * asks whether the money <em>was put there</em>, and it is measured against the high-water mark,
 * which never falls — so money leaving costs nothing at all. Each challenge means what its words
 * say, and the same withdrawal is fatal to one of them and invisible to the other.
 *
 * <p>The euros going back in are the last step, and they are the other half of the same claim: they
 * restart the holding challenge from nothing rather than from where it was, and they move the saving
 * challenge by nothing at all, because they are euros that have already been counted once.
 *
 * <p>One test method on its own application, for the reason
 * {@code HoldingABalanceForNinetyDaysApiTest} gives: the clock only goes forward and every figure
 * here is a whole balance rather than a difference.
 */
class AWithdrawalStopsAHoldingChallengeAndNotASavingOneApiTest extends ApiIntegrationTest {

    private static final String HOLD = "HOLD_A_THOUSAND_FOR_NINETY_DAYS";
    private static final String SAVE = "SAVE_TWO_THOUSAND";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-withdrawal-asymmetry"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_same_withdrawal_ends_the_holding_challenge_and_leaves_the_saving_one_untouched() {
        long savings = app.savingsAccountOf(ANKE);

        app.enrolIn(ANKE, HOLD);
        app.enrolIn(ANKE, SAVE);

        app.deposit(savings, ANKE, "1000.00");
        app.daysPass(10);

        ChallengeView holdingBefore = app.challengeOf(ANKE, HOLD);
        ChallengeView savingBefore = app.challengeOf(ANKE, SAVE);
        assertThat(holdingBefore.reading())
                .as("ten days at the floor")
                .isEqualByComparingTo("10");
        assertThat(savingBefore.reading())
                .as("a thousand euros of genuinely new saving")
                .isEqualByComparingTo("1000.00");

        app.withdraw(savings, ANKE, "200.00");

        assertThat(app.challengeOf(ANKE, HOLD).reading())
                .as("the money is no longer there, and a challenge about money being there is back "
                        + "at nothing")
                .isEqualByComparingTo("0");
        assertThat(app.challengeOf(ANKE, SAVE).reading())
                .as("the money was still put there, and a challenge about money having been put "
                        + "there is exactly where it was — using your savings costs you nothing here")
                .isEqualByComparingTo("1000.00");

        app.deposit(savings, ANKE, "200.00");

        assertThat(app.challengeOf(ANKE, HOLD).reading())
                .as("the floor is met again, and the ten days are gone rather than resumed")
                .isEqualByComparingTo("0");
        assertThat(app.challengeOf(ANKE, SAVE).reading())
                .as("and the same two hundred euros paid back in are not new saving, so the saving "
                        + "challenge has not moved either")
                .isEqualByComparingTo("1000.00");

        app.daysPass(5);

        assertThat(app.challengeOf(ANKE, HOLD).reading())
                .as("the new run counts from the day the money went back, five days ago")
                .isEqualByComparingTo("5");
        assertThat(app.challengeOf(ANKE, SAVE).reading()).isEqualByComparingTo("1000.00");
    }
}
