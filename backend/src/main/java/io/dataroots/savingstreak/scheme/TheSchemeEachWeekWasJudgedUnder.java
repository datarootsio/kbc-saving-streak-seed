package io.dataroots.savingstreak.scheme;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * The scheme's whole published history, in hand, answering which version any given week was judged
 * under.
 *
 * <p><strong>This is the thing that crosses the module boundary, and the fact that it is a history
 * rather than a set of figures is the feature.</strong> A run of weeks is re-derived from the whole
 * ledger every time anybody reads it, and it spans months — so handing the derivation "the weekly
 * threshold" would be handing it today's threshold and asking it to judge a week from March. Raise
 * the minimum from EUR 50 to EUR 80 next Monday and every EUR 60 week a customer ever secured would
 * un-secure itself, their current run would shorten, their best-ever run would shorten, and the next
 * deposit they made would be priced at the wrong rate — silently, with nothing logged and nothing
 * able to explain it. That is the failure this whole feature exists to prevent, and this type is
 * where the prevention is shaped: a caller that wants a threshold has to say <em>which week's</em>
 * threshold.
 *
 * <p><strong>Read once and carried, rather than asked per week.</strong> The scheme is read rather
 * than cached — a stored "current version" is a second place the answer lives, and two stored
 * figures that must agree eventually stop agreeing — and the one concession this module makes to
 * that is here: a single read of the history is handed down through a derivation that walks
 * twenty-six weeks backwards, instead of twenty-six reads that could in principle disagree with each
 * other. It is a value with no clock, no bean and no connection behind it, which is what lets the
 * pure derivation stay pure.
 *
 * <p><strong>A week is asked about by its Monday, and the rule is
 * {@link TheSchemeInForceOn}'s.</strong> There is not a second implementation of "which version is
 * in force" here: a week's verdict and today's reading are the same question asked about different
 * days, and two copies of that rule would be two answers the morning somebody published a version
 * for a Monday in the middle of a run.
 *
 * <p>Never empty. A history with no versions in it is a bank whose scheme was never written down,
 * which is a broken database rather than anything a customer did; the constructor says so rather
 * than handing out a value whose every method throws.
 */
public record TheSchemeEachWeekWasJudgedUnder(List<TheSchemeAsPublished> everyVersionPublished) {

    public TheSchemeEachWeekWasJudgedUnder {
        if (everyVersionPublished == null || everyVersionPublished.isEmpty()) {
            throw new IllegalArgumentException(
                    "the scheme's history cannot be empty: no version has ever been published, so "
                            + "no week could be judged under anything");
        }
        everyVersionPublished = List.copyOf(everyVersionPublished);
    }

    /**
     * The version that week was judged under, which is the version in force on the week's own
     * Monday.
     *
     * <p><strong>The Monday and not the Sunday, and not the day the question is asked.</strong> A
     * week is one verdict, so it has to be decided by one scheme, and the Monday is the only day of
     * it that is fixed before anything happens in it — a customer is told on Monday morning what
     * their week asks for, and a version published on the Wednesday cannot be allowed to change the
     * answer. It is also why a version may only ever take effect on a Monday: a mid-week date would
     * make this method a lie by making the week's own start no longer the moment its rule was
     * settled.
     */
    public TheSchemeAsPublished forTheWeekOf(SavingsWeek week) {
        return TheSchemeInForceOn.outOf(everyVersionPublished, week.startsOn());
    }

    /**
     * The version in force on a given day, for the readings that are about a day rather than about
     * a week — what the scheme says today, and what it will say on a date somebody is previewing.
     *
     * <p>Beside the method above rather than folded into it, because "which scheme is in force" and
     * "which scheme judged this week" are the same arithmetic and different questions, and a caller
     * holding a week should not have to turn it into a date to ask.
     */
    public TheSchemeAsPublished onTheDayOf(LocalDate day) {
        return TheSchemeInForceOn.outOf(everyVersionPublished, day);
    }

    /**
     * Every version ever published, newest first, which is the order a history is read in.
     *
     * <p>Newest first here and lowest first in the field, deliberately. The field is in the order
     * the rule walks it and the order the database hands it back; a reader wants the most recent
     * change at the top, because what changed last is what they are looking for. Reversing at the
     * point of reading rather than storing two orders is what keeps there being one list.
     *
     * <p>A copy rather than a view, because the field is immutable and the answer should be too.
     * {@code Collections.reverse} over a copy is the Java 17 spelling of it, written out here so
     * that nobody later reaches for a mutable field to make a one-liner available.
     */
    public List<TheSchemeAsPublished> newestFirst() {
        List<TheSchemeAsPublished> reversed = new ArrayList<>(everyVersionPublished);
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }
}
