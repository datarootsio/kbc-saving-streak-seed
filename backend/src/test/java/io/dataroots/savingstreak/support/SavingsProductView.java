package io.dataroots.savingstreak.support;

/**
 * One savings product as the API reports it, together with the terms it is offering today. Shared
 * for the same reason as {@link BalancesView}: a second copy of the shape can drift into
 * disagreeing about it, and then one of them is testing a contract nobody serves.
 *
 * <p>The kind is text rather than an enum the test imports, for the reason {@link OfferView} gives
 * about a state: a test asserting {@code "FIXED_TERM"} is asserting what actually goes over the
 * wire, and sharing the backend's enum would let a value be renamed on both sides at once with
 * every test still passing.
 *
 * <p>{@code currentTerms} is what the product is offering today and is deliberately not what any
 * account on it is living under. Free savings has published a second version, and a test that read
 * this as "what my account pays" would be asserting the exact confusion this whole feature exists
 * to remove.
 */
public record SavingsProductView(String code, String name, String kind, String description,
                                 int sortOrder, boolean openToNewAccounts,
                                 TermsVersionView currentTerms) {
}
