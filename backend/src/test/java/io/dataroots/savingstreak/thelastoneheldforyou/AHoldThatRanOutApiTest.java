package io.dataroots.savingstreak.thelastoneheldforyou;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.HoldView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The other end of a hold: the seventy-two hours running out, and the sweep that writes it down.
 *
 * <p><strong>Its own application, for the reason the shelf-life package gives about its
 * own.</strong> The only way to reach the far side of a deadline in this application is to wind
 * the development clock forward, which cannot be undone and is the whole run's — so a test about
 * a hold running out cannot share one. It also needs something scarce, and there is no scarce
 * offer in the seed and deliberately never will be: an offer written into the shared database
 * would put a fifth entry in a catalogue another test asserts is exactly four.
 *
 * <p>So this class starts an application on a file nothing has ever been written to, writes the
 * offers it needs through the administration API, and drives everything else over HTTP the way a
 * trainer would: hold it, wind the clock, run the job by name, look at what happened.
 *
 * <p><strong>Four days rather than three, everywhere, and that is not slack.</strong> Seventy-two
 * hours is seventy-two hours of real time, and the development clock advances in whole
 * <em>calendar</em> days — which in this application's zone are twenty-three hours long once a
 * year and twenty-five once more. Three winds of the clock across the last Sunday in March is
 * seventy-one hours, which is not past the deadline, and a suite that ran that weekend would
 * fail for a reason no reader could reconstruct. Four winds is past it on every day there has
 * ever been. The exactness of the span is pinned where it can be pinned, in
 * {@code TheShelfLifeOfAHoldTest}.
 *
 * <p>Each test holds its own thing on its own offer rather than borrowing one an earlier test
 * left lying about: a hold is a sequence of one-way doors and the clock only moves forward, so a
 * test that reused somebody else's would depend on the order the class happened to run in.
 */
class AHoldThatRanOutApiTest extends ApiIntegrationTest {

    /** The name a trainer types to run the sweep out of turn. */
    private static final String THE_SWEEP = "sweepTheRewardsScheme";

    /** Comfortably past seventy-two hours whatever the calendar did in the middle of them. */
    private static final int DAYS_THAT_TAKE_A_HOLD_PAST_ITS_MOMENT = 4;

    /** Comfortably short of them, for the same reason from the other side. */
    private static final int DAYS_THAT_LEAVE_A_HOLD_STANDING = 2;

    private static final long WHAT_IT_COSTS = 5;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWithSomethingScarce() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-last-one-held-for-you"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * <strong>The demonstration, end to end.</strong> Wind the clock past the seventy-two hours,
     * run the sweep by hand, and the hold is written down as lapsed with the thing back in the
     * window.
     *
     * <p>This is the whole of what a trainer does in a session, which is why it is asserted as
     * one sequence rather than split: it is one story and each half of it is uninteresting on
     * its own.
     */
    @Test
    void winding_the_clock_past_seventy_two_hours_and_running_the_job_lapses_the_hold() {
        String code = somethingScarce("A hold nobody converted");
        HoldView held = app.takeAHold(ANKE, code);
        assertThat(held.state()).isEqualTo("HELD");
        assertThat(theOfferAsReadBy(BRAM, code).whatIsLeft())
                .as("held, so there is nothing in the window for anybody else")
                .isEqualTo(0);

        app.daysPass(DAYS_THAT_TAKE_A_HOLD_PAST_ITS_MOMENT);
        app.runJob(THE_SWEEP);

        assertThat(theOfferAsReadBy(ANKE, code).yourHoldLapsesAt())
                .as("it is not hers any more")
                .isNull();
        assertThat(theOfferAsReadBy(BRAM, code).whatIsLeft())
                .as("and the thing is back in the window for whoever wants it")
                .isEqualTo(1);
        assertThat(theOfferAsReadBy(BRAM, code).claimable()).isTrue();
    }

    /**
     * And not a moment early. A hold two days old is still the customer's, and the sweep run
     * that morning has nothing to do with it.
     */
    @Test
    void a_hold_that_still_has_time_is_left_alone_by_the_sweep() {
        String code = somethingScarce("Still hers on the second day");
        HoldView held = app.takeAHold(ANKE, code);

        app.daysPass(DAYS_THAT_LEAVE_A_HOLD_STANDING);
        app.runJob(THE_SWEEP);

        assertThat(theOfferAsReadBy(ANKE, code).yourHoldLapsesAt())
                .as("two days in, seventy-two hours have not gone by")
                .isEqualTo(held.lapsesAt());
        assertThat(theOfferAsReadBy(BRAM, code).whatIsLeft())
                .as("and it is still nobody else's")
                .isEqualTo(0);
        assertThat(theConversionOf(ANKE, code).getStatusCode())
                .as("and she can still take the thing she is holding")
                .isEqualTo(HttpStatus.CREATED);
    }

    /**
     * <strong>Converting a lapsed hold is refused as lapsed, and nothing is spent.</strong>
     *
     * <p>The sweep is deliberately not run here, and that is the point of the test. A hold is
     * over the instant the clock passes the moment written on it, whether or not anything has
     * written that down: the sweep runs once a night, and a customer who converted at hour
     * seventy-five would otherwise be handed a thing the application had already told everybody
     * else was available again.
     */
    @Test
    void converting_a_hold_that_ran_out_is_refused_as_lapsed_before_any_sweep_has_run() {
        String code = somethingScarce("Ran out before the sweep");
        HoldView held = app.takeAHold(ANKE, code);
        earn(WHAT_IT_COSTS);
        long before = app.pointsBalanceOf(ANKE);

        app.daysPass(DAYS_THAT_TAKE_A_HOLD_PAST_ITS_MOMENT);

        ResponseEntity<JsonNode> refused = app.tryToConvertTheHold(ANKE, code);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .as("and the sentence says when it stopped being hers")
                .contains("ran out on")
                .contains(held.lapsesAt().toString());
        assertThat(app.pointsBalanceOf(ANKE))
                .as("nothing was spent on a thing she did not get")
                .isEqualTo(before);
        assertThat(theOfferAsReadBy(BRAM, code).whatIsLeft())
                .as("and the stock came back when the clock passed, not when a job ran")
                .isEqualTo(1);
    }

