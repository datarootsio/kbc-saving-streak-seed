package io.dataroots.savingstreak.challenges;

import io.dataroots.savingstreak.support.AchievementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ChallengeView;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two challenges about money being there rather than having been put there: how much is in
 * savings right now, and how many consecutive days it has stayed at or above a floor.
 *
 * <p>One is a photograph and the other is how long the photograph has looked the same. Both are
 * derived from the ledgers on every read — nothing about "held since" is written down anywhere —
 * which is what lets a trainer wind the development clock ninety days on and watch a holding
 * challenge finish inside a session.
 *
 * <p><strong>One test method, because the clock only goes forward.</strong> A second method in this
 * class would find the clock already a hundred and eighty days on and would be asserting against
 * whatever order the two happened to run in. The narrative is asserted on after every step instead,
 * which is what a run of days is — and the dip belongs inside the same narrative rather than beside
 * it, because the whole claim about a dip is that it takes away days that had already been counted.
 *
 * <p>Its own application on a database nothing has ever been written to, because every figure here
 * is exact: a balance is the whole of what somebody holds, and on the shared database Anke arrives
 * holding whatever the tests that ran first left behind.
 */
class HoldingABalanceForNinetyDaysApiTest extends ApiIntegrationTest {

    private static final String BUFFER = "BUILD_A_BUFFER";
    private static final String HOLD = "HOLD_A_THOUSAND_FOR_NINETY_DAYS";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseDaysThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-holding"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void ninety_days_at_the_floor_completes_and_a_one_day_dip_on_day_eighty_nine_starts_it_again() {
        long savings = app.savingsAccountOf(ANKE);
        long theOtherSavings = app.otherSavingsAccountOf(ANKE);

        ChallengeView bufferBeforeJoining = app.challengeOf(ANKE, BUFFER);
        assertThat(bufferBeforeJoining.kind()).isEqualTo("BALANCE_REACHED");
        assertThat(bufferBeforeJoining.repeatable())
                .as("a stock challenge is once in a lifetime: the same thousand euros would "
                        + "otherwise be worth a gold badge every time she re-enrolled")
                .isFalse();
        ChallengeView holdingBeforeJoining = app.challengeOf(ANKE, HOLD);
        assertThat(holdingBeforeJoining.kind()).isEqualTo("BALANCE_HELD");
        assertThat(holdingBeforeJoining.repeatable()).isFalse();
        assertThat(holdingBeforeJoining.rungs())
                .as("the rungs of a holding challenge are counts of days, and the headline one is "
                        + "ninety")
                .anySatisfy(rung -> assertThat(rung.threshold()).isEqualByComparingTo("90"));

        app.enrolIn(ANKE, BUFFER);
        app.enrolIn(ANKE, HOLD);

        ChallengeView bufferAtNothing = app.challengeOf(ANKE, BUFFER);
        assertThat(bufferAtNothing.reading())
                .as("she holds nothing, and nothing is a reading rather than an absence")
                .isEqualByComparingTo("0.00");
        assertThat(bufferAtNothing.nextRung()).isEqualTo("BRONZE");
        assertThat(bufferAtNothing.stillNeeded()).isEqualByComparingTo("500.00");
        ChallengeView holdingAtNothing = app.challengeOf(ANKE, HOLD);
        assertThat(holdingAtNothing.reading())
                .as("a customer who has never reached the floor has held it for no days")
                .isEqualByComparingTo("0");
        assertThat(holdingAtNothing.stillNeeded()).isEqualByComparingTo("30");

        // Six hundred into one account: past the buffer's bronze, and nowhere near the floor the
        // holding challenge is about.
        app.deposit(savings, ANKE, "600.00");
        ChallengeView bufferPastBronze = app.challengeOf(ANKE, BUFFER);
        assertThat(bufferPastBronze.reading()).isEqualByComparingTo("600.00");
        assertThat(bufferPastBronze.nextRung()).isEqualTo("SILVER");
        assertThat(bufferPastBronze.stillNeeded()).isEqualByComparingTo("400.00");
        assertThat(app.challengeOf(ANKE, HOLD).reading())
                .as("six hundred is under the floor, so nothing is being held yet")
                .isEqualByComparingTo("0");

