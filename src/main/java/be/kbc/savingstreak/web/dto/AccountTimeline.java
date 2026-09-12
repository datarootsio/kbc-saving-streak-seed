package be.kbc.savingstreak.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A year behind and a year ahead of one savings account. Both halves are needed: every points
 * expiry and every loyalty anniversary falls exactly twelve months after the deposit that
 * caused it, so a single twelve month window can never show a deposit and its maturity
 * together.
 */
public record AccountTimeline(
        LocalDate from,
        LocalDate today,
        LocalDate to,
        /** Where today sits in the window, as a percentage. */
        int nowPosition,
        /** The largest money movement in the window, for scaling the bars. */
        BigDecimal largestMovement,
        List<TimelineEvent> events,
        /** Money movements that fall before the window starts. */
        int earlierMovements,
        int nextBonusPoints,
        LocalDate nextBonusOn,
        int expiringNextPoints,
        LocalDate expiringNextOn) {
}
