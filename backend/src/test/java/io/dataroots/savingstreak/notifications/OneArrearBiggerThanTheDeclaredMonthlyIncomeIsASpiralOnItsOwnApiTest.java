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
 * One unpaid bill worth more than a month's declared income is a spiral all by itself, and is said
 * so without waiting for a second or a third.
 *
 * <p>The half of user story 24 that a count alone cannot reach. Three small arrears and one enormous
 * one are the same trouble, and a customer whose single missed tuition bill is worth two months of
 * everything they earn should not have to miss two more before the application says anything louder
 * than "a bill could not be paid". Either condition alone is the trigger, and this is the one the
 * count would miss.
 *
 * <p><strong>The income is declared and deliberately never paid.</strong> The income job is not run,
 * so the declaration does nothing but set the figure the arrears are measured against — which is
 * exactly what it is for here, and keeps the account's balance the doing of the sweep and the
 * billing run alone.
 *
 * <p>Both notifications the account earns are asserted: the ordinary one about the bill itself, and
 * the louder one about the pile, which on this account is one date deep.
 */
class OneArrearBiggerThanTheDeclaredMonthlyIncomeIsASpiralOnItsOwnApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";
    private static final String THE_SAVING_RULES_JOB = "fireSavingRulesDue";

    private static final String PILING_UP = "BILLS_ARE_PILING_UP";
    private static final String COULD_NOT_BE_PAID = "A_BILL_COULD_NOT_BE_PAID";

    private static final int THE_TUITIONS_DAY = 12;
    private static final int THE_PAYDAY = 28;

    /** More than the income below, which is the whole of the point. */
    private static final String THE_TUITION = "1200.00";

    private static final String THE_MONTHLY_INCOME = "800.00";

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-one-arrear-over-the-income"));
        theCustomer = app.aCustomerOfItsOwn("one arrear over a month of income");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_single_missed_bill_worth_more_than_a_months_income_is_announced_as_a_spiral() {
        long savingsAccount = app.savingsAccountOf(theCustomer);
        long currentAccount = app.currentAccountOf(theCustomer);

        windToTheFirstOfTheNextMonth();
        app.declareIncomeFor(theCustomer, THE_PAYDAY, THE_MONTHLY_INCOME);
        app.declareABillFor(theCustomer, "Tuition", THE_TUITIONS_DAY, THE_TUITION);
        app.leaveARuleStanding(savingsAccount, aWeeklySweepDownToNothingFrom(currentAccount));

        windTo(theNext(THE_TUITIONS_DAY));
        app.runJob(THE_SAVING_RULES_JOB);
        app.runJob(THE_BILLS_JOB);
        app.runJob(TheNotificationSweep.THE_JOB);

        assertThat(app.arrearsOf(theCustomer))
                .as("one date owed, and it is worth more than a month of this customer's income")
                .singleElement()
                .satisfies(owed ->
                        assertThat(owed.amount()).isEqualByComparingTo(new BigDecimal(THE_TUITION)));

        assertThat(whatWasSaidAbout(COULD_NOT_BE_PAID))
                .as("the ordinary notification is raised, as it would be for any missed bill")
                .hasSize(1);

        List<NotificationView> theWarning = whatWasSaidAbout(PILING_UP);
        assertThat(theWarning)
                .as("and the louder one is raised on a single arrear, because what it comes to has "
                        + "passed the income the customer declared — the count never reached three "
                        + "and never will")
                .singleElement()
                .satisfies(said -> {
                    assertThat(said.arrears()).isEqualTo(1L);
                    assertThat(said.amount()).isEqualByComparingTo(new BigDecimal(THE_TUITION));
                    assertThat(said.currentAccountId()).isEqualTo(currentAccount);
                    assertThat(said.savingsAccountId()).isNull();
                });

        // A second night carrying the same arrear, which is the once-only rule over the condition
        // that is not a count.
        app.daysPass(1);
        app.runJob(THE_BILLS_JOB);
        app.runJob(TheNotificationSweep.THE_JOB);
        assertThat(whatWasSaidAbout(PILING_UP))
                .as("standing over the threshold a second night is not crossing it a second time")
                .isEqualTo(theWarning);
    }

    /** Only what has been said to this customer under one reason, newest first. */
    private static List<NotificationView> whatWasSaidAbout(String reason) {
        return Arrays.stream(app.notificationsOf(theCustomer))
                .filter(said -> reason.equals(said.reason()))
                .toList();
    }

    /** A weekly sweep taking everything above nothing, as the lab's own recipe for an unpaid bill. */
    private static Map<String, Object> aWeeklySweepDownToNothingFrom(long fromCurrentAccountId) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("name", "Everything, every week");
        rule.put("fromCurrentAccountId", fromCurrentAccountId);
        rule.put("trigger", "WEEKLY");
        rule.put("dayOfWeek", app.theDateTheClockReads().getDayOfWeek().name());
        rule.put("howMuchMoves", "EVERYTHING_ABOVE");
        rule.put("floor", "0.00");
        return rule;
    }

    /** Onto the first of the next month, so that the tuition's day falls in the month after it. */
    private static void windToTheFirstOfTheNextMonth() {
        windTo(YearMonth.from(app.theDateTheClockReads()).plusMonths(1).atDay(1));
    }

    /** Winds the clock to that day, insisting on a move forwards: the clock only goes one way. */
    private static void windTo(LocalDate day) {
        long days = ChronoUnit.DAYS.between(app.theDateTheClockReads(), day);
        assertThat(days)
                .as("this test has to reach " + day + " and the clock cannot be wound backwards")
                .isPositive();
        app.daysPass(days);
    }

    /** The first of those days of the month that begins after today. */
    private static LocalDate theNext(int dayOfMonth) {
        LocalDate today = app.theDateTheClockReads();
        YearMonth month = YearMonth.from(today);
        LocalDate thisMonths = month.atDay(dayOfMonth);
        return thisMonths.isAfter(today) ? thisMonths : month.plusMonths(1).atDay(dayOfMonth);
    }
}
