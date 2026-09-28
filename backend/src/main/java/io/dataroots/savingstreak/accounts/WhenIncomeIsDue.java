package io.dataroots.savingstreak.accounts;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * Which days a monthly income falls due on: the paydays between two moments, oldest first, with the
 * month-end clamp.
 *
 * <p>A function of its arguments and nothing else — no database, no clock, no state — in the shape
 * {@code LoyaltyAnniversary} and {@code HowTheWeeklyMoneyIsSpent} already set. It is the whole of
 * the calendar rule, so there is one place to read what "the 31st" means in February and one place
 * to test it without staging a month of HTTP round-trips.
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
 * <p><strong>A range, not a single next day.</strong> {@code MovableClock} moves in whole calendar
 * days and a cron expression never fires for the days it skipped, so on a wound clock catching up is
 * the <em>only</em> way a payday is ever credited: a trainer who winds three months forward and runs
 * the job once has to be paid three times. Asking for a range rather than for "has today's payday
 * arrived" is what makes that the ordinary path rather than a special case.
 *
 * <p><strong>Open at the bottom and closed at the top.</strong> A payday counts as due when the day
 * it falls on begins strictly after the moment the account has been settled through, and at or
 * before the moment being run for. Strictly after, because the cursor starts at the declaration and
 * a day that had already begun when the customer declared their income is a day whose payment
 * happened — if it happened — before this application had been told anything. That is what makes a
 * declaration made on the 20th a promise about next month rather than a year of back pay, and it is
 * also what stops one run's last payday being the next run's first.
 *
 * <p><strong>The 31st is paid on the last day of a short month.</strong> Clamping is the banking
 * convention, and it is the only rule that does not skip February outright — a customer paid on the
 * 31st is paid twelve times a year, not seven. The clamp is applied month by month rather than
 * carried on the declaration, so the day the customer said is still the day they said: an income
 * declared for the 31st in January lands on the 28th in February and back on the 31st in March.
 *
 * <p>Days are counted through the calendar of the zone this application counts every calendar thing
 * in, so that "the twenty-fifth" is the twenty-fifth in Brussels whatever machine the application
 * happens to be running on. The zone is borrowed from {@code SavingsWeek} rather than written out
 * again here, for the reason {@code ClockConfiguration} gives when it borrows the same constant: a
 * second copy of "Europe/Brussels" is a copy that can be changed on its own, and a payday landing in
 * a different calendar from the week it lands in is a day nobody could reconcile. Quoting a constant
 * is not reading a module — there is no repository, no entity and no state in it, which is the same
 * bargain {@code AmountOfMoney} is shared under.
 */
public final class WhenIncomeIsDue {

    /** The first day of a month a customer can be paid on. */
    static final int EARLIEST_DAY_OF_THE_MONTH = 1;

    /**
     * The last. The 31st rather than the 28th, because a customer paid on the last day of the month
     * says the 31st, and the clamp below is what makes that mean February.
     */
    static final int LATEST_DAY_OF_THE_MONTH = 31;

    private WhenIncomeIsDue() {
    }

    /** Whether that is a day of the month somebody could be paid on at all. */
    static boolean isADayOfTheMonth(int dayOfMonth) {
        return dayOfMonth >= EARLIEST_DAY_OF_THE_MONTH && dayOfMonth <= LATEST_DAY_OF_THE_MONTH;
    }

