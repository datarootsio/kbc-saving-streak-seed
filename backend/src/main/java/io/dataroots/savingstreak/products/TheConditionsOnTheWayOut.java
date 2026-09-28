package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.util.Optional;

import io.dataroots.savingstreak.deposits.AConditionInTheWay;
import io.dataroots.savingstreak.deposits.WhatAnAgreementSaysAboutMoneyLeaving;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.deposits.AmountOfMoney.asMoney;

/**
 * Every condition an agreement can put in the way of money leaving, asked in one place and in one
 * order — the Products module's side of the question Deposits states.
 *
 * <p><strong>This class is the whole of the seam.</strong> Deposits declares
 * {@link WhatAnAgreementSaysAboutMoneyLeaving} and injects it as the interface; this answers it and
 * is the only thing in this application that knows the order the conditions are asked in. Nothing
 * outside this module implements the interface, and nothing outside this class asks
 * {@link NoticesService} whether a withdrawal may go ahead — a second caller would be a second
 * answer, given without the conditions it did not know to ask about.
 *
 * <p><strong>The order is the design, and it is written as a sequence of returns.</strong> A term
 * that has not matured, then notice that has not run, then a floor to keep: the first one with
 * something to say is the answer and the rest are never asked. One reason at a time, which is what
 * makes the sentence a customer reads something they can act on rather than a list they have to
 * triage. A method that gathered every objection and handed back a list would be pushing that
 * triage onto whoever called it, and whoever called it is a module that cannot tell a maturity date
 * from a notice period.
 *
 * <p><strong>Two of the three are asked now, and the third may never refuse anything at all</strong>
 * — a minimum balance withholds the bonus rate rather than holding somebody's money. The slice that
 * added maturity added a line above the notice line and changed nothing else, which is exactly what
 * the shape was built for: it did not get to relitigate whether a locked account should be refused
 * for its lock or for its notice first. The term is asked first because it is about every euro in
 * the account at once, and a customer told about notice first would give notice on money that was
 * locked away anyway.
 *
 * <p>Package-private, like every implementation of a declared interface in this codebase. What
 * Deposits holds is the interface; what Spring injects is this; and no third party can reach for it
 * by name.
 */
@Service
class TheConditionsOnTheWayOut implements WhatAnAgreementSaysAboutMoneyLeaving {

    private static final Logger log = LoggerFactory.getLogger(TheConditionsOnTheWayOut.class);

    /**
     * The lock a fixed term puts on money, asked first.
     *
     * <p>The lock rather than {@link FixedTermsService}, and that is not a detail. Breaking a term
     * charges money out of the ledger, so the service that breaks one holds the module that writes
     * withdrawals — and that module holds this class, through the interface Deposits declares.
     * Reaching for the breaking service from here would close a circle the application context
     * refuses to start on. What a gate needs is two rows and a clock, which is what
     * {@link TheTermAnAccountIsLockedInto} has.
     */
    private final TheTermAnAccountIsLockedInto term;
    private final NoticesService notices;

    TheConditionsOnTheWayOut(TheTermAnAccountIsLockedInto term, NoticesService notices) {
        this.term = term;
        this.notices = notices;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Read as prose: is the term up, then has notice been given and has it run, then is there
     * enough above the floor, which by this bank's reading is never a reason to refuse anybody their
     * own money.
     *
     * <p>Read-only and transactional, because it walks rows and is called from inside the
     * transaction the withdrawal is being written in. It writes nothing: the spending of whatever
     * the withdrawal runs on is {@link #moneyHasLeft}, on the other side of the money actually
     * moving, so a refused withdrawal leaves every notice exactly as it was.
     */
    @Transactional(readOnly = true)
    @Override
    public Optional<AConditionInTheWay> whatStopsTaking(long savingsAccountId, BigDecimal amount) {
        // A term that has not matured is asked first, above the notice below it, because it is
        // about every euro in the account at once: a customer told about notice first would give
        // notice on money that was locked away for another eight months anyway. Breaking the term
        // is how that refusal is answered, and it is a door of its own rather than a withdrawal
        // that pays a price without anybody having been shown one.
        Optional<AConditionInTheWay> locked = term.whatATermStops(savingsAccountId, amount);
        if (locked.isPresent()) {
            return locked;
        }
        Optional<AConditionInTheWay> notice = notices.whatNoticeStops(savingsAccountId, amount);
        if (notice.isPresent()) {
            return notice;
        }
        // A floor is asked last and answers nothing, by the reading this bank takes of a minimum
        // balance: a dip under it costs the bonus rate for the period that dipped and never the
        // money. {@link ConditionOnTheWayOut#A_FLOOR_TO_KEEP} has a name so that the order this
        // class exists to fix has somewhere to put it, and for no other reason.
        //
        // The bonus it withholds is paid now — TheRateAPeriodIsPaidAt decides it and the monthly
        // sweep writes it down — so there is a live rule here and still no arm below. That is not
        // an omission waiting to be filled in: a rule that both refused the withdrawal and withheld
        // the bonus could only ever do one of them, because a refused withdrawal never takes the
        // balance under the floor and there would be nothing left for the withholding to punish.
        // A withdrawal that empties a core saver to nought succeeds, and the month it happened in
        // is paid the headline rate. Anybody adding a floor arm here is deleting the feature.
        log.debug("the agreement has nothing in the way of a withdrawal savingsAccountId={} "
                + "amount={}", savingsAccountId, asMoney(amount));
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Spends the notice the withdrawal ran on, oldest first. A floor spends nothing, because
     * nothing was met to spend.
     *
     * <p><strong>And a term spends nothing either, which is worth saying rather than leaving as a
     * missing line.</strong> Notice is <em>met</em> by a withdrawal and is consumed by meeting it;
     * a term is one lock over the whole balance and is either on, in which case nothing left
     * through this door at all, or off, in which case there is nothing left to spend. The charge
     * for breaking one is taken by the door that breaks it, before the money moves and on the
     * balance the customer was quoted against — a charge taken here would be priced against a
     * balance the withdrawal had already reduced, so the figure shown before confirming and the
     * figure taken afterwards would differ by however much was withdrawn. What the term does here
     * instead is check its own invariant out loud, which {@link TheTermAnAccountIsLockedInto}
     * argues.
     *
     * <p>Asked in the same order as above, for consistency rather than necessity: the two arms
     * touch different rows, but a reader following this seam should not have to hold two orders in
     * their head.
     */
    @Transactional
    @Override
    public void moneyHasLeft(long savingsAccountId, BigDecimal amount) {
        term.moneyHasLeft(savingsAccountId, amount);
        notices.moneyHasLeft(savingsAccountId, amount);
    }
}
