package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.NotificationView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The louder warning fires on the third outstanding arrear, says nothing when a fourth joins them,
 * and fires a second time only once the customer has climbed out and fallen back in.
 *
 * <p>User stories 24 and 25. A customer needs to be able to tell a bad month from a spiral, and a
 * warning that repeated every time the hole got slightly deeper would be a warning that stopped
 * meaning anything. It is raised on the <em>crossing</em> of the threshold and not on standing over
 * it, which is why this test walks the pile up one arrear at a time, over it, past it, back down
 * through it and over it again, and asserts what was said at every step.
 *
 * <p><strong>Four bills on four days of one month</strong>, so that the count can be walked one at
 * a time by winding the clock and running the billing job — rather than three months of one rent,
 * which would take three times as long to say the same thing. The sweep left standing on the savings
 * account with a floor of nothing is the lab's own way of reaching an unpaid bill: the rules empty
 * the current account at two and the bills find nothing there at half past.
 *
 * <p><strong>Climbing out is the whole of the second half.</strong> The money comes back from
 * savings, the next billing run settles every arrear oldest first, and the account is under the
 * threshold again — at which point the crossing that was announced is spent, and the next month's
 * three missed bills are a new crossing and worth saying again.
 *
 * <p>Both of this feature's reasons stand in the customer's list by the end, which is what the last
 * assertion is for: they are read back over HTTP and marked read in one round trip, exactly as the
 * panel behind the bell does it.
 */
