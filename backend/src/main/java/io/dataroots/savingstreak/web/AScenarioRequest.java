package io.dataroots.savingstreak.web;

import java.util.List;

/**
 * One branch as a customer types it: what they call it, and the changes it is made of.
 *
 * <p>The name is theirs and travels back on the answer unchanged, because four columns on a screen
 * are four arguments and somebody choosing between them is choosing between their own words. The
 * application names only the branch nobody typed.
 *
 * <p>The changes are a list because they compose: five hundred out in March and twenty-five more a
 * week from April is one question about one future. They are folded in the order they arrive, and
 * nothing here decides that one of them cancels another.
 */
record AScenarioRequest(String called, List<AnAdjustmentRequest> adjustments) {
}
