package io.dataroots.savingstreak.whatifididthisinstead;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.RecurringBillView;
import io.dataroots.savingstreak.support.SimulationView;
import io.dataroots.savingstreak.support.SimulationView.CurrentAccountBehindItView;
import io.dataroots.savingstreak.support.SimulationView.WhereThisAccountStandsView;
import io.dataroots.savingstreak.support.TheCurrentAccountView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where a savings account and its holder actually stand, gathered from every module that owns a
 * piece of it and answered as one present a year of branches could be folded from.
 *
 * <p><strong>The test the whole feature's first slice turns on.</strong> Nothing projects yet, and
 * the thing worth proving before anything does is that the present the fold will start from is the
 * present the application is actually in. So every figure in the snapshot is asserted against the
 * screen that owns it — the balance against the account's own overview, the goals against the goals
 * resource, the weekly plan against the capacity resource, the rules against the rules resource, the
 * salary and the rent against the current account's own screen, the run of weeks and the high-water
 * mark against the customer's figures. A simulator whose present disagreed with any of those would
 * be predicting a year nobody is standing at the start of, and no amount of correct folding would
 * save it.
 *
 * <p>Its own customer, so that the whole present being asserted on is this test's, for the reason
 * {@link AnAccountWithAFutureToAskAbout} gives. On the shared application, because nothing here
 * winds a clock.
 */
class WhereThisAccountStandsIsGatheredFromEveryModuleApiTest extends ApiIntegrationTest {

    /** Far enough into the year that the deadline is inside the window without being at its edge. */
    private static final int MONTHS_TO_THE_DEADLINE = 6;

