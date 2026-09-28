package io.dataroots.savingstreak.potclosing;

import java.math.BigDecimal;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.PotClosedView;
import io.dataroots.savingstreak.support.PotSettlementView;
import io.dataroots.savingstreak.support.SharedPotView;
import io.dataroots.savingstreak.support.WithdrawalProposalView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stories 62, 63, 64 and 65: an owner closes the pot, every member gets their own remaining euros
 * back to the cent, the pot holds nothing afterwards, the goals nobody is saving for any more are
 * given up on, and a proposal still waiting is ended by the close.
 *
 * <p><strong>Their own euros, and never an arithmetic share.</strong> The test the ticket asks for
 * by name is the one with two members holding unequal remainders: an approved withdrawal has already
 * spent the oldest money in the pot, which was hers, so what comes back to each of them is what is
 * left of their <em>own</em> deposits and not a proportion of what the pot holds. That is what makes
 * closing exact to the cent with no rounding rule to state, and it is the same figure leaving the
 * pot would have handed each of them — worked out by the same method, so the two can never disagree.
 *
 * <p><strong>The pot is not emptied, it is distributed.</strong> What the pot held, added across the
 * settlements, is exactly what the members were given: no cent is left over to decide the owner of
 * and none is conjured. The close answers with both figures side by side so that the arithmetic can
 * be checked rather than trusted, which is the difference between a shared pot and a spreadsheet one
 * of them keeps.
 *
 * <p><strong>A member who never contributed is settled nothing and the close does not falter over
 * them.</strong> A viewer who only ever watched is on the list with nought beside them, no
 * withdrawal against them and nowhere for money to have gone — three absences that say one thing,
 * and the reason a close cannot be written as a loop over the people who happen to have money in it.
 *
 * <p><strong>The last owner goes with everybody else.</strong> Every other way of losing an owner is
 * refused when there is only one left, because a pot with nobody able to administer it has no way
 * back. A close is the one act allowed to take the last owner, because it takes the whole pot with
 * them — and the pot below has exactly one owner, so this test would fail if closing asked that rule.
 *
 * <p><strong>The memberships stay standing.</strong> Nobody leaves; the pot is now a record of what
 * these people saved for together, and a close that deleted the memberships would delete the record.
 * {@code AClosedPotStaysAReadableRecordApiTest} is where that is read back in full.
 *
 * <p>Its own application, for the reason every other shared-pot test gives: a pot's savings account
 * is held by nobody, and handing one to the shared database would hand every other test class an
 * account identifier that exists and that no customer holds. A pot of its own per test, so that what
 * one of them paid in cannot be what another is asking about; the clock never moves here, so the
 * methods may run in any order.
 *
 * <p>Each member's money lands in their own current account, which is the account their contribution
 * came from: a customer of this application holds one, so "the account their most recent
 * contribution came from" and "their current account" are the same account to read back — what the
 * assertions can and do show is that the euros follow the person rather than the owner who closed it.
 */
