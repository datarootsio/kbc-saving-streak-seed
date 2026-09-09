package io.dataroots.savingstreak.giftingpoints;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whatever a customer types where the points go, the answer is words and a status — never a server
 * error, and never a sentence that costs more to write than the request was worth.
 *
 * <p>The figure reaches this module as the text that was typed, so that "2.5" is answered as a
 * mistake about points rather than as a request that could not be read. That decision hands the
 * module every string a keyboard can produce, and some of them parse into a figure that is ruinous
 * to write back out: {@code BigDecimal} reads a nine-digit exponent for almost nothing, and it is
 * rendering the parsed figure that costs — a billion characters of {@code toPlainString}, or a
 * {@code toBigInteger} that throws out of the middle of the refusal it was being built for. The
 * figures below are exactly those, and each of them once escaped this endpoint as a 500 or an
 * {@code OutOfMemoryError} with no refusal logged at all.
 *
 * <p>Asserted from outside over HTTP, because the point is what the endpoint answers: a status a
 * page can act on, a sentence a person can read, and both pots exactly as they were. The refusal
 * <em>wording</em> is not this test's business — the sentences are ticket 02's to settle — so it
 * asserts only that there are words and that they quote the characters that were typed, which is
 * the property that keeps the parsed figure out of the answer.
 *
 * <p>The shared application is enough for this, unlike the tests about gifts that go through: a
 * refused gift moves nothing, so nothing here disturbs a database other tests are asserting on.
 */
class APointsFigureIsAnsweredInWordsApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    /**
     * A figure that parses for nothing and prints for a fortune. Each of these is one POST away from
     * any customer, and the answer to all of them is the ordinary refusal.
     *
     * <p>The last is the plain version of the same thing — a whole number of points larger than any
     * pot could count — and it is here so that the branch answering a figure too large to hold is
     * covered by the case that reads like a typo as well as by the ones that read like an attack.
     */
    @ParameterizedTest
    @ValueSource(strings = {"1e999999999", "1.5e-999999999", "-1e-999999999", "99999999999999999999"})
    void a_figure_nobody_could_hold_is_refused_in_words_and_moves_nothing(String typed) {
        long ankeHeld = seeded.pointsBalanceOf(ANKE);
        long bramHeld = seeded.pointsBalanceOf(BRAM);

        ResponseEntity<JsonNode> response = give(ANKE, seeded.contactDetailsOf(BRAM), typed);

        assertThat(response.getStatusCode())
                .as("a figure a customer typed is a bad request, never a fault in the application")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .as("and it comes back as words, quoting what was typed rather than what it parsed to")
                .isNotBlank()
                .contains(typed);
        assertThat(seeded.pointsBalanceOf(ANKE)).isEqualTo(ankeHeld);
        assertThat(seeded.pointsBalanceOf(BRAM)).isEqualTo(bramHeld);
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }

    /**
     * Read as unshaped JSON, because a refusal answers with the reason rather than with a gift:
     * asking for the response as a gift would fail to read it before the status could be looked at.
     */
    private ResponseEntity<JsonNode> give(String senderName, String recipientAsTyped, String points) {
        return http.postForEntity(
                "/api/customers/{id}/gifts",
                Map.of("recipientContactDetails", recipientAsTyped, "points", points),
                JsonNode.class,
                seeded.customerIdOf(senderName));
    }
}
