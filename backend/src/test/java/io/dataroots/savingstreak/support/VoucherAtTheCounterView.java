package io.dataroots.savingstreak.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A voucher as the counter surface reports it: what it is for, whose it is, whether to hand it
 * over, and what happened at the till if anything has.
 *
 * <p>The state travels as text rather than as an enum this test package declares a copy of. A test
 * asserting {@code "USED"} is asserting the string the API actually sends, which is the thing the
 * counter screen reads; a copied enum would go on compiling after the API stopped sending what it
 * names.
 */
public record VoucherAtTheCounterView(String voucherCode, String code, String title,
                                      long pointsSpent, long customerId, String customerName,
                                      Instant claimedAt, String state, boolean good,
                                      Instant usedAt, String usedByCounter, LocalDate expiresOn,
                                      Instant cancelledAt, String cancelledBecause,
                                      List<BundleMemberView> contents) {
}
