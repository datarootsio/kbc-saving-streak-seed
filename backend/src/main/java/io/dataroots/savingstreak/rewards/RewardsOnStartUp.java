package io.dataroots.savingstreak.rewards;

import java.util.List;

import io.dataroots.savingstreak.accounts.AccountHolder;
import io.dataroots.savingstreak.accounts.AccountsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Brings the claims already in the database up to what the module now records about them, before the
 * application serves anything.
 *
 * <p>A claim used to be made by a savings account and is now made by the customer, because the points
 * that pay for it are theirs. Every claim recorded before this release names an account and no
 * customer, and a customer's list of what they have claimed would be missing all of them — a voucher
 * somebody is holding, absent from the page that is supposed to show it to them. So each of those
 * claims is handed to the customer who holds the account it was made from, which is the person who
 * made it.
 *
 * <p>The same shape as the Points module's own start-up step, for the same reason and against the
 * same release. Who holds an account is asked of Accounts rather than joined to in SQL: the mapping
 * is that module's answer.
 *
 * <p>Then the old column goes, because it was written {@code not null} and a claim made from now on
 * has no savings account to put in it — the first claim after the upgrade would be refused by the
 * database.
 *
 * <p>Runs on every start, and is written so that all but the first do nothing.
 */
@Component
class RewardsOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(RewardsOnStartUp.class);

    private final RedemptionRepository redemptions;
    private final AccountsService accounts;

    RewardsOnStartUp(RedemptionRepository redemptions, AccountsService accounts) {
        this.redemptions = redemptions;
        this.accounts = accounts;
    }

    /**
     * Called once every bean exists and before the context finishes refreshing — which is before the
     * web server binds its port and can therefore be asked what somebody has claimed.
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (redemptions.claimsStillNameASavingsAccount() == 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that a list that looks short after a restart is not blamed on a step nobody can see.
            log.debug("claims are already kept per customer, nothing to bring up claims=0");
            return;
        }
        List<Long> savingsAccounts = redemptions.savingsAccountsBehindClaimsWithoutACustomer();
        int given = 0;
        int leftAlone = 0;
        for (long savingsAccountId : savingsAccounts) {
            AccountHolder holder = accounts.holderOfSavingsAccount(savingsAccountId).orElse(null);
            if (holder == null) {
                // Nobody to hand them to, and guessing would put somebody else's voucher on a
                // customer's page. There is no account to have made them, so no customer is missing
                // anything by their staying where they are.
                log.warn("claims made from an account nobody holds left without a customer "
                        + "savingsAccountId={}", savingsAccountId);
                leftAlone++;
                continue;
            }
            given += redemptions.giveClaimsMadeFrom(savingsAccountId, holder.customerId());
        }
        log.info("claims recorded before this release given to the customers who hold the accounts "
                        + "they were made from claims={} savingsAccounts={} accountsWithoutAHolder={}",
                given, savingsAccounts.size(), leftAlone);
        // Taken away whatever the pass found: a claim left un-owned is one voucher missing from a
        // page, a column that cannot be written is every claim from here on refused.
        redemptions.stopNamingTheSavingsAccountClaimsWereMadeFrom();
        log.info("claims no longer name a savings account column=savings_account_id");
    }
}
