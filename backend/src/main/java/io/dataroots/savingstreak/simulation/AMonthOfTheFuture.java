package io.dataroots.savingstreak.simulation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * One month of a branch: where it leaves the money, the points and the run of weeks, and what
 * happened to the points inside it.
 *
 * <p><strong>Seven figures a customer can check against each other with a pencil.</strong> The
 * points balance at the end of a month is the one at the end of the month before, plus what was
 * earned, plus what a bonus paid, less what expired — so a row that does not add up is a fold that
 * has gone wrong, and a reader can find out without reading any code. That is the whole reason the
 * three movements are reported beside the balance rather than left to be inferred from the
 * difference between two balances: a difference of zero can be nothing happening or six hundred
 * points arriving and six hundred going, and those are very different years.
 *
 * <p><strong>Nothing here is summed across the window, and that is a rule rather than an
 * omission.</strong> There is no "points you would gain" and no net figure anywhere in this answer.
 * The same point can be paid on an anniversary and expire inside the same twelve months, so a net
 * counts one point twice in opposite directions — the argument {@code AccountTimeline} already makes
 * about its own bar, made again here because a projection is where somebody would most like a single
 * number and where a single number would be most misleading. A comparison between branches is row
 * against row.
 *
 * <p><strong>The months are counted from the day the window opened rather than from the first of a
 * calendar month</strong>, and this is the decision on this record worth reading twice. A window
 * that opens on the 19th of September closes on the 19th of September a year later, and twelve rows
 * ending on the 19th of each month cover it exactly once with nothing over. Calendar months would
 * have given thirteen of them, the first and the last both part-months, and the customer would have
 * been shown a year made of fourteen boundaries instead of twelve. It is also what makes the
 * simulator checkable: wind the application's clock three months and the day it then reads is
 * {@code closesOn} on the third row, so the balance, the points and the run of weeks the application
 * reports can be put beside the three figures that row predicted. A row closing on the last day of
 * August could only ever be compared against a clock somebody wound to exactly that day.
 *
 * <p>{@code month} is the calendar month {@code closesOn} falls in, which is what a page writes at
 * the foot of a bar, and the twelve of them are always twelve different months because each closing
 * day is one calendar month after the last. It is a label rather than a boundary: the month's
 * figures are those of the stretch ending on {@code closesOn}, not those of the calendar month
 * named. Both are here so that a page need not work either of them out, and so that nothing is
 * derived against the browser's own clock — which can be a year away from the application's.
 *
 * <p>{@code securedWeeks} is the run as the application would report it on {@code closesOn}: the
 * consecutive secured weeks behind the customer, counting the week they are part-way through if
 * enough has already landed in it. The same reading {@code StreakOfSecuredWeeks} gives on the
 * account's own screen, so the two can be compared without anybody adjusting for a week in progress.
 *
 * <p>Every figure on it is an illustration rather than a promise. That is said once at the top of the
 * answer rather than on each of ninety fields, for the reason the response gives.
 */
public record AMonthOfTheFuture(YearMonth month, LocalDate closesOn, BigDecimal balance,
                                long pointsStanding, int securedWeeks, long pointsEarned,
                                long pointsABonusPaid, long pointsThatExpired) {
}
