package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
 * carries {@code amount}, the rung, and no {@code depositId}, {@code occurrenceId}, {@code points}
 * or {@code occursOn}; a loyalty notification carries {@code depositId}, {@code points} and
 * {@code occursOn} and no {@code amount}; an automatic transfer that did not happen carries
 * {@code occurrenceId}, {@code occursOn} — the day it was due — and {@code amount}, which for that
 * reason is what the current account was short rather than a rung; a bill that could not be paid
 * carries {@code billId}, {@code billName}, {@code occursOn} — the day it was owed — {@code amount},
 * what the bill asked for, and {@code balance}, what the account actually held; arrears piling up
 * carry {@code arrears}, how many are outstanding, and {@code amount}, what they come to; a budget
 * running low and a budget overspent carry {@code categoryId}, {@code categoryName},
 * {@code occursOn} — the day the month they are about began — {@code amount}, what that month
 * allows, and {@code balance}, what has been spent against it; a month over-committed carries
 * {@code occursOn}, the same day, {@code amount}, what the month is promised to, and
 * {@code balance}, what it has. A page reading one knows from the reason alone which fields it can
 * rely on.
 *
 * <p><strong>{@code occursOn} is a day and it means a different day per reason.</strong> The three
 * newest reasons send the day their month began rather than a month of their own, because that is a
 * date and it is the honest one, and a second date field meaning "month" would be one three reasons
 * out of ten could use. A page that wants the month reads the month off that day, in the zone the
 * backend has already decided it in.
 *
 * <p><strong>Two account references, and each of them null for the reasons that are not about that
 * account.</strong> {@code savingsAccountId} was on every notification until bills arrived; an
 * unpaid rent is a fact about a current account and there is no savings account it is about, so it
 * is null there and {@code currentAccountId} is filled instead. A page that puts a notification
 * beside the account it concerns reads whichever of the two its screen is about, and neither is a
 * stand-in for the other.
 *
 * <p>{@code occursOn} is a day and not a moment, for the reason {@code PointsExpiringNext} gives:
 * an anniversary is a calendar day decided in {@code Europe/Brussels}, and sending it as an instant
 * would let a browser in another zone render it as the day before.
 *
 * <p><strong>{@code offerCode}, {@code offerTitle} and {@code lapsesAt} are the one reason that
 * is about a reward rather than about money</strong>, and are null on every other. A customer
 * whose turn in a waiting list came has one of the thing held for them: the title is what the
 * line says, the code is what the page's reward-icon map is keyed on, and {@code lapsesAt} is
 * the deadline — the only one this response sends as a moment rather than as a day, because a
 * hold is seventy-two hours and not a number of calendar days, and a line that said "until the
 * 14th" about a hold that ends at nine that morning would be a promise the scheme does not
 * keep.
 *
 * <p>{@code readAt} is a moment rather than a flag, and it is null until somebody looks. Two states
 * and not three: there is no dismissing a notification, so read and unread is the whole state
 * machine, and the moment says when rather than only whether.
 */
record NotificationResponse(Long id, NotificationReason reason, Long savingsAccountId,
                            Long currentAccountId, Long depositId, Long occurrenceId, Long billId,
                            String billName, Long categoryId, String categoryName,
                            BigDecimal amount, BigDecimal balance, Long arrears, Long points,
                            LocalDate occursOn, String offerCode, String offerTitle,
                            Instant lapsesAt, Long noticeId, String productCode,
                            String productName, Integer termsVersion,
                            List<String> whatIsDifferent, Instant raisedAt, Instant readAt) {

    static NotificationResponse of(RaisedNotification raised) {
        return new NotificationResponse(raised.id(), raised.reason(), raised.savingsAccountId(),
                raised.currentAccountId(), raised.depositId(), raised.occurrenceId(),
                raised.billId(), raised.billName(), raised.categoryId(), raised.categoryName(),
                raised.amount(), raised.balance(), raised.arrears(), raised.points(),
                raised.occursOn(), raised.offerCode(), raised.offerTitle(), raised.lapsesAt(),
                raised.noticeId(), raised.productCode(), raised.productName(),
                raised.termsVersion(), raised.whatIsDifferent(), raised.raisedAt(),
                raised.readAt());
    }
}
