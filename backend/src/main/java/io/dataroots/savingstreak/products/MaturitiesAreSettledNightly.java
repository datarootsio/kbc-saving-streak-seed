package io.dataroots.savingstreak.products;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Settles the terms that reached their day, once a night.
 *
 * <p>Nightly although a term is a matter of months, for the reason the interest sweep is nightly
 * although its scheme is monthly: every account's term is up on a different day of the calendar,
 * because a maturity is counted from the day the term started running. A monthly job would have to
 * pick one day and would leave nearly everybody locked into a term that had already ended.
 *
 * <p><strong>At a quarter past three in the morning, and the half-hour before the interest sweep is
 * the whole reason for the time.</strong> A term that matured overnight has to be on its new terms
 * <em>before</em> the month is priced, or an account that rolled onto a lower rate at four in the
 * morning would be paid for the month at the rate it stopped being on at three. Running second would
 * also make the order the figures were decided in depend on how long the earlier job took. The other
 * two neighbours are incidental: the points expiry at three and the loyalty bonuses at half past
 * have nothing to say about what agreement an account is living under.
 *
 * <p>The moment comes off the application's clock rather than the machine's, which is what makes a
 * twelve-month rule demonstrable in an afternoon: a trainer winds the clock three years forward,
 * runs this job through the development jobs endpoint, and watches three maturities settle in order.
 * A job that read {@code Instant.now()} would find nothing to do and say so convincingly.
 *
 * <p>In every profile, like everything else that is part of the application. Only the ability to run
 * it out of turn is a lab affordance, and that lives in the web layer.
 *
 * <p>Package-private, like the other sweeps in this application: the rule is the module's, and
 * nothing outside this package needs a handle on the thing that triggers it.
 */
@Component
class MaturitiesAreSettledNightly {

    private static final Logger log = LoggerFactory.getLogger(MaturitiesAreSettledNightly.class);

    /**
     * A quarter past three in the morning, every day — half an hour before the interest sweep.
     *
     * <p>A named constant because it is also what the jobs endpoint reports as this job's schedule,
     * and a trainer deciding whether to run it by hand is reading that line.
     */
    private static final String EVERY_NIGHT_AT_A_QUARTER_PAST_THREE = "0 15 3 * * *";

    private final MaturitiesService maturities;
    private final Clock clock;

    MaturitiesAreSettledNightly(MaturitiesService maturities, Clock clock) {
        this.maturities = maturities;
        this.clock = clock;
    }

    /**
     * Named for what it does rather than for when it does it, because the name is what a trainer
     * types into the development jobs endpoint to run it now.
     *
     * <p>Nothing is logged here about the outcome, and nothing is returned to be logged. The module
     * writes one INFO line per maturity saying what the terms said and what was done, and one per
     * sweep saying how many there were, which are the lines worth grepping; a second line here would
     * say the same thing from further away.
     */
    @Scheduled(cron = EVERY_NIGHT_AT_A_QUARTER_PAST_THREE)
    void settleMaturedTerms() {
        Instant now = clock.instant();
        // The reading, so that a sweep that settled nothing on a clock nobody had wound forward is
        // explainable without anybody having to ask the clock endpoint what year it thought it was.
        log.debug("maturity sweep takes its moment from the application clock clockReads={}", now);
        maturities.settleMaturedTerms(now);
    }
}
