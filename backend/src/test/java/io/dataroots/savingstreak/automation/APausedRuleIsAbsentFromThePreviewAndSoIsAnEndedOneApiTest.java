package io.dataroots.savingstreak.automation;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.OccurrenceToComeView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A paused rule contributes nothing to the preview, and an ended one is absent from it.
 *
 * <p>The two are different sentences and the difference is the feature's one real idea. A paused
 * rule is still standing and still on the page — its holder means to have it back — but nothing falls
 * due while it is paused and a pause has no end date for a forecast to guess at, so promising its
 * transfers would be promising money its holder has told this application not to move. An ended rule
 * is not an instruction at all any more, and is not even in the list the preview walks.
 *
 * <p>Both halves matter. That a paused rule shows nothing is worth nothing unless resuming it brings
 * its days back, and that is asserted here too: a preview that dropped the rule for good would
 * satisfy the first half and be a rule nobody could resume.
 *
 * <p>The rule's own entry in the account's list is read beside the preview, because the pair is what
 * a page draws: a paused rule with no next day says {@code PAUSED} rather than merely showing a
 * blank, which is the difference between a page that can explain itself and one that leaves somebody
 * guessing why their saving stopped.
 */
class APausedRuleIsAbsentFromThePreviewAndSoIsAnEndedOneApiTest extends ApiIntegrationTest {

    private static final String FIFTY_EUROS = "50.00";

    private AnAccountWithRules account;

    @BeforeEach
    void anAccountWithTwoRulesToStopOneOf() {
        account = new AnAccountWithRules(http, "paused and ended in the preview");
    }

    @Test
    void a_paused_rule_contributes_nothing_and_says_it_is_paused_and_gets_its_days_back_on_resuming() {
        LocalDate today = account.theDateTheClockReads();
        SavingRuleView stays = account.leaveStanding(account.aFixedAmountEveryWeek(
                "The one that stays", today.plusDays(1).getDayOfWeek().name(), FIFTY_EUROS));
        SavingRuleView stopped = account.leaveStanding(account.aFixedAmountEveryWeek(
                "The one that is stopped", today.plusDays(2).getDayOfWeek().name(), FIFTY_EUROS));
        assertThat(rulesNamedIn(stays, stopped))
                .as("both rules are in the preview before either of them is stopped, or what "
                        + "follows is a test of a preview that was empty all along")
                .containsExactlyInAnyOrder(stays.id(), stopped.id());

        account.pause(stopped.id());

        assertThat(rulesNamedIn(stays, stopped))
                .as("nothing falls due while a rule is paused, so a preview that drew its days "
                        + "would be promising transfers its holder said not to make")
                .containsExactly(stays.id());
        SavingRuleView asItReads = theRule(stopped.id());
        assertThat(asItReads.state())
                .as("and the rule is still on the page saying why, rather than silently showing "
                        + "nothing")
                .isEqualTo("PAUSED");
        assertThat(asItReads.nextFiresOn()).isNull();
        assertThat(asItReads.nextMoves()).isNull();

        account.resume(stopped.id());

        assertThat(rulesNamedIn(stays, stopped))
                .as("resuming brings its days back, which is what makes a pause a stop rather than "
                        + "a deletion")
                .containsExactlyInAnyOrder(stays.id(), stopped.id());
        assertThat(theRule(stopped.id()).nextFiresOn()).isNotNull();
    }

    @Test
    void an_ended_rule_is_absent_from_the_preview_and_says_nothing_is_coming() {
        LocalDate today = account.theDateTheClockReads();
        SavingRuleView stays = account.leaveStanding(account.aFixedAmountEveryWeek(
                "The one that stays", today.plusDays(1).getDayOfWeek().name(), FIFTY_EUROS));
        SavingRuleView closed = account.leaveStanding(account.aFixedAmountEveryWeek(
                "The one that is ended", today.plusDays(2).getDayOfWeek().name(), FIFTY_EUROS));
        assertThat(rulesNamedIn(stays, closed))
                .containsExactlyInAnyOrder(stays.id(), closed.id());

        SavingRuleView ended = account.end(closed.id());

        assertThat(ended.state()).isEqualTo("ENDED");
        assertThat(ended.nextFiresOn())
                .as("an ended rule is a record rather than an instruction, so there is no next day "
                        + "to name")
                .isNull();
        assertThat(ended.nextMoves()).isNull();
        assertThat(rulesNamedIn(stays, closed))
                .as("and it is absent from the preview altogether — not shown with no days, but "
                        + "not walked at all")
                .containsExactly(stays.id());
        assertThat(account.endedRules())
                .as("while its record stays readable where a closed rule's record lives")
                .extracting(SavingRuleView::id)
                .contains(closed.id());
    }

    /** Which of the two rules this test left standing have anything at all in the preview. */
    private List<Long> rulesNamedIn(SavingRuleView... theOnesThisTestLeftStanding) {
        List<Long> mine = Arrays.stream(theOnesThisTestLeftStanding)
                .map(SavingRuleView::id)
                .toList();
        return account.preview().occurrences().stream()
                .map(OccurrenceToComeView::ruleId)
                .filter(mine::contains)
                .distinct()
                .toList();
    }

    private SavingRuleView theRule(long ruleId) {
        return account.rules().stream()
                .filter(rule -> rule.id() == ruleId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("rule " + ruleId + " is not on the account"));
    }
}
