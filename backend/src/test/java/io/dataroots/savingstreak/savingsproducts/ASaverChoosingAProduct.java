package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AMoveView;
import io.dataroots.savingstreak.support.AllocationsView;
import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.MoneyMovementView;
import io.dataroots.savingstreak.support.SavingsAccountOnTheOverviewView;
import io.dataroots.savingstreak.support.SavingsProductView;
import io.dataroots.savingstreak.support.WhatCanLeaveTodayView;
import io.dataroots.savingstreak.support.WhatMovingWouldCostView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer of one test's own, opened over HTTP, with the doors a saver choosing a product presses:
 * open another savings account on a named product, read what it is living under, put money in, take
 * it back out, and close it.
 *
 * <p><strong>A customer of their own rather than one of the seeded pair.</strong> Every test in this
 * run shares one database, and the two accounts named in the seed are read, deposited into and
 * counted by a hundred other classes. Opening and closing accounts changes how many a customer
 * holds, which is exactly the sort of thing another test would notice; a customer nobody else has
 * heard of cannot be noticed by anybody.
 *
 * <p><strong>Driven entirely over HTTP</strong>, so nothing here knows about a table, a column or a
 * service. The refusals are the one exception to the "assert the status" rule below — they come back
 * raw, because the tests whose subject is a refusal need the status and the sentence rather than a
 * shape that could not be read.
 *
 * <p>Shared between the three tests about opening and closing rather than copied into each, for the
 * reason the shared views give: three copies of "add a customer and open an account on a product"
 * are three chances to disagree about what doing that is.
 */
final class ASaverChoosingAProduct {

    /** The zone this application counts its days in, which is the zone an agreement is dated in. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    /**
     * What makes each of these customers' email addresses their own.
     *
     * <p>A counter rather than the name they were given, because two tests asking for "somebody
     * closing an account" would otherwise be one customer the second time and a refusal the third —
     * and the run shares a database, so the second time is any time this class has been used before.
     */
    private static final AtomicInteger HOW_MANY_HAVE_BEEN_OPENED = new AtomicInteger();

    private final TestRestTemplate http;
    private final long customerId;
    private final long currentAccountId;
    private final long theAccountTheyWereOpenedWith;

    ASaverChoosingAProduct(TestRestTemplate http, String whoTheyAre) {
        this.http = http;
        String address = "product.saver." + HOW_MANY_HAVE_BEEN_OPENED.incrementAndGet()
                + "@example.be";
        ResponseEntity<CustomerView> added = http.postForEntity("/api/customers",
                Map.of("name", whoTheyAre, "contactDetails", address), CustomerView.class);
        assertThat(added.getStatusCode()).as("opening %s", whoTheyAre).isEqualTo(HttpStatus.CREATED);
        this.customerId = added.getBody().id();
        OverviewView theirs = overview();
        this.currentAccountId = theirs.currentAccounts().get(0).id();
        this.theAccountTheyWereOpenedWith = theirs.savingsAccounts().get(0).id();
    }

    long customerId() {
        return customerId;
    }

    long currentAccountId() {
        return currentAccountId;
    }

    /** The savings account they were opened with, which nobody chose a product for. */
    long theAccountTheyWereOpenedWith() {
        return theAccountTheyWereOpenedWith;
    }

    /** Opens another savings account on that product, with a refusal ruled out. */
    SavingsAccountOnTheOverviewView open(String product) {
        ResponseEntity<SavingsAccountOnTheOverviewView> opened = tryToOpen(product,
                SavingsAccountOnTheOverviewView.class);
        assertThat(opened.getStatusCode()).as("opening a savings account on %s", product)
                .isEqualTo(HttpStatus.CREATED);
        return opened.getBody();
    }

    /** The same request with whatever came back, for the tests whose subject is the refusal. */
    ResponseEntity<JsonNode> tryToOpen(Object product) {
        return tryToOpen(product, JsonNode.class);
    }

    private <T> ResponseEntity<T> tryToOpen(Object product, Class<T> shape) {
        return http.postForEntity("/api/customers/{id}/savings-accounts",
                product == null ? Map.of() : Map.of("product", product), shape, customerId);
    }

