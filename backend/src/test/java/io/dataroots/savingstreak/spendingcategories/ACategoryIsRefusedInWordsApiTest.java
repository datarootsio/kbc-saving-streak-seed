package io.dataroots.savingstreak.spendingcategories;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Everything this application will not accept as a spending category comes back with the status
 * somebody chose and a sentence that says what is wrong.
 *
 * <p>User story 44, and the half of stories 4 and 5 that is about staying ended. A refusal is only
 * useful if the person who caused it can read why, so every one of these asserts the words as well
 * as the status — the reason travels in {@code detail}, which is RFC 9457's field and the one the
 * frontend shows unchanged.
 *
 * <p>Nothing is left standing afterwards, which is the other half of a refusal: a request the
 * application said no to must not have changed anything.
 *
 * <p>The two sentences this module does not own are asserted on as well: a current account nobody
 * has heard of is refused in the words {@code AccountsService} owns, and a category on somebody
 * else's account is a category that is not there rather than a category belonging to a stranger.
 */
class ACategoryIsRefusedInWordsApiTest extends ApiIntegrationTest {

    /** The cap this application puts on one account, quoted so the test says what it is asserting. */
    private static final int THE_MOST_ONE_ACCOUNT_CAN_CARRY = 20;

    private AnAccountWithCategories account;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountWithCategories(http, "refused-in-words");
    }

    @Test
    void a_category_with_a_blank_name_is_refused_because_a_name_is_all_a_category_is() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare("   ");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("a category is a name and nothing else, so a blank one describes nothing")
                .contains("name");
        assertThat(account.standingCategories()).isEmpty();
    }

    @Test
    void a_category_with_no_name_at_all_is_refused_the_same_way() {
        ResponseEntity<JsonNode> refused = account.tryToDeclare(null);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused)).contains("name");
        assertThat(account.standingCategories()).isEmpty();
    }

    @Test
    void a_request_with_no_body_at_all_is_answered_in_this_applications_own_words() {
        ResponseEntity<JsonNode> refused = http.postForEntity(
                "/api/current-accounts/{id}/categories", null, JsonNode.class, account.id());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("rather than in Spring's, which would be a sentence about JSON in front of "
                        + "somebody who typed nothing")
                .contains("name");
        assertThat(account.standingCategories()).isEmpty();
    }

    @Test
    void a_name_already_standing_on_the_account_is_refused_and_named_back() {
        account.declares("Groceries");

        ResponseEntity<JsonNode> refused = account.tryToDeclare("Groceries");

        assertThat(refused.getStatusCode())
                .as("a conflict rather than a bad request: what was typed is perfectly good, and it "
                        + "is the state of the account that already answers to it")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(refused))
                .as("named back, because a customer with twenty categories cannot be expected to "
                        + "remember which of them they already have")
                .contains("Groceries");
        assertThat(account.standingCategories())
                .as("and the one that was already there is untouched")
                .hasSize(1);
    }

    @Test
    void renaming_a_category_onto_a_name_already_standing_is_refused_the_same_way() {
        account.declares("Groceries");
        SpendingCategoryView fuel = account.declares("Fuel");

        ResponseEntity<JsonNode> refused =
                account.tryToRename(account.id(), fuel.categoryId(), "Groceries");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(refused)).contains("Groceries");
        assertThat(account.standingCategories())
                .as("nothing is written until every objection has been heard, so a refused rename "
                        + "leaves the category exactly as its holder last left it")
                .extracting(SpendingCategoryView::name)
                .containsExactly("Groceries", "Fuel");
    }

    @Test
    void renaming_a_category_to_the_name_it_already_carries_is_not_a_duplicate_of_itself() {
        SpendingCategoryView groceries = account.declares("Groceries");

        SpendingCategoryView renamed = account.renames(groceries.categoryId(), "Groceries");

        assertThat(renamed.name())
                .as("the name already standing on the account is this category's own, and a "
                        + "customer who retyped it has asked for nothing that is not already true")
                .isEqualTo("Groceries");
    }

    @Test
    void a_twenty_first_standing_category_is_refused_and_ending_one_makes_room_for_another() {
        for (int each = 1; each <= THE_MOST_ONE_ACCOUNT_CAN_CARRY; each++) {
            account.declares("Category " + each);
        }

        ResponseEntity<JsonNode> refused = account.tryToDeclare("One too many");

        assertThat(refused.getStatusCode())
                .as("a bad request rather than a conflict: what the customer does next is end one "
                        + "they no longer use and send this again")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .as("the sentence quotes the limit, so there is nothing left to work out")
                .contains(String.valueOf(THE_MOST_ONE_ACCOUNT_CAN_CARRY))
                .contains("End one");
        assertThat(account.standingCategories()).hasSize(THE_MOST_ONE_ACCOUNT_CAN_CARRY);

        account.ends(account.standingCategories().get(0).categoryId());

        assertThat(account.declares("One too many").name())
                .as("an ended category is on nothing that the cap protects, so ending one makes "
                        + "room")
                .isEqualTo("One too many");
        assertThat(account.standingCategories()).hasSize(THE_MOST_ONE_ACCOUNT_CAN_CARRY);
    }

    @Test
    void an_ended_category_cannot_be_renamed() {
        SpendingCategoryView fuel = account.declares("Fuel");
        account.ends(fuel.categoryId());

        ResponseEntity<JsonNode> refused =
                account.tryToRename(account.id(), fuel.categoryId(), "Petrol");

        assertThat(refused.getStatusCode())
                .as("a conflict rather than a bad request: the request was perfectly well formed "
                        + "and it is the state of the category that will not allow it")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(refused)).contains("Fuel").contains("ended");
        assertThat(account.endedCategories())
                .as("and the record is untouched, which is the whole point of keeping it")
                .extracting(SpendingCategoryView::name)
                .containsExactly("Fuel");
    }

    @Test
    void an_ended_category_cannot_be_ended_again() {
        SpendingCategoryView fuel = account.declares("Fuel");
        account.ends(fuel.categoryId());

        ResponseEntity<JsonNode> refused = account.tryToEnd(account.id(), fuel.categoryId());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(refused)).contains("ended");
        assertThat(account.endedCategories())
                .as("ending is one-way, so an ended category cannot be confused back into "
                        + "existence by pressing the button twice")
                .hasSize(1);
        assertThat(account.standingCategories()).isEmpty();
    }

    @Test
    void a_category_nobody_has_declared_cannot_be_renamed_or_ended() {
        SpendingCategoryView groceries = account.declares("Groceries");
        long noSuchCategory = groceries.categoryId() + 10_000;

        ResponseEntity<JsonNode> renameRefused =
                account.tryToRename(account.id(), noSuchCategory, "Fuel");

        assertThat(renameRefused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(renameRefused))
                .contains("no category " + noSuchCategory)
                .contains("current account " + account.id());

        ResponseEntity<JsonNode> endRefused = account.tryToEnd(account.id(), noSuchCategory);
        assertThat(endRefused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void a_category_on_somebody_elses_account_cannot_be_renamed_or_ended_through_your_own() {
        AnAccountWithCategories somebodyElse =
                new AnAccountWithCategories(http, "refused-not-yours");
        SpendingCategoryView theirs = somebodyElse.declares("Their groceries");

        ResponseEntity<JsonNode> renameRefused =
                account.tryToRename(account.id(), theirs.categoryId(), "Mine now");

        assertThat(renameRefused.getStatusCode())
                .as("a category is only ever found through the account in the path, so somebody "
                        + "else's is a category that is not there — telling a customer it exists "
                        + "but belongs to another account would be telling them about another "
                        + "account")
                .isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> endRefused = account.tryToEnd(account.id(), theirs.categoryId());
        assertThat(endRefused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(somebodyElse.standingCategories())
                .as("and their list is exactly where they left it")
                .extracting(SpendingCategoryView::name)
                .containsExactly("Their groceries");
    }

    @Test
    void a_category_declared_against_an_account_nobody_has_heard_of_is_refused_in_the_words_accounts_owns() {
        long noSuchAccount = account.anIdNoCurrentAccountHas();

        ResponseEntity<JsonNode> refused = account.tryToDeclareOn(noSuchAccount, "Groceries");

        assertThat(refused.getStatusCode())
                .as("an account nobody has heard of is a 404 before any rule about categories is "
                        + "reached")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailOf(refused))
                .as("in one sentence this application owns once, rather than one per module")
                .isEqualTo("There is no current account " + noSuchAccount + ".");
    }

    @Test
    void the_categories_of_an_account_nobody_has_heard_of_are_refused_rather_than_drawn_empty() {
        long noSuchAccount = account.anIdNoCurrentAccountHas();

        ResponseEntity<JsonNode> refused = account.tryToReadTheCategoriesOf(noSuchAccount);

        assertThat(refused.getStatusCode())
                .as("an empty list would tell somebody that an account they do not hold simply has "
                        + "nothing in it")
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
