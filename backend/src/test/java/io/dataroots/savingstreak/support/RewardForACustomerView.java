package io.dataroots.savingstreak.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One catalogue entry as the customer-shaped read reports it: everything {@link RewardView}
 * carries, plus whether this customer can claim it right now and the one reason if they cannot.
 *
 * <p>A third view rather than fields on the reward's, because it is a third endpoint answering a
 * third question — and because {@link RewardView} is the shape a test pins the plain catalogue to,
 * which is the one thing about this feature that must not move.
 *
 * <p>The four limit figures are the two caps as somebody set them, how many this customer has
 * had and how many they may still have. The caps and the remainder are boxed because null is
 * the answer for an offer nobody limited — which is every seeded one — and a primitive would
 * read "no limit" as "none left". How many they have had is a count and is nought when they
 * have had none, which is a different fact and is never null.
 *
 * <p>{@code whatIsLeft} is boxed, because null is the answer for every offer that never runs
 * out — all four of the seeded ones — and a primitive would read that as nought left, which is
 * the opposite fact.
 *
 * <p>{@code lockedBecause} is text rather than an enum the test imports, for the reason
 * {@link OfferView}'s state is: a test asserting {@code "NOT_OPEN_YET"} is asserting what actually
 * goes over the wire, which is what a page reads, and sharing the backend's enum would let a value
 * be renamed on both sides at once with every test still passing.
 * <p>{@code costInPoints} is what this customer would be charged today and
 * {@code ordinaryCostInPoints} is the figure to strike through, which is null whenever there is
 * nothing to strike through — so a test asserting the second is null is asserting that the page
 * draws one price, and a test asserting it is a number is asserting that it draws two. Boxed for
 * exactly that reason: a primitive would turn "no sale on" into a saving of the whole price.
 *
 * <p>{@code yourHoldLapsesAt} is set only for the one customer holding this offer, and null for
 * everybody else — which makes a test asserting it is null an assertion that a hold is somebody
 * else's, and one asserting it is a moment an assertion that the card is showing a countdown.
 * An instant, like the API sends, because a hold is seventy-two hours and not a number of days.
 *
 * <p>{@code yourPlaceInTheQueue} is set only for a customer waiting for this offer and is null
 * for everybody else, so a test asserting it is a number is asserting that the card shows a
 * position and one asserting it is null is asserting that they are not in the line. Boxed for
 * that reason: a primitive would turn "not waiting" into "position nought", which is not a
 * place anybody can be in. It never arrives beside {@code yourHoldLapsesAt} — the two are the
 * two ends of one pipeline, and a promotion moves a customer from the second to the first.
 */
public record RewardForACustomerView(String code, String title, String description,
                                     long costInPoints, boolean claimable, String lockedBecause,
                                     String whyItIsLocked, LocalDate opensOn, LocalDate closesOn,
                                     Integer maxPerCustomer, Integer maxPerCustomerPerWeek,
                                     long howManyYouHaveHad, Long howManyYouMayStillHave,
                                     Integer whatIsLeft,
                                     Long ordinaryCostInPoints, LocalDate discountClosesOn,
                                     Instant yourHoldLapsesAt, Integer yourPlaceInTheQueue,
                                     List<BundleMemberView> contents) {
}
