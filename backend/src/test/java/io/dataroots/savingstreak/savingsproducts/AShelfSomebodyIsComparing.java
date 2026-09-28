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
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.InterestPostingView;
import io.dataroots.savingstreak.support.JobRunView;
import io.dataroots.savingstreak.support.SavingsProductView;
import io.dataroots.savingstreak.support.WhatAYearInAProductWouldPayView;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A whole application of its own, on a database nothing has ever been written to, which will
 * project what a typed amount would earn in each product — and whose clock the test that started it
 * may wind a whole year.
 *
 * <p><strong>Its own application for both of the reasons the harnesses beside it give, at
 * once.</strong> The tests here wind twelve months, which nothing sharing a clock could survive,
 * and one of them closes a product to new accounts, which nothing sharing a database could survive
 * — the run's shared file is pinned by two tests to four products at four rates, and a door shut in
 * the middle of it would be shut for everybody afterwards. {@code ACoreSaverSomebodyHolds} reaches
 * for a file of its own for the first reason and {@link ACatalogueSomebodyAdministers} for the
 * second; this needs both.
 *
 * <p><strong>Driven entirely over HTTP</strong>, endpoints and all, so a test using it knows
 * nothing about a table, a column or a service. {@link ASaverChoosingAProduct} does the opening of
 * accounts and the moving of money, reused rather than copied, so that "open an account on a
 * product and put money in it" is one thing in these tests rather than two that could disagree.
 *
 * <p>The clock is wound through the endpoint a trainer would use and the sweep is run by the name a
 * trainer types, because the point of the conformance test is that the figure a customer was shown
 * matches the figure the application actually pays when the year is played out in front of them.
 */
final class AShelfSomebodyIsComparing implements AutoCloseable {

    /** The zone this application counts its days in, which is the zone a period is dated in. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    /** The name a trainer types to run the monthly sweep early, which is what these tests press. */
    static final String THE_INTEREST_SWEEP = "postMonthlyInterest";

    /** The four codes the catalogue seeds, in the order it seeds them. */
    static final String FREE_SAVINGS = "INSTANT";
    static final String THIRTY_TWO_DAY_NOTICE = "NOTICE32";
    static final String THE_CORE_SAVER = "CORE";
    static final String THE_TWELVE_MONTH_FIXED = "FIXED12";

    private final ConfigurableApplicationContext application;
    private final TestRestTemplate http;