    /** Giving up a hold that has already run out is refused in the same words. */
    @Test
    void giving_up_a_hold_that_ran_out_is_refused_in_the_same_words() {
        String code = somethingScarce("Too late to give up");
        app.takeAHold(ANKE, code);

        app.daysPass(DAYS_THAT_TAKE_A_HOLD_PAST_ITS_MOMENT);

        ResponseEntity<JsonNode> refused = app.tryToGiveUpTheHold(ANKE, code);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains("ran out on");
    }

    /**
     * Once the hold has lapsed, the same customer can take a fresh one — which is what makes the
     * row the sweep writes worth writing at all.
     *
     * <p>One live hold per offer per customer is a rule about live holds, and a hold that ran
     * out is not one. The sweep is run in the middle so that the fresh hold is taken against a
     * tidy table rather than against one with a stale row still calling itself held, which is
     * the state the step after this one in the sweep will read.
     */
    @Test
    void a_customer_whose_hold_lapsed_can_take_another() {
        String code = somethingScarce("Round two");
        HoldView first = app.takeAHold(ANKE, code);

        app.daysPass(DAYS_THAT_TAKE_A_HOLD_PAST_ITS_MOMENT);
        app.runJob(THE_SWEEP);
        HoldView second = app.takeAHold(ANKE, code);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.lapsesAt()).isAfter(first.lapsesAt());
        assertThat(theOfferAsReadBy(ANKE, code).yourHoldLapsesAt()).isEqualTo(second.lapsesAt());
    }

    /**
     * A hold lapses once. The sweep run again the following night finds nothing to do with it,
     * which is what terminal means and what stops a second run rewriting a state.
     */
    @Test
    void a_hold_that_has_already_lapsed_is_left_alone_by_the_next_sweep() {
        String code = somethingScarce("Swept twice");
        app.takeAHold(ANKE, code);
        app.daysPass(DAYS_THAT_TAKE_A_HOLD_PAST_ITS_MOMENT);
        app.runJob(THE_SWEEP);

        app.daysPass(1);
        app.runJob(THE_SWEEP);

        assertThat(theOfferAsReadBy(BRAM, code).whatIsLeft())
                .as("one offer, one hold, one return of the stock")
                .isEqualTo(1);
    }

    /**
     * <strong>Lapsing refunds nothing, because holding took nothing.</strong>
     *
     * <p>The mirror of the shelf-life package's assertion about an expiring voucher, and it is
     * true here for a quite different reason: an expiry keeps points the customer did spend, and
     * a lapse returns nothing because there was never anything to return. Both are worth
     * asserting because both are what the design rests on.
     */
    @Test
    void a_hold_lapsing_moves_no_points() {
        String code = somethingScarce("Nothing to give back");
        earn(WHAT_IT_COSTS);
        app.takeAHold(ANKE, code);
        long before = app.pointsBalanceOf(ANKE);

        app.daysPass(DAYS_THAT_TAKE_A_HOLD_PAST_ITS_MOMENT);
        app.runJob(THE_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(before);
    }

    /**
     * The sweep the trainer runs is the scheme's one job, and it is still the one the vouchers
     * go through. Holds did not get a job of their own and must not: the step that promotes
     * whoever is next in line has to run after this one in the same run, and three cron
     * expressions a few minutes apart would be that ordering expressed as a hope.
     */
    @Test
    void lapsing_is_a_step_of_the_one_sweep_rather_than_a_job_of_its_own() {
        assertThat(java.util.Arrays.stream(app.whatCanBeRun())
                .filter(job -> job.definedBy().equals("TheRewardsSweepRunsNightly"))
                .map(job -> job.name()))
                .as("one job for the whole scheme, whatever it learns to do overnight")
                .containsExactly(THE_SWEEP);
    }

    /**
     * Something nobody else's test has heard of, with exactly one of it, on sale.
     *
     * <p>A fresh offer per test, because a hold takes the last one and the clock only moves
     * forward: two tests sharing an offer would be two tests whose result depended on the order
     * they ran in.
     */
    private static String somethingScarce(String title) {
        String code = "LAST_ONE_" + Math.abs(title.hashCode());
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", title);
        offer.put("description", "Exactly one of these, and somebody wants it.");
        offer.put("costInPoints", WHAT_IT_COSTS);
        offer.put("voucherPrefix", "LST");
        offer.put("stock", 1);
        app.writeAnOffer(offer);
        app.publishTheOffer(code);
        return code;
    }

    private static RewardForACustomerView theOfferAsReadBy(String customerName, String code) {
        return app.theOfferAsReadBy(customerName, code);
    }

    private static ResponseEntity<JsonNode> theConversionOf(String customerName, String code) {
        return app.tryToConvertTheHold(customerName, code);
    }

    /** Points to spend, for the tests whose subject is that nothing was spent. */
    private static void earn(long points) {
        app.deposit(app.savingsAccountOf(ANKE), ANKE, points + ".00");
    }

    /** The sentence the backend wrote, which is the only thing a screen ever shows. */
    private static String reasonGivenBy(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody()).as("a problem document with a reason in it").isNotNull();
        return response.getBody().path("detail").asText();
    }
}
