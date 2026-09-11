package io.dataroots.savingstreak.points;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sweeps up the points whose twelve months have run out, once a night.
 *
 * <p>The application's first scheduled job of its own. Until points expired there was nothing to
 * schedule and {@link io.dataroots.savingstreak.jobs.SchedulingIsOn} stood there for a job that did
 * not exist yet; this is that job.
 *
 * <p>Nightly, at an hour when nobody is looking at their balance. Expiry is not urgent to the minute
 * — a batch has been sitting for twelve months by the time this touches it — and the only thing a
 * later sweep costs is that a customer can still see, and still spend, points whose anniversary
 * passed earlier that day. Spending them is the outcome the rule wants anyway, so the generosity is
 * on the right side.
 *
 * <p>The moment comes off the application's clock rather than the machine's, which is what makes a
 * twelve-month rule demonstrable in an afternoon: a trainer winds the clock a year forward, runs this
 * job through the development jobs endpoint, and watches a year-old balance go. A job that read
 * {@code Instant.now()} would find nothing to do and say so convincingly.
 *
 * <p>In every profile, like everything else that is part of the application. Only the ability to run
 * it out of turn is a lab affordance, and that lives in the web layer.
 *
 * <p>Package-private, like {@link PointsOnStartUp}: the sweep is the ledger's, and nothing outside
 * this package needs a handle on the thing that triggers it.
 */
@Component
class OldPointsExpireNightly {

    private static final Logger log = LoggerFactory.getLogger(OldPointsExpireNightly.class);

    /**
     * Three in the morning, every day.
     *
     * <p>Daily rather than yearly, so that a batch is retired within a day of its anniversary rather
     * than on the next new year. A named constant because it is also what the jobs endpoint reports
     * as this job's schedule, and a reader deciding whether to run it by hand is reading this line.
     */
    private static final String EVERY_NIGHT_AT_THREE = "0 0 3 * * *";

    private final PointsService points;
    private final Clock clock;

    OldPointsExpireNightly(PointsService points, Clock clock) {
        this.points = points;
        this.clock = clock;
    }

    /**
     * Named for what it does rather than for when it does it, because the name is what somebody
     * types into the development jobs endpoint to run it now.
     *
     * <p>Nothing is logged here about the outcome, and nothing is returned to be logged. The ledger
     * writes one INFO line per sweep carrying the cut-off it used and what it took, which is the line
     * worth grepping; a second line here would say the same thing from further away, and a figure
     * handed back to a scheduled method is a figure nothing can read.
     */
    @Scheduled(cron = EVERY_NIGHT_AT_THREE)
    void expireOldPoints() {
        Instant now = clock.instant();
        // The reading, so that a sweep that took nothing on a clock nobody had wound forward is
        // explainable without anybody having to ask the clock endpoint what year it thought it was.
        log.debug("points expiry sweep takes its moment from the application clock clockReads={}", now);
        points.expireOldPoints(now);
    }
}