class ArrearsPilingUpAreAnnouncedOnTheCrossingAndAgainOnlyAfterClimbingOutApiTest
        extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";
    private static final String THE_SAVING_RULES_JOB = "fireSavingRulesDue";

    private static final String PILING_UP = "BILLS_ARE_PILING_UP";
    private static final String COULD_NOT_BE_PAID = "A_BILL_COULD_NOT_BE_PAID";

    private static final int THE_RENTS_DAY = 5;
    private static final int THE_ENERGYS_DAY = 12;
    private static final int THE_PHONES_DAY = 20;
    private static final int THE_INTERNETS_DAY = 25;

    private static final String THE_RENT = "900.00";
    private static final String THE_ENERGY = "300.00";
    private static final String THE_PHONE = "100.00";
    private static final String THE_INTERNET = "50.00";

    /** What the first three come to, which is what the warning leads with. */
    private static final String WHAT_THREE_ARREARS_COME_TO = "1300.00";

    /** Every arrear at once, which is what it takes to climb out. */
    private static final String WHAT_ALL_FOUR_COME_TO = "1350.00";

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-arrears-piling-up"));
        theCustomer = app.aCustomerOfItsOwn("told when the arrears pile up");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_third_arrear_is_a_spiral_the_fourth_is_not_news_and_falling_back_in_is() {
        long savingsAccount = app.savingsAccountOf(theCustomer);
        long currentAccount = app.currentAccountOf(theCustomer);

        // Onto the first of a month before anything is declared, so that the four days below fall in
        // one month in the order this narrative walks them. Declared on a clock reading the
        // twenty-first, the first billing run would be catching up on an energy bill and a phone
        // bill the test had not got to yet, and the count would arrive at three a step early.
        windToTheFirstOfTheNextMonth();

        app.declareABillFor(theCustomer, "Rent", THE_RENTS_DAY, THE_RENT);
        app.declareABillFor(theCustomer, "Energy", THE_ENERGYS_DAY, THE_ENERGY);
        app.declareABillFor(theCustomer, "Phone", THE_PHONES_DAY, THE_PHONE);
        app.declareABillFor(theCustomer, "Internet", THE_INTERNETS_DAY, THE_INTERNET);
        // The sweep fires on the day before the rent falls due, which is the one night between the
        // declaration and the first billing run. A weekly rule left on today's weekday would not
        // come round until the eighth, and the rent would be paid out of a full account for a reason
        // that has nothing to do with this test.
        app.leaveARuleStanding(savingsAccount, aWeeklySweepDownToNothingFrom(currentAccount,
                theNext(THE_RENTS_DAY).minusDays(1)));

        // The rent, first: the rules take everything at two and the bills meet an empty account at
        // half past.
        windTo(theNext(THE_RENTS_DAY));
        app.runJob(THE_SAVING_RULES_JOB);
        theNightRunsItsBillsAndItsRaiser();
        assertThat(app.arrearsOf(theCustomer)).hasSize(1);
        assertThat(whatWasSaidAbout(PILING_UP))
                .as("one missed bill is a bad month, not a spiral")
                .isEmpty();

        // The energy bill, a week later. Two is still a bad month.
        windTo(theNext(THE_ENERGYS_DAY));
        theNightRunsItsBillsAndItsRaiser();
        assertThat(app.arrearsOf(theCustomer)).hasSize(2);
        assertThat(whatWasSaidAbout(PILING_UP))
                .as("and so is two — the threshold is three, and a warning that fired earlier would "
                        + "be a warning about something the customer can still see their way out of")
                .isEmpty();

        // The phone bill. Three outstanding at once is the crossing.
        windTo(theNext(THE_PHONES_DAY));
        theNightRunsItsBillsAndItsRaiser();
        assertThat(app.arrearsOf(theCustomer)).hasSize(3);
        List<NotificationView> afterTheCrossing = whatWasSaidAbout(PILING_UP);
        assertThat(afterTheCrossing)
                .as("the third outstanding arrear is the crossing, and it is said once")
                .singleElement()
                .satisfies(said -> {
                    assertThat(said.arrears()).isEqualTo(3L);
                    assertThat(said.amount())
                            .isEqualByComparingTo(new BigDecimal(WHAT_THREE_ARREARS_COME_TO));
                    assertThat(said.currentAccountId()).isEqualTo(currentAccount);
                    assertThat(said.savingsAccountId())
                            .as("a hole in a current account is not a fact about a savings account")
                            .isNull();
                    assertThat(said.billId())
                            .as("and no single date is to blame for the account having three of them")
                            .isNull();
                    assertThat(said.occursOn()).isNull();
                });

        // The same night's raiser run a second time, which is the idempotence check and is cheap.
        app.runJob(TheNotificationSweep.THE_JOB);
        assertThat(whatWasSaidAbout(PILING_UP))
                .as("a retry of the nightly raiser says nothing the second time")
                .isEqualTo(afterTheCrossing);

        // The internet bill. A fourth arrear is the hole getting deeper, not a new crossing.
        windTo(theNext(THE_INTERNETS_DAY));
        theNightRunsItsBillsAndItsRaiser();
        assertThat(app.arrearsOf(theCustomer)).hasSize(4);
        assertThat(whatWasSaidAbout(PILING_UP))
                .as("a fourth arrear does not raise a second copy: the account crossed the "
                        + "threshold once and has not been back under it since")
                .isEqualTo(afterTheCrossing);

        // Climbing out, the way the application already allows: money back from savings, and the
        // next billing run settles every arrear oldest first.
        app.withdraw(savingsAccount, theCustomer, WHAT_ALL_FOUR_COME_TO);
        theNightRunsItsBillsAndItsRaiser();
        assertThat(app.arrearsOf(theCustomer))
                .as("every hole is closed")
                .isEmpty();
        assertThat(whatWasSaidAbout(PILING_UP))
                .as("and climbing out says nothing new — nothing is ever deleted either")
                .isEqualTo(afterTheCrossing);

        // And falling back in. A new month, the same three bills, the same empty account.
        windTo(theNext(THE_PHONES_DAY));
        theNightRunsItsBillsAndItsRaiser();
        assertThat(app.arrearsOf(theCustomer)).hasSize(3);
        assertThat(whatWasSaidAbout(PILING_UP))
                .as("having climbed out and crossed the threshold a second time, the warning means "
                        + "something again and is said again")
                .hasSize(2)
                .satisfies(said -> {
                    assertThat(said.get(0).arrears()).isEqualTo(3L);
                    assertThat(said.get(0).amount())
                            .isEqualByComparingTo(new BigDecimal(WHAT_THREE_ARREARS_COME_TO));
                    assertThat(said.get(0).raisedAt())
                            .as("newest first, as the panel reads them")
                            .isAfter(said.get(1).raisedAt());
                });

        assertThat(whatWasSaidAbout(COULD_NOT_BE_PAID))
                .as("both of this feature's reasons stand in the customer's own list: seven due "
                        + "dates went unpaid across the two months and each of them was said once")
                .hasSize(7);

        assertThat(app.marksTheirNotificationsRead(theCustomer))
                .as("and every one of them can be marked read in the one round trip the panel makes")
                .isNotEmpty()
                .allSatisfy(said -> assertThat(said.readAt()).isNotNull());
        assertThat(whatWasSaidAbout(PILING_UP))
                .as("which leaves the warnings where they were, read")
                .hasSize(2)
                .allSatisfy(said -> assertThat(said.readAt()).isNotNull());
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
