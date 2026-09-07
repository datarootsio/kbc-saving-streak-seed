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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The whole derivation, read off the deposit ledger at a given moment: the week that moment falls
 * in, what has landed in it, and the run of consecutive secured weeks behind it.
 *
 * <p>No state, no clock and no bean, and that is what it is for. Two callers need this answer at two
 * different points in a request. {@link StreaksService} needs it when a savings account is read, off
 * the application's clock. The Deposits module needs it the instant a deposit has been recorded, to
 * price that deposit at the run the deposit has just been counted into — and Deposits is the module
 * this derivation reads its ledger from, so it cannot reach the answer through a service that reads
 * it back. A function both of them call is what lets the rule be stated once and priced and reported
 * from the same statement of it.
 *
 * <p>Which moment it is asked about is the caller's: this class never reads a clock. The one it is
 * given decides which week is "this week", and the run is walked back from there.
 */
public final class WeekAndStreakDerivation {

    private static final Logger log = LoggerFactory.getLogger(WeekAndStreakDerivation.class);

    private WeekAndStreakDerivation() {
    }

    /**
     * How the saving is going on this savings account as at the given moment: the week that moment
     * falls in, and the run of consecutive secured weeks behind that week.
     *
     * <p>One method for both, and one moment behind them. Two entry points would each be asked about
     * a moment of their own, and a pair of readings either side of midnight on a Monday would answer
     * about two different weeks — see {@link WeekAndStreak}. The week's own gross total is also the
     * figure the streak needs for the week it starts walking back from, so asking once counts it
     * once.
     */
    public static WeekAndStreak asAt(DepositsService deposits, long savingsAccountId, Instant now) {
        NewSavingsThisWeek thisWeek = newSavingsIn(deposits, savingsAccountId, now);
        return new WeekAndStreak(thisWeek, streakBehind(deposits, savingsAccountId, thisWeek));
    }

    /**
     * What has landed in this savings account so far in the week the given moment falls in, and what
     * the week still needs.
     *
     * <p>Gross: every deposit that landed in the week is counted, and money that has since been
     * withdrawn is counted with them. Withdrawals are invisible to a week's progress on purpose —
     * see {@link NewSavingsThisWeek} — so nothing in this method has to know that they exist.
     */
    private static NewSavingsThisWeek newSavingsIn(DepositsService deposits, long savingsAccountId,
                                                   Instant now) {
        SavingsWeek week = SavingsWeek.containing(now);
        List<DepositLanded> landed =
                deposits.depositsLandedBetween(savingsAccountId, week.startsAt(), week.endsAt());
        BigDecimal newSavings = landed.stream()
                .map(DepositLanded::amount)
                // Added back as decimals rather than summed by the database, for the reason the
                // money balance gives: SQLite has no decimal type and a sum it worked out itself
                // would accumulate in floating point.
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        NewSavingsThisWeek thisWeek = new NewSavingsThisWeek(week, newSavings);
        // Everything the answer was made of: the moment it was asked about, the week that moment
        // falls in, the zone it was read in, the two boundaries the ledger was actually queried
        // between, and the deposits that came back. A reviewer can add the amounts up by hand and
        // check both that the total is right and that the deposits either side of the boundary are
        // the ones a customer in Brussels would expect. Guarded, because rendering the deposits is
        // work, and this runs on every read of an account and on every deposit made into one.
        if (log.isDebugEnabled()) {
            log.debug("this week's new savings derived from the ledger savingsAccountId={} zone={} "
                            + "clockReads={} week={} weekStartsAt={} weekEndsAt={} deposits={} "
                            + "counted=[{}] newSavings={} weeklyMinimum={} stillNeeded={} secured={}",
                    savingsAccountId, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN, now, week,
                    week.startsAt(), week.endsAt(), landed.size(), countedInto(landed),
                    thisWeek.newSavings(), thisWeek.weeklyMinimum(), thisWeek.stillNeeded(),
                    thisWeek.isSecured());
        }
        return thisWeek;
    }

