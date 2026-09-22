package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A whole application of its own, on a database nothing has ever been written to, whose clock the
 * test that started it may move.
 *
 * <p>What a streak needs and the shared application cannot give. A streak is a run of weeks, so a
 * test about one is a sequence of deposits separated by weeks passing — and a week passes by moving
 * the development clock, which cannot be undone. Two tests sharing one clock would be asserting
 * against whatever order they happened to run in, and two tests sharing one database would each find
 * the other's deposits already counted into the week. A fresh application per test class gives a
 * seeded account with no history at all, which is the only place a run of weeks can be counted from
 * zero.
 *
 * <p>Driven entirely over HTTP, endpoints and all, so a test using it knows nothing about the
 * database or the derivation underneath. The clock is moved through the endpoint a trainer would use.
 *
 * <p>Shared rather than copied into each streak test, for the same reason as {@link BalancesView}:
 * four copies of "boot an application, find the seeded accounts, move a week on" are four chances to
 * disagree about what a week passing is.
 */
public final class AnApplicationWithAClockToMove implements AutoCloseable {

    /**
     * How many days a week is, as a move of the clock. Seven lands on the same weekday whatever day
     * the run happens on, so the move is always into the next week and never twice into the same one
     * — and the clock counts them as calendar days in the zone weeks are counted in, so it holds on
     * the two weekends a year that are 167 or 169 hours long.
     */
    private static final int DAYS_IN_A_WEEK = 7;

    private final ConfigurableApplicationContext application;
    private final TestRestTemplate http;
    private final SeededAccounts seeded;

    public AnApplicationWithAClockToMove(Path databaseFile) {
        // Command-line arguments rather than default properties, for the reason the walking skeleton
        // gives: defaults lose to application.properties, which would point this instance at the
        // real database.
        this.application = new SpringApplicationBuilder(SavingStreakApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + databaseFile,
                        "--spring.profiles.active=dev",
                        "--server.port=0");
        this.http = new TestRestTemplate();
        this.http.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
        this.seeded = new SeededAccounts(http);
    }

    /** The first savings account the customer holds, with no deposit ever made into it. */
    public long savingsAccountOf(String customerName) {
        return seeded.savingsAccountOf(customerName);
    }

    /** A second one the same customer holds, for asking whether two accounts' streaks stay apart. */
    public long otherSavingsAccountOf(String customerName) {
        return seeded.otherSavingsAccountOf(customerName);
    }

    /**
     * A deposit this test needed to land, with the refusal ruled out rather than assumed: a refused
     * deposit would leave a test asserting that a week nothing landed in secured nothing — and
     * passing.
     */
    public DepositView deposit(long savingsAccountId, String customerName, String amount) {
        ResponseEntity<DepositView> made = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", seeded.currentAccountOf(customerName)),
                DepositView.class,
                savingsAccountId);
        assertThat(made.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return made.getBody();
    }

    /**
     * A withdrawal, insisted on the same way: money that did not leave proves nothing about a streak.
     *
     * <p>Answers with what was recorded, because a test asserting on the order of a ledger needs the
     * moment this happened rather than only the fact that it did.
     */
    public WithdrawalView withdraw(long savingsAccountId, String customerName, String amount) {
        ResponseEntity<WithdrawalView> taken = http.postForEntity(
                "/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", seeded.currentAccountOf(customerName)),
                WithdrawalView.class,
                savingsAccountId);
        assertThat(taken.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return taken.getBody();
    }

