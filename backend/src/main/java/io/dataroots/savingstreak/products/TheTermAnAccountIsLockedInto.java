package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import io.dataroots.savingstreak.deposits.AConditionInTheWay;
import io.dataroots.savingstreak.deposits.ConditionOnTheWayOut;
import io.dataroots.savingstreak.streaks.SavingsWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.deposits.AmountOfMoney.asMoney;

/**
 * The lock a fixed term puts on money: when it comes off, how long is left, and what it says to a
 * withdrawal asked for before then.
 *
 * <p><strong>The arithmetic of a maturity date lives here and nowhere else.</strong> The day a term
 * is up is the day the account was opened plus the months its terms name, clamped — the same
 * clamping the anniversary rule already does, so a twelve-month term opened on the 29th of February
 * matures on the 28th of the following February rather than on a date that does not exist.
 * {@link WhatEachSavingsAccountIsOn} draws that date onto every reading of an agreement and asks
 * this class for it rather than keeping a second copy, because two places deciding when a term is up
 * is two answers on exactly one morning of the year.
 *
 * <p><strong>Nothing is stored about whether a term has matured.</strong> It is the account's
 * opening date and its terms' months against the injected clock, read at the moment somebody asks —
 * the line {@link WhenANoticeIsReady} holds for notice, held here for the same reason and with the
 * same consequence: a trainer who winds the clock a year forward finds the lock off, with no sweep
 * having run and no flag having been set. What happens <em>on</em> the maturity morning — rolling
 * over, moving to instant access, or sitting where it is — is {@link MaturitiesService}'s business;
 * this class only ever answers whether the day has come, and that stays true now the sweep exists.
 * There is still no swept flag and no {@code maturedOn} column: the sweep reads this subtraction
 * like everybody else, and what it writes down is what it <em>did</em> about a maturity rather than
 * that one happened.
 *
 * <p><strong>Why it is a class of its own rather than methods on {@link FixedTermsService}.</strong>
 * That service breaks a term, which means charging money out of the ledger, which means holding the
 * module that writes withdrawals. Deposits' own service already holds <em>that</em> module, and the
 * withdrawal service holds {@code WhatAnAgreementSaysAboutMoneyLeaving} — so a gate that reached
 * the breaking service would close a circle the application context refuses to start on. What the
 * gate needs is two rows and a clock, and those are what this class has; the breaking service is
 * built on top of it and nothing asks the breaking service a question on the way out of an account.
 *
 * <p>Package-private, like every other answer this module gives to a question somebody else asked.
 */
@Service
class TheTermAnAccountIsLockedInto {

    private static final Logger log = LoggerFactory.getLogger(TheTermAnAccountIsLockedInto.class);

    private final AccountAgreementRepository agreements;
    private final ProductTermsRepository terms;
    private final Clock clock;

    TheTermAnAccountIsLockedInto(AccountAgreementRepository agreements,
                                 ProductTermsRepository terms, Clock clock) {
        this.agreements = agreements;
        this.terms = terms;
        this.clock = clock;
    }

    /**
     * What, if anything, this account's term says about taking that much money out today.
     *
     * <p>Answers empty for an account that is not on a term at all — before a single figure is
     * worked out, which is what makes this free for every account that existed before terms did —
     * and empty again for a term whose day has come and gone. Otherwise one condition and one
     * sentence, and the sentence is where the maturity date and the days left go.
     *
     * <p><strong>The whole amount, whatever the amount is.</strong> A fixed term is not a pot with a
     * ceiling on it: the money is not the customer's again until the year is up, so taking one euro
     * out is refused in exactly the words taking a thousand is. There is deliberately no partial
     * reading here, because the spec's is that breaking a fixed term breaks the whole of it — a
     * partly-broken term would be two agreements over one balance.
     *
     * <p><strong>It does not price the break, and the refusal does not quote a figure.</strong> What
     * breaking costs is a fraction of what the account holds, and what it holds is the ledger's
     * answer, which this class deliberately cannot ask for — see the class note about the circle
     * that would close. The sentence sends the customer to the reading that does quote it, which is
     * the reading the screen shows beside the button, which is where a price belongs: beside the
     * thing it is the price of.
     *
     * <p>Package-private because the only caller is {@link TheConditionsOnTheWayOut}, which is the
     * one place the conditions an agreement carries are asked in order. A second caller reaching
     * past it would be a second answer to "may this money leave", asked without the conditions it
     * does not know about.
     */
    @Transactional(readOnly = true)
    Optional<AConditionInTheWay> whatATermStops(long savingsAccountId, BigDecimal amount) {
        Optional<ATermInForce> locked = theTermOn(savingsAccountId);
        if (locked.isEmpty()) {
            log.debug("no term stands in the way of a withdrawal savingsAccountId={} amount={}",
                    savingsAccountId, asMoney(amount));
            return Optional.empty();
        }
        ATermInForce term = locked.get();
        LocalDate today = today();
        // The judgement and the words are WhatAnAgreementStopsOnADay's, asked of today. They used
        // to be written out here, which was fine while this was the only door a customer met a
        // locked term at; the what-if fold is the second, and it has to meet the same sentence
        // about a day that has not happened yet. What stayed here is the reading of the rows, the
        // transaction around it and the line below — none of which a fold can have.
        Optional<AConditionInTheWay> stopped = WhatAnAgreementStopsOnADay.aTermThatHasNotMatured(
                term.termMonths(), term.maturesOn(), term.penaltyDays(), today);
        if (stopped.isEmpty()) {
            log.debug("a term that has matured stands in the way of nothing savingsAccountId={} "
                            + "product={} maturedOn={} on={} amount={}",
                    savingsAccountId, term.productCode(), term.maturesOn(), today, asMoney(amount));
            return Optional.empty();
        }
        long daysLeft = daysLeftOn(term.maturesOn(), today);
        String reason = stopped.get().reason();
        log.warn("a term refuses a withdrawal savingsAccountId={} product={} termMonths={} "
                        + "openedOn={} maturesOn={} daysLeft={} on={} amount={} condition={} "
                        + "reason={}",
                savingsAccountId, term.productCode(), term.termMonths(), term.openedOn(),
                term.maturesOn(), daysLeft, today, asMoney(amount),
                ConditionOnTheWayOut.A_TERM_THAT_HAS_NOT_MATURED, reason);
        return stopped;
    }