    /** Closes one of their accounts, with a refusal ruled out. */
    AnAgreementView close(long savingsAccountId) {
        ResponseEntity<AnAgreementView> closed = http.postForEntity(
                "/api/savings-accounts/{id}/close", null, AnAgreementView.class, savingsAccountId);
        assertThat(closed.getStatusCode()).as("closing savings account %d", savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return closed.getBody();
    }

    /** The same press with whatever came back, for the tests whose subject is the refusal. */
    ResponseEntity<JsonNode> tryToClose(long savingsAccountId) {
        return http.postForEntity("/api/savings-accounts/{id}/close", null, JsonNode.class,
                savingsAccountId);
    }

    /** What one of their savings accounts is living under, off the account's own page. */
    AnAgreementView agreementOn(long savingsAccountId) {
        return account(savingsAccountId).agreement();
    }

    BalancesView account(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    /** Every savings account they hold, as their overview lists them. */
    List<SavingsAccountOnTheOverviewView> savingsAccounts() {
        return overview().savingsAccounts();
    }

    /** Pays money in, with a refusal ruled out. */
    void payIn(long savingsAccountId, String amount) {
        ResponseEntity<JsonNode> paid = http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                JsonNode.class, savingsAccountId);
        assertThat(paid.getStatusCode()).as("paying EUR %s into savings account %d", amount,
                savingsAccountId).isEqualTo(HttpStatus.CREATED);
    }

    /** The same payment with whatever came back, for the tests whose subject is the refusal. */
    ResponseEntity<JsonNode> tryToPayIn(long savingsAccountId, String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                JsonNode.class, savingsAccountId);
    }

