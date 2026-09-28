package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.dataroots.savingstreak.budgets.ARecordedSpend;

/**
 * One spend as the API reports it: what its holder called it, what it cost, when it was recorded,
 * and what it was for.
 *
 * <p>One shape for what recording a spend gives back and for the list read afterwards — the same
 * bargain {@link RecurringBillResponse} and {@link SpendingCategoryResponse} strike — so that a page
 * which has just recorded one does not have to fetch the list again to see what it did.
 *
 * <p>The split comes down with it rather than behind a second request per spend. A spend without its
 * parts is half a record: the amount says a balance fell and the split says what its holder believes
 * it fell for, and a page drawing a list would make one request per row to put them back together.
 *
 * <p>Nothing here says what the account holds afterwards. That is Accounts' answer, read from
 * Accounts by the page that needs it, and a balance copied onto a spend would be wrong the moment
 * anything else moved.
 *
 * <p><strong>{@code correctedAt} is null on a spend nobody has corrected</strong>, and that null is
 * the whole of what the field is for: the ledger says plainly which spends were not right the first
 * time rather than quietly showing something different from what it showed yesterday. A stand-in
 * date would say it about every spend in the application. How to word the difference on a screen is
 * a reading and belongs to whoever draws it.
 */
record SpendResponse(long spendId, long currentAccountId, String name, BigDecimal amount,
                     Instant recordedAt, Instant correctedAt, List<SpendPartResponse> parts) {

    static SpendResponse of(ARecordedSpend spend) {
        return new SpendResponse(spend.spendId(), spend.currentAccountId(), spend.name(),
                spend.amount(), spend.recordedAt(), spend.correctedAt(),
                spend.parts().stream().map(SpendPartResponse::of).toList());
    }
}
