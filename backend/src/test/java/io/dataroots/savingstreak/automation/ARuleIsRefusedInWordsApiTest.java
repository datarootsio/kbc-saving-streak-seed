package io.dataroots.savingstreak.automation;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingRuleView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.automation.AnAccountWithRules.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rule this application will not keep is refused with a reason somebody can act on, and nothing is
 * left standing.
 *
 * <p>Every refusal is asserted three times over: the status, words in {@code detail} naming what was
 * objected to, and that the account's rules are untouched afterwards. A refusal that came back as a
 * bare status would leave a person guessing which of the eight things they typed was wrong, and this
 * application answers every error in one shape precisely so that it never has to.
 *
 * <p>An amount is an amount of money, so the objections to one are the objections a deposit, a
 * withdrawal and a goal's target get, in the same words: {@code AmountOfMoney} owns the rule and the
 * sentence, and a customer who has met the objection once has met it everywhere. The decimal-places
 * test below reads that sentence in full rather than a paraphrase of it, which is what would fail if
 * the wording were ever forked into this module.
 *
 * <p>The savings account in the path is the one refusal this module does not make. It is vouched for
 * by the controller and answered 404 in the words Accounts owns, which is why there is no
 * {@code NO_SUCH_ACCOUNT} kind to switch over — and the test for it is here rather than nowhere,
 * because "which layer answers this" is exactly the sort of claim that quietly stops being true.
 */
class ARuleIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private AnAccountWithRules account;
    private SeededAccounts seeded;

    @BeforeEach
    void anAccountToBeRefusedOn() {
        account = new AnAccountWithRules(http, "refused");
        seeded = new SeededAccounts(http);
    }

    @Test
    void a_rule_naming_a_current_account_its_holder_does_not_hold_is_refused() {
        Map<String, Object> somebody_elses = account.aFixedAmountEveryWeek("Not mine", "MONDAY", "50.00");
        somebody_elses.put("fromCurrentAccountId", seeded.currentAccountOf(SeededAccounts.BRAM));

        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(somebody_elses);

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("same customer");
    }

    /**
     * A current account nobody has heard of is refused in the words Accounts owns, and as a rule this
     * application will not keep rather than as a missing page: what is missing is a field in the
     * form, not the thing the request was addressed to.
     */
    @Test
    void a_rule_naming_a_current_account_that_does_not_exist_is_refused_in_the_words_accounts_owns() {
        long noSuchAccount = seeded.anIdNoCurrentAccountHas();
        Map<String, Object> madeUp = account.aFixedAmountEveryWeek("Made up", "MONDAY", "50.00");
        madeUp.put("fromCurrentAccountId", noSuchAccount);

        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(madeUp);

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .isEqualTo("There is no current account " + noSuchAccount + ".");
    }

    @Test
    void nothing_as_an_amount_is_refused() {
        Map<String, Object> noAmount = account.aFixedAmountEveryWeek("Nothing at all", "MONDAY", null);

        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(noAmount);

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("how much", "amount of money");
    }

    @Test
    void an_amount_of_zero_is_refused() {
        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(
                account.aFixedAmountEveryWeek("Nothing a week", "MONDAY", "0.00"));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("more than zero", "0.00");
    }

    /**
     * The sentence {@code AmountOfMoney} already gives, word for word. It is asserted in full rather
     * than by a keyword because the point is that the wording is shared: a copy of the rule written
     * out in this module would pass a keyword check and would be exactly the drift the shared class
     * exists to prevent.
     */
    @Test
    void an_amount_quoted_more_finely_than_money_is_refused_in_the_words_every_amount_gets() {
        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(
                account.aFixedAmountEveryWeek("Too precise", "MONDAY", "50.005"));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .isEqualTo("An amount of money has at most two decimal places, and 50.005 has 3.");
    }

    @Test
    void a_negative_floor_is_refused() {
        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(
                account.everythingAboveAFloorOnPayday("Below nothing", "-50.00"));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("-50.00", "nothing or more");
    }

    /**
     * A floor of nothing is a rule this application keeps, and a floor quoted more finely than money
     * is not — and being a zero does not excuse the second. The two objections to a figure are asked
     * separately of a floor precisely because only one of them has to be skipped for it, and a
     * {@code 0.00000} answered 201 while {@code 0.001} was answered 400 would be the same shape of
     * input getting two different answers.
     */
    @Test
    void a_floor_quoted_more_finely_than_money_is_refused_even_when_it_is_nothing() {
        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(
                account.everythingAboveAFloorOnPayday("Too precise about nothing", "0.00000"));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .isEqualTo("An amount of money has at most two decimal places, and 0.00000 has 5.");
    }

    @Test
    void a_day_of_the_month_above_thirty_one_is_refused() {
        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(
                account.aFixedAmountEveryMonth("The thirty-second", "32", "50.00"));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("between 1 and 31", "32");
    }

    @Test
    void a_day_of_the_month_below_one_is_refused() {
        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(
                account.aFixedAmountEveryMonth("The nothingth", "0", "50.00"));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("between 1 and 31", "0");
    }

    @Test
    void a_day_of_the_week_that_is_not_one_is_refused_with_the_seven_that_are() {
        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(
                account.aFixedAmountEveryWeek("Every Funday", "FUNDAY", "50.00"));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("FUNDAY", "MONDAY", "SUNDAY");
    }

    /** A weekly rule with no day at all is the other half of the same objection. */
    @Test
    void a_weekly_rule_with_no_day_at_all_is_refused() {
        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(
                account.aFixedAmountEveryWeek("Some day", null, "50.00"));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("day of the week");
    }

    @Test
    void a_rule_with_no_name_is_refused() {
        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(
                account.aFixedAmountEveryWeek("   ", "MONDAY", "50.00"));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("name");
    }

    @Test
    void a_trigger_that_is_none_of_the_three_is_refused_with_the_three_that_are() {
        Map<String, Object> madeUp = account.aRuleDrawnFromThisCustomersCurrentAccount("Whenever");
        madeUp.put("trigger", "WHEN_I_FEEL_LIKE_IT");
        madeUp.put("howMuchMoves", "A_FIXED_AMOUNT");
        madeUp.put("amount", "50.00");

        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(madeUp);

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("WEEKLY", "MONTHLY", "ON_PAYDAY");
    }

    /**
     * The one refusal this module does not make. The account in the path is vouched for by the
     * controller, in the words Accounts owns, before any rule about rules is reached.
     */
    @Test
    void a_rule_asked_for_on_an_account_that_does_not_exist_is_a_404_in_the_words_accounts_owns() {
        long noSuchAccount = seeded.anIdNoSavingsAccountHas();

        ResponseEntity<JsonNode> response = account.tryToLeaveStandingOn(noSuchAccount,
                account.aFixedAmountEveryWeek("Nowhere", "MONDAY", "50.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(response))
                .isEqualTo("There is no savings account " + noSuchAccount + ".");
    }

    @Test
    void a_rule_that_is_not_on_this_account_is_not_there_at_all() {
        long noSuchRule = account.anIdNoRuleOnThisAccountHas();

        ResponseEntity<JsonNode> changed = account.tryToChange(noSuchRule, Map.of("name", "Ghost"));
        ResponseEntity<JsonNode> ended = account.tryToEnd(noSuchRule);

        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(changed)).contains("no saving rule " + noSuchRule);
        assertThat(ended.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(ended)).contains("no saving rule " + noSuchRule);
    }

    /** A figure with a comma in it is answered about the figure rather than about the request. */
    @Test
    void an_amount_that_is_not_a_number_at_all_is_named_back_to_whoever_typed_it() {
        Map<String, Object> typedWithAComma = new HashMap<>(
                account.aFixedAmountEveryWeek("Continental", "MONDAY", "50,00"));

        ResponseEntity<JsonNode> response = account.tryToLeaveStanding(typedWithAComma);

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("50,00", "not an amount of money");
    }

    /**
     * Every refusal leaves the account exactly as it was. Asserted on every one of them rather than
     * on a chosen few, because a rule half-written by a refused request is the failure nothing on the
     * page would show.
     */
    private void assertRefused(ResponseEntity<JsonNode> response, HttpStatus status) {
        assertThat(response.getStatusCode())
                .describedAs("the refusal's status: " + response.getBody())
                .isEqualTo(status);
        assertThat(account.rules())
                .describedAs("a refused rule is not left standing")
                .extracting(SavingRuleView::name)
                .isEmpty();
    }
}
