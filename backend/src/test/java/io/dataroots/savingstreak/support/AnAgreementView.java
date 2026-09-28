package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a savings account is living under, as the API reports it: the product it is on, the version
 * of that product's terms it was opened with, the day it was opened, and the condition that product
 * attaches. Shared by every test that reads one back, for the same reason as {@link BalancesView} —
 * two copies of the shape drift into disagreeing about it, and then one of them is testing a
 * contract nobody serves.
 *
 * <p>Deliberately not {@link SavingsProductView}. That record is what a product is selling
 * <em>today</em>; this one is what one account is on, and the whole feature is that the two answers
 * differ. Free savings has published a second version, so a test that read the catalogue and called
 * it "what my account pays" would be asserting the exact confusion this module exists to remove.
 *
 * <p>The kind is text rather than an enum the test imports, for the reason {@link SavingsProductView}
 * gives: a test asserting {@code "INSTANT_ACCESS"} is asserting what actually goes over the wire.
 *
 * <p>{@code maturesOn} is null for every product with no term, which is three of the four. The other
 * two conditions read as zero when the product does not attach them — no notice to give, no floor to
 * keep — which is one reading per absence and is what a page renders a dash from.
 *
 * <p>{@code closedOn} is null while the account is open, which is every account until somebody
 * empties one and closes it. A closed account keeps every other field here, because all of it is
 * still what the money in its history lived under.
 */
public record AnAgreementView(String productCode, String productName, String productKind,
                              int version, LocalDate openedOn, int noticeDays,
                              BigDecimal minimumBalance, LocalDate maturesOn, LocalDate closedOn) {
}
