package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.scheme.SchemeService;
import io.dataroots.savingstreak.streaks.SavingsWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only face this module has: what the bank sells, what each product is offering today, and
 * every version each of them has ever published.
 *
 * <p><strong>Everything about a product goes through here.</strong> The two entities and their
 * repositories are package-private, so a rate is not a column anybody outside can read and a
 * version is not a row anybody outside can write. What leaves is {@link AProductOnOffer} and
 * {@link ASetOfTerms}, which are values in the units the rest of this application speaks — euros
 * and percentages, never basis points and never cents.
 *
 * <p><strong>Four things can be written, and none of them changes anything already
 * written.</strong> A product publishes the <em>next</em> version of its terms; a product is closed
 * to new accounts; a closed one is reopened; and one savings account's agreement is closed. There
 * is no fifth, and the two that are missing are missing on purpose: nothing here edits a published
 * version, because an agreement somebody is living under cannot be rewritten and
 * {@link ProductTerms} has no mutator to rewrite one with; and nothing here deletes anything,
 * because an account and every interest posting will point at a version. Those are not rules this
 * class enforces — they are doors that do not exist, which is a stronger statement and a shorter
 * one.
 *
 * <p><strong>The fourth is the odd one, and it is worth saying why it lives here.</strong> Closing
 * a savings account is not a decision about the catalogue; it is the end of one account's life on a
 * product, and what an account is living under is the one record this module keeps that is not
 * about the catalogue. Neither of the other two candidates could have kept it. Accounts owns the
 * account and cannot be told that a product exists, nor ask what an account still holds — a column
 * on {@code savings_account} is also a column {@code AccountsOnStartUp} silently drops on the start
 * that rebuilds that table. And a rule spread across a controller would be a decision about money
 * taken in the web layer, which no rule in this application is.
 *
 * <p><strong>Which is why this class knows what an account holds, and knows nothing else about
 * money.</strong> The balance is read at the moment the question is put and is handed straight to
 * the rule that judges it; nothing is kept, nothing is summed, and no euro is decided here. It is
 * the same bargain Shared Pots makes for the same reason: a rule about somebody else's figure asks
 * for the figure when it asks the question.
 *
 * <p><strong>Publishing writes a row and touches none of the others.</strong> That is the whole of
 * how a rate change happens here: version <em>n+1</em> is inserted, version <em>n</em> is left
 * exactly as it was, and every account opened under <em>n</em> goes on being an account opened
 * under <em>n</em>. Nothing in this module reads an account, so this is not a promise about
 * accounts that this class keeps carefully — it is a promise it could not break if it tried.
 *
 * <p><strong>"What this product pays" and "what your account pays" are not the same
 * sentence</strong>, and this class answers only the first of them. Free savings has published a
 * second version at a lower rate; the accounts opened before it carry on under the first, and
 * nothing here knows that any account exists. A later ticket puts an account under a version, and
 * it does so by naming the pair this module hands out rather than by copying the numbers.
 *
 * <p>The day is read off the application's own clock rather than {@code LocalDate.now()}, in the
 * zone this application counts its days in, so that a trainer who winds the clock two months
 * forward sees the version that would be on offer then — and so that everything about this
 * application's calendar agrees with everything else.
 *
 * <p>Read-only transactions on every read, which is most of this class, and that is a statement
 * about what those methods do rather than an optimisation: a transaction that cannot write is one
 * that cannot accidentally edit a published version. The three that write say so.
 */
@Service
public class ProductsService {

    private static final Logger log = LoggerFactory.getLogger(ProductsService.class);

