package io.dataroots.savingstreak.billsincategories;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BillInACategoryView;
import io.dataroots.savingstreak.support.RecurringBillView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Everything this application will not do with a bill's category, and the words it says so in.
 *
 * <p>User story 44. A refusal is only worth having if the person who typed the request can act on
 * it, and the three things that can be wrong here send them to three different places: the account,
 * the bill list and the category list. A single sentence covering all three would send two of every
 * three customers to look in the wrong one.
 *
 * <p>Every refusal is asserted to have changed nothing as well as to have been refused, because a
 * request that half took is worse than one that was refused at all: the label is what every figure
 * the rest of this feature derives is grouped by.
 */
class ABillsCategoryIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private AnAccountWithBillsInCategories account;

    private RecurringBillView rent;

    private SpendingCategoryView housing;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountWithBillsInCategories(http, "refused-in-words");
        rent = account.declaresABill("Rent", "1", "900.00");
        housing = account.declaresACategory("Housing");
    }

    @Test
    void a_current_account_nobody_has_heard_of_is_refused_before_any_rule_about_bills_is_reached() {
        long noSuchAccount = account.anIdNoCurrentAccountHas();

        ResponseEntity<JsonNode> refused = account.tryToPutBillInCategory(noSuchAccount,
                rent.billId(), housing.categoryId());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().get("detail").asText())
                .as("every module answering for a current account says its absence in the one "
                        + "sentence Accounts owns")
                .isEqualTo("There is no current account " + noSuchAccount + ".");

        assertThat(account.tryToReadTheBillCategoriesOf(noSuchAccount).getStatusCode())
                .as("an account nobody has heard of is refused rather than answered with the empty "
                        + "list of an account whose bills are simply filed nowhere")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void a_bill_that_is_not_on_this_account_is_refused_by_the_bill_rather_than_by_the_category() {
        long noSuchBill = rent.billId() + 10_000;

        ResponseEntity<JsonNode> refused = account.tryToPutBillInCategory(account.id(), noSuchBill,
                housing.categoryId());

        assertThat(refused.getStatusCode())
                .as("a bill that is not there is about something that is not there")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().get("detail").asText())
                .as("the sentence names the bill, so that somebody told about a category they did "
                        + "not name is not sent to look through the wrong list")
                .isEqualTo("There is no bill " + noSuchBill + " on current account " + account.id()
                        + ".");
        assertThat(account.theCategoryEachBillIsIn()).isEmpty();
    }

    @Test
    void a_bill_on_somebody_elses_account_answers_as_a_bill_that_is_not_there() {
        AnAccountWithBillsInCategories somebodyElse =
                new AnAccountWithBillsInCategories(http, "refused-somebody-else");
        RecurringBillView theirRent = somebodyElse.declaresABill("Their rent", "3", "750.00");

        ResponseEntity<JsonNode> refused = account.tryToPutBillInCategory(account.id(),
                theirRent.billId(), housing.categoryId());

        assertThat(refused.getStatusCode())
                .as("telling a customer that a bill exists but belongs to another account would be "
                        + "telling them about another account")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(somebodyElse.theCategoryEachBillIsIn()).isEmpty();
    }

    @Test
    void a_category_that_is_not_on_this_account_is_refused_in_the_categories_own_words() {
        long noSuchCategory = housing.categoryId() + 10_000;

        ResponseEntity<JsonNode> refused = account.tryToPutBillInCategory(account.id(),
                rent.billId(), noSuchCategory);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().get("detail").asText())
                .as("the same mistake whether it was made while renaming a category or while "
                        + "filing a bill under one, so it is the same sentence")
                .isEqualTo("There is no category " + noSuchCategory + " on current account "
                        + account.id() + ".");
        assertThat(account.theCategoryEachBillIsIn()).isEmpty();
    }

    @Test
    void a_request_naming_no_category_at_all_is_answered_in_this_applications_own_words() {
        ResponseEntity<JsonNode> refused = account.tryToPutBillInCategory(account.id(),
                rent.billId(), null);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("detail").asText())
                .as("taking a bill out of every category is a request that says what it means, "
                        + "rather than this one with nothing in it")
                .contains("Say which category this bill belongs in");
    }

    @Test
    void an_ended_bills_category_can_still_be_read_but_not_changed() {
        account.putsBillInCategory(rent.billId(), housing.categoryId());
        SpendingCategoryView utilities = account.declaresACategory("Utilities");
        account.endsTheBill(rent.billId());

        assertThat(account.theCategoryEachBillIsIn())
                .as("ending a bill is a closing rather than a deletion, and the months it was taken "
                        + "for stay explained — which they would not be if the label went with it")
                .extracting(BillInACategoryView::billId, BillInACategoryView::categoryName)
                .containsExactly(tuple(rent.billId(), "Housing"));

        ResponseEntity<JsonNode> refused = account.tryToPutBillInCategory(account.id(),
                rent.billId(), utilities.categoryId());

        assertThat(refused.getStatusCode())
                .as("the request was perfectly well formed and it is the state of the bill that "
                        + "will not allow it, which is what a conflict says")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("detail").asText())
                .contains("Rent")
                .contains("ended")
                .contains("still readable");
        assertThat(account.theCategoryEachBillIsIn())
                .as("a record that could be re-labelled afterwards is not a record")
                .extracting(BillInACategoryView::categoryName)
                .containsExactly("Housing");
    }

    @Test
    void an_ended_bill_cannot_be_taken_out_of_the_category_it_was_in_either() {
        account.putsBillInCategory(rent.billId(), housing.categoryId());
        account.endsTheBill(rent.billId());

        ResponseEntity<JsonNode> refused = account.tryToTakeBillOutOfEveryCategory(account.id(),
                rent.billId());

        assertThat(refused.getStatusCode())
                .as("taking the label off is a change like any other, and a bill that is over is "
                        + "not changed")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(account.theCategoryEachBillIsIn()).hasSize(1);
    }
}
