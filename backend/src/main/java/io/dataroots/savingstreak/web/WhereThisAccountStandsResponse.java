package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.deposits.DepositStillHoldingMoney;
import io.dataroots.savingstreak.points.PointsExpiringOnADay;
import io.dataroots.savingstreak.simulation.ACurrentAccountBehindIt;
import io.dataroots.savingstreak.simulation.TheStartingPoint;

/**
 * The present every branch of this account's future is folded from, as the API reports it: the day
 * it was taken, the euros, the lots of points and the days they go, the goals, the weekly plan, the
 * rules, the everyday accounts funding all of it, the run of weeks and the high-water mark.
 *
 * <p><strong>Sent so that a projection can be argued with.</strong> A simulator that answered twelve
 * months and nothing else asks to be believed; one that also says what it started from can be
 * checked, by a customer against their own screens and by a reviewer against the log. Every figure
 * here is one another endpoint already answers — the balance is the account's own, the goals are the
 * goals resource's, the streak is the overview's — and that is the point rather than a duplication:
 * if any of them disagreed, the fold would be predicting a year from a present nobody is in.
 *
 * <p><strong>The run of weeks and the high-water mark are the customer's, not this account's.</strong>
 * They read the same beside every account their holder has, exactly as they do on the overview, and
 * a client showing them under an account's heading is showing the person's figure in the account's
 * context — which is what the deposit into that account is judged by. The balance, the deposits, the
 * goals and the rules beside them are this pot's.
 *
 * <p>The days are plain dates and the moments are moments, each as the module that owns it reports
 * it. Which calendar day a moment falls on depends on the zone it is read in, and that zone is
 * settled once in the backend rather than guessed at by whichever machine draws the screen.
 *
 * <p>Nothing here is added up across the year and nothing is a projection: this is the present, and
 * every figure in it is a fact rather than an illustration. The first figure that is not will say so.
 */
