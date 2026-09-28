package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A notification as the API reports one, which is exactly as a test reads it: which rule spoke, the
 * pot and the deposit it was about, the figures behind it, when it was raised and when it was read.
 *
 * <p>The reason is read as the word the API sends rather than mapped onto the domain's own enum, for
 * the reason {@link MoneyMovementView} gives about a direction: a rename in the backend should fail a
 * test rather than be quietly translated back into the name the test meant. The page switches on that
 * word too, so a test asserting on it is asserting on the contract the frontend reads.
 *
 * <p>Which figures are filled in depends on the reason, and a test asserting that the ones that do
 * not apply came back null is asserting a real part of the contract — a balance rung is about an
 * account and about no one deposit, a coming anniversary is about one deposit and names no rung, and
 * an automatic transfer that did not happen names the occurrence it is about and no deposit at all.
 *
 * <p>Two account references, and each null for the reasons that are not about that account: a bill
 * that could not be paid and arrears piling up are facts about a current account and name no savings
 * account, so a test asserting that {@code savingsAccountId} came back null on one of those is
 * asserting a real part of the contract too.
 *
 * <p>{@code occursOn} means a different day for each family, and for the three reasons about budgets
 * it is the day the month the warning is about <em>began</em> — a month written down as a date,
 * which is the bargain the record strikes rather than carrying a column meaning "month". A test
 * asserting that a warning was about March asserts it by that day.
 *
 * <p>{@code offerCode}, {@code offerTitle} and {@code lapsesAt} belong to the one reason that is
 * about a reward rather than about money — a customer whose turn in a waiting list came — and are
 * null on every other, so a test asserting that a balance rung carries no offer is asserting a
 * real part of the contract. {@code lapsesAt} is an {@link Instant} because a hold is
 * seventy-two hours rather than a number of calendar days, which is the same reason
 * {@link HoldView}'s own deadline is one.
 *
 * <p>{@code noticeId}, {@code productCode}, {@code productName}, {@code termsVersion} and
 * {@code whatIsDifferent} belong to the three reasons about an agreement rather than about money —
 * notice that has run its days, a term coming up for maturity, and a product that has bettered the
 * terms an account is on — and are null, or empty, on every other. A test asserting that a balance
 * rung carries no product is asserting a real part of the contract.
 *
 * <p>{@code whatIsDifferent} is the <em>backend's</em> wording, quoted from the one function that
 * puts a difference between two agreements into words, so a test may assert on the sentence itself
 * and is asserting that the notice and the product's version history say the same thing. That the
 * move was an improvement is nowhere in those words: it is the reason, and asserting on the reason
 * is how a test says the judgement was made.
 */
public record NotificationView(Long id, String reason, Long savingsAccountId,
                               Long currentAccountId, Long depositId, Long occurrenceId,
                               Long billId, String billName, Long categoryId, String categoryName,
                               BigDecimal amount, BigDecimal balance, Long arrears, Long points,
                               LocalDate occursOn, String offerCode, String offerTitle,
                               Instant lapsesAt, Long noticeId, String productCode,
                               String productName, Integer termsVersion,
                               List<String> whatIsDifferent, Instant raisedAt, Instant readAt) {
}
