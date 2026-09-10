package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A notification as it is answered to whoever asks for one: the reason, the figures behind it, and
 * the two moments.
 *
 * <p>Figures rather than a sentence. Every euro and every date in this application is written
 * Dutch-style in the browser — "EUR 50,00", "9 september 2028" — and composing the sentence in Java
 * would fork that formatting into a second place that will drift. So a notification travels as its
 * reason and its numbers, and the page writes the words. Refusal messages are the exception and stay
 * where they are: a refusal's wording is domain logic, a notification's wording is not.
 *
 * <p>Public, unlike the row it is read from, because it is what the web layer names. The row itself
 * stays inside this module; a module that hands out its entities to be read elsewhere has no
 * boundary left to speak of.
 *
 * <p>{@code occursOn} is a day rather than a moment, for the reason {@code PointsExpiringNext}
 * gives: an anniversary is a day, and the zone it is read in is the backend's to pick rather than
 * the browser's.
 *
 * <p>The per-reason nullability of {@link Notification} is carried through unchanged — a balance
 * rung arrives with an {@code amount} and no {@code depositId}, {@code points} or {@code occursOn}
 * — so that whoever reads this can tell one family from the other by the reason alone.
 */
public record RaisedNotification(Long id, NotificationReason reason, Long savingsAccountId,
                                 Long depositId, BigDecimal amount, Long points, LocalDate occursOn,
                                 Instant raisedAt, Instant readAt) {

    /**
     * The row, read out. Package-private although the record is not: only this module has a row to
     * read one from, and only this module should be able to make one that claims to be a
     * notification.
     */
    static RaisedNotification of(Notification notification) {
        return new RaisedNotification(
                notification.getId(),
                notification.getReason(),
                notification.getSavingsAccountId(),
                notification.getDepositId(),
                notification.getAmount(),
                notification.getPoints(),
                notification.getOccursOn(),
                notification.getRaisedAt(),
                notification.getReadAt());
    }
}
