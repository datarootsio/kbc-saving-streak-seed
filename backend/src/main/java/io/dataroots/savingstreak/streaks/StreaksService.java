package io.dataroots.savingstreak.streaks;

import java.time.Clock;

import io.dataroots.savingstreak.deposits.DepositsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Streaks module's face to the rest of the application. It answers what a customer's saving
 * looks like: how much they have put away in the week they are part-way through, how much more that
 * week asks for, how many consecutive weeks they have secured, the longest run they have ever had,
 * and what a euro paid in earns at that run's rate.
 *
 * <p>The customer's, not one savings account's. Somebody saving towards two goals is saving: a week
 * counts what they put away wherever they put it, and the run of weeks and the rate it pays are
 * theirs. Which account the euros went into decides where the money sits and nothing else.
 *
 * <p>Nothing is stored. Every answer is derived from the deposit records at the moment it is asked
 * for, which is the deciding property of the whole feature: the application's clock is movable, in
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
 * moves when a trainer moves time. Reading the clock is the whole of what this class adds to
 * {@link WeekAndStreakDerivation}, which is the derivation itself and is shared with the Deposits
 * module — a deposit is priced by the same walk that this reports, off the moment the money moved
 * rather than off the moment somebody opened the page. What a week is — Monday to Sunday, in
 * Brussels — is {@link SavingsWeek}'s answer and is stated there once.
 */
@Service
public class StreaksService {

    private final DepositsService deposits;
    private final Clock clock;

    StreaksService(DepositsService deposits, Clock clock) {
        this.deposits = deposits;
        this.clock = clock;
    }

    /**
     * How this customer's saving is going right now: the week they are part-way through, and the run
     * of consecutive secured weeks behind that week.
     *
     * <p>One reading of the clock behind both. Two reads either side of midnight on a Monday would
     * answer about two different weeks — see {@link WeekAndStreak}.
     */
    @Transactional(readOnly = true)
    public WeekAndStreak weekAndStreakOf(long customerId) {
        return WeekAndStreakDerivation.asAt(deposits, customerId, clock.instant());
    }
}
