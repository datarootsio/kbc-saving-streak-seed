package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;

import io.dataroots.savingstreak.challenges.AnAchievement;

/**
 * One thing in the trophy case as a page draws it: which challenge, which rung of it, when it was
 * won, the reading that won it and the points it paid.
 *
 * <p>The reading and the points are the figures recorded at the moment it was won rather than
 * anything worked out now, which is what lets an old badge explain itself: a customer looking at a
 * bronze from last spring can see what their saving stood at on the day, whatever the challenge asks
 * for since.
 *
 * <p>The rung travels as its name and the challenge as both its code and its title, so a page can
 * label a badge without keeping a list of the bank's challenges of its own.
 */
record AchievementResponse(Long id, String challenge, String title, String rung, Instant awardedAt,
                           BigDecimal reading, long points) {

    static AchievementResponse of(AnAchievement achievement) {
        return new AchievementResponse(
                achievement.id(),
                achievement.challengeCode(),
                achievement.challengeTitle(),
                achievement.rung().name(),
                achievement.awardedAt(),
                achievement.reading(),
                achievement.points());
    }
}
