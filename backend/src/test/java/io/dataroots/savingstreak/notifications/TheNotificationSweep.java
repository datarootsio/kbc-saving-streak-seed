package io.dataroots.savingstreak.notifications;

import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;

/**
 * The sweep, as a test drives it: run it by the name a trainer would type, then read what it said.
 *
 * <p>Shared rather than copied into each test of this feature, for the reason
 * {@code AnApplicationWithAClockToMove} gives about the streak tests: five copies of "run the job
 * called raiseNotifications and pick out the rows about this account" are five chances to disagree
 * about what the sweep is.
 *
 * <p>Run over HTTP, at the endpoint a trainer uses, so a test that says the sweep raised something
 * is also saying the job is reachable by name. Read through {@code NotificationsService}, which is
 * this module's only way in and has no endpoint in front of it yet — see
 * {@link AnApplicationWithAClockToMove#theApplicationsOwn}.
 */
final class TheNotificationSweep {

    /** What the job is called, which is the name of the method that does it. */
    static final String THE_JOB = "raiseNotifications";

    private final AnApplicationWithAClockToMove app;

    TheNotificationSweep(AnApplicationWithAClockToMove app) {
        this.app = app;
    }

    /** Runs tonight's sweep now, as a trainer would. */
    void runs() {
        app.runJob(THE_JOB);
    }

    /**
     * Everything that has been said about one savings account, newest first.
     *
     * <p>By account rather than by customer, because a customer can hold two savings accounts and a
     * test that used one of them should not have to care what was said about the other.
     */
    List<RaisedNotification> whatWasSaidAbout(long savingsAccountId, String customerName) {
        return app.theApplicationsOwn(NotificationsService.class)
                .notificationsOf(app.customerIdOf(customerName)).stream()
                .filter(raised -> savingsAccountId == raised.savingsAccountId())
                .toList();
    }

    /**
     * Only what was said about the deposits in one savings account and their coming anniversaries,
     * newest first.
     *
     * <p>Separate from the read above because a deposit that moves a balance onto a rung raises a
     * balance notification too, and a test about anniversaries would otherwise have to count rows it
     * is not about — and would go green the day a change to the ladder happened to add or drop one.
     * Two rules share the sweep and each test asks about one of them.
     */
    List<RaisedNotification> whatWasSaidAboutAnAnniversaryIn(long savingsAccountId,
                                                             String customerName) {
        return whatWasSaidAbout(savingsAccountId, customerName).stream()
                .filter(raised -> NotificationReason.THE_ANNIVERSARY_REASONS
                        .contains(raised.reason()))
                .toList();
    }

    /** The record itself, for the one test whose subject is the rule the database keeps. */
    NotificationRepository theRecord() {
        return app.theApplicationsOwn(NotificationRepository.class);
    }
}