    private final SavingsProductRepository products;
    private final ProductTermsRepository terms;
    /**
     * The record of what each account is living under, which is the one thing in this module that
     * is not about the catalogue. It is asked rather than duplicated, so that "what is this account
     * on" has one answer wherever it is asked from.
     */
    private final WhatEachSavingsAccountIsOn agreements;
    /**
     * The ledger, asked one question and only when closing an account: what is still in it. This
     * module owns no money and adds nothing up — the figure is read at the moment the rule needs it
     * and handed straight to the rule.
     *
     * <p>There is no cycle here to be careful of, and it is worth saying so once. Deposits declares
     * {@link io.dataroots.savingstreak.deposits.TheTermsADepositLandsUnder} and this module
     * implements it, so Deposits has never needed to name this one; the dependency has always run
     * this way, and this field is the first time it is written down as an import of the service
     * rather than of an interface.
     */
    private final DepositsService deposits;
    /**
     * The scheme, asked one question and only when a year is projected: what the first week of a run
     * of weeks pays today.
     *
     * <p>A projection on the shelf is priced for somebody with no run of weeks behind them, and the
     * rate a run of none pays is the ladder's first rung — which the bank publishes and can reprice
     * on a Monday. This module has no opinion about it and keeps no copy of it; it is read at the
     * moment the question is put and handed straight to {@link WhatAYearInAProductWouldPay}, which
     * is the same bargain the balance above is read under.
     *
     * <p>No cycle to be careful of here either. The scheme module depends on nothing in this
     * application by construction — that is its whole stated direction — so it is the one module
     * that can be read from anywhere without arranging the graph around it.
     */
    private final SchemeService scheme;
    private final Clock clock;

    ProductsService(SavingsProductRepository products, ProductTermsRepository terms,
                    WhatEachSavingsAccountIsOn agreements, DepositsService deposits,
                    SchemeService scheme, Clock clock) {
        this.products = products;
        this.terms = terms;
        this.agreements = agreements;
        this.deposits = deposits;
        this.scheme = scheme;
        this.clock = clock;
    }

    /**
     * What one savings account is living under: the product, the version it was opened with, the
     * day it began, and the condition that product attaches.
     *
     * <p><strong>Not {@link #product}, and the difference is the feature.</strong> That method
     * answers what a product is selling today; this one answers what an account is on, which stops
     * being the same thing the moment a version is published. Free savings has published a second
     * version already, so the two answers differ for every account opened before it — and a screen
     * that drew the catalogue's rate under the heading "your agreement" would be stating the
     * confusion this module exists to remove.
     *
     * <p>Nothing at all for an account nothing has recorded an agreement for, rather than an
     * invented one. A caller draws no agreement panel then, which is an honest reading of a
     * database that has not been through the migration, and is the same empty every other absent
     * reading in this application answers with. Whether the account exists at all is a question for
     * Accounts, and this module does not pretend to answer it.
     */
    @Transactional(readOnly = true)
    public Optional<TheAgreementAnAccountIsOn> theAgreementOf(long savingsAccountId) {
        Optional<TheAgreementAnAccountIsOn> agreement = agreements.theAgreementOf(savingsAccountId);
        log.debug("what a savings account is living under was read savingsAccountId={} product={} "
                        + "version={} openedOn={}",
                savingsAccountId,
                agreement.map(TheAgreementAnAccountIsOn::productCode).orElse(null),
                agreement.map(TheAgreementAnAccountIsOn::version).orElse(null),
                agreement.map(TheAgreementAnAccountIsOn::openedOn).orElse(null));
        return agreement;
    }

    /**
     * Everything the bank sells, in the order somebody chose, each with the set of terms it is
     * offering today.
     *
     * <p><strong>Closed products are in it.</strong> A product closed to new accounts is still a
     * product customers are holding, and its card reads exactly as it always did with
     * {@link AProductOnOffer#openToNewAccounts} false beside it. Filtering it out here would be
     * this method answering a question that belongs to whoever is opening an account — and would
     * leave a customer holding an account whose product the application would not admit to having.
     *
     * <p>Two queries for the whole catalogue rather than one per product: the products in the order
     * they are shown in, and every version of every product in one go. Four products would
     * otherwise be five round trips to draw one screen, and the rows the second query reads are
     * exactly the rows the first one is about to need.
     */
    @Transactional(readOnly = true)
    public List<AProductOnOffer> catalogue() {
        LocalDate today = today();
        Map<String, List<ProductTerms>> published = everyVersionByProductCode();
        List<AProductOnOffer> catalogue = products.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(product -> onOffer(product, published, today))
                .toList();
        log.debug("the savings products catalogue was read on={} products={} openToNewAccounts={}",
                today, catalogue.size(),
                catalogue.stream().filter(AProductOnOffer::openToNewAccounts).count());
        return catalogue;
    }

