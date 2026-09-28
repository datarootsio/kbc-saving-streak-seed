package io.dataroots.savingstreak.rolloverandenvelopes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.CategorySpendingView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a customer chooses about the difference when a month ends: nothing follows them, the surplus
 * does, or the surplus and the overspend both do.
 *
 * <p>User stories 24, 25 and 26. A frugal month buys a generous one, an envelope you overfill is an
 * envelope with less in it next month, and a category that carries nothing starts every month clean.
 *
 * <p><strong>Three categories on one account, the same figure on each, the same spending in each,
 * and only the rule different.</strong> A rule is only meaningful by comparison: "the surplus rolls
 * over" says nothing on its own, and three tests with three sets of amounts could all pass against
 * an application that had quietly collapsed two of the rules into one. Run side by side on one
 * account over the same months, every difference below is the customer's own choice doing its work
 * — and the month with the overspend in the middle is where all three part company at once.
 *
 * <p>Nothing is stored and no month is ever closed. These figures are derived on every read from the
 * declarations and the movements, which is why the clock is wound rather than a job being run: there
 * is no job.
 *
 * <p>An application of its own, because months pass by winding the clock and that cannot be undone.
 */
class TheThreeRolloverRulesCarryDifferentlyAcrossMonthsApiTest extends ApiIntegrationTest {

    private static final String NOTHING = "NOTHING_ROLLS_OVER";

    private static final String SURPLUS = "THE_SURPLUS_ROLLS_OVER";

    private static final String ENVELOPE = "THE_SURPLUS_AND_THE_OVERSPEND_ROLL_OVER";

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-three-rollover-rules"));
        theCustomer = app.aCustomerOfItsOwn("three rollover rules");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void one_frugal_month_and_one_overspent_one_are_carried_three_different_ways() {
        SpendingCategoryView clean = app.declareACategoryFor(theCustomer, "Clean slate");
        SpendingCategoryView saved = app.declareACategoryFor(theCustomer, "Saved up");
        SpendingCategoryView envelope = app.declareACategoryFor(theCustomer, "Envelope");
        app.declareABudgetFor(theCustomer, clean.categoryId(), "100.00", NOTHING);
        app.declareABudgetFor(theCustomer, saved.categoryId(), "100.00", SURPLUS);
        app.declareABudgetFor(theCustomer, envelope.categoryId(), "100.00", ENVELOPE);

        YearMonth first = YearMonth.from(app.theDateTheClockReads());
        spendOnEachOf("A frugal month", "60.00", clean, saved, envelope);

        assertThat(rowOf(first, clean))
                .as("the first month is the same under every rule, because there is nothing behind "
                        + "it for any of them to carry")
                .satisfies(row -> assertReads(row, "100.00", "0.00", "100.00", "60.00", "40.00"));
        assertReads(rowOf(first, saved), "100.00", "0.00", "100.00", "60.00", "40.00");
        assertReads(rowOf(first, envelope), "100.00", "0.00", "100.00", "60.00", "40.00");

        YearMonth second = first.plusMonths(1);
        windTo(second.atDay(1));

        assertReads(rowOf(second, clean), "100.00", "0.00", "100.00", "0.00", "100.00");
        assertThat(rowOf(second, saved).carriedIn())
                .as("forty euros the customer did not spend, arriving because they asked for it: a "
                        + "frugal month buying a generous one is the whole of user story 24")
                .isEqualByComparingTo("40.00");
        assertReads(rowOf(second, saved), "100.00", "40.00", "140.00", "0.00", "140.00");
        assertReads(rowOf(second, envelope), "100.00", "40.00", "140.00", "0.00", "140.00");

        spendOnEachOf("An expensive month", "160.00", clean, saved, envelope);

        assertReads(rowOf(second, clean), "100.00", "0.00", "100.00", "160.00", "-60.00");
        assertReads(rowOf(second, saved), "100.00", "40.00", "140.00", "160.00", "-20.00");
        assertReads(rowOf(second, envelope), "100.00", "40.00", "140.00", "160.00", "-20.00");
        assertThat(rowOf(second, clean).overspent())
                .as("all three went over what they were allowed, which is what makes the third "
                        + "month the interesting one")
                .isTrue();
        assertThat(rowOf(second, saved).overspent()).isTrue();
        assertThat(rowOf(second, envelope).overspent()).isTrue();

        YearMonth third = second.plusMonths(1);
        windTo(third.atDay(1));

        assertThat(rowOf(third, clean).carriedIn())
                .as("a category that carries nothing is not haunted by January in either "
                        + "direction: it did not keep the forty and it is not chased for the sixty")
                .isEqualByComparingTo("0.00");
        assertReads(rowOf(third, clean), "100.00", "0.00", "100.00", "0.00", "100.00");
        assertThat(rowOf(third, saved).carriedIn())
                .as("the forgiving rule hands on nothing rather than a debt: February was allowed "
                        + "a hundred and forty and cost a hundred and sixty, so there is no "
                        + "surplus to follow them — and the overspend does not follow them either")
                .isEqualByComparingTo("0.00");
        assertReads(rowOf(third, saved), "100.00", "0.00", "100.00", "0.00", "100.00");
        assertThat(rowOf(third, envelope).carriedIn())
                .as("and the envelope carries the twenty it went over, because that consequence is "
                        + "the whole reason anybody keeps one — the surplus and the overspend are "
                        + "one rule and not two")
                .isEqualByComparingTo("-20.00");
        assertReads(rowOf(third, envelope), "100.00", "-20.00", "80.00", "0.00", "80.00");

        assertThat(rowOf(third, envelope).allowed())
                .as("the three rules were given the same figure and the same spending, and they "
                        + "part company by exactly what the customer chose")
                .isLessThan(rowOf(third, saved).allowed());
        assertThat(rowOf(third, saved).allowed())
                .as("while the forgiving rule and the clean slate agree about a month that went "
                        + "over, and differ only about one that did not")
                .isEqualByComparingTo(rowOf(third, clean).allowed());

        assertReads(rowOf(first, clean), "100.00", "0.00", "100.00", "60.00", "40.00");
        assertReads(rowOf(first, saved), "100.00", "0.00", "100.00", "60.00", "40.00");
        assertThat(rowOf(first, envelope).rollover())
                .as("and every month already gone still quotes the budget, the rule and the carry "
                        + "that stood in it, because nothing here was stored and nothing was closed")
                .isEqualTo(ENVELOPE);
    }

