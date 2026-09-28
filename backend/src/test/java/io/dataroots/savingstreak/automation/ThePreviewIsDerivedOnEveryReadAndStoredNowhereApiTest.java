package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.OccurrenceToComeView;
import io.dataroots.savingstreak.support.RulePreviewView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The preview is derived on every read and stored nowhere: change a balance or change a rule, and
 * the next read says something else, with nothing to invalidate.
 *
 * <p>This is the acceptance criterion for a decision rather than for a feature, and it is the one
 * {@code AReallocationWorthSuggesting} argues at length: a stored projection goes stale the moment
 * anything changes and then needs invalidation rules that are themselves a source of bugs. A test
 * cannot see that nothing was cached; what it can see is the consequence, which is that two reads
 * with a change between them differ — and differ without anything having been told to forget.
 *
 * <p>Both halves are here because they go stale for different reasons. A sweep's illustration goes
 * stale when money moves and no rule was touched at all; a rule's figure goes stale when the rule is
 * re-worded and no money moved. A cache keyed on either one alone would pass one of these and fail
 * the other.
 */
class ThePreviewIsDerivedOnEveryReadAndStoredNowhereApiTest extends ApiIntegrationTest {

    private static final BigDecimal THREE_HUNDRED = new BigDecimal("300.00");

    private static final String A_HUNDRED_EUROS = "100.00";

    private AnAccountWithRules account;

    @BeforeEach
    void anAccountToChangeUnderTheReader() {
        account = new AnAccountWithRules(http, "derived on every read");
    }

    @Test
    void moving_money_out_of_the_current_account_changes_what_the_next_read_says_a_sweep_would_move() {
        LocalDate itsDay = account.theDateTheClockReads().plusDays(1);
        BigDecimal floor = account.currentAccountBalance().subtract(THREE_HUNDRED).setScale(2);
        SavingRuleView sweep = account.leaveStanding(account.everythingAboveAFloorEveryWeek(
                "Sweep the rest", itsDay.getDayOfWeek().name(), floor.toPlainString()));
        assertThat(firstLineFor(sweep).wouldMove().amount())
                .as("the illustration before anything moved")
                .isEqualByComparingTo(THREE_HUNDRED);

        account.depositsByHand(A_HUNDRED_EUROS);

        assertThat(firstLineFor(sweep).wouldMove().amount())
                .as("a hundred euros left the current account, so a hundred euros less is above the "
                        + "floor — and nothing anywhere had to be told to forget the old figure")
                .isEqualByComparingTo(THREE_HUNDRED.subtract(new BigDecimal(A_HUNDRED_EUROS)));
        assertThat(theListed(sweep.id()).nextMoves().amount())
                .as("and the rule's own entry moved with it, because it is the same derivation")
                .isEqualByComparingTo(THREE_HUNDRED.subtract(new BigDecimal(A_HUNDRED_EUROS)));
    }

    @Test
    void changing_a_rule_changes_what_the_next_read_says_it_will_do() {
        LocalDate itsDay = account.theDateTheClockReads().plusDays(1);
        LocalDate itsNewDay = account.theDateTheClockReads().plusDays(3);
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Fifty a week", itsDay.getDayOfWeek().name(), "50.00"));
        assertThat(firstLineFor(rule).wouldMove().amount()).isEqualByComparingTo("50.00");
        assertThat(firstLineFor(rule).dueOn()).isEqualTo(itsDay);

        account.change(rule.id(), Map.of(
                "amount", A_HUNDRED_EUROS, "dayOfWeek", itsNewDay.getDayOfWeek().name()));

        assertThat(firstLineFor(rule).wouldMove().amount())
                .as("the figure the customer now says, on the very next read")
                .isEqualByComparingTo(A_HUNDRED_EUROS);
        assertThat(firstLineFor(rule).dueOn())
                .as("and the day they now say, which is a second thing a stored projection would "
                        + "have gone on promising")
                .isEqualTo(itsNewDay);
    }

    private OccurrenceToComeView firstLineFor(SavingRuleView rule) {
        RulePreviewView preview = account.preview();
        return preview.occurrences().stream()
                .filter(line -> line.ruleId() == rule.id())
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "rule " + rule.id() + " has nothing at all in the preview"));
    }

    private SavingRuleView theListed(long ruleId) {
        return account.rules().stream()
                .filter(rule -> rule.id() == ruleId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("rule " + ruleId + " is not on the account"));
    }
}
