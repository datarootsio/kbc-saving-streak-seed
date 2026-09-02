package io.dataroots.savingstreak.jobs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Switches scheduling on for the whole application, so that a method annotated
 * {@link org.springframework.scheduling.annotation.Scheduled @Scheduled} anywhere in it actually
 * runs.
 *
 * <p>Spring does not schedule anything unless something asks it to, and the failure is silent: a
 * participant who writes a correct expiry job against an application without this class watches
 * nothing happen and has no error to read. This application ships no jobs of its own — points
 * expiry and the loyalty bonus are exercises — so this class exists for jobs that do not exist yet,
 * which is the point: the machinery is the seed's job and the job is the participant's.
 *
 * <p>In every profile, unlike the controller that runs a job on demand. A job is part of the
 * application wherever it runs; only the ability to run one out of turn is a lab affordance.
 */
@Configuration
@EnableScheduling
class SchedulingIsOn {
}
