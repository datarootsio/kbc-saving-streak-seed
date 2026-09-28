package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One bill whose amount is not an amount of money is skipped, and every other customer's bills that
 * night still go out.
 *
 * <p><strong>Why this is a test and not a comment.</strong> The whole nightly run is one
 * transaction over every standing bill on every account, so anything thrown while a single row is
 * being looked at is not one bill missed — it is the night rolled back for everybody, and a
 * customer whose rent silently did not leave has no way of knowing that somebody else's row is why.
 * {@code takeBillsDueBy} therefore promises that nothing a single row can be wrong about takes the
 * night down, and a promise about a state the endpoints cannot produce is exactly the kind that
 * quietly stops being true.
 *
 * <p>It stopped being true once already. A guard on the amount was added to {@code present()}, but
 * the per-bill DEBUG line above it formatted the same amount first, for every standing bill, before
 * the guard could run — and formatting a null threw, at any log level, because SLF4J builds its
 * arguments whether or not DEBUG is on. The guard was unreachable, the run answered 500, and the
 * only trace was a generic job-failed wrapper naming no bill. That is what the null case below
 * holds still.
 *
 * <p><strong>Two customers, deliberately.</strong> The bad row is one customer's and the assertion
 * that matters is the other's: their bill falling due the same morning is the difference between one row
 * skipped and one transaction rolled back. A test with a single customer would pass either way.
 *
 * <p>The rows are hand-edited through {@link AnApplicationWithAClockToMove#theApplicationsOwn} for
 * the reason that hatch documents: the state under test is one the endpoints refuse to create —
 * {@code AmountOfMoney} turns away nought and less, and the column is the only place a null can come
 * from — while everything asserted about it is driven and read over HTTP. It is the same edit
 * somebody would make against a training database, which is the way this actually happens.
 *
 * <p>Each case also repairs the amount and runs the night again, because a date skipped must still
 * be owed: a run that swallowed the date instead of leaving the cursor where it was would satisfy
 * every assertion above it and lose the customer a month of rent.
 */
class ABillWhoseAmountIsNotAnAmountOfMoneyDoesNotTakeTheNightDownApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "takeBillsDue";
    private static final int THE_NINTH = 9;
    private static final String WHAT_THE_GYM_COSTS = "40.00";
    private static final String WHAT_THE_INTERNET_COSTS = "20.00";

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;
    private static String somebodyElse;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-bill-that-is-not-money"));
        theCustomer = app.aCustomerOfItsOwn("a bill that is not money");
        somebodyElse = app.aCustomerOfItsOwn("a bill that is not money somebody else");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_bill_with_no_amount_at_all_is_skipped_and_the_night_goes_on_for_everybody_else() {
        theNightWhereTheGymIsWorth(null);
    }

    @Test
    void a_bill_worth_nothing_is_skipped_and_the_night_goes_on_for_everybody_else() {
        theNightWhereTheGymIsWorth("0.00");
    }

    /**
     * Declares a bill for each of the two customers on the same day, makes the first one's worth whatever a
     * hand-edited row says, and runs the night over both.
     *
     * @param whatTheRowSays what to write into the first customer's amount column, or null for no amount at all
     */
    private void theNightWhereTheGymIsWorth(String whatTheRowSays) {
        RecurringBillView gym = app.declareABillFor(theCustomer, "Gym", THE_NINTH, WHAT_THE_GYM_COSTS);
        RecurringBillView internet = app.declareABillFor(somebodyElse, "Internet", THE_NINTH,
                WHAT_THE_INTERNET_COSTS);
        BigDecimal theGymsHolderHad = app.currentAccountBalanceOf(theCustomer);
        BigDecimal theOtherHolderHad = app.currentAccountBalanceOf(somebodyElse);

        LocalDate theDayBothFallDue = theNextNinthAfter(app.theDateTheClockReads());
        windTo(theDayBothFallDue);
        writeIntoTheAmountOf(gym.billId(), whatTheRowSays);

        app.runJob(THE_JOB);

        assertThat(app.currentAccountBalanceOf(somebodyElse))
                .as("the night was not rolled back: the customer whose row was fine was presented "
                        + "his bill on the morning it fell due, which is the whole assertion")
                .isEqualByComparingTo(theOtherHolderHad.subtract(new BigDecimal(WHAT_THE_INTERNET_COSTS)));
        assertThat(app.historyOfBill(somebodyElse, internet.billId()))
                .as("and it is written down, so the run reached the end rather than throwing "
                        + "halfway and undoing itself")
                .singleElement()
                .satisfies(taken -> {
                    assertThat(taken.dueOn()).isEqualTo(theDayBothFallDue);
                    assertThat(taken.outcome()).isEqualTo("PAID");
                });

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("while the bill nobody could put a price on took nothing at all")
                .isEqualByComparingTo(theGymsHolderHad);
        assertThat(app.historyOfBill(theCustomer, gym.billId()))
                .as("and was not recorded either way, because an outcome of paid or unpaid is a "
                        + "statement about an amount and there is no amount here to make it about")
                .isEmpty();

        writeIntoTheAmountOf(gym.billId(), WHAT_THE_GYM_COSTS);
        app.runJob(THE_JOB);

        assertThat(app.historyOfBill(theCustomer, gym.billId()))
                .as("the date was skipped rather than swallowed: with an amount to withdraw it is "
                        + "still owed, and the next run presents the day it always fell due on")
                .singleElement()
                .satisfies(taken -> {
                    assertThat(taken.dueOn()).isEqualTo(theDayBothFallDue);
                    assertThat(taken.outcome()).isEqualTo("PAID");
                });
        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("and the money moves then, once")
                .isEqualByComparingTo(theGymsHolderHad.subtract(new BigDecimal(WHAT_THE_GYM_COSTS)));

        app.endTheBill(theCustomer, gym.billId());
        app.endTheBill(somebodyElse, internet.billId());
    }

    /**
     * Writes an amount straight into the row, the way somebody editing a training database would.
     *
     * <p>A null is written as a literal rather than bound, because a bound null has no SQL type for
     * the driver to infer and that is a quarrel with JDBC rather than with the application.
     */
    private static void writeIntoTheAmountOf(long billId, String amount) {
        JdbcTemplate theFile = app.theApplicationsOwn(JdbcTemplate.class);
        if (amount == null) {
            theFile.update("update recurring_bill set amount = null where id = ?", billId);
        } else {
            theFile.update("update recurring_bill set amount = ? where id = ?",
                    new BigDecimal(amount), billId);
        }
    }

    /**
     * Winds the clock to that day, insisting on a move forwards: the clock only goes one way and
     * refuses a move of no days at all.
     */
    private static void windTo(LocalDate day) {
        long days = ChronoUnit.DAYS.between(app.theDateTheClockReads(), day);
        assertThat(days)
                .as("this test has to reach " + day + " and the clock cannot be wound backwards")
                .isPositive();
        app.daysPass(days);
    }

    /** The first ninth that begins after the given day. */
    private static LocalDate theNextNinthAfter(LocalDate today) {
        YearMonth month = YearMonth.from(today);
        LocalDate thisMonths = month.atDay(THE_NINTH);
        return thisMonths.isAfter(today) ? thisMonths : month.plusMonths(1).atDay(THE_NINTH);
    }
}
