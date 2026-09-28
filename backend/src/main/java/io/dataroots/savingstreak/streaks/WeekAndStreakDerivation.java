package io.dataroots.savingstreak.streaks;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.deposits.DepositLanded;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.WithdrawalMade;
import io.dataroots.savingstreak.deposits.WithdrawalsService;
import io.dataroots.savingstreak.scheme.TheSchemeAsPublished;
import io.dataroots.savingstreak.scheme.TheSchemeEachWeekWasJudgedUnder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The whole derivation, read off the money that has moved either way at a given moment: the week
 * that moment falls in, what was put away in it net of what came back out, and the run of
 * consecutive secured weeks behind it — each of those weeks judged against the scheme that was in
 * force on that week's own Monday.
 *
 * <p>Both ledgers, because a week is judged on what the customer is actually saving. Counting only
 * the deposits made the same fifty euros secure every week for ever — pay in on Monday, take it back
 * out on Tuesday, and the week was secured by money that never stayed — which walked a customer up
 * the whole multiplier ladder for nothing. {@link NewSavingsThisWeek} carries the rule; this walks
 * it.
 *
 * <p><strong>The scheme's history is an argument, and its being a history rather than a set of
 * figures is the load-bearing decision of the whole feature.</strong> This walk spans weeks — a run
 * of six is six Mondays apart, and the best-ever figure is read off every week the customer has ever
 * moved money in — so "the weekly threshold" could never have been the right argument to it. Handed
 * one threshold, this class would judge a week from March against a figure published in June: raise
 * the minimum from EUR 50 to EUR 80 next Monday and every EUR 60 week a customer ever secured would
 * un-secure itself, their current run would shorten, their best-ever run would shorten, and the next
 * deposit they made would be priced at the wrong rate, with nothing logged and nothing able to
 * explain it. So {@link TheSchemeEachWeekWasJudgedUnder} arrives instead, and every week this walk
 * reaches asks it what <em>that</em> week's Monday published. A week secured under EUR 50 is secured
 * for ever.
 *
 * <p>No state, no clock and no bean, and that is what it is for. The history is an argument for
 * exactly the reason the moment already was: two callers need this answer at two different points in
 * a request, and a class that read the scheme for itself would need a bean, which would need a
 * service, which is the thing that cannot be had here. {@link StreaksService} needs the answer when
 * a savings account is read, off the application's clock. The Deposits module needs it the instant a
 * deposit has been recorded, to price that deposit at the run the deposit has just been counted into
 * — and Deposits is the module this derivation reads its ledger from, so it cannot reach the answer
 * through a service that reads it back. A function both of them call is what lets the rule be stated
 * once and priced and reported from the same statement of it. Both of them now read the history for
 * themselves, once per request, and hand it in.
 *
 * <p><strong>Which way the dependency runs, said out loud because a reviewer will check it.</strong>
 * The scheme module depends on nothing in this application; {@code streaks} depends on it, which is
 * the direction the spec fixes and the direction the products module's own dependency already runs.
 * Nothing here reads a row: what arrives is a value with no clock, no bean and no connection behind
 * it, which is what lets this function stay pure while being about published policy. The one
 * direction that would be wrong is the other one — a scheme module that knew how a streak is counted
 * would be a second place the streak is decided.
 *
 * <p><strong>One read of the history, handed down, rather than one read per week.</strong> A walk of
 * twenty-six weeks that asked the database twenty-six times could in principle straddle a publish,
 * and a run counted half under one history and half under another is a run nobody could reproduce.
 * The caller reads once and this walks that one reading, which is the single concession the scheme
 * makes to being read rather than cached.
 *
 * <p>Which moment it is asked about is the caller's: this class never reads a clock. The one it is
 * given decides which week is "this week", and the run is walked back from there.
 *
 * <p>And whose saving it is about is the caller's too, which is a customer and not a savings
 * account. A week counts what somebody put away, wherever they put it: two accounts towards two
 * goals are one week's saving and one run of weeks, and the rate that run pays is theirs.
 */
public final class WeekAndStreakDerivation {

    private static final Logger log = LoggerFactory.getLogger(WeekAndStreakDerivation.class);

    private WeekAndStreakDerivation() {
    }

