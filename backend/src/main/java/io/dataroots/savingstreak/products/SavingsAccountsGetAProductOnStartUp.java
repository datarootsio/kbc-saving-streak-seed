package io.dataroots.savingstreak.products;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.WhatAnAccountIsLivingUnder;
import io.dataroots.savingstreak.streaks.SavingsWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Puts every savings account that existed before this catalogue did onto free savings at the bank's
 * opening terms, and stamps the version onto every deposit that landed in one, before the
 * application serves anything.
 *
 * <p><strong>An existing database is migrated, not deleted.</strong> This repository has a dozen
 * start-up migrations and a stated policy that they run before the web server binds its port; a
 * release that said "delete your database" would be the first one to break it — and what would be
 * deleted here is somebody's saving, their points, their run of weeks and the anniversaries their
 * deposits are counting towards. Schema generation adds the table; only the values are this class's
 * business, which is exactly the gap {@code DepositsOnStartUp} and {@code PointsOnStartUp} fill for
 * their own modules and say so at length.
 *
 * <p><strong>Every account, including the ones nobody holds.</strong> A shared pot holds a savings
 * account with no customer behind it, and instant access is precisely how that account already
 * behaves: money goes in from the members' current accounts and comes back out when the pot is
 * settled, with nothing to give notice of. An account left with no agreement would be an account no
 * rule could answer about — and the first thing that would ask is the very next contribution into
 * the pot, which stamps the version it landed under onto its deposit. The two migrations that came
 * before this one both had to leave holderless accounts behind, because points and weeks belong to
 * a customer and there was none; an agreement belongs to the account, so this one does not.
 *
 * <p><strong>And it says the interest starts today.</strong> The agreement is dated at the day the
 * money first arrived, which is the honest reading of when the account started being a savings
 * account — but it is emphatically not the day this bank started paying interest on it, which is
 * this morning. The two dates are written separately for exactly that reason: the periods are
 * counted from the first, so an account opened on the 20th goes on being paid for the 20th to the
 * 20th, and the sweep pays only the ones that begin on or after the second. <strong>No interest is
 * backdated</strong>, which this class promised before there was any interest to backdate.
 *
 * <p><strong>Dated at the account's first deposit, or at the migration when it has none.</strong>
 * The day an agreement began is the day interest will be counted from and the day a term would
 * mature from, and the honest reading for an account nobody recorded an opening date for is the day
 * its money first arrived. An account that has never been paid into has no such day and is dated
 * today, which is the first day anybody can say anything about it. <strong>No interest is
 * backdated</strong> either way: nothing in this ticket pays a cent, and the later ticket that does
 * starts every migrated account's first period at its own arrival rather than at this date.
 *
 * <p><strong>Version 1 and never the version on offer.</strong> Free savings has published a second
 * version at a lower rate, effective two months before the catalogue was seeded, and moving an
 * existing account onto it would be this application changing the terms somebody's money is living
 * under, overnight, without asking. That is the one thing the whole feature exists to make
 * impossible, and this is the migration that would have done it. Taking newer terms is a door the
 * customer opens, in a later ticket.
 *
 * <p><strong>Driven by {@link ProductsOnStartUp} rather than by Spring.</strong> It is deliberately
 * not a {@code SmartInitializingSingleton} of its own: this step is worth nothing until the
 * catalogue is seeded — an account cannot be put on a product the database has never heard of, and
 * a version cannot be stamped on a deposit before the version exists — and Spring gives no ordering
 * between two singletons that both want to run after everything is built. The seed calls this, in
 * order, as its last act. The alternative, declaring a dependency on the other component and hoping
 * the callback order follows the bean graph, is a guarantee that holds until somebody renames a
 * class.
 *
 * <p>Runs on every start, and is written so that all but the first do nothing and say so at DEBUG.
 */
@Component
class SavingsAccountsGetAProductOnStartUp {

    private static final Logger log =
            LoggerFactory.getLogger(SavingsAccountsGetAProductOnStartUp.class);

    private final AccountsService accounts;
    private final DepositsService deposits;
    private final WhatEachSavingsAccountIsOn agreements;
    private final AccountAgreementRepository onRecord;
    private final Clock clock;

    SavingsAccountsGetAProductOnStartUp(AccountsService accounts, DepositsService deposits,
                                        WhatEachSavingsAccountIsOn agreements,
                                        AccountAgreementRepository onRecord, Clock clock) {
        this.accounts = accounts;
        this.deposits = deposits;
        this.agreements = agreements;
        this.onRecord = onRecord;
        this.clock = clock;
    }

