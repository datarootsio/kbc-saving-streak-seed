package io.dataroots.savingstreak.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Makes the record of announced anniversaries unique over the deposit, the reason and the day,
 * before the application serves anything.
 *
 * <p>Which is what makes the anniversary half of the sweep idempotent by construction rather than by
 * care. The sweep also checks in Java, so a second run announces nothing without this; but the check
 * and the guarantee are different things. Two runs of the job at the same moment would both read the
 * same empty set of already-announced anniversaries, and only a rule the database keeps stops both
 * of them from writing the announcement.
 *
 * <p>Here rather than on the entity for the reason {@code LoyaltyOnStartUp} gives about its own
 * index, which holds word for word: the schema is generated from the entity model
 * ({@code ddl-auto=update}) against SQLite, and that dialect writes a composite unique clause
 * nowhere. It is also the only place a <em>partial</em> index could come from — the entity model has
 * no way to say "over these rows only", and over these rows only is exactly what this feature needs.
 * {@link NotificationRepository#makeTheRecordUniquePerDepositAndAnniversary} argues out why the
 * balance rows have to stay outside it.
 *
 * <p>The same shape as the other modules' start-up steps: it runs on every start, and all but the
 * first do nothing.
 */
@Component
class NotificationsOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(NotificationsOnStartUp.class);

    private final NotificationRepository notifications;

    NotificationsOnStartUp(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /**
     * Called once every bean exists and before the context finishes refreshing — which is before the
     * web server binds its port and before the scheduler can fire the nightly sweep, so no
     * anniversary can be announced against a record that is not yet unique.
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (notifications.theRecordIsAlreadyUniquePerDepositAndAnniversary() > 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that the guarantee is something a reviewer can confirm on any start rather than only
            // on the first.
            log.debug("the record of announced anniversaries is already unique per deposit, reason "
                    + "and anniversary index=one_notification_per_deposit_and_anniversary");
            return;
        }
        notifications.makeTheRecordUniquePerDepositAndAnniversary();
        log.info("the record of announced anniversaries was made unique per deposit, reason and "
                + "anniversary index=one_notification_per_deposit_and_anniversary "
                + "columns=[deposit_id, reason, occurs_on] over=[deposit_id is not null]");
    }
}
