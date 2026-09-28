package io.dataroots.savingstreak.challenges;

import java.util.List;

import io.dataroots.savingstreak.support.AchievementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ChallengeView;
import io.dataroots.savingstreak.support.EnrolmentView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Finishing "save EUR 500" and taking it on again asks for a real second EUR 500.
 *
 * <p>This is the sentence the whole anti-farming story is written to make true, and it is made true
 * by one decision rather than by a rule: a fresh enrolment reads the customer's high-water mark as
 * it stands <em>then</em> and measures everything afterwards above that. So the second round starts
 * at nought with the first round's five hundred euros sitting untouched underneath it, and the only
 * thing that can move it is money that has never been saved before. Nothing polices anything — the
 * arithmetic gives no other answer.
 *
 * <p><strong>The other two halves are asserted here too, because a repeat is all three at once.</strong>
 * The first round has to <em>end</em>, or there is nothing to repeat — reaching gold completes the
 * enrolment. The first round's badges have to <em>stay</em>, because a trophy case is a history and
 * nothing in it is ever revoked. And the second round's badges have to be <em>new rows</em> rather
 * than the old ones found again, which is what keying an award to its enrolment buys: two bronzes,
 * two ids, two payments, for two genuinely different five hundred euros.
 *
 * <p>The round trip is run inside the second round as well, which is the case a repeat makes newly
 * dangerous: somebody who has legitimately finished once could otherwise re-enrol and refill the
 * second round with the same money they already banked. They cannot, for the same reason they could
 * not the first time, and it is worth a test saying so at the one moment the mark and the balance
 * disagree.
 *
 * <p>Its own application on a database nothing has ever been written to, because this class
 * withdraws and because every figure in it is exact: a mark somebody else's test moved would decide
 * every reading below.
 */
class ARepeatAsksForANewFiveHundredApiTest extends ApiIntegrationTest {

    private static final String FIVE_HUNDRED = "SAVE_FIVE_HUNDRED";

