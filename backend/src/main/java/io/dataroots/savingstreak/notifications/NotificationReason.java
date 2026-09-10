package io.dataroots.savingstreak.notifications;

import java.util.EnumSet;
import java.util.Set;

/**
 * Why a notification was raised: the rules that have decided something was worth saying.
 *
 * <p>An enum, and public, for two reasons that pull the same way. The web layer sends the reason as
 * its name and the browser writes the sentence, because every euro and every date in this
 * application is formatted in the page and a sentence composed in Java would fork that formatting
 * into a second place that drifts. And a later feature — points about to expire, a reward newly
 * affordable — adds a value here without touching any of the machinery that stores, sweeps or
 * reports a notification.
 *
 * <p>A reason names a rule rather than a severity. Whether a notification is a warning or a
 * congratulation is a reading of it, and the page is where that reading belongs.
 */
public enum NotificationReason {

    /**
     * A savings balance stands on a rung of {@link BalanceThresholds} it was not last known to
     * stand on, and the new rung is the higher one. The rung landed on is the notification's
     * {@code amount}.
     */
    BALANCE_THRESHOLD_REACHED,

    /**
     * A savings balance no longer reaches a rung it did. The notification's {@code amount} is the
     * lowest rung the balance no longer reaches, so that reading the record backwards says exactly
     * where the balance now stands — see {@link NotificationsService}.
     */
    BALANCE_THRESHOLD_LOST;

    /**
     * The reasons that are about a savings balance rather than about a deposit, which is the set the
     * sweep asks the record for when it wants to know which rung an account was last known to stand
     * on. Written down here because it is a property of the reasons themselves, and a caller
     * listing them by hand would be a second list to keep in step with this one.
     */
    static final Set<NotificationReason> THE_BALANCE_REASONS =
            EnumSet.of(BALANCE_THRESHOLD_REACHED, BALANCE_THRESHOLD_LOST);
}