    AShelfSomebodyIsComparing(Path databaseFile) {
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
     * What each product a customer may still open would pay on that amount over twelve months, in
     * the catalogue's own order, with a refusal ruled out.
     */
    List<WhatAYearInAProductWouldPayView> whatAYearWouldPayOn(String amount) {
        ResponseEntity<WhatAYearInAProductWouldPayView[]> projected = http.getForEntity(
                "/api/savings-products/projections?amount={amount}",
                WhatAYearInAProductWouldPayView[].class, amount);
        assertThat(projected.getStatusCode())
                .describedAs("projecting twelve months on EUR " + amount)
                .isEqualTo(HttpStatus.OK);
        return List.of(projected.getBody());
    }

    /** The same read with whatever came back, for the test whose whole subject is the refusal. */
    ResponseEntity<JsonNode> tryToProject(String amount) {
        return http.getForEntity("/api/savings-products/projections?amount={amount}",
                JsonNode.class, amount);
    }

    /** The projection asked for with no amount at all, which is a different mistake from a bad one. */
    ResponseEntity<JsonNode> tryToProjectNothingAtAll() {
        return http.getForEntity("/api/savings-products/projections", JsonNode.class);
    }

    /** The whole shelf as a customer reads it, closed products and all. */
    List<SavingsProductView> shelf() {
        return List.of(http.getForObject("/api/savings-products", SavingsProductView[].class));
    }

    /** Shuts a product's door to new accounts, with a refusal ruled out. */
    SavingsProductView close(String code) {
        ResponseEntity<SavingsProductView> shut = http.postForEntity(
                "/api/admin/savings-products/{code}/close", null, SavingsProductView.class, code);
        assertThat(shut.getStatusCode()).describedAs("closing " + code + " to new accounts")
                .isEqualTo(HttpStatus.OK);
        return shut.getBody();
    }

    /** Puts it back on sale, so that one test's closed door is not every later test's. */
    SavingsProductView reopen(String code) {
        ResponseEntity<SavingsProductView> back = http.postForEntity(
                "/api/admin/savings-products/{code}/reopen", null, SavingsProductView.class, code);
        assertThat(back.getStatusCode()).describedAs("putting " + code + " back on sale")
                .isEqualTo(HttpStatus.OK);
        return back.getBody();
    }

    /** A customer of this test's own, with the doors a saver choosing a product presses. */
    ASaverChoosingAProduct aSaver(String whoTheyAre) {
        return new ASaverChoosingAProduct(http, whoTheyAre);
    }

    /**
     * Pays money in and hands back what the deposit says it earned, which
     * {@link ASaverChoosingAProduct#payIn} deliberately throws away.
     *
     * <p>Here rather than there because only these tests want it: the points a deposit earned and
     * the points its first anniversary will pay are the two figures the projection has to agree
     * with, and they are the application's own answers rather than anything a test worked out.
     */
    DepositView payInto(ASaverChoosingAProduct saver, long savingsAccountId, String amount) {
        ResponseEntity<DepositView> paid = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", saver.currentAccountId()),
                DepositView.class, savingsAccountId);
        assertThat(paid.getStatusCode())
                .describedAs("paying EUR " + amount + " into savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.CREATED);
        return paid.getBody();
    }

    /** Every month this account has been judged for, oldest first, through the account's own door. */
    List<InterestPostingView> interestPaidInto(long savingsAccountId) {
        ResponseEntity<InterestPostingView[]> read = http.getForEntity(
                "/api/savings-accounts/{id}/interest", InterestPostingView[].class,
                savingsAccountId);
        assertThat(read.getStatusCode())
                .describedAs("reading the interest paid into savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /** What this savings account holds, which is the amount plus everything it has been paid. */
    BigDecimal balanceOf(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId)
                .moneyBalance();
    }

    /** The day this application thinks it is, read off its own clock rather than the machine's. */
    LocalDate theDateTheClockReads() {
        return LocalDate.ofInstant(
                http.getForObject("/api/dev/clock", ClockView.class).now(), BRUSSELS);
    }

    /**
     * Winds the clock to the day that account's twelfth period ends, which is the first day the
     * sweep can pay for all twelve of them.
     *
     * <p>Counted to the day rather than "three hundred and sixty-five days on", because a period is
     * a calendar month from the day the account was opened and this test has to be right in a leap
     * year. The clock only moves forwards and only in whole days, which is what this arithmetic is
     * for.
     */
    void aWholeYearPassesFrom(LocalDate openedOn) {
        daysPass(ChronoUnit.DAYS.between(theDateTheClockReads(), openedOn.plusMonths(12)));
    }

    /** Moves the clock, through the endpoint a trainer would use. */
    void daysPass(long days) {
        ResponseEntity<ClockView> moved = http.postForEntity("/api/dev/clock/advance",
                Map.of("days", days), ClockView.class);
        assertThat(moved.getStatusCode()).describedAs("moving the clock " + days + " days on")
                .isEqualTo(HttpStatus.OK);
    }

    /** Runs the nightly job by the name a trainer types, with a failure ruled out. */
    JobRunView runJob(String name) {
        ResponseEntity<JobRunView> ran = http.postForEntity("/api/dev/jobs/{name}/run", null,
                JobRunView.class, name);
        assertThat(ran.getStatusCode()).describedAs("running " + name).isEqualTo(HttpStatus.OK);
        return ran.getBody();
    }

    /** The one card for that product, so a test can name what it is asserting about. */
    static WhatAYearInAProductWouldPayView theCardFor(
            List<WhatAYearInAProductWouldPayView> shelf, String code) {
        return shelf.stream()
                .filter(card -> card.product().code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the comparison has no card for " + code + ", and it should have"));
    }

    @Override
    public void close() {
        application.close();
    }
}