    @Test
    void the_snapshot_is_the_account_the_goals_the_plan_the_rules_the_household_and_the_customer() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "the whole present");
        LocalDate today = account.theDateTheClockReads();
        account.declaresAnIncome("25", "2600.00");
        account.declaresABill("Rent", "1", "950.00");
        account.declaresAWeeklyCapacity("60.00");
        GoalView car = account.opensAGoal("A car", "4000.00",
                today.plusMonths(MONTHS_TO_THE_DEADLINE).toString());
        account.leavesARuleStanding(aFixedAmountEveryWeek(account.currentAccountId(),
                "Every Friday", "FRIDAY", "40.00"));
        account.depositsByHand("120.00");

        SimulationView answered = account.simulation();
        WhereThisAccountStandsView standing = answered.whereThisAccountStands();

        // The window, which is the one figure this endpoint answers that nothing else does. Twelve
        // calendar months off the application's clock rather than off the machine's, so that a
        // demonstration with a wound clock draws the year the application is in.
        assertThat(answered.from())
                .as("the window opens on the day the application's clock reads, not the machine's")
                .isEqualTo(today);
        assertThat(answered.until())
                .as("and closes twelve calendar months on, the horizon the bar and the rules' "
                        + "preview already quote, so that the three forward-looking screens look "
                        + "equally far")
                .isEqualTo(today.plusMonths(12));
        assertThat(standing.asAt())
                .as("and the snapshot was taken on the day the window opens, because a present read "
                        + "on a different day is not the present the year is folded from")
                .isEqualTo(answered.from());
        assertThat(answered.scenarios())
                .as("nothing was asked about and one branch still comes back: the future the "
                        + "customer is already in, which is computed whether or not anybody named a "
                        + "scenario, because a projection with nothing beside it answers no question")
                .hasSize(1);
        assertThat(answered.theYearAlreadyUnderWay().from())
                .as("and it is drawn over the same window the answer names")
                .isEqualTo(answered.from());

        // The euros, and the deposits the balance is made of.
        BalancesView overview = account.balances();
        assertThat(standing.balance())
                .as("the balance is the one the account's own screen shows")
                .isEqualByComparingTo(overview.moneyBalance());
        assertThat(standing.deposits())
                .as("and the one deposit behind it is reported, because a withdrawal in a branch "
                        + "draws from these and an anniversary in a branch is priced on them")
                .hasSize(1);
        assertThat(standing.stillHeldAcrossTheDeposits())
                .as("what those deposits still hold adds up to the balance, which is what says they "
                        + "are the deposits the balance is made of rather than some other list")
                .isEqualByComparingTo(standing.balance());
        assertThat(standing.deposits().get(0).stillHolding()).isEqualByComparingTo("120.00");

        // The points, as days they go on rather than as lots — which is all the Points module will
        // say about its own storage, and all a fold needs.
        assertThat(standing.pointsStanding())
                .as("the points standing are the holder's, the same figure their overview shows")
                .isEqualTo(overview.pointsBalance());
        assertThat(standing.pointsGoing())
                .as("and they go on one day, twelve months after the afternoon they were earned on")
                .hasSize(1);
        assertThat(standing.pointsGoing().get(0).on())
                .as("the day the account's own bar already says they go")
                .isEqualTo(today.plusYears(1));
        assertThat(standing.pointsGoing().get(0).points()).isEqualTo(overview.pointsBalance());

        // The goals, in the goals resource's own shape, with what each is allocated.
        assertThat(standing.goals())
                .as("the goals are the goals screen's goals, object for object, so that a field "
                        + "added to a goal appears here the day it appears there")
                .isEqualTo(account.goals());
        assertThat(standing.goals()).extracting(GoalView::id).containsExactly(car.id());
        assertThat(standing.allocated().add(standing.unallocated()))
                .as("and what is allocated plus what is not is the balance, which is the whole of "
                        + "what a branch has to spend on its goals")
                .isEqualByComparingTo(standing.balance());

        // The weekly plan and the rules.
        assertThat(standing.weeklyCapacity())
                .as("the weekly plan is the one its own screen shows")
                .isEqualTo(account.weeklyCapacity());
        assertThat(standing.weeklyCapacity().weeklyCapacity()).isEqualByComparingTo("60.00");
        assertThat(standing.rules())
                .as("and the rules standing on the account are the rules screen's, each already "
                        + "carrying the day it next fires and what it would move")
                .isEqualTo(account.rules());
        assertThat(standing.rules()).hasSize(1);
        assertThat(standing.rules().get(0).nextFiresOn())
                .as("a rule with no next firing could not be fired by a fold at all")
                .isNotNull();

        // The household behind the saving: a rule can only move money that is there.
        TheCurrentAccountView everyday = account.currentAccount();
        CurrentAccountBehindItView funding = standing.currentAccount(account.currentAccountId());
        assertThat(funding)
                .as("the everyday account the saving comes out of is in the snapshot, because a "
                        + "branch that fired a rule against money nobody has would promise twelve "
                        + "transfers a salary could never cover")
                .isNotNull();
        assertThat(funding.balance()).isEqualByComparingTo(everyday.balance());
        assertThat(funding.income())
                .as("with the salary exactly as the account's own screen declares it")
                .isEqualTo(everyday.income());
        assertThat(funding.bills())
                .as("and the bills going the other way, which is the other half of what a morning "
                        + "leaves behind")
                .isEqualTo(everyday.bills());
        assertThat(funding.bills()).extracting(RecurringBillView::name).containsExactly("Rent");

        // And the two figures that are the person's rather than this pot's.
        assertThat(standing.customerId()).isEqualTo(account.customerId());
        assertThat(standing.mostEverSaved())
                .as("the high-water mark is the customer's, the same figure their own overview "
                        + "shows, and without it every branch containing a withdrawal overpays")
                .isEqualByComparingTo(overview.mostEverSaved());
        assertThat(standing.mostEverSaved()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(standing.currentStreakWeeks()).isEqualTo(overview.currentStreakWeeks());
        assertThat(standing.bestStreakWeeks()).isEqualTo(overview.bestStreakWeeks());
        assertThat(standing.currentMultiplier())
                .as("and the rate that run pays, which is what a deposit inside a branch earns at")
                .isEqualByComparingTo(overview.currentMultiplier());
        assertThat(standing.newSavingsThisWeek())
                .as("beside what has gone into this week so far, because the week a branch starts "
                        + "in is half-run and the Sunday that closes it is inside the window")
                .isEqualByComparingTo(overview.newSavingsThisWeek());
        assertThat(standing.weeklyMinimum()).isEqualByComparingTo(overview.weeklyMinimum());
        assertThat(standing.stillNeededThisWeek())
                .isEqualByComparingTo(overview.stillNeededThisWeek());
    }

    /**
     * A fixed amount every week on a chosen day, as a customer's form would send it.
     *
     * <p>Written out here rather than borrowed from the Automation tests' own builder, which is
     * package-private to them and rightly so: this test needs one rule standing so that the snapshot
     * has one to report, and reaching across a package boundary for eight fields would tie this
     * feature's tests to the shape of another feature's fixtures.
     */
    private static Map<String, Object> aFixedAmountEveryWeek(long fromCurrentAccountId, String name,
                                                             String dayOfWeek, String amount) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", name);
        rule.put("fromCurrentAccountId", fromCurrentAccountId);
        rule.put("trigger", "WEEKLY");
        rule.put("dayOfWeek", dayOfWeek);
        rule.put("howMuchMoves", "A_FIXED_AMOUNT");
        rule.put("amount", amount);
        return rule;
    }
}
