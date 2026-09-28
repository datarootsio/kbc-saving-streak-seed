package io.dataroots.savingstreak.challenges;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ChallengeRungView;
import io.dataroots.savingstreak.support.ChallengeView;
import io.dataroots.savingstreak.support.EnrolmentView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A challenge is something a customer chooses to be part-way through: they see what it asks of them,
 * they join it, and from that moment their saving fills it up.
 *
 * <p>The reading is the most they have ever saved now, less the most they had ever saved when they
 * joined. That single subtraction is what makes a challenge count the saving they did <em>since</em>
 * they enrolled and nothing they did before it, and it is also — because the mark never falls — the
 * whole of the anti-farming rule. {@code TheSameEurosMoveAChallengeOnceApiTest} is the other half of
 * that claim; this one is the ordinary path.
 *
 * <p>Its own application on a database nothing has ever been written to, because every figure here
 * is exact. On the shared one a customer arrives with whatever the tests that ran first left behind,
 * and a reading measured from a mark somebody else moved is really measuring the order the classes
 * happened to run in.
 *
 * <p>No points and no badges are asserted anywhere here, because no rung pays anything yet. What a
 * rung is worth is written on the card so that a customer can judge whether it is worth their while,
 * and paying it is the next slice.
 *
 * <p>The three tests share the application and stay out of each other's way by taking a customer and
 * a challenge nobody else here touches: Anke fills {@code SAVE_FIVE_HUNDRED}, Bram joins it and
 * leaves it, and nobody at all joins {@code SAVE_TWO_THOUSAND}, which is what makes it the honest
 * subject of the test about a challenge nobody has enrolled in. JUnit does not promise to run them
 * in the order they are written and nothing here needs it to.
 */
class EnrollingInAChallengeAndWatchingItFillApiTest extends ApiIntegrationTest {

    private static final String FIVE_HUNDRED = "SAVE_FIVE_HUNDRED";
    private static final String TWO_THOUSAND = "SAVE_TWO_THOUSAND";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-enrolling"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * What is on the card before anybody decides anything: the words, the ladder, and what each rung
     * pays. Nothing about a customer, because there is nothing about this customer to say — a
     * challenge they have not joined counts nothing, and the card says so by having nothing to
     * report rather than by reporting a nought they might mistake for progress they have made.
     */
    @Test
    void a_challenge_nobody_has_enrolled_in_says_what_it_asks_for_and_counts_nothing() {
        ChallengeView card = app.challengeOf(ANKE, TWO_THOUSAND);

        assertThat(card.title()).isNotBlank();
        assertThat(card.words())
                .as("the words a customer reads before deciding, not a restatement of the code")
                .isNotBlank();
        assertThat(card.kind()).isEqualTo("NEW_SAVINGS");
        assertThat(card.rungs())
                .as("three rungs, in the order they are climbed, each with what it asks for and "
                        + "what it pays")
                .extracting(ChallengeRungView::rung)
                .containsExactly("BRONZE", "SILVER", "GOLD");
        assertThat(card.rungs()).allSatisfy(rung -> {
            assertThat(rung.threshold()).isPositive();
            assertThat(rung.points()).isPositive();
        });

        assertThat(card.enrolled()).isFalse();
        assertThat(card.state()).isNull();
        assertThat(card.measuringFrom()).isNull();
        assertThat(card.reading())
                .as("enrolling is a decision somebody makes, not something that happens to them")
                .isNull();
        assertThat(card.nextRung()).isNull();
        assertThat(card.stillNeeded()).isNull();
    }

