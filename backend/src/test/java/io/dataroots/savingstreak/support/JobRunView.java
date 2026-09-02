package io.dataroots.savingstreak.support;

import java.time.Instant;

/**
 * What the development API reports about a job it ran on demand: which job, the moment the
 * application's clock read as it started, and how long it took.
 */
public record JobRunView(String name, String definedBy, Instant ranAt, long tookMillis) {
}
