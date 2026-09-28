package io.dataroots.savingstreak.goals;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The moves that made up an account's allocations, and the only other store this module has.
 *
 * <p>One read, scoped by the savings account, and every figure this module derives is worked out
 * from it in Java: what each goal holds, what the account has allocated altogether, and the history
 * of one goal. A {@code sum} asked of the database would be tempting and is not used, for the reason
 * {@code DepositsService.moneyBalanceOf} gives about the balance — SQLite has no decimal type and
 * keeps an amount as a float, so a sum it worked out itself would accumulate in floating point and
 * lose the cents somebody typed. Adding them back as decimals keeps them.
 *
 * <p>Scoped by the account without exception, like the goals themselves: a read by goal identifier
 * alone would answer about somebody else's account whenever a caller guessed a number.
 *
 * <p>Newest first, because that is the order a history is read in and the only order anything here
 * asks for. Identifier descending rather than moment descending: two moves made in the same second
 * are ordered by which was written, which is the order they happened in, and a moment cannot say.
 */
interface MoneyMovedBetweenGoalsRepository extends JpaRepository<MoneyMovedBetweenGoals, Long> {

    List<MoneyMovedBetweenGoals> findBySavingsAccountIdOrderByIdDesc(long savingsAccountId);
}
