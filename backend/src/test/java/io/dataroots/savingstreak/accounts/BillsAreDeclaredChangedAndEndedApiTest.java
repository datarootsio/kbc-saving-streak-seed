package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * A customer says what leaves their current account every month, says several of them, changes one
 * when the rent goes up or a provider moves its collection date, and ends one they no longer pay.
 *
 * <p>User stories 1 to 8. The account is the one thing in this application that nothing else was
 * competing for, and this is the sentence that gives the saving decision something to be measured
 * against. Nothing is debited here: a declared bill sits and waits, and the slice after this one
 * takes it.
 *
 * <p>Read back off the account's own page-read as well as taken from the answer, because they are
 * two different claims: that the application said yes, and that it wrote down what it said yes to —
 * in the list the page actually draws.
 */
class BillsAreDeclaredChangedAndEndedApiTest extends ApiIntegrationTest {

    private static final String THE_RENT = "900.00";
    private static final String THE_PHONE = "25.50";

    private AnAccountWithBills account;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountWithBills(http, "declared-changed-ended");
    }

    @Test
    void an_account_nobody_has_declared_a_bill_against_has_an_empty_list_rather_than_a_refusal() {
        assertThat(account.standingBills())
                .as("nothing goes out of an account nobody has said anything about, which is an "
                        + "answer about an account that exists rather than an absence")
                .isEmpty();
        assertThat(account.theAccount().bills()).isEmpty();
        assertThat(account.endedBills()).isEmpty();
    }

    @Test
    void a_declared_bill_is_what_the_account_reports_back() {
        RecurringBillView declared = account.declares("Rent", "1", THE_RENT);

        assertThat(declared.name()).isEqualTo("Rent");
        assertThat(declared.dayOfMonth()).isEqualTo(1);
        assertThat(declared.amount()).isEqualByComparingTo(THE_RENT);
        assertThat(declared.state()).isEqualTo("STANDING");
        assertThat(declared.endedAt())
                .as("a bill that stands has not ended, and the moment says so rather than the "
                        + "reader having to infer it")
                .isNull();
        assertThat(declared.declaredAt()).isNotNull();
        assertThat(declared.currentAccountId()).isEqualTo(account.id());

        assertThat(account.theAccount().bills())
                .as("and the page's own read of the account is what carries it, because that is the "
                        + "list a customer actually looks at")
                .extracting(RecurringBillView::name, RecurringBillView::dayOfMonth)
                .containsExactly(tuple("Rent", 1));
    }

    @Test
    void an_account_carries_several_bills_at_once_and_a_second_does_not_displace_the_first() {
        account.declares("Rent", "1", THE_RENT);
        account.declares("Energy", "8", "120.00");
        account.declares("Phone", "15", THE_PHONE);

        assertThat(account.standingBills())
                .as("a household has a rent and a phone bill and an energy bill, which is the one "
                        + "shape a bill does not share with a monthly income")
                .extracting(RecurringBillView::name)
                .containsExactly("Rent", "Energy", "Phone");
        assertThat(account.theAccount().bills())
                .as("in the order they were declared, which is the order the customer recognises "
                        + "their own list in")
                .extracting(RecurringBillView::name)
                .containsExactly("Rent", "Energy", "Phone");
    }

    @Test
    void a_bills_amount_can_be_changed_on_its_own_when_the_rent_goes_up() {
        RecurringBillView rent = account.declares("Rent", "1", THE_RENT);

        RecurringBillView raised = account.changes(rent.billId(), null, null, "975.00");

        assertThat(raised.amount()).isEqualByComparingTo("975.00");
        assertThat(raised.name())
                .as("the fields nobody named are left exactly as they were, which is what makes "
                        + "putting the rent up one edit rather than a retyped form")
                .isEqualTo("Rent");
        assertThat(raised.dayOfMonth()).isEqualTo(1);
        assertThat(onlyBillOf(account).amount()).isEqualByComparingTo("975.00");
    }

    @Test
    void a_bills_day_can_be_changed_on_its_own_when_a_provider_moves_its_collection_date() {
        RecurringBillView phone = account.declares("Phone", "15", THE_PHONE);

        RecurringBillView moved = account.changes(phone.billId(), null, "20", null);

        assertThat(moved.dayOfMonth()).isEqualTo(20);
        assertThat(moved.name()).isEqualTo("Phone");
        assertThat(moved.amount()).isEqualByComparingTo(THE_PHONE);
        assertThat(onlyBillOf(account).dayOfMonth()).isEqualTo(20);
    }

    @Test
    void a_bills_name_can_be_changed_on_its_own() {
        RecurringBillView energy = account.declares("Enrgy", "8", "120.00");

        RecurringBillView renamed = account.changes(energy.billId(), "Energy", null, null);

        assertThat(renamed.name()).isEqualTo("Energy");
        assertThat(renamed.dayOfMonth()).isEqualTo(8);
        assertThat(renamed.amount()).isEqualByComparingTo("120.00");
        assertThat(onlyBillOf(account).name()).isEqualTo("Energy");
    }

    @Test
    void a_bill_declared_on_the_thirty_first_is_accepted() {
        RecurringBillView lastDay = account.declares("Insurance", "31", "42.00");

        assertThat(lastDay.dayOfMonth())
                .as("a real rent is taken on the last day of the month and a customer says the "
                        + "31st; refusing it would make that bill undeclarable, and the slice that "
                        + "takes it is what clamps February")
                .isEqualTo(31);
        assertThat(onlyBillOf(account).dayOfMonth()).isEqualTo(31);
    }

    @Test
    void ending_a_bill_takes_it_off_the_standing_list_and_puts_it_among_the_ended() {
        RecurringBillView rent = account.declares("Rent", "1", THE_RENT);
        account.declares("Phone", "15", THE_PHONE);

        RecurringBillView ended = account.ends(rent.billId());

        assertThat(ended.state()).isEqualTo("ENDED");
        assertThat(ended.endedAt()).isNotNull();
        assertThat(ended.name())
                .as("what it was called, when it went out and what it cost are the whole reason "
                        + "ending is a closing rather than a deletion")
                .isEqualTo("Rent");
        assertThat(ended.amount()).isEqualByComparingTo(THE_RENT);

        assertThat(account.standingBills())
                .extracting(RecurringBillView::name)
                .as("it has left the list of what is about to go out")
                .containsExactly("Phone");
        assertThat(account.theAccount().bills())
                .extracting(RecurringBillView::name)
                .containsExactly("Phone");
        assertThat(account.endedBills())
                .extracting(RecurringBillView::name)
                .as("and it is readable among the bills this customer used to pay")
                .containsExactly("Rent");
    }

    @Test
    void declaring_a_bill_moves_no_money_by_itself() {
        BigDecimal before = account.theAccount().balance();

        account.declares("Rent", "1", THE_RENT);

        assertThat(account.theAccount().balance())
                .as("a bill is a sentence about the future; the money leaves when the job that "
                        + "takes it runs, and not a moment sooner")
                .isEqualByComparingTo(before);
    }

    /**
     * The one bill on an account that has exactly one, read back off the page's own read rather than
     * off the answer to the request that changed it — which is the second of the two claims every
     * test here makes.
     */
    private static RecurringBillView onlyBillOf(AnAccountWithBills account) {
        List<RecurringBillView> standing = account.theAccount().bills();
        assertThat(standing).hasSize(1);
        return standing.get(0);
    }
}
