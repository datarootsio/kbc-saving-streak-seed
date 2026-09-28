package io.dataroots.savingstreak.support;

import java.util.List;

/**
 * What the API says one savings account would be taking on if its holder took its product's newer
 * terms: the version they are on, the version on offer, whether there is anything newer at all, and
 * what differs between the two.
 *
 * <p>Shared for the same reason as {@link BalancesView}: a second copy of the shape can drift into
 * disagreeing about it, and then one of them is testing a contract nobody serves.
 *
 * <p>{@code whatWouldChange} is read as the backend's own sentences and never taken apart. The whole
 * claim under test is that one function in the backend words a difference and that both the account's
 * reading and the product's version history render from it — a test that rebuilt the sentence from
 * two figures could not tell one function from two, which is precisely the thing it exists to tell.
 * So the assertions compare the list with the history's list, and look for the figures inside the
 * strings.
 *
 * <p>{@code newerTermsExist} is boxed, so that a test can tell "the API said there is nothing newer"
 * apart from "the API did not send this field", which are the same value and very different
 * failures.
 */
public record TheNewerTermsView(Long savingsAccountId, String productCode, String productName,
                                Integer theVersionYouAreOn, Integer theVersionOnOfferToday,
                                Boolean newerTermsExist, List<String> whatWouldChange) {
}
