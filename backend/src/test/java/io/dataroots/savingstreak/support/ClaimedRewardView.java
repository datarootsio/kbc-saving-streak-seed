package io.dataroots.savingstreak.support;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A claimed reward as the API reports it: what it was, what it cost, the voucher, when — and where
 * that voucher has got to since.
 *
 * <p>The day it runs out is a {@link LocalDate} and not an instant, because that is what the API
 * sends: the backend decides which day a voucher's deadline falls on, in the zone it counts
 * calendars in, and a test that read it as a moment would be re-deciding that in whatever zone
 * the machine running the suite is set to — which is the mistake the field exists to prevent.
 * Null when the offer set no shelf life, which is every one of the four seeded offers.
 *
 * <p>The reason a voucher was cancelled is a plain string and is asserted as the words whoever
 * ran the scheme typed, because that is what the API sends and what the customer's own page shows
 * them. Null for every voucher nobody cancelled, which is almost all of them.
 *
 * <p>The lifecycle fields are appended rather than woven in, so that every test already
 * reading this record by name goes on reading the same fields. A record's components are named
 * here, not positional, as far as Jackson is concerned, so the ones an older test never mentions
 * simply arrive and sit there.
 */
public record ClaimedRewardView(Long id, String code, String title, long pointsSpent, String voucherCode,
                                Instant claimedAt, String state, Instant usedAt,
                                String usedByCounter, LocalDate expiresOn, Instant cancelledAt,
                                String cancelledBecause) {
}
