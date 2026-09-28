package io.dataroots.savingstreak.web;

import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.rewards.AnOfferAsItStands;
import io.dataroots.savingstreak.rewards.OfferState;

/**
 * One offer as the administration screen reads it: everything {@link RewardResponse} carries, plus
 * the two things a customer is never shown — which state it is in, and what its vouchers are
 * stamped with.
 *
 * <p>A second response beside the customer's rather than a wider version of it, because the two
 * are answers to different questions asked at different addresses. {@code /api/rewards} is the
 * catalogue and must keep saying exactly what it has always said; this is the catalogue's own
 * back office. One record serving both would mean either the customer's page receiving fields
 * about how the scheme is run, or this page having to go and read them from somewhere else.
 *
 * <p>The state travels as the enum's own name. The frontend switches on it to decide what a row
 * offers to do next — publish a draft, withdraw something live — and a string it had to know the
 * spellings of would be the same knowledge with nothing checking it.
 *
 * <p>The window travels as two days, either of them null, and without a verdict beside them. What
 * the administration screen shows is what somebody set, so that they can read it back and correct
 * it; whether the offer is open <em>today</em> is the customer's read, at the customer's address,
 * because it is an answer with a clock in it and this page is one somebody leaves open all
 * afternoon. Days rather than instants, for the reason a season's are: an offer's last day is
 * announced rather than timed, and an instant would be rendered a day out for anybody reading it
 * in another zone.
 *
 * <p>The three eligibility thresholds are here and on no customer-facing response either, and
 * the line is drawn in the same place. What an offer asks for is what somebody running the
 * scheme set, read back so that it can be corrected; what a <em>customer</em> needs is whether
 * they meet it and, if not, which rule stopped them — and that is a sentence, on their own read,
 * at their own address. A catalogue card carrying the raw thresholds would be the page doing the
 * comparison, which is the one thing the locked verdict exists to stop it doing. Null on each
 * means the offer asks no such thing, and the form shows that as the empty box it came from.
 *
 * <p>The stock is the total somebody set, read back so that they can correct it, and it is not
 * accompanied by how many are left — for the same reason the window is not accompanied by
 * whether the offer is open today. What is left has the claims table behind it and moves every
 * time anybody claims, and this page is one somebody leaves open all afternoon; a figure on it
 * would be one moment's answer going stale on the screen. The remaining count is on the
 * customer's read, where the person asking is the person it is about. Null means the offer never
 * runs out, which is what all four seeded offers say.
 *
 * <p>The promotion is all three of its parts, as they were set, and with no verdict beside them.
 * What this page shows is what somebody chose, so that they can read it back and correct it;
 * whether the sale is running <em>today</em> is the customer's read at the customer's address,
 * because it is an answer with a clock in it and this is a page somebody leaves open all
 * afternoon. All three are null together on an offer that is not on promotion, and the form shows
 * that as the empty boxes it came from.
 *
 * <p>The shelf life is here and on no customer-facing response, which is the right side of the
 * line for it: what a customer needs is the day <em>their own</em> voucher runs out, which is on
 * their claim, and "vouchers last 30 days" on a catalogue card would be the rule rather than the
 * promise. Null means vouchers from this offer never run out, and the form shows that as the
 * empty box it came from rather than as a nought.
 *
 * <p><strong>What a bundle contains is here, so that whoever composed one can read it
 * back.</strong> That is the argument every other field on this record makes, and it is the only
 * one available for this one: a bundle's contents are fixed when it is composed, so the back
 * office is where somebody checks that what they meant to put in is what went in. Empty on
 * everything that is not a bundle, which is every offer this application seeds.
 *
 * <p>The two caps are here for the same reason and are on no customer-facing catalogue: what the
 * back office shows is the rule somebody set, so that they can read it back and correct it,
 * while what a customer needs is where <em>they</em> stand inside it — how many they have had
 * and how many they may still have — and that is on their own reading, at their own address,
 * because it is an answer with a person in it. Null means there is no such cap, and the form
 * shows it as the empty box it came from rather than as a nought.
 */
record OfferResponse(String code, String title, String description, long costInPoints,
                     String voucherPrefix, OfferState state, LocalDate opensOn,
                     LocalDate closesOn, Integer voucherValidForDays,
                     Integer minimumStreakWeeks, String requiresBadge,
                     Long minimumLifetimePointsEarned,
                     Integer maxPerCustomer, Integer maxPerCustomerPerWeek,
                     Integer stock,
                     Long discountedCostInPoints, LocalDate discountOpensOn,
                     LocalDate discountClosesOn, List<BundleMemberResponse> contents) {

    static OfferResponse of(AnOfferAsItStands offer) {
        return new OfferResponse(offer.code(), offer.title(), offer.description(),
                offer.costInPoints(), offer.voucherPrefix(), offer.state(), offer.opensOn(),
                offer.closesOn(), offer.voucherValidForDays(), offer.minimumStreakWeeks(),
                offer.requiresBadge(), offer.minimumLifetimePointsEarned(),
                offer.maxPerCustomer(), offer.maxPerCustomerPerWeek(), offer.stock(),
                offer.discountedCostInPoints(), offer.discountOpensOn(),
                offer.discountClosesOn(),
                offer.contents().stream().map(BundleMemberResponse::of).toList());
    }
}
