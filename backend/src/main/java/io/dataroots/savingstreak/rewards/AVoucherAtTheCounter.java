package io.dataroots.savingstreak.rewards;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A voucher as somebody at a counter reads it: what it is for, whose it is, whether it is good, and
 * — once it has been — when it was handed over and by whom.
 *
 * <p>Separate from {@link ClaimedReward} rather than the same record read twice, because the two
 * answer different questions for different people. A claim is a customer's own record of something
 * they bought and it is theirs by construction, so it says nothing about who holds it; this is
 * somebody else looking a code up, and who is entitled to the thing is the first fact they need.
 * Merging them would put a customer identifier on a customer's own history page, where it means
 * nothing, in order to save a record.
 *
 * <p>The customer is an identifier and not a name. Who that is belongs to Accounts, and this module
 * asking it would be a dependency taken on for the sake of one string on one screen; the web layer
 * already puts a name beside an identifier this way, which is exactly what {@code CustomerController}
 * does when it asks Accounts for the accounts a customer holds and hangs a balance off each.
 *
 * <p>There is no {@code good} field, because {@link VoucherState#isGoodAtACounter()} is that
 * question and answering it twice is how two answers start disagreeing. The state travels and the
 * caller asks it.
 *
 * <p>{@code expiresOn} is the day the voucher runs out, and null when it never does. It is here as
 * well as on the customer's own reading because the two people at a counter are having one
 * conversation: somebody holding a code that ran out last week is owed the date out loud, and a
 * till that could only say "expired" would leave the customer with nothing to check against their
 * own screen. A voucher still in date carries it too, which is the same date their screen has been
 * showing them all along.
 *
 * <p>{@code cancelledAt} and {@code cancelledBecause} are here for the same reason and are null
 * for anything but a revoked voucher. Somebody at a counter turning a customer away is in the
 * hardest conversation this surface has — the code is real, the person believes it is good, and
 * nothing they did caused it — and the words whoever ran the scheme typed are the only thing that
 * turns "this one has been cancelled" into something the two of them can talk about. The points
 * having gone back is not said here, because it is not the till's to promise; it is on the
 * customer's own list, beside the claim.
 */
public record AVoucherAtTheCounter(String voucherCode, String rewardCode, String title,
                                   long pointsSpent, long customerId, Instant claimedAt,
                                   VoucherState state, Instant usedAt, String usedByCounter,
                                   LocalDate expiresOn, Instant cancelledAt,
                                   String cancelledBecause) {
}
