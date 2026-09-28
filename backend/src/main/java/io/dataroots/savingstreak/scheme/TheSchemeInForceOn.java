package io.dataroots.savingstreak.scheme;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Which of the scheme's published versions is the one in force on a given day.
 *
 * <p><strong>Derived, never stored.</strong> A "current version" column, or a flag on a row, would
 * be a second place the answer lives, and two stored figures that must agree eventually stop
 * agreeing — the day somebody publishes a version and the flag is not moved, the bank advertises one
 * ladder and the deposits landing that morning are priced by another. This application already holds
 * that line for a points balance, for a goal's status, for every offer window and for a savings
 * product's terms, and the comparison here is cheaper than any of those. It is also what makes the
 * feature need no midnight job: the date on the row <em>is</em> the activation, resolved at read
 * time.
 *
 * <p><strong>The rule is: the highest version whose day has come.</strong> A version dated ahead of
 * today has been published but is not deciding anything yet, which is how a change announced on a
 * Thursday for the Monday after next is written down without anybody having to remember to press
 * something. Among the versions that have taken effect, the highest number wins rather than the
 * latest date — the two agree whenever versions are published in order, which is always, and
 * ordering by version is what makes two rows sharing a Monday a settled question rather than a coin
 * toss. That tie-break is load-bearing rather than incidental: it is the whole of how a version
 * announced for next Monday is corrected without an edit door existing, because publishing another
 * version for the same Monday simply wins.
 *
 * <p><strong>A history with nothing yet in force still answers, with its lowest version.</strong>
 * The same fallback {@code TheTermsOnOfferToday} makes, and here it is doing more work than it does
 * there. This application's clock moves in both directions, and a trainer who winds it back far
 * enough would otherwise reach a day on which no scheme had been published — and a week judged
 * under no scheme at all is a week with no threshold, no ladder and no answer to "did I secure it".
 * The seed dates version 1 on a Monday long before any data precisely so that this case cannot
 * arise from an honest database; the fallback is what keeps it survivable in one somebody has
 * edited, and the lowest version is the only honest thing to fall back to, because it is what the
 * scheme was written with.
 *
 * <p>A class of its own rather than a method on the service, because it is a pure function over a
 * list and a date, it is asked by everything that reads the scheme, and the rule it states — what
 * "in force" means — is the sort of thing that gets re-decided slightly differently in each of three
 * call sites. There is nothing to construct and nothing to inject, which is also what makes it
 * testable at the calendar cases an API test would need weeks of clock-winding to reach.
 *
 * <p>It works over {@link TheSchemeAsPublished} rather than over the entity, which is where it
 * parts company with its counterpart in the products module. The reason is the one the spec gives
 * about the streak derivation: what crosses this module's boundary is the <em>history</em>, because
 * a run of weeks spans versions and one set of figures could never be the right argument. So the
 * history is a public value holding published records, {@link TheSchemeEachWeekWasJudgedUnder} asks
 * this function per week, and the rule is written down once for both sides of the boundary rather
 * than once inside and once outside.
 */
final class TheSchemeInForceOn {

    /** Highest version last, so that the newest is the end of the list. */
    private static final Comparator<TheSchemeAsPublished> BY_VERSION =
            Comparator.comparingInt(TheSchemeAsPublished::version);

    private TheSchemeInForceOn() {
    }

    /**
     * The version deciding things on that day, out of every version the bank has published.
     *
     * @param published every version of the scheme, in any order and never empty
     * @param day       the day the question is asked about — today for a reading, a week's own
     *                  Monday for a week being judged
     * @throws IllegalArgumentException when handed no versions at all, which is a bank whose scheme
     *                                  was never written down and is a broken database rather than
     *                                  a customer's mistake — there is no sentence to refuse
     *                                  somebody with, because nobody did anything wrong
     */
    static TheSchemeAsPublished outOf(List<TheSchemeAsPublished> published, LocalDate day) {
        if (published.isEmpty()) {
            throw new IllegalArgumentException(
                    "no version of the scheme has been published, so there is nothing in force on "
                            + day);
        }
        return published.stream()
                .filter(version -> version.hasStartedBy(day))
                .max(BY_VERSION)
                .orElseGet(() -> published.stream().min(BY_VERSION).orElseThrow());
    }
}
