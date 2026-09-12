package be.kbc.savingstreak.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MemberView(
        String firstName,
        String lastName,
        String initials,
        int pointsBalance,
        int pointsEarnedTotal,
        int streakWeeks,
        int bestStreakWeeks,
        BigDecimal multiplier,
        BigDecimal nextMultiplier,
        BigDecimal newSavingsThisWeek,
        BigDecimal weeklyGoal,
        int weeklyGoalPercent,
        boolean streakSafeThisWeek,
        BigDecimal savingsPeak,
        int pointsValidMonths,
        int pointsExpiringNext,
        LocalDate pointsExpiringOn,
        int pointsLapsed,
        int loyaltyPointsEarned,
        int giftedAwayPoints,
        int giftedToYouPoints) {
}
