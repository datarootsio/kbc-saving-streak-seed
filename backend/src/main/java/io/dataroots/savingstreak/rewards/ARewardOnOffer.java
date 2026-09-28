package io.dataroots.savingstreak.rewards;

/**
 * One entry in the catalogue as the rest of the application sees it: what to name it back, what to
 * show a person, and what it costs them.
 *
 * <p>A record rather than the enum it is built from, and that is the whole of this change. An enum
 * handed out is a promise that the catalogue is a closed set known at compile time, and every
 * caller that accepted one quietly took that promise on: the web layer parsed a code into a
 * constant, the claim record stored a constant, and a fifth reward could not exist without all
 * three of them being recompiled. Handing out a record instead says the only true thing — that this
 * is a row of the catalogue as it stands right now — and leaves the module free to change where the
 * rows come from without anybody outside noticing.
 *
 * <p>The code is the identity and the only part of this a caller may send back. Title and
 * description are words for a person, so that a page renders the catalogue without knowing anything
 * about what is in it; the cost is the backend's figure, for the reason the API client already
 * gives — a page that named a price could name the wrong one.
 *
 * <p>There is deliberately no voucher prefix here. What a voucher code is built out of is the
 * module's own business and nothing outside it has ever needed to know, so it stays behind the
 * service with everything else about how a claim is recorded.
 */
public record ARewardOnOffer(String code, String title, String description, long costInPoints) {
}
