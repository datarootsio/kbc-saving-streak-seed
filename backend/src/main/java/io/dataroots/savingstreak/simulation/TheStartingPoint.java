package io.dataroots.savingstreak.simulation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.automation.RecordedSavingRule;
import io.dataroots.savingstreak.deposits.DepositStillHoldingMoney;
import io.dataroots.savingstreak.goals.AllocationsOnAnAccount;
import io.dataroots.savingstreak.goals.SavingCapacityOnAnAccount;
import io.dataroots.savingstreak.loyalty.NextAnniversaryOfADeposit;
import io.dataroots.savingstreak.points.PointsExpiringOnADay;
import io.dataroots.savingstreak.products.TheNoticeOnAnAccount;
import io.dataroots.savingstreak.products.WhatAnAccountsProductPaysAndAsksFor;
import io.dataroots.savingstreak.scheme.TheSchemeEachWeekWasJudgedUnder;
import io.dataroots.savingstreak.streaks.WeekAndStreak;

/**
 * Where one savings account and its holder actually stand on the day the application's clock reads:
 * everything a branch of their future has to be folded from, gathered once and then never read
 * again.
 *
 * <p><strong>Read once, at the top, and that is the whole of this type's opinion.</strong> A fold
 * that asked a module a question halfway through its year would be folding two different instants of
 * the ledger into one answer — a balance from before a deposit committed and a streak from after it
 * — and the customer would be shown a future that no moment of the present leads to. So every figure
 * a branch could possibly want is here, taken in one read transaction, and the fold that comes after
 * it is arithmetic over this record and nothing else. The alternative considered and rejected was a
 * fold holding the services and asking as it went, which reads more naturally and is exactly the
 * thing that cannot be made consistent, cannot be unit tested without Spring, and — worst of the
 * three — is one careless call away from writing something. A snapshot cannot write.
 *
 * <p><strong>Nothing here is this module's own.</strong> Every field is another module's answer, in
 * that module's own type, unrepacked: {@link DepositStillHoldingMoney} is Deposits', {@link
 * PointsExpiringOnADay} is Points', {@link AllocationsOnAnAccount} and {@link
 * SavingCapacityOnAnAccount} are Goals', {@link RecordedSavingRule} is Automation's, {@link
 * ACurrentAccountBehindIt} carries Accounts' three. Simulation owns no entity, no repository and no
 * table, here or anywhere, and copying a module's figures into shapes of its own would be the first
 * step towards owning them — a second vocabulary for the same facts, free to drift from the first
 * the moment either is repriced. What this module owns is the <em>question</em>, and the question is
 * the list below.
 *
 * <p><strong>The streak and the high-water mark are the customer's, and not this account's.</strong>
 * That asymmetry is deliberate and it is the one thing about this record worth reading twice. A week
 * is secured by what somebody put away across every savings account they hold, and {@code
 * TheMostEverSaved} judges a deposit against the most they have ever held anywhere — so a branch
 * anchored on one pot is still reasoning about a run and a mark the other pot can move. Anke holds
 * two savings accounts, so this is not a hypothetical: a snapshot that counted only this account's
 * saving would tell her she had lost a run she still had, and would have her earning points a
 * second time on euros the ledger has already seen. {@code balance}, {@code deposits}, {@code goals}
 * and {@code rules} are the account's, because that is the pot being planned; {@code streak},
 * {@code theyStillHoldAltogether} and {@code theMostEverSaved} are the person's.
 *
 * <p><strong>The mark travels with what it is a mark over.</strong> {@code theMostEverSaved} on its
 * own prices nothing: what a deposit earns on is what it takes the customer <em>above</em> the mark,
 * and that subtraction needs what they hold now as well. The two are equal for somebody at their
 * peak and only for them — a customer who has taken money out sits below their own mark, and a fold
 * holding one figure would quietly pay them a second time on the euros filling the gap back up,
 * which is precisely the rule this whole feature exists to warn them about. Both are the person's
 * and neither is this account's, for the reason above: Anke's second savings account moves both.
 *
 * <p><strong>{@code pointsGoing} is the days rather than the batches.</strong> Points does not say
 * out loud that it keeps dated lots or when one was earned, and it is right not to: how long a batch
 * lasts is its rule and {@code PointsExpiry} is the one place it is written down. What a fold needs
 * is not the lots — it is how many points go on which day, which is what {@link PointsExpiringOnADay}
 * already is. So the day is quoted and the rule behind it is never restated. Everything a branch
 * earns after today is earned inside the window and therefore expires outside it, which is why this
 * one read is the whole of the expiry a twelve-month fold can see.
 *
 * <p><strong>{@code howLongABatchOfPointsLasts} is the one figure behind that rule the fold is
 * handed, and it is handed it by Points rather than read off the scheme.</strong> A branch earns
 * points inside the window and has to say when they would go, which means calling {@code
 * PointsExpiry} — and that rule now takes the lifetime as an argument, because the lifetime is
 * published and can change. The figure could have been read straight from the scheme module. It is
 * not, and that is deliberate: how long a batch of points lasts is the ledger's question and the
 * scheme merely holds the number, so a simulator reading it directly would be the second module
 * applying the points rule, which is the exact shape of bug this whole feature exists to remove.
 * {@code PointsService.howLongABatchEarnedNowLasts} is asked instead, so the fold uses the figure
 * the ledger itself would have used and cannot drift from it.
 *
 * <p><strong>{@code theSchemeEachWeekIsJudgedUnder} is the one field here that is a history rather
 * than a figure, and it is a history because a branch is a year long.</strong> A fold walks
 * fifty-two weeks forward, judges each of them and prices every deposit that lands in one — so it
 * needs what a week asks for and what a run pays <em>per week</em>, and a single set of figures could
 * no more be the right argument to a fold than it could to the derivation the fold restates.
 * {@code WeekAndStreakDerivation} takes exactly this value for exactly this reason, and taking the
 * same one is what keeps a branch's Sunday night and the application's Sunday night the same
 * judgement. It also makes a branch honest about a version the bank has already announced: a Monday
 * eight weeks out that a published version takes effect on prices the branch's deposits at the new
 * ladder from that Monday, which is what would actually happen and is a thing the customer is
 * entitled to see coming.
 *
 * <p>Read off the scheme rather than out of Streaks, which is where it parts company with
 * {@code howLongABatchOfPointsLasts} above, and the difference is worth naming because the two look
 * alike. That one is a <em>figure</em> Points applies a rule to, so taking it from Points is what
 * stops Simulation becoming the second module applying the points rule. This is not a figure at all:
 * it is the published history, the thing that says which figures, and the rules that consume it —
 * what secures a week, what a run pays — are still {@code NewSavingsThisWeek}'s and
 * {@code StreakMultiplier}'s and are called rather than restated. There is one door to the history
 * and Streaks does not own it, so a pass-through on Streaks would add a hop and no guarantee.
 *
 * <p>{@code asAt} is the day the clock reads rather than the day the machine does, in the zone this
 * application counts calendars in, for the reason every forward-looking read here already gives: the
 * clock can be wound a year ahead of the wall, and a snapshot dated off the wall would be a picture
 * of a present nobody is standing in.
 *
 * <p><strong>{@code until} travels with it</strong>, and it is {@code TimelineHorizon} quoted rather
 * than twelve months restated here. The two days are the window a fold walks and the window an
 * answer is drawn between, so a caller cannot be handed one without the other and nobody downstream
 * has to work out what day it is. Quoting is what keeps the three forward-looking screens the same
 * length: a simulator that ran to December beside a bar that ended in September would be two answers
 * to "how far ahead can I see", and somebody would find out by counting. The horizon's own
 * documentation argues this at length and names the one thing that must never happen — a repricing
 * of the expiry or loyalty rules silently changing how much of a year anybody is shown.
 *
 * <p><strong>{@code rulesSettledThrough} and the two cursors on each everyday account are a
 * snapshot of the machinery, and they are here because the alternative was being wrong.</strong> The
 * slice that wrote the fold argued that carrying every rule's, bill's and income's cursor would be a
 * snapshot of the bookkeeping rather than of the account, and left the fold assuming the run had
 * settled nothing — which made a branch fire the morning of {@code asAt} a second time, over a
 * morning the application had already lived through. A branch one morning's saving ahead of the
 * ledger is the one thing this feature may not be, so the assumption is gone and the cursors are
 * here. They are still not a picture of the account, and nothing renders them: they are what the
 * nightly run counts from, and a fold predicting that run has to count from the same place.
 *
 * <p>Read through their own narrow reads — {@code
 * AutomationService.howFarEachRuleOnAnAccountIsSettled} and the two on {@link
 * ACurrentAccountBehindIt} — rather than added to {@code RecordedSavingRule}, {@code ADeclaredBill}
 * or {@code DeclaredIncome}, each of which argues in its own documentation that a cursor is not
 * something a customer's screen should carry. All three of those arguments are right and none of
 * them is about a fold. The shape is {@code mostEverSavedBy}'s and {@code stillSavedBy}'s: the
 * narrowest fact that answers the question, asked for on its own by the one caller whose arithmetic
 * turns on it.
 *
 * <p><strong>{@code whenEachDepositNextPays} is the reversal of a decision the first slice made
 * deliberately.</strong> That slice avoided depending on Loyalty at all, on the grounds that {@code
 * LoyaltyAnniversary} is a pure calendar function and nothing needed the record of what had actually
 * been paid. That was right while the fold only ever looked forwards. It is wrong now: an
 * anniversary that fell before {@code asAt} and that the sweep has not caught is one the application
 * pays tonight, and a fold reading the calendar alone counts it as long paid and never pays it — so
 * a branch quietly loses the customer a bonus they are owed. Only Loyalty knows which anniversaries
 * were paid, so {@code LoyaltyService.whenTheDepositsInAnAccountNextPay} is asked, and it answers
 * exactly the missing fact: per deposit still holding money, the day it next pays — <em>including a
 * day already gone</em> — and what that day is worth. Simulation now reads eight modules rather than
 * seven, still one way, and Loyalty has still never heard of Simulation.
 *
 * <p><strong>{@code theProductItIsOn} and {@code theNoticeStanding} are the agreement, and their
 * absence was the bug this feature's correctness ticket existed to close.</strong> Every rule the
 * fold quotes now varies by product: what a euro saved here is worth in points, what an anniversary
 * pays per whole euro, what a month of interest comes to and whether the bonus rate is in it, and
 * what stands between the customer and their own money. A fold that was not handed those projected
 * every account as though it were free savings — which is exactly right for an account on free
 * savings and confidently wrong by two and a half percentage points for a customer on a twelve-month
 * fixed term. Both are {@code Products}' own public types, unrepacked, for the reason every other
 * field here is: {@link WhatAnAccountsProductPaysAndAsksFor} is one read of the whole agreement so
 * that no two halves of it can describe different versions, and {@link TheNoticeOnAnAccount} is what
 * that module already answers about notice standing on an account.
 *
 * <p>The notice travels as the notices themselves rather than as "how much is free today", because
 * a branch asks about a day that has not happened yet: a notice given last week and still running
 * this morning is money that <em>is</em> free on the day a scenario takes it out, and a figure taken
 * today would have refused that withdrawal. What turns the rows into an answer about any day is
 * {@code WhatAnAgreementStopsOnADay}, which is the same rule the withdrawal screen refuses with.
 *
 * <p><strong>What is not here is a maturity.</strong> A term that comes up inside the window is
 * rolled over, moved or left waiting by a nightly run this fold does not model, and a branch that
 * guessed which would be the simulator deciding a thing the maturity job decides. The term's date
 * travels because a withdrawal has to be refused against it; what happens on the day it arrives is
 * deliberately left to the ticket that owns maturity.
 *
 * <p>What is deliberately <strong>not</strong> here: rewards, redemptions, notifications, budgets and
 * spending categories. A branch cannot claim a reward, cannot raise an alert and does not model
 * discretionary spending — points accumulate and expire inside one and nothing draws them down.
 * Saying so here is cheaper than a customer working it out from a figure that failed to move.
 */
