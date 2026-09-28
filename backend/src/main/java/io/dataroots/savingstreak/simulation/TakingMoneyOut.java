package io.dataroots.savingstreak.simulation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import io.dataroots.savingstreak.deposits.AConditionInTheWay;
import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.products.WhatAnAgreementStopsOnADay;

/**
 * An amount taken back out of savings on a day, and the change this whole feature is most worth
 * having for.
 *
 * <p><strong>Five rules move at once, and a customer can do one of them in their head.</strong> The
 * balance falls, which is the obvious one and the only one anybody predicts. Then: the deposits the
 * money is drawn from hold less, so every anniversary ahead of the withdrawal pays a smaller tenth,
 * because {@code LoyaltyRate} pays on what a deposit <em>still holds</em> rather than on what landed
 * in it. The week's net new saving falls by the amount, so a week that was going to be secured may
 * not be, and a run that ends drops every euro paid in afterwards from the top of the ladder back to
 * the bottom of it. Money paid back in later earns <em>nothing</em> on the euros that merely climb
 * back to the high-water mark, because {@code TheMostEverSaved} has already paid for them. And the
 * goals whose allocations were counting on that balance are the reason the withdrawal may not be
 * able to take all of it. Five consequences, one press, and the application knew all five of them
 * before the customer did.
 *
 * <p><strong>Not one of those five is written here.</strong> This record carries an amount and a
 * day, says how much comes out on a morning, and stops. Where the euros come from, what the
 * anniversaries then pay, what the week comes to and what the next deposit earns are all
 * {@link TheNightReplayed}'s, because the order of a night is written down in exactly one place and
 * a change that reached into the ledger itself would be a second place. That is what makes this kind
 * compose with the others: a branch that takes five hundred out <em>and</em> saves twenty-five a week
 * is both questions asked of every morning, and the second one earns nothing for as long as the
 * first one's gap is being filled — which is the sentence the problem statement is about, and nobody
 * had to write it.
 *
 * <p><strong>Asking for more than the branch has is an outcome rather than a refusal.</strong> A
 * real withdrawal is all or nothing because a real customer can retype it; a branch cannot be
 * retyped by anybody, and refusing here would make the most interesting question in the set
 * unaskable — "what would happen if I took out more than I have free" is exactly the question
 * somebody about to do it is asking. So the fold takes what is free, dates a
 * {@code A_WITHDRAWAL_FALLS_SHORT} carrying what it managed, and carries on. What is free is the
 * money no goal has spoken for and not the balance, because a goal's claim stops a withdrawal in a
 * branch in precisely the way {@code WithdrawalsService} stops one in the application.
 *
 * <p><strong>The mark does not come back down.</strong> Nothing here touches what the customer has
 * ever earned on, and that is the rule rather than an omission: {@code TheMostEverSaved} says out
 * loud that taking money out leaves the mark exactly where it was, which is the whole of why a
 * withdrawal can leave the points ledger alone and the whole of why the money coming back earns
 * nothing. A branch that lowered the mark when the money left would have paid the customer a second
 * time for the same euros and would have told them a withdrawal is free.
 *
 * <p>Both fields may be missing or nonsense on the way in, deliberately, exactly as
 * {@link SavingMoreEachWeek}'s are: a change is built out of whatever was typed and refused in one
 * sentence by {@link #whyItCannotBeAsked} before a single day of any branch is folded, so nothing
 * below is ever asked of a change that has not been found askable.
 */
public record TakingMoneyOut(BigDecimal amount, LocalDate takenOn) implements AnAdjustment {

    @Override
    public AKindOfAdjustment kind() {
        return AKindOfAdjustment.TAKE_MONEY_OUT;
    }

    @Override
    public String asAsked() {
        return "TAKE_MONEY_OUT amount=" + (amount == null ? "not said" : amount.toPlainString())
                + " on=" + (takenOn == null ? "not said" : takenOn);
    }