    /**
     * One product and the terms it is offering today, by the code an account names it with.
     *
     * <p>Refused with {@link ProductRefused.Kind#NO_SUCH_PRODUCT} when the bank sells no such
     * thing, in a sentence that quotes back the code that arrived — because a page that has been
     * open since before a release is how this refusal actually happens, and the code somebody sent
     * is the only part of it they can act on.
     *
     * <p>A closed product is <em>not</em> refused. It exists, somebody is holding it, and it reads
     * perfectly well; saying there is nothing called that would send whoever asked off to check
     * their spelling for a product sitting right there.
     */
    @Transactional(readOnly = true)
    public AProductOnOffer product(String code) {
        SavingsProduct product = theProductOrRefuse(code);
        ASetOfTerms currentTerms = TheTermsOnOfferToday
                .outOf(terms.findByProductCodeOrderByVersionAsc(product.code()), today())
                .asPublished();
        log.debug("a savings product was read code={} kind={} version={} annualRatePercent={} "
                        + "openToNewAccounts={}",
                product.code(), product.kind(), currentTerms.version(),
                currentTerms.annualRatePercent(), product.openToNewAccounts());
        return product.with(currentTerms);
    }

    /**
     * Every version that product has ever published, oldest first, each with the line saying what
     * changed.
     *
     * <p>All of them, including versions dated ahead of today and including the one on offer now.
     * A history that left out what is current would make the reader join two lists to see how the
     * offer has moved, and a history that hid a version published for next month would hide exactly
     * the thing somebody is looking for when they come to this page.
     *
     * <p>Oldest first, because this reads as a story: what the product said at the start, and what
     * each change did to it. The line saying what changed is null on the first version, because
     * nothing changed — that is what a first version is.
     *
     * <p><strong>Each version now carries what it moved about the one before it, worded by the same
     * function an account's own reading is worded by.</strong> The prose beside it says <em>why</em>
     * the bank changed something and the sentences say <em>what</em> changed, and the two are
     * different things that a history needs both of — {@link ProductTerms#whatChanged} argues that
     * at length, and this is the mechanical half it says the module would produce.
     * {@link WhatIsDifferentBetweenTwoSetsOfTerms} is the one place either list is worded, so a
     * customer who reads this history and then reads their own account meets the same sentence
     * twice rather than two attempts at it.
     *
     * <p>Each version is compared with the version before it in this list rather than with the one
     * on offer today, because a history is a sequence of steps: comparing every entry with the
     * current version would tell a reader nine times over how far each old version is from today,
     * which is a different and much less useful question than what each change did.
     */
    @Transactional(readOnly = true)
    public List<AVersionAndWhatItChanged> everyVersionOf(String code) {
        SavingsProduct product = theProductOrRefuse(code);
        List<ASetOfTerms> published = terms.findByProductCodeOrderByVersionAsc(product.code())
                .stream()
                .map(ProductTerms::asPublished)
                .toList();
        List<AVersionAndWhatItChanged> versions = new ArrayList<>(published.size());
        for (int i = 0; i < published.size(); i++) {
            versions.add(new AVersionAndWhatItChanged(published.get(i), i == 0
                    ? List.of()
                    : WhatIsDifferentBetweenTwoSetsOfTerms.between(
                            published.get(i - 1), published.get(i))));
        }
        log.debug("the versions a savings product has published were read code={} versions={}",
                product.code(), versions.size());
        return List.copyOf(versions);
    }

