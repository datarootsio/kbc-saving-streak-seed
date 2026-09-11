package io.dataroots.savingstreak.weeklysavings;

import java.math.BigDecimal;
import java.util.Map;

import io.dataroots.savingstreak.SavingStreakApplication;
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
 * A trainer moves the development clock a week on, and the account's week starts again — with
 * nothing deposited and no job run in between.
 *
 * <p>The trainer's own path, driven through the endpoint they would use rather than through a clock
 * a test supplied: what a participant will actually do to demonstrate a week passing is post seven
 * days to {@code /api/dev/clock/advance}, and this is the assertion that the figure on the screen
 * follows.
 *
 * <p>Its own application and its own database, like the other tests that move a clock. Seven days
 * rather than any other number because it lands on the same weekday, so the move is into the next
 * week whichever day of the week the run happens on — and the clock counts those seven as calendar
 * days in the zone weeks are counted in, so this holds on the two weekends a year that are 167 or
 * 169 hours long as well.
 */
class TheWeekMovesWithTheDevelopmentClockApiTest extends ApiIntegrationTest {

    private static ConfigurableApplicationContext application;

    /** Bound to the application this class moves the clock on, not to the shared one. */
    private static TestRestTemplate movableHttp;

    private static SeededAccounts seeded;

    @BeforeAll
    static void startAnApplicationWhoseClockCanBeMoved() {
        // Command-line arguments rather than default properties, for the reason the walking skeleton
        // gives: defaults lose to application.properties, which would point this instance at the
        // real database.
        application = new SpringApplicationBuilder(SavingStreakApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:"
                                + aDatabaseFileThatDoesNotExistYet("saving-streak-week-on"),
                        "--spring.profiles.active=dev",
                        "--server.port=0");
        movableHttp = new TestRestTemplate();
        movableHttp.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
        seeded = new SeededAccounts(movableHttp);
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.close();
        }
    }

    /**
     * The one test in this class, because moving the clock is the one thing here that cannot be
     * undone: a second test would find it already moved and would be asserting against whatever
     * order the two happened to run in.
     */
    @Test
    void a_week_on_the_development_clock_leaves_the_week_asking_for_the_whole_minimum_again() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        DepositView made = deposit(savingsAccount, "60.00");
        BalancesView securedWeek = balancesOf(savingsAccount);
        assertThat(securedWeek.newSavingsThisWeek()).isGreaterThanOrEqualTo(new BigDecimal("60.00"));
        assertThat(securedWeek.stillNeededThisWeek()).isEqualByComparingTo("0.00");

        ResponseEntity<ClockView> moved = movableHttp.postForEntity(
                "/api/dev/clock/advance", Map.of("days", 7), ClockView.class);

        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        BalancesView aWeekOn = balancesOf(savingsAccount);
        assertThat(aWeekOn.newSavingsThisWeek()).isEqualByComparingTo("0.00");
        assertThat(aWeekOn.stillNeededThisWeek()).isEqualByComparingTo(aWeekOn.weeklyMinimum());
        // Nothing else moved. The deposit is still there, still worth what it was worth, and still
        // dated where it was: a new week is a new week's saving to do and not a history rewritten.
        assertThat(aWeekOn.moneyBalance()).isEqualByComparingTo(securedWeek.moneyBalance());
        assertThat(aWeekOn.pointsBalance()).isEqualTo(securedWeek.pointsBalance());
        assertThat(depositsInto(savingsAccount)).anySatisfy(listed -> {
            assertThat(listed.id()).isEqualTo(made.id());
            assertThat(listed.amount()).isEqualByComparingTo("60.00");
            assertThat(listed.pointsEarned()).isEqualTo(60);
            assertThat(listed.depositedAt()).isEqualTo(made.depositedAt());
        });
    }

    private static DepositView[] depositsInto(long savingsAccountId) {
        return movableHttp.getForObject(
                "/api/savings-accounts/{id}/deposits", DepositView[].class, savingsAccountId);
    }

    private static BalancesView balancesOf(long savingsAccountId) {
        return movableHttp.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
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
