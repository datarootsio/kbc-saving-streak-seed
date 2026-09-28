package io.dataroots.savingstreak.challenges;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: the rest of the application goes through {@link ChallengesService}. */
interface GoalSeenFinishedRepository extends JpaRepository<GoalSeenFinished, Long> {

    /**
     * Every goal this enrolment has ever seen finished, counting and not counting alike.
     *
     * <p>The whole set in one read rather than a question per goal, because the reading needs all of
     * it anyway: which goals are already written down decides what this pass writes, and how many of
     * them count is the reading itself. Two uses, one query.
     *
     * <p>By the enrolment and never by the customer, because that is the scope a sighting means
     * anything in: a customer who has been round a repeatable goals challenge twice has two
     * enrolments, and the goals that filled the first must not arrive already filled in the second.
     */
    List<GoalSeenFinished> findByEnrolmentId(long enrolmentId);

    /**
     * Whether the index that makes one sighting per enrolment per goal a rule the database keeps —
     * rather than one this application merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, so that the ordinary start — every
     * start after the first — reads one row and does nothing. {@code create unique index if not
     * exists} would be idempotent on its own; asking first is what lets the start-up step say
     * whether it did anything, which is the difference between a log a reviewer can trust and one
     * that always claims the same thing. The same shape as the trophy case's own guarantee beside
     * it, and as the Loyalty and Goals modules' before that.
     */
    @Query(value = "select count(*) from pragma_index_list('goal_seen_finished') "
            + "where name = 'one_sighting_per_enrolment_per_goal'", nativeQuery = true)
    long aGoalIsAlreadySeenOncePerEnrolment();

    /**
     * Makes it so, over the enrolment and the goal.
     *
     * <p>Created here rather than declared on the entity because the entity cannot: this schema is
     * generated from the entity model against SQLite, and that dialect writes a composite unique
     * clause nowhere — declared as a constraint, the table is simply created without it. A unique
     * index is a statement SQLite accepts, so this is where the guarantee comes from.
     *
     * <p>The reading also checks in Java, and would write nothing twice on its own; the check and
     * the guarantee are different things. Two reads of the challenges tab arriving at the same
     * instant would both find the goal unseen and both write a sighting, and the reading is a count
     * of rows — so without this, looking twice at once is a goal counted twice.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting against
     * one file cannot race each other into a failure. It carries its own transaction because its one
     * caller runs before the application has a transaction, a request, or a web server.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_sighting_per_enrolment_per_goal "
            + "on goal_seen_finished (enrolment_id, goal_id)", nativeQuery = true)
    void makeAGoalSeenOncePerEnrolment();
}
