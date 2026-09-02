package io.dataroots.savingstreak.support;

/**
 * A scheduled job as the development API lists it: the name to run it by, what defines it, and when
 * it would have run of its own accord. Shared by every test that asks, for the same reason as
 * {@link BalancesView} — copies of a shape drift into disagreeing about it.
 */
public record ScheduledJobView(String name, String definedBy, String schedule) {
}
