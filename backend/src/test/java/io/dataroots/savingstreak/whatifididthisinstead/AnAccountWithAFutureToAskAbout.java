package io.dataroots.savingstreak.whatifididthisinstead;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.SavingCapacityView;
import io.dataroots.savingstreak.support.SavingRuleView;
import io.dataroots.savingstreak.support.SimulationView;
import io.dataroots.savingstreak.support.TheCurrentAccountView;
import io.dataroots.savingstreak.support.WhatAdoptingChangedView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A savings account with a whole household behind it — money in it, goals on it, a rule standing on
 * it, a salary landing and a bill leaving — and the simulator driven over HTTP the way the frontend
 * drives it.
 *
 * <p><strong>Its own customer, every time</strong>, for the reason {@code AnAccountWithRules} gives:
 * the whole run shares one database, and a snapshot is an assertion about <em>everything</em> on an
 * account. A test that borrowed the seeded Anke's account would be asserting on the goals, rules and
 * deposits of whichever classes happened to run before it. Adding a customer costs one request and
 * buys an account whose entire present is this test's own.
 *
 * <p>Every read here goes to the resource that owns the figure — the goals screen's goals, the
 * capacity screen's capacity, the rules screen's rules, the current account's own screen — because
 * that is exactly what these tests are for: the simulator's present has to be the application's
 * present, and the only honest way to say so is to ask both.
 */
class AnAccountWithAFutureToAskAbout {

    /**
     * Makes each class's contact details different from every other class's without anybody having
     * to keep a list, exactly as the rules fixture does. An address used twice is the duplicate the
     * application refuses, and the second class to run would fail on the first one's customer.
     */
    private static final AtomicLong DISTINCT = new AtomicLong();

    private final TestRestTemplate http;
    private final long customerId;
    private final long savingsAccountId;
    private final long currentAccountId;

