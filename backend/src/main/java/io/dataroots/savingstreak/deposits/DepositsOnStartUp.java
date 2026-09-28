package io.dataroots.savingstreak.deposits;

import java.util.List;

import io.dataroots.savingstreak.accounts.AccountHolder;
import io.dataroots.savingstreak.accounts.AccountsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Brings the deposits already in the database up to what the module now records about them, before
 * the application serves anything.
 *
 * <p>There are two things to bring up. A deposit gained a record of how much of it is still in the
 * savings account, and the account's money balance is now summed from that rather than from what was
 * originally put in. Deposits written before the column existed hold nothing in it, and summing
 * those would report a balance no customer would recognise — so each of them is given back the whole
 * of its amount, which is what remains of a deposit that nothing could yet take money out of.
 *
 * <p>Then a deposit gained the customer whose saving it was, because a week and a run of weeks are
 * now counted per customer rather than per savings account. A deposit written before that column
 * says nothing about whose saving it was and would be counted into nobody's week: somebody who saved
 * EUR 60 yesterday would open the application on the morning of the upgrade to find they had saved
 * nothing this week and lost a run they were three weeks into. So each of those deposits is told
 * whose it was, by asking Accounts who holds the account the money went into — the mapping is that
 * module's answer, and a query in here reading its table would be Deposits knowing something about
 * savings accounts that it has no business knowing.
 *
 * <p>And then a deposit gained an origin, because a month's interest is now written into the same
 * ledger as a row of another kind. Every query that asks for the customer's own money — the mark a
 * deposit is judged against, the week, the anniversaries — asks the database for rows whose origin
 * says so, and a row that says nothing is not one of them: comparing a null to a value leaves it
 * out. A file written by the release before this one holds nothing but deposits and every one of
 * them would drop out of all three answers at once, so somebody would open the application on the
 * morning of the upgrade to find they had never saved anything and were owed no anniversary. Each
 * of those rows is told what it has always been, which is money its customer moved in.
 *
 * <p>Schema generation adds the columns; only the values are this class's business. The schema is
 * generated from the entity model rather than migrated ({@code ddl-auto=update}), which adds columns
 * but has no opinion about what should be in them for the rows that were already there. Until this
 * repo has migrations, that gap is filled here, where it is a statement with its reason next to it
 * rather than a surprise in a balance.
 *
 * <p>Runs on every start, and is written so that all but the first do nothing.
 */
@Component
class DepositsOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DepositsOnStartUp.class);

    private final DepositRepository deposits;
    private final AccountsService accounts;

    DepositsOnStartUp(DepositRepository deposits, AccountsService accounts) {
        this.deposits = deposits;
        this.accounts = accounts;
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
        giveEveryDepositWhatRemainsOfIt();
        sayWhoseSavingEveryDepositWas();
        sayWhereEveryRecordedDepositCameFrom();
    }

    private void giveEveryDepositWhatRemainsOfIt() {
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

    private void sayWhereEveryRecordedDepositCameFrom() {
        int said = deposits.sayThatEveryRecordedDepositWasTheCustomersOwnMoney();
        if (said == 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that a mark or a week that looks wrong on a restart is not blamed on a step nobody
            // can see.
            log.debug("no deposit was missing where its money came from deposits=0");
            return;
        }
        log.info("deposits recorded before this release told where their money came from "
                + "deposits={} origin=CUSTOMER", said);
    }

    private void sayWhoseSavingEveryDepositWas() {
        List<Long> savingsAccounts = deposits.savingsAccountsBehindDepositsWithoutACustomer();
        if (savingsAccounts.isEmpty()) {
            log.debug("no deposit was missing whose saving it was deposits=0");
            return;
        }
        int said = 0;
        int leftAlone = 0;
        for (long savingsAccountId : savingsAccounts) {
            AccountHolder holder = accounts.holderOfSavingsAccount(savingsAccountId).orElse(null);
            if (holder == null) {
                // Nobody to attribute them to, and guessing would count somebody else's saving into
                // this customer's week. There is no account to have taken the money, so no customer
                // is missing a week by their staying behind.
                log.warn("deposits into an account nobody holds left without a customer "
                        + "savingsAccountId={}", savingsAccountId);
                leftAlone++;
                continue;
            }
            said += deposits.sayWhoseSavingWentInto(savingsAccountId, holder.customerId());
        }
        log.info("deposits recorded before this release told whose saving they were deposits={} "
                        + "savingsAccounts={} accountsWithoutAHolder={}",
                said, savingsAccounts.size(), leftAlone);
    }
}
