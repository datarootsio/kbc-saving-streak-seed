package io.dataroots.savingstreak.sharedpots;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ordinary withdrawal endpoint refuses a shared pot's savings account outright, in a sentence
 * saying that a pot's money leaves it by proposal — so that there is no back door around an approval
 * gate that does not exist yet.
 *
 * <p><strong>The door is shut before it can be used</strong>, and that is the whole reason this is
 * in the slice that makes a pot payable into rather than in the slice that builds proposals. A
 * withdrawal draws an account's oldest deposits down first regardless of whose they are, so on a
 * shared pot it spends the other member's euros — and, because what somebody has ever earned on does
 * not fall when what they hold does, it also costs them the points on their next contributions. One
 * member taking money out of a shared pot is quietly spending somebody else's points, and the moment
 * a pot can hold real money is the moment that becomes possible.
 *
 * <p>Refused for a member as flatly as for a stranger, and in the same words. Being allowed to pay
 * in is not being allowed to take out; a member told "you are not a member of that pot" would go
 * looking for a mistake they did not make, and a member told nothing at all would conclude the
 * feature was broken rather than deliberate.
 *
 * <p>Its own application, for the reason ticket 01's pot tests give.
 */
class APotsMoneyLeavesItByProposalApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-pots-money-leaves-by-proposal"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** Story 61, from the inside: the member who paid it in cannot take it back out this way. */
    @Test
    void the_member_who_paid_it_in_is_told_that_a_pots_money_leaves_by_proposal() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        app.deposit(pot.savingsAccountId(), ANKE, "60.00");
        BigDecimal inHerCurrentAccount = app.currentAccountBalanceOf(ANKE);

        ResponseEntity<JsonNode> refused = app.tryToWithdraw(pot.savingsAccountId(), ANKE, "10.00");

        assertThat(refused.getStatusCode())
                .as("a real account and a request it does not answer, which is a bad request: "
                        + refused.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .isEqualTo("That savings account belongs to a shared pot, and a shared pot's money "
                        + "leaves it by proposal: propose a withdrawal, and it goes through when "
                        + "every other member with money still in the pot has approved it.");
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("and not a cent left the pot")
                .isEqualByComparingTo("60.00");
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("nor arrived anywhere else")
                .isEqualByComparingTo(inHerCurrentAccount);
    }

    /** And from the outside, in the same words, for the reason this class's javadoc gives. */
    @Test
    void so_is_somebody_the_pot_has_never_heard_of() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");
        app.deposit(pot.savingsAccountId(), ANKE, "30.00");

        ResponseEntity<JsonNode> refused = app.tryToWithdraw(pot.savingsAccountId(), BRAM, "5.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused)).contains("leaves it by proposal");
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("30.00");
    }

    /**
     * And a withdrawal from a personal savings account is still exactly what it was, which is the
     * assertion that says the door was shut on the pot and not on withdrawals.
     */
    @Test
    void nothing_about_a_withdrawal_from_a_personal_savings_account_changed() {
        long herOwn = app.savingsAccountOf(ANKE);
        app.deposit(herOwn, ANKE, "45.00");
        BigDecimal inTheAccount = app.balancesOf(herOwn).moneyBalance();

        app.withdraw(herOwn, ANKE, "20.00");

        assertThat(app.balancesOf(herOwn).moneyBalance())
                .isEqualByComparingTo(inTheAccount.subtract(new BigDecimal("20.00")));
    }

    /** The sentence a refusal carries, which is the whole of what the person gets to act on. */
    private static String detailOf(ResponseEntity<JsonNode> refused) {
        assertThat(refused.getBody()).isNotNull();
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered "
                        + refused.getBody())
                .isTrue();
        return refused.getBody().get("detail").asText();
    }
}
