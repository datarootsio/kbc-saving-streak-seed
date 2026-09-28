package io.dataroots.savingstreak.rewards;

import java.time.LocalDate;

/**
 * Everything an offer would say about itself if the form in front of somebody were written to the
 * row, gathered into one thing so that the check that argues with it takes one argument.
 *
 * <p><strong>This exists because the check had sixteen parameters.</strong> Seven slices written
 * in parallel each appended a rule and the value it needed — a window, a shelf life, three
 * eligibility thresholds, two caps, a stock figure, a discounted price and its own two dates —
 * and every one of them was a correct small change to a signature that was already too long. The
 * result was a method nobody could call without counting commas, two call sites whose arguments
 * had to be read side by side to be checked against each other, and a compiler that would have
 * said nothing at all about two {@code LocalDate}s swapped between the window and the promotion.
 * A record makes that a named field at both call sites and a compilation failure the moment a
 * name is wrong.
 *
 * <p><strong>Not {@link ANewOffer}, although it is very nearly the same list.</strong> That one is
 * a request as it arrives from outside: it carries the members of a bundle, it is what the web
 * layer builds, and it is public because it crosses the module's edge. This one is neither a
 * request nor an answer — it is the sixteen values <em>after</em> a code has been trimmed, a
 * title stripped and, on an edit, whatever the form left out filled in from the row that already
 * exists. That last part is the whole reason the two cannot be one type: an edit checks a
 * mixture of what was typed and what was already there, and a request cannot describe that
 * because a request is only the typing. Using {@code ANewOffer} here would have made the edit
 * path build a fake request, which is the kind of shortcut that ends with a rule checked against
 * the wrong half of an offer.
 *
 * <p>It says nothing about whether any of it is allowed. It is the subject of the argument and
 * not the argument — every rule, and every sentence a refusal is written in, is
 * {@code TheCatalogueAsItIsRun}'s.
 *
 * <p>Package-private, like everything in this module that is not a customer's answer: it exists
 * between a form and a check, and both of those are inside.
 *
 * @param code the offer's immutable natural key, already trimmed
 * @param title what the card is headed with, already trimmed
 * @param costInPoints the ordinary price
 * @param description the words on the card, or null for none
 * @param voucherPrefix the few letters in the middle of a voucher code, already trimmed
 * @param opensOn the first day it may be claimed, or null for no start
 * @param closesOn the last day it may be claimed, inclusive, or null for no end
 * @param voucherValidForDays the shelf life of a voucher issued from it, or null for none
 * @param minimumStreakWeeks the run of weeks it asks for, or null for no such rule
 * @param minimumLifetimePointsEarned the lifetime of points it asks for, or null for no such rule
 * @param maxPerCustomer the lifetime cap, or null for none
 * @param maxPerCustomerPerWeek the cap inside one savings week, or null for none
 * @param stock how many of it there are, or null for an offer that never runs out
 * @param discountedCostInPoints what it costs while the promotion runs, or null for no promotion
 * @param discountOpensOn the first day of the promotion, or null
 * @param discountClosesOn the last day of the promotion, or null
 */
record WhatAnOfferWouldSay(String code, String title, long costInPoints, String description,
                           String voucherPrefix, LocalDate opensOn, LocalDate closesOn,
                           Integer voucherValidForDays, Integer minimumStreakWeeks,
                           Long minimumLifetimePointsEarned, Integer maxPerCustomer,
                           Integer maxPerCustomerPerWeek, Integer stock,
                           Long discountedCostInPoints, LocalDate discountOpensOn,
                           LocalDate discountClosesOn) {
}
