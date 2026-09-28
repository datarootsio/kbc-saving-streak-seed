package io.dataroots.savingstreak.deposits;

import java.time.LocalDate;

/**
 * The agreement behind a savings account as this module reads it: which version of its terms money
 * landing in it is priced under, and the day that agreement ended, if it has.
 *
 * <p><strong>One reading rather than two questions, and that is the whole of this record's
 * reason to exist.</strong> {@link TheTermsADepositLandsUnder} used to answer with a bare version
 * number, and closing the door on a deposit into an account whose agreement has ended could have
 * been a second method beside it — {@code whenTheAccountWasClosed} — asked of the same keeper about
 * the same row. It is not, for two reasons. The first is that the two facts are decided together:
 * one row says both, read once, so a deposit cannot be refused on a reading of the agreement taken
 * at one moment and stamped with a version read at another. The second is that a second method is a
 * second thing this module would have had to learn to ask, and the one promise
 * {@link TheTermsADepositLandsUnder} makes is that Deposits knows nothing about products beyond
 * what a single answer hands it. Widening the answer keeps that promise; adding a question spends
 * it.
 *
 * <p><strong>A date rather than a flag</strong>, because the refusal names the day. A customer told
 * only that an account is closed has to go and find out when, and the day is what tells them which
 * account they are looking at — somebody who holds three of them closed one of them in March. The
 * keeper of agreements already stores the day for exactly that reason, so a boolean here would be
 * this module asking for less than it needs and then needing it.
 *
 * <p><strong>Nothing at all is still a possible answer</strong>, and it means what it has always
 * meant: an account written by a release older than the catalogue and not yet reached by the
 * start-up migration. Such an account is not closed — it is unrecorded — so the absence of this
 * reading is never a refusal, and a deposit into it is stamped with no version, exactly as before.
 *
 * <p>Public because it crosses the boundary this module declares and the module that keeps the
 * agreements implements.
 */
public record WhatAnAccountIsLivingUnder(

        /** Which version of the product's terms the account is living under, counting from one. */
        int version,

        /** The day the agreement ended, and null while the account is still open. */
        LocalDate closedOn) {

    /**
     * Whether the account's agreement has ended, so that no caller has to decide what a null date
     * means. The same helper the keeper's own reading carries, said again here rather than reached
     * for across the boundary.
     */
    public boolean isClosed() {
        return closedOn != null;
    }
}
