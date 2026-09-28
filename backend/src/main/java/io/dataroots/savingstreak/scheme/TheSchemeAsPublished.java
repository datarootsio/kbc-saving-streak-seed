package io.dataroots.savingstreak.scheme;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One published version of the scheme, as the rest of the application reads it: every figure this
 * bank has decided about saving, the Monday it started applying, and one line saying what changed.
 *
 * <p><strong>The same figures as the row, in the units everything outside this module
 * speaks.</strong> The columns are integers — basis points and cents — because that is how a rate
 * and an amount are held without either of them acquiring a scale somebody chose by accident. Not
 * one of those integers gets out. What arrives here is a euro amount quoted through the money
 * vocabulary this application already speaks, a multiple of one, a percentage a page can print, and
 * a count of months or days.
 *
 * <p><strong>A record rather than the entity.</strong> The entity is a row with an identifier and a
 * lifetime; this is a value that answers "what does the scheme say". Handing the entity out would
 * hand out its identifier, which nothing outside should name, and would leave every reader holding
 * something JPA might still be managing — and, worse here than anywhere, would hand out a mutable
 * handle on a figure four other modules are about to be priced from.
 *
 * <p><strong>Flat, with one exception, and the exception is argued.</strong> Twelve figures side by
 * side rather than grouped into a week, a ladder, a points policy and a set of notification
 * thresholds. Grouping reads better in a sentence and worse in a diff: a preview comparing two
 * versions figure by figure walks a flat list once, and a page pre-filling a form from the version
 * in force reads one field per box. The one exception is {@link #balanceRungs}, which is a list
 * because it is genuinely a list — an ordered collection of one kind of thing, of a length nobody
 * has fixed — and flattening it would mean six columns and a seventh version of the scheme the day
 * somebody wanted a seventh rung.
 *
 * <p><strong>Nothing here is nullable except the line saying what changed, and even that is
 * written.</strong> Zero is not the absence of a rule in any of these figures, and that is the
 * difference between this record and a product's terms: a weekly threshold of nought is a week that
 * secures itself, an ordinary rate of nought is a deposit that earns nothing, and a points lifetime
 * of nought is points that expire the moment they are earned. Every one of them is a figure the
 * scheme states rather than a rule it may be without, which is why the door that publishes a
 * version will require all of them rather than carrying any forward.
 *
 * <p><strong>{@link #whatChanged} is required rather than null on a first version</strong>, which
 * is where this record parts company with {@code ASetOfTerms}. A product's first version says
 * nothing changed because nothing did, and a customer reading it is reading the opening terms of an
 * agreement they chose. The scheme has no version anybody opted into: it applies to everybody from
 * its Monday, so every version of it — including the seeded one — owes the customer a sentence
 * saying what it is and why. The seed writes its own.
 */
public record TheSchemeAsPublished(

        /** Which version of the scheme this is, counting from one across the whole bank. */
        int version,

        /**
         * The Monday it takes effect.
         *
         * <p>A {@link LocalDate} rather than an instant, and always a Monday: a savings week runs
         * Monday to Sunday, and a scheme that changed on a Wednesday would judge one week under two
         * rules. Which version applies on a given day is worked out from this and never stored.
         */
        LocalDate effectiveFrom,

        /** What a week has to take in, net, to secure itself — {@code 50.00} is EUR 50. */
        BigDecimal weeklyThreshold,

        /** What the first week of a run pays per euro, as a multiple — {@code 1.0000}. */
        BigDecimal theOrdinaryRate,

        /** What each further week of a run adds to it, as a multiple — {@code 0.1000}. */
        BigDecimal extraForEachFurtherWeek,

        /** Where the ladder stops climbing, as a multiple — {@code 1.5000}. */
        BigDecimal theMostAStreakPays,

        /** How long a batch of points lasts, in whole months — 12. */
        int howLongABatchOfPointsLasts,

        /**
         * The balance rungs a customer is congratulated on reaching, ascending, in euros.
         *
         * <p>Ascending because every reading of the ladder walks it in order, and the list is the
         * order: a version that published them the other way round would be a ladder whose rungs
         * are in the wrong places rather than a list that needs sorting on the way out.
         */
        List<BigDecimal> balanceRungs,

        /**
         * The share of a budget at which it is said to be running low, as a percentage —
         * {@code 80.00} is four fifths.
         *
         * <p>A percentage rather than the fraction the rule compares against, for the reason
         * {@code BasisPointsOfTheScheme} gives: this is the figure somebody types and the figure a
         * page prints, and the division by a hundred belongs to whoever does the comparing.
         */
        BigDecimal whatShareOfABudgetIsRunningLow,

        /** How many bills outstanding at once is arrears piling up — 3. */
        int howManyOutstandingIsASpiral,

        /** How many days before a maturity it is worth saying so — 30. */
        int daysBeforeAMaturityIsWorthSaying,

        /** How many days before an anniversary it is worth saying so — 30. */
        int daysBeforeAnAnniversaryIsWorthSaying,

        /** One line saying what this version changed and why, and never blank. */
        String whatChanged) {

    /**
     * Whether this version's day has come on the given day, which is the whole of what "in force"
     * means here.
     *
     * <p>Not stored and not a column: a version announced for a Monday still to come is published,
     * readable and not applying to anything, and the only thing that changes on its Monday is the
     * answer to this question. {@link TheSchemeInForceOn} is where the rule that picks one out of
     * several is argued; this is the same comparison asked of a single version, so that a caller
     * holding one does not reimplement the "not after" that decides it.
     */
    public boolean hasStartedBy(LocalDate day) {
        return !effectiveFrom.isAfter(day);
    }
}
