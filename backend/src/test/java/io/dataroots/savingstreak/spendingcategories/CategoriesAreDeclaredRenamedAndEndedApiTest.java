package io.dataroots.savingstreak.spendingcategories;

import java.math.BigDecimal;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import io.dataroots.savingstreak.support.TheCurrentAccountView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer names the things their money goes on, names several of them, renames one they chose
 * badly, and ends one they no longer use — and what they ended stays readable.
 *
 * <p>User stories 1 to 6. Until now every euro that left a current account left as a named standing
 * bill or as a deposit the customer chose to make: there was no groceries, no fuel and no round of
 * drinks, and therefore nothing a customer could be wrong about in the way people are actually
 * wrong about money. This is the sentence that gives the rest of the feature something to count
 * against, and nothing in it moves a cent.
 *
 * <p>Read back off the list the page draws as well as taken from the answer to the request, because
 * they are two different claims: that the application said yes, and that it wrote down what it said
 * yes to.
 */
class CategoriesAreDeclaredRenamedAndEndedApiTest extends ApiIntegrationTest {

    private AnAccountWithCategories account;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountWithCategories(http, "declared-renamed-ended");
    }

    @Test
    void an_account_nobody_has_named_a_category_on_has_an_empty_list_rather_than_a_refusal() {
        assertThat(account.standingCategories())
                .as("an account whose holder has not described their spending yet is an account "
                        + "that exists, which is a different answer from one that does not")
                .isEmpty();
        assertThat(account.endedCategories()).isEmpty();
    }

    @Test
    void a_declared_category_comes_back_with_an_identifier_and_is_what_the_account_reports() {
        SpendingCategoryView groceries = account.declares("Groceries");

        assertThat(groceries.categoryId())
                .as("everything a customer does to a category afterwards names it, so declaring one "
                        + "has to hand back the identifier that names it")
                .isPositive();
        assertThat(groceries.name()).isEqualTo("Groceries");
        assertThat(groceries.currentAccountId()).isEqualTo(account.id());
        assertThat(groceries.state()).isEqualTo("STANDING");
        assertThat(groceries.declaredAt()).isNotNull();
        assertThat(groceries.endedAt())
                .as("a category that stands has not ended, and the moment says so rather than the "
                        + "reader having to infer it")
                .isNull();

        assertThat(account.standingCategories())
                .extracting(SpendingCategoryView::name)
                .containsExactly("Groceries");
    }

    @Test
    void an_account_carries_several_categories_at_once_in_the_order_they_were_named() {
        account.declares("Groceries");
        account.declares("Fuel");
        account.declares("Going out");

        assertThat(account.standingCategories())
                .as("telling groceries from fuel from going out is the whole reason a category is a "
                        + "row rather than a figure, and the order a customer wrote them in is the "
                        + "order they recognise their own list in")
                .extracting(SpendingCategoryView::name)
                .containsExactly("Groceries", "Fuel", "Going out");
    }

    @Test
    void a_category_can_be_renamed_while_it_stands() {
        SpendingCategoryView badlyNamed = account.declares("Grocerys");

        SpendingCategoryView renamed = account.renames(badlyNamed.categoryId(), "Groceries");

        assertThat(renamed.name()).isEqualTo("Groceries");
        assertThat(renamed.categoryId())
                .as("renaming a category is not opening another one: what was spent under it is "
                        + "still spent under it")
                .isEqualTo(badlyNamed.categoryId());
        assertThat(renamed.state()).isEqualTo("STANDING");
        assertThat(account.standingCategories())
                .as("a name chosen badly does not follow the customer around")
                .extracting(SpendingCategoryView::name)
                .containsExactly("Groceries");
    }

    @Test
    void a_name_is_trimmed_rather_than_kept_with_the_spaces_somebody_typed() {
        SpendingCategoryView padded = account.declares("  Going out  ");

        assertThat(padded.name())
                .as("judging and tidying are the same pass, exactly as they are for a bill's name")
                .isEqualTo("Going out");
        assertThat(account.standingCategories())
                .extracting(SpendingCategoryView::name)
                .containsExactly("Going out");
    }

    @Test
    void ending_a_category_takes_it_off_the_standing_list_and_puts_it_among_the_ended() {
        SpendingCategoryView fuel = account.declares("Fuel");
        account.declares("Groceries");

        SpendingCategoryView ended = account.ends(fuel.categoryId());

        assertThat(ended.state()).isEqualTo("ENDED");
        assertThat(ended.endedAt()).isNotNull();
        assertThat(ended.name())
                .as("what it was called is the whole reason ending is a closing rather than a "
                        + "deletion: the months it was spent under stay readable")
                .isEqualTo("Fuel");

        assertThat(account.standingCategories())
                .as("the list stays short without erasing what was spent under it")
                .extracting(SpendingCategoryView::name)
                .containsExactly("Groceries");
        assertThat(account.endedCategories())
                .as("and the ended ones are readable separately, which is what keeps the months it "
                        + "was live for readable too")
                .extracting(SpendingCategoryView::name)
                .containsExactly("Fuel");
    }

    @Test
    void the_same_name_may_stand_on_a_different_current_account() {
        AnAccountWithCategories somebodyElse =
                new AnAccountWithCategories(http, "declared-somewhere-else");
        account.declares("Groceries");

        SpendingCategoryView theirs = somebodyElse.declares("Groceries");

        assertThat(theirs.name())
                .as("two households both buy groceries, and a name is unique to an account rather "
                        + "than to this application")
                .isEqualTo("Groceries");
        assertThat(theirs.currentAccountId()).isEqualTo(somebodyElse.id());
        assertThat(account.standingCategories()).hasSize(1);
        assertThat(somebodyElse.standingCategories()).hasSize(1);
    }

    @Test
    void a_name_may_be_declared_again_once_the_first_one_has_been_ended() {
        SpendingCategoryView first = account.declares("Groceries");
        account.ends(first.categoryId());

        SpendingCategoryView second = account.declares("Groceries");

        assertThat(second.categoryId())
                .as("a category declared again is a new category rather than the old one brought "
                        + "back: ending is one-way, and what was spent under the first stays there")
                .isNotEqualTo(first.categoryId());
        assertThat(account.standingCategories())
                .extracting(SpendingCategoryView::name)
                .containsExactly("Groceries");
        assertThat(account.endedCategories())
                .extracting(SpendingCategoryView::name)
                .containsExactly("Groceries");
    }

    @Test
    void naming_a_category_moves_no_money_at_all() {
        BigDecimal before = theBalanceOf(account);

        account.declares("Groceries");
        account.declares("Fuel");

        assertThat(theBalanceOf(account))
                .as("a category is a name for money that leaves, not money leaving; what takes the "
                        + "money is a spend recorded against it")
                .isEqualByComparingTo(before);
    }

    /** What is in the account, read the way the page reads it. */
    private BigDecimal theBalanceOf(AnAccountWithCategories account) {
        ResponseEntity<TheCurrentAccountView> read = http.getForEntity(
                "/api/current-accounts/{id}", TheCurrentAccountView.class, account.id());
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read.getBody().balance();
    }
}
