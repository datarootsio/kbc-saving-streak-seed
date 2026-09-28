package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rule left standing against a savings account is listed back saying everything it says.
 *
 * <p>This is the whole of what this slice claims: nothing fires yet, so what a rule <em>is</em> —
 * its name, what makes it move, which day that is, how much moves and whether it is still standing —
 * is the only thing there is to observe, and it is observed the way the frontend will observe it.
 *
 * <p>The three triggers are asserted separately because each needs a different day, and the payday
 * one is the one worth staring at: it stores no day at all, and reads its holder's declared income
 * on every read. That is why moving the income moves the rule, which is asserted here rather than
 * left to be true.
 */
class ARuleIsLeftStandingOnASavingsAccountApiTest extends ApiIntegrationTest {

    private AnAccountWithRules account;

    @BeforeEach
    void anAccountToLeaveRulesStandingOn() {
        account = new AnAccountWithRules(http, "left standing");
    }

    @Test
    void a_rule_is_listed_back_with_its_name_its_trigger_and_day_how_much_moves_and_its_state() {
        SavingRuleView left = account.leaveStanding(
                account.aFixedAmountEveryWeek("Fifty a week", "MONDAY", "50.00"));

        List<SavingRuleView> standing = account.rules();

        assertThat(standing).hasSize(1);
        SavingRuleView listed = standing.get(0);
        assertThat(listed.id()).isEqualTo(left.id());
        assertThat(listed.name()).isEqualTo("Fifty a week");
        assertThat(listed.trigger()).isEqualTo("WEEKLY");
        assertThat(listed.dayOfWeek()).isEqualTo("MONDAY");
        assertThat(listed.howMuchMoves()).isEqualTo("A_FIXED_AMOUNT");
        assertThat(listed.amount()).isEqualByComparingTo("50.00");
        assertThat(listed.state()).isEqualTo("LIVE");
        assertThat(listed.savingsAccountId()).isEqualTo(account.id());
        assertThat(listed.fromCurrentAccountId()).isEqualTo(account.currentAccountId());
        assertThat(listed.createdAt()).isNotNull();
        assertThat(listed.endedAt()).isNull();
    }

    /** An account nobody has automated anything on has no rules, which is an answer and not a 404. */
    @Test
    void an_account_with_no_rules_says_so() {
        assertThat(account.rules()).isEmpty();
        assertThat(account.endedRules()).isEmpty();
    }

    @Test
    void a_weekly_rule_takes_a_day_of_the_week_and_no_day_of_the_month() {
        SavingRuleView weekly = account.leaveStanding(
                account.aFixedAmountEveryWeek("Every Friday", "FRIDAY", "20.00"));

        assertThat(weekly.trigger()).isEqualTo("WEEKLY");
        assertThat(weekly.dayOfWeek()).isEqualTo("FRIDAY");
        assertThat(weekly.dayOfMonth()).isNull();
    }

    @Test
    void a_monthly_rule_takes_a_day_of_the_month_and_no_day_of_the_week() {
        SavingRuleView monthly = account.leaveStanding(
                account.aFixedAmountEveryMonth("The first", "1", "300.00"));

        assertThat(monthly.trigger()).isEqualTo("MONTHLY");
        assertThat(monthly.dayOfMonth()).isEqualTo(1);
        assertThat(monthly.dayOfWeek()).isNull();
    }

    /**
     * The 31st is kept as the 31st rather than clamped when it is written down. Which day a shorter
     * month lands it on is arithmetic made when the rule fires, so a rule set in a long month must
     * not carry February's answer around with it.
     */
    @Test
    void a_monthly_rule_set_for_the_thirty_first_keeps_the_thirty_first() {
        SavingRuleView monthly = account.leaveStanding(
                account.aFixedAmountEveryMonth("Month end", "31", "100.00"));

        assertThat(monthly.dayOfMonth()).isEqualTo(31);
    }

