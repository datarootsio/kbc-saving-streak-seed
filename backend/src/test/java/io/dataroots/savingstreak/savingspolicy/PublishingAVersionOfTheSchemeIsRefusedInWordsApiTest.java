package io.dataroots.savingstreak.savingspolicy;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SchemeView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingspolicy.ASchemeSomebodyAdministers.reasonGivenBy;
import static io.dataroots.savingstreak.savingspolicy.ASchemeSomebodyAdministers.theSameSchemeAgain;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every way a version of the scheme can be refused, in the module's own sentence, with nothing
 * written down.
 *
 * <p><strong>Every test here asserts both halves.</strong> A refusal that came back with the right
 * status and wrote the row anyway would be the worst bug this feature could have — a scheme
 * everybody the bank has is about to be judged under, written by a request the application said no
 * to — so each of these reads the history afterwards and insists it is exactly the one seeded
 * version it was. The shared {@link #nothingWasPublished} does the second half, so no test can
 * forget it.
 *
 * <p><strong>It needs no ordering, unlike the test next door</strong>, because nothing here is
 * supposed to change anything: that is the point of every one of them. It still gets an application
 * and a file of its own, because a test whose subject is "nothing was written" cannot share a
 * database with tests that write.
 *
 * <p><strong>One refusal is deliberately not here.</strong> A Monday that comes before one a version
 * has already been announced for needs an announcement to exist first, which is a publish, which is
 * the one thing this file is arranged never to do. It is asserted next door, where publishing is
 * what the tests are for.
 *
 * <p>The sentences are asserted on for the part the person who typed them can act on — the figure
 * they typed, the box it came out of, the Monday that would have been accepted — rather than word
 * for word. A test pinning a whole sentence is a test that fails when somebody improves the
 * wording, which teaches the next person to stop improving the wording.
 */
class PublishingAVersionOfTheSchemeIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-scheme-publishing-refused");

    private static ASchemeSomebodyAdministers bank;

    @BeforeAll
    static void startABankSomebodyRuns() {
        bank = new ASchemeSomebodyAdministers(DATABASE);
    }

    @AfterAll
    static void stopIt() {
        if (bank != null) {
            bank.close();
        }
    }

    // The line saying what changed ------------------------------------------------------------

    /**
     * The one line saying what changed is required, because it applies to everybody from its Monday
     * and a customer whose rate moved will come looking for the reason.
     *
     * <p>Required on every version of the scheme, where a product's first one is allowed to say
     * nothing: nobody opted into the scheme, so there is no version of it somebody chose and can be
     * assumed to understand already.
     */
    @Test
    void a_version_that_does_not_say_what_changed_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.remove("whatChanged");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("what changed");
        nothingWasPublished();
    }

    /** Blank counts as absent, because a space is what somebody fills a required box with. */
    @Test
    void a_line_saying_what_changed_that_is_only_spaces_is_refused_too() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("whatChanged", "   ");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("what changed");
        nothingWasPublished();
    }

    // The Monday it takes effect --------------------------------------------------------------

    /**
     * A version needs the day it takes effect, and next Monday is not assumed in its place.
     *
     * <p>Which Monday a repricing starts on is the most consequential thing on this form, and a
     * layer that filled it in would be publishing a scheme on a date nobody chose.
     */
    @Test
    void a_version_with_no_day_it_takes_effect_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.remove("effectiveFrom");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("Monday it takes effect");
        nothingWasPublished();
    }

    /**
     * A day that is not a Monday is refused, because a savings week runs Monday to Sunday and a
     * version starting mid-week would judge one week under two schemes.
     *
     * <p>The day tried is the Tuesday after the Monday that would have been accepted, so that the
     * only thing wrong with it is the day of the week — and the sentence names the Monday, because
     * the fix is one edit.
     */
    @Test
    void a_day_that_is_not_a_monday_is_refused() {
        LocalDate aTuesday = bank.theNextMondayStillToCome().plusDays(1);
        assertThat(aTuesday.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        Map<String, Object> form = theSameSchemeAgain(bank.theSchemeInForce(), aTuesday,
                "A change announced for the middle of a week, which is not a thing a week can "
                        + "survive.");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains(aTuesday.toString(), "Tuesday", "Monday",
                        bank.theNextMondayStillToCome().toString());
        nothingWasPublished();
    }

    /**
     * Today is refused, however Monday-shaped it is: the week it would land in has already started,
     * and a week already being judged cannot be re-judged halfway through.
     *
     * <p>Asserted against whatever day the application thinks it is, rather than against a Monday
     * arranged for, because "today" is the case that matters and every day of the week is a today.
     */
    @Test
    void a_version_taking_effect_today_is_refused() {
        LocalDate today = bank.theDateTheClockReads();
        Map<String, Object> form = theSameSchemeAgain(bank.theSchemeInForce(), today,
                "A change starting this morning, in the middle of a week already running.");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains(today.toString());
        nothingWasPublished();
    }

    /**
     * And a Monday already gone is refused, which is the rule the whole feature turns on.
     *
     * <p><strong>This is where the scheme deliberately parts company with a product's terms, which
     * may be backdated and often are.</strong> An account is pinned to the version of a product it
     * was opened under, so a backdated version of those terms changes nothing already decided.
     * Nobody is pinned to a version of the scheme, and a customer's run of weeks is re-derived from
     * the whole ledger every time anybody reads it — so a version dated last Monday would re-judge
     * weeks that have already counted, un-securing weeks somebody was told they had secured, and
     * nothing would log it. That is the failure this feature exists to remove.
     */
    @Test
    void a_monday_that_has_already_gone_is_refused() {
        LocalDate lastMonday = bank.theNextMondayStillToCome().minusWeeks(2);
        assertThat(lastMonday).isBefore(bank.theDateTheClockReads());
        Map<String, Object> form = theSameSchemeAgain(bank.theSchemeInForce(), lastMonday,
                "A change backdated to a Monday that has already been judged.");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains(lastMonday.toString(), "still to come",
                        bank.theNextMondayStillToCome().toString());
        nothingWasPublished();
    }

    /**
     * A date box somebody typed a European date into is answered with a sentence about the date,
     * quoting back what they typed — not with a request that could not be read.
     */
    @Test
    void a_day_that_is_not_a_day_is_quoted_back() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("effectiveFrom", "31/12/2026");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("31/12/2026", "2026-12-31");
        nothingWasPublished();
    }

    // The figures ------------------------------------------------------------------------------

    /**
     * A figure nobody filled in is refused by name rather than read as nought.
     *
     * <p><strong>The most important refusal here, and more important than its counterpart on a
     * product's terms.</strong> Six of a product's eight figures read nought as a real agreement, so
     * an empty box read as nought publishes something somebody could have meant. Not one figure of
     * the scheme has an absence to read: an empty weekly threshold read as nought would publish a
     * scheme in which every week secures itself and every customer walks the whole ladder without
     * saving a cent.
     */
    @Test
    void a_figure_nobody_filled_in_is_refused_by_name() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.remove("weeklyThreshold");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("What a week asks for", "was not sent");
        nothingWasPublished();
    }

    /** And the same for a figure in the middle of the form, so the reading is not the first box's. */
    @Test
    void a_count_nobody_filled_in_is_refused_by_name_too() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("howManyOutstandingIsASpiral", "");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("bills outstanding", "was not sent");
        nothingWasPublished();
    }

    /** A week that asks for nothing is not a week: every week would secure itself. */
    @Test
    void a_weekly_threshold_of_nought_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("weeklyThreshold", "0.00");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("What a week asks for", "more than nothing");
        nothingWasPublished();
    }

    /** Nor is a week that asks for less than nothing, which a withdrawal would secure. */
    @Test
    void a_weekly_threshold_below_nought_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("weeklyThreshold", "-10.00");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("-10.00");
        nothingWasPublished();
    }

    /**
     * An ordinary rate of nought is refused, which is the figure where nought is not the absence of
     * a rule.
     *
     * <p>Nought <em>times</em> is not an offer at all — it is a deposit that silently earns no
     * points however much is saved — and {@code 1.0000} is what "changes nothing" spells for a
     * factor. A scheme whose first week pays nothing pays nothing for every week after it too.
     */
    @Test
    void an_ordinary_rate_of_nought_is_refused_because_it_is_a_factor_and_not_a_rate() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("theOrdinaryRate", "0");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("The ordinary rate", "1.0000");
        nothingWasPublished();
    }

    /** A step below nothing is a ladder that pays a customer less the longer they keep saving. */
    @Test
    void a_step_below_nothing_is_refused_as_a_ladder_that_descends() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("extraForEachFurtherWeek", "-0.1000");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("-0.1000", "descends");
        nothingWasPublished();
    }

    /**
     * A cap below the ordinary rate is refused, because it is a first week that pays more than the
     * scheme will ever pay again.
     *
     * <p>The sentence names both figures, because the fix is a choice between them rather than a
     * typo to spot.
     */
    @Test
    void a_cap_below_the_ordinary_rate_is_refused_as_a_ladder_that_descends() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("theOrdinaryRate", "1.2000");
        form.put("theMostAStreakPays", "0.9000");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("0.9000", "1.2000", "descends");
        nothingWasPublished();
    }

    /** Points that lasted no months at all would expire the moment they were earned. */
    @Test
    void a_points_lifetime_below_one_month_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("howLongABatchOfPointsLasts", "0");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains("How long a batch of points lasts", "less than one");
        nothingWasPublished();
    }

    /** A scheme with no rungs congratulates nobody on anything, which is a form that lost its list. */
    @Test
    void a_scheme_with_no_balance_rungs_at_all_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("balanceRungs", List.of());

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("at least one balance rung");
        nothingWasPublished();
    }

    /** And a list that was left out altogether reads the same way, because it is the same absence. */
    @Test
    void a_scheme_whose_rungs_were_left_out_altogether_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.remove("balanceRungs");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("at least one balance rung");
        nothingWasPublished();
    }

    /**
     * Rungs that do not climb are refused: a rung below the one before it is one nobody can ever
     * climb past.
     */
    @Test
    void balance_rungs_that_do_not_climb_are_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("balanceRungs", List.of("100", "500", "250", "1000"));

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("250", "500", "climb");
        nothingWasPublished();
    }

    /** Two rungs at one figure would congratulate somebody twice for arriving once. */
    @Test
    void two_balance_rungs_at_the_same_figure_are_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("balanceRungs", List.of("100", "500", "500", "1000"));

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("500", "climb");
        nothingWasPublished();
    }

    /** "You have reached EUR 499.99" is a sentence no bank sends. */
    @Test
    void a_balance_rung_that_is_not_a_whole_euro_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("balanceRungs", List.of("100", "499.99", "1000"));

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("499.99", "whole number of euros");
        nothingWasPublished();
    }

    /** A rung at nought is one every customer has already reached. */
    @Test
    void a_balance_rung_at_nothing_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("balanceRungs", List.of("0", "500", "1000"));

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("A balance rung", "more than nothing");
        nothingWasPublished();
    }

    /**
     * A blank box inside the list is a blank box and not a shorter ladder.
     *
     * <p>Dropped rather than refused, it would publish a ladder one rung shorter than the one on
     * the screen the administrator was looking at — which is a scheme nobody typed.
     */
    @Test
    void a_balance_rung_left_blank_is_refused_rather_than_dropped() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("balanceRungs", Arrays.asList("100", "", "1000"));

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("A balance rung", "was not sent");
        nothingWasPublished();
    }

    /** A share of nought would warn everybody the moment they set a budget, before spending a cent. */
    @Test
    void a_running_low_share_below_one_percent_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("whatShareOfABudgetIsRunningLow", "0.00");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("between 1% and 100%");
        nothingWasPublished();
    }

    /** And a share over a hundred is a line nobody can cross, so the warning would never be sent. */
    @Test
    void a_running_low_share_over_a_hundred_percent_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("whatShareOfABudgetIsRunningLow", "120.00");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("120.00", "between 1% and 100%");
        nothingWasPublished();
    }

    /** Nought bills outstanding would be arrears piling up for everybody who owes nothing. */
    @Test
    void arrears_of_fewer_than_one_bill_are_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("howManyOutstandingIsASpiral", "0");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("bills outstanding", "less than one");
        nothingWasPublished();
    }

    /** Nought days of warning is a warning that arrives on the morning of the thing it warns about. */
    @Test
    void a_notice_period_of_fewer_than_one_day_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("daysBeforeAMaturityIsWorthSaying", "0");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("before a maturity", "less than one");
        nothingWasPublished();
    }

    /** And the same for an anniversary, so the reading is not one field's. */
    @Test
    void an_anniversary_notice_period_of_fewer_than_one_day_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("daysBeforeAnAnniversaryIsWorthSaying", "-5");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("before an anniversary", "-5");
        nothingWasPublished();
    }

    // Figures too precise for the unit that holds them -----------------------------------------

    /**
     * A multiple quoted more finely than four places is refused rather than rounded.
     *
     * <p>Rounding a step from 0,10005 to 0,1000 would publish a ladder nobody typed, to everybody,
     * from a Monday — which is exactly the silent repricing this whole feature exists to prevent.
     */
    @Test
    void a_multiple_quoted_more_finely_than_four_places_is_refused_and_not_rounded() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("extraForEachFurtherWeek", "0.10005");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("0.10005", "four decimal places");
        nothingWasPublished();
    }

    /** An amount quoted more finely than a cent gets the sentence a deposit box already gives. */
    @Test
    void an_amount_that_is_not_quoted_to_the_cent_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("weeklyThreshold", "50.005");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("50.005", "two decimal places");
        nothingWasPublished();
    }

    /** And a share quoted finer than a hundredth of a percent is refused for the same reason. */
    @Test
    void a_share_quoted_more_finely_than_a_hundredth_of_a_percent_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("whatShareOfABudgetIsRunningLow", "80.005");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("80.005", "hundredth of a percent");
        nothingWasPublished();
    }

    // Characters that are not figures at all ---------------------------------------------------

    /** A number box somebody typed a comma into is answered with a sentence about the number. */
    @Test
    void a_figure_that_is_not_a_number_is_quoted_back() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("theOrdinaryRate", "1,25");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("1,25", "0.50");
        nothingWasPublished();
    }

    /** And a count somebody wrote out in words is answered with a sentence about whole numbers. */
    @Test
    void a_count_that_is_not_a_whole_number_is_quoted_back() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("howLongABatchOfPointsLasts", "twelve");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("twelve", "whole number");
        nothingWasPublished();
    }

    /**
     * A body that arrived empty is a fact about the request rather than a rule about the scheme,
     * and it is answered in this application's words rather than in Spring's.
     */
    @Test
    void a_publish_with_nothing_in_the_body_is_refused_in_words() {
        ResponseEntity<JsonNode> refused = bank.tryToPublishNothingAtAll();

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("what changed");
        nothingWasPublished();
    }

    /** And a form with every box empty is refused by the module, naming the first thing missing. */
    @Test
    void a_form_with_every_box_empty_is_refused_by_name() {
        ResponseEntity<JsonNode> refused = bank.tryToPublish(Map.of());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("Monday it takes effect");
        nothingWasPublished();
    }

    /**
     * A form with three mistakes in it comes back naming one of them, which is what every other
     * refusal in this application does.
     *
     * <p>Collecting all three would read better on a screen and would be the only refusal here
     * shaped like a list, and a screen that showed one sentence for eleven boxes and a list for one
     * of them is a screen nobody can write a renderer for.
     */
    @Test
    void a_form_with_several_mistakes_comes_back_naming_one_of_them() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("weeklyThreshold", "0.00");
        form.put("theOrdinaryRate", "0");
        form.put("howLongABatchOfPointsLasts", "0");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("What a week asks for");
        nothingWasPublished();
    }

    /**
     * A form filled in correctly in every box, dated on the next Monday still to come, which each
     * test then breaks in one place.
     *
     * <p>Built from the version the scheme is actually running on rather than typed out, so that a
     * test asserting a refusal is asserting about the one field it changed and not about a form
     * that was never any good.
     */
    private static Map<String, Object> aPerfectlyGoodForm() {
        return theSameSchemeAgain(bank.theSchemeInForce(), bank.theNextMondayStillToCome(),
                "A change that will never be written, because every test here breaks it.");
    }

    /**
     * Nothing was written: the bank still has exactly the one version it was seeded with, and it is
     * still the one in force.
     *
     * <p>The second half of every test in this file, in one place, so that no test can be written
     * without it. A refusal that answered correctly and wrote the row anyway would be the worst bug
     * this feature could have.
     */
    private static void nothingWasPublished() {
        List<SchemeView> history = new ArrayList<>(bank.everyVersionPublished());
        assertThat(history)
                .as("the versions of the scheme the bank has published, after a refusal")
                .extracting(SchemeView::version)
                .containsExactly(1);
        assertThat(bank.theSchemeInForce())
                .as("the scheme in force, after a refusal")
                .isEqualTo(history.get(0));
    }
}
