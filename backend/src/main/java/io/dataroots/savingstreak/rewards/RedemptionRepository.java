package io.dataroots.savingstreak.rewards;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: the rest of the application goes through {@link RewardsService}. */
interface RedemptionRepository extends JpaRepository<Redemption, Long> {

    /**
     * Newest first, because someone checking what they have spent starts from what they claimed
     * last. The identifier settles it when two claims share a moment, as everywhere else.
     */
    List<Redemption> findByCustomerIdOrderByClaimedAtDescIdDesc(long customerId);

    /**
     * One claim by the code printed on the voucher it issued, which is the only thing somebody at
     * a counter has to go on.
     *
     * <p>Exactly one or none, guaranteed by the unique constraint the column already carries and
     * by the reason that constraint was given: two vouchers under one code would be two customers
     * holding the same thing, and a counter with no way to tell which of them was standing in
     * front of it.
     */
    Optional<Redemption> findByVoucherCode(String voucherCode);

    /**
     * Every voucher still in play whose last good day is behind us — what the nightly sweep
     * retires, oldest first.
     *
     * <p><strong>A voucher with no day on it is not in this answer, and that is the rail the whole
     * slice rests on.</strong> {@code expiresOn is not null} is written out rather than left to
     * SQL deciding that {@code null < today} is unknown, because a reader of this query has to be
     * able to see the guarantee rather than deduce it from three-valued logic: an offer that set
     * no shelf life issues vouchers that this sweep can never touch, whatever the clock is wound
     * to. All four seeded offers are in that state, so this is what protects every voucher this
     * application has ever issued.
     *
     * <p>Still issued, and only still issued. A voucher that was handed over at a counter last
     * week has been spent and is not a liability that runs out; a voucher already expired must not
     * be expired a second time; and all three ends of a voucher's life are terminal, so an
     * expiry that overwrote one of them would be the one thing this state machine exists to
     * forbid. The entity refuses it as well — {@code Redemption.ranOut} throws — and this query is
     * what means it never comes up.
     *
     * <p>Strictly before today, because the day on the voucher is the last day it is good. A
     * voucher that runs out today is good today; the sweep takes it tomorrow morning, which is
     * exactly what somebody reading "valid until the 14th" expects to happen on the 15th.
     *
     * <p>Oldest first, by the day and then by the identifier when two share one. Nothing about the
     * outcome depends on the order — each voucher is judged alone and nothing is drawn down — but
     * a sweep whose log reads in the order things were promised is a sweep somebody can follow,
     * and the identifier settles ties the way it does everywhere else here.
     */
    @Query("select claim from Redemption claim where claim.voucherState = :state "
            + "and claim.expiresOn is not null and claim.expiresOn < :today "
            + "order by claim.expiresOn asc, claim.id asc")
    List<Redemption> vouchersThatRanOutBefore(@Param("state") VoucherState state,
                                              @Param("today") LocalDate today);

    /**
     * Whether claims are still stored against the savings account they were made from, which is how
     * every claim recorded before points belonged to a customer was stored.
     *
     * <p>Asked of the database's own catalogue rather than assumed, because both shapes exist in the
     * wild: a file this release created has never had the column, and a file the previous release
     * wrote still has it. Every statement below only means anything while it is there.
     */
    @Query(value = "select count(*) from pragma_table_info('redemption') "
            + "where name = 'savings_account_id'", nativeQuery = true)
    long claimsStillNameASavingsAccount();

    /**
     * The savings accounts behind every claim that has no customer on it yet, each named once.
     *
     * <p>Native, and the only thing that reads the old column: it is not on the entity any more,
     * because a claim is a customer's now. Who holds those accounts is not this module's to know —
     * {@link RewardsOnStartUp} asks Accounts and comes back with the answer.
     */
    @Query(value = "select distinct savings_account_id from redemption where customer_id is null",
            nativeQuery = true)
    List<Long> savingsAccountsBehindClaimsWithoutACustomer();

    /**
     * Gives every ownerless claim made from one savings account to the customer who holds it. Only
     * the claims that have no customer, so a start after the first changes nothing.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "update redemption set customer_id = :customerId "
            + "where customer_id is null and savings_account_id = :savingsAccountId",
            nativeQuery = true)
    int giveClaimsMadeFrom(@Param("savingsAccountId") long savingsAccountId,
                           @Param("customerId") long customerId);

    /**
     * How many claims have not got the title they were claimed under written on them, which is
     * every claim made before a claim kept one.
     *
     * <p>Native and counting nulls rather than a derived query over the entity, because the answer
     * is wanted before anything reads a claim: a page asked for somebody's vouchers in that window
     * would show them a reward with no name on it.
     */
    @Query(value = "select count(*) from redemption where title is null", nativeQuery = true)
    long claimsWithoutTheTitleTheyWereClaimedUnder();

