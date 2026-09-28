package io.dataroots.savingstreak.billsincategories;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BillInACategoryView;
import io.dataroots.savingstreak.support.RecurringBillView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * A customer ends a category their rent was filed under, and the rent is still filed under it — an
 * ended category, said out loud — rather than quietly turning up in no category at all.
 *
 * <p>User stories 4, 5 and 21 where they meet. Ending a category is a closing and not a deletion:
 * the months that were filed under it stay explained, and that promise is worth nothing if the
 * labels pointing at it are silently dropped when it is closed. A bill that unfiled itself would
 * rewrite what every month before the ending was filed as, which is the one thing a record must not
 * do.
 *
 * <p>The other half of it is what a customer can still do afterwards. The label is readable and the
 * bill can be moved somewhere that is still a word in use; what cannot happen is a <em>new</em> bill
 * being filed under a word its holder has stopped using, because that would be the ending undone by
 * the next request.
 */
class EndingACategoryLeavesItsBillsPointingAtAnEndedCategoryApiTest extends ApiIntegrationTest {

    private AnAccountWithBillsInCategories account;

    private RecurringBillView rent;

    private SpendingCategoryView housing;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountWithBillsInCategories(http, "ended-categories");
        rent = account.declaresABill("Rent", "1", "900.00");
        housing = account.declaresACategory("Housing");
        account.putsBillInCategory(rent.billId(), housing.categoryId());
    }

    @Test
    void the_bill_still_points_at_the_category_and_the_read_says_it_has_ended() {
        account.endsTheCategory(housing.categoryId());

        assertThat(account.theCategoryEachBillIsIn())
                .as("nothing is unpicked: the row stands, and what changed is that the word behind "
                        + "it is one its holder has stopped using")
                .extracting(BillInACategoryView::billId, BillInACategoryView::categoryId,
                        BillInACategoryView::categoryName, BillInACategoryView::categoryState)
                .containsExactly(tuple(rent.billId(), housing.categoryId(), "Housing", "ENDED"));
    }

    @Test
    void the_bill_can_be_moved_out_of_the_ended_category_into_one_that_still_stands() {
        account.endsTheCategory(housing.categoryId());
        SpendingCategoryView home = account.declaresACategory("Home");

        BillInACategoryView moved = account.putsBillInCategory(rent.billId(), home.categoryId());

        assertThat(moved.categoryId())
                .as("a customer who no longer likes where a bill sits moves it themselves, which is "
                        + "the honest alternative to the application deciding for them")
                .isEqualTo(home.categoryId());
        assertThat(moved.categoryState()).isEqualTo("STANDING");
    }

    @Test
    void the_bill_can_be_taken_out_of_the_ended_category_altogether() {
        account.endsTheCategory(housing.categoryId());

        BillInACategoryView out = account.takesBillOutOfEveryCategory(rent.billId());

        assertThat(out.categoryId()).isNull();
        assertThat(account.theCategoryEachBillIsIn()).isEmpty();
    }

    @Test
    void no_bill_can_be_put_into_the_ended_category_afterwards() {
        RecurringBillView energy = account.declaresABill("Energy", "8", "120.00");
        account.endsTheCategory(housing.categoryId());

        ResponseEntity<JsonNode> refused = account.tryToPutBillInCategory(account.id(),
                energy.billId(), housing.categoryId());

        assertThat(refused.getStatusCode())
                .as("the request is perfectly well formed and it is the state of the category that "
                        + "will not allow it, which is what a conflict says and a bad request does "
                        + "not")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("detail").asText())
                .as("an ended category is a record of months already gone rather than a word still "
                        + "in use, and the sentence says how to start using it again")
                .contains("Housing")
                .contains("ended")
                .contains("Declare it again");
        assertThat(account.theCategoryEachBillIsIn())
                .as("nothing was written: the rent is where it was and the energy is nowhere")
                .hasSize(1);
    }
}
