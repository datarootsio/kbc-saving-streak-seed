package io.dataroots.savingstreak.weeklysavings;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A week of saving runs from Monday to Sunday as a customer in Brussels counts it, and the figure a
 * savings account reports is derived from the ledger every time it is read.
 *
 * <p>Asserted against a clock this test stands wherever it likes, which is the only way to reach
 * the two cases that matter: a deposit at 23:30 on a Sunday and one at 00:30 on the Monday after it
 * are 23:30 and 22:30 the same evening in UTC, so an application counting weeks off its own clock's
 * reading would file the second one against the week that had just ended. Standing the clock
 * somewhere is also the only way to ask what happens when it moves backwards, which the development
 * clock refuses to do.
 *
 * <p>Its own application and its own database, like the fixed-clock test: everything here is dated
 * years away from what the rest of the run puts in the shared file. Every test works in a week of
 * its own, so none of them can see another's deposits and none of them depends on the order it ran
 * in.
 */
class WeeksRunMondayToSundayInBrusselsApiTest extends ApiIntegrationTest {

    /** The zone a week is counted in, as this test would have to name it to check the answer. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    /**
     * The last hours of a Sunday and the first of the Monday after it. Late enough and early enough
     * that in UTC they are the same evening, which is what makes them worth asserting.
     */
    private static final LocalTime LATE_ON_SUNDAY = LocalTime.of(23, 30);
    private static final LocalTime EARLY_ON_MONDAY = LocalTime.of(0, 30);

    /**
     * Where the clock this test's application reads is standing. Shared with the bean below and
     * moved by the tests, forwards and backwards, which is what a derivation with nothing stored is
     * supposed to survive.
     */
    private static final AtomicReference<Instant> theClockReads =
            new AtomicReference<>(Instant.parse("2026-01-07T12:00:00Z"));

    private static ConfigurableApplicationContext application;

    /** Bound to the application above rather than to the shared one this class inherits. */
    private static TestRestTemplate clockedHttp;

    private static SeededAccounts seeded;

