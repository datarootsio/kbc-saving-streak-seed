package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A shared pot as the API reports one: what it is called, what it holds, which savings account holds
 * that money, when it was opened, and who is in it with what role.
 *
 * <p>One shape for a pot just opened, a pot read back on its own and a pot in somebody's list —
 * which is the API's own promise, and reading all three as one record is what would fail if it ever
 * stopped being true. Shared by every test that reads a pot back, so that none of them can drift
 * into disagreeing about the shape of the answer.
 *
 * <p>The money is the pot's and the points are never the pot's: a euro paid in earns points for the
 * person who paid it, so there is no points figure here and that absence is the feature.
 *
 * <p>{@code closedAt} is null for as long as the pot is open. A closed pot is still read back
 * through this same record, still in every member's list of pots, and still carries its balance —
 * which is nought — so a test asserting "it is there and it is marked closed" reads both off one
 * answer.
 */
public record SharedPotView(Long id, String name, BigDecimal moneyBalance, Long savingsAccountId,
                            Instant openedAt, Instant closedAt, List<PotMemberView> members) {
}
