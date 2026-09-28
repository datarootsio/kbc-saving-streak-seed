package io.dataroots.savingstreak.points;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: the rest of the application goes through {@link PointsService}. */
interface PointsCreditRepository extends JpaRepository<PointsCredit, Long> {

    /**
     * Zero for a customer who has earned nothing, which is an answer rather than an absence.
     *
     * <p>Expired batches are left out explicitly. What is left in one is still recorded — that is
     * where the figure a sweep took survives — and a balance that summed it would report points the
     * customer cannot spend.
     */
    @Query("select coalesce(sum(credit.remainingPoints), 0) from PointsCredit credit "
            + "where credit.customerId = :customerId and credit.expiredAt is null")
    long remainingPointsOf(@Param("customerId") long customerId);

    /**
     * Everything this customer has ever been credited, whatever became of it. Zero for somebody
     * who has earned nothing, which is an answer rather than an absence.
     *
     * <p><strong>The credited figure and never what is left of it</strong>, which is the whole
     * difference between this and the balance above. A lifetime is a record of behaviour: it
     * counts a batch that was spent on a reward and a batch whose twelve months ran out, because
     * both of them were earned and neither of them un-happened. Summing what remains would be
     * asking the same question the balance already answers, and it would mean a customer's
     * lifetime went <em>down</em> every time they claimed something — which would make a reward
     * gated behind a lifetime a reward you lose the right to by using the scheme.
     *
     * <p>Expired batches are therefore included, deliberately and unlike every other sum here.
     * An expiry is points going out of reach rather than points never having been earned, and
     * the reason a batch is kept after it is swept — rather than emptied — is precisely so that
     * the figure it was survives.
     *
     * <p>Every reason, and no filter on one. A gift received was earned by whoever received it as
     * far as this figure is concerned, in the same way a challenge reward was: they are points
     * that arrived through the one door this ledger has, and picking which reasons count would
     * be this query deciding what behaviour the scheme wants to reward — which is the
     * administrator's decision and is made by setting a threshold, not by filtering a sum.
     */
    @Query("select coalesce(sum(credit.points), 0) from PointsCredit credit "
            + "where credit.customerId = :customerId")
    long everEarnedBy(@Param("customerId") long customerId);

    /**
     * The batches a spend can draw from, oldest first, skipping those with nothing left in them.
     *
     * <p>Oldest first is the rule the whole batch structure exists for: points earned earliest are
     * spent earliest, so the ones nearest expiring leave first. The identifier settles it when two
     * batches share a moment, the same way the deposit list does — a moment is only kept to the
     * millisecond, and two deposits can land inside one.
     *
     * <p>An expired batch is not one of them. Its points are gone whether or not anything was left
     * in it, and a spend that drew from one would be paying for a reward with points the balance
     * beside it has already stopped counting.
     *
     * <p>Which is also the set "what expires next" is answered from — the same rows a spend would
     * draw on, so the two answers cannot disagree about what the customer still has. The order is
     * not what decides that answer, though: {@link PointsService#whatExpiresNextFor} finds the
     * earliest day rather than trusting the front of this list, because twelve calendar months clamp
     * 29 February back onto the 28th and earned order is then a hair short of anniversary order.
     */
    @Query("select credit from PointsCredit credit "
            + "where credit.customerId = :customerId and credit.remainingPoints > 0 "
            + "and credit.expiredAt is null "
            + "order by credit.earnedAt asc, credit.id asc")
    List<PointsCredit> unspentOldestFirst(@Param("customerId") long customerId);

    /**
     * Every customer's batches that a sweep might have to end, oldest first: still holding
     * something, not already expired, and earned long enough ago to be worth judging.
     *
     * <p>Everybody's at once, because the sweep is one pass over the ledger rather than a pass per
     * customer. A batch nobody owns — one earned in an account whose holder had gone by the time
     * {@link PointsOnStartUp} looked, and so left without a customer — is swept up with the rest:
     * nobody can spend it, so nothing is taken from anybody by letting its twelve months run out.
     *
     * <p>Earned before a cut-off rather than exactly twelve months ago. The cut-off carries a
     * little slack and {@link PointsExpiry} says why; the rule itself is applied to each batch that
     * comes back, so this query only has to be generous rather than exact.
     *
     * <p>Oldest first because that is the order points leave in, spent or expired, and because it
     * makes the sweep's DEBUG lines read as a chronology rather than as whatever order the database
     * felt like.
     */
    @Query("select credit from PointsCredit credit "
            + "where credit.remainingPoints > 0 and credit.expiredAt is null "
            + "and credit.earnedAt < :earnedBefore "
            + "order by credit.earnedAt asc, credit.id asc")
    List<PointsCredit> unspentBatchesEarnedBefore(@Param("earnedBefore") Instant earnedBefore);

    /**
     * What each of the given deposits earned when it was made, one row per reason it earned under —
     * the batch as it was credited, not what is left of it, because what a deposit earned cannot
     * change afterwards.
     *
     * <p>The reasons are asked for rather than assumed, because a reference only means anything
     * alongside the reason that wrote it: the day a batch is earned for something other than a
     * deposit, its reference will be to that other thing and must not be read as a deposit's. The
     * caller names the reasons a deposit can earn under and gets every one of them back, so a
     * second way of earning shows up in the answer instead of being filtered out of it.
     */
    @Query("select credit.sourceReferenceId as sourceReferenceId, credit.reason as reason, "
            + "credit.points as points "
            + "from PointsCredit credit "
            + "where credit.reason in :reasons and credit.sourceReferenceId in :sourceReferenceIds")
    List<EarnedPoints> earnedBy(@Param("reasons") Collection<PointsReason> reasons,
                                @Param("sourceReferenceIds") Collection<Long> sourceReferenceIds);

