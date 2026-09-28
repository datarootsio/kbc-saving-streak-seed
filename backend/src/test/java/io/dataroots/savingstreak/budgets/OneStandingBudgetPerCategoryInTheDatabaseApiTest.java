package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
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
 * One standing budget per category is a rule the database keeps, not one this application merely
 * intends — and the rule is put there by the same start-up step that makes the standing categories
 * unique and each bill filed once.
 *
 * <p>{@link BudgetsService} finds the standing row and supersedes it, which is what makes declaring
 * a second figure a change rather than a duplicate. But the finding and the guarantee are different
 * things. Two requests naming a figure on one category at the same moment would both read the same
 * standing row and both write, and a category with two budgets in force would be two answers to
 * "what is this allowed to cost" with nothing to say which the customer meant — and every month
 * derived from it, and every month of the carry chain the next slice builds on top, would inherit
 * the ambiguity. What serialises writers today is a connection pool of one, which is a property of a
 * configuration file rather than a rule about budgets.
 *
 * <p>So this test writes to the table directly, past the service, and insists the write is refused.
 * Which makes it one of the handful in this codebase that reach below the HTTP seam on purpose
 * rather than for want of an endpoint — its subject <em>is</em> the storage — and it is the shape
 * the two tests beside it already use. The row it collides with is one the application itself wrote,
 * over HTTP.
 *
 * <p><strong>The writes that have to be accepted matter as much as the refusal.</strong> A figure
 * that has stood down is deliberately outside the index, because a category legitimately carries as
 * many settled rows as its holder has changed their mind — including two sharing a first month, when
 * a figure was named and replaced inside one month. An index over the account and the category alone
 * would refuse a customer that second change of mind, which is not a rule anybody meant to make.
 */
class OneStandingBudgetPerCategoryInTheDatabaseApiTest extends ApiIntegrationTest {

    /** Makes this class's customers different from every other class's, for the reason its own do. */
    private static final AtomicLong DISTINCT = new AtomicLong();

    @Autowired
    private MonthlyBudgetRepository budgets;

    @Test
    void the_index_refuses_a_second_standing_budget_on_one_category() {
        long account = aCurrentAccountOfItsOwn();
        long groceries = aCategoryOn(account, "Groceries");
        long fuel = aCategoryOn(account, "Fuel");
        budgetOverHttp(account, groceries, "250.00");
        Instant now = Instant.now();
        YearMonth thisMonth = YearMonth.now();

        assertThat(budgets.theCategoriesAreAlreadyBudgetedOnce())
                .as("the uniqueness is in the database file rather than only in this application's "
                        + "intentions, and the start-up step is what put it there")
                .isEqualTo(1);

        assertThatThrownBy(() -> budgets.saveAndFlush(new MonthlyBudget(account, groceries,
                new BigDecimal("300.00"), RolloverRule.NOTHING_ROLLS_OVER, thisMonth, now)))
                .as("a second figure in force on one category, written past the service's own read "
                        + "— which is what two requests naming one at the same moment would do")
                .isInstanceOf(DataAccessException.class)
                // SQLite's own words, and the reason they are asserted on rather than only the
                // exception type: this dialect hands the failure back as a JpaSystemException
                // rather than a DataIntegrityViolationException, so the type alone would also be
                // satisfied by a write that failed for some entirely different reason. The two
                // columns named in the message are the guarantee this test is about.
                .hasMessageContaining("UNIQUE constraint failed")
                .hasMessageContaining("monthly_budget.current_account_id")
                .hasMessageContaining("monthly_budget.category_id");

        assertThatCode(() -> budgets.saveAndFlush(
                aFigureThatHasAlreadyStoodDown(account, groceries, thisMonth, now)))
                .as("a figure that has stood down competes for nothing: it keeps the months it "
                        + "governed, and a customer who changes their mind twice in one month "
                        + "leaves two rows carrying this month's first day")
                .doesNotThrowAnyException();

        assertThatCode(() -> budgets.saveAndFlush(new MonthlyBudget(account, fuel,
                new BigDecimal("120.00"), RolloverRule.NOTHING_ROLLS_OVER, thisMonth, now)))
                .as("and a figure on another category is the ordinary case of a household deciding "
                        + "what each of its words is allowed to cost")
                .doesNotThrowAnyException();
    }

    /** A row written as the application writes a superseded one: standing, then stood down. */
    private static MonthlyBudget aFigureThatHasAlreadyStoodDown(long account, long categoryId,
                                                                YearMonth thisMonth, Instant now) {
        MonthlyBudget superseded = new MonthlyBudget(account, categoryId, new BigDecimal("199.00"),
                RolloverRule.NOTHING_ROLLS_OVER, thisMonth, now);
        superseded.supersededFrom(thisMonth, now);
        return superseded;
    }

    /** Names a figure through the API, so that the row this test collides with is the application's. */
    private void budgetOverHttp(long currentAccountId, long categoryId, String amount) {
        ResponseEntity<JsonNode> declared = http.exchange(
                "/api/current-accounts/{account}/categories/{category}/budget", HttpMethod.PUT,
                new HttpEntity<>(Map.of("amount", amount)), JsonNode.class, currentAccountId,
                categoryId);
        assertThat(declared.getStatusCode())
                .describedAs("the set-up: the application itself named this figure, over HTTP")
                .isEqualTo(HttpStatus.OK);
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
                Map.of("name", "Budgeter " + distinct + " for one standing budget",
                        "contactDetails", "one-standing-budget-" + distinct + "@example.be"),
                JsonNode.class);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class,
                added.getBody().get("id").asLong());
        return accounts.get("currentAccounts").get(0).get("id").asLong();
    }
}
