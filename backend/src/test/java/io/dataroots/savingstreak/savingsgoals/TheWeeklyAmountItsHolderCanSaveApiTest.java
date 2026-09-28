package io.dataroots.savingstreak.savingsgoals;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingCapacityView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A savings account carries the most its holder says they can put away in a week.
 *
 * <p>Declared, never derived. Nothing here pays anything in: the figure is a sentence the customer
 * said about themselves, and a test that had to stage six weeks of deposits to see it would be
 * testing the rolling average this feature deliberately does not have.
 *
 * <p><strong>The weekly minimum is written out here, and it used not to be.</strong> It was read off
 * {@code NewSavingsThisWeek.WEEKLY_MINIMUM}, because a test that wrote the number down would have
 * been the second place the figure lived. That constant is gone: the bank publishes the figure now,
 * version 1 of the scheme is seeded at EUR 50,00, and this application is running under the seeded
 * scheme. So the number is written down once, in one field, and what these tests assert is the
 * <em>relationship</em> — a euro under, exactly at, a euro over — rather than the figure. A training
 * exercise that reprices the week does it by publishing a version, which is a thing this test does
 * not do, so the figure it names stays the figure it is running under.
 *
 * <p>Its own customer, for the reason {@link AnAccountWithGoals} gives, except where the point being
 * made is about two accounts one customer holds — which is the seeded Anke, because she is the only
 * customer in the application who holds two.
 */
class TheWeeklyAmountItsHolderCanSaveApiTest extends ApiIntegrationTest {

    /**
     * What a week has to take in under the scheme this application is seeded at, written out once so
     * that the three figures below are one figure and two offsets from it.
     */
    private static final BigDecimal THE_WEEKLY_MINIMUM = new BigDecimal("50.00");

    /** A euro under what secures a week: declared and accepted, and warned about. */
    private static final String UNDER_THE_WEEKLY_MINIMUM =
            THE_WEEKLY_MINIMUM.subtract(BigDecimal.ONE).toPlainString();

    /** And a euro over it, which is the same declaration with nothing to say about streaks. */
    private static final String OVER_THE_WEEKLY_MINIMUM =
            THE_WEEKLY_MINIMUM.add(BigDecimal.ONE).toPlainString();

    private AnAccountWithGoals account;

    @BeforeEach
    void anAccountWhoseHolderHasSaidNothingYet() {
        account = new AnAccountWithGoals(http, "capacity");
    }

    /**
     * The absence is the point. A zero here would be the application putting a sentence in the
     * customer's mouth — "I can save nothing" — and then planning with it, so what is asserted is
     * that the figure is missing rather than that it is small.
     */
    @Test
    void before_it_is_ever_set_the_account_reports_no_capacity_rather_than_zero() {
        SavingCapacityView capacity = account.savingCapacity();

        assertThat(capacity.declared()).isFalse();
        assertThat(capacity.weeklyCapacity()).isNull();
        assertThat(capacity.declaredAt()).isNull();
        assertThat(capacity.aWeekIsNotSecuredAtThisRate()).isFalse();
    }

    @Test
    void a_capacity_that_was_declared_is_what_the_account_then_reports() {
        SavingCapacityView declared = account.canSave("120.00");

        assertThat(declared.weeklyCapacity()).isEqualByComparingTo("120.00");
        assertThat(declared.declared()).isTrue();
        assertThat(declared.declaredAt()).isNotNull();
        assertThat(account.savingCapacity().weeklyCapacity()).isEqualByComparingTo("120.00");
    }

    /**
     * Read back rather than only taken from the answer to the second declaration. What is being
     * asserted is that the new figure replaced the old one where it is kept, not that the reply
     * quoted what it was just handed.
     */
    @Test
    void a_capacity_can_be_changed_and_the_new_figure_replaces_the_old_one() {
        account.canSave("120.00");

        SavingCapacityView changed = account.canSave("200.00");

        assertThat(changed.weeklyCapacity()).isEqualByComparingTo("200.00");
        assertThat(account.savingCapacity().weeklyCapacity()).isEqualByComparingTo("200.00");
    }

