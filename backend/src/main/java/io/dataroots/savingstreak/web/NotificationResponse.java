package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.notifications.NotificationReason;
import io.dataroots.savingstreak.notifications.RaisedNotification;

/**
 * One notification as the API reports it: the reason a rule decided something was worth saying, and
 * the figures that produced it.
 *
 * <p><strong>Figures and no sentence.</strong> Every euro and every date in this application is
 * written Dutch-style in the browser — "EUR 50,00", "9 september 2028" — and a sentence composed in
 * Java would fork that formatting into a second place that will drift. So a notification travels as
 * its reason and its numbers and the page writes the words, exactly as it already composes "7 base
 * + 2 bonus at 1,30x + 30 loyalty" out of three numbers on a deposit. Refusals are the exception and
 * stay as sentences, because a refusal's wording is domain logic and a notification's wording is
 * not.
 *
 * <p>The reason travels as {@link NotificationReason} rather than as a {@code String}, which Jackson
 * writes as the enum's own name — the same word the page switches on to pick its sentence and its
 * icon. Unlike the money-movement ledger's direction, nothing here has to be turned into text first:
 * the enum is public precisely so that the contract can name it.
 *
 * <p>Which figures are filled in is decided by the reason and is total. A balance notification
 * carries {@code amount}, the rung, and no {@code depositId}, {@code points} or {@code occursOn}; a
 * loyalty notification carries {@code depositId}, {@code points} and {@code occursOn} and no
 * {@code amount}. A page reading one knows from the reason alone which fields it can rely on.
 *
 * <p>{@code occursOn} is a day and not a moment, for the reason {@code PointsExpiringNext} gives:
 * an anniversary is a calendar day decided in {@code Europe/Brussels}, and sending it as an instant
 * would let a browser in another zone render it as the day before.
 *
 * <p>{@code readAt} is a moment rather than a flag, and it is null until somebody looks. Two states
 * and not three: there is no dismissing a notification, so read and unread is the whole state
 * machine, and the moment says when rather than only whether.
 */
record NotificationResponse(Long id, NotificationReason reason, Long savingsAccountId,
                            Long depositId, BigDecimal amount, Long points, LocalDate occursOn,
                            Instant raisedAt, Instant readAt) {

    static NotificationResponse of(RaisedNotification raised) {
        return new NotificationResponse(raised.id(), raised.reason(), raised.savingsAccountId(),
                raised.depositId(), raised.amount(), raised.points(), raised.occursOn(),
                raised.raisedAt(), raised.readAt());
    }
}