    /**
     * Writes onto every claim that has no title the title the catalogue holds for the code it
     * names.
     *
     * <p>The closest thing to the truth that is still available. What these claims were called when
     * they were made is not recorded anywhere — that is the hole this release closes — and the
     * catalogue's answer is the one the page was showing for them yesterday, so nobody's history
     * changes on the morning of the upgrade.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "update redemption set title = "
            + "(select offer.title from reward_offer offer where offer.code = redemption.reward) "
            + "where title is null and reward in (select code from reward_offer)",
            nativeQuery = true)
    int giveClaimsTheTitleTheCatalogueHasForThem();

    /**
     * Writes the code itself onto whatever is left, which is a claim for something the catalogue no
     * longer has.
     *
     * <p>The honest answer rather than a failure, and the same one the reading used to fall back
     * to. Nothing can reach this on a file this application wrote, because every code in the table
     * came from the four the catalogue was seeded with — but a file that has been through
     * something else can carry anything, and a voucher with an empty name on it is worse than a
     * voucher with a code on it.
     */
    @Transactional
    @Modifying
    @Query(value = "update redemption set title = reward where title is null", nativeQuery = true)
    int giveClaimsTheCatalogueHasLostTheirCodeToReadBy();

    /**
     * How many vouchers have no state written on them, which is every voucher issued before a
     * voucher had one.
     *
     * <p>Native and counting nulls rather than a derived query over the entity, for the reason the
     * title's count above is: the answer is wanted before anything reads a claim, and a voucher
     * read in that window would reach the counter screen with no state on it and no way to say
     * whether it was good.
     */
    @Query(value = "select count(*) from redemption where voucher_state is null",
            nativeQuery = true)
    long vouchersWithoutAStateOfTheirOwn();

    /**
     * Writes {@code ISSUED} onto every voucher that has no state, which is the truth about all of
     * them.
     *
     * <p>There is no guessing in this one, unlike the title backfill next to it. A voucher in a
     * database written before this release has never been handed over at a counter, never outlived
     * a shelf life and never been cancelled, because this application had no way to do any of
     * those things: the three ends of a voucher's life arrive with the code that writes them. So
     * {@code ISSUED} is not a default standing in for an unknown — it is the only state any of
     * these rows has ever been in.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "update redemption set voucher_state = 'ISSUED' where voucher_state is null",
            nativeQuery = true)
    int sayEveryVoucherWithoutAStateWasIssued();

    /**
     * How many of each offer one customer has claimed — in all, and inside one savings week —
     * and how many of it have gone out altogether, with an offer nobody has ever claimed simply
     * missing from the answer.
     *
     * <p><strong>One query for the whole catalogue, which is the point of it.</strong> A limit is
     * a fact about a customer and an offer, so a reading of the catalogue needs one per row; a
     * derived count called inside the loop that draws the page would be a question per card,
     * against a table that grows by one row every time anybody claims anything. Grouped by the
     * code instead, and the reading picks each offer's row out of a map. The claim path asks the
     * same question through the same query rather than a narrower one of its own, so that the
     * figure a card is greyed against and the figure a claim is refused against cannot be counted
     * two different ways.
     *
     * <p><strong>Three counts and not two, because scarcity asked the same table the same
     * question at the same moment.</strong> What is left of a scarce offer is its stock less
     * every claim still standing against it, and that was a second query running once per scarce
     * offer on the very same page load. It is the third figure here instead. All three counts
     * therefore live in the projection rather than in a {@code where}: the group is every claim
     * under a code, and each figure says for itself which rows it wants. A code nobody has
     * claimed is still missing, which is what {@code noneOf} answers.
     *
     * <p><strong>The week is handed in as two moments rather than worked out here.</strong> Which
     * Monday a day belongs to is {@code SavingsWeek}'s answer and the zone it is read in is named
     * once, where it already lives; a query that did its own date arithmetic would be a second
     * definition of a week in an application whose streak, loyalty bonus and challenges all count
     * in the first one. Half-open — from the Monday's midnight inclusive to the following
     * Monday's exclusive — because the two weeks either side of a boundary have to agree about
     * which of them owns a claim made on the stroke of it, and that is the only reading under
     * which a weekly cap resets exactly once.
     *
     * <p><strong>A cancelled claim is not counted and an expired one is — in all three
     * figures.</strong> A cancellation is the scheme undoing a claim: an allowance that went on
     * being spent by a claim nobody ended up making would be the customer paying for somebody
     * else's mistake twice, and a thing that went on being counted out of the stock would be a
     * scheme refusing to sell something it still has. An expiry is the customer's own miss, and
     * giving back either the allowance or the stock for one would make a shelf life cost nobody
     * anything. The two limit counts have carried the clause since the slice that wrote them, in
     * anticipation; {@code goneInAll} gained it with the slice that first writes
     * {@code CANCELLED}, which is the slice that can finally make it change an answer. It is the
     * same clause written three times rather than a {@code where} shared between them, because
     * the three counts want different rows out of the same group and only this one is about
     * everybody.
     *
     * <p><strong>Stock returning is a claim ceasing to count and nothing else.</strong> There is
     * no column to put one back into, no restock step and nothing to go wrong halfway: an offer
     * with three of them and two live claims has one left, and cancelling one of the two makes it
     * two the next time anybody asks. That is the whole implementation of "cancelling returns the
     * stock", and it is the payoff of having derived what is left rather than decremented it —
     * the version of this feature that kept a column would have needed an increment here, an
     * argument about doing it twice, and a way to tell a restock from a cancellation afterwards.
     *
     * <p>The null state is let through deliberately, rather than left to be dropped by
     * three-valued logic. A claim with no state written on it is a claim that happened, and the
     * startup backfill means a running application has none — but {@code <> :cancelled} is
     * <em>unknown</em> for a null and would quietly leave such a row out, which is the one
     * direction this count must never be wrong in: an uncounted claim hands somebody another of
     * something they have already had their allowance of.
     */
    @Query("select new io.dataroots.savingstreak.rewards.HowManyHaveGone("
            + "claim.reward, "
            + "sum(case when claim.customerId = :customerId "
            + "and (claim.voucherState is null or claim.voucherState <> :cancelled) "
            + "then 1 else 0 end), "
            + "sum(case when claim.customerId = :customerId "
            + "and (claim.voucherState is null or claim.voucherState <> :cancelled) "
            + "and claim.claimedAt >= :weekStartsAt and claim.claimedAt < :weekEndsAt "
            + "then 1 else 0 end), "
            + "sum(case when claim.voucherState is null or claim.voucherState <> :cancelled "
            + "then 1 else 0 end)) "
            + "from Redemption claim "
            + "group by claim.reward")
    List<HowManyHaveGone> howManyOfEachHaveGone(
            @Param("customerId") long customerId,
            @Param("cancelled") VoucherState cancelled,
            @Param("weekStartsAt") Instant weekStartsAt,
            @Param("weekEndsAt") Instant weekEndsAt);

