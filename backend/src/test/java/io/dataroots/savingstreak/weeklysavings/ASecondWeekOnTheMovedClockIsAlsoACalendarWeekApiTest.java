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
 * A second "advance a week" is a second calendar week, even when real time has crossed a clock change
 * since the first one.
 *
 * <p>{@code AWeekOnTheMovedClockIsACalendarWeekApiTest} asks the same question of one move: seven
 * days from a standing start is 169 hours in the week the clocks go back, not 168. This asks it of
 * the composition of two, which is the ordinary way the clock is used and not an exotic one — the
 * position survives a restart, so advancing in October, coming back in November and advancing again
 * is a Monday morning at the training laptop. The trap is that the span for the new total worked out
 * against the later real moment is not the earlier span plus the days asked for: real time has lost
 * an hour in between, so "advance seven days" moves the clock 167 hours, and a trainer standing half
 * an hour into a Monday is put at 23:30 on the Sunday of the week they were already in. The week does
 * not reset, and the demonstration the button exists for shows the opposite of what it claims.
 *
 * <p>The moments are chosen so that the two answers differ, which real time reaches for one hour a
 * year: the clock is moved on Friday 2026-10-23, real time then passes over the fall-back of Sunday
 * 2026-10-25 with the application up, and the clock is moved again. Its own application, its own
 * database and its own real clock — see {@link TheClockTheseTestsMove}.
 *
 * <p>Asserted twice over, because the reading alone would not say what a participant loses: where the
 * clock stands, and what the account's week reports once it is there.
 */
class ASecondWeekOnTheMovedClockIsAlsoACalendarWeekApiTest extends ApiIntegrationTest {

    /** Seven days, asked for the way a trainer asks for the next week. Twice. */
    private static final long A_WEEK = 7;

    /**
     * Friday 2026-10-23 12:00 in Brussels, where the clock is first moved from: two days before the
     * Sunday the clocks go back, so the seven calendar days asked for span that Sunday and come to
     * 169 hours.
     */
    private static final Instant REAL_TIME_BEFORE_THE_CLOCKS_WENT_BACK =
            ZonedDateTime.of(2026, 10, 23, 12, 0, 0, 0, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                    .toInstant();

    /** Where the first move leaves the clock: the same time of day, seven dates on. */
    private static final LocalDateTime AFTER_THE_FIRST_MOVE = LocalDateTime.of(2026, 10, 30, 12, 0);

    /**
     * Sunday 2026-11-01 23:30 in Brussels, where real time has got to by the second move — past
     * 03:00 on 2026-10-25, which became 02:00. A clock 169 hours ahead then reads half an hour into
     * Monday 2026-11-09, and seven calendar days on from <em>there</em> is 168 hours; seven counted
     * from this real moment instead is the hour short that loses the week.
     */
    private static final Instant REAL_TIME_AFTER_THEY_DID =
            ZonedDateTime.of(2026, 11, 1, 23, 30, 0, 0, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                    .toInstant();

    /** Where the clock reads by then, half an hour into the Monday whose week the deposit belongs to. */
    private static final LocalDateTime BEFORE_THE_SECOND_MOVE = LocalDateTime.of(2026, 11, 9, 0, 30);

    /**
     * Where the second move has to leave it: half an hour into the next Monday, the same time of day
     * seven dates on again. The span worked out afresh for fourteen days reads 23:30 on Sunday
     * 2026-11-15 instead — an hour earlier, and the week the trainer was already in.
     */
    private static final LocalDateTime AFTER_THE_SECOND_MOVE = LocalDateTime.of(2026, 11, 16, 0, 30);

    /** What the second week measures, there being no clock change inside it. */
    private static final Duration A_WEEK_WITH_NOTHING_HAPPENING_IN_IT = Duration.ofHours(168);

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
                                + aDatabaseFileThatDoesNotExistYet("saving-streak-second-week"),
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

    /**
     * One test, because moving the clock cannot be undone: a second would find it already moved and
     * would be asserting against whichever order the two ran in.
     */
    @Test
    void a_week_on_from_a_week_already_moved_leaves_the_week_asking_for_the_whole_minimum() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        realClock.standAt(REAL_TIME_BEFORE_THE_CLOCKS_WENT_BACK);

        ClockView firstMove = advanceBy(A_WEEK);

        assertThat(readInBrussels(firstMove.now()))
                .as("the first week, counted through the calendar from a standing start")
                .isEqualTo(AFTER_THE_FIRST_MOVE);
        // Real time crosses the fall-back with the application up, which is what leaves the span the
        // clock is using and the span its total days would come to now an hour apart. The reading
        // moves with it, because a reading is the real moment plus that span.
        realClock.standAt(REAL_TIME_AFTER_THEY_DID);
        Instant beforeTheSecondMove = whereTheClockIs().now();
        assertThat(readInBrussels(beforeTheSecondMove))
                .as("where the trainer is standing when they ask for the next week")
                .isEqualTo(BEFORE_THE_SECOND_MOVE);
        DepositView made = deposit(savingsAccount, "30.00");
        BalancesView thisWeek = balancesOf(savingsAccount);
        assertThat(thisWeek.newSavingsThisWeek()).isEqualByComparingTo("30.00");

        ClockView secondMove = advanceBy(A_WEEK);

        // Where it now stands, said twice: as the calendar reads it, and as the span the move added.
        // The first is what a trainer expects — the same time of day, next Monday. The second is that
        // the days were counted from the reading rather than from the real moment, which is the hour
        // that decides which week they are in.
        assertThat(readInBrussels(secondMove.now())).isEqualTo(AFTER_THE_SECOND_MOVE);
        assertThat(Duration.between(beforeTheSecondMove, secondMove.now()))
                .as("seven calendar days on from where the clock was reading")
                .isEqualTo(A_WEEK_WITH_NOTHING_HAPPENING_IN_IT);
        assertThat(secondMove.movedForwardByDays()).isEqualTo(A_WEEK + A_WEEK);
        // And the week the account reports has gone with it. This is the criterion: a trainer who
        // advances a week is shown a week with its saving still to do.
        BalancesView aWeekOn = balancesOf(savingsAccount);
        assertThat(aWeekOn.newSavingsThisWeek()).isEqualByComparingTo("0.00");
        assertThat(aWeekOn.stillNeededThisWeek()).isEqualByComparingTo(aWeekOn.weeklyMinimum());
        // Nothing else moved: a new week is a new week's saving to do, not a history rewritten.
        assertThat(aWeekOn.moneyBalance()).isEqualByComparingTo(thisWeek.moneyBalance());
        assertThat(aWeekOn.pointsBalance()).isEqualTo(thisWeek.pointsBalance());
        assertThat(depositsInto(savingsAccount)).anySatisfy(listed -> {
            assertThat(listed.id()).isEqualTo(made.id());
            assertThat(listed.depositedAt()).isEqualTo(made.depositedAt());
        });
    }

    private static LocalDateTime readInBrussels(Instant moment) {
        return moment.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDateTime();
    }

    private static ClockView advanceBy(long days) {
        ResponseEntity<ClockView> response = movableHttp.postForEntity(
                "/api/dev/clock/advance", Map.of("days", days), ClockView.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static ClockView whereTheClockIs() {
        ResponseEntity<ClockView> response =
                movableHttp.getForEntity("/api/dev/clock", ClockView.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
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
