package io.dataroots.savingstreak.addingacustomer;

import java.util.HashMap;
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
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exactly three things are refused, each in words the person who typed them can act on, and a
 * refused attempt leaves the directory exactly as it was.
 *
 * <p>The directory is asserted alongside every refusal, because a customer half-opened is the one
 * failure that could not be tidied up afterwards: the row is saved before the accounts are, so a
 * refusal thrown after the save would leave somebody in the list of people to give points to who
 * holds nothing to save with. Only the transaction rolling back keeps that from happening, and the
 * duplicate test is where it is really asked — the other two are refused before anything is
 * written at all.
 *
 * <p>The duplicate is asserted against a seeded customer rather than against one this test adds,
 * so that nothing here writes anything: the whole run shares one database, and a test about
 * refusals should be the one test that cannot disturb another.
 */
class AddingACustomerIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    /**
     * A name of nothing at all and a name of spaces are the same mistake, because the rule reads
     * the trimmed text — and a person in a list of people needs something to be called.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void a_customer_with_no_name_is_refused(String noName) {
        int before = howManyBankHere();

        ResponseEntity<JsonNode> response = add(noName, "nobody.yet@example.be");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).isEqualTo("Fill in the name of the person to add.");
        assertThat(howManyBankHere()).isEqualTo(before);
    }

    /** A name missing from the body altogether is the same mistake, and gets the same sentence. */
    @Test
    void a_customer_with_no_name_field_at_all_is_refused() {
        int before = howManyBankHere();

        ResponseEntity<JsonNode> response = post(bodyOf(null, "nobody.yet@example.be"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).isEqualTo("Fill in the name of the person to add.");
        assertThat(howManyBankHere()).isEqualTo(before);
    }

    /**
     * No address is refused rather than allowed and left blank: the address is what a customer
     * signs in with and how a gift names them, so one without it could be created and never
     * reached. The sentence names them, because by then the name is the one thing that was typed.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void a_customer_with_no_contact_details_is_refused(String noAddress) {
        int before = howManyBankHere();

        ResponseEntity<JsonNode> response = add("Jonas Peeters", noAddress);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .isEqualTo("Fill in the email address Jonas Peeters will bank with.");
        assertThat(howManyBankHere()).isEqualTo(before);
    }

    /**
     * An address somebody already banks under. A conflict rather than a bad request: what was typed
     * is fine, and telling somebody to correct an address that needs no correcting would send them
     * looking for a mistake they did not make.
     *
     * <p>The sentence names who is already there, which is what makes it actionable — the person
     * adding a colleague finds out that the colleague is already here.
     */
    @Test
    void a_customer_under_an_address_somebody_already_banks_under_is_refused() {
        int before = howManyBankHere();

        ResponseEntity<JsonNode> response = add("Someone Else", seeded.contactDetailsOf(ANKE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(response))
                .isEqualTo(ANKE + " already banks here under that email address.");
        assertThat(howManyBankHere())
                .as("a refused customer leaves no half-opened row behind")
                .isEqualTo(before);
    }

    /**
     * The same address in different case is the same address, because that is how signing in finds
     * somebody. Allowing it would open a second customer that signing in could never reach past
     * the first.
     */
    @Test
    void the_same_address_in_a_different_case_is_the_same_address() {
        int before = howManyBankHere();

        ResponseEntity<JsonNode> response =
                add("Someone Else", seeded.contactDetailsOf(ANKE).toUpperCase());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(response))
                .isEqualTo(ANKE + " already banks here under that email address.");
        assertThat(howManyBankHere()).isEqualTo(before);
    }

    /** An address with a stray space around it is the same address too, because both are trimmed. */
    @Test
    void an_address_that_only_differs_by_surrounding_space_is_the_same_address() {
        int before = howManyBankHere();

        ResponseEntity<JsonNode> response =
                add("Someone Else", "  " + seeded.contactDetailsOf(ANKE) + "  ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(howManyBankHere()).isEqualTo(before);
    }

    private ResponseEntity<JsonNode> add(String name, String contactDetails) {
        return post(bodyOf(name, contactDetails));
    }

    private ResponseEntity<JsonNode> post(Map<String, String> body) {
        return http.postForEntity("/api/customers", body, JsonNode.class);
    }

    /** A map that tolerates a null, which {@link Map#of} does not, for the missing-field case. */
    private Map<String, String> bodyOf(String name, String contactDetails) {
        Map<String, String> body = new HashMap<>();
        body.put("name", name);
        body.put("contactDetails", contactDetails);
        return body;
    }

    private int howManyBankHere() {
        return http.getForObject("/api/customers", JsonNode.class).size();
    }

    /** The reason, from the one field every refusal in this application carries it in. */
    private String reasonGivenBy(ResponseEntity<JsonNode> response) {
        return response.getBody().get("detail").asText();
    }
}
