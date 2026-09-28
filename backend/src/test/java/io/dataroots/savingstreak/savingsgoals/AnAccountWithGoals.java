package io.dataroots.savingstreak.savingsgoals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import java.math.BigDecimal;

import io.dataroots.savingstreak.support.AllocationsView;
import io.dataroots.savingstreak.support.AppliedReallocationView;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.GoalMoveView;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.SavingCapacityView;
import io.dataroots.savingstreak.support.SuggestedReallocationView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A savings account nobody else's goals are on, and the goal endpoints driven over HTTP the way the
 * frontend drives them.
 *
 * <p><strong>Its own customer, every time.</strong> The whole run shares one database, and goals are
 * the one thing in this feature that accumulate on an account without anything ever clearing them:
 * a class that saved towards the seeded Anke's account would be asserting about a rank order that
 * the previous class's goals are also standing in. Adding a customer costs one request and buys an
 * account whose whole goal list is this test's.
 *
 * <p>Every refusal is available twice: once as a method that insists on success and hands back the
 * goal, and once as a {@code try...} method that hands back the whole response. A test asserting
 * that something was refused needs the status and the words; a test setting up three goals to
 * reorder needs neither, and should fail on the spot if one of them did not open.
 *
 * <p>The day comes off the application's clock rather than off the machine's, because other tests in
 * this run wind it forward and "already in the past" is measured against what the application thinks
 * today is.
 */
class AnAccountWithGoals {

    /**
     * Makes each class's contact details different from every other class's without anybody having
     * to keep a list. An address used twice is the duplicate the application refuses, and the second
     * class to run would fail on the first one's customer.
     */
    private static final AtomicLong DISTINCT = new AtomicLong();

    private final TestRestTemplate http;
    private final long savingsAccountId;

    /** Where money paid into the savings account comes from, so a test can give it a balance. */
    private final long currentAccountId;

    /** Whose the two accounts are, for reading the current account's balance back off the directory. */
    private final long customerId;

