package io.dataroots.savingstreak.accounts;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Everything this application will not accept as a monthly income comes back with the status
 * somebody chose and a sentence that says what is wrong.
 *
 * <p>User story 9 read against the income half of the feature. A refusal is only useful if the
 * person who caused it can read why, so every one of these asserts the words as well as the status —
 * the reason travels in {@code detail}, which is the field the frontend shows unchanged.
 *
 * <p>The amount's two rules are quoted from {@code AmountOfMoney} rather than restated in this
 * module, which is why the sentences here are the sentences a customer has already met on a deposit.
 * Asserting on them is asserting that the quoting actually happened.
 *
 * <p>Nothing is left declared afterwards, which is the other half of a refusal: a request the
 * application said no to must not have changed anything.
 */
class AnIncomeIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private AnAccountWithAnIncome account;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountWithAnIncome(http, "refused-in-words");
    }

    @Test
    void an_income_of_nothing_is_refused_because_it_is_not_an_amount_of_money() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("25", "0.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .contains("more than zero")
                .contains("0.00");
        assertThat(account.income().declared())
                .as("a refused declaration declares nothing")
                .isFalse();
    }

    @Test
    void an_income_of_less_than_nothing_is_refused_the_same_way() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("25", "-100.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused)).contains("more than zero");
        assertThat(account.income().declared()).isFalse();
    }

    @Test
    void an_income_carrying_a_third_decimal_place_is_refused_rather_than_rounded() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("25", "2500.123");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("rounding would move an amount nobody typed, and a bank that quietly decides "
                        + "what a figure was meant to say is worse than one that asks")
                .contains("two decimal places")
                .contains("2500.123");
        assertThat(account.income().declared()).isFalse();
    }

    @Test
    void a_day_of_the_month_no_month_has_is_refused_in_a_sentence_that_says_the_range() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("32", "2500.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .contains("between 1 and 31")
                .contains("32")
                .as("and it says what to do instead, because the 31st is the answer somebody "
                        + "reaching for 32 usually wanted")
                .contains("last day of a shorter month");
        assertThat(account.income().declared()).isFalse();
    }

    @Test
    void a_day_of_the_month_below_the_first_is_refused_too() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("0", "2500.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused)).contains("between 1 and 31");
        assertThat(account.income().declared()).isFalse();
    }

    @Test
    void an_amount_written_with_a_comma_is_named_back_to_whoever_typed_it() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("25", "2.500,00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("a person who typed a comma has to see the comma to see the mistake")
                .contains("2.500,00")
                .contains("not an amount of money");
        assertThat(account.income().declared()).isFalse();
    }

    @Test
    void a_day_that_is_not_a_number_at_all_is_named_back_the_same_way() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("the 25th", "2500.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .contains("the 25th")
                .contains("not a day of the month");
        assertThat(account.income().declared()).isFalse();
    }

    @Test
    void an_income_declared_against_an_account_nobody_has_is_told_so_in_the_words_accounts_owns() {
        long noSuchAccount = account.anIdNoCurrentAccountHas();

        ResponseEntity<JsonNode> refused = account.tryToDeclareOn(noSuchAccount, "25", "2500.00");

        assertThat(refused.getStatusCode())
                .as("an identifier for something that is not there, which is what a 404 says — and "
                        + "it is decided before any rule about income is reached")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused)).isEqualTo("There is no current account " + noSuchAccount + ".");
    }

    /** The sentence a refusal carries, which is the field the frontend shows unchanged. */
    private static String detailOf(ResponseEntity<JsonNode> refused) {
        assertThat(refused.getBody())
                .describedAs("a refusal answers in one shape (RFC 9457) carrying its reason")
                .isNotNull();
        return refused.getBody().get("detail").asText();
    }
}