    /**
     * Why taking money out cannot be asked about: the amount is not an amount of money, or the day
     * it would come out on is not a day inside the year this simulation is drawn over.
     *
     * <p>The first sentence is {@code AmountOfMoney}'s own and it is asked about a
     * <em>withdrawal</em>, so somebody who typed "500,00" into this box meets the words a real
     * withdrawal of "500,00" would have met rather than a second wording of one objection. Which
     * direction the movement is in is the only thing this kind has to say about it, and saying it is
     * the difference between "A withdrawal has to be an amount of more than zero" and a sentence
     * about a deposit the customer was not making.
     *
     * <p><strong>And then whatever the agreement puts in the way of it, judged on the day the money
     * would actually come out.</strong> A fixed term that has not matured refuses every euro; a
     * notice account refuses what the notice already given does not cover. Those are refusals rather
     * than outcomes, and that is the difference between them and the paragraph above: a withdrawal
     * bigger than the branch holds is a true thing that happens and the customer is shown what it
     * came to, whereas a withdrawal out of a locked account is a thing that cannot happen at all —
     * and a branch that folded it anyway would show somebody a year in which they spent money the
     * bank was never going to give them.
     *
     * <p><strong>In the withdrawal screen's own words, and on the branch's own day.</strong> The
     * sentence is {@code WhatAnAgreementStopsOnADay}'s, which is the same rule
     * {@code TheConditionsOnTheWayOut} refuses a real withdrawal with — one form of words, met here
     * and there, for the reason every refusal in this module gives. The day it is judged on is the
     * day the customer said the money comes out rather than today, which is the only honest
     * reading: a term that is up in four months does not refuse a withdrawal somebody has asked
     * about in six, and notice given last week has run by the time a branch reaches next month.
     *
     * <p><strong>Asked after the window and not before it.</strong> A day outside the twelve months
     * is a question this simulator cannot draw at all, so it is answered first; only a day the fold
     * would actually walk is worth putting to the agreement.
     *
     * <p>What is <strong>not</strong> here is how much the branch holds. A withdrawal larger than
     * the branch's free money is an outcome the fold reports and not a refusal, for the reason this
     * record's own documentation gives, and it could not be decided here in any case: what a branch
     * holds on a day in March is a thing three hundred nights of arithmetic arrive at, and this is
     * asked once, of the present, before any of them has been walked. The conditions above are
     * different in exactly that respect: they turn on the agreement and on a date, both of which
     * the snapshot carries, and on neither of which a night of folding has any effect.
     */
    @Override
    public Optional<String> whyItCannotBeAsked(TheStartingPoint standing) {
        if (amount == null) {
            return Optional.of("Say how much you would take out.");
        }
        Optional<String> notAnAmount = AmountOfMoney.whyItIsNotOne("withdrawal", amount);
        if (notAnAmount.isPresent()) {
            return notAnAmount;
        }
        Optional<String> outsideTheWindow = AnAdjustment.whyThatDayIsOutsideTheWindow(
                "The day the money comes out", takenOn, standing);
        if (outsideTheWindow.isPresent()) {
            return outsideTheWindow;
        }
        return WhatAnAgreementStopsOnADay.whatStopsTakingOn(
                        standing.theProductItIsOn().termMonths(),
                        standing.theProductItIsOn().maturesOn(),
                        standing.theProductItIsOn().earlyExitPenaltyDays(),
                        standing.theProductItIsOn().noticeDays(),
                        standing.theNoticeStanding().notices(),
                        amount, takenOn)
                .map(AConditionInTheWay::reason);
    }

    /**
     * All of it, on the one day the customer named, and nothing on any other day.
     *
     * <p>One day and not a standing arrangement: a customer who takes money out every month is
     * describing a life rather than a decision, and the question this kind answers is "what does
     * <em>this</em> cost me". Two withdrawals in one branch are two changes, both asked of every
     * morning, which is what the list of changes is for.
     *
     * <p>What comes out is what is asked for; what actually leaves is the walk's answer, because
     * only the walk knows what the goals have left free on that morning.
     */
    @Override
    public BigDecimal whatItTakesOutOn(LocalDate day) {
        return day.equals(takenOn) ? amount : BigDecimal.ZERO;
    }

    /**
     * Adopting a branch that takes money out moves no money at all, and says so: a withdrawal is a
     * thing the customer carries out on the day.
     *
     * <p><strong>This is the refusal the whole feature is for, stated as a sentence instead.</strong>
     * A customer who has read what five hundred out would cost them — the bonus it reprices, the
     * week it loses, the euros that will earn nothing on the way back up — and who then presses a
     * button, must not have the five hundred taken out from under them by a projection screen. There
     * is no undo on this application's ledger and there is no confirmation on this press, so the one
     * safe reading of "I will have this one" is that the branch's arithmetic was accepted and the
     * withdrawal was not made.
     *
     * <p><strong>And it is not scheduled either.</strong> This application has no future-dated
     * withdrawal, and inventing one here would be the simulator growing a write nothing else in the
     * application has — a standing instruction to take money out on a day, made from a question
     * somebody asked. A withdrawal is one press on the account's own screen on the morning it is
     * wanted, and the sentence names the day so that the customer knows which morning.
     *
     * <p>The figure is quoted as it was typed rather than as money read to the cent. Nothing was
     * moved, so there is nothing to quote to the cent, and the customer is being reminded of what
     * they asked about rather than told what a ledger did.
     */
    @Override
    public AChangeThePlanNowCarries adoptedThrough(ThePressesACustomerWouldHaveMade presses) {
        return AChangeThePlanNowCarries.yoursToCarryOut(AKindOfAdjustment.TAKE_MONEY_OUT,
                "Taking EUR " + (amount == null ? "—" : amount.toPlainString()) + " out on "
                        + (takenOn == null ? "the day you choose" : takenOn)
                        + " is yours to carry out. No money has been moved and nothing has been "
                        + "diarised: take it out of this pot on the day, from the account's own "
                        + "screen.");
    }
}
