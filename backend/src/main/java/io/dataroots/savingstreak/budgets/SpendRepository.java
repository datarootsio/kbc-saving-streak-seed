package io.dataroots.savingstreak.budgets;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: the rest of the application goes through {@link SpendsService}. */
interface SpendRepository extends JpaRepository<Spend, Long> {

    /**
     * One spend, found only through the account it is on.
     *
     * <p>Scoped by the account rather than found by identifier and checked afterwards, so that there
     * is no path through this module on which somebody else's spend is ever in hand. It is the same
     * shape a category and a bill are found through and it is the whole of what this application can
     * say about whose a spend is: there is no authentication to ask, so a guessed identifier answers
     * as a spend that is not there rather than as one that is not yours.
     */
    Optional<Spend> findByIdAndCurrentAccountId(long spendId, long currentAccountId);

    /**
     * The most recent spends on one account, newest first, and no more of them than were asked for.
     *
     * <p>Newest first because that is the question — "what have I spent lately" — and it is the
     * opposite of the categories' own list, which is in declaration order so that a customer
     * recognises the words they wrote. A spend is not recognised by its position; it is recognised
     * by being the thing that happened last.
     *
     * <p>The identifier breaks a tie on the moment. Two spends recorded inside the same millisecond
     * — which the seed and the simulator both do — would otherwise come back in whatever order
     * SQLite happened to store them, and a list that reorders itself between two reads is a list
     * nobody can test.
     *
     * <p>Limited by the caller rather than by this query, so that the bound is a named constant with
     * its reasoning beside it in {@link SpendsService} instead of a number buried in a method name.
     */
    List<Spend> findByCurrentAccountIdOrderByRecordedAtDescIdDesc(long currentAccountId,
                                                                   Limit howMany);

    /**
     * Every spend on any of these accounts, newest first: the spends as a ledger of everything that
     * moved reads them.
     *
     * <p>Across several accounts rather than one, because the ledger it feeds is a customer's and a
     * customer may hold more than one everyday account. Which accounts are theirs is Accounts'
     * answer and reaches this module already decided, exactly as it reaches the savings movements
     * and the bills.
     *
     * <p><strong>Unbounded, which is the opposite of the read above it and deliberate.</strong> The
     * recent-spends list is a page and is bounded because a page is a screen; this is a ledger, and
     * the deposits, the withdrawals and the bills merged beside it are each unbounded too. A bound
     * here would make a spend vanish out from under the rent it was recorded next to, which is a
     * worse answer than a long list.
     *
     * <p>Newest first, matching every other half of the ledger, so the merge in the web layer is a
     * merge of sorted lists rather than a sort of everything. The identifier breaks a tie on the
     * moment, for the reason it does below: two spends recorded inside one millisecond — which the
     * seed and the simulator both do — would otherwise reorder themselves between two reads.
     */
    List<Spend> findByCurrentAccountIdInOrderByRecordedAtDescIdDesc(
            Collection<Long> currentAccountIds);

    /**
     * Every spend on one account recorded inside a stretch of time, oldest first.
     *
     * <p>The other question entirely from the one above it, and the reason both exist. That one is
     * "what have I spent lately" and is a page, so it is newest first and bounded; this is "what did
     * this month cost" and is a sum, so it is a window and is not bounded at all — a month with
     * sixty spends in it has to report all sixty or the figure is wrong, and there is no honest way
     * to add up a limited list.
     *
     * <p><strong>Closed at the bottom and open at the top</strong>, which is the whole of what makes
     * a spend recorded in the last second of the last day of a month fall in that month and not the
     * next. {@link TheMonthAMomentFallsIn} owns the two moments and says why the top is open; this
     * signature is the half of it the database enforces, and the pair is the reason no spend is ever
     * counted in two months or in none.
     *
     * <p>Oldest first, which is the order the money actually left, so that the DEBUG line behind a
     * month's arithmetic reads as the month rather than as whatever order SQLite stored it in. The
     * identifier breaks a tie on the moment, for the reason it does above: two spends recorded
     * inside one millisecond — which the seed and the simulator both do — would otherwise reorder
     * themselves between two reads.
     */
    List<Spend> findByCurrentAccountIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAscIdAsc(
            long currentAccountId, Instant from, Instant before);
}
