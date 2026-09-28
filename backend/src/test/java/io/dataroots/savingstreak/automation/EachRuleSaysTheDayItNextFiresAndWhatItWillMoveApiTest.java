package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.OccurrenceToComeView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each rule in the account's list says the day it next fires and what it will move, which is the
 * question a customer opens the page with.
 *
 * <p>User story 47. A list of instructions that did not say when they next happen would send
 * somebody to a calendar to work out for themselves what "every month on the 31st" means in
 * February, and to a balance to work out what "everything above eight hundred" comes to.
 *
 * <p><strong>The pair is asserted against the preview's own first line, not only against figures
 * this test worked out.</strong> That is the claim worth making: the list entry and the twelve-month
 * preview come off one walk of one calendar, so a page showing "next: Monday, EUR 50,00" beside a
 * preview whose first line said something else would be this application disagreeing with itself
 * about a rule a customer is reading in one glance.
 */
class EachRuleSaysTheDayItNextFiresAndWhatItWillMoveApiTest extends ApiIntegrationTest {

    private static final String FIFTY_EUROS = "50.00";

    /** What the sweep is set to leave above its floor, so the figure it quotes is this exactly. */
    private static final BigDecimal THREE_HUNDRED = new BigDecimal("300.00");

    private AnAccountWithRules account;

    @BeforeEach
    void anAccountWithBothKindsOfRuleOnIt() {
        account = new AnAccountWithRules(http, "when each rule next fires");
    }

    @Test
    void a_fixed_amount_names_its_next_day_and_quotes_the_figure_it_will_move() {
        LocalDate itsDay = account.theDateTheClockReads().plusDays(3);

        SavingRuleView rule = account.leaveStanding(account.aFixedAmountEveryWeek(
                "Fifty a week", itsDay.getDayOfWeek().name(), FIFTY_EUROS));

        assertThat(rule.nextFiresOn())
                .as("the next day the customer's own instruction falls on, counted off this "
                        + "application's clock")
                .isEqualTo(itsDay);
        assertThat(rule.nextMoves().amount()).isEqualByComparingTo(FIFTY_EUROS);
        assertThat(rule.nextMoves().anIllustrationRatherThanAPromise())
                .as("a promise, because a fixed amount is the instruction itself")
                .isFalse();
        assertThat(theListed(rule.id()).nextFiresOn())
                .as("and the same answer when the rule is read back out of the account's list "
                        + "rather than out of the reply that created it")
                .isEqualTo(itsDay);
    }

    @Test
    void a_sweep_names_its_next_day_and_quotes_its_floor_with_an_illustration_beside_it() {
        LocalDate itsDay = account.theDateTheClockReads().plusDays(4);
        BigDecimal floor = account.currentAccountBalance().subtract(THREE_HUNDRED).setScale(2);

        SavingRuleView rule = account.leaveStanding(account.everythingAboveAFloorEveryWeek(
                "Sweep the rest", itsDay.getDayOfWeek().name(), floor.toPlainString()));

        assertThat(rule.nextFiresOn()).isEqualTo(itsDay);
        assertThat(rule.nextMoves().floor()).isEqualByComparingTo(floor);
        assertThat(rule.nextMoves().amount())
                .as("what today's balance would give, which is all anybody can honestly say about "
                        + "a sweep before the day comes")
                .isEqualByComparingTo(THREE_HUNDRED);
        assertThat(rule.nextMoves().anIllustrationRatherThanAPromise())
                .as("and it says so, so that a page cannot show it as a promise")
                .isTrue();
    }

    /**
     * The list and the preview are one answer. Two rules, so that the claim is about each rule's own
     * first day rather than about whichever day happens to come first on the account.
     */
    @Test
    void what_each_rule_says_is_the_first_line_the_preview_holds_for_it() {
        LocalDate today = account.theDateTheClockReads();
        SavingRuleView earlier = account.leaveStanding(account.aFixedAmountEveryWeek(
                "The earlier one", today.plusDays(2).getDayOfWeek().name(), FIFTY_EUROS));
        SavingRuleView later = account.leaveStanding(account.everythingAboveAFloorEveryWeek(
                "The later one", today.plusDays(5).getDayOfWeek().name(),
                account.currentAccountBalance().subtract(THREE_HUNDRED).setScale(2).toPlainString()));

        List<OccurrenceToComeView> coming = account.preview().occurrences();

        for (SavingRuleView rule : List.of(theListed(earlier.id()), theListed(later.id()))) {
            OccurrenceToComeView itsFirst = coming.stream()
                    .filter(line -> line.ruleId() == rule.id())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "rule " + rule.id() + " has nothing at all in the preview"));
            assertThat(rule.nextFiresOn())
                    .as("the day rule " + rule.id() + " says it next fires is the first day the "
                            + "preview holds for it — one walk of one calendar, or a page would "
                            + "show two answers to one question")
                    .isEqualTo(itsFirst.dueOn());
            assertThat(rule.nextMoves().amount())
                    .as("and the figure beside it is the figure that line quotes")
                    .isEqualByComparingTo(itsFirst.wouldMove().amount());
            assertThat(rule.nextMoves().anIllustrationRatherThanAPromise())
                    .isEqualTo(itsFirst.wouldMove().anIllustrationRatherThanAPromise());
        }
    }

    private SavingRuleView theListed(long ruleId) {
        return account.rules().stream()
                .filter(rule -> rule.id() == ruleId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("rule " + ruleId + " is not on the account"));
    }
}
