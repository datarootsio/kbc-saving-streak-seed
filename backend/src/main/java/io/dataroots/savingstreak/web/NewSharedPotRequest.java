package io.dataroots.savingstreak.web;

/**
 * What a customer sends to open a shared pot: what to call it, and who is opening it.
 *
 * <p>The name arrives as the text that was typed rather than trimmed or corrected on the way in, for
 * the reason a goal's target does: what is wrong with a name of nothing but spaces is a rule about
 * pots, and it deserves an answer about the pot rather than about a request that could not be read.
 *
 * <p><strong>The customer travels in the body, and the application trusts it.</strong> There is no
 * authentication in this application — signing in is recognising an address, and every endpoint
 * already trusts the customer identifier it is handed, {@code POST /api/customers/{id}/gifts} in the
 * path and this one in the body. A pot has no customer in its path to be opened under, so the
 * acting customer has to arrive somewhere, and the body is where every other call in this feature
 * will carry it. Roles on a pot are real rules about a claimed identity and not a security boundary,
 * and saying so plainly is better than a shape that pretends otherwise.
 */
record NewSharedPotRequest(String name, Long customerId) {
}
