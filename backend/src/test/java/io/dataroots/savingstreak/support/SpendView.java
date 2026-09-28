package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One spend as the API reports it: what its holder called it, what it cost, when it was recorded,
 * and what it was for.
 *
 * <p>Shared by every test that asks, for the same reason as {@link SpendingCategoryView} — copies of
 * a shape drift into disagreeing about it. It is the shape both the POST and the list answer with,
 * so a test that records a spend and then reads the list back is comparing one thing.
 *
 * <p>There is no balance in it, and that is the point rather than an omission: a test that wants to
 * know the money was taken reads the account's own balance back, which is where that figure lives.
 *
 * <p>{@code correctedAt} is null on a spend nobody has corrected, rather than a stand-in date, so a
 * test can assert the difference between "filed right the first time" and "filed again later"
 * exactly as a customer reading the ledger sees it.
 */
public record SpendView(long spendId, long currentAccountId, String name, BigDecimal amount,
                        Instant recordedAt, Instant correctedAt, List<SpendPartView> parts) {
}
