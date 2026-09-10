package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.streaks.SavingsWeek;
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

    /** Which customer this is, for a test asserting on who a gift names. */
    public long customerIdOf(String customerName) {
        return seeded.customerIdOf(customerName);
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
     * A refusal as the API answers with one (RFC 9457), of which these tests read the sentence the
     * customer is shown and nothing else.
     */
    private record ProblemView(String detail) {
    }
}