    /**
     * Said after money has left an account, and a term spends nothing by it.
     *
     * <p><strong>Notice is spent by being met; a term is not.</strong> A withdrawal that ran on
     * notice consumes it, which is why the telling exists at all. A term is one lock over the whole
     * balance: it is either on, in which case nothing left this account through the ordinary door
     * at all, or it is off, in which case there is nothing left to spend. So there is nothing here
     * to write, and an empty method with that sentence in front of it is the honest version of
     * that — the alternative is the next reader wondering which half of the seam the term forgot.
     *
     * <p><strong>What it does do is check the invariant out loud.</strong> Money leaving an account
     * whose term has not matured is not supposed to be possible: the gate above refuses it, and the
     * deliberate break moves the account onto free savings before a cent moves. If it ever happens,
     * a WARN here names the account and the date it should have been locked until, which is the
     * difference between finding out from this line and finding out from a customer whose locked
     * money left. It costs two rows on a withdrawal from an account that has no term, and nothing at
     * all after the first of them answers empty.
     */
    @Transactional(readOnly = true)
    void moneyHasLeft(long savingsAccountId, BigDecimal amount) {
        theTermOn(savingsAccountId)
                .filter(term -> !hasMatured(term.maturesOn(), today()))
                .ifPresent(term -> log.warn("money left a savings account whose term has not "
                                + "matured savingsAccountId={} product={} maturesOn={} amount={} "
                                + "reason=nothing should be able to do this, because the withdrawal "
                                + "gate refuses it and breaking the term moves the account off its "
                                + "term before anything moves",
                        savingsAccountId, term.productCode(), term.maturesOn(), asMoney(amount)));
    }

    /**
     * The term this account is on, and nothing at all when it is not on one.
     *
     * <p>Nothing for an account with no agreement on record, which is a database the start-up
     * migration has not reached; nothing for an account whose product has no term, which is three
     * of the four the bank sells; and nothing for an account that has been closed, which holds no
     * money to lock. Each of those is an account this class has no business refusing anything about.
     */
    @Transactional(readOnly = true)
    Optional<ATermInForce> theTermOn(long savingsAccountId) {
        return agreements.findBySavingsAccountId(savingsAccountId)
                .filter(agreement -> !agreement.isClosed())
                .map(agreement -> {
                    ProductTerms version = theVersionOfRecord(agreement);
                    return new ATermInForce(agreement, version.asPublished(),
                            version.annualRateBasisPoints());
                })
                .filter(term -> term.termMonths() > 0);
    }

    /**
     * The day a term is up, counted from the day that term started running, and nothing at all for
     * a product with no term.
     *
     * <p>{@code plusMonths} clamps the day of the month, so a twelve-month term started on the 29th
     * of February matures on the 28th of the following February rather than on a date that does not
     * exist — the same clamping the anniversary rule already does, and the same answer.
     *
     * <p><strong>The first argument is the day the term runs from and not, any more, the day the
     * account was opened.</strong> Those were the same date until a maturity could roll an account
     * into another term; they part company the moment one does, and
     * {@link AccountAgreement#theDayTheTermRunsFrom()} argues at length why a roll-over has to move
     * a date of its own rather than the account's opening date. Every caller hands in that reading,
     * which is what makes the second maturity of a rolling term a year after the first rather than a
     * restatement of a day that has already gone.
     *
     * <p>Static and here rather than private to whoever needed it first, because four things need
     * it: the reading of an agreement, which draws the date on every account's panel; this class,
     * which refuses withdrawals against it; {@link MaturitiesService}, which settles a maturity the
     * morning it arrives; and the rule that decides what a matured term left waiting earns. Two
     * copies of a calendar rule disagree on exactly the dates that are hard, which is the ones worth
     * getting right — and a sweep that disagreed with the gate would unlock money the gate was still
     * refusing.
     */
    static LocalDate whenTheTermIsUp(LocalDate theDayTheTermRunsFrom, int termMonths) {
        return termMonths == 0 ? null : theDayTheTermRunsFrom.plusMonths(termMonths);
    }

