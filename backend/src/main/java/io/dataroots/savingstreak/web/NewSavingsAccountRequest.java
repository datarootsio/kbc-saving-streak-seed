package io.dataroots.savingstreak.web;

/**
 * What goes up to open another savings account: the product the customer chose, and nothing else.
 *
 * <p><strong>One field, because one thing is being decided.</strong> There is no name to give an
 * account, no opening balance to put in it and no customer in the body — the customer is in the
 * path, an account is known by its number here as every account in this application is, and money
 * that appeared in a new account would be money nobody saved. What a saver decides at this moment
 * is which agreement to live under, and that is the whole of this request.
 *
 * <p><strong>Text, and judged by the backend.</strong> It arrives as whatever was typed or pressed
 * and is not read into an enum on the way in: whether the bank sells such a product, and whether it
 * is still opening accounts on one, are the catalogue's answers, and a controller that turned this
 * into a known code before asking would be refusing in the framework's words instead of the
 * domain's. An empty box stays an empty box for the same reason — "choose a product" is a sentence
 * somebody can act on, and a request that could not be read is not.
 */
record NewSavingsAccountRequest(String product) {
}