    /**
     * The batches the given deposits earned that are still there: nothing expired, nothing spent
     * down to zero, and only the reasons a deposit can have earned under.
     *
     * <p>The same set {@link #unspentOldestFirst} answers from, narrowed to what a named handful of
     * deposits earned rather than to one customer. So the days a deposit's points go and the day the
     * customer's next points go are read off the same rows, and the two cannot come to disagree
     * about what is still there.
     *
     * <p>The reasons are named for the reason {@link #earnedBy} gives, and it is load bearing here
     * for a second time: a batch credited for a gift references the gift rather than a deposit, and
     * a query that matched on the reference alone would put somebody else's points on the account of
     * whichever deposit happened to share a number with that gift.
     *
     * <p>Unordered, because the caller groups these by the day they expire and the order rows arrive
     * in cannot survive that. {@link PointsService#whenThePointsEarnedByDepositsGo} sorts the days
     * it produces, which is the only order anybody reads.
     */
    @Query("select credit from PointsCredit credit "
            + "where credit.reason in :reasons and credit.sourceReferenceId in :sourceReferenceIds "
            + "and credit.remainingPoints > 0 and credit.expiredAt is null")
    List<PointsCredit> survivingBatchesEarnedBy(@Param("reasons") Collection<PointsReason> reasons,
                                                @Param("sourceReferenceIds") Collection<Long> sourceReferenceIds);

    /**
     * Every batch credited before this release, which is every batch that does not yet carry the
     * moment it expires.
     *
     * <p>Everybody's at once and whatever became of them: a batch already spent to nothing and a
     * batch already swept are stamped alongside the living ones. Neither of them will ever be read
     * for its expiry — the sweep asks only for batches that have not gone, and a balance sums only
     * what is left — but a column that is null on some rows and not others is a column the next
     * reader has to ask a question about, and the answer would be a story about a release rather
     * than about the ledger.
     *
     * <p>The rows themselves rather than an update statement. The promise is twelve calendar months
     * in the zone this application counts calendars in, clamped at the end of February the way
     * {@link PointsExpiry} clamps it, and SQLite's own date arithmetic is a second implementation of
     * that rule which would disagree with the first on one day in four years. So the rows come back,
     * the rule is applied to each of them in Java, and there is still exactly one place that knows
     * what a lifetime means.
     *
     * <p>Only the unstamped ones, which is what makes the pass a floor: a start after the first
     * finds nothing and writes nothing.
     */
    @Query("select credit from PointsCredit credit where credit.expiresAt is null")
    List<PointsCredit> batchesNotYetCarryingWhenTheyExpire();

    /**
     * Whether batches are still stored against the savings account they were earned in, which is how
     * every batch written before points belonged to a customer was stored.
     *
     * <p>Asked of the database's own catalogue rather than assumed, because the two shapes both
     * exist in the wild: a file this release created has never had the column, and a file the
     * previous release wrote still has it. Every statement below only means anything while it is
     * there, which is why they are never run without asking this first.
     */
    @Query(value = "select count(*) from pragma_table_info('points_credit') "
            + "where name = 'savings_account_id'", nativeQuery = true)
    long batchesStillNameTheSavingsAccountTheyWereEarnedIn();

    /**
     * The savings accounts behind every batch that has no customer on it yet, each named once.
     *
     * <p>Native, and the only thing that reads the old column: it is not on the entity any more,
     * because a batch belongs to a customer now and a mapped column would be a second answer to who
     * owns these points. Who holds these accounts is not this module's to know — {@link
     * PointsOnStartUp} asks Accounts and comes back with the answer.
     */
    @Query(value = "select distinct savings_account_id from points_credit where customer_id is null",
            nativeQuery = true)
    List<Long> savingsAccountsBehindBatchesWithoutACustomer();

    /**
     * Gives every ownerless batch earned in one savings account to the customer who holds it.
     *
     * <p>Only the batches that have no customer, so a start after the first changes nothing, and a
     * batch already credited to somebody is never reassigned.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "update points_credit set customer_id = :customerId "
            + "where customer_id is null and savings_account_id = :savingsAccountId",
            nativeQuery = true)
    int giveBatchesEarnedIn(@Param("savingsAccountId") long savingsAccountId,
                            @Param("customerId") long customerId);

    /**
     * Takes the old column away, once nothing in it is needed any more.
     *
     * <p>Dropped rather than left behind, and this is the one migration in the application that has
     * to do more than fill a column in. The column was written {@code not null}, so a batch credited
     * from now on — which names a customer and no account — could not be inserted beside it at all.
     * A column that cannot be written and is read by nothing is not history, it is a table that
     * refuses to be added to.
     */
    @Transactional
    @Modifying
    @Query(value = "alter table points_credit drop column savings_account_id", nativeQuery = true)
    void stopNamingTheSavingsAccountBatchesWereEarnedIn();

    /**
     * One batch as this query reports it: what earned it, why, and how many points that was worth.
     */
    interface EarnedPoints {

        long getSourceReferenceId();

        PointsReason getReason();

        long getPoints();
    }
}
