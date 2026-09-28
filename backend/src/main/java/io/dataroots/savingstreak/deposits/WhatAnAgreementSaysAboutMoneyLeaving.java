package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Whatever keeps the agreement a savings account was opened under, asked the one question this
 * module cannot answer for itself: is there anything stopping this money leaving?
 *
 * <p><strong>Declared here and implemented elsewhere, which is the whole point of it.</strong> The
 * device is the one Accounts already uses to ask who may pay into an account nobody holds, and the
 * argument is the same argument. Deposits records what moved and which deposits it came out of; it
 * has no business knowing that this bank sells four products, that a product publishes versions of
 * its terms, or that one of them asks for thirty-two days' notice. A {@code WithdrawalsService}
 * that asked the Products module directly would learn all three, and the two services would then
 * want each other at start-up, because Products reads this module's ledger to find out when an
 * account was first paid into. The dependency runs the only way it can: Deposits states the
 * question, Products answers it, and the answer arrives in a vocabulary Deposits already has — an
 * amount, a sentence and a named condition.
 *
 * <p><strong>One question, asked once, answering with one reason.</strong> Not "is the term up",
 * "has notice run" and "is there enough above the floor" as three calls: asked separately they have
 * to be asked in the right order to mean anything, and this module would be holding a rule about
 * how to use Products instead of an answer. The order lives with the conditions, in
 * {@link ConditionOnTheWayOut}, and the caller here is entitled to be ignorant of it.
 *
 * <p><strong>The second method is a telling and not a second question.</strong> Some conditions are
 * spent by being met: a withdrawal that ran on notice consumes that notice, oldest first, and a
 * withdrawal that breaks a fixed term ends it. Only the module that knows what the condition was
 * can say what meeting it costs, and only this module knows the money actually moved — so the
 * conversation has two halves and both belong on the interface that names the relationship. Kept
 * apart as two interfaces, a caller could ask without ever telling, and a notice would be good for
 * an unlimited number of withdrawals.
 *
 * <p><strong>Injected as the interface and never as the thing behind it.</strong> That is what
 * keeps this module free of any knowledge that a product exists, and it is what keeps the two
 * services out of a start-up cycle. One implementation today and no registry of them: a second
 * keeper of agreements is a change to make when there is one.
 */
public interface WhatAnAgreementSaysAboutMoneyLeaving {

    /**
     * What, if anything, stops this much money leaving that savings account today.
     *
     * <p>Only ever asked about an account that exists and holds the money — both settled by
     * Deposits before the question is put — so an implementation has only the agreement to consult
     * and never has to second-guess a balance it did not sum.
     *
     * <p>An account with no agreement on record is answered as an account with nothing in the way,
     * rather than as a refusal. Such an account was written by a release older than the catalogue
     * and has not yet been reached by the start-up migration, which is a window measured in the
     * milliseconds before the web server binds its port — and refusing a withdrawal because a
     * migration has not run would be this application holding somebody's money over its own
     * bookkeeping.
     *
     * @param savingsAccountId an existing savings account that holds at least {@code amount}
     * @param amount           what the customer asked to take out, an amount of money
     * @return the one condition in the way, or empty when nothing is
     */
    Optional<AConditionInTheWay> whatStopsTaking(long savingsAccountId, BigDecimal amount);

    /**
     * Tells the agreement that this much money has now left the account, so that whatever the
     * withdrawal ran on is spent.
     *
     * <p>Called after the money has moved and inside the same transaction, so a withdrawal that
     * failed to be written spends nothing and a notice that was consumed cannot outlive the
     * withdrawal that consumed it.
     *
     * <p>Called on every successful withdrawal, including from an account with no condition on it
     * at all: whether there is anything to spend is the implementation's question, and a caller
     * that tried to decide it would be the second place this application knows what a notice period
     * is.
     *
     * @param savingsAccountId the account the money left, which
     *                         {@link #whatStopsTaking} has already answered about
     * @param amount           what left it
     */
    void moneyHasLeft(long savingsAccountId, BigDecimal amount);
}
