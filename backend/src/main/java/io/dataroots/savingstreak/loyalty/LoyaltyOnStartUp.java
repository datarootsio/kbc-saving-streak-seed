package io.dataroots.savingstreak.loyalty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Makes the record of what has been paid unique over the deposit and the anniversary, before the
 * application serves anything.
 *
 * <p>Which is the whole of what makes the sweep idempotent by construction rather than by care. The
 * sweep also checks in Java, so a second run pays nothing without this; but the check and the
 * guarantee are different things. Two runs of the job at the same moment would both read an empty
 * set of already-paid anniversaries, and only a rule the database keeps stops both of them from
 * paying.
 *
 * <p>Here rather than on the entity because the entity cannot say it. The schema is generated from
 * the entity model ({@code ddl-auto=update}) against SQLite, and that dialect writes a composite
 * unique clause nowhere: declared as a unique constraint, or as an index marked unique, the table is
 * created without it and the only statement that reaches the database is a drop that does nothing.
 * A {@code create unique index} is a statement SQLite does accept, so this is where the guarantee
 * comes from — and it is a step a reviewer can see happening in a DEBUG start-up log rather than an
 * annotation they would have to take on trust.
 *
 * <p>The same shape as the other modules' start-up steps: it runs on every start, and all but the
 * first do nothing.
 */
@Component
class LoyaltyOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyOnStartUp.class);

    private final LoyaltyBonusPaidRepository paid;

    LoyaltyOnStartUp(LoyaltyBonusPaidRepository paid) {
        this.paid = paid;
    }

    /**
     * Called once every bean exists and before the context finishes refreshing — which is before the
     * web server binds its port and before the scheduler can fire the nightly sweep, so no
     * anniversary can be paid against a record that is not yet unique.
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (paid.theRecordIsAlreadyUniquePerAnniversary() > 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that the guarantee is something a reviewer can confirm on any start rather than only
            // on the first.
            log.debug("the record of paid anniversaries is already unique per deposit and "
                    + "anniversary index=one_bonus_per_deposit_per_anniversary");
            return;
        }
        paid.makeTheRecordUniquePerAnniversary();
        log.info("the record of paid anniversaries was made unique per deposit and anniversary "
                + "index=one_bonus_per_deposit_per_anniversary columns=[deposit_id, "
                + "anniversary_ordinal]");
    }
}
