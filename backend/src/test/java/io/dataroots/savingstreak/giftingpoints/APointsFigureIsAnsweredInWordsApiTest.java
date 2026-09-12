package io.dataroots.savingstreak.giftingpoints;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * <p>There are two hazards here and they are not the same one. A short string with a ruinous
 * exponent is dear to <em>write out</em>, and that is the parameterised test below. A long string of
 * ordinary digits is dear to <em>read in</em>, because parsing is quadratic in the digit count, and
 * that is the test after it. Both end in the same place — a refusal in words, promptly, with a WARN
 * behind it — and neither costs the application anything on the way.
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
     * <p>The three in the middle are the same hazard reached through {@code stripTrailingZeros()},
     * which is where a figure that parsed perfectly well can still fault: it takes a one off the
     * scale per zero it strips, and a scale that walks off the end of an {@code int} throws. Two or
     * more trailing zeroes on top of an exponent that has already bottomed the scale out is what it
     * takes, so they are here alongside the near misses that survive it — {@code 1e2147483647} has
     * no zeroes to strip and {@code 10e2147483647} lands exactly on the boundary — because it is the
     * near misses that make this look covered when it is not.
     *
     * <p>The last is the plain version of the same thing — a whole number of points larger than any
     * pot could count — and it is here so that the branch answering a figure too large to hold is
     * covered by the case that reads like a typo as well as by the ones that read like an attack.
     */
    @ParameterizedTest
    @ValueSource(strings = {"1e999999999", "1.5e-999999999", "-1e-999999999",
            "100e2147483647", "1000e2147483646", "100.00e2147483647",
            "1e2147483647", "10e2147483647", "99999999999999999999"})
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

    /**
     * A figure with more digits in it than anybody could read cheaply. This is the other way the
     * same hazard is reached: not a short string with a ruinous exponent, but a string so long that
     * simply reading it as a number costs more than the whole request is worth. Both {@code new
     * BigDecimal(String)} and {@code stripTrailingZeros()} are quadratic in the digit count, so
     * before this bound existed a two-hundred-thousand-digit figure took ten seconds of a core, a
     * megabyte of digits took minutes, and the only thing to show for it was a shortfall arriving
     * long after whoever asked had given up.
     *
     * <p>Asserted on three properties and no wording. It comes back a bad request; it comes back
     * quickly, which is the property the bound exists for; and the sentence is short, because a
     * refusal that echoes the whole megabyte back is the same waste at the other end. Two hundred
     * thousand digits rather than a million: it is far past any figure a person could mean, and it
     * keeps the failure quick to see if this ever regresses.
     */
    @Test
    void a_figure_longer_than_any_number_of_points_is_refused_without_being_read() {
        String farMoreDigitsThanAnyPotCouldHold = "1" + "0".repeat(200_000);
        long ankeHeld = seeded.pointsBalanceOf(ANKE);
        long bramHeld = seeded.pointsBalanceOf(BRAM);

        Instant asked = Instant.now();
        ResponseEntity<JsonNode> response =
                give(ANKE, seeded.contactDetailsOf(BRAM), farMoreDigitsThanAnyPotCouldHold);
        Duration tookToAnswer = Duration.between(asked, Instant.now());

        assertThat(response.getStatusCode())
                .as("a figure a customer typed is a bad request, never a fault in the application")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tookToAnswer)
                .as("and it is refused on its length rather than read as a number, so it answers at "
                        + "once instead of spending a core on it")
                .isLessThan(Duration.ofSeconds(5));
        assertThat(reasonGivenBy(response))
                .as("in words short enough to read, rather than the whole figure handed back")
                .isNotBlank()
                .hasSizeLessThan(200);
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
