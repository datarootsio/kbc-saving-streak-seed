package io.dataroots.savingstreak.products;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.streaks.SavingsWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Puts the four savings products and the terms each of them has published into the database before
 * the application serves anything, and leaves them exactly as they are on every start after the
 * first.
 *
 * <p><strong>Seeded rather than migrated in.</strong> This is a training application whose database
 * is thrown away and remade; somebody who resets it has to find something to open an account on
 * without configuring anything first. The same reason the customers, their households, their
 * budgets, the challenges and the rewards catalogue are seeded, and the same shape —
 * {@code RewardsOnStartUp} is the component this one follows, down to building the rows afresh on
 * every call.
 *
 * <p><strong>Seed-if-absent, by code, and that is a promise rather than an implementation
 * detail.</strong> A product already carrying the code is left alone, untouched and unread, and so
 * is a version already published under that code and number. Seeding is a floor and never a reset,
 * so an administrator who publishes version 3 of free savings keeps it through every restart
 * afterwards. The trade is the one the rewards catalogue and the challenges make knowingly: a
 * change to the figures below does <em>not</em> reach a database that already has them, so
 * re-seeding an edited catalogue is a fresh file — because the alternative is a start-up that
 * overwrites an agreement somebody's money is living under.
 *
 * <p><strong>The versions are matched on the pair, not on the product.</strong> Asking "does free
 * savings have any terms" would make the seed skip its second version on a database that already
 * had its first, and that second version is the one row this whole feature is visible through: it
 * is what makes "what this product pays" a different sentence from "what your account pays".
 *
 * <p><strong>The effective dates are anchored to the day of the first start rather than written out
 * as calendar dates</strong>, which is the same argument {@code ChallengesOnStartUp} makes about
 * the season it seeds. A date hard-coded to, say, the first of March 2026 is a catalogue whose
 * second version has not happened yet for every trainer who runs this application before then, and
 * has happened suspiciously long ago for everyone who runs it after. The objection to anchoring is
 * that the dates move every time the database is rebuilt, and they do; that is the right trade
 * here, because seeding is idempotent, so the dates are written once and then never touched again,
 * and for the whole life of that file the catalogue sits still.
 *
 * <p><strong>And then every savings account is put on one of them.</strong> The catalogue is
 * seeded first and the accounts are migrated afterwards, in that order, by this class calling
 * {@link SavingsAccountsGetAProductOnStartUp} rather than by a second start-up component hoping to
 * be called second. Spring gives no ordering between two {@code SmartInitializingSingleton}s, and a
 * migration that ran before this one would find no products at all — so the ordering is a line of
 * code in a method rather than a property of a bean graph. No rate is read by anything yet and no
 * money moves: an account gains the name of the agreement it has always been living under, and that
 * is the whole of it.
 *
 * <p>Runs once every bean exists and before the context finishes refreshing — which is before the
 * web server binds its port and can therefore be asked for the catalogue. That ordering is the
 * reason it is not a {@code CommandLineRunner}: one of those runs after the application is already
 * accepting requests, and the first person to open the products page on the morning of a release
 * would be shown an empty shelf.
 */
