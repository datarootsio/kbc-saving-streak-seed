package io.dataroots.savingstreak.budgets;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * Which month a moment belongs to, and where the edges of a month are: the whole of this module's
 * calendar, in one place.
 *
 * <p>A function of its arguments and nothing else — no database, no clock, no state — in the shape
 * {@code WhenABillIsDue}, {@code WhenIncomeIsDue}, {@code WhichOccurrencesAreDue},
 * {@code LoyaltyAnniversary} and {@code HowTheWeeklyMoneyIsSpent} already set. Everything this
 * module derives is derived per month: what a category was allowed to cost, what its bills committed
 * to it, what was spent against it, and — from the next slice — what carried into it. Every one of
 * those readers has to answer "is this moment in March?" the same way, and the only way to make that
 * true is for there to be one answer.
 *
 * <p><strong>The zone is the whole of it.</strong> Every moment this application records is an
 * {@link Instant} and the clock it comes off reads UTC, so nothing underneath this class knows what
 * month it is anywhere. A spend recorded at 23:59:59 on 31 March in Brussels is 21:59:59 on the same
 * day in UTC — which is the easy case — while one recorded at 00:30 on 1 April in Brussels is 22:30
 * on 31 March in UTC, and a month counted off the clock's own reading would file it in March. Both
 * directions are wrong for the same reason: the month a person's money moved in is a question about
 * where that person is standing.
 *
 * <p>The zone is borrowed from {@code SavingsWeek} rather than written out again here, for the
 * reason {@code WhenABillIsDue} and {@code WhenIncomeIsDue} both give when they borrow the same
 * constant: a second copy of "Europe/Brussels" is a copy that can be changed on its own, and a
 * budget counted in a different calendar from the week that pays for it is a month nobody could
 * reconcile. Quoting a constant is not reading a module — there is no repository, no entity and no
 * state in it.
 *
 * <p><strong>A month's window is half open: closed at the bottom, open at the top.</strong> The
 * moment the month begins counts as inside it and the moment the next month begins does not, so
 * every instant in a year falls in exactly one month and no instant falls in two. Closing the top
 * instead — at the last nanosecond, or at the end of the last day — is the shape that produces the
 * bug this method exists to make impossible: a spend recorded in the last second of the last day of
 * a month would fall through the gap between one month's top and the next one's bottom, or land in
 * both.
 *
 * <p><strong>A month is carried as a {@link YearMonth} and stored as the day it begins.</strong>
 * A {@code YearMonth} is what the arithmetic is written in, because "the month after this one" is
 * one call and is right in every December; a {@link LocalDate} at the first of the month is what a
 * row holds, because that is a date and a database has one of those. It is the same bargain the
 * notification record strikes when it carries the month a warning was about in its existing
 * {@code occursOn} column.
 */
final class TheMonthAMomentFallsIn {

    private TheMonthAMomentFallsIn() {
    }

    /**
     * The month this moment falls in, read in the zone this application counts every calendar thing
     * in.
     *
     * <p>The one question this class exists to answer, and the reason it is a class: a spend, a bill
     * occurrence, a budget taking effect and a read being taken for "this month" are four callers
     * asking it, and four copies of one line would be four chances for one of them to read the
     * server's own zone instead.
     */
    static YearMonth of(Instant moment) {
        return YearMonth.from(moment.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN));
    }

    /**
     * The month a day falls in, for a caller that already holds a day rather than a moment.
     *
     * <p>No zone is applied, deliberately, for the reason {@code SavingsWeek.containing(LocalDate)}
     * gives: a {@link LocalDate} is already the answer to "which day is it, where somebody is
     * standing", and reading a zone again here would be converting a day that has no time of day to
     * convert.
     */
    static YearMonth of(LocalDate day) {
        return YearMonth.from(day);
    }

    /**
     * The day a month begins on, which is how a month is written down.
     *
     * <p>A row carries this rather than a month, because a month is not a type a database has and
     * the first of it is a date that sorts, compares and reads correctly without anybody decoding
     * it. Reading it back is {@link #of(LocalDate)}, and the pair is the only place the two
     * representations meet.
     */
    static LocalDate theDayItBegins(YearMonth month) {
        return month.atDay(1);
    }

    /**
     * The moment a month begins: midnight at the start of its first day, in the zone above.
     * Inclusive — a spend recorded at exactly this instant is in this month.
     */
    static Instant theMomentItBegins(YearMonth month) {
        return month.atDay(1).atStartOfDay(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toInstant();
    }

    /**
     * The moment the month after this one begins — the top of the window, and outside it.
     *
     * <p>Named for the next month rather than for "the end of this one" because that is what it is,
     * and because the alternative names lie: there is no last instant of a month to hold, and a
     * reader who saw {@code endOf} would reasonably suppose that a moment equal to it belonged
     * inside. Every window in this module is {@code theMomentItBegins(month) <= moment <
     * theMomentTheNextOneBegins(month)}, which puts the last second of the last day of a month in
     * that month and the first second of the next day in the next one.
     */
    static Instant theMomentTheNextOneBegins(YearMonth month) {
        return theMomentItBegins(month.plusMonths(1));
    }

    /**
     * Whether a moment falls inside a month, asked as one question rather than assembled from the
     * two above it at every call site.
     *
     * <p>The bill occurrences are filtered with this: they arrive as a list of everything an account
     * was ever presented with, and which of them a month holds is exactly this comparison. Written
     * once so that the half-open rule cannot be written closed by accident somewhere.
     */
    static boolean holds(YearMonth month, Instant moment) {
        return moment != null
                && !moment.isBefore(theMomentItBegins(month))
                && moment.isBefore(theMomentTheNextOneBegins(month));
    }
}
