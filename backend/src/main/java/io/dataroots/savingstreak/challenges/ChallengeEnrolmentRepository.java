package io.dataroots.savingstreak.challenges;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private: the rest of the application goes through {@link ChallengesService}. */
interface ChallengeEnrolmentRepository extends JpaRepository<ChallengeEnrolment, Long> {

    /**
     * Every enrolment the customer has ever taken, newest first.
     *
     * <p>All of them rather than only the live ones, because the card has to be able to say "you
     * left this" as well as "you are in this", and because a repeatable challenge a customer has
     * been round twice has two rows and the newest is the one they are standing in. Newest first is
     * what makes picking that one a matter of taking the first per code.
     */
    List<ChallengeEnrolment> findByCustomerIdOrderByIdDesc(long customerId);

    /**
     * The customer's latest enrolment in one challenge, whatever state it is in, or nothing if they
     * have never taken it on.
     *
     * <p>The row every decision about enrolling or leaving is made against: whether they are already
     * in it, and whether there is anything to leave.
     */
    Optional<ChallengeEnrolment> findFirstByCustomerIdAndChallengeCodeOrderByIdDesc(
            long customerId, String challengeCode);

    /**
     * Everybody who holds an enrolment in that state, each of them named once — which is to say, the
     * customers a nightly sweep has anything at all to do for.
     *
     * <p>The identifiers alone rather than the rows. The sweep judges a <em>customer</em>, because
     * one judging pass reads every live enrolment that person has and takes one mark for all of
     * them; handing it enrolments would have it judge somebody with three challenges running three
     * times over, to the same effect and at three times the cost. Distinct is therefore the whole
     * point of the query rather than a tidiness, and it is asked of the database because that is
     * where the duplicates are.
     *
     * <p>Everybody at once rather than a page at a time. This is a training application whose
     * database holds two seeded households and whatever a session adds, so the list is short and a
     * paged sweep would be machinery for a problem nobody here has. A bank with millions of
     * enrolments would read this in batches, and that is the line to change when it does.
     *
     * <p>In identifier order, which is the order they enrolled. The sweep is a loop over people and
     * the order it takes them in makes no difference to any of them — but an ordered one makes two
     * runs of the same night produce two logs that can be read side by side, and an unordered query
     * leaves that to whatever the database felt like.
     */
    @Query("select distinct enrolment.customerId from ChallengeEnrolment enrolment "
            + "where enrolment.state = :state order by enrolment.customerId")
    List<Long> everybodyHoldingAnEnrolment(@Param("state") EnrolmentState state);
}