    /**
     * Whether the day has come, which is true <em>on</em> the maturity date as well as after it.
     *
     * <p>Inclusive of the day itself, for the reading a notice takes of its own: a term of twelve
     * months opened on the 3rd of March is up on the 3rd of March, not on the 4th. The spec says a
     * term set to move to instant access is free "on the morning of its maturity", and this is the
     * line that makes that true.
     */
    static boolean hasMatured(LocalDate maturesOn, LocalDate today) {
        return !today.isBefore(maturesOn);
    }

    /** How many days are still to run, and nought on a term whose day has come. */
    static long daysLeftOn(LocalDate maturesOn, LocalDate today) {
        return hasMatured(maturesOn, today) ? 0 : ChronoUnit.DAYS.between(today, maturesOn);
    }

    /**
     * What breaking a term costs: the terms' penalty days of interest at the headline rate on what
     * the account holds, floored to the cent.
     *
     * <p><strong>The rate arithmetic is quoted rather than written again.</strong>
     * {@link WhatAnAnnualRateIsWorth} is the one place in this module a rate in basis points is
     * turned into euros, and it is the same place the monthly sweep is priced from — because a
     * second place a rate is priced is the first place two rates disagree, and the disagreement
     * would be a customer charged at a rate they were never paid at.
     *
     * <p><strong>The headline rate and never the bonus.</strong> A fixed term has no bonus to add,
     * and the price a customer was told is the headline one: "ninety days of interest at the
     * headline rate" is the sentence the terms are sold with, and a charge worked out at a rate
     * nobody was quoted would be a different agreement.
     *
     * <p><strong>On the balance at the moment of breaking.</strong> Not on the average balance the
     * month was paid on, which is what the interest sweep uses: that figure answers "what was
     * actually there for how long", which is the right question for paying interest on a month that
     * has happened and the wrong one for pricing something that is happening now. The customer is
     * shown this figure before they confirm, and the figure they are shown is the figure they are
     * charged, which only holds if both are read off the same balance.
     */
    static long whatBreakingWouldCost(long balanceCents, int annualRateBasisPoints,
                                      int earlyExitPenaltyDays) {
        return WhatAnAnnualRateIsWorth.overDays(balanceCents, annualRateBasisPoints,
                earlyExitPenaltyDays);
    }

    /** The day this application is standing on, in the one zone it counts its calendars in. */
    LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
    }

    /**
     * The exact version this account is living under, by the pair that addresses it.
     *
     * <p>A broken record rather than a customer's mistake when there is no such version, so it
     * throws rather than refusing in words — the same reading {@link WhatEachSavingsAccountIsOn}
     * makes of the same impossible state. Nobody did anything wrong, and there is no sentence to
     * tell them.
     */
    private ProductTerms theVersionOfRecord(AccountAgreement agreement) {
        return terms.findByProductCodeOrderByVersionAsc(agreement.productCode()).stream()
                .filter(version -> version.version() == agreement.version())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "savings account " + agreement.savingsAccountId() + " is on "
                                + agreement.productCode() + " version " + agreement.version()
                                + ", which has never been published"));
    }

    /**
     * One account's term as everything here reads it: the agreement it comes from and the version
     * of the terms that says how long it is.
     *
     * <p>The pair rather than a copy of the figures, which is the bargain this whole module makes:
     * the row says "twelve-month fixed, version 1" and the version says what version 1 locks money
     * away for. Every question below — when is it up, how much are the penalty days, which product
     * is it — is answered off one of the two rows rather than out of a snapshot that could drift.
     *
     * <p>The version travels as {@link ASetOfTerms}, the reading the rest of this application
     * speaks, with the headline rate in basis points carried beside it. That is the one figure the
     * published reading deliberately does not hand out in its stored unit — it quotes a percentage
     * — and the charge has to be worked out in basis points and cents so that the flooring is the
     * only rounding in it. Carrying it rather than converting a percentage back is what keeps that
     * true.
     */
    record ATermInForce(AccountAgreement agreement, ASetOfTerms itsTerms,
                        int annualRateBasisPoints) {

        long savingsAccountId() {
            return agreement.savingsAccountId();
        }

        String productCode() {
            return agreement.productCode();
        }

        LocalDate openedOn() {
            return agreement.openedOn();
        }

        /**
         * The day the term in force started running, which is the day it last rolled over and the
         * day the account was opened until one does.
         */
        LocalDate termRunsFrom() {
            return agreement.theDayTheTermRunsFrom();
        }

        int termMonths() {
            return itsTerms.termMonths();
        }

        int penaltyDays() {
            return itsTerms.earlyExitPenaltyDays();
        }

        /** What the terms this account was opened under say happens on the day the term is up. */
        MaturityAction maturityAction() {
            return itsTerms.maturityAction();
        }

        LocalDate maturesOn() {
            return whenTheTermIsUp(termRunsFrom(), termMonths());
        }
    }
}
