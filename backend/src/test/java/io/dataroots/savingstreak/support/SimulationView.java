package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * A savings account's futures as the API answers them: the two days they are drawn between, the
 * present every one of them starts from, and one answer per branch that was asked about. Shared by
 * every test that reads one back, for the reason {@link BalancesView} gives — copies of a shape
 * drift into disagreeing about it, and then one of them is testing a contract nobody serves.
 *
 * <p>{@code scenarios} always holds the future the customer is already in, first, whether or not
 * anybody asked about anything — a projection with nothing beside it answers no question.
 *
 * <p>Everything under {@code whereThisAccountStands} is a fact rather than a projection, and every
 * one of those facts is answered by some other endpoint too. That overlap is what the tests here
 * assert on: a simulator whose present disagreed with the application's present would be predicting
 * a year nobody is standing at the start of.
 */
public record SimulationView(LocalDate from, LocalDate until,
                             boolean anIllustrationRatherThanAPromise,
                             WhereThisAccountStandsView whereThisAccountStands,
                             List<HowAScenarioTurnsOutView> scenarios) {

    /** The one branch nobody has to ask for, which is always the first one answered. */
    public HowAScenarioTurnsOutView theYearAlreadyUnderWay() {
        return scenarios.get(0);
    }

    /**
     * One branch as the API answers it: what it is called, the window it is drawn over, the twelve
     * months it comes to and the dated things that happen in it.
     */
    public record HowAScenarioTurnsOutView(String called, LocalDate from, LocalDate until,
                                           List<AMonthOfTheFutureView> months,
                                           List<AThingThatHappensView> thingsThatHappen) {

        /** The row closing on that day, and nothing at all when no row closes on it. */
        public AMonthOfTheFutureView closingOn(LocalDate day) {
            return months.stream()
                    .filter(month -> month.closesOn().equals(day))
                    .findFirst()
                    .orElse(null);
        }

        /** Only the things of that kind, in the order the answer put them in. */
        public List<AThingThatHappensView> thingsOfKind(String kind) {
            return thingsThatHappen.stream().filter(thing -> thing.kind().equals(kind)).toList();
        }
    }

    /**
     * One dated thing in a branch: the day, the kind and the figure.
     *
     * <p>The kind is read as the text it travels as rather than as an enumeration of the test's own,
     * for the reason every other view here is a record of what was sent: a test that mapped the name
     * onto a copy of the enum would stop noticing the day a kind was renamed on the wire.
     */
    public record AThingThatHappensView(LocalDate on, String kind, BigDecimal figure) {
    }

    /**
     * One month of a branch: where it leaves the money, the points and the run of weeks, and what
     * happened to the points inside it.
     *
     * <p>The three movements are here beside the balance so that a test can do what a customer can
     * do with a pencil — the points at the end of a month are the ones at the end of the month
     * before, plus what was earned, plus what a bonus paid, less what expired.
     */
    public record AMonthOfTheFutureView(YearMonth month, LocalDate closesOn, BigDecimal balance,
                                        long pointsStanding, int securedWeeks, long pointsEarned,
                                        long pointsABonusPaid, long pointsThatExpired) {
    }

    /**
     * The present a branch would be folded from: the euros, the lots of points and the days they go,
     * the goals, the weekly plan, the rules, the everyday accounts funding it, the run of weeks and
     * the high-water mark.
     *
     * <p>The goals, the capacity, the rules, the income and the bills are held as the views their own
     * resources are already read through, which is the whole point: a test can assert that the
     * simulator's copy of a goal is the goals screen's goal by comparing the two objects rather than
     * by comparing seven fields and forgetting the eighth.
     */
    public record WhereThisAccountStandsView(LocalDate asAt,
                                             long savingsAccountId,
                                             long customerId,
                                             BigDecimal balance,
                                             List<DepositStillHoldingMoneyView> deposits,
                                             List<PointsGoingView> pointsGoing,
                                             long pointsStanding,
                                             BigDecimal allocated,
                                             BigDecimal unallocated,
                                             List<GoalView> goals,
                                             SavingCapacityView weeklyCapacity,
                                             List<SavingRuleView> rules,
                                             List<CurrentAccountBehindItView> currentAccounts,
                                             BigDecimal newSavingsThisWeek,
                                             BigDecimal weeklyMinimum,
                                             BigDecimal stillNeededThisWeek,
                                             int currentStreakWeeks,
                                             int bestStreakWeeks,
                                             BigDecimal currentMultiplier,
                                             BigDecimal mostEverSaved) {

        /**
         * What every deposit behind this account still holds, added up — which is the balance, and a
         * test that says so is saying the deposits reported are the deposits the balance is made of.
         */
        public BigDecimal stillHeldAcrossTheDeposits() {
            return deposits.stream()
                    .map(DepositStillHoldingMoneyView::stillHolding)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** The everyday account with that identifier, and nothing at all when it is not here. */
        public CurrentAccountBehindItView currentAccount(long currentAccountId) {
            return currentAccounts.stream()
                    .filter(account -> account.currentAccountId() == currentAccountId)
                    .findFirst()
                    .orElse(null);
        }
    }

    /** One deposit that still holds money: which it is, what is left in it, when it landed. */
    public record DepositStillHoldingMoneyView(long depositId, BigDecimal stillHolding,
                                               Instant landedAt) {
    }

    /** One day some of the holder's points go, and how many go on it. */
    public record PointsGoingView(LocalDate on, long points) {
    }

    /** One everyday account behind the saving: what is in it, what lands, and what leaves. */
    public record CurrentAccountBehindItView(long currentAccountId, BigDecimal balance,
                                             MonthlyIncomeView income,
                                             List<RecurringBillView> bills) {
    }
}
