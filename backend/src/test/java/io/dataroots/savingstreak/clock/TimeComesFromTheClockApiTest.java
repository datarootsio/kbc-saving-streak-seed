package io.dataroots.savingstreak.clock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
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
 * When the application records that something happened, the moment comes from the clock it was given
 * rather than from the machine it is running on.
 *
 * <p>Asserted by standing an application up against a clock that does not move and then asking it
 * over HTTP what it recorded. Nothing below the API is touched: the clock is a production seam — the
 * slice that lets a trainer wind time forward replaces this same bean — and a test reaching into a
 * service to prove it would be asserting the wiring rather than the behaviour.
 *
 * <p>Its own application and its own database, started the way the walking skeleton starts a second
 * one. Everything recorded here is dated years away from what the rest of the run puts in the shared
 * file, and a listing ordered by moment is not somewhere to leave that lying.
 */
class TimeComesFromTheClockApiTest extends ApiIntegrationTest {

    /**
     * What the clock reads: a moment no machine in this run is at, carrying nanoseconds so that the
     * precision the application keeps is read off what it recorded rather than assumed.
     */
    private static final Instant THE_CLOCK_READS = Instant.parse("2019-11-05T09:41:17.123456789Z");

    /** The same moment to the millisecond, which is the precision a recorded moment is kept to. */
    private static final Instant TO_THE_MILLISECOND = Instant.parse("2019-11-05T09:41:17.123Z");

    private static ConfigurableApplicationContext application;

    /** Bound to the application above rather than to the shared one this class inherits. */
    private static TestRestTemplate clockedHttp;

    private static SeededAccounts seeded;

    @BeforeAll
    static void startAnApplicationWhoseClockDoesNotMove() {
        // Command-line arguments rather than default properties, for the reason the walking skeleton
        // gives: defaults lose to application.properties, which would point this instance at the
        // real database.
        application = new SpringApplicationBuilder(SavingStreakApplication.class, AClockThatDoesNotMove.class)
                .run("--spring.datasource.url=jdbc:sqlite:"
                                + aDatabaseFileThatDoesNotExistYet("saving-streak-fixed-clock"),
                        "--spring.profiles.active=dev",
                        "--server.port=0");
        clockedHttp = new TestRestTemplate();
        clockedHttp.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
        seeded = new SeededAccounts(clockedHttp);
    }

    @AfterAll
    static void stopTheApplication() {
        application.close();
    }

    /**
     * The moment a deposit happened is the clock's, not the machine's. A machine would answer with
     * today, and today is years away from what this clock reads.
     */
    @Test
    void a_deposit_happens_at_the_moment_the_applications_clock_reads() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);

        ResponseEntity<DepositView> response = deposit(savingsAccount, "5.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().depositedAt()).isEqualTo(TO_THE_MILLISECOND);
        // And still that moment when it is read back, rather than only in the answer to the request
        // that made it.
        assertThat(depositsInto(savingsAccount).getBody()).anySatisfy(listed -> {
            assertThat(listed.id()).isEqualTo(response.getBody().id());
            assertThat(listed.depositedAt()).isEqualTo(TO_THE_MILLISECOND);
        });
    }

    /**
     * A claim reads the same clock a deposit does. Two clocks would mean a wound-forward application
     * where the deposits had moved and the vouchers had not.
     */
    @Test
    void a_claim_happens_at_the_moment_the_applications_clock_reads() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);
        deposit(savingsAccount, "10.00");

        ResponseEntity<ClaimedRewardView> response = claim(savingsAccount, "CHARITY_DONATION");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().claimedAt()).isEqualTo(TO_THE_MILLISECOND);
    }

    /**
     * A moment is kept to the millisecond, as it was when it came from the machine. Anything finer is
     * a reading the database does not hold on to, and the deposit would be reported back under one
     * moment and listed under another.
     */
    @Test
    void a_moment_is_kept_to_the_millisecond() {
        long savingsAccount = seeded.savingsAccountOf(ANKE);

        DepositView made = deposit(savingsAccount, "2.00").getBody();

        assertThat(made.depositedAt()).isNotEqualTo(THE_CLOCK_READS);
        assertThat(made.depositedAt()).isEqualTo(THE_CLOCK_READS.truncatedTo(ChronoUnit.MILLIS));
    }

    /**
     * Deposits sharing a moment are still listed newest first, which is the second half of the
     * ordering doing the work: by moment, and then by identifier. A clock that does not move makes
     * every deposit simultaneous — the hardest case that ordering has to answer, and one no machine's
     * clock could be relied on to produce.
     *
     * <p>Anke's other savings account, so that the deposits the rest of this class makes are not in
     * the listing being ordered.
     */
    @Test
    void deposits_made_at_the_same_moment_are_listed_newest_identifier_first() {
        long savingsAccount = seeded.otherSavingsAccountOf(ANKE);

        DepositView first = deposit(savingsAccount, "1.00").getBody();
        DepositView second = deposit(savingsAccount, "2.00").getBody();
        DepositView third = deposit(savingsAccount, "3.00").getBody();

        assertThat(first.depositedAt()).isEqualTo(third.depositedAt());
        assertThat(depositsInto(savingsAccount).getBody())
                .extracting(DepositView::id)
                .containsExactly(third.id(), second.id(), first.id());
    }

    private ResponseEntity<DepositView[]> depositsInto(long savingsAccountId) {
        return clockedHttp.getForEntity(
                "/api/savings-accounts/{id}/deposits", DepositView[].class, savingsAccountId);
    }

    private ResponseEntity<DepositView> deposit(long savingsAccountId, String amount) {
        return clockedHttp.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", seeded.currentAccountOf(ANKE)),
                DepositView.class,
                savingsAccountId);
    }

    private ResponseEntity<ClaimedRewardView> claim(long savingsAccountId, String reward) {
        return clockedHttp.postForEntity(
                "/api/savings-accounts/{id}/redemptions",
                Map.of("reward", reward),
                ClaimedRewardView.class,
                savingsAccountId);
    }

    /**
     * The clock this test's application is given, standing in for the one it would supply itself.
     *
     * <p>It carries no stereotype annotation and is handed to the builder by name instead. A
     * {@code @Configuration} or {@code @TestConfiguration} here would sit in a package the
     * application scans, and every other application these tests start — the walking skeleton's
     * among them — would quietly come up with its clock stopped in 2019.
     */
    static class AClockThatDoesNotMove {

        @Bean
        @Primary
        Clock aClockFixedToAChosenMoment() {
            return Clock.fixed(THE_CLOCK_READS, ZoneOffset.UTC);
        }
    }
}
