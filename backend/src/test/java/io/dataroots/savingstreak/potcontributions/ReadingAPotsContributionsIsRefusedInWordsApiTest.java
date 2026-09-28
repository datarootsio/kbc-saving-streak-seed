package io.dataroots.savingstreak.potcontributions;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.PotInvitationView;
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
 * Who has paid what into a shared pot is its members' business and nobody else's, and everybody else
 * is refused in a sentence they could read out loud.
 *
 * <p><strong>A 403, which is the status a role refusal already answers in.</strong> The request is
 * understood, the caller is known, and they are not allowed — and somebody in no pot at all is told
 * what would have been needed rather than told the pot is not there, because telling a stranger that
 * they are not a member is telling them there is a pot here to be a member of. The same line
 * {@code WhatAPotIsSavingForIsItsOwnersDecision} draws in front of a pot's goals, in the same words.
 *
 * <p>A request that names nobody is nobody: not a member, and refused exactly as a stranger is.
 *
 * <p>A pot nobody has heard of is the other refusal and it is a 404, because that is a mistake about
 * which pot rather than about who is asking. It is asked before the membership, so that a member
 * typing the wrong identifier is told the pot is missing rather than that they are not in it.
 *
 * <p>Its own application, for the reason every pot test gives: a pot on the shared database would
 * hand every other test class a savings account identifier that exists and that no customer holds.
 */
class ReadingAPotsContributionsIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    /** Somebody in no pot at all, for the stranger who asks anyway. */
    private static String stranger;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-reading-a-pots-contributions-is-refused"));
        stranger = app.aCustomerOfItsOwn("a stranger to a pots contributions");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void somebody_in_no_pot_at_all_may_not_read_who_has_paid_what_into_it() {
        SharedPotView pot = aPotWithMoneyInIt("None of a stranger's business");

        ResponseEntity<JsonNode> refused = app.tryToReadTheContributions(pot.id(), stranger);

        assertThat(refused.getStatusCode())
                .as("understood, from somebody known, who is not allowed — which is 403")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused))
                .as("told what would have been needed, and not that there is no pot here")
                .contains("member");
        assertThat(app.contributionsTo(pot.id(), ANKE))
                .as("and the members go on reading exactly what they read before")
                .hasSize(2);
    }

    @Test
    void a_request_that_names_nobody_is_refused_the_way_a_stranger_is() {
        SharedPotView pot = aPotWithMoneyInIt("Nobody said who was asking");

        ResponseEntity<JsonNode> refused = app.tryToReadTheContributions(pot.id(), null);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused)).contains("member");
    }

    @Test
    void a_pot_nobody_has_heard_of_is_a_mistake_about_which_pot_rather_than_about_who() {
        ResponseEntity<JsonNode> refused = app.tryToReadTheContributions(4_910_912L, ANKE);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused))
                .as("and it names the pot that is not there, so the page can say which")
                .contains("4910912");
    }

    @Test
    void a_stranger_may_not_read_the_pots_money_movements_either() {
        SharedPotView pot = aPotWithMoneyInIt("A history that is not a stranger's to read");

        ResponseEntity<JsonNode> refused =
                app.tryToReadTheMoneyMovementsOfThePot(pot.id(), stranger);

        assertThat(refused.getStatusCode())
                .as("the pot's history says what two other people have done with their money")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused)).contains("member");
        assertThat(app.tryToReadTheMoneyMovementsOfThePot(pot.id(), null).getStatusCode())
                .as("and a request that names nobody is nobody here as well")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * A pot with two members and money in it, opened for one test and no other.
     *
     * <p>Money in it on purpose: a refusal about an empty pot would pass whether the rule ran before
     * the figures were worked out or after, and the one that matters is the one that refuses before
     * anybody's contribution has been read.
     */
    private static SharedPotView aPotWithMoneyInIt(String name) {
        SharedPotView pot = app.openAPot(ANKE, name);
        PotInvitationView sent = app.invite(pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.accept(pot.id(), sent.id(), BRAM);
        app.deposit(pot.savingsAccountId(), ANKE, "30.00");
        app.deposit(pot.savingsAccountId(), BRAM, "20.00");
        return pot;
    }

    /** The sentence the customer is shown, which is the whole of what they get to act on. */
    private static String reasonIn(ResponseEntity<JsonNode> refused) {
        assertThat(refused.getBody())
                .as("a refusal answers with a problem body")
                .isNotNull();
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered "
                        + refused.getBody())
                .isTrue();
        return refused.getBody().get("detail").asText();
    }
}
