package io.dataroots.savingstreak.accounts;

/**
 * The customer who holds an account: which customer, and what to call them.
 *
 * <p>Both together rather than either alone, because whoever asks needs both and asking twice is two
 * queries for one fact. A page names the holder on screen; a module crediting or spending points
 * needs the customer those points belong to.
 *
 * <p>The customer's own record stays inside this module. This is a statement about who holds
 * something, which is all anybody outside has ever needed to be told.
 */
public record AccountHolder(Long customerId, String name) {
}
