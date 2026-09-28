package io.dataroots.savingstreak.sharedpots;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every pot a customer belongs to, and what they are to each of them — the list somebody opens to
 * find the one they are looking for.
 *
 * <p>It is a list of <em>memberships</em> rather than of pots somebody made, and the difference is
 * the whole feature: a pot is in this list because the customer belongs to it, so a pot they were
 * invited into will be in it and a pot they have left will not. Today the only way to belong to one
 * is to have opened it, which is why this class can only assert the half of that rule that exists —
 * and why it asserts as hard as it can that somebody else's pot is not in your list.
 *
 * <p>An empty list and a refusal are told apart, the line every per-customer read in this
 * application draws: "you are in no pots" and "there is nobody here by that number" are different
 * sentences to whoever is reading, and a made-up identifier answered with an empty list would say
 * that customer exists.
 *
 * <p>Its own application, for the reason {@link APotIsOpenedByACustomerWhoBecomesItsOwnerApiTest}
 * gives — and a customer of its own in every test, because a list asserted whole is a claim about
 * everything that customer belongs to, and the tests in this class share one application. Asked
 * about the seeded Anke, each of them would be asserting against whatever order they happened to
 * run in.
 */
class ACustomerSeesEveryPotTheyBelongToApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-pots-somebody-is-in"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * A customer who has opened two pots reads both of them back, oldest first, each one whole: its
     * name, what it holds, and the membership that says what they are to it.
     *
     * <p>Both are asserted in one test because the order is a claim about the pair, and a test that
     * opened one pot could not make it.
     */
    @Test
    void every_pot_the_customer_belongs_to_is_in_their_list_oldest_first() {
        String saver = app.aCustomerOfItsOwn("pots to list");
        SharedPotView kitchen = app.openAPot(saver, "Kitchen");
        SharedPotView holiday = app.openAPot(saver, "Greece 2027");

        List<SharedPotView> theirs = app.potsOf(saver);

        assertThat(theirs).containsExactly(kitchen, holiday);
        assertThat(theirs).allSatisfy(pot -> assertThat(pot.members())
                .as("a pot in somebody's list says what they are to it")
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.customerId()).isEqualTo(app.customerIdOf(saver));
                    assertThat(member.role()).isEqualTo("OWNER");
                }));
    }

    /**
     * And somebody else's pot is not in your list. A pot is not a thing this application shows to
     * whoever asks: it is shown to the people in it, and until there is a way in, everybody but the
     * customer who opened it is outside.
     */
    @Test
    void a_customer_who_belongs_to_no_pots_is_answered_with_an_empty_list() {
        String saver = app.aCustomerOfItsOwn("a pot of their own");
        String somebodyElse = app.aCustomerOfItsOwn("no pots at all");
        app.openAPot(saver, "New bike");

        assertThat(app.potsOf(somebodyElse))
                .as("an empty list is an answer: they are in none of them")
                .isEmpty();
    }

    /**
     * A customer nobody has heard of is refused and told why, with the identifier quoted back so
     * that a page which asked for the wrong customer can see which one it asked for. That is the
     * sentence every per-customer read of this application refuses in.
     */
    @Test
    void a_customer_nobody_has_heard_of_is_refused_with_the_reason() {
        long nobody = app.anIdNoCustomerHas();

        ResponseEntity<JsonNode> refused = app.tryToReadThePotsOf(nobody);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered " + refused.getBody())
                .isTrue();
        assertThat(refused.getBody().get("detail").asText()).contains(String.valueOf(nobody));
    }
}
