package io.dataroots.savingstreak.whatifididthisinstead;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.SimulationView;
import io.dataroots.savingstreak.support.SimulationView.AMonthOfTheFutureView;
import io.dataroots.savingstreak.support.SimulationView.HowAScenarioTurnsOutView;
import io.dataroots.savingstreak.support.TheCurrentAccountView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The year a savings account is already heading for, asked for over HTTP and read back the way the
 * frontend reads it.
 *
 * <p><strong>The branch nobody asked for is the one that always comes back.</strong> A projection
 * with nothing to compare against answers no question, so the future the customer is already in is
 * folded whether or not any scenario was named, and it comes first. That is what these tests insist
 * on at the seam: twelve rows, named, dated, marked as an illustration — and an account exactly
 * where it was afterwards.
 *
 * <p>Everything about <em>what</em> the fold works out — a deposit paid at the rate of the week it
 * secured, a batch going on its own anniversary, the opening day's points landing on the closing
 * day — is asserted as plain arithmetic in {@code TheNightIsReplayedOneDayAtATimeForTwelveMonthsTest},
 * because over HTTP the only way to reach those days is to wind a clock the whole run shares. What
 * is asserted here is what the seam owes a client: the shape, the days, the words, and the promise
 * that asking changed nothing.
 *
 * <p>Its own customer every time and every date counted off the application's clock rather than the
 * machine's, for the reasons {@link AnAccountWithAFutureToAskAbout} gives.
 */
class TheYearThisAccountIsHeadingForApiTest extends ApiIntegrationTest {

