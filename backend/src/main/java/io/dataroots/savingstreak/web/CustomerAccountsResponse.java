package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.accounts.CurrentAccount;
import io.dataroots.savingstreak.streaks.WeekAndStreak;

/**
 * A customer's accounts, kept in two lists because the two kinds are not interchangeable: a current
 * account is where a deposit takes its money from, a savings account is where it goes.
 *
 * <p>Each account carries the money in it, so that the first thing a customer sees after signing in
 * is everything they hold and what is in it. No figure is worked out here — a current account keeps
 * its balance and a savings account's is derived from its deposits — and this record only carries
 * them.
 *
 * <p>The points, the week and the run of weeks are the customer's own figures and sit beside the
 * lists rather than inside either of them. They are all earned by paying into any of these savings
 * accounts, and they belong to the person: repeating the same totals against every savings account
 * would say they were that account's, and the customer would appear to hold as many copies of their
 * points and as many streaks as they hold accounts.
 *
 * <p>The seven that describe the saving are the same seven, under the same names, that a savings
 * account's own endpoint reports — because they are the same figures, read for the same customer.
 * What the week asks for travels with what has landed in it, so a page showing progress towards
 * EUR 50 never writes the 50 into its own markup.
 */
record CustomerAccountsResponse(long pointsBalance,
                                BigDecimal newSavingsThisWeek,
                                BigDecimal weeklyMinimum,
                                BigDecimal stillNeededThisWeek,
                                int currentStreakWeeks,
                                int bestStreakWeeks,
                                BigDecimal currentMultiplier,
                                List<CurrentAccountResponse> currentAccounts,
                                List<SavingsAccountResponse> savingsAccounts) {

    /**
     * The customer's figures put together with their accounts. A factory rather than a constructor
     * call at the call site, because the seven figures come from three modules and unpacking one
     * module's answer into six arguments is the sort of thing that gets done differently the second
     * time.
     */
    static CustomerAccountsResponse of(long pointsBalance, WeekAndStreak saving,
                                       List<CurrentAccountResponse> currentAccounts,
                                       List<SavingsAccountResponse> savingsAccounts) {
        return new CustomerAccountsResponse(pointsBalance,
                saving.week().newSavings(), saving.week().weeklyMinimum(), saving.week().stillNeeded(),
                saving.streak().currentWeeks(), saving.streak().bestWeeks(),
                saving.streak().multiplier(),
                currentAccounts, savingsAccounts);
    }

    /** An everyday account, by the IBAN the customer knows it by and what is left in it. */
    record CurrentAccountResponse(Long id, String iban, BigDecimal balance) {

        static CurrentAccountResponse of(CurrentAccount account) {
            return new CurrentAccountResponse(account.getId(), account.getIban(), account.getBalance());
        }
    }

    /**
     * A savings account in an overview: what it holds, the same figure the account's own endpoint
     * reports and read the same way. There is no factory for it, because the balance comes from
     * another module and is assembled by whoever asked.
     */
    record SavingsAccountResponse(Long id, BigDecimal moneyBalance) {
    }
}
