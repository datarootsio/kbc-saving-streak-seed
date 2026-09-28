package io.dataroots.savingstreak.support;

/**
 * One thing inside a bundle, as every one of the three surfaces that draws one reports it.
 *
 * <p>One view for all three, unlike the offer itself, because a line of a bundle says the same
 * thing to a customer, to whoever composed it and to somebody at a till — the backend sends one
 * record to all three for the same reason, and three copies here would be three places to add a
 * field to.
 *
 * <p>The quantity is a primitive, because there is no such thing as a line of a bundle that does
 * not say how many: the backend refuses one before the offer is ever written.
 */
public record BundleMemberView(String code, String title, int quantity) {
}
