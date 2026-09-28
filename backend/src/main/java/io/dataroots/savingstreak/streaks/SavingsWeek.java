package io.dataroots.savingstreak.streaks;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * One week of saving: Monday to Sunday, as a customer in Brussels would count it.
 *
 * <p>A week rather than a pair of moments, because everything the streak is made of is stated in
 * weeks — this one's new savings, whether it was secured, how many of them run consecutively — and a
 * pair of instants passed around is a week nobody can name. Held as the Monday it starts on, which
 * is the one figure that identifies it.
 *
 * <p>The zone is this module's and is named here alone. Every moment the application records is an
 * {@link Instant} and the clock it comes off reads UTC, so nothing underneath this class knows what
 * day of the week it is anywhere: a deposit at 00:30 on Monday in Brussels is 23:30 on Sunday in
 * UTC, and a week counted off the clock's own reading would put it in the week that had just ended.
 * A named zone rather than a fixed offset, so that the two hours Brussels is ahead in summer and the
 * one it is ahead in winter are the zone's problem rather than this arithmetic's.
 *
 * <p>The one place a future decision to count somebody's week in another zone would be made.
 */
public record SavingsWeek(LocalDate startsOn) {

    /**
     * The zone a week is counted in. One zone for everybody, because a customer's timezone is not
     * something this application knows and inventing a per-customer one would be inventing a
     * setting no screen can set.
     */
    public static final ZoneId ZONE_WEEKS_ARE_COUNTED_IN = ZoneId.of("Europe/Brussels");

    public SavingsWeek {
        if (startsOn.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException(
                    "a savings week starts on a Monday, and " + startsOn + " is a "
                            + startsOn.getDayOfWeek());
        }
    }

    /**
     * The week the given moment falls in, read in the zone above. The moment is converted rather
     * than truncated: which day it is is a question about where somebody is standing, and the
     * instant on its own does not answer it.
     */
    public static SavingsWeek containing(Instant moment) {
        return containing(moment.atZone(ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate());
    }

    /**
     * The week a given day falls in, for a caller that already holds a day rather than a moment.
     *
     * <p>No zone is applied, deliberately. A {@link LocalDate} is already the answer to "which day
     * is it, where somebody is standing"; whoever worked it out read the zone above to get it, and
     * reading one again here would be converting a day that has no time of day to convert.
     */
    public static SavingsWeek containing(LocalDate day) {
        return new SavingsWeek(day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }

    /** The Sunday the week ends on, for saying which week this is rather than for comparing against. */
    public LocalDate endsOn() {
        return startsOn.plusDays(6);
    }

    /**
     * The week before this one, for whoever is walking a run of weeks backwards.
     *
     * <p>A week back through the calendar rather than seven days off a moment: the Monday before a
     * Monday is a Monday whatever the clocks did in between, and subtracting a fixed span from
     * midnight would land at 23:00 or 01:00 on the Sunday twice a year — a different week from the
     * one a customer would name.
     */
    public SavingsWeek previous() {
        return new SavingsWeek(startsOn.minusWeeks(1));
    }

    /** Midnight at the start of the Monday, in the zone weeks are counted in. Inclusive. */
    public Instant startsAt() {
        return startsOn.atStartOfDay(ZONE_WEEKS_ARE_COUNTED_IN).toInstant();
    }

    /**
     * Midnight at the start of the following Monday. Exclusive, so that a deposit landing on the
     * stroke of Monday belongs to the week beginning and not to both: the two weeks either side of a
     * boundary have to agree about which of them owns it, and half-open is the only way they can.
     *
     * <p>Worked out from the following Monday's own midnight rather than by adding seven days to
     * this one's, because the week a clock change falls in is 167 or 169 hours long and adding a
     * fixed span to it would land an hour either side of where the calendar says the week ends.
     */
    public Instant endsAt() {
        return startsOn.plusWeeks(1).atStartOfDay(ZONE_WEEKS_ARE_COUNTED_IN).toInstant();
    }

    /** How the week is written in a log line: the dates a reader would use to name it. */
    @Override
    public String toString() {
        return startsOn + "/" + endsOn();
    }
}
