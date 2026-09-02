package io.dataroots.savingstreak.jobs;

import java.time.Instant;

/**
 * What happened when a job was run on demand: which job it was, the moment the application's clock
 * read as it started, and how long it took.
 *
 * <p>The moment comes off the application's clock rather than the machine's, so that a participant
 * who wound the clock a year forward and then ran their expiry job is told the year the job thought
 * it was running in — which is the figure the job's own decisions were made against.
 */
public record AJobThatRan(String name, String definedBy, Instant ranAt, long tookMillis) {
}