    /**
     * How this customer's saving is going as at the given moment: the week that moment falls in, the
     * run of consecutive secured weeks behind that week, and the version of the scheme this week was
     * judged under.
     *
     * <p>One method for both, and one moment behind them. Two entry points would each be asked about
     * a moment of their own, and a pair of readings either side of midnight on a Monday would answer
     * about two different weeks — see {@link WeekAndStreak}. The week's own net total is also the
     * figure the streak needs for the week it starts walking back from, so asking once counts it
     * once.
     *
     * <p>One history behind them too, for the same reason and one more: the threshold this week is
     * judged against and the ladder the run is paid on both come off the version in force this week,
     * and two readings of the scheme could hand over two versions of it a second apart.
     *
     * @param theScheme every version the bank has published, so that each week the walk reaches can
     *                  be judged against the one in force on its own Monday — never against today's
     */
    public static WeekAndStreak asAt(DepositsService deposits, WithdrawalsService withdrawals,
                                     long customerId, Instant now,
                                     TheSchemeEachWeekWasJudgedUnder theScheme) {
        SavingsWeek week = SavingsWeek.containing(now);
        // The version this week is judged under, resolved once and used for all three of the things
        // this week decides: what the week asks for, what its run is paid on, and which row the
        // reading names. Three resolutions of the same question would be three chances to name a
        // different version, which is the whole reason the history is read once and carried.
        TheSchemeAsPublished judgingThisWeek = theScheme.forTheWeekOf(week);
        NewSavingsThisWeek thisWeek =
                newSavingsIn(deposits, withdrawals, customerId, now, week, judgingThisWeek);
        return new WeekAndStreak(thisWeek,
                streakBehind(deposits, withdrawals, customerId, thisWeek, theScheme,
                        TheLadderARunClimbs.theLadderIn(judgingThisWeek)),
                judgingThisWeek.version());
    }

