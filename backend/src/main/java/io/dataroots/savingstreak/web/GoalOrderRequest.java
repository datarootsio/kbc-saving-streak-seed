package io.dataroots.savingstreak.web;

import java.util.List;

/**
 * The whole order of importance, in one request: the account's live goals by identifier, most
 * important first.
 *
 * <p>All of them, every time. A strict total order has no valid intermediate state, so there is no
 * request here that moves one goal and leaves the rest to be worked out — a list that named a subset
 * would leave a goal behind with no way to say which, and this application would have to invent a
 * rule for where it went. The list <em>is</em> the order, and the module refuses anything that is not
 * a permutation of exactly this account's live goals.
 */
record GoalOrderRequest(List<Long> goalIds) {
}
