package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
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
 * One goal per place on a savings account is a rule the database keeps, not one this application
 * merely intends.
 *
 * <p>The order of importance is what this whole feature turns on: which goal takes a deadline minimum
 * first, which one the surplus falls to, and which goals a reallocation may take money out of are all
 * read off it, and ticket 08 is where a tie stops being untidy and starts moving somebody's money into
 * an arbitrary goal. {@link GoalsService} has kept the live ranks a run of 1..n since ticket 01 — a new
 * goal is ranked last, abandoning one closes the gap, the whole order is only ever set as a permutation
 * — and until now that was the only thing keeping it. It held because SQLite serialises writers and
 * this application runs on a pool of one connection, which is a fact about a configuration file rather
 * than a rule about goals: two requests adding a goal at the same moment would both read the same
 * {@code size() + 1} and both take it.
 *
 * <p>So this test writes to the table directly, past the service and past its arithmetic, and insists
 * the write is refused. Which makes it one of the two tests in this codebase that reach below the HTTP
 * seam on purpose rather than for want of an endpoint — its subject <em>is</em> the storage — and it is
 * the shape {@code TheRecordOfAnnouncedAnniversariesIsUniqueInTheDatabaseApiTest} already uses. The
 * goal it collides with is one the application itself ranked, over HTTP.
 *
 * <p>The two writes that have to be <em>accepted</em> matter as much as the refusal. A goal that has
 * been given up on holds no place at all, so any number of them can sit on one account; and the same
 * place on a different account is a different competition entirely.
 */
class TheOrderOfImportanceIsStrictInTheDatabaseApiTest extends ApiIntegrationTest {

    /** Makes this class's customers different from every other class's, for the reason its own do. */
    private static final AtomicLong DISTINCT = new AtomicLong();

    private static final BigDecimal A_TARGET = new BigDecimal("100.00");

    @Autowired
    private SavingsGoalRepository goals;

    @Test
    void the_index_refuses_a_second_live_goal_in_one_place_on_one_account() {
        long savingsAccount = aSavingsAccountOfItsOwn();
        long anotherAccount = aSavingsAccountOfItsOwn();
        Instant now = Instant.now();
        assertThat(theRankOfTheGoalAddedTo(savingsAccount))
                .describedAs("the set-up: the application itself put a goal in first place")
                .isEqualTo(1);

        assertThatThrownBy(() -> goals.saveAndFlush(
                new SavingsGoal(savingsAccount, "Also first", A_TARGET, null, 1, now)))
                .as("a second goal claiming first place on the same account, written past the "
                        + "service's own arithmetic — which is what two requests adding a goal at "
                        + "the same moment would do")
                .isInstanceOf(DataAccessException.class)
                // SQLite's own words, and the reason they are asserted on rather than only the
                // exception type: this dialect hands the failure back as a JpaSystemException rather
                // than a DataIntegrityViolationException, so the type alone would also be satisfied
                // by a write that failed for some entirely different reason. The two columns named
                // in the message are the guarantee this test is about.
                .hasMessageContaining("UNIQUE constraint failed")
                .hasMessageContaining("savings_goal.savings_account_id")
                .hasMessageContaining("savings_goal.goal_rank");

        assertThatCode(() -> goals.saveAndFlush(
                new SavingsGoal(anotherAccount, "First somewhere else", A_TARGET, null, 1, now)))
                .as("first place on another account is a different competition, and the index is "
                        + "over the pair rather than over the rank alone")
                .doesNotThrowAnyException();

        assertThatCode(() -> {
            goals.saveAndFlush(givenUpOn(savingsAccount, "Given up on", now));
            goals.saveAndFlush(givenUpOn(savingsAccount, "Also given up on", now));
        })
                .as("a goal that has left the order holds no place, and two of them are not two "
                        + "goals fighting over one: the index counts nulls as distinct, which is "
                        + "the right answer rather than a lucky one")
                .doesNotThrowAnyException();
    }

    /** A goal that was opened and then given up on, so that it carries no place in the order. */
    private static SavingsGoal givenUpOn(long savingsAccountId, String name, Instant now) {
        SavingsGoal goal = new SavingsGoal(savingsAccountId, name, A_TARGET, null, 2, now);
        goal.abandon(now);
        return goal;
    }

    /** Opens a goal through the API, so that the row it collides with is one the application ranked. */
    private int theRankOfTheGoalAddedTo(long savingsAccountId) {
        ResponseEntity<JsonNode> opened = http.postForEntity("/api/savings-accounts/{id}/goals",
                Map.of("name", "First in the order", "target", "100.00"), JsonNode.class,
                savingsAccountId);
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return opened.getBody().get("rank").asInt();
    }

    /**
     * A customer of this class's own, and their savings account. Its own, because this test writes
     * rows to the goals table that no rule would have written, and they should land on an account
     * nobody else in the run is asserting about.
     */
    private long aSavingsAccountOfItsOwn() {
        long distinct = DISTINCT.incrementAndGet();
        ResponseEntity<JsonNode> added = http.postForEntity("/api/customers",
                Map.of("name", "Saver " + distinct + " for a strict order",
                        "contactDetails", "goals-strict-order-" + distinct + "@example.be"),
                JsonNode.class);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class,
                added.getBody().get("id").asLong());
        return accounts.get("savingsAccounts").get(0).get("id").asLong();
    }
}
