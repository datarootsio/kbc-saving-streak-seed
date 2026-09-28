package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A savings account as the customer's overview reports one: what it holds, and which savings
 * product it is on. Shared for the same reason as {@link BalancesView}.
 *
 * <p>The product is on the overview and not only on the account's own page, because the overview is
 * where somebody holding two accounts finds out that they are not the same kind of account. The
 * code and the name both travel: the code is what everything else is addressed by, and the name is
 * what a card is allowed to print without keeping its own table of what each code is called.
 *
 * <p>Both are null for an account nothing has recorded an agreement for, which is a database that
 * has not been through the start-up migration.
 *
 * <p>{@code closedOn} is the day the account was closed, and null while it is still open. A closed
 * account is still on the overview — the money history hanging off the same screen reads every euro
 * that moved through it, and an account that vanished would leave those euros attributed to
 * nothing — so the date is what tells the two apart.
 */
public record SavingsAccountOnTheOverviewView(Long id, BigDecimal moneyBalance, String productCode,
                                              String productName, LocalDate closedOn) {
}
