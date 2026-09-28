package io.dataroots.savingstreak.whatifididthisinstead;

import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.SimulationView;
import io.dataroots.savingstreak.support.TheCurrentAccountView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asking what a future would look like changes nothing at all.
 *
 * <p><strong>The promise the whole feature rests on, and the first thing a reviewer should check.</strong>
 * A simulator is worth having only if exploring is free: a customer who suspects that pressing the
 * button might move their money will press it once and never again. So this reads the account before
 * and after — the balance, the points, the mark, the run of weeks, the goals and the everyday
 * account — and insists that not one figure moved.
 *
 * <p>Asked twice rather than once, because a read that writes on the first call and is idempotent
 * afterwards would pass a before-and-after taken around a single request. Twice is what catches a
 * gathering that quietly credits, expires or sweeps something the first time it is asked.
 *
 * <p>Its own customer, so that the figures being compared are only ever moved by this test, and on
 * the shared application, because nothing here winds a clock.
 */
class AskingAboutAFutureWritesNothingApiTest extends ApiIntegrationTest {

    @Test
    void the_balance_the_points_and_the_goals_are_exactly_what_they_were() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "asking is free");
        LocalDate today = account.theDateTheClockReads();
        account.declaresAnIncome("25", "2600.00");
        account.declaresABill("Rent", "1", "950.00");
        account.opensAGoal("A car", "4000.00", today.plusMonths(6).toString());
        account.depositsByHand("120.00");

        BalancesView before = account.balances();
        List<GoalView> goalsBefore = account.goals();
        TheCurrentAccountView everydayBefore = account.currentAccount();

        SimulationView firstAsking = account.simulation();
        SimulationView secondAsking = account.simulation();

        assertThat(account.balances())
                .as("nothing about the account moved: not the euros, not the points, not the mark "
                        + "and not the run of weeks — asking has to be free or nobody will ask")
                .isEqualTo(before);
        assertThat(account.goals())
                .as("and no goal was created, funded, reallocated or reached by the asking")
                .isEqualTo(goalsBefore);
        assertThat(account.currentAccount())
                .as("and no money left the everyday account, which is what a rule fired for real "
                        + "would have taken")
                .isEqualTo(everydayBefore);
        assertThat(secondAsking.whereThisAccountStands())
                .as("the second asking found exactly the present the first one did, which is the "
                        + "same statement from the other side: a read that wrote would have moved "
                        + "its own answer")
                .isEqualTo(firstAsking.whereThisAccountStands());
    }
}