    @BeforeAll
    static void startAnApplicationWhoseClockThisTestMoves() {
        // Command-line arguments rather than default properties, for the reason the walking skeleton
        // gives: defaults lose to application.properties, which would point this instance at the
        // real database.
        application = new SpringApplicationBuilder(SavingStreakApplication.class, AClockThisTestMoves.class)
                .run("--spring.datasource.url=jdbc:sqlite:"
                                + aDatabaseFileThatDoesNotExistYet("saving-streak-weeks"),
                        "--spring.profiles.active=dev",
                        "--server.port=0");
        clockedHttp = new TestRestTemplate();
        clockedHttp.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
        seeded = new SeededAccounts(clockedHttp);
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.close();
        }
    }

    /**
     * The plain case, in a week no clock change falls in: the last half hour of the Sunday belongs
     * to the week that is ending, and the first half hour of the Monday to the week beginning.
     */
    @Test
    void a_sunday_late_deposit_counts_for_the_week_ending_and_a_monday_early_one_for_the_week_starting() {
        assertTheSundayAndTheMondayFallInTheirOwnWeeks(LocalDate.parse("2026-03-01"));
    }

    /**
     * The same across the spring clock change. Brussels goes from one hour ahead of UTC to two on
     * the last Sunday in March, so the week ends at 22:00 UTC on the Sunday rather than at 23:00 —
     * a boundary worked out by adding seven days to the week's start would be an hour out, and the
     * Monday-early deposit would land in the week that had ended.
     */
    @Test
    void the_boundary_holds_across_the_spring_clock_change() {
        assertTheSundayAndTheMondayFallInTheirOwnWeeks(LocalDate.parse("2026-03-29"));
    }

    /** And across the autumn one, where the offset goes back from two hours to one. */
    @Test
    void the_boundary_holds_across_the_autumn_clock_change() {
        assertTheSundayAndTheMondayFallInTheirOwnWeeks(LocalDate.parse("2026-10-25"));
    }

    /**
     * Time moving on is enough to start a new week: nothing is deposited, no job is run, and the
     * figure is back to nothing because the week being asked about is a different one.
     */
    @Test
    void a_week_further_on_reports_nothing_landed_without_a_deposit_or_a_job() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        theClockReads.set(brussels(LocalDate.parse("2026-05-06"), LocalTime.NOON));
        deposit(savingsAccount, "40.00");
        assertThat(newSavingsThisWeekIn(savingsAccount)).isEqualByComparingTo("40.00");
        BigDecimal moneySaved = balancesOf(savingsAccount).moneyBalance();
        long pointsToSpend = balancesOf(savingsAccount).pointsBalance();

        theClockReads.set(brussels(LocalDate.parse("2026-05-13"), LocalTime.NOON));

        BalancesView aWeekOn = balancesOf(savingsAccount);
        assertThat(aWeekOn.newSavingsThisWeek()).isEqualByComparingTo("0.00");
        assertThat(aWeekOn.stillNeededThisWeek()).isEqualByComparingTo("50.00");
        // The money and the points are where they were: a new week is a new week's saving to do,
        // not anything taken away.
        assertThat(aWeekOn.moneyBalance()).isEqualByComparingTo(moneySaved);
        assertThat(aWeekOn.pointsBalance()).isEqualTo(pointsToSpend);
    }

    /**
     * The clock moving backwards puts the earlier week's figure back, which is the property that
     * matters: there is no weekly total written down anywhere that could be describing a week the
     * application has since left.
     */
    @Test
    void the_clock_moved_back_reports_the_earlier_weeks_figure_again() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        Instant inTheEarlierWeek = brussels(LocalDate.parse("2026-06-10"), LocalTime.NOON);
        theClockReads.set(inTheEarlierWeek);
        deposit(savingsAccount, "35.00");
        theClockReads.set(brussels(LocalDate.parse("2026-06-17"), LocalTime.NOON));
        assertThat(newSavingsThisWeekIn(savingsAccount)).isEqualByComparingTo("0.00");

        theClockReads.set(inTheEarlierWeek);

        assertThat(newSavingsThisWeekIn(savingsAccount)).isEqualByComparingTo("35.00");
    }

    /**
     * Deposits half an hour either side of a Monday midnight in Brussels, and each week reports only
     * its own.
     *
     * @param sunday the Sunday the week ends on
     */
    private void assertTheSundayAndTheMondayFallInTheirOwnWeeks(LocalDate sunday) {
        long savingsAccount = seeded.savingsAccountOf(ANKE);

        theClockReads.set(brussels(sunday, LATE_ON_SUNDAY));
        DepositView beforeMidnight = deposit(savingsAccount, "20.00");
        assertThat(newSavingsThisWeekIn(savingsAccount))
                .as("the week ending on " + sunday + ", read at 23:30 on that Sunday")
                .isEqualByComparingTo("20.00");

        theClockReads.set(brussels(sunday.plusDays(1), EARLY_ON_MONDAY));
        DepositView afterMidnight = deposit(savingsAccount, "30.00");
        assertThat(newSavingsThisWeekIn(savingsAccount))
                .as("the week starting on " + sunday.plusDays(1) + ", read at 00:30 on that Monday")
                .isEqualByComparingTo("30.00");

        // The two moments really are the same evening in UTC, which is the reading a week counted
        // off the clock's own zone would have gone by. Without this the test could pass against an
        // application that had simply put each deposit in the UTC week it landed in.
        assertThat(beforeMidnight.depositedAt().atZone(ZoneOffset.UTC).toLocalDate())
                .isEqualTo(afterMidnight.depositedAt().atZone(ZoneOffset.UTC).toLocalDate());

        // And the week that is ending still reports only its own deposit, read back after the
        // Monday one landed: the derivation is a question about a week, not a running total.
        theClockReads.set(brussels(sunday, LATE_ON_SUNDAY));
        assertThat(newSavingsThisWeekIn(savingsAccount))
                .as("the week ending on " + sunday + ", read again once the Monday deposit had landed")
                .isEqualByComparingTo("20.00");
    }

    /** A moment as a customer in Brussels would read it, whatever the offset is that week. */
    private static Instant brussels(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(BRUSSELS).toInstant();
    }

    private static BigDecimal newSavingsThisWeekIn(long savingsAccountId) {
        return balancesOf(savingsAccountId).newSavingsThisWeek();
    }

    private static BalancesView balancesOf(long savingsAccountId) {
        return clockedHttp.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    private static DepositView deposit(long savingsAccountId, String amount) {
        ResponseEntity<DepositView> response = clockedHttp.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", seeded.currentAccountOf(ANKE)),
                DepositView.class,
                savingsAccountId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    /**
     * The clock this test's application is given, standing wherever the test last put it.
     *
     * <p>It carries no stereotype annotation and is handed to the builder by name instead, for the
     * reason the fixed-clock test gives: a {@code @Configuration} here would sit in a package the
     * application scans, and every other application these tests start would come up reading it.
     */
    static class AClockThisTestMoves {

        @Bean
        @Primary
        Clock aClockStandingWhereTheTestPutIt() {
            return new Clock() {

                @Override
                public Instant instant() {
                    return theClockReads.get();
                }

                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                /**
                 * The same clock, because nothing in the application re-zones the one it is given —
                 * a week is worked out by converting the moment, not by asking the clock what day
                 * it is. A copy that ignored the zone silently would be worse than one that says so.
                 */
                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }
            };
        }
    }
}
