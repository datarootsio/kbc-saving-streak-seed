package io.dataroots.savingstreak.loyalty;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pays the anniversaries that have arrived, once a night.
 *
 * <p>Nightly, so that a customer is paid within a day of an anniversary rather than on the next new
 * year. A deposit's clock recurs and nothing about the payment is urgent to the minute, but "the
 * money I left alone paid me last week" is a promise that has to land while the customer still
 * remembers the anniversary.
 *
 * <p>After the points-expiry sweep, half an hour behind it. A night that both pays an anniversary
 * and retires an old batch of points should do the two in the order a customer would describe them —
 * yesterday's points go, today's arrive — and the order matters in one visible case: a bonus paid
 * for an anniversary more than twelve months past is credited already beyond its own twelve months,
 * and running second means it survives the night and is swept up by the following one rather than
 * appearing and vanishing inside the same run.
 *
 * <p>The moment comes off the application's clock rather than the machine's, which is what makes a
 * twelve-month rule demonstrable in an afternoon: a trainer winds the clock a year forward, runs
 * this job through the development jobs endpoint, and watches a year of loyalty arrive. A job that
 * read {@code Instant.now()} would find nothing to do and say so convincingly.
 *
 * <p>In every profile, like everything else that is part of the application. Only the ability to run
 * it out of turn is a lab affordance, and that lives in the web layer.
 *
 * <p>Package-private: the sweep is the Loyalty module's, and nothing outside this package needs a
 * handle on the thing that triggers it.
 */
@Component
class LoyaltyBonusesArePaidNightly {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyBonusesArePaidNightly.class);

    /**
     * Half past three in the morning, every day — half an hour after the points-expiry sweep.
     *
     * <p>A named constant because it is also what the jobs endpoint reports as this job's schedule,
     * and a trainer deciding whether to run it by hand is reading that line.
     */
    private static final String EVERY_NIGHT_AT_HALF_PAST_THREE = "0 30 3 * * *";

    private final LoyaltyService loyalty;
    private final Clock clock;

    LoyaltyBonusesArePaidNightly(LoyaltyService loyalty, Clock clock) {
        this.loyalty = loyalty;
        this.clock = clock;
    }

    /**
     * Named for what it does rather than for when it does it, because the name is what a trainer
     * types into the development jobs endpoint to run it now.
     *
     * <p>Nothing is logged here about the outcome, and nothing is returned to be logged. The module
     * writes one INFO line per sweep carrying the moment it judged, the cut-off it queried with and
     * what it paid, which is the line worth grepping; a second line here would say the same thing
     * from further away.
     */
    @Scheduled(cron = EVERY_NIGHT_AT_HALF_PAST_THREE)
    void payLoyaltyBonuses() {
        Instant now = clock.instant();
        // The reading, so that a sweep that paid nothing on a clock nobody had wound forward is
        // explainable without anybody having to ask the clock endpoint what year it thought it was.
        log.debug("loyalty sweep takes its moment from the application clock clockReads={}", now);
        loyalty.payLoyaltyBonuses(now);
    }
}
