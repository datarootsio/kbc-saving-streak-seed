package io.dataroots.savingstreak.simulation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.accounts.AccountHolder;
import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.CurrentAccount;
import io.dataroots.savingstreak.accounts.CustomerAccounts;
import io.dataroots.savingstreak.automation.AutomationService;
import io.dataroots.savingstreak.automation.RecordedSavingRule;
import io.dataroots.savingstreak.deposits.DepositStillHoldingMoney;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.goals.AllocationsOnAnAccount;
import io.dataroots.savingstreak.goals.GoalRefused;
import io.dataroots.savingstreak.goals.GoalsService;
import io.dataroots.savingstreak.goals.SavingCapacityOnAnAccount;
import io.dataroots.savingstreak.loyalty.LoyaltyService;
import io.dataroots.savingstreak.loyalty.NextAnniversaryOfADeposit;
import io.dataroots.savingstreak.points.PointsExpiringOnADay;
import io.dataroots.savingstreak.points.PointsService;
import io.dataroots.savingstreak.products.NoticesService;
import io.dataroots.savingstreak.products.TheNoticeOnAnAccount;
import io.dataroots.savingstreak.products.TheWholeAgreementInOneRead;
import io.dataroots.savingstreak.products.WhatAnAccountsProductPaysAndAsksFor;
import io.dataroots.savingstreak.scheme.SchemeService;
import io.dataroots.savingstreak.scheme.TheSchemeEachWeekWasJudgedUnder;
import io.dataroots.savingstreak.streaks.StreaksService;
import io.dataroots.savingstreak.streaks.WeekAndStreak;
import io.dataroots.savingstreak.timeline.TimelineHorizon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Simulation module's face to the rest of the application: the branches of a future a customer
 * could ask about, and — first of all, because nothing can be predicted from nowhere — where the
 * account they would branch from actually stands.
 *
 * <p><strong>A module of its own, and no existing one can hold it.</strong> It needs Deposits for
 * the euros and the lots they sit in, Points for the days those lots go, Streaks for the week and
 * the run, Loyalty for the anniversary each deposit is actually owed next, Goals for the targets and
 * what the weekly money is spent on, Automation for the rules standing on the account and Accounts
 * for the salary and the rent funding all of it — and those modules cannot see each other. Goals cannot see points; Points cannot see deposits; Loyalty knows
 * nothing about streaks. That is precisely the situation Timeline was created for, one module
 * further on, and the same answer applies a second time.
 *
 * <p>The web layer is not the home either, although it already assembles several modules' figures
 * into an account's overview. Assembling is not a rule; this is. How far ahead to look, which facts
 * a branch is allowed to be folded from, what a scenario may contain and which events are worth
 * reporting are all decisions, and a controller taking them would put a rule in the web layer.
 *
 * <p><strong>One way, and no cycle closed.</strong> Simulation reads nine modules and nothing
 * already in the application learns that Simulation exists. It has no entity, no repository and no
 * table — here or in any slice to come — and this class is the one place in the module that talks to
 * anything at all. Everything after it is arithmetic over {@link TheStartingPoint}.
 *
 * <p><strong>Asking writes nothing.</strong> Not "nothing much", and not "nothing that matters":
 * no account moves, no deposit is made, no point is earned or expired, no rule is created and no row
 * is written anywhere. That is the same promise {@code ADryRun} already makes for a rule nobody has
 * saved, made over twelve months instead of over one morning, and the read-only transaction below is
 * the acceptance criterion rather than an optimisation.
 */
