package io.dataroots.savingstreak.goals;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * The goals on an account, and the only store this module has.
 *
 * <p>Every read is scoped by the savings account, without exception, and that is the whole of how
 * one account's goals stay invisible to another. A read by goal identifier alone would answer for
 * somebody else's account whenever a caller guessed a number, and "the caller would not do that" is
 * not a rule — this is.
 *
 * <p>Package-private, like the row it reads: what this module keeps, and how, is nobody else's
 * business.
 */
interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    /**
     * The account's goals in a given state, in rank order.
     *
     * <p>Rank ascending is the order of importance itself — 1 is the most important — so the live
     * goals come back in the order the plan spends money in rather than in the order they were
     * created. Abandoned goals hold no rank, so this read is ordered by identifier for them instead;
     * {@link #findBySavingsAccountIdAndStateOrderByIdAsc} is that read, and asking for either one by
     * name is what keeps a null rank out of an {@code order by}.
     */
    List<SavingsGoal> findBySavingsAccountIdAndStateOrderByRankAsc(long savingsAccountId, GoalState state);

    /** The same in the order they were opened, for goals that hold no rank to be ordered by. */
    List<SavingsGoal> findBySavingsAccountIdAndStateOrderByIdAsc(long savingsAccountId, GoalState state);

    /**
     * Every goal on the account whatever state it is in, for the one question that spans both
     * families: what each end of a move in the ledger was called. A move out of a goal that has
     * since been given up on still has to read as the thing it was, so the live goals alone would
     * answer it with a blank.
     */
    List<SavingsGoal> findBySavingsAccountId(long savingsAccountId);

    /**
     * One goal on one account, whatever state it is in, or nothing at all.
     *
     * <p>Both halves of the key, always. A goal named by a customer who holds a different account is
     * a goal that is not there as far as that customer is concerned, and answering it with the row
     * would be answering a question about somebody else's saving.
     */
    Optional<SavingsGoal> findByIdAndSavingsAccountId(long id, long savingsAccountId);

    /**
     * Whether the index that makes the order of importance strict — one goal per place per account —
     * a rule the database keeps rather than one this application merely intends, is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, so that the ordinary start — every start
     * after the first — reads one row and does nothing. {@code create unique index if not exists} is
     * idempotent on its own; asking first is what lets the start-up step say whether it did anything,
     * which is the difference between a log a reviewer can trust and one that always claims the same.
     */
    @Query(value = "select count(*) from pragma_index_list('savings_goal') "
            + "where name = 'one_goal_per_place_on_a_savings_account'", nativeQuery = true)
    long theOrderIsAlreadyStrict();

    /**
     * Makes it so, over the account and the place in its order.
     *
     * <p>Created here rather than declared on the entity because the entity cannot: this schema is
     * generated from the entity model against SQLite, and that dialect writes a composite unique
     * clause nowhere — {@code LoyaltyBonusPaid} says at length what the generated DDL does instead. A
     * unique index is a statement SQLite accepts, so this is where the guarantee comes from.
     *
     * <p><strong>A goal that has left the order carries no place and is not covered.</strong> Its rank
     * is null, and SQLite counts nulls as distinct from one another in a unique index, so an account
     * can have any number of abandoned goals without any of them colliding. That is the right answer
     * rather than a lucky one: they are not competing, so there is nothing to be unique about.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting against
     * one file cannot race each other into a failure.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_goal_per_place_on_a_savings_account "
            + "on savings_goal (savings_account_id, goal_rank)", nativeQuery = true)
    void makeTheOrderStrict();
}
