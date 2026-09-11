package io.dataroots.savingstreak.deposithistory;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a deposit looked back at says about itself: the euros it earned, the uplift the run of weeks
 * added, the rate it was paid at, and the total the two come to.
 *
 * <p>A customer reading their history is not the customer who made the deposit. They no longer know
 * which week it fell in or what their run was worth at the time, and an entry saying "9 points"
 * against EUR 7.60 is a figure they have no way to check. These tests are about the entry carrying
 * enough to be checked.
 *
 * <p>On the shared application, and every test asserts on the deposit it made rather than on an
 * absolute figure: the run shares one database and one week, so whatever rate the account is on when
 * a test runs is whatever earlier tests left it on. What is asserted is what holds at every rung —
 * that the entry says the same as the answer the deposit was given, that a bonus of nothing is
 * written out as nothing, and that no figure is missing.
 */
class EachDepositInTheHistoryExplainsItselfApiTest extends ApiIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    /**
     * A deposit that earned no uplift still says what it was paid at and that the uplift was
     * nothing. Leaving either out would make "no bonus" and "no rate recorded" the same entry, and
     * the second is the one a reader would have to go and investigate.
     *
     * <p>One whole euro, because that is the amount whose bonus is nothing at every rung of the
     * ladder: a single base point at the best rate this scheme pays is 1.50 points, which floors
     * back to the one it started as. So this test says the same thing whatever week the shared run
     * has reached.
     */
    @Test
    void a_deposit_that_earned_no_bonus_reports_a_bonus_of_zero_and_the_rate_it_was_paid_at() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);

        DepositView made = deposit(savingsAccount, seeded.currentAccountOf(ANKE), "1.00");

        assertThat(made.streakBonusPoints()).as("one euro earns no uplift at any rung").isZero();
        assertThat(listed(savingsAccount, made.id())).satisfies(entry -> {
            assertThat(entry.basePoints()).isEqualTo(1);
            assertThat(entry.streakBonusPoints()).as("nothing, said rather than left out").isZero();
            assertThat(entry.multiplierApplied()).as("its own rate, not the absence of one")
                    .isEqualByComparingTo(made.multiplierApplied())
                    .isBetween(new BigDecimal("1.00"), new BigDecimal("1.50"));
            assertThat(entry.pointsEarned()).isEqualTo(1);
        });
    }

    /**
     * All four figures are in the entry as figures, rather than dropped from it when they happen to
     * be nothing.
     *
     * <p>Read as JSON rather than through the record every other test binds to, because a record
     * with a {@code long} in it reads an absent field back as zero and would agree with a body that
     * never mentioned the bonus at all. What the page and the next agent get is the body, so the
     * body is what this one test looks at.
     *
     * <p>One euro again, so that the bonus is the figure that is nothing at every rung: the field
     * that is easiest to leave out is the one that has nothing in it.
     */
    @Test
    void every_figure_is_written_into_the_entry_rather_than_left_out_of_it() throws Exception {
        long savingsAccount = seeded.savingsAccountOf(ANKE);

        DepositView made = deposit(savingsAccount, seeded.currentAccountOf(ANKE), "1.00");

        JsonNode entry = entryInTheHistoryJson(savingsAccount, made.id());
        assertThat(entry.hasNonNull("basePoints")).as("basePoints is in the body").isTrue();
        assertThat(entry.hasNonNull("streakBonusPoints")).as("streakBonusPoints is in the body").isTrue();
        assertThat(entry.hasNonNull("multiplierApplied")).as("multiplierApplied is in the body").isTrue();
        assertThat(entry.hasNonNull("pointsEarned")).as("pointsEarned is in the body").isTrue();
        assertThat(entry.get("streakBonusPoints").asLong()).isZero();
        assertThat(new BigDecimal(entry.get("multiplierApplied").asText()))
                .isEqualByComparingTo(made.multiplierApplied());
    }

    /**
     * The entry and the answer the deposit was given are the same four figures, for a deposit whose
     * cents floor away and whose uplift therefore has a remainder thrown away with it. The awkward
     * amount is the point: a history that recomputed anything would be recomputing it from the
     * amount, and this is where the two arithmetics come apart.
     */
    @Test
    void the_entry_says_what_the_deposit_was_told_at_the_time_down_to_the_cents() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);

        DepositView made = deposit(savingsAccount, seeded.currentAccountOf(ANKE), "7.60");

        assertThat(made.basePoints()).as("the cents floor away before the rate is applied").isEqualTo(7);
        assertThat(listed(savingsAccount, made.id())).satisfies(entry -> {
            assertThat(entry.basePoints()).isEqualTo(made.basePoints());
            assertThat(entry.streakBonusPoints()).isEqualTo(made.streakBonusPoints());
            assertThat(entry.multiplierApplied()).isEqualByComparingTo(made.multiplierApplied());
            assertThat(entry.pointsEarned()).isEqualTo(made.pointsEarned());
            assertThat(entry.basePoints() + entry.streakBonusPoints())
                    .as("the two parts of the entry add up to its total")
                    .isEqualTo(entry.pointsEarned());
        });
    }

    /** The entry for one deposit, insisted on: a deposit missing from its own history is the failure. */
    private DepositView listed(long savingsAccountId, Long depositId) {
        DepositView[] history = http.getForObject(
                "/api/savings-accounts/{id}/deposits", DepositView[].class, savingsAccountId);
        return Arrays.stream(history)
                .filter(entry -> depositId.equals(entry.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "deposit " + depositId + " is not in the history of savings account "
                                + savingsAccountId));
    }

    /** The same entry as the body actually sent, for the one test that is about the body. */
    private JsonNode entryInTheHistoryJson(long savingsAccountId, Long depositId) throws Exception {
        JsonNode history = JSON.readTree(http.getForObject(
                "/api/savings-accounts/{id}/deposits", String.class, savingsAccountId));
        for (JsonNode entry : history) {
            if (entry.path("id").asLong() == depositId.longValue()) {
                return entry;
            }
        }
        throw new AssertionError("deposit " + depositId + " is not in the history body of savings "
                + "account " + savingsAccountId);
    }

    /**
     * A deposit this test needed to land, with the refusal ruled out rather than assumed: the seeded
     * current accounts are shallow on purpose, and a refused deposit would leave a test asserting
     * things about an entry that was never written — and failing somewhere less helpful.
     */
    private DepositView deposit(long savingsAccountId, long currentAccountId, String amount) {
        ResponseEntity<DepositView> response = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                DepositView.class,
                savingsAccountId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
