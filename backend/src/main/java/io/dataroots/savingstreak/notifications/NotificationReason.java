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
    BALANCE_THRESHOLD_LOST,

    /**
     * A deposit's next anniversary falls within
     * {@link AnAnniversaryComingSoon#HOW_LONG_BEFORE_AN_ANNIVERSARY_IS_WORTH_SAYING}, is worth at
     * least one point, and the deposit stands behind an older one still holding money. The day and
     * the points are the notification's {@code occursOn} and {@code points}.
     *
     * <p>The calm one of the pair. A withdrawal drains the oldest deposit first, so the euros in
     * this one are behind at least one other deposit's worth of money: the anniversary is coming
     * and nothing a single withdrawal does reaches it first.
     */
    LOYALTY_BONUS_ABOUT_TO_PAY,

    /**
     * The same anniversary, on the deposit that is first in line for the next withdrawal — the
     * oldest one in the account still holding money.
     *
     * <p>The warning of the pair, and the reason this feature exists. A bonus is worked out on what
     * the deposit still holds at the moment its anniversary is judged, so the next euro withdrawn
     * from this account comes out of exactly the money this anniversary would have been paid on.
     * {@code WithdrawalsService} has always drained the oldest deposit first and calls that a
     * protection; this is that protection said out loud to the person it protects.
     */
    LOYALTY_BONUS_AT_RISK;

    /**
     * The reasons that are about a savings balance rather than about a deposit, which is the set the
     * sweep asks the record for when it wants to know which rung an account was last known to stand
     * on. Written down here because it is a property of the reasons themselves, and a caller
     * listing them by hand would be a second list to keep in step with this one.
     */
    static final Set<NotificationReason> THE_BALANCE_REASONS =
            EnumSet.of(BALANCE_THRESHOLD_REACHED, BALANCE_THRESHOLD_LOST);

    /**
     * The reasons that are about one deposit's anniversary, which is the set the sweep asks the
     * record for when it wants to know which anniversaries it has already announced.
     *
     * <p>Mutually exclusive for one deposit and one anniversary: a deposit is either the oldest one
     * in its account still holding money or it is not. Both can nevertheless stand against the same
     * deposit and the same day over time, and that is the point — a deposit shielded behind an older
     * one, whose shield is then emptied, is first in line for the next withdrawal and is announced
     * again under the other reason. That escalation is why the reason is part of what makes an
     * announcement unique rather than something the record overwrites.
     */
    static final Set<NotificationReason> THE_ANNIVERSARY_REASONS =
            EnumSet.of(LOYALTY_BONUS_ABOUT_TO_PAY, LOYALTY_BONUS_AT_RISK);
}
