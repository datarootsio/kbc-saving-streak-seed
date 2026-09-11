package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.jobs.AScheduledJob;

/**
 * A scheduled job as the development API lists it: the name to run it by, what defines it, and when
 * it would otherwise have run.
 */
record ScheduledJobResponse(String name, String definedBy, String schedule) {

    static ScheduledJobResponse of(AScheduledJob job) {
        return new ScheduledJobResponse(job.name(), job.definedBy(), job.schedule());
    }
}
