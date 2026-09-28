package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.savingspolicy.ASchemeSomebodyAdministers;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.NotificationView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How many unpaid bills count as arrears piling up comes from the scheme in force on the night the
 * sweep runs.
 *
 * <p>Two due dates go unpaid in one month on an account whose holder has declared no income, so the
 * count is the only thing that can make a spiral of them. Under the figure this application is
 * seeded at, two is a bad month and says nothing at all — the test that walks the pile from one to
 * four asserts exactly that. Here the bank has published two as the line before either bill falls
 * due, and the second arrear is a crossing.
 *
 * <p><strong>The Monday comes before the bills, deliberately.</strong> The replay judges an account's
 * whole history at tonight's count, which {@link WhenArrearsArePilingUp} argues out as the same
 * concession the declared income already makes; a test that let the figure move halfway through the
 * pile would be asserting about that concession rather than about where the figure comes from, and
 * those are two different claims. This one is only the second.
 *
 * <p>The sweep is run again on the crossing night, because a published count is not licence to say
 * the same thing twice: one crossing, one warning.
 *
 * <p>An application of its own, because both the clock and a published version of the scheme move
 * one way only — and a customer of its own, because the seeded households are already having their
 * own bills taken by the very jobs this test drives.
 */
class AChangeToHowManyOutstandingIsASpiralIsUsedByTheNextSweepApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";
    private static final String THE_SAVING_RULES_JOB = "fireSavingRulesDue";

    private static final String PILING_UP = "BILLS_ARE_PILING_UP";

    private static final int THE_RENTS_DAY = 12;
    private static final int THE_ENERGYS_DAY = 20;

    private static final String THE_RENT = "900.00";
    private static final String THE_ENERGY = "300.00";

    /** What the two come to, which is what the warning leads with. */
    private static final String WHAT_TWO_ARREARS_COME_TO = "1200.00";

    /** What the bank publishes: two outstanding at once is a spiral, where three used to be. */
    private static final String TWO_OUTSTANDING_IS_A_SPIRAL = "2";

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseSchemeThisTestMayPublishVersionsOf() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-published-count-of-arrears"));
        theCustomer = app.aCustomerOfItsOwn("warned at a published count of arrears");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_second_arrear_is_a_spiral_because_the_bank_published_two_as_the_line() {
        long savingsAccount = app.savingsAccountOf(theCustomer);
        long currentAccount = app.currentAccountOf(theCustomer);

        // Onto the first of a month before anything else, so that both due dates below fall in one
        // month in the order this narrative walks them — and so that the Monday the count moves on,
        // never more than seven days off, comes before either of them.
        windToTheFirstOfTheNextMonth();

        LocalDate theMondayTheCountMoves = app.theNextMondayStillToCome();
        Map<String, Object> twoIsASpiral = ASchemeSomebodyAdministers.theSameSchemeAgain(
                app.theSchemeInForce(), theMondayTheCountMoves,
                "Two bills outstanding at once is arrears piling up rather than three, so that a "
                        + "household is offered help a month earlier.");
        twoIsASpiral.put("howManyOutstandingIsASpiral", TWO_OUTSTANDING_IS_A_SPIRAL);
        app.publishAVersionOfTheScheme(twoIsASpiral);
        app.theClockReaches(theMondayTheCountMoves);
        assertThat(app.theSchemeInForce().howManyOutstandingIsASpiral())
                .as("the Monday has come, so this is the count tonight's sweep runs under")
                .isEqualTo(Integer.parseInt(TWO_OUTSTANDING_IS_A_SPIRAL));

        app.declareABillFor(theCustomer, "Rent", THE_RENTS_DAY, THE_RENT);
        app.declareABillFor(theCustomer, "Energy", THE_ENERGYS_DAY, THE_ENERGY);
        // The saving rule empties the current account the night before the rent falls due, which is
        // this lab's way of reaching an unpaid bill: the rules take everything at two and the bills
        // meet nothing at all at half past.
        app.leaveARuleStanding(savingsAccount, aWeeklySweepDownToNothingFrom(currentAccount,
                theNext(THE_RENTS_DAY).minusDays(1)));

        windTo(theNext(THE_RENTS_DAY));
        app.runJob(THE_SAVING_RULES_JOB);
        theNightRunsItsBillsAndItsRaiser();
        assertThat(app.arrearsOf(theCustomer)).hasSize(1);
        assertThat(whatWasSaidAbout(PILING_UP))
                .as("one missed bill is a bad month under any count this bank has published")
                .isEmpty();

        windTo(theNext(THE_ENERGYS_DAY));
        theNightRunsItsBillsAndItsRaiser();

        assertThat(app.arrearsOf(theCustomer)).hasSize(2);
        List<NotificationView> afterTheCrossing = whatWasSaidAbout(PILING_UP);
        assertThat(afterTheCrossing)
                .as("two outstanding at once is the crossing tonight, and would have been a quiet "
                        + "night under the three the scheme is seeded at")
                .singleElement()
                .satisfies(said -> {
                    assertThat(said.arrears()).isEqualTo(2L);
                    assertThat(said.amount())
                            .isEqualByComparingTo(new BigDecimal(WHAT_TWO_ARREARS_COME_TO));
                    assertThat(said.currentAccountId()).isEqualTo(currentAccount);
                });

        app.runJob(TheNotificationSweep.THE_JOB);

        assertThat(whatWasSaidAbout(PILING_UP))
                .as("one crossing, one warning — a published count does not loosen that")
                .isEqualTo(afterTheCrossing);
    }

    /** The two jobs this test is about, in the order the night runs them. */
    private static void theNightRunsItsBillsAndItsRaiser() {
        app.runJob(THE_BILLS_JOB);
        app.runJob(TheNotificationSweep.THE_JOB);
    }

    /** Only what has been said to this customer under one reason, newest first. */
    private static List<NotificationView> whatWasSaidAbout(String reason) {
        return Arrays.stream(app.notificationsOf(theCustomer))
                .filter(said -> reason.equals(said.reason()))
                .toList();
    }

    /**
     * A weekly sweep taking everything above nothing, which is the sharpest version of "I saved too
     * hard": the current account is emptied at two and every bill afterwards meets nothing at all.
     */
    private static Map<String, Object> aWeeklySweepDownToNothingFrom(long fromCurrentAccountId,
                                                                    LocalDate firing) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("name", "Everything, every week");
        rule.put("fromCurrentAccountId", fromCurrentAccountId);
        rule.put("trigger", "WEEKLY");
        rule.put("dayOfWeek", firing.getDayOfWeek().name());
        rule.put("howMuchMoves", "EVERYTHING_ABOVE");
        rule.put("floor", "0.00");
        return rule;
    }

    /** Winds the clock to that day, insisting on a move forwards: the clock only goes one way. */
    private static void windTo(LocalDate day) {
        long days = ChronoUnit.DAYS.between(app.theDateTheClockReads(), day);
        assertThat(days)
                .as("this test has to reach " + day + " and the clock cannot be wound backwards")
                .isPositive();
        app.daysPass(days);
    }

    /** Onto the first of the next month, which is where this test's narrative starts from. */
    private static void windToTheFirstOfTheNextMonth() {
        windTo(YearMonth.from(app.theDateTheClockReads()).plusMonths(1).atDay(1));
    }

    /** The first of those days of the month that begins after today. */
    private static LocalDate theNext(int dayOfMonth) {
        LocalDate today = app.theDateTheClockReads();
        YearMonth month = YearMonth.from(today);
        LocalDate thisMonths = month.atDay(dayOfMonth);
        return thisMonths.isAfter(today) ? thisMonths : month.plusMonths(1).atDay(dayOfMonth);
    }
}
