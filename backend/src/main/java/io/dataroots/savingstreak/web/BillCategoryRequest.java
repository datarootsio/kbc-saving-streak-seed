package io.dataroots.savingstreak.web;

/**
 * What a customer sends to put one of their standing bills in a category: the category, for the bill
 * in the path.
 *
 * <p>One field, because a bill is never split. A bill is one declared payment to one provider for an
 * amount its declaration fixes, so it is in one category or in none; a customer who wants their rent
 * divided between Housing and Utilities declares two bills, which is both simpler and truer to what
 * a bill is. A spend is the thing that carries a list of parts, because a supermarket trip really
 * was half food and half wine.
 *
 * <p>An identifier rather than a name, for the reason the goals' order is sent as identifiers: a
 * name is what the customer reads and it can be changed the moment after they read it, and a request
 * naming a word would have to guess which of two categories once called Groceries was meant.
 *
 * <p>Taking a bill out of every category is a DELETE of the same address rather than this request
 * with nothing in it. A body that names no category is a request that says nothing at all, and it is
 * refused in those words.
 */
record BillCategoryRequest(Long categoryId) {
}
