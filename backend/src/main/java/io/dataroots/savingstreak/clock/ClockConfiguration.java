package io.dataroots.savingstreak.clock;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The clock the application reads the current moment from.
 *
 * <p>One clock for the whole application, supplied rather than asked for: a module that called
 * {@code Instant.now()} would be reading the machine's clock instead of this one, and a demonstration
 * that wound time forward would move some of the application and not the rest. Everything that dates
 * a record takes this bean.
 *
 * <p>It reads UTC because every moment this application keeps is an {@link java.time.Instant}, which
 * has no zone in it. Where those moments are shown to somebody is a question for whoever shows them.
 *
 * <p>Which clock that is depends on the profile, and only one of the two is ever built. Anywhere the
 * application actually runs it is the system's, which is not movable by anybody; in development it is
 * a {@link MovableClock}, and a trainer can wind it forward. The alternative — one clock that is
 * movable everywhere and merely not exposed outside development — would ship the time machine and
 * trust the wiring to keep it out of reach.
 */
@Configuration
class ClockConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ClockConfiguration.class);

    /** The system's clock, which is the right answer everywhere the application actually runs. */
    @Bean
    @Profile("!dev")
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    /**
     * The clock a trainer can move, in the development profile alone.
     *
     * <p>Declared as a {@link MovableClock} rather than as a {@link Clock} so that the one class
     * allowed to move it can be handed the clock itself. Everything else asks for a {@code Clock} and
     * gets this, knowing only how to read it.
     *
     * <p>Twelve-month rules — points expiring, a loyalty bonus vesting — cannot be reached inside a
     * training day against a clock that only moves at the speed of the day, and this is how a trainer
     * reaches them.
     */
    @Bean
    @Profile("dev")
    MovableClock movableApplicationClock() {
        return new MovableClock(Clock.systemUTC());
    }

    /**
     * Says which clock the application came up on and what it reads, once everything has been wired.
     *
     * <p>The clock is injected rather than the one built above being logged, which is the difference
     * between reporting what the application is using and reporting what this class offered it: a
     * test that supplies its own clock overrides the bean, and a start-up line naming the one that
     * was overridden would be the first thing to mislead whoever is reading a wound-forward log.
     */
    @Bean
    SmartInitializingSingleton theClockInUse(Clock clock) {
        return () -> log.info("application clock in use clock={} reads={}", clock, clock.instant());
    }
}
