package io.dataroots.savingstreak.rewards;

import java.time.LocalDate;
import java.util.List;

/**
 * One row of the catalogue as whoever runs it reads it: everything the offer says about itself,
 * including the two things a customer is never shown.
 *
 * <p><strong>A second record beside {@link ARewardOnOffer} rather than more fields on it.</strong>
 * The two answer different questions and are read by different people. A customer is shown what
 * there is to buy and what it costs, and {@code ARewardOnOffer} says exactly that and deliberately
 * no more — its own javadoc argues that what a voucher code is built out of is the module's
 * business and that nothing outside has ever needed to know. That argument still holds for the
 * customer's catalogue and stops holding here: an administrator is the person who <em>chooses</em>
 * the prefix, so a screen that could not show them the one they chose could not let them correct
 * it either. Widening the customer's record to carry it would put the module's own plumbing on a
 * page the customer reads, to save writing eight lines here.
 *
 * <p>The state travels with it for the same reason. A draft, a published offer and a withdrawn one
 * are the same row and look identical without it, and the whole of the administration screen is
 * telling them apart.
 *
 * <p><strong>The window is here because somebody now sets it.</strong> Two days, either of them
 * null, and null means the offer has no such day — no first day is always open, no last day is
 * never closed. They are the administrator's own figures, read back to them exactly as they typed
 * them, and deliberately <em>not</em> accompanied by whether the offer is open today: that is a
 * question about a clock and it belongs on {@link AnOfferAsACustomerReadsIt}, where the person
 * asking it is the person it is about. A back office that reported "open" would be reporting one
 * moment's answer on a page somebody leaves sitting there all afternoon.
 *
 * <p>The shelf life is here because somebody running the scheme is the person who sets it, and a
 * screen that could not show them the number they chose could not let them correct it either —
 * which is the argument the prefix above already makes. Null means the offer never expires its
 * vouchers, which is what all four seeded offers say, and the administration screen shows that as
 * the absence it is rather than as a nought.
 *
 * <p><strong>The two caps are here because somebody running the scheme is the person who sets
 * them</strong>, and a screen that could not show them the numbers they chose could not let them
 * correct one — which is the argument the prefix and the shelf life above already make twice.
 * Null means there is no such cap, which is what all four seeded offers say and what the
 * administration form shows as the empty box it came from rather than as a nought. What is
 * deliberately <em>not</em> here is how many anybody has had: that is a fact about a customer
 * and it belongs on {@link AnOfferAsACustomerReadsIt}, where the person asking is the person it
 * is about.
 *
 * <p><strong>The stock is the total somebody set and not how many are left.</strong> It is here
 * because they set it and have to be able to read it back in order to correct it — the argument
 * the prefix and the shelf life above already make. What is left is the total less the claims
 * already made, and it is deliberately <em>not</em> beside it: that is a figure with a table of
 * claims behind it, it moves every time anybody claims, and a back office somebody leaves open
 * all afternoon would sit there showing one moment's answer. The customer's read is where the
 * remaining count belongs, and {@link AnOfferAsACustomerReadsIt} is where it is. Null means the
 * offer never runs out, which is what all four seeded offers say, and the form shows that as the
 * empty box it came from rather than as a nought.
 *
 * <p>What is not here is what nobody can yet set: a bundle's member lines, a hold on the last
 * one, and a waiting list for it. Stock, the limits, the eligibility rules and the discount all
 * used to be on that list and have each come off it as their own slice landed — which is the
 * rule this paragraph keeps rather than the list: a field reporting a null nobody can set would
 * be a promise that something on the screen does something.
 *
 * <p><strong>Who an offer is for is here for the reason the prefix and the shelf life are: it is
 * set on this screen and nowhere else.</strong> Three figures, each null when the offer asks for
 * no such thing, read back exactly as somebody typed them. It is deliberately <em>not</em>
 * accompanied by whether any particular customer meets them: that is a question about a person
 * and it belongs on {@link AnOfferAsACustomerReadsIt}, where the person asking it is the person
 * it is about. A back office reporting "most customers qualify" would be an answer about
 * everybody on a page about one row.
 *
 * <p>The badge reads back as the code it was typed as, unresolved. Whether the bank still runs a
 * challenge by that name is the challenges screen's answer, and this module does not know it —
 * a rule naming a badge nobody can win any more is a rule that locks the offer for everybody,
 * which is a true thing for this page to show rather than one to hide behind a lookup.
 *
 * <p><strong>The discount has left that list, and the paragraph above is left standing rather
 * than corrected.</strong> Three of its clauses are still true and a sentence that four slices
 * are each about to edit is a sentence that gets hand-merged; what is true now is that a
 * promotion is something somebody sets, so it is something this record has to read back to them.
 * All three parts of it are here — the price, the first day and the last — as they were typed and
 * with no verdict beside them. Whether the promotion is running <em>today</em> is deliberately
 * absent for the reason the window's verdict is: this is a page somebody leaves open all
 * afternoon, and one moment's answer sitting on it going stale is worse than no answer. The
 * customer's reading is where a clock belongs. All three are null together on an offer with no
 * promotion, which is every offer this application seeds.
 *
 * <p><strong>What a bundle contains is here too, and it is the one thing on this record that is
 * not a column of the row.</strong> Somebody who composed a hamper has to be able to read back
 * what they put in it — that is the same argument every other field here makes — and the lines
 * live in a table of their own rather than in a column, so they are read alongside. There is no
 * verdict beside them and no count of what is left of each: how many of the bundle can still be
 * handed over is an answer with the claims table in it and belongs on the customer's read, for
 * the reason the stock above is the total rather than the remainder.
 *
 * <p>Empty on everything that is not a bundle, which is every offer this application seeds, and
 * empty rather than null because the form draws a list from it.
 */
public record AnOfferAsItStands(String code, String title, String description, long costInPoints,
                                String voucherPrefix, OfferState state, LocalDate opensOn,
                                LocalDate closesOn, Integer voucherValidForDays,
                                Integer minimumStreakWeeks, String requiresBadge,
                                Long minimumLifetimePointsEarned,
                                Integer maxPerCustomer, Integer maxPerCustomerPerWeek,
                                Integer stock,
                                Long discountedCostInPoints, LocalDate discountOpensOn,
                                LocalDate discountClosesOn, List<WhatABundleContains> contents) {
}
