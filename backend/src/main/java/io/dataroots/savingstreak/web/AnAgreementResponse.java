package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.products.TheAgreementAnAccountIsOn;

/**
 * What a savings account is living under, as the screen reads it: the product it is on, the version
 * of that product's terms it was opened with, the day it was opened on them, and the one condition
 * that product attaches.
 *
 * <p><strong>Not the product's card, and the difference is the entire feature.</strong> The
 * catalogue says what free savings is selling today; this says what <em>this account</em> is on,
 * and the two stopped being the same sentence the moment a second version was published. Every
 * account opened before free savings was repriced carries on under version 1 at the rate it was
 * written with, for as long as its holder wants it. A page that drew the catalogue's figures under
 * the heading "your agreement" would be stating the exact confusion this module exists to remove,
 * which is why this reading travels with the account rather than being looked up beside it.
 *
 * <p><strong>The version number is on it, in plain sight.</strong> It is not an internal detail: it
 * is the answer to "what am I actually on", it is what a later screen will diff against the version
 * on offer, and it is what every deposit in the history below it is stamped with. A reading that
 * named the product and hid the version would leave a customer unable to tell two accounts apart
 * that are paying different rates.
 *
 * <p><strong>The condition is carried as all four of its shapes, with the absences said as
 * absences.</strong> {@code noticeDays} is zero for an account with nothing to give notice of,
 * {@code minimumBalance} is {@code 0.00} for one with no floor to keep, and {@code maturesOn} is
 * null for one that never matures — one reading per rule, so a page renders "nothing to keep,
 * nothing to give notice of, no end date" from the figures rather than from a guess about the kind.
 * The maturity date is the exception that is null rather than zero because there is no date that
 * means "never", and a sentinel would be a date some screen eventually printed.
 *
 * <p><strong>And no rate, which is still deliberate now that there is one.</strong> Interest is
 * paid monthly and every payment names the rate it was paid at, on the posting itself, beside the
 * balance it was worked out from — which is where a rate means something a customer can check. A
 * figure repeated on this panel would be a second place it lived, and the day an account takes its
 * product's newer terms the two would have to be kept in step. The version is here, and the version
 * is the address of the rate.
 *
 * <p><strong>And the day it ended, which is null for an account that is still open.</strong> A
 * closed account keeps every other field on this panel, because all of it is still what the money
 * in its history lived under — and {@code closedOn} is the one field that tells a screen it is
 * reading a record rather than somewhere to put money. A date rather than a flag, so that the panel
 * can say when without a second field to read it from, and so that "closed" and "closed on the 4th
 * of March" are one fact rather than two that could disagree.
 *
 * <p>The day travels as a plain date rather than as a moment, for the reason every other date on
 * these responses gives: which calendar day a moment falls on depends on the zone it is read in,
 * and the backend has already read it in the one zone this application counts its calendars in.
 */
record AnAgreementResponse(String productCode, String productName, String productKind, int version,
                           LocalDate openedOn, int noticeDays, BigDecimal minimumBalance,
                           LocalDate maturesOn, LocalDate closedOn) {

    /**
     * The module's own reading, turned into the one the screen gets.
     *
     * <p>The kind goes over the wire as its name rather than as an object, for the reason every
     * other enum on these responses does: what a client switches on is the text, and a shape that
     * wrapped it would make renaming a value on both sides at once invisible.
     */
    static AnAgreementResponse of(TheAgreementAnAccountIsOn agreement) {
        return new AnAgreementResponse(
                agreement.productCode(),
                agreement.productName(),
                agreement.kind().name(),
                agreement.version(),
                agreement.openedOn(),
                agreement.noticeDays(),
                agreement.minimumBalance(),
                agreement.maturesOn(),
                agreement.closedOn());
    }
}
