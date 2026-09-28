package io.dataroots.savingstreak.products;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pays the months that have ended, once a night.
 *
 * <p>Nightly although the scheme is monthly, for the reason the loyalty sweep is nightly although
 * its clock is yearly: every account's months end on a different day of the calendar, because a
 * period is counted from the day the account was opened. A monthly job would have to pick one day
 * and would pay nearly everybody late.
 *
 * <p>At a quarter to four in the morning, which puts it after the loyalty bonuses at half past
 * three and before the notifications at four. That ordering is deliberate: a night that pays an
 * anniversary, posts a month's interest and then tells somebody their term is maturing does those
 * things in the order a customer would describe them. It is also the order the figures depend on —
 * an anniversary is worked out from what a deposit still holds and interest from what the account
 * held each day, so neither disturbs the other, while a notification written at four can mention
 * both.
 *
 * <p>The moment comes off the application's clock rather than the machine's, which is what makes a
 * monthly rule demonstrable in an afternoon: a trainer winds the clock a year forward, runs this
 * job through the development jobs endpoint, and watches twelve months of interest arrive. A job
 * that read {@code Instant.now()} would find nothing to do and say so convincingly.
 *
 * <p>In every profile, like everything else that is part of the application. Only the ability to
 * run it out of turn is a lab affordance, and that lives in the web layer.
 *
 * <p>Package-private, like the other two sweeps in this application: the rule is the module's, and
 * nothing outside this package needs a handle on the thing that triggers it.
 */
@Component
class InterestIsPostedNightly {

    private static final Logger log = LoggerFactory.getLogger(InterestIsPostedNightly.class);

    /**
     * A quarter to four in the morning, every day — a quarter of an hour after the loyalty bonuses
     * and a quarter before the notifications.
     *
     * <p>A named constant because it is also what the jobs endpoint reports as this job's schedule,
     * and a trainer deciding whether to run it by hand is reading that line.
     */
    private static final String EVERY_NIGHT_AT_A_QUARTER_TO_FOUR = "0 45 3 * * *";

    private final InterestService interest;
    private final Clock clock;

    InterestIsPostedNightly(InterestService interest, Clock clock) {
        this.interest = interest;
        this.clock = clock;
    }

    /**
     * Named for what it does rather than for when it does it, because the name is what a trainer
     * types into the development jobs endpoint to run it now.
     *
     * <p>Nothing is logged here about the outcome, and nothing is returned to be logged. The module
     * writes one INFO line per sweep carrying the moment it judged, how many accounts it considered
     * and what it paid, which is the line worth grepping; a second line here would say the same
     * thing from further away.
     */
    @Scheduled(cron = EVERY_NIGHT_AT_A_QUARTER_TO_FOUR)
    void postMonthlyInterest() {
        Instant now = clock.instant();
        // The reading, so that a sweep that paid nothing on a clock nobody had wound forward is
        // explainable without anybody having to ask the clock endpoint what year it thought it was.
        log.debug("interest sweep takes its moment from the application clock clockReads={}", now);
        interest.postMonthlyInterest(now);
    }
}
