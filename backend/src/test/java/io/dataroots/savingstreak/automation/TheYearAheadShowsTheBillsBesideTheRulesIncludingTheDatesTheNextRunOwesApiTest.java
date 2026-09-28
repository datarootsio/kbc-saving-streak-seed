package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BillToComeView;
import io.dataroots.savingstreak.support.OccurrenceToComeView;
import io.dataroots.savingstreak.support.RecurringBillView;
import io.dataroots.savingstreak.support.RulePreviewView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The year ahead draws the bills beside what the rules will move, on one day at a time — and a date
 * the nightly run has not caught up with is on it, marked as owed rather than quietly dropped.
 *
 * <p>User story 29, and the two halves of it that are easy to get half right.
 *
 * <p><strong>Beside.</strong> A bar that forecast only the transfers would be decoration: the whole
 * decision a customer is making is how much of a current account to sweep into savings, and the rent
 * is the other claim on that account. A customer looking at March has to see the rent sitting next
 * to the sweep that would starve it, in one list, over one window.
 *
 * <p><strong>Including what the next run owes.</strong> The clock moves in whole calendar days and
 * a cron expression never fires for the days it skipped, so between a wind and a run every bill is
 * behind its cursor — and after downtime so is every real one. The forecast counts each bill from
 * its own cursor, exactly as the run does, so those dates are at the head of the list rather than
 * hidden. <strong>This is the defect the saving rules' own forecast is known to have and this one
 * deliberately does not reproduce:</strong> a forecast that raised its lower bound to today would
 * show nothing for the fifth and then watch the very next run take it. The assertion is made both
 * ways round — the date is in the forecast, and the run then takes that date and no other.
 *
 * <p>In the automation package because the year-ahead preview is that module's read model, beside
 * the other tests of what the rules will do.
 */
class TheYearAheadShowsTheBillsBesideTheRulesIncludingTheDatesTheNextRunOwesApiTest
        extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";

    /** The day this test stands on before winding past the day the rent and the sweep both fall. */
    private static final int THE_THIRD = 3;
    private static final int THE_FIFTH = 5;
    private static final String THE_RENT = "50.00";
    private static final String WHAT_THE_RULE_MOVES = "5.00";

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationStandingOnTheThirdOfAMonth() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-year-ahead-with-bills"));
        theCustomer = app.aCustomerOfItsOwn("the year ahead with bills");
        LocalDate today = app.theDateTheClockReads();
        LocalDate theThird = today.withDayOfMonth(1).plusMonths(1).plusDays(THE_THIRD - 1);
        app.daysPass(ChronoUnit.DAYS.between(today, theThird));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_rent_and_a_transfer_falling_on_one_morning_are_both_on_the_bar_even_when_already_owed() {
        long savingsAccount = app.savingsAccountOf(theCustomer);
        LocalDate theMorningBothFall = app.theDateTheClockReads().plusDays(2);
        assertThat(theMorningBothFall.getDayOfMonth())
                .as("the application was wound to the third, so two days on is the fifth")
                .isEqualTo(THE_FIFTH);

        RecurringBillView rent = app.declareABillFor(theCustomer, "Rent", THE_FIFTH, THE_RENT);
        SavingRuleView sweep = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(
                        app.currentAccountOf(theCustomer), "Every week into savings",
                        theMorningBothFall.getDayOfWeek().name(), WHAT_THE_RULE_MOVES));

        // Past the morning both of them fall on, and neither job is run: the state a trainer who
        // winds the clock and reads the page before running anything is actually in.
        app.daysPass(4);
        assertThat(app.theDateTheClockReads()).isAfter(theMorningBothFall);

        RulePreviewView year = app.previewOn(savingsAccount);

        assertThat(year.from())
                .as("the window still opens today, because that is the twelve months this looked "
                        + "forward over; what is owed is said on the lines themselves")
                .isEqualTo(app.theDateTheClockReads());
        assertThat(year.occurrences())
                .as("the rule's own morning is at the head of its list, already owed")
                .anySatisfy(due -> {
                    assertThat(due.ruleId()).isEqualTo(sweep.id());
                    assertThat(due.dueOn()).isEqualTo(theMorningBothFall);
                    assertThat(due.owedRatherThanStillToCome()).isTrue();
                });
        assertThat(year.bills())
                .as("and so is the rent — counted from the bill's own cursor, not from today. A "
                        + "forecast that raised its lower bound to the start of yesterday would "
                        + "show nothing here and then watch the next run take the money")
                .anySatisfy(date -> {
                    assertThat(date.billId()).isEqualTo(rent.billId());
                    assertThat(date.billName()).isEqualTo("Rent");
                    assertThat(date.dueOn()).isEqualTo(theMorningBothFall);
                    assertThat(date.amount()).isEqualByComparingTo(new BigDecimal(THE_RENT));
                    assertThat(date.owedRatherThanStillToCome())
                            .as("said on the line rather than left for a page to work out by "
                                    + "comparing the day against the browser's idea of today")
                            .isTrue();
                });
        assertThat(year.bills())
                .as("every line marked owed is dated before the window opens, and every line that "
                        + "is not, is not — one boundary, drawn by the backend against its own clock")
                .allSatisfy(date -> assertThat(date.owedRatherThanStillToCome())
                        .isEqualTo(date.dueOn().isBefore(year.from())));

        assertThat(theDaysOf(year.occurrences()))
                .as("the rent and the transfer are on the bar for the same morning, which is the "
                        + "whole point of drawing them together: a sweep sitting above a rent is a "
                        + "rent that may go unpaid, and that is only visible when both are there")
                .contains(theMorningBothFall);
        assertThat(theBillDaysOf(year.bills())).contains(theMorningBothFall);

        app.runJob(THE_BILLS_JOB);

        assertThat(app.historyOfBill(theCustomer, rent.billId()))
                .as("and the run then takes exactly the date the forecast showed, which is the "
                        + "other half of the same claim")
                .singleElement()
                .satisfies(taken -> {
                    assertThat(taken.dueOn()).isEqualTo(theMorningBothFall);
                    assertThat(taken.outcome()).isEqualTo("PAID");
                });
        assertThat(theBillDaysOf(app.previewOn(savingsAccount).bills()))
                .as("and the date leaves the forecast once it has been settled, because the "
                        + "forecast counts from the cursor the run just moved")
                .doesNotContain(theMorningBothFall);
    }

    private static List<LocalDate> theDaysOf(List<OccurrenceToComeView> occurrences) {
        return occurrences.stream().map(OccurrenceToComeView::dueOn).toList();
    }

    private static List<LocalDate> theBillDaysOf(List<BillToComeView> bills) {
        return bills.stream().map(BillToComeView::dueOn).toList();
    }
}
