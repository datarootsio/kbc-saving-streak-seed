package io.dataroots.savingstreak.clock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The development clock, standing on a real clock a test puts where it needs it.
 *
 * <p>The application's movable clock runs on the machine's, and two of the promises it makes cannot
 * be asked about from there: that seven days is a Brussels week even in the week the clocks change,
 * and that a reading never turns round. Both live in an hour that comes past twice a year, and a
 * test that waited for it would assert nothing on every other day. So the clock underneath is
 * replaced by one this test stands in that hour — the movable clock itself, the endpoint that moves
 * it and everything that dates a record off it are the real ones.
 *
 * <p>Handed to a {@code SpringApplicationBuilder} by name and carrying no stereotype annotation, for
 * the reason {@code TimeComesFromTheClockApiTest} gives: a {@code @Configuration} in a package the
 * application scans would quietly give every other application these tests start a clock a test had
 * moved.
 *
 * <p>Shared by the tests that need it rather than nested in one of them, because the pairing of
 * a movable clock with a real one underneath is the seam itself, and two copies of it could disagree
 * about what they were standing on.
 */
public final class TheClockTheseTestsMove {

    /**
     * The property a test starts the application with to say where real time is standing as it comes
     * up: {@code --the-clock-these-tests-move.stands-at=2026-10-25T21:30:00Z}.
     *
     * <p>Needed because putting a moved clock back happens during start-up, before a test holding
     * the context can reach in and stand the clock anywhere. A test whose question is what a restart
     * does — and the restart is where an hour can be lost — has to have real time already standing
     * where it wants it by then.
     */
    public static final String WHERE_REAL_TIME_STANDS = "the-clock-these-tests-move.stands-at";

    /**
     * The real clock the movable one is built on. Declared as a bean of its own type so that a test
     * can ask the application for it and stand it somewhere; everything inside the application asks
     * for a {@link Clock} and gets the movable one below.
     */
    @Bean
    ARealClockATestStands theRealClockUnderneath(
            @Value("${" + WHERE_REAL_TIME_STANDS + ":}") String standsAt) {
        // Where the real one is, until a test says otherwise: an application that came up standing
        // in 2027 before any test had asked it to would be a surprise in the log.
        return new ARealClockATestStands(
                standsAt.isBlank() ? Instant.now() : Instant.parse(standsAt), ZoneOffset.UTC);
    }

    /**
     * The application's development clock, over that real one instead of over the machine's. Primary
     * so that it wins over the one the application's own configuration offers, which is the same
     * class on the same zone and differs only in what it is standing on.
     */
    @Bean
    @Primary
    MovableClock theDevelopmentClockOverIt(ARealClockATestStands realClock) {
        return new MovableClock(realClock, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
    }

    /**
     * A real clock that reads what a test put in it and moves only when a test moves it. Standing in
     * for the passage of real time, which is the one thing a test cannot wait for.
     */
    public static final class ARealClockATestStands extends Clock {

        /**
         * Shared with every re-zoned copy, so that a copy reads the moment this one was last stood
         * at rather than the one it was created with — the same bargain the movable clock strikes.
         */
        private final AtomicReference<Instant> reading;

        private final ZoneId zone;

        private ARealClockATestStands(Instant reading, ZoneId zone) {
            this(new AtomicReference<>(reading), zone);
        }

        private ARealClockATestStands(AtomicReference<Instant> reading, ZoneId zone) {
            this.reading = reading;
            this.zone = zone;
        }

        /** Puts real time at a chosen moment, as though the application had been started then. */
        public void standAt(Instant moment) {
            reading.set(moment);
        }

        /** Lets that much real time pass, which is what a test cannot do by waiting. */
        public void letThisMuchTimePass(Duration passing) {
            reading.updateAndGet(was -> was.plus(passing));
        }

        @Override
        public Instant instant() {
            return reading.get();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new ARealClockATestStands(reading, zone);
        }

        @Override
        public String toString() {
            return "ARealClockATestStands[" + reading.get() + " " + zone + "]";
        }
    }
}
