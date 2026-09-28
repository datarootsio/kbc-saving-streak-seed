package io.dataroots.savingstreak.whatifididthisinstead;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.InterestPostingView;
import io.dataroots.savingstreak.support.JobRunView;
import io.dataroots.savingstreak.support.SimulationView;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An application of this test's own, with savers who hold accounts on real savings products and a
 * year that can be played out on it.
 *
 * <p><strong>Its own application, because a year passes in it.</strong> A clock cannot be wound
 * back, so nothing that shares one with another test can have twelve months go by in the middle of
 * it. That is the bargain {@code AnApplicationWithAClockToMove} already strikes and it is struck
 * again here.
 *
 * <p><strong>A harness of this package's own rather than a reach into the products tests.</strong>
 * {@code ASaverChoosingAProduct} does most of this already and is package-private in the package it
 * was written for, which is right: what a saver presses to open an account on a product is that
 * feature's vocabulary. What this one adds is the two things only the simulator needs — a branch
 * asked for over HTTP, and a night that runs <em>all six</em> of the jobs a projection can see,
 * including the monthly interest sweep the fold now posts.
 */
final class ASaverOnAProductWithAYearToPlayOut implements AutoCloseable {

    /** The zone this application counts its days in, which is the zone an agreement is dated in. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    /** The four codes the catalogue seeds, named here so a test can say which it means. */
    static final String FREE_SAVINGS = "INSTANT";
    static final String THIRTY_TWO_DAY_NOTICE = "NOTICE32";
    static final String THE_TWELVE_MONTH_FIXED = "FIXED12";

    private final ConfigurableApplicationContext application;
    private final TestRestTemplate http;

