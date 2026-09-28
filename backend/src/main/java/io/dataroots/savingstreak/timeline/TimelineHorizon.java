package io.dataroots.savingstreak.timeline;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * How far ahead an account's bar looks, and the two days it is drawn between.
 *
 * <p>Twelve months, and the figure is not arbitrary: it is the span that makes the bar
 * <em>complete</em>. Every batch of points that has not expired was earned within the last twelve
 * months, so its anniversary falls within the next twelve; every deposit that still holds money pays
 * on an anniversary within the next twelve, whatever its age, because they recur annually. So a
 * twelve-month window holds every surviving batch's departure and every remaining deposit's next
 * payment exactly once. There is nothing past the right-hand edge, and the screen can say so.
 *
 * <p>Which is a property rather than a coincidence, and it is worth saying out loud that it holds
 * only while both of those rules are twelve months. Reprice either one longer and this bar quietly
 * stops being the whole of what is coming and becomes a window onto part of it, and the sentence on
 * the screen promising otherwise becomes false. This is the one place to notice that.
 *
 * <p>Named here rather than read off {@code PointsExpiry} or {@code LoyaltyAnniversary}, and
 * deliberately not shared with either. Those two periods decide what a customer is owed and when;
 * this one decides how much of a screen to draw. Reading one of them would make a repricing of the
 * expiry rule silently change the length of the bar as well — and would pick, arbitrarily, which of
 * the two modules this screen belonged to.
 *
 * <p>Calendar months rather than a count of days, counted in the zone the application counts
 * calendar things in — the same reading both of the rules on the bar get, so that a marker due on
 * the last day of the window is inside it rather than a day past the end.
 *
 * <p><strong>Public, and read by the one other part of this application that looks forward.</strong>
 * The saving rules' twelve-month preview quotes this window rather than restating twelve months of
 * its own, so that the two forward-looking screens a customer can open look equally far: a bar that
 * ended in September beside a list of transfers that ran to December would be two answers to "what
 * is coming", and somebody would have to find out by counting which of them was the shorter. It is
 * quoted rather than copied for the reason {@code WhichOccurrencesAreDue} borrows
 * {@code SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN} — quoting a constant and two functions of their
 * arguments is not reading a module, because there is no repository, no entity and no state in this
 * one — and it is the same direction the paragraph above argues about: this class decides how much
 * of the future to draw, and now says so to both of the screens that draw it.
 *
 * <p>What it still refuses to do is read {@code PointsExpiry} or {@code LoyaltyAnniversary}, which is
 * the coupling the paragraph above is about and is unchanged. Repricing either of those rules must
 * not silently change the length of anybody's window; this is still the one place to notice that,
 * and there are now two screens to notice it for.
 *
 * <p><strong>It is a display horizon and it is not the scheme.</strong> Worth saying plainly now
 * that the bank publishes a scheme in dated versions and one of the figures on it is how long a
 * batch of points lasts, which is seeded at twelve months and is changeable from a screen. The two
 * twelves are not the same twelve. This one decides how much of the future to paint; that one
 * decides when somebody's points go. A prose audit of the screens went looking for sentences that
 * had a policy figure written into them and found three, all about the points lifetime and all now
 * reading it from {@code GET /api/scheme} — and this constant, and {@code TheWeeksAhead}'s six,
 * were deliberately left exactly as they were. Reading the published lifetime here would be the
 * coupling the paragraphs above have refused twice already, arriving by the back door and dressed
 * as tidying up.
 *
 * <p>What it does mean is that the property argued at the top — that there is nothing past the
 * right-hand edge — is now one a published version of the scheme can end, by naming a lifetime
 * longer than this window. The screen no longer promises otherwise: the sentence under the bar
 * names the day the window closes and the lifetime the scheme publishes, and claims nothing about
 * what is past the edge. This is still the one place to notice the question.
 *
 * <p>What it says out loud about its own module's screen is the pair of days on
 * {@link AccountTimeline}.
 */
public final class TimelineHorizon {

    /** How much of the year ahead a bar shows, in one place. */
    public static final Period HOW_FAR_AHEAD_THE_BAR_LOOKS = Period.ofMonths(12);

    private TimelineHorizon() {
    }

    /**
     * The day the window opens: the day the application's clock reads, in the zone it counts
     * calendars in.
     *
     * <p>Today rather than this moment, because everything placed on the bar is a day. A window that
     * opened at a time of day would put a marker due this afternoon fractionally behind its own
     * start.
     */
    public static LocalDate opensOn(Instant now) {
        return now.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
    }

    /**
     * The day it closes: twelve calendar months after it opened.
     *
     * <p>Added to the day rather than to the moment, so that the window's length is a whole number
     * of calendar months however the clocks moved inside it. A window opening on 29 February closes
     * on 28 February, which is the day a calendar gives and the same clamp both rules on the bar
     * apply to their own anniversaries.
     */
    public static LocalDate closesOn(LocalDate opensOn) {
        return opensOn.plus(HOW_FAR_AHEAD_THE_BAR_LOOKS);
    }
}
