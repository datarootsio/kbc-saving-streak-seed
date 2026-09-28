package io.dataroots.savingstreak.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.rewards.AnOfferAsACustomerReadsIt;
import io.dataroots.savingstreak.rewards.WhyAnOfferIsLocked;

/**
 * One catalogue entry as it stands for the customer asking, right now: everything
 * {@link RewardResponse} carries, plus whether they can claim it and the one reason if they
 * cannot.
 *
 * <p>A third response beside the customer's catalogue and the administrator's, and the reason is
 * the same one that put those two apart. {@code /api/rewards} is the catalogue with nobody in it,
 * pinned to four entries at four prices by a test nobody may edit, and every field added to it
 * would be a field added to that promise. This is the read the rewards page actually makes, and
 * the one whose answer changes overnight.
 *
 * <p>{@code lockedBecause} travels as the enum's own name for the reason the offer's state does:
 * the page styles and phrases by it, and a string it had to know the spellings of would be the
 * same knowledge with nothing checking it. It is null on anything claimable, and so is the
 * sentence beside it.
 *
 * <p>{@code whyItIsLocked} is the backend's sentence, shown unchanged, and it is the same sentence
 * the claim would be refused with. That is the point of sending it rather than letting the page
 * write one per reason: the card and the refusal cannot then tell somebody two different stories
 * about one rule, and a lock this page has never heard of still says something true.
 *
 * <p>{@code whatIsLeft} is how many are left rather than how many exist, and it is null on
 * everything that never runs out — which is all four of the offers this application has always
 * had. The total is deliberately not sent: it is the administrator's figure and a customer has
 * nothing to do with it, while "three left" is the whole of what makes somebody hurry. Null
 * rather than nought for an unlimited offer, because those are opposite facts and a page reading
 * a nought as "none left" would grey the entire catalogue.
 *
 * <p>The two days travel as days and are on every entry, locked or not, because an offer a
 * customer can claim today and which closes on the thirtieth is exactly the one they need the
 * thirtieth for.
 *
 * <p>The limits travel as four figures and are likewise on every entry: the two caps as somebody
 * set them, null for a cap that does not exist, and beside them how many this customer has
 * already had and how many they may still have. Not only on a locked one — "one left" is the
 * thing that makes somebody claim this week rather than next, and a card that only mentioned a
 * limit once it had stopped them would be telling them about a rule at the one moment they can
 * no longer act on it. {@code howManyYouMayStillHave} is null when nothing caps them, which is
 * every seeded offer for everybody, and is the backend's own subtraction because with two caps
 * running there are two remainders and only the smaller of them is true.
 *
 * <p><strong>{@code contents} is what a bundle hands over, and it is empty on everything
 * else.</strong> One price against three things is exactly the card a customer cannot read
 * without being told the three things, and it is the whole of "I want a bundle to say what is in
 * it". Each line is a code, a title and how many go in — no prices, because the bundle is priced
 * itself and a page that added the members up would be quoting a saving nobody decided. Empty
 * rather than null, because the page draws a list and an empty list draws nothing.
 *
 * <p><strong>{@code costInPoints} is what this customer would be charged today</strong> — the
 * discounted figure while a sale is running — and {@code ordinaryCostInPoints} is the one to
 * strike through, null whenever there is nothing to strike through. Two figures rather than one
 * and a percentage, so that the page draws the saving without computing it: a card that
 * subtracted one price from the other would be a second copy of the pricing rule living at the
 * far end of the wire, and the second copy is always the one that goes out of date.
 * {@code discountClosesOn} is the last day of the sale, null unless one is running, because "the
 * price goes back up on the eighth" is a different promise from "the offer goes away on the
 * thirtieth" and a customer told only the second finds the first out at the till.
 *
 * <p><strong>{@code yourHoldLapsesAt} is set only for the one customer who has this offer set
 * aside</strong>, and it changes how three of the fields above read. {@code claimable} is true,
 * because they may convert it; {@code whatIsLeft} may well be nought, because their own hold is
 * one of the ones taken off it; and the buttons are not "claim" but "claim it" and "give it up".
 * A moment rather than a day, because a hold is seventy-two hours rather than a number of
 * calendar days, and the page counts down to it. Null for everybody else, which is nearly
 * everybody, and for every offer nobody is holding, which is nearly every offer.
 *
 * <p><strong>{@code yourPlaceInTheQueue} is the other field that is about the customer rather
 * than about the offer</strong>: where they stand in the waiting list for this one, counted
 * from one because it is read out loud, and null for everybody who is not in it. It never
 * arrives beside {@code yourHoldLapsesAt}, because the two are the two ends of one pipeline —
 * somebody waits, their turn comes, and what they are given is the hold. So a page reads the
 * hold first, the place second, and the ordinary card otherwise.
 *
 * <p>There is deliberately no field saying that a queue could be joined. An offer worth
 * queueing for is one locked with {@code NOTHING_LEFT}, which is already on the card, and a
 * second field saying the same thing would be a copy of the rule living at the far end of the
 * wire. Whether this particular customer may join — the window, the rules, the caps — is
 * answered when they press, in a sentence, by the module that owns it.
 */
record RewardForACustomerResponse(String code, String title, String description, long costInPoints,
                                  boolean claimable, WhyAnOfferIsLocked lockedBecause,
                                  String whyItIsLocked, LocalDate opensOn, LocalDate closesOn,
                                  Integer maxPerCustomer, Integer maxPerCustomerPerWeek,
                                  long howManyYouHaveHad, Long howManyYouMayStillHave,
                                  Integer whatIsLeft,
                                  Long ordinaryCostInPoints, LocalDate discountClosesOn,
                                  Instant yourHoldLapsesAt, Integer yourPlaceInTheQueue,
                                  List<BundleMemberResponse> contents) {

    static RewardForACustomerResponse of(AnOfferAsACustomerReadsIt offer) {
        return new RewardForACustomerResponse(offer.code(), offer.title(), offer.description(),
                offer.costInPoints(), offer.claimable(), offer.lockedBecause(),
                offer.whyItIsLocked(), offer.opensOn(), offer.closesOn(), offer.maxPerCustomer(),
                offer.maxPerCustomerPerWeek(), offer.howManyYouHaveHad(),
                offer.howManyYouMayStillHave(), offer.whatIsLeft(),
                offer.ordinaryCostInPoints(), offer.discountClosesOn(),
                offer.yourHoldLapsesAt(), offer.yourPlaceInTheQueue(),
                offer.contents().stream().map(BundleMemberResponse::of).toList());
    }
}
