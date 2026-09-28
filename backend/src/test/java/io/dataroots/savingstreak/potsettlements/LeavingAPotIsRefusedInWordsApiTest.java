package io.dataroots.savingstreak.potsettlements;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.PotMemberView;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Every way a departure from a shared pot is turned down, each answered in a sentence somebody could
 * read out loud and with the status that says what sort of mistake it was.
 *
 * <p><strong>Who may make somebody else leave is the rule this class is really about.</strong>
 * Anybody may leave a pot they belong to; making somebody <em>else</em> leave is administration, and
 * administration is an owner's. A contributor who could remove a member would be deciding the
 * membership of a pot the owner is responsible for — and, since every departure settles money, would
 * be moving another member's euros out of the pot without them or the owner agreeing to it.
 *
 * <p><strong>And where the money lands.</strong> The settlement goes to one of the <em>departing</em>
 * member's own current accounts. An owner who could name their own account when removing somebody
 * would be taking that member's savings along with their membership, and the request would look like
 * ordinary administration from the outside.
 *
 * <p>The last owner is refused here as well, because the rule is about the pot rather than about the
 * act: a pot whose only owner left would have nobody who may invite, promote, change a goal or close
 * it, and no way back. It is a conflict rather than a bad request, for the reason the demotion is —
 * what was asked for is perfectly good and it is the state of the pot that will not allow it.
 *
 * <p>Its own application and its own pot per test, for the reason every other shared-pot test gives.
 */
class LeavingAPotIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-leaving-a-pot-is-refused-in-words"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** A contributor removing somebody else is told what would have been needed, and nobody leaves. */
    @Test
    void a_contributor_may_not_make_somebody_else_leave() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        String carla = app.aCustomerOfItsOwn("removed by a contributor");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        AMemberOfThePot.joins(app, pot.id(), ANKE, carla, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), carla, "20.00");

        ResponseEntity<JsonNode> refused = app.tryToLeaveThePot(pot.id(),
                app.customerIdOf(carla), app.customerIdOf(BRAM), app.currentAccountOf(carla));

        assertThat(refused.getStatusCode())
                .as("understood, from somebody known, who is not allowed: " + refused.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused))
                .as("and it says what would have been needed")
                .contains("Only an owner may remove a member from this pot.");
        assertThat(app.membersOfThePot(pot.id()))
                .as("and she is in the pot she was in")
                .extracting(PotMemberView::name)
                .contains(carla);
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("with her twenty euros still in it, which is the half of this that is money")
                .isEqualByComparingTo("20.00");
    }

    /** A viewer is refused in the same words, and so is somebody the pot has never heard of. */
    @Test
    void so_is_a_viewer_and_so_is_a_stranger_to_the_pot() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");
        String watching = app.aCustomerOfItsOwn("watching and removing");
        String stranger = app.aCustomerOfItsOwn("a stranger removing");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        AMemberOfThePot.joins(app, pot.id(), ANKE, watching, "VIEWER");

        ResponseEntity<JsonNode> byAViewer = app.tryToLeaveThePot(pot.id(),
                app.customerIdOf(BRAM), app.customerIdOf(watching), app.currentAccountOf(BRAM));
        ResponseEntity<JsonNode> byAStranger = app.tryToLeaveThePot(pot.id(),
                app.customerIdOf(BRAM), app.customerIdOf(stranger), app.currentAccountOf(BRAM));

        assertThat(byAViewer.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(byAStranger.getStatusCode())
                .as("the same status and the same sentence, deliberately: telling somebody they "
                        + "are not a member would tell them there is a pot here to be a member of")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(byAStranger)).isEqualTo(reasonIn(byAViewer));
        assertThat(app.membersOfThePot(pot.id()))
                .extracting(PotMemberView::name)
                .contains(BRAM);
    }

    /** Story 21 again, arriving by the other route: the last owner is told to pass ownership on. */
    @Test
    void the_last_owner_may_not_leave_and_is_told_to_pass_ownership_on_first() {
        SharedPotView pot = app.openAPot(ANKE, "Nobody left to administer it");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "75.00");

        ResponseEntity<JsonNode> refused = app.tryToLeaveThePot(pot.id(),
                app.customerIdOf(ANKE), app.customerIdOf(ANKE), app.currentAccountOf(ANKE));

        assertThat(refused.getStatusCode())
                .as("the request is fine and the pot's state will not allow it: " + refused.getBody())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(refused))
                .as("and it says what to do first, because the alternative is a pot nobody can "
                        + "administer and no way back")
                .contains("only owner")
                .contains("owner first");
        assertThat(app.membersOfThePot(pot.id()))
                .as("she is the owner she was")
                .extracting(PotMemberView::name, PotMemberView::role)
                .contains(tuple(ANKE, "OWNER"));
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("and not a cent of hers was settled out on the way to being refused")
                .isEqualByComparingTo("75.00");
    }

    /** And the sequence it tells her to follow, followed: hand the pot on, and then go. */
    @Test
    void an_owner_may_leave_once_somebody_else_owns_the_pot() {
        SharedPotView pot = app.openAPot(ANKE, "Handed on and left");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.changeTheRole(pot.id(), BRAM, "OWNER", ANKE);
        app.deposit(pot.savingsAccountId(), ANKE, "12.34");
        BigDecimal inHerCurrentAccount = app.currentAccountBalanceOf(ANKE);

        assertThat(app.leaveThePot(pot.id(), ANKE, ANKE).settled())
                .as("there is another owner, so leaving is a departure rather than an orphaning")
                .isEqualByComparingTo("12.34");
        assertThat(app.currentAccountBalanceOf(ANKE))
                .isEqualByComparingTo(inHerCurrentAccount.add(new BigDecimal("12.34")));
        assertThat(app.membersOfThePot(pot.id()))
                .as("and the pot still has somebody who may administer it")
                .anyMatch(member -> "OWNER".equals(member.role()));
    }

    /**
     * The settlement lands in the departing member's own account and nowhere else — which is what
     * stops removing somebody being a way of helping yourself to their savings.
     */
    @Test
    void the_settlement_may_not_be_addressed_to_somebody_else_s_current_account() {
        SharedPotView pot = app.openAPot(ANKE, "Her account, his money");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");
        BigDecimal inHerCurrentAccount = app.currentAccountBalanceOf(ANKE);

        ResponseEntity<JsonNode> refused = app.tryToLeaveThePot(pot.id(),
                app.customerIdOf(BRAM), app.customerIdOf(ANKE), app.currentAccountOf(ANKE));

        assertThat(refused.getStatusCode())
                .as("a form to fix rather than a state to wait out: " + refused.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(refused))
                .as("and it says whose account it had to be, without saying whose it is")
                .contains("one of their own current accounts")
                .contains(BRAM);
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and nothing of his reached hers")
                .isEqualByComparingTo(inHerCurrentAccount);
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("40.00");
        assertThat(app.membersOfThePot(pot.id()))
                .as("he is still in the pot, because a refused departure is not a departure")
                .extracting(PotMemberView::name)
                .contains(BRAM);
    }

    /** A customer who exists and is not in this pot: an absence rather than a permission. */
    @Test
    void a_customer_who_is_not_in_the_pot_is_told_so() {
        SharedPotView pot = app.openAPot(ANKE, "Not in it");
        String outsider = app.aCustomerOfItsOwn("never in the pot");

        ResponseEntity<JsonNode> refused = app.tryToLeaveThePot(pot.id(),
                app.customerIdOf(outsider), app.customerIdOf(outsider),
                app.currentAccountOf(outsider));

        assertThat(refused.getStatusCode())
                .as("there is no membership here to end: " + refused.getBody())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused)).contains("is not a member of shared pot " + pot.id());
    }

    /** A pot nobody has heard of, and a customer nobody has heard of, each said as the absence it is. */
    @Test
    void a_pot_and_a_customer_nobody_has_heard_of_are_each_said_to_be_absent() {
        SharedPotView pot = app.openAPot(ANKE, "Here all along");
        long noSuchPot = pot.id() + 5_000;

        ResponseEntity<JsonNode> noPot = app.tryToLeaveThePot(noSuchPot, app.customerIdOf(ANKE),
                app.customerIdOf(ANKE), app.currentAccountOf(ANKE));
        ResponseEntity<JsonNode> noCustomer = app.tryToLeaveThePot(pot.id(),
                app.customerIdOf(ANKE), app.anIdNoCustomerHas(), app.currentAccountOf(ANKE));

        assertThat(noPot.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(noPot)).isEqualTo("There is no shared pot " + noSuchPot + ".");
        assertThat(noCustomer.getStatusCode())
                .as("who is asking is settled before anything about the pot's membership")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(noCustomer)).contains("customer");
    }

    /**
     * The two boxes the domain cannot be asked about: who is doing it, and where the money should
     * land. A request that never said is a malformed request rather than a refusal about a rule.
     */
    @Test
    void a_request_that_never_said_who_or_where_is_a_form_to_fix() {
        SharedPotView pot = app.openAPot(ANKE, "Half a form");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");

        ResponseEntity<JsonNode> nobody = app.tryToLeaveThePotAsNobody(pot.id(),
                app.customerIdOf(BRAM), app.currentAccountOf(BRAM));
        ResponseEntity<JsonNode> nowhere = app.tryToLeaveThePotWithNowhereToSettleInto(pot.id(),
                app.customerIdOf(BRAM), app.customerIdOf(BRAM));

        assertThat(nobody.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(nobody)).contains("needs the customer doing it");
        assertThat(nowhere.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(nowhere)).contains("current account");
        assertThat(app.membersOfThePot(pot.id()))
                .as("and neither of them took him out of the pot")
                .extracting(PotMemberView::name)
                .contains(BRAM);
    }

    /** The sentence the customer is shown, which is the whole of what they get to act on. */
    private static String reasonIn(ResponseEntity<JsonNode> refused) {
        assertThat(refused.getBody())
                .as("a refusal answers with a problem body")
                .isNotNull();
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered " + refused.getBody())
                .isTrue();
        return refused.getBody().get("detail").asText();
    }
}
