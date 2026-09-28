package io.dataroots.savingstreak.streaks;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How much new saving has landed in a week, and what the week still asks for.
 *
 * <p>A week of one customer's saving, counting every account they paid into: the figure is what they
 * put away, not what any one goal took in.
 *
 * <p>Net, and that is the rule this record carries: what was put away in the week less what was
 * taken back out during it. A week that took EUR 60 in and let EUR 20 back out has put EUR 40 away,
 * because EUR 40 is what the customer is saving at the end of it.
 *
 * <p>Gross was the earlier rule and it could be farmed: EUR 50 paid in on Monday and taken straight
 * back out again secured the week, so the same fifty euros secured every week for ever and walked a
 * customer up the whole multiplier ladder without their ever saving a cent. Net closes that by
 * construction rather than by another rule — to secure six consecutive weeks, six times the weekly
 * minimum has to have genuinely gone in and stayed in over those weeks.
 *
 * <p>It can therefore be negative: a week in which more came out than went in put nothing away, and
 * saying so as a minus is what makes {@link #stillNeeded} true. A customer EUR 50 down on the week
 * needs EUR 100 before it counts, not EUR 50, and a figure clamped at nothing here would have told
 * them the wrong one.
 *
 * <p>What a week is judged on is not what a deposit earned. A withdrawal takes back no points and
 * never has — what a deposit earned when it landed is history and is never rewritten — so a week
 * turning out not to be secured costs the run it would have counted towards, and nothing else.
 *
 * <p>Nothing here is stored. The figure is summed from the deposit records every time it is asked
 * for, which is what lets the development clock be wound in either direction without leaving a
 * total behind describing a week that is now in the future.
 *
 * <p><strong>The record carries the threshold it was judged against, and there is no other way to
 * build one.</strong> It used to read one constant, because there was one figure and it was true of
 * every week there had ever been; the scheme is now published in versions with a Monday each, so
 * what a week asked for is a fact about <em>that</em> week and belongs to the record describing it
 * rather than to a static field somebody reads afterwards. A week secured under EUR 50 stays
 * secured after a later Monday raises the minimum to EUR 80, and it stays secured because the
 * record says what it was judged against instead of asking again.
 *
 * <p><strong>The constant and the two-argument constructor are gone, and their going is a decision
 * rather than a tidy-up.</strong> They were the bridge for callers that had not been moved over,
 * and a bridge nobody removes is how two thresholds start disagreeing — {@code LoyaltyRate} has
 * been through this migration already and records what it cost to finish. A defaulted constructor
 * left standing would have gone on judging some caller's week at EUR 50 after the bank started
 * asking EUR 80, and nothing anywhere would have objected, because EUR 50 is a perfectly good
 * threshold. So the threshold is now a component with no default and the compiler is what makes
 * every caller name one.
 *
 * <p>The warning that constant carried is now a warning about the component, and it is sharper for
 * it: there is still exactly one place a week is judged against what it asked for, and a caller
 * that wrote its own {@code >=}, or its own "of EUR 50" into a page, would be the second place the
 * threshold is decided.
 */
public record NewSavingsThisWeek(

        SavingsWeek week,

        BigDecimal newSavings,

        /**
         * What this week asks for, carried alongside the progress towards it so that whoever shows
         * the one shows the other without naming the figure itself. A screen that wrote "of EUR 50"
         * into its own markup would be the second place the minimum lives.
         *
         * <p>The week's own, not today's: the threshold published by the version of the scheme that
         * was in force on this week's Monday, which is what makes a run of weeks stand still when
         * the scheme is repriced. Quoted to the cent, like the saving it is compared against.
         */
        BigDecimal weeklyMinimum) {

    /**
     * Euros are quoted to the cent, and a figure that came back off a float has to be put back. This
     * record's own: the amounts it is summed from arrive quoted by the module that holds them, and
     * this is only the scale the total and what is still needed are written in.
     */
    private static final int DECIMAL_PLACES = 2;

    public NewSavingsThisWeek {
        // To the cent on the way in, because the terms came out of SQLite, which has no decimal type
        // and hands an amount back as a float: EUR 12.50 arrives as 12.5, and a sum of those carries
        // whatever scale the last term happened to have. Money is compared by value either way; this
        // is so that the figure reads as money wherever it is written down.
        newSavings = newSavings.setScale(DECIMAL_PLACES, RoundingMode.HALF_UP);
        // The threshold gets the same treatment and for the same reason: it comes out of the
        // scheme's own rows, through the same SQLite, so EUR 50,00 can arrive as 50.0 — and this one
        // is printed on a page beside the saving it is compared against, where two figures of the
        // same kind reading at different scales would look like two different kinds of figure.
        weeklyMinimum = weeklyMinimum.setScale(DECIMAL_PLACES, RoundingMode.HALF_UP);
    }

    /**
     * Whether the week has taken in what it asks for, and so counts towards a streak.
     *
     * <p>The comparison lives beside the figure being compared against, because "at least EUR 50 has
     * landed in it" is the whole definition of a secured week and a caller restating it is a second
     * place the threshold is decided. At least, not more than: a week that took in exactly the
     * minimum has done what the week asked.
     */
    public boolean isSecured() {
        return securedBy(newSavings, weeklyMinimum);
    }

    /**
     * Whether that much net new saving secures a week that asked for that much.
     *
     * <p>Here because whoever walks back through an account's earlier weeks has a total per week and
     * no reason to build a record around each one — and, now that the scheme has versions, has a
     * different threshold to judge each one against. Static so that there is still exactly one
     * comparison deciding what secures a week anywhere in this application: a caller writing its own
     * {@code >=} would be the second place the rule lives, which is the thing this class was pulled
     * out to prevent.
     *
     * <p><strong>There is no form of this that supplies the threshold itself.</strong> The
     * one-argument form was deleted with the constant, because whoever walks back through an
     * account's earlier weeks is asking about weeks that are not today, and reading one threshold
     * for all of them is precisely how a EUR 60 week un-secures itself the morning the minimum
     * rises. Naming the threshold is therefore the only way to ask, and the caller has to have got
     * it from somewhere — which is the whole point of making them say where.
     *
     * <p>At least, not more than: a week that took in exactly what it asked for has done what the
     * week asked. Compared by value with {@link BigDecimal#compareTo}, so a threshold that came back
     * off SQLite's float as {@code 50.0} is the same threshold as the {@code 50.00} that went in.
     *
     * @param netNewSavings   what the week put away, net of what came back out of it
     * @param weeklyThreshold what that week asked for, in euros — from the version of the scheme in
     *                        force on that week's own Monday, never from today's
     */
    public static boolean securedBy(BigDecimal netNewSavings, BigDecimal weeklyThreshold) {
        return netNewSavings.compareTo(weeklyThreshold) >= 0;
    }

    /**
     * Whether this amount is what carried the week over the line: the week has what it asks for now,
     * and would not have had it without this.
     *
     * <p>The amount has to have been counted into the figure already, which is what makes this a
     * question about a deposit that has landed rather than about one somebody is thinking of making.
     * It is the one thing a deposit's own log line can say that the week's figures cannot — "this is
     * the deposit that secured the week" — and it is asked here so that the subtraction is compared
     * against this week's own threshold in the class that owns it rather than at the call site.
     */
    public boolean wasCarriedOverBy(BigDecimal justLanded) {
        return isSecured() && !securedBy(newSavings.subtract(justLanded), weeklyMinimum);
    }

    /**
     * How much more the week needs, and nothing below zero: a week that has taken EUR 80 in needs no
     * more, and reporting that it needs minus thirty would be arithmetic rather than an answer.
     *
     * <p>A week that is down on itself needs more than the minimum, and that is the honest figure
     * rather than an accident: EUR 50 out and nothing in leaves the customer needing EUR 100 before
     * the week counts, because the first fifty only bring them back to level.
     */
    public BigDecimal stillNeeded() {
        BigDecimal outstanding = weeklyMinimum.subtract(newSavings);
        return outstanding.signum() > 0 ? outstanding : BigDecimal.ZERO.setScale(DECIMAL_PLACES);
    }
}
