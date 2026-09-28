package io.dataroots.savingstreak.accounts;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private for the same reason as {@link CustomerRepository}. */
interface RecurringBillRepository extends JpaRepository<RecurringBill, Long> {

    /**
     * The bills in one state on one account, in the order they were declared.
     *
     * <p>By identifier rather than by day of the month, because the order a customer wrote them in
     * is the order they recognise their own list in, and a list that re-sorted itself when somebody
     * moved a collection date would be a list that moved under them. Which day each goes out on is
     * on the row for whoever wants to sort by it.
     *
     * <p>A state rather than two methods, so that "what is standing" and "what did I end" are one
     * question asked twice rather than two queries that could drift.
     */
    List<RecurringBill> findByCurrentAccountIdAndStateInOrderByIdAsc(long currentAccountId,
                                                                     List<BillState> states);

    /**
     * Every bill in one state, on every account, in the order they were declared.
     *
     * <p>For the nightly run, which has nobody's account in front of it and has to walk them all.
     * The order is the order the declarations were made, so that two bills falling due on one
     * morning are presented in the order the customer wrote them rather than in whatever order the
     * database read them — deterministic, reconstructable from the log, and needing no priority
     * field anybody would have to maintain.
     *
     * <p>A state rather than "the standing ones", so that the one place which decides what standing
     * means stays {@link BillState#theOnesStillStanding} — a third value added later is then counted
     * once rather than in every query that mentions it.
     *
     * <p>All of them on every call. With the seeded households that is a handful of rows, and with a
     * real population a run would want them a page at a time — which is a change to make when there
     * is a population, not before. What bounds it meanwhile is the cap on how many bills one account
     * may carry.
     */
    List<RecurringBill> findByStateInOrderByIdAsc(List<BillState> states);

    /**
     * One bill, and only if it is on the account that was named.
     *
     * <p>Scoped by the account the same way a saving rule is scoped by its savings account: a bill
     * identifier somebody guessed answers as a bill that is not there rather than with somebody
     * else's rent. It is the whole of what this application can say about whose a bill is, because
     * there is no authentication to ask.
     */
    Optional<RecurringBill> findByIdAndCurrentAccountId(long billId, long currentAccountId);

    /**
     * How many bills are standing on this account, for the cap.
     *
     * <p>Counted rather than fetched, because the cap is a number and loading twenty rows to call
     * {@code size()} on them would be reading a list to ask its length.
     */
    long countByCurrentAccountIdAndStateIn(long currentAccountId, List<BillState> states);
}
