package io.dataroots.savingstreak.accounts;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Credits the paydays that have arrived, once a night.
 *
 * <p><strong>At one in the morning, and the hour is load-bearing.</strong> The night already runs
 * points expiry at three, loyalty at half past and the notifications sweep at four, and that order
 * exists so each reads state the ones before it have settled. Money arriving is the first thing that
 * happens in a night, because everything else in this application reads a balance — a payday
 * credited after a rule that swept the account would be a salary the sweep could not see, and the
 * customer would be told their automation did nothing on the one morning it had most to do.
 *
 * <p><strong>It catches up, because on a wound clock catching up is the only thing it ever does.</strong>
 * {@code MovableClock} moves in whole calendar days and a cron expression never fires for the days
 * it skipped, so a trainer who winds three months forward and runs this once has to be paid three
 * times or the feature cannot be demonstrated at all. Every payday between each account's cursor and
 * now, oldest first; {@link WhenIncomeIsDue} is the calendar and {@link AccountsService} is the
 * bookkeeping.
 *
 * <p>The moment comes off the application's clock rather than the machine's, which is what makes a
 * monthly rule demonstrable in an afternoon. A job that read {@code Instant.now()} would find
 * nothing to do and say so convincingly.
 *
 * <p>In every profile, like everything else that is part of the application. Only the ability to run
 * it out of turn is a lab affordance, and that lives in the web layer.
 *
 * <p>Package-private: the job is the Accounts module's, and nothing outside this package needs a
 * handle on the thing that triggers it.
 */
@Component
class IncomeLandsOnPayday {

    private static final Logger log = LoggerFactory.getLogger(IncomeLandsOnPayday.class);

    /**
     * One in the morning, every day — first in the night, two hours before points expire.
     *
     * <p>A named constant because it is also what the jobs endpoint reports as this job's schedule,
     * and a trainer deciding whether to run it by hand is reading that line.
     */
    private static final String EVERY_NIGHT_AT_ONE = "0 0 1 * * *";

    private final AccountsService accounts;
    private final Clock clock;

    IncomeLandsOnPayday(AccountsService accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * Named for what it does rather than for when it does it, because the name is what a trainer
     * types into {@code POST /api/dev/jobs/{name}/run}.
     *
     * <p>Nothing is logged here about the outcome, and nothing is returned to be logged. The module
     * writes one INFO line per run carrying the moment, the accounts it considered, the paydays it
     * credited and what they came to, which is the line worth grepping; a second line here would say
     * the same thing from further away.
     */
    @Scheduled(cron = EVERY_NIGHT_AT_ONE)
    void creditMonthlyIncome() {
        Instant now = clock.instant();
        // The reading, so that a run that credited nothing on a clock nobody had wound forward is
        // explainable without anybody having to ask the clock endpoint what day it thought it was.
        log.debug("monthly income run takes its moment from the application clock clockReads={}", now);
        accounts.creditIncomeDueBy(now);
    }
}