        // The other four hundred into her second savings account, which is the loophole this
        // closes: both readings are about the customer, so two accounts are one buffer.
        app.deposit(theOtherSavings, ANKE, "400.00");
        ChallengeView bufferAcrossBothAccounts = app.challengeOf(ANKE, BUFFER);
        assertThat(bufferAcrossBothAccounts.reading())
                .as("the balance spans every savings account she holds, so holding two of them is "
                        + "not a way of being asked for less")
                .isEqualByComparingTo("1000.00");
        assertThat(bufferAcrossBothAccounts.nextRung()).isEqualTo("GOLD");
        assertThat(bufferAcrossBothAccounts.stillNeeded()).isEqualByComparingTo("1500.00");
        ChallengeView holdingFromToday = app.challengeOf(ANKE, HOLD);
        assertThat(holdingFromToday.reading())
                .as("the floor was reached a moment ago, so no whole day has been held yet")
                .isEqualByComparingTo("0");
        assertThat(holdingFromToday.stillNeeded()).isEqualByComparingTo("30");

        app.daysPass(30);

        ChallengeView thirtyDaysHeld = app.challengeOf(ANKE, HOLD);
        assertThat(thirtyDaysHeld.reading())
                .as("a month of leaving it alone, counted from the day the floor was reached")
                .isEqualByComparingTo("30");
        assertThat(thirtyDaysHeld.nextRung()).isEqualTo("SILVER");
        assertThat(thirtyDaysHeld.stillNeeded())
                .as("the days still to go are on the card beside the days held")
                .isEqualByComparingTo("30");
        assertThat(app.achievementsOf(ANKE))
                .as("thirty days at the floor is the holding challenge's bronze")
                .anySatisfy(won -> {
                    assertThat(won.challenge()).isEqualTo(HOLD);
                    assertThat(won.rung()).isEqualTo("BRONZE");
                    assertThat(won.reading()).isEqualByComparingTo("30");
                });

        app.daysPass(59);

        ChallengeView theDayBeforeGold = app.challengeOf(ANKE, HOLD);
        assertThat(theDayBeforeGold.reading()).isEqualByComparingTo("89");
        assertThat(theDayBeforeGold.nextRung()).isEqualTo("GOLD");
        assertThat(theDayBeforeGold.stillNeeded())
                .as("one day short of ninety, which is the whole point of what happens next")
                .isEqualByComparingTo("1");

        // The dip. A hundred euros out on day eighty-nine, which is a balance of nine hundred
        // against a floor of a thousand.
        app.withdraw(savings, ANKE, "100.00");

        ChallengeView dipped = app.challengeOf(ANKE, HOLD);
        assertThat(dipped.reading())
                .as("a dip below the floor, however brief and however late, is the count starting "
                        + "again — that is what the word held means")
                .isEqualByComparingTo("0");
        assertThat(dipped.nextRung()).isEqualTo("BRONZE");
        assertThat(dipped.stillNeeded()).isEqualByComparingTo("30");
        assertThat(app.challengeOf(ANKE, BUFFER).reading())
                .as("and the buffer reads what she is actually holding, which is nine hundred")
                .isEqualByComparingTo("900.00");
        assertThat(app.achievementsOf(ANKE))
                .as("the badges already won are facts and are never taken back, whatever the "
                        + "balance does afterwards")
                .extracting(AchievementView::challenge, AchievementView::rung)
                .contains(Tuple.tuple(HOLD, "SILVER"), Tuple.tuple(BUFFER, "SILVER"));

        app.daysPass(1);
        assertThat(app.challengeOf(ANKE, HOLD).reading())
                .as("a day under the floor is a day not held")
                .isEqualByComparingTo("0");

        app.deposit(savings, ANKE, "100.00");
        assertThat(app.challengeOf(ANKE, HOLD).reading())
                .as("the money is back and the count on it starts at nothing: the eighty-nine days "
                        + "she had nearly finished are gone rather than resumed")
                .isEqualByComparingTo("0");

        app.daysPass(90);

        ChallengeView finished = app.challengeOf(ANKE, HOLD);
        assertThat(finished.reading())
                .as("ninety consecutive days at the floor, counted from the day the money went "
                        + "back rather than from the day it first arrived")
                .isEqualByComparingTo("90");
        assertThat(finished.state())
                .as("gold is the last rung, so reaching it ends the enrolment")
                .isEqualTo("COMPLETED");
        assertThat(finished.enrolled()).isFalse();
        assertThat(finished.nextRung()).isNull();
        assertThat(app.achievementsOf(ANKE))
                .extracting(AchievementView::challenge, AchievementView::rung)
                .contains(Tuple.tuple(HOLD, "GOLD"));
    }
}