    /**
     * What a named amount would be worth after twelve months in each product somebody may still
     * open an account on, in euros of interest and in points, in the catalogue's own order.
     *
     * <p><strong>Open products only, which is the one place this module hides a card.</strong>
     * {@link #catalogue()} sends a closed product because customers are holding it and their
     * agreements have to read; this is a screen about what to open, and a figure beside an
     * agreement nobody will sign is an invitation to choose it. A customer on a closed product
     * reads what their own account is living under, which is {@link #theAgreementOf} and a
     * different question.
     *
     * <p><strong>Not one figure in the answer is worked out here.</strong>
     * {@link WhatAYearInAProductWouldPay} quotes the four rules that decide them — the rate a month
     * is paid at, what a rate comes to over a month, what a euro is worth in points, and what an
     * anniversary pays — and that is the whole reason this ticket exists. A screen doing its own
     * arithmetic is the second place a rate is priced and the first place two rates disagree, and
     * the test that keeps them honest deposits the same amount, winds a year, runs the sweep and
     * compares to the cent.
     *
     * <p><strong>At the version each product is selling today</strong>, which is what a projection
     * has to be: somebody reading this holds no account yet, so there is no agreement of theirs to
     * quote. An account opened this afternoon is written under exactly the version this projection
     * was made from, and one opened after tomorrow's repricing is not — which is why the card
     * beside the figure names the version.
     *
     * <p><strong>And at the version of the <em>scheme</em> in force today, for the one figure that
     * is not the product's.</strong> The points a euro earns on the way in are the product's
     * multiple times what the run of weeks pays, and the run this projection supposes is no run at
     * all — so the ladder's first rung is read off the scheme here and handed down, once for the
     * whole shelf. A card that went on quoting a first rung the bank has stopped publishing would be
     * promising less, or more, than the account it is inviting somebody to open would actually pay:
     * the same second-place-a-rate-is-priced failure this method's whole design is against.
     *
     * @param amount what the customer typed, judged here and refused in {@code AmountOfMoney}'s own
     *               words if it is not an amount of money
     * @throws ProductRefused if it is not one
     */
    @Transactional(readOnly = true)
    public List<AProjectionOverTwelveMonths> whatAYearWouldPayOn(BigDecimal amount) {
        BigDecimal typed = theAmountOrRefuse(amount);
        LocalDate today = today();
        Map<String, List<ProductTerms>> published = everyVersionByProductCode();
        // Read once for the whole shelf rather than once per card, so that two products cannot be
        // priced under two versions of the scheme if somebody publishes one mid-request — which is
        // the same consistency argument the streak derivation makes about reading the history once.
        BigDecimal theOrdinaryRate = scheme.theSchemeInForce().theOrdinaryRate();
        List<AProjectionOverTwelveMonths> projections =
                products.findAllByOrderBySortOrderAscIdAsc().stream()
                        .filter(SavingsProduct::openToNewAccounts)
                        .map(product -> whatAYearInItWouldPay(product, published, today, typed,
                                theOrdinaryRate))
                        .toList();
        // One INFO line per projection asked for, with the figure that was typed and what each
        // product came back with, because this is the answer a customer is about to choose a
        // product on and "what were they actually shown" has to be answerable afterwards from the
        // log rather than by retyping the amount into an application whose rates have since moved.
        log.info("a twelve-month projection was made on={} amount={} products={} interest={} "
                        + "points={}",
                today, AmountOfMoney.asMoney(typed), projections.size(),
                projections.stream().map(AProjectionOverTwelveMonths::interest).toList(),
                projections.stream().map(AProjectionOverTwelveMonths::points).toList());
        return projections;
    }

