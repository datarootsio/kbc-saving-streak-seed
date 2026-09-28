package io.dataroots.savingstreak.automation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.automation.AnAccountWithRules.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rule can be ended; it leaves the list of standing rules and everything it said stays readable.
 *
 * <p>The second half is the one worth the test. Ending a rule is a closing rather than a deletion,
 * because the deposits it made are already in an account and "what happened to my money last year"
 * has to stay answerable after the instruction that did it is gone. Nothing has fired yet in this
 * slice, so what survives is the rule itself — its name, its trigger, its day and its figure — which
 * is exactly the half a deposits ledger could never hold.
 *
 * <p>An ended rule is then refused every change, in the words and with the status a conflict gets:
 * a record that could be rewritten afterwards is not a record. The three payday tests below are the
 * other half of that sentence, and the less obvious half — a rule nobody can change is still being
 * rewritten if one of the things it says is derived from something that goes on moving.
 */
class ARuleIsEndedAndItsRecordSurvivesApiTest extends ApiIntegrationTest {

    private AnAccountWithRules account;

    @BeforeEach
    void anAccountWithARuleToEnd() {
        account = new AnAccountWithRules(http, "ended");
    }

    @Test
    void an_ended_rule_leaves_the_list_of_standing_rules() {
        SavingRuleView keeping = account.leaveStanding(
                account.aFixedAmountEveryWeek("Keeping this one", "MONDAY", "20.00"));
        SavingRuleView ending = account.leaveStanding(
                account.aFixedAmountEveryWeek("Ending this one", "FRIDAY", "50.00"));

        account.end(ending.id());

        assertThat(AnAccountWithRules.namesOf(account.rules())).containsExactly("Keeping this one");
        assertThat(account.rules()).singleElement()
                .extracting(SavingRuleView::id).isEqualTo(keeping.id());
    }

    @Test
    void an_ended_rules_record_survives_saying_everything_it_said() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryMonth("Month end", "31", "125.00"));

        SavingRuleView ended = account.end(rule.id());

        assertThat(ended.state()).isEqualTo("ENDED");
        assertThat(ended.endedAt()).isNotNull();
        assertThat(account.endedRules()).singleElement().satisfies(kept -> {
            assertThat(kept.id()).isEqualTo(rule.id());
            assertThat(kept.name()).isEqualTo("Month end");
            assertThat(kept.trigger()).isEqualTo("MONTHLY");
            assertThat(kept.dayOfMonth()).isEqualTo(31);
            assertThat(kept.howMuchMoves()).isEqualTo("A_FIXED_AMOUNT");
            assertThat(kept.amount()).isEqualByComparingTo("125.00");
            assertThat(kept.state()).isEqualTo("ENDED");
            assertThat(kept.createdAt()).isEqualTo(asTheDatabaseKeepsIt(rule.createdAt()));
        });
    }

    /**
     * A payday rule's record survives the income it used to follow moving, which is the one way a
     * rule ended in this application could still be rewritten afterwards.
     *
     * <p>A standing payday rule stores no day and reads its holder's declaration on every read, and
     * that is right: move payday once and every rule waiting for it follows. An ended rule has
     * nothing left to wait for, so ending it writes the day down. Without that, this test would read
     * 5 out of a rule that only ever moved on the 25th — and "what happened to my money last year"
     * would be answered with a day the deposits in the account do not agree with.
     */
    @Test
    void an_ended_payday_rule_keeps_the_day_it_was_moving_on_when_the_income_moves_afterwards() {
        account.declaresAnIncome("25", "3000.00");
        SavingRuleView rule = account.leaveStanding(
                account.everythingAboveAFloorOnPayday("Payday saver", "200.00"));
        assertThat(rule.dayOfMonth()).isEqualTo(25);

        SavingRuleView ended = account.end(rule.id());
        account.declaresAnIncome("5", "3000.00");

        assertThat(ended.dayOfMonth()).isEqualTo(25);
        assertThat(account.endedRules()).singleElement().satisfies(kept -> {
            assertThat(kept.trigger()).isEqualTo("ON_PAYDAY");
            assertThat(kept.dayOfMonth()).isEqualTo(25);
            // The same record throughout: the moment it was ended never moves either.
            assertThat(kept.endedAt()).isEqualTo(asTheDatabaseKeepsIt(ended.endedAt()));
        });
    }

    /**
     * And it survives the declaration being withdrawn altogether, which is the same failure with
     * nothing left to derive from: a rule that had moved on the 25th for a year would go back to
     * saying it moved on no day at all.
     */
    @Test
    void an_ended_payday_rule_keeps_its_day_even_when_the_income_is_withdrawn_altogether() {
        account.declaresAnIncome("25", "3000.00");
        SavingRuleView rule = account.leaveStanding(
                account.everythingAboveAFloorOnPayday("Payday saver", "200.00"));
        account.end(rule.id());

        account.withdrawsTheIncomeDeclaration();

        assertThat(account.endedRules()).singleElement()
                .extracting(SavingRuleView::dayOfMonth).isEqualTo(25);
    }

    /**
     * Ended by a holder who never said when they are paid, and the record says exactly that: no day.
     * The honest answer, and the one thing a frozen day must not turn into a made-up one.
     */
    @Test
    void a_payday_rule_ended_with_no_income_declared_keeps_no_day_at_all() {
        SavingRuleView rule = account.leaveStanding(
                account.everythingAboveAFloorOnPayday("Never got paid", "200.00"));

        SavingRuleView ended = account.end(rule.id());

        assertThat(ended.dayOfMonth()).isNull();
        account.declaresAnIncome("12", "3000.00");
        assertThat(account.endedRules()).singleElement()
                .extracting(SavingRuleView::dayOfMonth).isNull();
    }

    @Test
    void changing_a_rule_that_is_already_ended_is_refused_as_the_rule_being_ended() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Over and done with", "MONDAY", "50.00"));
        account.end(rule.id());

        ResponseEntity<JsonNode> response =
                account.tryToChange(rule.id(), Map.of("amount", "75.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(response)).contains("Over and done with", "ended");
        assertThat(account.endedRules()).singleElement()
                .extracting(SavingRuleView::amount)
                .satisfies(stillWorth -> assertThat(stillWorth).isEqualByComparingTo("50.00"));
    }

    @Test
    void ending_a_rule_that_is_already_ended_is_refused_as_the_rule_being_ended() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Over and done with", "MONDAY", "50.00"));
        SavingRuleView ended = account.end(rule.id());

        ResponseEntity<JsonNode> response = account.tryToEnd(rule.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(response)).contains("ended");
        // The moment it was ended is the first one, not the second: a record that could be
        // overwritten would be a record of when it was last asked about.
        assertThat(account.endedRules()).singleElement()
                .extracting(SavingRuleView::endedAt).isEqualTo(asTheDatabaseKeepsIt(ended.endedAt()));
    }

    /**
     * The same moment, to the precision this application actually keeps one at. A moment answered
     * straight out of a write carries the clock's own microseconds; the same moment read back has
     * been through SQLite, which holds it to the millisecond. Truncating here rather than comparing
     * loosely says which of the two is being asserted — that it is the same moment, and not merely a
     * nearby one.
     */
    private static Instant asTheDatabaseKeepsIt(Instant moment) {
        return moment.truncatedTo(ChronoUnit.MILLIS);
    }
}
