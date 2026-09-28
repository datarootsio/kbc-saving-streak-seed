package io.dataroots.savingstreak.accounts;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * Which days a recurring bill falls due on: the dates between two moments, oldest first, with the
 * month-end clamp.
 *
 * <p>A function of its arguments and nothing else — no database, no clock, no state — in the shape
 * {@link WhenIncomeIsDue}, {@code WhichOccurrencesAreDue}, {@code LoyaltyAnniversary} and
 * {@code HowTheWeeklyMoneyIsSpent} already set. It is the whole of the calendar rule for a bill, so
 * there is one place to read what "the 31st" means in February and one place to test it without
 * staging a month of HTTP round-trips.
 *
 *
 * <p><strong>Public for the simulator's fold.</strong> That fold replays a year one night at a time
 * and asks, for each night, exactly the question the nightly run asks: what fell due since the last
 * one. Asked with yesterday's midnight as the cursor and this one as the moment, it answers for a
 * single day — the ordinary path, not a special case, which is why nothing new had to be written
 * here for it. A fold that decided for itself whether the 31st falls in February would be the
 * second place that clamp lived, and a customer would be shown a year the application will not
 * deliver. Quoting a function of its arguments is not reading a module: there is no repository, no
 * entity and no state here, which is the same ground {@code TimelineHorizon} stands on when the two
 * screens that draw a year quote it.
 * <p><strong>A class of its own rather than a second caller of {@link WhenIncomeIsDue}</strong>, and
 * the duplication is deliberate. The nightly billing run and the twelve-month bill forecast both
 * call this, and what they must never disagree about is which dates a <em>bill</em> falls on; what
 * {@link WhenIncomeIsDue} answers is which days a salary lands, and it carries two things this has
 * no use for — a "next payday" reading counted from an account's cursor, and the vocabulary a page
 * about being paid is written in. Sharing one class would make every change to the day a salary
 * lands a change to the day a rent leaves, which is two features tied together by an accident of
 * both counting months. The two are kept honest by the clamp being written the same way in both and
 * by each having a unit test of its own.
 *
 * <p><strong>A range, not a single next date.</strong> {@code MovableClock} moves in whole calendar
 * days and a cron expression never fires for the days it skipped, so on a wound clock catching up is
 * the <em>only</em> way a bill is ever taken: a trainer who winds two months forward and runs the job
 * once has to see both rents leave. Asking for a range rather than for "is the rent due today" is
 * what makes that the ordinary path rather than a special case.
 *
 * <p><strong>Open at the bottom and closed at the top.</strong> A due date counts as due when the day
 * it falls on begins strictly after the moment the bill has been settled through, and at or before
 * the moment being run for. Strictly after, because the cursor starts at the declaration and a day
 * that had already begun when the customer declared the bill is a day whose payment — if it happened
 * — happened before this application had been told anything. That is what makes a bill declared on
 * the 20th a promise about next month rather than a year of back rent, and it is also what stops one
 * run's last due date being the next run's first.
 *
 * <p><strong>The 31st is taken on the last day of a short month.</strong> Clamping is the banking
 * convention, and it is the only rule that does not skip February outright — a rent taken on the
 * last day of the month is taken twelve times a year, not seven. The clamp is applied month by month
 * rather than carried on the declaration, so the day the customer said is still the day they said: a
 * bill declared for the 31st in January is taken on the 28th in February and back on the 31st in
 * March. It is also what makes "once, not twice, in a thirty-day month" true — one month is walked
 * once, and the clamp names one date in it.
 *
 * <p>Days are counted through the calendar of the zone this application counts every calendar thing
 * in, so that "the first" is the first in Brussels whatever machine the application happens to be
 * running on. The zone is borrowed from {@code SavingsWeek} rather than written out again here, for
 * the reason {@link WhenIncomeIsDue} gives when it borrows the same constant: a second copy of
 * "Europe/Brussels" is a copy that can be changed on its own, and a rent leaving in a different
 * calendar from the salary that pays it is a month nobody could reconcile. Quoting a constant is not
 * reading a module — there is no repository, no entity and no state in it.
 */
public final class WhenABillIsDue {

    /** The first day of a month a bill can go out on. */
    static final int EARLIEST_DAY_OF_THE_MONTH = 1;

    /**
     * The last. The 31st rather than the 28th, because a rent taken on the last day of the month is
     * declared as the 31st, and the clamp below is what makes that mean February. Refusing it would
     * make a real bill undeclarable.
     */
    static final int LATEST_DAY_OF_THE_MONTH = 31;

