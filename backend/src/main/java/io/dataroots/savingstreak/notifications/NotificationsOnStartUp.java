package io.dataroots.savingstreak.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Makes the record unique over each of the things it must not say twice — a deposit's anniversary,
 * a saving-rule occurrence that could not be honoured, a bill's due date, a category's month, an
 * account's month, a notice that has run its days, a term's maturity date and a version of a
 * product's terms — before the application serves anything.
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
     * anniversary and no failed transfer can be announced against a record that is not yet unique.
     */
    @Override
    public void afterSingletonsInstantiated() {
        makeTheAnniversaryRecordUnique();
        makeTheFailedTransferRecordUnique();
        makeTheUnpaidBillRecordUnique();
        makeTheBudgetWarningRecordUnique();
        makeTheOverCommittedWarningRecordUnique();
        makeTheNoticeRecordUnique();
        makeTheMaturityWarningRecordUnique();
        makeTheBetteredTermsRecordUnique();
    }

    /**
     * One announcement per notice, kept by the database rather than by the sweep alone.
     *
     * <p>This is the guarantee behind "a notice that came free a fortnight ago and is still standing
     * is announced once, not on every night since". Ready notice never lapses, so the sweep reads
     * the same standing notice every night for as long as its money is unspent.
     *
     * <p>The reason is not in the key, unlike the anniversary index: there is only one thing this
     * application will ever say about a notice coming free, and it never comes free twice.
     */
    private void makeTheNoticeRecordUnique() {
        if (notifications.theRecordIsAlreadyUniquePerNotice() > 0) {
            log.debug("the record of announced notices is already unique per notice "
                    + "index=one_notification_per_notice");
            return;
        }
        notifications.makeTheRecordUniquePerNotice();
        log.info("the record of announced notices was made unique per notice "
                + "index=one_notification_per_notice columns=[notice_id] "
                + "over=[notice_id is not null]");
    }

    /**
     * One warning per account per maturity date, kept the same way.
     *
     * <p>Partial over the reason itself rather than over a null column, because this family fills
     * the account and the day and so do a notice that has become ready and a deposit's anniversary.
     * The day is in the key because a rolling term matures every year and every one of those years
     * is worth one line — {@code NotificationRepository.makeTheRecordUniquePerMaturity} argues that
     * out.
     */
    private void makeTheMaturityWarningRecordUnique() {
        if (notifications.theRecordIsAlreadyUniquePerMaturity() > 0) {
            log.debug("the record of announced maturities is already unique per account and day "
                    + "index=one_maturity_warning_per_account_and_day");
            return;
        }
        notifications.makeTheRecordUniquePerMaturity();
        log.info("the record of announced maturities was made unique per account and day "
                + "index=one_maturity_warning_per_account_and_day "
                + "columns=[savings_account_id, occurs_on] "
                + "over=[reason = 'A_TERM_IS_ABOUT_TO_MATURE']");
    }

    /**
     * One bettered-terms announcement per account per version, kept the same way — the guarantee
     * behind "once per version, and never again once the account has taken them".
     */
    private void makeTheBetteredTermsRecordUnique() {
        if (notifications.theRecordIsAlreadyUniquePerAccountAndVersion() > 0) {
            log.debug("the record of announced bettered terms is already unique per account and "
                    + "version index=one_bettered_terms_notification_per_account_and_version");
            return;
        }
        notifications.makeTheRecordUniquePerAccountAndVersion();
        log.info("the record of announced bettered terms was made unique per account and version "
                + "index=one_bettered_terms_notification_per_account_and_version "
                + "columns=[savings_account_id, terms_version] over=[terms_version is not null]");
    }

    /**
     * One warning per category per month, kept by the database rather than by the sweep alone.
     *
     * <p>This is the guarantee behind "a category four fifths gone on the tenth is said once, not on
     * every night to the thirty-first". The sweep asks what it has already said before it says
     * anything, and that check is what normally does the work; a rule the database keeps is what
     * makes it true when two runs read the same record at the same moment. The reason is in the key
     * with the category and the month because running low and having gone over are two different
     * things to say about one month.
     *
     * <p>Partial, over the rows that name a category, for the reason the unpaid-bill index is
     * partial: every other reason in this table names none, and a plain unique index over three
     * columns that are null on most rows would refuse the second balance notification ever raised.
     */
    private void makeTheBudgetWarningRecordUnique() {
        if (notifications.theRecordIsAlreadyUniquePerCategoryAndMonth() > 0) {
            log.debug("the record of announced budget warnings is already unique per category, "
                    + "reason and month index=one_notification_per_category_and_month");
            return;
        }
        notifications.makeTheRecordUniquePerCategoryAndMonth();
        log.info("the record of announced budget warnings was made unique per category, reason and "
                + "month index=one_notification_per_category_and_month "
                + "columns=[category_id, reason, occurs_on] over=[category_id is not null] "
                + "note=[occurs_on carries the day the month began]");
    }

    /**
     * One over-committed warning per account per month, kept the same way and for the same reason.
     *
     * <p>Partial over the reason itself rather than over a null column, because this family fills
     * the account and the day and so does a bill that could not be paid — and two bills falling due
     * on one day are two honest rows sharing all three values.
     * {@code NotificationRepository.makeTheRecordUniquePerOverCommittedMonth} argues that out.
     */
    private void makeTheOverCommittedWarningRecordUnique() {
        if (notifications.theRecordIsAlreadyUniquePerOverCommittedMonth() > 0) {
            log.debug("the record of announced over-committed months is already unique per account "
                    + "and month index=one_over_committed_warning_per_account_and_month");
            return;
        }
        notifications.makeTheRecordUniquePerOverCommittedMonth();
        log.info("the record of announced over-committed months was made unique per account and "
                + "month index=one_over_committed_warning_per_account_and_month "
                + "columns=[current_account_id, occurs_on] "
                + "over=[reason = 'THE_MONTH_IS_OVER_COMMITTED'] "
                + "note=[occurs_on carries the day the month began]");
    }

    /**
     * One notification per bill per due date, kept by the database rather than by the raiser alone.
     *
     * <p>This is the guarantee behind "a rent owed for six months raises one notification, not six".
     * The raiser asks what it has already said before it says anything, and that check is what
     * normally does the work; a rule the database keeps is what makes it true when two runs read the
     * same record at the same moment. The reason is in the key with the bill and the day because a
     * later reason about the same due date would be a different thing to say.
     *
     * <p>Partial, over the rows that name a bill, because the piling-up warning names none and every
     * other reason in this table names none either — a plain unique index over three columns that
     * are null on most rows is an index that would refuse the second balance notification ever
     * raised. Created here rather than declared on the entity for the reason the other two are: the
     * SQLite dialect writes a composite unique clause nowhere, so a constraint declared in the model
     * reaches the database as nothing at all.
     */
    private void makeTheUnpaidBillRecordUnique() {
        if (notifications.theRecordIsAlreadyUniquePerBillAndDueDate() > 0) {
            log.debug("the record of announced bills that could not be paid is already unique per "
                    + "bill, reason and due date index=one_notification_per_bill_and_due_date");
            return;
        }
        notifications.makeTheRecordUniquePerBillAndDueDate();
        log.info("the record of announced bills that could not be paid was made unique per bill, "
                + "reason and due date index=one_notification_per_bill_and_due_date "
                + "columns=[bill_id, reason, occurs_on] over=[bill_id is not null]");
    }

    /**
     * One announcement per failed saving-rule occurrence, kept by the database rather than only by
     * the sweep's own check — the same bargain, and the same argument, as the anniversary index.
     */
    private void makeTheFailedTransferRecordUnique() {
        if (notifications.theRecordIsAlreadyUniquePerOccurrence() > 0) {
            log.debug("the record of announced automatic transfers that did not happen is already "
                    + "unique per occurrence index=one_notification_per_occurrence");
            return;
        }
        notifications.makeTheRecordUniquePerOccurrence();
        log.info("the record of announced automatic transfers that did not happen was made unique "
                + "per occurrence index=one_notification_per_occurrence columns=[occurrence_id] "
                + "over=[occurrence_id is not null]");
    }

    private void makeTheAnniversaryRecordUnique() {
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
