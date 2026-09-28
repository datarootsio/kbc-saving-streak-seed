package io.dataroots.savingstreak.web;

import java.util.List;

/**
 * What somebody running the scheme sends to add an offer: its code, its words, its price and what
 * its vouchers are stamped with.
 *
 * <p>No state and no kind. Everything created is a draft, and whether an offer is an item or a
 * bundle is decided by whether the form named anything to go inside it rather than by a field
 * beside the list saying so — the arguments are on the module's own {@code ANewOffer}, which
 * this is read into unjudged.
 *
 * <p><strong>The members are the contents of a bundle, and an empty list is an ordinary
 * item.</strong> Each is a code and a quantity, sent as they were typed: what may go into a
 * bundle, how few of them is too few and how little of one is too little are all rules about
 * the catalogue and come back as sentences. Absent and empty mean the same thing here, which is
 * the one place this layer reads two shapes as one answer — a form with no bundle section and a
 * form whose bundle section nobody filled in are the same request, and a module that had to
 * tell them apart would be answering a question about HTML.
 *
 * <p>The shelf life is optional and boxed, and left out it means the vouchers this offer issues
 * never run out — which is every offer the application has today. Unlike the price below, a
 * missing one is not read as nought: nought days would be a voucher dead before it was printed,
 * the module refuses it in a sentence, and reading an empty box as one would be this layer
 * inventing a rule about something it has no business knowing.
 *
 * <p><strong>The three eligibility thresholds arrive as they were typed and are judged by the
 * module.</strong> Two numbers and a badge code, each left out for an offer that asks no such
 * thing — which is every offer this application ships. They are boxed for the reason the shelf
 * life is: an empty box is not a nought, and a streak requirement of nought weeks is a
 * restriction that restricts nobody, which the module refuses in a sentence. Whether a threshold
 * is a threshold and what the least one may be are rules about the catalogue, and reading an
 * empty box as anything but nothing would be this layer inventing one.
 *
 * <p>The two caps are optional and boxed like the shelf life, and left out they mean an offer
 * one customer may have as often as they like — which is every offer this application ships. A
 * missing one is not read as nought for the shelf life's reason turned around: nought would be
 * an offer nobody may ever claim, the module refuses it in a sentence, and reading an empty box
 * as one would be this layer inventing a rule about how often a reward may be had.
 *
 * <p>The stock is optional and boxed for the same reason the shelf life is, and left out it
 * means the offer never runs out — which is every offer this application ships. It is a count of
 * things rather than an amount of money, so it arrives as a number and there is nothing for this
 * layer to read wrong. Unlike the price, a missing one is emphatically not nought: nought is a
 * real and different answer meaning there are none of it at the moment, and reading an empty box
 * as one would sell out every offer somebody forgot to fill in.
 *
 * <p>The price arrives as a number rather than as text, unlike an amount of money. A point is a
 * whole thing with no decimal part and no currency, so there is no rounding to be done on the way
 * here and nothing for this layer to get wrong by reading it — which is exactly the reason an
 * amount of money does travel as text.
 *
 * <p><strong>The two days do travel as text, and for that same reason turned around.</strong>
 * "31/12/2026" and "next Tuesday" are both things somebody can put in a date box, and text is the
 * only form that still has the characters in it when the answer has to be a sentence about what
 * could not be read as a day. It is the shape a goal's deadline already arrives in, and the
 * reading is the same: absent or blank is an offer with no such day, which is the ordinary case.
 * <p><strong>The promotion is three fields and they go up together.</strong> A price, boxed like
 * the shelf life and for the same reason — absent is a real answer and means the offer is at its
 * ordinary price — and two days as text, like the window's, because a date box is a date box and
 * "next Tuesday" has to survive the journey with its characters intact in order to be quoted back
 * in the refusal. Whether three fields make a promotion, and whether the price is one the
 * catalogue will keep, are the module's rules and are refused there.
 */
record NewOfferRequest(String code, String title, String description, Long costInPoints,
                       String voucherPrefix, String opensOn, String closesOn,
                       Integer voucherValidForDays,
                       Integer minimumStreakWeeks, String requiresBadge,
                       Long minimumLifetimePointsEarned,
                       Integer maxPerCustomer, Integer maxPerCustomerPerWeek,
                       Integer stock,
                       Long discountedCostInPoints,
                       String discountOpensOn, String discountClosesOn,
                       List<BundleMemberRequest> members) {
}
