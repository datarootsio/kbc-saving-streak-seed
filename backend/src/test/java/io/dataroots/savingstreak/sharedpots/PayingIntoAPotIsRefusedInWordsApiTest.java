package io.dataroots.savingstreak.sharedpots;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

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
 * A pot is not a public collection box, and "viewer" means something: paying into a shared pot is
 * refused for somebody who is not in it and for somebody who is in it only to watch, each in a
 * sentence saying what to do about it.
 *
 * <p>And the other half of the same claim, which matters just as much: <strong>everything a deposit
 * was already refused for is refused in exactly the words it already was.</strong> An amount nobody
 * can read and a current account that cannot cover it are not pot problems, and a member who met one
 * of them should not be able to tell from the sentence that they were paying into a pot at all. Both
 * are asserted by putting the pot's refusal beside the personal account's and insisting they are the
 * same string — which is a claim no reading of either sentence on its own could make.
 *
 * <p>The two role refusals answer <strong>403</strong>, which this application had not used before
 * and which is the right status for both: the request is understood, the accounts are real, and the
 * person making it is not allowed to. A 400 would send somebody off to look for a mistake in what
 * they typed, and there is none; a 404 would tell them the account is not there, which they can
 * disprove by reading the pot.
 *
 * <p>Its own application, for the reason ticket 01's pot tests give.
 */
class PayingIntoAPotIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-paying-into-a-pot-refused"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** Story 32: a pot takes money from its members, and a pot nobody could join would be a pot. */
    @Test
    void somebody_who_is_not_a_member_is_told_so_and_told_what_to_do_about_it() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        String stranger = app.aCustomerOfItsOwn("a stranger to the pot");

        ResponseEntity<JsonNode> refused = app.tryToDeposit(pot.savingsAccountId(), stranger, "20.00");

        assertThat(refused.getStatusCode())
                .as("understood, and not allowed: " + refused.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(detailOf(refused))
                .isEqualTo("That savings account belongs to a shared pot you are not a member of. "
                        + "Ask somebody who owns the pot to invite you.");
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("and nothing of theirs is in it")
                .isEqualByComparingTo("0.00");
    }

    /** Story 27: a viewer sees what the pot holds and changes none of it. */
    @Test
    void a_viewer_is_told_that_watching_is_what_the_role_is_for() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");
        SomebodyElseInThePot.joins(app, pot.id(), ANKE, BRAM, PotRole.VIEWER);

        ResponseEntity<JsonNode> refused = app.tryToDeposit(pot.savingsAccountId(), BRAM, "20.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(detailOf(refused))
                .as("a different sentence from the stranger's, because there is a different thing "
                        + "to do next: be let in, or be allowed to pay")
                .isEqualTo("You are a viewer of that shared pot, so you can watch what it holds but "
                        + "not pay into it. Ask somebody who owns the pot to make you a contributor.");
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("0.00");
    }

    /**
     * Story 33: the existing rule about whose account the money comes from still holds.
     *
     * <p>It holds by construction rather than by a check of its own, and this test is what says so.
     * A deposit names no customer — the account the euros leave is who the application takes the
     * payer to be, exactly as it always has been — so a member naming somebody else's current
     * account is asking whether <em>that</em> person may pay into the pot. Here they may not, and
     * the answer says so. There is no request anybody can send that pays into a pot out of an
     * account that is not the payer's own, which is a stronger statement than a rule refusing one.
     */
    @Test
    void a_member_cannot_pay_in_out_of_a_current_account_that_is_not_theirs() {
        SharedPotView pot = app.openAPot(ANKE, "New bike");
        String somebodyElse = app.aCustomerOfItsOwn("whose current account is not the payers");

        ResponseEntity<JsonNode> refused = app.tryToDepositFrom(
                pot.savingsAccountId(), app.currentAccountOf(somebodyElse), "20.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(detailOf(refused))
                .as("the question was asked about the holder of the account named, who is in no pot")
                .contains("you are not a member of");
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("0.00");
    }

    /**
     * Story 34, the first half: an amount nobody can read is refused in the words it already is.
     *
     * <p>The comma is the mistake a Belgian page makes most, and what a member gets back for it is
     * the sentence about the comma — not a sentence about pots, and not the same sentence reworded.
     */
    @Test
    void an_unreadable_amount_is_refused_in_exactly_the_words_a_personal_account_uses() {
        SharedPotView pot = app.openAPot(ANKE, "Winter tyres");

        ResponseEntity<JsonNode> intoThePot =
                app.tryToDeposit(pot.savingsAccountId(), ANKE, "25,00");
        ResponseEntity<JsonNode> intoHerOwn =
                app.tryToDeposit(app.savingsAccountOf(ANKE), ANKE, "25,00");

        assertThat(intoThePot.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(intoHerOwn.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(intoThePot))
                .as("the same words, so that nothing about paying in feels like a different "
                        + "application")
                .isEqualTo(detailOf(intoHerOwn));
        assertThat(detailOf(intoThePot)).contains("is not an amount of money");
    }

    /** Story 34, the other half: a current account that cannot cover it says how much it holds. */
    @Test
    void a_deposit_the_current_account_cannot_cover_is_refused_in_exactly_those_words_too() {
        SharedPotView pot = app.openAPot(ANKE, "Roof");
        // Far more than any seeded current account holds, and nothing moves either time, so the two
        // refusals are quoting the same balance as well as the same sentence.
        String moreThanSheHas = "999999.00";

        ResponseEntity<JsonNode> intoThePot =
                app.tryToDeposit(pot.savingsAccountId(), ANKE, moreThanSheHas);
        ResponseEntity<JsonNode> intoHerOwn =
                app.tryToDeposit(app.savingsAccountOf(ANKE), ANKE, moreThanSheHas);

        assertThat(intoThePot.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(intoThePot)).isEqualTo(detailOf(intoHerOwn));
        assertThat(detailOf(intoThePot)).contains("There is not enough in that current account");
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("0.00");
    }

    /**
     * The other way money gets into a savings account in this application, and it is refused too: a
     * standing saving rule cannot be left against a pot's account.
     *
     * <p>Here rather than in a class of its own because it is the same question as the rest of this
     * one — which ways of putting money into a pot are open, and which are shut — and because the
     * answer is the same shape: a sentence saying what to do instead, which is to pay in by hand.
     *
     * <p>The reason it is shut is worth the line. A rule counts against <em>one customer's</em> limit
     * on standing rules, it draws on <em>one customer's</em> current account at two in the morning,
     * and it stops meaning anything the day that customer leaves the pot. Whose limit a pot's rule
     * eats and what becomes of it when they go are real questions, and the spec puts them in a
     * feature of their own. Refusing now is what stops a pot acquiring a rule nobody has decided the
     * rules for.
     */
    @Test
    void a_saving_rule_cannot_be_left_standing_against_a_pots_account() {
        SharedPotView pot = app.openAPot(ANKE, "Attic");
        Map<String, Object> everyWeek = new HashMap<>();
        everyWeek.put("name", "Into the pot every Friday");
        everyWeek.put("fromCurrentAccountId", app.currentAccountOf(ANKE));
        everyWeek.put("trigger", "WEEKLY");
        everyWeek.put("dayOfWeek", "FRIDAY");
        everyWeek.put("howMuchMoves", "A_FIXED_AMOUNT");
        everyWeek.put("amount", "25.00");

        ResponseEntity<JsonNode> refused =
                app.tryToLeaveARuleStanding(pot.savingsAccountId(), everyWeek);

        assertThat(refused.getStatusCode())
                .as("a real account and a rule it will not carry: " + refused.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(detailOf(refused))
                .isEqualTo("That savings account belongs to a shared pot, and a saving rule cannot "
                        + "be left standing against one. A pot is paid into by hand, by the members "
                        + "who want to pay into it.");
    }

    /**
     * And the deposit a personal savings account has always taken is still taken, in the same call
     * with the same answer. The whole of this slice is a new pairing beside the old one, and an old
     * pairing that had quietly stopped working would be the most expensive thing here to get wrong.
     */
    @Test
    void nothing_about_a_deposit_into_a_personal_savings_account_changed() {
        long herOwn = app.savingsAccountOf(ANKE);

        assertThat(app.deposit(herOwn, ANKE, "15.00").amount()).isEqualByComparingTo("15.00");

        assertThat(app.balancesOf(herOwn).moneyBalance())
                .as("her own account holds her own deposit, as it always did")
                .isGreaterThanOrEqualTo(new BigDecimal("15.00"));
    }

    /** The sentence a refusal carries, which is the whole of what the person gets to act on. */
    private static String detailOf(ResponseEntity<JsonNode> refused) {
        assertThat(refused.getBody())
                .as("a refusal answers with a problem detail and this one answered nothing")
                .isNotNull();
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered "
                        + refused.getBody())
                .isTrue();
        return refused.getBody().get("detail").asText();
    }
}
