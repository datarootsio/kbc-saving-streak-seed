package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import io.dataroots.savingstreak.accounts.WhoeverRecordsWhatASavingsAccountIsOn;
import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.deposits.TheTermsADepositLandsUnder;
import io.dataroots.savingstreak.deposits.WhatAEuroSavedIntoAnAccountIsWorth;
import io.dataroots.savingstreak.deposits.WhatAnAccountIsLivingUnder;
import io.dataroots.savingstreak.loyalty.LoyaltyRate;
import io.dataroots.savingstreak.loyalty.WhatAnAnniversaryPaysHere;
import io.dataroots.savingstreak.streaks.SavingsWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The record of which agreement every savings account is living under: written when an account is
 * opened, read whenever anything needs to know what the account is on.
 *
 * <p><strong>A class of its own rather than more methods on {@link ProductsService}</strong>, and
 * the reason is the sentence that service leads with: it reads and refuses, it does not write, and
 * every transaction it opens is read-only so that a published version cannot be edited by accident.
 * This is the module's one writer of anything, and folding it in would take that guarantee away
 * from the catalogue to give a home to something that is not about the catalogue at all. The two
 * also answer different questions — what a product is selling today, and what one account is living
 * under — which is the same split {@link AProductOnOffer} and {@link TheAgreementAnAccountIsOn}
 * make one layer out.
 *
 * <p><strong>It is the module's answer to four questions other modules declared</strong>, and it
 * implements all four interfaces rather than having a class each, because all four turn on the same
 * row: Accounts says an account was opened and needs an agreement written, Deposits asks which
 * version the money is landing under and what a euro saved into the account is worth, and Loyalty
 * asks what an anniversary pays there. One row answers them, one class owns the row, and four
 * classes sharing it would be four places to remember when taking newer terms arrives.
 *
 * <p><strong>Three of the four are read out of the version the account is living under, never out
 * of the version on the shelf.</strong> That is the whole of what this module is for, and it is the
 * mistake the two pricing answers are most likely to make: free savings has published a second
 * version at a lower rate, so an account opened before it must be priced by version 1 however many
 * versions the catalogue has since. {@link #theVersionOfRecordFor} is the one place that lookup
 * happens, and {@link TheTermsOnOfferToday} — which is the right answer to a different question —
 * is not reachable from any of them.
 *
 * <p><strong>Neither pricing answer lets a basis point out.</strong> The two figures are held as
 * integers and the two modules that ask for them speak plain decimals, so the conversion happens
 * here, on this side of the interface, where the number still has its unit attached.
 * {@link ProductTerms#anniversaryRatePerWholeEuro} argues that at length: the same integer read as
 * basis points, as a percentage or as a fraction is three different numbers, and two of them handed
 * to {@code LoyaltyRate} would pay a hundred or ten thousand times too much without anything
 * throwing.
 *
 * <p><strong>An account goes on the product the customer chose, and on free savings when nobody
 * chose anything.</strong> Two sentences because there are two ways an account comes into
 * existence. A customer picking a card off the catalogue is the decision this whole feature exists
 * to make possible, and the word they picked arrives here to be judged: the bank has to sell such a
 * thing, and it has to be still opening accounts on it. The accounts nobody chooses for — a new
 * customer's first account, and the one behind a shared pot — go on free savings, because instant
 * access is what every savings account in this application has always been and putting an account
 * somebody did not ask for on a twelve-month lock would be the application choosing for them.
 *
 * <p><strong>A new account goes on the version being sold the day it is opened; an account that
 * predates the catalogue goes on version 1.</strong> Those look like two rules and are one: an
 * account is written under the terms that were current when it began, and an account that has been
 * open since before the bank published anything began under the bank's opening terms. Free savings
 * was repriced two months before the catalogue was seeded, so an account opened this morning is on
 * version 2 and an account with a deposit from last year is on version 1 — and neither of them
 * moves when the next version is published. The alternative, putting everything on version 1, would
 * quietly sell somebody terms that stopped being on offer two months ago; the other alternative,
 * putting everything on the current version, would silently move every existing account onto a
 * lower rate on the morning of a release, which is the one thing this whole feature exists to make
 * impossible.
 *
 * <p><strong>The version is asked of the product that was chosen, never assumed to be one.</strong>
 * Each product has published its own versions on its own days, and "the one on offer today" is
 * {@link TheTermsOnOfferToday}'s answer over that product's list — the same answer the catalogue
 * card the customer pressed was drawn from. An account opened this morning therefore reads the rate
 * it was advertised at, which is the only version of this that is not a broken promise.
 *
 * <p><strong>And it closes an agreement, which is what closing a savings account means.</strong>
 * Nothing is deleted: the row stays, naming the product, the version and the day it began, and
 * gains the day it ended. Only an emptied account may be closed, so the balance is asked for before
 * anything is written — by whoever calls this, because a class that fetched a balance for itself
 * would be this module holding the ledger. {@link AccountAgreement} argues why the date lives on
 * that row rather than on the savings account, and why there is no way back.
 */
@Service
class WhatEachSavingsAccountIsOn
        implements WhoeverRecordsWhatASavingsAccountIsOn, TheTermsADepositLandsUnder,
        WhatAEuroSavedIntoAnAccountIsWorth, WhatAnAnniversaryPaysHere {

    private static final Logger log = LoggerFactory.getLogger(WhatEachSavingsAccountIsOn.class);

    /**
     * What a savings account nobody chose a product for is on.
     *
     * <p>Free savings, because instant access is the only agreement this application has ever
     * actually offered: money moves in and out whenever the holder likes, nothing is refused and
     * nothing has to be kept in. Putting an existing account on anything else would be changing the
     * rules its holder has been saving under, retrospectively, which is the thing the spec's
     * migration section rules out in so many words — and putting a <em>new</em> account nobody
     * chose for on anything else would be worse, because a twelve-month lock nobody asked for is a
     * lock all the same.
     *
     * <p><strong>Not checked against the door to new accounts, and that is deliberate.</strong> An
     * administrator who closed free savings would stop customers choosing it and would not stop a
     * new customer being opened, or a shared pot: those are accounts the bank opens as part of
     * something else, and failing them over a catalogue decision would make closing a product
     * break signing up. The choice a customer makes is the one the door is about.
     */
    static final String FREE_SAVINGS = "INSTANT";

    /** The bank's opening terms, which is what an account that predates the catalogue is on. */
    static final int THE_OPENING_TERMS = 1;

    /**
     * What a euro is worth where nothing says otherwise: the multiple of one, which is exactly what
     * every deposit in this application earned before there were products to multiply it by.
     *
     * <p>{@link BasisPoints#ONE_WHOLE_MULTIPLE} read as the figure that leaves this module, so that
     * the default and the seeded rows are one decision. An account with no agreement on record is
     * priced at it, and so is every product the catalogue seeds except the two that pay more.
     */
    private static final BigDecimal THE_MULTIPLE_THAT_CHANGES_NOTHING =
            BasisPoints.asAMultiple(BasisPoints.ONE_WHOLE_MULTIPLE);

    private final AccountAgreementRepository agreements;
    private final SavingsProductRepository products;
    private final ProductTermsRepository terms;
    /**
     * Ticket 07's reading of what a term says today, asked one question and only when somebody is
     * taking newer terms: is this account still locked in.
     *
     * <p>Quoted rather than reimplemented, for the reason that class already argues about the
     * maturity date: a second copy of the calendar rule disagrees with the first on exactly the
     * dates that are hard, and a twelve-month term opened on the 29th of February is the one worth
     * getting right once. It also means the day named in the refusal here is the same day named in
     * the refusal a withdrawal gets, which is what stops a customer being told two different
     * mornings by two screens of one application.
     *
     * <p>No cycle: that class holds two repositories and a clock and asks nothing of this one.
     */
    private final TheTermAnAccountIsLockedInto theTerm;
    private final Clock clock;

    WhatEachSavingsAccountIsOn(AccountAgreementRepository agreements,
                               SavingsProductRepository products,
                               ProductTermsRepository terms,
                               TheTermAnAccountIsLockedInto theTerm,
                               Clock clock) {
        this.agreements = agreements;
        this.products = products;
        this.terms = terms;
        this.theTerm = theTerm;
        this.clock = clock;
    }

    /**
     * Puts a freshly opened savings account on free savings, under the version being sold today.
     *
     * <p>Said by Accounts the moment the row exists, inside the transaction that wrote it, so that
     * an account and the record of what it is on are committed together. Nothing is refused here
     * and nothing can be: the account is already open.
     *
     * <p><strong>Idempotent, and it has to be.</strong> An account that already has an agreement is
     * left exactly as it is — not re-dated, not moved to the current version — because the second
     * caller of this is a restart, and a start-up step that re-opened every agreement at today's
     * terms would be the retrospective rewrite this module exists to prevent.
     */
    @Transactional
    @Override
    public void aSavingsAccountWasOpened(long savingsAccountId) {
        putOnWhatIsBeingSoldToday(savingsAccountId, FREE_SAVINGS, "a savings account was opened");
    }

    /**
     * Puts a freshly opened savings account on the product its customer chose, under the version
     * being sold today.
     *
     * <p>Said by Accounts the moment the row exists and inside the transaction that wrote it, like
     * the sentence above it — and unlike that sentence, this one can refuse. Two things are asked of
     * the word that arrived: that the bank sells such a product, and that it is still opening
     * accounts on it. Neither is something Accounts could have checked, which is the whole reason
     * the choice travels here to be judged.
     *
     * <p>A refusal takes the account back with it. Both statements are one transaction, so a
     * customer who names a product this bank does not sell is left holding exactly what they held
     * before — and never an account on nothing, which is the one state no rule in this application
     * could answer about.
     *
     * <p><strong>Not idempotent in the way the sentence above it is, and it does not have to
     * be.</strong> Nothing calls this twice: it is said once, about an account written a line
     * earlier, by the one door a customer chooses a product through. An account that somehow
     * already had an agreement is left on the one it had, because a start-up step re-opening
     * agreements at today's terms is the retrospective rewrite this module exists to prevent —
     * {@link #putOnRecord} is where that is kept true for both sentences at once.
     */
    @Transactional
    @Override
    public void aSavingsAccountWasOpenedOn(long savingsAccountId, String theProductChosen) {
        String code = theProductChosen == null ? "" : theProductChosen.trim();
        log.debug("a savings account is being opened on a chosen product savingsAccountId={} "
                + "productAsChosen={}", savingsAccountId, theProductChosen);
        if (code.isBlank()) {
            throw refusing(ProductRefused.Kind.NO_PRODUCT_CHOSEN,
                    "Choose the savings product to open the account on.",
                    "savingsAccountId=" + savingsAccountId);
        }
        SavingsProduct product = products.findByCode(code).orElseThrow(() -> refusing(
                ProductRefused.Kind.NO_SUCH_PRODUCT,
                "There is no savings product called " + code + ".",
                "savingsAccountId=" + savingsAccountId + " product=" + code));
        if (!product.openToNewAccounts()) {
            throw refusing(ProductRefused.Kind.A_PRODUCT_CLOSED_TO_NEW_ACCOUNTS,
                    product.name() + " is closed to new accounts, so no account can be opened on "
                            + "it. The accounts already on it carry on exactly as they were.",
                    "savingsAccountId=" + savingsAccountId + " product=" + product.code());
        }
        putOnWhatIsBeingSoldToday(savingsAccountId, product.code(),
                "a savings account was opened on the product its customer chose");
    }

    /**
     * Closes an emptied savings account, and answers with the agreement as it now reads.
     *
     * <p><strong>The balance is handed in rather than fetched.</strong> What an account still holds
     * is the ledger's answer, and a class in this module that asked the ledger for itself would be
     * the record of agreements holding the record of money — two modules' answers in one place, and
     * a dependency this module does not need. {@link ProductsService} reads it at the moment the
     * question is put and passes it straight through, which is the same bargain every derived total
     * in this application makes.
     *
     * <p><strong>Only an emptied account may be closed.</strong> Not because the euros would be
     * lost — nothing here deletes anything — but because they would have nowhere to be: a closed
     * account is a record, and a record holding four hundred euros is an account that is not closed.
     * Where the money should go instead is a decision with consequences for a week, a streak and a
     * loyalty clock, so it is the customer's to take and not this method's.
     *
     * <p>Everything else the account has stays exactly where it is: its deposits, its withdrawals,
     * its goals, its allocations and the agreement itself, which goes on naming the product and the
     * version every one of those rows was decided under.
     *
     * @throws IllegalStateException when the account has no agreement on record at all, which is a
     *                               database that has not been through the start-up migration
     *                               rather than anything a customer did — there is no sentence to
     *                               refuse somebody with, because nobody did anything wrong
     */
    @Transactional
    TheAgreementAnAccountIsOn closeTheAccount(long savingsAccountId, BigDecimal whatItStillHolds) {
        log.debug("a savings account is being closed savingsAccountId={} moneyBalance={}",
                savingsAccountId, whatItStillHolds);
        AccountAgreement agreement = agreements.findBySavingsAccountId(savingsAccountId)
                .orElseThrow(() -> new IllegalStateException(
                        "savings account " + savingsAccountId + " has no agreement on record, so "
                                + "there is nothing to close"));
        if (agreement.isClosed()) {
            throw refusing(ProductRefused.Kind.AN_ACCOUNT_ALREADY_CLOSED,
                    "Savings account " + savingsAccountId + " was closed on "
                            + agreement.closedOn() + ".",
                    "savingsAccountId=" + savingsAccountId + " closedOn=" + agreement.closedOn());
        }
        if (whatItStillHolds.signum() != 0) {
            throw refusing(ProductRefused.Kind.AN_ACCOUNT_THAT_STILL_HOLDS_MONEY,
                    "Savings account " + savingsAccountId + " still holds EUR "
                            // Written to the cent rather than printed straight, because a figure
                            // that has been through SQLite comes back as 40 rather than 40.00 and
                            // reads as a number instead of as money. AmountOfMoney owns how many
                            // places money has, and a second opinion about it here would be a
                            // second opinion about it everywhere.
                            + AmountOfMoney.asMoney(whatItStillHolds) + ". Take the money out or "
                            + "move it somewhere else, and then the account can be closed.",
                    "savingsAccountId=" + savingsAccountId + " moneyBalance="
                            + AmountOfMoney.asMoney(whatItStillHolds));
        }
        agreement.closeOn(today());
        agreements.save(agreement);
        // One line per account closed, at INFO, because this is a thing a customer did that cannot
        // be undone, and the first question about a missing account on an overview is whether it
        // was closed and when.
        log.info("a savings account was closed savingsAccountId={} product={} version={} "
                        + "openedOn={} closedOn={}",
                savingsAccountId, agreement.productCode(), agreement.version(),
                agreement.openedOn(), agreement.closedOn());
        return asRead(agreement);
    }

    /**
     * Writes the agreement at whatever version that product is selling today, on today's date.
     *
     * <p>The one place the version rule is applied, because both doors into this class apply the
     * same one: an account begins under the terms that were current when it began. The product is
     * the only thing that differs between them.
     */
    private void putOnWhatIsBeingSoldToday(long savingsAccountId, String productCode,
                                           String because) {
        LocalDate today = today();
        ProductTerms beingSoldToday = TheTermsOnOfferToday.outOf(
                terms.findByProductCodeOrderByVersionAsc(productCode), today);
        // Interest counts from the same day, because there is nothing behind this account for it
        // to be backdated over: it was opened this morning and its first period starts this
        // morning. The migration below is where the two dates part company.
        putOnRecord(savingsAccountId, productCode, beingSoldToday.version(), today, today, because);
    }

    /**
     * Every refusal this class makes says why in the log as well as to whoever asked, because only
     * one of the two is kept: the reason reaches the person at the keyboard and nowhere else.
     *
     * <p>The values that decided it travel as a fragment the caller assembles, rather than as a
     * fixed set of columns, because the four refusals here are about four different things — a box
     * nobody filled in, a word the catalogue does not know, a product's door, and a balance — and a
     * line with three empty fields on it is a line a reviewer has to read twice.
     */
    private ProductRefused refusing(ProductRefused.Kind kind, String reason, String what) {
        log.warn("a savings account was refused {} kind={} reason={}", what, kind, reason);
        return new ProductRefused(kind, reason);
    }

    /**
     * What that account is living under, for a deposit about to be stamped with it and, since the
     * door was closed on a deposit into an account whose agreement has ended, refused by it.
     *
     * <p>Empty for an account nothing has recorded yet, which is what
     * {@link TheTermsADepositLandsUnder} says it means: a row written before this module existed
     * and not yet reached by the start-up migration. The caller records no version rather than a
     * guessed one, and treats the absence as an account that is open, because an account nobody has
     * written an agreement for has not been closed by anybody either.
     *
     * <p>Two fields off one row, in one read. The version and the day it ended are decided by the
     * same row and are handed over together so that they cannot be read a moment apart — which is
     * the whole of what {@link WhatAnAccountIsLivingUnder} exists to make impossible.
     */
    @Transactional(readOnly = true)
    @Override
    public Optional<WhatAnAccountIsLivingUnder> whatAnAccountIsLivingUnder(long savingsAccountId) {
        return agreements.findBySavingsAccountId(savingsAccountId)
                .map(agreement -> new WhatAnAccountIsLivingUnder(
                        agreement.version(), agreement.closedOn()));
    }

    /**
     * What a euro saved into that account is worth in points, as a plain multiple of one, out of the
     * version the account is living under.
     *
     * <p>The multiple that changes nothing for an account no agreement has been written for, which
     * is what {@link WhatAEuroSavedIntoAnAccountIsWorth} says it means and is the one place in this
     * class where an absent agreement is answered with a figure rather than with an empty. A deposit
     * is landing and has to be priced now; there is an honest price, and it is what every deposit in
     * this application was paid before the catalogue existed.
     */
    @Transactional(readOnly = true)
    @Override
    public BigDecimal theMultipleAEuroEarnsAt(long savingsAccountId) {
        BigDecimal multiple = theVersionOfRecordFor(savingsAccountId)
                .map(ProductTerms::pointsMultiplier)
                .orElse(THE_MULTIPLE_THAT_CHANGES_NOTHING);
        log.debug("what a euro saved into a savings account is worth savingsAccountId={} "
                + "pointsMultiplier={}", savingsAccountId, multiple);
        return multiple;
    }

    /**
     * What an anniversary pays per whole euro sitting in that account, as a fraction of a point, out
     * of the version the account is living under.
     *
     * <p>A fraction and never the percentage the catalogue prints. The row holds 1 200 basis points,
     * the card says 12.00%, and what {@code LoyaltyRate} multiplies whole euros by is 0.1200 — three
     * spellings of one rate, two of which would quietly pay a hundred or ten thousand times too much.
     * {@link ProductTerms#anniversaryRatePerWholeEuro} is where that conversion is made and argued.
     *
     * <p>The tenth every anniversary used to pay for an account no agreement has been written for,
     * which is the same window, the same argument and the same honest default as the multiple above.
     */
    @Transactional(readOnly = true)
    @Override
    public BigDecimal perWholeEuroIn(long savingsAccountId) {
        BigDecimal rate = theVersionOfRecordFor(savingsAccountId)
                .map(ProductTerms::anniversaryRatePerWholeEuro)
                .orElse(LoyaltyRate.THE_TENTH_EVERY_ANNIVERSARY_USED_TO_PAY);
        log.debug("what an anniversary pays in a savings account savingsAccountId={} "
                + "ratePerWholeEuro={}", savingsAccountId, rate);
        return rate;
    }

    /**
     * Everything an account's agreement says, for whoever is drawing it: the product, the version,
     * the day it began and the condition that product attaches.
     *
     * <p>Nothing at all for an account no agreement has been written for, rather than an invented
     * one. The reading a screen gets then simply has no agreement panel in it, which is honest
     * about a database that has not been through the migration and is the same empty every other
     * absent reading in this application answers with.
     *
     * <p>Three rows are read for one answer — the agreement, the product it names and the version
     * it names — and that is the price of storing the pair rather than a copy of the numbers. It is
     * the right price: the alternative writes a dozen figures onto every account, and the day one
     * of them is wrong there is no row to go back to.
     */
    @Transactional(readOnly = true)
    Optional<TheAgreementAnAccountIsOn> theAgreementOf(long savingsAccountId) {
        return agreements.findBySavingsAccountId(savingsAccountId).map(this::asRead);
    }

    /**
     * Puts an account that predates the catalogue on free savings at the bank's opening terms,
     * dated at the day it began.
     *
     * <p>For the start-up migration and for nothing else, which is why the day is an argument: the
     * migration works it out from the ledger — an account's first deposit, or the day of the
     * migration when it never had one — and this class has no business reading a deposit.
     *
     * <p>Version 1 is named rather than derived. What was on offer on the day of an old account's
     * first deposit would usually be version 1 too, but "usually" is not a rule anybody could check,
     * and an account that was open before this bank published anything was open under the terms the
     * bank opened with. The migration is one sentence and this is it.
     *
     * <p>Interest counts from the day of the migration rather than from the day the agreement is
     * dated at, and the two are deliberately different here. The agreement began when the money
     * first arrived, which may be a year ago; this bank has been paying interest since this
     * morning, and paying a year of it to every account in an existing file would be a lab waking
     * up with money nobody can explain. The spec says so in as many words and
     * {@code SavingsAccountsGetAProductOnStartUp} hands in the day.
     *
     * @return the version the account is now on, which is what its deposits are stamped with
     */
    @Transactional
    int putOnTheOpeningTerms(long savingsAccountId, LocalDate openedOn, LocalDate migratedOn) {
        putOnRecord(savingsAccountId, FREE_SAVINGS, THE_OPENING_TERMS, openedOn, migratedOn,
                "a savings account that predates the catalogue was migrated");
        return whatAnAccountIsLivingUnder(savingsAccountId)
                .map(WhatAnAccountIsLivingUnder::version)
                .orElse(THE_OPENING_TERMS);
    }

    /**
     * Writes the agreement, unless the account already has one.
     *
     * <p>Checked in Java as well as guarded by the index {@link ProductsOnStartUp} creates, for the
     * reason the catalogue's seed gives about the same pair of things: the check is what lets this
     * be called twice without an exception, and the index is what makes one agreement per account
     * true rather than merely intended.
     */
    private void putOnRecord(long savingsAccountId, String productCode, int version,
                             LocalDate openedOn, LocalDate interestCountsFrom, String because) {
        Optional<AccountAgreement> alreadyOn = agreements.findBySavingsAccountId(savingsAccountId);
        if (alreadyOn.isPresent()) {
            AccountAgreement agreement = alreadyOn.get();
            // Not a refusal and not an error: it is the ordinary answer on every start after the
            // first, and on any path that could be walked twice. Said at DEBUG with the row that
            // was found, so that "why is this account still on version 1" is answerable from the
            // log rather than from the table.
            log.debug("a savings account is already on a product savingsAccountId={} product={} "
                            + "version={} openedOn={} leftAloneBecause={}",
                    savingsAccountId, agreement.productCode(), agreement.version(),
                    agreement.openedOn(), because);
            return;
        }
        AccountAgreement written = agreements.save(AccountAgreement.putting(
                savingsAccountId, productCode, version, openedOn, interestCountsFrom));
        // One line per agreement written, at INFO, because this row is what every rate, every
        // refusal and every interest posting about this account will be decided by for as long as
        // the account exists.
        log.info("a savings account was put on a product savingsAccountId={} product={} version={} "
                        + "openedOn={} interestCountsFrom={} because={}",
                written.savingsAccountId(), written.productCode(), written.version(),
                written.openedOn(), written.interestCountsFrom(), because);
    }

    /**
     * The agreement, the product it names and the version it names, read into one answer.
     *
     * <p>The version is looked up by the pair the row carries rather than by asking what is on
     * offer, which is the whole point of storing the pair: an account on version 1 reads version 1
     * for ever, however many versions have been published since.
     */
    private TheAgreementAnAccountIsOn asRead(AccountAgreement agreement) {
        SavingsProduct product = products.findByCode(agreement.productCode())
                .orElseThrow(() -> new IllegalStateException(
                        "savings account " + agreement.savingsAccountId() + " is on "
                                + agreement.productCode() + ", which this bank does not sell"));
        ASetOfTerms itsTerms = theTermsOfRecord(agreement);
        return new TheAgreementAnAccountIsOn(
                agreement.savingsAccountId(),
                product.code(),
                product.name(),
                product.kind(),
                itsTerms.version(),
                agreement.openedOn(),
                itsTerms.noticeDays(),
                itsTerms.minimumBalance(),
                whenTheTermIsUp(agreement.theDayTheTermRunsFrom(), itsTerms.termMonths()),
                agreement.closedOn());
    }

    /**
     * The exact version this account was opened under, by the pair that addresses it, as the rest of
     * the application reads it.
     */
    private ASetOfTerms theTermsOfRecord(AccountAgreement agreement) {
        return theVersionOfRecord(agreement).asPublished();
    }

    /**
     * The row holding the version that account is living under, and nothing at all for an account no
     * agreement has been written for.
     *
     * <p>The one lookup the two pricing answers share, so that what a deposit is multiplied by and
     * what its anniversaries pay come out of the same version — not merely out of the same product.
     * Both callers turn the empty into a figure of their own, which is where an absent agreement is
     * decided about, and neither of them can reach {@link TheTermsOnOfferToday} to be tempted by the
     * version on the shelf.
     *
     * <p>Two rows read for one number, which is the price of storing the pair rather than a copy of
     * the figures. It is the right price for the reason {@link #theAgreementOf} gives about the same
     * two reads: the alternative writes a dozen figures onto every account, and the day one of them
     * is wrong there is no row to go back to.
     */
    private Optional<ProductTerms> theVersionOfRecordFor(long savingsAccountId) {
        return agreements.findBySavingsAccountId(savingsAccountId).map(this::theVersionOfRecord);
    }

    /**
     * The exact row that agreement names, or a broken record said out loud.
     *
     * <p>A broken record rather than a customer's mistake when there is no such version, so it
     * throws rather than refusing in words: nobody did anything wrong, and there is no sentence to
     * tell them.
     */
    private ProductTerms theVersionOfRecord(AccountAgreement agreement) {
        List<ProductTerms> published = terms.findByProductCodeOrderByVersionAsc(
                agreement.productCode());
        return published.stream()
                .filter(version -> version.version() == agreement.version())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "savings account " + agreement.savingsAccountId() + " is on "
                                + agreement.productCode() + " version " + agreement.version()
                                + ", which has never been published"));
    }

    /**
     * The day a term is up, counted from the day that term started running, and nothing at all for
     * a product with no term.
     *
     * <p>Quoted from {@link TheTermAnAccountIsLockedInto} rather than worked out here, because that
     * class refuses withdrawals against exactly this date and two copies of a calendar rule
     * disagree on precisely the dates that are hard — a twelve-month term started on the 29th of
     * February is the one worth getting right, and it is worth getting right once.
     *
     * <p>The day handed in is {@link AccountAgreement#theDayTheTermRunsFrom()} and not the day the
     * account was opened, which matters only once a maturity has rolled the account into another
     * term: after that, the panel would otherwise go on showing a maturity date that went by a year
     * ago while the withdrawal gate refused withdrawals against the real one.
     */
    private static LocalDate whenTheTermIsUp(LocalDate theDayTheTermRunsFrom, int termMonths) {
        return TheTermAnAccountIsLockedInto.whenTheTermIsUp(theDayTheTermRunsFrom, termMonths);
    }

    /**
     * Moves one account onto free savings at the version being sold today, and answers with the
     * agreement as it now reads.
     *
     * <p><strong>What breaking a fixed term does to the account, and the only thing that moves an
     * account from one product to another.</strong> Everything else in this class writes an
     * agreement for an account that has none; this rewrites one that does, which is why it is the
     * only method here with a mutator behind it and why {@link AccountAgreement#moveTo} argues what
     * it may and may not touch.
     *
     * <p><strong>The version on offer today and never the one the account came from.</strong> The
     * customer is being put onto an agreement they are entering now, so the terms are the terms
     * being sold now — which is the same rule every newly opened account follows, and the same rule
     * a roll-over at maturity will follow when it arrives. Carrying the old version number across
     * would put the account on a version of free savings that was never on offer for it.
     *
     * <p><strong>The day it began does not move, and neither does the day its interest counts
     * from.</strong> Those two answer how long this agreement has been running and how far back this
     * bank is willing to pay for it, and neither of them changed: the same account has been open
     * since the same morning, and the months it has already been paid for are already recorded
     * against their ordinals. Re-dating the agreement would restart the ordinals and offer to pay
     * for months that have been paid.
     *
     * <p>Package-private, and the only caller is {@link FixedTermsService}. An account's product is
     * not something anything else in this application may change — the spec's line is that nothing
     * adopts anything on anybody's behalf — so the door is exactly as wide as the one thing that
     * uses it.
     *
     * @throws IllegalStateException when the account has no agreement on record at all, which is a
     *                               database that has not been through the start-up migration
     *                               rather than anything a customer did
     */
    @Transactional
    TheAgreementAnAccountIsOn moveToFreeSavings(long savingsAccountId, String because) {
        return moveToFreeSavingsAsAt(savingsAccountId, today(), because);
    }

    /**
     * Moves one account onto free savings at the version that was on offer on a named day, and
     * answers with the agreement as it now reads.
     *
     * <p><strong>The day is an argument because a maturity has its own day and it is not always
     * today.</strong> {@link MaturitiesService} settles every maturity an account has passed, and a
     * trainer who winds the clock a year forward makes it settle one that fell eleven months ago:
     * the terms that account falls onto are the ones the bank was selling on <em>that</em> morning,
     * because that is the morning the money became free. Pinning tonight's version instead would
     * silently apply a rate change published in between to an agreement that began before it, which
     * is the one thing this whole module exists to make impossible.
     *
     * <p>The method above it hands in today and is what breaking a term calls, because breaking
     * happens at the moment somebody presses the button. They are one method with two days rather
     * than two methods, so that there is still exactly one place in this application that moves an
     * account from one product to another.
     */
    @Transactional
    TheAgreementAnAccountIsOn moveToFreeSavingsAsAt(long savingsAccountId, LocalDate asAt,
                                                    String because) {
        AccountAgreement agreement = agreements.findBySavingsAccountId(savingsAccountId)
                .orElseThrow(() -> new IllegalStateException(
                        "savings account " + savingsAccountId + " has no agreement on record, so "
                                + "there is nothing to move"));
        String wasOn = agreement.productCode();
        int wasOnVersion = agreement.version();
        ProductTerms beingSoldToday = TheTermsOnOfferToday.outOf(
                terms.findByProductCodeOrderByVersionAsc(FREE_SAVINGS), asAt);
        agreement.moveTo(FREE_SAVINGS, beingSoldToday.version());
        agreements.save(agreement);
        // One line per move, at INFO, because this row is what every rate, every refusal and every
        // interest posting about this account will be decided by from here on — and because "why
        // is this account suddenly paying 0.50%" is answerable from this line alone.
        log.info("a savings account was moved onto another product savingsAccountId={} wasOn={} "
                        + "wasOnVersion={} nowOn={} nowOnVersion={} openedOn={} asAt={} because={}",
                savingsAccountId, wasOn, wasOnVersion, agreement.productCode(),
                agreement.version(), agreement.openedOn(), asAt, because);
        return asRead(agreement);
    }

    /**
     * Starts this account's next term on the day the last one matured, at the version its product
     * was selling that day, and answers with the agreement as it now reads.
     *
     * <p><strong>The account stays on its product and gains a new maturity a term further
     * out.</strong> That is the whole of what a roll-over is, and it is why this is not
     * {@link #moveToFreeSavingsAsAt} with different arguments: nothing moves between products, and
     * the field that moves here — the day the term runs from — is the one that method deliberately
     * refuses to touch. {@link AccountAgreement#rollTheTermOverInto} argues both halves.
     *
     * <p><strong>The version on offer on the maturity day, and never the one the account came
     * from.</strong> The spec's sentence is that a rolling term becomes an honest new agreement at
     * the rates on offer that day rather than the old one extended, so the account is pinned afresh
     * — which is the same rule a newly opened account follows and the same rule breaking a term
     * follows. Carrying the old version number forward would let an account renew, for ever, terms
     * the bank stopped selling years ago.
     *
     * <p><strong>The product's own versions, not free savings'.</strong> A twelve-month fixed term
     * rolls into another twelve-month fixed term; which version of it is the catalogue's answer on
     * that morning. If the product has since published a version that locks nothing away, the
     * account rolls onto that and simply has no term afterwards — which is honest, because it is the
     * agreement the bank is actually selling, and the sweep then finds nothing more to settle.
     *
     * <p>Package-private, and the only caller is {@link MaturitiesService}. A roll-over is something
     * a maturity does on a morning, not something anybody presses.
     *
     * @throws IllegalStateException when the account has no agreement on record at all, which is a
     *                               database that has not been through the start-up migration
     *                               rather than anything a customer did
     */
    @Transactional
    TheAgreementAnAccountIsOn rollTheTermOver(long savingsAccountId, LocalDate theDayItMatured,
                                              String because) {
        AccountAgreement agreement = agreements.findBySavingsAccountId(savingsAccountId)
                .orElseThrow(() -> new IllegalStateException(
                        "savings account " + savingsAccountId + " has no agreement on record, so "
                                + "there is no term to roll over"));
        int wasOnVersion = agreement.version();
        LocalDate wasRunningFrom = agreement.theDayTheTermRunsFrom();
        ProductTerms beingSoldThatDay = TheTermsOnOfferToday.outOf(
                terms.findByProductCodeOrderByVersionAsc(agreement.productCode()), theDayItMatured);
        agreement.rollTheTermOverInto(beingSoldThatDay.version(), theDayItMatured);
        agreements.save(agreement);
        // One line per roll-over, at INFO, because this row decides every rate, every refusal and
        // every interest posting about the account for another whole term — and because "why is this
        // account locked again" has to be answerable from one line naming the day it rolled and the
        // version it rolled onto.
        log.info("a savings account's term was rolled over savingsAccountId={} product={} "
                        + "wasOnVersion={} nowOnVersion={} wasRunningFrom={} nowRunningFrom={} "
                        + "termMonths={} nowMaturesOn={} because={}",
                savingsAccountId, agreement.productCode(), wasOnVersion, agreement.version(),
                wasRunningFrom, agreement.theDayTheTermRunsFrom(), beingSoldThatDay.termMonths(),
                whenTheTermIsUp(agreement.theDayTheTermRunsFrom(), beingSoldThatDay.termMonths()),
                because);
        return asRead(agreement);
    }

    /**
     * What this account's product is offering today, what the account is on, and what differs
     * between the two — and nothing at all for an account no agreement has been written for.
     *
     * <p><strong>A reading and never an act.</strong> Nothing here writes, nothing here decides
     * anything for anybody, and an account whose product has repriced twenty times goes on being an
     * account on the version it was opened under until its holder presses the other door. The whole
     * feature is the gap between knowing and moving, and this is the knowing half of it.
     *
     * <p><strong>Both versions are read the way their two questions are already answered
     * elsewhere</strong>: the account's by the pair its own row carries, which is the lookup every
     * price in this class shares, and the product's by {@link TheTermsOnOfferToday}, which is the
     * same answer the catalogue card is drawn from. This is the one method in this class allowed to
     * ask both, because comparing them is what it is for — {@link #theVersionOfRecordFor} says at
     * length why nothing else here may reach the version on the shelf.
     *
     * <p><strong>A closed account still answers.</strong> Its agreement is a record of what its
     * money lived under and the catalogue has gone on moving since; saying nothing would leave a
     * page unable to explain why a closed account's version is not the current one. Taking the terms
     * is what is refused for a closed account, next door, where the refusal can say when it closed.
     *
     * <p>The empty is for an account no agreement has been written for, which is a database that has
     * not been through the start-up migration — the same absence, for the same reason, that
     * {@link #theAgreementOf} answers with, and a page draws no panel from it rather than a panel
     * saying nothing has changed.
     */
    @Transactional(readOnly = true)
    Optional<TheNewerTermsOnOffer> theNewerTermsFor(long savingsAccountId) {
        return agreements.findBySavingsAccountId(savingsAccountId).map(agreement -> {
            ASetOfTerms itIsOn = theVersionOfRecord(agreement).asPublished();
            ASetOfTerms onOfferToday = whatIsBeingSoldToday(agreement.productCode());
            TheNewerTermsOnOffer newer = new TheNewerTermsOnOffer(
                    savingsAccountId,
                    agreement.productCode(),
                    theProductOf(agreement).name(),
                    itIsOn.version(),
                    onOfferToday.version(),
                    itIsOn.version() == onOfferToday.version()
                            ? List.of()
                            : WhatIsDifferentBetweenTwoSetsOfTerms.between(itIsOn, onOfferToday));
            log.debug("what newer terms would change for a savings account savingsAccountId={} "
                            + "product={} versionYouAreOn={} versionOnOfferToday={} "
                            + "newerTermsExist={} differences={}",
                    savingsAccountId, newer.productCode(), newer.theVersionYouAreOn(),
                    newer.theVersionOnOfferToday(), newer.newerTermsExist(),
                    newer.whatWouldChange().size());
            return newer;
        });
    }

    /**
     * Puts one account onto the version its product is selling today, because its holder asked, and
     * answers with the agreement as it now reads.
     *
     * <p><strong>It moves no money, no points and no allocations, and there is nothing here that
     * could.</strong> Taking newer terms is an agreement rather than a transaction: one column of
     * one row changes, and the ledger, the points balance, the goals and the week are not so much as
     * read. That is the strongest form the spec's promise can take at this layer — not a rule that
     * nothing moves, but a method with nothing in it that moves anything.
     *
     * <p><strong>The version on offer today, never a version the customer names.</strong> A door
     * that took a version number would let somebody put their account on a version that was
     * published for next month, or on one that stopped being sold in March, and would make every
     * screen that offered it responsible for deciding which versions are takeable. There is one
     * version on offer, it is the one the difference was worded against, and it is the one this
     * writes.
     *
     * <p><strong>Refused while a fixed term is running, which is what being locked in means.</strong>
     * The date in the sentence is {@link TheTermAnAccountIsLockedInto}'s, so a customer is told the
     * same morning here as they are told by a withdrawal the term refuses. Checked before the
     * version is compared, on purpose: an account inside its term is refused whether or not anything
     * newer exists, because the answer is about the lock rather than about the catalogue.
     *
     * <p><strong>Refused, too, on a term whose day has come — and that is a decision taken
     * deliberately rather than a lock stretched a day too far.</strong> A matured term reads
     * unlocked, so this door stood open to one, and what came through was not a repricing. A
     * waiting twelve-month term offered a twenty-four-month version took it, and the money that had
     * been free since the maturity morning was locked away again for another three hundred and
     * thirty-one days — because the day a term is up is derived from the version's {@code
     * termMonths} and taking a version moves it. The same press with a version of the same length
     * but a different ending did the mirror image: the date did not move, {@link MaturitiesService}
     * had already settled that date, and so an account whose own panel said it would roll over on a
     * day in the past sat through three more wound-forward years and never rolled. Both are one
     * mistake — a maturity that has already arrived being settled by terms it did not arrive under
     * — and the rule that removes it is that the version an account is on stops moving on the day
     * its term is up.
     *
     * <p><strong>The question underneath is what somebody is doing when they press this on a
     * waiting term, and the answer is that they are starting a new term rather than taking a better
     * rate on money that is now free.</strong> A matured term left waiting has not earned its own
     * headline rate since the morning it matured: {@link AWaitingTermEarnsTheFreeSavingsRate} moved
     * it onto the ordinary rate that day, and it is priced from free savings' versions from then on.
     * So the figures a newer version of a term product would actually bring such an account are a
     * term length and an ending — which is to say a lock and a day. Starting another term is
     * precisely what a roll-over is for, and a roll-over is something the terms said at the
     * beginning, not something pressed at the end.
     *
     * <p><strong>The alternative rejected was to allow it and re-date the term</strong>: read the
     * press as a roll-over the holder asked for, pin {@code termStartedOn} to the day they pressed,
     * and let the settlement of the new maturity follow. That keeps the record honest and it is one
     * call to {@link AccountAgreement#rollTheTermOverInto}, and it is still wrong, because it makes
     * one button mean two different things depending on a date the person pressing it is not being
     * shown: on an account inside its term, nothing but a repricing; on an account whose term is up,
     * another year of their money locked away. A lock is the one thing here a customer has to agree
     * to in words, and the promise this whole feature rests on is that nothing adopts anything on
     * anybody's behalf. The third possibility — allow it and hold the maturity date still — cannot
     * be built at all: the date is derived from the version, so the only way for it not to move is
     * for the version not to move.
     *
     * <p><strong>Which leaves an account on a term taking no newer terms at all, ever, and that is
     * the invariant worth having.</strong> The one version move a term ever makes is the one its own
     * maturity makes, so a maturity date moves only when a roll-over moves it — and a roll-over
     * settles the maturity it is moving away from in the same transaction. A
     * {@code MaturitySettled} row can therefore never come to point at a day the account's calendar
     * no longer produces, which is the invariant ticket 20 was written to keep. A holder who wants
     * the terms being sold today is told in the refusal how to have them: the money is free, and an
     * account opened on the product now opens on them.
     *
     * <p><strong>Being told the terms were bettered and being refused here is not a
     * contradiction.</strong> Notifications judges a rate rise against the version an account holds
     * and says so; that is about the product's offer rather than about this door, and it already
     * reaches accounts halfway through a term that cannot press anything either. The invitation is
     * to read. What it leads to on a term is a sentence saying why not, and what to do instead.
     *
     * <p><strong>Refused, too, for an account already on the version on offer</strong>, rather than
     * writing the version it is already on and reporting a move that did not happen. And refused for
     * a closed account, which is a record of what money lived under and not somewhere an agreement
     * can be entered into.
     *
     * <p><strong>A product closed to new accounts is not refused.</strong> Closing a product stops
     * the <em>next</em> account being opened on it; this account is already on it, its holder is
     * already living under its terms, and the bank has gone on publishing versions of them — which
     * {@code ProductsService#publishANewVersionOf} argues is exactly so that the accounts still on it
     * have something to compare themselves against. Refusing here would publish terms nobody on the
     * product could ever take.
     *
     * @throws IllegalStateException when the account has no agreement on record at all, which is a
     *                               database that has not been through the start-up migration rather
     *                               than anything a customer did — there is no sentence to refuse
     *                               somebody with, because nobody did anything wrong
     */
    @Transactional
    TheAgreementAnAccountIsOn takeTheNewerTerms(long savingsAccountId) {
        AccountAgreement agreement = agreements.findBySavingsAccountId(savingsAccountId)
                .orElseThrow(() -> new IllegalStateException(
                        "savings account " + savingsAccountId + " has no agreement on record, so "
                                + "there are no terms for it to take"));
        log.debug("taking the newer terms of a savings product was asked for savingsAccountId={} "
                        + "product={} version={} closedOn={}",
                savingsAccountId, agreement.productCode(), agreement.version(),
                agreement.closedOn());
        if (agreement.isClosed()) {
            throw refusing(ProductRefused.Kind.AN_ACCOUNT_ALREADY_CLOSED,
                    "Savings account " + savingsAccountId + " was closed on "
                            + agreement.closedOn() + ", so its terms do not change any more. It "
                            + "goes on naming the agreement its money lived under.",
                    "savingsAccountId=" + savingsAccountId + " closedOn=" + agreement.closedOn());
        }
        theTerm.theTermOn(savingsAccountId).ifPresent(term -> {
            if (!TheTermAnAccountIsLockedInto.hasMatured(term.maturesOn(), today())) {
                throw refusing(ProductRefused.Kind.AN_ACCOUNT_LOCKED_INTO_A_TERM,
                        "That savings account is a " + term.termMonths() + "-month fixed term "
                                + "and its terms are locked until it matures on "
                                + term.maturesOn() + ". That is what being locked in means: the "
                                + "agreement you opened runs to the end of the term, and what "
                                + "happens on the day it is up is the ending these terms already "
                                + "name.",
                        "savingsAccountId=" + savingsAccountId + " product=" + term.productCode()
                                + " maturesOn=" + term.maturesOn());
            }
            throw refusing(ProductRefused.Kind.A_TERM_WHOSE_DAY_HAS_COME,
                    "That savings account is a " + term.termMonths() + "-month fixed term and its "
                            + "day came on " + term.maturesOn() + ". A maturity is settled by the "
                            + "terms it arrived under, so the agreement stops moving there: newer "
                            + "terms of a product with a term carry a term of their own, and "
                            + "taking them now would move the day this one ended and start another "
                            + "one you have not agreed to. "
                            + whatToDoInsteadOfTakingNewerTerms(term),
                    "savingsAccountId=" + savingsAccountId + " product=" + term.productCode()
                            + " version=" + term.itsTerms().version() + " maturedOn="
                            + term.maturesOn() + " maturityAction=" + term.maturityAction());
        });
        int wasOnVersion = agreement.version();
        ASetOfTerms wasOn = theVersionOfRecord(agreement).asPublished();
        ASetOfTerms onOfferToday = whatIsBeingSoldToday(agreement.productCode());
        if (wasOnVersion >= onOfferToday.version()) {
            throw refusing(ProductRefused.Kind.AN_ACCOUNT_ALREADY_ON_THE_TERMS_ON_OFFER,
                    "Savings account " + savingsAccountId + " is already on "
                            + theProductOf(agreement).name() + " version " + wasOnVersion
                            + ", which is the version on offer today. There is nothing newer to "
                            + "take.",
                    "savingsAccountId=" + savingsAccountId + " product=" + agreement.productCode()
                            + " version=" + wasOnVersion + " onOfferToday="
                            + onOfferToday.version());
        }
        agreement.takeTheNewerTermsOfTheSameProduct(onOfferToday.version());
        agreements.save(agreement);
        // One line per version taken, at INFO, carrying both version numbers because that is the
        // whole of what happened — and because "why is this account suddenly paying 0.50%" is
        // answerable from this line alone, together with the fact that its holder asked for it.
        log.info("a savings account took the newer terms of its product savingsAccountId={} "
                        + "product={} wasOnVersion={} nowOnVersion={} effectiveFrom={} "
                        + "openedOn={} differences={}",
                savingsAccountId, agreement.productCode(), wasOnVersion, agreement.version(),
                onOfferToday.effectiveFrom(), agreement.openedOn(),
                WhatIsDifferentBetweenTwoSetsOfTerms.between(wasOn, onOfferToday).size());
        return asRead(agreement);
    }

    /**
     * The second half of the refusal a matured term gets: what its holder does instead, which is
     * different for each of the three endings they agreed to.
     *
     * <p><strong>Written from the ending rather than said once in general terms</strong>, because a
     * refusal whose useful half is "do something else" has to say which something. Money left
     * waiting is free and the answer is to move it; a term that rolls over has already pinned the
     * version it rolls onto, and the honest answer is that there is nothing to press; a term moving
     * to instant access will be on free savings by the morning, where this same door works in the
     * ordinary way.
     *
     * <p>The product is named the way a card names it, so that "open an account on it" is an
     * instruction somebody can follow on the screen they are standing on. The sweep is described as
     * something that happens rather than as a job with a name: what a customer needs to know is that
     * it is not waiting on them.
     */
    private String whatToDoInsteadOfTakingNewerTerms(
            TheTermAnAccountIsLockedInto.ATermInForce term) {
        String product = theProductOf(term.agreement()).name();
        return switch (term.maturityAction()) {
            case HOLD -> "The money has been free to move since that morning: take it out, move it "
                    + "into another savings account, or open a savings account on " + product
                    + " now to start a fresh term on the terms being sold today.";
            case ROLL_OVER -> "These terms say it goes straight into another term at the rates on "
                    + "offer on the day it matured, which happens on its own and pins that version "
                    + "for you. If you would rather have the terms being sold today, take the money "
                    + "out while it is free and open a savings account on " + product + " again.";
            case MOVE_TO_INSTANT -> "These terms say the account moves to instant access from that "
                    + "morning, which happens on its own; once it has, it is on free savings and "
                    + "free savings' newer terms are yours to take here in the ordinary way.";
        };
    }

    /**
     * The version that product is selling today, as the rest of this application reads it.
     *
     * <p>{@link TheTermsOnOfferToday} over that product's own versions, which is the same answer the
     * catalogue card is drawn from — so the version a customer is offered here is the version they
     * were shown there. Written once and shared by the reading and the taking, because the two
     * would be a genuinely dangerous pair to answer differently: a difference worded against one
     * version and a row written with another.
     */
    private ASetOfTerms whatIsBeingSoldToday(String productCode) {
        return TheTermsOnOfferToday
                .outOf(terms.findByProductCodeOrderByVersionAsc(productCode), today())
                .asPublished();
    }

    /**
     * The product an agreement names, or a broken record said out loud — the same reading
     * {@link #asRead} makes of the same impossible state, lifted out so that a refusal can name the
     * product the way a page does.
     */
    private SavingsProduct theProductOf(AccountAgreement agreement) {
        return products.findByCode(agreement.productCode())
                .orElseThrow(() -> new IllegalStateException(
                        "savings account " + agreement.savingsAccountId() + " is on "
                                + agreement.productCode() + ", which this bank does not sell"));
    }

    /**
     * The day this application thinks it is, in the zone it counts its days in — borrowed from
     * {@link SavingsWeek} for the reason {@link ProductsService} gives about the same line.
     */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
    }
}
