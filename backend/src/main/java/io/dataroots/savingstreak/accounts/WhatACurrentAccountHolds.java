package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;

/**
 * One current account as its holder finds it: which account, the IBAN they know it by, what is in
 * it, and who holds it.
 *
 * <p>For the screen that belongs to the account itself. Until now a current account was only ever
 * read as one row of a customer's directory, which answers "what do I hold" and cannot answer "what
 * is happening on this one" — and a page about a single account that had to fetch the whole
 * directory and pick a row out of it would be reading four accounts to draw one.
 *
 * <p>The holder travels with it for the reason {@link AccountHolder} gives: a page names whoever
 * holds the account on screen, and asking who that is would be a second query for one fact. It is
 * carried as the two fields rather than as an {@code AccountHolder}, because this record is built by
 * a constructor expression in a query and a projection that nested another record inside itself
 * would be a shape chosen for the reader over the thing reading it.
 *
 * <p>The income is deliberately not in it. What lands every month is a declaration with its own
 * endpoint, its own refusals and its own moment, and {@link DeclaredIncome} already says it; a read
 * model that folded the two together would give the same declaration two shapes.
 *
 * <p>A record rather than the entity, like everything that leaves this module.
 */
public record WhatACurrentAccountHolds(Long currentAccountId, String iban, BigDecimal balance,
                                       Long customerId, String customerName) {
}
