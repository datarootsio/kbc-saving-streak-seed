package io.dataroots.savingstreak.whatifididthisinstead;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.accounts.ADeclaredBill;
import io.dataroots.savingstreak.accounts.BillState;
import io.dataroots.savingstreak.accounts.DeclaredIncome;
import io.dataroots.savingstreak.automation.HowMuchMoves;
import io.dataroots.savingstreak.automation.RecordedSavingRule;
import io.dataroots.savingstreak.automation.RuleState;
import io.dataroots.savingstreak.automation.RuleTrigger;
import io.dataroots.savingstreak.automation.WhatWouldMove;
import io.dataroots.savingstreak.deposits.DepositStillHoldingMoney;
import io.dataroots.savingstreak.goals.AllocationsOnAnAccount;
import io.dataroots.savingstreak.goals.GoalState;
import io.dataroots.savingstreak.goals.GoalStatus;
import io.dataroots.savingstreak.goals.RecordedGoal;
import io.dataroots.savingstreak.goals.SavingCapacityOnAnAccount;
import io.dataroots.savingstreak.loyalty.LoyaltyAnniversary;
import io.dataroots.savingstreak.loyalty.LoyaltyRate;
import io.dataroots.savingstreak.loyalty.NextAnniversaryOfADeposit;
import io.dataroots.savingstreak.points.PointsExpiringOnADay;
import io.dataroots.savingstreak.products.NoticeGiven;
import io.dataroots.savingstreak.products.ProductKind;
import io.dataroots.savingstreak.products.TheNoticeOnAnAccount;
import io.dataroots.savingstreak.products.WhatAnAccountsProductPaysAndAsksFor;
import io.dataroots.savingstreak.simulation.ACurrentAccountBehindIt;
import io.dataroots.savingstreak.simulation.AKindOfThingThatHappens;
import io.dataroots.savingstreak.simulation.AMonthOfTheFuture;
import io.dataroots.savingstreak.simulation.AThingThatHappens;
import io.dataroots.savingstreak.simulation.AnAdjustment;
import io.dataroots.savingstreak.simulation.HowAScenarioTurnsOut;
import io.dataroots.savingstreak.simulation.MovingADeadline;
import io.dataroots.savingstreak.simulation.SavingMoreEachWeek;
import io.dataroots.savingstreak.simulation.StoppingForAWhile;
import io.dataroots.savingstreak.simulation.TakingMoneyOut;
import io.dataroots.savingstreak.simulation.TheNightReplayed;
import io.dataroots.savingstreak.simulation.TheStartingPoint;
import io.dataroots.savingstreak.scheme.TheSchemeAsPublished;
import io.dataroots.savingstreak.scheme.TheSchemeEachWeekWasJudgedUnder;
import io.dataroots.savingstreak.streaks.NewSavingsThisWeek;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.streaks.StreakOfSecuredWeeks;
import io.dataroots.savingstreak.streaks.TheLadderARunClimbs;
import io.dataroots.savingstreak.streaks.WeekAndStreak;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fold asserted directly, as the arithmetic it is.
 *
 * <p><strong>No Spring context, and this is the narrow exception this repository already
 * allows.</strong> It is the same bargain {@code WhenAGoalWillBeReachedTest} and {@code
 * HowTheWeeklyMoneyIsSpentTest} strike, for the same three reasons, and the fold earns it harder
 * than either. The cases that matter most here are a week closing on a Sunday, a deposit paid at the
 * rate of the week it itself secured, a batch expiring on its own anniversary and an anniversary
 * landing on the same morning as an expiry — and over HTTP the only way to reach any of them is to
 * wind a shared clock a year forward and hope. Which Monday the weeks are counted from can only be
 * pinned against a day that is known, and over HTTP the day is whatever the clock other classes in
 * the run have wound reads.
 *
 * <p>Everything about the answer arriving at the seam, and about the asking writing nothing, is
 * asserted over HTTP in {@code TheYearThisAccountIsHeadingForApiTest}, where it belongs.
 *
 * <p>Every day named here is counted from {@link #A_MONDAY}, which is a Monday on purpose: the week
 * this application counts starts on one, and a fixture opening mid-week would make every assertion
 * about a Sunday an assertion about arithmetic nobody can check by eye.
 */
class TheNightIsReplayedOneDayAtATimeForTwelveMonthsTest {

    private static final long AN_ACCOUNT = 1;
    private static final long A_CUSTOMER = 7;
    private static final long AN_EVERYDAY_ACCOUNT = 11;

    /** The day the window opens on, and a Monday, so that the Sundays are easy to count to. */
    private static final LocalDate A_MONDAY = LocalDate.of(2026, 9, 14);

    /** Twelve calendar months on, which is the last day the fold walks and the twelfth row's day. */
    private static final LocalDate A_YEAR_ON = A_MONDAY.plusMonths(12);

    /**
     * How long a batch of points lasts, as the snapshot carries it, and as the scheme publishes it
     * today.
     *
     * <p>On the snapshot rather than inside the fold, because the figure is published and can change
     * — the fold asks Points what the ledger would do and applies the rule with that answer. Twelve
     * here is what makes a batch earned on the opening day go on {@code A_YEAR_ON}, which is the
     * boundary this class has always asserted against.
     */
    private static final int TWELVE_MONTHS = 12;

    /**
     * The scheme as this fixture's bank has published it: one version, in force since long before
     * the window opens, at exactly the figures this application has always run on.
     *
     * <p>A history rather than a set of figures, because that is what the snapshot carries and what
     * the fold asks per week — the fold walks fifty-two of them and a version announced for a Monday
     * inside the window would take effect inside the window. One version is what makes every week of
     * every branch in this class read the way it read before the scheme had versions at all, which is
     * the point: these tests are about what a year of nights does, not about a repricing, and the
     * tests about a repricing live in {@code savingspolicy}.
     *
     * <p>Dated a year before the window opens rather than on it, so that no week the fold walks
     * backwards or forwards can fall before the only version there is.
     */
    private static final TheSchemeEachWeekWasJudgedUnder THE_SCHEME_AS_IT_STANDS =
            new TheSchemeEachWeekWasJudgedUnder(List.of(new TheSchemeAsPublished(
                    1, A_MONDAY.minusYears(1), new BigDecimal("50.00"), new BigDecimal("1.0000"),
                    new BigDecimal("0.1000"), new BigDecimal("1.5000"), TWELVE_MONTHS,
                    List.of(new BigDecimal("100.00")), new BigDecimal("80.00"), 3, 30, 30,
                    "The scheme as it has always been")));

    /** What a week asks for under that scheme, for the fixtures that build a week by hand. */
    private static final BigDecimal WHAT_A_WEEK_ASKS_FOR =
            THE_SCHEME_AS_IT_STANDS.onTheDayOf(A_MONDAY).weeklyThreshold();

    /** The moment that Monday begins, which is where an account whose night has run is settled to. */
    private static final Instant THE_WINDOW_OPENS =
            A_MONDAY.atStartOfDay(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toInstant();

    @Test
    void the_year_comes_back_as_twelve_rows_closing_on_the_monthly_anniversaries_of_the_day_it_opened() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith().build());

        assertThat(year.months())
                .as("twelve rows, because the window is twelve months and a customer comparing four "
                        + "columns cannot read one of them with eleven bars in it")
                .hasSize(12);
        assertThat(year.months().stream().map(AMonthOfTheFuture::closesOn))
                .as("each closing on the monthly anniversary of the day the window opened, so that "
                        + "the twelve of them cover the window exactly once with nothing over")
                .containsExactly(
                        LocalDate.of(2026, 10, 14), LocalDate.of(2026, 11, 14),
                        LocalDate.of(2026, 12, 14), LocalDate.of(2027, 1, 14),
                        LocalDate.of(2027, 2, 14), LocalDate.of(2027, 3, 14),
                        LocalDate.of(2027, 4, 14), LocalDate.of(2027, 5, 14),
                        LocalDate.of(2027, 6, 14), LocalDate.of(2027, 7, 14),
                        LocalDate.of(2027, 8, 14), A_YEAR_ON);
        assertThat(year.months().get(11).month())
                .as("and labelled with the calendar month it closes in, which is what a page writes "
                        + "under a bar")
                .isEqualTo(YearMonth.of(2027, 9));
        assertThat(year.until())
                .as("the last row closes on the day the window does, so nothing in the year is "
                        + "outside a row")
                .isEqualTo(A_YEAR_ON);
    }

    @Test
    void a_week_that_took_in_nothing_closes_on_its_sunday_and_the_run_goes_back_to_nothing() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                // Three weeks behind them and a week under way that nothing has landed in. No rule,
                // no income: the branch puts nothing away from here on.
                .aRunOf(3, new BigDecimal("0.00"))
                .build());

        assertThat(year.months().get(0).securedWeeks())
                .as("the week under way ended on its Sunday with nothing in it, so the run ended "
                        + "there rather than shortening by one — three weeks of saving are worth "
                        + "nothing the moment a fourth is missed")
                .isZero();
        assertThat(year.months().get(0).balance())
                .as("and no money moved, because a branch with no rule and no salary moves none")
                .isEqualByComparingTo("0.00");
    }

    @Test
    void a_deposit_is_paid_at_the_rate_of_the_run_that_includes_the_week_it_has_just_secured() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                .anEverydayAccountHolding("5000.00")
                .aWeeklyRuleEveryMondayOf("50.00")
                .build());

        AMonthOfTheFuture first = year.months().get(0);
        assertThat(first.balance())
                .as("four Mondays fall between the day the window opened and the day the first row "
                        + "closes, and fifty euros went in on each of them — the Monday the window "
                        + "opened on is not one of them, because the run fired it at two this "
                        + "morning and those euros are already in the balance this started from")
                .isEqualByComparingTo("200.00");
        assertThat(first.pointsEarned())
                .as("and each of those fifty-euro deposits was itself what carried its week over "
                        + "the line, so each is paid at the run that now includes that week: 50 at "
                        + "1.00, then 55, 60 and 65 as the ladder climbs a step a week")
                .isEqualTo(50 + 55 + 60 + 65);
        assertThat(first.securedWeeks())
                .as("three Sundays have closed a secured week by then, and the week under way is "
                        + "already secured too, which is the reading the account's own screen gives")
                .isEqualTo(4);
        assertThat(first.pointsStanding())
                .as("nothing expired and no bonus was paid, so the balance is what was earned")
                .isEqualTo(230);
    }

    @Test
    void the_ladder_stops_climbing_at_the_cap_and_a_later_month_is_paid_the_same_as_the_one_before() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                .anEverydayAccountHolding("5000.00")
                .aWeeklyRuleEveryMondayOf("50.00")
                .build());

        assertThat(year.months().get(5).pointsEarned())
                .as("by the sixth month every week is paid at the cap, so a month of four Mondays "
                        + "earns four lots of 75 and the rate has stopped being news")
                .isEqualTo(4 * 75);
    }

    @Test
    void a_batch_goes_on_the_day_its_twelve_months_are_up() {
        LocalDate theDayTheyGo = LocalDate.of(2026, 11, 3);
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                .pointsGoingOn(theDayTheyGo, 120)
                .build());

        assertThat(year.months().get(0).pointsThatExpired())
                .as("the day they go is after the first row closes, so the first month loses none")
                .isZero();
        assertThat(year.months().get(1).pointsThatExpired())
                .as("and the second row, which closes on the fourteenth of November, is the one "
                        + "that holds the third")
                .isEqualTo(120);
        assertThat(year.months().get(1).pointsStanding())
                .as("a future where points are lost is not a future where fewer are gained: the "
                        + "balance falls by exactly what went")
                .isZero();
    }

    @Test
    void points_already_owed_to_the_nightly_sweep_go_on_the_first_day_the_fold_walks() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                // A day already gone. The batch reached its twelve months this lunchtime and is
                // waiting on tonight's sweep, which is what a date in the past on that read means.
                .pointsGoingOn(A_MONDAY.minusDays(2), 40)
                .build());

        assertThat(year.months().get(0).pointsThatExpired())
                .as("points the sweep already owes are taken on the first night this walks rather "
                        + "than carried through the year as points the customer still has")
                .isEqualTo(40);
    }

    @Test
    void an_anniversary_and_an_expiry_landing_on_one_day_are_both_in_that_months_row() {
        LocalDate theDayBoth = LocalDate.of(2026, 11, 3);
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                .pointsGoingOn(theDayBoth, 120)
                // A deposit made a year and a day before the batch's own day, so that its first
                // anniversary falls on exactly the morning those points go.
                .aDepositOf("300.00", theDayBoth.minusYears(1))
                .build());

        AMonthOfTheFuture november = year.months().get(1);
        assertThat(november.pointsThatExpired())
                .as("the batch reached its twelve months that morning and went")
                .isEqualTo(120);
        assertThat(november.pointsABonusPaid())
                .as("and the deposit reached its own twelve months the same morning and paid a "
                        + "tenth of the three hundred euros still in it — the two rules are counted "
                        + "separately because a net of them would tell the customer nothing "
                        + "happened")
                .isEqualTo(30);
        assertThat(november.pointsStanding())
                .as("the sweep runs at three and the anniversaries at half past, so the bonus "
                        + "arrives after the expiry and is not itself taken by it")
                .isEqualTo(30);
    }

    @Test
    void a_deposit_holding_less_than_ten_euros_is_worth_nothing_on_its_anniversary() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                .aDepositOf("9.99", LocalDate.of(2025, 11, 3))
                .build());

        assertThat(year.months().stream().mapToLong(AMonthOfTheFuture::pointsABonusPaid).sum())
                .as("a tenth of nine whole euros rounds away, and an anniversary worth nothing is "
                        + "not an event — the same rule that has always made EUR 0.99 earn no point")
                .isZero();
    }

    @Test
    void points_earned_on_the_very_day_the_window_opened_go_on_the_very_day_it_closes() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                .anEverydayAccountHolding("5000.00")
                // Monday is the day the window opens on, and the run last caught up the evening
                // before — so this Monday is one the run still owes and the rule fires on the
                // opening day itself rather than only on the Mondays after it.
                .aWeeklyRuleEveryMondayOf("50.00")
                .theRunLastCaughtUpOn(A_MONDAY.minusDays(1))
                .build());

        AMonthOfTheFuture last = year.months().get(11);
        assertThat(last.closesOn()).isEqualTo(A_YEAR_ON);
        assertThat(last.pointsThatExpired())
                .as("the fifty points the opening morning earned are twelve months old on the last "
                        + "day of the window, which is inside it — everything earned after that "
                        + "morning goes after the window closes, and this one day is the whole of "
                        + "the expiry a fold can create for itself")
                .isEqualTo(50);
        assertThat(last.pointsABonusPaid())
                .as("and the deposit that earned them reaches its own first anniversary the same "
                        + "morning, paying a tenth of the fifty euros still in it — which arrives "
                        + "after the expiry, because the sweep runs at three and the anniversaries "
                        + "at half past")
                .isEqualTo(5);
    }

    @Test
    void a_deposit_earns_only_on_what_takes_the_customer_above_the_most_they_have_ever_saved() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                .anEverydayAccountHolding("5000.00")
                .aWeeklyRuleEveryMondayOf("50.00")
                // A hundred and fifty euros were taken out at some point and never put back, so the
                // mark sits a hundred and fifty above what is actually held.
                .holding("0.00").withAMarkOf("150.00")
                .build());

        AMonthOfTheFuture first = year.months().get(0);
        assertThat(first.balance())
                .as("four Mondays still moved fifty euros each, because the mark decides what earns "
                        + "and never what moves")
                .isEqualByComparingTo("200.00");
        assertThat(first.pointsEarned())
                .as("but the first three deposits only climb back to where the customer already was "
                        + "and earn nothing at all — a euro saved twice is one euro — and only the "
                        + "fifty of the fourth deposit is new saving, paid at the run of four weeks")
                .isEqualTo(65);
        assertThat(year.thingsThatHappen())
                .as("and each of those three mornings is a dated event carrying the euros that "
                        + "earned nothing, because a customer reading points that simply fail to "
                        + "rise reads the simulator as broken rather than as the rule it is")
                .filteredOn(thing -> thing.kind()
                        == AKindOfThingThatHappens.MONEY_ARRIVES_AND_EARNS_NOTHING)
                .hasSize(3)
                .allSatisfy(thing -> assertThat(thing.figure()).isEqualByComparingTo("50.00"));
    }

    @Test
    void a_rule_that_cannot_be_afforded_moves_nothing_and_is_not_tried_again_the_next_morning() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                // Enough for two Mondays and not for the third.
                .anEverydayAccountHolding("120.00")
                .aWeeklyRuleEveryMondayOf("50.00")
                .build());

        assertThat(year.months().get(0).balance())
                .as("a fixed amount moves all of itself or none of it, so the third Monday took "
                        + "nothing rather than the twenty that was left — and the run's cursor moves "
                        + "past an occurrence that fell short, so no later morning makes it up")
                .isEqualByComparingTo("100.00");
    }

    @Test
    void a_paused_rule_contributes_nothing_and_is_never_made_up() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                .anEverydayAccountHolding("5000.00")
                .aWeeklyRuleEveryMondayOf("50.00")
                .paused()
                .build());

        assertThat(year.months().get(11).balance())
                .as("nothing falls due while a rule is paused, which is what a pause already is in "
                        + "this application — and a pause has no end date for a fold to guess at")
                .isEqualByComparingTo("0.00");
    }

    @Test
    void a_salary_lands_before_the_rules_fire_and_the_bills_are_taken_after_them() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                // Nothing in the everyday account at all: the only money it ever sees is the salary.
                .anEverydayAccountHolding("0.00")
                .aSalaryOf("2000.00").onDayOfTheMonth(15)
                .aBillOf("1900.00").onDayOfTheMonth(15)
                // On the fifteenth of the month, and the fifteenth of September is the day after
                // the window opened, so the first month sees exactly one of these mornings.
                .aMonthlyRuleOnDayOfTheMonth(15, "50.00")
                .build());

        assertThat(year.months().get(0).balance())
                .as("the salary lands at one, the rule moves fifty at two and the rent is taken at "
                        + "half past — in any other order the fifty would not have been there to "
                        + "move, and a customer sweeping into savings ahead of the rent is exactly "
                        + "what the night's running order decides")
                .isEqualByComparingTo("50.00");
    }

    @Test
    void a_bill_that_could_not_be_paid_is_paid_out_of_the_next_salary_before_the_new_one_is() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                .anEverydayAccountHolding("0.00")
                .aSalaryOf("1000.00").onDayOfTheMonth(15)
                .aBillOf("900.00").onDayOfTheMonth(20)
                // Everything above nothing is swept into savings the day after the salary lands, so
                // the everyday account is empty when the rent falls due five days later.
                .aSweepOnDayOfTheMonth(16, "0.00")
                .build());

        assertThat(year.months().get(0).balance())
                .as("the sweep took the whole salary on the sixteenth and the rent could not be "
                        + "paid on the twentieth, so it stayed owed")
                .isEqualByComparingTo("1000.00");
        assertThat(year.months().get(1).balance())
                .as("and the next salary settled what was already owed before the sweep could look "
                        + "at it, which is the whole of 'outstanding first': only the hundred left "
                        + "over reached savings")
                .isEqualByComparingTo("1100.00");
    }

    @Test
    void nothing_at_all_happens_to_an_account_with_no_money_no_rules_and_no_salary() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith().build());

        assertThat(year.called())
                .as("the branch nobody asked for is still named, and in the customer's own words "
                        + "for it rather than in the application's")
                .isEqualTo(TheNightReplayed.THE_YEAR_ALREADY_UNDER_WAY);
        assertThat(year.months())
                .as("twelve rows of nothing, because a column that went short would be a column "
                        + "somebody had to explain")
                .allSatisfy(month -> {
                    assertThat(month.balance()).isEqualByComparingTo("0.00");
                    assertThat(month.pointsStanding()).isZero();
                    assertThat(month.pointsEarned()).isZero();
                    assertThat(month.pointsABonusPaid()).isZero();
                    assertThat(month.pointsThatExpired()).isZero();
                });
    }

    @Test
    void the_morning_the_run_has_already_settled_is_not_saved_a_second_time() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                .anEverydayAccountHolding("5000.00")
                // The window opens on a Monday and this rule moves on a Monday, so the run fired it
                // at two this morning and its fifty euros are already in the balance this started
                // from. A fold that walked from today assuming nothing had been settled would move
                // them again.
                .aWeeklyRuleEveryMondayOf("50.00")
                .build());

        assertThat(year.months().get(0).balance())
                .as("four Mondays after the opening one, and not five: this morning's transfer "
                        + "belongs to the balance the snapshot was taken at rather than to the year "
                        + "ahead of it, and counting it twice would put the whole branch one "
                        + "morning's saving ahead of the application it is predicting")
                .isEqualByComparingTo("200.00");
    }

    @Test
    void the_mornings_the_run_still_owes_all_fire_on_the_first_day_the_fold_walks() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                .anEverydayAccountHolding("5000.00")
                .aWeeklyRuleEveryMondayOf("50.00")
                // A clock wound four weeks forward and nobody ran the job: four Mondays have fallen
                // since the run last caught up, and the next run will fire every one of them.
                .theRunLastCaughtUpOn(A_MONDAY.minusWeeks(4))
                .build());

        assertThat(year.months().get(0).balance())
                .as("the four Mondays the run owes fire on the morning the window opens, exactly as "
                        + "the next run will fire them, and the four inside the first month follow "
                        + "— a branch that showed only the ones still to come would be denying "
                        + "transfers that are about to happen")
                .isEqualByComparingTo("400.00");
    }

    @Test
    void an_anniversary_the_sweep_has_not_caught_is_paid_on_the_first_day_the_fold_walks() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                // Landed a year and a fortnight ago, so its first anniversary fell a fortnight
                // before the window opened — and Loyalty says that anniversary is the one it is
                // still owed, because nobody has run the sweep since.
                .aDepositOwedAnAnniversaryNobodyHasPaid("300.00", A_MONDAY.minusYears(1).minusWeeks(2))
                .build());

        assertThat(year.months().get(0).pointsABonusPaid())
                .as("the application pays that bonus on the next sweep, so the branch pays it on "
                        + "the morning the window opens — a fold that read the calendar alone would "
                        + "take every anniversary before today as paid and quietly lose the "
                        + "customer thirty points they are already owed")
                .isEqualTo(30);
        assertThat(year.thingsThatHappen())
                .as("dated on the day the branch acts on it, which is the day the window opens")
                .contains(AThingThatHappens.worth(A_MONDAY,
                        AKindOfThingThatHappens.A_BONUS_IS_PAID, 30));
    }

    @Test
    void an_anniversary_the_sweep_has_already_paid_is_not_paid_again() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                // The same deposit, and this time the sweep has caught up: Loyalty promises the
                // anniversary a year from the one that fell, not the one that fell.
                .aDepositOf("300.00", A_MONDAY.minusYears(1).minusWeeks(2))
                .build());

        assertThat(year.months().get(0).pointsABonusPaid())
                .as("nothing is owed on the morning the window opens, because the row is already "
                        + "there — the reading that cannot pay a customer twice")
                .isZero();
        assertThat(year.months().stream().mapToLong(AMonthOfTheFuture::pointsABonusPaid).sum())
                .as("and the anniversary it is promised next falls inside the window, so the year "
                        + "still pays it exactly once")
                .isEqualTo(30);
    }

    @Test
    void the_things_that_happen_come_back_in_day_order_and_the_order_the_night_runs_them() {
        LocalDate theDayBoth = LocalDate.of(2026, 11, 3);
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                .pointsGoingOn(theDayBoth, 120)
                .aDepositOf("300.00", theDayBoth.minusYears(1))
                .build());

        assertThat(year.thingsThatHappen())
                .as("the expiry sweep runs at three and the loyalty sweep at half past, so on a day "
                        + "that does both the points go and then the bonus arrives — the order two "
                        + "markers sharing a day are already put in on the account's own bar")
                .containsExactly(
                        AThingThatHappens.worth(theDayBoth, AKindOfThingThatHappens.POINTS_EXPIRE, 120),
                        AThingThatHappens.worth(theDayBoth, AKindOfThingThatHappens.A_BONUS_IS_PAID, 30));
    }

    @Test
    void an_anniversary_worth_nothing_is_not_a_thing_that_happens() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                .aDepositOf("9.99", LocalDate.of(2025, 11, 3))
                .build());

        assertThat(year.thingsThatHappen())
                .as("a tenth of nine whole euros rounds away, and an anniversary that pays nothing "
                        + "writes no row and puts no marker on the account's own bar — so it is not "
                        + "a thing that happens in a branch either")
                .isEmpty();
    }

    @Test
    void a_run_of_weeks_that_ends_is_dated_on_the_sunday_it_ended_on() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                // Three weeks behind them and a week under way that nothing has landed in, and
                // nothing is ever going to: no rule and no salary.
                .aRunOf(3, new BigDecimal("0.00"))
                .build());

        assertThat(year.thingsThatHappen())
                .as("the run ends on the Sunday the empty week closes, carrying the three weeks "
                        + "that were lost — and it happens once, because a customer with no run "
                        + "left does not lose one every Sunday for a year")
                .containsExactly(AThingThatHappens.worth(A_MONDAY.plusDays(6),
                        AKindOfThingThatHappens.A_WEEK_IS_LOST, 3));
    }

    @Test
    void another_amount_each_week_lands_on_the_day_it_starts_and_on_every_seventh_day_after_it() {
        HowAScenarioTurnsOut branch = theYearThatFollowsIf(anAccountWith().build(),
                anotherEachWeekOf("25.00", A_MONDAY));

        assertThat(branch.months().get(0).balance())
                .as("the fourteenth, the twenty-first, the twenty-eighth, the fifth and the twelfth "
                        + "— five weeks between the day the window opens and the day the first row "
                        + "closes, counted in sevens from the day the customer named rather than "
                        + "rounded to a Monday they did not")
                .isEqualByComparingTo("125.00");
        assertThat(branch.months().get(0).pointsEarned())
                .as("twenty-five a week never carries a week over the fifty a week has to take in, "
                        + "so every one of them is paid at the ordinary rate — a sacrifice that "
                        + "earns and does not climb, which is exactly what the customer asked to be "
                        + "shown")
                .isEqualTo(5 * 25);
        assertThat(branch.months().get(11).balance())
                .as("and it goes on to the end of the window rather than for a month or a quarter, "
                        + "which is what 'each week' means: fifty-three of them by the day the year "
                        + "closes")
                .isEqualByComparingTo("1325.00");
        assertThat(branch.months().get(11).pointsThatExpired())
                .as("an extra started on the very day the window opens earns points that are twelve "
                        + "months old on the very day it closes, which is inside it — every later "
                        + "week's points go after the year ends and are never seen")
                .isEqualTo(25);
    }

    @Test
    void the_extra_is_added_to_what_the_rules_already_move_rather_than_put_in_their_place() {
        TheStartingPoint standing = anAccountWith()
                .anEverydayAccountHolding("5000.00")
                .aWeeklyRuleEveryMondayOf("50.00")
                .build();

        HowAScenarioTurnsOut carryingOn = theYearThatFollows(standing);
        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                anotherEachWeekOf("25.00", A_MONDAY));

        assertThat(carryingOn.months().get(0).balance())
                .as("four Mondays of the rule the account already has")
                .isEqualByComparingTo("200.00");
        assertThat(branch.months().get(0).balance())
                .as("and the same four, plus five weeks of the extra beside them: more means more, "
                        + "and a customer who wanted to move the rule's own figure would be editing "
                        + "the rule")
                .isEqualByComparingTo("325.00");
    }

    @Test
    void an_extra_that_carries_a_week_over_the_line_is_paid_at_the_run_it_has_just_secured() {
        TheStartingPoint standing = anAccountWith().build();

        HowAScenarioTurnsOut carryingOn = theYearThatFollows(standing);
        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                anotherEachWeekOf("50.00", A_MONDAY));

        assertThat(carryingOn.months().get(0).securedWeeks())
                .as("an account with no rule and no salary secures nothing at all")
                .isZero();
        assertThat(branch.months().get(0).pointsEarned())
                .as("each week's fifty is what carries that week over the line, so each is paid at "
                        + "the run that now includes it: fifty at the ordinary rate, then 55, 60, "
                        + "65 and 70 as the ladder climbs a step a week")
                .isEqualTo(50 + 55 + 60 + 65 + 70);
        assertThat(branch.months().get(0).securedWeeks())
                .as("four Sundays have closed a secured week by the day the first row closes, and "
                        + "the week under way is secured too — a run the branch would not have had "
                        + "at all, which is the half of the answer a balance cannot show")
                .isEqualTo(5);
    }

    @Test
    void an_extra_that_starts_later_in_the_year_pays_nothing_before_the_day_it_starts() {
        HowAScenarioTurnsOut branch = theYearThatFollowsIf(anAccountWith().build(),
                anotherEachWeekOf("25.00", A_MONDAY.plusWeeks(6)));

        assertThat(branch.months().get(0).balance())
                .as("the customer said they would start in six weeks, so the first month of the "
                        + "branch is the first month of the year they are already in")
                .isEqualByComparingTo("0.00");
        assertThat(branch.months().get(1).balance())
                .as("and three of them fall between the two rows' closing days once it has started")
                .isEqualByComparingTo("75.00");
    }

    @Test
    void a_goal_arrives_earlier_in_a_branch_that_puts_more_away_each_week() {
        TheStartingPoint standing = anAccountWith()
                .aWeeklyCapacityOf("50.00")
                .aGoalOf("400.00", null)
                .build();

        HowAScenarioTurnsOut carryingOn = theYearThatFollows(standing);
        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                anotherEachWeekOf("50.00", A_MONDAY));

        assertThat(theGoalsReachedIn(carryingOn))
                .as("four hundred euros at the fifty a week the customer has declared is eight "
                        + "weeks")
                .containsExactly(new AThingThatHappens(A_MONDAY.plusWeeks(8),
                        AKindOfThingThatHappens.A_GOAL_IS_REACHED, new BigDecimal("400.00")));
        assertThat(theGoalsReachedIn(branch))
                .as("and at a hundred it is four — the extra reaches the goals because it is added "
                        + "to the weekly figure the plan spends, which is the whole of what the "
                        + "customer came to ask: a month earlier, on a day the branch names")
                .containsExactly(new AThingThatHappens(A_MONDAY.plusWeeks(4),
                        AKindOfThingThatHappens.A_GOAL_IS_REACHED, new BigDecimal("400.00")));
    }

    @Test
    void a_branch_that_saves_more_gives_the_goals_a_plan_on_an_account_that_had_none() {
        TheStartingPoint standing = anAccountWith()
                .aGoalOf("400.00", null)
                .build();

        assertThat(theGoalsReachedIn(theYearThatFollows(standing)))
                .as("nobody has said how fast anything fills, so the goal has no date at all — not "
                        + "a slow one, which is a different sentence")
                .isEmpty();
        assertThat(theGoalsReachedIn(
                theYearThatFollowsIf(standing, anotherEachWeekOf("100.00", A_MONDAY))))
                .as("and a customer asking what a hundred a week would do has, inside that branch, "
                        + "said a figure — so the branch has a plan where the account has none, and "
                        + "the goal in it has a day")
                .containsExactly(new AThingThatHappens(A_MONDAY.plusWeeks(4),
                        AKindOfThingThatHappens.A_GOAL_IS_REACHED, new BigDecimal("400.00")));
    }

    @Test
    void a_goal_is_reached_on_the_monday_the_money_is_all_there_by() {
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                .aWeeklyCapacityOf("100.00")
                .aGoalOf("400.00", null)
                .build());

        assertThat(year.thingsThatHappen())
                .as("four hundred euros at a hundred a week is four weeks, counted forward from the "
                        + "Monday this week began on — the Monday the money is all there by, which "
                        + "is the day the goals screen itself names rather than the Monday the last "
                        + "week of saving starts on")
                .containsExactly(new AThingThatHappens(A_MONDAY.plusWeeks(4),
                        AKindOfThingThatHappens.A_GOAL_IS_REACHED, new BigDecimal("400.00")));
    }

    @Test
    void a_deadline_the_branch_goes_past_without_the_goal_arriving_is_raised_once_on_the_deadline() {
        LocalDate wantedBy = A_MONDAY.plusWeeks(2);
        HowAScenarioTurnsOut year = theYearThatFollows(anAccountWith()
                // Ten euros a week against a thousand, so the goal is a very long way off and the
                // deadline a fortnight out cannot be met.
                .aWeeklyCapacityOf("10.00")
                .aGoalOf("1000.00", wantedBy)
                .build());

        assertThat(year.thingsThatHappen())
                .as("once, on the day it was wanted by, and not on every day after it: a goal that "
                        + "is late stays late for the rest of the year, and three hundred markers "
                        + "would be saying one thing three hundred times")
                .containsExactly(new AThingThatHappens(wantedBy,
                        AKindOfThingThatHappens.A_DEADLINE_IS_MISSED, new BigDecimal("1000.00")));
    }

    private static HowAScenarioTurnsOut theYearThatFollows(TheStartingPoint standing) {
        return TheNightReplayed.theYearThatFollows(standing,
                TheNightReplayed.THE_YEAR_ALREADY_UNDER_WAY, List.of());
    }

    /**
     * The same year, folded with the changes a customer would make to it, under a name of their own.
     *
     * <p>Named rather than left as the do-nothing branch's name, because a branch that changes
     * something is not the year already under way and the two are put beside each other on a screen.
     */
    private static HowAScenarioTurnsOut theYearThatFollowsIf(TheStartingPoint standing,
                                                             AnAdjustment... changes) {
        return TheNightReplayed.theYearThatFollows(standing, "If I did this instead",
                List.of(changes));
    }

    /**
     * Only the goals a branch reaches, which is what a branch that changes a weekly figure is asked
     * about.
     *
     * <p>Filtered rather than asserted whole, because a branch that puts money away from the morning
     * the window opens also earns points that are twelve months old on the day it closes — true,
     * pinned elsewhere, and nothing at all to do with when the boat arrives.
     */
    private static List<AThingThatHappens> theGoalsReachedIn(HowAScenarioTurnsOut branch) {
        return branch.thingsThatHappen().stream()
                .filter(thing -> thing.kind() == AKindOfThingThatHappens.A_GOAL_IS_REACHED)
                .toList();
    }

    /** Another amount every week, from a day, which is the first change this fold learnt about. */
    private static AnAdjustment anotherEachWeekOf(String amount, LocalDate from) {
        return new SavingMoreEachWeek(new BigDecimal(amount), from);
    }

    private static AnAccountAsItStands anAccountWith() {
        return new AnAccountAsItStands();
    }

    /**
     * A present to fold from, assembled a fact at a time.
     *
     * <p>Built here rather than read off a running application, which is the point of the fold being
     * a pure function: a snapshot is fifteen figures and a test that wants a deposit made exactly a
     * year and a day ago can simply say so, instead of winding a clock the rest of the run shares.
     */
    private static final class AnAccountAsItStands {

        private BigDecimal balance = new BigDecimal("0.00");
        private BigDecimal stillSaved;
        private BigDecimal mark;
        private BigDecimal everydayBalance = new BigDecimal("0.00");
        private DeclaredIncome income = new DeclaredIncome(AN_EVERYDAY_ACCOUNT, null, null, null, null);
        private final List<ADeclaredBill> bills = new ArrayList<>();
        private final List<DepositStillHoldingMoney> deposits = new ArrayList<>();
        private final Map<Long, NextAnniversaryOfADeposit> paysNext = new LinkedHashMap<>();
        private final List<PointsExpiringOnADay> pointsGoing = new ArrayList<>();
        private final List<RecordedSavingRule> rules = new ArrayList<>();
        private final List<RecordedGoal> goals = new ArrayList<>();
        private BigDecimal weeklyCapacity;
        private int runOfWeeks;
        private BigDecimal newSavingsThisWeek = new BigDecimal("0.00");
        private BigDecimal pendingAmount;

        /**
         * The agreement this fixture's account is living under.
         *
         * <p><strong>An account with nothing on record by default, and that is a decision rather
         * than a shortcut.</strong> Every test in this class is about the order of a night — which
         * morning a rule fires on, which week a deposit is counted into, whether a batch goes before
         * a bonus is paid — and none of them is about a rate. The reading an account with no
         * agreement gets is the one this whole feature falls back on everywhere: the multiple that
         * changes nothing, the tenth every anniversary used to pay, no interest and nothing in the
         * way of the money. So these tests go on asserting the figures they always asserted, and
         * what a product does to those figures is asserted by the handful of tests at the foot of
         * this class that hand in an agreement on purpose — and, over a wound clock, by
         * {@code TheSimulatorAgreesWithTheApplicationOnANoticeAccountAndAFixedTermApiTest}.
         */
        private WhatAnAccountsProductPaysAndAsksFor product =
                WhatAnAccountsProductPaysAndAsksFor.whatAnAccountWithNoAgreementIsOn(AN_ACCOUNT);

        /** The notices standing on it, which is none unless a test is about a notice account. */
        private List<NoticeGiven> noticesStanding = List.of();

        /** Puts the account on an agreement, for the tests whose subject is what a product pays. */
        private AnAccountAsItStands on(WhatAnAccountsProductPaysAndAsksFor agreement) {
            this.product = agreement;
            return this;
        }

        /** Notice already given on an amount, on a day, for the tests about a notice account. */
        private AnAccountAsItStands withNoticeGivenOn(String amount, LocalDate givenOn,
                                                      int noticeDays) {
            List<NoticeGiven> standing = new ArrayList<>(noticesStanding);
            standing.add(new NoticeGiven(standing.size() + 1L, AN_ACCOUNT, new BigDecimal(amount),
                    new BigDecimal(amount), givenOn,
                    givenOn.plusDays(noticeDays), false, noticeDays));
            this.noticesStanding = standing;
            return this;
        }

        /**
         * How far the nightly run has already got with every rule, the salary and the bills.
         *
         * <p>Through the start of the day the window opens on by default, which is an account whose
         * night has already run: the transfers of this morning are in the balance and are not owed.
         * That is the ordinary state of the application at any hour a customer is awake, and a
         * fixture that defaulted to anything else would be testing a wound clock by accident.
         */
        private Instant theRunHasSettledThrough = THE_WINDOW_OPENS;

        private AnAccountAsItStands holding(String balance) {
            this.balance = new BigDecimal(balance);
            return this;
        }

        private AnAccountAsItStands withAMarkOf(String mark) {
            this.mark = new BigDecimal(mark);
            return this;
        }

        private AnAccountAsItStands aRunOf(int weeks, BigDecimal newSavingsThisWeek) {
            this.runOfWeeks = weeks;
            this.newSavingsThisWeek = newSavingsThisWeek;
            return this;
        }

        private AnAccountAsItStands anEverydayAccountHolding(String amount) {
            this.everydayBalance = new BigDecimal(amount);
            return this;
        }

        private AnAccountAsItStands aSalaryOf(String amount) {
            this.pendingAmount = new BigDecimal(amount);
            return this;
        }

        private AnAccountAsItStands aBillOf(String amount) {
            this.pendingAmount = new BigDecimal(amount);
            return this;
        }

        /** Finishes whichever of a salary or a bill was named last. */
        private AnAccountAsItStands onDayOfTheMonth(int dayOfMonth) {
            BigDecimal amount = pendingAmount;
            pendingAmount = null;
            if (!income.isDeclared()) {
                income = new DeclaredIncome(AN_EVERYDAY_ACCOUNT, dayOfMonth, amount,
                        Instant.EPOCH, null);
                return this;
            }
            bills.add(new ADeclaredBill(bills.size() + 1L, AN_EVERYDAY_ACCOUNT,
                    "A bill", dayOfMonth, amount, Instant.EPOCH, BillState.STANDING, null, null));
            return this;
        }

        /**
         * The nightly run last caught up on that day, so everything due since is genuinely owed and
         * fires on the first morning the fold walks — which is the state of every rule on a clock
         * somebody wound forward without running the jobs.
         */
        private AnAccountAsItStands theRunLastCaughtUpOn(LocalDate day) {
            this.theRunHasSettledThrough =
                    day.atStartOfDay(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toInstant();
            return this;
        }

        private AnAccountAsItStands aDepositOf(String amount, LocalDate landedOn) {
            return aDeposit(amount, landedOn, false);
        }

        /**
         * A deposit whose latest anniversary has fallen and that the loyalty sweep has not paid —
         * which is what Loyalty reports for the few hours between an anniversary arriving and the
         * sweep at half past three the next morning, and for as long as nobody runs the job.
         */
        private AnAccountAsItStands aDepositOwedAnAnniversaryNobodyHasPaid(String amount,
                                                                          LocalDate landedOn) {
            return aDeposit(amount, landedOn, true);
        }

        private AnAccountAsItStands aDeposit(String amount, LocalDate landedOn, boolean owed) {
            long depositId = deposits.size() + 1L;
            Instant landedAt =
                    landedOn.atTime(14, 0).atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toInstant();
            deposits.add(new DepositStillHoldingMoney(depositId, A_CUSTOMER, AN_ACCOUNT,
                    new BigDecimal(amount), landedAt));
            // The answer Loyalty gives: the earliest anniversary this deposit is owed and has not
            // been paid, and otherwise the calendar's next. Worked out here the way that read works
            // it out rather than written down as a date, so a fixture cannot promise a day no
            // anniversary of this deposit falls on.
            int passed = LoyaltyAnniversary.anniversariesPassedBy(landedAt, THE_WINDOW_OPENS);
            int paysNextOrdinal = owed && passed > 0 ? passed : passed + 1;
            paysNext.put(depositId, new NextAnniversaryOfADeposit(depositId,
                    LoyaltyAnniversary.dayOf(LoyaltyAnniversary.anniversaryOf(landedAt, paysNextOrdinal)),
                    LoyaltyRate.pointsOn(LoyaltyRate.wholeEurosIn(new BigDecimal(amount)),
                            LoyaltyRate.THE_TENTH_EVERY_ANNIVERSARY_USED_TO_PAY)));
            balance = balance.add(new BigDecimal(amount));
            return this;
        }

        private AnAccountAsItStands pointsGoingOn(LocalDate day, long points) {
            pointsGoing.add(new PointsExpiringOnADay(day, points));
            return this;
        }

        /** What the holder says they can put away in a week, which is what funds every goal. */
        private AnAccountAsItStands aWeeklyCapacityOf(String weekly) {
            this.weeklyCapacity = new BigDecimal(weekly);
            return this;
        }

        /** A goal with nothing allocated to it yet, at the back of the order the goals compete in. */
        private AnAccountAsItStands aGoalOf(String target, LocalDate wantedBy) {
            long goalId = goals.size() + 1L;
            goals.add(new RecordedGoal(goalId, AN_ACCOUNT, "A goal " + goalId,
                    new BigDecimal(target), wantedBy, goals.size() + 1, GoalState.LIVE,
                    GoalStatus.STILL_SAVING, new BigDecimal("0.00"), new BigDecimal(target),
                    null, null, null, Instant.EPOCH, null));
            return this;
        }

        private AnAccountAsItStands aWeeklyRuleEveryMondayOf(String amount) {
            return aRule(RuleTrigger.WEEKLY, DayOfWeek.MONDAY, null, HowMuchMoves.A_FIXED_AMOUNT,
                    new BigDecimal(amount), null);
        }

        private AnAccountAsItStands aMonthlyRuleOnDayOfTheMonth(int dayOfMonth, String amount) {
            return aRule(RuleTrigger.MONTHLY, null, dayOfMonth, HowMuchMoves.A_FIXED_AMOUNT,
                    new BigDecimal(amount), null);
        }

        private AnAccountAsItStands aSweepOnDayOfTheMonth(int dayOfMonth, String floor) {
            return aRule(RuleTrigger.MONTHLY, null, dayOfMonth, HowMuchMoves.EVERYTHING_ABOVE,
                    null, new BigDecimal(floor));
        }

        private AnAccountAsItStands paused() {
            RecordedSavingRule last = rules.remove(rules.size() - 1);
            rules.add(new RecordedSavingRule(last.id(), last.savingsAccountId(),
                    last.currentAccountId(), last.name(), last.trigger(), last.dayOfWeek(),
                    last.dayOfMonth(), last.howMuchMoves(), last.amount(), last.floor(),
                    last.split(), RuleState.PAUSED, last.createdAt(), Instant.EPOCH, null,
                    last.nextFiresOn(), last.nextMoves()));
            return this;
        }

        private AnAccountAsItStands aRule(RuleTrigger trigger, DayOfWeek dayOfWeek,
                                          Integer dayOfMonth, HowMuchMoves howMuchMoves,
                                          BigDecimal amount, BigDecimal floor) {
            rules.add(new RecordedSavingRule(rules.size() + 1L, AN_ACCOUNT, AN_EVERYDAY_ACCOUNT,
                    "A rule", trigger, dayOfWeek, dayOfMonth, howMuchMoves, amount, floor,
                    List.of(), RuleState.LIVE, Instant.EPOCH, null, null, null,
                    new WhatWouldMove(new BigDecimal("0.00"), floor, false, List.of(),
                            new BigDecimal("0.00"))));
            return this;
        }

        private TheStartingPoint build() {
            BigDecimal held = stillSaved == null ? balance : stillSaved;
            // A customer at their peak by default: the mark is what they hold, so everything a
            // branch pays in is new saving. A test that wants a withdrawal behind them says so.
            BigDecimal everEarnedOn = mark == null ? held : mark;
            Map<Long, Instant> rulesSettledThrough = new LinkedHashMap<>();
            for (RecordedSavingRule rule : rules) {
                rulesSettledThrough.put(rule.id(), theRunHasSettledThrough);
            }
            Map<Long, Instant> billsSettledThrough = new LinkedHashMap<>();
            for (ADeclaredBill bill : bills) {
                billsSettledThrough.put(bill.billId(), theRunHasSettledThrough);
            }
            return new TheStartingPoint(A_MONDAY, A_YEAR_ON, AN_ACCOUNT, A_CUSTOMER, balance,
                    deposits, pointsGoing, TWELVE_MONTHS,
                    new AllocationsOnAnAccount(AN_ACCOUNT, balance, new BigDecimal("0.00"), balance,
                            goals),
                    new SavingCapacityOnAnAccount(AN_ACCOUNT, weeklyCapacity,
                            weeklyCapacity == null ? null : Instant.EPOCH, WHAT_A_WEEK_ASKS_FOR),
                    rules, rulesSettledThrough, paysNext,
                    List.of(new ACurrentAccountBehindIt(AN_EVERYDAY_ACCOUNT, everydayBalance,
                            income, bills,
                            income.isDeclared() ? theRunHasSettledThrough : null,
                            billsSettledThrough)),
                    new WeekAndStreak(
                            new NewSavingsThisWeek(SavingsWeek.containing(A_MONDAY),
                                    newSavingsThisWeek, WHAT_A_WEEK_ASKS_FOR),
                            new StreakOfSecuredWeeks(runOfWeeks, Math.max(runOfWeeks, 1),
                                    TheLadderARunClimbs.theLadderIn(
                                            THE_SCHEME_AS_IT_STANDS.onTheDayOf(A_MONDAY))),
                            THE_SCHEME_AS_IT_STANDS.onTheDayOf(A_MONDAY).version()),
                    THE_SCHEME_AS_IT_STANDS,
                    held, everEarnedOn, product,
                    new TheNoticeOnAnAccount(AN_ACCOUNT, product.noticeDays(),
                            new BigDecimal("0.00"), new BigDecimal("0.00"), noticesStanding));
        }
    }

    @Test
    void moving_one_goals_deadline_moves_the_day_another_goal_is_reached() {
        LocalDate theCarIsWantedBy = A_MONDAY.plusWeeks(10);
        LocalDate theHolidayIsWantedBy = A_MONDAY.plusWeeks(8);
        TheStartingPoint standing = anAccountWith()
                .aWeeklyCapacityOf("100.00")
                // A thousand wanted in ten weeks is the whole hundred a week, so the goal behind it
                // is given nothing at all and never arrives.
                .aGoalOf("1000.00", theCarIsWantedBy)
                .aGoalOf("400.00", theHolidayIsWantedBy)
                .build();

        HowAScenarioTurnsOut carryingOn = theYearThatFollows(standing);
        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                movingTheDeadlineOf(1, A_MONDAY.plusWeeks(20)));

        assertThat(theGoalsReachedIn(carryingOn))
                .as("the car takes the whole plan to arrive in the ten weeks it is wanted in, so "
                        + "the holiday behind it is given nothing and has no day at all")
                .containsExactly(new AThingThatHappens(theCarIsWantedBy,
                        AKindOfThingThatHappens.A_GOAL_IS_REACHED, new BigDecimal("1000.00")));
        assertThat(theDeadlinesMissedIn(carryingOn))
                .as("and the holiday's own deadline goes by with the money never having reached it")
                .containsExactly(new AThingThatHappens(theHolidayIsWantedBy,
                        AKindOfThingThatHappens.A_DEADLINE_IS_MISSED, new BigDecimal("400.00")));
        assertThat(theGoalsReachedIn(branch))
                .as("two more months on the car halves what its deadline asks for every week, and "
                        + "the fifty a week that frees is what the holiday needed — so a change "
                        + "that moves no money at all moves the day a second goal arrives, which is "
                        + "the question this kind of change exists to answer")
                .containsExactly(
                        new AThingThatHappens(theHolidayIsWantedBy,
                                AKindOfThingThatHappens.A_GOAL_IS_REACHED, new BigDecimal("400.00")),
                        new AThingThatHappens(A_MONDAY.plusWeeks(20),
                                AKindOfThingThatHappens.A_GOAL_IS_REACHED, new BigDecimal("1000.00")));
        assertThat(theDeadlinesMissedIn(branch))
                .as("and neither of them is late: the car arrives on the day it is now wanted by "
                        + "and the holiday on the day it always was")
                .isEmpty();
    }

    @Test
    void a_goal_that_was_off_track_is_on_track_once_the_deadline_it_is_judged_against_moves() {
        LocalDate wantedBy = A_MONDAY.plusWeeks(10);
        TheStartingPoint standing = anAccountWith()
                // Fifty a week against a thousand is twenty weeks of saving, and the goal is wanted
                // in ten: the plan cannot find the hundred a week its deadline asks for.
                .aWeeklyCapacityOf("50.00")
                .aGoalOf("1000.00", wantedBy)
                .build();

        HowAScenarioTurnsOut carryingOn = theYearThatFollows(standing);
        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                movingTheDeadlineOf(1, A_MONDAY.plusWeeks(30)));

        assertThat(theDeadlinesMissedIn(carryingOn))
                .as("the projection lands ten weeks past the day it is wanted by, which is what "
                        + "OFF_TRACK is and what the marker on the day says out loud")
                .containsExactly(new AThingThatHappens(wantedBy,
                        AKindOfThingThatHappens.A_DEADLINE_IS_MISSED, new BigDecimal("1000.00")));
        assertThat(theDeadlinesMissedIn(branch))
                .as("and the same projection against a day thirty weeks out is ON_TRACK — the "
                        + "status is worked out against the deadline this branch has rather than "
                        + "against the account's, so the marker is simply not raised")
                .isEmpty();
        assertThat(theGoalsReachedIn(branch))
                .as("on the very Monday it was always going to arrive on: nothing about the money "
                        + "moved, only the day the customer would be happy to have it by")
                .containsExactly(new AThingThatHappens(A_MONDAY.plusWeeks(20),
                        AKindOfThingThatHappens.A_GOAL_IS_REACHED, new BigDecimal("1000.00")));
        assertThat(theGoalsReachedIn(carryingOn))
                .as("which is the same Monday the branch where nothing changed names")
                .isEqualTo(theGoalsReachedIn(branch));
    }

    @Test
    void moving_a_deadline_moves_no_euro_and_no_week_of_the_twelve_rows() {
        TheStartingPoint standing = anAccountWith()
                .anEverydayAccountHolding("5000.00")
                .aWeeklyRuleEveryMondayOf("60.00")
                .aWeeklyCapacityOf("50.00")
                .aGoalOf("1000.00", A_MONDAY.plusWeeks(10))
                .build();

        HowAScenarioTurnsOut carryingOn = theYearThatFollows(standing);
        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                movingTheDeadlineOf(1, A_MONDAY.plusWeeks(30)));

        assertThat(branch.months())
                .as("a deadline is a date and not a euro: the rule moves the same sixty every "
                        + "Monday, the same weeks are secured at the same rates, the same points "
                        + "are earned and expire — every one of the seven figures on all twelve "
                        + "rows is the figure the year already under way carries. What the change "
                        + "moves is which goal the plan funds first, and nothing else")
                .isEqualTo(carryingOn.months());
    }

    /** Only the deadlines a branch goes past without the goal having arrived. */
    private static List<AThingThatHappens> theDeadlinesMissedIn(HowAScenarioTurnsOut branch) {
        return branch.thingsThatHappen().stream()
                .filter(thing -> thing.kind() == AKindOfThingThatHappens.A_DEADLINE_IS_MISSED)
                .toList();
    }

    /** A goal wanted by another day, which is the change that moves no money at all. */
    private static AnAdjustment movingTheDeadlineOf(long goalId, LocalDate to) {
        return new MovingADeadline(goalId, to);
    }

    // --- Stopping for a while: the fold with a pause in it. ------------------------------------
    // Appended at the end of the class deliberately: three slices added a kind of change to this
    // fold at the same time, and the end of a file is the one place three people can all write
    // without any of them landing in the middle of somebody else's method.

    /** A stop between two days, both of them included, which is the second change this fold learnt. */
    private static AnAdjustment aStopFrom(LocalDate firstDay, LocalDate lastDay) {
        return new StoppingForAWhile(firstDay, lastDay);
    }

    @Test
    void a_standing_rule_does_not_fire_on_the_mornings_a_branch_has_stopped_saving_on() {
        TheStartingPoint standing = anAccountWith()
                .anEverydayAccountHolding("5000.00")
                .aWeeklyRuleEveryMondayOf("50.00")
                .build();

        HowAScenarioTurnsOut carryingOn = theYearThatFollows(standing);
        // The third and fourth Mondays of the window, and nothing either side of them.
        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                aStopFrom(A_MONDAY.plusWeeks(2), A_MONDAY.plusWeeks(3)));

        assertThat(carryingOn.months().get(0).balance())
                .as("the twenty-first, the twenty-eighth, the fifth and the twelfth: four Mondays "
                        + "of the rule between the day the window opens and the day the first row "
                        + "closes, the opening Monday having already fired before the snapshot")
                .isEqualByComparingTo("200.00");
        assertThat(branch.months().get(0).balance())
                .as("and two of them inside the stop, which is the same silence a paused rule "
                        + "already produces — nothing is moved on a morning its holder said they "
                        + "were stopping")
                .isEqualByComparingTo("100.00");
    }

    @Test
    void the_mornings_inside_a_stop_are_never_made_up_on_the_day_it_lifts() {
        TheStartingPoint standing = anAccountWith()
                .anEverydayAccountHolding("5000.00")
                .aWeeklyRuleEveryMondayOf("50.00")
                .build();

        HowAScenarioTurnsOut carryingOn = theYearThatFollows(standing);
        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                aStopFrom(A_MONDAY.plusWeeks(2), A_MONDAY.plusWeeks(3)));

        assertThat(carryingOn.months().get(1).balance()
                .subtract(branch.months().get(1).balance()))
                .as("the Monday after the stop lifts moves fifty and not a hundred and fifty: a "
                        + "pause is never made up, so the two occurrences inside it are gone rather "
                        + "than waiting behind the cursor")
                .isEqualByComparingTo("100.00");
        assertThat(carryingOn.months().get(11).balance()
                .subtract(branch.months().get(11).balance()))
                .as("and a year later the two columns still differ by exactly those two Mondays, "
                        + "which is what a customer weighing a fortnight off is entitled to be told")
                .isEqualByComparingTo("100.00");
    }

    @Test
    void the_first_sunday_inside_a_stop_ends_the_run_and_it_is_only_ended_once() {
        TheStartingPoint standing = anAccountWith()
                .anEverydayAccountHolding("5000.00")
                .aWeeklyRuleEveryMondayOf("50.00")
                // Two weeks behind them and fifty already in the week under way, so the branch is
                // climbing from the morning the window opens rather than starting from nothing.
                .aRunOf(2, new BigDecimal("50.00"))
                .build();

        // Eight whole weeks off, beginning on a Monday and ending on the Sunday eight weeks later.
        LocalDate stopBegins = LocalDate.of(2026, 11, 2);
        LocalDate stopEnds = LocalDate.of(2026, 12, 27);
        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing, aStopFrom(stopBegins, stopEnds));

        assertThat(branch.thingsThatHappen().stream()
                .filter(thing -> thing.kind() == AKindOfThingThatHappens.A_WEEK_IS_LOST)
                .toList())
                .as("the first Sunday inside the stop takes in nothing, so the run of eight weeks "
                        + "ends there rather than shortening by one — and it ends once, because a "
                        + "customer with no run left does not lose one on each of the seven Sundays "
                        + "after it")
                .containsExactly(AThingThatHappens.worth(stopBegins.plusDays(6),
                        AKindOfThingThatHappens.A_WEEK_IS_LOST, 8));
        assertThat(branch.months().get(2).securedWeeks())
                .as("and the row that closes inside the stop says so in the one figure a balance "
                        + "cannot: the run is back at nothing")
                .isZero();
    }

    @Test
    void money_paid_in_after_a_stop_earns_at_the_ordinary_rate_until_the_ladder_is_climbed_again() {
        TheStartingPoint standing = anAccountWith()
                .anEverydayAccountHolding("5000.00")
                .aWeeklyRuleEveryMondayOf("50.00")
                .aRunOf(2, new BigDecimal("50.00"))
                .build();

        HowAScenarioTurnsOut carryingOn = theYearThatFollows(standing);
        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                aStopFrom(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 12, 27)));

        assertThat(carryingOn.months().get(3).pointsEarned())
                .as("four Mondays in the row that closes in January, every one of them paid at the "
                        + "top of the ladder by a run that has never been broken: fifty euros at "
                        + "one and a half is seventy-five points apiece")
                .isEqualTo(4 * 75);
        assertThat(branch.months().get(3).pointsEarned())
                .as("the branch makes three deposits in that row rather than four, and pays for "
                        + "the stop twice over — the first Monday after it earns fifty at the "
                        + "ordinary rate, the next fifty-five and the next sixty, because the "
                        + "ladder is climbed a step a week and has to be climbed from the bottom")
                .isEqualTo(50 + 55 + 60);
        assertThat(branch.months().get(5).pointsEarned())
                .as("and by March the branch is earning what it was earning before it stopped: six "
                        + "secured weeks is the top of the ladder, and the cost of the two months "
                        + "off is the three months it took to get back there")
                .isEqualTo(carryingOn.months().get(5).pointsEarned());
    }

    @Test
    void the_salary_the_bills_and_the_points_expiry_all_carry_on_inside_a_stop() {
        LocalDate stopBegins = LocalDate.of(2026, 10, 1);
        LocalDate stopEnds = LocalDate.of(2026, 11, 30);
        TheStartingPoint standing = anAccountWith()
                // Nothing of its own: every euro this everyday account ever sees is the salary.
                .anEverydayAccountHolding("0.00")
                .aSalaryOf("2000.00").onDayOfTheMonth(15)
                .aBillOf("1500.00").onDayOfTheMonth(15)
                // A sweep rather than a fixed amount, so that what it moves is a reading of what
                // the everyday account was left holding while the branch was not saving.
                .aSweepOnDayOfTheMonth(20, "0.00")
                .pointsGoingOn(LocalDate.of(2026, 10, 20), 40)
                .build();

        HowAScenarioTurnsOut carryingOn = theYearThatFollows(standing);
        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing, aStopFrom(stopBegins, stopEnds));

        assertThat(branch.months().get(1).pointsThatExpired())
                .as("points whose twelve months are up go on the day they were always going to go: "
                        + "a customer who has stopped saving has not stopped the clock on what they "
                        + "already earned, and a branch that quietly kept them would be a lie in "
                        + "their favour")
                .isEqualTo(40);
        assertThat(branch.thingsThatHappen())
                .as("and it is a dated event inside the stop, where the screen can show it")
                .contains(AThingThatHappens.worth(LocalDate.of(2026, 10, 20),
                        AKindOfThingThatHappens.POINTS_EXPIRE, 40));
        assertThat(branch.months().get(2).balance())
                .as("two swept months missing from the branch: the twentieth of October and the "
                        + "twentieth of November are inside the stop, so only September's surplus "
                        + "has reached savings by the middle of December")
                .isEqualByComparingTo("500.00");
        assertThat(branch.months().get(3).balance())
                .as("and the first sweep after the stop finds three months of surplus waiting in "
                        + "the everyday account — five hundred a month, salary in and rent out, "
                        + "every month the customer was not saving. Had the salary stopped landing "
                        + "there would be nothing there to sweep, and had the bills stopped being "
                        + "taken there would be four and a half thousand")
                .isEqualByComparingTo("2000.00");
        assertThat(carryingOn.months().get(3).balance())
                .as("which is where the year already under way had arrived by the same day, a "
                        + "month at a time instead of in one morning: a stop is a pause on the "
                        + "saving and on nothing else")
                .isEqualByComparingTo("2000.00");
    }

    @Test
    void another_amount_each_week_does_not_land_on_the_mornings_the_same_scenario_stopped_on() {
        TheStartingPoint standing = anAccountWith().build();

        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                anotherEachWeekOf("50.00", A_MONDAY),
                aStopFrom(A_MONDAY.plusDays(14), A_MONDAY.plusDays(27)));

        assertThat(branch.months().get(0).balance())
                .as("five of the customer's weekly fifties fall between the day the window opens "
                        + "and the day the first row closes, and the two inside the stop do not "
                        + "land: they asked about both changes and meant both, and an extra that "
                        + "went in through a pause would show a pause that cost them nothing")
                .isEqualByComparingTo("150.00");
        assertThat(branch.thingsThatHappen().stream()
                .filter(thing -> thing.kind() == AKindOfThingThatHappens.A_WEEK_IS_LOST)
                .toList())
                .as("so the fortnight off breaks the run the extra had built, on the Sunday the "
                        + "first empty week closes and carrying the two weeks it cost")
                .containsExactly(AThingThatHappens.worth(A_MONDAY.plusDays(20),
                        AKindOfThingThatHappens.A_WEEK_IS_LOST, 2));
        assertThat(branch.months().get(0).pointsEarned())
                .as("fifty at the ordinary rate, fifty-five at the run of two — then nothing for a "
                        + "fortnight, and fifty again at the ordinary rate on the first Monday "
                        + "after it, because the ladder does not remember where it was")
                .isEqualTo(50 + 55 + 50);
    }

    // ------------------------------------------------------------------------------------------
    // What taking five hundred out really costs: the five rules that move at once.
    // ------------------------------------------------------------------------------------------

    @Test
    void a_later_anniversary_pays_less_in_a_branch_that_took_money_out_of_the_deposit_it_is_paid_on() {
        TheStartingPoint standing = anAccountWith()
                // Six months ago, so its first anniversary falls squarely inside the window.
                .aDepositOf("300.00", A_MONDAY.minusMonths(6))
                .build();

        HowAScenarioTurnsOut carryingOn = theYearThatFollows(standing);
        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                takingOutOn("200.00", A_MONDAY));

        assertThat(theBonusesPaidIn(carryingOn))
                .as("three hundred euros sitting where they are pay a tenth of their whole euros on "
                        + "the day they turn a year old")
                .containsExactly(new AThingThatHappens(A_MONDAY.plusMonths(6),
                        AKindOfThingThatHappens.A_BONUS_IS_PAID, BigDecimal.valueOf(30)));
        assertThat(theBonusesPaidIn(branch))
                .as("and two hundred of them taken out in September leaves a hundred to be paid on "
                        + "in March: the anniversary is repriced rather than cancelled, which is the "
                        + "half of the cost a customer cannot do in their head and the whole reason "
                        + "the fold carries deposits instead of a balance")
                .containsExactly(new AThingThatHappens(A_MONDAY.plusMonths(6),
                        AKindOfThingThatHappens.A_BONUS_IS_PAID, BigDecimal.valueOf(10)));
    }

    @Test
    void a_withdrawal_draws_the_oldest_deposit_down_first_which_is_what_decides_the_bonuses_ahead() {
        TheStartingPoint standing = anAccountWith()
                .aDepositOf("100.00", A_MONDAY.minusMonths(6))
                .aDepositOf("300.00", A_MONDAY.minusMonths(3))
                .build();

        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                takingOutOn("150.00", A_MONDAY));

        assertThat(theBonusesPaidIn(theYearThatFollows(standing)))
                .as("left alone, both deposits pay a tenth of themselves on their own anniversary")
                .containsExactly(
                        new AThingThatHappens(A_MONDAY.plusMonths(6),
                                AKindOfThingThatHappens.A_BONUS_IS_PAID, BigDecimal.valueOf(10)),
                        new AThingThatHappens(A_MONDAY.plusMonths(9),
                                AKindOfThingThatHappens.A_BONUS_IS_PAID, BigDecimal.valueOf(30)));
        assertThat(theBonusesPaidIn(branch))
                .as("a hundred and fifty out empties the deposit that has been there longest and "
                        + "takes the other fifty off the newer one, exactly as a real withdrawal "
                        + "draws: the older pays nothing at all because a tenth of nothing is not a "
                        + "bonus, and the newer pays on the two hundred and fifty it has left. Drawn "
                        + "the other way round the customer would be shown 10 and 15, which is a "
                        + "year of bonuses this application is not going to pay them")
                .containsExactly(new AThingThatHappens(A_MONDAY.plusMonths(9),
                        AKindOfThingThatHappens.A_BONUS_IS_PAID, BigDecimal.valueOf(25)));
    }

    @Test
    void a_withdrawal_that_takes_the_week_under_the_minimum_loses_that_week_and_ends_the_run() {
        TheStartingPoint standing = anAccountWith()
                .aDepositOf("500.00", A_MONDAY.minusMonths(2))
                // Three weeks behind them and sixty euros already in the week under way, which is
                // over the line: the run stands at three and this Sunday would have made it four.
                .aRunOf(3, new BigDecimal("60.00"))
                .build();

        assertThat(theWeeksLostIn(theYearThatFollows(standing)))
                .as("left alone, this Sunday secures the week and the run ends a week later, when "
                        + "the first week with nothing in it closes — the branch has no rule and no "
                        + "salary, so every week after this one is empty")
                .containsExactly(new AThingThatHappens(A_MONDAY.plusDays(13),
                        AKindOfThingThatHappens.A_WEEK_IS_LOST, BigDecimal.valueOf(3)));
        assertThat(theWeeksLostIn(theYearThatFollowsIf(standing, takingOutOn("50.00", A_MONDAY))))
                .as("and fifty euros taken out on the Monday leaves ten net in the week, which is "
                        + "under the fifty a week asks for: the week is lost on its own Sunday and "
                        + "the two weeks behind it go with it. A run does not shorten by one — it "
                        + "ends, and every euro paid in afterwards is back at the bottom of the "
                        + "ladder. The week counts what came out because it is net, which is the "
                        + "rule NewSavingsThisWeek exists to state once")
                .containsExactly(new AThingThatHappens(A_MONDAY.plusDays(6),
                        AKindOfThingThatHappens.A_WEEK_IS_LOST, BigDecimal.valueOf(2)));
    }

    @Test
    void a_withdrawal_larger_than_what_the_goals_have_left_free_falls_short_at_that_figure() {
        TheStartingPoint standing = whereTheGoalsHaveClaimed("400.00", anAccountWith()
                .aDepositOf("500.00", A_MONDAY.minusMonths(2))
                .build());

        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                takingOutOn("300.00", A_MONDAY));

        assertThat(theWithdrawalsThatFellShortIn(branch))
                .as("five hundred in the account with four hundred of it spoken for leaves one "
                        + "hundred free, so a request for three hundred takes a hundred and says so "
                        + "— what it managed, rather than what it was short by, because that is the "
                        + "figure the balance actually moved by. A goal's claim stops a withdrawal "
                        + "in a branch exactly as it stops one in the application")
                .containsExactly(new AThingThatHappens(A_MONDAY,
                        AKindOfThingThatHappens.A_WITHDRAWAL_FALLS_SHORT, new BigDecimal("100.00")));
        assertThat(branch.months().get(0).balance())
                .as("and the branch carries on with what is left rather than being refused: asking "
                        + "for more than there is is the most interesting question in the set, and "
                        + "refusing it would make it unaskable")
                .isEqualByComparingTo("400.00");
    }

    @Test
    void money_paid_back_in_after_a_withdrawal_earns_nothing_until_it_has_climbed_back_to_the_mark() {
        TheStartingPoint standing = anAccountWith()
                .aDepositOf("200.00", A_MONDAY.minusMonths(2))
                .build();

        HowAScenarioTurnsOut payingItBack = theYearThatFollowsIf(standing,
                takingOutOn("100.00", A_MONDAY),
                anotherEachWeekOf("50.00", A_MONDAY.plusDays(1)));

        assertThat(theMoneyThatEarnedNothingIn(
                theYearThatFollowsIf(standing, anotherEachWeekOf("50.00", A_MONDAY.plusDays(1)))))
                .as("a customer at their own high-water mark earns on every euro they put away, so "
                        + "nothing about the extra on its own is worth a word")
                .isEmpty();
        assertThat(theMoneyThatEarnedNothingIn(payingItBack))
                .as("but a hundred taken out leaves them a hundred below a mark that does not come "
                        + "down with it, so the first two fifties merely fill the gap back up and "
                        + "earn not one point between them — not a reduced rate, nothing. The third "
                        + "is above the mark again and earns in full, which is why there are two of "
                        + "these and not fifty-two. This is the rule the customer would otherwise "
                        + "discover a month after it cost them")
                .containsExactly(
                        new AThingThatHappens(A_MONDAY.plusDays(1),
                                AKindOfThingThatHappens.MONEY_ARRIVES_AND_EARNS_NOTHING,
                                new BigDecimal("50.00")),
                        new AThingThatHappens(A_MONDAY.plusDays(8),
                                AKindOfThingThatHappens.MONEY_ARRIVES_AND_EARNS_NOTHING,
                                new BigDecimal("50.00")));
        assertThat(payingItBack.months().get(11).pointsStanding())
                .as("and a year later the branch that took the money out and put it back is a "
                        + "hundred euros of points behind the one that never touched it, although "
                        + "both accounts hold the same money — which is the sentence a balance on "
                        + "its own cannot say")
                .isLessThan(theYearThatFollowsIf(standing,
                        anotherEachWeekOf("50.00", A_MONDAY.plusDays(1)))
                        .months().get(11).pointsStanding());
    }

    @Test
    void a_withdrawal_takes_nothing_at_all_when_the_goals_have_spoken_for_the_whole_balance() {
        TheStartingPoint standing = whereTheGoalsHaveClaimed("500.00", anAccountWith()
                .aDepositOf("500.00", A_MONDAY.minusMonths(2))
                .build());

        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                takingOutOn("500.00", A_MONDAY));

        assertThat(theWithdrawalsThatFellShortIn(branch))
                .as("every euro is claimed, so the withdrawal takes nothing and says it took "
                        + "nothing — which is a real answer to the question, and a better one than "
                        + "a branch in which the money quietly left")
                .containsExactly(new AThingThatHappens(A_MONDAY,
                        AKindOfThingThatHappens.A_WITHDRAWAL_FALLS_SHORT, new BigDecimal("0.00")));
        assertThat(branch.months().get(11).balance())
                .as("and the balance is exactly where it was a year later")
                .isEqualByComparingTo("500.00");
    }

    /** Only the bonuses a branch's anniversaries paid, which is what a withdrawal reprices. */
    private static List<AThingThatHappens> theBonusesPaidIn(HowAScenarioTurnsOut branch) {
        return onlyThingsOfKind(branch, AKindOfThingThatHappens.A_BONUS_IS_PAID);
    }

    /** Only the Sundays a branch lost a run on. */
    private static List<AThingThatHappens> theWeeksLostIn(HowAScenarioTurnsOut branch) {
        return onlyThingsOfKind(branch, AKindOfThingThatHappens.A_WEEK_IS_LOST);
    }

    /** Only the withdrawals a branch could not take all of. */
    private static List<AThingThatHappens> theWithdrawalsThatFellShortIn(HowAScenarioTurnsOut branch) {
        return onlyThingsOfKind(branch, AKindOfThingThatHappens.A_WITHDRAWAL_FALLS_SHORT);
    }

    /** Only the days money arrived in a branch and earned nothing at all. */
    private static List<AThingThatHappens> theMoneyThatEarnedNothingIn(HowAScenarioTurnsOut branch) {
        return onlyThingsOfKind(branch, AKindOfThingThatHappens.MONEY_ARRIVES_AND_EARNS_NOTHING);
    }

    private static List<AThingThatHappens> onlyThingsOfKind(HowAScenarioTurnsOut branch,
                                                            AKindOfThingThatHappens kind) {
        return branch.thingsThatHappen().stream()
                .filter(thing -> thing.kind() == kind)
                .toList();
    }

    /** An amount taken back out of savings on a day, which is the change this slice added. */
    private static AnAdjustment takingOutOn(String amount, LocalDate day) {
        return new TakingMoneyOut(new BigDecimal(amount), day);
    }

    /**
     * The same present with some of the balance spoken for by the goals.
     *
     * <p>Copied rather than built with a figure of its own, because what the goals have claimed is
     * the one fact on the snapshot that only a withdrawal reads and the builder above is shared with
     * every other test in this class. {@code unallocated} is written as the record's own
     * documentation says it is derived — the balance less what is claimed, negative if it falls that
     * way — so that a fixture cannot promise an account whose figures do not add up.
     */
    private static TheStartingPoint whereTheGoalsHaveClaimed(String claimed,
                                                             TheStartingPoint standing) {
        BigDecimal allocated = new BigDecimal(claimed);
        return new TheStartingPoint(standing.asAt(), standing.until(), standing.savingsAccountId(),
                standing.customerId(), standing.balance(), standing.deposits(),
                standing.pointsGoing(), standing.howLongABatchOfPointsLasts(),
                new AllocationsOnAnAccount(standing.savingsAccountId(), standing.balance(),
                        allocated, standing.balance().subtract(allocated),
                        standing.goals().goals()),
                standing.weeklyCapacity(), standing.rules(), standing.rulesSettledThrough(),
                standing.whenEachDepositNextPays(), standing.currentAccounts(), standing.streak(),
                standing.theSchemeEachWeekIsJudgedUnder(),
                standing.theyStillHoldAltogether(), standing.theMostEverSaved(),
                standing.theProductItIsOn(), standing.theNoticeStanding());
    }

    // --- Four futures at once. ------------------------------------------------------------------
    // Two questions about several branches folded from one present, asserted here rather than over
    // HTTP for the reason the rest of this class gives: a withdrawal has to land on a day with a
    // name, and over HTTP the only named day is whatever the clock the whole run shares has been
    // wound to. What the seam owes a client is in FourFuturesAtOnceApiTest.

    @Test
    void a_branch_that_takes_money_out_leaves_the_present_it_was_folded_from_untouched() {
        TheStartingPoint standing = anAccountWith()
                // Eight hundred, all of it in one deposit, because what a branch takes out it takes
                // out of deposits: aDepositOf puts the euros in the balance as it puts them in the
                // lot they sit in, the way a real deposit does both at once.
                .aDepositOf("800.00", A_MONDAY.minusMonths(6))
                .build();

        HowAScenarioTurnsOut before = theYearThatFollows(standing);
        HowAScenarioTurnsOut takingItOut =
                theYearThatFollowsIf(standing, takingOutOn("500.00", A_MONDAY));
        HowAScenarioTurnsOut after = theYearThatFollows(standing);

        assertThat(takingItOut.months().get(11).balance())
                .as("the branch in the middle really did take five hundred out, so this is not two "
                        + "identical folds agreeing about nothing")
                .isEqualByComparingTo("300.00");
        assertThat(after.months())
                .as("and the same present folded again afterwards answers exactly what it answered "
                        + "before: every figure of all eighty-four. This is the claim four columns "
                        + "on one screen rest on — TheStartingPoint copies every list it is handed, "
                        + "so the deposits a withdrawal draws down are that branch's own running "
                        + "copy and the column beside it never sees them. A fold that mutated what "
                        + "it was folded from would make the third column an answer about the first "
                        + "two, and nothing on the screen would say so")
                .isEqualTo(before.months());
        assertThat(after.thingsThatHappen())
                .as("and the dated events with them, which is where a bonus repriced by somebody "
                        + "else's withdrawal would show first")
                .isEqualTo(before.thingsThatHappen());
    }

    @Test
    void two_withdrawals_on_two_days_are_two_withdrawals_rather_than_the_later_replacing_the_earlier() {
        TheStartingPoint standing = anAccountWith()
                // Eight hundred, all of it in one deposit, because what a branch takes out it takes
                // out of deposits: aDepositOf puts the euros in the balance as it puts them in the
                // lot they sit in, the way a real deposit does both at once.
                .aDepositOf("800.00", A_MONDAY.minusMonths(6))
                .build();

        HowAScenarioTurnsOut branch = theYearThatFollowsIf(standing,
                takingOutOn("300.00", A_MONDAY),
                takingOutOn("200.00", A_MONDAY.plusMonths(3)));

        assertThat(branch.months().get(0).balance())
                .as("the first withdrawal lands on the morning the window opens, so the first month "
                        + "closes three hundred lower")
                .isEqualByComparingTo("500.00");
        assertThat(branch.months().get(11).balance())
                .as("and the second takes another two hundred three months later rather than "
                        + "standing in for the first: five hundred out altogether. Every change on a "
                        + "branch is asked of every morning, which is what makes two changes of one "
                        + "kind compose — a branch where the later replaced the earlier would close "
                        + "on six hundred and quietly hand the customer back two hundred euros they "
                        + "had said they were taking out")
                .isEqualByComparingTo("300.00");
    }
}
