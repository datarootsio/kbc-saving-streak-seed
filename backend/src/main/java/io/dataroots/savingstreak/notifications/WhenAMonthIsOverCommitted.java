package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.util.Objects;

import io.dataroots.savingstreak.accounts.TheMonthAhead;
import io.dataroots.savingstreak.budgets.WhatACategoryCostInAMonth;
import io.dataroots.savingstreak.budgets.WhatAnAccountSpentInAMonth;

/**
 * Whether a current account has promised this month to more than the month has: what its bills, its
 * arrears and its budgets still claim, against its balance and the income still due to arrive in it.
 *
 * <p><strong>Both sides are counted forwards, and that is the whole of the arithmetic.</strong> The
 * balance being measured against is the balance now, and the money this month has already spent has
 * already come out of it. So the claims have to be the claims that are still to be met: the bills
 * still to fall plus what is already owed, which is exactly what {@code TheMonthAhead} already adds
 * up, and what is <em>left</em> of each budget rather than the whole of what it allows. Taking the
 * whole allowance against a balance the month's groceries have already left would count those
 * groceries twice, and would announce an over-committed month on every account that has ever spent
 * anything.
 *
 * <p><strong>Each budget is floored at nothing before it is added up.</strong> A category that has
 * gone over its allowance is not handing the difference back to the rest of the month — the money
 * left the account and nothing returns it — so a fifty-euro overspend on the groceries must not
 * quietly pay for fifty euros of the holiday fund. The account-level {@code left} nets the two
 * against each other, which is the right answer to "how is the plan going" and the wrong one to
 * "what is still to come out"; so the rows are added up here rather than the total read off the top
 * of them. It is the one figure in this class that is not taken straight from a read model, and
 * this is why.
 *
 * <p><strong>The month's own window and the month ahead's are not the same window, and both are
 * honest.</strong> {@code TheMonthAhead} looks from today to this day next month, which is the
 * window its own documentation argues at length for and the window the bills and the income are
 * already counted in; the budgets are a calendar month, because that is what a customer declares
 * them for. Reconciling the two would mean a second walk of {@code WhenABillIsDue} and
 * {@code WhenIncomeIsDue} in this module, which is the copy of a calendar the whole application is
 * arranged to avoid. What the difference actually costs is small and is worth naming: a bill falling
 * in the first days of next month is counted against a budget claim that stops at the end of this
 * one, so the warning leans towards being raised. That is the conservative direction for a warning
 * about money running out, and it is the same direction {@code TheMonthAhead} already leans in by
 * counting arrears as due immediately.
 *
 * <p><strong>An account with nothing budgeted in the month says nothing.</strong> This is a warning
 * about a plan being bigger than the month it is a plan for, and an account whose holder has made no
 * plan has no such thing to say. Bills and arrears outrunning a balance on their own is what
 * {@link NotificationReason#A_BILL_COULD_NOT_BE_PAID} and
 * {@link NotificationReason#BILLS_ARE_PILING_UP} are for, and the month card on the account's own
 * screen has drawn that shortfall in red since before this feature existed; a third line saying the
 * same thing in different words would be the inbox getting less worth reading rather than more.
 *
 * <p>Pure, and asserted over HTTP rather than in a test of its own, for the reason
 * {@link WhenArrearsArePilingUp} gives about its own replay.
 */
final class WhenAMonthIsOverCommitted {

    private WhenAMonthIsOverCommitted() {
    }

    /**
     * Where one account's month stands: what it is promised to, what it has, and whether the first
     * is more than the second.
     *
     * @param monthAhead what Accounts says the coming month has to cover, which is where the
     *                   balance, the income still due, the bills still to fall and the arrears all
     *                   come from — one read rather than four, so the figures cannot describe four
     *                   different instants
     * @param spending   what this module's own budgets read says the calendar month allows and has
     *                   cost, category by category
     */
    static ThePromise of(TheMonthAhead monthAhead, WhatAnAccountSpentInAMonth spending) {
        BigDecimal budgetsStillClaim = whatTheBudgetsStillClaim(spending);
        BigDecimal hasGot = monthAhead.balance().add(monthAhead.incomeDue());
        BigDecimal promisedTo = monthAhead.billsDue().add(budgetsStillClaim);
        boolean anythingBudgeted = spending.allowed() != null;
        return new ThePromise(promisedTo, hasGot, budgetsStillClaim, anythingBudgeted,
                anythingBudgeted && promisedTo.compareTo(hasGot) > 0);
    }

    /**
     * What is still to come out of the balance under the budgets: what is left of each category that
     * has one, with a category already over its allowance contributing nothing rather than a
     * negative figure.
     */
    private static BigDecimal whatTheBudgetsStillClaim(WhatAnAccountSpentInAMonth spending) {
        return spending.categories().stream()
                .map(WhatACategoryCostInAMonth::left)
                .filter(Objects::nonNull)
                .map(left -> left.signum() > 0 ? left : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * What a month is promised to and what it has: the two figures the warning carries, the part of
     * the first that the budgets account for, whether the account has a plan at all, and the
     * comparison itself.
     *
     * <p>The parts are kept beside the answer rather than thrown away, because the DEBUG line behind
     * the transition check is what a reviewer redoes the arithmetic from, and a line saying only
     * that a month was over-committed would be a line nobody could check.
     */
    record ThePromise(BigDecimal promisedTo, BigDecimal hasGot, BigDecimal budgetsStillClaim,
                      boolean anythingBudgeted, boolean isOverCommitted) {
    }
}
