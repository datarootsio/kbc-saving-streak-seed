package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.automation.ARuleAsAsked;
import io.dataroots.savingstreak.automation.AutomationService;
import io.dataroots.savingstreak.automation.HowMuchMoves;
import io.dataroots.savingstreak.automation.RuleTrigger;
import io.dataroots.savingstreak.clock.ClockService;
import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.WithdrawalsService;
import io.dataroots.savingstreak.goals.GoalsService;
import io.dataroots.savingstreak.goals.RecordedGoal;
import io.dataroots.savingstreak.scheme.SchemeService;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The half of the seeded demonstration that gives Anke a decision worth simulating: weeks of saving
 * already behind her, a mark she has already fallen below, two dated goals competing for a capacity
 * that will not stretch to both, and a standing rule quietly filling the pot every week.
 *
 * <p><strong>Why any of this is here.</strong> The simulator answers four questions, and a household
 * with nothing behind it answers none of them. With no capacity declared the goals project nothing
 * at all; with one goal there is no second arrival day for a moved deadline to move; with one deposit
 * a withdrawal is plain subtraction rather than a repriced anniversary; with no run of weeks a stop
 * costs no rate; and with savings at their own peak the day money arrives and earns nothing — the
 * event this whole feature exists to warn about — never fires. Every figure below exists to make one
 * of those visible on the first screen a trainer opens, and the list of what each one is for is in
 * {@link #giveHerTheWeeksAlreadyBehindHer} and {@link #giveHerTheDecisionSheIsWeighingUp}.
 *
 * <p><strong>Why it is a profile of its own rather than more lines in {@link DemoData}.</strong> A
 * run of secured weeks cannot be declared. It is derived, by {@code WeekAndStreakDerivation}, from
 * deposits that landed in weeks that have already ended — so the only honest way to seed one is to
 * pay money in and move the development clock on a week, which is exactly what a trainer does by
 * hand. That makes the seeded Anke a customer with a ledger, a points balance, a mark and a run of
 * weeks behind her, and the test suite's seeded Anke is the customer a hundred and one test classes
 * count <em>from zero</em> — "an account with no history at all", in
 * {@code AnApplicationWithAClockToMove}'s own words. The two cannot be the same customer. So the
 * lived-in half is a bean that exists only under the {@code demo} profile, which the Maven plugin
 * adds to {@code spring-boot:run} (see {@code backend/pom.xml}) and no test asks for; the tests that
 * do want it — {@code AHouseholdWithADecisionToMakeApiTest} — start an application of their own with
 * it on. One seed, one gate, one transaction, and a pristine Anke still available to everything that
 * needs to count from nothing.
 *
 * <p><strong>Declared, never generated</strong>, on the same terms as the rest of the seed: every
 * figure below is written out here, there is no randomness anywhere in it, and the days are counted
 * off the application's clock so that a reset on any day of the week produces the same shape. The
 * amounts go in through {@link DepositsService}, {@link WithdrawalsService}, {@link GoalsService} and
 * {@link AutomationService} rather than into rows, so the seeded history is held to exactly the rules
 * a customer doing it by hand would meet — including the points each deposit earns at the rate the
 * run it secured pays, which is a figure nobody could have written down correctly in advance.
 *
 * <p><strong>The clock is left two weeks forward, and that is the price of a real history.</strong>
 * Winding is the only way to put a deposit in a week that has ended, and the clock is forward-only by
 * design, so a seed that wants two weeks behind it has to spend two weeks of clock getting there. A
 * fresh demonstration database therefore reads two days short of a fortnight ahead of the machine —
 * {@code GET /api/dev/clock} says {@code movedForwardByDays=14} before anybody has pressed anything —
 * and everything else in the application is relative to that reading, so nothing else notices. The
 * winds all happen before the salary, the bills, the categories and the spends are declared, which is
 * what keeps their cursors starting at the day the demonstration opens rather than a fortnight in
 * arrears; see the two-phase call in {@link DemoData#seed}.
 */
@Component
@Profile("demo")
class AHouseholdWithADecisionToMake {

    private static final Logger log = LoggerFactory.getLogger(AHouseholdWithADecisionToMake.class);

    /**
     * How many days apart the seeded deposits are, and therefore how far the clock ends up forward.
     *
     * <p>Seven, because seven days is the same weekday in the next week whatever day the database is
     * reset on — the property {@code MovableClock} counts its days through the calendar for — so
     * three deposits a week apart land in three consecutive weeks on a Monday reset and on a Saturday
     * one alike. Any other number would seed a run of three weeks on some days of the week and of two
     * on others, which is a demonstration that changes shape depending on when it is given.
     */
    private static final int A_WEEK = 7;

    /**
     * The three deposits already behind her, oldest first: one two weeks ago, one last week, one this
     * week. Each is comfortably above the weekly minimum version 1 of the scheme publishes — EUR
     * 50,00 — so each secures the week it landed in and the run reads three by the time the seed is
     * done: a run long enough that the ladder is paying ×1.20 rather than the ordinary rate, and
     * therefore long enough that a branch which stops for two months has a rate to lose and not only
     * a habit. Comfortably above rather than exactly at it, which is what keeps the seed honest if
     * somebody demonstrating the application reprices the scheme upwards by a few euros.
     *
     * <p>Three of them rather than one, and of different sizes, because a withdrawal is drawn from
     * the oldest deposit first: it is that ordering which decides whose anniversary survives to pay,
     * and a single deposit makes taking five hundred out look like subtraction. Together they come to
     * EUR 1 050,00, which is enough for a five hundred euro branch to bite into two of them.
     */
    private static final List<String> THE_DEPOSITS_ALREADY_BEHIND_HER =
            List.of("400.00", "350.00", "300.00");

    /**
     * And the money she took back out again, the same week as the last of them.
     *
     * <p>The one figure here that is not about the past at all. It is what leaves her below her own
     * high-water mark: the most she has ever saved is the EUR 1 050,00 she stood at for a moment, the
     * pot holds EUR 850,00, and so the first EUR 200,00 of everything she saves from now on earns
     * <em>nothing</em> — {@code TheMostEverSaved} has already paid on those euros. Without this
     * withdrawal the mark and the balance are the same number and
     * {@code MONEY_ARRIVES_AND_EARNS_NOTHING} never fires in any branch, which would leave the
     * feature's own problem statement undemonstrable on the demonstration database.
     *
     * <p>It has to be the last thing that moves, too. Taken before the third deposit it would leave
     * the balance back at its peak, and the gap this figure exists to open would close again.
     *
     * <p>Small enough to leave the week it falls in secured: EUR 300,00 in less EUR 200,00 out is
     * EUR 100,00 of net new saving, which is twice what a week asks for, so the run survives the
     * seeding of it.
     */
    private static final String TAKEN_BACK_OUT_AGAIN = "200.00";

    /**
     * The goal in front, and the one that cannot be paid for at the rate she has declared.
     *
     * <p>EUR 1 200,00 wanted in twenty-six weeks is a deadline minimum of EUR 46,16 a week against a
     * declared capacity of EUR 40,00 (see {@code DemoData}): it takes the whole capacity, still does
     * not arrive in time, and reads {@code OFF_TRACK} on the goals screen from the first minute. That
     * is what gives {@code SAVE_MORE_EACH_WEEK} and {@code MOVE_A_DEADLINE} something to change.
     */
    private static final String THE_GOAL_IN_FRONT = "A new kitchen";
    private static final String THE_KITCHEN_COSTS = "1200.00";
    private static final int THE_KITCHEN_IS_WANTED_IN_WEEKS = 26;

    /**
     * And the goal behind it, which at the current rate never arrives at all.
     *
     * <p>This is the one that makes moving a deadline worth asking about, and no other branch can
     * show it: the kitchen in front eats all forty euros, so this goal is given nothing every week and
     * has no arrival day at all. Push the kitchen's deadline out to forty-eight weeks and its minimum
     * falls to EUR 25,00, fifteen euros a week come free, and <em>this</em> goal acquires a date —
     * a second goal's future moved by a change made to the first, which is the whole point of the
     * question.
     *
     * <p>Forty-four weeks out, so that the fifteen euros a week a moved deadline frees reach
     * EUR 600,00 with weeks to spare and the answer is a goal that goes from never to on time rather
     * than from never to still late. Both deadlines sit well inside the twelve months the simulator
     * folds, so both are days a branch may name.
     */
    private static final String THE_GOAL_BEHIND_IT = "A week in the Ardennes";
    private static final String THE_ARDENNES_COSTS = "600.00";
    private static final int THE_ARDENNES_IS_WANTED_IN_WEEKS = 44;

    /**
     * The rule standing on the account: sixty euros every Tuesday, out of the everyday account the
     * salary lands in.
     *
     * <p>Sixty rather than forty, deliberately: what the automation actually moves and what its holder
     * says she can afford are two different declarations, and a demonstration in which they are the
     * same figure teaches that they are one. Sixty a week is EUR 260,00 in a month against the
     * EUR 1 435,00 her salary leaves after her bills, so it runs for as long as anybody cares to wind
     * the clock and never starves a bill — the constraint {@code DemoData}'s own documentation puts on
     * every rule this seed stands up.
     *
     * <p>It is also what gives {@code STOP_FOR_A_WHILE} something to silence. A stop pauses the rules;
     * an account with no rule standing on it pauses nothing, and the branch comes back identical to
     * the year already under way, which reads as a broken simulator rather than as a household with
     * no automation.
     *
     * <p>No split across the goals. What a rule with no split pays in arrives unallocated, which is
     * the state the goals screen is most worth reading in — the money is there, the goals have not
     * claimed it, and a withdrawal in a branch is free to draw on all of it.
     */
    private static final String THE_RULE = "Sixty every Tuesday";
    private static final String THE_RULE_MOVES = "60.00";
    private static final DayOfWeek THE_RULE_FIRES_ON = DayOfWeek.TUESDAY;

    private final ClockService theClock;
    private final Clock clock;
    private final DepositsService deposits;
    private final WithdrawalsService withdrawals;
    private final GoalsService goals;
    private final AutomationService automation;
    /**
     * Asked one question, and only so that the line below can say what the seeded deposits were
     * judged against. The figure used to be a constant this class imported; the bank publishes it
     * now, so a seed that wrote EUR 50 into its own log line would be describing a demonstration
     * that no longer matches the scheme the demonstration is running under.
     */
    private final SchemeService scheme;

    AHouseholdWithADecisionToMake(ClockService theClock, Clock clock, DepositsService deposits,
                                  WithdrawalsService withdrawals, GoalsService goals,
                                  AutomationService automation, SchemeService scheme) {
        this.theClock = theClock;
        this.clock = clock;
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.goals = goals;
        this.automation = automation;
        this.scheme = scheme;
    }

    /**
     * What this history takes out of the everyday account in total, so that the account can be opened
     * with it and still be left holding the figure the shared test fixtures assert against.
     *
     * <p>Net of the withdrawal, because money taken back out of savings lands in the same everyday
     * account it left. Derived rather than declared, for the reason
     * {@code AHousehold.spentInTheSeededWeeks} gives about the supermarket trips: a second constant
     * would be made wrong, silently, by the first change to a deposit.
     */
    BigDecimal takenOutOfTheEverydayAccount() {
        return THE_DEPOSITS_ALREADY_BEHIND_HER.stream()
                .map(BigDecimal::new)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(new BigDecimal(TAKEN_BACK_OUT_AGAIN));
    }

    /**
     * The first half: the weeks she has already saved, laid down a week apart by moving the clock
     * between them, and the withdrawal that leaves her below her own mark.
     *
     * <p>Called before the salary, the bills and the spends are declared, because every one of those
     * starts a cursor at the moment it is declared and a cursor started before these winds would open
     * the demonstration a fortnight in arrears — a first nightly run presenting two weeks of back
     * rent, which is the state {@code AnIncomeDeclaredTodayIsNotAYearOfBackPay} exists to keep the
     * application out of.
     */
    void giveHerTheWeeksAlreadyBehindHer(long currentAccountId, long savingsAccountId) {
        for (int deposit = 0; deposit < THE_DEPOSITS_ALREADY_BEHIND_HER.size(); deposit++) {
            if (deposit > 0) {
                // A week of clock between one deposit and the next, which is the only way to put a
                // deposit in a week that has ended: the run of secured weeks is derived from the
                // ledger and cannot be asserted into existence.
                theClock.advanceBy(A_WEEK);
            }
            deposits.deposit(savingsAccountId, currentAccountId,
                    new BigDecimal(THE_DEPOSITS_ALREADY_BEHIND_HER.get(deposit)));
        }
        withdrawals.withdraw(savingsAccountId, currentAccountId, new BigDecimal(TAKEN_BACK_OUT_AGAIN));
        // The three figures a reader checks a branch against before opening any screen: what the pot
        // holds, the mark it has to climb back to before a euro earns anything again, and the gap
        // between them, which is exactly how much of her next saving is worth no points at all.
        log.info("a history already behind her savingsAccountId={} deposits={} paidIn={} "
                        + "takenBackOut={} holding={} clockMovedForwardByDays={}",
                savingsAccountId, THE_DEPOSITS_ALREADY_BEHIND_HER.size(),
                AmountOfMoney.asMoney(takenOutOfTheEverydayAccount()
                        .add(new BigDecimal(TAKEN_BACK_OUT_AGAIN))),
                AmountOfMoney.asMoney(new BigDecimal(TAKEN_BACK_OUT_AGAIN)),
                AmountOfMoney.asMoney(deposits.moneyBalanceOf(savingsAccountId)),
                theClock.howFarItHasMoved().movedForwardByDays());
    }

    /**
     * And the second half: the two goals competing for a capacity that will not stretch to both, and
     * the rule quietly filling the pot every week.
     *
     * <p>Called after the salary and the bills are declared, and for two reasons. The rule draws on
     * the everyday account, so the account has to have something arriving in it before a rule on it
     * means anything; and a rule left standing before the winds above would be two weekly occurrences
     * in arrears the moment the demonstration opened, which the next nightly run would catch up in
     * one go.
     *
     * <p>The deadlines are counted off the application's clock rather than off {@code LocalDate.now()},
     * which by this point are a fortnight apart — the whole of this class's reason for existing.
     */
    void giveHerTheDecisionSheIsWeighingUp(long currentAccountId, long savingsAccountId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
        RecordedGoal inFront = goals.addGoal(savingsAccountId, THE_GOAL_IN_FRONT,
                new BigDecimal(THE_KITCHEN_COSTS), today.plusWeeks(THE_KITCHEN_IS_WANTED_IN_WEEKS));
        RecordedGoal behindIt = goals.addGoal(savingsAccountId, THE_GOAL_BEHIND_IT,
                new BigDecimal(THE_ARDENNES_COSTS), today.plusWeeks(THE_ARDENNES_IS_WANTED_IN_WEEKS));
        automation.leaveARuleStanding(savingsAccountId,
                new ARuleAsAsked(currentAccountId, THE_RULE, RuleTrigger.WEEKLY, THE_RULE_FIRES_ON,
                        null, HowMuchMoves.A_FIXED_AMOUNT, new BigDecimal(THE_RULE_MOVES), null,
                        List.of()));
        // The decision itself, in one line: the two goals in the order they compete in, what each
        // wants and by when, and the rule that is the only thing actually paying for either of them.
        // A trainer who reads this line knows what the four branches are about to argue over without
        // opening the screen, and a reviewer who finds a branch changing a date this line does not
        // mention is looking at the wrong account.
        log.info("a decision worth simulating savingsAccountId={} inFront=\"{}\" wants={} by={} "
                        + "behindIt=\"{}\" wants={} by={} rule=\"{}\" moves={} every={} "
                        + "weeklyMinimum={}",
                savingsAccountId, inFront.name(), AmountOfMoney.asMoney(inFront.target()),
                inFront.deadline(), behindIt.name(), AmountOfMoney.asMoney(behindIt.target()),
                behindIt.deadline(), THE_RULE, AmountOfMoney.asMoney(new BigDecimal(THE_RULE_MOVES)),
                THE_RULE_FIRES_ON,
                AmountOfMoney.asMoney(scheme.theSchemeInForce().weeklyThreshold()));
    }
}
