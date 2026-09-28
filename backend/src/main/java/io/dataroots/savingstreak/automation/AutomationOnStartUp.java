package io.dataroots.savingstreak.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Makes the record of what the rules have done unique over the rule and the day it was due, before
 * the application serves anything.
 *
 * <p>Which is the whole of what makes the nightly rules job idempotent by construction rather than
 * by care. The job also keeps a cursor and checks the record in Java, so a second run moves no money
 * without this; but the check and the guarantee are different things. Two runs of the job at the
 * same moment would both read the same cursor and the same empty set of settled days, and only a
 * rule the database keeps stops both of them from depositing — which on this table means money.
 *
 * <p>Here rather than on the entity because the entity cannot say it. The schema is generated from
 * the entity model ({@code ddl-auto=update}) against SQLite, and that dialect writes a composite
 * unique clause nowhere: declared as a unique constraint, or as an index marked unique, the table is
 * created without it and the only statement that reaches the database is a drop that does nothing. A
 * {@code create unique index} is a statement SQLite does accept, so this is where the guarantee
 * comes from — and it is a step a reviewer can watch happen in a DEBUG start-up log rather than an
 * annotation they would have to take on trust. {@code AccountsOnStartUp}, {@code LoyaltyOnStartUp},
 * {@code GoalsOnStartUp} and {@code NotificationsOnStartUp} are the same step for the same reason.
 *
 * <p>The same shape as those: it runs on every start, and all but the first do nothing.
 */
@Component
class AutomationOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(AutomationOnStartUp.class);

    private final RuleOccurrenceRepository occurrences;

    AutomationOnStartUp(RuleOccurrenceRepository occurrences) {
        this.occurrences = occurrences;
    }

    /**
     * Called once every bean exists and before the context finishes refreshing — which is before the
     * web server binds its port and before the scheduler can fire the nightly job, so no rule can
     * fire against a record that is not yet unique.
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (occurrences.theRecordIsAlreadyUniquePerDayDue() > 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that the guarantee is something a reviewer can confirm on any start rather than only
            // on the first.
            log.debug("the record of what the saving rules did is already unique per rule and day "
                    + "due index=one_occurrence_per_rule_per_day");
            return;
        }
        occurrences.makeTheRecordUniquePerDayDue();
        log.info("the record of what the saving rules did was made unique per rule and day due "
                + "index=one_occurrence_per_rule_per_day columns=[saving_rule_id, due_on]");
    }
}