    @Test
    void a_capacity_of_zero_is_refused_and_the_account_is_left_with_none() {
        ResponseEntity<JsonNode> refused = account.tryToDeclareCapacity("0.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(AnAccountWithGoals.reasonGivenBy(refused))
                .isEqualTo("A weekly saving capacity has to be an amount of more than zero, and 0.00 "
                        + "is not.");
        assertThat(account.savingCapacity().declared()).isFalse();
    }

    @Test
    void a_capacity_of_less_than_zero_is_refused() {
        ResponseEntity<JsonNode> refused = account.tryToDeclareCapacity("-25.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(AnAccountWithGoals.reasonGivenBy(refused)).contains("more than zero");
    }

    /**
     * Refused rather than rounded, like every other amount of money in this application: rounding
     * would move a figure nobody typed, and a plan built on it would be quoting the customer saying
     * something they did not say.
     */
    @Test
    void a_capacity_quoted_more_finely_than_money_is_refused() {
        ResponseEntity<JsonNode> refused = account.tryToDeclareCapacity("50.005");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(AnAccountWithGoals.reasonGivenBy(refused))
                .isEqualTo("An amount of money has at most two decimal places, and 50.005 has 3.");
    }

    /** A refusal changes nothing: the figure that was in force is still the one the account reports. */
    @Test
    void a_refused_capacity_leaves_the_one_already_declared_alone() {
        account.canSave("120.00");

        ResponseEntity<JsonNode> refused = account.tryToDeclareCapacity("0.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(account.savingCapacity().weeklyCapacity()).isEqualByComparingTo("120.00");
    }

    /**
     * Accepted, and told about. The customer is allowed to save less than a streak week asks for —
     * this application does not decide how much somebody is able to put away — but a capacity under
     * the minimum is them telling us their own plan never secures a week, and that is worth their
     * knowing.
     */
    @Test
    void a_capacity_below_the_weekly_minimum_is_accepted_and_says_a_week_is_not_secured_at_that_rate() {
        SavingCapacityView declared = account.canSave(UNDER_THE_WEEKLY_MINIMUM);

        assertThat(declared.declared()).isTrue();
        assertThat(declared.weeklyCapacity()).isEqualByComparingTo(UNDER_THE_WEEKLY_MINIMUM);
        assertThat(declared.aWeekIsNotSecuredAtThisRate()).isTrue();
        assertThat(declared.weeklyMinimum()).isEqualByComparingTo(THE_WEEKLY_MINIMUM);
        assertThat(account.savingCapacity().aWeekIsNotSecuredAtThisRate()).isTrue();
    }

    /**
     * Exactly the minimum is enough, which is the boundary the week itself is judged on: at least,
     * not more than.
     */
    @Test
    void a_capacity_at_the_weekly_minimum_is_accepted_and_reports_no_such_warning() {
        SavingCapacityView declared = account.canSave(THE_WEEKLY_MINIMUM.toPlainString());

        assertThat(declared.declared()).isTrue();
        assertThat(declared.aWeekIsNotSecuredAtThisRate()).isFalse();
        assertThat(account.savingCapacity().aWeekIsNotSecuredAtThisRate()).isFalse();
    }

    @Test
    void a_capacity_above_the_weekly_minimum_is_accepted_and_reports_no_such_warning() {
        SavingCapacityView declared = account.canSave(OVER_THE_WEEKLY_MINIMUM);

        assertThat(declared.aWeekIsNotSecuredAtThisRate()).isFalse();
    }

    /** And the warning goes away again when the customer says they can manage more. */
    @Test
    void raising_a_capacity_over_the_weekly_minimum_takes_the_warning_with_it() {
        account.canSave(UNDER_THE_WEEKLY_MINIMUM);

        SavingCapacityView raised = account.canSave(OVER_THE_WEEKLY_MINIMUM);

        assertThat(raised.aWeekIsNotSecuredAtThisRate()).isFalse();
        assertThat(account.savingCapacity().aWeekIsNotSecuredAtThisRate()).isFalse();
    }

    /**
     * Per savings account, and the same-customer case is the one worth asking: a figure kept against
     * the customer rather than against the account would pass every test about two strangers and
     * fail here. Anke holds two savings accounts, which is why she holds two.
     */
    @Test
    void a_capacity_on_one_account_leaves_another_the_same_customer_holds_unset() {
        SeededAccounts seeded = new SeededAccounts(http);
        long oneOfHers = seeded.savingsAccountOf(ANKE);
        long theOtherOfHers = seeded.otherSavingsAccountOf(ANKE);
        assertThat(oneOfHers).isNotEqualTo(theOtherOfHers);

        SavingCapacityView declared = AnAccountWithGoals.canSaveOn(oneOfHers, "300.00", http);

        assertThat(declared.weeklyCapacity()).isEqualByComparingTo("300.00");
        SavingCapacityView theOthers = AnAccountWithGoals.savingCapacityOf(theOtherOfHers, http);
        assertThat(theOthers.declared()).isFalse();
        assertThat(theOthers.weeklyCapacity()).isNull();
    }

    /**
     * An account nobody has heard of is refused rather than answered with the "nothing declared yet"
     * of an account that simply has no figure. The Goals module cannot tell the two apart — it reads
     * no other module — so the web layer asks Accounts first, in the words Accounts owns.
     */
    @Test
    void an_account_that_is_not_one_is_refused_rather_than_reported_as_having_no_capacity() {
        long noSuchAccount = new SeededAccounts(http).anIdNoSavingsAccountHas();

        ResponseEntity<JsonNode> read = http.getForEntity(
                "/api/savings-accounts/{id}/saving-capacity", JsonNode.class, noSuchAccount);

        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(AnAccountWithGoals.reasonGivenBy(read)).contains(String.valueOf(noSuchAccount));
    }
}