    AnAccountWithGoals(TestRestTemplate http, String purpose) {
        this.http = http;
        this.customerId = aFreshCustomerFor(purpose);
        JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class, customerId);
        this.savingsAccountId = firstAccountIn(accounts, "savingsAccounts");
        this.currentAccountId = firstAccountIn(accounts, "currentAccounts");
    }

    long id() {
        return savingsAccountId;
    }

    /**
     * Pays money into the savings account, so that there is a balance for the goals to claim. The
     * customer is this class's own and starts with EUR 1500.00 to spend, so nothing another test did
     * can change what this one has to allocate.
     */
    void savesUp(String amount) {
        ResponseEntity<JsonNode> paidIn = http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                JsonNode.class, savingsAccountId);
        assertThat(paidIn.getStatusCode())
                .describedAs("paying in the " + amount + " this test is about to allocate")
                .isEqualTo(HttpStatus.CREATED);
    }

    /**
     * What the savings account itself says it holds, read off the account overview rather than off
     * the goals. It is the figure the deposits module derives, and the whole claim of this feature is
     * that moving money between goals never changes it — a test that only read the balance the goals
     * report back could not tell a balance that did not move from a balance the goals module copied.
     */
    BigDecimal savingsBalance() {
        return savings().moneyBalance();
    }

    /** The whole account overview, for a test watching a figure beside the balance — points, say. */
    BalancesView savings() {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    /** What the current account the money came from and goes back to is holding right now. */
    BigDecimal currentAccountBalance() {
        JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class, customerId);
        for (JsonNode account : accounts.get("currentAccounts")) {
            if (account.get("id").asLong() == currentAccountId) {
                return account.get("balance").decimalValue();
            }
        }
        throw new AssertionError("this test's own current account " + currentAccountId + " is gone");
    }

    /**
     * Takes money back out of the savings account, to the current account it came from, and insists
     * it was allowed. A test asserting on what a withdrawal left behind should fail on the spot if
     * it was refused, rather than two assertions later on a balance that never moved.
     */
    void withdraw(String amount) {
        ResponseEntity<JsonNode> taken = tryToWithdraw(amount);
        assertThat(taken.getStatusCode())
                .describedAs("withdrawing " + amount + ": " + taken.getBody())
                .isEqualTo(HttpStatus.CREATED);
    }

    /** A withdrawal exactly as somebody sent it, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToWithdraw(String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", currentAccountId),
                JsonNode.class, savingsAccountId);
    }

    /** What this account's holder has said they can put away in a week, or that they have not. */
    SavingCapacityView savingCapacity() {
        return savingCapacityOf(savingsAccountId, http);
    }

    /**
     * Declares the weekly capacity and insists it was accepted, handing back what the account then
     * reports. A test asserting on what a figure did should fail on the spot if the figure was
     * refused, rather than an assertion later on a capacity that was never declared.
     */
    SavingCapacityView canSave(String weeklyCapacity) {
        return canSaveOn(savingsAccountId, weeklyCapacity, http);
    }

    /** A capacity exactly as somebody typed it, for the figures this feature refuses. */
    ResponseEntity<JsonNode> tryToDeclareCapacity(String weeklyCapacity) {
        Map<String, Object> body = new HashMap<>();
        body.put("weeklyCapacity", weeklyCapacity);
        return http.exchange("/api/savings-accounts/{id}/saving-capacity", HttpMethod.PUT,
                new HttpEntity<>(body), JsonNode.class, savingsAccountId);
    }

    /**
     * Declares a weekly capacity on any savings account, named by identifier, and insists it was
     * accepted.
     *
     * <p>Static and taking the account for the reason {@link #savingCapacityOf} is: the test that
     * asserts a capacity is per account works on two accounts one seeded customer holds, neither of
     * which this class opened.
     */
    static SavingCapacityView canSaveOn(long savingsAccountId, String weeklyCapacity,
                                        TestRestTemplate http) {
        ResponseEntity<SavingCapacityView> declared = http.exchange(
                "/api/savings-accounts/{id}/saving-capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("weeklyCapacity", weeklyCapacity)), SavingCapacityView.class,
                savingsAccountId);
        assertThat(declared.getStatusCode())
                .describedAs("declaring a weekly saving capacity of " + weeklyCapacity
                        + " on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return declared.getBody();
    }

    /**
     * What any savings account says about its weekly capacity, named by identifier.
     *
     * <p>Static and taking the account, because the one thing a capacity has to be is per account:
     * the test that asserts it reads a second account this class did not open — one the same
     * customer holds — and a method bound to this instance's account could not ask about it.
     */
    static SavingCapacityView savingCapacityOf(long savingsAccountId, TestRestTemplate http) {
        ResponseEntity<SavingCapacityView> read = http.getForEntity(
                "/api/savings-accounts/{id}/saving-capacity", SavingCapacityView.class, savingsAccountId);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** What the account holds, what its goals have claimed of it, and what no goal has claimed. */
    AllocationsView allocations() {
        ResponseEntity<AllocationsView> read = http.getForEntity(
                "/api/savings-accounts/{id}/goals/allocations", AllocationsView.class, savingsAccountId);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** Moves money from what no goal has claimed into this goal. */
    AllocationsView putTowards(long goalId, String amount) {
        return allocated(tryToPutTowards(goalId, amount), "putting " + amount + " towards goal " + goalId);
    }

    ResponseEntity<JsonNode> tryToPutTowards(long goalId, String amount) {
        return tryToMove(goalId, amount, "INTO_THE_GOAL", null);
    }

    /** Moves money out of this goal, back to what no goal has claimed. */
    AllocationsView free(long goalId, String amount) {
        return allocated(tryToFree(goalId, amount), "freeing " + amount + " from goal " + goalId);
    }

    ResponseEntity<JsonNode> tryToFree(long goalId, String amount) {
        return tryToMove(goalId, amount, "OUT_OF_THE_GOAL", null);
    }

    /** Moves money straight from one goal to another, without it ever being spare in between. */
    AllocationsView moveBetween(long outOfGoalId, long intoGoalId, String amount) {
        return allocated(tryToMoveBetween(outOfGoalId, intoGoalId, amount),
                "moving " + amount + " out of goal " + outOfGoalId + " into goal " + intoGoalId);
    }

    ResponseEntity<JsonNode> tryToMoveBetween(long outOfGoalId, long intoGoalId, String amount) {
        return tryToMove(intoGoalId, amount, "INTO_THE_GOAL", outOfGoalId);
    }

    /** A move exactly as somebody sent it, for the requests refused for how they read. */
    ResponseEntity<JsonNode> tryToMove(long goalId, String amount, String direction, Long otherGoalId) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("direction", direction);
        body.put("otherGoalId", otherGoalId);
        return http.postForEntity("/api/savings-accounts/{id}/goals/{goalId}/allocations", body,
                JsonNode.class, savingsAccountId, goalId);
    }

    /**
     * What the account says is worth moving between its goals right now — or that there is nothing
     * worth suggesting, which is a real answer with a sentence behind it.
     *
     * <p>Insisting on a 200 rather than handing back the response, because reading a suggestion is
     * never refused for anything a test here sets up: the account is this test's own and the read
     * takes no figures at all. The one refusal it has — an account nobody has heard of — is asked for
     * by identifier in the test that is about it.
     */
    SuggestedReallocationView suggestedReallocation() {
        ResponseEntity<SuggestedReallocationView> read = http.getForEntity(
                "/api/savings-accounts/{id}/goals/suggested-reallocation",
                SuggestedReallocationView.class, savingsAccountId);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * Takes the advice, and hands back what was applied and what the account's money looks like
     * afterwards.
     *
     * <p>Nothing is sent with it, deliberately: what is applied is worked out at the moment of
     * acceptance rather than handed back from an earlier read, so there is no plan for a test to post.
     */
    AppliedReallocationView acceptTheSuggestedReallocation() {
        ResponseEntity<AppliedReallocationView> accepted = http.postForEntity(
                "/api/savings-accounts/{id}/goals/suggested-reallocation", null,
                AppliedReallocationView.class, savingsAccountId);
        assertThat(accepted.getStatusCode())
                .describedAs("accepting whatever is worth suggesting on savings account "
                        + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return accepted.getBody();
    }

    /** The moves that made up one goal's allocation, newest first. */
    List<GoalMoveView> historyOf(long goalId) {
        ResponseEntity<GoalMoveView[]> read = http.getForEntity(
                "/api/savings-accounts/{id}/goals/{goalId}/allocations", GoalMoveView[].class,
                savingsAccountId, goalId);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return Arrays.asList(read.getBody());
    }

    ResponseEntity<JsonNode> tryToReadHistoryOf(long goalId) {
        return http.getForEntity("/api/savings-accounts/{id}/goals/{goalId}/allocations",
                JsonNode.class, savingsAccountId, goalId);
    }

    /**
     * Insists the move was made and hands back what the account's money looks like afterwards. A
     * test setting up an allocation to assert something else about should fail on the spot if the
     * set-up was refused, rather than three assertions later on a figure that never moved.
     */
    private AllocationsView allocated(ResponseEntity<JsonNode> response, String whatWasAsked) {
        assertThat(response.getStatusCode())
                .describedAs(whatWasAsked + ": " + response.getBody())
                .isEqualTo(HttpStatus.OK);
        return allocations();
    }

    /** What day the application's clock reads, in the zone every day-shaped answer it gives is in. */
    LocalDate today() {
        return http.getForObject("/api/dev/clock", ClockView.class)
                .now()
                .atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                .toLocalDate();
    }

    GoalView add(String name, String target, LocalDate deadline) {
        ResponseEntity<GoalView> opened = http.postForEntity(
                "/api/savings-accounts/{id}/goals", bodyOf(name, target, deadline),
                GoalView.class, savingsAccountId);
        assertThat(opened.getStatusCode())
                .describedAs("opening the goal \"" + name + "\" this test is about to assert on")
                .isEqualTo(HttpStatus.CREATED);
        return opened.getBody();
    }

    ResponseEntity<JsonNode> tryToAdd(String name, String target, LocalDate deadline) {
        return http.postForEntity("/api/savings-accounts/{id}/goals", bodyOf(name, target, deadline),
                JsonNode.class, savingsAccountId);
    }

    /** A target exactly as somebody typed it, for the figures that are refused for how they read. */
    ResponseEntity<JsonNode> tryToAddWithTargetAsTyped(String name, String target) {
        return tryToAdd(name, target, null);
    }

    List<GoalView> goals() {
        return goalsAt("/api/savings-accounts/{id}/goals");
    }

    List<GoalView> abandonedGoals() {
        return goalsAt("/api/savings-accounts/{id}/goals/abandoned");
    }

    GoalView goal(long goalId) {
        ResponseEntity<GoalView> read = http.getForEntity(
                "/api/savings-accounts/{id}/goals/{goalId}", GoalView.class, savingsAccountId, goalId);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    ResponseEntity<JsonNode> tryToRead(long goalId) {
        return http.getForEntity("/api/savings-accounts/{id}/goals/{goalId}", JsonNode.class,
                savingsAccountId, goalId);
    }

    GoalView change(long goalId, Map<String, Object> whatToChange) {
        ResponseEntity<GoalView> changed = http.exchange(
                "/api/savings-accounts/{id}/goals/{goalId}", HttpMethod.PATCH,
                new HttpEntity<>(whatToChange), GoalView.class, savingsAccountId, goalId);
        assertThat(changed.getStatusCode())
                .describedAs("changing " + whatToChange + " on goal " + goalId)
                .isEqualTo(HttpStatus.OK);
        return changed.getBody();
    }

    ResponseEntity<JsonNode> tryToChange(long goalId, Map<String, Object> whatToChange) {
        return http.exchange("/api/savings-accounts/{id}/goals/{goalId}", HttpMethod.PATCH,
                new HttpEntity<>(whatToChange), JsonNode.class, savingsAccountId, goalId);
    }

    List<GoalView> reorder(List<Long> goalIdsInOrder) {
        ResponseEntity<GoalView[]> reordered = http.exchange(
                "/api/savings-accounts/{id}/goals/order", HttpMethod.PUT,
                new HttpEntity<>(Map.of("goalIds", goalIdsInOrder)), GoalView[].class, savingsAccountId);
        assertThat(reordered.getStatusCode())
                .describedAs("putting the goals in the order " + goalIdsInOrder)
                .isEqualTo(HttpStatus.OK);
        return Arrays.asList(reordered.getBody());
    }

    ResponseEntity<JsonNode> tryToReorder(List<Long> goalIdsInOrder) {
        return http.exchange("/api/savings-accounts/{id}/goals/order", HttpMethod.PUT,
                new HttpEntity<>(Map.of("goalIds", goalIdsInOrder)), JsonNode.class, savingsAccountId);
    }

    /**
     * Pins a weekly amount to a goal and insists it was accepted, handing back the goal as the plan
     * then reads it. A test asserting what a pin did to the goals under it should fail on the spot
     * if the pin itself was refused.
     */
    GoalView pin(long goalId, String weeklyAmount) {
        ResponseEntity<GoalView> pinned = http.exchange(
                "/api/savings-accounts/{id}/goals/{goalId}/weekly-amount", HttpMethod.PUT,
                new HttpEntity<>(Map.of("weeklyAmount", weeklyAmount)), GoalView.class,
                savingsAccountId, goalId);
        assertThat(pinned.getStatusCode())
                .describedAs("pinning " + weeklyAmount + " a week to goal " + goalId + ": "
                        + pinned.getBody())
                .isEqualTo(HttpStatus.OK);
        return pinned.getBody();
    }

    /** A pin exactly as somebody typed it, for the figures this feature refuses. */
    ResponseEntity<JsonNode> tryToPin(long goalId, String weeklyAmount) {
        Map<String, Object> body = new HashMap<>();
        body.put("weeklyAmount", weeklyAmount);
        return http.exchange("/api/savings-accounts/{id}/goals/{goalId}/weekly-amount",
                HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class, savingsAccountId, goalId);
    }

    /** Takes the pin off a goal and insists it was allowed, handing back the goal as it now reads. */
    GoalView unpin(long goalId) {
        ResponseEntity<GoalView> unpinned = http.exchange(
                "/api/savings-accounts/{id}/goals/{goalId}/weekly-amount", HttpMethod.DELETE, null,
                GoalView.class, savingsAccountId, goalId);
        assertThat(unpinned.getStatusCode())
                .describedAs("taking the pin off goal " + goalId + ": " + unpinned.getBody())
                .isEqualTo(HttpStatus.OK);
        return unpinned.getBody();
    }

    ResponseEntity<JsonNode> tryToUnpin(long goalId) {
        return http.exchange("/api/savings-accounts/{id}/goals/{goalId}/weekly-amount",
                HttpMethod.DELETE, null, JsonNode.class, savingsAccountId, goalId);
    }

    GoalView abandon(long goalId) {
        ResponseEntity<GoalView> abandoned = http.postForEntity(
                "/api/savings-accounts/{id}/goals/{goalId}/abandon", null, GoalView.class,
                savingsAccountId, goalId);
        assertThat(abandoned.getStatusCode())
                .describedAs("giving up on goal " + goalId)
                .isEqualTo(HttpStatus.OK);
        return abandoned.getBody();
    }

    ResponseEntity<JsonNode> tryToAbandon(long goalId) {
        return http.postForEntity("/api/savings-accounts/{id}/goals/{goalId}/abandon", null,
                JsonNode.class, savingsAccountId, goalId);
    }

    /** The reason a refusal gave, which is the field every error in this application carries it in. */
    static String reasonGivenBy(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody())
                .describedAs("a refusal with no body at all says nothing to the person who caused it")
                .isNotNull();
        return response.getBody().path("detail").asText();
    }

    static List<Long> idsOf(List<GoalView> goals) {
        return goals.stream().map(GoalView::id).toList();
    }

    private List<GoalView> goalsAt(String path) {
        ResponseEntity<GoalView[]> listed = http.getForEntity(path, GoalView[].class, savingsAccountId);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new ArrayList<>(Arrays.asList(listed.getBody()));
    }

    /**
     * A body that can carry a null deadline, which {@code Map.of} cannot hold. A goal with no day is
     * the ordinary case, so the test has to be able to send one.
     */
    private static Map<String, Object> bodyOf(String name, String target, LocalDate deadline) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("target", target);
        body.put("deadline", deadline == null ? null : deadline.toString());
        return body;
    }

    /**
     * Opens a customer of this class's own — one account of each kind, no deposits, no goals and
     * nothing any other test has touched — and answers with their identifier.
     */
    private long aFreshCustomerFor(String purpose) {
        // The name is made distinct as well as the address, and that is not belt and braces. Tests
        // elsewhere in this run ask for "an identifier no savings account has" by walking the
        // directory and looking each customer up *by name*, which answers with the first customer
        // wearing it; a dozen customers all called "Saver for refused" would hide eleven accounts
        // from that walk, and the identifier it called unused would be one this class had just been
        // given. That is a failure handed to whichever test ran second, about a row it never created.
        long distinct = DISTINCT.incrementAndGet();
        String address = "goals-" + purpose + "-" + distinct + "@example.be";
        ResponseEntity<JsonNode> added = http.postForEntity("/api/customers",
                Map.of("name", "Saver " + distinct + " for " + purpose, "contactDetails", address),
                JsonNode.class);
        assertThat(added.getStatusCode())
                .describedAs("opening the customer whose account this test's goals go on")
                .isEqualTo(HttpStatus.CREATED);
        return added.getBody().get("id").asLong();
    }

    private static long firstAccountIn(JsonNode accounts, String whichKind) {
        JsonNode ofThatKind = accounts.get(whichKind);
        assertThat(ofThatKind.size())
                .describedAs("a customer is opened with a " + whichKind + " entry to save from or into")
                .isPositive();
        return ofThatKind.get(0).get("id").asLong();
    }
}
