package io.dataroots.savingstreak.products;

import java.time.LocalDate;

/**
 * A term whose terms said to wait keeps its product and earns what free savings earns, from the
 * morning it matured until its holder does something about it.
 *
 * <p><strong>The rule exists because "keeps its product" and "earns the term rate" are not the same
 * sentence, and the spec asks for the first without the second.</strong> A twelve-month fixed term
 * pays its headline rate for locking money away for a year. Once the year is up and the money is
 * free to leave at any moment, nothing is being locked away and there is nothing to pay for: an
 * account left sitting on a matured term at the term's own rate would be the best instant-access
 * account the bank sells, paid for a promise that expired. So the product stays — the account's
 * agreement still names the term it served, and the record of what it was on is undisturbed — and
 * the rate it earns from that morning is the ordinary one.
 *
 * <p><strong>Derived, never stored, and that is the point of it.</strong> There is no "waiting" flag
 * and no column saying a maturity went by: whether this account is a term that has matured and been
 * left where it is, is three facts about rows that already exist — its version locks money away, its
 * version says to hold, and its maturity has passed. That is the same line the term reading itself
 * holds about maturity, and it buys the same thing: the rate does not depend on the nightly sweep
 * having run. A trainer who winds the clock a year forward and prices a month before settling any
 * maturities gets the same answer as one who settles first.
 *
 * <p><strong>Only {@code HOLD} reaches this at all.</strong> A rolling term has a maturity in the
 * future again by the time anything reads it, so the last condition is false; an account moved to
 * instant access is on free savings, so the first is. Holding is the one ending that leaves an
 * account visibly on a term it is no longer serving, which is why it is the one ending that needs a
 * rule about what it earns.
 *
 * <p><strong>Which version of free savings, and why the maturity day rather than today.</strong>
 * The account fell onto the ordinary rate on the morning its term was up, so the terms it fell onto
 * are the ones the bank was selling that morning — the same rule a roll-over follows when it pins
 * the version current on its own day, and the same rule an account moved to instant access follows.
 * Reading today's version instead would silently move a waiting account onto every rate change the
 * bank published afterwards, which is the one thing this module exists to make impossible. Choosing
 * the version is the caller's job, because it needs the catalogue and this rule needs nothing; what
 * is decided here is the day to choose it on, which travels back as part of the answer.
 *
 * <p>A class of its own rather than a method on the interest sweep, because it is a rule about
 * agreements that the sweep merely obeys — and because the sentence "a matured term left waiting
 * earns the free-savings rate" is the sort of thing that gets re-decided slightly differently the
 * second time somebody needs it. There is nothing to construct and nothing to inject.
 */
final class AWaitingTermEarnsTheFreeSavingsRate {

    private AWaitingTermEarnsTheFreeSavingsRate() {
    }

    /**
     * The day this account fell onto the free-savings rate, and nothing at all when it has not.
     *
     * <p>Answers the maturity day for an account whose version locks money away, says to hold at the
     * end of it, and whose term was already up when the period being priced began. Empty for every
     * other account in this application, which is nearly all of them — including a term that has
     * matured but rolls over or moves, because neither of those leaves an account sitting on a
     * matured term.
     *
     * <p>The day is what comes back rather than a plain yes, because the caller needs it twice: to
     * choose the version of free savings that was on offer on it, and to say in the log which
     * morning the account stopped earning its term rate. Two calls for one fact would be two places
     * the maturity could be worked out.
     *
     * @param itsTerms the version the account is living under, never the one on the shelf
     * @param theTermRunsFrom the day that term started running, which is not always the day the
     *                        account was opened
     * @param periodBegan the first day of the stretch of time being priced
     */
    static LocalDate theDayItFellOntoTheOrdinaryRate(ProductTerms itsTerms,
                                                     LocalDate theTermRunsFrom,
                                                     LocalDate periodBegan) {
        if (itsTerms.termMonths() == 0 || itsTerms.maturityAction() != MaturityAction.HOLD) {
            return null;
        }
        LocalDate maturedOn = TheTermAnAccountIsLockedInto.whenTheTermIsUp(
                theTermRunsFrom, itsTerms.termMonths());
        return periodBegan.isBefore(maturedOn) ? null : maturedOn;
    }
}
