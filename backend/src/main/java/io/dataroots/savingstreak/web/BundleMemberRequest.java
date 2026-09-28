package io.dataroots.savingstreak.web;

/**
 * One line of a bundle as the form sends it: the code of an offer that already exists, and how
 * many of it go in.
 *
 * <p>Both arrive exactly as they were typed and neither is judged here. Whether the catalogue has
 * an offer under that code, whether it is withdrawn, whether it is itself a bundle and what the
 * least a quantity may be are rules about the catalogue, and every one of them is refused by the
 * module in a sentence — the same line {@link NewOfferRequest} draws about titles and prices.
 *
 * <p>The quantity is boxed, and a missing one is not read as one of it. "Two cinema tickets" and
 * "a cinema ticket" are different hampers, and a layer that filled in the number somebody left
 * out would be this application guessing at what is in one. It travels as a number rather than
 * as text, like the price and unlike the days: a count of things has no format to get wrong.
 */
record BundleMemberRequest(String code, Integer quantity) {
}
