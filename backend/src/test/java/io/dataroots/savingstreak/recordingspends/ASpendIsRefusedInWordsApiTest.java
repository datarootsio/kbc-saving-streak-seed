package io.dataroots.savingstreak.recordingspends;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.recordingspends.AnAccountThatSpends.Part;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every spend this application will not record, refused in words the person who typed it can act on
 * — and with the balance exactly where they left it.
 *
 * <p>User stories 13, 16 and 44. A refusal here is not a failure of the request: the answer is no,
 * the person who asked is the one who can act on it, and the sentence is the whole of what they get.
 * So each of these asserts the words as well as the status, and every one of them asserts that
 * nothing moved — a refusal that took the money anyway would be the worst outcome in this feature
 * and the one no status code would reveal.
 *
 * <p><strong>The two most likely to be got wrong are here deliberately</strong>: a split whose parts
 * come to a cent more and a cent less than the spend, and a spend one cent larger than the balance.
 * Both are arithmetic nobody notices going wrong, and both are the sort of mistake an application
 * would otherwise paper over by rounding or by taking what is there.
 */
class ASpendIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private AnAccountThatSpends account;

    private SpendingCategoryView groceries;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountThatSpends(http, "refused");
        groceries = account.declares("Groceries");
    }

    @Test
    void a_split_a_cent_over_what_was_spent_is_refused_and_nothing_moves() {
        BigDecimal before = account.balance();

        ResponseEntity<JsonNode> refused = account.tryToSpend("Delhaize", "50.00",
                Part.of(groceries.categoryId(), "30.00"), Part.unfiled("20.01"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("the application cannot quietly lose euros for somebody, so it says which two "
                        + "figures disagree")
                .contains("50.01")
                .contains("50.00");
        assertThat(account.balance()).isEqualByComparingTo(before);
        assertThat(account.recentSpends()).isEmpty();
    }

    @Test
    void a_split_a_cent_under_what_was_spent_is_refused_and_nothing_moves() {
        BigDecimal before = account.balance();

        ResponseEntity<JsonNode> refused = account.tryToSpend("Delhaize", "50.00",
                Part.of(groceries.categoryId(), "30.00"), Part.unfiled("19.99"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused)).contains("49.99").contains("50.00");
        assertThat(account.balance()).isEqualByComparingTo(before);
        assertThat(account.recentSpends()).isEmpty();
    }

    @Test
    void an_eleventh_part_is_refused_in_words() {
        BigDecimal before = account.balance();

        Part[] elevenWays = new Part[11];
        for (int part = 0; part < elevenWays.length; part++) {
            elevenWays[part] = Part.unfiled("1.00");
        }
        ResponseEntity<JsonNode> refused = account.tryToSpend("A very long receipt", "11.00",
                elevenWays);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("the limit is quoted, so there is nothing left for the customer to work out")
                .contains("10");
        assertThat(account.balance()).isEqualByComparingTo(before);
    }

    @Test
    void a_spend_one_cent_larger_than_the_balance_moves_nothing_and_records_nothing() {
        BigDecimal balance = account.balance();
        BigDecimal aCentMore = balance.add(new BigDecimal("0.01"));

        ResponseEntity<JsonNode> refused = account.tryToSpend("More than I have",
                aCentMore.toPlainString(), Part.unfiled(aCentMore.toPlainString()));

        assertThat(refused.getStatusCode())
                .as("the account is in no unexpected state; there is simply less in it than was "
                        + "asked for")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("the refusal names what was asked for and what was there, because the gap is "
                        + "what the customer has to decide about")
                .contains(aCentMore.toPlainString())
                .contains(balance.setScale(2).toPlainString());
        assertThat(account.balance())
                .as("no partial spend and no overdraft: a customer is never taught that going below "
                        + "zero is free")
                .isEqualByComparingTo(balance);
        assertThat(account.recentSpends())
                .as("nothing was recorded either, so there is no row to explain away later")
                .isEmpty();
    }

    @Test
    void a_spend_with_no_name_is_refused_in_words() {
        ResponseEntity<JsonNode> refused = account.tryToSpend(" ", "10.00", Part.unfiled("10.00"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("a list of spends should read like somebody's week rather than like a bank "
                        + "statement, and only a name does that")
                .containsIgnoringCase("name");
        assertThat(account.recentSpends()).isEmpty();
    }

    @Test
    void an_amount_that_is_not_an_amount_of_money_is_refused_naming_what_was_typed() {
        ResponseEntity<JsonNode> refused = account.tryToSpend("Delhaize", "25,00",
                Part.unfiled("25.00"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("a person who typed a comma has to see the comma to see the mistake")
                .contains("25,00");
        assertThat(account.recentSpends()).isEmpty();
    }

    @Test
    void an_amount_of_no_value_is_refused_in_words() {
        ResponseEntity<JsonNode> refused = account.tryToSpend("Nothing at all", "0.00",
                Part.unfiled("0.00"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused)).containsIgnoringCase("more than zero");
        assertThat(account.recentSpends()).isEmpty();
    }

    @Test
    void an_amount_quoted_more_finely_than_money_is_refused_rather_than_rounded() {
        ResponseEntity<JsonNode> refused = account.tryToSpend("Three decimal places", "12.505",
                Part.unfiled("12.505"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("a bank that quietly decides what a figure was meant to say is worse than one "
                        + "that asks")
                .containsIgnoringCase("two decimal places");
        assertThat(account.recentSpends()).isEmpty();
    }

    @Test
    void a_spend_with_no_parts_at_all_is_refused_in_words() {
        ResponseEntity<JsonNode> refused = account.tryToSpend("Unsplit", "10.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("one part is enough and it does not have to name a category, so the sentence "
                        + "says so rather than demanding one")
                .containsIgnoringCase("category");
        assertThat(account.recentSpends()).isEmpty();
    }

    @Test
    void a_part_naming_a_category_that_is_not_on_this_account_is_refused_in_words() {
        long noSuchCategory = account.anIdNoCategoryHas();
        BigDecimal before = account.balance();

        ResponseEntity<JsonNode> refused = account.tryToSpend("Filed under nothing real", "10.00",
                Part.of(noSuchCategory, "10.00"));

        assertThat(refused.getStatusCode())
                .as("an identifier for something that is not there is what a 404 says, and it is "
                        + "the categories' own answer rather than a second copy of it")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused)).contains(String.valueOf(noSuchCategory));
        assertThat(account.balance()).isEqualByComparingTo(before);
        assertThat(account.recentSpends()).isEmpty();
    }

    @Test
    void a_part_naming_an_ended_category_is_refused_in_words() {
        SpendingCategoryView over = account.declares("Something I stopped");
        account.ends(over.categoryId());
        BigDecimal before = account.balance();

        ResponseEntity<JsonNode> refused = account.tryToSpend("Filed under a closed word", "10.00",
                Part.of(over.categoryId(), "10.00"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(refused))
                .as("an ended category is a record rather than a word still in use, and the "
                        + "sentence says to declare it again")
                .contains("Something I stopped");
        assertThat(account.balance()).isEqualByComparingTo(before);
        assertThat(account.recentSpends()).isEmpty();
    }

    @Test
    void a_part_worth_nothing_is_refused_rather_than_dropped() {
        ResponseEntity<JsonNode> refused = account.tryToSpend("Half of it worth nothing", "10.00",
                Part.of(groceries.categoryId(), "10.00"), Part.unfiled("0.00"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused)).containsIgnoringCase("more than zero");
        assertThat(account.recentSpends()).isEmpty();
    }

    @Test
    void a_spend_against_an_account_nobody_has_heard_of_is_refused_before_any_rule_about_spends() {
        long noSuchAccount = account.anIdNoCurrentAccountHas();

        ResponseEntity<JsonNode> refused = account.tryToSpendOn(noSuchAccount, "Delhaize", "10.00",
                Part.unfiled("10.00"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(account.tryToReadTheSpendsOf(noSuchAccount).getStatusCode())
                .as("an empty list would tell somebody that an account they do not hold simply has "
                        + "nothing on it")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void a_request_with_no_body_at_all_is_answered_in_this_applications_own_words() {
        ResponseEntity<JsonNode> refused = account.tryToSpendNothingAtAll();

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("a body that did not arrive is a fact about the request, and it still deserves "
                        + "a sentence about spends")
                .containsIgnoringCase("spend");
    }

    /** The sentence the application came back with, which is the whole of what a customer gets. */
    private static String detailOf(ResponseEntity<JsonNode> refused) {
        JsonNode body = refused.getBody();
        assertThat(body).describedAs("a refusal carries words").isNotNull();
        assertThat(body.get("detail")).describedAs("as an RFC 9457 problem detail").isNotNull();
        return body.get("detail").asText();
    }
}
