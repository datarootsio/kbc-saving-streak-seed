package io.dataroots.savingstreak.challenges;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: the rest of the application goes through {@link ChallengesService}. */
interface ChallengeAwardRepository extends JpaRepository<ChallengeAward, Long> {

    /**
     * Everything the customer has ever won, newest first — the trophy case, in one read.
     *
     * <p>By the moment and then by identifier, because a single deposit clearing three rungs wins
     * all three at the same instant and a list ordered by moment alone would put them in whatever
     * order the database felt like. The identifier breaks the tie in the order they were written,
     * which is the order they were climbed, so gold appears above silver appears above bronze.
     */
    List<ChallengeAward> findByCustomerIdOrderByAwardedAtDescIdDesc(long customerId);

    /**
     * Which rungs of which of these enrolments have already been awarded — the pairs and nothing
     * else, because that is the whole of what a judging pass has to know in order not to pay twice.
     *
     * <p>Every enrolment at once rather than a question per enrolment, so that judging somebody with
     * six challenges running is one query and not six. The rows it would otherwise read one at a
     * time are exactly the rows it is about to decide against.
     *
     * <p>The reading and the points are deliberately not asked for. They are the audit trail and the
     * pass has no use for them; what a customer has been paid altogether is read out of the points
     * ledger, which is where their points live.
     */
    @Query("select award.enrolmentId as enrolmentId, award.rung as rung, "
            + "award.awardedAt as awardedAt "
            + "from ChallengeAward award where award.enrolmentId in :enrolmentIds")
    List<RungAlreadyAwarded> rungsAlreadyAwardedFor(
            @Param("enrolmentIds") Collection<Long> enrolmentIds);

    /**
     * Whether the index that makes one award per enrolment per rung a rule the database keeps —
     * rather than one this application merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, so that the ordinary start — every
     * start after the first — reads one row and does nothing. {@code create unique index if not
     * exists} would be idempotent on its own; asking first is what lets the start-up step say
     * whether it did anything, which is the difference between a log a reviewer can trust and one
     * that always claims the same thing. The same shape as the Loyalty module's own guarantee.
     */
    @Query(value = "select count(*) from pragma_index_list('challenge_award') "
            + "where name = 'one_award_per_enrolment_per_rung'", nativeQuery = true)
    long theTrophyCaseIsAlreadyUniquePerRung();

    /**
     * Makes it so, over the enrolment and the rung.
     *
     * <p>Created here rather than declared on the entity because the entity cannot: this schema is
     * generated from the entity model against SQLite, and that dialect writes a composite unique
     * clause nowhere — declared as a constraint the table is simply created without it. A unique
     * index is a statement SQLite accepts, so this is where the guarantee comes from.
     *
     * <p>The pass also checks in Java, and would pay nothing twice on its own; the check and the
     * guarantee are different things. Two reads of the challenges tab arriving at the same instant
     * would both see an empty set of awarded rungs, and only a rule the database keeps stops both of
     * them from minting a badge.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting against
     * one file cannot race each other into a failure. It carries its own transaction because its one
     * caller runs before the application has a transaction, a request, or a web server.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_award_per_enrolment_per_rung "
            + "on challenge_award (enrolment_id, rung)", nativeQuery = true)
    void makeTheTrophyCaseUniquePerRung();

    /** One rung of one enrolment that has already been won, as the query above reports it. */
    interface RungAlreadyAwarded {

        long getEnrolmentId();

        Rung getRung();

        /**
         * When it was won, which the judging pass has no use for and a card does: a ladder says
         * which rungs are lit and the date is what turns a lit rung into something that happened.
         */
        Instant getAwardedAt();
    }
}
