package io.dataroots.savingstreak.rolloverandenvelopes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.CategorySpendingView;
import io.dataroots.savingstreak.support.SpendView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A spend filed under the wrong word in a month already gone, corrected months later, moves every
 * figure derived from that month — including the carry in every month after it.
 *
 * <p>User stories 19 and 20, met at the place they are hardest to keep: a customer who discovers in
 * March that January's car repair went under Groceries wants January fixed, and they want February
 * and March to stop being wrong as a consequence. A correction that fixed only the month it happened
 * in would leave two envelopes quietly carrying each other's mistakes for ever.
 *
 * <p><strong>This falls out rather than being arranged for, and that is the claim worth
 * asserting.</strong> Nothing in this feature is stored: no rollup row, no monthly close, no job and
 * no cursor. A correction replaces the parts of one spend, and the next read of any month folds the
 * chain again from the records as they now are. This test exists because "it falls out for free" is
 * exactly the sort of sentence that stops being true the first time somebody caches a carry, and
 * this is what would fail when they did.
 *
 * <p><strong>Two envelopes, because an envelope is where a correction shows.</strong> Under a rule
 * that carries nothing, a mistake in January reaches no further than January and the interesting
 * half of the claim cannot be tested at all. Under envelopes the two categories' chains swap
 * completely, which is the strongest statement this feature can make about a correction.
 *
 * <p>An application of its own, because a month is only a month already gone once the clock has been
 * wound past it, and winding cannot be undone.
 */
class ACorrectionInAMonthAlreadyGoneMovesTheCarryAfterItApiTest extends ApiIntegrationTest {

    private static final String ENVELOPE = "THE_SURPLUS_AND_THE_OVERSPEND_ROLL_OVER";

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-correction-and-the-carry"));
        theCustomer = app.aCustomerOfItsOwn("a correction and the carry");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void euros_moved_between_two_envelopes_in_january_swap_every_carry_after_january() {
        SpendingCategoryView groceries = app.declareACategoryFor(theCustomer, "Groceries");
        SpendingCategoryView car = app.declareACategoryFor(theCustomer, "Car");
        app.declareABudgetFor(theCustomer, groceries.categoryId(), "100.00", ENVELOPE);
        app.declareABudgetFor(theCustomer, car.categoryId(), "100.00", ENVELOPE);

        YearMonth january = YearMonth.from(app.theDateTheClockReads());
        SpendView theRepair = app.spendFor(theCustomer, "Garage", "150.00",
                groceries.categoryId());

        assertThat(rowOf(january, groceries).left())
                .as("a hundred and fifty euros of car repair filed under Groceries, which is the "
                        + "mistake this whole test is about")
                .isEqualByComparingTo("-50.00");
        assertThat(rowOf(january, car).left()).isEqualByComparingTo("100.00");

        YearMonth february = january.plusMonths(1);
        windTo(february.atDay(1));
        YearMonth march = february.plusMonths(1);
        windTo(march.atDay(1));

        assertThat(rowOf(march, groceries).carriedIn())
                .as("January left Groceries fifty down; February, allowed fifty and spending "
                        + "nothing, handed fifty on — so March begins fifty up on the strength of "
                        + "a spend that was never groceries")
                .isEqualByComparingTo("50.00");
        assertThat(rowOf(march, car).carriedIn())
                .as("while the car, having been charged for nothing, has two untouched months "
                        + "behind it")
                .isEqualByComparingTo("200.00");

        app.correctTheSplitFor(theCustomer, theRepair.spendId(), car.categoryId(), "150.00");

        assertThat(rowOf(january, groceries))
                .as("January itself is fixed rather than annotated: the euros are simply not there "
                        + "any more, because the split is the only thing in a spend that was ever "
                        + "an opinion")
                .satisfies(row -> {
                    assertThat(row.spent()).isEqualByComparingTo("0.00");
                    assertThat(row.left()).isEqualByComparingTo("100.00");
                    assertThat(row.overspent()).isFalse();
                });
        assertThat(rowOf(january, car))
                .as("and the word they meant carries the whole of it, in the month it actually "
                        + "happened in")
                .satisfies(row -> {
                    assertThat(row.spent()).isEqualByComparingTo("150.00");
                    assertThat(row.left()).isEqualByComparingTo("-50.00");
                    assertThat(row.overspent()).isTrue();
                });

        assertThat(rowOf(february, groceries).carriedIn())
                .as("February, two months downstream of the correction, moves with it")
                .isEqualByComparingTo("100.00");
        assertThat(rowOf(february, car).carriedIn()).isEqualByComparingTo("-50.00");

        assertThat(rowOf(march, groceries).carriedIn())
                .as("and so does March: the two chains have swapped end to end, which is what "
                        + "\"every figure derived from that month moves with it\" means when the "
                        + "figures are envelopes")
                .isEqualByComparingTo("200.00");
        assertThat(rowOf(march, car).carriedIn()).isEqualByComparingTo("50.00");

        assertThat(app.spendingThisMonthOf(theCustomer).theCategory(car.categoryId()).allowed())
                .as("with this month's allowance reading the same as the month named by hand, "
                        + "because they are one read asked about one month")
                .isEqualByComparingTo("150.00");
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
