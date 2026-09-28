package io.dataroots.savingstreak.challenges;

import io.dataroots.savingstreak.support.AchievementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ChallengeRungView;
import io.dataroots.savingstreak.support.ChallengeView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A card carries the rungs this customer has already won on the enrolment it is about, and when.
 *
 * <p>The card is what a ladder is drawn from, and a ladder with none of its rungs lit is a picture
 * of a challenge rather than a picture of somebody's place in one. Without this the only record of a
 * badge is the trophy case, and a page would have to read a history in order to colour a bar about
 * now — which would also get a repeatable challenge wrong, because an old round's badges are in the
 * case and are not this round's.
 *
 * <p><strong>The three cases are one narrative because they are one claim.</strong> A rung is dark
 * until it is won; it lights with the moment it was won on, and that moment is the same one the
 * trophy case reports for the same badge; it stays lit when the enrolment ends, because an award is
 * a fact and quitting costs nobody what they had already earned; and it goes dark again on a fresh
 * enrolment, because the second round asks for the whole of it again.
 *
 * <p>Its own application on a database nothing has ever been written to, for the reason every
 * exact-figure class in this feature has one: a mark another test moved would decide which rungs
 * were cleared here.
 */
class ACardSaysWhichRungsAreAlreadyWonApiTest extends ApiIntegrationTest {

    private static final String FIVE_HUNDRED = "SAVE_FIVE_HUNDRED";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-a-lit-ladder"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_rung_lights_when_it_is_won_stays_lit_when_she_leaves_and_is_dark_again_next_time() {
        long savings = app.savingsAccountOf(ANKE);

        assertThat(app.challengeOf(ANKE, FIVE_HUNDRED).rungs())
                .as("nobody has joined it, so there is no ladder of hers to light")
                .allSatisfy(rung -> assertThat(rung.wonAt()).isNull());

        app.enrolIn(ANKE, FIVE_HUNDRED);
        assertThat(app.challengeOf(ANKE, FIVE_HUNDRED).rungs())
                .as("joining wins nothing: enrolling is where the counting starts")
                .allSatisfy(rung -> assertThat(rung.wonAt()).isNull());

        // Bronze asks for a hundred, and nothing else on the ladder is near.
        app.deposit(savings, ANKE, "100.00");
        ChallengeView afterBronze = app.challengeOf(ANKE, FIVE_HUNDRED);
        ChallengeRungView bronze = rungOf(afterBronze, "BRONZE");
        assertThat(bronze.wonAt())
                .as("the rung she has just cleared is lit on the card she is looking at")
                .isNotNull();
        assertThat(rungOf(afterBronze, "SILVER").wonAt())
                .as("and the two above it are not, because she has not reached them")
                .isNull();
        assertThat(rungOf(afterBronze, "GOLD").wonAt()).isNull();
        assertThat(bronze.threshold())
                .as("what the rung asks for and what it pays are what they always were")
                .isEqualByComparingTo("100.00");
        assertThat(bronze.points()).isEqualTo(25);

        AchievementView inTheCase = app.achievementsOf(ANKE).get(0);
        assertThat(inTheCase.rung()).isEqualTo("BRONZE");
        assertThat(bronze.wonAt())
                .as("the same badge, so the same moment: the card and the trophy case are two "
                        + "readings of one award rather than two answers")
                .isEqualTo(inTheCase.awardedAt());

        app.leave(ANKE, FIVE_HUNDRED);
        ChallengeView afterLeaving = app.challengeOf(ANKE, FIVE_HUNDRED);
        assertThat(afterLeaving.state()).isEqualTo("ABANDONED");
        assertThat(afterLeaving.reading())
                .as("an enrolment that is over reports no reading, which is what makes the lit "
                        + "rung the only thing left on the card saying she got somewhere")
                .isNull();
        assertThat(rungOf(afterLeaving, "BRONZE").wonAt())
                .as("still lit, because an award is a fact and leaving costs her nothing she had "
                        + "already earned")
                .isEqualTo(inTheCase.awardedAt());

        // Round two of a repeatable challenge, which measures from a fresh mark and asks for the
        // whole hundred again.
        app.enrolIn(ANKE, FIVE_HUNDRED);
        ChallengeView roundTwo = app.challengeOf(ANKE, FIVE_HUNDRED);
        assertThat(roundTwo.reading()).isEqualByComparingTo("0.00");
        assertThat(roundTwo.rungs())
                .as("a dark ladder again: the badge from the first round is history, and telling "
                        + "her she is a third of the way through this one would be a lie the "
                        + "arithmetic underneath does not tell")
                .allSatisfy(rung -> assertThat(rung.wonAt()).isNull());
        assertThat(app.achievementsOf(ANKE))
                .as("and the trophy case still holds it, because nothing in it is ever revoked")
                .extracting(AchievementView::rung)
                .containsExactly("BRONZE");
    }

    private static ChallengeRungView rungOf(ChallengeView card, String rung) {
        return card.rungs().stream()
                .filter(one -> rung.equals(one.rung()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + rung + " rung on " + card.code()
                        + ", whose rungs are " + card.rungs()));
    }
}