    /** Takes money back out, with a refusal ruled out. */
    void takeOut(long savingsAccountId, String amount) {
        ResponseEntity<JsonNode> taken = http.postForEntity("/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", currentAccountId),
                JsonNode.class, savingsAccountId);
        assertThat(taken.getStatusCode()).as("taking EUR %s out of savings account %d", amount,
                savingsAccountId).isEqualTo(HttpStatus.CREATED);
    }

    /** The same withdrawal with whatever came back, for the tests whose subject is the refusal. */
    ResponseEntity<JsonNode> tryToTakeOut(long savingsAccountId, String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", currentAccountId),
                JsonNode.class, savingsAccountId);
    }

    /**
     * What their current account holds, which is what a refused deposit has to have left alone.
     *
     * <p>Read off the overview rather than remembered, because the figure a test wants is the one
     * the application has now and not the one the test last put there.
     */
    BigDecimal whatTheirCurrentAccountHolds() {
        return overview().currentAccounts().get(0).balance();
    }

    /** Whatever the application answers at that path under this account, read as it comes. */
    ResponseEntity<JsonNode> read(String path, long savingsAccountId) {
        return http.getForEntity(path, JsonNode.class, savingsAccountId);
    }

    /** Which version that product is selling today, asked of the catalogue rather than written down. */
    int whatIsBeingSoldToday(String product) {
        return http.getForObject("/api/savings-products/{code}", SavingsProductView.class, product)
                .currentTerms().version();
    }

    /** The day this application thinks it is, read off its own clock rather than the machine's. */
    LocalDate theDayTheApplicationIsStandingOn() {
        return LocalDate.ofInstant(http.getForObject("/api/dev/clock", ClockView.class).now(),
                BRUSSELS);
    }

    /**
     * Moves money from one of their savings accounts to another of their own, refusal ruled out.
     *
     * <p>One press, which is the whole of the operation under test: not a withdrawal that happens
     * to be followed by a deposit, which is what this door exists to stop being the only way.
     */
    AMoveView move(long fromSavingsAccountId, long toSavingsAccountId, String amount) {
        ResponseEntity<AMoveView> moved = tryToMove(fromSavingsAccountId, toSavingsAccountId,
                amount, AMoveView.class);
        assertThat(moved.getStatusCode()).as("moving EUR %s from savings account %d to %d", amount,
                fromSavingsAccountId, toSavingsAccountId).isEqualTo(HttpStatus.CREATED);
        return moved.getBody();
    }

    /** The same press with whatever came back, for the tests whose subject is the refusal. */
    ResponseEntity<JsonNode> tryToMove(long fromSavingsAccountId, long toSavingsAccountId,
                                       String amount) {
        return tryToMove(fromSavingsAccountId, toSavingsAccountId, amount, JsonNode.class);
    }

    private <T> ResponseEntity<T> tryToMove(long fromSavingsAccountId, long toSavingsAccountId,
                                            String amount, Class<T> shape) {
        return http.postForEntity("/api/savings-accounts/{id}/moves",
                Map.of("amount", amount, "toSavingsAccountId", toSavingsAccountId), shape,
                fromSavingsAccountId);
    }

    /** What the application says moving would cost, read before anything has moved. */
    WhatMovingWouldCostView whatMovingWouldCost(long fromSavingsAccountId, long toSavingsAccountId,
                                                String amount) {
        ResponseEntity<WhatMovingWouldCostView> cost = http.postForEntity(
                "/api/savings-accounts/{id}/moves/preview",
                Map.of("amount", amount, "toSavingsAccountId", toSavingsAccountId),
                WhatMovingWouldCostView.class, fromSavingsAccountId);
        assertThat(cost.getStatusCode()).as("asking what moving EUR %s would cost", amount)
                .isEqualTo(HttpStatus.OK);
        return cost.getBody();
    }

    /** The same question with whatever came back, for the tests whose subject is the refusal. */
    ResponseEntity<JsonNode> tryToAskWhatMovingWouldCost(long fromSavingsAccountId,
                                                         long toSavingsAccountId, String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/moves/preview",
                Map.of("amount", amount, "toSavingsAccountId", toSavingsAccountId), JsonNode.class,
                fromSavingsAccountId);
    }

    /**
     * Every movement in or out of this customer's savings accounts, newest first — the record of
     * money that moved, which is where a move has to appear as one entry rather than two.
     */
    List<MoneyMovementView> theMoneyThatMoved() {
        return http.exchange("/api/customers/{id}/money-movements", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<MoneyMovementView>>() { }, customerId)
                .getBody();
    }

    /** Opens a savings goal on one of their accounts and answers its identifier. */
    long openAGoal(long savingsAccountId, String name, String target) {
        ResponseEntity<GoalView> opened = http.postForEntity(
                "/api/savings-accounts/{id}/goals", Map.of("name", name, "target", target),
                GoalView.class, savingsAccountId);
        assertThat(opened.getStatusCode()).as("opening the goal %s", name)
                .isEqualTo(HttpStatus.CREATED);
        return opened.getBody().id();
    }

    /** Puts money towards a goal out of what no goal has claimed, refusal ruled out. */
    void putTowards(long savingsAccountId, long goalId, String amount) {
        ResponseEntity<JsonNode> moved = http.postForEntity(
                "/api/savings-accounts/{id}/goals/{goalId}/allocations",
                Map.of("amount", amount, "direction", "INTO_THE_GOAL"), JsonNode.class,
                savingsAccountId, goalId);
        assertThat(moved.getStatusCode()).as("putting EUR %s towards goal %d", amount, goalId)
                .isEqualTo(HttpStatus.OK);
    }

    /** What the account holds and how much of it its goals have claimed. */
    AllocationsView allocationsOn(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}/goals/allocations",
                AllocationsView.class, savingsAccountId);
    }

    /** How much of what that account holds its agreement would let leave today, and why less. */
    WhatCanLeaveTodayView whatCanLeaveToday(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}/free-to-take-today",
                WhatCanLeaveTodayView.class, savingsAccountId);
    }

    /** The sentence the domain wrote, carried into the problem detail untouched. */
    static String reasonGivenBy(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody()).as("the problem detail").isNotNull();
        return response.getBody().path("detail").asText();
    }

    private OverviewView overview() {
        return http.getForObject("/api/customers/{id}/accounts", OverviewView.class, customerId);
    }

    /**
     * Only the two lists, because that is all these tests read off the overview. The nine figures
     * beside them belong to the customer and have their own tests; naming them here would be a
     * second copy of a shape that already has one.
     */
    private record OverviewView(List<CurrentAccountView> currentAccounts,
                                List<SavingsAccountOnTheOverviewView> savingsAccounts) {
    }

    private record CurrentAccountView(Long id, String iban, BigDecimal balance) {
    }

    private record CustomerView(Long id, String name, String contactDetails) {
    }
}