    /**
     * Publishes the next version of a product's terms and answers with it as it now reads.
     *
     * <p><strong>It writes one row and reads nothing back to correct.</strong> The version number
     * is one higher than the highest that product has published — counted from the rows rather than
     * held in a column on the product, for {@link TheTermsOnOfferToday}'s reason: a second stored
     * copy of "where this product has got to" is a second thing that eventually stops agreeing with
     * the first. The unique index over the pair is what catches two administrators counting the
     * same number at the same moment, and {@code ProductTermsRepository} argues that at length.
     *
     * <p><strong>A version effective in the past is accepted, and so is one effective
     * today.</strong> Backdating a correction is an ordinary thing for a bank to do, and refusing
     * it would leave somebody who published a rate a day late with no way to say what actually
     * happened. What that does to accounts opened in between is nothing at all: an account names
     * the version it was opened under, so a version published over the top of a date it has already
     * passed changes what is <em>offered</em> on that date and never what was <em>agreed</em> on
     * it. The one date refused is one before the version this follows, which would make the history
     * read backwards; {@link ProductRefused.Kind#A_VERSION_DATED_BEFORE_THE_ONE_BEFORE_IT} is where
     * that is argued.
     *
     * <p><strong>A version effective ahead of today is accepted and is not on offer yet.</strong>
     * That is how a rate change announced on Monday for the first of next month is written down
     * without anybody having to remember to press something on the morning — the rule is
     * {@link TheTermsOnOfferToday}'s and this method does not repeat it.
     *
     * <p>A product closed to new accounts may still publish. Closing stops the next account; it
     * does not stop the bank changing what it would sell if it reopened, and it emphatically does
     * not stop the bank correcting the terms the accounts still on it can compare themselves
     * against.
     */
    @Transactional
    public ASetOfTerms publishANewVersionOf(String code, ANewVersionOfTheTerms typed) {
        SavingsProduct product = theProductOrRefuse(code);
        List<ProductTerms> published = terms.findByProductCodeOrderByVersionAsc(product.code());
        if (published.isEmpty()) {
            // The same reading {@link TheTermsOnOfferToday} makes of the same impossible state, and
            // for the same reason: a product whose terms were never written is a broken database
            // rather than a customer's mistake, so there is no sentence to refuse anybody with.
            // Nobody did anything wrong, and there is no version number to count from.
            throw new IllegalArgumentException("a product with no published terms has no version to "
                    + "follow, and " + product.code() + " has none");
        }
        ProductTerms theOneBefore = published.get(published.size() - 1);
        ProductTerms next = WhatAVersionMaySay.readInto(
                product.code(), theOneBefore.version() + 1, typed);
        if (next.effectiveFrom().isBefore(theOneBefore.effectiveFrom())) {
            String reason = "Version " + next.version() + " of " + product.code() + " cannot take "
                    + "effect on " + next.effectiveFrom() + ", because version "
                    + theOneBefore.version() + " took effect on " + theOneBefore.effectiveFrom()
                    + " and a product's versions have to read forwards.";
            log.warn("a new version of a savings product's terms was refused product={} version={} "
                            + "effectiveFrom={} theOneBefore={} kind={} reason={}",
                    product.code(), next.version(), next.effectiveFrom(),
                    theOneBefore.effectiveFrom(),
                    ProductRefused.Kind.A_VERSION_DATED_BEFORE_THE_ONE_BEFORE_IT, reason);
            throw new ProductRefused(
                    ProductRefused.Kind.A_VERSION_DATED_BEFORE_THE_ONE_BEFORE_IT, reason);
        }
        ASetOfTerms written = terms.save(next).asPublished();
        // One INFO line carrying every figure that was decided, because this is the business event
        // this whole module exists for and the agreement it writes down is what accounts will be
        // living under for years. "Which rate was published, on what day, and what the person who
        // published it said about it" is the first question anybody debugging an interest posting
        // will ask, and it is answered here rather than reconstructed from the rows.
        log.info("a savings product published a new version of its terms product={} version={} "
                        + "effectiveFrom={} annualRatePercent={} bonusRatePercent={} noticeDays={} "
                        + "termMonths={} minimumBalance={} earlyExitPenaltyDays={} "
                        + "pointsMultiplier={} anniversaryRatePercent={} maturityAction={} "
                        + "theOneBefore={} onOfferToday={} whatChanged={}",
                written.productCode(), written.version(), written.effectiveFrom(),
                written.annualRatePercent(), written.bonusRatePercent(), written.noticeDays(),
                written.termMonths(), written.minimumBalance(), written.earlyExitPenaltyDays(),
                written.pointsMultiplier(), written.anniversaryRatePercent(),
                written.maturityAction(), theOneBefore.version(),
                !written.effectiveFrom().isAfter(today()), written.whatChanged());
        return written;
    }

    /**
     * Stops anybody opening a new account on that product, and disturbs nothing that is already on
     * it.
     *
     * <p>Answered with the product as it now reads, terms and all, because a closed product still
     * has terms and a screen that showed a blank card after closing one would be showing something
     * false about a product customers are holding.
     *
     * <p><strong>Closing a product that is already closed is not refused.</strong> There is no
     * lifecycle here to be at the wrong point of — it is one flag with two readings — and the
     * administrator asked for the product to be shut and it is shut. The rewards catalogue refuses
     * a second withdrawal because withdrawing is the end of an offer's life and cannot be undone;
     * this can be undone by the method below it, which is exactly what makes it a different kind of
     * thing. The log says whether anything actually moved, so a repeated press is visible without
     * being an error.
     */
    @Transactional
    public AProductOnOffer closeToNewAccounts(String code) {
        return whetherItIsOnSale(code, true);
    }

