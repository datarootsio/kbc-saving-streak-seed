package io.dataroots.savingstreak.theweeksahead;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.MonthAheadView;
import io.dataroots.savingstreak.support.RecurringBillView;
import io.dataroots.savingstreak.support.WeekAheadView;
import io.dataroots.savingstreak.support.WeeksAheadView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The six weekly rows and the month-ahead card are two windows on one calendar, and they say the
 * same thing about the days they both hold.
 *
 * <p><strong>The claim this test exists for.</strong> Both cards are drawn on the same screenful of
 * a customer's life, one above the other, and both are built from the bill and payday calendars the
 * 01:00 and 02:30 runs walk — {@code WhenABillIsDue} and {@code WhenIncomeIsDue}. If they could
 * disagree about which day a salary lands on or which day a rent falls due, a customer would be
 * reading two answers to one question and would have no way of telling which of them the nightly run
 * was going to act on. They cannot, and this is what would fail if somebody gave either window a
 * calendar of its own.
 *
 * <p><strong>How the two windows are compared, given that they are different lengths.</strong> The
 * month card looks from today to this day next month; the weekly card looks from the Monday this
 * week began on for six whole weeks. Only some of those days are shared, so the comparison is made
 * over the rows that end on or before the month card's own last day — read off the card rather than
 * worked out here — and the fixture deliberately puts every dated thing this account has inside
 * that stretch. What falls outside it is asserted to be outside it, so the agreement is a real
 * comparison rather than two empty sums.
 *
 * <p><strong>The arrear is in it on purpose.</strong> A date that could not be paid is a claim on
 * the very next money in, and both cards count it that way: the month card folds it into
 * {@code billsDue}, and the weekly card puts it on the first row. It is the figure most likely to
 * be counted by one and not the other, and it is the one a customer carrying two rents most needs
 * the two cards to agree about.
 *
 * <p>Covers user stories 33 and 34, and the note the specification makes about the two windows.
 *
 * <p>An application of its own, because an arrear needs a nightly run and a run needs a clock this
 * test may wind.
 */
class TheWeeklyRowsAndTheMonthAheadAgreeOverTheDaysTheyShareApiTest extends ApiIntegrationTest {

    /** More than a new customer's account holds, which is what makes the first date go unpaid. */
    private static final String MORE_THAN_THE_ACCOUNT_HOLDS = "2000.00";

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationCarryingAnArrearAndAMonthOfDates() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-two-windows-one-calendar"));
        theCustomer = app.aCustomerOfItsOwn("two windows one calendar");
        LocalDate theFirst = windToAMondayThatIsTheFirstOfAMonth();

        // A date the account cannot cover, presented on the second, which leaves the arrear both
        // cards have to agree about. The bill is ended afterwards: ending stops it being presented
        // again and waives nothing that is already owed, so what is left is one debt and no future
        // dates to confuse the comparison with.
        RecurringBillView tooBig = app.declareABillFor(theCustomer, "Last year's tax", 2,
                MORE_THAN_THE_ACCOUNT_HOLDS);
        app.aNightPasses();
        assertThat(app.theDateTheClockReads())
                .describedAs("the night takes the clock to the second, which is the day the tax "
                        + "was owed on")
                .isEqualTo(theFirst.plusDays(1));
        app.endTheBill(theCustomer, tooBig.billId());
        assertThat(app.arrearsOf(theCustomer))
                .describedAs("the date went unpaid, which is the whole of what this test needs "
                        + "before it can say the two cards count it the same way")
                .hasSize(1);

