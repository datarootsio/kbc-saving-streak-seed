package io.dataroots.savingstreak.potcontributions;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.PotContributionView;
import io.dataroots.savingstreak.support.PotInvitationView;
import io.dataroots.savingstreak.support.SharedPotView;
import io.dataroots.savingstreak.support.WithdrawalProposalView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every member can read the pot's honest arithmetic: for each of them, how much they have paid in
 * altogether, how much of their money is still in the pot, and how many points their contributions
 * to this pot have earned them.
 *
 * <p><strong>The figures have to add up, and that is the whole of what this screen is for.</strong>
 * What is still each member's, added across the members, is what the pot holds — not approximately,
 * to the cent, because both figures are summed from the very same deposits. A shared pot that could
 * not be checked that way would be asking two people to trust a number, which is the situation the
 * feature exists to end.
 *
 * <p><strong>And paid-in and still-theirs come apart the moment money leaves.</strong> A withdrawal
 * draws the pot's oldest deposits down first, whoever paid them in, so one member's approved
 * withdrawal is spent out of another member's contribution. The second half of this test proves the
 * screen says so: what she paid in does not move, what is still hers falls to nothing, and the
 * figures still add up to what the pot now holds. That is the arithmetic the approval gate exists
 * to protect people from, read back on the screen that shows it.
 *
 * <p>Points are the member's own and the pot's never: they are what <em>this</em> pot's
 * contributions earned each of them, and they do not fall when a withdrawal spends the euros that
 * earned them. Both are asserted, because a points figure that followed the euros back out would be
 * a quiet second opinion about the rule that points are keyed per customer.
 *
 * <p>Its own application and its own clock, for the reason {@link AnApplicationWithAClockToMove}
 * documents: what a deposit earns is judged against everything its customer has ever saved, so a
 * test quoting a points figure to the point needs customers nothing has ever landed for. Doubly so
 * here — a pot on the shared database would hand every other test class a savings account identifier
 * that exists and that no customer holds.
 *
 * <p>One test method, because the story is one narrative: the figures before the withdrawal are what
 * the figures after it are read against.
 */
class EveryMemberSeesWhoHasContributedWhatApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    /** Somebody who joined to watch, so that a member who has paid nothing in can be read back. */
    private static String viewer;

    @BeforeAll
    static void startAnApplicationWhoseSaversHaveNoHistory() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-every-member-sees-who-contributed-what"));
        viewer = app.aCustomerOfItsOwn("a viewer of a pots contributions");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void what_each_member_paid_in_what_is_still_theirs_and_what_it_earned_them() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        joins(pot.id(), BRAM, "CONTRIBUTOR");
        joins(pot.id(), viewer, "VIEWER");

        // Her money goes in first, which is what makes it the oldest in the pot — and the oldest is
        // what a withdrawal takes, whoever paid it in.
        app.deposit(pot.savingsAccountId(), ANKE, "120.00");
        app.deposit(pot.savingsAccountId(), BRAM, "80.00");

        List<PotContributionView> contributions = app.contributionsTo(pot.id(), ANKE);

        assertThat(contributions)
                .as("every member of the pot has a row, in the order they joined it")
                .extracting(PotContributionView::name)
                .containsExactly(ANKE, BRAM, viewer);
        assertThat(hers(contributions).paidInAltogether()).isEqualByComparingTo("120.00");
        assertThat(hers(contributions).stillTheirs())
                .as("nothing has left the pot, so every euro she paid in is still hers")
                .isEqualByComparingTo("120.00");
        assertThat(hers(contributions).pointsEarned())
                .as("a euro saved is a point earned, at her own ordinary rate")
                .isEqualTo(120);
        assertThat(his(contributions).paidInAltogether()).isEqualByComparingTo("80.00");
        assertThat(his(contributions).stillTheirs()).isEqualByComparingTo("80.00");
        assertThat(his(contributions).pointsEarned()).isEqualTo(80);

        PotContributionView watching = rowFor(contributions, viewer);
        assertThat(watching.paidInAltogether())
                .as("a member who has paid nothing in is on the list with nought beside them, "
                        + "because absent would read as a member nobody can see")
                .isEqualByComparingTo("0.00");
        assertThat(watching.stillTheirs()).isEqualByComparingTo("0.00");
        assertThat(watching.pointsEarned()).isZero();
        assertThat(watching.role()).isEqualTo("VIEWER");

        assertThat(whatIsStillEverybodys(contributions))
                .as("**and here is the arithmetic**: what is still each member's, added up, is what "
                        + "the pot holds — both summed from the same deposits")
                .isEqualByComparingTo(app.potWith(pot.id()).moneyBalance());
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("200.00");

        assertThat(app.contributionsTo(pot.id(), viewer))
                .as("and a viewer reads the very same figures as the owner: the point of the screen "
                        + "is that there is one set of them")
                .isEqualTo(contributions);
        assertThat(app.contributionsTo(pot.id(), BRAM)).isEqualTo(contributions);

        // And now she takes a hundred and fifty out, with his approval — which is the only way she
        // can, because the oldest euros go first and thirty of them are his.
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "150.00");
        assertThat(proposed.whoseAssentItNeeds())
                .as("his money is in the pot, so this spends his and he gets a say")
                .isNotEmpty();
        assertThat(app.approve(pot.id(), proposed.id(), BRAM).state()).isEqualTo("APPROVED");

        List<PotContributionView> afterwards = app.contributionsTo(pot.id(), ANKE);

        assertThat(hers(afterwards).paidInAltogether())
                .as("what she paid in is history and history does not move")
                .isEqualByComparingTo("120.00");
        assertThat(hers(afterwards).stillTheirs())
                .as("but her deposit was the oldest, so all of it went: none of the pot is hers")
                .isEqualByComparingTo("0.00");
        assertThat(his(afterwards).paidInAltogether())
                .as("he paid in eighty and that is still what he paid in")
                .isEqualByComparingTo("80.00");
        assertThat(his(afterwards).stillTheirs())
                .as("and thirty of his euros went with hers, which is what he was asked about")
                .isEqualByComparingTo("50.00");
        assertThat(hers(afterwards).pointsEarned())
                .as("the points she earned are hers and do not leave with the euros")
                .isEqualTo(120);
        assertThat(his(afterwards).pointsEarned()).isEqualTo(80);

        assertThat(whatIsStillEverybodys(afterwards))
                .as("and the figures still add up after a withdrawal has drawn across two members")
                .isEqualByComparingTo(app.potWith(pot.id()).moneyBalance());
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("50.00");
    }

    /** What is still each member's, added across the members — the sum the pot's balance answers. */
    private static BigDecimal whatIsStillEverybodys(List<PotContributionView> contributions) {
        return contributions.stream()
                .map(PotContributionView::stillTheirs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static PotContributionView hers(List<PotContributionView> contributions) {
        return rowFor(contributions, ANKE);
    }

    private static PotContributionView his(List<PotContributionView> contributions) {
        return rowFor(contributions, BRAM);
    }

    private static PotContributionView rowFor(List<PotContributionView> contributions, String name) {
        return contributions.stream()
                .filter(contribution -> name.equals(contribution.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        name + " has no row in " + contributions));
    }

    private static void joins(long potId, String customerName, String role) {
        PotInvitationView sent = app.invite(potId, ANKE, customerName, role);
        app.accept(potId, sent.id(), customerName);
    }
}