record WhereThisAccountStandsResponse(LocalDate asAt,
                                      long savingsAccountId,
                                      long customerId,
                                      BigDecimal balance,
                                      List<DepositStillHoldingMoneyResponse> deposits,
                                      List<PointsGoingResponse> pointsGoing,
                                      long pointsStanding,
                                      BigDecimal allocated,
                                      BigDecimal unallocated,
                                      List<GoalResponse> goals,
                                      SavingCapacityResponse weeklyCapacity,
                                      List<SavingRuleResponse> rules,
                                      List<CurrentAccountBehindItResponse> currentAccounts,
                                      BigDecimal newSavingsThisWeek,
                                      BigDecimal weeklyMinimum,
                                      BigDecimal stillNeededThisWeek,
                                      int currentStreakWeeks,
                                      int bestStreakWeeks,
                                      BigDecimal currentMultiplier,
                                      BigDecimal mostEverSaved) {

    /**
     * The snapshot as it came out of the module, unpacked once.
     *
     * <p>A factory rather than a constructor call at the call site, for the reason the customer's
     * overview gives about its own nine figures: these come from seven modules, and unpacking a
     * module's answer into six arguments is the sort of thing that gets done differently the second
     * time it is written.
     *
     * <p>The goals, the capacity and the rules borrow the response shapes their own resources
     * already serve, rather than growing narrower copies here. A client that can read the goals
     * screen can read these without learning a second spelling of a goal, and a field added to a
     * goal appears here the day it appears there instead of the day somebody remembers.
     */
    static WhereThisAccountStandsResponse of(TheStartingPoint standing) {
        return new WhereThisAccountStandsResponse(
                standing.asAt(),
                standing.savingsAccountId(),
                standing.customerId(),
                standing.balance(),
                standing.deposits().stream().map(DepositStillHoldingMoneyResponse::of).toList(),
                standing.pointsGoing().stream().map(PointsGoingResponse::of).toList(),
                standing.pointsStanding(),
                standing.goals().allocated(),
                standing.goals().unallocated(),
                standing.goals().goals().stream().map(GoalResponse::of).toList(),
                SavingCapacityResponse.of(standing.weeklyCapacity()),
                standing.rules().stream().map(SavingRuleResponse::of).toList(),
                standing.currentAccounts().stream().map(CurrentAccountBehindItResponse::of).toList(),
                standing.streak().week().newSavings(),
                standing.streak().week().weeklyMinimum(),
                standing.streak().week().stillNeeded(),
                standing.streak().streak().currentWeeks(),
                standing.streak().streak().bestWeeks(),
                standing.streak().streak().multiplier(),
                standing.theMostEverSaved());
    }

    /**
     * One deposit that still holds money: which it is, what is left in it, and the moment it landed.
     *
     * <p>Only the deposits with something left in them, because only those are the ones a branch can
     * still do anything to. An emptied deposit pays nothing on its anniversary however old it is and
     * has nothing a withdrawal could draw from; its points outlive it and are counted in the days
     * above, where they belong.
     *
     * <p>The moment rather than the day, and it is the one place in this answer where that is the
     * right choice: an anniversary is counted from the moment the money landed, so this is the input
     * to a rule rather than a date for a customer to read. The deposit history beside it says what
     * the next one is worth.
     *
     * <p>Named {@code stillHolding} rather than {@code amount}, because it is not what was paid in.
     * A deposit of EUR 500 that a withdrawal has taken EUR 200 out of still holds EUR 300, and it is
     * EUR 300 that its next anniversary is priced on.
     */
    record DepositStillHoldingMoneyResponse(long depositId, BigDecimal stillHolding,
                                            Instant landedAt) {

        static DepositStillHoldingMoneyResponse of(DepositStillHoldingMoney deposit) {
            return new DepositStillHoldingMoneyResponse(
                    deposit.id(), deposit.remainingAmount(), deposit.depositedAt());
        }
    }

    /**
     * One day some of the holder's points go, and how many go on it.
     *
     * <p>The days rather than the lots, which is how the Points module answers and is deliberately
     * all it will say: every point going on one day is one figure here however many lots it came
     * from, and the twelve-month rule behind the date stays that module's own.
     *
     * <p>The holder's, across everything they have ever earned — including points somebody gave
     * them, which are theirs and expire like the rest. It is therefore a wider figure than the
     * account's own timeline draws, and it is the right one for a fold: a branch cannot report a
     * points balance falling on a day unless it can see every point that falls.
     *
     * <p>A day already gone means points that are going tonight rather than a mistake, exactly as it
     * does everywhere else this application reports an expiry date.
     */
    record PointsGoingResponse(LocalDate on, long points) {

        static PointsGoingResponse of(PointsExpiringOnADay going) {
            return new PointsGoingResponse(going.on(), going.points());
        }
    }

    /**
     * One everyday account a branch's saving would have to come out of: what is in it, what its
     * holder says lands in it monthly, and the bills standing against it.
     *
     * <p>Here because a rule can only move money that is there, and the branches this whole feature
     * exists for are the ones whose answer turns on whether it was. The income and the bills are the
     * shapes the current account's own screen already serves, for the reason the factory above
     * gives.
     *
     * <p>Every account the holder has rather than only the ones this savings account's rules draw
     * from — a customer who has written no rule yet is the one asking this hardest.
     */
    record CurrentAccountBehindItResponse(long currentAccountId, BigDecimal balance,
                                          MonthlyIncomeResponse income,
                                          List<RecurringBillResponse> bills) {

        static CurrentAccountBehindItResponse of(ACurrentAccountBehindIt account) {
            return new CurrentAccountBehindItResponse(
                    account.currentAccountId(),
                    account.balance(),
                    MonthlyIncomeResponse.of(account.income()),
                    account.bills().stream().map(RecurringBillResponse::of).toList());
        }
    }
}
