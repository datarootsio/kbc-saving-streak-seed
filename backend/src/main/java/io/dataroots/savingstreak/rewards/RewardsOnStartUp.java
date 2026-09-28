package io.dataroots.savingstreak.rewards;

import java.util.List;

import io.dataroots.savingstreak.accounts.AccountHolder;
import io.dataroots.savingstreak.accounts.AccountsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Puts the catalogue into the database and brings the claims already in it up to what the module
 * now records, before the application serves anything.
 *
 * <p><strong>The catalogue is seeded, not migrated in.</strong> This is a training application
 * whose database is thrown away and remade; somebody who resets it has to find something to spend
 * points on without configuring anything first. The same reason the customers, their households,
 * their budgets and the challenges are seeded, and the same shape — {@code ChallengesOnStartUp} is
 * the component this one follows, down to building the rows afresh on every call.
 *
 * <p><strong>Seed-if-absent, by code, and that is a promise rather than an implementation
 * detail.</strong> A row already carrying the code is left alone, untouched and unread, so an
 * administrator who retitles an offer or reprices one keeps their figure through every restart
 * afterwards. Seeding is a floor and never a reset. The trade is the one the challenges make
 * knowingly: a change to the four entries below does <em>not</em> reach a database that already has
 * them, so re-seeding an edited catalogue is a fresh file, because the alternative is a start-up
 * that overwrites whatever the scheme changed last night.
 *
 * <p><strong>The four entries are the enum transcribed.</strong> Same codes, same titles, same
 * words, same prices, same voucher prefixes, in the same order, with every column the later slices
 * fill left null. That is the whole safety argument of this change and it is asserted rather than
 * asserted-to: the existing claiming and refusal tests pass unchanged, four prices and all.
 *
 * <p><strong>Then the claims.</strong> A claim used to be made by a savings account and is now made
 * by the customer, because the points that pay for it are theirs; a claim used to borrow its
 * title from the catalogue and now writes one down, because a catalogue somebody runs is one an
 * offer can be renamed in or withdrawn from; and a voucher used to be a code the application
 * forgot about and now has a state, because a scheme that cannot tell a voucher it has handed over
 * from one it has not is a scheme issuing liabilities it cannot see. All three are backfills of
 * the same kind — every row already in a database is made to read as what it is — and none of them
 * can wait until a request arrives, because the first request is somebody opening the page that
 * shows their vouchers, or somebody at a counter typing one of those codes in.
 *
 * <p><strong>Then whatever the table is still holding out for.</strong> A check the fixed catalogue
 * left on the reward column, and any column a claim cannot fill: both belong to
 * {@link RedemptionTable} rather than here, because the same things are true of any shape this
 * table has ever had — including one written by an application that is not this one — and none of
 * them have a backfill worth writing.
 *
 * <p>Runs on every start, and is written so that all but the first do nothing.
 */