    /**
     * The payday rule's day is its holder's declaration, read on every read, which is why nobody is
     * asked for it twice.
     */
    @Test
    void a_payday_rule_takes_its_day_from_the_holders_declared_income() {
        account.declaresAnIncome("25", "2500.00");

        SavingRuleView onPayday = account.leaveStanding(
                account.everythingAboveAFloorOnPayday("Sweep on payday", "800.00"));

        assertThat(onPayday.trigger()).isEqualTo("ON_PAYDAY");
        assertThat(onPayday.dayOfMonth()).isEqualTo(25);
        assertThat(onPayday.dayOfWeek()).isNull();
    }

    /**
     * And because it is read rather than copied, moving payday moves every rule waiting for it. A
     * day stored on the rule would be a second answer to a question that has one, and the two would
     * eventually disagree.
     */
    @Test
    void a_payday_rule_follows_that_income_when_the_customer_is_paid_on_a_different_day() {
        account.declaresAnIncome("25", "2500.00");
        SavingRuleView onPayday = account.leaveStanding(
                account.everythingAboveAFloorOnPayday("Sweep on payday", "800.00"));
        assertThat(onPayday.dayOfMonth()).isEqualTo(25);

        account.declaresAnIncome("28", "2500.00");

        assertThat(account.rules())
                .filteredOn(rule -> rule.id().equals(onPayday.id()))
                .singleElement()
                .extracting(SavingRuleView::dayOfMonth)
                .isEqualTo(28);
    }

    /**
     * Nobody having declared an income is an absence rather than a day. A page reading a zero or a
     * first-of-the-month here would tell the customer their rule moves on a day nobody named.
     */
    @Test
    void a_payday_rule_has_no_day_at_all_until_an_income_is_declared() {
        SavingRuleView onPayday = account.leaveStanding(
                account.everythingAboveAFloorOnPayday("Waiting for a salary", "500.00"));

        assertThat(onPayday.dayOfMonth()).isNull();
    }

    /**
     * The two kinds of amount, and the point of asserting both in one test is the field that is
     * empty in each: a rule says which of the two it is, and the figure it does not use is absent
     * rather than zero.
     */
    @Test
    void a_rule_moves_either_a_fixed_amount_or_everything_above_a_floor_and_says_which() {
        SavingRuleView fixed = account.leaveStanding(
                account.aFixedAmountEveryWeek("Fifty a week", "TUESDAY", "50.00"));
        SavingRuleView sweep = account.leaveStanding(
                account.everythingAboveAFloorOnPayday("Whatever is left", "800.00"));

        assertThat(fixed.howMuchMoves()).isEqualTo("A_FIXED_AMOUNT");
        assertThat(fixed.amount()).isEqualByComparingTo("50.00");
        assertThat(fixed.floor()).isNull();

        assertThat(sweep.howMuchMoves()).isEqualTo("EVERYTHING_ABOVE");
        assertThat(sweep.floor()).isEqualByComparingTo("800.00");
        assertThat(sweep.amount()).isNull();
    }

    /** A floor of nothing is a customer saying "sweep the lot", which is a thing somebody can mean. */
    @Test
    void a_sweep_down_to_nothing_is_a_rule_this_application_keeps() {
        SavingRuleView sweep = account.leaveStanding(
                account.everythingAboveAFloorOnPayday("Empty it", "0.00"));

        assertThat(sweep.floor()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Several rules stand at once, in the order they were written, which is the order they fire in. */
    @Test
    void rules_are_listed_in_the_order_their_holder_wrote_them() {
        account.leaveStanding(account.aFixedAmountEveryWeek("First", "MONDAY", "10.00"));
        account.leaveStanding(account.aFixedAmountEveryMonth("Second", "15", "20.00"));
        account.leaveStanding(account.everythingAboveAFloorOnPayday("Third", "100.00"));

        assertThat(AnAccountWithRules.namesOf(account.rules()))
                .containsExactly("First", "Second", "Third");
    }
}
