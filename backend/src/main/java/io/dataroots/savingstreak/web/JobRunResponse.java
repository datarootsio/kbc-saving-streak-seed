package io.dataroots.savingstreak.web;

import java.time.Instant;

import io.dataroots.savingstreak.jobs.AJobThatRan;

/**
 * What a job run on demand did: which job it was, the moment the application's clock read as it
 * started — which is where in time the job believed it was running — and how long it took.
 */
record JobRunResponse(String name, String definedBy, Instant ranAt, long tookMillis) {

    static JobRunResponse of(AJobThatRan ran) {
        return new JobRunResponse(ran.name(), ran.definedBy(), ran.ranAt(), ran.tookMillis());
    }
}
