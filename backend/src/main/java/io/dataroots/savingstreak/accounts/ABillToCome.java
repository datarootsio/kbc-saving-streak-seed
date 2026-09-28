package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One date a recurring bill is going to fall due on, and what it will take when it does.
 *
 * <p>{@link ABillThatFellDue} in the future tense, and a separate record for the reason the two
 * tenses are different sentences: that one carries a moment it was settled at and an outcome, and
 * both of those are things that happened. Neither exists yet here, and a record with half its fields
 * null would leave a page unable to tell "this has not happened" from "this happened and moved
 * nothing" — which is the distinction this whole feature keeps apart everywhere else.
 *
 * <p>{@code billName} travels with the date because both forecasts that carry these are
 * <em>merged</em>: every bill on an account in one list in date order on the month-ahead read, and
 * the bills interleaved with the saving rules on the year-ahead one. A list of days with nothing but
 * identifiers on it is a list nobody can act on.
 *
 * <p>The dates come from {@link WhenABillIsDue}, which is the same calendar the nightly run asks, so
 * a forecast quotes the day a bill will actually go out on — month-end clamp and all — and the
 * amount it will actually take when it gets there. Dates a month's presentation is already recorded
 * for are dropped by the same question the run drops them with, so the two cannot disagree about
 * which dates are left.
 *
 * <p><strong>{@code owedRatherThanStillToCome} is the one thing about a line that a day alone does
 * not say.</strong> A bill is forecast from its own cursor rather than from today — exactly as the
 * run counts — so a bill the nightly run has not caught up with carries the dates it is late for at
 * the head of the list, dated before the window opens. On a wound clock that is the ordinary state
 * of every bill, because the clock moves in whole calendar days and the cron never fires for the
 * ones it skipped. Those dates are debits the <em>next</em> run will make, and a forecast that
 * clamped its bottom to today would hide them and then watch the run take them — the disagreement
 * this whole record exists not to have. A page has to be able to say which is which, and working it
 * out by comparing each day against the browser's idea of today would be re-deriving a boundary this
 * application has already drawn against its own clock.
 *
 * <p>{@code amount} is what the bill is worth now. A rent that goes up in April changes every date
 * in the forecast, because the forecast is derived on every read and stored nowhere: it is what the
 * run would take if it ran, not a promise made earlier.
 *
 * <p>Public, unlike the row and the entity behind it: it is what leaves this module, and it leaves
 * it twice — to the web layer for the month ahead, and to Automation for the year ahead.
 */
public record ABillToCome(long billId, long currentAccountId, String billName, LocalDate dueOn,
                          BigDecimal amount, boolean owedRatherThanStillToCome) {
}
