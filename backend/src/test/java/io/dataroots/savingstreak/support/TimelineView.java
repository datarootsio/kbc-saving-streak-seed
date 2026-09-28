package io.dataroots.savingstreak.support;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * A savings account's year ahead as the API reports it: the two days the bar is drawn between, and
 * every dated thing the deposits in that account have coming. Shared by every test that reads one
 * back, for the reason {@link BalancesView} gives — copies of a shape drift into disagreeing about
 * it, and then one of them is testing a contract nobody serves.
 *
 * <p>Everything in {@code events} belongs to the deposits made into the account it was read from:
 * the anniversaries are theirs, and so are the expiries, which are the points those deposits earned.
 * It is the one place in this API where points are reported per account rather than per customer,
 * and the tests that matter most about this shape are the ones that hold two of a customer's
 * accounts apart.
 *
 * <p>An event's day can be one already gone, which means a thing that is owed and happening
 * overnight rather than a mistake. No event is ever worth nothing.
 */
public record TimelineView(LocalDate from, LocalDate until, List<Event> events) {

    /** The days points go, in order, for a test that cares which dates are on the bar. */
    public List<LocalDate> daysPointsGo() {
        return daysOf("POINTS_EXPIRE");
    }

    /** The days a bonus arrives, in order. */
    public List<LocalDate> daysBonusesArrive() {
        return daysOf("LOYALTY_BONUS");
    }

    /**
     * How many points go on one day, and nothing at all when no marker says any do — so that a test
     * can tell "nothing goes that day" from "nothing goes that day either", which is what an
     * assertion on a zero would blur.
     */
    public Long pointsGoingOn(LocalDate day) {
        return pointsOn("POINTS_EXPIRE", day);
    }

    /** How many points arrive on one day, and nothing at all when none do. */
    public Long pointsArrivingOn(LocalDate day) {
        return pointsOn("LOYALTY_BONUS", day);
    }

    private List<LocalDate> daysOf(String kind) {
        return events.stream().filter(event -> kind.equals(event.kind())).map(Event::on).toList();
    }

    private Long pointsOn(String kind, LocalDate day) {
        return events.stream()
                .filter(event -> kind.equals(event.kind()) && day.equals(event.on()))
                .map(Event::points)
                .findFirst()
                .orElse(null);
    }

    /** Every marker as one line each, for a failure message that says what was actually on the bar. */
    @Override
    public String toString() {
        return "a bar from " + from + " to " + until + " carrying " + Arrays.toString(events.toArray());
    }

    /**
     * One marker: the day, which of the two things it is, and how many points.
     *
     * <p>The kind is held as the name it was sent under rather than as a copy of the backend's enum.
     * A test's job here is to say that the API answers {@code POINTS_EXPIRE}, and a view that
     * imported the enum would be asserting that Jackson can read a name into a type it was handed —
     * which stays true through a rename that breaks every client there is.
     */
    public record Event(LocalDate on, String kind, long points) {

        @Override
        public String toString() {
            return points + " points " + kind + " on " + on;
        }
    }
}
