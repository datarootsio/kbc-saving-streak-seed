package io.dataroots.savingstreak.web;

/**
 * What somebody at a counter sends when they hand a voucher over: which counter they are, and
 * nothing else.
 *
 * <p>Free text, because there is no list of counters in this application and inventing one in
 * order to validate a field would be building a directory to look rigorous. It is required all the
 * same: a voucher marked used by nobody is a redemption nobody can ask about afterwards, and the
 * point of recording it is that somebody can.
 *
 * <p>The voucher itself is in the path rather than in here. It is the thing being acted on, it is
 * what the reading before this one was addressed with, and a code in a URL is how the two requests
 * stay obviously about the same voucher.
 */
record UseVoucherRequest(String counter) {
}
