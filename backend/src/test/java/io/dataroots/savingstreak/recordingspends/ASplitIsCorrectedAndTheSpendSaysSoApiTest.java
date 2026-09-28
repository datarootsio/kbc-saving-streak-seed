package io.dataroots.savingstreak.recordingspends;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import io.dataroots.savingstreak.recordingspends.AnAccountThatSpends.Part;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.SpendPartView;
import io.dataroots.savingstreak.support.SpendView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

/**
 * A customer fixes a spend they filed wrongly, or files one they left unfiled — and the spend says
 * plainly afterwards that it was not right the first time.
 *
 * <p>User stories 18, 19, 20 and 43. A category got wrong is not a small mistake in this feature: it
 * is the one input every figure downstream is derived from, so one bad split poisons a category's
 * month, the carry into the next one and the comparison against the months before it. Correcting the
 * split is how a customer un-poisons all of it at once, which is why the correction replaces the
 * whole split rather than patching a part of it.
 *
 * <p><strong>What a correction may not touch is asserted here as loudly as what it may.</strong>
 * The amount stands, the name stands, and the spend cannot be deleted: the money moved, and a record
 * that can be unmade is not a record. The only thing about a spend that was ever an opinion is which
 * categories it belongs to.
 *
 * <p>Every claim about the money is made against the balance, as it is for recording one. A
 * correction moves nothing, and the way to say that in a test is to read the balance before and
 * after rather than to assert that no withdrawal was written.
 */
class ASplitIsCorrectedAndTheSpendSaysSoApiTest extends ApiIntegrationTest {

