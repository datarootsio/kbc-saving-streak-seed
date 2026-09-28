package io.dataroots.savingstreak.deposits;

import java.util.Optional;

/**
 * The two facts this module needs about the agreement a savings account is living under: which
 * version of it the money is landing on, and whether that agreement has ended.
 *
 * <p><strong>Declared here and implemented by whoever keeps the agreements</strong>, which is the
 * device this codebase already uses twice — Accounts states the question a shared pot answers, and
 * the Rewards sweep states the question the customer standings answer. Deposits records money
 * moving and what it earned; it has no business knowing that products exist, that they publish
 * versions, or that free savings was repriced two months ago. A {@code DepositsService} that asked
 * the Products module directly would learn all three, and the two services would then want each
 * other at start-up, because Products reads this module's ledger to find out when an account was
 * first paid into.
 *
 * <p><strong>A version number and a day, and nothing else.</strong> Not the terms, not the rate,
 * not the product's code: a deposit stamps the version it landed under so that what it was priced
 * by can be looked up years later, and a copy of the numbers on every deposit row would be a second
 * place the agreement lived. The number is meaningless without the account it belongs to, and that
 * is the point — the pair is the address, and a deposit already names its account.
 *
 * <p><strong>The day is the day the agreement ended</strong>, and it is here because a closed
 * savings account takes no more money and this module is the one that says no to money. It arrives
 * on the reading this module was already given rather than through a question of its own, so that
 * closing the door cost Deposits nothing it did not already know about products: what a version is
 * and, now, that an agreement can have stopped. {@link WhatAnAccountIsLivingUnder} argues the
 * widening against the alternative of a second method.
 *
 * <p><strong>Nothing at all is a possible answer, and it is not a failure.</strong> An account with
 * no agreement on record is an account written by a release older than the catalogue and not yet
 * reached by the start-up migration, which is a window measured in the milliseconds before the web
 * server binds its port. A deposit that lands in it is recorded with no version rather than with a
 * guessed one, and the migration stamps it on the next start — the same answer
 * {@link Deposit#multiplierApplied} gives about a rate that was never written down.
 */
public interface TheTermsADepositLandsUnder {

    /**
     * What that savings account is living under today: the version its money is priced by, and the
     * day its agreement ended, if it has ended.
     *
     * <p>Both facts on one reading rather than two, for the reasons
     * {@link WhatAnAccountIsLivingUnder} sets out at length — chiefly that they are decided by one
     * row and that a second method would be a second thing this module had to know to ask.
     *
     * @param savingsAccountId the account the money is landing in, which exists
     * @return what the account is living under, or empty when nothing has yet recorded what it is
     *         on — which is an account the start-up migration has not reached and never an account
     *         that is closed
     */
    Optional<WhatAnAccountIsLivingUnder> whatAnAccountIsLivingUnder(long savingsAccountId);
}
