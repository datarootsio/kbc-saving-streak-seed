package io.dataroots.savingstreak.rewards;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything one reading of one offer is judged against: the day it is being read on, and the
 * facts about the person reading it that the offer's own rules ask questions of.
 *
 * <p><strong>One parameter object because four slices each needed one more fact, and a parameter
 * per slice is how a signature rots.</strong> {@code claimable()} and {@code lockedBecause(why,
 * words)} took nothing about the customer at all when the window was the only rule there was.
 * Then eligibility wanted a standing, the limits wanted a count of what somebody had already had,
 * scarcity wanted how many were left, and a promotion wanted the day — and each of those, taken
 * on its own, is an obviously reasonable extra parameter. Taken together they are two methods
 * with four positional arguments of three different types, called from four places, where
 * swapping two of them compiles. Every later rule would add a fifth. So the facts travel as one
 * thing with names on them, and a rule that needs a new fact adds a component here rather than a
 * parameter everywhere.
 *
 * <p><strong>Assembled once per offer per reading, never fetched from inside.</strong> The day and
 * the standing are the same for every row of one catalogue read — the arguments for that are on
 * {@link CustomerStanding} and beside {@code RewardsService.catalogueFor} — and what is left and
 * how many they have had are per offer, taken out of the one query each is counted by before the
 * first row is drawn. Nothing in here reads a clock or a repository: it is the facts as of one
 * moment, handed in, so that a list long enough to cross midnight cannot judge its first row
 * against a different world from its last.
 *
 * <p><strong>Every component may be absent, and absent means the rule it feeds is not in
 * play.</strong> {@code whatIsLeft} is null on an offer with no stock, and the standing is the
 * empty one for a request that has no customer behind it. A component being null is never a
 * reason to lock: it is the ordinary state of an offer nobody has restricted, which is all four
 * of the seeded ones.
 *
 * <p>Package-private, like the entity it is handed to. What leaves this module is
 * {@link AnOfferAsACustomerReadsIt}, with all of this already folded into it.
 *
 * <p><strong>The hold is the fifth fact, and it arrived here rather than as a fifth parameter
 * for exactly the reason this record exists at all.</strong> Two rules and a card all want to
 * know whether this customer is holding this offer: the limit, because a live hold counts
 * against a cap; the stock check, because a customer must never be locked out by their own
 * reservation; and the card, because it has to show the countdown and the two things they can do
 * about it. Widening {@code claimable} and {@code lockedBecause} a fifth time is the thing the
 * paragraph above says not to do.
 *
 * <p><strong>The place in the queue arrived the same way the hold did, and it is the one fact
 * here that no rule reads.</strong> Nothing locks an offer because somebody is waiting for it
 * and nothing unlocks one either: a queue is something a customer joined <em>because</em> the
 * card said sold out, so the card goes on saying sold out and gains a line saying where they
 * stand. It is here rather than fetched in the card because it is a fact about this customer
 * and this offer as of one reading, which is the whole of what this record is for, and because
 * the alternative — a query per card — is the thing every other component here exists to avoid.
 *
 * <p><strong>The two bundle facts are on here for exactly the reason the paragraph above
 * predicted.</strong> A bundle's reading has to say what is in it, and a bundle whose members
 * cannot all be supplied has to lock with a sentence naming the one that ran short — and both of
 * those, taken on their own, are obviously reasonable extra parameters to {@code claimable} and
 * {@code lockedBecause}. Taken with the four that came before they are two methods nobody can
 * call correctly. They are worked out where every other figure here is worked out, once per
 * offer per reading, in {@code RewardsService}, and they arrive already decided.
 *
 * <p>{@code contents} is empty rather than null for everything that is not a bundle, because
 * "there is nothing in it" and "it is not the sort of thing that has things in it" are the same
 * answer to the only question anybody asks of it — what to draw under the card. Every offer this
 * application seeds is in that state.
 *
 * @param today the day the whole reading is being taken against, off the application's clock
 * @param whatIsLeft how many of this offer remain to anybody else, and null when it never runs
 *        out; it already has every live hold subtracted from it, this customer's own included
 * @param haveGone how many of this offer this customer has already had, in all and this week
 * @param standing the streak, badges and points the web layer assembled for this customer
 * @param theirHold the hold this customer has on this offer, and null when they hold none
 * @param theirPlaceInTheQueue where this customer stands in the queue for this offer, one-based,
 *        and null when they are not waiting for it — which is nearly everybody nearly always
 * @param contents what this offer hands over, and empty for everything that is not a bundle
 * @param theMemberInTheWay the first member of this bundle that cannot supply the quantity the
 *        bundle needs of it, and null when none of them is short — which includes every offer
 *        that is not a bundle at all
 */
record WhatIsKnownToday(LocalDate today, Integer whatIsLeft, HowManyHaveGone haveGone,
                        CustomerStanding standing, AHoldOfYourOwn theirHold,
                        Integer theirPlaceInTheQueue,
                        List<WhatABundleContains> contents,
                        AMemberThatCannotSupply theMemberInTheWay) {

    /** Whether this customer has the last one set aside for them right now. */
    boolean theyHoldOne() {
        return theirHold != null;
    }

    /**
     * How many of this offer this customer has had or is holding, which is the figure a lifetime
     * cap is measured against.
     *
     * <p><strong>A live hold counts, and the spec says so.</strong> A cap of one on an offer
     * somebody is holding has been reached: they have the thing set aside, and letting them claim
     * a second while the first waits for them would be a customer with two of something one
     * person may have one of. The alternative — count it only once converted — would make the cap
     * enforceable only by whoever pressed last, which is the sort of rule that holds until two
     * people try it.
     *
     * <p>It is nevertheless not the figure a card shows as {@code howManyYouHaveHad}. That one is
     * what they have <em>had</em>, which is claims, and a hold is not a thing anybody has had
     * yet. What the hold changes is how many more they may take, which is the subtraction.
     */
    long everHadOrHolds() {
        return haveGone.everHad() + (theyHoldOne() ? 1 : 0);
    }

    /**
     * And the same figure for the savings week, which counts a hold only in the week it was
     * taken in.
     *
     * <p>Seventy-two hours can straddle a Monday, and a hold taken last Saturday is not part of
     * this week's allowance: the weekly cap exists to spread a popular reward out, and a week
     * that inherited last week's reservations would quietly be a rolling window, which is the one
     * thing the spec rules out by name.
     */
    long hadOrHoldsThisWeek() {
        return haveGone.thisWeek() + (theyHoldOne() && theirHold.takenThisWeek() ? 1 : 0);
    }

    /** When their own hold runs out, and null when they hold none. */
    Instant theirHoldLapsesAt() {
        return theirHold == null ? null : theirHold.lapsesAt();
    }
}
