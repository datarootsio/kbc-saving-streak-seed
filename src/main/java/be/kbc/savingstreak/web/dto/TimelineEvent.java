package be.kbc.savingstreak.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One mark on a savings account's timeline: money arriving or leaving, or a points event
 * maturing (a loyalty bonus, or a batch of points lapsing).
 */
public record TimelineEvent(
        /** MONEY_IN, MONEY_OUT, BONUS_PAID, BONUS_DUE, POINTS_LAPSED or POINTS_EXPIRING. */
        String kind,
        LocalDate on,
        /** Where the date sits in the window, 0 at the left edge and 100 at the right. */
        int position,
        /** Set for money events. */
        BigDecimal amount,
        /** Set for points events. */
        int points,
        String label) {
}
