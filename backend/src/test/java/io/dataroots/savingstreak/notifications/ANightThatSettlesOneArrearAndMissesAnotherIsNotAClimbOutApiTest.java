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
import io.dataroots.savingstreak.support.ArrearView;
import io.dataroots.savingstreak.support.NotificationView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A night that clears the oldest arrear and misses a new date is not a climb-out, and does not earn
 * a second piling-up warning.
 *
 * <p><strong>The ordinary shape of a household digging itself out</strong>, and the one the
 * once-only rule is easiest to get wrong on. Money comes back from savings, the oldest arrear
 * clears, a bill falls due the same night and fails: three dates owed at dusk, three owed at dawn,
 * and an account that was never for one moment under the threshold. The customer has already been
 * told their bills are piling up and does not need telling again — and a warning that arrived every
 * month a household paid off one bill and missed another would be a warning nobody read.
 *
 * <p>The trap is that the 02:30 billing run settles what is owed and then presents what is newly
 * due, and stamps <em>both</em> with the same moment. Replaying those two movements one at a time
 * reads the pile as dipping to two in between, which spends the crossing being carried and makes
 * the failure that follows look like a fresh one. {@link WhenArrearsArePilingUp} therefore nets
 * everything sharing an instant before it tests the threshold at all: a crossing is a statement
 * about a night, not about a moment inside one.
 *
 * <p>The sibling test beside this one,
 * {@link ArrearsPilingUpAreAnnouncedOnTheCrossingAndAgainOnlyAfterClimbingOutApiTest}, walks the
 * pile up to the threshold, past it, all the way out and in again. It never settles anything on a
 * night it also misses something, which is exactly the gap this one fills.
 */
class ANightThatSettlesOneArrearAndMissesAnotherIsNotAClimbOutApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";
    private static final String THE_SAVING_RULES_JOB = "fireSavingRulesDue";

    private static final String PILING_UP = "BILLS_ARE_PILING_UP";

    private static final int THE_RENTS_DAY = 5;
    private static final int THE_ENERGYS_DAY = 12;
    private static final int THE_PHONES_DAY = 20;
    private static final int THE_INTERNETS_DAY = 25;

    /** Exactly what comes back from savings on the last night, so that exactly one arrear clears. */
    private static final String THE_RENT = "900.00";

    private static final String THE_ENERGY = "300.00";
    private static final String THE_PHONE = "100.00";
    private static final String THE_INTERNET = "50.00";

    /** Rent, energy and phone: what the account owes when it crosses the threshold. */
    private static final String WHAT_THE_FIRST_THREE_COME_TO = "1300.00";

    /** Energy, phone and internet: what it owes the morning after, still three dates. */
    private static final String WHAT_THE_NEXT_THREE_COME_TO = "450.00";

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-settling-one-missing-another"));
        theCustomer = app.aCustomerOfItsOwn("settles one and misses another");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void clearing_the_oldest_arrear_on_a_night_a_new_date_fails_raises_no_second_warning() {
        long savingsAccount = app.savingsAccountOf(theCustomer);
        long currentAccount = app.currentAccountOf(theCustomer);

        // Onto the first of a month before anything is declared, so that the four days below fall in
        // one month in the order this narrative walks them.
        windToTheFirstOfTheNextMonth();

        app.declareABillFor(theCustomer, "Rent", THE_RENTS_DAY, THE_RENT);
        app.declareABillFor(theCustomer, "Energy", THE_ENERGYS_DAY, THE_ENERGY);
        app.declareABillFor(theCustomer, "Phone", THE_PHONES_DAY, THE_PHONE);
        app.declareABillFor(theCustomer, "Internet", THE_INTERNETS_DAY, THE_INTERNET);
        // The lab's own way of reaching an unpaid bill: the rules empty the current account at two
        // and the bills find nothing there at half past. Fired once, the night before the rent falls
        // due, and never again — the nights after this one are billing runs against an account that
        // holds exactly what this test put into it.
        app.leaveARuleStanding(savingsAccount, aWeeklySweepDownToNothingFrom(currentAccount,
                theNext(THE_RENTS_DAY).minusDays(1)));

        windTo(theNext(THE_RENTS_DAY));
        app.runJob(THE_SAVING_RULES_JOB);
        theNightRunsItsBillsAndItsRaiser();
        windTo(theNext(THE_ENERGYS_DAY));
        theNightRunsItsBillsAndItsRaiser();
        windTo(theNext(THE_PHONES_DAY));
        theNightRunsItsBillsAndItsRaiser();

        List<ArrearView> atDusk = app.arrearsOf(theCustomer);
        assertThat(atDusk)
                .as("three dates outstanding, which is the crossing")
                .hasSize(3);
        assertThat(atDusk.get(0).dueOn()).isEqualTo(theLast(THE_RENTS_DAY));
        assertThat(whatWasSaidAbout(PILING_UP))
                .as("and it is announced once")
                .singleElement()
                .satisfies(said -> {
                    assertThat(said.arrears()).isEqualTo(3L);
                    assertThat(said.amount())
                            .isEqualByComparingTo(new BigDecimal(WHAT_THE_FIRST_THREE_COME_TO));
                });
        List<NotificationView> afterTheCrossing = whatWasSaidAbout(PILING_UP);

        // The night the household digs: exactly the rent comes back from savings, and the internet
        // bill falls due the same night. No sweep tonight — this is money the customer moved, not
        // money a rule moved.
        windTo(theNext(THE_INTERNETS_DAY));
        app.withdraw(savingsAccount, theCustomer, THE_RENT);
        theNightRunsItsBillsAndItsRaiser();

        List<ArrearView> atDawn = app.arrearsOf(theCustomer);
        assertThat(atDawn)
                .as("the rent is paid and the internet is not: three dates owed at dusk, three owed "
                        + "at dawn, and the account was never under the threshold in between")
                .hasSize(3);
        assertThat(atDawn.stream().map(ArrearView::billName))
                .as("a different three, which is the whole point — the oldest cleared and a new "
                        + "date took its place")
                .containsExactly("Energy", "Phone", "Internet");
        assertThat(atDawn.stream().map(ArrearView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(new BigDecimal(WHAT_THE_NEXT_THREE_COME_TO));

        assertThat(whatWasSaidAbout(PILING_UP))
                .as("and the warning is not repeated: settling one arrear on a night another date "
                        + "fails is not a climb-out, however the two movements are stamped")
                .isEqualTo(afterTheCrossing);

        // The same night's raiser a second time, which is the cheap idempotence check.
        app.runJob(TheNotificationSweep.THE_JOB);
        assertThat(whatWasSaidAbout(PILING_UP))
                .as("nor does a retry of the raiser find one")
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

    /** The most recent of those days of the month, which is the day a date already owed fell due. */
    private static LocalDate theLast(int dayOfMonth) {
        LocalDate today = app.theDateTheClockReads();
        YearMonth month = YearMonth.from(today);
        LocalDate thisMonths = month.atDay(dayOfMonth);
        return thisMonths.isAfter(today) ? month.minusMonths(1).atDay(dayOfMonth) : thisMonths;
    }
}
