package io.dataroots.savingstreak.sharedpots;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.sharedpots.PotsAsSomebodyWouldTypeThem.aPotCalled;
import static io.dataroots.savingstreak.sharedpots.PotsAsSomebodyWouldTypeThem.aPotNobodyIsOpening;
import static io.dataroots.savingstreak.sharedpots.PotsAsSomebodyWouldTypeThem.aPotWithNoNameAtAll;
import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A pot this application will not open, and a pot nobody has heard of, each answered in a sentence
 * somebody could read out loud.
 *
 * <p>Every refusal here is checked for three things: the status, that there is a reason at all, and
 * that the reason says something the person could act on. A refusal whose body is empty is a page
 * that can only say "something went wrong", which is the one thing a bank should never say to
 * somebody who has just typed something wrong.
 *
 * <p>The statuses are the distinction this application already draws everywhere else. Something that
 * is not there — a pot, a customer — is a 404; a form to fix is a 400. A name of nothing but spaces
 * is the second of those and not the first: the pot is in no unexpected state, what arrived does not
 * describe a pot, and what the customer does next is type a name.
 *
 * <p><strong>Who is asking is settled before what they asked for.</strong> A request that is wrong
 * about both is answered about the customer, because a page signed in as nobody will go on getting
 * every other request wrong as well — and the test that says so is the only place that order is
 * visible from outside.
 *
 * <p>Its own application, for the reason {@link APotIsOpenedByACustomerWhoBecomesItsOwnerApiTest}
 * gives: the one pot this class opens is opened only to count past.
 */
class APotIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    /**
     * An identifier no pot has: one past the highest that does, and pots are numbered from a single
     * sequence. Worked out once, before any of these tests run, and safe for the whole class because
     * nothing below opens a pot — every test here is about one that could not be opened.
     */
    private static long noSuchPot;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-pot-is-refused"));
        noSuchPot = app.openAPot(ANKE, "Somewhere to count past").id() + 1;
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** A name of nothing but spaces is no name, which is the same rule a customer's own name is held to. */
    @Test
    void a_name_of_nothing_but_spaces_is_refused_as_a_form_to_fix() {
        ResponseEntity<JsonNode> refused =
                app.tryToOpenAPot(aPotCalled("   ", app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(refused))
                .contains("name")
                .contains("pot");
    }

    /**
     * And a form sent with the name box never filled in at all gets the same sentence, because it is
     * the same mistake: having the rule answer both is one fewer place for the wording to drift.
     */
    @Test
    void a_pot_with_no_name_at_all_is_refused_in_the_same_words() {
        ResponseEntity<JsonNode> noName =
                app.tryToOpenAPot(aPotWithNoNameAtAll(app.customerIdOf(ANKE)));
        ResponseEntity<JsonNode> blankName =
                app.tryToOpenAPot(aPotCalled("", app.customerIdOf(ANKE)));

        assertThat(noName.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blankName.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(noName)).isEqualTo(reasonIn(blankName));
    }

    /**
     * A customer this application has never heard of is something that is not there, which is what a
     * 404 says, and the sentence quotes the identifier so a page that asked for the wrong customer
     * can see which one it asked for.
     */
    @Test
    void a_customer_nobody_has_heard_of_cannot_open_a_pot() {
        long nobody = app.anIdNoCustomerHas();

        ResponseEntity<JsonNode> refused = app.tryToOpenAPot(aPotCalled("Kitchen", nobody));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused)).contains(String.valueOf(nobody));
    }

    /**
     * And when both are wrong, it is the customer that is answered about: the order the two checks
     * are made in is a decision, and this is where it is visible.
     */
    @Test
    void a_pot_wrong_about_both_is_answered_about_the_customer() {
        long nobody = app.anIdNoCustomerHas();

        ResponseEntity<JsonNode> refused = app.tryToOpenAPot(aPotCalled("  ", nobody));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused)).contains(String.valueOf(nobody));
    }

    /**
     * A body that says nothing about who is opening the pot is a malformed request rather than one
     * of the module's refusals — the same line the gift endpoint draws between a field that was
     * never filled in and a field filled in with somebody who does not exist.
     */
    @Test
    void a_pot_nobody_is_opening_is_refused_as_a_malformed_request() {
        ResponseEntity<JsonNode> refused = app.tryToOpenAPot(aPotNobodyIsOpening("Kitchen"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(refused)).contains("customer");
    }

    /** Reading a pot that is not there is answered as the absence it is, with the pot named back. */
    @Test
    void a_pot_that_does_not_exist_is_not_found_and_says_so() {
        ResponseEntity<JsonNode> refused = app.tryToReadThePot(noSuchPot);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused)).contains(String.valueOf(noSuchPot));
    }

    /**
     * And so is reading its members, in the same sentence: the membership of a pot that is not there
     * is not an empty list, and answering one would say the pot exists.
     */
    @Test
    void the_members_of_a_pot_that_does_not_exist_are_not_an_empty_list() {
        ResponseEntity<JsonNode> refused = app.tryToReadTheMembers(noSuchPot);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused)).contains(String.valueOf(noSuchPot));
    }

    /**
     * The sentence the customer is shown, insisted on as present: a refusal carries its reason in
     * {@code detail}, and one that did not would be a page able to say only that something went
     * wrong.
     */
    private static String reasonIn(ResponseEntity<JsonNode> refused) {
        assertThat(refused.getBody())
                .as("a refusal answers with a problem body")
                .isNotNull();
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered " + refused.getBody())
                .isTrue();
        return refused.getBody().get("detail").asText();
    }
}