@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final AccountsService accounts;
    private final DepositsService deposits;
    private final PointsService points;
    private final GoalsService goals;
    private final AutomationService automation;
    private final StreaksService streaks;

    /**
     * What each deposit is actually owed next, which the calendar on its own cannot say.
     *
     * <p>The eighth module, and the reversal of a decision the first slice took deliberately. It
     * avoided Loyalty because {@code LoyaltyAnniversary} is a pure calendar function and nothing then
     * needed the record of what had been paid. A fold does: an anniversary that fell before the
     * window opened and that the sweep has not caught is one the application pays tonight, and a fold
     * reading the calendar alone writes it off as long paid. Only this module knows which
     * anniversaries have rows under them, so only this module can answer.
     */
    private final LoyaltyService loyalty;

    /**
     * The agreement the account is living under, and the notice standing on it.
     *
     * <p><strong>The ninth module, and the correction that stands between this feature and a
     * confidently wrong number.</strong> Every rule the fold quotes now varies by product — what a
     * euro saved here earns, what an anniversary pays, what a month of interest comes to, and what
     * refuses a withdrawal — so a fold that was never told which product it was projecting would
     * promise a fixed-term customer a tenth and no interest at all. Only this module knows what an
     * account's agreement says, so only this module can answer.
     *
     * <p><strong>Two doors of one module rather than one.</strong> The agreement and the notices
     * standing on an account are different facts with different lifetimes, each already owned by
     * the class that answers it, and the alternative — a wide reading that handed both back
     * together — is a shape that exists only for this caller. It is the same argument the three
     * narrow reads per current account make one step further down.
     */
    private final TheWholeAgreementInOneRead agreement;
    private final NoticesService notices;
    /**
     * What every week of a branch is judged and priced by, read as the whole published history
     * because a branch is a year of weeks and no single set of figures could judge fifty-two of them.
     * {@link TheStartingPoint} argues at length why this one comes off the scheme while the points
     * lifetime beside it comes off Points.
     */
    private final SchemeService scheme;

    private final Clock clock;

    SimulationService(AccountsService accounts, DepositsService deposits, PointsService points,
                      GoalsService goals, AutomationService automation, StreaksService streaks,
                      LoyaltyService loyalty, TheWholeAgreementInOneRead agreement,
                      NoticesService notices, SchemeService scheme, Clock clock) {
        this.accounts = accounts;
        this.deposits = deposits;
        this.points = points;
        this.goals = goals;
        this.automation = automation;
        this.streaks = streaks;
        this.loyalty = loyalty;
        this.agreement = agreement;
        this.notices = notices;
        this.scheme = scheme;
        this.clock = clock;
    }

    /**
     * Everything a branch of this account's future would have to be folded from, read once.
     *
     * <p><strong>In one read transaction</strong>, for the reason the account's own endpoints give
     * one step further out and this one needs harder than any of them. Seven modules are asked, and
     * a deposit committing between two of them would produce a snapshot whose halves describe
     * different instants of the ledger — a balance from before it beside a streak from after it —
     * and every one of the twelve months folded from that snapshot would inherit the contradiction.
     * A wrong figure on a screen is a wrong figure; a wrong figure at the start of a fold is a wrong
     * year.
     *
     * <p><strong>The clock is read once</strong>, here, and every day in the snapshot is counted
     * from that one reading. The modules underneath read it again on their own behalf — a rule's
     * next firing and a bill's next due day are theirs to date — and the readings are microseconds
     * apart inside one transaction, which no rule in this application turns on: everything here is a
     * day, and a day does not change between two instructions.
     *
     * <p><strong>The account is vouched for by whoever calls this.</strong> There is no refusal here
     * for an account nobody has heard of, and that is not an omission: this module cannot tell one
     * from an account that exists and has never been paid into, both being a balance of nothing and
     * a list of no deposits. Who holds which account is the Accounts module's answer and the caller
     * has already asked it. A holder that has gone missing between the caller's question and this one
     * is not a refusal either — it is a caller that vouched for something untrue, and it says so as
     * a mistake rather than as a sentence for a customer to read.
     *
     * <p>The three reads per current account are deliberate and they are cheap: a household holds one
     * or two, and the alternative — one wide read handing back accounts, income and bills together —
     * is a read that exists only for this caller and that every other caller would then have to be
     * talked out of using. Three narrow answers already owned by Accounts beat a fourth shape owned
     * by nobody.
     */
    @Transactional(readOnly = true)
    public TheStartingPoint whereThisAccountStands(long savingsAccountId) {
        LocalDate asAt = TimelineHorizon.opensOn(clock.instant());
        LocalDate until = TimelineHorizon.closesOn(asAt);
        AccountHolder holder = accounts.holderOfSavingsAccount(savingsAccountId)
                .orElseThrow(() -> new IllegalStateException("no savings account " + savingsAccountId
                        + " to stand on: whoever asked for a simulation of it vouched for it first"));
        long customerId = holder.customerId();

        // The euros, and the lots they sit in. The deposits that still hold something are the ones
        // whose anniversaries a branch can still pay and the ones a withdrawal inside a branch can
        // still draw from, oldest first; an emptied deposit has nothing left to reprice.
        BigDecimal balance = deposits.moneyBalanceOf(savingsAccountId);
        List<DepositStillHoldingMoney> stillHolding =
                deposits.depositsStillHoldingMoneyIn(savingsAccountId);

        // The days the customer's points go, which is Points' own answer about its own rule. Not the
        // lots and not the days they were earned: how long a batch lasts is that module's to apply
        // and this one must never be in a position to restate it.
        List<PointsExpiringOnADay> pointsGoing = points.whenThePointsOfACustomerGo(customerId);
        // And the one figure the fold needs in order to say when the points a branch earns would go.
        // Asked of Points and not of the scheme, although the scheme is where the number is
        // published: the rule belongs to the ledger, so the fold takes the ledger's own answer and
        // hands it straight back to the ledger's own rule. Reading it off the scheme here would make
        // Simulation the second module applying the points lifetime, which is precisely the
        // duplication the published scheme exists to end rather than to create.
        int howLongABatchOfPointsLasts = points.howLongABatchEarnedNowLasts();

        // The goals with what each has been allocated, asked against the balance just read so that
        // the shares and the pot they are shares of are the same figure. The capacity beside them,
        // because what a week is expected to bring is what the goals are funded out of.
        AllocationsOnAnAccount allocations = goals.allocationsOn(savingsAccountId, balance);
        SavingCapacityOnAnAccount weeklyCapacity = goals.savingCapacityOn(savingsAccountId);

        // The rules standing on the account, each already carrying the day it next fires and what it
        // would move. Paused rules are among them and belong there: a branch that stops for a while
        // is reasoning about rules that are standing and silent, which is what a pause already is.
        List<RecordedSavingRule> rules = automation.rulesOn(savingsAccountId);
        // And how far the night has already got with each of them. The rules say what will happen;
        // this says what the run still owes, which is a different question and the one a fold walking
        // forward from this morning has to have an answer to — the morning of asAt may already have
        // fired, and on a clock somebody wound without running the jobs every rule is months behind.
        Map<Long, Instant> rulesSettledThrough =
                automation.howFarEachRuleOnAnAccountIsSettled(savingsAccountId);

        // Which anniversary each deposit is owed next and what it is worth. Asked of Loyalty rather
        // than worked out from the calendar, because the calendar cannot tell an anniversary that has
        // been paid from one that fell this lunchtime and is waiting on tonight's sweep — and the
        // second of those is a bonus the application is about to pay that a branch would otherwise
        // never pay at all.
        Map<Long, NextAnniversaryOfADeposit> whenEachDepositNextPays =
                loyalty.whenTheDepositsInAnAccountNextPay(savingsAccountId);

        List<ACurrentAccountBehindIt> funding = theEverydayAccountsBehind(customerId);

        // The two figures that are the person's rather than this pot's, and the reason
        // TheStartingPoint argues about the asymmetry at length: a week is secured by what they put
        // away everywhere, and the mark is the most they have ever held anywhere.
        WeekAndStreak streak = streaks.weekAndStreakOf(customerId);
        // And what every week of the year ahead will be judged and priced by. The whole history
        // rather than what the scheme says today, because a fold walks fifty-two weeks and a version
        // already announced for a Monday inside the window takes effect inside the window: a branch
        // handed one ladder would go on paying the old one for months after the application had
        // stopped. Read once, here, with everything else, so that no branch can be folded from a
        // scheme a later branch was not.
        TheSchemeEachWeekWasJudgedUnder theSchemeEachWeekIsJudgedUnder =
                scheme.theSchemeThroughItsVersions();
        // Both halves of the pair TheMostEverSaved judges a deposit against, because the mark on its
        // own prices nothing: what a deposit earns on is what it takes the customer above the mark,
        // and that subtraction needs what they hold now as well. They are equal for a customer at
        // their peak and only for them.
        // Their own money and not a cent of the interest the bank has added, because the fold
        // restates the mark rule and the mark is blind to interest: a projection folded from a
        // balance that included it would keep promising points on euros the ledger will not pay
        // for. The pair has to be about the same money at both ends of the subtraction.
        BigDecimal theyStillHoldAltogether = deposits.stillSavedOutOfTheirOwnMoneyBy(customerId);
        BigDecimal theMostEverSaved = deposits.mostEverSavedBy(customerId);

        // The agreement the account is living under, in one read, because a fold that asked for the
        // rate and then for the floor could be handed two different versions of the terms. Every
        // rule the fold quotes turns on one of these figures, and the two loyalty ones arrive
        // already in the units the rules that consume them work in.
        WhatAnAccountsProductPaysAndAsksFor theProductItIsOn =
                agreement.whatTheProductOfAnAccountPaysAndAsksFor(savingsAccountId);
        // And the notices standing on it, as rows rather than as a figure, because a branch asks
        // about a day that has not happened yet: notice still running this morning is money that is
        // free on the morning a scenario takes it out.
        TheNoticeOnAnAccount theNoticeStanding = notices.noticeOn(savingsAccountId);

        TheStartingPoint standing = new TheStartingPoint(asAt, until, savingsAccountId, customerId,
                balance, stillHolding, pointsGoing, howLongABatchOfPointsLasts, allocations,
                weeklyCapacity, rules, rulesSettledThrough, whenEachDepositNextPays, funding, streak,
                theSchemeEachWeekIsJudgedUnder,
                theyStillHoldAltogether, theMostEverSaved, theProductItIsOn, theNoticeStanding);
        // One line per snapshot with everything that will decide every branch folded from it, counted
        // rather than listed: a screen full of projections is checkable against this line, and a
        // fold that came out wrong is diagnosed by asking first whether it was folded from the right
        // present. The high-water mark is named in full rather than counted, because it is the one
        // figure here that silently decides whether a whole branch earns anything at all.
        log.debug("where this account stands savingsAccountId={} customerId={} asAt={} until={} "
                        + "balance={} deposits={} daysPointsGo={} pointsStanding={} "
                        + "howLongABatchOfPointsLasts={} goals={} "
                        + "unallocated={} weeklyCapacity={} rules={} rulesOwedFromBefore={} "
                        + "depositsOwedAnAnniversary={} currentAccounts={} "
                        + "securedWeeks={} newSavingsThisWeek={} schemeVersion={} "
                        + "weeklyMinimum={} stillSaved={} mostEverSaved={} "
                        + "product={} version={} annualRateBasisPoints={} bonusRateBasisPoints={} "
                        + "pointsMultiple={} anniversaryPerWholeEuro={} noticeDays={} "
                        + "noticeReadyToTakeToday={} maturesOn={} periodsAlreadyJudged={}",
                savingsAccountId, customerId, asAt, until, balance, stillHolding.size(),
                pointsGoing.size(), standing.pointsStanding(), howLongABatchOfPointsLasts,
                allocations.goals().size(),
                allocations.unallocated(), weeklyCapacity.weeklyCapacity(), rules.size(),
                howManyAreBehind(rulesSettledThrough, asAt),
                howManyAreOwedAnAnniversary(whenEachDepositNextPays, asAt),
                funding.size(), streak.streak().currentWeeks(), streak.week().newSavings(),
                streak.theVersionOfTheSchemeThisWeekWasJudgedUnder(),
                streak.week().weeklyMinimum(),
                theyStillHoldAltogether, theMostEverSaved, theProductItIsOn.productCode(),
                theProductItIsOn.version(), theProductItIsOn.annualRateBasisPoints(),
                theProductItIsOn.bonusRateBasisPoints(), theProductItIsOn.pointsMultiplier(),
                theProductItIsOn.anniversaryRatePerWholeEuro(), theProductItIsOn.noticeDays(),
                theNoticeStanding.readyToTakeToday(), theProductItIsOn.maturesOn(),
                theProductItIsOn.periodsAlreadyJudged());
        return standing;
    }

    /**
     * Every future this account was asked about, beside the one it is already heading for.
     *
     * <p><strong>One snapshot for all of them.</strong> The present is read once, here, and every
     * branch is folded from that one reading — so four columns on a screen differ only by what the
     * customer changed and never by a deposit that committed between two of them. It is the reason
     * this method exists at all rather than a caller asking for the present and then for the
     * branches: two questions would be two instants.
     *
     * <p><strong>The do-nothing branch is always computed</strong>, whether or not anything was
     * asked about, and it comes first. A projection with nothing beside it answers no question, and a
     * customer exploring is comparing against the year they are already in rather than against
     * nothing. It is named in the customer's own words for it rather than the application's.
     *
     * <p><strong>And it still writes nothing.</strong> The transaction is read-only, the fold is a
     * pure function over a snapshot it cannot reach past, and no branch has an identifier because
     * there is nothing anywhere to give one to. Asking is free in the strong sense: no account moves,
     * no deposit is made, no point is earned or expired, no rule is created and no row is written.
     *
     * <p><strong>Several futures in one asking, each folded on its own, and none of them able to
     * reach another.</strong> They come back in the order they were typed, after the do-nothing one,
     * because a customer reading four columns left to right is reading the four questions they wrote
     * in the order they wrote them. Every one of them is folded from the snapshot above and from
     * nothing else, and {@link TheStartingPoint} copies every list it is handed for exactly this
     * reason: a withdrawal in the third column draws down the third column's own running copy of the
     * deposits and leaves the second column's alone. How many futures one asking may carry, and how
     * many changes one of them may be made of, are {@link WhatMayBeAskedAtOnce}'s to say.
     *
     * <p><strong>The whole question is found askable before a single day is folded.</strong> In two
     * passes and in this order. First the shape of it — how many futures, and whether each of them
     * has a name — which needs nothing read from anywhere and so is settled before the present is
     * even gathered. Then every change in every scenario put to its own rule, all of them, before the
     * first branch is walked: a bad amount in the fourth column is a sentence rather than three years
     * of nights folded for nothing, and the answer is about the first thing wrong with what the
     * customer asked rather than about whichever the reader happened to reach.
     *
     * <p>Each refusal is the kind's own sentence and carries the column and the chip it belongs to
     * beside it rather than inside it — this loop knows that a change can refuse itself, which
     * scenario it was asked in and which change of that scenario it was, and knows nothing whatever
     * about what any kind objects to.
     *
     * <p>The account is vouched for by whoever calls this, for the reason
     * {@link #whereThisAccountStands} gives at length.
     *
     * @param asked the scenarios the customer typed, in their order, and empty when they have asked
     *              about nothing at all — which is still answered, with the branch they are in
     */
    @Transactional(readOnly = true)
    public TheFuturesOfThisAccount theFuturesOf(long savingsAccountId,
                                                List<AScenarioToAskAbout> asked) {
        // How many, and whether each of them is a column somebody could read: no account is touched
        // to answer either, so a question that was never going to be folded costs no reads at all.
        WhatMayBeAskedAtOnce.theQuestionIsOneThisApplicationWillTake(savingsAccountId, asked);
        TheStartingPoint standing = whereThisAccountStands(savingsAccountId);
        // Counted from one in both loops, because the numbers travel back to a customer who is
        // looking at their own columns and chips rather than at an array.
        for (int position = 1; position <= asked.size(); position++) {
            AScenarioToAskAbout scenario = asked.get(position - 1);
            List<AnAdjustment> changes = scenario.adjustments();
            for (int change = 1; change <= changes.size(); change++) {
                whyItCannotBeAsked(standing, scenario, change, changes.get(change - 1));
            }
        }

        List<HowAScenarioTurnsOut> branches = new ArrayList<>(asked.size() + 1);
        // The branch nobody has to ask for, first and always: a projection with nothing beside it
        // answers no question, and a request carrying no scenario at all still comes back with the
        // year the customer is actually living in.
        branches.add(TheNightReplayed.theYearThatFollows(standing,
                TheNightReplayed.THE_YEAR_ALREADY_UNDER_WAY, List.of()));
        for (AScenarioToAskAbout scenario : asked) {
            // Each folded from the same snapshot and from its own changes, so two branches in one
            // request differ by exactly what the customer changed and by nothing else — a branch
            // cannot reach the snapshot, and there is nothing else for it to reach.
            branches.add(TheNightReplayed.theYearThatFollows(standing, scenario.called(),
                    scenario.adjustments()));
        }
        return new TheFuturesOfThisAccount(standing, branches);
    }

    /**
     * Turns one branch into the plan the customer has decided on, and answers with exactly what
     * changed.
     *
     * <p><strong>Adopting is not this module inventing a write path.</strong> It is the presses a
     * customer would have made on the screens that own the figures, made once, so that a decision
     * they have already taken does not have to be retyped into four screens. Every one of them goes
     * through {@link ThePressesACustomerWouldHaveMade} to the module that owns the rule — the weekly
     * capacity and a goal's deadline are both Goals' — and this method does not know what either of
     * those rules says. It never will: that is what keeps a deadline meaning one thing in this
     * application rather than one thing on the goal screen and another on the simulator.
     *
     * <p><strong>All of it or none of it, and the transaction is how.</strong> A customer left with
     * their capacity raised and their deadline unchanged has a plan they did not choose, which is
     * worse than a refusal — they would have to work out which half went through before they could
     * put it right. So the presses run inside one transaction that is rolled back whole the moment
     * any of them is refused, and the refusal names the part that caused it. It is written this way
     * round deliberately rather than by checking everything first: a check first would be this
     * module forming its own opinion of what Goals will accept, and the day the two opinions differed
     * the customer would be refused by a rule nobody owns. The modules rule, and the transaction
     * undoes.
     *
     * <p><strong>Two of the four kinds change nothing, and say so out loud.</strong> A withdrawal is
     * a thing a customer does on the day and pausing is what {@code RulePause} already is; each
     * comes back as a line naming it as theirs to carry out, because a press that quietly moved five
     * hundred euros off the back of a projection is the one thing this feature must never do — and a
     * press that silently did nothing about it would be just as bad in the other direction. Which of
     * the four does which is written in the four kinds and nowhere here: there is no switch in this
     * method, and a fifth kind of change would add no line to it.
     *
     * <p><strong>Pressing twice raises the capacity twice.</strong> Nothing here remembers that a
     * branch was adopted, and nothing should: a second press is a second decision, made by somebody
     * looking at a screen that has already been redrawn against the first one. The answer says what
     * each press changed, and the second one names the figure it raised from, so the customer can
     * see that it happened twice.
     *
     * <p><strong>Nothing is folded.</strong> Adopting does not replay the year, does not read the
     * snapshot and does not put a change to {@link AnAdjustment#whyItCannotBeAsked}: those are the
     * rules about what this application will <em>draw</em> — a day has to be inside the twelve months
     * the window covers — and they are not the rules about what a plan may be. A customer who wants
     * a deadline in fourteen months has asked for a deadline the goal screen would take, and
     * refusing it here in the simulator's words would be a second opinion about a rule that is not
     * the simulator's.
     *
     * <p>The caps on what one asking may carry are asked first all the same, because they are rules
     * about the question rather than about the fold: a branch with no name cannot be reported back
     * and a branch of fifty changes is not a decision anybody made on a screen this application
     * drew.
     *
     * <p>The account is vouched for by whoever calls this, for the reason
     * {@link #whereThisAccountStands} gives at length.
     *
     * @param chosen the branch the customer pressed adopt on, as they built it
     * @throws SimulationRefused if any part of it is refused, in the owning module's own sentence,
     *                           with nothing at all applied
     */
    @Transactional
    public WhatAdoptingAScenarioChanged adopt(long savingsAccountId, AScenarioToAskAbout chosen) {
        WhatMayBeAskedAtOnce.theQuestionIsOneThisApplicationWillTake(savingsAccountId, List.of(chosen));
        List<AnAdjustment> changes = chosen.adjustments();
        // The inputs behind the decision, before any of them has been made: a reviewer reading a
        // refusal further down has to be able to see the whole branch that was pressed, and by then
        // the transaction carrying it has been rolled back.
        log.debug("scenario adoption asked savingsAccountId={} scenario={} changes={} adjustments={}",
                savingsAccountId, chosen.called(), changes.size(), asAsked(changes));

        ThePressesACustomerWouldHaveMade presses =
                new ThePressesACustomerWouldHaveMade(goals, savingsAccountId);
        List<AChangeThePlanNowCarries> carried = new ArrayList<>(changes.size());
        // Counted from one, as the chips under a column's heading are, so that a refusal points at
        // the one the customer is looking at.
        for (int change = 1; change <= changes.size(); change++) {
            AnAdjustment adjustment = changes.get(change - 1);
            try {
                carried.add(adjustment.adoptedThrough(presses));
            } catch (GoalRefused refusal) {
                // Goals' own sentence, word for word, with this module adding only where to point.
                // The words are not touched and the reason is not restated: a deadline in the past
                // is refused in the words the goal screen already refuses one in, which is the whole
                // argument for pressing through the owning module rather than writing here.
                throw refusing(savingsAccountId, chosen, change, adjustment, refusal.getMessage());
            } catch (SimulationRefused refusal) {
                // A box the change could not be adopted without, refused in the change's own words.
                // It arrives without a position on it, because a kind cannot know which of a
                // branch's changes it is, and this is the one place that does know.
                throw refusing(savingsAccountId, chosen, change, adjustment, refusal.getMessage());
            }
        }

        // One line per adoption naming every change it made, which is the line a reviewer puts
        // beside the plan the customer now has. The sentences rather than the kinds, because
        // "SAVE_MORE_EACH_WEEK" says what was pressed and the sentence says what the figure became —
        // and a second press on the same branch is only tellable from the first by its figures.
        log.info("scenario adopted savingsAccountId={} scenario={} changes={} applied={} "
                        + "yoursToCarryOut={} whatChanged={}",
                savingsAccountId, chosen.called(), carried.size(),
                howManyOf(carried, false), howManyOf(carried, true), whatChanged(carried));
        return new WhatAdoptingAScenarioChanged(savingsAccountId, chosen.called(), carried);
    }

    /**
     * Warns with the part that caused it and hands back the refusal, so that no adoption can be
     * abandoned without a line saying which change did it and in whose words.
     *
     * <p>The reason is passed through untouched. What is added is a locator and never an opinion:
     * the column, the chip's position in it and how that chip reads, hung beside the sentence as
     * extension members rather than spliced into it, for the reason {@link SimulationRefused} gives
     * at length — one mistake reads the same however many changes were in the branch.
     *
     * <p>The line says that nothing was applied, in as many words. It is the fact a reviewer is
     * actually checking, the transaction that makes it true is invisible in the log, and a refusal
     * that arrived after two successful presses would otherwise read as a half-applied plan.
     */
    private static SimulationRefused refusing(long savingsAccountId, AScenarioToAskAbout chosen,
                                              int changeNumber, AnAdjustment adjustment,
                                              String reason) {
        log.warn("scenario adoption refused savingsAccountId={} scenario={} change={} adjustment={} "
                        + "applied=nothing reason={}",
                savingsAccountId, chosen.called(), changeNumber, adjustment.asAsked(), reason);
        return SimulationRefused.aboutThatChange(chosen.called(), changeNumber, adjustment.asAsked(),
                reason);
    }

    /** The changes in a branch as one greppable phrase, for the line named before any press is made. */
    private static String asAsked(List<AnAdjustment> changes) {
        return changes.stream().map(AnAdjustment::asAsked).collect(Collectors.joining(" | "));
    }

    /** How many of the lines were pressed, or how many were handed back to the customer. */
    private static long howManyOf(List<AChangeThePlanNowCarries> carried, boolean yoursToCarryOut) {
        return carried.stream().filter(line -> line.yoursToCarryOut() == yoursToCarryOut).count();
    }

    /** Every line of what adopting changed, in one phrase, for the one line per adoption. */
    private static String whatChanged(List<AChangeThePlanNowCarries> carried) {
        return carried.stream()
                .map(line -> line.kind() + (line.yoursToCarryOut() ? " (yours to carry out): " : ": ")
                        + line.what())
                .collect(Collectors.joining(" | "));
    }

    /**
     * One change put to its own rule, refused in that rule's own words and warned about with the
     * scenario it was asked in.
     *
     * <p>Named in the warning rather than merely counted, because a customer comparing four columns
     * is told which of their four questions this is about and a reviewer reading the log has to be
     * able to find the same one. What the reason says is never this class's to decide: a change
     * refuses itself, which is what keeps a fifth kind of change from having to be added to a
     * method here.
     *
     * <p><strong>And the refusal carries the same two facts out to whoever asked.</strong> A
     * question can hold four futures of ten changes each, so a true sentence about a day outside the
     * window leaves somebody staring at forty boxes unless it says which one. The scenario's name and
     * the change's position ride beside the reason rather than being spliced into it, for the reason
     * {@link SimulationRefused} gives at length: the objection to an amount of nothing is one form of
     * words whether it was asked once or asked in the fourth column of four.
     *
     * @param changeNumber which of that scenario's changes this is, counting from one as a customer
     *                     counts the chips under a column's heading
     */
    private void whyItCannotBeAsked(TheStartingPoint standing, AScenarioToAskAbout scenario,
                                    int changeNumber, AnAdjustment adjustment) {
        adjustment.whyItCannotBeAsked(standing).ifPresent(reason -> {
            log.warn("adjustment rejected savingsAccountId={} scenario={} change={} adjustment={} "
                            + "reason={}", standing.savingsAccountId(), scenario.called(),
                    changeNumber, adjustment.asAsked(), reason);
            throw SimulationRefused.aboutThatChange(scenario.called(), changeNumber,
                    adjustment.asAsked(), reason);
        });
    }

    /**
     * The everyday accounts a branch's saving would come out of: what each holds, what lands in it
     * and what leaves it.
     *
     * <p>Every account the holder has rather than only the ones this savings account's rules draw
     * from, for the reason {@link ACurrentAccountBehindIt} gives: the customer who has written no
     * rule yet is the one asking this hardest, and an answer that appeared only once a rule existed
     * would arrive after the decision it informs.
     *
     * <p>A holder with no everyday account at all is an empty list rather than a refusal. It is a
     * customer whose branches can move no money, which is a true and rather bleak answer, and it is
     * not this module's place to call it an error.
     */
    private List<ACurrentAccountBehindIt> theEverydayAccountsBehind(long customerId) {
        CustomerAccounts held = accounts.accountsOf(customerId)
                .orElseThrow(() -> new IllegalStateException("no customer " + customerId
                        + ", who Accounts has just named as the holder of a savings account"));
        List<ACurrentAccountBehindIt> funding = new ArrayList<>(held.currentAccounts().size());
        for (CurrentAccount account : held.currentAccounts()) {
            long currentAccountId = account.getId();
            funding.add(new ACurrentAccountBehindIt(
                    currentAccountId,
                    // Asked of the account's own read rather than taken off the entity in hand, so
                    // that the figure a branch spends is the figure the account's own screen shows.
                    accounts.balanceOfCurrentAccount(currentAccountId).orElse(BigDecimal.ZERO),
                    accounts.monthlyIncomeOn(currentAccountId),
                    accounts.billsOn(currentAccountId),
                    // And the two cursors beside them, because a fold is predicting the nightly run
                    // rather than drawing a page: what the salary and the rent are is on the
                    // declarations, and what the run still owes of either is only on these.
                    accounts.howFarTheIncomeOnAnAccountIsPaid(currentAccountId),
                    accounts.howFarEachBillOnAnAccountIsSettled(currentAccountId)));
        }
        return funding;
    }

    /**
     * How many of those cursors sit before the day the window opens — how many rules the next run
     * still owes a morning for.
     *
     * <p>Counted rather than listed, and on the snapshot's line rather than the fold's, because it
     * is the one figure that explains a branch whose first month saves more than a month's worth. On
     * a clock somebody wound without running the jobs it is every rule on the account, which is the
     * ordinary state of a demonstration and reads as a fault until this number is beside it.
     */
    private static long howManyAreBehind(Map<Long, Instant> settledThrough, LocalDate asAt) {
        Instant theWindowOpens = TheNightReplayed.theMomentThatDayBegins(asAt);
        return settledThrough.values().stream()
                .filter(cursor -> cursor != null && cursor.isBefore(theWindowOpens))
                .count();
    }

    /**
     * How many deposits are owed an anniversary that has already fallen and that the sweep has not
     * paid — the bonuses the application will pay tonight and that a branch has to pay too.
     *
     * <p>The figure the second of this slice's two corrections turns on, so it is on the line a
     * reviewer reads rather than left to be inferred from a bonus that arrived a month early.
     */
    private static long howManyAreOwedAnAnniversary(
            Map<Long, NextAnniversaryOfADeposit> nextPaying, LocalDate asAt) {
        return nextPaying.values().stream()
                .filter(next -> next.points() > 0 && !next.on().isAfter(asAt))
                .count();
    }
}
