package io.dataroots.savingstreak.runningajob;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Scheduled jobs that exist for these tests and nowhere else.
 *
 * <p>The application deliberately ships no jobs of its own — points expiry and the loyalty bonus are
 * exercises a participant writes — so the only honest way to test that a scheduled job runs, and that
 * it can be run out of turn, is to bring one. These stand in for the job a participant will write.
 *
 * <p>Guarded twice, and on purpose. {@link TestConfiguration} keeps them out of the component scan of
 * the contexts Spring Boot's test support builds; the profile keeps them out of the applications that
 * other tests start with a {@code SpringApplicationBuilder}, which have no such exclusion in them. An
 * application in this run that did not ask for these jobs must not find one ticking in it.
 */
@TestConfiguration
@Profile(JobsThisTestDefines.PROFILE)
public class JobsThisTestDefines {

    /** Named on the command line by the tests that want these jobs, and by nothing else. */
    static final String PROFILE = "jobs-under-test";

    @Bean
    AJobThatRunsOnItsOwn aJobThatRunsOnItsOwn() {
        return new AJobThatRunsOnItsOwn();
    }

    @Bean
    AJobThatWaitsForTheNewYear aJobThatWaitsForTheNewYear() {
        return new AJobThatWaitsForTheNewYear();
    }

    @Bean
    AJobThatBreaks aJobThatBreaks() {
        return new AJobThatBreaks();
    }

    /**
     * Runs often and without being asked, which is how a test can tell that scheduling is switched on
     * at all rather than that a job can be invoked by name.
     */
    static class AJobThatRunsOnItsOwn {

        private final AtomicInteger runs = new AtomicInteger();

        @Scheduled(fixedRate = 200)
        void tick() {
            runs.incrementAndGet();
        }

        int runs() {
            return runs.get();
        }
    }

    /**
     * Runs at three in the morning on the first of January, which is to say never during a test — the
     * shape of an expiry job a participant would otherwise have to wait a year to watch, and so the
     * one worth running on demand.
     */
    static class AJobThatWaitsForTheNewYear {

        private final AtomicInteger runs = new AtomicInteger();
        private final AtomicReference<Instant> lastRunObservedAt = new AtomicReference<>();

        @Scheduled(cron = "0 0 3 1 1 *")
        void sweepUpTheOldPoints() {
            runs.incrementAndGet();
            lastRunObservedAt.set(Instant.now());
        }

        int runs() {
            return runs.get();
        }

        Instant lastRunObservedAt() {
            return lastRunObservedAt.get();
        }
    }

    /** A job that fails the way a half-written one does, so that the answer to that can be asserted. */
    static class AJobThatBreaks {

        @Scheduled(cron = "0 0 4 1 1 *")
        void fallOver() {
            throw new IllegalStateException("this job was written to fall over");
        }
    }
}