    /**
     * The run of consecutive secured weeks this account is on, and the longest run it has ever been
     * on, both worked out by walking back through its weeks from the one it is part-way through.
     *
     * <p>The rule, whole: a week is secured once the weekly minimum of gross new saving has landed in
     * it, and the current run is the consecutive secured weeks ending at the most recently secured
     * one — counted only while that week is this week or the one immediately before it. The walk
     * below <em>is</em> that rule rather than an implementation of three clauses of it. It starts at
     * this week; if this week is not secured it steps back one, because a week still running has
     * ended nothing and money can still land in it; then it counts secured weeks backwards until it
     * meets one that is not. A customer who skipped last week therefore reads zero <em>now</em>, on a
     * Wednesday, without waiting for this week to end: the step back lands on last week, last week
     * is not secured, and the count never starts.
     *
     * <p>The best-ever run is the longest run anywhere in what the walk's map holds, which is every
     * week the account has ever taken money in. It is worked out independently of the current one, so
     * that a lapse costs the run and not the record.
     *
     * <p>This week's total is not queried again: it arrives in {@code thisWeek}, off the same moment
     * that chose the week, so the figure the screen shows for the week and the figure the streak
     * judged the week on are the same number.
     */
    private static StreakOfSecuredWeeks streakBehind(DepositsService deposits, long savingsAccountId,
                                                     NewSavingsThisWeek thisWeek) {
        SavingsWeek currentWeek = thisWeek.week();
        // Everything before this week began, which is the whole of the account's history as far as a
        // run of weeks is concerned.
        //
        // Bounded at the start of this week rather than left open, so that a deposit dated in the
        // future — a trainer wound the clock forward, paid money in, and wound it back — is not a
        // secured week in a run nobody has lived through. The best-ever run is the longest in the
        // account's history, and next month is not history.
        List<DepositLanded> earlier =
                deposits.depositsLandedBefore(savingsAccountId, currentWeek.startsAt());
        Map<LocalDate, BigDecimal> grossByWeek = new HashMap<>();
        for (DepositLanded deposit : earlier) {
            // Added as decimals rather than summed by the database, for the reason this week's own
            // total gives: SQLite keeps an amount as a float and a sum it worked out itself would
            // accumulate in floating point.
            grossByWeek.merge(SavingsWeek.containing(deposit.depositedAt()).startsOn(),
                    deposit.amount(), BigDecimal::add);
        }
        // The one week the query above deliberately left out, filled in from the answer that already
        // has it. No key can collide: every deposit in `earlier` landed before this week began.
        grossByWeek.put(currentWeek.startsOn(), thisWeek.newSavings());

        List<NewSavingsThisWeek> walkedBackThrough = new ArrayList<>();
        SavingsWeek at = currentWeek;
        // This week not being secured yet is the one case the walk steps over rather than stops at.
        // Remembered rather than re-derived, because the log line has to say so: a reader seeing
        // this week reported "not secured" at the head of the walk would otherwise read the run as
        // having ended here, and it has not.
        boolean thisWeekIsStillRunning = !weekAsCounted(grossByWeek, at).isSecured();
        if (thisWeekIsStillRunning) {
            walkedBackThrough.add(weekAsCounted(grossByWeek, at));
            at = at.previous();
        }
        int currentWeeks = 0;
        while (weekAsCounted(grossByWeek, at).isSecured()) {
            walkedBackThrough.add(weekAsCounted(grossByWeek, at));
            currentWeeks++;
            at = at.previous();
        }
        // Where the run was found to end: the first week walking back that did not take in what a
        // week asks for. The walk stops here rather than reading the history back to the account's
        // first deposit, because nothing older can belong to a run that ends now. It always
        // terminates: past the oldest deposit every week is empty, and an empty week is not secured.
        NewSavingsThisWeek endedAt = weekAsCounted(grossByWeek, at);
        walkedBackThrough.add(endedAt);

        // The whole derivation, so that a reviewer can redo it by hand: the week it started from, the
        // weeks it walked back through with what landed in each and whether that secured it, the week
        // it stopped at and why, and — for the best-ever figure, which the walk does not reach — every
        // week in the account's history that was secured, in order, so the longest run of adjacent
        // Mondays can be read straight off the line. Guarded, because rendering the weeks is work and
        // this runs on every read of an account and on every deposit made into one.
        //
        // Said before the two figures are put together rather than after, so that a walk whose
        // arithmetic disagreed with itself would still have said what it walked: the record below
        // refuses a run longer than the best there has ever been, and the line that explains where
        // the run came from is the only way to find out why.
        int bestWeeks = longestRunIn(grossByWeek);
        if (log.isDebugEnabled()) {
            log.debug("streak of secured weeks derived from the ledger savingsAccountId={} zone={} "
                            + "thisWeek={} weeklyMinimum={} earlierDeposits={} weeksWithSavingInThem={} "
                            + "walkedBackThrough=[{}] streakEndedAt={} securedWeeks=[{}] "
                            + "currentStreakWeeks={} bestStreakWeeks={} multiplier={}",
                    savingsAccountId, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN, currentWeek,
                    thisWeek.weeklyMinimum(), earlier.size(), grossByWeek.size(),
                    asWalked(walkedBackThrough, thisWeekIsStillRunning), asWalked(endedAt),
                    securedWeeksIn(grossByWeek),
                    currentWeeks, bestWeeks, StreakMultiplier.paidByAStreakOf(currentWeeks));
        }
        return new StreakOfSecuredWeeks(currentWeeks, bestWeeks);
    }