    /**
     * The whole of the migration: which accounts are not on a product, and which deposits do not
     * say what they landed under.
     *
     * <p>Both questions are asked before anything is written, so that the ordinary start — every
     * start after the first — is two counting queries and a line at DEBUG. A walk that wrote the
     * same update against every account on every start would be a hundred statements a morning to
     * change nothing, and a log that claimed to have migrated something every time is a log nobody
     * reads twice.
     *
     * <p>The two passes are separate because they are about different rows and the second depends
     * on the first: an account has to be on a version before its deposits can be stamped with one.
     * A start killed between them leaves the accounts migrated and the deposits unstamped, which
     * the next start finishes — the second question is asked of the deposits rather than of the
     * accounts precisely so that it can be.
     */
    void putEveryAccountThatPredatesTheCatalogueOnAProduct() {
        List<Long> everyAccount = accounts.everySavingsAccount();
        Set<Long> alreadyOnAProduct = new HashSet<>(onRecord.everySavingsAccountAlreadyOnAProduct());
        List<Long> withoutAnAgreement = everyAccount.stream()
                .filter(savingsAccountId -> !alreadyOnAProduct.contains(savingsAccountId))
                .toList();
        long depositsWithoutAVersion = deposits.howManyDepositsDoNotSayWhichVersionTheyLandedUnder();
        log.debug("savings accounts considered for a product accounts={} alreadyOnAProduct={} "
                        + "withoutAnAgreement={} depositsWithoutAVersion={}",
                everyAccount.size(), alreadyOnAProduct.size(), withoutAnAgreement.size(),
                depositsWithoutAVersion);
        if (withoutAnAgreement.isEmpty() && depositsWithoutAVersion == 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that an account reading no agreement after a restart is not blamed on a step nobody
            // can see.
            log.debug("every savings account is already on a product and every deposit says which "
                    + "version it landed under accounts={} deposits=0", everyAccount.size());
            return;
        }
        int accountsPutOn = putThemOnFreeSavings(withoutAnAgreement);
        int depositsStamped = depositsWithoutAVersion == 0 ? 0 : stampTheDepositsInto(everyAccount);
        // The line the ticket asks for by name: how many accounts and how many deposits this
        // release brought forward, and onto what. A reviewer opening an older database reads this
        // one line to know what the morning of the upgrade did.
        log.info("savings accounts were put on the product they have always been on accounts={} "
                        + "deposits={} product={} version={} datedAt=theirFirstDepositOrToday "
                        + "today={}",
                accountsPutOn, depositsStamped, WhatEachSavingsAccountIsOn.FREE_SAVINGS,
                WhatEachSavingsAccountIsOn.THE_OPENING_TERMS, today());
    }

    /**
     * Each account put on free savings at version 1, dated at the day its money first arrived.
     *
     * <p>One account at a time and one transaction each, rather than one statement over the lot:
     * the date is a different answer for every row and it comes from another module's ledger. A
     * start killed part-way through leaves the accounts it reached migrated and the rest for the
     * next start, which is the right failure — every one of them is a complete agreement or no
     * agreement at all, and never half of one.
     */
    private int putThemOnFreeSavings(List<Long> withoutAnAgreement) {
        // Read once, outside the loop, so that every account this migration touches says it
        // started earning interest on the same day — the day of the migration. A reading per
        // account would put two of them on either side of midnight and leave a reviewer wondering
        // what happened between them.
        LocalDate migratedOn = today();
        int putOn = 0;
        for (long savingsAccountId : withoutAnAgreement) {
            LocalDate openedOn = whenItsMoneyFirstArrived(savingsAccountId);
            agreements.putOnTheOpeningTerms(savingsAccountId, openedOn, migratedOn);
            putOn++;
        }
        return putOn;
    }

    /**
     * Every deposit that does not say which version it landed under, told the version the account
     * it landed in is on.
     *
     * <p>Asked account by account because the answer is the account's: today every one of them is
     * version 1, and the day an account is opened on a notice product that stops being true — so
     * the version is read off the agreement rather than written in as a constant here, and this
     * step keeps working when it does.
     *
     * <p>Only the deposits that say nothing are touched. A deposit that already names a version
     * names the version it actually landed under, which may not be the version its account is on
     * today, and a migration that overwrote it would be rewriting history to match the present.
     */
    private int stampTheDepositsInto(List<Long> everyAccount) {
        int stamped = 0;
        for (long savingsAccountId : everyAccount) {
            int version = agreements.whatAnAccountIsLivingUnder(savingsAccountId)
                    .map(WhatAnAccountIsLivingUnder::version)
                    .orElse(WhatEachSavingsAccountIsOn.THE_OPENING_TERMS);
            int theseOnes = deposits.sayWhichVersionTheDepositsIntoAnAccountLandedUnder(
                    savingsAccountId, version);
            if (theseOnes > 0) {
                log.debug("deposits told which version they landed under savingsAccountId={} "
                        + "version={} deposits={}", savingsAccountId, version, theseOnes);
            }
            stamped += theseOnes;
        }
        return stamped;
    }

    /**
     * The day this account's money first arrived, or today for an account that has never been paid
     * into.
     *
     * <p>Asked of the Deposits module rather than joined to in SQL, for the reason
     * {@code PointsOnStartUp} gives about who holds an account: the ledger is that module's answer,
     * and a query in here reading its table would be Products knowing how Deposits stores things.
     *
     * <p>The moment is read into a day in the zone this application counts its days in, so that a
     * deposit made at half past midnight belongs to the day the rest of the application would put
     * it on.
     */
    private LocalDate whenItsMoneyFirstArrived(long savingsAccountId) {
        return deposits.whenTheFirstDepositIntoLanded(savingsAccountId)
                .map(this::asADay)
                .orElseGet(this::today);
    }

    private LocalDate asADay(Instant moment) {
        return LocalDate.ofInstant(moment, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
    }

    private LocalDate today() {
        return asADay(clock.instant());
    }
}