    /**
     * The whole of the ordinary path in one narrative, because it is one: the mark is taken at the
     * moment of joining, and every euro of genuinely new saving after that moves the reading and
     * shortens what the next rung asks for.
     *
     * <p>Anke saves EUR 60 <em>before</em> she joins, so that the first assertion after enrolling is
     * the one that matters most — her reading is nothing, not sixty. A challenge is something she
     * does rather than something her past already did.
     *
     * <p>The second deposit goes into her other savings account, which is the loophole this closes:
     * the mark is the customer's across everything they hold, so two accounts are two ways of paying
     * into one challenge rather than two challenges.
     */
    @Test
    void enrolling_takes_the_mark_and_the_reading_fills_from_there_across_every_account() {
        long savings = app.savingsAccountOf(ANKE);
        long theOtherSavings = app.otherSavingsAccountOf(ANKE);

        app.deposit(savings, ANKE, "60.00");
        assertThat(app.mostEverSavedOf(ANKE)).isEqualByComparingTo("60.00");

        EnrolmentView enrolment = app.enrolIn(ANKE, FIVE_HUNDRED);
        assertThat(enrolment.id()).isNotNull();
        assertThat(enrolment.challenge()).isEqualTo(FIVE_HUNDRED);
        assertThat(enrolment.state()).isEqualTo("ACTIVE");
        assertThat(enrolment.measuringFrom())
                .as("the enrolment records the mark it will measure from, which is the most she had "
                        + "ever saved at the moment she joined")
                .isEqualByComparingTo("60.00");
        assertThat(enrolment.enrolledAt()).isNotNull();
        assertThat(enrolment.endedAt()).isNull();

        ChallengeView justJoined = app.challengeOf(ANKE, FIVE_HUNDRED);
        assertThat(justJoined.enrolled()).isTrue();
        assertThat(justJoined.state()).isEqualTo("ACTIVE");
        assertThat(justJoined.measuringFrom()).isEqualByComparingTo("60.00");
        assertThat(justJoined.reading())
                .as("the sixty euros she saved before joining are not this challenge's")
                .isEqualByComparingTo("0.00");
        assertThat(justJoined.nextRung()).isEqualTo("BRONZE");
        assertThat(justJoined.stillNeeded())
                .as("bronze asks for a hundred and she has none of it yet")
                .isEqualByComparingTo("100.00");

        app.deposit(savings, ANKE, "100.00");
        ChallengeView pastBronze = app.challengeOf(ANKE, FIVE_HUNDRED);
        assertThat(pastBronze.reading()).isEqualByComparingTo("100.00");
        assertThat(pastBronze.nextRung())
                .as("bronze is cleared, so the rung to aim at is the one above it")
                .isEqualTo("SILVER");
        assertThat(pastBronze.stillNeeded()).isEqualByComparingTo("150.00");

        app.deposit(theOtherSavings, ANKE, "150.00");
        ChallengeView acrossBothAccounts = app.challengeOf(ANKE, FIVE_HUNDRED);
        assertThat(acrossBothAccounts.reading())
                .as("the reading spans every savings account she holds, so a second account is a "
                        + "second way of paying into one challenge and not a loophole")
                .isEqualByComparingTo("250.00");
        assertThat(acrossBothAccounts.nextRung()).isEqualTo("GOLD");
        assertThat(acrossBothAccounts.stillNeeded()).isEqualByComparingTo("250.00");

        app.deposit(savings, ANKE, "250.00");
        ChallengeView allTheWayUp = app.challengeOf(ANKE, FIVE_HUNDRED);
        assertThat(allTheWayUp.reading()).isEqualByComparingTo("500.00");
        assertThat(allTheWayUp.nextRung())
                .as("there is no rung above gold, so there is nothing left to ask for")
                .isNull();
        assertThat(allTheWayUp.stillNeeded()).isNull();

        assertThat(app.challengeOf(ANKE, TWO_THOUSAND).reading())
                .as("the same five hundred euros are counted by every challenge that asks about "
                        + "them — but only by the ones she joined")
                .isNull();
    }

    /**
     * Leaving is a state change and never a removal. The enrolment is still there afterwards, under
     * the same identifier and with the same mark on it, because the badges a later slice hangs off
     * it have to keep pointing at something — and because a customer who left a challenge did
     * something, and a row that is gone cannot say so.
     *
     * <p>The reading goes with it. Progress is derived on every read rather than stored, so there is
     * no frozen number to show for an enrolment that is over, and reporting one worked out from
     * to-day's mark would be a challenge quietly carrying on counting for somebody who left it.
     */
    @Test
    void leaving_ends_the_enrolment_rather_than_removing_it() {
        long savings = app.savingsAccountOf(BRAM);

        EnrolmentView joined = app.enrolIn(BRAM, FIVE_HUNDRED);
        app.deposit(savings, BRAM, "120.00");
        assertThat(app.challengeOf(BRAM, FIVE_HUNDRED).reading()).isEqualByComparingTo("120.00");

        EnrolmentView left = app.leave(BRAM, FIVE_HUNDRED);
        assertThat(left.id())
                .as("the same enrolment, ended — not a different one, and not nothing")
                .isEqualTo(joined.id());
        assertThat(left.state()).isEqualTo("ABANDONED");
        assertThat(left.measuringFrom()).isEqualByComparingTo(joined.measuringFrom());
        assertThat(left.endedAt()).isNotNull();

        ChallengeView afterLeaving = app.challengeOf(BRAM, FIVE_HUNDRED);
        assertThat(afterLeaving.enrolled()).isFalse();
        assertThat(afterLeaving.state())
                .as("the card says he left it rather than pretending he was never in it")
                .isEqualTo("ABANDONED");
        assertThat(afterLeaving.reading()).isNull();
        assertThat(afterLeaving.nextRung()).isNull();
        assertThat(afterLeaving.stillNeeded()).isNull();

        app.deposit(savings, BRAM, "200.00");
        assertThat(app.challengeOf(BRAM, FIVE_HUNDRED).reading())
                .as("and saving afterwards moves a challenge he is no longer in by nothing")
                .isNull();
    }
}
