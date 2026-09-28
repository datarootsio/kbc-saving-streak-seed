package io.dataroots.savingstreak.deposits;

/**
 * The one condition standing between a customer and money they asked to take out, and the sentence
 * that says what to do about it.
 *
 * <p><strong>One, never a list.</strong> Whatever answers
 * {@link WhatAnAgreementSaysAboutMoneyLeaving} walks its conditions in
 * {@link ConditionOnTheWayOut}'s own order and stops at the first that has something to say, so
 * this record is the answer to "what is in the way", not "what would be in the way if you fixed
 * this one". A customer told three things at once has to work out which of them to do first, which
 * is a job the module that knows the order should not be handing back.
 *
 * <p><strong>A sentence rather than the figures it is built from.</strong> How many days are left,
 * how much is ready today and which day the money comes free are all worked out by whoever knows
 * the agreement, and only it can put them into words a person can act on. A record of figures would
 * push that wording into Deposits, which cannot tell a notice period from a fixed term and would
 * have to learn both in order to write either sentence.
 *
 * <p><strong>And the condition beside it, which is not decoration.</strong> The sentence is for the
 * person; the constant is for the log and for the page. A reviewer grepping a night's log for why a
 * withdrawal was refused reads {@code condition=NOTICE_THAT_HAS_NOT_RUN} without having to match on
 * prose that a later slice will reword, and a screen that wants to grey the right button reads the
 * same value rather than searching the sentence for the word "notice".
 */
public record AConditionInTheWay(ConditionOnTheWayOut condition, String reason) {
}
