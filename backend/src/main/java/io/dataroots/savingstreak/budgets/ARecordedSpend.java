package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One spend as its holder finds it: what they called it, what it cost, when it was recorded, and
 * what it was for.
 *
 * <p>A record rather than the entity, like everything that leaves this module. The rows are
 * package-private and this is the shape the web layer, and any later module, is allowed to hold.
 *
 * <p>It carries its parts rather than an identifier somebody else has to go and resolve. A spend
 * without its split is half a record — the amount says a balance fell and the split says what the
 * customer believes it fell for — and a page that had to make a second request per spend to draw a
 * list would make one request per row of it.
 *
 * <p>{@code amount} is quoted to the cent here, once, where the record leaves the module. SQLite has
 * no decimal type and hands EUR 12.50 back as 12.5, and a caller adding these up or printing one
 * would otherwise either restate the rounding or print a figure that does not read as money.
 *
 * <p><strong>There is no balance in it.</strong> What the account holds afterwards is Accounts'
 * answer and is read from Accounts, by the page and by the test alike. A balance copied onto a spend
 * would be a second answer to that question and would be wrong the moment anything else moved.
 *
 * <p><strong>{@code correctedAt} is nothing at all on a spend nobody has corrected</strong>, rather
 * than a repeat of {@code recordedAt} or any other stand-in. That is the whole of what the field is
 * for: the ledger says plainly which spends were not right the first time, and a figure that was
 * always filled in would say it about every spend in the application. Whoever draws it decides how
 * to word the difference, because that is a reading — the same bargain a category's colour and an
 * unfiled part's wording are struck under.
 */
public record ARecordedSpend(long spendId, long currentAccountId, String name, BigDecimal amount,
                             Instant recordedAt, Instant correctedAt, List<ASpendPart> parts) {
}
