package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.products.WhatCanLeaveToday;

/**
 * What a savings account holds and how much of it the agreement would let leave today, with the one
 * condition in the way when the two differ.
 *
 * <p><strong>One figure, so that no screen has to compose one.</strong> Before this reading a page
 * wanting "what can I take today" had to fetch the balance, the term and the notice and put the
 * three together — which means holding the order the conditions compose in, on a screen, where the
 * day a fourth condition is sold nobody would think to look. The module that owns the conditions
 * answers, and a panel prints.
 *
 * <p><strong>{@code whyItIsLess} is the sentence a withdrawal of the whole balance would be refused
 * in, carried untouched.</strong> Not a wording invented for a screen: the same words, from the same
 * rule, so somebody who reads the panel and then types the amount anyway is told the same thing
 * twice rather than two different things. It is null exactly when {@code freeToTakeToday} equals
 * {@code balance}, and {@code condition} is null with it — a reason without a shortfall would be a
 * warning about nothing.
 *
 * <p><strong>{@code condition} is the named constant beside the sentence</strong>, for the reason
 * {@code AConditionInTheWay} gives about carrying both: the sentence is for the person, and the
 * constant is for a screen that wants to draw a locked account differently from one waiting on
 * notice without matching on prose a later slice will reword.
 *
 * <p><strong>It is the agreement's answer and not the whole of what may be withdrawn.</strong> What
 * a savings goal has claimed is the Goals module's reading, answered on its own endpoint and shown
 * beside this one — the same line {@link TheNoticeOnAnAccountResponse} takes about the same
 * question, and for the same reason: a reading that tried to be the whole answer would be the second
 * place this application decides what can leave a savings account.
 */
record WhatCanLeaveTodayResponse(long savingsAccountId, BigDecimal balance,
                                 BigDecimal freeToTakeToday, String condition, String whyItIsLess) {

    static WhatCanLeaveTodayResponse of(WhatCanLeaveToday free) {
        return new WhatCanLeaveTodayResponse(
                free.savingsAccountId(),
                free.balance(),
                free.freeToTakeToday(),
                free.condition() == null ? null : free.condition().name(),
                free.whyItIsLess());
    }
}
