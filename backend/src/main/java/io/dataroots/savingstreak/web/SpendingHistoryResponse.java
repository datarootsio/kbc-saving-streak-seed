package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.budgets.HowAnAccountHasBeenGoing;

/**
 * One current account's last few months as the API reports it: which months they are, and a
 * category's worth of history under each of the account's words.
 *
 * <p>One read for the whole comparison. The alternative — a request per month, or a request per
 * category — would be six or twenty round trips for one screen, and every one of them would fold the
 * same carry chain again.
 *
 * <p><strong>{@code months} is the window every category is drawn against</strong>, oldest first, as
 * {@code "2026-03"} each. It is sent so that a page can draw one set of column headings and line a
 * category's own shorter run of months up under them: a category younger than the window, or one
 * ended inside it, has fewer rows than this list has entries, and that is the answer rather than a
 * gap to fill with noughts.
 *
 * <p>{@code month} is the month the application's clock is in, which the window ends with, and
 * {@code earliest} is the one it begins with. Both are sent rather than inferred for the reason
 * {@link MonthOfSpendingResponse} sends the days its month runs between: a page working out what
 * month it is would draw a month nobody is in on a wound clock.
 *
 * <p>A category the window holds nothing for is not in {@code categories} at all — one ended before
 * the window began is a record of months this read is not about, and a row of six blanks says
 * nothing at the cost of the space a real row would have taken.
 *
 * <p>Every figure is derived on the read from the declarations and the movements. Nothing is stored
 * and no job produces any of it, so correcting a split in a month already gone changes that month's
 * row, the average taken over it and this month's comparison against that average, all at once.
 */
record SpendingHistoryResponse(long currentAccountId, String month, String earliest,
                               List<String> months, List<CategoryComparedResponse> categories) {

    static SpendingHistoryResponse of(HowAnAccountHasBeenGoing history) {
        return new SpendingHistoryResponse(history.currentAccountId(), history.month().toString(),
                history.earliest().toString(),
                history.months().stream().map(Object::toString).toList(),
                history.categories().stream().map(CategoryComparedResponse::of).toList());
    }
}