class ClosingAPotReturnsEveryMemberTheirOwnRemainingEurosApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-closing-a-pot-settles-everybody"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * The test the ticket asks for by name: two members holding unequal remainders, each given back
     * their own and not a share.
     */
    @Test
    void two_members_with_unequal_remainders_are_each_given_back_their_own() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        // Hers is the oldest money in the pot, so it is the money the withdrawal spends first —
        // which is what leaves the two of them holding unequal remainders out of equal deposits.
        app.deposit(pot.savingsAccountId(), ANKE, "100.00");
        app.deposit(pot.savingsAccountId(), BRAM, "100.00");
        WithdrawalProposalView his = app.proposeAWithdrawal(pot.id(), BRAM, "60.50");
        assertThat(app.approve(pot.id(), his.id(), ANKE).state()).isEqualTo("APPROVED");
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("sixty euros fifty has gone, all of it out of her deposit")
                .isEqualByComparingTo("139.50");
        BigDecimal inHerCurrentAccount = app.currentAccountBalanceOf(ANKE);
        BigDecimal inHisCurrentAccount = app.currentAccountBalanceOf(BRAM);

        PotClosedView closed = app.closeThePot(pot.id(), ANKE);

        assertThat(hers(closed).settled())
                .as("thirty-nine euros fifty: her hundred less the sixty fifty already spent out of "
                        + "it, and not half of what the pot held: " + closed)
                .isEqualByComparingTo("39.50");
        assertThat(his(closed).settled())
                .as("and his hundred is untouched, because the withdrawal never reached his deposit")
                .isEqualByComparingTo("100.00");
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("hers landed in her own current account")
                .isEqualByComparingTo(inHerCurrentAccount.add(new BigDecimal("39.50")));
        assertThat(app.currentAccountBalanceOf(BRAM))
                .as("and his in his, which is where his contribution came from")
                .isEqualByComparingTo(inHisCurrentAccount.add(new BigDecimal("100.00")));
        assertThat(hers(closed).withdrawalId())
                .as("money moved for each of them, so each has a movement to point at")
                .isNotNull();
        assertThat(his(closed).withdrawalId()).isNotNull();

        assertThat(closed.thePotHeld())
                .as("what it held is what the two settlements add up to, to the cent")
                .isEqualByComparingTo(hers(closed).settled().add(his(closed).settled()));
        assertThat(closed.thePotNowHolds())
                .as("and the pot holds nothing once it is closed")
                .isEqualByComparingTo("0.00");
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("read back off the pot itself, which is the point of saying it twice")
                .isEqualByComparingTo("0.00");
        assertThat(app.potWith(pot.id()).closedAt())
                .as("and the pot says it is closed: " + app.potWith(pot.id()))
                .isEqualTo(closed.closedAt());
    }

    /** A member who never paid in is settled nothing, and the close goes through over them. */
    @Test
    void a_member_who_never_contributed_is_settled_nothing_and_the_close_goes_through() {
        SharedPotView pot = app.openAPot(ANKE, "Watched by somebody");
        String watching = app.aCustomerOfItsOwn("watched a pot closed");
        AMemberOfThePot.joins(app, pot.id(), ANKE, watching, "VIEWER");
        app.deposit(pot.savingsAccountId(), ANKE, "45.25");
        BigDecimal inTheirCurrentAccount = app.currentAccountBalanceOf(watching);

        PotClosedView closed = app.closeThePot(pot.id(), ANKE);

        PotSettlementView theirs = settlementFor(closed, watching);
        assertThat(theirs.settled())
                .as("they never paid anything in, so there is nothing of theirs to come back: "
                        + closed)
                .isEqualByComparingTo("0.00");
        assertThat(theirs.withdrawalId())
                .as("and no withdrawal was recorded, because nothing happened to any money")
                .isNull();
        assertThat(theirs.toCurrentAccountId())
                .as("nor is there anywhere it would have gone: they have shown the pot no account")
                .isNull();
        assertThat(app.currentAccountBalanceOf(watching)).isEqualByComparingTo(inTheirCurrentAccount);
        assertThat(hers(closed).settled())
                .as("and the member who did pay in is settled all of it, undisturbed by them")
                .isEqualByComparingTo("45.25");
        assertThat(closed.thePotNowHolds()).isEqualByComparingTo("0.00");
    }

    /** Story 65: the goals nobody is saving for any more are given up on by the close. */
    @Test
    void the_pots_live_goals_are_abandoned_so_no_screen_projects_towards_them() {
        SharedPotView pot = app.openAPot(ANKE, "Saving for two things");
        app.deposit(pot.savingsAccountId(), ANKE, "300.00");
        GoalView worktops = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Worktops", "500.00");
        GoalView taps = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Taps", "150.00");

        PotClosedView closed = app.closeThePot(pot.id(), ANKE);

        assertThat(closed.goalsAbandoned())
                .as("both of them, named in the answer so that the close is its own receipt: "
                        + closed)
                .containsExactlyInAnyOrder(worktops.id(), taps.id());
        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), ANKE))
                .as("and the goals screen has nothing left to project towards, because nobody is "
                        + "saving for either of them any more")
                .isEmpty();
        assertThat(closed.thePotNowHolds())
                .as("the three hundred came back to her rather than staying claimed by a goal")
                .isEqualByComparingTo("0.00");
    }

    /** A proposal still waiting when the pot closes is ended by it rather than left hanging. */
    @Test
    void a_proposal_still_waiting_is_ended_by_the_close() {
        SharedPotView pot = app.openAPot(ANKE, "Deciding when it ended");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "70.00");
        app.deposit(pot.savingsAccountId(), BRAM, "70.00");
        WithdrawalProposalView waiting = app.proposeAWithdrawal(pot.id(), ANKE, "20.00");
        assertThat(waiting.state()).isEqualTo("PROPOSED");

        PotClosedView closed = app.closeThePot(pot.id(), ANKE);

        assertThat(closed.proposalsEnded())
                .as("the close says which questions it stopped asking: " + closed)
                .containsExactly(waiting.id());
        assertThat(app.withdrawalProposalsOf(pot.id()).get(0).state())
                .as("and the proposal is closed rather than waiting for ever on a pot that is over")
                .isNotEqualTo("PROPOSED");
        assertThat(app.withdrawalProposalsOf(pot.id()).get(0).whoseAssentItNeeds())
                .as("nobody is being asked anything about it any more")
                .isEmpty();
        assertThat(hers(closed).settled())
                .as("and her seventy came back to her whole: the proposal never moved a cent")
                .isEqualByComparingTo("70.00");
        assertThat(his(closed).settled()).isEqualByComparingTo("70.00");
    }

    /**
     * An owner who is the pot's only owner may close it, which is the one act allowed to leave a pot
     * with nobody administering it — because there is no pot left to administer.
     */
    @Test
    void the_only_owner_may_close_the_pot_although_they_may_not_leave_it() {
        SharedPotView pot = app.openAPot(ANKE, "Only one owner");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), BRAM, "12.34");

        PotClosedView closed = app.closeThePot(pot.id(), ANKE);

        assertThat(closed.closedAt())
                .as("she is the only owner and she closed it all the same: " + closed)
                .isNotNull();
        assertThat(his(closed).settled()).isEqualByComparingTo("12.34");
        assertThat(closed.settledTo())
                .as("both of them are settled, in the order they joined")
                .extracting(PotSettlementView::name)
                .containsExactly(ANKE, BRAM);
    }

    private static PotSettlementView hers(PotClosedView closed) {
        return settlementFor(closed, ANKE);
    }

    private static PotSettlementView his(PotClosedView closed) {
        return settlementFor(closed, BRAM);
    }

    private static PotSettlementView settlementFor(PotClosedView closed, String name) {
        return closed.settledTo().stream()
                .filter(settlement -> name.equals(settlement.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        name + " was not settled by the close at all: " + closed));
    }
}