    @Test
    void an_envelope_overfilled_leaves_the_next_month_allowing_less_than_nothing() {
        SpendingCategoryView holiday = app.declareACategoryFor(theCustomer, "Holiday");
        app.declareABudgetFor(theCustomer, holiday.categoryId(), "100.00", ENVELOPE);

        YearMonth overfilled = YearMonth.from(app.theDateTheClockReads());
        app.spendFor(theCustomer, "Flights", "250.00", holiday.categoryId());
        assertReads(rowOf(overfilled, holiday), "100.00", "0.00", "100.00", "250.00", "-150.00");

        YearMonth next = overfilled.plusMonths(1);
        windTo(next.atDay(1));

        CategorySpendingView row = rowOf(next, holiday);
        assertThat(row.carriedIn())
                .as("a hundred and fifty euros over is a hundred and fifty euros carried, which is "
                        + "the only rule under which a month can begin with less than its budget")
                .isEqualByComparingTo("-150.00");
        assertThat(row.allowed())
                .as("and what this month allows is therefore less than nothing — a real state "
                        + "reported as the negative figure it is, not an error and not rounded up "
                        + "to nought, because rounding it up would remove the mechanic and leave "
                        + "the word")
                .isEqualByComparingTo("-50.00");
        assertThat(row.spent())
                .as("nothing has been spent in it")
                .isEqualByComparingTo("0.00");
        assertThat(row.left())
                .as("so what is left is exactly what arrived: fifty euros in the red before a "
                        + "single purchase")
                .isEqualByComparingTo("-50.00");
        assertThat(row.overspent())
                .as("and the month is over before it has begun, which is the answer a customer "
                        + "asked for when they chose an envelope")
                .isTrue();
    }

    /**
     * The five figures a month is worth checking, asserted together, because "budgeted, carried in,
     * allowed, spent, left" is only worth reading if all five agree — a helper that checked one at a
     * time would let a month pass with a budget from one row and a subtraction from another.
     */
    private static void assertReads(CategorySpendingView row, String budgeted, String carriedIn,
                                    String allowed, String spent, String left) {
        assertThat(row.budgeted()).as(row.name() + " was allowed EUR " + budgeted).isEqualByComparingTo(budgeted);
        assertThat(row.carriedIn())
                .as(row.name() + " was handed EUR " + carriedIn + " by the months before it")
                .isEqualByComparingTo(carriedIn);
        assertThat(row.allowed())
                .as(row.name() + " therefore allows EUR " + allowed + ", which is the budget and "
                        + "the carry together")
                .isEqualByComparingTo(allowed);
        assertThat(row.spent()).as(row.name() + " cost EUR " + spent).isEqualByComparingTo(spent);
        assertThat(row.left())
                .as(row.name() + " leaves EUR " + left + ", which is what it allows less what it "
                        + "cost")
                .isEqualByComparingTo(left);
    }

    /** One category's row out of one month, named by the month rather than by whatever the clock reads. */
    private static CategorySpendingView rowOf(YearMonth month, SpendingCategoryView category) {
        return app.spendingInMonthOf(theCustomer, month.toString())
                .theCategory(category.categoryId());
    }

    /**
     * The same purchase in each of the three categories, so that the only thing that differs between
     * them is the rule. Three spends rather than one split three ways, because a split is a fact
     * about one purchase and these are three separate claims on one balance.
     */
    private static void spendOnEachOf(String name, String amount,
                                      SpendingCategoryView... categories) {
        for (SpendingCategoryView category : categories) {
            app.spendFor(theCustomer, name + " on " + category.name(), amount,
                    category.categoryId());
        }
    }

    /**
     * Moves the clock to a day, counted through the calendar rather than by a fixed span. The clock
     * only goes forward and only in whole days, which is exactly the way a trainer moves it — and a
     * fixed thirty would land in the wrong month twice a year.
     */
    private static void windTo(LocalDate day) {
        long days = ChronoUnit.DAYS.between(app.theDateTheClockReads(), day);
        assertThat(days).describedAs("the clock only goes forward").isPositive();
        app.daysPass(days);
        assertThat(app.theDateTheClockReads()).isEqualTo(day);
    }
}
