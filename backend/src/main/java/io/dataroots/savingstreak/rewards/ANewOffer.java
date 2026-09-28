package io.dataroots.savingstreak.rewards;

import java.time.LocalDate;
import java.util.List;

/**
 * An offer somebody wants the catalogue to have: the five things it takes to be one.
 *
 * <p>There is no state here and there is no choosing one. Everything created arrives as a draft,
 * because the reason drafts exist is that a half-written reward should never be on a customer's
 * screen, and a create call that could name {@code PUBLISHED} would be a way of skipping straight
 * past that in one request. Publishing is its own press, made when whoever wrote it has read it
 * back.
 *
 * <p><strong>There is no kind either, and that is still true now that a bundle can be
 * written.</strong> The paragraph that used to stand here said a field for the kind would be a
 * field with one valid answer; what has changed is only that there are now two kinds, and the
 * kind is still not asked for. An offer with member lines is a bundle and an offer without them
 * is an item — the list below <em>is</em> the answer — and a separate field would be a second
 * way of saying it, which is a second way of saying it wrong: {@code BUNDLE} with nothing in it,
 * or {@code ITEM} with a hamper's worth of lines attached.
 *
 * <p><strong>The members are two or more offers that already exist, with quantities.</strong>
 * Empty is an ordinary item and is what every offer this application ships is. Two is the least
 * a bundle can be, because one thing in a wrapper is the thing itself sold twice — priced
 * separately, stocked separately, and claimable under two codes — and nobody composing a hamper
 * means that. What may be inside one is refused in sentences by the module: not itself, not
 * something the catalogue has never heard of, not something withdrawn, and not another bundle.
 * The last of those is a depth limit written as a rule, and the argument for it is that a bundle
 * of bundles has no natural bottom: the stock of everything in it becomes a walk of unknown
 * length done on every read of every card, and a customer would be quoted one price for a tree.
 *
 * <p>The code is on it, and it is the only time it ever will be. An offer's code is fixed from the
 * moment it exists, because a claim already made points at it, so it is chosen here and refused
 * everywhere afterwards — which is why {@link AChangeToAnOffer} carries one only in order to say no
 * to it.
 *
 * <p><strong>The window is optional on both ends and either end may stand alone.</strong> An
 * offer that opens on the first of December and never closes is a perfectly ordinary thing to
 * write, and so is one that closes at the end of the month and has been open all along; a record
 * that demanded both would make "runs from now until Christmas" impossible to say. Both null is
 * the ordinary case and the one every seeded entry is in.
 *
 * <p>The days arrive as days, already read. Turning the text somebody typed into a date is the
 * web layer's job — a date that is not a date is a fact about a form, and it is answered in the
 * same sentence a goal's deadline is — but whether the two days make a window an offer can
 * actually be claimed in is a rule about the catalogue, and that is refused here with everything
 * else.
 *
 * <p>Every field is taken as it was typed and judged by the module rather than by the request.
 * Whether a title is a title and whether a price is a price are rules about the catalogue, and a
 * record that rejected them would be the web layer deciding a little of it.
 *
 * <p><strong>The two caps read exactly like the shelf life below and are boxed for the same
 * reason.</strong> Null is a real answer rather than a missing one: no cap at all is what every
 * offer this application ships says about itself, and it is what a form with two empty boxes
 * means. Nought is not that answer and is refused — a cap of nought is not a limit, it is an
 * offer nobody may ever claim, and somebody who typed it meant either "no cap", which is an
 * empty box, or they made a mistake worth being told about at the form rather than a fortnight
 * later.
 *
 * <p>The stock is optional and boxed for the same reason the shelf life below it is, and the
 * absence means the same kind of thing: an offer nobody gave a number to never runs out, which is
 * every offer this application ships. Nought is a different answer and a legal one — "there are
 * none of these at the moment" is a thing somebody can honestly write down, and the offer reads
 * as sold out from the minute it is published — so an empty box has to travel as nothing at all
 * rather than as a zero the module would then have to guess about. A negative number is refused,
 * because there is no number of things smaller than none.
 *
 * <p>The shelf life is the one optional thing here, and it is a boxed {@link Integer} because null
 * is a real answer rather than a missing one: a voucher that never runs out is what all four
 * seeded offers issue and what most offers will. Nought is not that answer and is refused — the
 * argument is on {@link VoucherShelfLife} — so an empty box on the form travels as nothing at all
 * rather than as a zero the module would have to guess about.
 *
 * <p><strong>The three eligibility thresholds are optional, boxed, and all of them ANDed.</strong>
 * A run of weeks, a badge and a lifetime of points earned, each null when the offer asks for no
 * such thing — which is what all four seeded offers say and what most offers will. Boxed for the
 * reason the shelf life is: nought is not the same answer as nothing, and a streak of nought
 * weeks would be a rule every customer already meets written down as though it were a rule. They
 * are a closed list of three plain figures rather than anything anybody has to write, and the
 * argument for that is on {@code WhoAnOfferIsFor}.
 *
 * <p>The badge is a plain string — the code of the achievement, as the trophy case names it —
 * because this module reads no other module and a field typed as another module's enum would be
 * the dependency the whole design is avoiding, declared in the one place nothing could remove it
 * from.
 *
 * <p><strong>A promotion can be written in from the start, and it is three fields or none.</strong>
 * An offer put into the catalogue for a January sale is an offer whose discount is part of what
 * it is, and a create followed by a change would leave a moment in between where the catalogue
 * held something nobody meant — the argument the window above already makes. The price is boxed
 * because absent is the ordinary answer; the two days are days, already read, for the same reason
 * the window's are.
 *
 * <p>All three or none of them, and the module refuses anything in between rather than guessing.
 * A price with no window would strike the ordinary figure through forever, which makes "usually
 * 250" a sentence about a price nobody was ever charged; a window with no price is two dates that
 * do nothing. Neither is a thing somebody means, and a record that quietly dropped the odd one
 * out would be this application deciding which half of a half-filled form was the real one.
 */
public record ANewOffer(String code, String title, String description, long costInPoints,
                        String voucherPrefix, LocalDate opensOn, LocalDate closesOn,
                        Integer voucherValidForDays,
                        Integer minimumStreakWeeks, String requiresBadge,
                        Long minimumLifetimePointsEarned,
                        Integer maxPerCustomer, Integer maxPerCustomerPerWeek,
                        Integer stock,
                        Long discountedCostInPoints, LocalDate discountOpensOn,
                        LocalDate discountClosesOn, List<AMemberOfABundle> members) {
}
