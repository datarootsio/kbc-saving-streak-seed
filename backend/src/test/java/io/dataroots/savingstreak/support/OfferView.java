package io.dataroots.savingstreak.support;

import java.time.LocalDate;
import java.util.List;

/**
 * One offer as the administration API reports it: everything {@link RewardView} carries, plus the
 * state it is in and what its vouchers are stamped with.
 *
 * <p>A second view rather than more fields on the reward's, because the two are different
 * endpoints answering different people — and because the reward's is the shape a test pins the
 * customer's catalogue to, which is the one thing about this feature that must not move.
 *
 * <p>The shelf life is boxed, because null is the answer for every offer that never expires its
 * vouchers — which is all four seeded ones — and a primitive would read that as nought days.
 *
 * <p>The state is text rather than an enum the test imports. A test asserting {@code "DRAFT"} is
 * asserting what actually goes over the wire, which is what a page reads; sharing the backend's
 * enum would let a value be renamed on both sides at once with every test still passing.
 *
 * <p>The three eligibility thresholds are boxed for the same reason as the shelf life: null is
 * the answer for an offer that asks no such thing, which is all four seeded ones, and a primitive
 * would read that as a rule of nought. They are read back exactly as somebody set them and
 * without any verdict about whether a particular customer meets them — that is the customer's
 * read, and {@link RewardForACustomerView} is where a test asks it.
 *
 * <p>The two caps are boxed for the shelf life's reason: null is the answer for an offer nobody
 * limited, which is all four seeded ones, and a primitive would read that as a cap of nought —
 * which is the one value the backend refuses. They are what somebody set, read back, and there
 * is nothing here about how many anybody has had: that is the customer's read and
 * {@link RewardForACustomerView} is where a test asks it.
 *
 * <p>The stock is boxed too, and it is the total somebody set rather than how many are left:
 * the back office reads back what was typed into the box. How many are left is the customer's
 * read, and {@link RewardForACustomerView} is where a test asks that.
 *
 * <p>The window is two days, either of them null, read back as the administration screen reads
 * them: what somebody set, rather than whether the offer is open today. Whether it is open is the
 * customer's read, and {@link RewardForACustomerView} is where a test asks that.
 * <p>The promotion is all three of its parts, boxed for the same reason the shelf life is: an
 * offer at its ordinary price has none of them, which is every offer this application seeds, and
 * a primitive price would read that as nought points. They are what somebody set rather than
 * whether the sale is on today — the administration screen reports no verdict, and where a test
 * asks whether the price has actually moved is {@link RewardForACustomerView}.
 */
public record OfferView(String code, String title, String description, long costInPoints,
                        String voucherPrefix, String state, LocalDate opensOn,
                        LocalDate closesOn, Integer voucherValidForDays,
                        Integer minimumStreakWeeks, String requiresBadge,
                        Long minimumLifetimePointsEarned,
                        Integer maxPerCustomer, Integer maxPerCustomerPerWeek,
                        Integer stock,
                        Long discountedCostInPoints, LocalDate discountOpensOn,
                        LocalDate discountClosesOn, List<BundleMemberView> contents) {
}