    private WhenABillIsDue() {
    }

    /** Whether that is a day of the month a bill could go out on at all. */
    static boolean isADayOfTheMonth(int dayOfMonth) {
        return dayOfMonth >= EARLIEST_DAY_OF_THE_MONTH && dayOfMonth <= LATEST_DAY_OF_THE_MONTH;
    }

    /**
     * Every date this bill falls due on after {@code settledThrough} and at or before {@code now},
     * oldest first.
     *
     * <p>Oldest first because that is the order the days actually fell, and arrears built in any
     * other order are arrears nobody can reconcile against a bank statement. It costs nothing to
     * promise — the months are walked forwards — and everything downstream is written expecting it.
     *
     * <p>Walked month by month rather than divided out of the span between the two moments, because
     * the division is wrong in exactly the case the clamp exists for: 31 January to 28 February is
     * not a month to any month-counting arithmetic, while the date the rent leaves on is plainly the
     * next one. One step per calendar month between the two moments, which is a handful on a nightly
     * run and about twelve hundred for the century a trainer is most unlikely to wind.
     *
     * <p><strong>This is the calendar and not the whole rule.</strong> It answers which dates a day
     * of the month falls on in a stretch of time, and it knows nothing about what has already been
     * taken, about whether the account can afford it, or about whether the bill has been ended. The
     * record is what says a date has already been settled, and {@link AccountsService} is where the
     * two meet.
     *
     * @param dayOfMonth     the day the customer said, 1 to 31, unclamped
     * @param settledThrough the bill's cursor: the moment through which its due dates are settled,
     *                       or null for a row that has none, which is answered as nothing due
     * @param now            the moment the run is judging against, off the application's clock
     */
    public static List<LocalDate> dueDatesBetween(int dayOfMonth, Instant settledThrough, Instant now) {
        List<LocalDate> due = new ArrayList<>();
        if (settledThrough == null || !now.isAfter(settledThrough)) {
            // Nothing has passed since this bill was last settled. The ordinary answer for a second
            // run of the job in one night, and for a bill declared this minute.
            //
            // A cursor that is not there at all is answered the same way rather than thrown over.
            // It cannot happen in the application as it ships — declaring a bill sets the cursor and
            // nothing ever clears it — but the nightly run is one transaction over every standing
            // bill, so one hand-edited row with no cursor would otherwise abort the whole night and
            // take no bill at all. Counting from the beginning of time instead would be a century of
            // back rent, which is the only answer worse than none.
            return due;
        }
        YearMonth month = monthOf(settledThrough);
        YearMonth lastMonthToLookIn = monthOf(now);
        while (!month.isAfter(lastMonthToLookIn)) {
            LocalDate dueDate = theDayItFallsOnIn(month, dayOfMonth);
            Instant theDayBegins = theMomentThatDayBegins(dueDate);
            if (theDayBegins.isAfter(settledThrough) && !theDayBegins.isAfter(now)) {
                due.add(dueDate);
            }
            month = month.plusMonths(1);
        }
        return due;
    }

    /**
     * The date that month's bill actually falls on: the day the customer said, or the last day of
     * the month when that month has no such day.
     *
     * <p>{@link YearMonth#atDay} would throw for the 31st of February rather than clamp, which is
     * the right behaviour for a date somebody typed and the wrong one for a recurring instruction —
     * so the clamp is made here, once, and every caller gets the same February.
     */
    static LocalDate theDayItFallsOnIn(YearMonth month, int dayOfMonth) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    /**
     * The moment a due date begins. A due date is a day rather than a time of day — the job that
     * takes it runs at half past two in the morning — so the moment it can first be said to have
     * arrived is the moment the day starts.
     *
     * <p>Visible to the module because a capped catch-up leaves its cursor at the beginning of the
     * last day it settled rather than at the moment it ran, which is what lets the next run continue
     * from exactly where this one stopped. {@code WhichOccurrencesAreDue} exposes the same moment to
     * its own module for the same reason.
     */
    static Instant theMomentThatDayBegins(LocalDate day) {
        return day.atStartOfDay(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toInstant();
    }

    /** The month a moment falls in, read in the zone this application counts calendars in. */
    private static YearMonth monthOf(Instant moment) {
        return YearMonth.from(moment.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN));
    }
}
