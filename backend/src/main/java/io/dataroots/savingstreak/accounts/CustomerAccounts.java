package io.dataroots.savingstreak.accounts;

import java.util.List;

/**
 * Everything one customer holds, in the two kinds the application distinguishes: the current
 * accounts money can come from, and the savings accounts it can go to. A deposit is a movement
 * from the first kind to the second, which is why they are never one list.
 */
public record CustomerAccounts(List<CurrentAccount> currentAccounts, List<SavingsAccount> savingsAccounts) {
}
