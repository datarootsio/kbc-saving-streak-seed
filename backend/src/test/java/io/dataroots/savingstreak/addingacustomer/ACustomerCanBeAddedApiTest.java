package io.dataroots.savingstreak.addingacustomer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GiftView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer added over the API is a customer in every sense the rest of the application uses the
 * word: they are in the directory, they can sign in, they hold the accounts that let them save, and
 * a gift can be addressed to them.
 *
 * <p>Those four are asserted rather than only the 201, because "created" is the easy half. The
 * reason this endpoint exists at all is that a gift can only go to somebody who banks here, and a
 * customer that was written down but that Gifting cannot find is the exact failure that would leave
 * the feature useless while still passing a test about the status code.
 *
 * <p>Every test here adds somebody under an address of its own. The whole run shares one database,
 * so an address used twice would be the duplicate this feature refuses, and the second test to run
 * would fail on the first one's row.
 *
 * <p>Nothing is asserted about how many customers there are. Other tests in this run add their own,
 * and a count is the one thing about the directory that cannot be true for two tests at once.
 *
 * <p>Nothing seeded is written to either, which is the same rule read from the other side. This
 * class adds customers to a directory the whole run shares, and it sorts near the front of the run,
 * so anything it changes about Anke or Bram is a failure handed to a test that has not run yet and
 * that never mentioned adding a customer. Every customer either end of the gift below is one this
 * test opened.
 */
class ACustomerCanBeAddedApiTest extends ApiIntegrationTest {

