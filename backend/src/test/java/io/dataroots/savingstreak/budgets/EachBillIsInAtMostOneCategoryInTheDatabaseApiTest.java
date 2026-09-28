package io.dataroots.savingstreak.budgets;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One category per bill is a rule the database keeps, not one this application merely intends — and
 * the rule is put there by the same start-up step that makes the standing categories unique.
 *
 * <p>{@link BudgetsService} finds the row and moves it, which is what makes filing a bill twice a
 * move rather than a second label. But the finding and the guarantee are different things. Two
 * requests filing one bill at the same moment would both read the same empty answer and both write,
 * and a bill in two categories would be a bill counted whole in the committed half of two different
 * months — with nothing to say which the customer meant, and every figure the rest of this feature
 * derives inheriting the ambiguity. What serialises writers today is a connection pool of one, which
 * is a property of a configuration file rather than a rule about bills.
 *
 * <p>So this test writes to the table directly, past the service, and insists the write is refused.
 * Which makes it one of the handful in this codebase that reach below the HTTP seam on purpose
 * rather than for want of an endpoint — its subject <em>is</em> the storage — and it is the shape
 * {@code TheStandingCategoriesAreUniqueInTheDatabaseApiTest} beside it already uses. The row it
 * collides with is one the application itself wrote, over HTTP.
 *
 * <p><strong>The write that has to be accepted matters as much as the refusal.</strong> The same
 * bill identifier against another account is not a rival — an identifier is only ever read through
 * the account it is on — and a second bill on the same account is the ordinary case of a household
 * filing everything it pays.
 */
class EachBillIsInAtMostOneCategoryInTheDatabaseApiTest extends ApiIntegrationTest {

    /** Makes this class's customers different from every other class's, for the reason its own do. */
    private static final AtomicLong DISTINCT = new AtomicLong();

    @Autowired
    private CategorisedBillRepository categorisedBills;

    @Test
    void the_index_refuses_a_second_category_for_one_bill_on_one_account() {
        long account = aCurrentAccountOfItsOwn();
        long anotherAccount = aCurrentAccountOfItsOwn();
        long bill = aBillOn(account);
        long housing = aCategoryOn(account, "Housing");
        long utilities = aCategoryOn(account, "Utilities");
        putBillInCategory(account, bill, housing);
        Instant now = Instant.now();

        assertThat(categorisedBills.theBillsAreAlreadyInAtMostOneCategoryEach())
                .as("the uniqueness is in the database file rather than only in this application's "
                        + "intentions, and the start-up step is what put it there")
                .isEqualTo(1);

        assertThatThrownBy(() -> categorisedBills.saveAndFlush(
                new CategorisedBill(account, bill, utilities, now)))
                .as("a second category for one bill, written past the service's own read — which is "
                        + "what two requests filing it at the same moment would do")
                .isInstanceOf(DataAccessException.class)
                // SQLite's own words, and the reason they are asserted on rather than only the
                // exception type: this dialect hands the failure back as a JpaSystemException
                // rather than a DataIntegrityViolationException, so the type alone would also be
                // satisfied by a write that failed for some entirely different reason. The two
                // columns named in the message are the guarantee this test is about.
                .hasMessageContaining("UNIQUE constraint failed")
                .hasMessageContaining("categorised_bill.current_account_id")
                .hasMessageContaining("categorised_bill.bill_id");

        assertThatCode(() -> categorisedBills.saveAndFlush(
                new CategorisedBill(anotherAccount, bill, utilities, now)))
                .as("a bill identifier is only ever read through the account it is on, so the pair "
                        + "is what competes rather than the bill alone")
                .doesNotThrowAnyException();

        assertThatCode(() -> categorisedBills.saveAndFlush(
                new CategorisedBill(account, aBillOn(account), housing, now)))
                .as("and a second bill filed on the same account is the ordinary case of a "
                        + "household describing everything it pays")
                .doesNotThrowAnyException();
    }

    /** Files a bill through the API, so that the row this test collides with is the application's. */
    private void putBillInCategory(long currentAccountId, long billId, long categoryId) {
        ResponseEntity<JsonNode> filed = http.exchange(
                "/api/current-accounts/{account}/bills/{bill}/category", HttpMethod.PUT,
                new HttpEntity<>(Map.of("categoryId", categoryId)), JsonNode.class,
                currentAccountId, billId);
        assertThat(filed.getStatusCode())
                .describedAs("the set-up: the application itself filed this bill, over HTTP")
                .isEqualTo(HttpStatus.OK);
    }

    private long aBillOn(long currentAccountId) {
        ResponseEntity<JsonNode> declared = http.postForEntity(
                "/api/current-accounts/{id}/bills",
                Map.of("name", "Rent " + DISTINCT.incrementAndGet(), "dayOfMonth", "1",
                        "amount", "900.00"),
                JsonNode.class, currentAccountId);
        assertThat(declared.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return declared.getBody().get("billId").asLong();
    }

    private long aCategoryOn(long currentAccountId, String name) {
        ResponseEntity<JsonNode> declared = http.postForEntity(
                "/api/current-accounts/{id}/categories", Map.of("name", name), JsonNode.class,
                currentAccountId);
        assertThat(declared.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return declared.getBody().get("categoryId").asLong();
    }

    /**
     * A customer of this class's own, and their current account. Its own, because this test writes
     * rows to the table that no rule would have written, and they should land on an account nobody
     * else in the run is asserting about.
     */
    private long aCurrentAccountOfItsOwn() {
        long distinct = DISTINCT.incrementAndGet();
        ResponseEntity<JsonNode> added = http.postForEntity("/api/customers",
                Map.of("name", "Filer " + distinct + " for one category per bill",
                        "contactDetails", "one-category-per-bill-" + distinct + "@example.be"),
                JsonNode.class);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class,
                added.getBody().get("id").asLong());
        return accounts.get("currentAccounts").get(0).get("id").asLong();
    }
}
