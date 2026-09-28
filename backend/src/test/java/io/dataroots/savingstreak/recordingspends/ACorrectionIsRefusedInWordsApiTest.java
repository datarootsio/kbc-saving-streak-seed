package io.dataroots.savingstreak.recordingspends;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.recordingspends.AnAccountThatSpends.Part;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SpendPartView;
import io.dataroots.savingstreak.support.SpendView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every correction this application will not make, refused in words the person who typed it can act
 * on — and with the spend still reading exactly as it did.
 *
 * <p>User stories 19 and 44. A correction is held to the same rules the original split was held to,
 * and that is the claim this class makes one rule at a time: the parts add up to what was spent, a
 * part may carry no category, there are at most ten of them, and none of them may name a category
 * that is not on this account. A correction that was allowed to break any of those would let a
 * customer reach, by correcting, a split the application would have refused to record.
 *
 * <p><strong>A refused correction leaves the old split standing.</strong> That is asserted as often
 * as the sentence is, because it is the outcome no status code would reveal: a correction that wiped
 * the parts and then refused the new ones would leave a spend with nothing filed under anything and
 * a balance nobody could explain.
 *
 * <p><strong>A spend on somebody else's account is refused in the same words as one that does not
 * exist</strong>, and that is the whole of what this application can say about whose a spend is. It
 * has no authentication to ask, so the only honest answer is the one that tells a guesser nothing.
 */
class ACorrectionIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private AnAccountThatSpends account;

    private SpendingCategoryView groceries;

    private SpendView spent;

    @BeforeEach
    void anAccountOfThisTestsOwnWithOneSpendOnIt() {
        account = new AnAccountThatSpends(http, "correction-refused");
        groceries = account.declares("Groceries");
        spent = account.spends("Delhaize", "50.00", Part.of(groceries.categoryId(), "50.00"));
    }

    @Test
    void a_corrected_split_a_cent_over_what_was_spent_is_refused_and_the_old_split_stands() {
        ResponseEntity<JsonNode> refused = account.tryToCorrect(spent.spendId(),
                Part.of(groceries.categoryId(), "30.00"), Part.unfiled("20.01"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("the invariant a correction is held to is the one the original was held to, so "
                        + "it says which two figures disagree in the same words")
                .contains("50.01")
                .contains("50.00");
        assertThat(theSplitNowOn(spent.spendId()))
                .as("a refused correction leaves the spend exactly as its holder left it")
                .hasSize(1);
    }

    @Test
    void a_corrected_split_a_cent_under_what_was_spent_is_refused_and_the_old_split_stands() {
        ResponseEntity<JsonNode> refused = account.tryToCorrect(spent.spendId(),
                Part.of(groceries.categoryId(), "30.00"), Part.unfiled("19.99"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused)).contains("49.99").contains("50.00");
        assertThat(theSplitNowOn(spent.spendId())).hasSize(1);
    }

    @Test
    void an_eleventh_part_in_a_correction_is_refused_in_words() {
        Part[] elevenWays = new Part[11];
        for (int part = 0; part < elevenWays.length; part++) {
            elevenWays[part] = Part.unfiled(part == 10 ? "40.00" : "1.00");
        }

        ResponseEntity<JsonNode> refused = account.tryToCorrect(spent.spendId(), elevenWays);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("the limit is quoted, so there is nothing left for the customer to work out")
                .contains("10");
        assertThat(theSplitNowOn(spent.spendId())).hasSize(1);
    }

    @Test
    void a_correction_with_no_parts_at_all_is_refused_rather_than_emptying_the_split() {
        ResponseEntity<JsonNode> refused = account.tryToCorrect(spent.spendId());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("one part is enough and it does not have to name a category, so the sentence "
                        + "says so rather than demanding one")
                .containsIgnoringCase("category");
        assertThat(theSplitNowOn(spent.spendId()))
                .as("a spend whose euros were filed under nothing at all would be a record of money "
                        + "that left for no reason")
                .hasSize(1);
    }

    @Test
    void a_correction_naming_a_category_that_is_not_on_this_account_is_refused_in_words() {
        long noSuchCategory = account.anIdNoCategoryHas();

        ResponseEntity<JsonNode> refused = account.tryToCorrect(spent.spendId(),
                Part.of(noSuchCategory, "50.00"));

        assertThat(refused.getStatusCode())
                .as("an identifier for something that is not there is what a 404 says, and it is "
                        + "the categories' own answer rather than a second copy of it")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused)).contains(String.valueOf(noSuchCategory));
        assertThat(theSplitNowOn(spent.spendId())).hasSize(1);
    }

    @Test
    void a_correction_naming_an_ended_category_is_refused_in_words() {
        SpendingCategoryView over = account.declares("Something I stopped");
        account.ends(over.categoryId());

        ResponseEntity<JsonNode> refused = account.tryToCorrect(spent.spendId(),
                Part.of(over.categoryId(), "50.00"));

        assertThat(refused.getStatusCode())
                .as("an ended category is a record of months already gone rather than a word still "
                        + "in use, which is a conflict rather than a typo")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(refused)).contains("Something I stopped");
        assertThat(theSplitNowOn(spent.spendId())).hasSize(1);
    }

    @Test
    void a_part_of_a_correction_worth_nothing_is_refused_rather_than_dropped() {
        ResponseEntity<JsonNode> refused = account.tryToCorrect(spent.spendId(),
                Part.of(groceries.categoryId(), "50.00"), Part.unfiled("0.00"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused)).containsIgnoringCase("more than zero");
        assertThat(theSplitNowOn(spent.spendId())).hasSize(1);
    }

    @Test
    void a_part_of_a_correction_quoted_more_finely_than_money_is_refused_rather_than_rounded() {
        ResponseEntity<JsonNode> refused = account.tryToCorrect(spent.spendId(),
                Part.of(groceries.categoryId(), "49.995"), Part.unfiled("0.005"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("an application that quietly decides what a figure was meant to say is worse "
                        + "than one that asks")
                .containsIgnoringCase("two decimal places");
        assertThat(theSplitNowOn(spent.spendId())).hasSize(1);
    }

    @Test
    void a_correction_of_a_spend_that_does_not_exist_is_refused_naming_it() {
        long noSuchSpend = account.anIdNoSpendHas();

        ResponseEntity<JsonNode> refused = account.tryToCorrect(noSuchSpend,
                Part.of(groceries.categoryId(), "50.00"));

        assertThat(refused.getStatusCode())
                .as("an identifier for something that is not there is what a 404 says")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused)).contains(String.valueOf(noSuchSpend));
    }

    @Test
    void a_correction_of_a_spend_on_somebody_elses_account_is_refused_in_the_very_same_words() {
        AnAccountThatSpends somebodyElse = new AnAccountThatSpends(http, "correction-elsewhere");
        SpendView theirs = somebodyElse.spends("Not mine", "12.00", Part.unfiled("12.00"));

        ResponseEntity<JsonNode> refused = account.tryToCorrect(theirs.spendId(),
                Part.of(groceries.categoryId(), "12.00"));

        assertThat(refused.getStatusCode())
                .as("no customer is ever told about another account, so a spend that is not on "
                        + "this one answers exactly as one that is nowhere does")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused))
                .as("the sentence names the account that was asked about rather than the one the "
                        + "spend is really on, so that nothing in it hints that the spend exists")
                .contains(String.valueOf(theirs.spendId()))
                .contains(String.valueOf(account.id()))
                .doesNotContain("current account " + somebodyElse.id() + ".");
        assertThat(somebodyElse.recentSpends().get(0).correctedAt())
                .as("and their spend is untouched, which is the half a status code would not show")
                .isNull();
    }

    @Test
    void a_correction_against_an_account_nobody_has_heard_of_is_refused_before_any_rule_about_spends() {
        long noSuchAccount = account.anIdNoCurrentAccountHas();

        ResponseEntity<JsonNode> refused = account.tryToCorrectOn(noSuchAccount, spent.spendId(),
                Part.of(groceries.categoryId(), "50.00"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused)).contains(String.valueOf(noSuchAccount));
    }

    @Test
    void a_correction_with_no_body_at_all_is_answered_in_this_applications_own_words() {
        ResponseEntity<JsonNode> refused = account.tryToCorrectWithNothingAtAll(spent.spendId());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("a body that did not arrive is a fact about the request, and it still deserves "
                        + "a sentence about what a correction is")
                .containsIgnoringCase("split");
        assertThat(theSplitNowOn(spent.spendId())).hasSize(1);
    }

    @Test
    void no_refusal_of_a_correction_moves_any_money() {
        BigDecimal before = account.balance();

        account.tryToCorrect(spent.spendId(), Part.unfiled("49.99"));
        account.tryToCorrect(spent.spendId(), Part.of(account.anIdNoCategoryHas(), "50.00"));
        account.tryToCorrect(account.anIdNoSpendHas(), Part.unfiled("50.00"));

        assertThat(account.balance())
                .as("a correction moves no money when it is accepted, so it certainly moves none "
                        + "when it is refused")
                .isEqualByComparingTo(before);
    }

    /** The split the spend reads back with now, which is the only place the answer to that lives. */
    private List<SpendPartView> theSplitNowOn(long spendId) {
        return account.recentSpends().stream()
                .filter(spend -> spend.spendId() == spendId)
                .findFirst()
                .orElseThrow()
                .parts();
    }

    /** The sentence the application came back with, which is the whole of what a customer gets. */
    private static String detailOf(ResponseEntity<JsonNode> refused) {
        JsonNode body = refused.getBody();
        assertThat(body).describedAs("a refusal carries words").isNotNull();
        assertThat(body.get("detail")).describedAs("as an RFC 9457 problem detail").isNotNull();
        return body.get("detail").asText();
    }
}