    AnAccountWithAFutureToAskAbout(TestRestTemplate http, String purpose) {
        this.http = http;
        this.customerId = aFreshCustomerFor(http, purpose);
        JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class, customerId);
        this.savingsAccountId = firstAccountIn(accounts, "savingsAccounts");
        this.currentAccountId = firstAccountIn(accounts, "currentAccounts");
    }

    long id() {
        return savingsAccountId;
    }

    long customerId() {
        return customerId;
    }

    long currentAccountId() {
        return currentAccountId;
    }

    /**
     * The day the application's clock reads, which is not necessarily today: this fixture stands on
     * the application the whole run shares, and other classes in it wind that clock.
     *
     * <p>Every day this test names is counted from here rather than from {@code LocalDate.now()},
     * which is the difference between a test that is right whenever it runs and one that is right
     * until somebody adds a class ahead of it.
     */
    LocalDate theDateTheClockReads() {
        ClockView read = http.getForObject("/api/dev/clock", ClockView.class);
        return read.now().atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
    }

    /** What this account's futures would be folded from, insisted on. */
    SimulationView simulation() {
        return simulationOf(savingsAccountId);
    }

    /**
     * The same question about any account, so that a test holding two pots can ask about both.
     *
     * <p>The status is insisted on because {@code postForObject} hands back the error body rather
     * than throwing: a simulation that had quietly become a refusal would satisfy every "nothing is
     * there" assertion ever written against it.
     */
    SimulationView simulationOf(long anySavingsAccountId) {
        ResponseEntity<SimulationView> answered = http.postForEntity(
                "/api/savings-accounts/{id}/simulations", Map.of("scenarios", List.of()),
                SimulationView.class, anySavingsAccountId);
        assertThat(answered.getStatusCode())
                .describedAs("asking what savings account " + anySavingsAccountId + " has ahead of it")
                .isEqualTo(HttpStatus.OK);
        return answered.getBody();
    }

    /**
     * The same question with a branch in it: one scenario, folded beside the year already under way,
     * insisted on.
     *
     * <p>The body is the one the frontend posts — a list of scenarios, each a name and a list of
     * changes, every figure and every day as the text a customer typed. Asserting the status is what
     * keeps a branch that had quietly become a refusal from satisfying every "nothing happened"
     * assertion ever written against it.
     */
    SimulationView asking(Map<String, Object> scenario) {
        ResponseEntity<SimulationView> answered = http.postForEntity(
                "/api/savings-accounts/{id}/simulations", Map.of("scenarios", List.of(scenario)),
                SimulationView.class, savingsAccountId);
        assertThat(answered.getStatusCode())
                .describedAs("asking what " + scenario.get("called") + " would do")
                .isEqualTo(HttpStatus.OK);
        return answered.getBody();
    }

    /** The same question answered or refused, for a test about what cannot be asked at all. */
    ResponseEntity<JsonNode> askingFor(Map<String, Object> scenario) {
        return http.postForEntity("/api/savings-accounts/{id}/simulations",
                Map.of("scenarios", List.of(scenario)), JsonNode.class, savingsAccountId);
    }

    /** One branch as a customer types it: what they call it, and the changes it is made of. */
    static Map<String, Object> aScenarioCalled(String called, Map<String, Object> change) {
        return Map.of("called", called, "adjustments", List.of(change));
    }

    /**
     * One branch made of several changes, in the order the customer typed them.
     *
     * <p><strong>A list, and the only several-changes form there is.</strong> Two slices added one
     * of these at the same time under two names — this one and a varargs {@code aScenarioMadeOf} —
     * and the first of them asked that whoever came next keep one. The list is the one kept, for
     * three reasons. It is the shape the request body itself has, so a fixture that takes a list of
     * changes reads like the JSON array it is about to post. It is the shape a test can build in a
     * loop, which is exactly what the test for the cap on how many changes a scenario may carry has
     * to do, and where varargs would have wanted an array cast and a {@code @SafeVarargs} apology
     * for a generic array it never really made. And one name beside {@link #aScenarioCalled(String,
     * Map)} says the two are the same question asked with one change or with several, where two
     * names had a reader learning both to be sure they were not two things.
     *
     * <p>Beside the one-change form rather than in place of it, because a scenario carrying one
     * change is what most of these tests ask about and {@code List.of(oneChange)} reads like an
     * apology for the shape.
     */
    static Map<String, Object> aScenarioCalled(String called, List<Map<String, Object>> changes) {
        return Map.of("called", called, "adjustments", changes);
    }

    /** Another amount put away every week, from a day, as the boxes a customer fills in. */
    static Map<String, Object> anotherEachWeekOf(String amount, Object from) {
        return Map.of("kind", "SAVE_MORE_EACH_WEEK", "amount", amount, "on", String.valueOf(from));
    }

    /** What lands in the everyday account every month, insisted on. */
    void declaresAnIncome(String dayOfMonth, String amount) {
        ResponseEntity<JsonNode> declared = http.exchange(
                "/api/current-accounts/{id}/monthly-income", HttpMethod.PUT,
                new HttpEntity<>(Map.of("dayOfMonth", dayOfMonth, "amount", amount)),
                JsonNode.class, currentAccountId);
        assertThat(declared.getStatusCode())
                .describedAs("declaring an income of " + amount + " on day " + dayOfMonth)
                .isEqualTo(HttpStatus.OK);
    }

    /** And what leaves it every month, insisted on. */
    void declaresABill(String name, String dayOfMonth, String amount) {
        ResponseEntity<JsonNode> declared = http.postForEntity(
                "/api/current-accounts/{id}/bills",
                Map.of("name", name, "dayOfMonth", dayOfMonth, "amount", amount),
                JsonNode.class, currentAccountId);
        assertThat(declared.getStatusCode())
                .describedAs("declaring the bill \"" + name + "\" of " + amount)
                .isEqualTo(HttpStatus.CREATED);
    }

    /** What the holder says they can put away in a week, insisted on. */
    void declaresAWeeklyCapacity(String weeklyCapacity) {
        ResponseEntity<JsonNode> declared = http.exchange(
                "/api/savings-accounts/{id}/saving-capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("weeklyCapacity", weeklyCapacity)), JsonNode.class,
                savingsAccountId);
        assertThat(declared.getStatusCode())
                .describedAs("declaring a weekly capacity of " + weeklyCapacity)
                .isEqualTo(HttpStatus.OK);
    }

    /** Opens a goal on the account, insisted on. */
    GoalView opensAGoal(String name, String target, String deadline) {
        ResponseEntity<GoalView> opened = http.postForEntity(
                "/api/savings-accounts/{id}/goals",
                Map.of("name", name, "target", target, "deadline", deadline),
                GoalView.class, savingsAccountId);
        assertThat(opened.getStatusCode())
                .describedAs("opening the goal \"" + name + "\"")
                .isEqualTo(HttpStatus.CREATED);
        return opened.getBody();
    }

    /** Leaves a rule standing on the account, insisted on. */
    SavingRuleView leavesARuleStanding(Map<String, Object> rule) {
        ResponseEntity<SavingRuleView> left = http.postForEntity(
                "/api/savings-accounts/{id}/saving-rules", rule, SavingRuleView.class,
                savingsAccountId);
        assertThat(left.getStatusCode())
                .describedAs("leaving the rule " + rule + " standing")
                .isEqualTo(HttpStatus.CREATED);
        return left.getBody();
    }

    /** Moves money into savings by hand, which is how this fixture gets deposits to reason about. */
    void depositsByHand(String amount) {
        ResponseEntity<JsonNode> made = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                JsonNode.class, savingsAccountId);
        assertThat(made.getStatusCode())
                .describedAs("depositing " + amount + " out of current account " + currentAccountId)
                .isEqualTo(HttpStatus.CREATED);
    }

    /**
     * Takes money back out of savings, which is the only way on a clock nobody may wind to put a
     * customer below their own high-water mark — the state in which their next deposit earns nothing.
     */
    void withdrawsByHand(String amount) {
        ResponseEntity<JsonNode> taken = http.postForEntity(
                "/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", currentAccountId),
                JsonNode.class, savingsAccountId);
        assertThat(taken.getStatusCode())
                .describedAs("withdrawing " + amount + " into current account " + currentAccountId
                        + ": " + taken.getBody())
                .isEqualTo(HttpStatus.CREATED);
    }

    /** The account's own overview: the balance, the points, the mark and the run of weeks. */
    BalancesView balances() {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    /** The goals as the goals screen reads them. */
    List<GoalView> goals() {
        return Arrays.asList(http.getForObject("/api/savings-accounts/{id}/goals", GoalView[].class,
                savingsAccountId));
    }

    /** The weekly plan as its own screen reads it. */
    SavingCapacityView weeklyCapacity() {
        return http.getForObject("/api/savings-accounts/{id}/saving-capacity",
                SavingCapacityView.class, savingsAccountId);
    }

    /** The rules standing on the account, in the order their holder wrote them. */
    List<SavingRuleView> rules() {
        return Arrays.asList(http.getForObject("/api/savings-accounts/{id}/saving-rules",
                SavingRuleView[].class, savingsAccountId));
    }

    /** The everyday account as its own screen reads it: the balance, the salary and the bills. */
    TheCurrentAccountView currentAccount() {
        return http.getForObject("/api/current-accounts/{id}", TheCurrentAccountView.class,
                currentAccountId);
    }

    private static long aFreshCustomerFor(TestRestTemplate http, String purpose) {
        long distinct = DISTINCT.incrementAndGet();
        String address = "what-if-" + purpose + "-" + distinct + "@example.be";
        ResponseEntity<JsonNode> added = http.postForEntity("/api/customers",
                Map.of("name", "Planner " + distinct + " for " + purpose, "contactDetails", address),
                JsonNode.class);
        assertThat(added.getStatusCode())
                .describedAs("opening the customer whose futures this test asks about")
                .isEqualTo(HttpStatus.CREATED);
        return added.getBody().get("id").asLong();
    }

    private static long firstAccountIn(JsonNode accounts, String kind) {
        return accounts.get(kind).get(0).get("id").asLong();
    }

    /** A goal wanted by another day, as the boxes a customer fills in for that kind of change. */
    static Map<String, Object> movingTheDeadlineOf(long goalId, Object to) {
        return Map.of("kind", "MOVE_A_DEADLINE", "goalId", goalId, "on", String.valueOf(to));
    }

    /**
     * Gives a goal up, insisted on — which is how a test reaches the one state a goal can be in that
     * has no deadline left to move.
     */
    void givesUpOn(long goalId) {
        ResponseEntity<GoalView> given = http.postForEntity(
                "/api/savings-accounts/{id}/goals/{goalId}/abandon", null, GoalView.class,
                savingsAccountId, goalId);
        assertThat(given.getStatusCode())
                .describedAs("giving up on goal " + goalId)
                .isEqualTo(HttpStatus.OK);
    }

    // --- Stopping for a while. ------------------------------------------------------------------
    // Appended at the end of the class deliberately: three slices added a kind of change to this
    // fixture at the same time, and the end of a file is the one place three people can all write
    // without any of them landing in the middle of somebody else's method.

    /**
     * A stop between two days, both of them included, as the boxes a customer fills in.
     *
     * <p>The two days go in {@code on} and {@code until}, which is the one kind of change the second
     * box exists for, and both travel as the text that was typed like every other figure and day in
     * this form.
     */
    static Map<String, Object> aStopFrom(Object firstDay, Object lastDay) {
        return Map.of("kind", "STOP_FOR_A_WHILE", "on", String.valueOf(firstDay),
                "until", String.valueOf(lastDay));
    }

    /**
     * An amount taken back out of savings on a day, as the boxes a customer fills in.
     *
     * <p>The same two boxes another amount each week fills in, which is the whole of why the form
     * has one shape: what a kind makes of them is the kind's own business.
     */
    static Map<String, Object> takingOutOn(String amount, Object day) {
        return Map.of("kind", "TAKE_MONEY_OUT", "amount", amount, "on", String.valueOf(day));
    }

    // --- Several futures in one asking. ---------------------------------------------------------

    /**
     * Several branches in one request, folded beside the year already under way, insisted on.
     *
     * <p>The body the frontend posts when a customer has typed more than one column: one
     * {@code scenarios} array, the branches in the order they were typed, and every figure and every
     * day still the text that was typed. Beside {@link #asking(Map)} rather than in place of it,
     * because most of these tests are about one branch and a list of one would read like an apology.
     *
     * <p>The status is insisted on for the reason the single-branch form gives: a question that had
     * quietly become a refusal would satisfy every assertion ever written about what it answered.
     */
    SimulationView asking(List<Map<String, Object>> scenarios) {
        ResponseEntity<SimulationView> answered = http.postForEntity(
                "/api/savings-accounts/{id}/simulations", Map.of("scenarios", scenarios),
                SimulationView.class, savingsAccountId);
        assertThat(answered.getStatusCode())
                .describedAs("asking about " + scenarios.size() + " futures at once: " + scenarios)
                .isEqualTo(HttpStatus.OK);
        return answered.getBody();
    }

    /**
     * The same question answered or refused, for a test about how much one asking may carry.
     *
     * <p>Takes the scenarios as a list rather than as a body so that a test refusing a fifth column
     * says {@code fiveOfThem} and not {@code Map.of("scenarios", fiveOfThem)} — the caps are about
     * what a customer typed, and the request shape around it is this fixture's business.
     */
    ResponseEntity<JsonNode> askingFor(List<Map<String, Object>> scenarios) {
        return http.postForEntity("/api/savings-accounts/{id}/simulations",
                Map.of("scenarios", scenarios), JsonNode.class, savingsAccountId);
    }

    // --- Turning the one I like into a plan. ----------------------------------------------------

    /**
     * Presses adopt on one branch, insisted on: the branch becomes the plan, and the answer says
     * what changed.
     *
     * <p>The body is one scenario in exactly the shape a column of the comparison is asked about in,
     * which is the whole point of the press — the branch the customer read is the branch they adopt,
     * so the fixture builds it with the same {@code aScenarioCalled} helpers the questions are built
     * with and nothing about it is adoption's own.
     *
     * <p>The status is insisted on for the reason every other insisting form here gives: an adoption
     * that had quietly become a refusal would satisfy every "nothing was applied" assertion ever
     * written against it, which on this endpoint is the assertion that matters most.
     */
    WhatAdoptingChangedView adopting(Map<String, Object> scenario) {
        ResponseEntity<WhatAdoptingChangedView> adopted = http.postForEntity(
                "/api/savings-accounts/{id}/simulations/adopt", scenario,
                WhatAdoptingChangedView.class, savingsAccountId);
        assertThat(adopted.getStatusCode())
                .describedAs("adopting \"" + scenario.get("called") + "\": " + adopted.getBody())
                .isEqualTo(HttpStatus.OK);
        return adopted.getBody();
    }

    /**
     * The same press answered or refused, for a test about a plan this application will not write.
     *
     * <p>Hands back the whole response rather than the body, because what a refusal says — and which
     * part of the branch it names beside the sentence — is the thing under test.
     */
    ResponseEntity<JsonNode> adoptingFor(Map<String, Object> scenario) {
        return http.postForEntity("/api/savings-accounts/{id}/simulations/adopt", scenario,
                JsonNode.class, savingsAccountId);
    }

    /**
     * The most a year of this bank's interest could add to a figure, for the assertions about a
     * branch's closing euros.
     *
     * <p><strong>Why these tests stopped naming one figure.</strong> Until the fold learnt the
     * product it is projecting it paid no interest at all, so a branch that saved eight hundred
     * euros and touched nothing closed on eight hundred euros and the assertion could say so to the
     * cent. It closes on a little more now, because every savings account in this application earns
     * something every month and the fold is finally saying so — and exactly how much depends on
     * which day of which month the clock happens to read, so there is no one figure to write down.
     *
     * <p>What is still worth asserting to the cent is the <em>euros the customer put away</em>, so
     * these tests bound the answer rather than loosen it: at least what was saved, and at most a
     * twentieth more. The best rate this bank sells is 2.40% a year and the one these accounts are
     * on is 0.50%, so a branch that had quietly doubled a Friday, missed one, or paid a withdrawal
     * back in would be caught by that ceiling as surely as by a single figure — and a branch that
     * paid no interest at all would be caught by the floor, which is the thing this ticket added.
     */
    static BigDecimal andAtMostAYearsInterestOn(String euros) {
        return new BigDecimal(euros).multiply(new BigDecimal("1.05"));
    }

    /** The same ceiling over a figure a test has already worked out. */
    static BigDecimal andAtMostAYearsInterestOn(BigDecimal euros) {
        return euros.multiply(new BigDecimal("1.05"));
    }
}
