package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.accounts.ADeclaredBill;
import io.dataroots.savingstreak.accounts.AnArrear;
import io.dataroots.savingstreak.accounts.DeclaredIncome;
import io.dataroots.savingstreak.accounts.TheMonthAhead;
import io.dataroots.savingstreak.accounts.WhatACurrentAccountHolds;

/**
 * One current account, on its own: what is in it, and what its holder says lands in it every month.
 *
 * <p>The answer a screen of its own needs. The customer's directory of accounts carries a balance
 * too, and the two are the same figure read the same way — but the directory answers "what do I
 * hold" for a whole customer, and a page about one account that read it would be fetching every
 * account somebody has in order to draw one of them. This is the current account's own resource, the
 * way {@code SavingsAccountResponse} is a savings account's.
 *
 * <p>The income is nested rather than flattened into fields beside the balance, and it is the same
 * shape the income's own endpoint answers with. A page reads it here when it draws the account and
 * gets it back in that shape after declaring one, so the two answers cannot describe the
 * declaration differently — and {@code declared} keeps saying the thing that matters before anything
 * has been said: nobody has named a figure, which is not the same as somebody naming nought.
 *
 * <p><strong>The bills come down with it, and only the standing ones.</strong> What goes out every
 * month is read in the same breath as what is in the account and what arrives, because that is the
 * one question the page exists to answer — and a page that had to make three requests to draw one
 * card would show the balance a moment before it could show what is claimed against it. They are
 * nested in the same shape the bills' own endpoint answers with, so the list a page draws on arrival
 * and the bill it gets back after declaring one cannot describe a bill differently. Ended bills are
 * deliberately not here: they are a record rather than a claim on the balance, and they are read
 * from their own path by whoever wants them.
 *
 * <p><strong>And what is still owed, in the same breath.</strong> An arrear is a claim on this
 * balance exactly as a standing bill is — the difference is that it is already overdue — so it comes
 * down with the account for the same reason the bills do: the page exists to show what arrives, what
 * goes out and what is owed in one place, and three requests to draw one screen would show the
 * balance a moment before showing what is against it. Oldest first, which is also the order the
 * nightly run settles them in.
 *
 * <p>An empty list when nothing is owed, and the page draws no section at all for it. That is
 * deliberate and it is the difference between a panel somebody reads and a panel somebody learns to
 * skip: this one is absent rather than empty, so its appearing means something.
 *
 * <p><strong>And what the month ahead has to cover, which is what the other three are for.</strong>
 * The balance, the income and the bills are the inputs; {@code monthAhead} is the sentence they add
 * up to — what is due in, what is due out and what that leaves — and it comes down in the same
 * breath for the same reason they all do. It is derived on every read and stored nowhere, so a bill
 * declared a moment ago is already in it.
 *
 * <p>The name is shared with {@link CustomerAccountsResponse.CurrentAccountResponse}, exactly as a
 * savings account's own resource shares its name with the entry in that directory, and for the same
 * reason: both describe the same account, one on its own page and one in a list, and the list's
 * entry is the smaller of the two.
 */
record CurrentAccountResponse(long currentAccountId, String iban, String customerName,
                              BigDecimal balance, MonthlyIncomeResponse income,
                              List<RecurringBillResponse> bills,
                              List<ArrearResponse> arrears, MonthAheadResponse monthAhead) {

    static CurrentAccountResponse of(WhatACurrentAccountHolds account, DeclaredIncome income,
                                     List<ADeclaredBill> bills, List<AnArrear> arrears,
                                     TheMonthAhead monthAhead) {
        return new CurrentAccountResponse(
                account.currentAccountId(),
                account.iban(),
                account.customerName(),
                account.balance(),
                MonthlyIncomeResponse.of(income),
                bills.stream().map(RecurringBillResponse::of).toList(),
                arrears.stream().map(ArrearResponse::of).toList(),
                MonthAheadResponse.of(monthAhead));
    }
}
