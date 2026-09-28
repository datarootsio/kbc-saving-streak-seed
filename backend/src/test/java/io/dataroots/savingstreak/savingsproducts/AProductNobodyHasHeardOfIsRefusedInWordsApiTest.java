package io.dataroots.savingstreak.savingsproducts;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingsProductView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A product the bank does not sell is refused in words, and a product it does sell cannot be
 * written to at all.
 *
 * <p>Two rules in one file because they are the same promise from opposite sides: the catalogue
 * answers about what exists, and nothing answers a request to change it. A published set of terms
 * is an agreement somebody's money will be living under, and this application offers no door to
 * edit one — so the assertion is about the surface rather than about a rule inside it, which is the
 * only way a test at this seam can state "there is no way to do that".
 */
class AProductNobodyHasHeardOfIsRefusedInWordsApiTest extends ApiIntegrationTest {

    /** A code the bank has never sold, and is not one letter away from one it has. */
    private static final String NOT_A_PRODUCT = "SUPER_SAVER_9000";

    /**
     * Asking for a product nobody has heard of is a mistake about what, not about how, and it is
     * reported as one — with the code that arrived quoted back, because a page open since before a
     * release is how this actually happens and the code is the only part of it anybody can act on.
     */
    @Test
    void a_product_the_bank_does_not_sell_is_not_found() {
        ResponseEntity<JsonNode> response = http.getForEntity(
                "/api/savings-products/{code}", JsonNode.class, NOT_A_PRODUCT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(response)).contains(NOT_A_PRODUCT);
    }

    /** The history of a product that does not exist is refused in the same words, for the same reason. */
    @Test
    void the_versions_of_a_product_the_bank_does_not_sell_are_not_found() {
        ResponseEntity<JsonNode> response = http.getForEntity(
                "/api/savings-products/{code}/versions", JsonNode.class, NOT_A_PRODUCT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(response)).contains(NOT_A_PRODUCT);
    }

    /** And the refusal changed nothing: the shelf is the same four products it was. */
    @Test
    void a_refusal_leaves_the_shelf_exactly_as_it_was() {
        List<SavingsProductView> before = shelf();

        http.getForEntity("/api/savings-products/{code}", JsonNode.class, NOT_A_PRODUCT);

        assertThat(shelf()).containsExactlyElementsOf(before);
    }

    /**
     * There is no way to publish, edit or withdraw a product over this API, and that is the whole
     * of the promise that a published version is never rewritten.
     *
     * <p>Asserted as method-not-allowed on every verb but GET, on both the product and its history,
     * because at this seam "cannot be edited" is a statement about the doors that exist. A later
     * ticket opens an administration door that publishes the <em>next</em> version; it will not
     * open one that changes this one, and the day somebody adds a PUT here this test is what says
     * so.
     */
    @Test
    void a_published_product_and_its_versions_cannot_be_written_to() {
        for (String path : List.of("/api/savings-products", "/api/savings-products/INSTANT",
                "/api/savings-products/INSTANT/versions")) {
            for (HttpMethod verb : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)) {
                assertThat(http.exchange(path, verb, null, JsonNode.class).getStatusCode())
                        .as("%s %s", verb, path)
                        .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            }
        }
    }

    private List<SavingsProductView> shelf() {
        return List.of(http.getForObject("/api/savings-products", SavingsProductView[].class));
    }

    /** The sentence the domain wrote, carried into the problem detail untouched. */
    private static String reasonGivenBy(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody()).isNotNull();
        return response.getBody().path("detail").asText();
    }
}