    /**
     * Every payday for that day of the month falling after {@code settledThrough} and at or before
     * {@code now}, oldest first.
     *
     * <p>Oldest first because that is the order the days actually fell, and a history read in any
     * other order is one nobody can reconcile against a bank statement. It costs nothing to promise
     * — the months are walked forwards — and everything downstream is written expecting it.
     *
     * <p>Walked month by month rather than divided out of the span between the two moments, because
     * the division is wrong in exactly the case the clamp exists for: 31 January to 28 February is
     * not a month to any month-counting arithmetic, while the payday that day is plainly the next
     * one. One step per calendar month between the two moments, which is a handful on a nightly run
     * and about twelve hundred for the century a trainer is most unlikely to wind.
     *
     * <p><strong>This is the calendar and not the whole rule.</strong> It answers which days a day
     * of the month falls on in a stretch of time, and it knows nothing about what has already been
     * paid — which is why moving a declared payday later inside a month is <em>not</em> its problem
     * to solve: the day the customer now names does fall in that stretch. The record is what says a
     * month already holds a salary, and {@code AccountsService} is where the two meet.
     *
     * @param dayOfMonth     the day the customer said, 1 to 31, unclamped
     * @param settledThrough the account's cursor: the moment through which its paydays are settled,
     *                       or null for a row that has none, which is answered as nothing due
     * @param now            the moment the run is judging against, off the application's clock
     */
    public static List<LocalDate> paydaysBetween(int dayOfMonth, Instant settledThrough, Instant now) {
        List<LocalDate> due = new ArrayList<>();
        if (settledThrough == null || !now.isAfter(settledThrough)) {
            // Nothing has passed since this account was last settled. The ordinary answer for a
            // second run of the job in one night, and for a declaration made this minute.
            //
            // A cursor that is not there at all is answered the same way rather than thrown over,
            // so that this agrees with MonthlyIncome.settledThrough, which defends against exactly
            // the same null. It cannot happen in the application as it ships — the declaration sets
            // the cursor and nothing ever clears it — but the nightly run is one transaction over
            // every declaration, so one hand-edited row with no cursor would otherwise abort the
            // whole night and credit no account at all. Counting from the beginning of time instead
            // would be a century of back pay, which is the only answer worse than none.
            return due;
        }
        YearMonth month = monthOf(settledThrough);
        YearMonth lastMonthToLookIn = monthOf(now);
        while (!month.isAfter(lastMonthToLookIn)) {
            LocalDate payday = paydayIn(month, dayOfMonth);
            Instant theDayBegins = startOf(payday);
            if (theDayBegins.isAfter(settledThrough) && !theDayBegins.isAfter(now)) {
                due.add(payday);
            }
            month = month.plusMonths(1);
        }
        return due;
    }

    /**
     * The next day this income falls due after the given moment — this month's payday if it has not
     * begun yet, and otherwise next month's.
     *
     * <p>The day a customer is told to expect their money, and the same clamp applies: a declaration
     * for the 31st made in January says the 28th when February is what is next.
     *
     * <p>A payday whose day has already begun is behind rather than next, which is the same boundary
     * {@link #paydaysBetween} draws and for the same reason — it is the reading that makes "what is
     * coming" and "what has been credited" two halves of one calendar rather than two rules that can
     * both claim the same day.
     *
     * <p><strong>The moment to ask from is the account's cursor, not the wall clock.</strong> Asked
     * from midnight, this disagrees with the job for an hour: the job runs at one in the morning, so
     * between 00:00 and 01:00 on a payday — and for the whole of it if a run is missed or the clock
     * was wound past it — today's salary has not been credited and yet its day has begun, and a page
     * asking from "now" would name next month while the money for this one is still coming. Asked
     * from the cursor, the answer is the first payday the job will credit, which is the same
     * boundary the job itself counts from. The caller is {@code AccountsService}, which passes the
     * cursor and then steps past any month the record already holds a salary for.
     *
     * @param dayOfMonth     the day the customer said, 1 to 31, unclamped
     * @param settledThrough the account's cursor, the moment its paydays are settled through
     */
    static LocalDate theNextPaydayAfter(int dayOfMonth, Instant settledThrough) {
        YearMonth month = monthOf(settledThrough);
        LocalDate thisMonths = paydayIn(month, dayOfMonth);
        return startOf(thisMonths).isAfter(settledThrough)
                ? thisMonths
                : paydayIn(month.plusMonths(1), dayOfMonth);
    }

    /**
     * The day that month's payday actually falls on: the day the customer said, or the last day of
     * the month when that month has no such day.
     *
     * <p>{@link YearMonth#atDay} would throw for the 31st of February rather than clamp, which is
     * the right behaviour for a date somebody typed and the wrong one for a recurring instruction —
     * so the clamp is made here, once, and every caller gets the same February.
     */
    static LocalDate paydayIn(YearMonth month, int dayOfMonth) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    /** The month a moment falls in, read in the zone this application counts calendars in. */
    private static YearMonth monthOf(Instant moment) {
        return YearMonth.from(moment.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN));
    }

    /**
     * The moment a payday begins. A payday is a day rather than a time of day — the job that credits
     * it runs at one in the morning — so the moment it can first be said to have arrived is the
     * moment the day starts.
     *
     * <p>Visible to the module, the way {@link WhenABillIsDue#theMomentThatDayBegins} is, because a
     * forecast asks this calendar a question bounded by a <em>day</em> — "which paydays fall between
     * the cursor and this day next month" — and a caller turning that day into a moment itself would
     * be a second place deciding when a day begins, in a second calendar, one hour out twice a year.
     */
    static Instant startOf(LocalDate payday) {
        return payday.atStartOfDay(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toInstant();
    }
}
