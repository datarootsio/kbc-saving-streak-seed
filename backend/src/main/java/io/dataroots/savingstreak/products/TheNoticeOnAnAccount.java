package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything one savings account's notice says at once: how many days the agreement asks for, what
 * is ready to take today, what is still waiting, and the notices behind both figures.
 *
 * <p><strong>One reading rather than four requests.</strong> "How much can I take today" and "what
 * is still running" are the same walk over the same rows against the same reading of the clock, and
 * a screen that asked for them separately could be told two things that were true a second apart.
 * The same argument {@code AccountPairing} makes about three questions that have to be asked in the
 * right order to mean anything.
 *
 * <p><strong>{@code noticeDays} is on it, and is what tells a page whether any of this
 * applies.</strong> Nought means the account has nothing to give notice of, the two amounts are
 * nought, the list is empty, and a screen draws no panel at all. That is free savings and the core
 * saver, and saying so as a figure rather than as an absence is what lets one component render
 * every account without asking what kind it is.
 *
 * <p><strong>{@code readyToTakeToday} is about notice and not about the balance.</strong> It is
 * what ready notice covers, which is a ceiling rather than a promise: how much the account actually
 * holds and how much of that a savings goal has spoken for are two other modules' answers, and a
 * withdrawal is still weighed against all three. A reading that tried to be the whole answer here
 * would be the second place this application decides what can be withdrawn.
 *
 * <p>The notices are the ones still standing, oldest first, so that the order they are listed in is
 * the order a withdrawal will use them in — a customer watching a withdrawal spend the top of the
 * list is watching the rule rather than being told about it.
 */
public record TheNoticeOnAnAccount(

        long savingsAccountId,

        int noticeDays,

        BigDecimal readyToTakeToday,

        BigDecimal stillWaiting,

        List<NoticeGiven> notices) {
}
