package io.dataroots.savingstreak.automation;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the saving rules that have fallen due, once a night.
 *
 * <p><strong>At two in the morning, and the hour is load-bearing.</strong> Income lands at one,
 * points expire at three, loyalty pays at half past and the notifications sweep runs at four, and
 * that order exists so each reads state the ones before it have settled. The rules run second
 * because a sweep reads a balance: fired before the salary landed, a rule told to move everything
 * above eight hundred euros would find the account at eight hundred and move nothing, on the one
 * morning it had most to do. And they run before the notifications sweep, so that a transfer that
 * could not be honoured is in the history the sweep reads that same night.
 *
 * <p><strong>It catches up, because on a wound clock catching up is the only thing it ever does.</strong>
 * {@code MovableClock} moves in whole calendar days and a cron expression never fires for the days
 * it skipped, so a trainer who winds a month forward and runs this once has to see the days in
 * between or the feature cannot be demonstrated at all. Every occurrence between each rule's cursor
 * and now, oldest first; {@link WhichOccurrencesAreDue} is the calendar and {@link AutomationService}
 * is the bookkeeping.
 *
 * <p>The moment comes off the application's clock rather than the machine's, which is what makes a
 * monthly rule demonstrable in an afternoon. A job that read {@code Instant.now()} would find
 * nothing to do and say so convincingly.
 *
 * <p>In every profile, like everything else that is part of the application. Only the ability to run
 * it out of turn is a lab affordance, and that lives in the web layer.
 *
 * <p>Package-private: the job is this module's, and nothing outside this package needs a handle on
 * the thing that triggers it.
 */
@Component
class SavingRulesRunNightly {

    private static final Logger log = LoggerFactory.getLogger(SavingRulesRunNightly.class);

    /**
     * Two in the morning, every day — after the income has landed and before the points expire.
     *
     * <p>A named constant because it is also what the jobs endpoint reports as this job's schedule,
     * and a trainer deciding whether to run it by hand is reading that line.
     */
    private static final String EVERY_NIGHT_AT_TWO = "0 0 2 * * *";

    private final AutomationService automation;
    private final Clock clock;

    SavingRulesRunNightly(AutomationService automation, Clock clock) {
        this.automation = automation;
        this.clock = clock;
    }

    /**
     * Named for what it does rather than for when it does it, because the name is what a trainer
     * types into {@code POST /api/dev/jobs/{name}/run}.
     *
     * <p>Nothing is logged here about the outcome, and nothing is returned to be logged. The module
     * writes one INFO line per run carrying the moment, the rules it considered, the occurrences it
     * fired and what they came to, which is the line worth grepping; a second line here would say
     * the same thing from further away.
     */
    @Scheduled(cron = EVERY_NIGHT_AT_TWO)
    void fireSavingRulesDue() {
        Instant now = clock.instant();
        // The reading, so that a run that fired nothing on a clock nobody had wound forward is
        // explainable without anybody having to ask the clock endpoint what day it thought it was.
        log.debug("saving rules run takes its moment from the application clock clockReads={}", now);
        automation.fireRulesDueBy(now);
    }
}
