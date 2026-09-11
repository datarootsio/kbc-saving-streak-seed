package io.dataroots.savingstreak.web;

/**
 * What a trainer or a participant sends to move the application's clock: how many days further on
 * than it already is.
 *
 * <p>Whole days, as a number rather than as the text that was typed — unlike an amount of money,
 * where "25,00" is a mistake worth a sentence about the amount, there is nothing to say about a
 * number of days beyond whether it is one.
 */
record AdvanceClockRequest(Long days) {
}
