package io.dataroots.savingstreak.automation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A paused rule is still on its holder's list and says that it is paused; pausing it again, and
 * resuming a rule that was never paused, are accepted quietly.
 *
 * <p>User stories 30, 31 and 35. Two sentences that belong together because they are the same
 * request sent twice.
 *
 * <p><strong>The rule stays on the list, and that is the half somebody could get wrong.</strong> A
 * paused rule that vanished from the account's rules would be a rule nobody could resume, and
 * "paused" would be indistinguishable from "gone" at the only place a customer looks. It is also why
 * the state is asserted rather than the absence of a next day: a page that had to infer a pause from
 * a rule showing nothing to come could not tell it from one whose day has simply not arrived.
 *
 * <p><strong>Pressing a button twice is not a mistake worth a sentence.</strong> Both directions are
 * exercised, because they fail in different ways: a second pause that was accepted but re-stamped
 * the moment it began would quietly shorten the window of occurrences that are never made up, and a
 * resume of a rule that was never paused must leave its cursor exactly where it was or skip whatever
 * that rule was still owed.
 *
 * <p>That the second press leaves the <em>moment</em> alone is asserted where it can actually bite,
 * which is not here: this application's clock runs on, so two presses a request apart are two
 * moments a millisecond of rounding could hide. {@link ARulePausedThroughTwoOccurrencesMakesNeitherOfThemUpApiTest}
 * presses it again nine days later, on a clock it winds, and the difference is nine days.
 */
class APausedRuleSaysSoAndPressingTheButtonTwiceChangesNothingApiTest extends ApiIntegrationTest {

    private AnAccountWithRules account;

    @BeforeEach
    void anAccountWithARuleToPause() {
        account = new AnAccountWithRules(http, "paused");
    }

    @Test
    void a_paused_rule_stays_on_the_list_and_says_it_is_paused() {
        SavingRuleView firing = account.leaveStanding(
                account.aFixedAmountEveryWeek("Still going", "MONDAY", "20.00"));
        SavingRuleView stopping = account.leaveStanding(
                account.aFixedAmountEveryWeek("Stopped for a bit", "FRIDAY", "50.00"));

        SavingRuleView paused = account.pause(stopping.id());

        assertThat(paused.state())
                .as("a rule its holder stopped says so in a word, rather than leaving a page to "
                        + "infer a pause from a rule that happens to show nothing to come")
                .isEqualTo("PAUSED");
        assertThat(paused.pausedAt())
                .as("and carries the moment it was stopped, which is what a pause is recorded as")
                .isNotNull();
        assertThat(AnAccountWithRules.namesOf(account.rules()))
                .as("a paused rule is standing rather than gone: it is still on the list its holder "
                        + "reads, or there would be nothing there for them to resume")
                .containsExactly("Still going", "Stopped for a bit");
        assertThat(account.rules())
                .filteredOn(rule -> rule.id().equals(stopping.id()))
                .singleElement()
                .satisfies(read -> {
                    assertThat(read.state()).isEqualTo("PAUSED");
                    assertThat(read.pausedAt())
                            .as("read back off the record rather than only answered to whoever "
                                    + "pressed the button")
                            .isEqualTo(asTheDatabaseKeepsIt(paused.pausedAt()));
                });
        assertThat(account.rules())
                .filteredOn(rule -> rule.id().equals(firing.id()))
                .singleElement()
                .satisfies(read -> {
                    assertThat(read.state())
                            .as("while the rule beside it is untouched, which is what says pausing "
                                    + "is about one rule rather than about the account")
                            .isEqualTo("LIVE");
                    assertThat(read.pausedAt())
                            .as("a rule that was never paused carries no such moment")
                            .isNull();
                });
        assertThat(account.endedRules())
                .as("pausing is not ending: nothing has left for the record")
                .isEmpty();
    }

    @Test
    void pausing_a_paused_rule_is_accepted_quietly() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Twice pressed", "TUESDAY", "30.00"));
        account.pause(rule.id());

        ResponseEntity<JsonNode> again = account.tryToPause(rule.id());

        assertThat(again.getStatusCode())
                .as("a button pressed twice is not a mistake worth a sentence")
                .isEqualTo(HttpStatus.OK);
        assertThat(again.getBody().path("state").asText())
                .as("and it answers with what is now true, which is what the customer asked for")
                .isEqualTo("PAUSED");
    }

    @Test
    void resuming_a_rule_that_was_never_paused_is_accepted_quietly_and_leaves_it_exactly_as_it_was() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("Never stopped", "WEDNESDAY", "40.00"));

        ResponseEntity<JsonNode> resumed = account.tryToResume(rule.id());

        assertThat(resumed.getStatusCode())
                .as("resuming a rule that is already running is the same button pressed twice")
                .isEqualTo(HttpStatus.OK);
        assertThat(resumed.getBody().path("state").asText()).isEqualTo("LIVE");
        assertThat(resumed.getBody().path("pausedAt").isNull())
                .as("a rule that was never paused has no moment it was paused at, before or after")
                .isTrue();
        assertThat(account.rules())
                .filteredOn(read -> read.id().equals(rule.id()))
                .singleElement()
                .satisfies(read -> {
                    assertThat(read.state()).isEqualTo("LIVE");
                    assertThat(read.createdAt())
                            .as("and nothing about it moved")
                            .isEqualTo(asTheDatabaseKeepsIt(rule.createdAt()));
                });
    }

    @Test
    void a_rule_paused_and_resumed_is_live_again_and_no_longer_says_when_it_was_paused() {
        SavingRuleView rule = account.leaveStanding(
                account.aFixedAmountEveryWeek("There and back", "THURSDAY", "25.00"));
        account.pause(rule.id());

        SavingRuleView resumed = account.resume(rule.id());

        assertThat(resumed.state()).isEqualTo("LIVE");
        assertThat(resumed.pausedAt())
                .as("a rule that is running again is not in a pause, so it carries no moment one "
                        + "began: what the pause covered is the module's record rather than "
                        + "something a rule as it reads today goes on saying")
                .isNull();
        assertThat(account.rules())
                .filteredOn(read -> read.id().equals(rule.id()))
                .singleElement()
                .satisfies(read -> assertThat(read.state()).isEqualTo("LIVE"));
    }

    /**
     * The same moment, to the precision this application actually keeps one at. A moment answered
     * straight out of a write carries the clock's own microseconds; the same moment read back has
     * been through SQLite, which holds it to the millisecond.
     */
    private static Instant asTheDatabaseKeepsIt(Instant moment) {
        return moment.truncatedTo(ChronoUnit.MILLIS);
    }
}