    /**
     * How many of one offer have gone out — every claim ever made against its code.
     *
     * <p><strong>The other half of what is left, and the reason no column holds it.</strong>
     * Stock less this is how many remain, counted at the moment somebody asks rather than
     * decremented as claims are made, for the reason the spec gives once and this codebase
     * already follows twice: two stored figures that must agree eventually stop agreeing.
     *
     * <p><strong>Every state counts but one, and that is the rule rather than an
     * oversight.</strong> A voucher handed over at a counter is obviously gone. A voucher that
     * outlived its shelf life is gone too — the expiry sweep says so in its own javadoc and says
     * it twice, that an expiry returns no points and no stock, because a shelf life a customer
     * missed is the customer's miss and restocking it would hand the thing to somebody else at
     * the scheme's expense. The one state that does not count is {@code CANCELLED}, because a
     * cancellation is the scheme's own mistake and puts the thing back in the window. This query
     * was deliberately written without that filter while nothing could write the state, and this
     * is the slice that adds it — so that an administrator lowering a stock figure is refused
     * against what is actually out rather than against claims the scheme itself revoked, and is
     * never told that three have gone when one of the three was given back.
     *
     * <p>The name is kept although the query is no longer derived from it, and the filter is
     * written out rather than expressed as {@code countByRewardAndVoucherStateNot}. A derived
     * {@code Not} would compare a null state with {@code <>}, which is <em>unknown</em> in SQL
     * and would quietly drop any claim written before a voucher had a state at all — the same
     * trap the grouped count above spells its way around, and the one direction a stock count
     * must never be wrong in, because an uncounted claim lets an administrator set the stock
     * below what is genuinely out there.
     *
     * <p>By the code rather than by a foreign key, because that is what a claim stores: the code
     * is the offer's immutable natural key and a claim names it as text, which is what keeps a
     * voucher readable after the offer behind it has been withdrawn.
     *
     * <p><strong>One offer, and only the paths with no reading behind them use it now.</strong>
     * The reading of the catalogue and the claim both used to come through here, once per scarce
     * offer; they take the same figure off {@code howManyOfEachHaveGone} above since
     * that query and this one were folded together. What is left is the two callers that have no
     * customer and no catalogue in front of them — an administrator lowering a stock figure, who
     * is refused when it is below what has already gone out, and the line that says what is left
     * of the offer behind a voucher somebody has just cancelled — and for one offer a grouped
     * count of the whole table would be the wrong shape of question.
     *
     * <p>Both of those callers add {@code RewardHoldRepository.howManyAreHeldOf} to whatever
     * this answers, because a hold is stock that is spoken for and neither figure is the whole
     * of what has gone. The two are kept as two queries over two tables rather than folded into
     * one sum here: a cancelled claim and a lapsed hold are told apart by different rules, and a
     * single count would be the place they stopped being.
     */
    @Query("select count(claim) from Redemption claim where claim.reward = :reward "
            + "and (claim.voucherState is null or claim.voucherState <> :cancelled)")
    long countByReward(@Param("reward") String reward,
                       @Param("cancelled") VoucherState cancelled);
}
