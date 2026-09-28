package io.dataroots.savingstreak.rolloverandenvelopes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.CategorySpendingView;
import io.dataroots.savingstreak.support.MonthlyBudgetView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Changing what happens to the difference applies from now rather than backwards, exactly as
 * changing the figure does: the months already gone go on carrying under the rule that stood in
 * them.
 *
 * <p>User story 27, and the reason the rule is a column on a budget rather than one on a category. A
 * customer who turns Groceries into an envelope in the third month is saying what the third month
 * does with its difference; they are not saying that the first two had been envelopes all along. A
 * rule written over in place would hand them a surplus they had explicitly declined to keep, or a
 * debt they were never told they were running — and either way their history would rearrange itself
 * the moment they changed their mind.
 *
 * <p><strong>One mechanism, and that is the whole claim.</strong> Changing the rule ends the
 * standing row at the month before this one and opens a new one effective this month, which is
 * precisely what changing the amount already did. There is no second path, no second state and no
 * "rule history" beside the budget history: the fold simply reads whichever row covered the month it
 * was asked about.
 *
 * <p>An application of its own, because months pass by winding the clock and that cannot be undone.
 */
class ChangingTheRolloverRuleAppliesFromNowRatherThanBackwardsApiTest extends ApiIntegrationTest {

    private static final String NOTHING = "NOTHING_ROLLS_OVER";

    private static final String SURPLUS = "THE_SURPLUS_ROLLS_OVER";

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-rule-changed"));
        theCustomer = app.aCustomerOfItsOwn("a rule changed");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_rule_changed_in_the_second_month_leaves_the_first_carrying_nothing() {
        SpendingCategoryView groceries = app.declareACategoryFor(theCustomer, "Groceries");
        YearMonth first = YearMonth.from(app.theDateTheClockReads());
        MonthlyBudgetView stoodFirst = app.declareABudgetFor(theCustomer, groceries.categoryId(),
                "100.00", NOTHING);

        assertThat(stoodFirst.rollover())
                .as("the rule is half of what was declared and comes straight back, so a page that "
                        + "has just changed one draws it without fetching a month to find out")
                .isEqualTo(NOTHING);
        app.spendFor(theCustomer, "Delhaize", "20.00", groceries.categoryId());
        assertThat(rowOf(first, groceries).left()).isEqualByComparingTo("80.00");

        YearMonth second = first.plusMonths(1);
        windTo(second.atDay(1));

        MonthlyBudgetView stoodSecond = app.declareABudgetFor(theCustomer, groceries.categoryId(),
                "100.00", SURPLUS);

        assertThat(stoodSecond.amount())
                .as("the figure has not moved: the only thing this customer changed is what "
                        + "becomes of the difference")
                .isEqualByComparingTo("100.00");
        assertThat(stoodSecond.rollover()).isEqualTo(SURPLUS);
        assertThat(stoodSecond.effectiveFrom())
                .as("and the new row takes effect in the month it was named in, which is what a "
                        + "customer means by changing their rule — the same supersession a change "
                        + "of amount goes through, because it is the same one row")
                .isEqualTo(second.toString());
        assertThat(stoodSecond.stoodThrough())
                .as("a figure in force has no last month")
                .isNull();
        assertThat(stoodSecond.budgetId())
                .as("a new row rather than the old one written over, which is the only way the "
                        + "month before it can still be read as it was")
                .isNotEqualTo(stoodFirst.budgetId());

        CategorySpendingView theFirstMonth = rowOf(first, groceries);
        assertThat(theFirstMonth.rollover())
                .as("the month already gone quotes the rule that stood in it and not the one "
                        + "standing now")
                .isEqualTo(NOTHING);
        assertThat(theFirstMonth.left())
                .as("and every figure in it is exactly as it was")
                .isEqualByComparingTo("80.00");

        CategorySpendingView theSecondMonth = rowOf(second, groceries);
        assertThat(theSecondMonth.rollover()).isEqualTo(SURPLUS);
        assertThat(theSecondMonth.carriedIn())
                .as("nothing arrives in this month, because what a month is handed is decided by "
                        + "the rule that stood in the month before it — the eighty euros were let "
                        + "go under a rule the customer really did hold at the time")
                .isEqualByComparingTo("0.00");
        assertThat(theSecondMonth.allowed()).isEqualByComparingTo("100.00");

        app.spendFor(theCustomer, "Colruyt", "30.00", groceries.categoryId());

        YearMonth third = second.plusMonths(1);
        windTo(third.atDay(1));

        assertThat(rowOf(third, groceries).carriedIn())
                .as("and from the month the rule was named in onwards it does its work: seventy "
                        + "euros left over in a month that really was governed by it")
                .isEqualByComparingTo("70.00");
        assertThat(rowOf(third, groceries).allowed()).isEqualByComparingTo("170.00");
        assertThat(rowOf(first, groceries).carriedIn())
                .as("with the months behind it untouched, which is what makes a history what "
                        + "happened rather than what its owner currently intends")
                .isEqualByComparingTo("0.00");
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
