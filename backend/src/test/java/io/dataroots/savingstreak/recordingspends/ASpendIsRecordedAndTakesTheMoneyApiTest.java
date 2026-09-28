package io.dataroots.savingstreak.recordingspends;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.recordingspends.AnAccountThatSpends.Part;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.SpendPartView;
import io.dataroots.savingstreak.support.SpendView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * A customer records what they actually spent: the money leaves the current account there and then,
 * the spend says what it was for, and the recent ones read back newest first.
 *
 * <p>User stories 11, 12, 14, 15 and 17. Until now every euro that left a current account left as a
 * named standing bill or as a deposit the customer chose to make, so the balance they were deciding
 * to save from was a balance nobody could be wrong about. This is the sentence that makes it one
 * they can: the groceries, the fuel and the round of drinks are recorded, and the balance falls by
 * exactly what was recorded.
 *
 * <p><strong>Every claim about the money is made against the balance</strong> rather than against a
 * row, because the balance is what a customer sees and what every other figure in this application
 * is derived from. A test that asserted a row exists would be asserting that this module wrote
 * something down; asserting the balance fell is asserting that the money moved.
 */
class ASpendIsRecordedAndTakesTheMoneyApiTest extends ApiIntegrationTest {

    private AnAccountThatSpends account;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountThatSpends(http, "recorded");
    }

    @Test
    void a_spend_takes_its_amount_out_of_the_balance_there_and_then() {
        SpendingCategoryView groceries = account.declares("Groceries");
        BigDecimal before = account.balance();

        SpendView delhaize = account.spends("Delhaize", "42.50",
                Part.of(groceries.categoryId(), "42.50"));

        assertThat(delhaize.spendId())
                .as("everything a customer does to a spend afterwards names it, so recording one "
                        + "has to hand back the identifier that names it")
                .isPositive();
        assertThat(delhaize.name()).isEqualTo("Delhaize");
        assertThat(delhaize.amount()).isEqualByComparingTo("42.50");
        assertThat(delhaize.currentAccountId()).isEqualTo(account.id());
        assertThat(delhaize.recordedAt()).isNotNull();
        assertThat(account.balance())
                .as("a spend takes the money immediately, so the balance the customer is deciding "
                        + "to save from is the balance they really have")
                .isEqualByComparingTo(before.subtract(new BigDecimal("42.50")));
    }

    @Test
    void a_spend_is_split_across_the_categories_it_was_actually_for() {
        SpendingCategoryView groceries = account.declares("Groceries");
        SpendingCategoryView goingOut = account.declares("Going out");
        BigDecimal before = account.balance();

        SpendView supermarket = account.spends("Supermarket trip", "60.00",
                Part.of(groceries.categoryId(), "35.00"),
                Part.of(goingOut.categoryId(), "25.00"));

        assertThat(supermarket.parts())
                .as("a trip that was half food and half wine is recorded as what it was, in the "
                        + "order it was typed")
                .extracting(SpendPartView::categoryName, SpendPartView::amount)
                .containsExactly(
                        tuple("Groceries", new BigDecimal("35.00")),
                        tuple("Going out", new BigDecimal("25.00")));
        assertThat(account.balance())
                .as("one payment left the account, whatever it was split into")
                .isEqualByComparingTo(before.subtract(new BigDecimal("60.00")));
    }

    @Test
    void a_part_may_carry_no_category_at_all_and_a_spend_may_be_half_filed_and_half_not() {
        SpendingCategoryView groceries = account.declares("Groceries");

        SpendView inAHurry = account.spends("Corner shop", "50.00",
                Part.of(groceries.categoryId(), "30.00"),
                Part.unfiled("20.00"));

        assertThat(inAHurry.parts())
                .as("recording a spend at all should never be the hard part: thirty euros filed and "
                        + "twenty not is the honest record of somebody who was in a hurry")
                .extracting(SpendPartView::categoryId, SpendPartView::categoryName)
                .containsExactly(
                        tuple(groceries.categoryId(), "Groceries"),
                        tuple(null, null));
    }

    @Test
    void a_whole_spend_may_be_recorded_with_nothing_filed_under_anything() {
        BigDecimal before = account.balance();

        SpendView unfiled = account.spends("Something I will file later", "12.34",
                Part.unfiled("12.34"));

        assertThat(unfiled.parts()).singleElement()
                .as("a euro nobody has filed is still a euro that left the account")
                .satisfies(part -> {
                    assertThat(part.categoryId()).isNull();
                    assertThat(part.amount()).isEqualByComparingTo("12.34");
                });
        assertThat(account.balance()).isEqualByComparingTo(before.subtract(new BigDecimal("12.34")));
    }

    @Test
    void ten_parts_are_kept_and_the_money_still_matches_them_to_the_cent() {
        SpendingCategoryView groceries = account.declares("Groceries");
        BigDecimal before = account.balance();

        Part[] tenWays = new Part[10];
        for (int part = 0; part < tenWays.length; part++) {
            tenWays[part] = part % 2 == 0 ? Part.of(groceries.categoryId(), "1.00")
                    : Part.unfiled("1.00");
        }
        SpendView splitTenWays = account.spends("A long receipt", "10.00", tenWays);

        assertThat(splitTenWays.parts())
                .as("ten is the limit rather than the refusal, and the tenth part is kept")
                .hasSize(10);
        assertThat(account.balance()).isEqualByComparingTo(before.subtract(new BigDecimal("10.00")));
    }

    @Test
    void the_recent_spends_come_back_newest_first_each_carrying_its_split() {
        SpendingCategoryView fuel = account.declares("Fuel");
        account.spends("Monday", "10.00", Part.of(fuel.categoryId(), "10.00"));
        account.spends("Tuesday", "20.00", Part.unfiled("20.00"));
        account.spends("Wednesday", "30.00", Part.of(fuel.categoryId(), "30.00"));

        List<SpendView> recent = account.recentSpends();

        assertThat(recent)
                .as("\"what have I spent lately\" is answered with the last thing first")
                .extracting(SpendView::name)
                .containsExactly("Wednesday", "Tuesday", "Monday");
        assertThat(recent.get(0).parts()).singleElement()
                .satisfies(part -> assertThat(part.categoryName()).isEqualTo("Fuel"));
        assertThat(recent.get(1).parts()).singleElement()
                .satisfies(part -> assertThat(part.categoryName()).isNull());
    }

    @Test
    void an_account_nobody_has_spent_on_has_an_empty_list_rather_than_a_refusal() {
        assertThat(account.recentSpends())
                .as("an account whose holder has not recorded a spend yet is an account that "
                        + "exists, which is a different answer from one that does not")
                .isEmpty();
    }

    @Test
    void a_spend_counts_at_the_moment_the_applications_clock_reads_and_cannot_be_backdated() {
        ClockView clock = http.getForObject("/api/dev/clock", ClockView.class);

        SpendView today = account.spends("Whatever the clock says", "5.00", Part.unfiled("5.00"));

        LocalDate theClockReads = clock.now().atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                .toLocalDate();
        LocalDate theSpendCountsTo = today.recordedAt()
                .atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
        assertThat(theSpendCountsTo)
                .as("the day a spend counts to is the day the application's clock reads, and there "
                        + "is no field in the request for it to have come from instead")
                .isEqualTo(theClockReads);
    }

    @Test
    void a_category_ended_afterwards_still_says_what_a_spend_was_filed_under() {
        SpendingCategoryView goingOut = account.declares("Going out for a while");
        account.spends("Round of drinks", "18.00", Part.of(goingOut.categoryId(), "18.00"));

        account.ends(goingOut.categoryId());

        assertThat(account.recentSpends().get(0).parts()).singleElement()
                .as("ending a category leaves every euro ever filed under it exactly where it is, "
                        + "so the split still reads as what it was")
                .satisfies(part -> {
                    assertThat(part.categoryId()).isEqualTo(goingOut.categoryId());
                    assertThat(part.categoryName()).isEqualTo("Going out for a while");
                });
    }
}
