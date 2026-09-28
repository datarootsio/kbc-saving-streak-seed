package io.dataroots.savingstreak.streaks;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.WithdrawalsService;
import io.dataroots.savingstreak.scheme.SchemeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Streaks module's face to the rest of the application. It answers what a customer's saving
 * looks like: how much they have put away in the week they are part-way through, how much more that
 * week asks for, how many consecutive weeks they have secured, the longest run they have ever had,
 * what a euro paid in earns at that run's rate, and which version of the scheme decided the last two.
 *
 * <p>The customer's, not one savings account's. Somebody saving towards two goals is saving: a week
 * counts what they put away wherever they put it, and the run of weeks and the rate it pays are
 * theirs. Which account the euros went into decides where the money sits and nothing else.
 *
 * <p>Nothing is stored. Every answer is derived from the records of money moving at the moment it is
 * asked for, which is the deciding property of the whole feature: the application's clock is movable, in
 * a training session it is wound both ways, and a weekly total or a streak counter written down
 * would routinely find itself describing a week that is now in the future. A derivation cannot
 * disagree with the ledger it is derived from, and the cost — a couple of queries per read against a
 * training-sized history — is not one.
 *
 * <p>What a deposit was actually paid is the other half of that sentence and is not here. The
 * figures below answer "what is my rate"; the ledger answers "what was this deposit paid", which is
 * history and is recorded at the moment the money moved.
 *
 * <p>Which week it is comes from the application's clock rather than from the machine's, so a week
 * moves when a trainer moves time. What a week <em>asks for</em> comes from the scheme, and reading
 * the two is the whole of what this class adds to {@link WeekAndStreakDerivation}, which is the
 * derivation itself and is shared with the Deposits module — a deposit is priced by the same walk
 * that this reports, off the moment the money moved rather than off the moment somebody opened the
 * page. What a week is — Monday to Sunday, in Brussels — is {@link SavingsWeek}'s answer and is
 * stated there once.
 *
 * <p><strong>The scheme's whole history is read, not the version in force.</strong> A run of weeks
 * spans Mondays, and each of those weeks is judged against what its own Monday published, so one set
 * of figures could never be the right argument — {@link WeekAndStreakDerivation} argues that at
 * length and is where the rule is applied. Read once per answer and handed down, rather than asked
 * per week, so that a walk of twenty-six weeks cannot straddle a publish half way through itself.
 *
 * <p>This module depends on {@code scheme} and {@code scheme} depends on nothing in this application
 * — the direction the spec fixes and the one the products module already runs in. The scheme holds
 * the numbers; the rule about what a number means to a week stays here, where it has always been
 * argued.
 */
@Service
public class StreaksService {

    private final DepositsService deposits;
    /** The other half of a week: a week counts what was put away less what was taken back out. */
    private final WithdrawalsService withdrawals;
    /**
     * Where the figures every week is judged against come from. Asked for its whole history rather
     * than for what it says today, because the answers below span weeks and a week from March is not
     * judged by June's scheme.
     */
    private final SchemeService scheme;
    private final Clock clock;

    StreaksService(DepositsService deposits, WithdrawalsService withdrawals, SchemeService scheme,
                   Clock clock) {
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.scheme = scheme;
        this.clock = clock;
    }

    /**
     * How this customer's saving is going right now: the week they are part-way through, the run
     * of consecutive secured weeks behind that week, and the version of the scheme this week is
     * judged under.
     *
     * <p>One reading of the clock behind both. Two reads either side of midnight on a Monday would
     * answer about two different weeks — see {@link WeekAndStreak}. One reading of the scheme too,
     * for the same reason said about versions rather than about weeks.
     */
    @Transactional(readOnly = true)
    public WeekAndStreak weekAndStreakOf(long customerId) {
        return WeekAndStreakDerivation.asAt(deposits, withdrawals, customerId, clock.instant(),
                scheme.theSchemeThroughItsVersions());
    }

    /**
     * Which weeks of a stretch this customer secured, earliest first, whether or not they are next to
     * each other.
     *
     * <p><strong>A second question about weeks, asked because a run is not the only game played on
     * them.</strong> A challenge counting five secured weeks is not counting a streak: a week that
     * fell short ends a run and costs a count nothing, so the two figures differ on purpose and a
     * customer dipping into their savings loses one and keeps the other. Both are answered from the
     * same derivation and against the same threshold — the one the scheme published for each week's
     * own Monday — so that "a secured week" cannot come to mean two things in one application, and
     * so that a repricing cannot take a challenge rung back off somebody who had already climbed it.
     *
     * <p>Read-only, and it changes nothing about what a streak is. It exists so that a module asking
     * about weeks does not sum the deposit ledger itself — the moment two places work out what a week
     * put away, the net-of-withdrawals rule is written down twice and drifts the first time it moves.
     *
     * <p>The moment comes in rather than off the clock, unlike {@link #weekAndStreakOf}, because the
     * caller is asking about a stretch of somebody's history with its own beginning and is entitled
     * to be answered about the same instant it judged everything else at. The scheme does not come
     * in with it: the history is every version the bank has ever published, so there is no reading of
     * it a caller could sensibly have an opinion about.
     *
     * @param from  the moment the stretch begins; the whole of the week containing it is counted
     * @param until the moment being asked about; the whole of the week containing it is counted
     */
    @Transactional(readOnly = true)
    public List<SavingsWeek> securedWeeksBetween(long customerId, Instant from, Instant until) {
        return WeekAndStreakDerivation.securedWeeksBetween(
                deposits, withdrawals, customerId, from, until,
                scheme.theSchemeThroughItsVersions());
    }
}
