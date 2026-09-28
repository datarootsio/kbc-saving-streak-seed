package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.products.TheTermOnAnAccount;

/**
 * Everything one savings account's fixed term says at once: how long it was locked for, the day it
 * matures, how long is left, and what breaking it now would cost.
 *
 * <p><strong>One reading rather than four, because they are one answer read against one
 * clock.</strong> How long is left and whether the day has come are two readings of the same
 * subtraction, and what breaking costs is a fraction of a balance that moves — a page that fetched
 * them separately could be told three things that were true a second apart, and the price beside
 * the button would not be the price the button charges. The same argument the notice reading makes
 * about its two totals.
 *
 * <p><strong>{@code termMonths} is what tells a page whether to draw the panel at all.</strong>
 * Nought is free savings, the core saver and the notice account: no lock, no maturity date, nothing
 * to break. Saying it as a figure rather than as an absence is what lets one component render every
 * savings account without first asking what kind it is — and it is why this endpoint answers for
 * every account rather than refusing the ones with no term.
 *
 * <p><strong>{@code whatBreakingWouldCost} is quoted before anything is confirmed and is the figure
 * that will actually be charged.</strong> The backend works both out from one balance with one
 * function, so a screen can print this beside the button in the knowledge that pressing it takes
 * exactly that. A page that assembled a price of its own out of {@code earlyExitPenaltyDays} and a
 * rate would be a second opinion about a charge, and the second opinion is the one that is wrong.
 *
 * <p><strong>{@code locked} is the backend's own reading of the pair above it</strong>, so that a
 * screen greying a withdrawal box and the withdrawal gate itself agree by construction rather than
 * by two subtractions that happen to match. It is also the one field that changes on the morning of
 * maturity without anybody pressing anything, which is exactly the sort of thing a page should not
 * be working out for itself against a clock it cannot see.
 *
 * <p><strong>{@code whatHappensAtMaturity} is the ending in the words the terms use, and the
 * backend writes the sentence.</strong> What happens at the end is part of what was agreed at the
 * beginning, so it comes off the version the account is living under and not off what the product
 * is selling today — which is exactly the pair a page could not be trusted to tell apart. The name
 * travels beside the sentence for a screen that wants to branch on the ending rather than print it;
 * both are null on an account with no term, like the maturity date above them.
 *
 * <p>The maturity date travels as a plain date rather than as a moment, for the reason every other
 * date on these responses gives: which calendar day a moment falls on depends on the zone it is read
 * in, and the backend has already read it in the one zone this application counts its calendars in.
 */
record TheTermOnAnAccountResponse(long savingsAccountId, int termMonths, LocalDate maturesOn,
                                  boolean matured, boolean locked, long daysLeft,
                                  BigDecimal balance, int earlyExitPenaltyDays,
                                  BigDecimal whatBreakingWouldCost, String maturityAction,
                                  String whatHappensAtMaturity) {

    static TheTermOnAnAccountResponse of(TheTermOnAnAccount term) {
        return new TheTermOnAnAccountResponse(
                term.savingsAccountId(),
                term.termMonths(),
                term.maturesOn(),
                term.matured(),
                term.locked(),
                term.daysLeft(),
                term.balance(),
                term.earlyExitPenaltyDays(),
                term.whatBreakingWouldCost(),
                // By name, and null on an account with no term. The enum is spelled out here
                // rather than serialised as itself for the reason every other word this API sends
                // is: what crosses the boundary is a string a page may compare, and a Java enum
                // that acquired a value would otherwise change the wire format by accident.
                term.maturityAction() == null ? null : term.maturityAction().name(),
                term.whatHappensAtMaturity());
    }
}
