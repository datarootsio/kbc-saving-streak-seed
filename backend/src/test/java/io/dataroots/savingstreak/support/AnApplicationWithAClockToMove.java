package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
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

    /**
     * Makes every customer a test opens for itself different from every other one, without anybody
     * having to keep a list. Static, because two test classes each running their own application
     * still share this JVM, and a name used twice is a customer the wrong test would find.
     */
    private static final AtomicLong CUSTOMERS_OPENED_BY_TESTS = new AtomicLong();

    private final ConfigurableApplicationContext application;
    private final TestRestTemplate http;
    private final SeededAccounts seeded;

    public AnApplicationWithAClockToMove(Path databaseFile) {
        this(databaseFile, "dev");
    }

    /**
     * The same application seeded under other profiles, and optionally with something the test
     * brought defined in it as well as everything the application ships.
     *
     * <p>{@code dev} alone is the seeded pair of households with no history behind either of them,
     * which is what a run of weeks counted from zero needs. {@code "dev,demo"} adds the half of Anke
     * that has already been lived in — deposits in weeks that have ended, a run of secured weeks, a
     * mark she has fallen below, two dated goals and a standing rule. The two cannot be the same
     * customer, and {@code AHouseholdWithADecisionToMake} is where that is argued out.
     *
     * <p>The extra sources are for the tests whose subject is what the application does when
     * something inside it goes wrong. A nightly sweep that carries on past a customer it cannot
     * judge has nothing to demonstrate unless one customer genuinely cannot be judged, and no
     * sequence of requests arranges that: the failure has to be brought. What is brought is defined
     * in the test, guarded by a profile of its own so that it exists in this one application and in
     * nobody else's, and registered here in the same way {@code JobsThisTestDefines} registers the
     * scheduled jobs it brings. It is still the whole application over HTTP, and a test using it
     * still drives and asserts through the endpoints — the extra source is the fault, not the
     * observation.
     *
     * @param profiles      the profiles to start under, which must include whichever one guards any
     *                      classes being brought
     * @param alsoDefinedBy the test's own configuration classes, added to the application's own
     */
    public AnApplicationWithAClockToMove(Path databaseFile, String profiles,
                                         Class<?>... alsoDefinedBy) {
        Class<?>[] sources = new Class<?>[alsoDefinedBy.length + 1];
        sources[0] = SavingStreakApplication.class;
        System.arraycopy(alsoDefinedBy, 0, sources, 1, alsoDefinedBy.length);
        // Command-line arguments rather than default properties, for the reason the walking skeleton
        // gives: defaults lose to application.properties, which would point this instance at the
        // real database.
        this.application = new SpringApplicationBuilder(sources)
                .run("--spring.datasource.url=jdbc:sqlite:" + databaseFile,
                        "--spring.profiles.active=" + profiles,
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

    /**
     * One night passes: the clock moves a day on and the three runs that move money are fired in the
     * order the night fires them — the salary at one, the saving rules at two, the bills at half past
     * two.
     *
     * <p><strong>The order is the whole of this method</strong>, and it is why a test that wants a
     * month of a household's life asks for thirty-one nights rather than winding thirty-one days and
     * running each job once. Those are not the same month. A single wind and a single run of each job
     * credits every salary in the stretch, then fires every rule occurrence in it, then presents
     * every bill — so a sweep that would have emptied the account on one morning empties it against
     * money that had not arrived yet on any of the others, and every bill in the stretch meets an
     * account at its floor. Night by night, each morning's bills meet the balance that morning's
     * rules actually left, which is the ordering the whole feature exists to demonstrate.
     *
     * <p>The three that move money and not the other three. Points expiring at three, loyalty at half
     * past three and notifications at four change nothing a balance can see, and a test that has to
     * name them says so itself.
     */
    public void aNightPasses() {
        daysPass(1);
        runJob("creditMonthlyIncome");
        runJob("fireSavingRulesDue");
        runJob("takeBillsDue");
    }

    /** That many nights, one after another, each of them a whole night. */
    public void nightsPass(int nights) {
        for (int night = 0; night < nights; night++) {
            aNightPasses();
        }
    }

    /**
     * One night passes and <em>all six</em> of the runs that a projection can see are fired, in the
     * order they are scheduled: the salary at one, the saving rules at two, the bills at half past
     * two, the points expiring at three, the loyalty bonuses at half past and the monthly interest
     * at a quarter to four.
     *
     * <p>{@link #aNightPasses} fires the three that move euros out of a current account, which is
     * what a test about a household's balance needs. A test that puts the application's own figures
     * beside a simulator's needs all six, because three of the figures a month row carries — what
     * expired, what a bonus paid, and the euros the bank itself added — are only ever moved by the
     * last three, and a branch that predicted them against an application nobody had run those jobs
     * on would be marked wrong for being right.
     *
     * <p><strong>The sixth was added when the fold learnt the product it is projecting.</strong>
     * Until then a branch posted no interest at all, so a night without the sweep in it was a night
     * the simulator agreed with; a fold that now pays every projected month has to be checked
     * against an application that has been paid them too. It goes last because that is where its own
     * run is scheduled, a quarter of an hour behind the loyalty sweep.
     *
     * <p>The order is the whole of this method, for the reason {@link #aNightPasses} gives at length
     * and for one more: a batch expiring before a bonus is credited is what stops a bonus paid this
     * morning from going the same morning, and a test that ran the two the other way round would be
     * checking a night this application does not have.
     */
    public void aWholeNightPasses() {
        daysPass(1);
        runJob("creditMonthlyIncome");
        runJob("fireSavingRulesDue");
        runJob("takeBillsDue");
        runJob("expireOldPoints");
        runJob("payLoyaltyBonuses");
        runJob("postMonthlyInterest");
    }

    /** That many nights, one after another, each of them a whole night with all five runs. */
    public void wholeNightsPass(int nights) {
        for (int night = 0; night < nights; night++) {
            aWholeNightPasses();
        }
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

    /**
     * One of this application's own beans, by type, for a test whose subject has no read over HTTP
     * yet.
     *
     * <p>The exception rather than the way in, and it says so out loud. Behaviour is asserted at the
     * HTTP seam and nowhere lower, because a lower seam binds a test to the storage decisions later
     * slices need free to change. But a rule can arrive before the endpoint that reports it does,
     * and a nightly sweep whose whole effect is rows nobody can yet ask for would otherwise have to
     * be taken on trust for a slice or two. A test that reaches through here is asserting on what it
     * drove through the endpoints — a deposit, a withdrawal, a job run by name — and only reading
     * the answer from the module that owns it.
     */
    public <T> T theApplicationsOwn(Class<T> type) {
        return application.getBean(type);
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
     * What this savings account's futures look like: the window, where it stands today, and the one
     * branch nobody has to ask for.
     *
     * <p>Here so that a test winding a clock can put the application's own figures beside the ones a
     * branch predicted. It is the one read {@link AnApplicationWithAClockToMove} was missing when the
     * simulator arrived, and it is the read the whole feature is checked through: ask for a year,
     * wind three months, run the nights, and see whether the third row was telling the truth.
     *
     * <p>A {@code POST} carrying no scenario at all, which is exactly the request the frontend makes
     * before anybody has typed a branch, and the status is insisted on for the reason {@link #deposit}
     * gives: a simulation that had quietly become a refusal would satisfy every assertion about what
     * a branch does not contain.
     */
    public SimulationView simulationOf(long savingsAccountId) {
        ResponseEntity<SimulationView> answered = http.postForEntity(
                "/api/savings-accounts/{id}/simulations", Map.of("scenarios", List.of()),
                SimulationView.class, savingsAccountId);
        assertThat(answered.getStatusCode())
                .describedAs("asking what savings account " + savingsAccountId + " has ahead of it")
                .isEqualTo(HttpStatus.OK);
        return answered.getBody();
    }

    /**
     * The same question with branches in it, as the frontend posts them: a list of scenarios, each a
     * name and the changes it is made of, every figure and every day as the text a customer typed.
     */
    public SimulationView simulationOf(long savingsAccountId, List<Map<String, Object>> scenarios) {
        ResponseEntity<SimulationView> answered = http.postForEntity(
                "/api/savings-accounts/{id}/simulations", Map.of("scenarios", scenarios),
                SimulationView.class, savingsAccountId);
        assertThat(answered.getStatusCode())
                .describedAs("asking what " + scenarios + " would do to savings account "
                        + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return answered.getBody();
    }

    /**
     * The goals on an account, in the order of importance their holder put them in.
     *
     * <p>Read as the goals screen reads them, so that a test asking whether a seeded household
     * really carries a goal it is going to miss is reading the same status the customer would.
     */
    public List<GoalView> goalsOn(long savingsAccountId) {
        ResponseEntity<GoalView[]> read = http.getForEntity("/api/savings-accounts/{id}/goals",
                GoalView[].class, savingsAccountId);
        assertThat(read.getStatusCode())
                .describedAs("reading the goals on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /**
     * What the account has coming in the year ahead: the window the bar is drawn in, and every dated
     * thing the deposits in this account have in it.
     *
     * <p>Read per account rather than per customer, which is the whole of what this resource is for
     * — so a test holding two of one customer's accounts can ask each of them and be told two
     * different years.
     */
    public TimelineView timelineOf(long savingsAccountId) {
        return http.getForObject(
                "/api/savings-accounts/{id}/timeline", TimelineView.class, savingsAccountId);
    }

    /**
     * The account overview exactly as the API sends it, for a test that has to say a figure is
     * <em>not</em> in it.
     *
     * <p>The body rather than {@link BalancesView}, because a view binds the fields it knows about
     * and says nothing about the ones it does not: a test asserting that nothing was added to the
     * overview cannot ask a record that would have to be changed first in order to notice.
     *
     * <p>The status is insisted on for the reason {@link #deposit} gives, and it matters more here
     * than anywhere: a refusal is answered with a problem body, and a problem body contains none of
     * the words a test like that is looking for. An overview that had started answering 404 or 500
     * would satisfy every "this figure is not in it" assertion ever written against it.
     */
    public String theAccountOverviewAsItIsSent(long savingsAccountId) {
        return theBodyOfAReadThatHadToSucceed(
                "/api/savings-accounts/{id}", savingsAccountId);
    }

    /**
     * The customer's own overview exactly as the API sends it — the read the home screen makes, as
     * text, for a test that has to say a figure is <em>not</em> on it.
     *
     * <p>The other overview there is. {@link #theAccountOverviewAsItIsSent} is one savings account's
     * summary; this is the customer's, where the figures that belong to the person rather than to an
     * account live. A test saying nothing was added to the front page has to ask both, and has to
     * ask for the text for the reason that method gives: a view binds the fields it knows about and
     * says nothing about the ones it does not.
     *
     * <p>Insisted on the same way, and for the same reason: an overview that answered a problem
     * body instead would pass any assertion about what it does not contain.
     */
    public String theCustomerOverviewAsItIsSent(String customerName) {
        return theBodyOfAReadThatHadToSucceed(
                "/api/customers/{id}/accounts", seeded.customerIdOf(customerName));
    }

    /**
     * Every deposit into the account exactly as the history sends it, for a test that has to say a
     * figure is <em>not</em> in one.
     *
     * <p>The text rather than {@link #depositsInto}, and this is the sharper case of the rule
     * {@link #theAccountOverviewAsItIsSent} states. {@link DepositView} is filled in by Jackson,
     * which drops JSON properties the record has no component for, so a comparison of two arrays of
     * views is a comparison of the fields the view already knew about — a field <em>added</em> to
     * the deposit response is invisible to it. A test saying nothing was added to a deposit's
     * breakdown has to read the breakdown as it was sent.
     */
    public String theDepositHistoryAsItIsSent(long savingsAccountId) {
        return theBodyOfAReadThatHadToSucceed(
                "/api/savings-accounts/{id}/deposits", savingsAccountId);
    }

    /**
     * The ledger of money that moved exactly as the API sends it, for a test that has to say a
     * figure is <em>not</em> in it.
     *
     * <p>The text rather than {@link #moneyMovementsOf}, for the reason
     * {@link #theDepositHistoryAsItIsSent} gives about the deposit history and which holds word for
     * word here: {@link MoneyMovementView} is filled in by Jackson too, so comparing two arrays of
     * entries compares the fields the view already knew about and a field <em>added</em> to the
     * ledger's entries never reaches it. A test saying nothing about a gift was written into the
     * ledger has to read the ledger as it was sent.
     */
    public String theMoneyMovementLedgerAsItIsSent(String customerName) {
        return theBodyOfAReadThatHadToSucceed(
                "/api/customers/{id}/money-movements", seeded.customerIdOf(customerName));
    }

    /**
     * The body of a read that had to succeed, as text. {@code getForObject} hands back the error
     * body rather than throwing, so a read whose status nobody looked at is a read that can quietly
     * become a refusal — and every assertion about what a body does not contain would then be
     * asserting about a problem document.
     *
     * <p>The body itself is insisted on as well as the status, because the whole point of this
     * method is to hand back something a test can ask questions of: a 200 with nothing in it would
     * otherwise leave the caller with a null, and the failure would arrive as a
     * {@link NullPointerException} somewhere else rather than as the diagnosis this method exists
     * to give.
     */
    private String theBodyOfAReadThatHadToSucceed(String path, Object... uriVariables) {
        ResponseEntity<String> read = http.getForEntity(path, String.class, uriVariables);
        assertThat(read.getStatusCode())
                .describedAs("a read of " + path + " this test needs to have succeeded before it "
                        + "can say anything about what the body does not contain")
                .isEqualTo(HttpStatus.OK);
        assertThat(read.getBody())
                .describedAs("a read of " + path + " answered with a body, because a test cannot "
                        + "say what an empty answer does not contain")
                .isNotBlank();
        return read.getBody();
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

    /**
     * A gift of points from one customer to another, with the refusal ruled out: a gift that was
     * turned down would leave a test asserting that points nobody moved are still where they were —
     * and passing.
     *
     * <p>The recipient is named to this method by name and reaches the API as the contact details
     * they bank under, because that is what the contract carries. The points go over the wire as the
     * text they are given here, the way a deposit's amount does, so a test can hand this whatever a
     * customer could type.
     */
    public GiftView give(String senderName, String recipientName, String points) {
        ResponseEntity<GiftView> given =
                giveNaming(senderName, seeded.contactDetailsOf(recipientName), points);
        assertThat(given.getStatusCode())
                .describedAs("a gift this test needs in order to have moved any points")
                .isEqualTo(HttpStatus.CREATED);
        return given.getBody();
    }

    /**
     * The same request with the recipient addressed as whatever text is given here, answered with
     * whatever the API answered — for a test about how an address is matched, where the point is the
     * text and not that the gift went through.
     */
    public ResponseEntity<GiftView> giveNaming(String senderName, String recipientAsTyped,
                                               String points) {
        return http.postForEntity(
                "/api/customers/{id}/gifts",
                Map.of("recipientContactDetails", recipientAsTyped, "points", points),
                GiftView.class,
                seeded.customerIdOf(senderName));
    }

    /**
     * Every gift the customer was part of, sent and received together, newest first — the record
     * both parties read, and the only way from outside to say whether a gift was written down.
     */
    public GiftView[] giftsOf(String customerName) {
        return http.getForObject("/api/customers/{id}/gifts", GiftView[].class,
                seeded.customerIdOf(customerName));
    }

    /**
     * Everything that has been said to the customer as the API reports it, newest first, read and
     * unread together — the read the panel makes.
     *
     * <p>Read over HTTP rather than out of the module, now that there is an endpoint in front of it:
     * a test that says a sweep raised something is then also saying the customer can see it.
     *
     * <p>The status is insisted on for the reason {@link #deposit} gives, and it matters as much
     * here: {@code getForObject} hands back the error body rather than throwing, so a read that had
     * quietly become a refusal would satisfy every "nothing has been said yet" assertion ever
     * written against it.
     */
    public NotificationView[] notificationsOf(String customerName) {
        ResponseEntity<NotificationView[]> read = http.getForEntity(
                "/api/customers/{id}/notifications", NotificationView[].class,
                seeded.customerIdOf(customerName));
        assertThat(read.getStatusCode())
                .describedAs("a read of somebody's notifications this test needs to have succeeded")
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * The customer looks at their notifications, which marks every unread one read and answers the
     * whole list back — one round trip, as the panel makes it.
     *
     * <p>Insisted on the same way: a call that was refused would leave a test asserting that
     * notifications nobody marked are still unread — and passing.
     */
    public NotificationView[] marksTheirNotificationsRead(String customerName) {
        ResponseEntity<NotificationView[]> marked = http.postForEntity(
                "/api/customers/{id}/notifications/read", null, NotificationView[].class,
                seeded.customerIdOf(customerName));
        assertThat(marked.getStatusCode())
                .describedAs("a marking-read this test needs in order to have read anything")
                .isEqualTo(HttpStatus.OK);
        return marked.getBody();
    }

    /** Which customer this is, for a test asserting on who a gift names. */
    public long customerIdOf(String customerName) {
        return seeded.customerIdOf(customerName);
    }

    /**
     * A customer this test opened for itself, answered by the name every other method here takes.
     *
     * <p><strong>Why a test about a salary or a bill needs one.</strong> The seeded pair no longer
     * arrive empty: each of them carries a household — a declared monthly income and a handful of
     * declared bills — so that a trainer resetting the database finds an account that already looks
     * lived in. That household is credited and debited by the very nightly runs a clock-moving test
     * drives, so a test that declared its own rent on the seeded Anke would be asserting about her
     * rent as well as its own, and would start answering differently the next time somebody adjusted
     * a seeded figure.
     *
     * <p>It is the same argument {@code AnAccountWithBills} and {@code AnAccountWithAnIncome} already
     * make on the shared application — "its own customer, every time" — arriving here for the same
     * reason. A fresh application is what gives a streak somewhere to be counted from zero; it is no
     * longer what gives a bill an account with nothing else standing on it.
     *
     * <p>The name carries the purpose and a number, so that no two callers — here or in another test
     * class against another application — can collide, and so that a log line from a failing run says
     * which test opened the account it is talking about.
     *
     * @param purpose what this test is about, in a word or two, for the name the customer is opened
     *                under
     */
    public String aCustomerOfItsOwn(String purpose) {
        long distinct = CUSTOMERS_OPENED_BY_TESTS.incrementAndGet();
        String name = purpose + " " + distinct;
        ResponseEntity<JsonNode> opened = http.postForEntity("/api/customers",
                Map.of("name", name,
                        "contactDetails", "clock." + purpose.replace(' ', '.') + "." + distinct
                                + "@example.be"),
                JsonNode.class);
        assertThat(opened.getStatusCode())
                .describedAs("opening a customer of this test's own for " + purpose + ": "
                        + opened.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return name;
    }

    /**
     * The current account the customer's money comes out of and, now, the one their salary lands in.
     * For a test that has to name the account rather than the person — declaring an income is done
     * against one account, because a household with two of them declares it against the right one.
     */
    public long currentAccountOf(String customerName) {
        return seeded.currentAccountOf(customerName);
    }

    /**
     * Declares what lands in the customer's current account every month, with the refusal ruled out:
     * a declaration that was turned down would leave a test asserting that a job credited nothing —
     * and passing for the wrong reason.
     *
     * <p>The figures go over the wire as the text a customer would type, the way a deposit's amount
     * does.
     */
    public MonthlyIncomeView declareIncomeFor(String customerName, int dayOfMonth, String amount) {
        ResponseEntity<MonthlyIncomeView> declared = http.exchange(
                "/api/current-accounts/{id}/monthly-income", HttpMethod.PUT,
                new HttpEntity<>(Map.of("dayOfMonth", String.valueOf(dayOfMonth), "amount", amount)),
                MonthlyIncomeView.class, currentAccountOf(customerName));
        assertThat(declared.getStatusCode())
                .describedAs("declaring an income of " + amount + " on day " + dayOfMonth
                        + " for " + customerName)
                .isEqualTo(HttpStatus.OK);
        return declared.getBody();
    }

    /** What the customer's current account says about the income declared against it. */
    public MonthlyIncomeView incomeOf(String customerName) {
        ResponseEntity<MonthlyIncomeView> read = http.getForEntity(
                "/api/current-accounts/{id}/monthly-income", MonthlyIncomeView.class,
                currentAccountOf(customerName));
        assertThat(read.getStatusCode())
                .describedAs("a read of the income declared against " + customerName
                        + "'s current account")
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * Withdraws the declaration, insisted on the same way: a withdrawal that was refused would leave
     * a test asserting that a job credited nothing after it — and passing because it never happened.
     */
    public MonthlyIncomeView withdrawTheIncomeOf(String customerName) {
        ResponseEntity<MonthlyIncomeView> withdrawn = http.exchange(
                "/api/current-accounts/{id}/monthly-income", HttpMethod.DELETE, null,
                MonthlyIncomeView.class, currentAccountOf(customerName));
        assertThat(withdrawn.getStatusCode())
                .describedAs("withdrawing the income declared against " + customerName
                        + "'s current account")
                .isEqualTo(HttpStatus.OK);
        return withdrawn.getBody();
    }

    /**
     * Declares something that leaves the customer's current account every month, with the refusal
     * ruled out: a bill that was turned down would leave a test asserting that a nightly run took
     * nothing — and passing for the wrong reason.
     *
     * <p>The figures go over the wire as the text a customer would type, the way a deposit's amount
     * does, so a test can hand this whatever somebody could put in the box.
     */
    public RecurringBillView declareABillFor(String customerName, String name, int dayOfMonth,
                                             String amount) {
        ResponseEntity<RecurringBillView> declared = http.postForEntity(
                "/api/current-accounts/{id}/bills",
                Map.of("name", name, "dayOfMonth", String.valueOf(dayOfMonth), "amount", amount),
                RecurringBillView.class, currentAccountOf(customerName));
        assertThat(declared.getStatusCode())
                .describedAs("declaring \"" + name + "\" of " + amount + " on day " + dayOfMonth
                        + " for " + customerName + ": " + declared.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return declared.getBody();
    }

    /**
     * The bills standing against the customer's current account, in the order they were declared,
     * as the account's own read sends them — which is the list the page draws.
     *
     * <p>Read off the account rather than off the bills' own path, so that a test asserting what a
     * customer sees after a nightly run is asserting on the one read the screen actually makes.
     */
    public List<RecurringBillView> billsOf(String customerName) {
        return theCurrentAccountOf(customerName).bills();
    }

    /**
     * The customer's current account as its own resource reports it — the read the page makes,
     * carrying the iban, the balance, the declared income, the standing bills and what is still
     * owed in one answer.
     *
     * <p>The whole read rather than a field of it, for a test whose subject is the account as a
     * <em>shape</em>: two databases seeded from nothing have to come back describing the same
     * household, and asking five endpoints for five fields would be five chances to compare
     * something other than what a person sees.
     */
    public TheCurrentAccountView theCurrentAccountOf(String customerName) {
        ResponseEntity<TheCurrentAccountView> read = http.getForEntity(
                "/api/current-accounts/{id}", TheCurrentAccountView.class,
                currentAccountOf(customerName));
        assertThat(read.getStatusCode())
                .describedAs("a read of " + customerName + "'s current account")
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * Says a standing bill differently and insists it was accepted, handing back the bill as it now
     * reads.
     *
     * <p>What a test about a bill presented on a wound clock needs and {@code AnAccountWithBills}
     * cannot give: correcting the day a bill goes out on is how that day moves inside a month the
     * bill has already been taken in, and watching what the next run does about it needs a clock
     * this test may wind. The mirror of {@link #changeRule}, and the change arrives as the map of
     * text a customer's form would send so that a test can hand this whatever somebody could type.
     */
    public RecurringBillView changeTheBill(String customerName, long billId,
                                           Map<String, Object> whatToChange) {
        ResponseEntity<RecurringBillView> changed = http.exchange(
                "/api/current-accounts/{account}/bills/{bill}", HttpMethod.PATCH,
                new HttpEntity<>(whatToChange), RecurringBillView.class,
                currentAccountOf(customerName), billId);
        assertThat(changed.getStatusCode())
                .describedAs("changing " + whatToChange + " on bill " + billId + " of "
                        + customerName + ": " + changed.getBody())
                .isEqualTo(HttpStatus.OK);
        return changed.getBody();
    }

    /**
     * Ends a bill for good, insisted on: a bill that was not ended would leave a test asserting that
     * an ended bill was not taken — while what it was really watching was a bill that had never
     * stopped standing.
     */
    public RecurringBillView endTheBill(String customerName, long billId) {
        ResponseEntity<RecurringBillView> ended = http.exchange(
                "/api/current-accounts/{account}/bills/{bill}", HttpMethod.DELETE, null,
                RecurringBillView.class, currentAccountOf(customerName), billId);
        assertThat(ended.getStatusCode())
                .describedAs("ending bill " + billId + " for " + customerName)
                .isEqualTo(HttpStatus.OK);
        return ended.getBody();
    }

    /**
     * One bill's own history as the API reports it, newest first — every date it fell due and what
     * became of it.
     *
     * <p>The status is insisted on for the reason {@link #historyOf} gives: {@code getForObject}
     * hands back the error body rather than throwing, so a history that had quietly become a refusal
     * would satisfy every "this bill was never presented" assertion ever written against it.
     */
    public List<BillOccurrenceView> historyOfBill(String customerName, long billId) {
        ResponseEntity<BillOccurrenceView[]> read = http.getForEntity(
                "/api/current-accounts/{account}/bills/{bill}/history", BillOccurrenceView[].class,
                currentAccountOf(customerName), billId);
        assertThat(read.getStatusCode())
                .describedAs("reading the history of bill " + billId + " for " + customerName)
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /**
     * What the customer's current account still owes, oldest first, as the arrears' own path reports
     * it.
     *
     * <p>Read off the endpoint the spec names rather than off the account, so that the path a later
     * slice hangs a notification off is exercised as well — and the status is insisted on for the
     * reason {@link #historyOfBill} gives: a read that had quietly become a refusal would satisfy
     * every "nothing is owed" assertion ever written against it.
     */
    public List<ArrearView> arrearsOf(String customerName) {
        ResponseEntity<ArrearView[]> read = http.getForEntity(
                "/api/current-accounts/{id}/arrears", ArrearView[].class,
                currentAccountOf(customerName));
        assertThat(read.getStatusCode())
                .describedAs("reading what " + customerName + "'s current account still owes")
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /**
     * The same answer as the account's own read carries, for a test asserting that the page and the
     * endpoint agree about what is owed.
     */
    public List<ArrearView> arrearsTheAccountItselfReports(String customerName) {
        return theCurrentAccountOf(customerName).arrears();
    }

    /**
     * A withdrawal without insisting it was accepted, for a test whose subject is the refusal: money
     * a goal has spoken for refuses to come back, which is what makes a goal a commitment rather than
     * a label — and the words it refuses in are the whole of what the customer gets to act on.
     */
    public ResponseEntity<JsonNode> tryToWithdraw(long savingsAccountId, String customerName,
                                                  String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", seeded.currentAccountOf(customerName)),
                JsonNode.class, savingsAccountId);
    }

    /**
     * A deposit without insisting it was accepted, for a test whose subject is the refusal: paying
     * into a shared pot somebody is not in, or is only watching, is turned down in a sentence, and
     * the sentence is the whole of what the person gets to act on.
     *
     * <p>From the customer's own current account, which is what makes the refusal a refusal about
     * the pot rather than about whose money it is.
     */
    public ResponseEntity<JsonNode> tryToDeposit(long savingsAccountId, String customerName,
                                                 String amount) {
        return tryToDepositFrom(savingsAccountId, currentAccountOf(customerName), amount);
    }

    /**
     * The same, from a current account named by identifier rather than by whose it is — for a test
     * about paying in from an account that is not the payer's own, where naming the person would be
     * assuming the very thing under test.
     *
     * <p>The amount travels as the text somebody would type, like every other amount here, so that
     * a test about "2500,00" can send the comma that caused the trouble.
     */
    public ResponseEntity<JsonNode> tryToDepositFrom(long savingsAccountId, long currentAccountId,
                                                     String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                JsonNode.class, savingsAccountId);
    }

    /**
     * Leaves a saving rule standing against the account, insisted on: a rule that was refused would
     * leave a test asserting that a night nothing fired on moved no money — and passing for entirely
     * the wrong reason.
     *
     * <p>The rule arrives as the map of text a customer's form would send, so that a test can hand
     * this whatever somebody could type and this class needs to know nothing about the eight fields
     * a rule has.
     */
    public SavingRuleView leaveARuleStanding(long savingsAccountId, Map<String, Object> rule) {
        ResponseEntity<SavingRuleView> left = http.postForEntity(
                "/api/savings-accounts/{id}/saving-rules", rule, SavingRuleView.class,
                savingsAccountId);
        assertThat(left.getStatusCode())
                .describedAs("leaving " + rule + " standing on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.CREATED);
        return left.getBody();
    }

    /** The rules standing against one savings account, in the order their holder wrote them. */
    public List<SavingRuleView> rulesOn(long savingsAccountId) {
        ResponseEntity<SavingRuleView[]> listed = http.getForEntity(
                "/api/savings-accounts/{id}/saving-rules", SavingRuleView[].class, savingsAccountId);
        assertThat(listed.getStatusCode())
                .describedAs("reading the rules standing on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return List.of(listed.getBody());
    }

    /**
     * The same request without insisting it was accepted, for a test whose subject is the refusal:
     * the limit on how many rules one customer may leave standing is the one that matters here, and
     * a test about it needs the status and the words rather than a rule.
     */
    public ResponseEntity<JsonNode> tryToLeaveARuleStanding(long savingsAccountId,
                                                            Map<String, Object> rule) {
        return http.postForEntity("/api/savings-accounts/{id}/saving-rules", rule, JsonNode.class,
                savingsAccountId);
    }

    /**
     * Says a standing rule differently and insists it was accepted, handing back the rule as it now
     * reads.
     *
     * <p>What a test about a rule fired on a wound clock needs and {@code AnAccountWithRules} cannot
     * give: changing the day a rule moves on is one of the two ways that day can move inside a
     * period the rule has already fired in, and watching what the next run does about it needs a
     * clock this test may wind.
     */
    public SavingRuleView changeRule(long savingsAccountId, long ruleId,
                                     Map<String, Object> whatToChange) {
        ResponseEntity<SavingRuleView> changed = http.exchange(
                "/api/savings-accounts/{id}/saving-rules/{ruleId}", HttpMethod.PATCH,
                new HttpEntity<>(whatToChange), SavingRuleView.class, savingsAccountId, ruleId);
        assertThat(changed.getStatusCode())
                .describedAs("changing " + whatToChange + " on rule " + ruleId
                        + " of savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return changed.getBody();
    }

    /**
     * Stops a rule until this test resumes it, insisted on: a pause that was quietly refused would
     * leave a test asserting that a paused rule fired nothing — while what it was really watching
     * was a rule that had never been paused at all, on a night nothing else fired either.
     */
    public SavingRuleView pauseRule(long savingsAccountId, long ruleId) {
        ResponseEntity<SavingRuleView> paused = http.postForEntity(
                "/api/savings-accounts/{id}/saving-rules/{ruleId}/pause", null, SavingRuleView.class,
                savingsAccountId, ruleId);
        assertThat(paused.getStatusCode())
                .describedAs("pausing rule " + ruleId + " on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        assertThat(paused.getBody().state())
                .describedAs("a rule this test paused has to say it is paused, or every assertion "
                        + "after it is about something else")
                .isEqualTo("PAUSED");
        return paused.getBody();
    }

    /**
     * Starts a paused rule again from this moment, insisted on the same way and for the sharper
     * reason: a resume that did not happen would leave a test asserting that nothing was made up —
     * and passing because the rule was still paused.
     */
    public SavingRuleView resumeRule(long savingsAccountId, long ruleId) {
        ResponseEntity<SavingRuleView> resumed = http.postForEntity(
                "/api/savings-accounts/{id}/saving-rules/{ruleId}/resume", null, SavingRuleView.class,
                savingsAccountId, ruleId);
        assertThat(resumed.getStatusCode())
                .describedAs("resuming rule " + ruleId + " on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        assertThat(resumed.getBody().state())
                .describedAs("a rule this test resumed has to say it is live again, or what follows "
                        + "is a test of a rule that is still stopped")
                .isEqualTo("LIVE");
        return resumed.getBody();
    }

    /**
     * Ends a rule for good, insisted on the same way: a rule that was not ended would leave a test
     * asserting that an ended rule fired nothing — while what it was really watching was a rule that
     * had never stopped standing.
     */
    public SavingRuleView endRule(long savingsAccountId, long ruleId) {
        ResponseEntity<SavingRuleView> ended = http.exchange(
                "/api/savings-accounts/{id}/saving-rules/{ruleId}", HttpMethod.DELETE, null,
                SavingRuleView.class, savingsAccountId, ruleId);
        assertThat(ended.getStatusCode())
                .describedAs("ending rule " + ruleId + " on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return ended.getBody();
    }

    /**
     * What every rule standing on that savings account will do over the coming twelve months, as the
     * API reports it, insisted on.
     *
     * <p>The status is insisted on for the reason {@link #historyOf} gives: {@code getForObject}
     * hands back the error body rather than throwing, so a preview that had quietly become a refusal
     * would satisfy every "nothing is coming" assertion ever written against it.
     */
    public RulePreviewView previewOn(long savingsAccountId) {
        ResponseEntity<RulePreviewView> read = http.getForEntity(
                "/api/savings-accounts/{id}/saving-rules/preview", RulePreviewView.class,
                savingsAccountId);
        assertThat(read.getStatusCode())
                .describedAs("reading what the rules on savings account " + savingsAccountId
                        + " will do")
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * What a rule nobody has saved would move today, insisted on — the dry run, asked of the rule
     * exactly as a customer's form would send it.
     */
    public DryRunView dryRun(long savingsAccountId, Map<String, Object> rule) {
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
     * The same request without insisting it was accepted, for the tests whose subject is the
     * refusal: a dry run turns down exactly what a save would, and a test about that needs the
     * status and the words rather than a figure.
     */
    public ResponseEntity<JsonNode> tryADryRun(long savingsAccountId, Map<String, Object> rule) {
        return http.postForEntity("/api/savings-accounts/{id}/saving-rules/preview", rule,
                JsonNode.class, savingsAccountId);
    }

    /**
     * One rule's own history as the API reports it, newest first — every day it fell due and what
     * became of it.
     *
     * <p>The status is insisted on for the reason {@link #deposit} gives: {@code getForObject} hands
     * back the error body rather than throwing, so a history that had quietly become a refusal would
     * satisfy every "this rule fired nothing" assertion ever written against it.
     */
    public List<RuleOccurrenceView> historyOf(long savingsAccountId, long ruleId) {
        ResponseEntity<RuleOccurrenceView[]> read = http.getForEntity(
                "/api/savings-accounts/{id}/saving-rules/{ruleId}/history",
                RuleOccurrenceView[].class, savingsAccountId, ruleId);
        assertThat(read.getStatusCode())
                .describedAs("reading the history of rule " + ruleId + " on savings account "
                        + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /**
     * Opens a goal on this savings account, insisted on, so that a test about a rule spreading money
     * across goals has goals to spread it across.
     *
     * <p>No deadline, which is the ordinary goal and the one this feature's tests want: a deadline
     * would put the goal into the weekly plan and make the test's own figures depend on what day of
     * the week the clock happens to read.
     */
    public GoalView openAGoal(long savingsAccountId, String name, String target) {
        Map<String, Object> goal = new HashMap<>();
        goal.put("name", name);
        goal.put("target", target);
        ResponseEntity<GoalView> opened = http.postForEntity(
                "/api/savings-accounts/{id}/goals", goal, GoalView.class, savingsAccountId);
        assertThat(opened.getStatusCode())
                .describedAs("opening the goal \"" + name + "\" on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.CREATED);
        return opened.getBody();
    }

    /**
     * What the account holds, what its goals have claimed of it, and what no goal has claimed.
     *
     * <p>The one read every test about a split finishes on: allocated plus unallocated equals the
     * balance, and that sum is the promise a split has to keep to the cent.
     */
    public AllocationsView allocationsOn(long savingsAccountId) {
        ResponseEntity<AllocationsView> read = http.getForEntity(
                "/api/savings-accounts/{id}/goals/allocations", AllocationsView.class,
                savingsAccountId);
        assertThat(read.getStatusCode())
                .describedAs("reading the allocations on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * Moves money into a goal by hand, out of what no goal has claimed, insisted on — which is how a
     * test sets a goal up as nearly complete before letting a rule fire into it.
     */
    public AllocationsView allocate(long savingsAccountId, long goalId, String amount) {
        Map<String, Object> move = new HashMap<>();
        move.put("amount", amount);
        move.put("direction", "INTO_THE_GOAL");
        ResponseEntity<AllocationsView> moved = http.postForEntity(
                "/api/savings-accounts/{id}/goals/{goalId}/allocations", move, AllocationsView.class,
                savingsAccountId, goalId);
        assertThat(moved.getStatusCode())
                .describedAs("allocating " + amount + " to goal " + goalId)
                .isEqualTo(HttpStatus.OK);
        return moved.getBody();
    }

    /**
     * Gives up on a goal, insisted on. A goal abandoned after a rule's split was written is the one
     * thing that can take a line out of a split without the customer re-wording it, and a test about
     * that needs the abandoning to have actually happened.
     */
    public GoalView abandonGoal(long savingsAccountId, long goalId) {
        ResponseEntity<GoalView> abandoned = http.postForEntity(
                "/api/savings-accounts/{id}/goals/{goalId}/abandon", null, GoalView.class,
                savingsAccountId, goalId);
        assertThat(abandoned.getStatusCode())
                .describedAs("abandoning goal " + goalId + " on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return abandoned.getBody();
    }

    /**
     * Names one more thing the customer's money goes on, insisted on: a category that was refused
     * would leave a test asserting that a month reported nothing under it — and passing for the
     * wrong reason.
     */
    public SpendingCategoryView declareACategoryFor(String customerName, String name) {
        ResponseEntity<SpendingCategoryView> declared = http.postForEntity(
                "/api/current-accounts/{id}/categories", Map.of("name", name),
                SpendingCategoryView.class, currentAccountOf(customerName));
        assertThat(declared.getStatusCode())
                .describedAs("declaring the category \"" + name + "\" for " + customerName + ": "
                        + declared.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return declared.getBody();
    }

    /** Ends a category for good, insisted on, handing back the category as it now reads. */
    public SpendingCategoryView endTheCategoryFor(String customerName, long categoryId) {
        ResponseEntity<SpendingCategoryView> ended = http.exchange(
                "/api/current-accounts/{account}/categories/{category}", HttpMethod.DELETE, null,
                SpendingCategoryView.class, currentAccountOf(customerName), categoryId);
        assertThat(ended.getStatusCode())
                .describedAs("ending category " + categoryId + " for " + customerName)
                .isEqualTo(HttpStatus.OK);
        return ended.getBody();
    }

    /**
     * Says what a category is allowed to cost each month, insisted on.
     *
     * <p>What a test about a budget superseded in one month and read back in another needs and the
     * shared application cannot give: the months between the two figures pass by winding the clock,
     * which cannot be undone, so the test that does it has to own its own application. The figure
     * goes over the wire as the text a customer would type, the way a bill's amount does.
     */
    public MonthlyBudgetView declareABudgetFor(String customerName, long categoryId, String amount) {
        return declareABudgetFor(customerName, categoryId, amount, null);
    }

    /**
     * The same, saying what should become of the difference when a month it governs ends.
     *
     * <p>Two methods rather than one with a null at every call site, because most tests about months
     * have no opinion about rollover and the default is the honest thing for them to send — a
     * customer who has never thought about it has a rule all the same. A test whose subject
     * <em>is</em> the rule names it here, and the name goes over the wire as the text a page would
     * send, so that what a test drives and what the frontend drives are the same request.
     *
     * @param rollover one of the rollover rules by name, or null to send none at all — which is
     *                 what a page that has never offered the choice does
     */
    public MonthlyBudgetView declareABudgetFor(String customerName, long categoryId, String amount,
                                               String rollover) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("rollover", rollover);
        ResponseEntity<MonthlyBudgetView> declared = http.exchange(
                "/api/current-accounts/{account}/categories/{category}/budget", HttpMethod.PUT,
                new HttpEntity<>(body), MonthlyBudgetView.class,
                currentAccountOf(customerName), categoryId);
        assertThat(declared.getStatusCode())
                .describedAs("budgeting EUR " + amount + " on category " + categoryId + " for "
                        + customerName + " with rollover " + rollover + ": " + declared.getBody())
                .isEqualTo(HttpStatus.OK);
        return declared.getBody();
    }

    /** Stops budgeting a category without ending it, insisted on. */
    public MonthlyBudgetView stopBudgetingFor(String customerName, long categoryId) {
        ResponseEntity<MonthlyBudgetView> stopped = http.exchange(
                "/api/current-accounts/{account}/categories/{category}/budget", HttpMethod.DELETE,
                null, MonthlyBudgetView.class, currentAccountOf(customerName), categoryId);
        assertThat(stopped.getStatusCode())
                .describedAs("stopping the budget on category " + categoryId + " for "
                        + customerName)
                .isEqualTo(HttpStatus.OK);
        return stopped.getBody();
    }

    /**
     * Puts one of the customer's standing bills in a category, insisted on — which is what makes a
     * bill occurrence the committed half of that category's month.
     */
    public void putBillInCategoryFor(String customerName, long billId, long categoryId) {
        ResponseEntity<JsonNode> filed = http.exchange(
                "/api/current-accounts/{account}/bills/{bill}/category", HttpMethod.PUT,
                new HttpEntity<>(Map.of("categoryId", categoryId)), JsonNode.class,
                currentAccountOf(customerName), billId);
        assertThat(filed.getStatusCode())
                .describedAs("putting bill " + billId + " in category " + categoryId + " for "
                        + customerName + ": " + filed.getBody())
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * Records a spend out of the customer's current account, insisted on, filed wholly under one
     * category.
     *
     * <p>One part, because the split is not what a test about months is asserting — how a split is
     * judged is settled on the shared application, where it belongs. The amount travels as text, as
     * every other amount a customer types does.
     */
    public SpendView spendFor(String customerName, String name, String amount, long categoryId) {
        Map<String, Object> part = new HashMap<>();
        part.put("categoryId", categoryId);
        part.put("amount", amount);
        ResponseEntity<SpendView> recorded = http.postForEntity(
                "/api/current-accounts/{id}/spends",
                Map.of("name", name, "amount", amount, "parts", List.of(part)), SpendView.class,
                currentAccountOf(customerName));
        assertThat(recorded.getStatusCode())
                .describedAs("recording \"" + name + "\" of EUR " + amount + " for " + customerName
                        + ": " + recorded.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return recorded.getBody();
    }

    /**
     * Records a spend split several ways, insisted on, handing back the spend as it now reads.
     *
     * <p>The other half of {@link #spendFor}, and the two are separate methods rather than one with
     * a varargs list because they answer different questions. That one is for a test about months,
     * where the split is beside the point and one part keeps the arithmetic readable; this is for a
     * test about the ledger, where the split <em>is</em> the point — a row that says what a supermarket
     * trip was for has to have been recorded as more than one thing.
     *
     * <p>A part with no category at all is one of these too, which is a state the customer chose and
     * not a gap, and the ledger has to draw it.
     */
    public SpendView spendForSplitAcross(String customerName, String name, String amount,
                                         PartAsTyped... parts) {
        ResponseEntity<SpendView> recorded = http.postForEntity(
                "/api/current-accounts/{id}/spends",
                Map.of("name", name, "amount", amount, "parts", asSent(parts)), SpendView.class,
                currentAccountOf(customerName));
        assertThat(recorded.getStatusCode())
                .describedAs("recording \"" + name + "\" of EUR " + amount + " for " + customerName
                        + ": " + recorded.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return recorded.getBody();
    }

    /**
     * Says again what a spend already recorded was for, insisted on, handing back the spend as it
     * now reads — which is the only way a spend on the ledger ever comes to carry a corrected-at
     * moment.
     *
     * <p>The whole split rather than the part that changed, because that is what the endpoint takes:
     * a correction is the customer saying what the spend was for, and a request naming one part
     * would leave the others to be guessed at. There is no name in it and no amount, because the
     * money moved.
     */
    public SpendView correctTheSplitFor(String customerName, long spendId, PartAsTyped... parts) {
        ResponseEntity<SpendView> corrected = http.exchange(
                "/api/current-accounts/{account}/spends/{spend}/split", HttpMethod.PUT,
                new HttpEntity<>(Map.of("parts", asSent(parts))), SpendView.class,
                currentAccountOf(customerName), spendId);
        assertThat(corrected.getStatusCode())
                .describedAs("correcting the split of spend " + spendId + " for " + customerName
                        + ": " + corrected.getBody())
                .isEqualTo(HttpStatus.OK);
        return corrected.getBody();
    }

    /**
     * Replaces the whole split of a spend already recorded, insisted on.
     *
     * <p>What a test about a correction reaching backwards needs and the shared application cannot
     * give: the month the spend was made in is only a month already gone once the clock has been
     * wound past it, and winding cannot be undone. The whole split is sent rather than the part that
     * changed, because that is what a correction is — the customer saying what the spend was for —
     * and a request naming one part would leave the others to be guessed at.
     */
    public SpendView correctTheSplitFor(String customerName, long spendId, long categoryId,
                                        String amount) {
        Map<String, Object> part = new HashMap<>();
        part.put("categoryId", categoryId);
        part.put("amount", amount);
        ResponseEntity<SpendView> corrected = http.exchange(
                "/api/current-accounts/{account}/spends/{spend}/split", HttpMethod.PUT,
                new HttpEntity<>(Map.of("parts", List.of(part))), SpendView.class,
                currentAccountOf(customerName), spendId);
        assertThat(corrected.getStatusCode())
                .describedAs("correcting spend " + spendId + " for " + customerName + " into "
                        + "category " + categoryId + ": " + corrected.getBody())
                .isEqualTo(HttpStatus.OK);
        return corrected.getBody();
    }

    /** The parts as a page sends them. HashMaps, because {@code Map.of} will not hold a null. */
    private static List<Map<String, Object>> asSent(PartAsTyped... parts) {
        List<Map<String, Object>> split = new ArrayList<>();
        for (PartAsTyped part : parts) {
            Map<String, Object> one = new HashMap<>();
            one.put("categoryId", part.categoryId());
            one.put("amount", part.amount());
            split.add(one);
        }
        return split;
    }

    /**
     * How the month the clock is in is going on the customer's current account.
     *
     * <p>Read without naming a month, which is the whole point of the endpoint: on a wound clock a
     * test working out the month itself would be writing the application's arithmetic twice.
     */
    public MonthOfSpendingView spendingThisMonthOf(String customerName) {
        ResponseEntity<MonthOfSpendingView> read = http.getForEntity(
                "/api/current-accounts/{id}/spending", MonthOfSpendingView.class,
                currentAccountOf(customerName));
        assertThat(read.getStatusCode())
                .describedAs("reading this month's spending for " + customerName)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * The next six weeks of cash flow on the customer's current account, as the card's own endpoint
     * sends them — the read the budget screen makes.
     *
     * <p>Read without naming a week, which is the whole point of the address: on a wound clock a
     * test working out which Monday this week began on would be writing the application's own
     * arithmetic twice, and would pass against a card drawn over some other six weeks.
     *
     * <p>The status is insisted on for the reason {@link #deposit} gives: {@code getForObject} hands
     * back the error body rather than throwing, so a read that had quietly become a refusal would
     * satisfy every "nothing is claimed in that week" assertion ever written against it.
     */
    public WeeksAheadView weeksAheadOf(String customerName) {
        ResponseEntity<WeeksAheadView> read = http.getForEntity(
                "/api/current-accounts/{id}/weeks-ahead", WeeksAheadView.class,
                currentAccountOf(customerName));
        assertThat(read.getStatusCode())
                .describedAs("reading the weeks ahead for " + customerName)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * What the savings account's holder has said they can put away in a week, or the record saying
     * they never have.
     *
     * <p>Here so that a test about the figure a budget <em>offers</em> can say that the figure a
     * customer <em>declared</em> did not move. The two live in different modules and are drawn side
     * by side, and the claim worth asserting is that reading one never writes the other.
     */
    public SavingCapacityView savingCapacityOf(long savingsAccountId) {
        ResponseEntity<SavingCapacityView> read = http.getForEntity(
                "/api/savings-accounts/{id}/saving-capacity", SavingCapacityView.class,
                savingsAccountId);
        assertThat(read.getStatusCode())
                .describedAs("reading the saving capacity declared on savings account "
                        + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * Declares the weekly figure, insisted on — the request the press that adopts a budget's offer
     * makes, sent exactly as the page sends it.
     *
     * <p>The figure travels as the text a customer would type, the way every other amount in this
     * fixture does, so that a test adopting a derived figure sends what the frontend would send:
     * the offer quoted to the cent, and nothing worked out here.
     */
    public SavingCapacityView declareSavingCapacityFor(long savingsAccountId, String weekly) {
        ResponseEntity<SavingCapacityView> declared = http.exchange(
                "/api/savings-accounts/{id}/saving-capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("weeklyCapacity", weekly)), SavingCapacityView.class,
                savingsAccountId);
        assertThat(declared.getStatusCode())
                .describedAs("declaring a weekly capacity of " + weekly + " on savings account "
                        + savingsAccountId + ": " + declared.getBody())
                .isEqualTo(HttpStatus.OK);
        return declared.getBody();
    }

    /** The same answer about a month already gone, named as the year and the month. */
    public MonthOfSpendingView spendingInMonthOf(String customerName, String yearMonth) {
        ResponseEntity<MonthOfSpendingView> read = http.getForEntity(
                "/api/current-accounts/{id}/spending/{month}", MonthOfSpendingView.class,
                currentAccountOf(customerName), yearMonth);
        assertThat(read.getStatusCode())
                .describedAs("reading " + yearMonth + " for " + customerName)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * How the last few months went on the customer's current account: the window, and a row of
     * history per category.
     *
     * <p>Read without naming a month for the reason {@link #spendingThisMonthOf} is: the comparison
     * always ends with the month the clock is in, and a test working that out itself would be
     * writing the application's arithmetic a second time — on a clock wound a year forward, wrongly.
     */
    public SpendingHistoryView theLastFewMonthsOf(String customerName) {
        ResponseEntity<SpendingHistoryView> read = http.getForEntity(
                "/api/current-accounts/{id}/spending/history", SpendingHistoryView.class,
                currentAccountOf(customerName));
        assertThat(read.getStatusCode())
                .describedAs("reading the last few months of spending for " + customerName)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** What the customer signs in with, which is also how a gift addresses them. */
    public String contactDetailsOf(String customerName) {
        return seeded.contactDetailsOf(customerName);
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

    /**
     * A shared pot this test needed opened, with the refusal ruled out: a pot that was turned down
     * would leave a test asserting that nobody is a member of nothing — and passing.
     *
     * <p>The customer who opens it becomes its owner, and travels in the body rather than in the
     * path, because that is what the contract carries: a pot belongs to no customer, so there is no
     * customer in its path to open it under.
     */
    /**
     * One savings product and the terms it is offering on the day this application is standing on.
     *
     * <p>For a test that needs to say what the catalogue is selling <em>now</em> without writing
     * the figure down: what an account is living under is asserted against this rather than against
     * a constant, so the assertion stays about the two readings differing rather than about what
     * the seed happened to write.
     */
    public SavingsProductView theSavingsProduct(String code) {
        SavingsProductView product = http.getForObject("/api/savings-products/{code}",
                SavingsProductView.class, code);
        assertThat(product)
                .describedAs("the savings product " + code + " the catalogue is offering")
                .isNotNull();
        return product;
    }

    public SharedPotView openAPot(String customerName, String name) {
        ResponseEntity<SharedPotView> opened = tryToOpenAPot(
                Map.of("name", name, "customerId", seeded.customerIdOf(customerName)),
                SharedPotView.class);
        assertThat(opened.getStatusCode())
                .describedAs("opening the shared pot \"" + name + "\" for " + customerName)
                .isEqualTo(HttpStatus.CREATED);
        return opened.getBody();
    }

    /**
     * The same request with whatever body is given here, answered with whatever the API answered —
     * for a test whose subject is the refusal, where the point is the sentence and not that a pot
     * was opened.
     *
     * <p>{@code HashMap} at the call site rather than {@code Map.of}, so that a test about a missing
     * field can send a null, which {@code Map.of} will not hold.
     */
    public ResponseEntity<JsonNode> tryToOpenAPot(Map<String, Object> pot) {
        return tryToOpenAPot(pot, JsonNode.class);
    }

    private <T> ResponseEntity<T> tryToOpenAPot(Map<String, Object> pot, Class<T> shape) {
        return http.postForEntity("/api/shared-pots", pot, shape);
    }

    /**
     * One pot as the API reports it, insisted on: a pot that could not be read would satisfy no
     * assertion about what is in it, and would fail as a null rather than as a status.
     */
    public SharedPotView potWith(long potId) {
        ResponseEntity<SharedPotView> read = http.getForEntity(
                "/api/shared-pots/{id}", SharedPotView.class, potId);
        assertThat(read.getStatusCode())
                .describedAs("reading shared pot " + potId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** The same read without insisting it succeeded, for a test about a pot that is not there. */
    public ResponseEntity<JsonNode> tryToReadThePot(long potId) {
        return http.getForEntity("/api/shared-pots/{id}", JsonNode.class, potId);
    }

    /** Who belongs to the pot and what each of them is to it, read on its own. */
    public List<PotMemberView> membersOfThePot(long potId) {
        ResponseEntity<PotMemberView[]> read = http.getForEntity(
                "/api/shared-pots/{id}/members", PotMemberView[].class, potId);
        assertThat(read.getStatusCode())
                .describedAs("reading the members of shared pot " + potId)
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /** The same read without insisting it succeeded, for a test about a pot that is not there. */
    public ResponseEntity<JsonNode> tryToReadTheMembers(long potId) {
        return http.getForEntity("/api/shared-pots/{id}/members", JsonNode.class, potId);
    }

    /** Every shared pot this customer belongs to, oldest first, with what each of them holds. */
    public List<SharedPotView> potsOf(String customerName) {
        ResponseEntity<SharedPotView[]> read = http.getForEntity(
                "/api/customers/{id}/shared-pots", SharedPotView[].class,
                seeded.customerIdOf(customerName));
        assertThat(read.getStatusCode())
                .describedAs("reading the shared pots " + customerName + " belongs to")
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /** The same read for a customer named by identifier, for a test about one who does not exist. */
    public ResponseEntity<JsonNode> tryToReadThePotsOf(long customerId) {
        return http.getForEntity("/api/customers/{id}/shared-pots", JsonNode.class, customerId);
    }

    /**
     * Every savings account the customer holds, for a test about what is — and is not — theirs.
     *
     * <p>Read off their own overview, which is where the question is really being asked: a pot's
     * account belongs to the pot, and the whole of what that means to this customer is that it is
     * not in this list.
     */
    public List<Long> savingsAccountsOf(String customerName) {
        return seeded.savingsAccountsOf(customerName);
    }

    /** Everything the customer currently holds in savings, summed across every account of theirs. */
    public BigDecimal stillSavedBy(String customerName) {
        return seeded.stillSavedBy(customerName);
    }

    /** An identifier no customer has, for a test about being told so. */
    public long anIdNoCustomerHas() {
        return seeded.anIdNoCustomerHas();
    }

    /**
     * The most the customer has ever had in savings, across every account they hold.
     *
     * <p>The figure an enrolment records as the mark it measures from, read off the same overview
     * the customer reads it off. A test that asserts a challenge's reading against it is asserting
     * that the challenge and the points ledger are answering the same question, which is the one
     * decision the whole feature rests on.
     */
    public BigDecimal mostEverSavedOf(String customerName) {
        return seeded.mostEverSavedOf(customerName);
    }

    /**
     * Every challenge open to the customer, with their standing in each — the read the Challenges
     * tab makes.
     *
     * <p>The status is insisted on for the reason {@link #deposit} gives: {@code getForObject} hands
     * back the error body rather than throwing, so a read that had been refused would arrive as a
     * list of nulls and a test would fail somewhere further down, about a field rather than about a
     * refusal. The test whose subject <em>is</em> the refusal asks
     * {@link #tryToReadTheChallengesOf} instead.
     */
    public List<ChallengeView> challengesOf(String customerName) {
        ResponseEntity<ChallengeView[]> read = http.getForEntity(
                "/api/customers/{id}/challenges", ChallengeView[].class,
                seeded.customerIdOf(customerName));
        assertThat(read.getStatusCode())
                .describedAs("reading the challenges open to " + customerName + ": " + read.getBody())
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /**
     * One of those challenges by its code, because every assertion in this feature is about a named
     * challenge and picking it out of the list by hand is the same three lines every time.
     */
    public ChallengeView challengeOf(String customerName, String code) {
        return challengesOf(customerName).stream()
                .filter(challenge -> code.equals(challenge.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no challenge called " + code + " is open to "
                        + customerName + "; the ones that are: "
                        + challengesOf(customerName).stream().map(ChallengeView::code).toList()));
    }

    /** The same read for a customer named by identifier, for a test about one who does not exist. */
    public ResponseEntity<JsonNode> tryToReadTheChallengesOf(long customerId) {
        return http.getForEntity("/api/customers/{id}/challenges", JsonNode.class, customerId);
    }

    /**
     * An enrolment this test needed to take, with the refusal ruled out: a customer who never joined
     * would leave a test asserting that a challenge nobody is in reads nothing — and passing.
     */
    public EnrolmentView enrolIn(String customerName, String code) {
        ResponseEntity<EnrolmentView> enrolled = tryToEnrolIn(customerName, code);
        assertThat(enrolled.getStatusCode())
                .describedAs("enrolling " + customerName + " in " + code + ": " + enrolled.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return enrolled.getBody();
    }

    /** The same request answered with whatever the API answered, for a test about being refused. */
    public ResponseEntity<EnrolmentView> tryToEnrolIn(String customerName, String code) {
        return http.postForEntity("/api/customers/{id}/challenges/{code}/enrolments", null,
                EnrolmentView.class, seeded.customerIdOf(customerName), code);
    }

    /** And for a customer named by identifier, which is the only way to name one who is not there. */
    public ResponseEntity<JsonNode> tryToEnrol(long customerId, String code) {
        return http.postForEntity("/api/customers/{id}/challenges/{code}/enrolments", null,
                JsonNode.class, customerId, code);
    }

    /**
     * Leaving a challenge, with the refusal ruled out, and answering with the enrolment as it now
     * stands — because the claim being made is that it was ended rather than removed.
     */
    public EnrolmentView leave(String customerName, String code) {
        ResponseEntity<EnrolmentView> left = tryToLeave(customerName, code);
        assertThat(left.getStatusCode())
                .describedAs("leaving " + code + " as " + customerName + ": " + left.getBody())
                .isEqualTo(HttpStatus.OK);
        return left.getBody();
    }

    /** The same request answered with whatever the API answered, for a test about being refused. */
    public ResponseEntity<EnrolmentView> tryToLeave(String customerName, String code) {
        return http.exchange("/api/customers/{id}/challenges/{code}/enrolments", HttpMethod.DELETE,
                null, EnrolmentView.class, seeded.customerIdOf(customerName), code);
    }

    /**
     * Everything the customer has ever achieved as the API reports it, newest first — the trophy
     * case, which is the only way from outside to say that a rung was awarded at all.
     *
     * <p>The status is insisted on for the reason {@link #deposit} gives: {@code getForObject} hands
     * back the error body rather than throwing, so a read that had been refused would arrive as an
     * empty list and would satisfy every "nothing has been won yet" assertion ever written against
     * it. The test whose subject <em>is</em> the refusal asks {@link #tryToReadTheAchievementsOf}
     * instead.
     */
    public List<AchievementView> achievementsOf(String customerName) {
        ResponseEntity<AchievementView[]> read = http.getForEntity(
                "/api/customers/{id}/achievements", AchievementView[].class,
                seeded.customerIdOf(customerName));
        assertThat(read.getStatusCode())
                .describedAs("reading the trophy case of " + customerName + ": " + read.getBody())
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /** The same read for a customer named by identifier, for a test about one who does not exist. */
    public ResponseEntity<JsonNode> tryToReadTheAchievementsOf(long customerId) {
        return http.getForEntity("/api/customers/{id}/achievements", JsonNode.class, customerId);
    }

    /**
     * One savings account as the API answers for it, whatever it answers — for a test asking what
     * the personal account page makes of an account nobody holds.
     */
    public ResponseEntity<JsonNode> tryToReadTheSavingsAccount(long savingsAccountId) {
        return http.getForEntity("/api/savings-accounts/{id}", JsonNode.class, savingsAccountId);
    }

    /**
     * An invitation this test needed sent, with the refusal ruled out: an invitation that was turned
     * down would leave a test asserting that nobody accepted anything — and passing.
     *
     * <p>The person invited is named to this method by name and reaches the API as the email address
     * they bank under, because that is what the contract carries — the same translation
     * {@link #give} makes for a gift, and for the same reason: a test says who, and the wire says
     * how they are addressed.
     */
    public PotInvitationView invite(long potId, String ownerName, String invitedName, String role) {
        ResponseEntity<PotInvitationView> sent = http.postForEntity(
                "/api/shared-pots/{potId}/invitations",
                Map.of("contactDetails", seeded.contactDetailsOf(invitedName),
                        "role", role,
                        "customerId", seeded.customerIdOf(ownerName)),
                PotInvitationView.class,
                potId);
        assertThat(sent.getStatusCode())
                .describedAs("inviting " + invitedName + " into shared pot " + potId + " as "
                        + role + ": " + sent.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return sent.getBody();
    }

    /**
     * The same request with whatever body is given here, answered with whatever the API answered —
     * for a test whose subject is the refusal, where the point is the sentence and not that somebody
     * was invited.
     *
     * <p>{@code HashMap} at the call site rather than {@code Map.of}, so that a test about a missing
     * field can send a null, which {@code Map.of} will not hold.
     */
    public ResponseEntity<JsonNode> tryToInvite(long potId, Map<String, Object> invitation) {
        return http.postForEntity("/api/shared-pots/{potId}/invitations", invitation,
                JsonNode.class, potId);
    }

    /** Every invitation the pot has issued, in any state, oldest first. */
    public List<PotInvitationView> invitationsIssuedBy(long potId) {
        ResponseEntity<PotInvitationView[]> read = http.getForEntity(
                "/api/shared-pots/{potId}/invitations", PotInvitationView[].class, potId);
        assertThat(read.getStatusCode())
                .describedAs("reading the invitations shared pot " + potId + " has issued")
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /** The same read without insisting it succeeded, for a test about a pot that is not there. */
    public ResponseEntity<JsonNode> tryToReadTheInvitationsIssuedBy(long potId) {
        return http.getForEntity("/api/shared-pots/{potId}/invitations", JsonNode.class, potId);
    }

    /** The invitations waiting for this customer to answer, which is the panel they would read. */
    public List<PotInvitationView> invitationsWaitingFor(String customerName) {
        ResponseEntity<PotInvitationView[]> read = http.getForEntity(
                "/api/customers/{id}/pot-invitations", PotInvitationView[].class,
                seeded.customerIdOf(customerName));
        assertThat(read.getStatusCode())
                .describedAs("reading the invitations waiting for " + customerName)
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /** The same read for a customer named by identifier, for a test about one who does not exist. */
    public ResponseEntity<JsonNode> tryToReadTheInvitationsWaitingFor(long customerId) {
        return http.getForEntity("/api/customers/{id}/pot-invitations", JsonNode.class, customerId);
    }

    /**
     * The invited customer accepts, with the refusal ruled out: an acceptance that was turned down
     * would leave a test asserting that somebody who never joined is not a member — and passing.
     */
    public PotInvitationView accept(long potId, long invitationId, String customerName) {
        ResponseEntity<PotInvitationView> accepted = http.postForEntity(
                "/api/shared-pots/{potId}/invitations/{id}/accept",
                Map.of("customerId", seeded.customerIdOf(customerName)),
                PotInvitationView.class, potId, invitationId);
        assertThat(accepted.getStatusCode())
                .describedAs(customerName + " accepting invitation " + invitationId + " to shared pot "
                        + potId + ": " + accepted.getBody())
                .isEqualTo(HttpStatus.OK);
        return accepted.getBody();
    }

    /** The same answer without insisting it succeeded, for a test whose subject is the refusal. */
    public ResponseEntity<JsonNode> tryToAccept(long potId, long invitationId, long customerId) {
        return http.postForEntity("/api/shared-pots/{potId}/invitations/{id}/accept",
                Map.of("customerId", customerId), JsonNode.class, potId, invitationId);
    }

    /** The invited customer declines, insisted on the same way. */
    public PotInvitationView decline(long potId, long invitationId, String customerName) {
        ResponseEntity<PotInvitationView> declined = http.postForEntity(
                "/api/shared-pots/{potId}/invitations/{id}/decline",
                Map.of("customerId", seeded.customerIdOf(customerName)),
                PotInvitationView.class, potId, invitationId);
        assertThat(declined.getStatusCode())
                .describedAs(customerName + " declining invitation " + invitationId + " to shared pot "
                        + potId + ": " + declined.getBody())
                .isEqualTo(HttpStatus.OK);
        return declined.getBody();
    }

    /** The same answer without insisting it succeeded, for a test whose subject is the refusal. */
    public ResponseEntity<JsonNode> tryToDecline(long potId, long invitationId, long customerId) {
        return http.postForEntity("/api/shared-pots/{potId}/invitations/{id}/decline",
                Map.of("customerId", customerId), JsonNode.class, potId, invitationId);
    }

    /**
     * An owner takes an invitation back before anybody answered it, insisted on the same way.
     *
     * <p>The acting customer travels as a query parameter rather than in a body, because that is
     * what the contract carries for a DELETE — and a test that sent it any other way would not be
     * driving the API anybody else drives.
     */
    public PotInvitationView revoke(long potId, long invitationId, String customerName) {
        ResponseEntity<PotInvitationView> revoked = http.exchange(
                "/api/shared-pots/{potId}/invitations/{id}?customerId={customerId}",
                HttpMethod.DELETE, null, PotInvitationView.class,
                potId, invitationId, seeded.customerIdOf(customerName));
        assertThat(revoked.getStatusCode())
                .describedAs(customerName + " revoking invitation " + invitationId + " to shared pot "
                        + potId + ": " + revoked.getBody())
                .isEqualTo(HttpStatus.OK);
        return revoked.getBody();
    }

    /** The same revocation without insisting it succeeded, for a test whose subject is the refusal. */
    public ResponseEntity<JsonNode> tryToRevoke(long potId, long invitationId, long customerId) {
        return http.exchange("/api/shared-pots/{potId}/invitations/{id}?customerId={customerId}",
                HttpMethod.DELETE, null, JsonNode.class, potId, invitationId, customerId);
    }

    /** The same revocation with nobody saying who is doing it, which is a form that was never filled in. */
    public ResponseEntity<JsonNode> tryToRevokeAsNobody(long potId, long invitationId) {
        return http.exchange("/api/shared-pots/{potId}/invitations/{id}", HttpMethod.DELETE, null,
                JsonNode.class, potId, invitationId);
    }

    /**
     * An owner changes what somebody is to the pot, with the refusal ruled out: a change that was
     * turned down would leave a test asserting that a viewer still cannot pay in — and passing for
     * the wrong reason entirely.
     *
     * <p>Both people are named here by name and reach the API as identifiers, the way every other
     * helper in this class translates: the member whose role it is travels in the path, because the
     * membership is what is being changed, and the owner making the change travels in the body,
     * because that is what the contract carries for a PUT.
     */
    public PotMemberView changeTheRole(long potId, String memberName, String role, String ownerName) {
        ResponseEntity<PotMemberView> changed = http.exchange(
                "/api/shared-pots/{potId}/members/{customerId}/role", HttpMethod.PUT,
                new HttpEntity<>(Map.of("role", role,
                        "customerId", seeded.customerIdOf(ownerName))),
                PotMemberView.class, potId, seeded.customerIdOf(memberName));
        assertThat(changed.getStatusCode())
                .describedAs(ownerName + " making " + memberName + " a " + role + " of shared pot "
                        + potId + ": " + changed.getBody())
                .isEqualTo(HttpStatus.OK);
        return changed.getBody();
    }

    /**
     * The same change with whatever body is given here, about whichever customer is named — for a
     * test whose subject is the refusal, where the point is the sentence and not that a role changed.
     *
     * <p>{@code HashMap} at the call site rather than {@code Map.of}, so that a test about a missing
     * field can send a null, which {@code Map.of} will not hold.
     */
    public ResponseEntity<JsonNode> tryToChangeTheRole(long potId, long memberCustomerId,
                                                       Map<String, Object> change) {
        return http.exchange("/api/shared-pots/{potId}/members/{customerId}/role", HttpMethod.PUT,
                new HttpEntity<>(change), JsonNode.class, potId, memberCustomerId);
    }

    /**
     * A member leaves the pot and is settled what is still theirs, with the refusal ruled out: a
     * departure that was turned down would leave a test asserting that the other member's money was
     * untouched — and passing for the wrong reason entirely, since nothing moves on a refusal
     * either.
     *
     * <p>The money comes back to the leaving member's own current account, which is the ordinary
     * case and the only one the API allows; a test about somebody else's account names it through
     * {@link #tryToLeaveThePot}.
     *
     * <p>Everybody is named here by name and reaches the API as an identifier, the way every other
     * helper in this class translates. The member travels in the path because their membership is
     * what ends, and the person deciding travels as a query parameter, because that is what the
     * contract carries for a DELETE — a test that sent it any other way would not be driving the API
     * anybody else drives.
     */
    public PotSettlementView leaveThePot(long potId, String memberName, String decidedByName) {
        ResponseEntity<PotSettlementView> left = http.exchange(
                "/api/shared-pots/{potId}/members/{memberId}"
                        + "?customerId={customerId}&toCurrentAccountId={toCurrentAccountId}",
                HttpMethod.DELETE, null, PotSettlementView.class, potId,
                seeded.customerIdOf(memberName), seeded.customerIdOf(decidedByName),
                seeded.currentAccountOf(memberName));
        assertThat(left.getStatusCode())
                .describedAs(decidedByName + " taking " + memberName + " out of shared pot " + potId
                        + ": " + left.getBody())
                .isEqualTo(HttpStatus.OK);
        return left.getBody();
    }

    /**
     * The same departure with whichever identifiers are given here, answered with whatever the API
     * answered — for a test whose subject is the refusal, where the point is the sentence and not
     * that anybody left.
     *
     * <p>Identifiers rather than names, because half of what these tests refuse is a customer this
     * application has never heard of and an account belonging to the wrong person, neither of which
     * has a name to be translated from.
     */
    public ResponseEntity<JsonNode> tryToLeaveThePot(long potId, long memberCustomerId,
                                                     long decidedByCustomerId,
                                                     long toCurrentAccountId) {
        return http.exchange("/api/shared-pots/{potId}/members/{memberId}"
                        + "?customerId={customerId}&toCurrentAccountId={toCurrentAccountId}",
                HttpMethod.DELETE, null, JsonNode.class, potId, memberCustomerId,
                decidedByCustomerId, toCurrentAccountId);
    }

    /** The same with nobody saying who is doing it, which is a form that was never filled in. */
    public ResponseEntity<JsonNode> tryToLeaveThePotAsNobody(long potId, long memberCustomerId,
                                                             long toCurrentAccountId) {
        return http.exchange(
                "/api/shared-pots/{potId}/members/{memberId}?toCurrentAccountId={toCurrentAccountId}",
                HttpMethod.DELETE, null, JsonNode.class, potId, memberCustomerId,
                toCurrentAccountId);
    }

    /** And with nowhere named for the money to come back to, which is the other empty box. */
    public ResponseEntity<JsonNode> tryToLeaveThePotWithNowhereToSettleInto(long potId,
                                                                            long memberCustomerId,
                                                                            long decidedByCustomerId) {
        return http.exchange(
                "/api/shared-pots/{potId}/members/{memberId}?customerId={customerId}",
                HttpMethod.DELETE, null, JsonNode.class, potId, memberCustomerId,
                decidedByCustomerId);
    }

    /**
     * An owner closes the pot, with the refusal ruled out: a close that was turned down would leave
     * a test asserting that nothing more can be done to the pot — and passing for the wrong reason
     * entirely, since nothing moves on a refused close either.
     *
     * <p>Answers with everything the close did, because that is what the endpoint answers with and
     * because the whole of this slice is what one request does to several people's money at once.
     *
     * <p>Nobody names an account. Closing settles each member into the current account their most
     * recent contribution came from, which is the contract: a test that had to name one would be
     * driving an API nobody else drives.
     */
    public PotClosedView closeThePot(long potId, String customerName) {
        ResponseEntity<PotClosedView> closed = http.postForEntity(
                "/api/shared-pots/{potId}/close",
                Map.of("customerId", seeded.customerIdOf(customerName)),
                PotClosedView.class, potId);
        assertThat(closed.getStatusCode())
                .describedAs(customerName + " closing shared pot " + potId + ": " + closed.getBody())
                .isEqualTo(HttpStatus.OK);
        return closed.getBody();
    }

    /**
     * The same request with whatever body is given here, answered with whatever the API answered —
     * for a test whose subject is the refusal, where the point is the sentence and not that a pot
     * was closed.
     *
     * <p>{@code HashMap} at the call site rather than {@code Map.of}, so that a test about a missing
     * field can send a null, which {@code Map.of} will not hold.
     */
    public ResponseEntity<JsonNode> tryToClose(long potId, Map<String, Object> closing) {
        return http.postForEntity("/api/shared-pots/{potId}/close", closing, JsonNode.class, potId);
    }

    /** The same close by whichever customer is named, for a test about somebody who may not make it. */
    public ResponseEntity<JsonNode> tryToClose(long potId, long customerId) {
        return tryToClose(potId, Map.of("customerId", customerId));
    }

    /**
     * A gift answered as unshaped JSON, for a test comparing the sentence a refused gift gives with
     * the sentence some other module gives for the same objection.
     *
     * <p>Read as JSON rather than as a gift for the reason {@code AGiftIsRefusedInWordsApiTest}
     * gives: asking for the response as a gift would read the error body as an empty one and lose
     * the sentence before the status could be looked at.
     */
    public ResponseEntity<JsonNode> tryToGive(String senderName, String recipientAsTyped,
                                              String points) {
        return http.postForEntity("/api/customers/{id}/gifts",
                Map.of("recipientContactDetails", recipientAsTyped, "points", points),
                JsonNode.class, seeded.customerIdOf(senderName));
    }

    /**
     * Signing in with whatever address is given here, answered with whatever the API answered — for
     * a test asserting that two modules say the same thing about an address nobody banks under.
     */
    public ResponseEntity<JsonNode> trySigningIn(String contactDetails) {
        return http.postForEntity("/api/customers/sign-in", Map.of("contactDetails", contactDetails),
                JsonNode.class);
    }

    /**
     * A proposal to take money out of a shared pot, with the refusal ruled out: a proposal that was
     * turned down would leave a test asserting that nothing moved — and passing for the wrong
     * reason, since nothing moves when one is made either.
     *
     * <p>The money comes back to the proposer's own current account, which is the ordinary case and
     * the only one the API allows; a test about somebody else's account names it through
     * {@link #tryToPropose}. The amount goes over the wire as the text a member would type, the way
     * a deposit's does.
     */
    public WithdrawalProposalView proposeAWithdrawal(long potId, String customerName, String amount) {
        ResponseEntity<WithdrawalProposalView> proposed = http.postForEntity(
                "/api/shared-pots/{potId}/withdrawal-proposals",
                Map.of("amount", amount,
                        "toCurrentAccountId", seeded.currentAccountOf(customerName),
                        "customerId", seeded.customerIdOf(customerName)),
                WithdrawalProposalView.class, potId);
        assertThat(proposed.getStatusCode())
                .describedAs(customerName + " proposing a withdrawal of " + amount
                        + " from shared pot " + potId + ": " + proposed.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return proposed.getBody();
    }

    /**
     * The same request with whatever body is given here, answered with whatever the API answered —
     * for a test whose subject is the refusal, where the point is the sentence and not that a
     * proposal was made.
     *
     * <p>{@code HashMap} at the call site rather than {@code Map.of}, so that a test about a missing
     * field can send a null, which {@code Map.of} will not hold.
     */
    public ResponseEntity<JsonNode> tryToPropose(long potId, Map<String, Object> proposal) {
        return http.postForEntity("/api/shared-pots/{potId}/withdrawal-proposals", proposal,
                JsonNode.class, potId);
    }

    /** Every proposal the pot has ever had, in any state, oldest first. */
    public List<WithdrawalProposalView> withdrawalProposalsOf(long potId) {
        ResponseEntity<WithdrawalProposalView[]> read = http.getForEntity(
                "/api/shared-pots/{potId}/withdrawal-proposals", WithdrawalProposalView[].class,
                potId);
        assertThat(read.getStatusCode())
                .describedAs("reading the withdrawal proposals of shared pot " + potId)
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /** The same read without insisting it succeeded, for a test about a pot that is not there. */
    public ResponseEntity<JsonNode> tryToReadTheProposalsOf(long potId) {
        return http.getForEntity("/api/shared-pots/{potId}/withdrawal-proposals", JsonNode.class,
                potId);
    }

    /**
     * A member approves a withdrawal proposal, with the refusal ruled out: an approval that was
     * turned down would leave a test asserting that the money had not moved — and passing for the
     * wrong reason entirely, since nothing moves on a refused approval either.
     *
     * <p>Answers with the proposal as it now reads, because the whole of this slice is what the last
     * approval does to it: a page that approves is showing the row it just changed, and so is a test.
     */
    public WithdrawalProposalView approve(long potId, long proposalId, String customerName) {
        ResponseEntity<WithdrawalProposalView> approved = http.postForEntity(
                "/api/shared-pots/{potId}/withdrawal-proposals/{id}/approve",
                Map.of("customerId", seeded.customerIdOf(customerName)),
                WithdrawalProposalView.class, potId, proposalId);
        assertThat(approved.getStatusCode())
                .describedAs(customerName + " approving proposal " + proposalId + " to shared pot "
                        + potId + ": " + approved.getBody())
                .isEqualTo(HttpStatus.OK);
        return approved.getBody();
    }

    /** The same approval without insisting it succeeded, for a test whose subject is the refusal. */
    public ResponseEntity<JsonNode> tryToApprove(long potId, long proposalId, long customerId) {
        return http.postForEntity("/api/shared-pots/{potId}/withdrawal-proposals/{id}/approve",
                Map.of("customerId", customerId), JsonNode.class, potId, proposalId);
    }

    /** And with nobody saying who is doing it, which is a form that was never filled in. */
    public ResponseEntity<JsonNode> tryToApproveAsNobody(long potId, long proposalId) {
        return http.postForEntity("/api/shared-pots/{potId}/withdrawal-proposals/{id}/approve",
                Map.of(), JsonNode.class, potId, proposalId);
    }

    /**
     * A member rejects a withdrawal proposal, insisted on the same way: a rejection that was turned
     * down would leave a test asserting that the pot still held its money, which it would have
     * whether or not anybody said no.
     */
    public WithdrawalProposalView reject(long potId, long proposalId, String customerName) {
        ResponseEntity<WithdrawalProposalView> rejected = http.postForEntity(
                "/api/shared-pots/{potId}/withdrawal-proposals/{id}/reject",
                Map.of("customerId", seeded.customerIdOf(customerName)),
                WithdrawalProposalView.class, potId, proposalId);
        assertThat(rejected.getStatusCode())
                .describedAs(customerName + " rejecting proposal " + proposalId + " to shared pot "
                        + potId + ": " + rejected.getBody())
                .isEqualTo(HttpStatus.OK);
        return rejected.getBody();
    }

    /** The same rejection without insisting it succeeded, for a test whose subject is the refusal. */
    public ResponseEntity<JsonNode> tryToReject(long potId, long proposalId, long customerId) {
        return http.postForEntity("/api/shared-pots/{potId}/withdrawal-proposals/{id}/reject",
                Map.of("customerId", customerId), JsonNode.class, potId, proposalId);
    }

    /**
     * The member who proposed a withdrawal takes it back, insisted on the same way.
     *
     * <p>The acting customer travels as a query parameter rather than in a body, because that is
     * what the contract carries for a DELETE — and a test that sent it any other way would not be
     * driving the API anybody else drives.
     */
    public WithdrawalProposalView takeBackTheProposal(long potId, long proposalId,
                                                      String customerName) {
        ResponseEntity<WithdrawalProposalView> takenBack = http.exchange(
                "/api/shared-pots/{potId}/withdrawal-proposals/{id}?customerId={customerId}",
                HttpMethod.DELETE, null, WithdrawalProposalView.class,
                potId, proposalId, seeded.customerIdOf(customerName));
        assertThat(takenBack.getStatusCode())
                .describedAs(customerName + " taking back proposal " + proposalId + " to shared pot "
                        + potId + ": " + takenBack.getBody())
                .isEqualTo(HttpStatus.OK);
        return takenBack.getBody();
    }

    /** The same without insisting it succeeded, for a test whose subject is the refusal. */
    public ResponseEntity<JsonNode> tryToTakeBackTheProposal(long potId, long proposalId,
                                                             long customerId) {
        return http.exchange(
                "/api/shared-pots/{potId}/withdrawal-proposals/{id}?customerId={customerId}",
                HttpMethod.DELETE, null, JsonNode.class, potId, proposalId, customerId);
    }

    /** The same with nobody saying who is doing it, which is a form that was never filled in. */
    public ResponseEntity<JsonNode> tryToTakeBackTheProposalAsNobody(long potId, long proposalId) {
        return http.exchange("/api/shared-pots/{potId}/withdrawal-proposals/{id}",
                HttpMethod.DELETE, null, JsonNode.class, potId, proposalId);
    }

    /**
     * What a member of a pot would type to propose a withdrawal, as the body that travels.
     *
     * <p>A {@code HashMap} so that a test about a field nobody filled in can send a null, and here
     * rather than in a fixture of its own because the three things a proposal carries are all any
     * test needs to vary.
     */
    public Map<String, Object> aWithdrawalOf(String amount, Long toCurrentAccountId,
                                             Long customerId) {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("amount", amount);
        proposal.put("toCurrentAccountId", toCurrentAccountId);
        proposal.put("customerId", customerId);
        return proposal;
    }

    /**
     * What the scheme says today, as any customer reads it — and as the pre-filled publishing form
     * is built from.
     *
     * <p>Here as well as on {@code ASchemeSomebodyAdministers} because a test whose subject is a
     * rule the scheme prices needs both halves in one application: the deposits, the spends and the
     * nightly jobs this class already drives, and the door that changes a figure. The publishing
     * fixture has its own application because publishing cannot be undone — and so does every test
     * that reaches these two methods, for exactly the same reason and by the same arrangement.
     */
    public SchemeView theSchemeInForce() {
        return http.getForObject("/api/scheme", SchemeView.class);
    }

    /**
     * Publishes a version of the scheme through the administration door, with the refusal ruled out
     * rather than assumed: a refused publish would leave a test asserting that nothing changed —
     * and passing. The same insistence, for the same reason,
     * {@code ASchemeSomebodyAdministers.publish} makes.
     */
    public SchemeVersionPublishedView publishAVersionOfTheScheme(Map<String, Object> form) {
        ResponseEntity<SchemeVersionPublishedView> published = http.postForEntity(
                "/api/admin/scheme/versions", form, SchemeVersionPublishedView.class);
        assertThat(published.getStatusCode())
                .as("publishing a version of the scheme: %s", form)
                .isEqualTo(HttpStatus.CREATED);
        return published.getBody();
    }

    /**
     * The first Monday strictly after the day this application thinks it is, which is the earliest
     * day a version of the scheme may take effect on.
     *
     * <p>Worked out from the application's clock rather than the machine's, because the rule it is
     * about is worked out from the application's clock — a test that computed "next Monday" from
     * {@code LocalDate.now()} would pass or fail depending on how far it had already wound.
     */
    public LocalDate theNextMondayStillToCome() {
        return theDateTheClockReads().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    /**
     * Winds the clock forward until the application says it is that day, which is how a version
     * announced for a Monday is watched coming into force.
     *
     * <p>Forward only, and it says so by insisting: this clock can be wound back, and a test that
     * quietly went backwards to reach a date would be asserting about a morning that had already
     * happened.
     */
    public void theClockReaches(LocalDate day) {
        LocalDate today = theDateTheClockReads();
        assertThat(day).as("a day this test can wind the clock forward to").isAfterOrEqualTo(today);
        daysPass(ChronoUnit.DAYS.between(today, day));
        assertThat(theDateTheClockReads()).isEqualTo(day);
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
     * One part of a split as somebody types it: an amount, and a category or none at all.
     *
     * <p>A record rather than a map at every call site, so that a test reads as the split it is
     * describing. The amount is text, because it is text that travels: a customer types "20.00" and
     * what the application makes of it is the application's answer rather than the test's.
     */
    public record PartAsTyped(Long categoryId, String amount) {

        /** A part filed under a category. */
        public static PartAsTyped filedUnder(long categoryId, String amount) {
            return new PartAsTyped(categoryId, amount);
        }

        /** A part the customer has not decided about, which is a state rather than a gap. */
        public static PartAsTyped unfiled(String amount) {
            return new PartAsTyped(null, amount);
        }
    }

    /**
     * A refusal as the API answers with one (RFC 9457), of which these tests read the sentence the
     * customer is shown and nothing else.
     */
    private record ProblemView(String detail) {
    }

    // ------------------------------------------------ a pot's goals, asked for by somebody in it

    /**
     * The goals on an account as one named customer reads them, insisted on.
     *
     * <p>The same read {@link #goalsOn} makes, with {@code ?customerId=} on it — which is how a
     * member of a shared pot reads what the pot is saving for, and how a pot tells a member from a
     * stranger. Insisted on, because a read that was refused would leave a test asserting about an
     * empty list and passing for the wrong reason.
     */
    public List<GoalView> goalsAsReadBy(long savingsAccountId, String customerName) {
        ResponseEntity<GoalView[]> read = http.getForEntity(
                askedBy("/api/savings-accounts/" + savingsAccountId + "/goals", customerName),
                GoalView[].class);
        assertThat(read.getStatusCode())
                .describedAs(customerName + " reading the goals on savings account "
                        + savingsAccountId + ": " + read.getBody())
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /** The same read without insisting it succeeded, for a test whose subject is the refusal. */
    public ResponseEntity<JsonNode> tryToReadTheGoals(long savingsAccountId, String customerName) {
        return http.getForEntity(
                askedBy("/api/savings-accounts/" + savingsAccountId + "/goals", customerName),
                JsonNode.class);
    }

    /** One goal of an account, whatever the API answers, asked for by somebody or by nobody. */
    public ResponseEntity<JsonNode> tryToReadTheGoal(long savingsAccountId, long goalId,
                                                     String customerName) {
        return http.getForEntity(askedBy("/api/savings-accounts/" + savingsAccountId + "/goals/"
                + goalId, customerName), JsonNode.class);
    }

    /** What the account has allocated to its goals, whatever the API answers. */
    public ResponseEntity<JsonNode> tryToReadTheAllocations(long savingsAccountId,
                                                            String customerName) {
        return http.getForEntity(askedBy("/api/savings-accounts/" + savingsAccountId
                + "/goals/allocations", customerName), JsonNode.class);
    }

    /** What the account's holder says they can save each week, whatever the API answers. */
    public ResponseEntity<JsonNode> tryToReadTheSavingCapacity(long savingsAccountId,
                                                               String customerName) {
        return http.getForEntity(askedBy("/api/savings-accounts/" + savingsAccountId
                + "/saving-capacity", customerName), JsonNode.class);
    }

    /**
     * A goal opened on the account by a named customer, insisted on — the request an owner of a
     * shared pot makes when the group decides what it is saving for.
     */
    public GoalView openAGoalAs(long savingsAccountId, String customerName, String name,
                                String target) {
        return openAGoalAs(savingsAccountId, customerName, name, target, null);
    }

    /**
     * The same, with a day the group wants it by — which is what puts the goal into the weekly plan
     * and gives it a status to be on track or late against.
     *
     * <p>The deadline travels as the text a page sends, like every other field here, and a
     * {@code HashMap} carries it so that "no deadline at all" is a null rather than an absent key.
     */
    public GoalView openAGoalAs(long savingsAccountId, String customerName, String name,
                                String target, LocalDate deadline) {
        Map<String, Object> goal = new HashMap<>();
        goal.put("name", name);
        goal.put("target", target);
        goal.put("deadline", deadline == null ? null : deadline.toString());
        ResponseEntity<GoalView> opened = http.postForEntity(
                askedBy("/api/savings-accounts/" + savingsAccountId + "/goals", customerName),
                goal, GoalView.class);
        assertThat(opened.getStatusCode())
                .describedAs(customerName + " opening the goal \"" + name + "\" on savings account "
                        + savingsAccountId + ": " + opened.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return opened.getBody();
    }

    /** The same request without insisting it succeeded, for a test whose subject is the refusal. */
    public ResponseEntity<JsonNode> tryToOpenAGoal(long savingsAccountId, String customerName,
                                                   String name, String target) {
        return http.postForEntity(
                askedBy("/api/savings-accounts/" + savingsAccountId + "/goals", customerName),
                Map.of("name", name, "target", target), JsonNode.class);
    }

    /** A change to a goal's name, sent by whoever is named, whatever the API answers. */
    public ResponseEntity<JsonNode> tryToRenameTheGoal(long savingsAccountId, long goalId,
                                                       String customerName, String name) {
        return http.exchange(askedBy("/api/savings-accounts/" + savingsAccountId + "/goals/"
                        + goalId, customerName), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("name", name)), JsonNode.class);
    }

    /** The whole order at once, sent by whoever is named, whatever the API answers. */
    public ResponseEntity<JsonNode> tryToReorderTheGoals(long savingsAccountId, String customerName,
                                                         List<Long> goalIds) {
        return http.exchange(askedBy("/api/savings-accounts/" + savingsAccountId + "/goals/order",
                        customerName), HttpMethod.PUT,
                new HttpEntity<>(Map.of("goalIds", goalIds)), JsonNode.class);
    }

    /** A weekly figure pinned to one goal, sent by whoever is named, whatever the API answers. */
    public ResponseEntity<JsonNode> tryToPinAWeeklyAmount(long savingsAccountId, long goalId,
                                                          String customerName, String weekly) {
        return http.exchange(askedBy("/api/savings-accounts/" + savingsAccountId + "/goals/"
                        + goalId + "/weekly-amount", customerName), HttpMethod.PUT,
                new HttpEntity<>(Map.of("weeklyAmount", weekly)), JsonNode.class);
    }

    /** The pin taken off again, sent by whoever is named, whatever the API answers. */
    public ResponseEntity<JsonNode> tryToUnpinTheWeeklyAmount(long savingsAccountId, long goalId,
                                                              String customerName) {
        return http.exchange(askedBy("/api/savings-accounts/" + savingsAccountId + "/goals/"
                        + goalId + "/weekly-amount", customerName), HttpMethod.DELETE,
                HttpEntity.EMPTY, JsonNode.class);
    }

    /** A goal given up on, sent by whoever is named, whatever the API answers. */
    public ResponseEntity<JsonNode> tryToAbandonTheGoal(long savingsAccountId, long goalId,
                                                        String customerName) {
        return http.postForEntity(askedBy("/api/savings-accounts/" + savingsAccountId + "/goals/"
                + goalId + "/abandon", customerName), null, JsonNode.class);
    }

    /** Money moved into a goal out of what no goal has claimed, whatever the API answers. */
    public ResponseEntity<JsonNode> tryToAllocate(long savingsAccountId, long goalId,
                                                  String customerName, String amount) {
        return http.postForEntity(askedBy("/api/savings-accounts/" + savingsAccountId + "/goals/"
                        + goalId + "/allocations", customerName),
                Map.of("amount", amount, "direction", "INTO_THE_GOAL"), JsonNode.class);
    }

    /** The weekly capacity declared by whoever is named, whatever the API answers. */
    public ResponseEntity<JsonNode> tryToDeclareTheSavingCapacity(long savingsAccountId,
                                                                  String customerName,
                                                                  String weekly) {
        return http.exchange(askedBy("/api/savings-accounts/" + savingsAccountId
                        + "/saving-capacity", customerName), HttpMethod.PUT,
                new HttpEntity<>(Map.of("weeklyCapacity", weekly)), JsonNode.class);
    }

    /**
     * The same path with {@code ?customerId=} on it, or the path exactly as it was when nobody is
     * named.
     *
     * <p>Nobody named is the request every goals screen in this application has always sent, and it
     * has to stay sendable: a personal account's goals are its holder's own decision, and a test
     * that could not send the old request could not show that the old request still works.
     *
     * <p>The identifier is written into the path rather than passed as a template variable, because
     * these calls go through {@code getForEntity} and {@code exchange} with no variables of their
     * own and a query template would have to be expanded either way.
     */
    private String askedBy(String path, String customerName) {
        return customerName == null ? path : path + "?customerId=" + customerIdOf(customerName);
    }

    /**
     * Who has put what into the pot, as the member named reads it: paid in altogether, still theirs,
     * and what their contributions to this pot have earned them.
     *
     * <p>Insisted on as a read that succeeded, because a refusal would satisfy no assertion about
     * the arithmetic and would fail as a null rather than as a status. The test whose subject
     * <em>is</em> the refusal asks {@link #tryToReadTheContributions} instead.
     *
     * <p>Signed by whoever is asking, because this read is a members-only one: what two other people
     * have each put into a pot is nobody else's business, and the query parameter is how every read
     * in this feature says who is asking.
     */
    public List<PotContributionView> contributionsTo(long potId, String customerName) {
        ResponseEntity<PotContributionView[]> read = http.getForEntity(
                askedBy("/api/shared-pots/" + potId + "/contributions", customerName),
                PotContributionView[].class);
        assertThat(read.getStatusCode())
                .describedAs("reading the contributions to shared pot " + potId + " as "
                        + customerName + ": " + read.getBody())
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /**
     * The same read without insisting it succeeded, for a test about somebody who may not make it —
     * and with {@code null} for the customer, the request that names nobody at all.
     */
    public ResponseEntity<JsonNode> tryToReadTheContributions(long potId, String customerName) {
        return http.getForEntity(
                askedBy("/api/shared-pots/" + potId + "/contributions", customerName),
                JsonNode.class);
    }

    /**
     * Every movement in and out of the pot's savings account, newest first, as the member named
     * reads it — the same list, in the same shape, that a customer's own money movements arrive in.
     *
     * <p>Read through {@link MoneyMovementView}, which is the record every test of a personal
     * ledger already reads, because that sameness is the claim: a pot's history is not a second
     * screen to learn.
     */
    public List<MoneyMovementView> moneyMovementsOfThePot(long potId, String customerName) {
        ResponseEntity<MoneyMovementView[]> read = http.getForEntity(
                askedBy("/api/shared-pots/" + potId + "/money-movements", customerName),
                MoneyMovementView[].class);
        assertThat(read.getStatusCode())
                .describedAs("reading the money movements of shared pot " + potId + " as "
                        + customerName + ": " + read.getBody())
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /** The same read without insisting it succeeded, for a test about somebody who may not make it. */
    public ResponseEntity<JsonNode> tryToReadTheMoneyMovementsOfThePot(long potId,
                                                                       String customerName) {
        return http.getForEntity(
                askedBy("/api/shared-pots/" + potId + "/money-movements", customerName),
                JsonNode.class);
    }

    /**
     * One of the running application's own beans, handed to a test that has no other way to arrange
     * what it needs — the single place in this class that goes round the HTTP seam, and so the one
     * that has to say why.
     *
     * <p>A <em>one-off</em> challenge, one that cannot be taken on again once it is finished, is a
     * row in the definitions table, and the bank seeds none: both challenges it offers are
     * repeatable. There is no endpoint that offers a challenge either, and deliberately so — an
     * admin screen for definitions is out of scope — so a test that needs a definition the seed does
     * not write has nowhere else to write one.
     *
     * <p>The alternative was a seed row, and it was rejected: a challenge seeded for everybody would
     * change what every other test in the run finds on the challenges tab, in order to let one test
     * ask one question. A definition written into this application's own throwaway database is
     * visible to this test and to nothing else.
     *
     * <p>It widens nothing. A repository here is package-private to the module that owns it, so the
     * only caller that can name one is a test already inside that package, which could have booted
     * its own context and asked the same question the longer way round.
     */
    public <T> T aBeanOfTheApplication(Class<T> type) {
        return application.getBean(type);
    }

    /**
     * Takes money back out of a goal, into what no goal has claimed, insisted on — the other half of
     * {@link #allocate}, and how a test empties a goal it has already filled.
     *
     * <p>A test about a goal counting once needs to be able to un-finish one, because "finished,
     * emptied, finished again" is the case the rule is written for, and a goals ledger that only ever
     * went one way could not put a test in it.
     */
    public AllocationsView freeFromAGoal(long savingsAccountId, long goalId, String amount) {
        Map<String, Object> move = new HashMap<>();
        move.put("amount", amount);
        move.put("direction", "OUT_OF_THE_GOAL");
        ResponseEntity<AllocationsView> moved = http.postForEntity(
                "/api/savings-accounts/{id}/goals/{goalId}/allocations", move, AllocationsView.class,
                savingsAccountId, goalId);
        assertThat(moved.getStatusCode())
                .describedAs("freeing " + amount + " from goal " + goalId)
                .isEqualTo(HttpStatus.OK);
        return moved.getBody();
    }

    /**
     * Every season the bank runs, with its window and the challenges in it — the one read in this
     * feature that names no customer.
     *
     * <p>A season belongs to the bank rather than to anybody, which is why this takes no name: what
     * is running and until when is the same answer for everybody, in the way the rewards catalogue
     * is. Where a customer stands inside one is {@link #challengeOf}, on their own tab.
     *
     * <p>The status is insisted on for the reason {@link #deposit} gives: a read that had been
     * refused would arrive as a list of nulls and a test would fail somewhere further down, about a
     * field rather than about a refusal.
     */
    public List<CampaignView> campaigns() {
        ResponseEntity<CampaignView[]> read = http.getForEntity("/api/campaigns", CampaignView[].class);
        assertThat(read.getStatusCode())
                .describedAs("reading the seasons the bank runs: " + read.getBody())
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /**
     * One of those seasons by its code, because every assertion about a season is about a named one
     * and picking it out of the list by hand is the same three lines every time.
     */
    public CampaignView campaign(String code) {
        return campaigns().stream()
                .filter(season -> code.equals(season.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the bank runs no season called " + code
                        + "; the ones it does run: "
                        + campaigns().stream().map(CampaignView::code).toList()));
    }

    /**
     * Writes an offer into the catalogue and puts it on sale, insisted on at both steps — the
     * state every test about a window starts from, because a draft is refused for being a draft
     * long before anybody looks at its dates.
     *
     * <p>Here rather than on a helper of the test's own, because an offer with a season on it is
     * the one thing a clock-moving test cannot arrange any other way: the seeded four carry no
     * window, and the demonstration this whole feature exists for is "wind the clock on and watch
     * the card change". The offer arrives as the map of text an administration form would send,
     * so that a test can hand this whatever somebody could type and this class needs to know
     * nothing about how many fields an offer has.
     *
     * <p>Nothing is put back afterwards and nothing needs to be: an application of this kind owns
     * its own throwaway database, which is the whole reason a test that winds a clock has one.
     */
    public OfferView anOfferOnSale(Map<String, Object> offer) {
        ResponseEntity<OfferView> written =
                http.postForEntity("/api/admin/rewards", offer, OfferView.class);
        assertThat(written.getStatusCode())
                .describedAs("writing the offer " + offer + " this test needs")
                .isEqualTo(HttpStatus.CREATED);
        ResponseEntity<OfferView> published = http.postForEntity(
                "/api/admin/rewards/{code}/publish", null, OfferView.class,
                written.getBody().code());
        assertThat(published.getStatusCode())
                .describedAs("putting " + written.getBody().code() + " on sale: "
                        + published.getBody())
                .isEqualTo(HttpStatus.OK);
        return published.getBody();
    }

    /**
     * Writes an offer through the administration API, as whoever runs the scheme would, and
     * insists it took: an offer that was refused would leave a test claiming against something
     * that is not there and failing somewhere further down, about a field rather than about a
     * refusal.
     *
     * <p>Whatever the test wants to send, rather than a parameter per column. An offer has five
     * fields today and the feature adds a dozen more, and a helper that grew an argument per
     * slice would be a helper every caller has to be edited to keep using. What goes up is the
     * body, and what a body may say is the backend's business.
     *
     * <p>Only an application of the test's own can be written into safely. One database file
     * serves the shared run, and the customer's catalogue there is asserted to be exactly the
     * four seeded offers — so an offer written here belongs in a throwaway file this class
     * started, which is the only kind it can be asked for.
     */
    public OfferView writeAnOffer(Map<String, Object> offer) {
        ResponseEntity<OfferView> written =
                http.postForEntity("/api/admin/rewards", offer, OfferView.class);
        assertThat(written.getStatusCode())
                .describedAs("writing the offer this test needs: " + offer)
                .isEqualTo(HttpStatus.CREATED);
        return written.getBody();
    }

    /**
     * One offer as whoever runs the catalogue reads it, whatever state it is in — including the
     * two things a customer is never shown.
     */
    public OfferView theOfferAsItStands(String code) {
        ResponseEntity<OfferView> read =
                http.getForEntity("/api/admin/rewards/{code}", OfferView.class, code);
        assertThat(read.getStatusCode()).describedAs("reading the offer \"" + code + "\"")
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * An attempt to change an offer, read as unshaped JSON, for the changes this feature refuses.
     * A refusal's body is a reason rather than an offer, and a test that deserialised one into an
     * offer would be asserting on nulls.
     */
    public ResponseEntity<JsonNode> tryToChangeTheOffer(String code, Map<String, Object> change) {
        return http.exchange("/api/admin/rewards/{code}", HttpMethod.PATCH,
                new HttpEntity<>(change), JsonNode.class, code);
    }

    /**
     * Takes an offer back out of the catalogue, insisted on — the state a queue may be joined
     * for but nobody may ever be promoted onto.
     */
    public OfferView withdrawTheOffer(String code) {
        ResponseEntity<OfferView> withdrawn = http.postForEntity(
                "/api/admin/rewards/{code}/withdraw", null, OfferView.class, code);
        assertThat(withdrawn.getStatusCode())
                .describedAs("withdrawing \"" + code + "\": " + withdrawn.getBody())
                .isEqualTo(HttpStatus.OK);
        return withdrawn.getBody();
    }

    /** Puts one of those offers on sale, insisted on: a draft is not something anybody can claim. */
    public OfferView publishTheOffer(String code) {
        ResponseEntity<OfferView> published = http.postForEntity(
                "/api/admin/rewards/{code}/publish", null, OfferView.class, code);
        assertThat(published.getStatusCode())
                .describedAs("publishing \"" + code + "\": " + published.getBody())
                .isEqualTo(HttpStatus.OK);
        return published.getBody();
    }

    /**
     * The catalogue as one customer reads it today: every offer on sale, each one saying whether
     * they can claim it right now and the single reason if they cannot.
     *
     * <p>The read the rewards page makes, and the one a wound clock is watched through. The status
     * is insisted on for the reason {@link #deposit} gives: a read that had quietly become a
     * refusal would arrive as a list of nulls and the failure would land somewhere further down,
     * about a field rather than about a refusal.
     */
    public List<RewardForACustomerView> theCatalogueAsReadBy(String customerName) {
        ResponseEntity<RewardForACustomerView[]> read = http.getForEntity(
                "/api/customers/{id}/rewards", RewardForACustomerView[].class,
                seeded.customerIdOf(customerName));
        assertThat(read.getStatusCode())
                .describedAs("reading the catalogue as " + customerName + " sees it")
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /**
     * What this customer has claimed, newest first — the list their own page draws, and where a
     * voucher that has been used or has run out says so.
     */
    public List<ClaimedRewardView> claimsOf(String customerName) {
        ResponseEntity<ClaimedRewardView[]> read = http.getForEntity(
                "/api/customers/{id}/redemptions", ClaimedRewardView[].class,
                seeded.customerIdOf(customerName));
        assertThat(read.getStatusCode())
                .describedAs("reading what " + customerName + " has claimed")
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /**
     * One entry of that reading, by its code, because every assertion about a window is about a
     * named offer — and because an offer missing from the list is itself the failure worth
     * reporting in words rather than as an empty {@link java.util.Optional} somewhere else.
     */
    public RewardForACustomerView theOfferAsReadBy(String customerName, String code) {
        return theCatalogueAsReadBy(customerName).stream()
                .filter(entry -> code.equals(entry.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(customerName + " cannot see \"" + code
                        + "\" at all; a locked offer is meant to be shown rather than hidden, so "
                        + "this is the failure and not a missing set-up step"));
    }

    /**
     * A claim without insisting it was accepted, for the tests whose subject is the refusal: a
     * window that is not open turns a claim down in a sentence, and the sentence and the status
     * are the whole of what the test is about.
     */
    public ResponseEntity<JsonNode> tryToClaim(String customerName, String reward) {
        return http.postForEntity("/api/customers/{id}/redemptions", Map.of("reward", reward),
                JsonNode.class, seeded.customerIdOf(customerName));
    }

    /**
     * One voucher as somebody at a counter reads it, insisted on: a lookup that was refused would
     * arrive as a record full of nulls and fail a later assertion about a field.
     */
    public VoucherAtTheCounterView voucherAtACounter(String voucherCode) {
        ResponseEntity<VoucherAtTheCounterView> read = http.getForEntity(
                "/api/staff/vouchers/{code}", VoucherAtTheCounterView.class, voucherCode);
        assertThat(read.getStatusCode())
                .describedAs("looking up the voucher " + voucherCode)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * An attempt to hand a voucher over at a counter, read as a problem document rather than as a
     * voucher — because a refusal's body is a reason, and a test that deserialised one into a
     * voucher would be asserting on nulls.
     */
    public ResponseEntity<JsonNode> tryToHandOverTheVoucher(String voucherCode, String counter) {
        return http.postForEntity("/api/staff/vouchers/{code}/use", Map.of("counter", counter),
                JsonNode.class, voucherCode);
    }

    /**
     * Revokes a voucher the way whoever runs the scheme would, insisted on: a cancellation that
     * was refused would leave a test asserting that points came back and failing about a balance
     * rather than about the refusal.
     *
     * <p>At the very end of the class, where every slice appends its own, and over HTTP like
     * everything else here — nothing in this class has ever reached for a service or a row.
     *
     * <p>The refusals are not wrapped: a test whose subject is a cancellation being turned down
     * wants the status and the sentence, and reads them through {@link #tryToCancelTheVoucher}
     * below rather than through this.
     */
    public VoucherAtTheCounterView cancelTheVoucher(String voucherCode, String reason) {
        ResponseEntity<VoucherAtTheCounterView> cancelled = http.postForEntity(
                "/api/admin/vouchers/{code}/cancel", Map.of("reason", reason),
                VoucherAtTheCounterView.class, voucherCode);
        assertThat(cancelled.getStatusCode())
                .describedAs("cancelling the voucher " + voucherCode + ": " + cancelled.getBody())
                .isEqualTo(HttpStatus.OK);
        return cancelled.getBody();
    }

    /**
     * The same cancellation read as unshaped JSON, for the ones this feature refuses — a refusal's
     * body is a reason rather than a voucher, and a test that deserialised one into a voucher
     * would be asserting on nulls.
     */
    public ResponseEntity<JsonNode> tryToCancelTheVoucher(String voucherCode, String reason) {
        return http.postForEntity("/api/admin/vouchers/{code}/cancel", Map.of("reason", reason),
                JsonNode.class, voucherCode);
    }

    /**
     * Puts the last of something aside for this customer, and insists it took.
     *
     * <p>Insisted on for the reason {@link #deposit} gives: a hold that was refused would arrive
     * as a record full of nulls and the failure would land further down, about a field rather
     * than about the refusal. The tests whose subject <em>is</em> the refusal use
     * {@link #tryToTakeAHold} instead.
     */
    public HoldView takeAHold(String customerName, String reward) {
        ResponseEntity<HoldView> taken = http.postForEntity("/api/customers/{id}/holds",
                Map.of("reward", reward), HoldView.class, seeded.customerIdOf(customerName));
        assertThat(taken.getStatusCode())
                .describedAs("putting \"" + reward + "\" aside for " + customerName)
                .isEqualTo(HttpStatus.CREATED);
        return taken.getBody();
    }

    /** The same request read as a problem document, for the tests about being refused one. */
    public ResponseEntity<JsonNode> tryToTakeAHold(String customerName, String reward) {
        return http.postForEntity("/api/customers/{id}/holds", Map.of("reward", reward),
                JsonNode.class, seeded.customerIdOf(customerName));
    }

    /** Turns a hold into the claim it was being kept for, and insists it took. */
    public ClaimedRewardView convertTheHold(String customerName, String reward) {
        ResponseEntity<ClaimedRewardView> claimed = http.postForEntity(
                "/api/customers/{id}/holds/{code}/claim", null, ClaimedRewardView.class,
                seeded.customerIdOf(customerName), reward);
        assertThat(claimed.getStatusCode())
                .describedAs("converting " + customerName + "'s hold on \"" + reward + "\"")
                .isEqualTo(HttpStatus.CREATED);
        return claimed.getBody();
    }

    /** The same conversion read as a problem document, for the ones this feature refuses. */
    public ResponseEntity<JsonNode> tryToConvertTheHold(String customerName, String reward) {
        return http.postForEntity("/api/customers/{id}/holds/{code}/claim", null, JsonNode.class,
                seeded.customerIdOf(customerName), reward);
    }

    /** Gives a hold up, which puts the thing back in the window at once, and insists it took. */
    public HoldView giveUpTheHold(String customerName, String reward) {
        ResponseEntity<HoldView> given = http.exchange("/api/customers/{id}/holds/{code}",
                HttpMethod.DELETE, null, HoldView.class,
                seeded.customerIdOf(customerName), reward);
        assertThat(given.getStatusCode())
                .describedAs("giving up " + customerName + "'s hold on \"" + reward + "\"")
                .isEqualTo(HttpStatus.OK);
        return given.getBody();
    }

    /** The same, read as a problem document, for a hold that is not there to give up. */
    public ResponseEntity<JsonNode> tryToGiveUpTheHold(String customerName, String reward) {
        return http.exchange("/api/customers/{id}/holds/{code}", HttpMethod.DELETE, null,
                JsonNode.class, seeded.customerIdOf(customerName), reward);
    }

    /**
     * Puts a customer in the queue for something that has run out, and insists it took.
     *
     * <p>At the very end of the class, where every slice appends its own, and over HTTP like
     * everything else here — nothing in this class has ever reached for a service or a row.
     *
     * <p>Insisted on for the reason {@link #deposit} gives: a place that was refused would
     * arrive as a record full of nulls and the failure would land somewhere further down,
     * about a position rather than about a refusal. The tests whose subject <em>is</em> the
     * refusal use {@link #tryToJoinTheQueue} instead.
     */
    public PlaceInTheQueueView joinTheQueue(String customerName, String reward) {
        ResponseEntity<PlaceInTheQueueView> joined =
                http.postForEntity("/api/customers/{id}/waiting-lists", Map.of("reward", reward),
                        PlaceInTheQueueView.class, seeded.customerIdOf(customerName));
        assertThat(joined.getStatusCode())
                .describedAs("putting " + customerName + " in the queue for \"" + reward + "\"")
                .isEqualTo(HttpStatus.CREATED);
        return joined.getBody();
    }

    /** The same request read as a problem document, for the tests about being refused a place. */
    public ResponseEntity<JsonNode> tryToJoinTheQueue(String customerName, String reward) {
        return http.postForEntity("/api/customers/{id}/waiting-lists", Map.of("reward", reward),
                JsonNode.class, seeded.customerIdOf(customerName));
    }

    /** Takes a customer out of a queue, which closes the gap behind them, and insists it took. */
    public PlaceInTheQueueView leaveTheQueue(String customerName, String reward) {
        ResponseEntity<PlaceInTheQueueView> left =
                http.exchange("/api/customers/{id}/waiting-lists/{code}", HttpMethod.DELETE, null,
                        PlaceInTheQueueView.class, seeded.customerIdOf(customerName), reward);
        assertThat(left.getStatusCode())
                .describedAs("taking " + customerName + " out of the queue for \"" + reward + "\"")
                .isEqualTo(HttpStatus.OK);
        return left.getBody();
    }

    /** The same, read as a problem document, for a queue they are not in to leave. */
    public ResponseEntity<JsonNode> tryToLeaveTheQueue(String customerName, String reward) {
        return http.exchange("/api/customers/{id}/waiting-lists/{code}", HttpMethod.DELETE, null,
                JsonNode.class, seeded.customerIdOf(customerName), reward);
    }

    /**
     * Who is queued for one offer, in order, as whoever runs the scheme reads it.
     *
     * <p>Its own read rather than something inferred from the customers' own cards, because it
     * is a different address answering a different question — one offer's whole line, with
     * names on it — and it is the only place the order is visible as an order.
     */
    public List<WaitingListEntryView> theWaitingListFor(String code) {
        ResponseEntity<WaitingListEntryView[]> read = http.getForEntity(
                "/api/admin/rewards/{code}/waiting-list", WaitingListEntryView[].class, code);
        assertThat(read.getStatusCode())
                .describedAs("reading the waiting list for \"" + code + "\"")
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /**
     * Every version that savings product has published, oldest first, for a test that needs to say
     * what the catalogue has done rather than what it is selling.
     *
     * <p>The last of them is what a fresh publish follows, and it is what
     * {@link #theSameTermsAgain} fills a form from — so a test changing one figure of an agreement
     * never has to write the other ten down.
     */
    public List<TermsVersionView> versionsOfTheSavingsProduct(String code) {
        ResponseEntity<TermsVersionView[]> read = http.getForEntity(
                "/api/savings-products/{code}/versions", TermsVersionView[].class, code);
        assertThat(read.getStatusCode())
                .describedAs("reading the versions " + code + " has published")
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /**
     * Every month of interest this savings account has been paid, oldest first, with the arithmetic
     * behind each one.
     *
     * <p>Read through the endpoint the account's own screen reads, rather than out of the module
     * that wrote it, so that a test asserting what a month paid is asserting what a customer would
     * be shown. The months that paid nothing are in it, which is how a test can say that a sweep
     * judged a period without paying for it.
     */
    public List<InterestPostingView> interestPaidInto(long savingsAccountId) {
        ResponseEntity<InterestPostingView[]> read = http.getForEntity(
                "/api/savings-accounts/{id}/interest", InterestPostingView[].class,
                savingsAccountId);
        assertThat(read.getStatusCode())
                .describedAs("reading the interest paid into savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    /**
     * Publishes a new version of a savings product's terms through the administration door, with the
     * refusal ruled out rather than assumed: a refused publish would leave a test asserting that
     * nothing was repriced — and passing.
     *
     * <p><strong>Only ever on an application of this test's own, which is the whole reason it lives
     * here.</strong> Publishing cannot be undone — that is the promise of the catalogue — so a test
     * that published into the shared database would leave the version there for every test
     * afterwards, and the two that pin the shelf to four products at four rates would start failing
     * on whatever order the run happened to take. {@link AnApplicationWithAClockToMove} is already
     * an application of one test's own on a database nothing has ever been written to, and a
     * version published into it goes when the file does.
     */
    public TermsVersionView publishAVersionOf(String code, Map<String, Object> form) {
        ResponseEntity<TermsVersionView> published = http.postForEntity(
                "/api/admin/savings-products/{code}/versions", form, TermsVersionView.class, code);
        assertThat(published.getStatusCode())
                .describedAs("publishing a version of " + code + ": " + form)
                .isEqualTo(HttpStatus.CREATED);
        return published.getBody();
    }

    /**
     * A filled-in publishing form saying exactly what a version already says, ready for a test to
     * change the one figure it is about.
     *
     * <p>Every figure, every time, because that is the contract: a version carries nothing over from
     * the version before it and the module refuses an absent figure by name. Built from a version
     * that was actually served, so a test cannot quietly assert against a shape the API does not
     * have.
     *
     * <p>A {@link java.util.LinkedHashMap} rather than {@code Map.of}, so that a test can put one
     * figure back over another and so that the form logged beside a failure reads in the order the
     * screen draws it. The same helper {@code ACatalogueSomebodyAdministers} keeps for the
     * administration tests; it is copied rather than shared because that class is a different
     * application with a different purpose, and lifting it into a third place to be imported by both
     * would put a products-only form in the support package for every test in the run to find.
     */
    public static Map<String, Object> theSameTermsAgain(TermsVersionView as, LocalDate effectiveFrom,
                                                        String whatChanged) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("effectiveFrom", effectiveFrom.toString());
        form.put("annualRatePercent", as.annualRatePercent().toPlainString());
        form.put("bonusRatePercent", as.bonusRatePercent().toPlainString());
        form.put("noticeDays", String.valueOf(as.noticeDays()));
        form.put("termMonths", String.valueOf(as.termMonths()));
        form.put("minimumBalance", as.minimumBalance().toPlainString());
        form.put("earlyExitPenaltyDays", String.valueOf(as.earlyExitPenaltyDays()));
        form.put("pointsMultiplier", as.pointsMultiplier().toPlainString());
        form.put("anniversaryRatePercent", as.anniversaryRatePercent().toPlainString());
        form.put("maturityAction", as.maturityAction());
        form.put("whatChanged", whatChanged);
        return form;
    }

    /**
     * What one savings account is living under, read off its own page.
     *
     * <p>Here so that a test about a monthly rule can ask the application which day its periods are
     * counted from and which version of which product's terms decide the rate, instead of writing
     * either into itself. A test that assumed an account was opened today and on version 2 would
     * pass for the wrong reason the morning somebody changed the seed.
     */
    public AnAgreementView theAgreementOf(long savingsAccountId) {
        AnAgreementView agreement = balancesOf(savingsAccountId).agreement();
        assertThat(agreement)
                .describedAs("savings account " + savingsAccountId + " is living under an "
                        + "agreement, without which nothing can say what it is paid")
                .isNotNull();
        return agreement;
    }

    /**
     * One published version of a savings product's terms, by the number an account names it with.
     *
     * <p>The rate a test checks interest against comes from here rather than from a figure written
     * into the test, because the rate an account is paid at is the rate <em>its own version</em>
     * names — and free savings has published two. A test that hard-coded 0.50% would be asserting
     * what the seed happens to write, and would pass on an account that is on the other one.
     */
    public TermsVersionView theVersionOf(String productCode, int version) {
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

    /** What this savings account holds, which is the figure interest is supposed to raise. */
    public BigDecimal moneyBalanceOf(long savingsAccountId) {
        return balancesOf(savingsAccountId).moneyBalance();
    }

    /**
     * Closes an emptied savings account, with a refusal ruled out, and answers the agreement as it
     * now reads — including the day it ended on.
     *
     * <p>Here so that a test whose subject is what the nightly runs do about a closed account can
     * close one on an application whose clock it winds. Only an emptied account may be closed, so a
     * refusal here is a test that has not emptied it and is worth failing loudly for rather than
     * discovering three assertions later as a rule that fired after all.
     */
    public AnAgreementView closeTheSavingsAccount(long savingsAccountId) {
        ResponseEntity<AnAgreementView> closed = http.postForEntity(
                "/api/savings-accounts/{id}/close", null, AnAgreementView.class, savingsAccountId);
        assertThat(closed.getStatusCode())
                .describedAs("closing savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return closed.getBody();
    }

    /**
     * Opens another savings account for that customer on the product they name, with the refusal
     * ruled out: an account that was never opened would leave a test asserting about an identifier
     * nobody holds.
     *
     * <p>Here so that a test about what an agreement does can have an account on a product that is
     * not free savings — a fixed term, most of all, because a term is the one condition that refuses
     * a change of terms. The product travels as the word a customer would press, which is what the
     * catalogue card sends.
     */
    public long openASavingsAccountOn(String customerName, String product) {
        ResponseEntity<JsonNode> opened = http.postForEntity(
                "/api/customers/{id}/savings-accounts", Map.of("product", product), JsonNode.class,
                customerIdOf(customerName));
        assertThat(opened.getStatusCode())
                .describedAs("opening a savings account on " + product + " for " + customerName
                        + ": " + opened.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return opened.getBody().path("id").asLong();
    }

    /**
     * What this account's product is offering today, what the account is on, and what taking the
     * newer terms would change — read off the account's own page rather than from a door of its own.
     *
     * <p>Off the account's page on purpose: the claim under test is that the comparison arrives with
     * the agreement it is a comparison with, so a test that fetched it separately would not be
     * asserting the thing the screen actually gets.
     */
    public TheNewerTermsView theNewerTermsFor(long savingsAccountId) {
        TheNewerTermsView newer = balancesOf(savingsAccountId).newerTerms();
        assertThat(newer)
                .describedAs("savings account " + savingsAccountId + " says whether its product has "
                        + "published anything newer than the version it is on")
                .isNotNull();
        return newer;
    }

    /**
     * What this savings account's term says today: how long it is, the day it is up, whether that
     * day has come, whether the money is still locked, and the ending its terms name.
     *
     * <p>Read off the endpoint the account's own panel reads, rather than inferred from the
     * agreement's maturity date, because "matured" and "locked" are the two readings a test about a
     * term that has reached its day is actually about — and a test that worked them out from a date
     * of its own would be keeping a second copy of the maturity calendar.
     */
    public TheTermOnAnAccountView theTermOn(long savingsAccountId) {
        ResponseEntity<TheTermOnAnAccountView> read = http.getForEntity(
                "/api/savings-accounts/{id}/term", TheTermOnAnAccountView.class, savingsAccountId);
        assertThat(read.getStatusCode())
                .describedAs("reading the term on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * Takes the newer terms of the product this account is on, with the refusal ruled out, and
     * answers the agreement as it now reads.
     *
     * <p>A refusal ruled out rather than assumed, because the shape of the mistake is quiet: a
     * refused press would leave a test asserting that an account is still on the version it was
     * already on, and passing for the wrong reason.
     */
    public AnAgreementView takeTheNewerTerms(long savingsAccountId) {
        ResponseEntity<AnAgreementView> taken = http.postForEntity(
                "/api/savings-accounts/{id}/newer-terms/take", null, AnAgreementView.class,
                savingsAccountId);
        assertThat(taken.getStatusCode())
                .describedAs("taking the newer terms on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return taken.getBody();
    }

    /** The same press, with whatever came back, for the tests whose subject is the refusal. */
    public ResponseEntity<JsonNode> tryToTakeTheNewerTerms(long savingsAccountId) {
        return http.postForEntity("/api/savings-accounts/{id}/newer-terms/take", null,
                JsonNode.class, savingsAccountId);
    }

    /**
     * Gives notice on an amount held in a savings account, with the refusal ruled out, and answers
     * the notice as it now reads.
     *
     * <p>Here rather than in a fixture of one test's own, because a test that winds a clock through
     * a notice period needs an application with a clock to wind and the notice given on it — and
     * there was no way to do the second through this one. The refusal is ruled out rather than
     * assumed for the reason every other press here rules it out: a refused notice would leave a
     * test asserting that nothing was ever announced about it, and passing for the wrong reason.
     */
    public NoticeView giveNoticeOn(long savingsAccountId, String amount) {
        ResponseEntity<NoticeView> given = http.postForEntity(
                "/api/savings-accounts/{id}/notices", Map.of("amount", amount), NoticeView.class,
                savingsAccountId);
        assertThat(given.getStatusCode())
                .describedAs("giving notice on EUR " + amount + " in savings account "
                        + savingsAccountId)
                .isEqualTo(HttpStatus.CREATED);
        return given.getBody();
    }

    /** What this account's notice says today: the days it asks for, the two totals and the list. */
    public TheNoticeOnAnAccountView theNoticeOn(long savingsAccountId) {
        ResponseEntity<TheNoticeOnAnAccountView> read = http.getForEntity(
                "/api/savings-accounts/{id}/notices", TheNoticeOnAnAccountView.class,
                savingsAccountId);
        assertThat(read.getStatusCode())
                .describedAs("reading the notice on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /**
     * Breaks the fixed term on one of this customer's accounts, with the refusal ruled out, and
     * answers what the break cost.
     *
     * <p>Here rather than in a fixture of one test's own for the reason {@link #giveNoticeOn} gives:
     * the charge it writes is the one movement in this application that leaves a savings account
     * and reaches nobody, so a test about what the ledger records — or about what a month of
     * interest is then paid on — needs a clock to wind <em>and</em> a term broken on the application
     * whose clock it is winding. The refusal is ruled out because a term that was never broken
     * would leave a test asserting that no charge was ever recorded, and passing for it.
     */
    public ATermBrokenView breakTheTermOn(long savingsAccountId) {
        ResponseEntity<ATermBrokenView> broken = http.postForEntity(
                "/api/savings-accounts/{id}/term/break", null, ATermBrokenView.class,
                savingsAccountId);
        assertThat(broken.getStatusCode())
                .describedAs("breaking the term on savings account " + savingsAccountId)
                .isEqualTo(HttpStatus.OK);
        return broken.getBody();
    }

    /**
     * Moves money from one of a customer's savings accounts to another of their own, in the one
     * press the operation is, with the refusal ruled out.
     *
     * <p>One press and not a withdrawal followed by a deposit, which is the whole of why the door
     * exists: the euros never leave savings, so the week is not netted to nothing and the points
     * they have already earned are not charged for again. A test reading the ledger afterwards is
     * reading one entry naming both accounts, which is the only kind of movement in this
     * application with a savings account at both ends.
     */
    public AMoveView move(long fromSavingsAccountId, long toSavingsAccountId, String amount) {
        ResponseEntity<AMoveView> moved = http.postForEntity("/api/savings-accounts/{id}/moves",
                Map.of("amount", amount, "toSavingsAccountId", toSavingsAccountId), AMoveView.class,
                fromSavingsAccountId);
        assertThat(moved.getStatusCode())
                .describedAs("moving EUR " + amount + " from savings account "
                        + fromSavingsAccountId + " to " + toSavingsAccountId)
                .isEqualTo(HttpStatus.CREATED);
        return moved.getBody();
    }
}
