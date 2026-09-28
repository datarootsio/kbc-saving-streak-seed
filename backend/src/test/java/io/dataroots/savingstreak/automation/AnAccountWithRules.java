package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.DryRunView;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.RulePreviewView;
import io.dataroots.savingstreak.support.MonthlyIncomeView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A savings account nobody else's rules are standing on, and the saving-rule endpoints driven over
 * HTTP the way the frontend drives them.
 *
 * <p><strong>Its own customer, every time</strong>, for the reason {@code AnAccountWithGoals} gives
 * and one of this feature's own: the whole run shares one database, rules accumulate on an account
 * without anything ever clearing them, and the limit on how many may stand at once is counted per
 * <em>customer</em>. A class that left ten rules standing on the seeded Anke's account would refuse
 * the eleventh rule of whichever class ran after it, about a row it never created. Adding a customer
 * costs one request and buys an account whose whole rule list is this test's.
 *
 * <p>Every call is available twice: once as a method that insists on success and hands back the
 * rule, and once as a {@code try...} method that hands back the whole response. A test asserting
 * that something was refused needs the status and the words; a test leaving a rule standing in order
 * to change it needs neither, and should fail on the spot if the rule was never left standing.
 *
 * <p>The request bodies are built rather than restated in each test, because a rule has eight fields
 * and a test about the name of one should not have to say the other seven. They come from
 * {@link RulesAsSomebodyWouldTypeThem}, which the tests that watch a rule fire share — those stand
 * on an application of their own and would otherwise need a second copy of what a rule looks like.
 */
class AnAccountWithRules {

    /**
     * Makes each class's contact details different from every other class's without anybody having
     * to keep a list. An address used twice is the duplicate the application refuses, and the second
     * class to run would fail on the first one's customer.
     */
    private static final AtomicLong DISTINCT = new AtomicLong();

    private final TestRestTemplate http;
    private final long savingsAccountId;

    /** Whose account this is, for the reads that are asked of the customer rather than the account. */
    private final long customerId;

    /** Where the money a rule moves would come from: this customer's own current account. */
    private final long currentAccountId;