    /** What the seed prices the rungs of {@code SAVE_FIVE_HUNDRED} at, and the ladder altogether. */
    private static final long BRONZE_PAYS = 25;
    private static final long SILVER_PAYS = 75;
    private static final long GOLD_PAYS = 200;
    private static final long THE_WHOLE_LADDER_PAYS = BRONZE_PAYS + SILVER_PAYS + GOLD_PAYS;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-a-real-second-five-hundred"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * Two rounds of the same challenge in one narrative, because the claim is about what the second
     * one inherits from the first and there is no way to ask that in two independent tests.
     */
    @Test
    void the_second_five_hundred_has_to_be_a_real_second_five_hundred() {
        long savings = app.savingsAccountOf(ANKE);

        EnrolmentView firstRound = app.enrolIn(ANKE, FIVE_HUNDRED);
        assertThat(firstRound.measuringFrom())
                .as("she has saved nothing yet, so the first round measures from nothing")
                .isEqualByComparingTo("0.00");

        // Five hundred in one go clears the whole ladder, which is the shortest honest way to get an
        // enrolment finished and a trophy case with something in it to inherit.
        app.deposit(savings, ANKE, "500.00");
        long beforeTheFirstRoundWasJudged = app.pointsBalanceOf(ANKE);
        List<AchievementView> afterTheFirstRound = app.achievementsOf(ANKE);
        assertThat(afterTheFirstRound)
                .as("all three rungs, newest first, for five hundred euros genuinely saved")
                .extracting(AchievementView::rung)
                .containsExactly("GOLD", "SILVER", "BRONZE");
        assertThat(app.pointsBalanceOf(ANKE) - beforeTheFirstRoundWasJudged)
                .isEqualTo(THE_WHOLE_LADDER_PAYS);

        ChallengeView finished = app.challengeOf(ANKE, FIVE_HUNDRED);
        assertThat(finished.state())
                .as("gold is the last rung, so reaching it is the end of the enrolment — and that "
                        + "is what makes a repeat a thing there is any need for")
                .isEqualTo("COMPLETED");
        assertThat(finished.repeatable())
                .as("and the card says out loud that it can be taken on again")
                .isTrue();

        // Round two. The enrolment she has is finished rather than running, so this is allowed —
        // and the mark it takes is the one her first round left behind.
        EnrolmentView secondRound = app.enrolIn(ANKE, FIVE_HUNDRED);
        assertThat(secondRound.id())
                .as("a second enrolment and not the first one handed back")
                .isNotEqualTo(firstRound.id());
        assertThat(secondRound.measuringFrom())
                .as("the mark as it stands now, which is the whole of the rule: the five hundred "
                        + "she has already been paid for is underneath this round, not inside it")
                .isEqualByComparingTo("500.00");
        assertThat(secondRound.state()).isEqualTo("ACTIVE");

        ChallengeView startingAgain = app.challengeOf(ANKE, FIVE_HUNDRED);
        assertThat(startingAgain.reading())
                .as("so the second round starts at nought however much she is holding")
                .isEqualByComparingTo("0.00");
        assertThat(startingAgain.nextRung()).isEqualTo("BRONZE");
        assertThat(startingAgain.stillNeeded()).isEqualByComparingTo("100.00");

        long paidForTheFirstRound = app.pointsBalanceOf(ANKE);
        assertThat(app.achievementsOf(ANKE))
                .as("and everything she won in the first round is still in the case")
                .extracting(AchievementView::rung)
                .containsExactly("GOLD", "SILVER", "BRONZE");
        assertThat(app.pointsBalanceOf(ANKE))
                .as("re-enrolling pays nothing: the first round's rungs are not re-awarded against "
                        + "the second round's enrolment just because its reading will pass them")
                .isEqualTo(paidForTheFirstRound);

        // The round trip, inside the second round, which is where it is most tempting: she is
        // holding five hundred euros and a fresh challenge that asks for five hundred euros.
        app.withdraw(savings, ANKE, "500.00");
        app.deposit(savings, ANKE, "500.00");
        assertThat(app.challengeOf(ANKE, FIVE_HUNDRED).reading())
                .as("the euros she already banked cannot be banked again, whichever round she is in")
                .isEqualByComparingTo("0.00");
        assertThat(app.achievementsOf(ANKE))
                .as("and so nothing was minted for them")
                .hasSize(3);
        assertThat(app.pointsBalanceOf(ANKE))
                .as("and nothing was paid for them")
                .isEqualTo(paidForTheFirstRound);

        // Genuinely new money, and the second round moves exactly as the first one did.
        app.deposit(savings, ANKE, "100.00");
        long beforeTheSecondBronze = app.pointsBalanceOf(ANKE);
        List<AchievementView> withASecondBronze = app.achievementsOf(ANKE);
        assertThat(withASecondBronze)
                .as("a second bronze, for a second hundred euros nobody had saved before")
                .extracting(AchievementView::rung)
                .containsExactly("BRONZE", "GOLD", "SILVER", "BRONZE");
        assertThat(app.pointsBalanceOf(ANKE) - beforeTheSecondBronze)
                .as("and it pays what bronze pays, all over again")
                .isEqualTo(BRONZE_PAYS);
        assertThat(withASecondBronze.get(0).id())
                .as("a row of its own, hung off the second enrolment rather than the first — which "
                        + "is why the first round's bronze did not have to be found and reused")
                .isNotEqualTo(withASecondBronze.get(3).id());
        assertThat(withASecondBronze.get(0).reading())
                .as("won at a hundred, counted from the mark the second round started at")
                .isEqualByComparingTo("100.00");
        assertThat(withASecondBronze.get(3).reading())
                .as("and the first round's bronze still says what it said when it was won")
                .isEqualByComparingTo("500.00");

        // Four hundred more finishes the second round, and by then she has genuinely put away a
        // thousand euros rather than walking five hundred in and out twice.
        app.deposit(savings, ANKE, "400.00");
        long beforeTheSecondRoundFinished = app.pointsBalanceOf(ANKE);
        assertThat(app.achievementsOf(ANKE))
                .as("two whole ladders, in the order they were climbed")
                .extracting(AchievementView::rung)
                .containsExactly("GOLD", "SILVER", "BRONZE", "GOLD", "SILVER", "BRONZE");
        assertThat(app.pointsBalanceOf(ANKE) - beforeTheSecondRoundFinished)
                .as("silver and gold of the second round, and nothing of the first")
                .isEqualTo(SILVER_PAYS + GOLD_PAYS);
        assertThat(app.challengeOf(ANKE, FIVE_HUNDRED).state()).isEqualTo("COMPLETED");

        assertThat(app.mostEverSavedOf(ANKE))
                .as("two finished rounds of \"save EUR 500\" mean a thousand euros genuinely put "
                        + "away, which is the sentence this whole feature is built to make true")
                .isEqualByComparingTo("1000.00");
    }
}
