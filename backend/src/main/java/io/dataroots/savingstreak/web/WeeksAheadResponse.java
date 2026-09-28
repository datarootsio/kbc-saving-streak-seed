package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.budgets.TheWeeksAhead;

/**
 * The next six weeks of one current account's cash flow as the API reports it, and what that says
 * its holder could save each week.
 *
 * <p><strong>The figure the whole feature exists to put in front of somebody.</strong> The rows say
 * what each week brings and what claims it; the totals say what the six of them leave altogether;
 * and {@code couldSaveWeekly} is that total divided by six — the sentence the application could not
 * say before, which is how much a customer can put away without breaking anything they have already
 * promised themselves.
 *
 * <p><strong>{@code couldSaveWeekly} is offered and never applied.</strong> Nothing here writes to
 * the declared saving capacity. The two are drawn side by side and adopting this figure is a press
 * that sends it to the capacity endpoint the goals engine already owns, which is why they are
 * assembled beside each other in this layer and the frontend rather than by either module reaching
 * into the other — the argument {@code CustomerController.moneyMovementsOf} makes about the ledger,
 * applied to a figure instead of a list.
 *
 * <p>It is never negative. A customer whose six weeks do not cover themselves could save nothing,
 * and a negative offer is not one; {@code leftOver} beside it is the honest figure and is negative
 * when it is negative, which is the warning half of the same read. {@code worthOffering} is the
 * comparison already made, so that a page does not decide for itself when there is an offer to show.
 *
 * <p><strong>{@code from} and {@code until} are sent rather than inferred</strong>, for the reason
 * {@link MonthAheadResponse} sends the ends of its own window: a page working out what week it is
 * would draw six weeks nobody in this application is in on a wound clock, and an account with
 * nothing coming would otherwise leave it unable to say how far "nothing" reaches.
 *
 * <p>Every figure is derived on the read, from the declarations, the movements and the same bill and
 * payday calendars the nightly runs walk. Nothing is stored.
 */
record WeeksAheadResponse(long currentAccountId, LocalDate from, LocalDate until,
                          BigDecimal arriving, BigDecimal committed, BigDecimal claimedByBudgets,
                          BigDecimal leftOver, BigDecimal couldSaveWeekly, boolean worthOffering,
                          List<WeekAheadResponse> weeks) {

    static WeeksAheadResponse of(TheWeeksAhead ahead) {
        return new WeeksAheadResponse(ahead.currentAccountId(), ahead.from(), ahead.until(),
                ahead.arriving(), ahead.committed(), ahead.claimedByBudgets(), ahead.leftOver(),
                ahead.couldSaveWeekly(), ahead.isWorthOffering(),
                ahead.weeks().stream().map(WeekAheadResponse::of).toList());
    }
}
