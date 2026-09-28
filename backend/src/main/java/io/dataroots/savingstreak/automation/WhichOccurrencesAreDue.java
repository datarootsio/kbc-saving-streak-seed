package io.dataroots.savingstreak.automation;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * Which days a saving rule falls due on: the days its trigger lands on between two moments, oldest
 * first, with the month-end clamp.
 *
 * <p>A function of its arguments and nothing else — no database, no clock, no state — in the shape
 * {@code WhenIncomeIsDue}, {@code LoyaltyAnniversary} and {@code HowTheWeeklyMoneyIsSpent} already
 * set. It is the whole of the calendar rule, so there is one place to read what "the 31st" means in
 * February and one place to test it without staging a month of HTTP round-trips.
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
 * the <em>only</em> way an occurrence past today ever fires: a trainer who winds a month forward and
 * runs the job once has to see the days in between. Asking for a range rather than for "is the rule
 * due today" is what makes that the ordinary path rather than a special case.
 *
 * <p><strong>Open at the bottom and closed at the top.</strong> A day counts as due when it begins
 * strictly after the moment the rule has been settled through, and at or before the moment being run
 * for. Strictly after, because a rule's cursor starts at the moment it was left standing and a day
 * that had already begun when the customer wrote it is a day the rule did not exist for — which is
 * what makes a new rule an instruction about the future rather than a bill for the past. It is also
 * what stops one run's last occurrence being the next run's first.
 *
 * <p><strong>The 31st falls on the last day of a short month.</strong> Clamping is the banking
 * convention, and it is the only rule that does not skip February outright — a customer saving on
 * the 31st saves twelve times a year, not seven. The clamp is applied month by month rather than
 * carried on the rule, so the day the customer said is still the day they said: a rule set for the
 * 31st in January moves on the 28th in February and back on the 31st in March.
 *
 * <p>Days are counted through the calendar of the zone this application counts every calendar thing
 * in, borrowed from {@code SavingsWeek} rather than written out again, for the reason
 * {@code WhenIncomeIsDue} gives when it borrows the same constant: a weekly rule's Monday and a
 * {@code SavingsWeek}'s Monday have to be the same Monday, or a rule written to hold a streak
 * together would land in the wrong week twice a year. Quoting a constant is not reading a module —
 * there is no repository, no entity and no state in it.
 */
public final class WhichOccurrencesAreDue {

    private WhichOccurrencesAreDue() {
    }

    /**
     * Every day this trigger falls due on after {@code settledThrough} and at or before {@code now},
     * oldest first.
     *
     * <p>Oldest first because that is the order the days actually fell, and a history read in any
     * other order is one nobody can reconcile. It costs nothing to promise — the calendar is walked
     * forwards — and everything downstream is written expecting it.
     *
     * <p>Walked day by day for a weekly rule and month by month for a monthly one, rather than
     * divided out of the span between the two moments. The division is wrong in exactly the case the
     * clamp exists for: 31 January to 28 February is not a month to any month-counting arithmetic,
     * while the day that rule next moves is plainly the 28th.
     *
     * <p>A rule missing the day its trigger needs falls due on no day at all, and that is an answer
     * rather than an error. A payday rule whose holder has declared no income is the case that
     * actually happens: nothing yet says when that rule moves, so nothing is due, and the run says so
     * in a line rather than throwing over a rule somebody is perfectly entitled to have left
     * standing.
     *
     * <p><strong>A payday rule is not asked here, and asking is refused rather than answered.</strong>
     * Its days are not a fact about the calendar — see {@link #theDaysSalaryLandedOn} — and the one
     * mistake this feature has made three times is asking the calendar a question only the record
     * can answer. A trigger this class cannot honestly answer for is better as a refusal somebody
     * reads in a stack trace than as a plausible list of days money then moves on.
     *
     * @param trigger        what makes the rule move: {@link RuleTrigger#WEEKLY} or
     *                       {@link RuleTrigger#MONTHLY}
     * @param dayOfWeek      the day a {@link RuleTrigger#WEEKLY} rule moves on, and null otherwise
     * @param dayOfMonth     the day a {@link RuleTrigger#MONTHLY} rule moves on, unclamped
     * @param settledThrough the rule's cursor: the moment through which its occurrences are settled
     * @param now            the moment the run is judging against, off the application's clock
     * @throws IllegalArgumentException for {@link RuleTrigger#ON_PAYDAY}
     */
    public static List<LocalDate> daysDueBetween(RuleTrigger trigger, DayOfWeek dayOfWeek,
                                          Integer dayOfMonth, Instant settledThrough, Instant now) {
        if (settledThrough == null || !now.isAfter(settledThrough)) {
            // Nothing has passed since this rule was last settled. The ordinary answer for a second
            // run of the job in one night, and for a rule left standing this minute.
            return List.of();
        }
        return switch (trigger) {
            case WEEKLY -> dayOfWeek == null
                    ? List.of()
                    : everyWeekOn(dayOfWeek, settledThrough, now);
            case MONTHLY -> dayOfMonth == null
                    ? List.of()
                    : everyMonthOn(dayOfMonth, settledThrough, now);
            case ON_PAYDAY -> throw new IllegalArgumentException("a payday rule falls due on the "
                    + "days a salary actually landed, which is a record rather than a calendar: "
                    + "ask theDaysSalaryLandedOn with the days the accounts module credited");
        };
    }

