package io.dataroots.savingstreak.points;

import java.time.Instant;
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
 * <p><strong>And every batch is stamped with the moment it expires.</strong> A batch used to be told
 * every night how long it had left, against a constant; it is now stamped when it is earned, and the
 * sweep reads the stamp. Every batch written before that release has no stamp, and a sweep that
 * found one would have to either skip it for ever or make the figure up — so each of them is given
 * the twelve months it already has. That is exactly what those rows compute today, so nothing moves,
 * nothing expires early, and the first sweep after the upgrade does precisely what the last sweep
 * before it did.
 *
 * <p>Runs on every start, and is written so that all but the first do nothing.
 */
@Component
class PointsOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(PointsOnStartUp.class);

    /**
     * The lifetime every batch written before this release was promised, in months.
     *
     * <p><strong>The literal twelve, and deliberately not the scheme's figure.</strong> These rows
     * were credited by code that added twelve months to the moment they were earned, every night,
     * against a constant — so twelve months is what they have been computing and twelve months is
     * what their owners were told. The scheme's version 1 says the same thing today, which is why
     * nothing moves either way; but reading it from there would make this pass a statement about
     * what the scheme happens to say rather than about what these particular rows were promised, and
     * the whole reason the stamp exists is that those two can come apart.
     */
    private static final int THE_TWELVE_MONTHS_EVERY_EXISTING_BATCH_WAS_PROMISED = 12;

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
        giveEveryBatchToTheCustomerWhoHoldsTheAccountThatEarnedIt();
        stampEveryBatchWithTheTwelveMonthsItWasPromised();
    }

    /**
     * Writes onto every unstamped batch the moment twelve months after it was earned.
     *
     * <p>A floor and never a reset, like every other start-up pass in this application: only the
     * batches with no stamp are touched, so a start after the first finds nothing to do, and a stamp
     * that is already there is never argued with. That last part is what makes the column worth
     * having — a pass that corrected what it found would give every batch whatever the current rule
     * says each morning, which is the nightly recomputation this ticket removed, moved to a worse
     * hour.
     *
     * <p>Every batch, including the ones already spent to nothing and the ones already swept. Their
     * stamps will never be read, but a column that is null on some rows and filled on others is a
     * column the next reader has to have the history of a release explained to them.
     *
     * <p>The rule is applied rather than the months added here, so that twelve calendar months means
     * in this pass exactly what it means in a credit — including the clamp that puts a batch earned
     * on 29 February onto the 28th.
     */
    private void stampEveryBatchWithTheTwelveMonthsItWasPromised() {
        List<PointsCredit> unstamped = credits.batchesNotYetCarryingWhenTheyExpire();
        if (unstamped.isEmpty()) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that a sweep that took nothing is not blamed on a step nobody can see.
            log.debug("every batch of points already carries the moment it expires batches=0");
            return;
        }
        int stamped = 0;
        for (PointsCredit batch : unstamped) {
            Instant promised = PointsExpiry.anniversaryOf(batch.getEarnedAt(),
                    THE_TWELVE_MONTHS_EVERY_EXISTING_BATCH_WAS_PROMISED);
            if (batch.stampExpiringAt(promised)) {
                stamped++;
            }
        }
        credits.saveAll(unstamped);
        log.info("points credited before this release stamped with the twelve months they were "
                        + "promised batches={} months={}",
                stamped, THE_TWELVE_MONTHS_EVERY_EXISTING_BATCH_WAS_PROMISED);
    }

    /**
     * Hands every batch credited before points belonged to a customer to whoever holds the savings
     * account that earned it, and then takes the old column away.
     */
    private void giveEveryBatchToTheCustomerWhoHoldsTheAccountThatEarnedIt() {
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
