package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.budgets.ASpendPart;

/**
 * One part of a split as the API reports it: how much of the spend it accounts for, and what it is
 * filed under.
 *
 * <p>{@code categoryId} and {@code categoryName} are both null on an uncategorised part, which is a
 * state and not a gap — money recorded by somebody who had not decided what it was for yet. How to
 * word that on a screen is the page's own reading, exactly as a category's colour is.
 *
 * <p>The name travels beside the identifier so that a page can draw a split without holding the
 * account's category list, and so that a part filed under a category since ended still says what it
 * says. An ended category has left the standing list and keeps every euro ever filed under it.
 */
record SpendPartResponse(Long categoryId, String categoryName, BigDecimal amount) {

    static SpendPartResponse of(ASpendPart part) {
        return new SpendPartResponse(part.categoryId(), part.categoryName(), part.amount());
    }
}