    /**
     * What this customer has put away so far in the week the given moment falls in, and what the
     * week still needs.
     *
     * <p>Net: every deposit that landed in the week, less every withdrawal made during it. A week
     * is what the customer has actually put away by the end of it, and money paid in on Monday and
     * taken back out on Tuesday has been put away by nobody.
     *
     * <p>Which week a withdrawal counts against is the week it was made in, not the week the deposit
     * it drew down landed in. It is the simpler rule and the only one a customer can follow while
     * the week is running: what has gone in since Monday, less what has come out since Monday.
     *
     * <p>What the week asks for arrives on the record rather than being compared here, and it is this
     * week's own figure — the threshold published by the version in force on this Monday. That is
     * also the figure the screen quotes back, so the page and the ledger cannot disagree about what
     * the week asked, even on the morning a repricing takes effect.
     */
    private static NewSavingsThisWeek newSavingsIn(DepositsService deposits,
                                                   WithdrawalsService withdrawals, long customerId,
                                                   Instant now, SavingsWeek week,
                                                   TheSchemeAsPublished judging) {
        List<DepositLanded> landed =
                deposits.depositsLandedBetween(customerId, week.startsAt(), week.endsAt());
        List<WithdrawalMade> takenOut =
                withdrawals.withdrawalsMadeBetween(customerId, week.startsAt(), week.endsAt());
        BigDecimal newSavings = landed.stream()
                .map(DepositLanded::amount)
                // Added back as decimals rather than summed by the database, for the reason the
                // money balance gives: SQLite has no decimal type and a sum it worked out itself
                // would accumulate in floating point.
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(takenOut.stream()
                        .map(WithdrawalMade::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        NewSavingsThisWeek thisWeek =
                new NewSavingsThisWeek(week, newSavings, judging.weeklyThreshold());
        // Everything the answer was made of: the moment it was asked about, the week that moment
        // falls in, the zone it was read in, the two boundaries the ledger was actually queried
        // between, the deposits that came back, and the version of the scheme that decided what the
        // week asked for. A reviewer can add the amounts up by hand and check both that the total is
        // right and that the deposits either side of the boundary are the ones a customer in
        // Brussels would expect — and can go and read the row the threshold came out of. Guarded,
        // because rendering the deposits is work, and this runs on every read of an account and on
        // every deposit made into one.
        if (log.isDebugEnabled()) {
            log.debug("this week's new savings derived from the ledger customerId={} zone={} "
                            + "clockReads={} week={} weekStartsAt={} weekEndsAt={} deposits={} "
                            + "counted=[{}] withdrawals={} takenBackOut=[{}] newSavings={} "
                            + "schemeVersion={} schemeEffectiveFrom={} weeklyMinimum={} "
                            + "stillNeeded={} secured={}",
                    customerId, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN, now, week,
                    week.startsAt(), week.endsAt(), landed.size(), countedInto(landed),
                    takenOut.size(), takenBackOutOf(takenOut),
                    thisWeek.newSavings(), judging.version(), judging.effectiveFrom(),
                    thisWeek.weeklyMinimum(), thisWeek.stillNeeded(), thisWeek.isSecured());
        }
        return thisWeek;
    }

    /**
     * The run of consecutive secured weeks this customer is on, and the longest run they have ever
     * been on, both worked out by walking back through their weeks from the one they are part-way
     * through — and every week judged against the scheme in force on its own Monday.
     *
     * <p>The rule, whole: a week is secured once the weekly minimum <em>that week asked for</em> of
     * net new saving has landed in it — everything paid in during the week less everything taken back
     * out of savings during it — and the current run is the consecutive secured weeks ending at the
     * most recently secured one, counted only while that week is this week or the one immediately
     * before it. The walk below <em>is</em> that rule rather than an implementation of three clauses
     * of it. It starts at this week; if this week is not secured it steps back one, because a week
     * still running has ended nothing and money can still land in it; then it counts secured weeks
     * backwards until it meets one that is not. A customer who skipped last week therefore reads zero
     * <em>now</em>, on a Wednesday, without waiting for this week to end: the step back lands on last
     * week, last week is not secured, and the count never starts.
     *
     * <p><strong>Each step back is a step into a week that may have asked for something else.</strong>
     * The threshold is not read once before the loop and reused; it is asked of the history per week,
     * by {@link #weekAsCounted}, which is what makes the walk's verdicts facts about the past rather
     * than opinions of this morning. Nothing in the loop itself had to change for that — the
     * comparison was always {@link NewSavingsThisWeek#isSecured()} — which is the whole argument for
     * having put the comparison there in the first place.
     *
     * <p>The best-ever run is the longest run anywhere in what the walk's map holds, which is every
     * week the customer ever moved money either way in, and it is judged the same way, week by week.
     * It is worked out independently of the current one, so that a lapse costs the run and not the
     * record — and because every week keeps the verdict its own Monday gave it, a repricing cannot
     * quietly rewrite the record either.
     *
     * <p>This week's total is not queried again: it arrives in {@code thisWeek}, off the same moment
     * that chose the week, so the figure the screen shows for the week and the figure the streak
     * judged the week on are the same number.
     *
     * @param theLadderThisWeekPays what a euro is worth now, off the version in force this week —
     *                              carried on the answer rather than fetched again by whoever prices
     *                              something, for the reason {@link StreakOfSecuredWeeks} gives
     */
    private static StreakOfSecuredWeeks streakBehind(DepositsService deposits,
                                                     WithdrawalsService withdrawals, long customerId,
                                                     NewSavingsThisWeek thisWeek,
                                                     TheSchemeEachWeekWasJudgedUnder theScheme,
                                                     TheLadderARunClimbs theLadderThisWeekPays) {
        SavingsWeek currentWeek = thisWeek.week();
        // Everything before this week began, which is the whole of the customer's saving as far as a
        // run of weeks is concerned.
        //
        // Bounded at the start of this week rather than left open, so that a deposit dated in the
        // future — a trainer wound the clock forward, paid money in, and wound it back — is not a
        // secured week in a run nobody has lived through. The best-ever run is the longest in what
        // the customer has actually saved, and next month is not history.
        List<DepositLanded> earlier =
                deposits.depositsLandedBefore(customerId, currentWeek.startsAt());
        // And everything that came back out over the same stretch, counted against the week it was
        // taken out in. Bounded at the same moment, so the two halves of every week are counted
        // across the same boundary and a withdrawal made this week cannot land in last week's total.
        List<WithdrawalMade> takenOutEarlier =
                withdrawals.withdrawalsMadeBefore(customerId, currentWeek.startsAt());
        Map<LocalDate, BigDecimal> netByWeek = netByWeekOf(earlier, takenOutEarlier);
        // The one week the query above deliberately left out, filled in from the answer that already
        // has it. No key can collide: every deposit in `earlier` landed before this week began.
        netByWeek.put(currentWeek.startsOn(), thisWeek.newSavings());

        List<NewSavingsThisWeek> walkedBackThrough = new ArrayList<>();
        SavingsWeek at = currentWeek;
        // Each week is counted, and therefore judged and logged, exactly once. It used to be
        // rebuilt at every mention of it, which was free while the threshold was a constant and is
        // not now: a week resolved twice is a week whose version is said twice, and a reader of the
        // log would be counting the same Monday as two decisions.
        NewSavingsThisWeek week = weekAsCounted(netByWeek, at, theScheme, customerId);
        // This week not being secured yet is the one case the walk steps over rather than stops at.
        // Remembered rather than re-derived, because the log line has to say so: a reader seeing
        // this week reported "not secured" at the head of the walk would otherwise read the run as
        // having ended here, and it has not.
        boolean thisWeekIsStillRunning = !week.isSecured();
        if (thisWeekIsStillRunning) {
            walkedBackThrough.add(week);
            at = at.previous();
            week = weekAsCounted(netByWeek, at, theScheme, customerId);
        }
        int currentWeeks = 0;
        while (week.isSecured()) {
            walkedBackThrough.add(week);
            currentWeeks++;
            at = at.previous();
            week = weekAsCounted(netByWeek, at, theScheme, customerId);
        }
        // Where the run was found to end: the first week walking back that did not take in what a
        // week asks for. The walk stops here rather than reading the saving back to the customer's
        // first deposit, because nothing older can belong to a run that ends now. It always
        // terminates: past the oldest deposit every week is empty, and an empty week is not secured
        // under any threshold the scheme may publish, because a threshold of nothing is refused at
        // the door that publishes one.
        NewSavingsThisWeek endedAt = week;
        walkedBackThrough.add(endedAt);

        // The whole derivation, so that a reviewer can redo it by hand: the week it started from, the
        // weeks it walked back through with what landed in each, what each of them asked for and
        // whether that secured it, the week it stopped at and why, and — for the best-ever figure,
        // which the walk does not reach — every week of the customer's saving that was secured, in
        // order and each with the version of the scheme that judged it, so the longest run of
        // adjacent Mondays can be read straight off the line. Guarded, because rendering the weeks is
        // work and this runs on every read of an account and on every deposit made into one.
        //
        // Said before the two figures are put together rather than after, so that a walk whose
        // arithmetic disagreed with itself would still have said what it walked: the record below
        // refuses a run longer than the best there has ever been, and the line that explains where
        // the run came from is the only way to find out why.
        int bestWeeks = longestRunIn(netByWeek, theScheme);
        if (log.isDebugEnabled()) {
            log.debug("streak of secured weeks derived from the ledger customerId={} zone={} "
                            + "thisWeek={} schemeVersion={} weeklyMinimum={} earlierDeposits={} "
                            + "earlierWithdrawals={} weeksWithMoneyMovedInThem={} "
                            + "walkedBackThrough=[{}] streakEndedAt={} securedWeeks=[{}] "
                            + "currentStreakWeeks={} bestStreakWeeks={} ladder={} multiplier={}",
                    customerId, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN, currentWeek,
                    theScheme.forTheWeekOf(currentWeek).version(),
                    thisWeek.weeklyMinimum(), earlier.size(), takenOutEarlier.size(),
                    netByWeek.size(),
                    asWalked(walkedBackThrough, thisWeekIsStillRunning), asWalked(endedAt),
                    securedWeeksIn(netByWeek, theScheme),
                    currentWeeks, bestWeeks, theLadderThisWeekPays,
                    StreakMultiplier.paidByAStreakOf(currentWeeks, theLadderThisWeekPays));
        }
        return new StreakOfSecuredWeeks(currentWeeks, bestWeeks, theLadderThisWeekPays);
    }

    /**
     * Which weeks of a stretch the customer secured, earliest first — every week in it whose net new
     * savings reached what <em>that week</em> asked for, whether or not they are next to each other.
     *
     * <p><strong>For the questions that are about weeks but are not about a run.</strong> A streak is
     * a walk backwards that stops at the first week that fell short; this is a count that nothing
     * stops. A challenge asking for five secured weeks is asking this, and a customer who broke their
     * run in week two has still secured weeks one and three. Both answers come from here so that they
     * cannot disagree about what a secured week is: the rule is
     * {@link NewSavingsThisWeek#securedBy(BigDecimal, BigDecimal)}, the week is {@link SavingsWeek},
     * the threshold is the one the scheme published for that week's own Monday, and the net figure is
     * summed the one way this class sums it. A module that wanted this and added up deposits of its
     * own would be a second answer to "what did that week put away", and the two would drift the
     * first time either side of the rule moved.
     *
     * <p>A challenge is therefore judged the same way a run is: a week secured under EUR 50 stays
     * counted after a later Monday raises the minimum, so a customer half way through a five-week
     * challenge does not lose two of them on the morning the bank reprices.
     *
     * <p>Whole weeks, from the one containing {@code from} to the one containing {@code until},
     * inclusive at both ends. A stretch that begins on a Wednesday counts the Monday-to-Sunday week
     * that Wednesday falls in, all of it — the week rule is not restated here and a week is not
     * pro-rated, because half a week secured is not a thing this application has ever had a name for.
     *
     * <p>Bounded at both ends in the query rather than filtered afterwards, so that a ledger of years
     * is not read to answer about a month, and so that no bucket can fall outside the stretch asked
     * about.
     *
     * @param from      the moment the stretch begins; its week is the first week counted
     * @param until     the moment it is being asked about; its week is the last week counted, in
     *                  full, even though most of it may still be ahead
     * @param theScheme every version the bank has published, asked per week for the reason the class
     *                  documentation gives at length
     */
    public static List<SavingsWeek> securedWeeksBetween(DepositsService deposits,
                                                        WithdrawalsService withdrawals,
                                                        long customerId, Instant from,
                                                        Instant until,
                                                        TheSchemeEachWeekWasJudgedUnder theScheme) {
        SavingsWeek firstWeek = SavingsWeek.containing(from);
        SavingsWeek lastWeek = SavingsWeek.containing(until);
        if (lastWeek.startsOn().isBefore(firstWeek.startsOn())) {
            // A stretch that ends before it begins, which a clock wound backwards can ask for. No
            // week is in it, and saying so beats summing a ledger between two boundaries the wrong
            // way round.
            log.debug("no weeks to count, the stretch ends before it begins customerId={} from={} "
                    + "until={} firstWeek={} lastWeek={}", customerId, from, until, firstWeek, lastWeek);
            return List.of();
        }
        List<DepositLanded> landed =
                deposits.depositsLandedBetween(customerId, firstWeek.startsAt(), lastWeek.endsAt());
        List<WithdrawalMade> takenOut = withdrawals
                .withdrawalsMadeBetween(customerId, firstWeek.startsAt(), lastWeek.endsAt());
        Map<LocalDate, BigDecimal> netByWeek = netByWeekOf(landed, takenOut);
        List<SavingsWeek> secured =
                securedWeeksInOrder(netByWeek, theScheme).stream().map(SavingsWeek::new).toList();
        // Everything the count was made of: the stretch asked about, the weeks it was widened to,
        // what the ledger gave up between those two boundaries, and which of those weeks reached the
        // minimum their own Monday asked for. A reviewer can add the amounts up by hand and check the
        // total, which side of a Monday each movement fell on, and which version of the scheme said
        // what each week needed. The version in force on the last week is named on its own as well,
        // because it is the one a reader comparing this against today's scheme will reach for first.
        if (log.isDebugEnabled()) {
            log.debug("secured weeks counted from the ledger customerId={} zone={} from={} until={} "
                            + "weeksScanned={}..{} scannedFrom={} scannedUntil={} deposits={} "
                            + "counted=[{}] withdrawals={} takenBackOut=[{}] "
                            + "weeksWithMoneyMovedInThem={} schemeVersion={} weeklyMinimum={} "
                            + "securedWeeks=[{}] secured={}",
                    customerId, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN, from, until, firstWeek,
                    lastWeek, firstWeek.startsAt(), lastWeek.endsAt(), landed.size(),
                    countedInto(landed), takenOut.size(), takenBackOutOf(takenOut), netByWeek.size(),
                    theScheme.forTheWeekOf(lastWeek).version(),
                    theScheme.forTheWeekOf(lastWeek).weeklyThreshold(),
                    securedWeeksIn(netByWeek, theScheme), secured.size());
        }
        return secured;
    }

    /**
     * What each week put away: everything paid in counted into the week it landed in, less everything
     * taken back out counted into the week it left in.
     *
     * <p>One place, because the walk behind a streak and the count of separate weeks are two
     * questions about the same figure. Added as decimals rather than summed by the database, for the
     * reason this week's own total gives: SQLite keeps an amount as a float and a sum it worked out
     * itself would accumulate in floating point.
     */
    private static Map<LocalDate, BigDecimal> netByWeekOf(List<DepositLanded> landed,
                                                          List<WithdrawalMade> takenOut) {
        Map<LocalDate, BigDecimal> netByWeek = new HashMap<>();
        for (DepositLanded deposit : landed) {
            netByWeek.merge(SavingsWeek.containing(deposit.depositedAt()).startsOn(),
                    deposit.amount(), BigDecimal::add);
        }
        for (WithdrawalMade withdrawal : takenOut) {
            netByWeek.merge(SavingsWeek.containing(withdrawal.withdrawnAt()).startsOn(),
                    withdrawal.amount().negate(), BigDecimal::add);
        }
        return netByWeek;
    }

    /**
     * A week as the walk sees it: the week itself, the net new saving the map holds for it or nothing
     * at all for a week no money moved either way in, and what that week asked for.
     *
     * <p><strong>This is where the load-bearing rule actually happens.</strong> The threshold is
     * asked of the history <em>per week</em>, against the week's own Monday, so the verdict this
     * record then gives is the verdict that week got when it was lived through. The alternative —
     * resolving one threshold before the walk and comparing every week against it — is a single line
     * shorter and is the bug: it would un-secure a EUR 60 week the morning the minimum rose to EUR 80.
     *
     * <p>Built into {@link NewSavingsThisWeek} rather than compared here, so that "at least what the
     * week asked for landed in it" is decided in the one class that owns the comparison and never
     * restated. It is also what quotes an absent week's total as EUR 0.00 rather than as 0.
     *
     * <p>One DEBUG line per week judged, which is the line a reviewer greps when a rate surprises
     * them: it names the week, the version in force on its Monday, the day that version started, what
     * it asked for, what landed, and the verdict. Said here rather than at the call sites because
     * here is where the decision is made, and said once per week because the walk above now counts
     * each week exactly once.
     */
    private static NewSavingsThisWeek weekAsCounted(Map<LocalDate, BigDecimal> netByWeek,
                                                    SavingsWeek week,
                                                    TheSchemeEachWeekWasJudgedUnder theScheme,
                                                    long customerId) {
        TheSchemeAsPublished judging = theScheme.forTheWeekOf(week);
        NewSavingsThisWeek counted = new NewSavingsThisWeek(week,
                netByWeek.getOrDefault(week.startsOn(), BigDecimal.ZERO),
                judging.weeklyThreshold());
        log.debug("a week is judged under the scheme in force on its own Monday customerId={} "
                        + "week={} weekStartsOn={} schemeVersion={} schemeEffectiveFrom={} "
                        + "weeklyMinimum={} newSavings={} secured={}",
                customerId, week, week.startsOn(), judging.version(), judging.effectiveFrom(),
                counted.weeklyMinimum(), counted.newSavings(), counted.isSecured());
        return counted;
    }

    /**
     * The longest run of consecutive secured weeks anywhere in the customer's saving.
     *
     * <p>Independent of where the current run is and of whether there is one: a run that ended in
     * March is the record until something beats it. Read off the secured weeks in date order, a run
     * continuing wherever one Monday is the week after the last.
     *
     * <p>And each of those weeks secured under its own Monday's threshold, which is what makes the
     * record a record rather than a reading. A best-ever run that shortened the morning the bank
     * raised the minimum would be the application taking back something a customer had already been
     * told they had done.
     */
    private static int longestRunIn(Map<LocalDate, BigDecimal> netByWeek,
                                    TheSchemeEachWeekWasJudgedUnder theScheme) {
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate week : securedWeeksInOrder(netByWeek, theScheme)) {
            run = previous != null && previous.plusWeeks(1).equals(week) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = week;
        }
        return longest;
    }

    /**
     * The Mondays of the weeks the customer put in what that week asked for, oldest first.
     *
     * <p>The threshold is resolved inside the filter, week by week, rather than once outside it. It
     * is the same rule the walk applies and the same reason: these weeks are history, and history is
     * judged by what was published at the time.
     *
     * <p>Silent, deliberately, although every one of these is a decision made under a version. This
     * runs over every week the customer has ever moved money in, so a line each would be a page of
     * log for one read of one account; the versions are written into {@link #securedWeeksIn} instead,
     * on the one line that is already about all of them.
     */
    private static List<LocalDate> securedWeeksInOrder(Map<LocalDate, BigDecimal> netByWeek,
                                                       TheSchemeEachWeekWasJudgedUnder theScheme) {
        return netByWeek.entrySet().stream()
                .filter(week -> NewSavingsThisWeek.securedBy(week.getValue(),
                        theScheme.forTheWeekOf(SavingsWeek.containing(week.getKey()))
                                .weeklyThreshold()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /**
     * Those same Mondays for the log line, each with the version of the scheme that judged it, so the
     * best-ever figure can be counted off it by hand and a run that spans a repricing can be seen to
     * have spanned one.
     */
    private static String securedWeeksIn(Map<LocalDate, BigDecimal> netByWeek,
                                         TheSchemeEachWeekWasJudgedUnder theScheme) {
        return securedWeeksInOrder(netByWeek, theScheme).stream()
                .map(monday -> monday + " under scheme version "
                        + theScheme.forTheWeekOf(SavingsWeek.containing(monday)).version())
                .collect(Collectors.joining("; "));
    }

    /**
     * The walk written out for the log line: every week it went through in the order it went through
     * them, each with what landed in it and whether that secured it, and the two weeks whose part in
     * the walk is not obvious from their own figures named for what they were — the current week the
     * walk stepped over because it is still running, and the week the run was found to end at.
     *
     * <p>Called only under the debug guard, which is why the walk is collected as weeks and turned
     * into a sentence here rather than built as one: a read of an account outside a debug session
     * should not be joining strings it is going to throw away.
     */
    private static String asWalked(List<NewSavingsThisWeek> walkedBackThrough,
                                   boolean thisWeekIsStillRunning) {
        List<String> said = new ArrayList<>();
        for (int week = 0; week < walkedBackThrough.size(); week++) {
            String walked = asWalked(walkedBackThrough.get(week));
            if (week == 0 && thisWeekIsStillRunning) {
                walked += " but still running, so it ends nothing — stepped over";
            } else if (week == walkedBackThrough.size() - 1) {
                walked += " — the run ends here";
            }
            said.add(walked);
        }
        return String.join("; ", said);
    }

    /**
     * One week of the walk: what landed in it, what it asked for, and whether that was enough to
     * secure it.
     *
     * <p>What it asked for is written out beside the total now that the figure varies from week to
     * week. A walk whose weeks each carried a different minimum, printed without them, would read as
     * arithmetic a reviewer could not check — "EUR 60 secured" on one line and "EUR 60 not secured"
     * three lines up, with nothing on either saying why.
     */
    private static String asWalked(NewSavingsThisWeek week) {
        return week.week() + " EUR " + week.newSavings().toPlainString()
                + " of EUR " + week.weeklyMinimum().toPlainString()
                + (week.isSecured()
                ? " secured"
                : " not secured, short by EUR " + week.stillNeeded().toPlainString());
    }

    /**
     * The withdrawals subtracted from the week, written out for the same log line and in the same
     * shape as the deposits added to it, so that a reader can do the subtraction by hand and see
     * which side of a Monday each one fell.
     */
    private static String takenBackOutOf(List<WithdrawalMade> takenOut) {
        return takenOut.stream()
                .map(withdrawal -> "withdrawal " + withdrawal.id()
                        // Written out as it arrives: Withdrawals quotes an amount to the cent on
                        // its way out of the module, so there is nothing to round here.
                        + " EUR " + withdrawal.amount().toPlainString()
                        + " at " + withdrawal.withdrawnAt()
                        + " (" + withdrawal.withdrawnAt().atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN) + ")")
                .collect(Collectors.joining("; "));
    }

    /**
     * The deposits the week was counted from, written out for the log line above: each one's
     * identifier, its amount to the cent, and the moment it landed both as the application recorded
     * it and as a customer in Brussels would read it. The second is what makes a boundary case
     * checkable — "23:30 on Sunday" is not something a reader can see in a UTC instant.
     */
    private static String countedInto(List<DepositLanded> landed) {
        return landed.stream()
                .map(deposit -> "deposit " + deposit.id()
                        // Written out as it arrives: Deposits quotes an amount to the cent on its
                        // way out of the module, so there is nothing to round here.
                        + " EUR " + deposit.amount().toPlainString()
                        + " at " + deposit.depositedAt()
                        + " (" + deposit.depositedAt().atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN) + ")")
                .collect(Collectors.joining("; "));
    }
}
