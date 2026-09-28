package io.dataroots.savingstreak.sharedpots;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whose approval a withdrawal from a shared pot needs: every other member who still has money in it,
 * and nobody else.
 *
 * <p>A direct unit test rather than an API one, and it is the single exception this feature's spec
 * sanctions — the exception {@code HowAnAmountIsSplitTest}, {@code WhenABillIsDueTest} and
 * {@code TheMonthAMomentFallsInTest} already take. This is a function of its arguments with no
 * database, no clock and no HTTP in it, and the table of cases it has to be right across — the sole
 * contributor, the member settled down to nothing, the viewer, the proposer who is asked about their
 * own proposal — is a morning of deposits, settlements and wound clocks over HTTP and four lines
 * each here.
 *
 * <p><strong>Every one of these cases is asserted over HTTP as well.</strong> The API tests in
 * {@code potwithdrawals} are where the behaviour lives; this is for the table. A rule that is right
 * here and wired up wrongly would pass this class and fail those, which is the arrangement those
 * three precedents describe.
 *
 * <p>The figures here are what is still each member's <em>in this pot</em> — the sum of what remains
 * of their own deposits into its savings account — and not what they hold in savings altogether.
 * That distinction is the one the whole gate turns on, and it is kept where the figures are read.
 */
class WhoseAssentAWithdrawalNeedsTest {

    private static final Instant WHENEVER = Instant.parse("2026-01-05T09:00:00Z");

    private static final APotMember ANKE = new APotMember(1L, "Anke Peeters", PotRole.OWNER, WHENEVER);
    private static final APotMember BRAM =
            new APotMember(2L, "Bram De Vos", PotRole.CONTRIBUTOR, WHENEVER);
    private static final APotMember CHARLOTTE =
            new APotMember(3L, "Charlotte Janssens", PotRole.CONTRIBUTOR, WHENEVER);

    // ------------------------------------------------- the member whose euros are at stake

    @Test
    void every_other_member_with_money_in_the_pot_has_to_approve_it() {
        List<APotMember> mustAnswer = WhoseAssentAWithdrawalNeeds.of(
                ANKE.customerId(), List.of(ANKE, BRAM),
                stillTheirs(ANKE, "300.00", BRAM, "200.00"));

        assertThat(mustAnswer)
                .as("Bram's euros are in the pot, and a withdrawal draws the oldest deposits down "
                        + "first whoever paid them in — so it can spend his, and he gets a veto")
                .containsExactly(BRAM);
    }

    @Test
    void so_does_every_one_of_them_when_there_are_several() {
        List<APotMember> mustAnswer = WhoseAssentAWithdrawalNeeds.of(
                ANKE.customerId(), List.of(ANKE, BRAM, CHARLOTTE),
                stillTheirs(ANKE, "300.00", BRAM, "200.00", CHARLOTTE, "0.01"));

        assertThat(mustAnswer)
                .as("a single cent is still somebody's money, and the members come back in the "
                        + "order they were given, which is the order they joined")
                .containsExactly(BRAM, CHARLOTTE);
    }

    // --------------------------------------------------------- the sole contributor

    @Test
    void the_only_member_with_money_in_the_pot_needs_nobodys_approval() {
        List<APotMember> mustAnswer = WhoseAssentAWithdrawalNeeds.of(
                ANKE.customerId(), List.of(ANKE, BRAM), stillTheirs(ANKE, "300.00"));

        assertThat(mustAnswer)
                .as("Bram is in the pot and has never paid into it, so there is nothing of his to "
                        + "spend and nobody to protect: the gate exists only where somebody is")
                .isEmpty();
    }

    @Test
    void a_pot_with_one_member_in_it_needs_nobodys_approval_either() {
        List<APotMember> mustAnswer = WhoseAssentAWithdrawalNeeds.of(
                ANKE.customerId(), List.of(ANKE), stillTheirs(ANKE, "300.00"));

        assertThat(mustAnswer).isEmpty();
    }

    // ------------------------------------------------ the member settled down to nothing

    @Test
    void a_member_whose_money_has_all_gone_is_not_asked() {
        List<APotMember> mustAnswer = WhoseAssentAWithdrawalNeeds.of(
                ANKE.customerId(), List.of(ANKE, BRAM),
                stillTheirs(ANKE, "300.00", BRAM, "0.00"));

        assertThat(mustAnswer)
                .as("Bram paid in and has since been settled down to nothing: he has no euros left "
                        + "in this pot, so a withdrawal cannot spend any of his")
                .isEmpty();
    }

