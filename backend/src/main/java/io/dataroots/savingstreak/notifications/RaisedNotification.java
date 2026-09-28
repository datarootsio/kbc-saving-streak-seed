package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One notification as it leaves this module: the reason a rule decided something was worth saying,
 * and every figure behind it.
 *
 * <p>A record rather than the entity, for the reason every other module's read model exists: the row
 * is package-private and stays that way, and a module that handed out its entities to be read
 * elsewhere would have no boundary left to speak of.
 *
 * <p>Which fields are filled is decided by the reason and is total — {@link Notification} sets out
 * which belong to which — so a caller that has read the reason knows what it can rely on. Two
 * account references rather than one, because a savings balance and an unpaid rent are facts about
 * two different accounts and a column meaning "whichever account the reason implies" is one nobody
 * can query. A bill reference and a category reference sit beside each other for the same reason,
 * and neither stands in for the other.
 *
 * <p><strong>{@code occursOn} is a day, and which day it is depends on the reason</strong> — which
 * is the one field a caller has to read {@link Notification}'s own table before using. It is an
 * anniversary, a day a transfer was due, a day a bill was owed, and, for the three reasons about
 * budgets, the day the month the warning is about began. A month is not a type a database has and
 * the first of it is a date that sorts and compares without anybody decoding it, which is the same
 * bargain {@code TheMonthAMomentFallsIn} strikes from the other side of the application.
 *
 * <p><strong>{@code offerCode}, {@code offerTitle} and {@code lapsesAt} belong to one reason
 * and are null on every other</strong>: a customer whose turn in a waiting list came and who
 * now has one of the thing held for them. The title is a snapshot, which is what lets an inbox
 * line go on reading correctly after the catalogue has been renamed; the code is what the
 * page's reward-icon map is keyed on. {@code lapsesAt} is the only deadline in this record that
 * is a moment rather than a day, because a hold is seventy-two hours rather than a number of
 * calendar days — the argument is on {@link Notification} and on
 * {@code rewards.TheShelfLifeOfAHold}.
 *
 * <p><strong>{@code noticeId}, {@code productCode}, {@code productName}, {@code termsVersion} and
 * {@code whatIsDifferent} belong to the three reasons about a savings agreement</strong>, and each
 * is null — or, for the list, empty — on every other. Notice that has run its days names the
 * notice it is about; a term coming up for maturity names its product and carries the day in
 * {@code occursOn}; a product that has bettered an account's terms names the product and the
 * version on offer, carries the two headline rates in {@code amount} and {@code balance}, and
 * carries what the two agreements say differently.
 *
 * <p><strong>{@code whatIsDifferent} is quoted and never composed.</strong> Every sentence in it is
 * {@code products.WhatIsDifferentBetweenTwoSetsOfTerms}'s, which is the one place in this
 * application a difference between two agreements is put into words — the same sentences a
 * product's version history prints. What this module decided for itself is that the move was an
 * improvement, and that judgement is a reason rather than a sentence: it is why the row exists and
 * it is written nowhere inside the words. A list rather than one blob, because each sentence is
 * about one figure and a page draws them as the list they are.
 */
public record RaisedNotification(Long id, NotificationReason reason, Long savingsAccountId,
                                 Long currentAccountId, Long depositId, Long occurrenceId,
                                 Long billId, String billName, Long categoryId,
                                 String categoryName, BigDecimal amount, BigDecimal balance,
                                 Long arrears, Long points, LocalDate occursOn,
                                 String offerCode, String offerTitle, Instant lapsesAt,
                                 Long noticeId, String productCode, String productName,
                                 Integer termsVersion, List<String> whatIsDifferent,
                                 Instant raisedAt, Instant readAt) {

    static RaisedNotification of(Notification notification) {
        return new RaisedNotification(
                notification.getId(),
                notification.getReason(),
                notification.getSavingsAccountId(),
                notification.getCurrentAccountId(),
                notification.getDepositId(),
                notification.getOccurrenceId(),
                notification.getBillId(),
                notification.getBillName(),
                notification.getCategoryId(),
                notification.getCategoryName(),
                notification.getAmount(),
                notification.getBalance(),
                notification.getArrears(),
                notification.getPoints(),
                notification.getOccursOn(),
                notification.getOfferCode(),
                notification.getOfferTitle(),
                notification.getLapsesAt(),
                notification.getNoticeId(),
                notification.getProductCode(),
                notification.getProductName(),
                notification.getTermsVersion(),
                notification.getWhatIsDifferent(),
                notification.getRaisedAt(),
                notification.getReadAt());
    }
}
