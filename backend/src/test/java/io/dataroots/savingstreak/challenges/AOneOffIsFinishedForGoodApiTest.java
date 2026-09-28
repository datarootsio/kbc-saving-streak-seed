package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AchievementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ChallengeView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A one-off is a one-off: finish it, and the application will not let you take it on again.
 *
 * <p>The other half of the repeat story, and the half that has to be refused rather than merely
 * measured. A repeatable challenge can be taken on again because a fresh enrolment takes a fresh
 * mark, so the second round asks for genuinely new money. A challenge the bank has declared a
 * one-off is making a different promise — that the badge means the first and only time — and there
 * is no arithmetic that keeps that promise. Only a refusal does.
 *
 * <p><strong>409 and not 404, and not 400.</strong> The customer exists, the challenge exists, and
 * the request naming both is perfectly well formed. What will not allow it is the state of an
 * enrolment they already hold, which is exactly what a conflict is, and a page that told them to
 * correct what they typed would send them looking for a mistake they did not make.
 *
 * <p><strong>Finishing is what closes it, not merely having been in it.</strong> A one-off somebody
 * enrolled in and never finished is still theirs to be in, and one they abandoned part-way is
 * theirs to come back to — the promise the bank made was about the badge, and they have not won it.
 * So the refusal keys on a <em>completed</em> enrolment and nothing else, and this class says so by
 * abandoning one and re-joining it.
 *
 * <p><strong>The one-off is this test's own.</strong> Both challenges the bank seeds are repeatable,
 * because both count new saving and a flow can honestly be repeated. A seeded one-off would change
 * what every other test in the run sees on the challenges tab in order to let this one ask its
 * question, so the definition is written into this application's own throwaway database instead —
 * which is also the first exercise of {@code ChallengeDefinition.repeatable} as a field that decides
 * anything, rather than one that is written and read back.
 */
class AOneOffIsFinishedForGoodApiTest extends ApiIntegrationTest {

    /** A one-off of this test's own, and no seed anywhere writes one under this code. */
    private static final String ONCE_IN_A_LIFETIME = "THE_FIRST_HUNDRED";

    /** A repeatable one the bank does seed, for the contrast that makes the refusal mean something. */
    private static final String FIVE_HUNDRED = "SAVE_FIVE_HUNDRED";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwnWithAOneOffInIt() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-a-one-off"));
        app.aBeanOfTheApplication(ChallengeDefinitionRepository.class).save(
                ChallengeDefinition.offering(
                        ONCE_IN_A_LIFETIME,
                        "Your first EUR 100",
                        "The first hundred euros you ever put away, and only the first. Take it on "
                                + "once; there is no second time to have started.",
                        ChallengeKind.NEW_SAVINGS,
                        null,
                        List.of(new ChallengeRung(Rung.BRONZE, new BigDecimal("20.00"), 5),
                                new ChallengeRung(Rung.SILVER, new BigDecimal("50.00"), 10),
                                new ChallengeRung(Rung.GOLD, new BigDecimal("100.00"), 20)),
                        false,
                        null));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** The refusal, and what it does not touch: the badges the one round did win. */
    @Test
    void finishing_a_one_off_refuses_a_second_enrolment_and_keeps_every_badge_it_paid() {
        long savings = app.savingsAccountOf(ANKE);

        ChallengeView offered = app.challengeOf(ANKE, ONCE_IN_A_LIFETIME);
        assertThat(offered.repeatable())
                .as("the card says out loud that this one cannot be taken on again")
                .isFalse();

        app.enrolIn(ANKE, ONCE_IN_A_LIFETIME);
        app.deposit(savings, ANKE, "100.00");

        assertThat(app.challengeOf(ANKE, ONCE_IN_A_LIFETIME).state())
                .as("a hundred euros is gold, and gold is the end of it")
                .isEqualTo("COMPLETED");
        List<AchievementView> won = app.achievementsOf(ANKE);
        assertThat(won).extracting(AchievementView::rung)
                .containsExactly("GOLD", "SILVER", "BRONZE");

        ResponseEntity<JsonNode> again = app.tryToEnrol(app.customerIdOf(ANKE), ONCE_IN_A_LIFETIME);

        assertThat(again.getStatusCode())
                .as("the state of an enrolment she already holds is what refuses this, which is a "
                        + "conflict and not a mistake in what she sent")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("detail").asText())
                .as("and the sentence names the challenge, so a page that has been open a while "
                        + "can see which one it sent")
                .contains(ONCE_IN_A_LIFETIME);

        assertThat(app.achievementsOf(ANKE))
                .as("a refusal takes nothing away: the round she did finish is still in the case")
                .hasSize(won.size());
        assertThat(app.challengeOf(ANKE, ONCE_IN_A_LIFETIME).state())
                .as("and the enrolment it refused to replace is still the finished one")
                .isEqualTo("COMPLETED");
    }

    /**
     * A one-off she started and left is hers to start again. The bank's promise was about the badge,
     * and she has not won it.
     */
    @Test
    void a_one_off_that_was_never_finished_can_still_be_taken_on() {
        String customer = app.aCustomerOfItsOwn("a-one-off-nobody-finished");

        app.enrolIn(customer, ONCE_IN_A_LIFETIME);
        assertThat(app.leave(customer, ONCE_IN_A_LIFETIME).state()).isEqualTo("ABANDONED");

        assertThat(app.tryToEnrolIn(customer, ONCE_IN_A_LIFETIME).getStatusCode())
                .as("nothing was ever completed, so there is no one-off to have used up")
                .isEqualTo(HttpStatus.CREATED);
    }

    /**
     * And the refusal belongs to the flag rather than to having finished something. A repeatable
     * challenge finished by the same customer on the same day is taken on again without a murmur,
     * which is what makes the paragraph above a decision and not an accident.
     */
    @Test
    void a_repeatable_challenge_finished_the_same_way_is_taken_on_again() {
        String customer = app.aCustomerOfItsOwn("a-repeatable-one-finished-once");
        long savings = app.savingsAccountOf(customer);

        app.enrolIn(customer, FIVE_HUNDRED);
        app.deposit(savings, customer, "500.00");
        assertThat(app.challengeOf(customer, FIVE_HUNDRED).state()).isEqualTo("COMPLETED");

        assertThat(app.tryToEnrolIn(customer, FIVE_HUNDRED).getStatusCode())
                .as("the same shape of request, the same finished enrolment, and the only "
                        + "difference is what the definition says about being taken on again")
                .isEqualTo(HttpStatus.CREATED);
    }
}
