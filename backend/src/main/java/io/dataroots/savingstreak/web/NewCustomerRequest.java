package io.dataroots.savingstreak.web;

/**
 * What is sent to open a customer: what to call them, and the address they will bank under.
 *
 * <p>Two fields and no more. What they start with in their current account, what their account
 * number is and how many savings accounts they open with are the Accounts module's decisions rather
 * than the caller's — a page that could name its own opening balance would be a page that can mint
 * money, and a page that could name its own IBAN would be one that can collide with somebody
 * else's.
 *
 * <p>Both arrive as the text that was typed, with nothing read out of them on the way in. Whether a
 * name of spaces is a name, and whether an address is one somebody already banks under, are rules
 * with sentences attached, and they are answered where the rules live.
 */
record NewCustomerRequest(String name, String contactDetails) {
}
