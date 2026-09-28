package io.dataroots.savingstreak.savingsproducts;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingsAccountOnTheOverviewView;
import io.dataroots.savingstreak.support.SavingsProductView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsproducts.ASaverChoosingAProduct.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every way an account will not be opened, each in a sentence the customer can act on, and each
 * leaving them holding exactly what they held before they pressed the button.
 *
 * <p><strong>Three statuses and three different things to do next.</strong> A product nobody sells
 * is a 404 — check what you typed. A product closed to new accounts is a 409 — that card is real,
 * the bank has stopped signing that agreement, and nothing you typed is wrong. An empty box is a
 * 400 — choose something. The middle one is the distinction worth the most: answering 404 for a
 * closed product would send somebody hunting for a typo in a name printed on the screen they pressed
 * the button from.
 *
 * <p><strong>The closed product is put back before the test ends.</strong> Closing is the one thing
 * an administrator does here that can be undone — that is what makes it a flag rather than a
 * lifecycle — so this test can borrow the shared catalogue for a moment and hand it back, in a
 * {@code finally} so that a failed assertion hands it back too. Publishing could not be borrowed
 * this way, which is why the tests that publish boot an application of their own.
 */
class OpeningASavingsAccountIsRefusedInWordsApiTest extends ApiIntegrationTest {

    /**
     * A product code nobody will ever seed, so that "there is no such thing" is a claim about the
     * catalogue rather than about which tests happened to run first.
     */
    private static final String NOTHING_THIS_BANK_SELLS = "SPACESHIP";

    @Test
    void a_product_this_bank_does_not_sell_is_refused_as_not_found() {
        ASaverChoosingAProduct saver = aSaver("somebody naming a product that is not there");
        int held = saver.savingsAccounts().size();

        ResponseEntity<JsonNode> refused = saver.tryToOpen(NOTHING_THIS_BANK_SELLS);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(refused))
                .isEqualTo("There is no savings product called " + NOTHING_THIS_BANK_SELLS + ".");
        assertThat(saver.savingsAccounts()).as("nothing was opened").hasSize(held);
    }

    /**
     * A closed product is a conflict, in a sentence that says the accounts already on it are
     * untouched — because the customer reading it may well be holding one.
     */
    @Test
    void a_product_closed_to_new_accounts_is_refused_as_a_conflict() {
        ASaverChoosingAProduct saver = aSaver("somebody choosing a retired product");
        int held = saver.savingsAccounts().size();

        ResponseEntity<JsonNode> refused;
        closeToNewAccounts("FIXED12");
        try {
            refused = saver.tryToOpen("FIXED12");
        } finally {
            reopenToNewAccounts("FIXED12");
        }

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .isEqualTo("Twelve-month fixed is closed to new accounts, so no account can be "
                        + "opened on it. The accounts already on it carry on exactly as they were.");
        assertThat(saver.savingsAccounts()).as("nothing was opened").hasSize(held);
    }

    /** And it is a conflict rather than an absence: the product is still in the catalogue, closed. */
    @Test
    void the_product_that_was_refused_is_still_in_the_catalogue() {
        SavingsProductView whileOpen = product("CORE");

        SavingsProductView whileClosed;
        closeToNewAccounts("CORE");
        try {
            whileClosed = product("CORE");
        } finally {
            reopenToNewAccounts("CORE");
        }

        assertThat(whileClosed.openToNewAccounts()).isFalse();
        assertThat(whileClosed.name()).isEqualTo(whileOpen.name());
        assertThat(whileClosed.currentTerms()).isEqualTo(whileOpen.currentTerms());
    }

    /**
     * Choosing nothing at all is a form to finish rather than a thing that is not there.
     *
     * <p>All three shapes of "nothing" — no field, an empty string, a string of spaces — because a
     * page with a select that has never been touched sends one of them and nobody can say which.
     * Blank counts as absent for the reason a version with no line about what changed does: a space
     * is what a required box gets filled with by somebody who has decided the rule does not apply.
     */
    @Test
    void choosing_nothing_at_all_is_refused_as_a_form_to_finish() {
        ASaverChoosingAProduct saver = aSaver("somebody who chose nothing");
        int held = saver.savingsAccounts().size();

        for (Object nothing : nothingAtAll()) {
            ResponseEntity<JsonNode> refused = saver.tryToOpen(nothing);

            assertThat(refused.getStatusCode()).as("choosing %s", nothing)
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(reasonGivenBy(refused))
                    .isEqualTo("Choose the savings product to open the account on.");
        }
        assertThat(saver.savingsAccounts()).as("nothing was opened").hasSize(held);
    }

    /**
     * A customer nobody has heard of is told so in the words Accounts owns for an absent customer —
     * the same sentence their overview would answer with for the same identifier.
     */
    @Test
    void a_customer_nobody_has_heard_of_cannot_open_anything() {
        long noSuchCustomer = oneMoreThanTheHighestCustomer();

        ResponseEntity<JsonNode> refused = http.postForEntity("/api/customers/{id}/savings-accounts",
                Map.of("product", "INSTANT"), JsonNode.class, noSuchCustomer);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(refused))
                .isEqualTo("There is no customer " + noSuchCustomer + ".");
    }

    /**
     * And a refused opening leaves the catalogue exactly as it was, which is the claim that makes
     * every assertion above about "nothing was opened" worth making.
     */
    @Test
    void a_refusal_writes_nothing_anywhere() {
        ASaverChoosingAProduct saver = aSaver("somebody whose choice went nowhere");
        List<SavingsAccountOnTheOverviewView> before = saver.savingsAccounts();

        assertThat(saver.tryToOpen(NOTHING_THIS_BANK_SELLS).getStatusCode()).isNotEqualTo(
                HttpStatus.CREATED);

        assertThat(saver.savingsAccounts()).isEqualTo(before);
    }

    /**
     * The three shapes of "nothing was chosen" a page can actually send. A field left out of the
     * body altogether arrives as a null and is the first of them; the other two are a box somebody
     * cleared and a box somebody put a space in.
     */
    private static List<Object> nothingAtAll() {
        return Arrays.asList(null, "", "   ");
    }

    private void closeToNewAccounts(String code) {
        theDoorToNewAccounts(code, "close");
    }

    private void reopenToNewAccounts(String code) {
        theDoorToNewAccounts(code, "reopen");
    }

    private void theDoorToNewAccounts(String code, String door) {
        ResponseEntity<SavingsProductView> answered = http.postForEntity(
                "/api/admin/savings-products/{code}/" + door, null, SavingsProductView.class, code);
        assertThat(answered.getStatusCode()).as("%s %s to new accounts", door, code)
                .isEqualTo(HttpStatus.OK);
    }

    private SavingsProductView product(String code) {
        return http.getForObject("/api/savings-products/{code}", SavingsProductView.class, code);
    }

    /** An identifier no customer has: one past the highest that does. */
    private long oneMoreThanTheHighestCustomer() {
        return List.of(http.getForObject("/api/customers", CustomerView[].class)).stream()
                .mapToLong(CustomerView::id)
                .max()
                .orElse(0L) + 1;
    }

    private ASaverChoosingAProduct aSaver(String whoTheyAre) {
        return new ASaverChoosingAProduct(http, whoTheyAre);
    }

    private record CustomerView(Long id, String name, String contactDetails) {
    }
}
