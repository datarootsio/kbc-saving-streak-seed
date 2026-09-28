package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.InterestPostingView;
import io.dataroots.savingstreak.support.JobRunView;
import io.dataroots.savingstreak.support.TermsVersionView;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A whole application of its own, on a database nothing has ever been written to, whose clock a
 * test may wind through whole months — and which will open a savings account on the core saver for
 * anybody who asks.
 *
 * <p><strong>Its own application because it winds months, twice over.</strong> The rule under test
 * is a monthly one and the interesting case is two consecutive months paying different amounts, so
 * a test here moves the clock sixty-odd days. Nothing sharing a clock or a database with the rest
 * of the run could survive that, which is the same reason {@code ANoticeAccountSomebodyHolds} and
 * {@link io.dataroots.savingstreak.support.AnApplicationWithAClockToMove} each start one.
 *
 * <p><strong>Every test here opens an account of its own, on the day the clock happens to be
 * standing on.</strong> A period is a calendar month counted from the day the account was opened,
 * so an account opened now has its own periods whatever the class has already wound past — which is
 * what makes these tests independent of the order JUnit runs them in without any of them having to
 * work out where in somebody else's month the clock has got to. The clock only ever moves forwards,
 * and {@link #aWholePeriodPassesFor} counts the days to the end of the next period rather than
 * assuming a month is thirty-one days, because these tests have to be right in February.
 *
 * <p><strong>It opens on {@code CORE} directly, and that is a change of shape from the notice
 * harness beside it.</strong> That one republishes free savings with a notice period, because when
 * it was written nothing could open an account on a product of the customer's choosing. That door
 * exists now, so the account under test here is the core saver the catalogue actually seeds — its
 * floor, its headline rate and its bonus rate as published — and nothing here republishes anything.
 * The figures are still read back through the API rather than written into a test: the bonus a
 * month earns is the bonus <em>this account's own version</em> names, and a test that typed 0.70%
 * would be asserting what the seed happens to say this month.
 *
 * <p>Driven entirely over HTTP, endpoints and all, so a test using it knows nothing about a table,
 * a column or a service. {@link ASaverChoosingAProduct} is what does the opening and the moving of
 * money, reused rather than copied, so that "open an account on a product and put money in it" is
 * one thing in these tests rather than two that could disagree.
 */
final class ACoreSaverSomebodyHolds implements AutoCloseable {

    /** The zone this application counts its days in, which is the zone a period is dated in. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    /** The product under test, by the code the catalogue seeds it under and never changes. */
    static final String THE_CORE_SAVER = "CORE";

    /** The name a trainer types to run the monthly sweep early, which is what these tests press. */
    static final String THE_INTEREST_SWEEP = "postMonthlyInterest";

    private final ConfigurableApplicationContext application;
    private final TestRestTemplate http;

    ACoreSaverSomebodyHolds(Path databaseFile) {
        // Command-line arguments rather than default properties, for the reason the walking
        // skeleton gives: defaults lose to application.properties, which would point this instance
        // at the real database.
        this.application = new SpringApplicationBuilder(SavingStreakApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + databaseFile,
                        "--spring.profiles.active=dev",
                        "--server.port=0");
        this.http = new TestRestTemplate();
        this.http.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
    }

    /**
     * A customer of this test's own, with a core saver opened today on top of the account they were
     * opened with.
     *
     * <p>Two accounts rather than one, and the second one matters: the account a customer is opened
     * with is on free savings, which has no bonus to earn, so the same customer carries both halves
     * of the comparison this feature is about. The core saver is opened <em>after</em> the clock
     * has been wound by whatever ran before, so its first period begins today.
     */
    ACoreSaver aCoreSaverOpenedToday(String whoHoldsIt) {
        ASaverChoosingAProduct saver = new ASaverChoosingAProduct(http, whoHoldsIt);
        long coreSaver = saver.open(THE_CORE_SAVER).id();
        return new ACoreSaver(saver, coreSaver);
    }

    /** What one savings account is living under, off its own page. */
    AnAgreementView theAgreementOf(long savingsAccountId) {
        AnAgreementView agreement = theAccount(savingsAccountId).agreement();
        assertThat(agreement)
                .describedAs("savings account " + savingsAccountId + " is living under an "
                        + "agreement, without which nothing can say what it is paid")
                .isNotNull();
        return agreement;
    }

    /**
     * One published version of a product's terms, by the number the account names it with.
     *
     * <p>The rates these tests assert against come from here rather than from figures typed into
     * them, because an account is paid at the rate its own version names. A test that hard-coded
     * the core saver's 0.80% and 0.70% would pass or fail on whatever the seed was written with
     * this month, and would say nothing at all about whether the two are added together.
     */
    TermsVersionView theVersionOf(String productCode, int version) {
        ResponseEntity<TermsVersionView[]> read = http.getForEntity(
                "/api/savings-products/{code}/versions", TermsVersionView[].class, productCode);
        assertThat(read.getStatusCode())
                .describedAs("reading the versions " + productCode + " has published")
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody()).stream()
                .filter(published -> published.version() == version)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        productCode + " has never published a version " + version));
    }

    /**
     * Every month this account has been judged for, oldest first, read through the endpoint the
     * account's own screen reads.
     *
     * <p>Through the door rather than out of the module that wrote it, so that a test asserting
     * what a month paid is asserting what a customer would be shown.
     */
    List<InterestPostingView> interestPaidInto(long savingsAccountId) {
        ResponseEntity<InterestPostingView[]> read = http.getForEntity(
                "/api/savings-accounts/{id}/interest", InterestPostingView[].class,
                savingsAccountId);
        assertThat(read.getStatusCode())
                .describedAs("reading the interest paid into savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /** What this savings account holds, which is the figure a withholding must leave alone. */
    BigDecimal balanceOf(long savingsAccountId) {
        return theAccount(savingsAccountId).moneyBalance();
    }

    /**
     * One savings account's own page, by identifier.
     *
     * <p>Here rather than on {@link ASaverChoosingAProduct} because these tests ask about accounts
     * rather than about people: two of them read an account the clock has been wound past, and one
     * reads the account its customer was opened with rather than the one it chose.
     */
    private BalancesView theAccount(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class,
                savingsAccountId);
    }

    /** The day this application thinks it is, read off its own clock rather than the machine's. */
    LocalDate theDateTheClockReads() {
        return LocalDate.ofInstant(
                http.getForObject("/api/dev/clock", ClockView.class).now(), BRUSSELS);
    }

    /** Moves the clock, through the endpoint a trainer would use. */
    void daysPass(long days) {
        ResponseEntity<ClockView> moved = http.postForEntity("/api/dev/clock/advance",
                Map.of("days", days), ClockView.class);
        assertThat(moved.getStatusCode()).describedAs("moving the clock " + days + " days on")
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * Winds the clock to the day that account's <em>n</em>-th period ends, which is the first day
     * the sweep can pay for it.
     *
     * <p>Counted to the day rather than "thirty-one days on", because a period is a calendar month
     * from the day the account was opened and these tests must be right whichever month the run
     * happens in. The clock only moves forwards and only in whole days, which is exactly what this
     * arithmetic is for.
     */
    void aWholePeriodPassesFor(LocalDate openedOn, int ordinal) {
        LocalDate until = openedOn.plusMonths(ordinal);
        daysPass(ChronoUnit.DAYS.between(theDateTheClockReads(), until));
    }

    /** Runs the nightly job by the name a trainer types, with a failure ruled out. */
    JobRunView runJob(String name) {
        ResponseEntity<JobRunView> ran = http.postForEntity("/api/dev/jobs/{name}/run", null,
                JobRunView.class, name);
        assertThat(ran.getStatusCode()).describedAs("running " + name).isEqualTo(HttpStatus.OK);
        return ran.getBody();
    }

    /**
     * Takes money out with whatever came back, unasserted, for the test whose whole subject is that
     * nothing refuses it.
     *
     * <p>Raw rather than through {@link ASaverChoosingAProduct#takeOut}, which asserts the status
     * itself. The rule under test here is that a floor refuses nothing, and a test whose assertion
     * lives in a helper is a test that reads as though it were about something else — the status
     * and the sentence beside it belong in the method that is about them.
     */
    ResponseEntity<JsonNode> tryToTakeOut(ACoreSaver saver, String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", saver.saver().currentAccountId()),
                JsonNode.class, saver.savingsAccountId());
    }

    @Override
    public void close() {
        application.close();
    }

    /**
     * One customer's core saver: the saver who holds it, so that money can be moved in and out of
     * it, and the account itself.
     *
     * <p>A pair rather than two locals in every test, because every one of these tests needs both
     * and the two are only meaningful together — the account is where the rule happens and the
     * saver is who it happens to.
     */
    record ACoreSaver(ASaverChoosingAProduct saver, long savingsAccountId) {

        /** Pays money into the core saver, with a refusal ruled out. */
        void payIn(String amount) {
            saver.payIn(savingsAccountId, amount);
        }

        /** Takes money back out of the core saver, with a refusal ruled out. */
        void takeOut(String amount) {
            saver.takeOut(savingsAccountId, amount);
        }

        /** The account this customer was opened with, which is on free savings and has no bonus. */
        long theirFreeSavingsAccount() {
            return saver.theAccountTheyWereOpenedWith();
        }
    }
}