public record TheStartingPoint(LocalDate asAt,
                               LocalDate until,
                               long savingsAccountId,
                               long customerId,
                               BigDecimal balance,
                               List<DepositStillHoldingMoney> deposits,
                               List<PointsExpiringOnADay> pointsGoing,
                               int howLongABatchOfPointsLasts,
                               AllocationsOnAnAccount goals,
                               SavingCapacityOnAnAccount weeklyCapacity,
                               List<RecordedSavingRule> rules,
                               Map<Long, Instant> rulesSettledThrough,
                               Map<Long, NextAnniversaryOfADeposit> whenEachDepositNextPays,
                               List<ACurrentAccountBehindIt> currentAccounts,
                               WeekAndStreak streak,
                               TheSchemeEachWeekWasJudgedUnder theSchemeEachWeekIsJudgedUnder,
                               BigDecimal theyStillHoldAltogether,
                               BigDecimal theMostEverSaved,
                               WhatAnAccountsProductPaysAndAsksFor theProductItIsOn,
                               TheNoticeOnAnAccount theNoticeStanding) {

    /**
     * Copied on the way in, so that a snapshot cannot be edited after it was taken.
     *
     * <p>Which matters more here than it does on an ordinary read. The whole promise of this feature
     * is that asking changes nothing, and a fold is a long walk over these lists; a list the fold
     * could add a simulated deposit to would be a branch quietly rewriting the present it was
     * measured against, and the second branch in the same request would be folded from it.
     */
    public TheStartingPoint {
        deposits = List.copyOf(deposits);
        pointsGoing = List.copyOf(pointsGoing);
        rules = List.copyOf(rules);
        rulesSettledThrough = Map.copyOf(rulesSettledThrough);
        whenEachDepositNextPays = Map.copyOf(whenEachDepositNextPays);
        currentAccounts = List.copyOf(currentAccounts);
    }

    /**
     * How many points the customer holds as this snapshot was taken.
     *
     * <p>Added up from the days they go on rather than asked of Points a second time, and the two
     * cannot disagree: both are every batch of theirs with something left in it that has not
     * expired, so the sum of what every day costs them is the whole of what they have. Asking for
     * the balance as well would be a second answer to one question, and the day it differed from
     * this sum by a point nobody would know which of them the month rows had been built from.
     */
    public long pointsStanding() {
        return pointsGoing.stream().mapToLong(PointsExpiringOnADay::points).sum();
    }
}