    @Test
    void a_member_nobody_has_a_figure_for_is_a_member_with_nothing() {
        // The two ways a member comes to have nothing are a member who never paid in and a member
        // whose deposits have all been drawn down — and the second of those leaves no row in the
        // read the figures come from, so it arrives as an absence rather than as nought. Both are
        // the same fact and both are answered the same way.
        List<APotMember> mustAnswer = WhoseAssentAWithdrawalNeeds.of(
                ANKE.customerId(), List.of(ANKE, BRAM, CHARLOTTE),
                stillTheirs(ANKE, "300.00", CHARLOTTE, "50.00"));

        assertThat(mustAnswer).containsExactly(CHARLOTTE);
    }

    // ------------------------------------------------------------------- the viewer

    @Test
    void a_viewer_is_never_asked() {
        APotMember watching = new APotMember(4L, "Karel Maes", PotRole.VIEWER, WHENEVER);

        List<APotMember> mustAnswer = WhoseAssentAWithdrawalNeeds.of(
                ANKE.customerId(), List.of(ANKE, watching), stillTheirs(ANKE, "300.00"));

        assertThat(mustAnswer)
                .as("a viewer cannot pay in, so there is nothing of theirs in the pot — which is "
                        + "what \"viewer\" means")
                .isEmpty();
    }

    @Test
    void a_viewer_is_not_asked_even_if_there_is_money_against_their_name() {
        // A viewer cannot pay in, so the one way this arises is somebody being demoted after
        // contributing. The rule is unconditional all the same: a viewer is never asked. What a pot
        // owes a demoted contributor is a question about demotion and about settlement, and it
        // belongs to the slices that own those rather than to this one.
        APotMember demoted = new APotMember(4L, "Karel Maes", PotRole.VIEWER, WHENEVER);

        List<APotMember> mustAnswer = WhoseAssentAWithdrawalNeeds.of(
                ANKE.customerId(), List.of(ANKE, demoted),
                stillTheirs(ANKE, "300.00", demoted, "75.00"));

        assertThat(mustAnswer).isEmpty();
    }

    // -------------------------------------------------------------- the proposer themselves

    @Test
    void the_proposer_is_never_asked_about_their_own_proposal() {
        List<APotMember> mustAnswer = WhoseAssentAWithdrawalNeeds.of(
                BRAM.customerId(), List.of(ANKE, BRAM),
                stillTheirs(ANKE, "300.00", BRAM, "200.00"));

        assertThat(mustAnswer)
                .as("proposing is asking, and a proposal that counted its own author's assent "
                        + "would be a vote for yourself")
                .containsExactly(ANKE);
    }

    @Test
    void an_owner_is_asked_like_anybody_else_whose_money_is_in_the_pot() {
        List<APotMember> mustAnswer = WhoseAssentAWithdrawalNeeds.of(
                BRAM.customerId(), List.of(ANKE, BRAM, CHARLOTTE),
                stillTheirs(ANKE, "300.00", BRAM, "200.00", CHARLOTTE, "100.00"));

        assertThat(mustAnswer)
                .as("the gate is about whose euros are at stake and not about rank: an owner who "
                        + "has paid in is asked, and being an owner is neither a veto nor a pass")
                .containsExactly(ANKE, CHARLOTTE);
    }

    // ------------------------------------------------------------------- a pot with nothing in it

    @Test
    void a_pot_nobody_has_paid_into_needs_nobodys_approval() {
        List<APotMember> mustAnswer = WhoseAssentAWithdrawalNeeds.of(
                ANKE.customerId(), List.of(ANKE, BRAM, CHARLOTTE), Map.of());

        assertThat(mustAnswer)
                .as("there is nothing to take out and nobody's euros to protect — whether such a "
                        + "withdrawal is refused at all is a question about the amount, and it is "
                        + "answered where amounts are")
                .isEmpty();
    }

    /**
     * What is still each member's, as the pairs a test names them in — a member left out of the list
     * has nothing, which is exactly how the read this stands for reports one.
     *
     * <p>A {@code LinkedHashMap} so that the order a test wrote its members in is the order they are
     * iterated in, and a rule that happened to depend on a map's iteration order would fail the same
     * way on every run rather than on some of them.
     */
    private static Map<Long, BigDecimal> stillTheirs(Object... membersAndAmounts) {
        Map<Long, BigDecimal> figures = new LinkedHashMap<>();
        for (int at = 0; at < membersAndAmounts.length; at += 2) {
            APotMember member = (APotMember) membersAndAmounts[at];
            figures.put(member.customerId(), new BigDecimal((String) membersAndAmounts[at + 1]));
        }
        return figures;
    }
}
