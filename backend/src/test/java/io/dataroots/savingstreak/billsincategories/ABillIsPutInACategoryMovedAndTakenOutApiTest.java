package io.dataroots.savingstreak.billsincategories;

import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BillInACategoryView;
import io.dataroots.savingstreak.support.RecurringBillView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * A customer says which category each of their standing bills belongs to, moves one they filed
 * wrongly, and takes another out of every category again.
 *
 * <p>User story 21. Until now a category could only ever hold what a customer chose to spend, and
 * the rent — the largest thing that leaves a current account every month — sat outside the picture
 * entirely. This is the slice that makes a category's month whole: what a household is committed to
 * and what it chooses are described in the same words.
 *
 * <p>Read back off the list the page draws as well as taken from the answer to the request, because
 * they are two different claims: that the application said yes, and that it wrote down what it said
 * yes to.
 *
 * <p><strong>Nothing here touches the bill.</strong> The bill's own read is asserted after every
 * label is put on, moved and taken off, because the whole design of this slice is that the label is
 * a row in another module keyed on the bill — and a bill that changed when it was filed would mean
 * the cycle this feature refuses to close had been closed after all.
 */
class ABillIsPutInACategoryMovedAndTakenOutApiTest extends ApiIntegrationTest {

    private AnAccountWithBillsInCategories account;

    private RecurringBillView rent;

    private SpendingCategoryView housing;

