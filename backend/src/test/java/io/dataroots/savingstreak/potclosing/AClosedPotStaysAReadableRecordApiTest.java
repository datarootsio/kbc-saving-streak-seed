package io.dataroots.savingstreak.potclosing;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.MoneyMovementView;
import io.dataroots.savingstreak.support.PotContributionView;
import io.dataroots.savingstreak.support.PotMemberView;
import io.dataroots.savingstreak.support.SharedPotView;
import io.dataroots.savingstreak.support.WithdrawalProposalView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Story 66: a closed pot's history, membership, contributions, money movements and proposals all
 * remain readable, and the pot is still in the list of pots each of its members belongs to, marked
 * closed.
 *
 * <p><strong>This is why closing is not deleting, and it is the whole of what "the pot becomes a
 * record" means.</strong> Two people saved for a kitchen together; the kitchen is paid for, the
 * money has gone back to them, and what remains is the answer to "what did we each put in" for as
 * long as anybody wants to ask it. A close that emptied the pot out of the application would take
 * that with it, and the one thing a shared pot is for — being checkable rather than trusted — would
 * end the day the saving did.
 *
 * <p><strong>The memberships are what carry it.</strong> Nobody leaves when a pot closes, and this
 * is the test that says so out loud: the pot is in both members' lists, the members list still names
 * them with the roles they held, and the contributions screen has a row per person. A close that
 * ended the memberships — the obvious way to write one, since leaving does — would pass every
 * assertion about the money and fail every assertion here.
 *
 * <p><strong>And every read answers 200, which is what makes the conflicts next door honest.</strong>
 * Every act on a closed pot is refused, so the refusal has to be about the acting: a read refused
 * alongside them would make the pot a thing that has gone, and a page would have nothing to show
 * anybody who followed a link to it.
 *
 * <p>Its own application, for the reason every other shared-pot test gives. One test method for the
 * reads, because the story is one narrative — what these two did, read back after it ended.
 */
class AClosedPotStaysAReadableRecordApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-closed-pot-stays-readable"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void everything_the_two_of_them_did_reads_back_after_the_pot_has_closed() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        AMemberOfThePot.joins(app, pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), ANKE, "120.00");
        app.deposit(pot.savingsAccountId(), BRAM, "80.00");
        GoalView worktops = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Worktops", "500.00");
        WithdrawalProposalView hers = app.proposeAWithdrawal(pot.id(), ANKE, "50.00");
        assertThat(app.approve(pot.id(), hers.id(), BRAM).state()).isEqualTo("APPROVED");

        app.closeThePot(pot.id(), ANKE);

        SharedPotView afterwards = app.potWith(pot.id());
        assertThat(afterwards.closedAt())
                .as("the pot is still there and says it is closed: " + afterwards)
                .isNotNull();
        assertThat(afterwards.name())
                .as("still called what they called it")
                .isEqualTo("Kitchen");
        assertThat(afterwards.moneyBalance())
                .as("and holding nothing, because closing gave it all back")
                .isEqualByComparingTo("0.00");
        assertThat(afterwards.members())
                .as("**nobody left**: a closed pot is a record of who saved for it together, and "
                        + "the roles they held are part of that record")
                .extracting(PotMemberView::name, PotMemberView::role)
                .containsExactly(tuple(ANKE, "OWNER"), tuple(BRAM, "CONTRIBUTOR"));
        assertThat(app.membersOfThePot(pot.id()))
                .as("read on its own as well, because the two answers are one list")
                .hasSize(2);

        List<PotContributionView> contributions = app.contributionsTo(pot.id(), BRAM);
        assertThat(contributions)
                .as("the honest arithmetic survives the pot: what each of them carried is still "
                        + "there to read")
                .extracting(PotContributionView::name, PotContributionView::paidInAltogether)
                .containsExactly(tuple(ANKE, new BigDecimal("120.00")),
                        tuple(BRAM, new BigDecimal("80.00")));
        assertThat(contributions)
                .as("and nothing is still anybody's, because everything came back to somebody")
                .allSatisfy(contribution ->
                        assertThat(contribution.stillTheirs()).isEqualByComparingTo("0.00"));
        assertThat(contributions.get(0).pointsEarned())
                .as("the points they earned by saving into it are theirs and did not leave with "
                        + "the euros")
                .isEqualTo(120);

        assertThat(app.moneyMovementsOfThePot(pot.id(), ANKE))
                .as("the pot's ledger still reads back the way a personal one does: two payments "
                        + "in, the withdrawal they agreed on, and the two settlements that ended it")
                .extracting(MoneyMovementView::direction)
                .hasSize(5);
        assertThat(app.withdrawalProposalsOf(pot.id()))
                .as("and the decision they made together still names them")
                .extracting(WithdrawalProposalView::id, WithdrawalProposalView::state)
                .containsExactly(tuple(hers.id(), "APPROVED"));

        assertThat(app.potsOf(ANKE))
                .as("the pot is still one of hers, marked closed rather than gone from the list")
                .extracting(SharedPotView::id)
                .contains(pot.id());
        assertThat(app.potsOf(BRAM))
                .as("and still one of his, for the same reason")
                .extracting(SharedPotView::id)
                .contains(pot.id());
        assertThat(app.potsOf(BRAM).stream()
                .filter(listed -> pot.id().equals(listed.id()))
                .findFirst()
                .orElseThrow()
                .closedAt())
                .as("and the list says so, so that a finished pot does not read as one still being "
                        + "saved into")
                .isEqualTo(afterwards.closedAt());

        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), ANKE))
                .as("what they were saving for is no longer being projected towards, which is "
                        + "story 65 read from the other side")
                .isEmpty();
        assertThat(worktops.id())
                .as("the goal existed, which is what makes the emptiness above mean something")
                .isNotNull();
    }
}
