package io.dataroots.savingstreak.potclosing;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 68: ending the arrangement is an owner's decision, and a contributor or a viewer who tries
 * to close the pot is refused with 403.
 *
 * <p><strong>Closing is the largest thing anybody can do to a pot.</strong> It gives everybody their
 * money back, abandons what the group was saving for, ends whatever they were still deciding, and
 * cannot be undone by anybody — there is no reopening a pot and deliberately no endpoint for one. A
 * contributor who could do that would be able to end an arrangement the owners made, in one click,
 * on everybody's behalf.
 *
 * <p><strong>403, and in the same words every other role refusal in this feature uses.</strong> The
 * request is understood, the caller is known and they are not allowed: not a 404, which would tell a
 * member that the pot they can plainly read is not there, and not a 400, because there is nothing
 * about what they typed to fix. Somebody in no pot at all is refused in the same sentence as a
 * contributor, on purpose — telling a stranger they are not a member would tell them there is a pot
 * here to be a member of.
 *
 * <p><strong>Nothing happens on a refused close</strong>, which is the assertion that makes the
 * status worth having: the pot is still open, still holds what it held, and its members are still
 * in it. A refusal that had settled half of them before changing its mind would be the worst outcome
 * this feature has.
 *
 * <p>Its own application and a pot of its own per test, for the reason every other shared-pot test
 * gives. The clock never moves here, so the methods may run in any order.
 */
class ClosingAPotIsAnOwnersDecisionApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-closing-a-pot-is-an-owners"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** A contributor pays in; what the group does with the pot is not theirs to decide. */
    @Test
    void a_contributor_closing_the_pot_is_refused() {
        SharedPotView pot = aPotWith(BRAM, "CONTRIBUTOR", "Not his to end");

        ResponseEntity<JsonNode> closing = app.tryToClose(pot.id(), app.customerIdOf(BRAM));

        assertThat(closing.getStatusCode())
                .as("understood, from somebody the application knows, and not allowed")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(closing)).isEqualTo("Only an owner may close this pot.");
        nothingHappened(pot);
    }

    /** And a viewer, who cannot even pay into it. */
    @Test
    void a_viewer_closing_the_pot_is_refused() {
        SharedPotView pot = aPotWith(BRAM, "VIEWER", "Watched, not ended");

        ResponseEntity<JsonNode> closing = app.tryToClose(pot.id(), app.customerIdOf(BRAM));

        assertThat(closing.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(closing)).isEqualTo("Only an owner may close this pot.");
        nothingHappened(pot);
    }

    /** Somebody in no pot at all, refused in the very same sentence and for the reason above. */
    @Test
    void somebody_who_is_not_in_the_pot_closing_it_is_refused_in_the_same_words() {
        SharedPotView pot = aPotWith(BRAM, "CONTRIBUTOR", "Nothing to do with them");
        String stranger = app.aCustomerOfItsOwn("a stranger closing a pot");

        ResponseEntity<JsonNode> closing = app.tryToClose(pot.id(), app.customerIdOf(stranger));

        assertThat(closing.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(closing))
                .as("told what would have been needed, and not that there is a pot here to be a "
                        + "member of")
                .isEqualTo("Only an owner may close this pot.");
        nothingHappened(pot);
    }

    /** A customer this application has never heard of, which is an absence rather than a role. */
    @Test
    void a_customer_nobody_has_heard_of_closing_the_pot_is_refused() {
        SharedPotView pot = aPotWith(BRAM, "CONTRIBUTOR", "Closed by nobody");

        ResponseEntity<JsonNode> closing = app.tryToClose(pot.id(), app.anIdNoCustomerHas());

        assertThat(closing.getStatusCode())
                .as("an absence, which is what a 404 says, and not a role they do not hold")
                .isEqualTo(HttpStatus.NOT_FOUND);
        nothingHappened(pot);
    }

    /** A body that never said who was closing it, which is a form that was not filled in. */
    @Test
    void closing_a_pot_without_saying_who_is_doing_it_is_refused() {
        SharedPotView pot = aPotWith(BRAM, "CONTRIBUTOR", "Closed by an empty box");

        Map<String, Object> nobody = new HashMap<>();
        nobody.put("customerId", null);
        ResponseEntity<JsonNode> closing = app.tryToClose(pot.id(), nobody);

        assertThat(closing.getStatusCode())
                .as("a field that was never filled in is a malformed request")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(closing)).isEqualTo("Closing a shared pot needs the customer doing it.");
        nothingHappened(pot);
    }

    /** A pot she owns, with somebody else in it holding the role this test is about. */
    private static SharedPotView aPotWith(String otherMember, String role, String name) {
        SharedPotView pot = app.openAPot(ANKE, name);
        AMemberOfThePot.joins(app, pot.id(), ANKE, otherMember, role);
        app.deposit(pot.savingsAccountId(), ANKE, "40.00");
        return pot;
    }

    /** The pot is exactly as it was: open, holding what it held, with everybody still in it. */
    private static void nothingHappened(SharedPotView pot) {
        SharedPotView afterwards = app.potWith(pot.id());
        assertThat(afterwards.closedAt())
                .as("the pot is still open, because a refused close closes nothing: " + afterwards)
                .isNull();
        assertThat(afterwards.moneyBalance())
                .as("and still holds what it held, because nothing was settled out of it")
                .isEqualByComparingTo("40.00");
        assertThat(afterwards.members()).hasSize(2);
    }

    /** The sentence the customer is shown, which is the whole of what they get to act on. */
    private static String reasonIn(ResponseEntity<JsonNode> refused) {
        assertThat(refused.getBody())
                .as("a refusal answers with a problem body")
                .isNotNull();
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered "
                        + refused.getBody())
                .isTrue();
        return refused.getBody().get("detail").asText();
    }
}
