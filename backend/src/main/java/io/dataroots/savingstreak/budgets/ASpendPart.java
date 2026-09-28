package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;

/**
 * One part of a split as its holder finds it: how much of the spend it accounts for, and what that
 * part is filed under.
 *
 * <p>{@code categoryId} and {@code categoryName} are both nothing at all on an uncategorised part,
 * which is a state and not a gap: the customer recorded the money and has not decided what it was
 * for. Whoever draws this says so in their own words — "Not filed yet" — because how to word an
 * absence on a screen is a reading, exactly as a category's colour is.
 *
 * <p><strong>The name travels with the identifier.</strong> A page drawing a split would otherwise
 * have to look each category up in a list it fetched separately, and one of those categories may
 * have been ended since — an ended category keeps every euro ever filed under it and has left the
 * standing list, so the lookup would come back empty and a real record would be drawn as an unknown
 * one. The name here is the name the category has now, not the name it had when the spend was
 * recorded: renaming a category is a correction to how a word was spelled, and everything filed
 * under it follows the name.
 */
public record ASpendPart(Long categoryId, String categoryName, BigDecimal amount) {
}