    AnAccountWithRules(TestRestTemplate http, String purpose) {
        this.http = http;
        this.customerId = aFreshCustomerFor(http, purpose);
        JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class, customerId);
        this.savingsAccountId = firstAccountIn(accounts, "savingsAccounts");
        this.currentAccountId = firstAccountIn(accounts, "currentAccounts");
    }

    long id() {
        return savingsAccountId;
    }

    long currentAccountId() {
        return currentAccountId;
    }

    /**
     * Declares what lands in this customer's current account every month, and insists it was
     * accepted. A rule that fires on payday has no day of its own — it reads this one — so a test
     * about such a rule has to be able to say what payday is, and to move it.
     */
    MonthlyIncomeView declaresAnIncome(String dayOfMonth, String amount) {
        ResponseEntity<MonthlyIncomeView> declared = http.exchange(
                "/api/current-accounts/{id}/monthly-income", HttpMethod.PUT,
                new HttpEntity<>(Map.of("dayOfMonth", dayOfMonth, "amount", amount)),
                MonthlyIncomeView.class, currentAccountId);
        assertThat(declared.getStatusCode())
                .describedAs("declaring an income of " + amount + " on day " + dayOfMonth
                        + ": " + declared.getBody())
                .isEqualTo(HttpStatus.OK);
        return declared.getBody();
    }

    /**
     * Takes the declaration back, so that the account's holder is once again somebody who has never
     * said when they are paid. The harshest thing that can happen to a payday rule's day, and the
     * reason an ended one has to have stopped following it.
     */
    void withdrawsTheIncomeDeclaration() {
        ResponseEntity<JsonNode> withdrawn = http.exchange(
                "/api/current-accounts/{id}/monthly-income", HttpMethod.DELETE, null, JsonNode.class,
                currentAccountId);
        assertThat(withdrawn.getStatusCode())
                .describedAs("withdrawing the income declared on current account " + currentAccountId)
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * Opens a goal on this account, insisted on, so that a test about a rule's split has a goal that
     * is genuinely being saved towards on the account the rule feeds.
     *
     * <p>Its own goal rather than one of the seeded customer's, for the reason this whole class
     * exists: a split is refused for naming a goal that is not live on <em>this</em> account, and a
     * test that borrowed somebody else's goal could not tell that refusal from a passing one.
     */
    GoalView opensAGoal(String name, String target) {
        ResponseEntity<GoalView> opened = http.postForEntity(
                "/api/savings-accounts/{id}/goals", Map.of("name", name, "target", target),
                GoalView.class, savingsAccountId);
        assertThat(opened.getStatusCode())
                .describedAs("opening the goal \"" + name + "\" this test's split names")
                .isEqualTo(HttpStatus.CREATED);
        return opened.getBody();
    }

    /** Gives up on one of them, which is how a goal stops being live without leaving the account. */
    GoalView abandons(long goalId) {
        ResponseEntity<GoalView> abandoned = http.postForEntity(
                "/api/savings-accounts/{id}/goals/{goalId}/abandon", null, GoalView.class,
                savingsAccountId, goalId);
        assertThat(abandoned.getStatusCode())
                .describedAs("abandoning goal " + goalId + " on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return abandoned.getBody();
    }

    /**
     * The day the application's clock reads, which is not necessarily today: this fixture stands on
     * the application the whole run shares, and other classes in it wind that clock.
     *
     * <p>Every test here counts its days from this rather than from {@code LocalDate.now()}, which
     * is the difference between a test that is right whenever it runs and one that is right until
     * somebody adds a class ahead of it.
     */
    LocalDate theDateTheClockReads() {
        ClockView read = http.getForObject("/api/dev/clock", ClockView.class);
        return read.now().atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
    }

    /**
     * What every rule standing on this account will do over the coming twelve months, insisted on.
     *
     * <p>The status is insisted on because {@code getForObject} hands back the error body rather
     * than throwing: a preview that had quietly become a refusal would satisfy every "nothing is
     * coming" assertion ever written against it.
     */
    RulePreviewView preview() {
        ResponseEntity<RulePreviewView> read = http.getForEntity(
                "/api/savings-accounts/{id}/saving-rules/preview", RulePreviewView.class,
                savingsAccountId);
        assertThat(read.getStatusCode())
                .describedAs("reading what the rules on savings account " + savingsAccountId
                        + " will do")
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** What a rule nobody has saved would move today, insisted on. */
    DryRunView dryRun(Map<String, Object> rule) {
        ResponseEntity<DryRunView> answered = http.postForEntity(
                "/api/savings-accounts/{id}/saving-rules/preview", rule, DryRunView.class,
                savingsAccountId);
        assertThat(answered.getStatusCode())
                .describedAs("asking what " + rule + " would move on savings account "
                        + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return answered.getBody();
    }

    /**
     * What the current account this customer's rules draw from holds, read the way a page reads it.
     * A test about a sweep's illustration has to be able to say what the illustration is of.
     */
    BigDecimal currentAccountBalance() {
        JsonNode accounts = http.getForObject("/api/customers/{id}/accounts", JsonNode.class,
                customerId);
        return accounts.get("currentAccounts").get(0).get("balance").decimalValue();
    }

    /**
     * Moves money out of the current account by hand, which is how a test changes the balance a
     * sweep would be quoted against without touching a rule.
     */
    void depositsByHand(String amount) {
        ResponseEntity<JsonNode> made = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                JsonNode.class, savingsAccountId);
        assertThat(made.getStatusCode())
                .describedAs("depositing " + amount + " out of current account " + currentAccountId)
                .isEqualTo(HttpStatus.CREATED);
    }

    /** The rules standing against the account, in the order their holder wrote them. */
    List<SavingRuleView> rules() {
        return rulesAt("/api/savings-accounts/{id}/saving-rules");
    }

    /** What was ended, which is where a rule's record lives on after it stops being an instruction. */
    List<SavingRuleView> endedRules() {
        return rulesAt("/api/savings-accounts/{id}/saving-rules/ended");
    }

    /**
     * What a rule that already stands would move today with a change applied to it, insisted on —
     * the preview the change form asks on every keystroke.
     */
    DryRunView dryRunAChange(long ruleId, Map<String, Object> whatToChange) {
        ResponseEntity<DryRunView> answered = http.postForEntity(
                "/api/savings-accounts/{id}/saving-rules/{ruleId}/preview", whatToChange,
                DryRunView.class, savingsAccountId, ruleId);
        assertThat(answered.getStatusCode())
                .describedAs("asking what " + whatToChange + " would move on rule " + ruleId)
                .isEqualTo(HttpStatus.OK);
        return answered.getBody();
    }

    /** The same request without insisting it was accepted, for the changes this feature refuses. */
    ResponseEntity<JsonNode> tryADryRunOfAChange(long ruleId, Map<String, Object> whatToChange) {
        return http.postForEntity("/api/savings-accounts/{id}/saving-rules/{ruleId}/preview",
                whatToChange, JsonNode.class, savingsAccountId, ruleId);
    }

    /**
     * The same request without insisting it was accepted, asked of the dry run rather than of the
     * save — for the tests whose subject is that the two refuse the same things in the same
     * sentences.
     */
    ResponseEntity<JsonNode> tryADryRun(Map<String, Object> rule) {
        return http.postForEntity("/api/savings-accounts/{id}/saving-rules/preview", rule,
                JsonNode.class, savingsAccountId);
    }

    /** Leaves a rule standing and insists it was accepted, handing back the rule that now exists. */
    SavingRuleView leaveStanding(Map<String, Object> rule) {
        ResponseEntity<SavingRuleView> left = http.postForEntity(
                "/api/savings-accounts/{id}/saving-rules", rule, SavingRuleView.class, savingsAccountId);
        assertThat(left.getStatusCode())
                .describedAs("leaving " + rule + " standing on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.CREATED);
        return left.getBody();
    }

    /** A rule exactly as somebody sent it, for the ones this feature refuses. */
    ResponseEntity<JsonNode> tryToLeaveStanding(Map<String, Object> rule) {
        return tryToLeaveStandingOn(savingsAccountId, rule);
    }

    /**
     * The same request against any savings account, named by identifier — for the test about being
     * told that the account somebody named is not one this application has heard of.
     */
    ResponseEntity<JsonNode> tryToLeaveStandingOn(long anySavingsAccountId, Map<String, Object> rule) {
        return http.postForEntity("/api/savings-accounts/{id}/saving-rules", rule, JsonNode.class,
                anySavingsAccountId);
    }

    /** Changes a rule and insists it was accepted, handing back the rule as it now reads. */
    SavingRuleView change(long ruleId, Map<String, Object> whatToChange) {
        ResponseEntity<SavingRuleView> changed = http.exchange(
                "/api/savings-accounts/{id}/saving-rules/{ruleId}", HttpMethod.PATCH,
                new HttpEntity<>(whatToChange), SavingRuleView.class, savingsAccountId, ruleId);
        assertThat(changed.getStatusCode())
                .describedAs("changing " + whatToChange + " on rule " + ruleId)
                .isEqualTo(HttpStatus.OK);
        return changed.getBody();
    }

    ResponseEntity<JsonNode> tryToChange(long ruleId, Map<String, Object> whatToChange) {
        return http.exchange("/api/savings-accounts/{id}/saving-rules/{ruleId}", HttpMethod.PATCH,
                new HttpEntity<>(whatToChange), JsonNode.class, savingsAccountId, ruleId);
    }

    /** Pauses a rule and insists it was allowed, handing back the rule as it now reads. */
    SavingRuleView pause(long ruleId) {
        ResponseEntity<SavingRuleView> paused = http.postForEntity(
                "/api/savings-accounts/{id}/saving-rules/{ruleId}/pause", null, SavingRuleView.class,
                savingsAccountId, ruleId);
        assertThat(paused.getStatusCode())
                .describedAs("pausing rule " + ruleId + " on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return paused.getBody();
    }

    /**
     * The same request without insisting it was accepted, for the tests whose subject is the answer:
     * pausing twice, which is accepted quietly, and pausing an ended rule, which is not.
     */
    ResponseEntity<JsonNode> tryToPause(long ruleId) {
        return http.postForEntity("/api/savings-accounts/{id}/saving-rules/{ruleId}/pause", null,
                JsonNode.class, savingsAccountId, ruleId);
    }

    /** Resumes a rule and insists it was allowed, handing back the rule as it now reads. */
    SavingRuleView resume(long ruleId) {
        ResponseEntity<SavingRuleView> resumed = http.postForEntity(
                "/api/savings-accounts/{id}/saving-rules/{ruleId}/resume", null, SavingRuleView.class,
                savingsAccountId, ruleId);
        assertThat(resumed.getStatusCode())
                .describedAs("resuming rule " + ruleId + " on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return resumed.getBody();
    }

    ResponseEntity<JsonNode> tryToResume(long ruleId) {
        return http.postForEntity("/api/savings-accounts/{id}/saving-rules/{ruleId}/resume", null,
                JsonNode.class, savingsAccountId, ruleId);
    }

    /** Ends a rule and insists it was allowed, handing back the rule as it now reads. */
    SavingRuleView end(long ruleId) {
        ResponseEntity<SavingRuleView> ended = http.exchange(
                "/api/savings-accounts/{id}/saving-rules/{ruleId}", HttpMethod.DELETE, null,
                SavingRuleView.class, savingsAccountId, ruleId);
        assertThat(ended.getStatusCode())
                .describedAs("ending rule " + ruleId + " on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return ended.getBody();
    }

    ResponseEntity<JsonNode> tryToEnd(long ruleId) {
        return http.exchange("/api/savings-accounts/{id}/saving-rules/{ruleId}", HttpMethod.DELETE,
                null, JsonNode.class, savingsAccountId, ruleId);
    }

    /**
     * A fixed amount every week on a chosen day, which is the ordinary rule this feature is for.
     *
     * <p>The four builders below say what a rule looks like through
     * {@link RulesAsSomebodyWouldTypeThem} rather than writing it out again, because the tests that
     * watch a rule <em>fire</em> stand on an application of their own and need the same bodies. Two
     * copies of "a weekly rule is a trigger, a day and an amount" are two chances to disagree about
     * what a rule looks like. What this class adds is the current account: a rule stands against one
     * customer's accounts, and that customer is this class's.
     */
    Map<String, Object> aFixedAmountEveryWeek(String name, String dayOfWeek, String amount) {
        return RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(
                currentAccountId, name, dayOfWeek, amount);
    }

    /** A fixed amount every month on a chosen date. */
    Map<String, Object> aFixedAmountEveryMonth(String name, String dayOfMonth, String amount) {
        return RulesAsSomebodyWouldTypeThem.aFixedAmountEveryMonth(
                currentAccountId, name, dayOfMonth, amount);
    }

    /** Everything above a floor, every week on a chosen day. */
    Map<String, Object> everythingAboveAFloorEveryWeek(String name, String dayOfWeek, String floor) {
        return RulesAsSomebodyWouldTypeThem.everythingAboveAFloorEveryWeek(
                currentAccountId, name, dayOfWeek, floor);
    }

    /** Everything above a floor, on the day the holder's declared income lands. */
    Map<String, Object> everythingAboveAFloorOnPayday(String name, String floor) {
        return RulesAsSomebodyWouldTypeThem.everythingAboveAFloorOnPayday(
                currentAccountId, name, floor);
    }

    /**
     * The bones of any rule: a name, and the current account this customer actually holds. A test
     * that is about one field fills that field in and leaves the rest of this alone.
     */
    Map<String, Object> aRuleDrawnFromThisCustomersCurrentAccount(String name) {
        return RulesAsSomebodyWouldTypeThem.aRuleDrawnFrom(currentAccountId, name);
    }

    /**
     * An identifier no rule on this account has: one past the highest that does. Both lists are
     * walked, because an ended rule is still a rule that exists and handing its identifier back as
     * an unused one would test nothing.
     */
    long anIdNoRuleOnThisAccountHas() {
        return Stream.concat(rules().stream(), endedRules().stream())
                .mapToLong(SavingRuleView::id)
                .max()
                .orElse(0L) + 1;
    }

    /** The reason a refusal gave, which is the field every error in this application carries it in. */
    static String reasonGivenBy(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody())
                .describedAs("a refusal with no body at all says nothing to the person who caused it")
                .isNotNull();
        return response.getBody().path("detail").asText();
    }

    static List<String> namesOf(List<SavingRuleView> rules) {
        return rules.stream().map(SavingRuleView::name).toList();
    }

    private List<SavingRuleView> rulesAt(String path) {
        ResponseEntity<SavingRuleView[]> listed =
                http.getForEntity(path, SavingRuleView[].class, savingsAccountId);
        assertThat(listed.getStatusCode())
                .describedAs("reading " + path + " for savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return Arrays.asList(listed.getBody());
    }

    /**
     * A customer of this test class's own, named after what the class is for, so that two classes
     * never leave rules standing on one account. The name is made distinct as well as the address
     * for the reason {@code AnAccountWithGoals} gives: tests elsewhere in this run look customers up
     * by name, and a dozen customers wearing one name would hide eleven accounts from that walk.
     */
    private static long aFreshCustomerFor(TestRestTemplate http, String purpose) {
        long distinct = DISTINCT.incrementAndGet();
        String address = "rules-" + purpose + "-" + distinct + "@example.be";
        ResponseEntity<JsonNode> added = http.postForEntity("/api/customers",
                Map.of("name", "Automator " + distinct + " for " + purpose, "contactDetails", address),
                JsonNode.class);
        assertThat(added.getStatusCode())
                .describedAs("opening the customer whose account this test's rules stand on")
                .isEqualTo(HttpStatus.CREATED);
        return added.getBody().get("id").asLong();
    }

    private static long firstAccountIn(JsonNode accounts, String whichKind) {
        JsonNode ofThatKind = accounts.get(whichKind);
        assertThat(ofThatKind.size())
                .describedAs("a customer is opened with a " + whichKind + " entry to automate with")
                .isPositive();
        return ofThatKind.get(0).get("id").asLong();
    }
}
