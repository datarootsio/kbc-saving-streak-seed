package io.dataroots.savingstreak.jobs;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Switches scheduling on for the whole application, so that a method annotated
 * {@link org.springframework.scheduling.annotation.Scheduled @Scheduled} anywhere in it actually
 * runs.
 *
 * <p>Spring does not schedule anything unless something asks it to, and the failure is silent: a
 * participant who writes a correct job against an application without this class watches nothing
 * happen and has no error to read.
 *
 * <p>This class was written before there was anything to schedule, for jobs that did not exist yet.
 * There is one now — the nightly sweep that retires points twelve months after they were earned —
 * and the rest are still exercises, so the reason to keep this switch on has not changed: the
 * machinery is the seed's job and the next job is the participant's.
 *
 * <p>In every profile, unlike the controller that runs a job on demand. A job is part of the
 * application wherever it runs; only the ability to run one out of turn is a lab affordance.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "saving-streak.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingIsOn {
}
