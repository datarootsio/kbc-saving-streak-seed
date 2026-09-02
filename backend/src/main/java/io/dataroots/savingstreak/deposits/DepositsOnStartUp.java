package io.dataroots.savingstreak.deposits;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Brings the deposits already in the database up to what the module now records about them, before
 * the application serves anything.
 *
 * <p>There is one thing to bring up so far. A deposit gained a record of how much of it is still in
 * the savings account, and the account's money balance is now summed from that rather than from what
 * was originally put in. Deposits written before the column existed hold nothing in it, and summing
 * those would report a balance no customer would recognise — so each of them is given back the whole
 * of its amount, which is what remains of a deposit that nothing could yet take money out of.
 *
 * <p>Schema generation adds the column; only the values are this class's business. The schema is
 * generated from the entity model rather than migrated ({@code ddl-auto=update}), which adds columns
 * but has no opinion about what should be in them for the rows that were already there. Until this
 * repo has migrations, that gap is filled here, where it is one statement with its reason next to it
 * rather than a surprise in a balance.
 *
 * <p>Runs on every start, and is written so that all but the first do nothing.
 */
@Component
class DepositsOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DepositsOnStartUp.class);

    private final DepositRepository deposits;

    DepositsOnStartUp(DepositRepository deposits) {
        this.deposits = deposits;
    }

    /**
     * Called once every bean exists and before the context finishes refreshing — which is before the
     * web server binds its port and can therefore be asked for a balance.
     *
     * <p>That ordering is the whole reason this is not a {@code CommandLineRunner}, which Spring
     * Boot runs after the application is already accepting requests. A balance asked for in that
     * window would be summed from deposits that had not yet been told what remains of them, and the
     * first customer to open the page on the morning of an upgrade would be answered with a failure.
     */
    @Override
    public void afterSingletonsInstantiated() {
        int filledIn = deposits.giveEveryDepositWhatRemainsOfIt();
        if (filledIn == 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that a balance that looks wrong on a restart is not blamed on a step nobody can see.
            log.debug("no deposit was missing what remains of it deposits=0");
            return;
        }
        log.info("deposits recorded before this release given what remains of them deposits={} "
                + "remainingAmount=theirOwnAmount", filledIn);
    }
}
