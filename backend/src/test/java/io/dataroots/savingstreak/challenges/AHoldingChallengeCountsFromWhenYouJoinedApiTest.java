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
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holding a floor is counted from the later of the moment the balance rose to it and the moment the
 * customer joined, so that a year of quietly holding a thousand euros buys no part of a ninety-day
 * badge.
 *
 * <p><strong>Why this is not the same test as the ninety-day one.</strong>
 * {@link HoldingABalanceForNinetyDaysApiTest} has its customer join before she has a euro in
 * savings, so the moment the floor was reached is always the later of the two and the enrolment
 * never has anything to say. That is the ordinary case, and it hid this one: the customer who was
 * already holding the floor when they joined. The spec rules out retro-crediting in two places —
 * "progress counts from the moment you enrol", and "no challenge looks backwards at saving done
 * before enrolment" — and the other counting kinds honour it. This one did not, and an enrolment
 * was worth an instant gold badge to anybody with a settled balance.
 *
 * <p><strong>Two customers, because the rule is a comparison and a comparison needs both sides.</strong>
 * Anke reaches the floor long before she joins, so her enrolment governs and her count starts at
 * nothing. Bram joins before he has the money, so the moment his balance rose to the floor is the
 * later of the two and it governs exactly as it always did — which is the half of this change that
 * must not move.
 *
 * <p><strong>One test method, because the clock only goes forward.</strong> A second method here
 * would find the clock already wound on and would be asserting against whatever order the two
 * happened to run in. The narrative is asserted on after every step instead, and the dip belongs
 * inside it: the claim about a dip is that it takes away days already counted, so it has to follow
 * days already counted.
 *
 * <p>Its own application on a database nothing has ever been written to, for the reason the
 * ninety-day test gives: a balance is the whole of what somebody holds, and on the shared database
 * these two would arrive holding whatever the tests that ran first left behind.
 */
class AHoldingChallengeCountsFromWhenYouJoinedApiTest extends ApiIntegrationTest {

    private static final String HOLD = "HOLD_A_THOUSAND_FOR_NINETY_DAYS";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseDaysThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-holding-from-joining"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void holding_the_floor_long_before_joining_counts_for_nothing_and_the_days_are_served_after() {
        long ankesSavings = app.savingsAccountOf(ANKE);
        long bramsSavings = app.savingsAccountOf(BRAM);

        // Anke settles a thousand euros into savings and then does nothing at all with it for a
        // third of a year, without having joined anything.
        app.deposit(ankesSavings, ANKE, "1000.00");
        app.daysPass(120);

        ChallengeView beforeSheJoins = app.challengeOf(ANKE, HOLD);
        assertThat(beforeSheJoins.enrolled())
                .as("a challenge nobody has joined is not counting for them")
                .isFalse();
        assertThat(beforeSheJoins.reading()).isNull();

        app.enrolIn(ANKE, HOLD);

        ChallengeView theMomentSheJoins = app.challengeOf(ANKE, HOLD);
        assertThat(theMomentSheJoins.reading())
                .as("a hundred and twenty days of holding it before she joined buy no part of "
                        + "this: progress counts from the moment you enrol, so the count starts at "
                        + "nothing")
                .isEqualByComparingTo("0");
        assertThat(theMomentSheJoins.nextRung()).isEqualTo("BRONZE");
        assertThat(theMomentSheJoins.stillNeeded())
                .as("bronze is thirty days away, all thirty of which are still to be served")
                .isEqualByComparingTo("30");
        assertThat(app.achievementsOf(ANKE))
                .as("joining mints nothing; a badge is something that happens afterwards")
                .noneSatisfy(won -> assertThat(won.challenge()).isEqualTo(HOLD));

        // Bram joins with nothing saved, which is the case that must not move: his floor moment is
        // still in front of him, so it is still the one that governs.
        app.enrolIn(BRAM, HOLD);
        assertThat(app.challengeOf(BRAM, HOLD).reading())
                .as("he holds nothing, so there is nothing to have held")
                .isEqualByComparingTo("0");

        app.daysPass(10);

        assertThat(app.challengeOf(ANKE, HOLD).reading())
                .as("ten days served since she joined, and ten is what the card says rather than "
                        + "a hundred and thirty")
                .isEqualByComparingTo("10");

        // Bram's thousand arrives ten days into his enrolment, so the floor moment is the later of
        // the two and is the one the count runs from.
        app.deposit(bramsSavings, BRAM, "1000.00");
        assertThat(app.challengeOf(BRAM, HOLD).reading())
                .as("the floor was reached a moment ago, so no whole day has been held yet")
                .isEqualByComparingTo("0");

        app.daysPass(20);

        assertThat(app.challengeOf(ANKE, HOLD).reading())
                .as("thirty days served since joining is thirty days held")
                .isEqualByComparingTo("30");
        assertThat(app.achievementsOf(ANKE))
                .as("and bronze arrives on the thirtieth day after she joined rather than on the "
                        + "day she joined")
                .anySatisfy(won -> {
                    assertThat(won.challenge()).isEqualTo(HOLD);
                    assertThat(won.rung()).isEqualTo("BRONZE");
                    assertThat(won.reading()).isEqualByComparingTo("30");
                });

        ChallengeView bramTwentyDaysIn = app.challengeOf(BRAM, HOLD);
        assertThat(bramTwentyDaysIn.reading())
                .as("twenty days since his money arrived, which is later than his enrolment and so "
                        + "is what the count runs from — unchanged by any of this")
                .isEqualByComparingTo("20");
        assertThat(bramTwentyDaysIn.nextRung()).isEqualTo("BRONZE");
        assertThat(bramTwentyDaysIn.stillNeeded()).isEqualByComparingTo("10");

        // The dip, after joining, still restarts the count from the dip rather than from the
        // enrolment: a hundred out of a thousand is nine hundred against a floor of a thousand.
        app.withdraw(ankesSavings, ANKE, "100.00");

        ChallengeView dipped = app.challengeOf(ANKE, HOLD);
        assertThat(dipped.reading())
                .as("a dip below the floor is the count starting again, whatever the enrolment says")
                .isEqualByComparingTo("0");
        assertThat(dipped.nextRung()).isEqualTo("BRONZE");
        assertThat(dipped.stillNeeded()).isEqualByComparingTo("30");
        assertThat(app.achievementsOf(ANKE))
                .as("the bronze she served for is a fact and is never taken back")
                .extracting(AchievementView::challenge, AchievementView::rung)
                .contains(Tuple.tuple(HOLD, "BRONZE"));

        app.deposit(ankesSavings, ANKE, "100.00");
        assertThat(app.challengeOf(ANKE, HOLD).reading())
                .as("the money is back and the count on it starts at nothing rather than resuming "
                        + "at thirty")
                .isEqualByComparingTo("0");

        app.daysPass(5);
        assertThat(app.challengeOf(ANKE, HOLD).reading())
                .as("five days since the money went back, counted from the dip rather than from "
                        + "the enrolment, which is older")
                .isEqualByComparingTo("5");
    }
}
