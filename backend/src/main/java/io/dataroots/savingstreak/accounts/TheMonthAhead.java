package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * What one current account has to cover between today and this day next month: what is in it, what
 * is due to arrive, what is due to leave, and what that leaves.
 *
 * <p><strong>The figure this whole feature exists to put in front of somebody.</strong> A balance on
 * its own says "you have 2480 euros"; this says "you have 2480 euros and 1165 of it is spoken for",
 * which is the sentence a customer needs <em>before</em> they decide how much to sweep into savings
 * rather than after. Saving is a claim on money other things also want, and until the other claims
 * are on the screen there is no judgement to make.
 *
 * <p><strong>The arithmetic is here rather than on the page, and it adds up exactly.</strong>
 * {@code leavesYou} is {@code balance + incomeDue - billsDue} to the cent, so a page draws four
 * figures that a customer can check against each other with a pencil. A page doing its own
 * subtraction would be a second place this application decides what a month costs, and the two would
 * disagree the first time either changed.
 *
 * <p><strong>{@code billsDue} counts what is already owed as well as what is still to fall.</strong>
 * An arrear is a claim on this balance exactly as a standing bill is — the difference is only that
 * it is overdue, and the nightly run offers the money to it <em>first</em>. A month-ahead figure
 * that quoted only the dates still to come would tell a customer carrying two rents that they had
 * room they do not have, which is the opposite of what this read is for. {@code arrearsOutstanding}
 * says how much of the figure that is, so the page can name it rather than leaving somebody to
 * wonder why the total is larger than their bills.
 *
 * <p><strong>{@code leavesYou} may be negative, and that is the answer rather than an error.</strong>
 * A balance never goes below nought — the run refuses rather than overdrawing — but what a month has
 * to cover can plainly exceed what is there, and rounding that up to nought would hide exactly the
 * month a customer needs to be warned about. A negative figure is a month that cannot be paid in
 * full unless something changes.
 *
 * <p><strong>Derived on every read and stored nowhere</strong>, as every other preview in this
 * codebase is: a stored projection goes stale the moment a balance, a bill or a declaration changes,
 * and then needs invalidation rules that are themselves a source of bugs. Change anything and the
 * next read says something else, with nothing to invalidate.
 *
 * <p>{@code from} and {@code until} are sent rather than left to be inferred, for the reason the
 * year-ahead window is: an account with nothing falling due would otherwise leave a page unable to
 * say how far "nothing" reaches, and a page working out what day it is would draw a month nobody is
 * in on a wound clock.
 */
public record TheMonthAhead(LocalDate from, LocalDate until, BigDecimal balance,
                            BigDecimal incomeDue, BigDecimal billsDue,
                            BigDecimal arrearsOutstanding, BigDecimal leavesYou) {

    /**
     * How far ahead this looks, in one place.
     *
     * <p>A calendar month rather than a count of days, so that every monthly thing an account
     * carries falls in it exactly once whatever the length of the month: a rent on the 31st and a
     * salary on the 25th are each counted once in February and once in March.
     */
    public static final Period HOW_FAR_AHEAD_THE_MONTH_LOOKS = Period.ofMonths(1);

    /**
     * The day the window opens: the day the application's clock reads, in the zone it counts
     * calendars in.
     *
     * <p>Today rather than this moment, for the reason {@code TimelineHorizon} gives about its own:
     * everything placed in the window is a day, and a window that opened at a time of day would put
     * a bill falling due this afternoon fractionally behind its own start.
     */
    static LocalDate opensOn(Instant now) {
        return now.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
    }

    /**
     * The day it closes: the day before this day next month, or the last day of next month when
     * next month has no such day.
     *
     * <p><strong>Ordinarily a day short of a whole calendar month, and that is on purpose.</strong>
     * The range this names is inclusive of both ends, so a window running to <em>this</em> day next
     * month would hold thirteen 25ths in a year and would count a rent twice for any bill falling on
     * today's day of the month. A month a customer is asked to plan for holds each of their monthly
     * claims once, which is what makes the figure at the bottom of it a figure they can act on.
     *
     * <p><strong>The exception is the whole of this method, and subtracting a day without it is a
     * bug this was sent back for.</strong> {@link LocalDate#plus(java.time.temporal.TemporalAmount)}
     * clamps to the shorter month's last day <em>before</em> anything is taken off it, so
     * {@code 31 March + 1 month - 1 day} is 29 April: a window one day short of the month it claims
     * to be, losing a day for every day the next month is short. Everything falling on the days that
     * dropped off contributed nothing at all, because its occurrence this month is already behind
     * the cursor and its next one lands one day past the top — an account with a bill on the 30th
     * quoted a month with no rent in it, and one with a payday on the 30th quoted a month with no
     * salary in it and drew the red shortfall warning on a month that was fine. So when the clamp
     * fires, the last day of that shorter month <em>is</em> the day before the day that does not
     * exist, and the window closes there.
     *
     * <p>What that makes true, for every day the clock can read: <strong>nothing a month has to
     * cover is ever left out</strong> — every day of the month from the 1st to the 31st falls inside
     * this window at least once, clamp and all — and <strong>nothing is counted that will not
     * actually be taken</strong>. A claim can be in it twice only where the clamp fired and its
     * earlier date is one the nightly run has not reached yet, and then both really do leave the
     * account before the window is out: a rent on the 31st with today reading 31 March is taken
     * tonight and again on 30 April.
     *
     * <p><strong>One consequence of that is meant to stay, and it is worth naming because it looks
     * like the bug above and is not.</strong> On roughly five days a year — the 29th of a February,
     * 30 April, 30 June, 30 September, 30 November — a bill on a later day of the month has had
     * <em>today's</em> debit clamped forward onto the opening day, and its next one then falls more
     * than a month out and is outside this window. A rent on the 31st read on 29 February 2028
     * closes at 28 March and does not hold the 31 March debit. That is not a claim gone missing: the
     * clamped occurrence is dated the opening day, which is <em>inside</em> the window, so it is in
     * the figure right up until the 02:30 run takes it — and once the run has taken it the money has
     * already left the balance the card is quoting beside it. What the card then says is that the
     * coming month holds no more of that bill, which is true, because the calendar the run walks
     * really does put 31 days between those two debits. Widening the window to catch the later one
     * would be quoting a rent twice in a month that only pays it once.
     *
     * <p>Counted in calendar months rather than in days, so that the window is a month however long
     * this month happens to be — the same clamp every other monthly thing in this application gets,
     * read the way {@code WhenABillIsDue} reads it rather than a second rule about short months.
     *
     * <p>The year-ahead bar's own {@code TimelineHorizon} does not subtract the day, which is why a
     * bill on today's day of the month can show thirteen times over twelve months there. That window
     * belongs to the saving-rules preview and is shared with the rule occurrences drawn beside the
     * bills; giving the bills on that bar a different last day from the rules on the same bar would
     * be a worse disagreement than the one it fixes, so it is left where it is.
     */
    static LocalDate closesOn(LocalDate opensOn) {
        LocalDate thisDayNextMonth = opensOn.plus(HOW_FAR_AHEAD_THE_MONTH_LOOKS);
        boolean nextMonthHasNoSuchDay = thisDayNextMonth.getDayOfMonth() != opensOn.getDayOfMonth();
        return nextMonthHasNoSuchDay ? thisDayNextMonth : thisDayNextMonth.minusDays(1);
    }
}
