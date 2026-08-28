package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.accounts.CurrentAccount;

/**
 * A customer's accounts, kept in two lists because the two kinds are not interchangeable: a current
 * account is where a deposit takes its money from, a savings account is where it goes and the only
 * one of the two that earns anything.
 *
 * <p>Both carry what they are worth, so that the first thing a customer sees after signing in is
 * everything they hold and what is in it. Neither figure is worked out here — a current account
 * keeps its balance and a savings account's is derived from its deposits — and this record only
 * carries them.
 */
record CustomerAccountsResponse(List<CurrentAccountResponse> currentAccounts,
                                List<SavingsAccountResponse> savingsAccounts) {

    /** An everyday account, by the IBAN the customer knows it by and what is left in it. */
    record CurrentAccountResponse(Long id, String iban, BigDecimal balance) {

        static CurrentAccountResponse of(CurrentAccount account) {
            return new CurrentAccountResponse(account.getId(), account.getIban(), account.getBalance());
        }
    }

    /**
     * A savings account in an overview: what it holds and what that has earned, the same two figures
     * the account's own endpoint reports and read the same way. There is no factory for it, because
     * the two balances come from two other modules and are assembled by whoever asked.
     */
    record SavingsAccountResponse(Long id, BigDecimal moneyBalance, long pointsBalance) {
    }
}