    private AnAccountThatSpends account;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountThatSpends(http, "corrected");
    }

    @Test
    void a_spend_recorded_unfiled_is_filed_afterwards_and_the_rushed_entry_becomes_a_good_record() {
        SpendingCategoryView groceries = account.declares("Groceries");
        SpendView inAHurry = account.spends("Corner shop", "20.00", Part.unfiled("20.00"));

        SpendView filed = account.corrects(inAHurry.spendId(),
                Part.of(groceries.categoryId(), "20.00"));

        assertThat(filed.parts()).singleElement()
                .as("recording a spend at all should never be the hard part, so putting a category "
                        + "on it later is what turns a rushed entry into a good record")
                .satisfies(part -> {
                    assertThat(part.categoryId()).isEqualTo(groceries.categoryId());
                    assertThat(part.categoryName()).isEqualTo("Groceries");
                    assertThat(part.amount()).isEqualByComparingTo("20.00");
                });
    }

    @Test
    void a_split_is_moved_wholesale_from_one_category_to_another() {
        SpendingCategoryView groceries = account.declares("Groceries for moving");
        SpendingCategoryView repairs = account.declares("Car repairs");
        SpendView filedWrongly = account.spends("Garage", "180.00",
                Part.of(groceries.categoryId(), "180.00"));

        SpendView putRight = account.corrects(filedWrongly.spendId(),
                Part.of(repairs.categoryId(), "150.00"),
                Part.of(groceries.categoryId(), "30.00"));

        assertThat(putRight.parts())
                .as("the correction replaces the whole split at once, in the order it was typed, "
                        + "rather than adding a part to what was there")
                .extracting(SpendPartView::categoryName, SpendPartView::amount)
                .containsExactly(
                        tuple("Car repairs", new BigDecimal("150.00")),
                        tuple("Groceries for moving", new BigDecimal("30.00")));
        assertThat(account.recentSpends().get(0).parts())
                .as("what the list reads back is what the correction answered with, because the old "
                        + "parts are gone rather than kept beside the new ones")
                .hasSize(2);
    }

    @Test
    void a_split_may_be_corrected_back_to_carrying_no_category_at_all() {
        SpendingCategoryView fuel = account.declares("Fuel I was not sure about");
        SpendView filed = account.spends("Q8", "60.00", Part.of(fuel.categoryId(), "60.00"));

        SpendView unfiled = account.corrects(filed.spendId(), Part.unfiled("60.00"));

        assertThat(unfiled.parts()).singleElement()
                .as("uncategorised is a state a customer chooses rather than a gap, so a correction "
                        + "may arrive at it as well as leave it")
                .satisfies(part -> {
                    assertThat(part.categoryId()).isNull();
                    assertThat(part.categoryName()).isNull();
                });
    }

    @Test
    void a_corrected_spend_carries_the_moment_it_was_corrected_and_an_uncorrected_one_carries_none() {
        SpendingCategoryView goingOut = account.declares("Going out");
        SpendView recorded = account.spends("Round of drinks", "18.00", Part.unfiled("18.00"));

        assertThat(recorded.correctedAt())
                .as("a spend filed right the first time carries nothing there rather than a "
                        + "stand-in date, so the ledger never pretends it was corrected")
                .isNull();

        SpendView corrected = account.corrects(recorded.spendId(),
                Part.of(goingOut.categoryId(), "18.00"));

        assertThat(corrected.correctedAt())
                .as("the ledger does not pretend somebody got it right the first time")
                .isNotNull();
        assertThat(corrected.recordedAt())
                .as("when the money left is a fact about the money and does not move when an "
                        + "opinion about it is corrected")
                .isEqualTo(recorded.recordedAt());
        assertThat(account.recentSpends().get(0).correctedAt())
                .as("the moment is on the spend rather than only on the answer to the correction, "
                        + "so the list says it too")
                .isEqualTo(corrected.correctedAt());
    }

    @Test
    void a_spend_already_corrected_can_be_corrected_again_and_the_moment_moves() throws Exception {
        SpendingCategoryView groceries = account.declares("Groceries twice over");
        SpendingCategoryView goingOut = account.declares("Going out twice over");
        SpendView recorded = account.spends("Delhaize", "40.00", Part.unfiled("40.00"));

        SpendView once = account.corrects(recorded.spendId(),
                Part.of(groceries.categoryId(), "40.00"));
        // The clock is read to the millisecond and both corrections are one request apart, so two
        // made inside one tick would carry the same moment and say nothing about whether the second
        // one moved it. A pause is the honest way to ask that question of a real clock.
        Thread.sleep(5);
        SpendView twice = account.corrects(recorded.spendId(),
                Part.of(goingOut.categoryId(), "25.00"),
                Part.of(groceries.categoryId(), "15.00"));

        assertThat(twice.parts())
                .as("a correction is not one-way: somebody who gets it wrong twice is somebody who "
                        + "has to be able to put it right twice")
                .extracting(SpendPartView::categoryName)
                .containsExactly("Going out twice over", "Groceries twice over");
        assertThat(twice.correctedAt())
                .as("the moment says when it was last put right, which is the one a reader of the "
                        + "ledger is asking about")
                .isAfter(once.correctedAt());
    }

    @Test
    void correcting_a_spend_moves_no_money_at_all() {
        SpendingCategoryView groceries = account.declares("Groceries that cost nothing more");
        SpendView spent = account.spends("Delhaize", "42.50", Part.unfiled("42.50"));
        BigDecimal afterTheSpend = account.balance();

        account.corrects(spent.spendId(), Part.of(groceries.categoryId(), "42.50"));

        assertThat(account.balance())
                .as("a category is a word for money that left, not money leaving: correcting one "
                        + "moves nothing and the balance is exactly where the customer left it")
                .isEqualByComparingTo(afterTheSpend);
    }

    @Test
    void a_correction_leaves_the_amount_and_the_name_exactly_as_they_were() {
        SpendingCategoryView groceries = account.declares("Groceries by name");
        SpendView spent = account.spends("Delhaize on the corner", "42.50", Part.unfiled("42.50"));

        SpendView corrected = account.corrects(spent.spendId(),
                Part.of(groceries.categoryId(), "42.50"));

        assertThat(corrected.name())
                .as("the name stands: there is no field in a correction for it and no endpoint "
                        + "that would take one")
                .isEqualTo("Delhaize on the corner");
        assertThat(corrected.amount())
                .as("the amount stands, because the money moved")
                .isEqualByComparingTo("42.50");
        assertThat(corrected.spendId()).isEqualTo(spent.spendId());
    }

    @Test
    void a_part_may_be_filed_under_a_category_declared_after_the_spend_was_recorded() {
        SpendView recorded = account.spends("Something new", "15.00", Part.unfiled("15.00"));

        SpendingCategoryView afterwards = account.declares("A word I thought of later");
        SpendView filed = account.corrects(recorded.spendId(),
                Part.of(afterwards.categoryId(), "15.00"));

        assertThat(filed.parts()).singleElement()
                .as("a customer who names the category after recording the spend is exactly the "
                        + "customer this correction exists for")
                .satisfies(part ->
                        assertThat(part.categoryName()).isEqualTo("A word I thought of later"));
    }

    @Test
    void there_is_no_endpoint_to_change_a_spends_amount_or_name_and_none_to_delete_one() {
        SpendView spent = account.spends("It stands", "30.00", Part.unfiled("30.00"));

        assertThat(account.tryTo(HttpMethod.DELETE, spent.spendId()).getStatusCode().is2xxSuccessful())
                .as("a record that can be unmade is not a record, so there is nothing here to "
                        + "delete a spend with")
                .isFalse();
        assertThat(account.tryTo(HttpMethod.PUT, spent.spendId()).getStatusCode().is2xxSuccessful())
                .as("the amount and the name are facts about money that moved, so there is nothing "
                        + "here to rewrite them with either")
                .isFalse();
        assertThat(account.tryTo(HttpMethod.PATCH, spent.spendId()).getStatusCode().is2xxSuccessful())
                .isFalse();

        SpendView stillThere = account.recentSpends().get(0);
        assertThat(stillThere.spendId()).isEqualTo(spent.spendId());
        assertThat(stillThere.name()).isEqualTo("It stands");
        assertThat(stillThere.amount()).isEqualByComparingTo("30.00");
        assertThat(stillThere.correctedAt())
                .as("none of those was a correction either, so the spend still says it was right "
                        + "the first time")
                .isNull();
    }

    @Test
    void a_correction_on_one_spend_leaves_every_other_spend_exactly_as_it_was() {
        SpendingCategoryView groceries = account.declares("Groceries left alone");
        SpendingCategoryView fuel = account.declares("Fuel left alone");
        SpendView monday = account.spends("Monday", "10.00", Part.of(fuel.categoryId(), "10.00"));
        SpendView tuesday = account.spends("Tuesday", "20.00", Part.unfiled("20.00"));

        account.corrects(tuesday.spendId(), Part.of(groceries.categoryId(), "20.00"));

        SpendView mondayAfterwards = account.recentSpends().stream()
                .filter(spend -> spend.spendId() == monday.spendId())
                .findFirst()
                .orElseThrow();
        assertThat(mondayAfterwards.correctedAt())
                .as("the parts of one spend are the parts of that spend, and a correction that "
                        + "reached another one would be rewriting a record nobody asked about")
                .isNull();
        assertThat(mondayAfterwards.parts()).singleElement()
                .satisfies(part ->
                        assertThat(part.categoryName()).isEqualTo("Fuel left alone"));
    }

    @Test
    void the_moment_of_a_correction_is_the_applications_clock_rather_than_the_wall_clock() {
        SpendingCategoryView groceries = account.declares("Groceries on the clock");
        SpendView recorded = account.spends("Delhaize", "11.00", Part.unfiled("11.00"));
        Instant clockReads = http.getForObject("/api/dev/clock", ClockView.class).now();

        SpendView corrected = account.corrects(recorded.spendId(),
                Part.of(groceries.categoryId(), "11.00"));

        assertThat(corrected.correctedAt())
                .as("nothing in this application stamps a row from the wall clock, because the "
                        + "development clock can be wound years forward")
                .isCloseTo(clockReads, within(Duration.ofMinutes(1)));
    }
}
