package io.dataroots.savingstreak.budgets;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One standing category of each name per current account is a rule the database keeps, not one this
 * application merely intends — and the rule is put there by a start-up step that says whether it had
 * to.
 *
 * <p>{@link BudgetsService} also checks in Java, and has to: the check is what produces the sentence
 * a customer reads, naming the category they already have. But the check and the guarantee are
 * different things. Two requests declaring "Groceries" at the same moment would both read the same
 * empty answer and both write, leaving an account with two categories of one name — and every figure
 * this module derives would then have two answers to "what did the groceries cost" with nothing to
 * say which the customer meant. What serialises writers today is a connection pool of one, which is a
 * property of a configuration file rather than a rule about categories.
 *
 * <p>So this test writes to the table directly, past the service and past its check, and insists the
 * write is refused. Which makes it one of the handful in this codebase that reach below the HTTP
 * seam on purpose rather than for want of an endpoint — its subject <em>is</em> the storage — and it
 * is the shape {@code TheOrderOfImportanceIsStrictInTheDatabaseApiTest} and
 * {@code TheRecordOfCreditedIncomeIsUniqueInTheDatabaseApiTest} already use. It is also the one
 * class in this feature that sits in the module's own package rather than in a scenario package,
 * because the repository whose guarantee it is about is package-private, as every row in this
 * module is. The category it collides with is one the application itself declared, over HTTP.
 *
 * <p><strong>The three writes that have to be accepted matter as much as the refusal.</strong> The
 * same word on another account is another household's groceries; another word on the same account is
 * the ordinary case; and the same word on a category that was <em>ended</em> is the whole reason the
 * index is partial — a customer who ended Groceries in March and starts filing groceries again in
 * June declares it afresh, and an index over the pair alone would refuse them.
 */
class TheStandingCategoriesAreUniqueInTheDatabaseApiTest extends ApiIntegrationTest {

    /** Makes this class's customers different from every other class's, for the reason its own do. */
    private static final AtomicLong DISTINCT = new AtomicLong();

    @Autowired
    private SpendingCategoryRepository categories;

    @Test
    void the_index_refuses_a_second_standing_category_of_one_name_on_one_account() {
        long account = aCurrentAccountOfItsOwn();
        long anotherAccount = aCurrentAccountOfItsOwn();
        Instant now = Instant.now();
        assertThat(theNameOfTheCategoryDeclaredOn(account))
                .describedAs("the set-up: the application itself declared this category, over HTTP")
                .isEqualTo("Groceries");

        assertThat(categories.theStandingCategoriesAreAlreadyUniquePerName())
                .as("the uniqueness is in the database file rather than only in this application's "
                        + "intentions, and the start-up step is what put it there")
                .isEqualTo(1);

        assertThatThrownBy(() -> categories.saveAndFlush(
                new SpendingCategory(account, "Groceries", now)))
                .as("a second standing category of the same name on the same account, written past "
                        + "the service's own check — which is what two requests declaring it at the "
                        + "same moment would do")
                .isInstanceOf(DataAccessException.class)
                // SQLite's own words, and the reason they are asserted on rather than only the
                // exception type: this dialect hands the failure back as a JpaSystemException rather
                // than a DataIntegrityViolationException, so the type alone would also be satisfied
                // by a write that failed for some entirely different reason. The two columns named
                // in the message are the guarantee this test is about.
                .hasMessageContaining("UNIQUE constraint failed")
                .hasMessageContaining("spending_category.current_account_id")
                .hasMessageContaining("spending_category.name");

        assertThatCode(() -> categories.saveAndFlush(
                new SpendingCategory(anotherAccount, "Groceries", now)))
                .as("two households both buy groceries, and the index is over the pair rather than "
                        + "over the name alone")
                .doesNotThrowAnyException();

        assertThatCode(() -> categories.saveAndFlush(new SpendingCategory(account, "Fuel", now)))
                .as("and a different word on the same account is the ordinary case")
                .doesNotThrowAnyException();

        assertThatCode(() -> {
            categories.saveAndFlush(endedCategory(account, "Groceries", now));
            categories.saveAndFlush(endedCategory(account, "Groceries", now));
        })
                .as("an ended category is a record of months already gone rather than a word still "
                        + "in use, so the name is free again and two of them are not two categories "
                        + "fighting over one: the index covers the standing rows and no others, "
                        + "which is the right answer rather than a lucky one")
                .doesNotThrowAnyException();
    }

    /** A category that was declared and then ended, so that it is outside the partial index. */
    private static SpendingCategory endedCategory(long currentAccountId, String name, Instant now) {
        SpendingCategory category = new SpendingCategory(currentAccountId, name, now);
        category.end(now);
        return category;
    }

    /**
     * Declares a category through the API, so that the row this test collides with is one the
     * application itself wrote.
     */
    private String theNameOfTheCategoryDeclaredOn(long currentAccountId) {
        ResponseEntity<JsonNode> declared = http.postForEntity(
                "/api/current-accounts/{id}/categories", Map.of("name", "Groceries"),
                JsonNode.class, currentAccountId);
        assertThat(declared.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return declared.getBody().get("name").asText();
    }

    /**
     * A customer of this class's own, and their current account. Its own, because this test writes
     * rows to the categories table that no rule would have written, and they should land on an
     * account nobody else in the run is asserting about.
     */
    private long aCurrentAccountOfItsOwn() {
        long distinct = DISTINCT.incrementAndGet();
        ResponseEntity<JsonNode> added = http.postForEntity("/api/customers",
                Map.of("name", "Spender " + distinct + " for a unique list of categories",
                        "contactDetails", "categories-unique-" + distinct + "@example.be"),
                JsonNode.class);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class,
                added.getBody().get("id").asLong());
        return accounts.get("currentAccounts").get(0).get("id").asLong();
    }
}