    @Test
    void an_added_customer_is_created_and_named_back() {
        ResponseEntity<JsonNode> response = add("Chloe Janssens", "chloe.janssens@example.be");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("name").asText()).isEqualTo("Chloe Janssens");
        assertThat(response.getBody().get("contactDetails").asText())
                .isEqualTo("chloe.janssens@example.be");
        assertThat(response.getBody().get("id").asLong())
                .as("the identifier the caller could not have known, so that the page can select them")
                .isPositive();
    }

    /**
     * The directory is what the gift page reads to build its list of people, so being in it is the
     * whole of what "somebody to give points to" means.
     */
    @Test
    void an_added_customer_appears_among_the_people_who_bank_here() {
        long id = idOf(add("Dries Maes", "dries.maes@example.be"));

        JsonNode everybody = http.getForObject("/api/customers", JsonNode.class);

        assertThat(idsIn(everybody)).contains(id);
    }

    /** Recognised by the address they were added under, which is the only way in this application. */
    @Test
    void an_added_customer_can_sign_in() {
        long id = idOf(add("Els Vermeulen", "els.vermeulen@example.be"));

        ResponseEntity<JsonNode> signedIn = http.postForEntity("/api/customers/sign-in",
                Map.of("contactDetails", "els.vermeulen@example.be"), JsonNode.class);

        assertThat(signedIn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(signedIn.getBody().get("id").asLong()).isEqualTo(id);
    }

    /**
     * A current account with money in it and a savings account to move it into, because a customer
     * who cannot deposit can only ever receive gifts — and every point in this application comes
     * from a deposit.
     */
    @Test
    void an_added_customer_holds_an_account_to_save_from_and_one_to_save_into() {
        long id = idOf(add("Femke Claes", "femke.claes@example.be"));

        JsonNode held = http.getForObject("/api/customers/{id}/accounts", JsonNode.class, id);

        assertThat(held.get("currentAccounts")).hasSize(1);
        assertThat(new BigDecimal(held.get("currentAccounts").get(0).get("balance").asText()))
                .as("something to move into savings")
                .isPositive();
        assertThat(held.get("savingsAccounts")).hasSize(1);
        assertThat(new BigDecimal(held.get("savingsAccounts").get(0).get("moneyBalance").asText()))
                .as("a savings account starts empty, like every other")
                .isEqualByComparingTo("0.00");
        assertThat(held.get("pointsBalance").asLong())
                .as("saved nothing, so earned nothing")
                .isZero();
    }

    /**
     * The account number is made up, and it is a well-formed Belgian IBAN all the same — the two
     * seeded customers were written down with real check digits, and an account this application
     * opens itself should not be the one that fails a validator.
     */
    @Test
    void an_added_customer_holds_a_well_formed_belgian_iban() {
        long id = idOf(add("Gert Willems", "gert.willems@example.be"));

        JsonNode held = http.getForObject("/api/customers/{id}/accounts", JsonNode.class, id);
        String iban = held.get("currentAccounts").get(0).get("iban").asText();

        assertThat(iban).matches("BE\\d{14}");
        assertThat(mod97Of(iban))
                .as("%s does not satisfy the mod-97-10 rule every IBAN shares", iban)
                .isEqualTo(1);
        assertThat(nationalCheckDigitsOf(iban))
                .as("%s does not carry Belgium's own last-two-digits check", iban)
                .isTrue();
    }

    /**
     * The point of the whole feature: an added customer is a full end of a gift. One earns points
     * by saving with the account they were opened with, gives some to the other by the address
     * they were added under, and the gift lands where a gift lands.
     *
     * <p>Both ends are added by this test rather than either being a seeded customer. That is not
     * tidiness: the whole run shares one database, and two existing tests assert that what Anke has
     * earned less what she has claimed is exactly her balance — a sum a gift out of her pot silently
     * falsifies, in whichever test happens to run after this one. Adding both ends touches nothing
     * anybody else is asserting on, and it covers the half that reaching for Anke never did, which
     * is that an added customer can <em>send</em>.
     */
    @Test
    void an_added_customer_can_be_given_points() {
        long sender = idOf(add("Hanne Peeters", "hanne.peeters@example.be"));
        long recipient = idOf(add("Ilse Aerts", "ilse.aerts@example.be"));
        long theyHadNone = pointsOf(recipient);
        earnPoints(sender);

        ResponseEntity<JsonNode> gift = http.postForEntity("/api/customers/{id}/gifts",
                Map.of("recipientContactDetails", "ilse.aerts@example.be", "points", "3"),
                JsonNode.class, sender);

        assertThat(gift.getStatusCode())
                .as("the gift was refused: %s", gift.getBody())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(theyHadNone).isZero();
        assertThat(pointsOf(recipient)).isEqualTo(3);
        GiftView[] theirs = http.getForObject("/api/customers/{id}/gifts", GiftView[].class, recipient);
        assertThat(theirs).hasSize(1);
        assertThat(theirs[0].direction()).isEqualTo("RECEIVED");
        assertThat(theirs[0].senderName()).isEqualTo("Hanne Peeters");
        assertThat(theirs[0].recipientName()).isEqualTo("Ilse Aerts");
        assertThat(theirs[0].points()).isEqualTo(3);
    }

    /** Trimmed on the way in, so a pasted address with a stray space is the address they typed. */
    @Test
    void what_was_typed_is_stored_trimmed() {
        ResponseEntity<JsonNode> response = add("  Ines Smet  ", "  ines.smet@example.be  ");

        assertThat(response.getBody().get("name").asText()).isEqualTo("Ines Smet");
        assertThat(response.getBody().get("contactDetails").asText()).isEqualTo("ines.smet@example.be");
    }

    /**
     * Gives the customer something to gift, the only way this application makes a point: they move
     * EUR 10 of their own opening balance into their own savings account, and a whole euro is a
     * point. Both accounts are the ones they were opened with, so nothing outside this customer is
     * read or written.
     */
    private void earnPoints(long customerId) {
        JsonNode held = http.getForObject("/api/customers/{id}/accounts", JsonNode.class, customerId);
        ResponseEntity<JsonNode> deposit = http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", "10.00",
                        "fromCurrentAccountId", held.get("currentAccounts").get(0).get("id").asLong()),
                JsonNode.class, held.get("savingsAccounts").get(0).get("id").asLong());
        assertThat(deposit.getStatusCode())
                .as("the gift needs points to move, and this is where they come from: %s",
                        deposit.getBody())
                .isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<JsonNode> add(String name, String contactDetails) {
        return http.postForEntity("/api/customers",
                Map.of("name", name, "contactDetails", contactDetails), JsonNode.class);
    }

    private long idOf(ResponseEntity<JsonNode> added) {
        assertThat(added.getStatusCode())
                .as("adding was refused: %s", added.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return added.getBody().get("id").asLong();
    }

    private long pointsOf(long customerId) {
        return http.getForObject("/api/customers/{id}/accounts", JsonNode.class, customerId)
                .get("pointsBalance").asLong();
    }

    private long[] idsIn(JsonNode customers) {
        long[] ids = new long[customers.size()];
        for (int at = 0; at < customers.size(); at++) {
            ids[at] = customers.get(at).get("id").asLong();
        }
        return ids;
    }

    /**
     * The mod-97-10 rule: move the four leading characters to the end, read letters as numbers, and
     * the whole thing modulo 97 is 1 for every valid IBAN there is.
     */
    private int mod97Of(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder asDigits = new StringBuilder();
        for (char character : rearranged.toCharArray()) {
            asDigits.append(Character.isLetter(character)
                    ? String.valueOf(Character.toUpperCase(character) - 'A' + 10)
                    : String.valueOf(character));
        }
        return new BigInteger(asDigits.toString()).mod(BigInteger.valueOf(97)).intValue();
    }

    /** Belgium's own: the last two digits of the account number are the ten before them mod 97. */
    private boolean nationalCheckDigitsOf(String iban) {
        String bban = iban.substring(4);
        int remainder = (int) (Long.parseLong(bban.substring(0, 10)) % 97);
        return Integer.parseInt(bban.substring(10)) == (remainder == 0 ? 97 : remainder);
    }
}