@Component
class RewardsOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(RewardsOnStartUp.class);

    /**
     * The four rewards this application has always offered, and what each of them costs.
     *
     * <p>Transcribed from the enum that used to hold them, figure for figure and word for word, and
     * in the order they were declared in: cheapest first, so the catalogue reads as a ladder from
     * what a first deposit can already afford up to what saving for a while buys. That order is
     * what the rows are written in and what the catalogue is served in, so a customer sees the page
     * they saw yesterday.
     *
     * <p>Every one of them is published, a plain item, and leaves every other column null — no
     * stock, no window, no limits, no rules, no discount and no voucher shelf life. A null is the
     * absence of the rule in every case, so four offers that say nothing about themselves behave
     * exactly as four constants did. {@code FAMILY_CINEMA_PACK} is a bundle in everything but
     * structure and is seeded as an item all the same, for the same reason: this slice moves
     * nothing a customer already knew.
     *
     * <p>Built afresh on every call rather than held as a constant, for the reason the challenges
     * seed gives: these are entities, and a constant would be one instance handed to {@code save},
     * which attaches it, gives it an identifier, and turns the next start's insert into an update
     * of a row this step is supposed never to touch. Two applications in one JVM is not a
     * hypothetical here — it is how the tests assert what a restart does.
     */
    private static List<RewardOffer> theCatalogueThisApplicationHasAlwaysOffered() {
        return List.of(
                RewardOffer.onOfferAlready(
                        "CHARITY_DONATION",
                        "Charity donation",
                        "Ten points, given as money to this season's good cause. Nothing comes back "
                                + "to you.",
                        10,
                        "DON"),
                RewardOffer.onOfferAlready(
                        "SNACK_VOUCHER",
                        "Coffee or snack voucher",
                        "A coffee and something to go with it, at any counter in the scheme.",
                        40,
                        "SNK"),
                RewardOffer.onOfferAlready(
                        "CINEMA_TICKET",
                        "Cinema ticket",
                        "One seat, one film, any evening of the week.",
                        100,
                        "CIN"),
                RewardOffer.onOfferAlready(
                        "FAMILY_CINEMA_PACK",
                        "Family cinema pack",
                        "Two seats side by side and a snack big enough to share.",
                        180,
                        "FAM"));
    }

    private final RewardOfferRepository offers;
    private final RedemptionRepository redemptions;
    private final RedemptionTable table;
    private final AccountsService accounts;

    RewardsOnStartUp(RewardOfferRepository offers, RedemptionRepository redemptions,
                     RedemptionTable table, AccountsService accounts) {
        this.offers = offers;
        this.redemptions = redemptions;
        this.table = table;
        this.accounts = accounts;
    }

    /**
     * Called once every bean exists and before the context finishes refreshing — which is before the
     * web server binds its port and can therefore be asked for the catalogue or for what somebody
     * has claimed.
     */
    @Override
    public void afterSingletonsInstantiated() {
        seedTheCatalogue();
        giveOldClaimsToTheCustomersWhoMadeThem();
        // Before the sweep below, because SQLite refuses to drop a column that a check constraint
        // mentions: taking the check off first can only leave the sweep more able to do its job.
        table.takeTheOldCatalogueCheckOffTheRewardColumn();
        // Never skipped: the backfill above reads the savings account column and this is what drops
        // it, so a start that found nothing to hand over still has to clear whatever the table is
        // holding out for. This is the step that decides whether the next claim can be written.
        table.takeAwayColumnsNoClaimCanFill();
        // Last but one, because it writes to the table the two steps above have just finished
        // reshaping, and it reads titles out of the catalogue the first step seeded.
        giveOldClaimsTheTitleTheyWereClaimedUnder();
        // Last, and after the reshaping for the same reason the title is: on a file written before
        // vouchers had a state, the column itself only exists because generation has just added it,
        // and on a file from somewhere else the rebuild above has just copied it across. Nothing
        // reads it, so it could have gone anywhere after the surgery; it sits beside the other
        // backfill because it is the same kind of step and the two should be found together.
        everyVoucherAlreadyOutThereWasIssued();
    }

    /**
     * Writes whichever of the four entries the database has not got, and leaves the rest exactly as
     * they are.
     *
     * <p>Matched by code, which is the offer's natural key and the thing a claim already points at.
     * A row found under a code is not read, not compared and not corrected: what it says is what
     * whoever runs the scheme last said, and a start-up that argued with them would make every edit
     * a temporary one.
     */
    private void seedTheCatalogue() {
        List<RewardOffer> catalogue = theCatalogueThisApplicationHasAlwaysOffered();
        List<String> added = catalogue.stream()
                .filter(offer -> !offers.existsByCode(offer.code()))
                .map(offer -> offers.save(offer).code())
                .toList();
        // At INFO on every start, including the ordinary one where nothing was added. What the
        // catalogue did on the way up is the first question asked of a scheme whose entries are now
        // editable — a title somebody changed, an offer that went missing — and "added=0
        // leftAlone=4" is the line that answers it without anybody turning a logger up.
        log.info("the rewards catalogue was seeded added={} codes={} leftAlone={}",
                added.size(), added, catalogue.size() - added.size());
    }

    /**
     * Hands every claim recorded against a savings account to the customer who holds it, so that the
     * voucher shows up on the page of the person who is carrying it.
     */
    private void giveOldClaimsToTheCustomersWhoMadeThem() {
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
    }

    /**
     * Writes onto every claim that has none the title it should read by, so that a voucher issued
     * before this release goes on saying what it said yesterday.
     *
     * <p>A claim keeps its own title now, because an offer can be renamed or withdrawn and a claim
     * that borrowed the live one would change its mind about what it was. Every claim already in a
     * database was written without one, and a null would reach the page as a reward with no name
     * on it — so the catalogue is asked once, here, for the answer it was giving anyway, and
     * whatever it no longer has keeps its code to read by.
     */
    private void giveOldClaimsTheTitleTheyWereClaimedUnder() {
        long without = redemptions.claimsWithoutTheTitleTheyWereClaimedUnder();
        if (without == 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that a voucher reading oddly after a restart is not blamed on a step nobody can see.
            log.debug("every claim already says what it was claimed for claims=0");
            return;
        }
        int fromTheCatalogue = redemptions.giveClaimsTheTitleTheCatalogueHasForThem();
        int byTheirCode = redemptions.giveClaimsTheCatalogueHasLostTheirCodeToReadBy();
        log.info("claims recorded before this release were given the title they were claimed under "
                        + "claims={} fromTheCatalogue={} readingByTheirCode={}",
                without, fromTheCatalogue, byTheirCode);
    }

    /**
     * Writes {@code ISSUED} onto every voucher that has no state, so that a voucher handed out
     * before this release reads at a counter as exactly what it is.
     *
     * <p>A voucher has a life now — issued, then used or expired or cancelled — and a claim
     * written before it had one arrives with the column empty, because SQLite will not add a
     * {@code not null} column to a table that already has rows. A null would reach the counter
     * screen as a voucher whose state nobody can say, which is worse than no screen at all: the
     * one question somebody at a till is asking is whether to hand the thing over.
     *
     * <p>Nothing is being guessed at. Every one of these rows has been sitting in a database that
     * had no way to use, expire or cancel a voucher, so {@code ISSUED} is not the safest answer —
     * it is the only true one, and it is what the customer holding the code has every right to
     * expect.
     */
    private void everyVoucherAlreadyOutThereWasIssued() {
        long without = redemptions.vouchersWithoutAStateOfTheirOwn();
        if (without == 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that a voucher refused at a counter after a restart is not blamed on a step nobody
            // can see.
            log.debug("every voucher already says where it is in its life vouchers=0");
            return;
        }
        int issued = redemptions.sayEveryVoucherWithoutAStateWasIssued();
        log.info("vouchers issued before this release were given the state they are in "
                + "vouchers={} setToIssued={}", without, issued);
    }
}
