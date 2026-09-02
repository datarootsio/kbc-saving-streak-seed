package io.dataroots.savingstreak.clock;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
 */
@Configuration
class ClockConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ClockConfiguration.class);

    /**
     * The system's clock, which is the right answer everywhere the application actually runs.
     *
     * <p>Replaceable is the point of it. Twelve-month rules — points expiring, a loyalty bonus
     * vesting — cannot be reached inside a training day against a clock that only moves at the speed
     * of the day, and a profile that hands out a movable one is how a trainer reaches them.
     */
    @Bean
    Clock applicationClock() {
        Clock clock = Clock.systemUTC();
        // Which clock the application came up on and what it reads at startup. A later slice hands
        // out a movable one, and the first question anybody reading a wound-forward log asks is
        // which of the two is in there.
        log.info("application clock configured clock={} reads={}", clock, clock.instant());
        return clock;
    }
}
