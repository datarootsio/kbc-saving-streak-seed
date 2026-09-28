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
 * <p><strong>Every query in here filters on the state and on the clock together</strong>, and
 * none of them is allowed to filter on only one. A hold whose moment has gone by is over whether
 * or not the sweep has written that down, so a query that asked only for {@code HELD} would count
 * stock as unavailable that came back hours ago, and a query that asked only about the moment
 * would count a hold somebody gave up on the morning they took it. The pair is the definition of
 * "live", it is the same pair {@link RewardHold#isLiveAt} applies to a row in hand, and the two
 * have to agree exactly — which is why both read the deadline as strictly after the moment.
 */
interface RewardHoldRepository extends JpaRepository<RewardHold, Long> {

    /**
     * How many of each offer are being held by anybody, right now, in one grouped query for a
     * whole reading of the catalogue.
     *
     * <p>Grouped and taken once, for the reason the claims are counted that way beside it: the
     * rewards page reads every offer at once, and a count per card would be a query per card and
     * two of them could disagree about a hold taken while the page was being drawn. Offers nobody
     * is holding produce no row at all rather than a nought, which is nearly every offer nearly
     * always, and {@link HowManyAreHeld#noneOf} is what stands in for them.
     *
     * <p>It counts everybody's holds including the reader's own, because stock does not care
     * whose a hold is. The two places that must not let a customer's own hold lock them out of
     * their own hold say so where they say it, and say why.
     */
    @Query("select new io.dataroots.savingstreak.rewards.HowManyAreHeld("
            + "hold.offerCode, count(hold)) "
            + "from RewardHold hold "
            + "where hold.state = :held and hold.lapsesAt > :now "
            + "group by hold.offerCode")
    List<HowManyAreHeld> howManyOfEachAreHeld(@Param("held") HoldState held,
                                              @Param("now") Instant now);

    /**
     * Everything one customer is holding right now, as rows.
     *
     * <p>Rows rather than a count, because the card has to say <em>when</em> their hold runs out
     * and a counting query cannot answer that. It is a handful of rows at most — a hold lasts
     * three days — so this is cheaper than the grouped count above and is taken once for the
     * whole reading for the same reason.
     */
    @Query("select hold from RewardHold hold "
            + "where hold.customerId = :customerId and hold.state = :held and hold.lapsesAt > :now "
            + "order by hold.lapsesAt asc, hold.id asc")
    List<RewardHold> everythingOneCustomerIsHolding(@Param("customerId") long customerId,
                                                    @Param("held") HoldState held,
                                                    @Param("now") Instant now);

    /**
     * The one live hold a customer has on one offer, or nothing.
     *
     * <p>An {@link Optional} and not a list, and that is an assertion rather than a convenience:
     * one live hold per offer per customer is the rule, taking a second is refused, and a hold's
     * deadline only ever moves forward — so two live ones cannot exist and a query that quietly
     * returned the first of several would hide the day they did. If this ever throws, the rule
     * has been broken somewhere and that is worth finding out about.
     */
    @Query("select hold from RewardHold hold "
            + "where hold.customerId = :customerId and hold.offerCode = :offerCode "
            + "and hold.state = :held and hold.lapsesAt > :now")
    Optional<RewardHold> theLiveHoldOneCustomerHasOn(@Param("customerId") long customerId,
                                                     @Param("offerCode") String offerCode,
                                                     @Param("held") HoldState held,
                                                     @Param("now") Instant now);

    /**
     * The last hold this customer had on this offer, whatever became of it.
     *
     * <p>Only ever read in order to say something true to somebody who asked to convert or give
     * up a hold they do not have: whether they gave it up, whether they already claimed it, or
     * whether it ran out — and if so, when. The kind of refusal is the same in every case, because
     * what is missing is the same thing; the sentence is not, because "you gave that one up" and
     * "it ran out on Tuesday" send somebody to do quite different things next.
     */
    Optional<RewardHold> findFirstByCustomerIdAndOfferCodeOrderByTakenAtDescIdDesc(
            long customerId, String offerCode);

    /**
     * Every hold whose moment has gone by and which nothing has written down yet — the sweep's
     * whole input.
     *
     * <p>At or before the moment, which is the exact complement of the strictly-after every other
     * query here reads: a hold is live up to its moment and swept from it. Were the two to
     * disagree by an instant there would be a hold that was neither live nor sweepable, and a
     * customer whose thing had vanished into the gap.
     *
     * <p>Oldest deadline first, and the identifier to break ties, so that a night's work happens
     * in a defined order and the log of it reads in the order things actually ran out. It matters
     * more than it looks: the step that promotes whoever is next in line runs immediately after
     * this one and hands out whatever came back, so the order these are written in is the order
     * the queue behind them moves.
     */
    @Query("select hold from RewardHold hold "
            + "where hold.state = :held and hold.lapsesAt <= :now "
            + "order by hold.lapsesAt asc, hold.id asc")
    List<RewardHold> holdsPastTheirMoment(@Param("held") HoldState held,
                                          @Param("now") Instant now);

    /**
     * How many of one offer are being held right now, for the one question that is about a single
     * offer rather than about a whole reading: whether an administrator may lower its stock.
     *
     * <p>Its own count rather than the grouped query above filtered down, matching
     * {@code RedemptionRepository.countByReward}, which survives for exactly the same caller and
     * exactly the same reason: the administration path is about one code and has no reading to
     * take a figure out of.
     */
    @Query("select count(hold) from RewardHold hold "
            + "where hold.offerCode = :offerCode and hold.state = :held and hold.lapsesAt > :now")
    long howManyAreHeldOf(@Param("offerCode") String offerCode, @Param("held") HoldState held,
                          @Param("now") Instant now);
}
