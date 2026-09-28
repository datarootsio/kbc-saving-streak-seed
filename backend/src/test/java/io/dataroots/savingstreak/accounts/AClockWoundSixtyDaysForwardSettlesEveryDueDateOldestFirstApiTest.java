package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BillOccurrenceView;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A clock wound sixty days forward and one run of the job settles every date that fell in between,
 * oldest first.
 *
 * <p>User stories 12 and 13. Catching up is not a nicety here, it is the only path there is:
 * {@code MovableClock} moves in whole calendar days and a cron expression never fires for the days
 * it skipped, so on a wound clock every bill that is ever taken is taken by catch-up. A job that
 * only ever asked "is the rent due today" would find nothing to do on a clock two months on and
 * would say so convincingly. Downtime is the same story with a real clock: a rent that fell while
 * the application was off is not quietly forgiven.
 *
 * <p><strong>The order is asserted as well as the total, and it is the half a balance cannot
 * say.</strong> Two debits arriving in one run subtract the same amount however they were dealt
 * with, and "oldest first" is the promise that makes arrears build in the order they were incurred —
 * which is what the next slice's settling depends on. It is read as the order the rows were written:
 * the record hands back an identifier per date, and the date settled first carries the smaller one.
 *
 * <p>The dates expected are worked out from the calendar in this test rather than written down,
 * because sixty days from an unknown starting day is two months or three depending on which month it
 * starts in, and a test that assumed one of those would be a test that fails on the wrong days of
 * the year.
 */
class AClockWoundSixtyDaysForwardSettlesEveryDueDateOldestFirstApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "takeBillsDue";
    private static final String THE_PHONE_BILL = "10.00";
    private static final int THE_TENTH = 10;
    private static final int HOW_MANY_DAYS_ARE_MISSED = 60;

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-sixty-days-of-bills"));
        theCustomer = app.aCustomerOfItsOwn("sixty days of bills");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void every_date_that_fell_while_nothing_was_running_is_settled_in_one_run_oldest_first() {
        RecurringBillView phone = app.declareABillFor(theCustomer, "Phone", THE_TENTH, THE_PHONE_BILL);
        LocalDate declaredOn = app.theDateTheClockReads();
        BigDecimal before = app.currentAccountBalanceOf(theCustomer);

        app.daysPass(HOW_MANY_DAYS_ARE_MISSED);
        LocalDate now = app.theDateTheClockReads();
        List<LocalDate> everyDateThatFell = everyTenthBetween(declaredOn, now);
        assertThat(everyDateThatFell)
                .as("sixty days holds at least two of any day of the month, or this test is not "
                        + "about catching up at all")
                .hasSizeGreaterThanOrEqualTo(2);

        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("winding sixty days forward and running the job once produces every debit that "
                        + "fell in them, not one")
                .isEqualByComparingTo(before.subtract(new BigDecimal(THE_PHONE_BILL)
                        .multiply(BigDecimal.valueOf(everyDateThatFell.size()))));

        List<BillOccurrenceView> history = app.historyOfBill(theCustomer, phone.billId());
        assertThat(history.stream().map(BillOccurrenceView::dueOn))
                .as("every date in between, newest first as a history reads")
                .containsExactlyElementsOf(newestFirst(everyDateThatFell));
        assertThat(history).allSatisfy(settled ->
                assertThat(settled.outcome()).isEqualTo("PAID"));

        assertThat(history.stream()
                .sorted(Comparator.comparing(BillOccurrenceView::id))
                .map(BillOccurrenceView::dueOn))
                .as("written in the order the dates actually fell — the row settled first carries "
                        + "the smaller identifier — which is the only order arrears can be built in")
                .containsExactlyElementsOf(everyDateThatFell);

        assertThat(history.get(0).daysLate())
                .as("the newest of them was settled the morning the clock stopped at, and the "
                        + "lateness the record reports is the gap a trainer demonstrating catch-up "
                        + "points at")
                .isEqualTo(ChronoUnit.DAYS.between(history.get(0).dueOn(), now));
    }

    /** The same dates the other way round, which is the order a history is read in. */
    private static List<LocalDate> newestFirst(List<LocalDate> oldestFirst) {
        List<LocalDate> reversed = new ArrayList<>(oldestFirst);
        Collections.reverse(reversed);
        return reversed;
    }

    /**
     * Every tenth of a month falling strictly after the day the bill was declared and at or before
     * the day the clock now reads, oldest first — the calendar this test expects the application to
     * have walked, worked out here rather than assumed.
     *
     * <p>Strictly after the declaration, because the cursor starts there: a bill declared on the
     * tenth is a promise about next month rather than a debit for that morning.
     */
    private static List<LocalDate> everyTenthBetween(LocalDate declaredOn, LocalDate now) {
        List<LocalDate> dates = new ArrayList<>();
        YearMonth month = YearMonth.from(declaredOn);
        while (!month.isAfter(YearMonth.from(now))) {
            LocalDate tenth = month.atDay(Math.min(THE_TENTH, month.lengthOfMonth()));
            if (tenth.isAfter(declaredOn) && !tenth.isAfter(now)) {
                dates.add(tenth);
            }
            month = month.plusMonths(1);
        }
        return dates;
    }
}
