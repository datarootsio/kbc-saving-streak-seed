package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.util.Optional;

import io.dataroots.savingstreak.deposits.AConditionInTheWay;
import io.dataroots.savingstreak.deposits.AmountOfMoney;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.deposits.AmountOfMoney.asMoney;

/**
 * The one reading behind "what is free to take out of this account today", assembled out of the
 * answers this module already gives and adding no rule of its own.
 *
 * <p><strong>Why it exists at all.</strong> Until now the figure was three answers a screen had to
 * put together: the balance, whether a term has the account locked, and how much ready notice
 * covers. Every screen that wanted it was obliged to learn the order those compose in — that a lock
 * beats notice, that a floor holds nothing back — and a screen holding that order is a second copy
 * of {@link TheConditionsOnTheWayOut}, kept in step by hand. The module that owns the conditions
 * answers the question instead, and whoever is drawing a panel prints a number.
 *
 * <p><strong>The condition is asked, never inferred.</strong> Whether anything at all stands in the
 * way is {@link TheConditionsOnTheWayOut#whatStopsTaking} asked about the whole balance — the exact
 * question a withdrawal of everything would ask — so the sentence the customer reads here is
 * character for character the sentence they would be refused with. Nothing here decides that a
 * floor holds no money back or that a matured term stops nothing: it asks, and prints what it is
 * told.
 *
 * <p><strong>The figure is quoted from the two readings that hold it.</strong> A locked term frees
 * nothing, because a term is one lock over every euro at once; otherwise what is free is what ready
 * notice covers, capped by the balance, because {@code readyToTakeToday} is a ceiling on notice
 * rather than a claim about money the account holds. Both come from the readings ticket 07 and
 * ticket 06 already publish, and neither is recomputed here.
 *
 * <p><strong>An empty account is answered without asking.</strong> There is nothing for a condition
 * to hold, and a locked term does refuse a withdrawal of nought — so asking would hand a screen a
 * warning about a withdrawal nobody could make. Nought free out of nought held, with no condition
 * named, is the honest reading.
 *
 * <p>Public because the door into it is a controller in {@code web}, which is where every other
 * public face of this module — {@code ProductsService}, {@code NoticesService},
 * {@code FixedTermsService} — is reached from. Nothing in the application holds this class but that
 * controller: it is a reading, and no rule depends on it.
 */
@Service
public class TheMoneyAnAgreementLetsGo {

    private static final Logger log = LoggerFactory.getLogger(TheMoneyAnAgreementLetsGo.class);

    private static final BigDecimal NOTHING = AmountOfMoney.quotedToTheCent(BigDecimal.ZERO);

    /**
     * The conditions in the order they are asked, which is the only place that order lives.
     *
     * <p>Held as the class rather than as the interface Deposits declares, unlike everywhere else
     * this is injected. The interface belongs to Deposits and is the vocabulary a withdrawal is
     * weighed in; this is the module that implements it, asking its own implementation a question
     * about itself, from the same package. Going out through Deposits to get back here would be a
     * longer way round to the same object and would put a reading of an agreement behind a module
     * that has no business knowing there is one.
     */
    private final TheConditionsOnTheWayOut conditions;

    private final FixedTermsService terms;

    private final NoticesService notices;

    TheMoneyAnAgreementLetsGo(TheConditionsOnTheWayOut conditions, FixedTermsService terms,
                              NoticesService notices) {
        this.conditions = conditions;
        this.terms = terms;
        this.notices = notices;
    }

    /**
     * What this account's agreement would let leave today, for every savings account and not only
     * for the ones with a condition attached.
     *
     * <p>Free savings answers with the whole balance free and nothing in the way, which is the true
     * answer and the one a screen prints without a warning beside it. Refusing the question would
     * make every screen ask what kind of account it was holding before it dared ask this — the same
     * reading, for the same reason, the notice door and the term door give every account.
     *
     * <p>Read-only and transactional because it walks rows through two readings that each do.
     *
     * @param savingsAccountId an existing savings account, vouched for by whoever asked
     * @return what it holds, what may leave today, and why the two differ when they do
     */
    @Transactional(readOnly = true)
    public WhatCanLeaveToday whatCanLeaveToday(long savingsAccountId) {
        TheTermOnAnAccount term = terms.termOn(savingsAccountId);
        BigDecimal balance = term.balance();
        if (balance.signum() <= 0) {
            log.debug("what can leave a savings account today was read savingsAccountId={} "
                    + "balance={} freeToTakeToday={} condition=none reason=it holds nothing",
                    savingsAccountId, asMoney(balance), asMoney(balance));
            return new WhatCanLeaveToday(savingsAccountId, balance, balance, null, null);
        }
        Optional<AConditionInTheWay> inTheWay = conditions.whatStopsTaking(savingsAccountId, balance);
        if (inTheWay.isEmpty()) {
            log.debug("what can leave a savings account today was read savingsAccountId={} "
                    + "balance={} freeToTakeToday={} condition=none", savingsAccountId,
                    asMoney(balance), asMoney(balance));
            return new WhatCanLeaveToday(savingsAccountId, balance, balance, null, null);
        }
        AConditionInTheWay condition = inTheWay.get();
        BigDecimal free = AmountOfMoney.quotedToTheCent(howMuchTheConditionLeaves(savingsAccountId,
                term, balance));
        log.info("what can leave a savings account today was read savingsAccountId={} balance={} "
                        + "freeToTakeToday={} condition={} reason={}", savingsAccountId,
                asMoney(balance), asMoney(free), condition.condition(), condition.reason());
        return new WhatCanLeaveToday(savingsAccountId, balance, free, condition.condition(),
                condition.reason());
    }

    /**
     * How much is free when something is in the way, quoted from the reading that knows.
     *
     * <p>A term first and notice second, in the order {@link TheConditionsOnTheWayOut} asks them,
     * so that an account which is both locked and asks for notice is answered by the lock — which
     * is the condition the customer would actually be refused under. Two orders, one here and one
     * there, would disagree on exactly that account.
     *
     * <p>A term that has the money locked frees nothing: it is one lock over every euro at once,
     * which is why there is no fraction of it to work out. Breaking it is a door of its own, priced
     * on its own panel, and the price is not a withdrawal.
     *
     * <p>Otherwise it is what ready notice covers, capped by the balance. The cap matters because
     * notice is given on an intention rather than on money that is there — somebody may give notice
     * on five hundred euros while holding two hundred — so the uncapped figure would promise money
     * the account does not hold.
     *
     * <p>There is deliberately no floor arm. A minimum balance withholds a month's bonus rate and
     * never a euro of anybody's money, which {@link TheConditionsOnTheWayOut} argues at length; an
     * arm here would be this application's second opinion about that, and it would hold money the
     * withdrawal gate lets go.
     */
    private BigDecimal howMuchTheConditionLeaves(long savingsAccountId, TheTermOnAnAccount term,
                                                 BigDecimal balance) {
        if (term.locked()) {
            return NOTHING;
        }
        return notices.noticeOn(savingsAccountId).readyToTakeToday().min(balance);
    }
}
