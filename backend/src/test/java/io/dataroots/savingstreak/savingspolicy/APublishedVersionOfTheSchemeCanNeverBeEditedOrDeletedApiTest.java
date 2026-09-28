package io.dataroots.savingstreak.savingspolicy;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SchemeView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A published version of the scheme can never be edited and never be deleted — because there is no
 * door that could do either.
 *
 * <p><strong>The assertion is about the surface rather than about a rule inside it, and that is the
 * point.</strong> A check that refuses an edit is a check somebody can loosen; a handler that was
 * never written cannot be. A scheme somebody's week was judged under cannot be rewritten behind
 * them, and the only enforceable form that promise has at this seam is the absence of a verb. So
 * what is asserted here is every address and every verb somebody would reach for, and that not one
 * of them does anything.
 *
 * <p><strong>The one door that does exist is the one that adds.</strong> {@code POST
 * /api/admin/scheme/versions} publishes the next version and is deliberately left out of the list
 * below; a version announced for next Monday is taken back by publishing another one for that same
 * Monday, which wins by having the higher number, and that is the whole of the correction surface.
 * The reason there is no edit door is that superseding does the job without one.
 *
 * <p><strong>Two statuses are accepted and no third.</strong> A path Spring has a pattern for
 * answers 405 and one it has no pattern for answers 404, and which of those a given address lands
 * on is a fact about how the handlers happen to be written rather than about the promise. What the
 * promise says is that nothing succeeds, so that is what is asserted: never a 2xx, and never a
 * status that suggests the request was understood and merely declined for a reason that could
 * change.
 *
 * <p>On the run's shared application, because nothing here writes anything — which is the whole
 * claim. The scheme is read before and after all the same, because a test asserting that nothing
 * happened and not checking is a test asserting its own opinion.
 */
class APublishedVersionOfTheSchemeCanNeverBeEditedOrDeletedApiTest extends ApiIntegrationTest {

    /**
     * The doors somebody would go looking for, none of which exists.
     *
     * <p>{@code /versions/1} is in the list because it is the address a REST-shaped guess produces:
     * a version has a number, the number looks like an identifier, and PUT-at-an-identifier is what
     * somebody writes when they have not read why this module exists. There is no handler under it,
     * and there never will be.
     */
    private static final List<String> ADDRESSES_NOBODY_BUILT = List.of(
            "/api/admin/scheme",
            "/api/admin/scheme/versions/1",
            "/api/scheme",
            "/api/scheme/versions",
            "/api/scheme/versions/1");

    /** Everything but the one verb that does exist, which is the POST that publishes. */
    private static final List<HttpMethod> THE_VERBS_THAT_WOULD_CHANGE_SOMETHING =
            List.of(HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    /** Not one way of editing or deleting a version of the scheme reaches anything. */
    @Test
    void no_verb_that_would_change_or_remove_a_version_of_the_scheme_reaches_anything() {
        for (String address : ADDRESSES_NOBODY_BUILT) {
            for (HttpMethod verb : THE_VERBS_THAT_WOULD_CHANGE_SOMETHING) {
                ResponseEntity<JsonNode> answered =
                        http.exchange(address, verb, null, JsonNode.class);

                assertThat(answered.getStatusCode())
                        .as("%s %s", verb, address)
                        .isIn(HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED);
            }
        }
    }

    /**
     * The publishing door itself takes nothing but a POST, so a page reaching for a PUT at it lands
     * nowhere either.
     *
     * <p>Its own test because the address is the one address here that does exist, and "there is a
     * door and it only opens one way" is a different claim from "there is no door".
     */
    @Test
    void the_door_that_publishes_a_version_cannot_be_used_to_change_or_remove_one() {
        for (HttpMethod verb : THE_VERBS_THAT_WOULD_CHANGE_SOMETHING) {
            ResponseEntity<JsonNode> answered =
                    http.exchange("/api/admin/scheme/versions", verb, null, JsonNode.class);

            assertThat(answered.getStatusCode())
                    .as("%s /api/admin/scheme/versions", verb)
                    .isIn(HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED);
        }
    }

    /**
     * And after all of that, the scheme is exactly the scheme it was — the version in force and the
     * whole history, word for word.
     *
     * <p>Read before and after in one test rather than trusted, because "nothing happened" is the
     * claim and a claim nobody checks is a comment.
     */
    @Test
    void the_scheme_and_every_version_of_it_is_exactly_what_it_was_afterwards() {
        SchemeView inForceBefore = theSchemeInForce();
        List<SchemeView> historyBefore = everyVersionPublished();

        for (String address : ADDRESSES_NOBODY_BUILT) {
            for (HttpMethod verb : THE_VERBS_THAT_WOULD_CHANGE_SOMETHING) {
                http.exchange(address, verb, null, JsonNode.class);
            }
        }

        assertThat(theSchemeInForce()).isEqualTo(inForceBefore);
        assertThat(everyVersionPublished()).isEqualTo(historyBefore);
    }

    private SchemeView theSchemeInForce() {
        return http.getForObject("/api/scheme", SchemeView.class);
    }

    private List<SchemeView> everyVersionPublished() {
        return List.of(http.getForObject("/api/scheme/versions", SchemeView[].class));
    }
}
