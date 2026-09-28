package io.dataroots.savingstreak.web;

/**
 * What a customer typed into the box that gives notice: an amount, as characters.
 *
 * <p>Text rather than a number, like every other amount arriving at this API. Whether the
 * characters are a number at all is a question about what was typed and is answered by the
 * controller; whether the number is an amount of money this application will act on is a rule, and
 * it belongs to the domain. A record carrying a {@code BigDecimal} would have the framework answer
 * the first question silently and badly — a body with a word in it would be refused by Jackson,
 * with a sentence written by a library, before anything that owns the rule ever saw it.
 */
record GiveNoticeRequest(String amount) {
}
