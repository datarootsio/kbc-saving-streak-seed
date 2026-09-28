package io.dataroots.savingstreak.rewards;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A reward that has been handed over, as the rest of the application sees it: what it was, what it
 * cost at the time, the voucher it produced, and when.
 *
 * <p>The stored record stays inside the module; this is a statement about something that happened.
 * There is no way to undo one, because redemption is final — the voucher exists the moment this does.
 *
 * <p><strong>The voucher now has a life, and this carries it.</strong> This record used to say
 * that a claim was the whole story and that nothing came after it, which was true of the
 * application and was the thing wrong with it: the scheme printed six characters and forgot about
 * them, so the same code was good at the same counter every day for ever. The state, the moment it
 * was handed over and the counter that took it travel with the claim because they are the half of
 * it the customer most needs to be told — a voucher they have already spent should read as spent
 * on their own page, with the day on it, rather than sitting there looking as good as the rest.
 * Finality is not being reversed: an issued voucher can become used, expired or cancelled, and
 * none of those can become anything else.
 *
 * <p>{@code usedAt} and {@code usedByCounter} are null while the state is anything but
 * {@code USED}, which is the only pair of nulls worth having here: they are a single fact — what
 * happened at the counter — and it either happened or it did not.
 *
 * <p><strong>{@code expiresOn} is the day the voucher runs out, and null when it never does.</strong>
 * A day rather than a moment, decided here rather than by whatever machine draws the screen, for
 * the reason argued at length on {@link VoucherShelfLife} and on {@code PointsExpiringNext} before
 * it: a customer is told a date they can act on, and a page that turned an instant into a date
 * would turn it into a date in its own zone. It travels on every claim rather than only on the
 * ones that have run out, because the day is what the customer needs <em>before</em> the deadline
 * — a voucher that only mentions its shelf life once it is too late is a voucher that never
 * mentioned it. Null means the offer set no shelf life, which is what all four seeded offers do
 * and therefore what almost every voucher in this application says.
 *
 * <p>The reward is named by its code and read out by its title, rather than carried as the
 * catalogue entry it came from. A claim is a thing that happened, and what it happened to is a name
 * somebody typed and a price somebody paid: handing back a live catalogue entry would tie a claim
 * made last month to whatever the catalogue says about that code today, which is the one thing the
 * stored cost already exists to prevent. The title travels beside the code because a voucher with
 * only {@code FAMILY_CINEMA_PACK} on it is a voucher nobody can read, and the page that shows it has
 * no business turning a code into English.
 *
 * <p><strong>{@code cancelledAt} and {@code cancelledBecause} are one fact between them, like the
 * pair above</strong>, and they are null for anything but a cancelled voucher. The reason travels
 * out of the module rather than being turned into a sentence here, because it is not the
 * application's sentence: it is what whoever ran the scheme typed when they revoked the claim,
 * and the customer is owed those words rather than a paraphrase of them. A cancellation with no
 * reason on the screen is a voucher that stopped working, which is the thing having a reason at
 * all is meant to prevent.
 *
 * <p>What came back is deliberately not a field of its own. A cancellation refunds exactly what
 * the claim cost, which is {@code pointsSpent}, already here and already the figure the customer
 * recognises; a second number beside it could only ever say the same thing or be wrong.
 */
public record ClaimedReward(Long id, String rewardCode, String title, long pointsSpent,
                            String voucherCode, Instant claimedAt, VoucherState state,
                            Instant usedAt, String usedByCounter, LocalDate expiresOn,
                            Instant cancelledAt, String cancelledBecause) {
}
