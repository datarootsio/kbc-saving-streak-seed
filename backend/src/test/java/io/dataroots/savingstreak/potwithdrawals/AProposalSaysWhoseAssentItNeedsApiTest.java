package io.dataroots.savingstreak.potwithdrawals;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.PotMemberView;
import io.dataroots.savingstreak.support.SharedPotView;
import io.dataroots.savingstreak.support.WithdrawalProposalView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A proposal to take money out of a shared pot says who must still answer it: every other member who
 * still has money in the pot, and nobody else.
 *
 * <p><strong>This is the rule the whole feature exists for, read off a screen.</strong> A withdrawal
 * draws a savings account's oldest deposits down first, whoever paid them in, so one taken out of a
 * shared pot spends the other members' euros — and because what somebody has ever earned points on
 * does not fall when what they hold does, it costs them the points on their next contributions too.
 * Whoever's euros are at stake gets a veto; whoever has none is not asked; a viewer is never asked,
 * having none by construction.
 *
 * <p>The same four situations are stated directly against the rule in
 * {@code WhoseAssentAWithdrawalNeedsTest}, which is where the table of cases lives. This is where
 * the behaviour does: every case in that table is reachable through the API, and a rule that is
 * right and wired up wrongly fails here.
 *
 * <p>Its own application, for the reason the other shared-pot tests give: a pot's savings account is
 * held by nobody, and handing one to the shared database would hand every other test class an
 * account identifier that exists and that no customer holds.
 *
 * <p>A pot of its own per test, so that what one of them paid in cannot be what another one is
 * asking about. The pots are independent and the clock never moves here, so the methods may run in
 * any order.
 */
class AProposalSaysWhoseAssentItNeedsApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-whose-assent-a-withdrawal-needs"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** Stories 46 and 48: the member whose euros are at stake is the member who is asked. */
    @Test
    void the_other_member_with_money_in_the_pot_has_to_answer_it() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "80.00");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");

        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "30.00");

        assertThat(proposed.state())
                .as("it waits, and nothing about it has happened yet: " + proposed)
                .isEqualTo("PROPOSED");
        assertThat(proposed.whoseAssentItNeeds())
                .as("Bram's forty euros are in the pot and the oldest deposits go first whoever "
                        + "paid them in, so this withdrawal can spend his money — and he says")
                .extracting(PotMemberView::customerId)
                .containsExactly(app.customerIdOf(BRAM));
        assertThat(proposed.whoseAssentItNeeds())
                .extracting(PotMemberView::name)
                .as("named, because the sentence a page writes is \"waiting for Bram\"")
                .containsExactly(BRAM);
        assertThat(proposed.answeredBy())
                .as("and nobody has answered it, which is the state of every proposal so far")
                .isEmpty();
        assertThat(proposed.proposedByCustomerId()).isEqualTo(app.customerIdOf(ANKE));
        assertThat(proposed.amount()).isEqualByComparingTo("30.00");
        assertThat(proposed.toCurrentAccountId()).isEqualTo(app.currentAccountOf(ANKE));
        assertThat(proposed.closedAt())
                .as("nothing has closed it, and that absence is what says it is still waiting")
                .isNull();
    }

    /** Story 49: a member with nothing at stake does not get to hold the withdrawal up. */
    @Test
    void a_member_who_has_never_paid_into_the_pot_is_not_asked() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "60.00");

        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "25.00");

        assertThat(proposed.whoseAssentItNeeds())
                .as("Bram is a member and may pay in, and has not: there is nothing of his for "
                        + "this withdrawal to spend, so there is nothing to protect him from")
                .isEmpty();
        assertThat(app.membersOfThePot(pot.id()))
                .as("and he is still in the pot, which is the point — not being asked is not "
                        + "being left out")
                .extracting(PotMemberView::name)
                .containsExactly(ANKE, BRAM);
    }

    /** Story 50: the sole contributor withdraws without ceremony. */
    @Test
    void the_only_member_with_money_in_the_pot_needs_nobodys_approval() {
        SharedPotView pot = app.openAPot(ANKE, "A boat");
        app.deposit(pot.savingsAccountId(), ANKE, "90.00");

        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "90.00");

        assertThat(proposed.whoseAssentItNeeds())
                .as("there is nobody else's money in the pot, so the gate has nobody to protect "
                        + "and says so with an empty list rather than with a gate nobody can open")
                .isEmpty();
        assertThat(proposed.state())
                .as("and a gate with nobody on either side of it is not a gate to wait at: the "
                        + "withdrawal goes through as it is asked for. What that does to the money "
                        + "is asserted in TheLastApprovalMovesTheMoneyApiTest; what matters here is "
                        + "that nobody was asked")
                .isEqualTo("APPROVED");
    }

    /** Story 27, from the side where a viewer is not obstructive but simply not involved. */
    @Test
    void a_viewer_is_never_asked() {
        SharedPotView pot = app.openAPot(ANKE, "The bathroom");
        AnotherMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "VIEWER");
        app.deposit(pot.savingsAccountId(), ANKE, "70.00");

        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "20.00");

        assertThat(proposed.whoseAssentItNeeds())
                .as("a viewer cannot pay in, so there is nothing of theirs in the pot and nothing "
                        + "for them to veto — which is what \"viewer\" means")
                .isEmpty();
        assertThat(app.membersOfThePot(pot.id()))
                .as("and he is still a member, watching: not being asked is not being left out")
                .extracting(PotMemberView::name)
                .containsExactly(ANKE, BRAM);
    }
}
