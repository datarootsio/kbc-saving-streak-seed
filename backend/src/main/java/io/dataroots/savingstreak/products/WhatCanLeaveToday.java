package io.dataroots.savingstreak.products;

import java.math.BigDecimal;

import io.dataroots.savingstreak.deposits.ConditionOnTheWayOut;

/**
 * How much of what a savings account holds its agreement would let go today, beside what it holds —
 * and, when the two differ, the one condition in the way and the sentence that says what to do
 * about it.
 *
 * <p><strong>One figure rather than three readings and a subtraction.</strong> "What can I take
 * today" is answered today by a term reading, a notice reading and a balance, and anybody wanting
 * the number has had to fetch all three and combine them — which is a rule about how conditions
 * compose, living on whichever screen last needed it. This reading is that rule, in the module that
 * owns the conditions, so the figure a customer is shown is the figure the withdrawal gate will
 * agree with.
 *
 * <p><strong>Nothing here is a new rule, and that is the point.</strong> Whether anything is in the
 * way at all is {@link TheConditionsOnTheWayOut} answering the same question a withdrawal asks it,
 * with the same sentence; how much is free when something <em>is</em> in the way is
 * {@code FixedTermsService}'s reading and {@code NoticesService}'s reading quoted, never restated. A
 * class that worked out for itself that a locked term frees nothing would be the second place that
 * was decided, and the second place is the one that is wrong the morning a rule gains a clause.
 *
 * <p><strong>{@code whyItIsLess} is null exactly when {@code freeToTakeToday} equals
 * {@code balance}.</strong> The two travel together so that a screen never has to decide what an
 * absent reason means: a reason without a shortfall would be a warning about nothing, and a
 * shortfall without a reason would be a figure a customer cannot act on. The named
 * {@link ConditionOnTheWayOut} travels beside the sentence for the reason
 * {@code AConditionInTheWay} gives about carrying both — the sentence is for the person, the
 * constant is for the log and for a screen that wants to branch without matching on prose.
 *
 * <p><strong>It is what the <em>agreement</em> lets go, and not the whole of what may be
 * withdrawn.</strong> A withdrawal is weighed against three things — the balance, the agreement and
 * what a savings goal has claimed — and the third is the Goals module's answer, read separately and
 * shown beside this one. Folding it in here would put a reading of somebody's goals inside the
 * module that keeps their agreement, which is the boundary {@code TheNoticeOnAnAccount} already
 * declines to cross about the same question.
 *
 * <p>An account holding nothing is answered with nothing free, nothing in the way and no sentence.
 * There is no money for a condition to hold, so saying a locked term stands in the way of an empty
 * account would be a warning about a withdrawal nobody could make.
 */
public record WhatCanLeaveToday(

        /** The savings account this is about. */
        long savingsAccountId,

        /** What the account holds today, which is the ceiling every figure here sits under. */
        BigDecimal balance,

        /** How much of that the agreement would let leave today, in euros, never above the balance. */
        BigDecimal freeToTakeToday,

        /** The one condition in the way, and null when the whole balance is free. */
        ConditionOnTheWayOut condition,

        /** That condition as the sentence a withdrawal is refused in, and null when there is none. */
        String whyItIsLess) {
}