        // Declared after the night, so that both calendars are counted from a cursor this test put
        // there rather than from whatever the run left behind.
        app.declareIncomeFor(theCustomer, 12, "2500.00");
        app.declareABillFor(theCustomer, "Electricity", 20, "150.00");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_weekly_window_opens_behind_the_month_card_and_reaches_further_than_it() {
        WeeksAheadView weeks = app.weeksAheadOf(theCustomer);
        MonthAheadView month = app.theCurrentAccountOf(theCustomer).monthAhead();

        assertThat(weeks.from())
                .as("the weekly card opens on the Monday this week began on, which on any day but "
                        + "a Monday is behind the day the month card opens on — the row a customer "
                        + "is standing in is the row they most want to read")
                .isBeforeOrEqualTo(month.from());
        assertThat(weeks.until())
                .as("and reaches six whole weeks out, which is further than a month: the two are "
                        + "different windows on one calendar rather than two lengths of the same "
                        + "one, and there would be nothing to compare if they were not")
                .isAfter(month.until());
    }

    @Test
    void what_arrives_and_what_is_committed_agree_over_the_days_the_two_windows_share() {
        WeeksAheadView weeks = app.weeksAheadOf(theCustomer);
        MonthAheadView month = app.theCurrentAccountOf(theCustomer).monthAhead();

        List<WeekAheadView> shared = theRowsEndingBy(weeks, month.until());
        assertThat(shared)
                .as("there are whole weeks inside the month card's own window, or there is nothing "
                        + "here to compare")
                .isNotEmpty();

        assertThat(theTotalOf(shared, WeekAheadView::arriving))
                .as("the salary lands on the day both calendars say it lands on, so the rows "
                        + "inside the month card add up to exactly what the month card says is "
                        + "coming in — one payday, counted once by each of them")
                .isEqualByComparingTo(month.incomeDue());
        assertThat(theTotalOf(shared, WeekAheadView::committed))
                .as("and what is committed agrees the same way: one electricity date still to "
                        + "fall and one tax bill already owed, which the month card folds into "
                        + "billsDue and the weekly card puts on its first row")
                .isEqualByComparingTo(month.billsDue());
        assertThat(month.arrearsOutstanding())
                .as("and the arrear really is in both of those figures rather than in neither, "
                        + "which is what makes the agreement worth asserting")
                .isEqualByComparingTo(MORE_THAN_THE_ACCOUNT_HOLDS);
    }

    @Test
    void the_arrear_is_on_the_first_row_because_it_is_offered_the_very_next_money_in() {
        WeeksAheadView weeks = app.weeksAheadOf(theCustomer);

        assertThat(weeks.weeks().get(0).committed())
                .as("the 02:30 run settles what is owed before it presents anything new, so a "
                        + "debt is a claim on this week rather than on the week its date comes "
                        + "round again — a forecast that waited would tell somebody carrying two "
                        + "rents that they had room they do not have")
                .isEqualByComparingTo(MORE_THAN_THE_ACCOUNT_HOLDS);
    }

    /** The rows that end on or before that day, which is the stretch the two windows share. */
    private static List<WeekAheadView> theRowsEndingBy(WeeksAheadView weeks, LocalDate until) {
        return weeks.weeks().stream().filter(week -> !week.endsOn().isAfter(until)).toList();
    }

    /** One column added down those rows, which is what the month card's figure is compared against. */
    private static BigDecimal theTotalOf(List<WeekAheadView> rows,
                                         Function<WeekAheadView, BigDecimal> column) {
        return rows.stream().map(column).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Winds the clock on to the next Monday that is also the first of a month, and answers it.
     *
     * <p>The first of the month is what puts every date this fixture declares — a payday on the
     * twelfth, an electricity bill on the twentieth — inside the stretch the two windows share,
     * whatever the length of the month the run happens to land in. A Monday is what makes the
     * weekly window open on a day this test can count from.
     */
    private static LocalDate windToAMondayThatIsTheFirstOfAMonth() {
        LocalDate day = app.theDateTheClockReads();
        long days = 0;
        while (!(day.getDayOfWeek() == DayOfWeek.MONDAY && day.getDayOfMonth() == 1)) {
            day = day.plusDays(1);
            days++;
        }
        if (days > 0) {
            app.daysPass(days);
        }
        assertThat(app.theDateTheClockReads()).isEqualTo(day);
        return day;
    }
}