@Component
class ProductsOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ProductsOnStartUp.class);

    private static final String FREE_SAVINGS = "INSTANT";
    private static final String THIRTY_TWO_DAY_NOTICE = "NOTICE32";
    private static final String CORE_SAVER = "CORE";
    private static final String TWELVE_MONTH_FIXED = "FIXED12";

    /**
     * How long before the first start the bank's opening terms took effect.
     *
     * <p>A year, and the figure is doing two jobs. The first is arithmetic: free savings' second
     * version is dated two months back, and a first version has to be older than the second or the
     * history reads backwards. The second is the one that will matter in the next ticket — every
     * savings account that already exists is migrated onto version 1 dated at its first deposit,
     * and a lab that has been running for months would otherwise have accounts opened before the
     * terms they are opened under. A year covers every file anybody has, and it reads as a bank
     * that has been open a while rather than one that opened this morning.
     */
    private static final int THE_BANK_PUBLISHED_ITS_FIRST_TERMS_THIS_MANY_MONTHS_AGO = 12;

    /**
     * How long before the first start free savings was repriced.
     *
     * <p>Two months, from the spec, and it is the whole reason a second version is seeded at all.
     * It has to be far enough back that a version published since an account was opened is plainly
     * a thing that happened rather than a thing happening today, and near enough that a trainer
     * does not have to explain a gap. Two months is also comfortably inside the window a wound-
     * forward clock will be moved through in a session.
     */
    private static final int FREE_SAVINGS_WAS_REPRICED_THIS_MANY_MONTHS_AGO = 2;

    /** No rate, no bonus, no notice, no term, no floor, no penalty: zero is the absent rule. */
    private static final int NONE = 0;

    /** The tenth every deposit in this application has always been paid on its anniversary. */
    private static final int THE_TENTH_LOYALTY_HAS_ALWAYS_PAID = 1_000;

    /** Five hundred euros, in cents, which is the floor the core saver asks to be kept. */
    private static final long THE_CORE_SAVERS_FLOOR_IN_CENTS = 50_000L;

    /**
     * The four products this bank sells, in the order somebody chose to show them in.
     *
     * <p>Ordered from the account that asks nothing of you to the one that asks for a year, which
     * is the order a person actually weighs these in and the order the rate climbs in. It is a
     * decision rather than an accident of insertion, which is why it is a column rather than the
     * identifier the rows happen to get.
     *
     * <p>The words on each card say the condition and the rate in the same breath, because that is
     * the sentence this whole feature exists to let a customer read: what you give up is what you
     * are paid for. A card that only said the rate would be a card that made the freest product
     * look like the worst one.
     *
     * <p>Built afresh on every call rather than held as a constant, for the reason the rewards and
     * challenges seeds give: these are entities, and a constant would be one instance handed to
     * {@code save}, which attaches it, gives it an identifier, and turns the next start's insert
     * into an update of a row this step is supposed never to touch. Two applications in one JVM is
     * not a hypothetical here — it is how the tests assert what a restart does.
     */
    private static List<SavingsProduct> theProductsThisBankSells() {
        return List.of(
                SavingsProduct.onOffer(
                        FREE_SAVINGS,
                        "Free savings",
                        ProductKind.INSTANT_ACCESS,
                        "Money in and out whenever you like, with nothing to give notice of and "
                                + "nothing to keep in. The lowest rate here, which is the price of "
                                + "that freedom.",
                        1),
                SavingsProduct.onOffer(
                        THIRTY_TWO_DAY_NOTICE,
                        "32-day notice",
                        ProductKind.NOTICE,
                        "A better rate for telling us a month before you need the money. Give "
                                + "notice on an amount, wait 32 days, and it is yours; until then "
                                + "it stays where it is.",
                        2),
                SavingsProduct.onOffer(
                        CORE_SAVER,
                        "Core saver",
                        ProductKind.MINIMUM_BALANCE,
                        "Keep EUR 500 in it and a bonus rate is yours on top. Dip under and you "
                                + "keep your money — you lose the bonus for the month you dipped, "
                                + "and nothing else.",
                        3),
                SavingsProduct.onOffer(
                        TWELVE_MONTH_FIXED,
                        "Twelve-month fixed",
                        ProductKind.FIXED_TERM,
                        "The best rate here, and the money is not yours again until the year is "
                                + "up. You can break the term if you must, and it costs you 90 "
                                + "days of interest.",
                        4));
    }

    /**
     * Every set of terms this bank has published: one opening version per product, and free
     * savings' second.
     *
     * <p>The figures are the spec's table transcribed, rate for rate, and they deliberately match
     * the sibling implementation of this feature so that the two codebases can be compared on how
     * they are built rather than on what they do.
     *
     * <p>Zero appears seven times per row and means the same thing every time: the rule is not
     * there. No bonus to earn, no notice to give, no term to serve, no floor to keep, no price for
     * leaving. The two loyalty figures are never zero — a product that paid no points and no
     * anniversary would be worse than the flat rule it replaces — so every product carries at least
     * the multiple of one and the tenth that this application has always paid.
     *
     * <p>{@link MaturityAction#HOLD} on the three products with no term, because the column has to
     * say something and the question is never asked of them; holding is the reading that does
     * nothing. The twelve-month fixed rolls over, which is the ending a customer who chose to lock
     * money away for a year is most likely to want again — and a roll-over is written at the rates
     * of the day it rolls, so it is an honest new agreement rather than this one extended.
     *
     * @param today the day the seed first ran, which every effective date is anchored to
     */
    private static List<ProductTerms> theTermsThisBankHasPublished(LocalDate today) {
        LocalDate opening =
                today.minusMonths(THE_BANK_PUBLISHED_ITS_FIRST_TERMS_THIS_MANY_MONTHS_AGO);
        LocalDate theRepricing = today.minusMonths(FREE_SAVINGS_WAS_REPRICED_THIS_MANY_MONTHS_AGO);
        return List.of(
                ProductTerms.published(
                        FREE_SAVINGS, 1, opening,
                        60, NONE, NONE, NONE, NONE, NONE,
                        BasisPoints.ONE_WHOLE_MULTIPLE, THE_TENTH_LOYALTY_HAS_ALWAYS_PAID,
                        MaturityAction.HOLD,
                        null),
                // The row the whole feature is visible through. Free savings pays less than it did
                // when the bank opened, and every account opened before this date carries on under
                // version 1 — so "what this product pays" and "what your account pays" stop being
                // the same sentence on the very first screen, without a trainer having to publish
                // anything.
                ProductTerms.published(
                        FREE_SAVINGS, 2, theRepricing,
                        50, NONE, NONE, NONE, NONE, NONE,
                        BasisPoints.ONE_WHOLE_MULTIPLE, THE_TENTH_LOYALTY_HAS_ALWAYS_PAID,
                        MaturityAction.HOLD,
                        "Rate cut from 0.60% to 0.50% a year. Nothing else changed, and accounts "
                                + "opened before this date carry on at 0.60% until their holder "
                                + "takes these terms."),
                ProductTerms.published(
                        THIRTY_TWO_DAY_NOTICE, 1, opening,
                        160, NONE, 32, NONE, NONE, NONE,
                        11_000, 1_200,
                        MaturityAction.HOLD,
                        null),
                ProductTerms.published(
                        CORE_SAVER, 1, opening,
                        80, 70, NONE, NONE, THE_CORE_SAVERS_FLOOR_IN_CENTS, NONE,
                        BasisPoints.ONE_WHOLE_MULTIPLE, THE_TENTH_LOYALTY_HAS_ALWAYS_PAID,
                        MaturityAction.HOLD,
                        null),
                ProductTerms.published(
                        TWELVE_MONTH_FIXED, 1, opening,
                        240, NONE, NONE, 12, NONE, 90,
                        12_500, 1_500,
                        MaturityAction.ROLL_OVER,
                        null));
    }

    private final SavingsProductRepository products;
    private final ProductTermsRepository terms;
    private final AccountAgreementRepository agreements;
    /**
     * The record of what each month has paid, for the one guarantee this class makes on its behalf:
     * a posting per account per period, and never two.
     */
    private final InterestPostingRepository postings;
    /**
     * The record of what was done about each maturity, for the one guarantee this class makes on its
     * behalf: a settlement per account per maturity, and never two.
     */
    private final MaturitySettledRepository settlements;
    private final SavingsAccountsGetAProductOnStartUp savingsAccounts;
    private final Clock clock;

    ProductsOnStartUp(SavingsProductRepository products, ProductTermsRepository terms,
                      AccountAgreementRepository agreements, InterestPostingRepository postings,
                      MaturitySettledRepository settlements,
                      SavingsAccountsGetAProductOnStartUp savingsAccounts, Clock clock) {
        this.products = products;
        this.terms = terms;
        this.agreements = agreements;
        this.postings = postings;
        this.settlements = settlements;
        this.savingsAccounts = savingsAccounts;
        this.clock = clock;
    }

    /**
     * The four guarantees, then the two seeds, then the migration — and the order is the whole of
     * what makes the last one work.
     *
     * <p>A savings account cannot be put on a product the database has never heard of, and a
     * deposit cannot be stamped with a version that has not been published, so the migration runs
     * last and is called from here rather than left to Spring. Two
     * {@code SmartInitializingSingleton}s have no ordering between them at all: whichever bean
     * happens to be registered first is called first, which is a fact about a class name rather
     * than about what either step needs.
     */
    @Override
    public void afterSingletonsInstantiated() {
        makeAVersionUniquePerProduct();
        makeAnAgreementUniquePerSavingsAccount();
        makeAPostingUniquePerPeriod();
        makeASettlementUniquePerMaturity();
        seedTheProducts();
        seedTheTermsTheyHavePublished();
        savingsAccounts.putEveryAccountThatPredatesTheCatalogueOnAProduct();
        sayWhenInterestStartsCountingOnTheAgreementsThatPredateIt();
    }

    /**
     * Makes one posting per account per period a rule the database keeps, before a cent of interest
     * can be paid.
     *
     * <p>With the other two guarantees rather than in a start-up step of its own, because one
     * start-up component per module is what makes a start a reviewer can read top to bottom — and
     * because this one has to be in place before the scheduler can fire the nightly sweep, which is
     * the same window the other two are protected in.
     *
     * <p>The sweep also checks in Java and would pay nothing twice on its own. The check and the
     * guarantee are different things: two runs of the job at the same moment would both read the
     * same empty set of periods already judged, and only a rule the database keeps stops both of
     * them from paying.
     */
    private void makeAPostingUniquePerPeriod() {
        if (postings.aPostingIsAlreadyUniquePerPeriod() > 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that the guarantee is something a reviewer can confirm on any start rather than only
            // on the first.
            log.debug("an interest posting is already unique per account and period "
                    + "index=one_posting_per_account_per_period");
            return;
        }
        postings.makeAPostingUniquePerPeriod();
        log.info("an interest posting was made unique per account and period "
                + "index=one_posting_per_account_per_period columns=[savings_account_id, "
                + "period_ordinal]");
    }

    /**
     * Makes one settlement per account per maturity a rule the database keeps, before a term can
     * reach its day.
     *
     * <p>With the other three guarantees rather than in a start-up step of its own, because one
     * start-up component per module is what makes a start a reviewer can read top to bottom — and
     * because this one has to be in place before the scheduler can fire the nightly maturity sweep,
     * which is the same window the others are protected in.
     *
     * <p>The sweep also checks in Java and would settle nothing twice on its own. The check and the
     * guarantee are different things: two runs of the job at the same moment would both read the
     * same empty set of maturities already settled, and only a rule the database keeps stops both of
     * them from rolling the same account into two terms.
     */
    private void makeASettlementUniquePerMaturity() {
        if (settlements.aMaturityIsAlreadyUniquePerAccount() > 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that the guarantee is something a reviewer can confirm on any start rather than only
            // on the first.
            log.debug("a settled maturity is already unique per account and maturity "
                    + "index=one_settlement_per_account_per_maturity");
            return;
        }
        settlements.makeAMaturityUniquePerAccount();
        log.info("a settled maturity was made unique per account and maturity "
                + "index=one_settlement_per_account_per_maturity columns=[savings_account_id, "
                + "maturity_ordinal]");
    }

    /**
     * Tells every agreement written before there was interest that it starts earning it today.
     *
     * <p>Last, and after the migration, because the migration writes agreements of its own and
     * those already say when their interest counts from — this is for the rows a previous release
     * left behind. An agreement dated at a deposit from last spring would otherwise either be paid
     * half a year of interest on the morning of an upgrade, if the day it was opened were read as
     * the day interest starts, or never be paid at all, if the absence were left standing. Today is
     * the honest third answer: nothing has been paid on this account yet, and the bank starts
     * paying now.
     *
     * <p>Runs on every start and does nothing on all but the first, for the reason every other step
     * here does: only the rows saying nothing are touched, so an account that has already been paid
     * for a month cannot have the day it counts from moved out from under those postings.
     */
    private void sayWhenInterestStartsCountingOnTheAgreementsThatPredateIt() {
        LocalDate today = today();
        int told = agreements.sayInterestCountsFrom(today);
        if (told == 0) {
            log.debug("no savings account was missing the day its interest counts from accounts=0");
            return;
        }
        log.info("savings accounts written before this release were told when their interest starts "
                + "counting accounts={} interestCountsFrom={} backdated=none", told, today);
    }

    /**
     * Makes one agreement per savings account a rule the database keeps, before any agreement is
     * written.
     *
     * <p>Before the migration for the reason the version index is before the seed: the guarantee is
     * worth nothing after the rows it is about are already in. Two agreements for one account would
     * be two answers to what that account is living under, and the deposits landing in it would be
     * stamped with whichever row came back first.
     *
     * <p>Here rather than on the entity, and here rather than in the migration's own class, for the
     * same two reasons the rest of this class gives: the SQLite dialect will not write it from an
     * annotation, and one start-up component per module is what makes a start a reviewer can read
     * top to bottom.
     */
    private void makeAnAgreementUniquePerSavingsAccount() {
        if (agreements.anAgreementIsAlreadyUniquePerSavingsAccount() > 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that the guarantee is something a reviewer can confirm on any start rather than only
            // on the first.
            log.debug("an agreement is already unique per savings account "
                    + "index=one_agreement_per_savings_account");
            return;
        }
        agreements.makeAnAgreementUniquePerSavingsAccount();
        log.info("an agreement was made unique per savings account "
                + "index=one_agreement_per_savings_account columns=[savings_account_id]");
    }

    /**
     * Makes one version per product a rule the database keeps, before anything can be written or
     * read.
     *
     * <p>First, and before either seed, because the guarantee is worth nothing after the rows it is
     * about are already in. The seed also checks in Java and would write nothing twice on its own;
     * the check and the guarantee are different things, and the administration door that publishes
     * a version arrives in a later ticket with no idea this conversation happened.
     *
     * <p>Here rather than on the entity because the entity cannot say it: the schema is generated
     * from the entity model against SQLite, and that dialect writes a composite unique clause
     * nowhere. {@code ChallengesOnStartUp} and {@code LoyaltyOnStartUp} say the same thing at
     * greater length about the same problem, and this is deliberately the same answer.
     */
    private void makeAVersionUniquePerProduct() {
        if (terms.aVersionIsAlreadyUniquePerProduct() > 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that the guarantee is something a reviewer can confirm on any start rather than only
            // on the first.
            log.debug("a version is already unique per savings product index=one_version_per_product");
            return;
        }
        terms.makeAVersionUniquePerProduct();
        log.info("a version was made unique per savings product index=one_version_per_product "
                + "columns=[product_code, version]");
    }

    /**
     * Writes whichever of the four products the database has not got, and leaves the rest exactly
     * as they are.
     *
     * <p>Matched by code, which is a product's natural key and the thing an account will point at.
     * A row found under a code is not read, not compared and not corrected: what it says is what
     * whoever runs the bank last said, and a start-up that argued with them would make every change
     * a temporary one.
     */
    private void seedTheProducts() {
        List<SavingsProduct> catalogue = theProductsThisBankSells();
        List<String> written = catalogue.stream()
                .filter(product -> !products.existsByCode(product.code()))
                .map(product -> products.save(product).code())
                .toList();
        if (written.isEmpty()) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that a shelf that looks short after a restart is not blamed on a step nobody can see.
            log.debug("the savings products this bank sells are already there products={} written=0",
                    catalogue.stream().map(SavingsProduct::code).toList());
            return;
        }
        log.info("the savings products this bank sells were seeded written={} codes={} "
                        + "alreadyThere={}",
                written.size(), written, catalogue.size() - written.size());
    }

    /**
     * Writes whichever published version the database has not got, and leaves the rest exactly as
     * they are.
     *
     * <p>After the products, because a version belongs to one: terms seeded against a product row
     * that is not there would be an agreement for something nobody sells, and a start that wrote
     * them the other way round would leave a window — brief, but real — in which a version existed
     * with nothing to attach it to.
     *
     * <p>Matched by the product code <em>and</em> the version number, so that free savings' second
     * version is written on a database that already has its first. A row found under that pair is
     * left exactly as it is, which is the whole of what "a published version is never edited" means
     * on a restart.
     */
    private void seedTheTermsTheyHavePublished() {
        LocalDate today = today();
        List<ProductTerms> published = theTermsThisBankHasPublished(today);
        List<String> written = published.stream()
                .filter(version -> !terms.existsByProductCodeAndVersion(
                        version.productCode(), version.version()))
                .map(version -> {
                    ASetOfTerms saved = terms.save(version).asPublished();
                    // One line per agreement written down, at INFO, because a set of terms is the
                    // thing accounts will live under for years and "which rate was written on the
                    // day this file was made" is the first question anybody debugging an interest
                    // posting will ask.
                    log.info("a set of savings product terms was seeded product={} version={} "
                                    + "effectiveFrom={} annualRatePercent={} bonusRatePercent={} "
                                    + "noticeDays={} termMonths={} minimumBalance={} "
                                    + "earlyExitPenaltyDays={} pointsMultiplier={} "
                                    + "anniversaryRatePercent={} maturityAction={}",
                            saved.productCode(), saved.version(), saved.effectiveFrom(),
                            saved.annualRatePercent(), saved.bonusRatePercent(), saved.noticeDays(),
                            saved.termMonths(), saved.minimumBalance(),
                            saved.earlyExitPenaltyDays(), saved.pointsMultiplier(),
                            saved.anniversaryRatePercent(), saved.maturityAction());
                    return saved.productCode() + " v" + saved.version();
                })
                .toList();
        if (written.isEmpty()) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that a product reading at yesterday's rate after a restart is not blamed on a step
            // nobody can see.
            log.debug("the terms this bank has published are already there versions={} written=0",
                    published.size());
            return;
        }
        log.info("the terms this bank has published were seeded written={} versions={} "
                        + "alreadyThere={} anchoredTo={}",
                written.size(), written, published.size() - written.size(), today);
    }

    /**
     * The day this application thinks it is, in the zone it counts its days in — borrowed from
     * {@link SavingsWeek} for the reason {@link ProductsService} gives about the same line: a
     * second copy of the zone is a copy that can be changed on its own.
     */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
    }
}