    /**
     * The days a {@link RuleTrigger#ON_PAYDAY} rule falls due on: those of the days a salary was
     * actually credited to its holder's current account that fall in the rule's own range, oldest
     * first.
     *
     * <p><strong>A payday is a thing that happened, not a thing the calendar says.</strong> A
     * declared income carries a day of the month and a cursor of its own, and the rule carries a
     * second cursor; nothing reconciles them, so working the days out from the day-of-the-month and
     * the rule's cursor invents paydays. Every one of them moves real money on a morning nobody was
     * paid: a rule left standing before an income was ever declared bills its holder for the months
     * in between, and a declaration withdrawn and made again bills them for the gap. A sweep is
     * worse than a fixed amount there, because a phantom payday drains the account to its floor.
     *
     * <p>So the question is turned round. {@code AccountsService.paydaysCreditedBetween} is asked
     * which salaries it has actually credited since this rule last ran, and the days those salaries
     * were due for — filtered to the days the rule was standing on, and to nothing else — are the
     * days due. One salary credited is one occurrence; none credited is none. The two modules cannot
     * disagree about which paydays exist, because only one of them is answering.
     *
     * <p><strong>Two different moments bound a payday rule, and they bound different things.</strong>
     * Which salaries to look at is a question about when they were <em>credited</em>, and the
     * caller has already asked it of the record with the rule's cursor
     * ({@code AccountsService.paydaysCreditedBetween}): a salary credited since the last run is one
     * this rule has not seen, however long ago the day it was due fell. That is what stops a
     * late-credited salary being lost — the day it was due is behind the cursor the moment it is
     * written, and judging on the day would drop it and never come back.
     *
     * <p>What is left to judge here is the other question: whether the day that salary was for is a
     * day this rule was standing on. The rule's own creation moment is what answers it, and it is a
     * fixed moment rather than a cursor that moves — <em>a rule is not a bill for the past</em>, and
     * a catch-up that credited six months of salary in one go would otherwise fire a rule written
     * last week for all six. A day that had already begun when the customer wrote the rule is a day
     * the rule did not exist for, which is the same boundary {@link #daysDueBetween} applies to the
     * calendar, open at the bottom and closed at the top.
     *
     * <p>Firing a day twice is therefore <em>not</em> what this method prevents, and deliberately so.
     * That is the record's job — one occurrence per rule per period, asked of the occurrences
     * themselves — for the reason this feature has had to learn three times: a guarantee about money
     * cannot rest on a cursor, which is a number this application writes.
     *
     * <p>Sorted and de-duplicated here rather than trusted from the caller. The record is unique per
     * account per payday so a duplicate should not exist, and a day due twice would be a day the
     * unique index over rule and day due then refused mid-run — cheaper to make it impossible.
     *
     * @param salariesCredited the days the salaries credited since the rule last ran were due for
     * @param standingSince    the moment the rule was left standing, before which no day is its
     * @param now              the moment the run is judging against, off the application's clock
     */
    static List<LocalDate> theDaysSalaryLandedOn(List<LocalDate> salariesCredited,
                                                 Instant standingSince, Instant now) {
        if (standingSince == null || !now.isAfter(standingSince)) {
            return List.of();
        }
        return salariesCredited.stream()
                .filter(day -> startOf(day).isAfter(standingSince) && !startOf(day).isAfter(now))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Every day this trigger falls due on inside a window named by two <em>days</em> — both ends
     * included — oldest first: the shape a forecast asks the calendar in, where the question is
     * "between today and a day twelve months out" rather than "since the cursor".
     *
     * <p>The same walk as {@link #daysDueBetween} and deliberately not a second one. What differs is
     * only how the ends are said, and saying them here is what keeps a forecast from restating the
     * boundary arithmetic and getting it off by a day: that method is open at the bottom, so a window
     * that is meant to <em>include</em> the day it opens on has to be asked from the moment the day
     * before it began. A caller doing that subtraction for itself is a caller that can forget it, and
     * a preview missing today's transfer is exactly the disagreement between the forecast and the
     * firing this whole class exists to make impossible.
     *
     * <p><strong>The day the window opens on is not the bottom of it.</strong> The bottom is the
     * rule's cursor — see {@link #theBottomOf} — so a rule the run still owes days for is forecast
     * from the oldest day it owes rather than from today, and the forecast and the next run say the
     * same thing about the same rule. The day the window opens on is the bottom only for a caller
     * with no cursor to hand in.
     *
     * <p>A rule that has already had its turn today is not excluded here, because that is not a
     * question about the calendar: it is a question for the record, which is why the caller asks it
     * of the occurrences as well.
     *
     * @param notBefore the rule's cursor — the moment through which its occurrences are settled —
     *                  so that the days a forecast shows are exactly the days the run would still
     *                  fire, including the ones it is late with; null for a caller that has none
     */
    static List<LocalDate> daysDueInTheWindow(RuleTrigger trigger, DayOfWeek dayOfWeek,
                                              Integer dayOfMonth, LocalDate opensOn,
                                              LocalDate closesOn, Instant notBefore) {
        return daysDueBetween(trigger, dayOfWeek, dayOfMonth, theBottomOf(opensOn, notBefore),
                startOf(closesOn));
    }

    /**
     * The days a rule that fires on payday is <em>expected</em> to fall due on inside that window,
     * from the day its holder has declared their salary lands, with the same month-end clamp every
     * other monthly thing in this application gets.
     *
     * <p><strong>A forecast, and never a firing.</strong> {@link #theDaysSalaryLandedOn} is the
     * other half of this and the two are asked in opposite directions on purpose: what a payday rule
     * <em>did</em> comes out of the record of salaries actually credited, because deriving it from a
     * day-number invents paydays and moves real money on mornings nobody was paid. What a payday rule
     * <em>will</em> do has no record to come out of — the salary has not landed and cannot have — so
     * the only honest source is the declaration itself, which is the customer's own statement about
     * when they are paid. Nothing here moves money, and that is the whole of why the two may differ:
     * a forecast that is wrong costs a line on a page, and a firing that is wrong costs a month's
     * rent.
     *
     * <p><strong>The future only, and the caller is what keeps it there.</strong> The licence above
     * — that there is no record to read because the salary has not landed — is true of a day still
     * ahead and false of a month already past, where the record exists and can say that nobody was
     * paid. Walked back over such a month this invents paydays exactly as
     * {@link #daysDueBetween} would: a rule left standing before an income was ever declared then
     * promises a year of transfers for mornings nobody was paid on, and the run makes one of them.
     * So {@code notBefore} is the moment the record stops being able to answer — <em>now</em>, off
     * the application's clock — and never the rule's cursor, and the days already owed are taken
     * from {@code AccountsService.paydaysCreditedBetween} the way the run takes them. That division
     * is made by the caller and written down there.
     *
     * <p>A rule whose holder has declared no income falls due on no day at all, which is the same
     * answer {@link #daysDueBetween} gives a rule missing the day its trigger needs, and is what a
     * page draws "nothing yet says when this moves" from.
     *
     * @param dayOfMonth the day the declared income lands on, unclamped, or null when nobody has
     *                   declared one
     * @param notBefore  the moment beyond which nothing can be known from the record — the moment
     *                   being forecast from — so that only days still ahead come out of a
     *                   declaration; null for a caller with none, which is the unit tests and
     *                   nothing else
     */
    static List<LocalDate> theDaysASalaryIsExpectedOn(Integer dayOfMonth, LocalDate opensOn,
                                                      LocalDate closesOn, Instant notBefore) {
        if (dayOfMonth == null) {
            return List.of();
        }
        return everyMonthOn(dayOfMonth, theBottomOf(opensOn, notBefore), startOf(closesOn));
    }

    /**
     * The moment a forward-looking window really begins: <strong>the rule's cursor, wherever it
     * sits</strong>, and the start of the day before the window opens only when there is no cursor
     * to count from.
     *
     * <p><strong>The cursor, and never the later of the two.</strong> The run counts from the cursor
     * and fires everything it finds, however far back that reaches — that is the whole of the
     * catch-up this feature is demonstrated through. So a forecast that clamped its bottom to
     * yesterday would be answering a different question from the run, and every occurrence the next
     * run still owes would vanish from the preview and from the day a rule says it next fires. That
     * is not a corner: {@code MovableClock} moves in whole calendar days and the cron never fires
     * for the days it skipped, so between a wind and a run <em>every</em> rule is behind its cursor.
     * A trainer who winds a month forward and reads the page before running the job was shown a
     * preview beginning next month, and then watched the job move a month of transfers it had never
     * mentioned. Downtime is the same picture.
     *
     * <p>Answering with the days still owed is what makes "the day it next fires" true: the next
     * time the rule fires, the day it fires <em>for</em> is the oldest day it owes. The day may be
     * behind today, and that is the honest answer rather than an awkward one — the transfer has not
     * happened yet, and a line dated last Monday is a customer being told what the next run is about
     * to do.
     *
     * <p>Nothing is promised twice by widening it. A day whose period this rule has already had its
     * turn in is dropped by the caller out of the record, which is the same question the run asks
     * and the only one that can answer it: a guarantee about money cannot rest on a cursor, which is
     * a number this application writes.
     *
     * <p>The day the window opens on is still inside it when there is no cursor at all, because the
     * range this is handed to is open at the bottom and a window named by a day is meant to include
     * it. That subtraction is made here rather than by a caller for the reason the whole class
     * exists: a caller doing its own boundary arithmetic is a caller that can get it off by a day.
     */
    private static Instant theBottomOf(LocalDate opensOn, Instant notBefore) {
        return notBefore == null ? startOf(opensOn.minusDays(1)) : notBefore;
    }

    /**
     * The day a moment falls on, read in the zone this application counts calendars in.
     *
     * <p>Shared so that every part of this feature that turns a moment into a day turns it into the
     * same day. How late an occurrence was is the gap between the day it was due and the day it was
     * settled on, and a lateness counted in one calendar against a due day named in another would be
     * off by a day for anything settled near midnight.
     */
    static LocalDate theDayItFallsOn(Instant moment) {
        return dateOf(moment);
    }

    /**
     * The moment a day begins, in the calendar this application counts days in — the moment an
     * occurrence falling on that day can first be said to be due.
     *
     * <p>Shared with the run so that a catch-up stopped part-way through can be settled <em>through
     * the day it stopped at</em> rather than through the moment it stopped. The comparison a range
     * is made of is "does this day begin after the cursor", so a cursor sitting exactly at the start
     * of the last day fired excludes that day and keeps every day after it: the next run continues
     * where this one stopped, to the day, with nothing fired twice and nothing skipped.
     */
    static Instant theMomentThatDayBegins(LocalDate day) {
        return startOf(day);
    }

    /**
     * Every occurrence of that weekday in the range. Stepped a week at a time from the first one on
     * or after the day the cursor sits in, so a century of catch-up is five thousand steps rather
     * than thirty-six thousand.
     */
    private static List<LocalDate> everyWeekOn(DayOfWeek dayOfWeek, Instant settledThrough,
                                               Instant now) {
        List<LocalDate> due = new ArrayList<>();
        LocalDate day = dateOf(settledThrough).with(TemporalAdjusters.nextOrSame(dayOfWeek));
        while (!startOf(day).isAfter(now)) {
            if (startOf(day).isAfter(settledThrough)) {
                due.add(day);
            }
            day = day.plusWeeks(1);
        }
        return due;
    }

    /**
     * That day of each month in the range, clamped to the last day of a month that is shorter. One
     * step per calendar month between the two moments, which is a handful on a nightly run and about
     * twelve hundred for the century a trainer is most unlikely to wind.
     */
    private static List<LocalDate> everyMonthOn(int dayOfMonth, Instant settledThrough, Instant now) {
        List<LocalDate> due = new ArrayList<>();
        YearMonth month = monthOf(settledThrough);
        YearMonth lastMonthToLookIn = monthOf(now);
        while (!month.isAfter(lastMonthToLookIn)) {
            LocalDate day = dayIn(month, dayOfMonth);
            if (startOf(day).isAfter(settledThrough) && !startOf(day).isAfter(now)) {
                due.add(day);
            }
            month = month.plusMonths(1);
        }
        return due;
    }

    /**
     * The stretch of calendar this trigger moves money in exactly once, named by the day it begins
     * on: the savings week for a weekly rule, and the calendar month for a monthly one and for a
     * rule that fires on payday.
     *
     * <p><strong>This is the period, and the period rather than the day is what "already settled"
     * has to be asked about.</strong> A rule's day is a figure its holder may correct — by editing
     * the rule, or, for a payday rule, by re-declaring when they are paid — and correcting it inside
     * a period it has already fired in produces a second due day that the cursor does not exclude
     * and that no row under that day denies. The 15th and the 20th are different days; April is one
     * April, and a month holds one monthly transfer. {@code AccountsService.dueInMonthsNotAlreadyPaid}
     * asks exactly this question about a salary, in the same words and for the same reason; this is
     * the same widening for a rule, generalised over the three triggers because a weekly rule has
     * the same hole with Monday and Friday in it.
     *
     * <p>A weekly rule's period is a {@link SavingsWeek} rather than any seven days, so that the
     * week a rule is counted as having fired in and the week its deposit counts toward are the same
     * week — a rule moved from Monday to Friday moved inside one week to a customer looking at their
     * streak, and it has to be one week here as well.
     */
    static LocalDate thePeriodItMovesInOnce(RuleTrigger trigger, LocalDate day) {
        return switch (trigger) {
            case WEEKLY -> SavingsWeek.containing(day).startsOn();
            case MONTHLY, ON_PAYDAY -> day.withDayOfMonth(1);
        };
    }

    /**
     * The last day of the period that day falls in, for asking the record about whole periods rather
     * than about the days the calendar happened to name.
     *
     * <p>Which day of a period was settled is exactly what must not be assumed, so the stretch a run
     * looks over is widened to its ends before the question is asked.
     */
    static LocalDate theLastDayOfThePeriodHolding(RuleTrigger trigger, LocalDate day) {
        return switch (trigger) {
            case WEEKLY -> SavingsWeek.containing(day).endsOn();
            case MONTHLY, ON_PAYDAY -> day.with(TemporalAdjusters.lastDayOfMonth());
        };
    }

    /**
     * The day that month's occurrence actually falls on: the day the customer said, or the last day
     * of the month when that month has no such day.
     *
     * <p>{@link YearMonth#atDay} would throw for the 31st of February rather than clamp, which is
     * the right behaviour for a date somebody typed and the wrong one for a recurring instruction —
     * so the clamp is made here, once, and every caller gets the same February.
     */
    static LocalDate dayIn(YearMonth month, int dayOfMonth) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    /** The day a moment falls on, read in the zone this application counts calendars in. */
    private static LocalDate dateOf(Instant moment) {
        return moment.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
    }

    /** The month a moment falls in, read in the same zone. */
    private static YearMonth monthOf(Instant moment) {
        return YearMonth.from(moment.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN));
    }

    /**
     * The moment a day begins. An occurrence falls on a day rather than at a time of day — the job
     * that fires it runs at two in the morning — so the moment it can first be said to be due is the
     * moment the day starts.
     */
    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toInstant();
    }
}