    /**
     * Puts it back on sale to new accounts, and publishes nothing: it comes back offering whatever
     * version is effective the day somebody next opens an account on it, which may not be the
     * version it was offering when it closed.
     */
    @Transactional
    public AProductOnOffer reopenToNewAccounts(String code) {
        return whetherItIsOnSale(code, false);
    }

    /**
     * Closes one savings account the customer has emptied, and answers with the agreement as it now
     * reads — product, version, the day it began, and the day it ended.
     *
     * <p><strong>Only an emptied account may be closed, and the balance is read here.</strong> What
     * the account still holds is the ledger's answer and is fetched at the moment the question is
     * put, so that a deposit made a second earlier is part of the answer. It is handed straight to
     * the rule and kept nowhere: this module owns no money and this method does not start it owning
     * any.
     *
     * <p><strong>Nothing is emptied on the customer's behalf.</strong> A refusal names the figure
     * so that what to do next is obvious, and the customer withdraws or moves the money themselves.
     * Where those euros land decides a week, a streak and a loyalty clock, and an application that
     * chose for them would be taking three decisions to save somebody one press.
     *
     * <p><strong>Nothing about the account goes away.</strong> Its deposits, its withdrawals, its
     * goals, its allocations and the saving rules pointing at it are all untouched, and read exactly
     * as they read for an account with nothing in it — which, by the rule above, is what it is. The
     * agreement itself stays too, naming the product and the version every one of those rows was
     * decided under, because a closed account that could not say what it had been is an account
     * whose whole history stops meaning anything.
     *
     * <p>Whether the account exists at all is a question for Accounts, and this module does not
     * pretend to answer it: an identifier nobody's account has arrives here as an agreement nobody
     * wrote, which is a broken database rather than a customer's mistake. Whoever is taking requests
     * settles that the account exists, and that the customer holds it, before asking this.
     *
     * @throws ProductRefused if the account still holds money, or was closed already
     */
    @Transactional
    public TheAgreementAnAccountIsOn closeTheSavingsAccount(long savingsAccountId) {
        BigDecimal whatItStillHolds = deposits.moneyBalanceOf(savingsAccountId);
        log.debug("closing a savings account was asked for savingsAccountId={} moneyBalance={}",
                savingsAccountId, whatItStillHolds);
        return agreements.closeTheAccount(savingsAccountId, whatItStillHolds);
    }

    /**
     * What one savings account's product is offering today, what the account is on, and what
     * differs between the two — the reading a customer decides from, and nothing more than a
     * reading.
     *
     * <p><strong>Not {@link #product} and not {@link #theAgreementOf}, but the sentence that joins
     * them.</strong> Those two answer what the bank is selling and what this account is living
     * under; this one answers the question a customer actually has when the two differ, which is
     * what it would mean to move. It is a third read rather than a field on either, because it is
     * the only one of the three that has to ask both.
     *
     * <p>Nothing at all for an account no agreement has been written for, like every other reading
     * about an account in this module: a page draws no panel then, rather than one saying nothing
     * has changed about an agreement nobody wrote.
     */
    @Transactional(readOnly = true)
    public Optional<TheNewerTermsOnOffer> theNewerTermsFor(long savingsAccountId) {
        return agreements.theNewerTermsFor(savingsAccountId);
    }

    /**
     * Puts one savings account onto the version its product is selling today, because its holder
     * asked, and answers with the agreement as it now reads.
     *
     * <p><strong>The fifth thing this module can be asked to write, and it is the one the whole
     * feature exists for.</strong> Nothing in this application adopts newer terms on anybody's
     * behalf, because newer is not the same as better — free savings' second version cut the rate
     * — so an account carries on under its version until this door is pressed. The only other place
     * an account's version moves is a roll-over at maturity, which its holder agreed to when they
     * opened the term.
     *
     * <p><strong>No money, no points and no allocations move.</strong> This method reads no ledger,
     * holds no balance and calls nothing that does: one column of one row changes, and what comes
     * back is the agreement rather than a receipt, because nothing was paid.
     *
     * <p>Refused on an account that is on a term at all — while the term runs because the agreement
     * is locked, and once its day has come because a maturity is settled by the terms it arrived
     * under — refused for an account already on the version on offer, and refused for a closed
     * account. {@link WhatEachSavingsAccountIsOn#takeTheNewerTerms} is where each of those is
     * argued and worded; this method decides none of them.
     *
     * @throws ProductRefused if the account is on a term, running or matured, if there is nothing
     *                        newer to take, or if the account has been closed
     */
    @Transactional
    public TheAgreementAnAccountIsOn takeTheNewerTerms(long savingsAccountId) {
        log.debug("taking the newer terms of a savings product was asked for savingsAccountId={}",
                savingsAccountId);
        return agreements.takeTheNewerTerms(savingsAccountId);
    }