    ASaverOnAProductWithAYearToPlayOut(Path databaseFile) {
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
     * A customer of this test's own with an account on that product, the money already in it.
     *
     * <p>One customer per product rather than one customer holding both, because what a current
     * account is opened with has to cover what goes into savings and because the run of weeks and
     * the high-water mark are the <em>person's</em> — two products under one holder would have each
     * projection reasoning about the other one's deposits, which is a true and interesting thing
     * and is not what these tests are about.
     */
    ASaverOnAProduct aSaverOn(String product, String whoTheyAre, String paysIn) {
        ASaverOnAProduct saver = new ASaverOnAProduct(whoTheyAre, product);
        saver.paysIn(paysIn);
        return saver;
    }

    /** The day this application thinks it is, read off its own clock rather than the machine's. */
    LocalDate theDateTheClockReads() {
        return LocalDate.ofInstant(
                http.getForObject("/api/dev/clock", ClockView.class).now(), BRUSSELS);
    }

    /** Moves the clock to twelve months after that day, through the endpoint a trainer would use. */
    void aWholeYearPassesFrom(LocalDate openedOn) {
        daysPass(ChronoUnit.DAYS.between(theDateTheClockReads(), openedOn.plusMonths(12)));
    }

    void daysPass(long days) {
        ResponseEntity<ClockView> moved = http.postForEntity("/api/dev/clock/advance",
                Map.of("days", days), ClockView.class);
        assertThat(moved.getStatusCode()).describedAs("moving the clock " + days + " days on")
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * Every nightly run a projection can see, in the order they are scheduled.
     *
     * <p>Fired once after a single wind rather than night by night, and that is safe here for
     * exactly one reason: nothing moves in these accounts. There is no salary to land, no rule to
     * fire and no bill to present, so the ordering that makes {@code aWholeNightPasses} exist —
     * this morning's bills meeting the balance this morning's rules left — has nothing to order. A
     * single catch-up credits the same twelve months on the same twelve days, and it is six round
     * trips rather than two and a half thousand.
     *
     * <p>The order still matters inside the one catch-up: a batch expiring before a bonus is
     * credited is what stops a bonus paid this morning from going the same morning, and the
     * interest is last because a quarter to four is where its own run is scheduled.
     */
    void everyNightlyRunThatAProjectionCanSee() {
        runJob("creditMonthlyIncome");
        runJob("fireSavingRulesDue");
        runJob("takeBillsDue");
        runJob("expireOldPoints");
        runJob("payLoyaltyBonuses");
        runJob("postMonthlyInterest");
    }

    /** Runs one nightly job by the name a trainer types, with a failure ruled out. */
    JobRunView runJob(String name) {
        ResponseEntity<JobRunView> ran = http.postForEntity("/api/dev/jobs/{name}/run", null,
                JobRunView.class, name);
        assertThat(ran.getStatusCode()).describedAs("running " + name).isEqualTo(HttpStatus.OK);
        return ran.getBody();
    }

    @Override
    public void close() {
        application.close();
    }

    /** One saver, the account they opened on a product, and everything a test does to either. */
    final class ASaverOnAProduct {

        private final long customerId;
        private final long currentAccountId;
        private final long savingsAccountId;
        private final String product;

        private ASaverOnAProduct(String whoTheyAre, String product) {
            this.product = product;
            ResponseEntity<CustomerView> added = http.postForEntity("/api/customers",
                    Map.of("name", whoTheyAre,
                            "contactDetails", "simulated.product."
                                    + whoTheyAre.replace(' ', '.') + "@example.be"),
                    CustomerView.class);
            assertThat(added.getStatusCode()).describedAs("opening " + whoTheyAre)
                    .isEqualTo(HttpStatus.CREATED);
            this.customerId = added.getBody().id();
            OverviewView theirs = overview();
            this.currentAccountId = theirs.currentAccounts().get(0).id();
            ResponseEntity<SavingsAccountView> opened = http.postForEntity(
                    "/api/customers/{id}/savings-accounts", Map.of("product", product),
                    SavingsAccountView.class, customerId);
            assertThat(opened.getStatusCode())
                    .describedAs("opening a savings account on " + product)
                    .isEqualTo(HttpStatus.CREATED);
            this.savingsAccountId = opened.getBody().id();
        }

        long savingsAccountId() {
            return savingsAccountId;
        }

        String product() {
            return product;
        }

        /** Pays money into the account on the product, with a refusal ruled out. */
        void paysIn(String amount) {
            ResponseEntity<JsonNode> paid = http.postForEntity(
                    "/api/savings-accounts/{id}/deposits",
                    Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                    JsonNode.class, savingsAccountId);
            assertThat(paid.getStatusCode())
                    .describedAs("paying EUR " + amount + " into " + product + ": "
                            + paid.getBody())
                    .isEqualTo(HttpStatus.CREATED);
        }

        /** The twelve months this account is heading for, as the frontend asks for them. */
        SimulationView theYearAhead() {
            ResponseEntity<SimulationView> answered = http.postForEntity(
                    "/api/savings-accounts/{id}/simulations", Map.of("scenarios", List.of()),
                    SimulationView.class, savingsAccountId);
            assertThat(answered.getStatusCode())
                    .describedAs("asking what the year ahead holds for " + product)
                    .isEqualTo(HttpStatus.OK);
            return answered.getBody();
        }

        /** The same question carrying one branch, answered or refused, as it comes back. */
        ResponseEntity<JsonNode> askingAbout(Map<String, Object> scenario) {
            return http.postForEntity("/api/savings-accounts/{id}/simulations",
                    Map.of("scenarios", List.of(scenario)), JsonNode.class, savingsAccountId);
        }

        /** A withdrawal really attempted, answered or refused, as it comes back. */
        ResponseEntity<JsonNode> tryingToTakeOut(String amount) {
            return http.postForEntity("/api/savings-accounts/{id}/withdrawals",
                    Map.of("amount", amount, "toCurrentAccountId", currentAccountId),
                    JsonNode.class, savingsAccountId);
        }

        /** What the account's own screen reports: the euros, the points and the run of weeks. */
        BalancesView asTheAccountReportsItself() {
            return http.getForObject("/api/savings-accounts/{id}", BalancesView.class,
                    savingsAccountId);
        }

        /** Every month this account has been judged for, oldest first. */
        List<InterestPostingView> interestPaidIn() {
            return List.of(http.getForObject("/api/savings-accounts/{id}/interest",
                    InterestPostingView[].class, savingsAccountId));
        }

        private OverviewView overview() {
            return http.getForObject("/api/customers/{id}/accounts", OverviewView.class,
                    customerId);
        }
    }

    /** The sentence the domain wrote, carried into the problem detail untouched. */
    static String reasonGivenBy(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody()).describedAs("the problem detail").isNotNull();
        return response.getBody().path("detail").asText();
    }

    /** Only the fields these tests read, so that a change elsewhere cannot break them. */
    private record OverviewView(List<CurrentAccountView> currentAccounts) {
    }

    private record CurrentAccountView(Long id, BigDecimal balance) {
    }

    private record CustomerView(Long id, String name) {
    }

    private record SavingsAccountView(Long id) {
    }
}
