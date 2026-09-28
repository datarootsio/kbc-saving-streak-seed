package io.dataroots.savingstreak.rolloverandenvelopes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.CategorySpendingView;
import io.dataroots.savingstreak.support.RecurringBillView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An envelope emptied by the bills filed in it carries that overspend forward exactly as one emptied
 * at the supermarket does.
 *
 * <p>User stories 21, 22 and 25 at the point where they meet. A category's month is the committed
 * money and the discretionary money added — the bills that fell due in it and the spends its holder
 * chose — and the carry is folded from that whole figure rather than from the half the customer
 * picked out by hand. A fold that counted only the spends would let somebody overfill an envelope
 * with their own standing orders and never be told, which is the one way this feature could report a
 * budget that was being kept while the money was plainly gone.
 *
 * <p><strong>Asserted deliberately rather than relied upon.</strong> That the committed half is
 * inside {@code spent} is true today by construction, and it is exactly the kind of truth a later
 * slice removes by accident while making the two halves easier to draw apart. This is what would
 * fail.
 *
 * <p>An application of its own, because a bill occurrence only exists once a nightly run has
 * presented one, and a run needs a clock this test may wind.
 */
class AnEnvelopeCarriesTheOverspendItsOwnBillsCausedApiTest extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";

    private static final String ENVELOPE = "THE_SURPLUS_AND_THE_OVERSPEND_ROLL_OVER";

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-envelope-of-bills"));
        theCustomer = app.aCustomerOfItsOwn("an envelope of bills");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_season_ticket_larger_than_its_envelope_takes_the_overspend_into_the_next_month() {
        SpendingCategoryView transport = app.declareACategoryFor(theCustomer, "Transport");
        RecurringBillView seasonTicket = app.declareABillFor(theCustomer, "Season ticket", 1,
                "150.00");
        app.putBillInCategoryFor(theCustomer, seasonTicket.billId(), transport.categoryId());

        // The first of next month, where a bill declared for the first falls due. Counted through
        // the calendar rather than guessed at: a fixed thirty would land in the wrong month twice
        // a year.
        YearMonth theMonthItWasTakenIn = YearMonth.from(app.theDateTheClockReads()).plusMonths(1);
        windTo(theMonthItWasTakenIn.atDay(1));
        // The figure is named in the month the bill falls due in, so that the month before it
        // carried none — an envelope opened a month early would hand this one a full hundred it
        // never spent, and the overspend under test would be buried under it.
        app.declareABudgetFor(theCustomer, transport.categoryId(), "100.00", ENVELOPE);
        app.runJob(THE_BILLS_JOB);

        CategorySpendingView taken = rowOf(theMonthItWasTakenIn, transport);
        assertThat(taken.carriedIn())
                .as("nothing arrives in the first month a figure stands in, which is what makes "
                        + "everything below the bill's doing")
                .isEqualByComparingTo("0.00");
        assertThat(taken.committed())
                .as("money that left on a standing instruction the customer made once")
                .isEqualByComparingTo("150.00");
        assertThat(taken.discretionary())
                .as("and nothing they chose, which is what makes this month's overspend entirely "
                        + "the bill's doing")
                .isEqualByComparingTo("0.00");
        assertThat(taken.spent())
                .as("what a category's month cost is both halves added, whichever way the money "
                        + "left")
                .isEqualByComparingTo("150.00");
        assertThat(taken.left()).isEqualByComparingTo("-50.00");

        YearMonth after = theMonthItWasTakenIn.plusMonths(1);
        windTo(after.atDay(1));

        CategorySpendingView next = rowOf(after, transport);
        assertThat(next.carriedIn())
                .as("the envelope carries what the bill took it over by, exactly as it would carry "
                        + "a supermarket trip: the fold reads what the month cost and does not ask "
                        + "which half it came out of")
                .isEqualByComparingTo("-50.00");
        assertThat(next.allowed())
                .as("so the month allows fifty euros of transport, and a customer whose standing "
                        + "orders are eating their envelope is told so before they spend a cent "
                        + "of it")
                .isEqualByComparingTo("50.00");
    }

    /** One category's row out of one month, named by the month rather than by whatever the clock reads. */
    private static CategorySpendingView rowOf(YearMonth month, SpendingCategoryView category) {
        return app.spendingInMonthOf(theCustomer, month.toString())
                .theCategory(category.categoryId());
    }

    /**
     * Moves the clock to a day, counted through the calendar rather than by a fixed span. The clock
     * only goes forward and only in whole days, which is exactly the way a trainer moves it.
     */
    private static void windTo(LocalDate day) {
        long days = ChronoUnit.DAYS.between(app.theDateTheClockReads(), day);
        assertThat(days).describedAs("the clock only goes forward").isPositive();
        app.daysPass(days);
        assertThat(app.theDateTheClockReads()).isEqualTo(day);
    }
}
