package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import io.dataroots.savingstreak.SavingStreakApplication;
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

    /** A withdrawal, insisted on the same way: money that did not leave proves nothing about a streak. */
    public void withdraw(long savingsAccountId, String customerName, String amount) {
        ResponseEntity<WithdrawalView> taken = http.postForEntity(
                "/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", seeded.currentAccountOf(customerName)),
                WithdrawalView.class,
                savingsAccountId);
        assertThat(taken.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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
     * The account as the API reports it: both balances, the week, the run of weeks behind it, and
     * what that run pays.
     */
    public BalancesView balancesOf(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
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

    /** As much of a withdrawal as these tests read back: that it happened, and for how much. */
    private record WithdrawalView(Long id, BigDecimal amount, Instant withdrawnAt) {
    }

    /**
     * A refusal as the API answers with one (RFC 9457), of which these tests read the sentence the
     * customer is shown and nothing else.
     */
    private record ProblemView(String detail) {
    }
}
