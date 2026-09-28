package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.loyalty.LoyaltyRate;

/**
 * Everything the agreement one savings account is living under decides, gathered into one answer:
 * what it pays on the money, what it pays on the points, what it asks for on the way out and how
 * far the interest sweep has already got with it.
 *
 * <p><strong>One read rather than six, and that is what it is for.</strong> Every figure here is
 * already answerable somewhere in this module — the rate off the version of record, the floor off
 * the same row, the maturity date off {@code TheAgreementAnAccountIsOn}, how many periods have been
 * judged off the postings. A caller that asked for them separately would be asking six questions
 * about an account whose agreement could be taken newer between any two of them, and would fold a
 * year out of halves that describe different agreements. The what-if simulator is that caller: its
 * whole design is a snapshot read once and then never re-read, and {@code TheStartingPoint} argues
 * the point at length.
 *
 * <p><strong>Rates in the units the rules that consume them work in, and never in the units the
 * screens print.</strong> The interest rates are integer basis points a year and the floor is long
 * cents, because {@link WhatAnAnnualRateIsWorth} and {@link TheRateAPeriodIsPaidAt} take those and
 * because a percentage would have to be divided by a hundred somewhere nobody wrote down. The
 * points multiplier is a plain multiple of one — {@code 1.2500} is a quarter more — because that is
 * what {@code TheRateADepositIsPaidAt.combining} multiplies by. The anniversary rate is a fraction
 * of a point per whole euro — {@code 0.1200} is twelve percent — because that is what
 * {@link LoyaltyRate#pointsOn(long, BigDecimal)} multiplies whole euros by. Those last two are the
 * unit trap this feature has warned about since ticket 9: the same rate has three honest spellings
 * in the rows behind this record, and two of them would pay a hundred and ten thousand times too
 * much without anything throwing. The conversion happens once, on this side of the boundary, where
 * the number still has its unit attached. Nothing here is a percentage and nothing here is for
 * printing; {@code ASetOfTerms} is the record that prints.
 *
 * <p><strong>{@code openedOn} and {@code interestCountsFrom} are two dates and not one.</strong>
 * The first is where the monthly periods are counted from and is the day the agreement began; the
 * second is the day this bank started paying interest on this account, which for an account
 * migrated onto the catalogue is the day the migration ran rather than the day the account was
 * opened. A period that began before the second earns nothing and is passed over rather than
 * written down, which is exactly what the sweep does and is why no interest is backdated.
 *
 * <p><strong>{@code periodsAlreadyJudged} is a fact about the sweep and not about the
 * account.</strong> It is the highest monthly period this account has a posting for, so a caller
 * projecting forward knows which month to start from — and knows that a period whose day has come
 * and which has no posting is one the application will pay tonight rather than one it has decided
 * against. A fold that counted periods off the calendar alone would pay a month twice on an account
 * whose sweep is up to date, or never pay a month the sweep still owes on a clock somebody wound
 * without running the jobs. It is the same reading {@code rulesSettledThrough} gives for the saving
 * rules and {@code whenEachDepositNextPays} gives for the anniversaries.
 *
 * <p><strong>The condition is carried as its figures rather than as a yes or a no.</strong> Whether
 * money may leave depends on the day it would leave on and on how much of it, neither of which is
 * known when a snapshot is taken, so what travels is the term's length, the day it is up, the price
 * of breaking it and the notice the agreement asks for — and
 * {@link WhatAnAgreementStopsOnADay} is what turns those into the sentence a customer reads,
 * whichever day is being asked about.
 *
 * <p><strong>An account with no agreement on record still gets an answer.</strong> Such an account
 * was written by a release older than the catalogue and has not yet been reached by the start-up
 * migration — a window measured in the milliseconds before the web server binds its port — and
 * {@link #whatAnAccountWithNoAgreementIsOn} is what it is answered with: the rate that pays nothing,
 * the multiple that changes nothing, the tenth every anniversary in this application used to pay,
 * and nothing in the way of the money. That is the same reading
 * {@code WhatAEuroSavedIntoAnAccountIsWorth} and {@code WhatAnAnniversaryPaysHere} already take,
 * and it is taken here for their reason: a projection is being drawn and has to be drawn from
 * something, and an empty would leave the drawing deciding what an absent product pays.
 */
public record WhatAnAccountsProductPaysAndAsksFor(

        /** The savings account this is about. */
        long savingsAccountId,

        /** The product it is on, by the code that never changes, and empty when it is on none. */
        String productCode,

        /** Which of the four shapes of agreement it is, and null when there is no agreement. */
        ProductKind kind,

        /** Which version of that product's terms it is living under, counting from one. */
        int version,

        /** The day the agreement began, which is the day its monthly periods are counted from. */
        LocalDate openedOn,

        /** The day this bank started paying interest on it, and null when it never has. */
        LocalDate interestCountsFrom,

        /** The highest monthly period it has a posting for, and nought when it has none. */
        int periodsAlreadyJudged,

        /** What the agreement pays a year whatever happens, in basis points. */
        int annualRateBasisPoints,

        /** What it pays on top for keeping the floor, in basis points, and nought where it offers none. */
        int bonusRateBasisPoints,

        /** The floor that has to be kept for that bonus, in cents, and nought where there is none. */
        long minimumBalanceCents,

        /** What a euro saved here earns, as a multiple of one — never a percentage. */
        BigDecimal pointsMultiplier,

        /** What an anniversary pays per whole euro, as a fraction of a point — never a percentage. */
        BigDecimal anniversaryRatePerWholeEuro,

        /** Days of warning before money may leave, and nought when the product asks for none. */
        int noticeDays,

        /** How long the term is, in months, and nought on an account that is not on one. */
        int termMonths,

        /** The day the term is up, and null when the account is not on a term at all. */
        LocalDate maturesOn,

        /** How many days of interest breaking the term early costs, for the sentence that says so. */
        int earlyExitPenaltyDays) {

    /**
     * What an account with no agreement on record is projected under: an agreement that pays no
     * interest, changes no points, pays the tenth loyalty has always paid and holds on to nothing.
     *
     * <p>Not a refusal and not an empty, for the reason the record's own documentation gives. Every
     * figure in it is the one the rest of this application already falls back on, so a projection
     * of such an account is exactly the projection it would have been given the day before there
     * were products to vary it.
     */
    public static WhatAnAccountsProductPaysAndAsksFor whatAnAccountWithNoAgreementIsOn(
            long savingsAccountId) {
        return new WhatAnAccountsProductPaysAndAsksFor(savingsAccountId, "", null, 0, null, null, 0,
                0, 0, 0L, BasisPoints.asAMultiple(BasisPoints.ONE_WHOLE_MULTIPLE),
                LoyaltyRate.THE_TENTH_EVERY_ANNIVERSARY_USED_TO_PAY, 0, 0, null, 0);
    }
}
