package io.dataroots.savingstreak.rewards;

import java.time.Duration;
import java.time.Instant;

/**
 * How long the last one stays somebody's, and the moment the one they are holding stops being
 * theirs.
 *
 * <p>The rule in one place, the way {@link VoucherShelfLife} holds the shelf life of a voucher and
 * {@code PointsExpiry} holds the twelve-month rule for a batch of points. The moment a customer is
 * told when they take a hold, the moment their card counts down to, the moment a conversion is
 * judged against and the moment the nightly sweep sweeps by all have to be the same moment, and
 * arithmetic written four times is arithmetic that disagrees with itself.
 *
 * <p><strong>Seventy-two hours, and the number is not an arbitrary generosity.</strong> An
 * afternoon would be the better business rule — scarce stock sitting idle for three days is three
 * days nobody else can have it — and it is rejected for a reason that has nothing to do with
 * business. {@code MovableClock} advances in <em>whole calendar days</em>, and winding that clock
 * forward is the only way anything time-dependent in this application can be shown working: a
 * trainer moves the clock on, runs the sweep by hand, and watches what happens. A shelf life
 * shorter than a day cannot be wound past, so a hold that lasted an afternoon would be a rule
 * nobody could ever demonstrate and, in a training application, a rule that may as well not
 * exist. Three days is the shortest span that is comfortably more than one wind of the clock and
 * still short enough to sit through in a session. The spec says exactly this, and it is repeated
 * here because this is the file somebody would come to in order to change the number.
 *
 * <p><strong>A moment rather than a day, which is the opposite of what a voucher gets.</strong>
 * A voucher's deadline is a date because a voucher is a code somebody carries to a counter and
 * "it runs out on the 14th" is what they can act on; the argument is on {@link VoucherShelfLife}.
 * A hold is not carried anywhere. It is a countdown on a screen the customer is looking at, it is
 * measured in hours by the spec, and rounding it to a calendar day would make two people who took
 * a hold on the same evening lose it at different times by up to a day depending on which side of
 * midnight they pressed. So it is an instant, and the page counts down to it.
 *
 * <p>Hours rather than three days of calendar arithmetic, deliberately. Seventy-two hours is what
 * the spec promises and it is the same seventy-two hours for everybody, including across the two
 * weekends a year when a calendar day is not twenty-four hours long. A customer who took a hold on
 * the Saturday of a clock change would otherwise have an hour more or an hour less than the person
 * who took one the week before, for a reason nobody could explain to them.
 *
 * <p>There is deliberately no {@code hasLapsedBy} beside this, for the reason
 * {@link VoucherShelfLife} gives for having no predicate either: the moment is a column, every
 * caller has the row and the clock in hand, and a predicate here would be the same comparison in a
 * second place that could come to differ from the query the sweep uses.
 */
final class TheShelfLifeOfAHold {

    /**
     * Seventy-two hours, named once.
     *
     * <p>Quoted in the sentence a customer is refused a second hold with, and used by the
     * arithmetic below, so that the number somebody reads and the number the clock is measured
     * against cannot come apart — the reason {@code VoucherShelfLife} names its own floor.
     */
    static final int THE_HOURS_A_HOLD_LASTS = 72;

    private TheShelfLifeOfAHold() {
    }

    /** The moment a hold taken at this moment stops being anybody's. */
    static Instant theMomentItLapses(Instant takenAt) {
        return takenAt.plus(Duration.ofHours(THE_HOURS_A_HOLD_LASTS));
    }
}
