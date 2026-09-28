package io.dataroots.savingstreak.rewards;

import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * How long a voucher lasts, and which day the one in somebody's hand runs out on.
 *
 * <p>The rule in one place, the way {@link io.dataroots.savingstreak.points.PointsExpiry} holds the
 * twelve-month rule for a batch of points. A shelf life is arithmetic over a calendar and a zone,
 * and arithmetic over a calendar written twice is arithmetic that disagrees with itself: the day
 * the customer is told at the moment they claim, the day their list shows afterwards and the day
 * the nightly sweep judges against all have to be the same day or the application is lying to
 * somebody.
 *
 * <p><strong>A day and not a moment.</strong> A voucher is a code somebody carries to a counter,
 * and "it runs out on the 14th" is what they can act on; "it runs out at 09:42:17 on the 14th"
 * is precision nobody can use and a deadline the sweep would miss by hours anyway, because the
 * sweep runs once a night. It is the same argument {@code PointsExpiringNext} makes for reporting
 * an anniversary as a date, and it is why this answers {@link LocalDate} rather than an instant:
 * a moment handed to a browser is a moment that browser turns into whichever day its own machine
 * is set to, and a customer in London would be told a deadline a day early.
 *
 * <p><strong>The zone is the one this application already counts calendars in</strong>, named once
 * where it lives rather than a second time here.
 *
 * <p><strong>The day it runs out is the last day it is good</strong>, inclusive — the same reading
 * the spec gives an offer's closing day, and the reading every person who sees a date on a voucher
 * will assume. So a shelf life of one day is a voucher good for the day it was claimed and no
 * longer, seven days is that day and the six after it, and a voucher claimed on the 1st with
 * thirty days on it is good through the 30th. The alternative — the first day it is <em>not</em>
 * good — makes the number on the administration form and the date on the customer's screen
 * disagree by one about what "a week" is, and somebody would be turned away at a counter on a day
 * their own screen said the thing was still good.
 *
 * <p>There is deliberately no {@code hasRunOutBy} beside this. The sweep asks the database for the
 * vouchers whose day has passed, because it has to read them anyway, and a predicate here would be
 * the same comparison in a second place that could come to differ from the query — which is the
 * reason {@code PointsExpiry} gives for having no predicate of its own either.
 */
final class VoucherShelfLife {

    /**
     * The shortest shelf life an offer may set, in days.
     *
     * <p>One rather than nought, because a voucher whose last good day is the day before it was
     * issued is not a shelf life, it is a voucher nobody can ever use — and an administrator who
     * typed a nought meant either "no expiry" (which is leaving the box empty) or they made a
     * mistake. It is also the shortest thing that can be demonstrated: {@code MovableClock}
     * advances in whole calendar days, so anything finer than a day cannot be wound past in a
     * training session and would be a rule nobody could show working.
     */
    static final int THE_LEAST_A_SHELF_LIFE_CAN_BE = 1;

    private VoucherShelfLife() {
    }

    /**
     * The last day a voucher claimed at this moment, from an offer with this shelf life, is good.
     *
     * <p>Counted in whole days from the calendar day the claim was made on, rather than in hours
     * from the moment it was made. Two customers who claimed the same thing on the same afternoon
     * hold vouchers that run out on the same day, which is what both of them would tell you they
     * had been promised — and an hours-based deadline would make one of them a few hours luckier
     * than the other for having queued later.
     */
    static LocalDate theDayItRunsOutOn(Instant claimedAt, int validForDays) {
        return claimedAt.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                .toLocalDate()
                .plusDays(validForDays - 1L);
    }
}
