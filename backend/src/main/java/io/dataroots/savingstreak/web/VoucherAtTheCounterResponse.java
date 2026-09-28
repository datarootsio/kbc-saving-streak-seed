package io.dataroots.savingstreak.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.rewards.AVoucherAtTheCounter;
import io.dataroots.savingstreak.rewards.VoucherState;
import io.dataroots.savingstreak.rewards.WhatABundleContains;

/**
 * A voucher as the counter screen reads it: what it is for, who it belongs to, whether to hand the
 * thing over, and — once somebody has — when it went and which counter took it.
 *
 * <p>{@code good} is sent even though {@code state} is, and it is not a second fact. It is the
 * module's own {@link VoucherState#isGoodAtACounter()} answered once, here, so that the screen
 * standing in front of a queue never has to know which states are the terminal ones — the page
 * draws a green panel or a red one from a boolean, and a state added by a later slice changes what
 * the boolean says without changing a line of the frontend. A page that decided for itself from
 * the state would be the one place in this application that holds a list of them.
 *
 * <p>The day it runs out is sent whether it has or not, and null when the offer gave it no shelf
 * life. A till that could only see the date once the voucher was dead would have nothing to say
 * to somebody asking how long they have left, which is a question people ask at counters.
 *
 * <p>The reason a voucher was cancelled is sent with the day it happened, and both are null for
 * anything else. {@code good} already says the voucher is no good and {@code state} already says
 * which of the three it is; what the reason adds is the only thing the person at the till can
 * actually say to the customer in front of them, because a cancellation is the one refusal here
 * that nothing the customer did caused. The same words go out on the customer's own list, so the
 * two of them are reading one sentence rather than two.
 *
 * <p>The holder's name is put beside the voucher here rather than carried out of the Rewards
 * module, because who a customer is belongs to Accounts. It is the shape {@code CustomerController}
 * already uses when it asks Accounts which accounts somebody holds and hangs a balance off each.
 * Null when the customer behind the claim is no longer there, which is honest: the voucher is
 * still a voucher, and a counter can still hand it over.
 *
 * <p><strong>{@code contents} is what a bundle's voucher is a voucher for, and it is assembled
 * the same way the name above is.</strong> One voucher covers the whole hamper — that is the
 * decision the spec argues for against a voucher per line — so the one thing the till cannot do
 * without is a list of what to put on the counter. It is not on the claim and is not snapshotted
 * onto one: what a claim keeps is its code, its title and what it cost, and the contents are
 * read off the catalogue by the module, which is safe precisely because a bundle's contents are
 * fixed when it is composed. Empty for every voucher that is not a bundle's, which is every
 * voucher this application has ever issued.
 */
record VoucherAtTheCounterResponse(String voucherCode, String code, String title, long pointsSpent,
                                   long customerId, String customerName, Instant claimedAt,
                                   VoucherState state, boolean good, Instant usedAt,
                                   String usedByCounter, LocalDate expiresOn, Instant cancelledAt,
                                   String cancelledBecause,
                                   List<BundleMemberResponse> contents) {

    static VoucherAtTheCounterResponse of(AVoucherAtTheCounter voucher, String customerName,
                                          List<WhatABundleContains> contents) {
        return new VoucherAtTheCounterResponse(
                voucher.voucherCode(),
                voucher.rewardCode(),
                voucher.title(),
                voucher.pointsSpent(),
                voucher.customerId(),
                customerName,
                voucher.claimedAt(),
                voucher.state(),
                voucher.state().isGoodAtACounter(),
                voucher.usedAt(),
                voucher.usedByCounter(),
                voucher.expiresOn(),
                voucher.cancelledAt(),
                voucher.cancelledBecause(),
                contents.stream().map(BundleMemberResponse::of).toList());
    }
}
