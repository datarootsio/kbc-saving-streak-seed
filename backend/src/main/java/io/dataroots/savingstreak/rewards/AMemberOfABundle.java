package io.dataroots.savingstreak.rewards;

/**
 * One line of a bundle as somebody composing one asks for it: an offer's code, and how many of it
 * go in.
 *
 * <p>A record of its own rather than two parallel lists on {@link ANewOffer}, for the reason
 * every parameter object in this module exists: two lists that have to be the same length are two
 * lists that one day are not, and the answer to "which quantity belongs to which code" would be
 * an index rather than a field.
 *
 * <p>Both fields arrive exactly as they were typed and are judged by the module. Whether the code
 * names an offer that exists, whether that offer is itself a bundle, whether it has been
 * withdrawn and what the least a quantity may be are all rules about the catalogue, and a record
 * that refused any of them would be the web layer deciding a little of it — the same line
 * {@link ANewOffer} draws about titles and prices.
 *
 * <p>The quantity is boxed, and a missing one is refused rather than read as one of it. "Two
 * cinema tickets" and "a cinema ticket" are different bundles, and a form that quietly filled in
 * the number somebody left out would be this application guessing at the contents of a hamper.
 * Nought and below are refused too: nought of something is not something that is in a bundle.
 *
 * <p>Public, because it arrives on {@link ANewOffer}, which the web layer builds.
 */
public record AMemberOfABundle(String code, Integer quantity) {
}
