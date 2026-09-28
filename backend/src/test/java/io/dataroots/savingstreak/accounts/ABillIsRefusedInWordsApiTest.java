package io.dataroots.savingstreak.accounts;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Everything this application will not accept as a recurring bill comes back with the status
 * somebody chose and a sentence that says what is wrong.
 *
 * <p>User story 37. A refusal is only useful if the person who caused it can read why, so every one
 * of these asserts the words as well as the status — the reason travels in {@code detail}, which is
 * the field the frontend shows unchanged.
 *
 * <p>The amount's two rules are quoted from {@code AmountOfMoney} rather than restated in this
 * module, which is why the sentences here are the sentences a customer has already met on a deposit
 * and on a declared income. Asserting on them is asserting that the quoting actually happened.
 *
 * <p>Nothing is left standing afterwards, which is the other half of a refusal: a request the
 * application said no to must not have changed anything.
 */
class ABillIsRefusedInWordsApiTest extends ApiIntegrationTest {

    /** The cap this application puts on one account, quoted so the test says what it is asserting. */
    private static final int THE_MOST_ONE_ACCOUNT_CAN_CARRY = 20;

    private AnAccountWithBills account;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountWithBills(http, "refused-in-words");
    }

    @Test
    void a_bill_with_a_blank_name_is_refused_because_a_customer_could_not_tell_it_from_another() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("   ", "1", "900.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("a name is the whole reason a bill is a row rather than a figure")
                .contains("name");
        assertThat(account.standingBills()).isEmpty();
    }

    @Test
    void a_bill_with_no_name_at_all_is_refused_the_same_way() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare(null, "1", "900.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused)).contains("name");
        assertThat(account.standingBills()).isEmpty();
    }

    @Test
    void a_bill_worth_nothing_is_refused_because_it_is_not_an_amount_of_money() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("Rent", "1", "0.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .contains("more than zero")
                .contains("0.00");
        assertThat(account.standingBills()).isEmpty();
    }

    @Test
    void a_bill_worth_less_than_nothing_is_refused_the_same_way() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("Rent", "1", "-900.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused)).contains("more than zero");
        assertThat(account.standingBills()).isEmpty();
    }

    @Test
    void a_bill_carrying_a_third_decimal_place_is_refused_rather_than_rounded() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("Rent", "1", "900.123");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("rounding would move an amount nobody typed, and a bank that quietly decides "
                        + "what a figure was meant to say is worse than one that asks")
                .contains("two decimal places")
                .contains("900.123");
        assertThat(account.standingBills()).isEmpty();
    }

    @Test
    void a_day_of_the_month_no_month_has_is_refused_in_a_sentence_that_says_the_range() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("Rent", "32", "900.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .contains("between 1 and 31")
                .contains("32");
        assertThat(detailOf(refused))
                .as("and it says what the 31st means, because that is the answer somebody who "
                        + "typed 32 was probably reaching for")
                .contains("last day");
        assertThat(account.standingBills()).isEmpty();
    }

    @Test
    void a_day_below_the_first_of_the_month_is_refused_the_same_way() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("Rent", "0", "900.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused)).contains("between 1 and 31").contains("0");
        assertThat(account.standingBills()).isEmpty();
    }

    @Test
    void a_day_that_is_not_a_number_at_all_is_named_back_to_whoever_typed_it() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("Rent", "the 1st", "900.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("a person who typed words has to see the words to see the mistake")
                .contains("the 1st");
        assertThat(account.standingBills()).isEmpty();
    }

    @Test
    void an_amount_written_the_belgian_way_is_named_back_rather_than_rejected_as_unreadable_json() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("Rent", "1", "900,00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .contains("900,00")
                .contains("full stop");
        assertThat(account.standingBills()).isEmpty();
    }

    @Test
    void a_twenty_first_standing_bill_is_refused_and_ending_one_makes_room_for_another() {
        for (int each = 1; each <= THE_MOST_ONE_ACCOUNT_CAN_CARRY; each++) {
            account.declares("Bill " + each, "1", "10.00");
        }

        ResponseEntity<JsonNode> refused = account.tryToDeclare("One too many", "1", "10.00");

        assertThat(refused.getStatusCode())
                .as("a bad request rather than a conflict: what the customer does next is end one "
                        + "they no longer pay and send this again")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .contains(String.valueOf(THE_MOST_ONE_ACCOUNT_CAN_CARRY))
                .as("the sentence quotes the limit, so there is nothing left to work out")
                .contains("End one");
        assertThat(account.standingBills()).hasSize(THE_MOST_ONE_ACCOUNT_CAN_CARRY);

        account.ends(account.standingBills().get(0).billId());

        assertThat(account.declares("One too many", "1", "10.00").name())
                .as("an ended bill is not on the page and nothing will take it, so it costs neither "
                        + "of the things the cap protects — ending one makes room")
                .isEqualTo("One too many");
        assertThat(account.standingBills()).hasSize(THE_MOST_ONE_ACCOUNT_CAN_CARRY);
    }

    @Test
    void an_ended_bill_cannot_be_changed() {
        RecurringBillView rent = account.declares("Rent", "1", "900.00");
        account.ends(rent.billId());

        ResponseEntity<JsonNode> refused =
                account.tryToChange(account.id(), rent.billId(), null, null, "950.00");

        assertThat(refused.getStatusCode())
                .as("a conflict rather than a bad request: the request was perfectly well formed "
                        + "and it is the state of the bill that will not allow it")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(refused)).contains("Rent").contains("ended");
        assertThat(account.endedBills().get(0).amount())
                .as("and the record is untouched, which is the whole point of keeping it")
                .isEqualByComparingTo("900.00");
    }

    @Test
    void an_ended_bill_cannot_be_ended_again() {
        RecurringBillView rent = account.declares("Rent", "1", "900.00");
        account.ends(rent.billId());

        ResponseEntity<JsonNode> refused = account.tryToEnd(account.id(), rent.billId());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(refused)).contains("ended");
        assertThat(account.endedBills()).hasSize(1);
    }

    @Test
    void a_change_that_says_nothing_is_refused_rather_than_answered_with_an_untouched_bill() {
        RecurringBillView rent = account.declares("Rent", "1", "900.00");

        ResponseEntity<JsonNode> refused =
                account.tryToChange(account.id(), rent.billId(), null, null, null);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("answering 200 to it would tell a page its edit went through")
                .contains("Say what to change");
    }

    @Test
    void a_bill_on_somebody_elses_account_cannot_be_changed_or_ended_through_your_own() {
        AnAccountWithBills somebodyElse = new AnAccountWithBills(http, "refused-not-yours");
        RecurringBillView theirRent = somebodyElse.declares("Their rent", "1", "900.00");

        ResponseEntity<JsonNode> changeRefused =
                account.tryToChange(account.id(), theirRent.billId(), null, null, "1.00");

        assertThat(changeRefused.getStatusCode())
                .as("a bill is only ever found through the account in the path, so somebody else's "
                        + "is a bill that is not there — telling a customer it exists but belongs "
                        + "to another account would be telling them about another account")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(changeRefused))
                .contains("no bill " + theirRent.billId())
                .contains("current account " + account.id());

        ResponseEntity<JsonNode> endRefused = account.tryToEnd(account.id(), theirRent.billId());
        assertThat(endRefused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(somebodyElse.standingBills()).hasSize(1);
        assertThat(somebodyElse.standingBills().get(0).amount())
                .as("and their rent is exactly where they left it")
                .isEqualByComparingTo("900.00");
    }

    @Test
    void a_bill_declared_against_an_account_nobody_has_heard_of_is_refused_in_the_words_accounts_owns() {
        long noSuchAccount = account.anIdNoCurrentAccountHas();

        ResponseEntity<JsonNode> refused =
                account.tryToDeclareOn(noSuchAccount, "Rent", "1", "900.00");

        assertThat(refused.getStatusCode())
                .as("an account nobody has heard of is a 404 before any rule about bills is reached")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused)).isEqualTo("There is no current account " + noSuchAccount + ".");
    }

    /**
     * The reason, as the person who caused the refusal reads it. RFC 9457 puts it in {@code detail},
     * and that is the field the frontend renders unchanged.
     */
    private static String detailOf(ResponseEntity<JsonNode> refused) {
        assertThat(refused.getBody())
                .describedAs("a refusal carries a problem detail")
                .isNotNull();
        return refused.getBody().get("detail").asText();
    }
}