    private SpendingCategoryView utilities;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountWithBillsInCategories(http, "filed-moved-taken-out");
        rent = account.declaresABill("Rent", "1", "900.00");
        housing = account.declaresACategory("Housing");
        utilities = account.declaresACategory("Utilities");
    }

    @Test
    void an_account_whose_bills_are_filed_nowhere_has_an_empty_list_rather_than_a_refusal() {
        assertThat(account.theCategoryEachBillIsIn())
                .as("a bill nobody has filed is a bill with no row, and an account of them is an "
                        + "account that exists — which is a different answer from one that does not")
                .isEmpty();
    }

    @Test
    void a_bill_put_in_a_category_comes_back_naming_it_and_is_what_the_account_reports() {
        BillInACategoryView filed = account.putsBillInCategory(rent.billId(), housing.categoryId());

        assertThat(filed.billId()).isEqualTo(rent.billId());
        assertThat(filed.currentAccountId()).isEqualTo(account.id());
        assertThat(filed.categoryId()).isEqualTo(housing.categoryId());
        assertThat(filed.categoryName())
                .as("a list of identifiers is a list nobody can act on, so the word travels with "
                        + "the label")
                .isEqualTo("Housing");
        assertThat(filed.categoryState()).isEqualTo("STANDING");
        assertThat(filed.filedAt()).isNotNull();

        assertThat(account.theCategoryEachBillIsIn())
                .extracting(BillInACategoryView::billId, BillInACategoryView::categoryId)
                .containsExactly(tuple(rent.billId(), housing.categoryId()));
    }

    @Test
    void filing_a_bill_leaves_the_bill_itself_exactly_as_it_was() {
        account.putsBillInCategory(rent.billId(), housing.categoryId());

        assertThatTheBillIsUntouched(rent.billId());
    }

    @Test
    void a_bill_can_be_moved_from_one_category_to_another_and_is_in_one_of_them_only() {
        account.putsBillInCategory(rent.billId(), housing.categoryId());

        BillInACategoryView moved = account.putsBillInCategory(rent.billId(),
                utilities.categoryId());

        assertThat(moved.categoryId()).isEqualTo(utilities.categoryId());
        assertThat(moved.categoryName()).isEqualTo("Utilities");
        assertThat(account.theCategoryEachBillIsIn())
                .as("a bill is never split: putting it somewhere moves the one label rather than "
                        + "adding a second, and the whole of that bill's history is re-labelled "
                        + "with it")
                .extracting(BillInACategoryView::billId, BillInACategoryView::categoryId)
                .containsExactly(tuple(rent.billId(), utilities.categoryId()));
    }

    @Test
    void putting_a_bill_where_it_already_is_changes_nothing_and_is_not_refused() {
        BillInACategoryView first = account.putsBillInCategory(rent.billId(), housing.categoryId());

        BillInACategoryView again = account.putsBillInCategory(rent.billId(), housing.categoryId());

        assertThat(again.categoryId())
                .as("a PUT is the whole of a fact put at an address that names it, so sending it "
                        + "twice is the same request made twice")
                .isEqualTo(housing.categoryId());
        assertThat(again.filedAt().truncatedTo(ChronoUnit.MILLIS))
                .as("it was not moved, so the moment it was filed is the moment it was filed")
                // To the millisecond, because the first answer carries the moment the application
                // stamped and the second carries it back from SQLite, which keeps three decimal
                // places of a second and not six. The claim is about the moment, not about the
                // precision a file format has.
                .isEqualTo(first.filedAt().truncatedTo(ChronoUnit.MILLIS));
        assertThat(account.theCategoryEachBillIsIn()).hasSize(1);
    }

    @Test
    void a_bill_can_be_taken_out_of_every_category_again() {
        account.putsBillInCategory(rent.billId(), housing.categoryId());

        BillInACategoryView out = account.takesBillOutOfEveryCategory(rent.billId());

        assertThat(out.billId()).isEqualTo(rent.billId());
        assertThat(out.categoryId())
                .as("the answer names the bill and says plainly that there is now no category "
                        + "behind it, rather than handing back nothing and leaving the page to "
                        + "assume")
                .isNull();
        assertThat(out.categoryName()).isNull();
        assertThat(account.theCategoryEachBillIsIn())
                .as("a bill in no category has no row at all, which is what the page draws as not "
                        + "being in one")
                .isEmpty();
        assertThatTheBillIsUntouched(rent.billId());
    }

    @Test
    void taking_a_bill_out_of_a_category_it_was_never_in_does_nothing_and_is_not_refused() {
        BillInACategoryView out = account.takesBillOutOfEveryCategory(rent.billId());

        assertThat(out.categoryId())
                .as("pressing the same button twice is a customer saying the same thing twice")
                .isNull();
        assertThat(account.theCategoryEachBillIsIn()).isEmpty();
    }

    @Test
    void several_bills_sit_in_several_categories_at_once_on_one_account() {
        RecurringBillView energy = account.declaresABill("Energy", "8", "120.00");
        RecurringBillView phone = account.declaresABill("Phone", "15", "25.00");

        account.putsBillInCategory(rent.billId(), housing.categoryId());
        account.putsBillInCategory(energy.billId(), utilities.categoryId());
        account.putsBillInCategory(phone.billId(), utilities.categoryId());

        assertThat(account.theCategoryEachBillIsIn())
                .as("what a category has committed to it is more than one bill, and the order is "
                        + "the order the bills were declared in — the order the page already draws "
                        + "them in")
                .extracting(BillInACategoryView::billId, BillInACategoryView::categoryName)
                .containsExactly(
                        tuple(rent.billId(), "Housing"),
                        tuple(energy.billId(), "Utilities"),
                        tuple(phone.billId(), "Utilities"));
    }

    @Test
    void renaming_the_bill_leaves_it_exactly_where_it_was_filed() {
        account.putsBillInCategory(rent.billId(), housing.categoryId());

        account.renamesTheBill(rent.billId(), "Rent, the new flat");

        assertThat(account.theCategoryEachBillIsIn())
                .as("the label is on the thing rather than on what it was called that month, which "
                        + "is why the row names the bill by identifier and copies nothing")
                .extracting(BillInACategoryView::billId, BillInACategoryView::categoryName)
                .containsExactly(tuple(rent.billId(), "Housing"));
    }

    @Test
    void renaming_the_category_is_what_the_label_says_afterwards() {
        account.putsBillInCategory(rent.billId(), housing.categoryId());

        account.renamesTheCategory(housing.categoryId(), "Home");

        assertThat(account.theCategoryEachBillIsIn())
                .as("renaming a category is not opening another one: everything filed under it "
                        + "stays filed under it and reads by the word the customer now uses")
                .extracting(BillInACategoryView::categoryId, BillInACategoryView::categoryName)
                .containsExactly(tuple(housing.categoryId(), "Home"));
    }

    /**
     * That the bill's own record says exactly what it said when it was declared.
     *
     * <p>The claim of half this class: the label is a row in the budgets module keyed on the bill,
     * the accounts module gains nothing and learns nothing, and a bill that changed when it was
     * filed would mean the cycle this feature exists to refuse had been closed after all.
     *
     * <p>Field by field rather than record against record, and the amount by its value rather than
     * by {@code equals}: the bill declared came back from the application that had just parsed
     * "900.00" and this one comes back from SQLite, which has no decimal type and hands 900.00 back
     * as 900. The scale is a fact about the file format; the claim here is about the bill.
     */
    private void assertThatTheBillIsUntouched(long billId) {
        RecurringBillView asItNowReads = account.standingBills().stream()
                .filter(bill -> bill.billId() == billId)
                .findFirst()
                .orElseThrow();
        assertThat(asItNowReads.name()).isEqualTo(rent.name());
        assertThat(asItNowReads.dayOfMonth()).isEqualTo(rent.dayOfMonth());
        assertThat(asItNowReads.amount()).isEqualByComparingTo(rent.amount());
        assertThat(asItNowReads.state()).isEqualTo("STANDING");
        assertThat(asItNowReads.endedAt()).isNull();
        assertThat(asItNowReads.lastTakenOn())
                .as("nothing moved: a category is a word for money that leaves, not money leaving")
                .isNull();
    }
}
