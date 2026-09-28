package io.dataroots.savingstreak.simulation;

import java.time.LocalDate;
import java.util.List;

/**
 * How one branch of a future turns out: what the customer called it, the two days it is drawn
 * between, and the twelve months it comes to.
 *
 * <p><strong>Named, because an answer nobody can point at cannot be compared.</strong> Four columns
 * on a screen are four arguments, and a customer choosing between them is choosing between the words
 * they typed rather than between "scenario 2" and "scenario 3". The branch where nothing changes is
 * named too, in the customer's own words for it rather than in the application's — {@link
 * TheNightReplayed#THE_YEAR_ALREADY_UNDER_WAY} — because "carrying on as I am" is a decision as much
 * as any of the others and a column labelled "baseline" would quietly make it the absence of one.
 *
 * <p><strong>The window travels with the answer.</strong> Twelve rows with no days on them cannot be
 * placed: a page would have to work out what year it is, and this application's clock can be wound a
 * year ahead of the machine drawing the screen. The two days are {@code TimelineHorizon}'s, arriving
 * here off the snapshot the fold was handed, so that the simulator, the account's bar and the rules'
 * preview all look exactly as far ahead as each other.
 *
 * <p><strong>Always twelve rows, whatever happens in the branch.</strong> A year in which nothing at
 * all moves is twelve rows of unchanged figures rather than an empty list, because a customer
 * comparing columns is reading across them and a short column would be a column that had to be
 * explained. The rows are in date order, oldest first, and the last one closes on {@code until}.
 *
 * <p><strong>The rows are for reading down; {@code thingsThatHappen} is what the customer is
 * actually deciding about.</strong> Twelve balances cannot say that the holiday arrives in June,
 * that a run of six weeks ends on a Sunday in March, or that the money paid back in November earns
 * nothing at all. The two answer different questions, so both are here and neither is added into the
 * other — a month row that tried to carry its own events would make a column of figures into a
 * column of paragraphs, and a list that tried to carry balances would be the rows again in a worse
 * order.
 *
 * <p><strong>Ordered by day, and within a day in the order the night runs them</strong>, which is
 * the order {@link AKindOfThingThatHappens} is declared in and the promise {@code AccountTimeline}
 * already makes about the same kind of list for the same reason. A branch in which nothing worth
 * dating happens is an empty list rather than an absent one, because a page draws a column either
 * way.
 *
 * <p>Nothing here was written down anywhere. A branch is derived, read once and thrown away, and
 * asking for one moves no money, earns no point, expires no batch and creates no rule.
 */
public record HowAScenarioTurnsOut(String called, LocalDate from, LocalDate until,
                                   List<AMonthOfTheFuture> months,
                                   List<AThingThatHappens> thingsThatHappen) {

    /** Copied on the way in, so that an answer cannot be edited after it was worked out. */
    public HowAScenarioTurnsOut {
        months = List.copyOf(months);
        thingsThatHappen = List.copyOf(thingsThatHappen);
    }
}
