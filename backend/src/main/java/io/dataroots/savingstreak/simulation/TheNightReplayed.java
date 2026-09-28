package io.dataroots.savingstreak.simulation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.dataroots.savingstreak.accounts.ADeclaredBill;
import io.dataroots.savingstreak.accounts.BillState;
import io.dataroots.savingstreak.accounts.DeclaredIncome;
import io.dataroots.savingstreak.accounts.WhenABillIsDue;
import io.dataroots.savingstreak.accounts.WhenIncomeIsDue;
import io.dataroots.savingstreak.automation.RecordedSavingRule;
import io.dataroots.savingstreak.automation.RuleState;
import io.dataroots.savingstreak.automation.RuleTrigger;
import io.dataroots.savingstreak.automation.WhatARuleWouldMove;
import io.dataroots.savingstreak.automation.WhichOccurrencesAreDue;
import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.deposits.DepositStillHoldingMoney;
import io.dataroots.savingstreak.deposits.TheMostEverSaved;
import io.dataroots.savingstreak.deposits.TheRateADepositIsPaidAt;
import io.dataroots.savingstreak.goals.GoalState;
import io.dataroots.savingstreak.goals.HowTheWeeklyMoneyIsSpent;
import io.dataroots.savingstreak.goals.HowTheWeeklyMoneyIsSpent.AGoalCompetingForIt;
import io.dataroots.savingstreak.goals.HowTheWeeklyMoneyIsSpent.TheWeeklyMoneySpent;
import io.dataroots.savingstreak.goals.RecordedGoal;
import io.dataroots.savingstreak.goals.WhenAGoalWillBeReached;
import io.dataroots.savingstreak.goals.WhenAGoalWillBeReached.AGoalOnItsWay;
import io.dataroots.savingstreak.goals.WhenAGoalWillBeReached.TheProjection;
import io.dataroots.savingstreak.loyalty.LoyaltyAnniversary;
import io.dataroots.savingstreak.loyalty.LoyaltyRate;
import io.dataroots.savingstreak.loyalty.NextAnniversaryOfADeposit;
import io.dataroots.savingstreak.points.PointsExpiringOnADay;
import io.dataroots.savingstreak.points.PointsExpiry;
import io.dataroots.savingstreak.products.TheDailyBalancesOfAnAccount;
import io.dataroots.savingstreak.products.TheDailyBalancesOfAnAccount.AMovementOfMoney;
import io.dataroots.savingstreak.products.TheDailyBalancesOfAnAccount.TheBalanceAcrossAPeriod;
import io.dataroots.savingstreak.products.TheMonthlyPeriodsOfAnAccount;
import io.dataroots.savingstreak.products.TheRateAPeriodIsPaidAt;
import io.dataroots.savingstreak.products.TheRateAPeriodIsPaidAt.WhatAPeriodEarned;
import io.dataroots.savingstreak.products.WhatAnAccountsProductPaysAndAsksFor;
import io.dataroots.savingstreak.products.WhatAnAnnualRateIsWorth;
import io.dataroots.savingstreak.scheme.TheSchemeAsPublished;
import io.dataroots.savingstreak.streaks.NewSavingsThisWeek;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.streaks.StreakMultiplier;
import io.dataroots.savingstreak.streaks.TheLadderARunClimbs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A year of this application's nights, replayed one at a time over a snapshot that nobody can write
 * to: income landing, rules firing, bills being taken, batches going, anniversaries paying, and a
 * week closing every Sunday on whether enough was actually put away.
 *
 * <p><strong>Replayed rather than solved, and that is the decision the whole feature rests
 * on.</strong> Every rule in this application is already written as <em>what happens on this
 * day</em>. A closed form — "fifty euros a week for fifty-two weeks at an average rate" — would be a
 * second statement of every one of those rules, free to drift from the first the moment either is
 * repriced, and it would be wrong in all the places that actually matter to a customer: the week a
 * deposit secures and is therefore paid at the new rate, the anniversary that pays less because a
 * withdrawal emptied the deposit it is counted on, the batch that expires in March and takes the
 * points a bonus paid in February with it. Walking the days is slower to write and it is the only
 * version that cannot disagree with the application it is predicting.
 *
 * <p><strong>The rules are quoted, never restated.</strong> Nearly every rule this needs is already
 * a static, clock-free function of its arguments, which is not luck — it is how this codebase was
 * written, and it is the seam this feature hangs off. {@link WhenIncomeIsDue} says which days a
 * salary lands on; {@link WhichOccurrencesAreDue} which days a rule falls due; {@link
 * WhatARuleWouldMove} how much it moves out of a balance; {@link WhenABillIsDue} which days a bill
 * falls on; {@link PointsExpiry} when a batch's twelve months are up; {@link LoyaltyAnniversary}
 * when a deposit pays and {@link LoyaltyRate} what it pays; {@link NewSavingsThisWeek} what secures
 * a week and {@link StreakMultiplier} what a run of them is worth — both of them asked at the
 * figures the scheme published for the week being walked, off the history on the snapshot, because a
 * rule quoted at a constant is a rule half quoted; {@link TheRateADepositIsPaidAt}
 * how a run of weeks and a product's multiple compose into the one rate a deposit is priced at;
 * {@link TheMostEverSaved} which euros of a deposit have not been paid for before;
 * {@link TheMonthlyPeriodsOfAnAccount} which day each of an account's months closes on,
 * {@link TheDailyBalancesOfAnAccount} what it held on average and at its lowest across one,
 * {@link TheRateAPeriodIsPaidAt} whether that month earned its bonus and
 * {@link WhatAnAnnualRateIsWorth} what the rate then comes to in euros. Several of those were
 * package-private until this class existed and each says in its own documentation why it is not any
 * more. Nothing is re-implemented here that could be called.
 *
 * <p><strong>The one exception is the deposit itself, and it is the single biggest risk in this
 * feature.</strong> {@code DepositsService.deposit} reads the clock and writes the ledger, so a fold
 * that must write nothing cannot call it. What {@link #theDepositLands} does instead is restate the
 * three rules that method is made of, in the order that method applies them — the mark, then the run
 * of weeks <em>including</em> the week this deposit may have just secured, then the double flooring.
 * That restatement is a copy, copies drift, and the only thing standing between this copy and a
 * customer being told a number the application will not honour is the test that winds the clock
 * three months and puts the application's own figures beside the ones this predicted. Anybody
 * changing how a deposit is priced has to change it here as well, and the comments on that method
 * name every line of {@code DepositsService.deposit} and {@code PointsService.creditPointsFor} they
 * came from.
 *
 * <p><strong>The product is projected, not assumed, and that is the correction this slice
 * made.</strong> Four of the rules above stopped being the same for everybody the day accounts went
 * onto savings products: what a euro saved here earns in points, what an anniversary pays per whole
 * euro, what a month of interest comes to and whether the bonus rate is in it, and what stands
 * between a customer and their own money on the way out. Until this slice the fold knew none of
 * that and priced every account as free savings — which is exactly right for an account on free
 * savings and is a confident lie to a customer on a twelve-month fixed term, who is paid two and a
 * half percent rather than half a percent, a quarter more on every euro they put away and fifteen
 * percent rather than ten on every anniversary. So the account's whole agreement travels on
 * {@link TheStartingPoint} as {@link WhatAnAccountsProductPaysAndAsksFor}, read once with the rest
 * of the present, and every one of the four rules is <em>quoted</em> with that agreement's own
 * figures. None of the four is restated here, and the one rule this class does restate — the
 * deposit — restates it with the product's multiple in it, in the order
 * {@link TheRateADepositIsPaidAt} says the two factors compose.
 *
 * <p><strong>The interest is posted month by month, and it compounds because the sweep
 * compounds.</strong> A projection of a year that ignored the interest that year would pay was the
 * worse answer it replaced. Each of the account's monthly periods is closed on the day the sweep
 * would close it, priced on the average of the balances the branch actually stood at across that
 * period — the walk {@code InterestService} makes over the ledger, made here over the movements the
 * branch itself produced — and paid into the balance, so that the next month's average has it in.
 * What the interest does <em>not</em> do is the three things {@code DepositOrigin} says money the
 * bank added never does: it earns no points, it secures no week, and it raises neither the
 * high-water mark nor what the customer still holds out of their own money. It is money in the
 * account all the same, so a withdrawal in a branch takes it first — the cheapest money first, as
 * the real withdrawal does — which is what stops a withdrawal of a month's interest from repricing
 * a deposit's anniversaries.
 *
 * <p><strong>A condition on the way out refuses the question rather than being folded past.</strong>
 * A scenario that takes money out of a locked fixed term, or out of a notice account that notice
 * does not cover on the day it names, is refused before a single day is walked and in the words the
 * withdrawal screen itself refuses one in — {@code WhatAnAgreementStopsOnADay} writes both
 * sentences and all three callers quote it. Silently projecting such a withdrawal would be the
 * worst failure this feature has available to it: a customer shown a year in which they took five
 * hundred euros out of an account that will not let them.
 *
 * <p><strong>The grain is a day, and the promise is a day.</strong> The night's jobs run at one, at
 * two, at half past two, at three and at half past three, and this runs its steps in that order
 * because two rules firing on one morning and a batch expiring before an anniversary is paid are
 * differences a customer's balance can see. What it does not model is the time of day a thing was
 * created: a batch earned at two in the afternoon is swept at three the following morning, and this
 * expires it on the day its twelve months are up. That day is what {@code PointsExpiringOnADay} and
 * {@code NextAnniversaryOfADeposit} already promise the customer — "a date in the past here means
 * points that are going tonight" — so the fold reports the promised day and is at most a few hours
 * out of step with the sweep that carries it out. A fold carrying times of day would be predicting
 * the scheduler rather than the scheme.
 *
 * <p><strong>The opening day is walked, and what the application has already done to it is not
 * done again.</strong> This is the correction the third slice made, and it is worth reading twice
 * because the version before it was wrong in the customer's favour. Every rule, every bill and the
 * income each carry a cursor — the moment through which the nightly run has settled them — and the
 * walk asks each of them, every morning, exactly the question the run asks: what has fallen due
 * since that cursor. On an ordinary morning the run fired at two and the cursor is past the start of
 * today, so nothing is owed and the morning's transfer is <em>not</em> made twice; on a clock
 * somebody wound without running the jobs the cursor is months behind, everything in between is
 * genuinely owed, and it all fires on the first day this walks — exactly as the next run will fire
 * it. Assuming the cursors away made the first of the twelve rows one morning's saving ahead of the
 * ledger, which is the one thing a simulator may not be, and the cost of closing it is three narrow
 * reads that nothing renders.
 *
 * <p><strong>An anniversary the sweep has not caught is paid, and the calendar cannot say so.</strong>
 * The second half of the same correction. Which anniversaries have actually been paid is Loyalty's
 * record and not a fact about a date, so this is handed {@code
 * LoyaltyService.whenTheDepositsInAnAccountNextPay}'s answer on the snapshot — per deposit, the
 * anniversary it is <em>owed</em> next and what that is worth. An anniversary that fell this
 * lunchtime is one the application pays at half past three tomorrow morning; the version before this
 * one read the calendar, took every anniversary before the window opened as long paid, and quietly
 * lost the customer a bonus they were about to be given. The first slice deliberately kept Simulation
 * out of Loyalty, which was right while nothing needed the record and is wrong now — and the
 * reversal is recorded here and on {@link TheStartingPoint} rather than left to be noticed.
 *
 * <p><strong>The points the opening day earns are tracked.</strong> {@link
 * TheStartingPoint} carries the days the customer's <em>existing</em> points go rather than the
 * batches behind them, on the argument that everything a branch earns is earned inside the window
 * and therefore expires outside it. That argument has one hole and it is closed here. A batch earned
 * strictly after the opening day expires strictly after {@code until} and is indeed never seen
 * again. A batch earned <em>on</em> the opening day — a rule firing that morning, an anniversary
 * falling that day, and in the next slice a weekly extra a customer starts today — expires exactly
 * twelve months later, which is {@code until}, which is the last day this walks and the closing day
 * of the twelfth row. So every batch the fold creates is dated and put on the same schedule as the
 * ones it was handed, through {@link PointsExpiry} — with the lifetime the ledger itself would have
 * used, which is on the snapshot — rather than by adding twelve months here, and the boundary is
 * closed by construction rather than by a special case. Assuming it away would have lost
 * a customer a year's points on one day of the twelve months and nobody would have found out from
 * the figures.
 *
 * <p><strong>Pure, and that is an acceptance criterion rather than a style.</strong> No Spring, no
 * clock, no repository, no service, no {@code Instant.now()}. The one moment it ever builds is the
 * midnight a day begins at, in {@code SavingsWeek}'s zone, because every rule it quotes is written
 * against moments. Everything it knows arrives on the snapshot and everything it decides goes back
 * on the answer, which is what makes the cases that matter most — a week closing on a Sunday, a
 * deposit paid at the rate of the week it itself secured, an anniversary and an expiry on one day —
 * testable as the arithmetic they are instead of by winding a clock a year and hoping.
 *
 * <p><strong>Where this is knowingly approximate</strong>, said out loud rather than discovered:
 * <ul>
 * <li>A sweep rule moves a share of a balance nobody has yet. It is computed against the balance the
 * branch has arrived at on that morning, which is the best answer available and is why the response
 * says once that every figure on it is an illustration rather than a promise — the same word {@code
 * WhatWouldMove} already carries for the same reason.</li>
 * <li>The grain of a cursor is a day here and a moment there. The run settles a rule through the
 * moment it ran; this asks whether a day <em>began</em> after that moment, so a rule and the morning
 * it fired on agree to the day and never to the hour. Everything this fold reports is a day, so the
 * two cannot disagree about anything it says.</li>
 * <li>Whether a period has already had its turn is asked of the cursor alone. The run asks the
 * record as well — one occurrence per rule per week or month, one salary per account per payday —
 * because a guarantee about money cannot rest on a number this application writes. A branch is not a
 * guarantee about money and has no record to ask, so a rule whose day its holder moved inside a
 * period it had already fired in can be one transfer out for that one period. The cursor is right
 * about every other period and about every rule nobody has edited mid-month.</li>
 * <li>An anniversary or a batch that is already owed is acted on on the day the window opens, which
 * is the day the promise was made rather than the small hours the sweep will actually catch it in.
 * Both are inside the window and inside the same month row unless the window opens on a row's
 * closing day.</li>
 * <li>A rule that could not be honoured is not retried, because the run does not retry it: the
 * cursor moves past an occurrence that was presented and fell short. A bill that could not be paid
 * <em>is</em> retried every night afterwards, because the run settles what is still owed before it
 * looks at anything newly due, and the arrears are paid oldest first.</li>
 * <li>A monthly interest period that had already begun before the window opened is priced as though
 * the balance had stood all through its earlier days at the figure the account stands at this
 * morning. The branch has no ledger behind the day it starts on and asking for one would be a second
 * read of a history nothing renders; the supposition is the same one the products comparison screen
 * makes about money left alone, it is wrong by at most one month of one rate, and it is right to the
 * cent for the ordinary case of an account whose period boundary has not yet been crossed.</li>
 * <li>A term that matures inside the window is not rolled over, moved or left waiting, because
 * which of those happens is the maturity run's decision and a fold guessing it would be the second
 * place this application decides what a term does on the day it is up. The interest goes on being
 * priced at the term's own rate for the whole of the window.</li>
 * <li>Nothing in a branch spends points, claims a reward, raises a notification or touches a budget.
 * Points accumulate and expire and nothing draws them down, which {@link TheStartingPoint} says out
 * loud for the same reason: it is cheaper said here than discovered from a figure that did not
 * move.</li>
 * </ul>
 *
 * <p><strong>The goals are projected rather than allocated, and the two are different claims.</strong>
 * A branch does not spread its deposits across the goals: it asks the two functions the goals screen
 * itself asks — {@link HowTheWeeklyMoneyIsSpent#asAt} for what the plan gives each goal every week,
 * and {@link WhenAGoalWillBeReached#forAllOf} for the Monday each one's money is all there by — of
 * the branch's own capacity, the branch's own goals and the branch's own deadlines. For the branch
 * where nothing changes those are the account's, so the day named here is the day the goals screen
 * names, to the Monday; in a branch that adds twenty-five a week or moves a deadline they are the
 * changed ones, and the difference between the two days is the whole of the answer the customer came
 * for. Allocating the branch's deposits day by day instead would be a second statement of how money
 * reaches a goal, free to disagree with the first, and it would answer a question nobody asked — a
 * weekly capacity is the customer's own sentence about what they can put away, not a prediction of
 * what the rules will move.
 *
 * <p><strong>A branch's changes are questions asked of the day, never instructions given to
 * it.</strong> An {@link AnAdjustment} cannot reach this ledger: the walk asks every change, every
 * morning, what it would put away and asks the plan what each change adds to the weekly figure, and
 * then the walk — the only place the order of a night is written down — decides what that means. So
 * adding a kind of change adds a question and never a pass over the calendar, two changes in one
 * scenario compose because both are asked, and the branch where nothing changes is the same walk
 * asked of an empty list. Where a new question has to be asked from is said in {@link
 * AnAdjustment}'s own numbered list, which is the one place that recipe is written down.
 */
public final class TheNightReplayed {

    private static final Logger log = LoggerFactory.getLogger(TheNightReplayed.class);

    /**
     * What the branch where nothing changes is called, in the customer's own words for it.
     *
     * <p>Named rather than left blank, and named as a decision rather than as an absence. Carrying
     * on is what most people will in fact do, it has consequences of its own, and a column headed
     * "baseline" would quietly present it as the thing the others are measured against rather than
     * as one of the things being chosen between. It is also the only branch the customer did not
     * type a name for, so the application owes it one.
     */
    public static final String THE_YEAR_ALREADY_UNDER_WAY = "If I carry on as I am";

    /** How many rows a branch comes back as, which is the horizon read a month at a time. */
    private static final int MONTHS_IN_THE_ANSWER = 12;

    /** Points are whole, here as everywhere else in this application. */
    private static final int POINTS_ARE_WHOLE = 0;

    private TheNightReplayed() {
    }

    /**
     * The twelve months this account is heading for, folded one night at a time out of where it
     * stands today.
     *
     * <p>Every day from the day the window opens to the day it closes, inclusive, is walked exactly
     * once, and the twelve rows close on the twelve monthly anniversaries of the opening day — so
     * the rows cover the window with nothing left over and the last of them closes on {@code until}.
     *
     * <p>The changes are asked of every day of the walk and of the weekly plan the goals are funded
     * out of. An empty list is the branch where nothing changes, which is folded whether or not
     * anybody asked about anything, and every change in the list has already been found askable —
     * {@link AnAdjustment#whyItCannotBeAsked} is put to all of them before a single day of any branch
     * is folded, so that a bad change in the fourth scenario does not cost three folds first.
     *
     * @param standing    where the account and its holder actually are, read once and never re-read
     * @param called      what the customer calls this branch; the do-nothing one is called
     *                    {@link #THE_YEAR_ALREADY_UNDER_WAY}
     * @param adjustments the changes this branch is made of, in the order they were typed, and empty
     *                    for the branch where nothing changes
     */
    public static HowAScenarioTurnsOut theYearThatFollows(TheStartingPoint standing, String called,
                                                          List<AnAdjustment> adjustments) {
        // One line before the fold naming what this branch was asked to imagine, and one after it
        // carrying what the branch came to. The pair is what a reviewer reads a column of the screen
        // against: the second on its own says a year was folded and not which year, and four columns
        // of figures with no questions beside them are four answers to nothing.
        log.debug("a branch of this account's future savingsAccountId={} scenario={} from={} "
                        + "until={} adjustments={} asked=[{}]",
                standing.savingsAccountId(), called, standing.asAt(), standing.until(),
                adjustments.size(), whatItWasAskedToImagine(adjustments));
        TheBranchAsItGoes branch = new TheBranchAsItGoes(standing, adjustments);
        List<AMonthOfTheFuture> months = new ArrayList<>(MONTHS_IN_THE_ANSWER);
        int monthsClosed = 0;
        LocalDate closesNext = standing.asAt().plusMonths(1);

        for (LocalDate day = standing.asAt(); !day.isAfter(standing.until()); day = day.plusDays(1)) {
            branch.theNightOf(day);
            // The month closes once the day it closes on has been lived through, so an anniversary
            // paid on the last day of a month is in that month rather than the next one.
            while (monthsClosed < MONTHS_IN_THE_ANSWER && !day.isBefore(closesNext)) {
                months.add(branch.theMonthJustClosed(closesNext));
                monthsClosed++;
                closesNext = standing.asAt().plusMonths(monthsClosed + 1L);
            }
        }
        // A window shorter than twelve monthly anniversaries cannot happen while the horizon is
        // twelve months, and the walk above would silently answer with fewer rows if it ever did.
        // Filled in from where the branch ended rather than left short, because a page reading
        // across four columns cannot draw one of them with eleven bars.
        while (monthsClosed < MONTHS_IN_THE_ANSWER) {
            months.add(branch.theMonthJustClosed(standing.asAt().plusMonths(monthsClosed + 1L)));
            monthsClosed++;
        }

        // The goals are a projection over the whole window rather than a thing that happens in a
        // night, so they are worked out beside the walk and their days are merged into it below.
        List<AThingThatHappens> thingsThatHappen =
                new ArrayList<>(branch.thingsThatHappenedInTheNight());
        thingsThatHappen.addAll(whatBecomesOfTheGoals(standing, adjustments));
        // By the day, and then by the order the night runs them, which is what the kinds are
        // declared in. Stable, so that two things of one kind on one day stay in the order the walk
        // produced them — two bonuses on a morning are in the order the deposits behind them landed.
        thingsThatHappen.sort(Comparator.comparing(AThingThatHappens::on)
                .thenComparing(AThingThatHappens::kind));

        // One line per fold, and deliberately not one per day: a fold is three hundred and sixty-six
        // steps and a line each would bury the six lines that matter. Everything a reviewer needs to
        // check a column of the screen against is here — which branch, the window it was drawn
        // between, the three figures the last row carries, and how many events of each kind came out
        // of it, which is the half of the answer the rows cannot be checked against.
        AMonthOfTheFuture last = months.get(months.size() - 1);
        log.debug("a year replayed one night at a time savingsAccountId={} scenario={} from={} "
                        + "until={} daysWalked={} months={} closingBalance={} closingPoints={} "
                        + "closingStreakWeeks={} things={} thingsByKind={}",
                standing.savingsAccountId(), called, standing.asAt(), standing.until(),
                standing.asAt().datesUntil(standing.until().plusDays(1)).count(),
                months.size(), AmountOfMoney.asMoney(last.balance()), last.pointsStanding(),
                last.securedWeeks(), thingsThatHappen.size(), howManyOfEachKind(thingsThatHappen));
        return new HowAScenarioTurnsOut(called, standing.asAt(), standing.until(), months,
                thingsThatHappen);
    }

    /**
     * Every change this branch was asked to imagine, in the order they were typed and each in its own
     * words, for the line said before the fold.
     *
     * <p>Each change writes itself out rather than being written out here, which is the same
     * decision the rest of this design makes for the same reason: a method here that knew how to
     * describe every kind would be a switch that every new kind had to be added to, and the first
     * thing to go stale in it would be the line a reviewer trusts.
     */
    private static String whatItWasAskedToImagine(List<AnAdjustment> adjustments) {
        List<String> asked = new ArrayList<>(adjustments.size());
        for (AnAdjustment adjustment : adjustments) {
            asked.add(adjustment.asAsked());
        }
        return String.join("; ", asked);
    }

    /**
     * How many things of each kind the fold produced, for the one line a reviewer checks a column of
     * the screen against.
     *
     * <p>Every kind is in it, including the ones that happened nought times, because "no goal is
     * reached in this branch" is the interesting half of that sentence and a line that simply left
     * the kind out would read as a fold that had not looked.
     */
    private static Map<AKindOfThingThatHappens, Integer> howManyOfEachKind(
            List<AThingThatHappens> thingsThatHappen) {
        Map<AKindOfThingThatHappens, Integer> counted = new EnumMap<>(AKindOfThingThatHappens.class);
        for (AKindOfThingThatHappens kind : AKindOfThingThatHappens.values()) {
            counted.put(kind, 0);
        }
        for (AThingThatHappens thing : thingsThatHappen) {
            counted.merge(thing.kind(), 1, Integer::sum);
        }
        return counted;
    }

    /**
     * What becomes of this branch's goals: the day each one arrives, and each deadline it goes past
     * without arriving.
     *
     * <p><strong>The two functions the goals screen itself asks, asked of the branch's own
     * figures.</strong> {@link HowTheWeeklyMoneyIsSpent#asAt} spreads the weekly capacity across the
     * goals in the order they compete in — pins first, then each dated goal's minimum, then whatever
     * is left to the highest-ranked goal still open — and {@link WhenAGoalWillBeReached#forAllOf}
     * turns what each one is given into the Monday its money is all there by. Neither is restated
     * here and neither could be: the competition between goals is three passes with a rejected
     * alternative behind each, and a second copy of it in this package would answer a customer's
     * "when do I get the boat" differently from the screen they opened yesterday.
     *
     * <p><strong>How a branch's projection relates to the real read.</strong> For the branch where
     * nothing changes they are the same arithmetic over the same figures on the same day, so the day
     * named here is the day {@code GoalsService} names on the goals screen, to the Monday — which is
     * what makes the comparison between columns mean anything, because the do-nothing column has to
     * be the customer's actual present rather than a second opinion about it. A branch that adds to
     * the weekly capacity or moves a deadline asks the same two functions with that figure changed,
     * and the difference between the two Mondays is the answer the customer came for. What this is
     * <em>not</em> is a simulation of money reaching a goal: the fold never allocates a deposit to
     * anything, because how a deposit is spread is a rule Goals owns and a second statement of it
     * here would be free to drift from the first.
     *
     * <p>Only the goals this account is live on, in the order they compete in, which is exactly what
     * the snapshot's allocations carry. A goal that has been given up on has left the order
     * altogether and has no date to be late for.
     *
     * <p>Only days inside the window. A goal arriving in three years' time is a true answer to a
     * question nobody asked of a twelve-month bar, and a deadline that went by before the window
     * opened is not one this branch passes — it was already missed, and saying so again here would be
     * the simulator reporting history as though it were a consequence of the branch.
     */
    private static List<AThingThatHappens> whatBecomesOfTheGoals(TheStartingPoint standing,
                                                                 List<AnAdjustment> adjustments) {
        List<RecordedGoal> inRankOrder = standing.goals().goals().stream()
                .filter(goal -> goal.state() == GoalState.LIVE)
                .toList();
        if (inRankOrder.isEmpty()) {
            return List.of();
        }
        // The day each goal is wanted by in this branch, which is the day it is wanted by on the
        // account unless a change moved it. Worked out once, before either function is asked,
        // because both of them turn on it: the plan funds a dated goal to what it needs over the
        // weeks left before that day, and the projection is called late or in time by comparing
        // against the same day. Asking the two with different deadlines would have a branch funding
        // a goal to a date and then reporting it against another.
        Map<Long, LocalDate> wantedBy = new HashMap<>();
        for (RecordedGoal goal : inRankOrder) {
            LocalDate thisBranchWantsItBy =
                    theDayThisBranchWantsItBy(goal.id(), goal.deadline(), adjustments);
            wantedBy.put(goal.id(), thisBranchWantsItBy);
            if (thisBranchWantsItBy != null && !thisBranchWantsItBy.equals(goal.deadline())) {
                log.debug("a branch wants a goal by another day savingsAccountId={} goalId={} "
                                + "wasWantedBy={} nowWantedBy={}",
                        standing.savingsAccountId(), goal.id(), goal.deadline(),
                        thisBranchWantsItBy);
            }
        }
        List<AGoalCompetingForIt> competing = inRankOrder.stream()
                .map(goal -> new AGoalCompetingForIt(goal.id(), goal.name(), goal.rank(),
                        goal.stillNeeded(), wantedBy.get(goal.id()), goal.pinnedWeeklyAmount()))
                .toList();
        TheWeeklyMoneySpent spent = HowTheWeeklyMoneyIsSpent.asAt(standing.savingsAccountId(),
                theWeeklyPlanThisBranchIsFundedBy(standing, adjustments), competing, standing.asAt());
        List<AGoalOnItsWay> onTheirWay = competing.stream()
                .map(goal -> new AGoalOnItsWay(goal.goalId(), goal.name(), goal.rank(),
                        goal.stillNeeded(), goal.deadline(), spent.forGoal(goal.goalId())))
                .toList();
        Map<Long, TheProjection> projected = WhenAGoalWillBeReached.forAllOf(
                standing.savingsAccountId(), onTheirWay, standing.asAt());

        List<AThingThatHappens> whatBecomesOfThem = new ArrayList<>();
        for (RecordedGoal goal : inRankOrder) {
            TheProjection projection = projected.getOrDefault(goal.id(), TheProjection.NOTHING_SAID);
            LocalDate arrivesOn = projection.willBeReachedOn();
            if (arrivesOn != null && isInside(arrivesOn, standing)) {
                whatBecomesOfThem.add(new AThingThatHappens(arrivesOn,
                        AKindOfThingThatHappens.A_GOAL_IS_REACHED, goal.target()));
            }
            LocalDate thisBranchWantsItBy = wantedBy.get(goal.id());
            if (thisBranchWantsItBy == null || !isInside(thisBranchWantsItBy, standing)) {
                continue;
            }
            // Late is late: no date at all means the plan gives this goal nothing and it never
            // arrives, which is as missed as a Monday past the deadline.
            boolean late = arrivesOn == null || arrivesOn.isAfter(thisBranchWantsItBy);
            if (late) {
                whatBecomesOfThem.add(new AThingThatHappens(thisBranchWantsItBy,
                        AKindOfThingThatHappens.A_DEADLINE_IS_MISSED, goal.target()));
            }
        }
        return whatBecomesOfThem;
    }

    /**
     * The day <em>this branch</em> wants a goal by: the day its holder gave it, and then whatever
     * each change in turn says it wants that goal by instead.
     *
     * <p>In turn, and each handed what the one before it answered, so that two changes naming one
     * goal compose rather than the walk having to decide which of them counts. A change that is not
     * about this goal answers with the day it was handed, which is what the question's default on
     * {@link AnAdjustment} already does, so a branch with no deadline in it comes out of here with
     * the account's own dates untouched.
     *
     * <p>Nothing about the real goal moves, here or anywhere: this is a day handed to two pure
     * functions and thrown away with the rest of the branch. A customer who wants the deadline
     * actually changed presses adopt, and Goals writes it in Goals' own words.
     */
    private static LocalDate theDayThisBranchWantsItBy(long goalId, LocalDate deadline,
                                                       List<AnAdjustment> adjustments) {
        LocalDate wantedBy = deadline;
        for (AnAdjustment adjustment : adjustments) {
            wantedBy = adjustment.theDayItWantsThatGoalBy(goalId, wantedBy);
        }
        return wantedBy;
    }

    /**
     * The weekly figure <em>this branch's</em> goals are funded out of: what the customer has
     * declared they can put away, and whatever this branch's changes add to it.
     *
     * <p><strong>This is where a branch that saves more reaches the day a goal arrives.</strong> The
     * goals are a projection rather than an allocation — the fold never spreads a deposit across
     * them, for the reasons {@link #whatBecomesOfTheGoals} gives — so the only thing that can move a
     * projected Monday is the capacity the projection is made from. A change that paid money in
     * without adding to this figure would move the balance, the points and the run and leave the one
     * date the customer came for exactly where it was, which would read as the simulator being broken
     * rather than as a branch that did nothing.
     *
     * <p><strong>Nothing declared stays nothing declared</strong> until a change adds to it, because
     * an undeclared capacity is a state rather than a zero and {@code SavingCapacityOnAnAccount} is
     * emphatic about the difference: a goal on an account whose holder has said nothing gets no
     * projection at all rather than a bad one. A customer who asks what another twenty-five a week
     * would do has, inside that branch, said a figure — so the branch has a plan where the account
     * has none, and the goals in it have dates. That is the honest reading of the question, and it is
     * the one a page draws a column from.
     */
    private static BigDecimal theWeeklyPlanThisBranchIsFundedBy(TheStartingPoint standing,
                                                                List<AnAdjustment> adjustments) {
        BigDecimal more = BigDecimal.ZERO;
        for (AnAdjustment adjustment : adjustments) {
            more = more.add(adjustment.whatItAddsToTheWeeklyPlan());
        }
        BigDecimal declared = standing.weeklyCapacity().weeklyCapacity();
        if (more.signum() <= 0) {
            return declared;
        }
        return AmountOfMoney.quotedToTheCent(declared == null ? more : declared.add(more));
    }

    /** Whether a day falls inside the window this branch is drawn over, both ends included. */
    private static boolean isInside(LocalDate day, TheStartingPoint standing) {
        return !day.isBefore(standing.asAt()) && !day.isAfter(standing.until());
    }

    /**
     * One branch's ledger while it is being walked: the euros, the lots, the batches and the week,
     * all of them held here and nowhere else.
     *
     * <p>Mutable on purpose and unreachable from outside, which is the bargain a fold makes. Every
     * figure it starts with was copied off the snapshot, so nothing it does can be seen by the
     * snapshot, by another branch folded from the same snapshot, or by the application — the promise
     * that asking is free is kept by there being nothing here to write to.
     */
    private static final class TheBranchAsItGoes {

        private final TheStartingPoint standing;

        /**
         * The changes this branch was asked to imagine, asked of every morning of the walk.
         *
         * <p>Held rather than applied once at the top, because a change is a question about a day: a
         * weekly extra falls on some mornings and not others, and a change a later slice adds falls
         * on days this one has never heard of. Every change is asked every question, so two of them
         * in one scenario compose rather than the later replacing the earlier.
         */
        private final List<AnAdjustment> adjustments;

        /** The savings account's euros. */
        private BigDecimal balance;

        /** What the customer holds across every savings account, and the mark they are judged on. */
        private BigDecimal stillSaved;
        private BigDecimal everEarnedOn;

        /** Every deposit with something left in it, oldest first, because anniversaries are theirs. */
        private final List<ADepositTheBranchHolds> deposits = new ArrayList<>();

        /**
         * What of the balance is interest the bank added rather than money the customer put away.
         *
         * <p><strong>Carried apart from the balance because three rules turn on the
         * difference.</strong> {@code DepositOrigin} says it out loud: money the bank added does not
         * raise the most they have ever saved, does not secure a week and pays no anniversary. It is
         * still money in the account, so it is in {@code balance} and a withdrawal may take it —
         * first, in fact, because the real withdrawal takes the cheapest money first — and when it
         * does, what the customer still holds out of their own money must not fall with it. A branch
         * holding one figure would either pay points on the bank's own interest on the way back up
         * or reprice anniversaries a withdrawal never touched.
         *
         * <p>Worked out on the way in by subtraction rather than read: the snapshot's balance is
         * every row this account holds and its deposits are every row that is not interest, so what
         * is left over is exactly the interest still sitting there. No second read, and nothing to
         * disagree with.
         */
        private BigDecimal interestStillInTheAccount;

        /**
         * Every movement of money this branch's savings account has seen, in cents, as the interest
         * sweep's own walk wants them.
         *
         * <p>Kept so that {@link TheDailyBalancesOfAnAccount} can be <em>asked</em> what a month's
         * average and lowest balance were rather than a second average being worked out here. The
         * first entry is the balance the account opens the walk with, dated the day before the
         * earliest period this branch may still be owed, which is what makes that period read as a
         * month the money was there for all of.
         */
        private final List<AMovementOfMoney> asTheMoneyMoved = new ArrayList<>();

        /**
         * The next monthly period this branch has to judge, counting from one.
         *
         * <p>Started from what the sweep has already posted rather than from the calendar, for the
         * reason the rules' cursors are: a period whose day has come and which has no posting is one
         * the application pays tonight, and a period it has already paid must not be paid twice.
         */
        private int theNextPeriodToJudge;

        /** The everyday accounts the saving comes out of, and the bills they have not managed to pay. */
        private final Map<Long, BigDecimal> everydayBalances = new TreeMap<>();
        private final List<ABillStillOwed> owed = new ArrayList<>();

        /** How many points go on which day: the days off the snapshot, and the ones this fold makes. */
        private final Map<LocalDate, Long> pointsGoingOn = new TreeMap<>();
        private long pointsStanding;

        /** The week being lived through, what has gone into it net, and the run behind it. */
        private SavingsWeek week;
        private BigDecimal netThisWeek;
        private int runOfWeeksBehindThisOne;

        /** What has happened to the points since the last row closed. */
        private long pointsEarnedThisMonth;
        private long pointsABonusPaidThisMonth;
        private long pointsThatExpiredThisMonth;

        /**
         * How many salaries landed in each everyday account this morning, for the rules that fire on
         * one.
         *
         * <p>A count rather than a set, because a run catching up credits every payday it owes in one
         * night and a payday rule falls due once for each of them — which is the whole of what
         * {@code WhichOccurrencesAreDue.theDaysSalaryLandedOn} means when it says a payday is a thing
         * that happened rather than a thing the calendar says.
         */
        private final Map<Long, Integer> paidThisMorning = new HashMap<>();

        /**
         * Where the nightly run has got to with each rule, each income and each bill, moved forward
         * as this walk lives through the days.
         *
         * <p>Started from the real cursors on the snapshot and then kept here, which is what makes
         * the first morning of the walk the one the application has actually reached rather than a
         * morning the fold assumed nobody had touched. A rule the run still owes mornings for owes
         * them to this branch too, and fires them on the first day it walks.
         */
        private final Map<Long, Instant> rulesSettledThrough = new HashMap<>();
        private final Map<Long, Instant> incomePaidThrough = new HashMap<>();
        private final Map<Long, Instant> billsSettledThrough = new HashMap<>();

        /** Every dated thing this branch has produced so far, in the order the walk produced it. */
        private final List<AThingThatHappens> thingsThatHappen = new ArrayList<>();

        private TheBranchAsItGoes(TheStartingPoint standing, List<AnAdjustment> adjustments) {
            this.standing = standing;
            this.adjustments = adjustments;
            this.balance = AmountOfMoney.quotedToTheCent(standing.balance());
            this.stillSaved = AmountOfMoney.quotedToTheCent(standing.theyStillHoldAltogether());
            this.everEarnedOn = AmountOfMoney.quotedToTheCent(standing.theMostEverSaved());
            this.interestStillInTheAccount = whatOfTheBalanceIsInterest(standing);
            this.theNextPeriodToJudge = standing.theProductItIsOn().periodsAlreadyJudged() + 1;
            // The balance the branch opens with, presented to the interest walk as one movement on
            // the day before the earliest period it may still be owed. Dated there rather than on
            // the day the window opens so that a period already under way reads as a month the
            // money was there for all of, which is the supposition the class note names as
            // knowingly approximate and argues for.
            asTheMoneyMoved.add(new AMovementOfMoney(
                    theDayTheOpeningBalanceIsSupposedToHaveStoodFrom(standing).minusDays(1),
                    inCents(this.balance)));
            Instant theWindowOpens = theMomentThatDayBegins(standing.asAt());
            for (DepositStillHoldingMoney deposit : standing.deposits()) {
                // How many anniversaries this deposit has actually been paid, read backwards out of
                // the one Loyalty is promising it next. Not counted off the calendar: an anniversary
                // that fell this lunchtime has passed and has not been paid, and taking the calendar's
                // answer would write off a bonus the sweep pays tonight.
                deposits.add(new ADepositTheBranchHolds(deposit.depositedAt(),
                        AmountOfMoney.quotedToTheCent(deposit.remainingAmount()),
                        howManyAnniversariesTheSweepHasPaid(deposit.depositedAt(),
                                standing.whenEachDepositNextPays().get(deposit.id()), theWindowOpens)));
            }
            for (RecordedSavingRule rule : standing.rules()) {
                // Where the run has got to with this rule. A rule whose cursor is missing — which is
                // a row from before the column existed and nothing else — is taken as settled through
                // the moment it was left standing, which is the same fallback the run itself applies
                // rather than abort a night over one old row.
                Instant cursor = standing.rulesSettledThrough().get(rule.id());
                rulesSettledThrough.put(rule.id(),
                        cursor == null ? rule.createdAt() : cursor);
            }
            for (ACurrentAccountBehindIt account : standing.currentAccounts()) {
                everydayBalances.put(account.currentAccountId(),
                        AmountOfMoney.quotedToTheCent(account.balance()));
                if (account.income().isDeclared()) {
                    Instant cursor = account.incomePaidThrough();
                    incomePaidThrough.put(account.currentAccountId(),
                            cursor == null ? account.income().declaredAt() : cursor);
                }
                for (ADeclaredBill bill : account.bills()) {
                    Instant cursor = account.billsSettledThrough().get(bill.billId());
                    billsSettledThrough.put(bill.billId(),
                            cursor == null ? bill.declaredAt() : cursor);
                }
            }
            for (PointsExpiringOnADay going : standing.pointsGoing()) {
                // A day already gone means points that are going tonight, which is what that record
                // says out loud. Folded onto the opening day rather than dropped, so that a batch
                // waiting on a sweep goes in this branch as it will in the application.
                LocalDate goesOn = going.on().isBefore(standing.asAt()) ? standing.asAt() : going.on();
                pointsGoingOn.merge(goesOn, going.points(), Long::sum);
            }
            this.pointsStanding = standing.pointsStanding();
            this.week = standing.streak().week().week();
            this.netThisWeek = standing.streak().week().newSavings();
            // The run of weeks *behind* the one being lived through, which is what the walk that
            // derived the snapshot's figure would have counted had this week not existed. That
            // derivation counts this week in when it is already secured and steps over it when it is
            // not, so taking one back off a secured week is reading its answer rather than
            // second-guessing it — and it is the figure a Sunday adds one to.
            this.runOfWeeksBehindThisOne = Math.max(0, standing.streak().streak().currentWeeks()
                    - (standing.streak().week().isSecured() ? 1 : 0));
        }

        /**
         * What that night does, in the order the scheduled jobs do it: income at one, the saving
         * rules at two, the bills that fell due at half past, the batches whose twelve months are up
         * at three, the anniversaries at half past three — and then, if it is a Sunday, the week
         * closes.
         *
         * <p>The order is not decoration. A salary landing before the rules fire is what lets a rule
         * move money the morning it arrives; the bills coming after the rules is why a customer who
         * sweeps everything above a floor can still be short of the rent, which is the behaviour the
         * billing job's own documentation insists on; and a batch expiring before an anniversary is
         * paid is what stops a bonus credited this morning from going the same morning.
         *
         * <p>The interest is a quarter of an hour behind the anniversaries, at a quarter to four,
         * which is where its own run is scheduled and is not a detail: a month's interest is paid on
         * what the account held across the month that has just closed, so an anniversary priced
         * after it would be priced on a deposit that had not been paid yet — and it must be in the
         * balance before a withdrawal made during the day can take it.
         *
         * <p>What a branch's own changes put away goes in beside the rules, which is the only place
         * it can honestly go: it is money saved on that day, so it must be in the balance before a
         * batch expires or an anniversary is priced on it, and it must be in the week before the
         * week closes. It is not a step of the night the application runs — there is no scheduled job
         * that pays a customer's intentions in — and it says so where it is written.
         */
        private void theNightOf(LocalDate day) {
            paidThisMorning.clear();
            theSalariesThatLand(day);
            theRulesThatFire(day);
            theExtraTheCustomerWouldPutAwayThemselves(day);
            theBillsThatFellDue(day);
            theBatchesWhoseTwelveMonthsAreUp(day);
            theAnniversariesThatPay(day);
            theInterestThisAccountIsOwed(day);
            theMoneyTheCustomerTakesOut(day);
            if (day.equals(week.endsOn())) {
                theWeekCloses();
            }
        }

        /**
         * One in the morning: every declared salary this account is still owed a payday for, as of
         * this morning.
         *
         * <p>Asked of the income's own cursor rather than of yesterday, which is the correction this
         * slice made. The run counts from the moment it last credited this account and credits
         * everything it finds, so on the morning the window opens it owes nothing it has already paid
         * — and on a clock somebody wound three months without running the job it owes three
         * salaries, all of which land on the first day this walks, exactly as they will land on the
         * next run.
         *
         * <p>{@link WhenIncomeIsDue} is where the month-end clamp lives: a salary on the 31st lands
         * on the 28th in February, and a fold deciding that for itself would be the second place it
         * was decided.
         */
        private void theSalariesThatLand(LocalDate day) {
            Instant thisMorning = theMomentThatDayBegins(day);
            for (ACurrentAccountBehindIt account : standing.currentAccounts()) {
                DeclaredIncome income = account.income();
                if (!income.isDeclared()) {
                    // Nobody has said what lands here, which is an answer rather than an absence and
                    // is Accounts' sentence rather than this fold's. Nothing is credited.
                    continue;
                }
                Instant cursor = incomePaidThrough.get(account.currentAccountId());
                if (cursor == null) {
                    // Declared, and nothing says from when. Nothing can honestly be owed.
                    continue;
                }
                List<LocalDate> paydays = WhenIncomeIsDue.paydaysBetween(income.dayOfMonth(),
                        cursor, thisMorning);
                incomePaidThrough.put(account.currentAccountId(), thisMorning);
                if (paydays.isEmpty()) {
                    continue;
                }
                for (int payday = 0; payday < paydays.size(); payday++) {
                    everydayBalances.merge(account.currentAccountId(),
                            AmountOfMoney.quotedToTheCent(income.amount()),
                            TheBranchAsItGoes::andThen);
                }
                paidThisMorning.merge(account.currentAccountId(), paydays.size(), Integer::sum);
            }
        }

        /**
         * Two in the morning: every occurrence each rule standing on this savings account still owes
         * as of this morning, in the order its holder wrote the rules.
         *
         * <p>Only a live rule fires. A paused rule contributes nothing and is never made up
         * afterwards, which is what a pause already is in this application, and an ended rule is not
         * on the snapshot at all because it is a record rather than an instruction. A paused rule's
         * cursor is left exactly where it is, as the run leaves it, because a resume deliberately
         * does not move it.
         *
         * <p><strong>However many it owes, and not one a morning.</strong> The run fires every
         * occurrence between a rule's cursor and now, oldest first, so a rule three months behind
         * makes thirteen deposits on the night it catches up — and so does this, on the first day it
         * walks. That is not a special case: it is the same question asked with the same cursor, and
         * on an ordinary morning the answer is nothing at all, because the run already fired at two
         * and moved the cursor past the start of today.
         *
         * <p><strong>A morning this branch has stopped saving on fires nothing, and the cursor moves
         * over it.</strong> That is the whole of "a pause is never made up", and it is the run's own
         * arrangement rather than this fold's invention: {@code AutomationService} drops the days
         * that fall inside a {@code RulePause} out of the due range outright and still settles the
         * rule through the moment it ran, so a fortnight paused is a fortnight in which nothing
         * happened and the morning it lifts has no arrears of four Mondays behind it. Leaving the
         * cursor where it was instead would have every occurrence of the stop land in one heap on
         * the day the customer resumed, which is a kind of pause this application does not have and
         * would turn the cheapest two months of the branch into its most expensive morning.
         */
        private void theRulesThatFire(LocalDate day) {
            Instant thisMorning = theMomentThatDayBegins(day);
            boolean stopped = theSavingIsStoppedOn(day);
            for (RecordedSavingRule rule : standing.rules()) {
                if (rule.state() != RuleState.LIVE) {
                    continue;
                }
                if (stopped) {
                    rulesSettledThrough.put(rule.id(), thisMorning);
                    continue;
                }
                int owed = howManyOccurrencesItStillOwes(rule, thisMorning);
                if (owed == 0) {
                    rulesSettledThrough.put(rule.id(), thisMorning);
                    continue;
                }
                if (!everydayBalances.containsKey(rule.currentAccountId())) {
                    // The account this rule draws from is not one of the holder's any more, so there
                    // is no balance to judge it against — the same reading the run takes, which
                    // settles nothing and leaves the occurrence due, cursor and all.
                    continue;
                }
                for (int occurrence = 0; occurrence < owed; occurrence++) {
                    theRuleMoves(rule, day);
                }
                rulesSettledThrough.put(rule.id(), thisMorning);
            }
        }

        /**
         * Whether any change this branch was asked to imagine means nothing at all goes into savings
         * this morning.
         *
         * <p>Any of them rather than the last of them, because two changes in one scenario compose:
         * a customer who asked about two separate stops has stopped on the mornings of both, and one
         * who asked about a stop and another amount each week has told this fold both things at
         * once. Asked once a morning and handed to the two steps that move money into savings, so
         * that the reading cannot differ between them.
         */
        private boolean theSavingIsStoppedOn(LocalDate day) {
            for (AnAdjustment adjustment : adjustments) {
                if (adjustment.itStopsTheSavingOn(day)) {
                    return true;
                }
            }
            return false;
        }

        /** One occurrence of one rule, judged against what the everyday account holds this minute. */
        private void theRuleMoves(RecordedSavingRule rule, LocalDate day) {
            BigDecimal held = everydayBalances.get(rule.currentAccountId());
            BigDecimal wouldMove = WhatARuleWouldMove.outOfABalanceOf(
                    rule.howMuchMoves(), rule.amount(), rule.floor(), held);
            if (wouldMove.signum() <= 0) {
                // A sweep over an account already at or under its floor has no surplus to move.
                return;
            }
            if (held.compareTo(wouldMove) < 0) {
                // A fixed amount moves all of itself or none of it. The run records that it
                // could not be honoured and moves its cursor past it, so the branch does the
                // same: nothing moves and this morning is not tried again.
                return;
            }
            everydayBalances.put(rule.currentAccountId(),
                    AmountOfMoney.quotedToTheCent(held.subtract(wouldMove)));
            theDepositLands(wouldMove, day);
        }

        /**
         * How many occurrences this rule has fallen due for and not yet been settled for, as of this
         * morning.
         *
         * <p>Asked of {@link WhichOccurrencesAreDue} with the rule's own cursor, which is exactly the
         * question the nightly run asks: what has fallen due since this rule was last settled. Open
         * at the bottom and closed at the top, so a day that had already begun when the cursor was
         * written is not its, and one run's last occurrence is never the next run's first. The clamp
         * that puts the 31st on the 28th of February is applied where it is written down rather than
         * here.
         *
         * <p>A rule that fires on payday is the one case the calendar cannot answer, and that class
         * refuses to be asked — a payday rule falls due on the days a salary actually landed, which
         * is a record rather than a calendar. In a branch the record is what this fold credited an
         * hour earlier, so it is read off that and the rule's own cursor is not consulted at all:
         * one salary credited is one occurrence, which is the run's own rule, and it is what keeps a
         * payday rule from being billed for months nobody was paid in.
         */
        private int howManyOccurrencesItStillOwes(RecordedSavingRule rule, Instant thisMorning) {
            if (rule.trigger() == RuleTrigger.ON_PAYDAY) {
                return paidThisMorning.getOrDefault(rule.currentAccountId(), 0);
            }
            return WhichOccurrencesAreDue.daysDueBetween(rule.trigger(), rule.dayOfWeek(),
                    rule.dayOfMonth(), rulesSettledThrough.get(rule.id()), thisMorning).size();
        }

        /**
         * Beside the rules: whatever this branch's own changes would put away this morning, paid in
         * through the very same restated deposit rule a rule's transfer goes through.
         *
         * <p><strong>Every change is asked, and each is asked once a day.</strong> A change that puts
         * nothing away on this morning answers with nothing, which is what {@link AnAdjustment}'s
         * default says, so this loop is what a later kind that pays money in plugs into without
         * touching anything else — and two changes that both pay in on one morning are two deposits
         * rather than one, in the order the customer typed them.
         *
         * <p><strong>It is not judged against the everyday account, and that is the decision worth
         * reading twice.</strong> A saving rule moves money out of a current account and moves
         * nothing at all when the money is not there; this is the customer saying what they could put
         * away, which is the same sentence a weekly saving capacity is — and it is why adopting such
         * a change raises that capacity rather than standing a rule up. A branch that made a customer
         * prove their sacrifice against a modelled salary would be answering a question about their
         * income when they asked one about their resolve, and it would have a fold deciding
         * affordability, which no rule in this application has asked it to decide.
         *
         * <p>Which week the euros land in, what of them is new saving and what run they are paid at
         * are all {@link #theDepositLands}'s answers rather than this method's, for the reason that
         * method exists: those three rules are stated once in this fold and once only.
         *
         * <p><strong>Nothing is put away on a morning the branch has stopped saving on</strong>, and
         * that is where a stop and another amount each week compose. A stop is a customer saying
         * nothing goes into savings between two days; an extra is the same customer saying what they
         * would put away each week. They meant both, and a fold that paid the extra in through the
         * stop would show a branch whose pause broke no week at all.
         */
        private void theExtraTheCustomerWouldPutAwayThemselves(LocalDate day) {
            if (theSavingIsStoppedOn(day)) {
                // A stop and an extra in one scenario are a customer who meant both, and the reading
                // that honours both is that the weeks inside the stop take in nothing — which is
                // what breaks the run and is the whole of what they asked to be shown. Paying the
                // extra in anyway would answer with a stop that cost them nothing.
                return;
            }
            for (AnAdjustment adjustment : adjustments) {
                BigDecimal extra = adjustment.whatItAlsoPaysInOn(day);
                if (extra.signum() > 0) {
                    theDepositLands(AmountOfMoney.quotedToTheCent(extra), day);
                }
            }
        }

        /**
         * Half past two: what is still owed, oldest first, and then what fell due this morning.
         *
         * <p>Outstanding first is the whole of the rule, and it is the billing run's own: what is
         * already owed is settled out of whatever the account holds before a newly due date is
         * allowed to look at it, so March's rent is paid before April's. A bill is all or nothing —
         * an account that cannot cover it has nothing taken and the date stays owed — and a bill
         * whose amount is not an amount of money is passed over rather than guessed at.
         *
         * <p>Which dates have fallen due is asked of each bill's own cursor, for the reason the
         * salary above gives: the rent taken at half past two this morning must not be taken twice,
         * and a run three months behind presents three months of rent on the night it catches up.
         */
        private void theBillsThatFellDue(LocalDate day) {
            for (Iterator<ABillStillOwed> arrears = owed.iterator(); arrears.hasNext(); ) {
                if (takeIfItIsThere(arrears.next())) {
                    arrears.remove();
                }
            }
            Instant thisMorning = theMomentThatDayBegins(day);
            for (ACurrentAccountBehindIt account : standing.currentAccounts()) {
                for (ADeclaredBill bill : account.bills()) {
                    if (bill.state() != BillState.STANDING
                            || bill.amount() == null || bill.amount().signum() <= 0) {
                        continue;
                    }
                    Instant cursor = billsSettledThrough.get(bill.billId());
                    if (cursor == null) {
                        continue;
                    }
                    List<LocalDate> due = WhenABillIsDue.dueDatesBetween(bill.dayOfMonth(),
                            cursor, thisMorning);
                    billsSettledThrough.put(bill.billId(), thisMorning);
                    for (int date = 0; date < due.size(); date++) {
                        ABillStillOwed presented = new ABillStillOwed(bill.currentAccountId(),
                                AmountOfMoney.quotedToTheCent(bill.amount()));
                        if (!takeIfItIsThere(presented)) {
                            owed.add(presented);
                        }
                    }
                }
            }
        }

        /** All of it or none of it, out of the everyday account the bill stands against. */
        private boolean takeIfItIsThere(ABillStillOwed bill) {
            BigDecimal held = everydayBalances.get(bill.currentAccountId());
            if (held == null || held.compareTo(bill.amount()) < 0) {
                return false;
            }
            everydayBalances.put(bill.currentAccountId(),
                    AmountOfMoney.quotedToTheCent(held.subtract(bill.amount())));
            return true;
        }

        /** Three in the morning: the points whose twelve months are up today go. */
        private void theBatchesWhoseTwelveMonthsAreUp(LocalDate day) {
            Long going = pointsGoingOn.remove(day);
            if (going == null || going == 0) {
                // A day on which no points move is not a thing that happens, which is the reading
                // the account's own bar already gives about its own markers.
                return;
            }
            pointsStanding -= going;
            pointsThatExpiredThisMonth += going;
            thingsThatHappen.add(AThingThatHappens.worth(day,
                    AKindOfThingThatHappens.POINTS_EXPIRE, going));
        }

        /**
         * Half past three: every deposit whose next anniversary falls today pays, on the whole euros
         * still in it, at the rate the account's own product names.
         *
         * <p><strong>The rate is the agreement's and is no longer a tenth.</strong> It comes off
         * {@link TheStartingPoint}'s reading of the account's whole agreement, as a fraction of a
         * point per whole euro, and it is the same figure {@code LoyaltyService} pays the real
         * anniversary at. A fold still pricing at the flat tenth would quietly promise a
         * twelve-month fixed-term customer two thirds of what they are actually owed, and nothing
         * would have objected, because a tenth is a perfectly good rate.
         *
         * <p>What it pays is {@link LoyaltyRate}'s answer, on what the deposit <em>still holds</em>
         * rather than on what landed in it — which is what makes a withdrawal in a branch reprice
         * every anniversary ahead of it. When the anniversary falls is {@link
         * LoyaltyAnniversary}'s, counted from the moment the money landed every time rather than
         * from the last one paid, so a leap-day deposit's fourth anniversary is on the 29th again.
         *
         * <p>The batch it credits is dated at the anniversary, exactly as the sweep dates it, which
         * is why it can itself expire twelve months later — and why an anniversary falling on the
         * opening day leaves a batch that goes on the last day this walks.
         *
         * <p><strong>An anniversary the sweep is late with is paid on the first day this walks.</strong>
         * The snapshot says which anniversary each deposit is <em>owed</em> next, and that day can be
         * one already gone — an anniversary arrives at whatever time of day the money landed and the
         * sweep runs at half past three the following morning. The application pays it tonight, so
         * this branch pays it on the morning the window opens, which is the same reading the expiring
         * batches already get. A deposit owed more than one is paid all of them that morning, which
         * is what a sweep catching up does, and the loop keeps going until the anniversary it reaches
         * is one that has not fallen.
         */
        private void theAnniversariesThatPay(LocalDate day) {
            for (ADepositTheBranchHolds deposit : deposits) {
                while (true) {
                    int ordinal = deposit.anniversariesPaid + 1;
                    Instant anniversary = LoyaltyAnniversary.anniversaryOf(deposit.landedAt, ordinal);
                    if (!theDayItIsActuallyPaidOn(LoyaltyAnniversary.dayOf(anniversary)).equals(day)) {
                        break;
                    }
                    deposit.anniversariesPaid = ordinal;
                    long bonus = LoyaltyRate.pointsOn(LoyaltyRate.wholeEurosIn(deposit.stillHolding),
                            standing.theProductItIsOn().anniversaryRatePerWholeEuro());
                    if (bonus == 0) {
                        // A deposit holding less than one whole point's worth is nothing on its
                        // anniversary — ten euros at a tenth, nine at twelve percent, and no amount
                        // at all under a product that pays nothing for staying put, which is
                        // LoyaltyRate.theLeastABonusIsPaidOn's answer about the rate handed in
                        // rather than a threshold written down here. No batch is written
                        // and nothing is dated: an anniversary worth nothing is not an event, which
                        // is the sweep's own reading and the account bar's.
                        continue;
                    }
                    theBatchLands(bonus, anniversary);
                    pointsABonusPaidThisMonth += bonus;
                    thingsThatHappen.add(AThingThatHappens.worth(day,
                            AKindOfThingThatHappens.A_BONUS_IS_PAID, bonus));
                }
            }
        }

        /**
         * A quarter to four: every monthly period of this account that has closed and has not been
         * paid is paid, at the rate that period earned, on the average of the balances the branch
         * actually stood at across it.
         *
         * <p><strong>Every figure in it is quoted and not one of them is worked out here.</strong>
         * Which day a period closes on is {@link TheMonthlyPeriodsOfAnAccount}'s, counted from the
         * day the agreement began; what the account held on average and at its lowest across it is
         * {@link TheDailyBalancesOfAnAccount}'s walk over the movements this branch has made, which
         * is the same walk the sweep makes over the ledger's own; whether the month earned the
         * bonus rate is {@link TheRateAPeriodIsPaidAt}'s judgement, asked once per projected month
         * exactly as the sweep asks it once per real month; and what the rate comes to over a month
         * is {@link WhatAnAnnualRateIsWorth}, flooring and all. A fold that worked out a twelfth of
         * its own would be the second place a rate is turned into euros, and the customer would be
         * promised one figure here and paid another by the bank.
         *
         * <p><strong>However many it owes, and not one a month.</strong> The sweep pays every period
         * that has closed and has no posting, so a clock somebody wound six months without running
         * the jobs is six months paid on the night it catches up — and so is the first day this
         * walks. That is the same cursor reading the saving rules and the anniversaries already get.
         *
         * <p><strong>A period that began before this bank started paying interest on the account
         * earns nothing and is stepped over</strong>, exactly as the sweep steps over it and writes
         * no posting: no interest is backdated over the day an account was migrated onto the
         * catalogue, which is the whole of that rule.
         *
         * <p>A month worth nothing at all moves no money and is not dated. The sweep writes a
         * posting for it all the same, because a posting is how it knows not to judge that month
         * again; a branch has its own cursor and nothing to write to.
         */
        private void theInterestThisAccountIsOwed(LocalDate day) {
            WhatAnAccountsProductPaysAndAsksFor product = standing.theProductItIsOn();
            if (product.openedOn() == null || product.interestCountsFrom() == null) {
                // An account with no agreement on record, or one this bank has never started
                // paying interest on. Nothing is owed and nothing is guessed at.
                return;
            }
            int closed = TheMonthlyPeriodsOfAnAccount.periodsGoneBy(product.openedOn(), day);
            while (theNextPeriodToJudge <= closed) {
                int ordinal = theNextPeriodToJudge;
                theNextPeriodToJudge++;
                LocalDate from = TheMonthlyPeriodsOfAnAccount.beginningOf(product.openedOn(), ordinal);
                LocalDate until = TheMonthlyPeriodsOfAnAccount.endOf(product.openedOn(), ordinal);
                if (from.isBefore(product.interestCountsFrom())) {
                    continue;
                }
                TheBalanceAcrossAPeriod held =
                        TheDailyBalancesOfAnAccount.across(asTheMoneyMoved, from, until);
                WhatAPeriodEarned earned = TheRateAPeriodIsPaidAt.forAPeriodWhoseLowestBalanceWas(
                        product.annualRateBasisPoints(), product.bonusRateBasisPoints(),
                        product.minimumBalanceCents(), held.lowestCents());
                long cents = WhatAnAnnualRateIsWorth.overAMonth(held.averageCents(),
                        earned.annualRateBasisPoints());
                // One line per projected month with the four figures that decided it, because "why
                // does this branch end on more money than that one" is a question about a rate and
                // a floor rather than about a balance, and a fold that paid a month without its
                // bonus has to be able to say which month and on what lowest balance.
                log.debug("a branch is paid a month of interest savingsAccountId={} period={} "
                                + "from={} until={} days={} averageDailyBalance={} "
                                + "lowestDailyBalance={} floor={} rateBasisPoints={} "
                                + "bonusEarned={} interest={} paidOn={}",
                        standing.savingsAccountId(), ordinal, from, until, held.days(),
                        held.averageCents(), held.lowestCents(), product.minimumBalanceCents(),
                        earned.annualRateBasisPoints(), earned.bonusEarned(), cents, day);
                if (cents == 0) {
                    continue;
                }
                theInterestLands(AmountOfMoney.quotedToTheCent(BigDecimal.valueOf(cents, 2)), day);
            }
        }

        /**
         * A month's interest arriving in the branch: the balance grows and nothing else does.
         *
         * <p><strong>The three things it deliberately does not touch</strong> are the three
         * {@code DepositOrigin} names, and each of them would be a different lie. It does not raise
         * {@code everEarnedOn}, or the customer would earn nothing on the next euros of their own
         * they put away. It does not raise {@code stillSaved}, which is what they hold out of their
         * own money and is one half of the subtraction the mark is judged by. It does not go into
         * {@code netThisWeek}, or the bank would be securing the customer's weeks for them. And it
         * makes no deposit, so it starts no loyalty clock and pays no anniversary of its own — a
         * bank paying points on the points it has already paid.
         */
        private void theInterestLands(BigDecimal paid, LocalDate day) {
            balance = AmountOfMoney.quotedToTheCent(balance.add(paid));
            interestStillInTheAccount =
                    AmountOfMoney.quotedToTheCent(interestStillInTheAccount.add(paid));
            asTheMoneyMoved.add(new AMovementOfMoney(day, inCents(paid)));
        }

        /**
         * The day this branch actually acts on a promised day: the day itself, or the day the window
         * opens when the promise has already fallen and nothing has kept it.
         *
         * <p>The same fold the expiring batches get, said once so that a bonus owed and a batch owed
         * are dealt with on the same morning and in the order the night deals with them.
         */
        private LocalDate theDayItIsActuallyPaidOn(LocalDate promised) {
            return promised.isBefore(standing.asAt()) ? standing.asAt() : promised;
        }

        /**
         * During the day, after everything the night did: whatever this branch's own changes would
         * take back out of savings.
         *
         * <p><strong>Last, and that is the night's order rather than a convenience.</strong> There
         * is no scheduled job that takes a customer's money out — a withdrawal is a thing somebody
         * does at a cashpoint — so it lands after the rules have fired, after the bills, after the
         * batches have gone and after the anniversaries have been priced on what the deposits held
         * this morning, and before the week is judged on what went into it. A Sunday withdrawal is
         * part of that Sunday's week, which is exactly how a week that was going to be secured stops
         * being secured.
         *
         * <p><strong>What is free is what no goal has spoken for, and asking for more is an outcome
         * rather than a refusal.</strong> The branch takes what there is, dates what it managed and
         * carries on — refusing would make the most interesting question in the set unaskable, which
         * is the argument {@link TakingMoneyOut} makes at length. Every change is asked, so two
         * withdrawals on one morning are two withdrawals and the second is weighed against what the
         * first left.
         */
        private void theMoneyTheCustomerTakesOut(LocalDate day) {
            for (AnAdjustment adjustment : adjustments) {
                BigDecimal asked = adjustment.whatItTakesOutOn(day);
                if (asked.signum() <= 0) {
                    continue;
                }
                BigDecimal free = whatNoGoalHasSpokenFor();
                BigDecimal taken = AmountOfMoney.quotedToTheCent(asked.min(free));
                if (taken.signum() > 0) {
                    theMoneyLeaves(taken, day);
                }
                if (taken.compareTo(asked) < 0) {
                    // What it managed rather than what it was short by, because that is the figure
                    // the customer's balance actually moved by and the one the next sentence on the
                    // screen is about. An outcome and not a refusal, so nothing is thrown and
                    // nothing is warned: the branch goes on to the rest of the year.
                    thingsThatHappen.add(new AThingThatHappens(day,
                            AKindOfThingThatHappens.A_WITHDRAWAL_FALLS_SHORT, taken));
                }
                // One line per withdrawal, which is not one line per day: a branch has one or two of
                // these in a year and each of them is the event the whole column is about. Both
                // figures are on it, because "asked=500.00 took=120.00" is the half of the answer a
                // balance that fell by a hundred and twenty cannot be checked against.
                log.debug("a branch takes money out savingsAccountId={} on={} asked={} free={} "
                                + "took={} balance={} netThisWeek={}",
                        standing.savingsAccountId(), day, AmountOfMoney.asMoney(asked),
                        AmountOfMoney.asMoney(free), AmountOfMoney.asMoney(taken),
                        AmountOfMoney.asMoney(balance), AmountOfMoney.asMoney(netThisWeek));
            }
        }

        /**
         * What a withdrawal in this branch may take: the branch's own balance less what the goals
         * have claimed, and never less than nothing.
         *
         * <p><strong>The same subtraction {@code WithdrawalsService} makes</strong>, which is the
         * point — a goal's claim stops a withdrawal in a branch exactly as it stops one in the
         * application, and a fold that weighed a withdrawal against the balance would tell a
         * customer they could take money their goals are holding. What the goals have claimed is the
         * snapshot's figure and does not move inside a branch: a branch never allocates a deposit to
         * a goal, for the reasons {@link #whatBecomesOfTheGoals} gives at length, so nothing in a
         * year of walking can change what has been spoken for.
         *
         * <p>The <em>balance</em> is the branch's own rather than the snapshot's, and the difference
         * only shows in a branch that has moved money before the withdrawal day: what the rules paid
         * in since the window opened is free money by the same subtraction, and what an earlier
         * withdrawal already took is gone from it. In a branch that does nothing else this is the
         * unallocated figure the snapshot carried, to the cent.
         *
         * <p>Floored at nothing, although {@code AllocationsOnAnAccount} deliberately reports a
         * negative unallocated as it falls: an account whose balance has dropped below what its
         * goals claimed is a real state of affairs somebody has to see on their own screen, and it
         * is still nothing at all to take out.
         */
        private BigDecimal whatNoGoalHasSpokenFor() {
            BigDecimal claimed = standing.goals().allocated();
            BigDecimal free = balance.subtract(claimed == null ? BigDecimal.ZERO : claimed);
            return free.max(BigDecimal.ZERO);
        }

        /**
         * The money leaving the branch: the balance, what the customer still holds, the week's net
         * total, and the deposits it is drawn from.
         *
         * <p><strong>The mark is not in that list, and that is the rule rather than an
         * oversight.</strong> {@code TheMostEverSaved} says out loud that taking money out leaves
         * the high-water mark exactly where it was — points earned are the customer's and are never
         * taken back — so what a branch pays in afterwards earns on nothing until it has climbed
         * back to where it already was. That is the fifth rule the problem statement is about, and
         * it falls out of this method saying nothing about {@code everEarnedOn}: the deposit that
         * comes later asks {@link #theDepositLands}, which asks the mark, which has not moved.
         *
         * <p><strong>What the week counts is net</strong>, which is {@code NewSavingsThisWeek}'s own
         * rule and the reason it can go below nothing: a week that took fifty in and let five
         * hundred back out has put four hundred and fifty <em>less</em> than nothing away, and a
         * figure clamped at zero would have the next deposit securing a week it has not secured.
         */
        private void theMoneyLeaves(BigDecimal taken, LocalDate day) {
            // The cheapest money first, which is the order the real withdrawal draws its rows down
            // in: what the bank added before what the customer saved. It is what stops a customer
            // who takes a month's interest back out from losing a year of anniversaries on a
            // deposit the withdrawal never reached.
            BigDecimal outOfTheInterest = taken.min(interestStillInTheAccount);
            BigDecimal outOfTheDeposits = taken.subtract(outOfTheInterest);
            balance = AmountOfMoney.quotedToTheCent(balance.subtract(taken));
            interestStillInTheAccount =
                    AmountOfMoney.quotedToTheCent(interestStillInTheAccount.subtract(outOfTheInterest));
            // Only the part that came out of the customer's own money, because that is what the
            // figure means: interest was never in it, so taking interest out cannot take it down.
            stillSaved = AmountOfMoney.quotedToTheCent(stillSaved.subtract(outOfTheDeposits));
            // The week counts the whole of what left, whatever it was drawn from. A week is what the
            // customer has actually put away by the end of it, and five hundred euros leaving the
            // account is five hundred euros leaving the account — which is the reading
            // WeekAndStreakDerivation already takes, where a withdrawal is netted off without
            // anybody asking which rows it emptied.
            netThisWeek = new NewSavingsThisWeek(week, netThisWeek.subtract(taken),
                    whatThisWeekAsksFor()).newSavings();
            asTheMoneyMoved.add(new AMovementOfMoney(day, -inCents(taken)));
            theOldestDepositsGoFirst(outOfTheDeposits, day);
        }

        /**
         * The deposits the money actually comes out of: the one that has been there longest first,
         * until the amount is met.
         *
         * <p><strong>This is what makes a withdrawal cost something rather than merely subtract
         * something.</strong> An anniversary pays a tenth of the whole euros a deposit
         * <em>still holds</em>, so which deposits a withdrawal empties decides which anniversaries
         * ahead of it are still worth anything — and taking the oldest first is what the real
         * withdrawal does, ordered by the moment the money landed. A branch that drew the newest
         * down first, or that simply lowered a balance and left the deposits alone, would show the
         * customer a year of bonuses the application is not going to pay them.
         *
         * <p>Sorted rather than trusted to the order of the list, although the list is in fact
         * oldest first — the snapshot's deposits arrive that way from Deposits' own read and the
         * walk appends the ones it makes in the order the days go by. Asking the question the rule
         * actually asks costs one sort on the handful of mornings a year anybody takes money out,
         * and it cannot be quietly wrong the day somebody changes how the list is built. Stable, so
         * two deposits that landed at the same moment stay in the order they were handed over, which
         * is the identifier order the real withdrawal falls back on.
         *
         * <p>A deposit emptied to nothing is left in the list rather than removed. It pays nothing on
         * its anniversaries from here on, because a tenth of no whole euros is no points and an
         * anniversary worth nothing is not an event — which is the same answer the application gives,
         * where an emptied deposit drops out of the read the loyalty sweep works from.
         */
        private void theOldestDepositsGoFirst(BigDecimal taken, LocalDate day) {
            if (taken.signum() <= 0) {
                // The whole of it came out of the interest the bank had added, so no deposit was
                // touched and no anniversary ahead of it is repriced.
                return;
            }
            BigDecimal stillToTake = taken;
            List<String> drawnDown = new ArrayList<>();
            for (ADepositTheBranchHolds deposit : deposits.stream()
                    .sorted(Comparator.comparing((ADepositTheBranchHolds held) -> held.landedAt))
                    .toList()) {
                if (stillToTake.signum() <= 0) {
                    break;
                }
                BigDecimal outOfThisOne = deposit.stillHolding.min(stillToTake);
                if (outOfThisOne.signum() <= 0) {
                    continue;
                }
                deposit.stillHolding =
                        AmountOfMoney.quotedToTheCent(deposit.stillHolding.subtract(outOfThisOne));
                stillToTake = stillToTake.subtract(outOfThisOne);
                drawnDown.add("[landedAt=" + deposit.landedAt + " took="
                        + AmountOfMoney.asMoney(outOfThisOne) + " leftInIt="
                        + AmountOfMoney.asMoney(deposit.stillHolding) + "]");
            }
            // Which deposits it came out of and what is left in each, in the order it came out of
            // them, said at the moment it is decided rather than inferred a year later from an
            // anniversary that paid less than somebody expected. The shape is the one the real
            // withdrawal already logs, so the two can be read side by side.
            log.debug("a branch drew the oldest deposits down first savingsAccountId={} on={} "
                            + "took={} shortBy={} drawnDown={}",
                    standing.savingsAccountId(), day, AmountOfMoney.asMoney(taken),
                    AmountOfMoney.asMoney(stillToTake), String.join(" ", drawnDown));
        }

        /**
         * A deposit landing inside the branch: what of it is new saving, what run of weeks it is
         * paid at, and what that comes to in points.
         *
         * <p><strong>This is the one rule in this feature that is restated rather than
         * called</strong>, because {@code DepositsService.deposit} reads the clock and writes the
         * ledger. It is the three steps that method applies, in the order it applies them, and each
         * is named against the line it came from:
         *
         * <ol>
         * <li><strong>The mark.</strong> {@code TheMostEverSaved.newSavingIn(amount, stillSaved,
         * everEarnedOn)}, with both figures read <em>before</em> this deposit is counted, exactly as
         * that method reads them before the row is saved. Points are paid on what this takes the
         * customer above the most they have ever held, and on nothing else.</li>
         * <li><strong>The run, including the week this deposit may have just secured.</strong> That
         * method records the deposit and only then walks the weeks, so a deposit that carries its
         * week past the weekly minimum is already priced at the longer run. Here the week's net
         * total takes the deposit first and the run is read after it, which is the same order and
         * the same answer. Note what the week counts: the <em>amount</em>, not the new saving — a
         * week is judged on what was put away, and whether those euros had been paid for before is a
         * different question with a different answer.</li>
         * <li><strong>The double flooring.</strong> {@code PointsService.creditPointsFor} floors the
         * euros of the new saving, multiplies the whole euros by the rate, and floors again; the base
         * lot is the whole euros and the streak lot is the difference. Flooring once at the end pays
         * a different figure on any amount with cents in it, which is why both are here.</li>
         * </ol>
         *
         * <p>The batch it credits is dated at the start of the day, and its lifetime is
         * {@link PointsExpiry}'s to count, out of the figure the snapshot was handed.
         */
        private void theDepositLands(BigDecimal amount, LocalDate day) {
            // 1. Which euros of it have not been paid for before.
            BigDecimal newSaving = TheMostEverSaved.newSavingIn(amount, stillSaved, everEarnedOn);

            // The euros of it that earned nothing at all, because the mark has seen them. Dated
            // before anything else this method does, because it is a fact about the amount rather
            // than about what the amount then went on to earn — and it is the sentence the whole
            // feature exists to put in front of somebody before they take money out rather than
            // after. A branch where none of it is new saving and a branch where half of it is are
            // both this event; what differs is the figure.
            BigDecimal earnedNothing = amount.subtract(newSaving);
            if (earnedNothing.signum() > 0) {
                thingsThatHappen.add(new AThingThatHappens(day,
                        AKindOfThingThatHappens.MONEY_ARRIVES_AND_EARNS_NOTHING,
                        AmountOfMoney.quotedToTheCent(earnedNothing)));
            }

            balance = AmountOfMoney.quotedToTheCent(balance.add(amount));
            stillSaved = AmountOfMoney.quotedToTheCent(stillSaved.add(amount));
            everEarnedOn = AmountOfMoney.quotedToTheCent(everEarnedOn.add(newSaving));
            deposits.add(new ADepositTheBranchHolds(theMomentThatDayBegins(day), amount, 0));
            asTheMoneyMoved.add(new AMovementOfMoney(day, inCents(amount)));

            // 2. The week with this deposit counted into it, the run that week belongs to, and what
            // the account's own product pays on top of it. The two factors multiply rather than
            // adding and are floored once at the end, which is TheRateADepositIsPaidAt's rule and
            // not this fold's — the same call DepositsService makes, with the same two arguments.
            netThisWeek = new NewSavingsThisWeek(week, netThisWeek.add(amount),
                    whatThisWeekAsksFor()).newSavings();
            BigDecimal multiplier = TheRateADepositIsPaidAt.combining(
                    StreakMultiplier.paidByAStreakOf(theRunAsItStands(),
                            TheLadderARunClimbs.theLadderIn(theSchemeJudgingThisWeek())),
                    standing.theProductItIsOn().pointsMultiplier());

            // 3. Floor the euros, multiply by the rate, floor again.
            long wholeEuros = newSaving.setScale(POINTS_ARE_WHOLE, RoundingMode.FLOOR).longValueExact();
            long paidAtTheRate = BigDecimal.valueOf(wholeEuros)
                    .multiply(multiplier)
                    .setScale(POINTS_ARE_WHOLE, RoundingMode.FLOOR)
                    .longValueExact();
            if (paidAtTheRate > 0) {
                theBatchLands(paidAtTheRate, theMomentThatDayBegins(day));
            }
            pointsEarnedThisMonth += paidAtTheRate;
        }

        /**
         * A batch of points arriving in the branch, put on the same schedule as the ones the
         * snapshot was handed.
         *
         * <p>When it goes is {@link PointsExpiry}'s answer about the moment it was earned, never
         * twelve months added here. A batch earned after the opening day goes after the window
         * closes and is never seen again; one earned <em>on</em> the opening day goes on the last day
         * this walks, and putting every batch on the same schedule is what makes that fall out
         * rather than need catching.
         *
         * <p><strong>And with the lifetime the ledger itself would have used</strong>, which arrives
         * on the snapshot. That rule no longer knows how long a batch lasts — the figure is published
         * in the scheme and applied by Points — so calling it is only half of not restating it: a
         * fold that called the rule with twelve of its own would predict a batch going on a day the
         * application would never take it, the moment the bank published anything else. The
         * snapshot's figure is the ledger's own answer, and {@link TheStartingPoint} argues at length
         * why it comes from Points rather than off the scheme.
         */
        private void theBatchLands(long points, Instant earnedAt) {
            pointsStanding += points;
            pointsGoingOn.merge(
                    PointsExpiry.dayOf(PointsExpiry.anniversaryOf(earnedAt,
                            standing.howLongABatchOfPointsLasts())),
                    points, Long::sum);
        }

        /**
         * Sunday night: the week is judged on what was net put away in it, and the run either grows
         * by one or goes back to nothing.
         *
         * <p>Net is the rule and {@link NewSavingsThisWeek} owns it: what went in less what came
         * back out, because a week is what the customer has actually put away by the end of it. A
         * run does not shorten by a week — it ends, which is what makes six consecutive weeks worth
         * something.
         */
        private void theWeekCloses() {
            if (NewSavingsThisWeek.securedBy(netThisWeek, whatThisWeekAsksFor())) {
                runOfWeeksBehindThisOne++;
            } else {
                if (runOfWeeksBehindThisOne > 0) {
                    // What ended, rather than that a week went by. A customer who has secured
                    // nothing does not lose a run every Sunday, and fifty-two of those would bury
                    // the half-dozen events a decision actually turns on. The figure is the run that
                    // was lost; what the rate drops back to is StreakMultiplier's answer about
                    // nought weeks and is not worth saying twice.
                    thingsThatHappen.add(AThingThatHappens.worth(week.endsOn(),
                            AKindOfThingThatHappens.A_WEEK_IS_LOST, runOfWeeksBehindThisOne));
                }
                runOfWeeksBehindThisOne = 0;
            }
            week = new SavingsWeek(week.startsOn().plusWeeks(1));
            netThisWeek = BigDecimal.ZERO;
        }

        /**
         * The run of consecutive secured weeks as the application would report it right now: the
         * weeks behind this one, and this one too once enough has landed in it.
         *
         * <p>The same reading the derivation gives on the account's own screen, which matters twice
         * over — it is the rate a deposit made this minute is paid at, and it is the figure a month
         * row can be put beside the application's own after a clock has been wound to that day.
         */
        private int theRunAsItStands() {
            return runOfWeeksBehindThisOne
                    + (NewSavingsThisWeek.securedBy(netThisWeek, whatThisWeekAsksFor()) ? 1 : 0);
        }

        /**
         * The version of the scheme the week being lived through would be judged under, asked of the
         * history the snapshot carries.
         *
         * <p><strong>Per week and never once at the top, which is the same rule the application's own
         * derivation follows and the reason the snapshot carries a history at all.</strong> A branch
         * is fifty-two weeks long. A version the bank has already announced for a Monday three months
         * out takes effect three months out, inside the window, and a fold that had resolved one
         * scheme on the opening morning would go on judging weeks and pricing deposits at the old
         * figures for the rest of the year — showing the customer a future the application would not
         * give them, in the one place they came to find out what their future looks like.
         *
         * <p>Asked rather than cached against the week, because it is a walk over a handful of
         * published rows and the day this became worth memoising is the day the bank publishes
         * thousands of versions of its savings scheme.
         */
        private TheSchemeAsPublished theSchemeJudgingThisWeek() {
            return standing.theSchemeEachWeekIsJudgedUnder().forTheWeekOf(week);
        }

        /**
         * What the week being lived through has to take in, net, to secure itself.
         *
         * <p>Named on its own because three places in this fold need it — the two that requote the
         * week's running total to the cent, and the Sunday night that judges it — and a fold that
         * reached for a constant in any one of them would be judging a branch by a figure the bank
         * may have stopped using. It is the threshold, not a comparison: what secures a week is
         * {@link NewSavingsThisWeek}'s to decide and is asked of it.
         */
        private BigDecimal whatThisWeekAsksFor() {
            return theSchemeJudgingThisWeek().weeklyThreshold();
        }

        /** The row for the month that has just ended, and the counters start again from nothing. */
        private AMonthOfTheFuture theMonthJustClosed(LocalDate closesOn) {
            AMonthOfTheFuture month = new AMonthOfTheFuture(YearMonth.from(closesOn), closesOn,
                    balance, pointsStanding, theRunAsItStands(), pointsEarnedThisMonth,
                    pointsABonusPaidThisMonth, pointsThatExpiredThisMonth);
            pointsEarnedThisMonth = 0;
            pointsABonusPaidThisMonth = 0;
            pointsThatExpiredThisMonth = 0;
            return month;
        }

        /** Everything dated the walk itself produced, in the order the nights produced it. */
        private List<AThingThatHappens> thingsThatHappenedInTheNight() {
            return thingsThatHappen;
        }

        private static BigDecimal andThen(BigDecimal held, BigDecimal arriving) {
            return AmountOfMoney.quotedToTheCent(held.add(arriving));
        }
    }

    /**
     * How many of a deposit's anniversaries the sweep has actually paid, read backwards out of the
     * one Loyalty says it is owed next.
     *
     * <p><strong>Not {@code anniversariesPassedBy}, and the difference is a bonus.</strong> That
     * function is a calendar reading and says how many anniversaries have <em>fallen</em>. What this
     * fold needs is how many have been <em>paid</em>, because one that fell this lunchtime and is
     * waiting on tonight's sweep is money the application is about to credit and that a branch must
     * credit too. Only Loyalty knows which have rows under them, and {@code
     * LoyaltyService.whenTheDepositsInAnAccountNextPay} is where it says so: the day it answers with
     * is the earliest anniversary this deposit is owed and has not been paid, and it is deliberately
     * a day in the past exactly while a payment is outstanding.
     *
     * <p>Turned back into an ordinal by walking this deposit's anniversaries until one falls on that
     * day — one step per year the deposit has been open, on a handful of deposits. Walking rather
     * than dividing, for the reason that class gives about its own walk: twelve months either side
     * of a leap day are not the same arithmetic, and the one function that knows it is the one being
     * asked.
     *
     * <p>A deposit Loyalty said nothing about, and one whose promised day does not match any
     * anniversary — which is what a deposit worth nothing gets, because an anniversary that pays
     * nothing writes no row and is indistinguishable from one never reached — is taken as having
     * been paid everything the calendar has already passed. That is the reading that cannot pay a
     * customer twice, and for a deposit worth nothing it costs nothing either way.
     */
    private static int howManyAnniversariesTheSweepHasPaid(Instant landedAt,
                                                           NextAnniversaryOfADeposit owedNext,
                                                           Instant theWindowOpens) {
        int theCalendarsNext = LoyaltyAnniversary.anniversariesPassedBy(landedAt, theWindowOpens) + 1;
        if (owedNext == null) {
            return theCalendarsNext - 1;
        }
        for (int ordinal = 1; ordinal <= theCalendarsNext; ordinal++) {
            if (LoyaltyAnniversary.dayOf(LoyaltyAnniversary.anniversaryOf(landedAt, ordinal))
                    .equals(owedNext.on())) {
                return ordinal - 1;
            }
        }
        return theCalendarsNext - 1;
    }

    /**
     * How much of what this account holds is interest the bank added rather than money its holder
     * put away.
     *
     * <p>By subtraction, and deliberately not by a read of its own. The balance is every row the
     * account holds; the deposits on the snapshot are every row that is <em>not</em> interest —
     * {@code DepositRepository} says so where that query is written, and says why — so the
     * difference is exactly what interest is still sitting there. A second read asking Deposits for
     * the same figure would be a second answer to one question, and the day the two differed by a
     * cent nobody would know which of them a branch had been folded from.
     *
     * <p>Floored at nothing, because a balance that has somehow fallen below the deposits standing
     * against it is a broken ledger rather than a negative amount of interest, and a branch is not
     * the place to discover it.
     */
    private static BigDecimal whatOfTheBalanceIsInterest(TheStartingPoint standing) {
        BigDecimal saved = BigDecimal.ZERO;
        for (DepositStillHoldingMoney deposit : standing.deposits()) {
            saved = saved.add(deposit.remainingAmount());
        }
        BigDecimal added = AmountOfMoney.quotedToTheCent(standing.balance()).subtract(saved);
        return AmountOfMoney.quotedToTheCent(added.max(BigDecimal.ZERO));
    }

    /**
     * The day from which the balance this branch opens with is supposed to have stood there, for
     * the walk that averages a month.
     *
     * <p>The day the window opens, ordinarily, because that is where the branch's own history
     * begins and every period after it is walked in full. The exception is a period already under
     * way when the window opened: its earlier days are behind the snapshot, the branch has no
     * ledger for them, and the honest supposition — said out loud in the class note's list of what
     * is knowingly approximate — is that the money was there all along. Dating the opening balance
     * at the start of that period is how that supposition is made rather than asserted, and it is
     * worth at most one month of one rate.
     */
    private static LocalDate theDayTheOpeningBalanceIsSupposedToHaveStoodFrom(
            TheStartingPoint standing) {
        LocalDate openedOn = standing.theProductItIsOn().openedOn();
        if (openedOn == null) {
            return standing.asAt();
        }
        LocalDate theEarliestStillOwed = TheMonthlyPeriodsOfAnAccount.beginningOf(openedOn,
                standing.theProductItIsOn().periodsAlreadyJudged() + 1);
        return theEarliestStillOwed.isBefore(standing.asAt()) ? theEarliestStillOwed
                : standing.asAt();
    }

    /**
     * An amount of money as the whole cents the interest walk counts in.
     *
     * <p>Exact by construction: everything the branch holds has already been quoted to the cent, so
     * moving the point two places leaves a whole number and {@code longValueExact} can only throw
     * on a figure this application never wrote down.
     */
    private static long inCents(BigDecimal amount) {
        return AmountOfMoney.quotedToTheCent(amount).movePointRight(2).longValueExact();
    }

    /**
     * The moment a day begins, in the zone this application counts every calendar thing in.
     *
     * <p>The only moment this class ever builds, and it is built rather than read: every rule it
     * quotes is written against instants, and the grain of the walk is a day. The zone is {@code
     * SavingsWeek}'s, borrowed rather than written out again for the reason the three calendars this
     * fold calls each give when they borrow the same constant — a weekly rule's Monday and a savings
     * week's Monday have to be the same Monday.
     */
    static Instant theMomentThatDayBegins(LocalDate day) {
        return day.atStartOfDay(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toInstant();
    }

    /**
     * One deposit inside a branch: when it landed, what is still in it, and how many of its
     * anniversaries this branch has paid.
     *
     * <p>Mutable, and only ever from inside the walk. What it still holds is what a withdrawal in
     * the branch draws down, oldest first, and what every anniversary ahead of it is priced on,
     * which is the whole of why a deposit rather than a balance is carried.
     */
    private static final class ADepositTheBranchHolds {

        private final Instant landedAt;
        private BigDecimal stillHolding;
        private int anniversariesPaid;

        private ADepositTheBranchHolds(Instant landedAt, BigDecimal stillHolding,
                                       int anniversariesPaid) {
            this.landedAt = landedAt;
            this.stillHolding = stillHolding;
            this.anniversariesPaid = anniversariesPaid;
        }
    }

    /**
     * A bill this branch has not managed to pay: which account it stands against and what it asks
     * for.
     *
     * <p>Kept in the order they fell due, because the run settles what is owed oldest first — a
     * customer who scrapes together nine hundred euros pays the older rent rather than the convenient
     * one.
     */
    private record ABillStillOwed(long currentAccountId, BigDecimal amount) {
    }
}