    /**
     * The one method underneath both, because closing and reopening are one flag read two ways and
     * two copies of "find it, move the flag, log it, answer with it" is two chances to log one of
     * them and not the other.
     */
    private AProductOnOffer whetherItIsOnSale(String code, boolean closing) {
        SavingsProduct product = theProductOrRefuse(code);
        boolean changed = closing ? product.closeToNewAccounts() : product.reopenToNewAccounts();
        products.save(product);
        log.info("a savings product's door to new accounts was set code={} openToNewAccounts={} "
                        + "changed={}",
                product.code(), product.openToNewAccounts(), changed);
        ASetOfTerms currentTerms = TheTermsOnOfferToday
                .outOf(terms.findByProductCodeOrderByVersionAsc(product.code()), today())
                .asPublished();
        return product.with(currentTerms);
    }

    private SavingsProduct theProductOrRefuse(String code) {
        return products.findByCode(code).orElseThrow(() -> {
            String reason = "There is no savings product called " + code + ".";
            log.warn("a savings product was refused code={} kind={} reason={}",
                    code, ProductRefused.Kind.NO_SUCH_PRODUCT, reason);
            return new ProductRefused(ProductRefused.Kind.NO_SUCH_PRODUCT, reason);
        });
    }

    /**
     * Every product's versions, gathered by code, so that the catalogue asks the database once
     * rather than once per card.
     *
     * <p>A {@link LinkedHashMap} through {@code groupingBy}, so the lists stay in the order the
     * query returned them — lowest version first — which is the order the history is served in and
     * the order {@link TheTermsOnOfferToday} is entitled to assume nothing about.
     */
    private Map<String, List<ProductTerms>> everyVersionByProductCode() {
        return terms.findAllByOrderByProductCodeAscVersionAsc().stream()
                .collect(Collectors.groupingBy(ProductTerms::productCode, LinkedHashMap::new,
                        Collectors.toList()));
    }

    private AProductOnOffer onOffer(SavingsProduct product,
                                    Map<String, List<ProductTerms>> published, LocalDate today) {
        List<ProductTerms> versions = published.getOrDefault(product.code(), List.of());
        return product.with(TheTermsOnOfferToday.outOf(versions, today).asPublished());
    }