    @Test
    void twelve_months_come_back_for_an_account_nobody_asked_a_question_about() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "the year ahead");
        LocalDate today = account.theDateTheClockReads();

        SimulationView answered = account.simulation();

        assertThat(answered.anIllustrationRatherThanAPromise())
                .as("every figure under this answer depends on a balance nobody has yet, and it is "
                        + "said once here rather than on each of ninety fields — the word this "
                        + "codebase already uses on a rule's preview, for exactly the same reason")
                .isTrue();
        assertThat(answered.scenarios())
                .as("nobody asked about anything and one branch still came back: the one they are "
                        + "already living in, without which no other figure would have anything to "
                        + "be better or worse than")
                .hasSize(1);

        HowAScenarioTurnsOutView carryingOn = answered.theYearAlreadyUnderWay();
        assertThat(carryingOn.called())
                .as("named in the customer's own words for it, because carrying on is a decision "
                        + "as much as any of the others rather than the absence of one")
                .isEqualTo("If I carry on as I am");
        assertThat(carryingOn.from()).isEqualTo(answered.from());
        assertThat(carryingOn.until())
                .as("drawn over the window the answer names, which is the horizon the account's own "
                        + "bar and the rules' preview already quote")
                .isEqualTo(answered.until());
        assertThat(carryingOn.months())
                .as("twelve rows, because the window is twelve months")
                .hasSize(12);
        assertThat(carryingOn.months().stream().map(AMonthOfTheFutureView::closesOn))
                .as("each closing on a monthly anniversary of the day the window opened, the last "
                        + "of them on the day it closes, so that the twelve cover the year exactly "
                        + "once with nothing over — and so that a trainer can wind the clock to one "
                        + "of those days and put the application's figures beside that row's")
                .containsExactlyElementsOf(
                        List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12).stream()
                                .map(month -> today.plusMonths(month))
                                .toList());
        assertThat(carryingOn.months().get(11).month())
                .as("and labelled with the calendar month it closes in, which is what a page writes "
                        + "under a bar")
                .isEqualTo(YearMonth.from(today.plusMonths(12)));
        assertThat(carryingOn.months().get(11).closesOn())
                .isEqualTo(answered.until());
    }

    @Test
    void the_rows_start_from_where_the_account_actually_is_and_the_points_add_up_down_the_column() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "rows that add up");
        LocalDate today = account.theDateTheClockReads();
        account.declaresAnIncome("25", "2400.00");
        account.declaresABill("Rent", "1", "900.00");
        account.leavesARuleStanding(Map.of(
                "name", "Every Friday", "fromCurrentAccountId", account.currentAccountId(),
                "trigger", "WEEKLY", "dayOfWeek", DayOfWeek.FRIDAY.name(),
                "howMuchMoves", "A_FIXED_AMOUNT", "amount", "60.00"));
        account.depositsByHand("200.00");

        SimulationView answered = account.simulation();
        HowAScenarioTurnsOutView carryingOn = answered.theYearAlreadyUnderWay();
        BalancesView now = account.balances();

        assertThat(carryingOn.months().get(0).balance())
                .as("the first month starts from the euros the account's own screen shows and adds "
                        + "the Fridays to them, so it is never below where the account is today")
                .isGreaterThanOrEqualTo(now.moneyBalance());
        assertThat(carryingOn.months().get(11).balance())
                .as("and sixty euros every Friday for a year is visibly more money than the two "
                        + "hundred that is there now")
                .isGreaterThan(carryingOn.months().get(0).balance());

        long standing = now.pointsBalance();
        for (AMonthOfTheFutureView month : carryingOn.months()) {
            standing += month.pointsEarned() + month.pointsABonusPaid() - month.pointsThatExpired();
            assertThat(month.pointsStanding())
                    .as("the points at the end of " + month.month() + " are the ones at the end of "
                            + "the month before, plus what was earned, plus what a bonus paid, less "
                            + "what expired — seven figures a customer can check against each other "
                            + "with a pencil, and a row that does not add up is a fold gone wrong")
                    .isEqualTo(standing);
        }
        assertThat(carryingOn.months().get(11).securedWeeks())
                .as("sixty euros a Friday is above what a week asks for, so the run of secured "
                        + "weeks is well past the six the ladder stops climbing at by the end")
                .isGreaterThanOrEqualTo(6);
        assertThat(carryingOn.until()).isEqualTo(today.plusMonths(12));
    }

    @Test
    void folding_a_year_writes_nothing_at_all() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "folding is free");
        LocalDate today = account.theDateTheClockReads();
        account.declaresAnIncome("25", "2400.00");
        account.declaresABill("Rent", "1", "900.00");
        account.opensAGoal("A boat", "3000.00", today.plusMonths(8).toString());
        account.leavesARuleStanding(Map.of(
                "name", "Every Monday", "fromCurrentAccountId", account.currentAccountId(),
                "trigger", "WEEKLY", "dayOfWeek", DayOfWeek.MONDAY.name(),
                "howMuchMoves", "A_FIXED_AMOUNT", "amount", "75.00"));
        account.depositsByHand("150.00");

        BalancesView before = account.balances();
        List<GoalView> goalsBefore = account.goals();
        TheCurrentAccountView everydayBefore = account.currentAccount();

        SimulationView firstAsking = account.simulation();
        SimulationView secondAsking = account.simulation();

        assertThat(account.balances())
                .as("a fold makes fifty-odd deposits, credits a year of points and expires whatever "
                        + "is old enough — inside itself. Not one euro and not one point of it "
                        + "reached the account, or exploring would cost a customer money")
                .isEqualTo(before);
        assertThat(account.goals())
                .as("and no goal was funded by a year that was only ever imagined")
                .isEqualTo(goalsBefore);
        assertThat(account.currentAccount())
                .as("and the everyday account still holds what a year of Mondays would have taken "
                        + "out of it")
                .isEqualTo(everydayBefore);
        assertThat(secondAsking.theYearAlreadyUnderWay())
                .as("the second asking folded exactly the same year, which is the same statement "
                        + "from the other side: a fold that wrote would have moved its own answer")
                .isEqualTo(firstAsking.theYearAlreadyUnderWay());
    }

    @Test
    void nothing_is_added_up_across_the_window() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "no totals");

        JsonNode answered = http.postForObject("/api/savings-accounts/{id}/simulations",
                Map.of("scenarios", List.of()), JsonNode.class, account.id());
        JsonNode branch = answered.get("scenarios").get(0);

        assertThat(fieldsOf(branch))
                .as("a branch is what it is called, the window, the months and the dated things, "
                        + "and nothing else. "
                        + "No 'pointsGained', no 'netPoints', no total of any kind — the same point "
                        + "can be paid on an anniversary and expire inside the same twelve months, "
                        + "so a net would count it twice in opposite directions. The shape is the "
                        + "promise, which is why it is asserted rather than described")
                .containsExactlyInAnyOrder("called", "from", "until", "months",
                        "thingsThatHappen");
        assertThat(fieldsOf(branch.get("months").get(0)))
                .as("and a month is the month, the day it closes on and the seven figures that "
                        + "belong to that month alone")
                .containsExactlyInAnyOrder("month", "closesOn", "balance", "pointsStanding",
                        "securedWeeks", "pointsEarned", "pointsABonusPaid", "pointsThatExpired");
    }

    private static List<String> fieldsOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
