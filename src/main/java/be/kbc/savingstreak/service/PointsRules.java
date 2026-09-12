package be.kbc.savingstreak.service;

import org.springframework.stereotype.Component;

/**
 * The loyalty rules of Saving Streak.
 *
 * <p>Every whole euro moved into a savings account is worth {@value #POINTS_PER_EURO} point. On
 * top of that, each consecutive week of saving adds {@value #BONUS_BP_PER_WEEK} basis points to
 * the multiplier, up to {@value #MAX_MULTIPLIER_BP} (a 50% bonus).
 *
 * <p>A week only joins the streak once {@value #WEEKLY_MINIMUM_CENTS} eurocents have been
 * deposited in it; smaller deposits still earn points, they just do not secure the week.
 */
@Component
public class PointsRules {

    public static final int POINTS_PER_EURO = 1;
    public static final int BASE_MULTIPLIER_BP = 10_000;
    public static final int BONUS_BP_PER_WEEK = 1_000;
    public static final int MAX_MULTIPLIER_BP = 15_000;
    public static final long WEEKLY_MINIMUM_CENTS = 5_000;
    /** How long points stay valid after they are earned. */
    public static final int POINTS_VALID_MONTHS = 12;
    /** The loyalty rate: paid on every anniversary a deposit stays untouched. */
    public static final int LOYALTY_BONUS_BP = 1_000;
    public static final int LOYALTY_PERIOD_MONTHS = 12;

    /** Whether a week's deposits are enough to let that week join the streak. */
    public boolean securesTheWeek(long depositedThisWeekCents) {
        return depositedThisWeekCents >= WEEKLY_MINIMUM_CENTS;
    }

    public int multiplierBasisPoints(int streakWeeks) {
        int weeksBeyondFirst = Math.max(0, streakWeeks - 1);
        return Math.min(BASE_MULTIPLIER_BP + weeksBeyondFirst * BONUS_BP_PER_WEEK, MAX_MULTIPLIER_BP);
    }

    public int basePoints(long amountCents) {
        return (int) (amountCents * POINTS_PER_EURO / 100);
    }

    /** The loyalty bonus a principal is worth: 10% of the base points it would earn. */
    public int loyaltyBonusFor(long principalCents) {
        return (int) ((long) basePoints(principalCents) * LOYALTY_BONUS_BP / BASE_MULTIPLIER_BP);
    }

    public int pointsFor(long amountCents, int streakWeeks) {
        return (int) ((long) basePoints(amountCents) * multiplierBasisPoints(streakWeeks) / BASE_MULTIPLIER_BP);
    }
}
