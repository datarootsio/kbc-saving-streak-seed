package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day a declared income is going to land, and what it will bring when it does.
 *
 * <p>{@link ABillToCome} the other way round, and a separate record for the reason that one is
 * separate from {@link ABillThatFellDue}: a payday still to come has no moment it was credited at
 * and no outcome, because neither of those has happened yet, and a record with half its fields null
 * would leave a reader unable to tell "this has not happened" from "this happened and moved
 * nothing".
 *
 * <p><strong>Why the days and not only the total.</strong> {@code TheMonthAhead} wants one figure —
 * what is coming in, over one window — and the days behind it say nothing it draws. A forecast cut
 * into weeks wants the opposite: which week a salary lands in is the whole of what makes one weekly
 * row the one that pays for the others, and a total over six weeks would put every euro of it in no
 * week at all. So the dates are answered and whoever wants the total adds them up, rather than two
 * calendars being walked for two shapes of the same answer.
 *
 * <p>The dates come from {@link WhenIncomeIsDue}, which is the same calendar the 01:00 run asks, so
 * a forecast quotes the day a salary will actually be credited on — month-end clamp and all. Months
 * a payday is already recorded for are dropped by the same question the run drops them with, so the
 * two cannot disagree about which paydays are left.
 *
 * <p><strong>{@code owedRatherThanStillToCome} is the one thing about a day alone that the day does
 * not say.</strong> An income is forecast from its own cursor rather than from today, exactly as the
 * run counts, so a payday the run has not caught up with comes back dated before the window opens.
 * On a wound clock that is the ordinary state of every declaration, because the clock moves in whole
 * calendar days and the cron never fires for the ones it skipped. Those are credits the <em>next</em>
 * run will make, and a forecast that clamped its bottom to today would leave them out and then watch
 * the run pay them.
 *
 * <p>{@code amount} is what the declaration says now. A salary that goes up in April changes every
 * day in the forecast, because the forecast is derived on every read and stored nowhere: it is what
 * the run would credit if it ran, not a promise made earlier.
 *
 * <p>Public, unlike the row behind it: it is what leaves this module, to the web layer and to
 * Budgets, which cuts it into weeks.
 */
public record AnIncomeToCome(long currentAccountId, LocalDate dueOn, BigDecimal amount,
                             boolean owedRatherThanStillToCome) {
}
