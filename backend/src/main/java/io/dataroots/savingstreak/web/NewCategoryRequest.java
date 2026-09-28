package io.dataroots.savingstreak.web;

/**
 * What a customer sends to name one more thing their money goes on: the word, for the account in
 * the path.
 *
 * <p>One field, because a category is a name and nothing else. What it is allowed to cost is a
 * budget, sent to the category's own budget path once it exists, and a form that asked for both at
 * once would make naming your spending impossible without also deciding what it may cost — which is
 * a decision worth making separately and changing separately.
 *
 * <p>There is nothing else in it. Which account the money leaves is the path, when it was declared
 * is the application's clock, and whether the word is one this application will keep is the Budgets
 * module's answer rather than this record's.
 */
record NewCategoryRequest(String name) {
}
