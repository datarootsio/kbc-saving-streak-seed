package io.dataroots.savingstreak.rewards;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Package-private: the rest of the application goes through {@link RewardsService}.
 *
 * <p><strong>Not one query in here reads a clock, and that is the difference from
 * {@link RewardHoldRepository} worth stating out loud.</strong> Every query over holds filters
 * on the state <em>and</em> the moment, because a hold is over the instant the clock passes the
 * deadline written on it whether or not the sweep has said so. A place in a queue has no
 * deadline at all: somebody who joined in March is still waiting in June, in the same position.
 * So "waiting" here is one column, and a query that took a moment would be inventing an expiry
 * nobody was told about. The argument is on {@link WaitingState}.
 *
 * <p><strong>Every ordering in here is {@code joinedAt} then the identifier, and it is always
 * both.</strong> The order people joined in is the whole of what a queue is, and two people who
 * joined in the same millisecond — which a wound-forward clock makes ordinary rather than
 * exotic, because a trainer builds a queue in one afternoon — have to come out in a defined
 * order all the same. The identifier is what breaks the tie, it rises with the insert, and it is
 * the same tie-break {@code RewardHoldRepository.holdsPastTheirMoment} uses for the same reason.
 * The position a customer is shown and the order the sweep promotes in are counted over this one
 * ordering, so the two cannot disagree.
 */
interface WaitingListRepository extends JpaRepository<WaitingListPlace, Long> {

    /**
     * The one live place a customer has in the queue for one offer, or nothing.
     *
     * <p>An {@link Optional} and not a list, and that is an assertion rather than a convenience,
     * exactly as it is on {@code RewardHoldRepository.theLiveHoldOneCustomerHasOn}: joining
     * twice is refused, so two waiting rows for one person and one offer cannot exist, and a
     * query that quietly returned the first of several would hide the day they did.
     */
    @Query("select place from WaitingListPlace place "
            + "where place.customerId = :customerId and place.offerCode = :offerCode "
            + "and place.state = :waiting")
    Optional<WaitingListPlace> thePlaceOneCustomerHasIn(@Param("customerId") long customerId,
                                                        @Param("offerCode") String offerCode,
                                                        @Param("waiting") WaitingState waiting);

    /**
     * Every queue this customer is in, oldest joining first — a handful of rows at most, read
     * once for a whole reading of the catalogue.
     *
     * <p>Rows rather than a count, because the card has to say <em>where</em> they stand and a
     * counting query cannot answer that. It is shaped like
     * {@code RewardHoldRepository.everythingOneCustomerIsHolding} and taken in the same place
     * for the same reason: one pass over this person's own rows before the first card is drawn,
     * so that no two cards in one reading disagree about what they are waiting for.
     */
    @Query("select place from WaitingListPlace place "
            + "where place.customerId = :customerId and place.state = :waiting "
            + "order by place.joinedAt asc, place.id asc")
    List<WaitingListPlace> everyQueueOneCustomerIsIn(@Param("customerId") long customerId,
                                                     @Param("waiting") WaitingState waiting);

    /**
     * How many people are ahead of one place in its own queue, which is its position less one.
     *
     * <p>A count rather than the whole queue read and indexed, because a customer's own card
     * wants one number and the queue behind them may be long. Ahead means joined earlier, or
     * joined in the same millisecond and was written first — the same two-part ordering every
     * other query here reads, spelled out as a comparison because a count cannot be ordered.
     * The two have to agree exactly: a position counted one way and a promotion ordered the
     * other would tell somebody they were first and then hand the thing to somebody else.
     */
    @Query("select count(place) from WaitingListPlace place "
            + "where place.offerCode = :offerCode and place.state = :waiting "
            + "and (place.joinedAt < :joinedAt "
            + "or (place.joinedAt = :joinedAt and place.id < :id))")
    long howManyAreAheadOf(@Param("offerCode") String offerCode,
                           @Param("waiting") WaitingState waiting,
                           @Param("joinedAt") Instant joinedAt, @Param("id") long id);

    /**
     * The whole queue for one offer, in the order people joined — what an administrator reads,
     * and what the sweep promotes out of.
     *
     * <p>The whole of it rather than a page, for the reason the administration catalogue is not
     * paged either: this is a training application, a queue is a handful of people, and paging
     * would be machinery for a problem that does not exist here. The sweep reads the same list
     * and stops when the stock runs out, which is what makes "who is next" and "who was
     * promoted" answers to the same question.
     */
    @Query("select place from WaitingListPlace place "
            + "where place.offerCode = :offerCode and place.state = :waiting "
            + "order by place.joinedAt asc, place.id asc")
    List<WaitingListPlace> theQueueFor(@Param("offerCode") String offerCode,
                                       @Param("waiting") WaitingState waiting);

    /**
     * The last place this customer had in this queue, whatever became of it.
     *
     * <p>Only ever read in order to say something true to somebody who asked to leave a queue
     * they are not in: whether their turn already came, whether they left it themselves, or
     * whether they were never in it. The kind of refusal is the same in every case, because
     * what is missing is the same thing; the sentence is not, because "your turn came and one
     * is being held for you" and "you already left" send somebody to do quite different things
     * next. It is the same method, for the same reason, that
     * {@code RewardHoldRepository.findFirstByCustomerIdAndOfferCodeOrderByTakenAtDescIdDesc} is.
     */
    Optional<WaitingListPlace> findFirstByCustomerIdAndOfferCodeOrderByJoinedAtDescIdDesc(
            long customerId, String offerCode);

    /**
     * Every offer somebody is waiting for — the sweep's whole input, and usually nothing at all.
     *
     * <p>Codes rather than rows, because the step that follows has to read each offer, count
     * what is left of it and only then look at who is in line; pulling every waiting row in the
     * database in order to group them here would be the same walk done twice. An empty answer is
     * the ordinary one — no offer in this application's own catalogue can ever run out — and it
     * costs the nightly sweep one {@code select} that comes back with nothing, which is the same
     * bargain the bundle fold already strikes on every reading of the catalogue.
     *
     * <p>Ordered by the code so that a night's promotions happen in a defined order and the log
     * of them reads the same way twice. Which offer is promoted first cannot matter — no two
     * offers share stock, and a bundle's members are drawn down by arithmetic rather than by a
     * queue — but a sweep whose output order depended on what the database felt like returning
     * is a sweep nobody can diff against last night's.
     */
    @Query("select distinct place.offerCode from WaitingListPlace place "
            + "where place.state = :waiting order by place.offerCode asc")
    List<String> everyOfferSomebodyIsWaitingFor(@Param("waiting") WaitingState waiting);
}