    /**
     * A week as the walk sees it: the week itself and the gross new saving the map holds for it, or
     * nothing at all for a week no deposit ever landed in.
     *
     * <p>Built into {@link NewSavingsThisWeek} rather than compared here, so that "at least the
     * weekly minimum landed in it" is decided in the one class that owns the minimum and never
     * restated. It is also what quotes an absent week's total as EUR 0.00 rather than as 0.
     */
    private static NewSavingsThisWeek weekAsCounted(Map<LocalDate, BigDecimal> grossByWeek, SavingsWeek week) {
        return new NewSavingsThisWeek(week, grossByWeek.getOrDefault(week.startsOn(), BigDecimal.ZERO));
    }

    /**
     * The longest run of consecutive secured weeks anywhere in the account's history.
     *
     * <p>Independent of where the current run is and of whether there is one: a run that ended in
     * March is the record until something beats it. Read off the secured weeks in date order, a run
     * continuing wherever one Monday is the week after the last.
     */
    private static int longestRunIn(Map<LocalDate, BigDecimal> grossByWeek) {
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate week : securedWeeksInOrder(grossByWeek)) {
            run = previous != null && previous.plusWeeks(1).equals(week) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = week;
        }
        return longest;
    }

    /** The Mondays of the weeks that took in what a week asks for, oldest first. */
    private static List<LocalDate> securedWeeksInOrder(Map<LocalDate, BigDecimal> grossByWeek) {
        return grossByWeek.entrySet().stream()
                .filter(week -> NewSavingsThisWeek.securedBy(week.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** Those same Mondays for the log line, so the best-ever figure can be counted off it by hand. */
    private static String securedWeeksIn(Map<LocalDate, BigDecimal> grossByWeek) {
        return securedWeeksInOrder(grossByWeek).stream()
                .map(LocalDate::toString)
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

    /** One week of the walk: what landed in it, and whether that was enough to secure it. */
    private static String asWalked(NewSavingsThisWeek week) {
        return week.week() + " EUR " + week.newSavings().toPlainString()
                + (week.isSecured()
                ? " secured"
                : " not secured, short by EUR " + week.stillNeeded().toPlainString());
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
