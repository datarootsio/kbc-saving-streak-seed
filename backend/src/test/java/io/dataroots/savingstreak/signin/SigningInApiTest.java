package io.dataroots.savingstreak.signin;

import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer says which address they bank under and is told who that is, which is where every other
 * thing this application does starts from: the customer that comes back is the one whose accounts
 * the next request asks for.
 *
 * <p>Recognising somebody is not letting them in, and none of these tests pretends otherwise. There
 * is no password to get wrong and nothing that stops the request after this one from naming an
 * account belonging to somebody else — asserted below, so that nobody reads a sign-in screen as
 * proof that this application has authentication.
 */
class SigningInApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    record CustomerView(Long id, String name, String contactDetails) {
    }

    record AccountsView(List<Object> currentAccounts, List<Object> savingsAccounts) {
    }

    @Test
    void signing_in_with_an_address_a_customer_banks_under_answers_with_that_customer() {
        ResponseEntity<CustomerView> response = signIn(seeded.contactDetailsOf(ANKE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo(ANKE);
    }

    /**
     * Somebody typing their own address types it from memory. A capital letter or a space carried in
     * from a paste is not a different person, and being turned away over one would be this
     * application refusing to recognise a customer it recognises perfectly well.
     */
    @Test
    void an_address_is_recognised_however_it_was_typed() {
        String asSeeded = seeded.contactDetailsOf(ANKE);

        ResponseEntity<CustomerView> response = signIn("  " + asSeeded.toUpperCase() + "  ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo(ANKE);
    }

    @Test
    void an_address_nobody_banks_under_is_not_found() {
        ResponseEntity<String> response = signIn("nobody@example.be", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("No customer banks here under that email address.");
    }

    @Test
    void signing_in_without_filling_anything_in_is_refused_with_something_to_do_about_it() {
        ResponseEntity<String> response = signIn("   ", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Fill in the email address you bank with.");
    }

    /**
     * The customer that comes back is the one the rest of the API is asked about, so signing in
     * genuinely leads somewhere rather than only answering a name.
     */
    @Test
    void the_customer_that_comes_back_is_the_one_whose_accounts_can_then_be_read() {
        CustomerView signedIn = signIn(seeded.contactDetailsOf(ANKE)).getBody();

        ResponseEntity<AccountsView> accounts = http.getForEntity(
                "/api/customers/{id}/accounts", AccountsView.class, signedIn.id());

        assertThat(accounts.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accounts.getBody().currentAccounts()).isNotEmpty();
        assertThat(accounts.getBody().savingsAccounts()).isNotEmpty();
    }

    /**
     * Signing in as one customer does not stop anybody reading another customer's accounts, and this
     * test says so out loud. The sign-in screen is a way in, not a wall: nothing is carried from
     * this request to the next, and nothing checks who is asking. An authentication slice is what
     * would make this test fail, and it should — by then it is asserting the wrong thing.
     */
    @Test
    void signing_in_as_one_customer_guards_nothing_belonging_to_another() {
        signIn(seeded.contactDetailsOf(ANKE));

        ResponseEntity<AccountsView> somebodyElses = http.getForEntity(
                "/api/customers/{id}/accounts", AccountsView.class,
                signIn(seeded.contactDetailsOf(SeededAccounts.BRAM)).getBody().id());

        assertThat(somebodyElses.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<CustomerView> signIn(String contactDetails) {
        return signIn(contactDetails, CustomerView.class);
    }

    private <T> ResponseEntity<T> signIn(String contactDetails, Class<T> answer) {
        return http.postForEntity(
                "/api/customers/sign-in", Map.of("contactDetails", contactDetails), answer);
    }
}