    /**
     * One card of the comparison: the product as it is selling today, and what twelve months of the
     * typed amount would come to in it.
     *
     * <p>The row rather than the published record, because the arithmetic wants basis points and
     * cents — which is the same reason the interest sweep holds a {@link ProductTerms} and not an
     * {@link ASetOfTerms}. What leaves is the published record all the same, inside
     * {@link AProductOnOffer}, so no basis point gets out of this module.
     *
     * <p><strong>The second figure is worked out on exactly two products and null on the rest, and
     * the condition says which.</strong> A bonus with a floor to keep is the one shape where the
     * headline figure is incomplete: which of the two numbers the customer gets depends on a
     * discipline nobody has asked them for yet. A product with no bonus has one honest figure and a
     * second identical one beside it would read as a choice that does not exist.
     */
    private AProjectionOverTwelveMonths whatAYearInItWouldPay(
            SavingsProduct product, Map<String, List<ProductTerms>> published, LocalDate today,
            BigDecimal amount, BigDecimal theOrdinaryRate) {
        ProductTerms onOffer = TheTermsOnOfferToday.outOf(
                published.getOrDefault(product.code(), List.of()), today);
        long amountCents = inCents(amount);
        WhatAYearInAProductWouldPay.AYearOfInterest leftAlone = WhatAYearInAProductWouldPay.on(
                amountCents, onOffer.annualRateBasisPoints(), onOffer.bonusRateBasisPoints(),
                onOffer.minimumBalanceCents(), true);
        boolean hasABonusToLose =
                onOffer.bonusRateBasisPoints() > 0 && onOffer.minimumBalanceCents() > 0;
        BigDecimal ifTheFloorIsNotKept = hasABonusToLose
                ? asEuros(WhatAYearInAProductWouldPay.on(amountCents,
                        onOffer.annualRateBasisPoints(), onOffer.bonusRateBasisPoints(),
                        onOffer.minimumBalanceCents(), false).interestCents())
                : null;
        long whenItLands = WhatAYearInAProductWouldPay.pointsWhenTheMoneyLands(
                amount, theOrdinaryRate, onOffer.pointsMultiplier());
        long onItsAnniversary = WhatAYearInAProductWouldPay.pointsOnItsFirstAnniversary(
                amount, onOffer.anniversaryRatePerWholeEuro());
        log.debug("a product was projected over twelve months code={} version={} amount={} "
                        + "annualRateBasisPoints={} bonusRateBasisPoints={} floor={} "
                        + "interest={} interestIfTheFloorIsNotKept={} bonusEarned={} "
                        + "theOrdinaryRate={} pointsWhenTheMoneyLands={} "
                        + "pointsOnItsFirstAnniversary={}",
                product.code(), onOffer.version(), AmountOfMoney.asMoney(amount),
                onOffer.annualRateBasisPoints(), onOffer.bonusRateBasisPoints(),
                asEuros(onOffer.minimumBalanceCents()), asEuros(leftAlone.interestCents()),
                ifTheFloorIsNotKept, leftAlone.bonusEarnedEveryMonth(), theOrdinaryRate,
                whenItLands, onItsAnniversary);
        return new AProjectionOverTwelveMonths(
                product.with(onOffer.asPublished()),
                amount,
                asEuros(leftAlone.interestCents()),
                asEuros(leftAlone.balanceCents()),
                leftAlone.bonusEarnedEveryMonth(),
                ifTheFloorIsNotKept,
                whenItLands,
                onItsAnniversary,
                whenItLands + onItsAnniversary);
    }

    /**
     * The typed figure as an amount of money, or the refusal saying why it is not one.
     *
     * <p>{@code AmountOfMoney}'s sentence, unchanged, because there is one rule in this application
     * about what an amount of money is and a projection is not the place to invent a second reading
     * of it. Nothing at all is refused separately, because "you typed nothing" and "what you typed
     * is not a number" are different mistakes and only the second one has a figure to quote back.
     */
    private BigDecimal theAmountOrRefuse(BigDecimal amount) {
        if (amount == null) {
            throw refuseTheProjection("A projection needs an amount of money to be made on.");
        }
        AmountOfMoney.whyItIsNotOne("projection", amount).ifPresent(reason -> {
            throw refuseTheProjection(reason);
        });
        return AmountOfMoney.quotedToTheCent(amount);
    }

    private ProductRefused refuseTheProjection(String reason) {
        log.warn("a twelve-month projection was refused kind={} reason={}",
                ProductRefused.Kind.AN_AMOUNT_NO_PROJECTION_CAN_BE_MADE_ON, reason);
        return new ProductRefused(
                ProductRefused.Kind.AN_AMOUNT_NO_PROJECTION_CAN_BE_MADE_ON, reason);
    }

    /**
     * An amount of euros as the whole cents the arithmetic works in.
     *
     * <p>Exact, and it is the refusal above that makes it so: an amount quoted more finely than the
     * cent never reaches here, so moving the point two places lands on a whole number and
     * {@code longValueExact} is a statement rather than a gamble. Two places written out, the way
     * {@code InterestService} writes the same move, because how many places a euro has is
     * {@code AmountOfMoney}'s own figure and it does not leave that module.
     */
    private static long inCents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    /** Cents back as the euros everything outside this module speaks, quoted the way money is. */
    private static BigDecimal asEuros(long cents) {
        return AmountOfMoney.quotedToTheCent(BigDecimal.valueOf(cents, 2));
    }

    /**
     * The day this application thinks it is, in the zone it counts its days in.
     *
     * <p>Borrowed from {@link SavingsWeek} rather than written out again, because a second copy of
     * the zone is a copy that can be changed on its own — and a catalogue that decided which
     * version was current in a different zone from the one a week is counted in would disagree with
     * the rest of the application for a few hours every evening.
     */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
    }
}
