package io.dataroots.savingstreak.deposits;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Brings the withdrawals already in the database up to what the module now records about them,
 * before the application serves anything.
 *
 * <p>There is one thing to bring up. A withdrawal gained a purpose, because the charge for breaking
 * a fixed term early is now written into the same ledger as a row of another kind. The two queries
 * that count a week's saving ask the database for the rows the customer took out themselves, and a
 * row that says nothing is not one of them: comparing a null to a value leaves it out. A file
 * written by the release before this one holds nothing but customers' withdrawals, so every one of
 * them would drop out of both answers at once, and somebody who took EUR 60 back out yesterday
 * would open the application on the morning of the upgrade to find their week counted only what
 * they put in. Each of those rows is told what it has always been.
 *
 * <p>A class of its own rather than a fourth step inside {@link DepositsOnStartUp}, and the reason
 * is that class's own name and first sentence: it is about the deposits already in the database,
 * it holds the deposit repository and nothing else, and a step about a different table filed inside
 * it would be findable only by somebody who already knew to look there. Two components, two
 * subjects, and the module's start-up story reads as two sentences instead of one with a clause
 * bolted on.
 *
 * <p>Schema generation adds the column; only the values are this class's business. The schema is
 * generated from the entity model rather than migrated ({@code ddl-auto=update}), which adds columns
 * but has no opinion about what should be in them for the rows that were already there. Until this
 * repo has migrations, that gap is filled here, where it is a statement with its reason next to it
 * rather than a surprise in a run of weeks.
 *
 * <p>Runs on every start, and is written so that all but the first do nothing.
 */
@Component
class WithdrawalsOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalsOnStartUp.class);

    private final WithdrawalRepository withdrawals;

    WithdrawalsOnStartUp(WithdrawalRepository withdrawals) {
        this.withdrawals = withdrawals;
    }

    /**
     * Called once every bean exists and before the context finishes refreshing — which is before the
     * web server binds its port and can therefore be asked how much somebody saved this week.
     *
     * <p>That ordering is the whole reason this is not a {@code CommandLineRunner}, which Spring
     * Boot runs after the application is already accepting requests. A week asked for in that window
     * would be counted from withdrawals that had not yet been told why they happened, and the first
     * customer to open the page on the morning of an upgrade would be shown a week that never was.
     */
    @Override
    public void afterSingletonsInstantiated() {
        int said = withdrawals.sayThatEveryRecordedWithdrawalWasTakenByTheCustomer();
        if (said == 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that a week that looks wrong on a restart is not blamed on a step nobody can see.
            log.debug("no withdrawal was missing why it happened withdrawals=0");
            return;
        }
        log.info("withdrawals recorded before this release told why they happened withdrawals={} "
                + "purpose=CUSTOMER", said);
    }
}
