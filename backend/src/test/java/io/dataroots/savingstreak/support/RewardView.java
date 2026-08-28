package io.dataroots.savingstreak.support;

/**
 * One catalogue entry as the API reports it. Shared for the same reason as {@link BalancesView}: a
 * second copy of the shape can drift into disagreeing about it, and then one of them is testing a
 * contract nobody serves.
 */
public record RewardView(String code, String title, String description, long costInPoints) {
}
