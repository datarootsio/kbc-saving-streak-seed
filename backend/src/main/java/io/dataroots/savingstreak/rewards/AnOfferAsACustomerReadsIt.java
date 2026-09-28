package io.dataroots.savingstreak.rewards;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One entry in the catalogue as it stands <em>for one person, right now</em>: what it is, what it
 * costs, whether they can claim it this minute, and the single reason if they cannot.
 *
 * <p><strong>A third record rather than fields on either of the two that exist.</strong>
 * {@link ARewardOnOffer} is the catalogue with nobody in it — the same four entries at the same
 * four prices for everybody, served at an address whose shape a test pins to the character — and
 * widening it would be changing the one thing this whole feature was built not to change.
 * {@link AnOfferAsItStands} is the back office, and it carries the voucher prefix and the stored
 * state precisely because an administrator is the person who <em>chose</em> them; a customer is
 * never shown either. This one is the third question, and it is the only one of the three whose
 * answer depends on who is asking and on what day it is.
 *
 * <p><strong>Everything on it below the price is derived on read and stored nowhere.</strong>
 * Whether an offer is open is the window and the clock; how many are left is the stock less the
 * claims made, what a customer may still have is their own history against the caps, whether
 * the offer is theirs at all is their standing against its rules, and what it costs today is
 * the promotion against the day. Every one of those is a question asked at the moment of
 * reading rather than a column a nightly job sets — and what is still not here, because nothing
 * can yet set it, is a bundle, a hold and a place in a queue. The application
 * already holds that line in two places — a points balance is a sum over unexpired batches and a
 * goal's status is computed against its projection — and the reason is the same here: two stored
 * figures that must agree eventually stop agreeing, and this one would have to agree with a clock.
 *
 * <p><strong>{@code claimable} is a verdict and not a comparison the page makes.</strong> It is
 * true when nothing at all is in the way, and it is the backend's answer rather than
 * {@code lockedBecause == null} worked out at the other end — the same bargain
 * {@code AVoucherAtTheCounter.good} strikes beside its own state, and for the same reason: the day
 * a lock arrives that a page has never heard of, a page reading the boolean draws it correctly and
 * a page inferring the boolean draws it as claimable.
 *
 * <p><strong>The reason travels twice, as a value and as a sentence, and both are needed.</strong>
 * {@link WhyAnOfferIsLocked} is what a page styles and groups by; {@code whyItIsLocked} is what a
 * person reads, written here because it needs the offer's own title and its own dates in it and
 * because the same words are what a claim is refused with. One sentence written in one place is
 * what stops the card and the refusal from telling a customer two different stories about the same
 * rule. Both are null when nothing is in the way — a locked reason on a claimable offer would be
 * a card explaining a rule it is not subject to.
 *
 * <p><strong>The window is on every entry, open or not.</strong> Not only on a locked one: a
 * customer saving towards something wants to know it closes on the thirtieth <em>while they can
 * still claim it</em>, which is the whole of "so that I know whether I have time to save for it".
 * The days travel as days, for the reason a season's do: an offer's last day is a thing announced
 * on a poster, and sending it as an instant would put it out by one for anybody reading east of
 * here. Both are null on an offer that has never had a window, which is all four of the seeded
 * ones.
 *
 * <p><strong>The limits are on every entry too, and they are the first thing here whose answer
 * depends on who is asking.</strong> Four components rather than one, because a customer looking
 * at a capped offer has two different questions and the page has to answer both: what the rule
 * is — {@code maxPerCustomer} in a lifetime, {@code maxPerCustomerPerWeek} in a week, either or
 * both null for an offer that caps nothing — and where they personally stand inside it, which is
 * {@code howManyYouHaveHad} and {@code howManyYouMayStillHave}. "Two of three claimed, one left"
 * is a sentence the card can only write with all four, and it is the sentence that turns a limit
 * from something somebody discovers by being refused into something they can plan around.
 *
 * <p>{@code howManyYouMayStillHave} is the backend's subtraction and not the page's, which is
 * the same bargain the price and the lock already strike. With two caps there are two
 * remainders, and the one that matters is the smaller: an offer capped at five in a lifetime and
 * one a week has one left for somebody who has had none, and a page doing the arithmetic would
 * have to know which week this application counts in and where its boundaries are before it
 * could get that right. Null means nothing caps them, which is every seeded offer for
 * everybody — deliberately not a very large number, so that "there is no limit" and "you may
 * have a lot" stay different answers.
 *
 * <p>{@code howManyYouHaveHad} counts what a limit counts and nothing else: a claim whose
 * voucher has since expired is in it, because an expiry is the customer's own miss, and a
 * cancelled one is not, because a cancellation is the scheme undoing the claim. Nothing in this
 * release can cancel anything, so today the second half of that is a rule with nothing to apply
 * to. It is a count of claims rather than of vouchers still alive, which is why a customer at
 * their limit stays at it after their voucher runs out.
 *
 * <p><strong>{@code whatIsLeft} is a figure and not a flag, and it is null far more often than
 * not.</strong> A scarce offer says how many are left, because "three left" is the only thing in
 * this record a customer can hurry about; an offer with no stock set says nothing at all, which
 * is what all four seeded entries say and what most offers will. Null is the absence of scarcity
 * rather than nought of something — the two are opposite facts and a primitive would have
 * reported the first as the second, greying every unlimited card in the catalogue.
 *
 * <p>It is derived like everything else here: the total somebody set, less the claims already
 * made, counted at the moment of the read. It is not a second opinion about {@code claimable}
 * either — an offer with nothing left is locked with {@link WhyAnOfferIsLocked#NOTHING_LEFT} and
 * says so in the sentence, and a page is never asked to infer a lock from a nought.
 *
 * <p><strong>The price on it is what this customer would be charged today, and the ordinary one
 * travels beside it.</strong> {@code costInPoints} is the effective figure — the discounted one
 * while a promotion runs and the ordinary one on every other day — because it is the number the
 * card puts in front of somebody and the number the claim will take. {@code ordinaryCostInPoints}
 * is what to strike through, and it is null whenever there is nothing to strike through, so that
 * a page draws the second figure by asking whether it is there rather than by comparing two
 * numbers. That is the whole of "the page performs no arithmetic": a card that subtracted one
 * price from the other would be a second implementation of the pricing rule, living at the end of
 * the wire, going out of date the first time anybody changed the first one.
 *
 * <p>{@code discountClosesOn} is the last day of the promotion, and like the ordinary price it is
 * null unless one is running. It is here rather than left to the offer's own closing day because
 * they are different promises about different things — "the offer goes away on the thirtieth" and
 * "the price goes back up on the eighth" — and a customer who was told only the first would find
 * the second out at the till.
 *
 * <p><strong>{@code yourHoldLapsesAt} is the one field on this record that is about something
 * the customer has rather than about the offer</strong>, and it is null for everybody who is not
 * holding this particular thing, which is nearly everybody nearly always. When it is set, three
 * other fields have to be read differently and the page is told so by this one alone:
 * {@code claimable} is true because they may convert, {@code whatIsLeft} may well be nought
 * because their own hold is one of the ones subtracted from it, and the button is not "claim"
 * but a pair — take it, or give it up.
 *
 * <p>A moment rather than a number of hours remaining, and not a day. Hours remaining would be
 * stale before the page finished drawing; a day would round seventy-two hours to something that
 * is not what anybody was promised. The argument is written out on {@link TheShelfLifeOfAHold}.
 *
 * <p><strong>A customer's own hold is an affordance and never a lock, which is a decision worth
 * stating because the opposite is defensible.</strong> Held stock is unavailable, an offer whose
 * last one is held reads as {@code NOTHING_LEFT} — to <em>everybody else</em>. Showing the holder
 * the same grey card with "sold out" on it would be this application taking something away from
 * somebody and then telling them they could not have it, on the strength of a rule that exists
 * for their benefit. So the reading returns before the stock question is ever asked of them, the
 * card stays claimable, and this field is how the page knows to draw the countdown and the two
 * buttons instead of the ordinary one.
 *
 * <p><strong>{@code yourPlaceInTheQueue} is the second field here that is about the customer
 * rather than about the offer</strong>, and it is null for everybody who is not waiting for
 * this particular thing — which, again, is nearly everybody nearly always. One-based, because
 * it is read out loud: first in line is first.
 *
 * <p>It travels beside {@code yourHoldLapsesAt} and never with it. The two are the two ends of
 * one pipeline — a customer waits, their turn comes, and what they are given is a hold — so a
 * card showing both would be showing somebody a queue they have already come out of. A page
 * therefore reads them in that order: a hold if there is one, otherwise a place, otherwise the
 * ordinary card.
 *
 * <p><strong>There is no "you could join this queue" flag, deliberately.</strong> An offer
 * worth queueing for is one locked with {@link WhyAnOfferIsLocked#NOTHING_LEFT}, which is
 * already on the card and already the thing the page styles by; a second field saying the same
 * thing would be a second copy of the rule, living at the end of the wire, out of date the
 * first time the rule moved. Whether this particular customer may actually join — the window,
 * the rules, the caps — is the same gauntlet a hold runs and is answered when they press, in a
 * sentence, by the module that owns it.
 *
 * <p><strong>{@code contents} is what a bundle hands over, and it is empty on everything
 * else.</strong> "I want a bundle to say what is in it, so that I know what I am getting for one
 * price" is the whole of the customer's side of a bundle, and it cannot be met by a title: one
 * price against three things is exactly the card on which somebody has to be able to see the
 * three things. Each line is a code, the member's title and how many of it go in — no price,
 * because a bundle is priced itself and a page that added the members up would be quoting a
 * saving nobody decided.
 *
 * <p>Empty rather than null for an offer that is not a bundle, because the page draws a list and
 * an empty list draws nothing. Every offer this application seeds is in that state, which is
 * what makes this field invisible on all four of them.
 *
 * <p>Public, like the other two, because it is what leaves this module. The entity and its
 * repository stay behind {@link RewardsService}.
 */
public record AnOfferAsACustomerReadsIt(String code, String title, String description,
                                        long costInPoints, boolean claimable,
                                        WhyAnOfferIsLocked lockedBecause, String whyItIsLocked,
                                        LocalDate opensOn, LocalDate closesOn,
                                        Integer maxPerCustomer, Integer maxPerCustomerPerWeek,
                                        long howManyYouHaveHad, Long howManyYouMayStillHave,
                                        Integer whatIsLeft,
                                        Long ordinaryCostInPoints, LocalDate discountClosesOn,
                                        Instant yourHoldLapsesAt, Integer yourPlaceInTheQueue,
                                        List<WhatABundleContains> contents) {
}
