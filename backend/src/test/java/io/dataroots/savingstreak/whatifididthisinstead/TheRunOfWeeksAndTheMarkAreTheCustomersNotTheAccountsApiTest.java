package io.dataroots.savingstreak.whatifididthisinstead;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SeededAccounts;
import io.dataroots.savingstreak.support.SimulationView.WhereThisAccountStandsView;
import io.dataroots.savingstreak.support.SimulationView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer saving into two pots has one run of weeks and one high-water mark, and a branch of
 * either pot's future is reasoning about both.
 *
 * <p><strong>The asymmetry this whole snapshot turns on.</strong> The balance, the deposits, the
 * goals and the rules are the account's, because that is the pot being planned. The run of secured
 * weeks and the most the customer has ever saved are the <em>person's</em>: a week is secured by
 * what they put away across every savings account they hold, and {@code TheMostEverSaved} judges a
 * deposit against the most they have ever held anywhere. A snapshot that counted only one pot would
 * tell Anke she had lost a run she still had, and would have her earning points a second time on
 * euros the ledger has already seen — which is precisely the surprise this feature exists to warn
 * people about, arriving from the feature itself.
 *
 * <p>So this pays into one pot and reads both. What moved in one column only is the account's; what
 * moved in both is the customer's, and the two lists are exactly the ones the snapshot promises.
 *
 * <p>Anke holds two savings accounts in the seed, which is why this can be asked at all. Her savings
 * are put back at their peak first: the run shares one database, and an earlier class that withdrew
 * would leave this deposit filling a gap rather than saving anything new — which earns nothing, and
 * rightly, and would have this test measuring the order the classes happened to run in.
 *
 * <p>On the shared application, because nothing here winds a clock.
 */
class TheRunOfWeeksAndTheMarkAreTheCustomersNotTheAccountsApiTest extends ApiIntegrationTest {

    /** Enough to move the mark and the week without being a sum her everyday account would notice. */
    private static final String PAID_IN = "60.00";

    @Test
    void paying_into_one_pot_moves_that_pots_balance_and_both_pots_view_of_the_customer() {
        SeededAccounts seeded = new SeededAccounts(http);
        long onePot = seeded.savingsAccountOf(ANKE);
        long theOtherPot = seeded.otherSavingsAccountOf(ANKE);
        seeded.savingsBackAtTheirPeak(onePot, ANKE);

        WhereThisAccountStandsView onePotBefore = whereItStands(onePot);
        WhereThisAccountStandsView theOtherPotBefore = whereItStands(theOtherPot);
        paysIn(onePot, seeded.currentAccountOf(ANKE));

        WhereThisAccountStandsView onePotAfter = whereItStands(onePot);
        WhereThisAccountStandsView theOtherPotAfter = whereItStands(theOtherPot);

        // Each snapshot is about the pot it was asked for, and says so.
        assertThat(onePotAfter.savingsAccountId()).isEqualTo(onePot);
        assertThat(theOtherPotAfter.savingsAccountId()).isEqualTo(theOtherPot);
        assertThat(onePotAfter.customerId())
                .as("and both are about one person, which is what makes the two halves of this test "
                        + "comparable at all")
                .isEqualTo(theOtherPotAfter.customerId());

        // What is the account's moved in one column only.
        assertThat(onePotAfter.balance())
                .as("the pot that was paid into holds EUR " + PAID_IN + " more")
                .isEqualByComparingTo(onePotBefore.balance().add(new BigDecimal(PAID_IN)));
        assertThat(theOtherPotAfter.balance())
                .as("and the other pot holds exactly what it held: a balance is the account's, and "
                        + "a branch of it is planning that pot rather than the person's savings")
                .isEqualByComparingTo(theOtherPotBefore.balance());
        assertThat(onePotAfter.deposits())
                .as("the deposit landed in the pot it was paid into")
                .hasSize(onePotBefore.deposits().size() + 1);
        assertThat(theOtherPotAfter.deposits())
                .as("and nowhere else")
                .isEqualTo(theOtherPotBefore.deposits());

        // What is the customer's moved in both.
        assertThat(onePotAfter.mostEverSaved())
                .as("the high-water mark rose, because Anke's savings were at their peak and are "
                        + "now EUR " + PAID_IN + " past it")
                .isEqualByComparingTo(onePotBefore.mostEverSaved().add(new BigDecimal(PAID_IN)));
        assertThat(theOtherPotAfter.mostEverSaved())
                .as("and it rose by the same amount in the pot that received nothing, because the "
                        + "mark is the person's: without that, every branch of this pot containing "
                        + "a withdrawal would overpay")
                .isEqualByComparingTo(onePotAfter.mostEverSaved());
        assertThat(theOtherPotAfter.newSavingsThisWeek())
                .as("the week counts what she put away, wherever she put it — a branch anchored on "
                        + "this pot is still reasoning about a week the other pot secured")
                .isEqualByComparingTo(theOtherPotBefore.newSavingsThisWeek().add(new BigDecimal(PAID_IN)));
        assertThat(onePotAfter.newSavingsThisWeek())
                .as("and both pots read the same week, because there is only one")
                .isEqualByComparingTo(theOtherPotAfter.newSavingsThisWeek());
        assertThat(onePotAfter.currentStreakWeeks())
                .as("and the same run of secured weeks, which is the run a lost week in either pot "
                        + "would end")
                .isEqualTo(theOtherPotAfter.currentStreakWeeks());
        assertThat(onePotAfter.pointsStanding())
                .as("and the same points, because a points balance is one pot per person however "
                        + "many savings accounts they hold")
                .isEqualTo(theOtherPotAfter.pointsStanding());
    }

    private WhereThisAccountStandsView whereItStands(long savingsAccountId) {
        ResponseEntity<SimulationView> answered = http.postForEntity(
                "/api/savings-accounts/{id}/simulations", Map.of("scenarios", List.of()),
                SimulationView.class, savingsAccountId);
        assertThat(answered.getStatusCode())
                .describedAs("asking where savings account " + savingsAccountId + " stands")
                .isEqualTo(HttpStatus.OK);
        return answered.getBody().whereThisAccountStands();
    }

    private void paysIn(long savingsAccountId, long fromCurrentAccountId) {
        ResponseEntity<JsonNode> made = http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", PAID_IN, "fromCurrentAccountId", fromCurrentAccountId),
                JsonNode.class, savingsAccountId);
        assertThat(made.getStatusCode())
                .describedAs("paying EUR " + PAID_IN + " into savings account " + savingsAccountId
                        + ": " + made.getBody())
                .isEqualTo(HttpStatus.CREATED);
    }
}
