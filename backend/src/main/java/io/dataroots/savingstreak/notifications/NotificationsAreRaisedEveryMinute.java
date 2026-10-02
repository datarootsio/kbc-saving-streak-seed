package io.dataroots.savingstreak.notifications;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Checks notification rules once a minute so the exercise can be demonstrated without waiting
 * overnight. The first EUR 100 crossing is also announced immediately after a deposit.
 * Reads the application's movable clock, just like a manually triggered job.
 */
@Component
class NotificationsAreRaisedEveryMinute {

    private static final Logger log = LoggerFactory.getLogger(NotificationsAreRaisedEveryMinute.class);

    /** Run on the first second of each minute. */
    private static final String EVERY_MINUTE = "0 * * * * *";

    private final NotificationsService notifications;
    private final Clock clock;

    NotificationsAreRaisedEveryMinute(NotificationsService notifications, Clock clock) {
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
    @Scheduled(cron = EVERY_MINUTE)
    void raiseNotifications() {
        Instant now = clock.instant();
        // The reading, so that a sweep that said nothing on a clock nobody had wound forward is
        // explainable without anybody having to ask the clock endpoint what year it thought it was.
        log.debug("notifications sweep takes its moment from the application clock clockReads={}",
                now);
        notifications.raiseNotifications(now);
    }
}