    /**
     * A week passes, through the endpoint a trainer would use, and the move is insisted on: a clock
     * that refused to move would leave every later assertion about the wrong week.
     */
    public void aWeekPasses() {
        ResponseEntity<ClockView> moved = http.postForEntity(
                "/api/dev/clock/advance", Map.of("days", DAYS_IN_A_WEEK), ClockView.class);
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * A stretch of time passes, through the endpoint a trainer would use, and the move is insisted
     * on for the reason {@link #aWeekPasses} gives.
     *
     * <p>Counted in days, because that is what the clock endpoint takes and because a rule measured
     * in months is demonstrated by winding a number of days on and seeing which side of an
     * anniversary the clock lands. A test that wants a year says so in days and says why.
     */
    public void daysPass(long days) {
        ResponseEntity<ClockView> moved = http.postForEntity(
                "/api/dev/clock/advance", Map.of("days", days), ClockView.class);
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** What the application's clock reads now, which is the moment its own rules are judged against. */
    public Instant theClockReads() {
        return http.getForObject("/api/dev/clock", ClockView.class).now();
    }

    /**
     * What day the application's clock reads, in the zone it counts calendars in — which is the zone
     * every day-shaped answer it gives is a day in.
     */
    public LocalDate theDateTheClockReads() {
        return theClockReads().atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
    }

    /**
     * Runs a named job now and insists that it ran: a job refused for want of a name, or one that
     * threw, would leave a test asserting that nothing had happened — and passing.
     */
    public JobRunView runJob(String name) {
        ResponseEntity<JobRunView> ran = http.postForEntity(
                "/api/dev/jobs/{name}/run", null, JobRunView.class, name);
        assertThat(ran.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ran.getBody();
    }

    /** The jobs this application says can be run, so that a test can ask whether one is there at all. */
    public ScheduledJobView[] whatCanBeRun() {
        return http.getForObject("/api/dev/jobs", ScheduledJobView[].class);
    }

    /** What the customer has to spend, read off the overview where the figure belongs. */
    public long pointsBalanceOf(String customerName) {
        return seeded.pointsBalanceOf(customerName);
    }

    /** How many of the customer's points go next, and null when they have none left to lose. */
    public Long pointsExpiringNextOf(String customerName) {
        return seeded.pointsExpiringNextOf(customerName);
    }

    /** The day those points go, and null when there are none. */
    public LocalDate pointsExpiringNextOnOf(String customerName) {
        return seeded.pointsExpiringNextOnOf(customerName);
    }

    /**
     * What is in the customer's current account, for a test that has to say no euros moved.
     *
     * <p>The other end of every movement this application makes, and the figure that would give a
     * bonus away if paying one ever touched money: a savings balance that had not changed while the
     * current account had would be euros moving in a direction nobody asked for.
     */
    public BigDecimal currentAccountBalanceOf(String customerName) {
        return seeded.currentAccountBalanceOf(customerName);
    }

    /** Every movement of money in or out of the customer's savings, newest first. */
    public MoneyMovementView[] moneyMovementsOf(String customerName) {
        return http.getForObject("/api/customers/{id}/money-movements", MoneyMovementView[].class,
                seeded.customerIdOf(customerName));
    }

    /**
     * The account as the API reports it: both balances, the week, the run of weeks behind it, and
     * what that run pays.
     */
    public BalancesView balancesOf(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    /**
     * The account overview exactly as the API sends it, for a test that has to say a figure is
     * <em>not</em> in it.
     *
     * <p>The body rather than {@link BalancesView}, because a view binds the fields it knows about
     * and says nothing about the ones it does not: a test asserting that nothing was added to the
     * overview cannot ask a record that would have to be changed first in order to notice.
     */
    public String theAccountOverviewAsItIsSent(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", String.class, savingsAccountId);
    }

    /**
     * Every deposit into the account as the history reports it, newest first — what a customer sees
     * when they look back at what a deposit earned, rather than what they were told at the time.
     */
    public DepositView[] depositsInto(long savingsAccountId) {
        return http.getForObject(
                "/api/savings-accounts/{id}/deposits", DepositView[].class, savingsAccountId);
    }

    /**
     * A reward claimed by the customer, with the refusal ruled out: a claim that was turned down
     * would leave a test asserting that points nobody spent are still there — and passing.
     *
     * <p>By the customer rather than out of an account, because that is whose points pay for it.
     */
    public ClaimedRewardView claim(String customerName, String reward) {
        ResponseEntity<ClaimedRewardView> claimed = http.postForEntity(
                "/api/customers/{id}/redemptions",
                Map.of("reward", reward),
                ClaimedRewardView.class,
                seeded.customerIdOf(customerName));
        assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return claimed.getBody();
    }

    /** Why a claim the customer could not afford was refused, in the words they are given. */
    public String whyTheClaimWasRefused(String customerName, String reward) {
        ResponseEntity<ProblemView> refused = http.postForEntity(
                "/api/customers/{id}/redemptions",
                Map.of("reward", reward),
                ProblemView.class,
                seeded.customerIdOf(customerName));
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        return refused.getBody().detail();
    }

    @Override
    public void close() {
        application.close();
    }

    /**
     * As much of a withdrawal as these tests read back: that it happened, for how much, and when.
     *
     * <p>The moment is here because a ledger asserted to be newest-first has to be checked against
     * the moments the API itself reported, rather than against the order the requests were sent in.
     */
    public record WithdrawalView(Long id, BigDecimal amount, Instant withdrawnAt) {
    }

    /**
     * A refusal as the API answers with one (RFC 9457), of which these tests read the sentence the
     * customer is shown and nothing else.
     */
    private record ProblemView(String detail) {
    }
}
