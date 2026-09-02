package io.dataroots.savingstreak.jobs;

/**
 * A scheduled job the application has in it, as somebody deciding which one to run sees it: what to
 * call it, what defines it, and when it would have run of its own accord.
 *
 * <p>The schedule is carried as the sentence it was written as rather than as a next-run moment,
 * because the question this answers is "is this the job I mean?" and a cron expression is how its
 * author wrote it down.
 */
public record AScheduledJob(String name, String definedBy, String schedule) {
}
