package io.dataroots.savingstreak.points;

import java.util.List;

import io.dataroots.savingstreak.accounts.AccountHolder;
import io.dataroots.savingstreak.accounts.AccountsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Brings the points already in the database up to what the ledger now records about them, before the
 * application serves anything.
 *
 * <p>Points used to belong to the savings account the money went into and now belong to the customer
 * who holds it, so every batch credited before this release names an account and no customer.
 * Summing a customer's points would skip all of them, and somebody who saved for a cinema ticket
 * last week and starts the application today would be told they have nothing — so each of those
 * batches is handed to the customer who holds the account it was earned in, which is the same person
 * who could have spent it the day before the upgrade.
 *
 * <p>Who holds an account is asked of Accounts rather than joined to in SQL. The mapping is that
 * module's answer, and a query in here reading its table would be this module knowing something
 * about savings accounts that its own storage has just stopped recording.
 *
 * <p>Then the old column goes. Schema generation adds columns and never takes one away, and this one
 * cannot simply be left: it was written {@code not null}, so the first deposit after the upgrade
 * would be a batch that names a customer and no account, and the insert would be refused. The schema
 * is generated from the entity model rather than migrated ({@code ddl-auto=update}), and until this
 * repo has migrations that gap is filled here, where it is a statement with its reason next to it
 * rather than a surprise in a balance.
 *
 * <p>Runs on every start, and is written so that all but the first do nothing.
 */
@Component
class PointsOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(PointsOnStartUp.class);

    private final PointsCreditRepository credits;
    private final AccountsService accounts;

    PointsOnStartUp(PointsCreditRepository credits, AccountsService accounts) {
        this.credits = credits;
        this.accounts = accounts;
    }

    /**
     * Called once every bean exists and before the context finishes refreshing — which is before the
     * web server binds its port and can therefore be asked for a points balance.
     *
     * <p>That ordering is the whole reason this is not a {@code CommandLineRunner}, which Spring Boot
     * runs after the application is already accepting requests. A balance asked for in that window
     * would be summed from batches that had not yet been given their customer, and the first person
     * to open the page on the morning of an upgrade would be told their points were gone.
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (credits.batchesStillNameTheSavingsAccountTheyWereEarnedIn() == 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that a balance that looks wrong on a restart is not blamed on a step nobody can see.
            log.debug("points are already kept per customer, nothing to bring up batches=0");
            return;
        }
        List<Long> savingsAccounts = credits.savingsAccountsBehindBatchesWithoutACustomer();
        int given = 0;
        int leftAlone = 0;
        for (long savingsAccountId : savingsAccounts) {
            AccountHolder holder = accounts.holderOfSavingsAccount(savingsAccountId).orElse(null);
            if (holder == null) {
                // Nothing to hand them to, and guessing is worse than leaving them: a batch given to
                // the wrong person is points somebody else can spend. Nobody loses anything by their
                // staying behind — there is no account to have earned them and so no customer who
                // could ever have spent them — and they are simply never summed again.
                log.warn("points earned in an account nobody holds left without a customer "
                        + "savingsAccountId={}", savingsAccountId);
                leftAlone++;
                continue;
            }
            given += credits.giveBatchesEarnedIn(savingsAccountId, holder.customerId());
        }
        log.info("points credited before this release given to the customers who hold the accounts "
                        + "that earned them batches={} savingsAccounts={} accountsWithoutAHolder={}",
                given, savingsAccounts.size(), leftAlone);
        // Taken away whatever the pass found, because the column is what stops the next deposit from
        // being credited at all: a batch left un-owned is one balance short, a column that cannot be
        // written is every deposit from here on refused.
        credits.stopNamingTheSavingsAccountBatchesWereEarnedIn();
        log.info("points no longer name the savings account they were earned in column=savings_account_id");
    }
}
