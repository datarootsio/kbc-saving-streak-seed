package io.dataroots.savingstreak.savingsproducts;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingsProductView;
import io.dataroots.savingstreak.support.TermsVersionView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A version that has been published can never be edited, and nothing about a savings product can
 * ever be deleted — because there is no door that could do either.
 *
 * <p><strong>The assertion is about the surface rather than about a rule inside it, and that is the
 * point.</strong> A check that refuses an edit is a check somebody can loosen; a handler that was
 * never written cannot be. So what is asserted here is every address and every verb somebody would
 * reach for — editing a product, editing a version, deleting one, deleting the other — and that not
 * one of them does anything. That is the strongest form this promise has at this seam, and it is
 * the form that keeps being true as the module grows: the day somebody adds a PUT, this file is
 * what says so.
 *
 * <p><strong>Two statuses are accepted and no third.</strong> A path Spring has a pattern for
 * answers 405 and one it has no pattern for answers 404, and which of those a given address lands
 * on is a fact about how the handlers happen to be written rather than about the promise. What the
 * promise says is that nothing succeeds, so that is what is asserted: never a 2xx, and never a
 * status that suggests the request was understood and merely declined for a reason that could
 * change.
 *
 * <p>On the run's shared application, because nothing here writes anything — which is the whole
 * claim. The catalogue is read before and after all the same, because a test asserting that nothing
 * happened and not checking is a test asserting its own opinion.
 */
class APublishedVersionCanNeverBeEditedOrDeletedApiTest extends ApiIntegrationTest {

    /** The doors somebody would go looking for, none of which exists. */
    private static final List<String> ADDRESSES_NOBODY_BUILT = List.of(
            "/api/admin/savings-products",
            "/api/admin/savings-products/INSTANT",
            "/api/admin/savings-products/INSTANT/versions",
            "/api/admin/savings-products/INSTANT/versions/1",
            "/api/admin/savings-products/INSTANT/versions/2");

    /** Everything but the two verbs that do exist, which are the POSTs that publish and close. */
    private static final List<HttpMethod> THE_VERBS_THAT_WOULD_CHANGE_SOMETHING =
            List.of(HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    /**
     * Not one of the ways of editing or deleting a version reaches anything, on the administration
     * surface or on the customer's.
     *
     * <p>{@code /versions/1} and {@code /versions/2} are in the list because they are the addresses
     * a REST-shaped guess produces: a version has a number, the number looks like an identifier,
     * and PUT-at-an-identifier is what somebody writes when they have not read why this module
     * exists. There is no handler under either, and there never will be.
     */
    @Test
    void no_verb_that_would_change_or_remove_a_product_or_a_version_reaches_anything() {
        for (String address : ADDRESSES_NOBODY_BUILT) {
            for (HttpMethod verb : THE_VERBS_THAT_WOULD_CHANGE_SOMETHING) {
                ResponseEntity<JsonNode> answered = http.exchange(address, verb, null,
                        JsonNode.class);

                assertThat(answered.getStatusCode())
                        .as("%s %s", verb, address)
                        .isIn(HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED);
            }
        }
    }

    /**
     * The customer's own addresses are not a way in either — the same rule from the other side, and
     * the one a page that has been open too long would actually reach for.
     */
    @Test
    void the_customers_own_product_addresses_cannot_be_written_to_either() {
        for (String address : List.of("/api/savings-products/INSTANT",
                "/api/savings-products/INSTANT/versions",
                "/api/savings-products/INSTANT/versions/1")) {
            for (HttpMethod verb : THE_VERBS_THAT_WOULD_CHANGE_SOMETHING) {
                ResponseEntity<JsonNode> answered = http.exchange(address, verb, null,
                        JsonNode.class);

                assertThat(answered.getStatusCode())
                        .as("%s %s", verb, address)
                        .isIn(HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED);
            }
        }
    }

    /**
     * And after all of that, the catalogue is exactly the catalogue it was: the same four products
     * saying the same things, and free savings' two versions word for word.
     *
     * <p>Read before and after in one test rather than trusted, because "nothing happened" is the
     * claim and a claim nobody checks is a comment.
     */
    @Test
    void every_product_and_every_version_is_exactly_what_it_was_afterwards() {
        List<SavingsProductView> shelfBefore = shelf();
        List<TermsVersionView> historyBefore = versionsOfFreeSavings();

        for (String address : ADDRESSES_NOBODY_BUILT) {
            for (HttpMethod verb : THE_VERBS_THAT_WOULD_CHANGE_SOMETHING) {
                http.exchange(address, verb, null, JsonNode.class);
            }
        }

        assertThat(shelf()).isEqualTo(shelfBefore);
        assertThat(versionsOfFreeSavings()).isEqualTo(historyBefore);
    }

    private List<SavingsProductView> shelf() {
        return List.of(http.getForObject("/api/savings-products", SavingsProductView[].class));
    }

    private List<TermsVersionView> versionsOfFreeSavings() {
        return List.of(http.getForObject("/api/savings-products/{code}/versions",
                TermsVersionView[].class, "INSTANT"));
    }
}
