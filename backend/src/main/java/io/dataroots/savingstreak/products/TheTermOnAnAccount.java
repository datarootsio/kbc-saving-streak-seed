package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Everything one savings account's term says today: how long it was locked for, the day it matures,
 * how long is left, and what breaking it now would cost.
 *
 * <p><strong>One reading rather than four, because they are one answer read against one
 * clock.</strong> How long is left and whether the day has come are two readings of the same
 * subtraction, and what breaking costs is a fraction of a balance that moves — a page that fetched
 * them separately could be told three things that were true a second apart, and the figure beside
 * the button would not be the figure the button charges. The same argument
 * {@link TheNoticeOnAnAccount} makes about notice, and the same argument the agreement's own reading
 * makes about travelling with the account rather than being looked up beside it.
 *
 * <p><strong>{@code termMonths} is what tells a page whether to draw the panel at all.</strong>
 * Nought is free savings, the core saver and the notice account: no lock, no maturity date, nothing
 * to break. Saying it as a figure rather than as an absence is what lets one component render every
 * savings account without first asking what kind it is — and it is why the door this comes through
 * answers for every account rather than refusing the ones with no term.
 *
 * <p><strong>{@code whatBreakingWouldCost} is quoted before anything is confirmed, and it is the
 * figure that will actually be charged.</strong> That only holds because both are worked out the
 * same way off the same balance, in the same class, floored the same direction — which is exactly
 * why the price is not assembled by whoever is drawing the screen out of a rate and a number of
 * days. A customer who is told a price and charged another one has been told nothing.
 *
 * <p><strong>It is nought on an account with nothing in it and on a term that has matured.</strong>
 * Both are true rather than absent: there is nothing to charge a penalty on, and there is nothing
 * left to break. A null would make a page decide what an absent price meant, and the two cases mean
 * different things that both come to nought euros.
 *
 * <p><strong>And there is no rate on it</strong>, which is the line {@code AnAgreementResponse}
 * already holds about the same panel: a rate that lived on two screens would have to be kept in step
 * on the day an account takes its product's newer terms. What a customer needs here is the price
 * and the number of days it was worked out over, both of which are on it; the rate is on every
 * interest posting, beside the balance it was applied to.
 */
public record TheTermOnAnAccount(

        /** The savings account this is about. */
        long savingsAccountId,

        /** How long the money was locked for, in months, and nought when it was never locked. */
        int termMonths,

        /** The day the term is up, and null when the account is not on a term at all. */
        LocalDate maturesOn,

        /** Whether the day has come, which is true on the maturity date itself as well as after. */
        boolean matured,

        /** How many days are still to run, and nought once the day has come. */
        long daysLeft,

        /** What the account holds, which is what the price below is a fraction of. */
        BigDecimal balance,

        /** Days of interest breaking costs, straight off the terms, and nought without a term. */
        int earlyExitPenaltyDays,

        /** What breaking would cost today, in euros, worked out exactly as it would be charged. */
        BigDecimal whatBreakingWouldCost,

        /**
         * What the terms this account was opened under say happens on the day the term is up, and
         * null on an account that is not on a term.
         *
         * <p>Off the version the account is <em>living under</em> and never the one on the shelf,
         * which is the whole of what makes it worth showing: the bank may be selling a twelve-month
         * fixed term that moves to instant access at the end, while this account agreed to one that
         * rolls over, and the screen that told its holder the wrong one would be telling them their
         * money comes free on a morning it does not.
         *
         * <p>The name rather than only the sentence beside it, because a screen has reasons to
         * branch on the ending that are not about printing it — a panel that wants to warn about a
         * roll-over differently from a move should not be matching on the text of a sentence.
         */
        MaturityAction maturityAction,

        /**
         * That ending as a sentence a customer reads, naming the day, and null without a term.
         *
         * <p><strong>Written here rather than assembled by whoever is drawing the screen.</strong>
         * The ticket's words are "in the words the terms use", and the terms are this module's: a
         * page that turned {@code ROLL_OVER} into a sentence of its own would be a second statement
         * of what an agreement says, kept in step by hand, and the day it fell behind a customer
         * would be told the wrong ending in perfectly confident prose. It is the same bargain every
         * refusal in this application makes — the sentence comes from the rule that knows.
         */
        String whatHappensAtMaturity) {

    /**
     * Whether money is actually locked away right now, so that no caller has to decide what the
     * combination of a term and a date means.
     *
     * <p>The one question a screen greying a button is asking, and the one question the withdrawal
     * gate answers with a refusal. Both read it here rather than each writing {@code termMonths > 0
     * && !matured} in their own words.
     */
    public boolean locked() {
        return termMonths > 0 && !matured;
    }
}
