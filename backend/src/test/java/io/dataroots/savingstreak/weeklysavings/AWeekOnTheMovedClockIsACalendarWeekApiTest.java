package io.dataroots.savingstreak.weeklysavings;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Map;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.clock.TheClockTheseTestsMove;
import io.dataroots.savingstreak.clock.TheClockTheseTestsMove.ARealClockATestStands;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seven days on the development clock is the next week even in the week the clocks go back, which is
 * 169 hours long.
 *
 * <p>{@code TheWeekMovesWithTheDevelopmentClockApiTest} asserts the same reset against the machine's
 * clock, wherever the calendar happens to be standing when the suite runs — which is the ordinary
 * case and almost never the hard one. The hard one is the weekend a clock change falls in: seven
 * times 86400 seconds lands at 23:30 on the Sunday, still inside the week that was ending, so a
 * trainer presses the button for the next week and is shown the week they were already in. It comes
 * past twice a year, so it is reached here by standing the clock underneath the movable one in it
 * rather than by waiting for it.
 *
 * <p>Its own application, its own database and its own real clock — see
 * {@link TheClockTheseTestsMove}. One test, because moving the clock cannot be undone: a second
 * would find it already moved and would be asserting against whichever order the two ran in.
 */
class AWeekOnTheMovedClockIsACalendarWeekApiTest extends ApiIntegrationTest {

    /** Seven days, asked for the way a trainer asks for the next week. */
    private static final int A_WEEK = 7;

    /**
     * Half past midnight on Monday 2026-10-19 in Brussels — the start of the week the clocks go back
     * in, which they do at 03:00 on Sunday 2026-10-25.
     */
    private static final Instant THE_MONDAY_THE_WEEK_THE_CLOCKS_GO_BACK_BEGINS =
            ZonedDateTime.of(2026, 10, 19, 0, 30, 0, 0, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                    .toInstant();

    /** Where a week on lands, read where a week is counted: the same time of day, seven dates on. */
    private static final LocalDateTime THE_MONDAY_THE_NEXT_WEEK_BEGINS =
            LocalDateTime.of(2026, 10, 26, 0, 30);

    /** What that week measures, because the clocks going back give it an extra hour. */
    private static final Duration A_WEEK_WITH_AN_HOUR_GIVEN_BACK = Duration.ofHours(169);

    private static ConfigurableApplicationContext application;

    /** Bound to the application this class moves the clock on, not to the shared one. */
    private static TestRestTemplate movableHttp;

    private static ARealClockATestStands realClock;

    private static SeededAccounts seeded;

    @BeforeAll
    static void startAnApplicationOnAClockThisTestStands() {
        // Command-line arguments rather than default properties, for the reason the walking skeleton
        // gives: defaults lose to application.properties, which would point this instance at the
        // real database.
        application = new SpringApplicationBuilder(
                SavingStreakApplication.class, TheClockTheseTestsMove.class)
                .run("--spring.datasource.url=jdbc:sqlite:"
                                + aDatabaseFileThatDoesNotExistYet("saving-streak-calendar-week"),
                        "--spring.profiles.active=dev",
                        "--server.port=0");
        movableHttp = new TestRestTemplate();
        movableHttp.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
        realClock = application.getBean(ARealClockATestStands.class);
        seeded = new SeededAccounts(movableHttp);
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.close();
        }
    }

    @Test
    void a_week_on_from_the_week_the_clocks_go_back_leaves_the_week_asking_for_the_whole_minimum() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        realClock.standAt(THE_MONDAY_THE_WEEK_THE_CLOCKS_GO_BACK_BEGINS);
        DepositView made = deposit(savingsAccount, "60.00");
        BalancesView securedWeek = balancesOf(savingsAccount);
        assertThat(securedWeek.newSavingsThisWeek()).isEqualByComparingTo("60.00");
        assertThat(securedWeek.stillNeededThisWeek()).isEqualByComparingTo("0.00");

        ResponseEntity<ClockView> moved = advanceBy(A_WEEK);

        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Where the clock now stands, said twice: as the calendar reads it, and as a span. The first
        // is what a trainer expects — the same time of day, next Monday. The second is what makes it
        // a calendar week rather than a fixed one, and it is the hour that decides the week.
        assertThat(moved.getBody().now().atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                .toLocalDateTime())
                .isEqualTo(THE_MONDAY_THE_NEXT_WEEK_BEGINS);
        assertThat(Duration.between(THE_MONDAY_THE_WEEK_THE_CLOCKS_GO_BACK_BEGINS,
                moved.getBody().now()))
                .isEqualTo(A_WEEK_WITH_AN_HOUR_GIVEN_BACK);
        BalancesView aWeekOn = balancesOf(savingsAccount);
        assertThat(aWeekOn.newSavingsThisWeek()).isEqualByComparingTo("0.00");
        assertThat(aWeekOn.stillNeededThisWeek()).isEqualByComparingTo(aWeekOn.weeklyMinimum());
        // And the week that ended is still on the ledger it was derived from: a new week is a new
        // week's saving to do, not a history rewritten.
        assertThat(aWeekOn.moneyBalance()).isEqualByComparingTo(securedWeek.moneyBalance());
        assertThat(depositsInto(savingsAccount)).anySatisfy(listed -> {
            assertThat(listed.id()).isEqualTo(made.id());
            assertThat(listed.depositedAt()).isEqualTo(made.depositedAt());
        });
    }

    private static ResponseEntity<ClockView> advanceBy(long days) {
        return movableHttp.postForEntity(
                "/api/dev/clock/advance", Map.of("days", days), ClockView.class);
    }

    private static DepositView[] depositsInto(long savingsAccountId) {
        return movableHttp.getForObject(
                "/api/savings-accounts/{id}/deposits", DepositView[].class, savingsAccountId);
    }

    private static BalancesView balancesOf(long savingsAccountId) {
        return movableHttp.getForObject(
                "/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    private static DepositView deposit(long savingsAccountId, String amount) {
        ResponseEntity<DepositView> response = movableHttp.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", seeded.currentAccountOf(ANKE)),
                DepositView.class,
                savingsAccountId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
