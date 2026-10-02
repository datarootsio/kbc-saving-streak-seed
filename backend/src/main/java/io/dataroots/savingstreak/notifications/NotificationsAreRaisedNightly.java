package io.dataroots.savingstreak.notifications;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Says what the rules have to say, once a night.
 *
 * <p>At four in the morning, and the hour is load-bearing. Points expire at three and loyalty pays
 * at half past, so a sweep at four reads state the night has already settled: an anniversary that
 * arrived last night has been paid and moved on by the time this looks at it, and is never
 * announced as still coming.
 *
 * <p>The first EUR 100 milestone is also checked immediately after a deposit. Other changes use
 * this nightly sweep. A rung lost to a withdrawal at noon is announced at four the next morning.
 * Both triggers keep notification rules and logging in the Notifications module.
 *
 * <p>The moment comes off the application's clock rather than the machine's, which is what makes a
 * rule about a year demonstrable in an afternoon: a trainer winds the clock forward, runs this job
 * through the development jobs endpoint, and watches the notifications arrive.
 *
 * <p>In every profile, like everything else that is part of the application. Only the ability to run
 * it out of turn is a lab affordance, and that lives in the web layer.
 *
 * <p>Package-private: the sweep is this module's, and nothing outside the package needs a handle on
 * the thing that triggers it.
 */
@Component
class NotificationsAreRaisedNightly {

    private static final Logger log = LoggerFactory.getLogger(NotificationsAreRaisedNightly.class);

    /**
     * Four in the morning, every day — an hour after the points-expiry sweep and half an hour after
     * the loyalty one.
     *
     * <p>A named constant because it is also what the jobs endpoint reports as this job's schedule,
     * and a trainer deciding whether to run it by hand is reading that line.
     */
    private static final String EVERY_NIGHT_AT_FOUR = "0 0 4 * * *";

    private final NotificationsService notifications;
    private final Clock clock;

    NotificationsAreRaisedNightly(NotificationsService notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Named for what it does rather than for when it does it, because the name is what a trainer
     * types into {@code POST /api/dev/jobs/{name}/run} to make it happen now.
     *
     * <p>Nothing is logged here about the outcome and nothing is returned to be logged. The module
     * writes one line per sweep and one per notification raised, which are the lines worth grepping;
     * a line here would say the same thing from further away.
     */
    @Scheduled(cron = EVERY_NIGHT_AT_FOUR)
    void raiseNotifications() {
        Instant now = clock.instant();
        // The reading, so that a sweep that said nothing on a clock nobody had wound forward is
        // explainable without anybody having to ask the clock endpoint what year it thought it was.
        log.debug("notifications sweep takes its moment from the application clock clockReads={}",
                now);
        notifications.raiseNotifications(now);
    }
}
