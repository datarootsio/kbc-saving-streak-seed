package io.dataroots.savingstreak.avoucherthatoutliveditsshelflife;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.JobRunView;
import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.ScheduledJobView;
import io.dataroots.savingstreak.support.VoucherAtTheCounterView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A voucher stops being a liability without end: the offer it came from gave it a number of days,
 * the customer was told the day it runs out from the moment they claimed, and the nightly sweep
 * retires it once that day is behind us.
 *
 * <p><strong>Its own application, and that is not merely the usual reason.</strong> The only way
 * to reach the far side of a deadline in this application is to wind the development clock
 * forward, which cannot be undone and is the whole run's clock — so a test about a shelf life
 * cannot share one. It also needs an offer with a shelf life on it, and there is no such offer in
 * the seed and deliberately never will be: the four the application ships set no shelf life, which
 * is precisely what protects every voucher already out there. Writing one into the shared database
 * would put a fifth entry in a catalogue another test asserts is exactly four.
 *
 * <p>So this class starts an application on a file nothing has ever been written to, writes the
 * offer it needs through the administration API, publishes it, and drives everything else over
 * HTTP the way a trainer would: claim, wind the clock, run the job by name, look at what happened.
 * Nothing here knows what a voucher is stored as.
 *
 * <p>Every test claims its own voucher rather than borrowing one an earlier test left lying about.
 * A voucher's life is a sequence of one-way doors and the clock only moves forward, so a test that
 * reused somebody else's would be a test whose result depended on the order the class happened to
 * run in. Each one claims at whatever the clock reads when it starts and winds forward from there,
 * which is why they compose in any order.
 */
class AVoucherThatOutlivedItsShelfLifeApiTest extends ApiIntegrationTest {

    /** The name a trainer types to run the sweep out of turn. */
    private static final String THE_SWEEP = "sweepTheRewardsScheme";

    /** The offer this whole class is about: something cheap whose vouchers do not last. */
    private static final String A_SHORT_LIVED_OFFER = "A_WEEK_TO_USE_IT";

    private static final long WHAT_IT_COSTS = 5;

    /**
     * A week, counted the way a person counts one: the day it was claimed and the six after it.
     * Short enough to wind past in a test and long enough that the arithmetic is not one day.
     */
    private static final int DAYS_A_VOUCHER_LASTS = 7;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWithSomethingThatRunsOut() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-voucher-with-a-shelf-life"));
        app.writeAnOffer(Map.of(
                "code", A_SHORT_LIVED_OFFER,
                "title", "A week to use it",
                "description", "Something to spend points on, while it lasts.",
                "costInPoints", WHAT_IT_COSTS,
                "voucherPrefix", "WEK",
                "voucherValidForDays", DAYS_A_VOUCHER_LASTS));
        app.publishTheOffer(A_SHORT_LIVED_OFFER);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * The shelf life is something somebody running the scheme sets, reads back and can correct —
     * which is the half of this that happens before any voucher exists.
     */
    @Test
    void an_offer_carries_the_shelf_life_whoever_runs_the_catalogue_gave_it() {
        OfferView offer = app.theOfferAsItStands(A_SHORT_LIVED_OFFER);

        assertThat(offer.voucherValidForDays()).isEqualTo(DAYS_A_VOUCHER_LASTS);
    }

    /**
     * And an offer nobody gave a number to has none, which is the state all four seeded offers are
     * in and the reason nothing already issued changes meaning.
     */
    @Test
    void an_offer_nobody_gave_a_shelf_life_has_none() {
        assertThat(app.theOfferAsItStands("CHARITY_DONATION").voucherValidForDays())
                .as("the four the application has always offered set no shelf life")
                .isNull();
    }

    /**
     * The one refusal the shelf life adds. Nought days is a voucher dead before it is printed, and
     * somebody who typed it meant either "no expiry", which is an empty box, or they made a
     * mistake — and a screen that accepted it would issue codes nobody could ever use.
     */
    @Test
    void an_offer_cannot_be_given_a_shelf_life_of_no_days() {
        ResponseEntity<JsonNode> refused = app.tryToChangeTheOffer(A_SHORT_LIVED_OFFER,
                Map.of("voucherValidForDays", 0));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("shelf life");
        assertThat(app.theOfferAsItStands(A_SHORT_LIVED_OFFER).voucherValidForDays())
                .as("a refused change leaves the offer exactly as it was")
                .isEqualTo(DAYS_A_VOUCHER_LASTS);
    }

    /**
     * The customer is told the day at the moment they claim, not once it is too late.
     *
     * <p>The day the API answers with is the day the application's clock is on plus the six that
     * follow it, because the day a voucher runs out is the last day it is good. A voucher whose
     * shelf life only appeared on the screen after it had run out would never have had one.
     */
    @Test
    void a_voucher_says_the_day_it_runs_out_from_the_moment_it_is_claimed() {
        LocalDate today = app.theDateTheClockReads();

        ClaimedRewardView claimed = claimTheShortLivedOne();

        assertThat(claimed.expiresOn())
                .as("the day it was claimed on and the six after it")
                .isEqualTo(today.plusDays(DAYS_A_VOUCHER_LASTS - 1L));
    }

    /** And it is on their own list afterwards, which is where they will go looking for it. */
    @Test
    void the_customers_list_carries_the_day_their_voucher_runs_out() {
        ClaimedRewardView claimed = claimTheShortLivedOne();

        assertThat(theClaimFor(claimed.voucherCode()).expiresOn())
                .isEqualTo(claimed.expiresOn());
    }

    /**
     * A voucher from an offer with no shelf life has no day on it, anywhere, ever. It is the same
     * absence the offer has and it means the same thing: this one does not run out.
     */
    @Test
    void a_voucher_from_an_offer_with_no_shelf_life_has_no_day_on_it() {
        ClaimedRewardView claimed = claim("CHARITY_DONATION", 10);

        assertThat(claimed.expiresOn()).isNull();
        assertThat(theClaimFor(claimed.voucherCode()).expiresOn()).isNull();
    }

    /**
     * The sweep is a scheduled job of the application's own and a trainer can find it by name,
     * which is the whole arrangement: a deadline measured in days cannot be reached inside a
     * training session by waiting.
     *
     * <p>One job for the scheme rather than one for vouchers, because the sweep's remaining steps
     * — holds lapsing, waiters being promoted — belong in this same job in a particular order, and
     * the name a trainer learns should not have to change when they arrive.
     */
    @Test
    void the_sweep_is_listed_among_the_jobs_a_trainer_can_run() {
        ScheduledJobView[] jobs = app.whatCanBeRun();

        assertThat(Arrays.stream(jobs).map(ScheduledJobView::name))
                .as("the scheme's own nightly sweep, findable without reading the source")
                .contains(THE_SWEEP);
        ScheduledJobView sweep = Arrays.stream(jobs)
                .filter(job -> job.name().equals(THE_SWEEP)).findFirst().orElseThrow();
        assertThat(sweep.definedBy()).isEqualTo("TheRewardsSweepRunsNightly");
        assertThat(sweep.schedule())
                .as("nightly, and last of the night's runs")
                .isEqualTo("cron 0 0 5 * * *");
    }

    /**
     * Winding the clock past the day and running the job by hand is the demonstration, end to end:
     * a voucher that was good this morning is retired, and it is retired against the clock the
     * application is running on rather than the machine's.
     */
    @Test
    void winding_the_clock_past_the_day_and_running_the_sweep_retires_the_voucher() {
        ClaimedRewardView claimed = claimTheShortLivedOne();
        assertThat(theClaimFor(claimed.voucherCode()).state()).isEqualTo("ISSUED");

        app.daysPass(DAYS_A_VOUCHER_LASTS);
        Instant theApplicationThinksItIs = app.theClockReads();
        JobRunView ran = app.runJob(THE_SWEEP);

        assertThat(ran.name()).isEqualTo(THE_SWEEP);
        assertThat(ran.definedBy()).isEqualTo("TheRewardsSweepRunsNightly");
        assertThat(Duration.between(theApplicationThinksItIs, ran.ranAt()).abs())
                .as("the job judged the day the wound-forward clock reads, not the machine's")
                .isLessThan(Duration.ofMinutes(5));
        assertThat(theClaimFor(claimed.voucherCode()).state())
                .as("a week past its last good day, and the sweep has it")
                .isEqualTo("EXPIRED");
    }

    /**
     * And not a day early. The day printed on the voucher is the last day it is good, so a sweep
     * run that very morning has nothing to do with it — which is what somebody reading "runs out
     * on the 14th" expects to be true on the 14th.
     */
    @Test
    void a_voucher_is_still_good_on_the_day_it_runs_out() {
        ClaimedRewardView claimed = claimTheShortLivedOne();

        app.daysPass(DAYS_A_VOUCHER_LASTS - 1L);
        assertThat(app.theDateTheClockReads())
                .as("standing on exactly the day the voucher says it runs out")
                .isEqualTo(claimed.expiresOn());
        app.runJob(THE_SWEEP);

        VoucherAtTheCounterView atTheTill = app.voucherAtACounter(claimed.voucherCode());
        assertThat(atTheTill.state()).isEqualTo("ISSUED");
        assertThat(atTheTill.good())
                .as("a counter would hand this over today, and should")
                .isTrue();
    }

    /**
     * <strong>The rail this whole slice rests on.</strong> Every offer the application ships sets
     * no shelf life, so every voucher ever issued by it has no day on it — and no amount of
     * winding the clock can make the sweep touch one. A sweep that expired vouchers by age rather
     * than by the day they were promised would change the meaning of every code already in
     * somebody's pocket.
     */
    @Test
    void a_voucher_whose_offer_set_no_shelf_life_is_never_touched_whatever_the_clock_says() {
        ClaimedRewardView claimed = claim("CHARITY_DONATION", 10);

        // Months rather than the week the short-lived offer gets, and deliberately short of a
        // year: this class winds one clock forward across all of its tests, and a run that took
        // the whole lot past twelve months would start expiring the points the later claims are
        // paid with — which would be a test failing about the ledger while claiming to be about
        // a voucher.
        app.daysPass(100);
        app.runJob(THE_SWEEP);

        assertThat(theClaimFor(claimed.voucherCode()).state())
                .as("nothing gave this voucher a day, so nothing can retire it")
                .isEqualTo("ISSUED");
        assertThat(app.voucherAtACounter(claimed.voucherCode()).good()).isTrue();
    }

    /**
     * An expired voucher cannot be handed over, and the refusal says which of the three it is.
     *
     * <p>"Expired" and "already used" are different conversations across a counter, and the day is
     * in the sentence because the person in front of them has a code they believe is good. A
     * conflict rather than a not-found: the code is real, the reading endpoint still answers for
     * it, and telling a till to correct a code that needed no correcting would send them hunting
     * for a mistake nobody made.
     */
    @Test
    void an_expired_voucher_cannot_be_handed_over_and_the_refusal_says_it_expired() {
        ClaimedRewardView claimed = claimTheShortLivedOne();
        app.daysPass(DAYS_A_VOUCHER_LASTS);
        app.runJob(THE_SWEEP);

        ResponseEntity<JsonNode> refused =
                app.tryToHandOverTheVoucher(claimed.voucherCode(), "Leuven Bondgenotenlaan, till 2");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .as("the day it ran out, so the two people at the counter can agree about it")
                .contains(claimed.expiresOn().toString());
        assertThat(reasonGivenBy(refused)).contains("ran out");
        // And the refusal changed nothing: an expired voucher is expired, not half-used.
        VoucherAtTheCounterView afterwards = app.voucherAtACounter(claimed.voucherCode());
        assertThat(afterwards.state()).isEqualTo("EXPIRED");
        assertThat(afterwards.good()).isFalse();
        assertThat(afterwards.usedAt()).isNull();
        assertThat(afterwards.usedByCounter()).isNull();
    }

    /**
     * <strong>Expiring refunds nothing.</strong> The points were spent on the day the voucher was
     * claimed and they are not coming back, because a shelf life somebody is refunded for is not a
     * shelf life at all — nobody would ever have a reason to use a voucher in time. The thing that
     * does refund is an administrator cancelling one, which is a later slice and is why the two
     * are different states.
     *
     * <p>There is nothing to assert about stock here and there is deliberately nothing pretending
     * to be: no offer can run out yet. What can be asserted is the half that exists, and the half
     * that exists is the points.
     */
    @Test
    void expiring_a_voucher_gives_no_points_back() {
        ClaimedRewardView claimed = claimTheShortLivedOne();
        app.daysPass(DAYS_A_VOUCHER_LASTS);
        long beforeTheSweep = app.pointsBalanceOf(ANKE);

        app.runJob(THE_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE))
                .as("an expiry is the customer's own miss and the scheme keeps the points")
                .isEqualTo(beforeTheSweep);
        assertThat(theClaimFor(claimed.voucherCode()).pointsSpent())
                .as("what it cost is what it cost, whatever became of the voucher")
                .isEqualTo(WHAT_IT_COSTS);
    }

    /**
     * The customer's own list is where they find out, so it has to tell the three apart: a code
     * they can still spend, one they have already spent, and one they left too long.
     */
    @Test
    void the_customers_list_tells_issued_used_and_expired_apart() {
        // The one that ran out goes first, because letting it run out winds the clock a week on
        // and the other two have to be claimed on this side of that: a voucher meant to still be
        // good cannot be claimed before a sweep that would take it.
        ClaimedRewardView leftTooLong = claimAndLetItRunOut();
        ClaimedRewardView handedOver = claimTheShortLivedOne();
        assertThat(app.tryToHandOverTheVoucher(handedOver.voucherCode(), "Gent Korenmarkt")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        ClaimedRewardView stillGood = claimTheShortLivedOne();

        List<ClaimedRewardView> list = app.claimsOf(ANKE);

        assertThat(stateIn(list, stillGood.voucherCode())).isEqualTo("ISSUED");
        assertThat(stateIn(list, handedOver.voucherCode())).isEqualTo("USED");
        assertThat(stateIn(list, leftTooLong.voucherCode())).isEqualTo("EXPIRED");
    }

    /**
     * A voucher only runs out once. The sweep run again the following night finds nothing to do
     * with it, which is what "terminal" means and what stops a second run rewriting a state.
     */
    @Test
    void a_voucher_that_has_already_run_out_is_left_alone_by_the_next_sweep() {
        ClaimedRewardView claimed = claimAndLetItRunOut();

        app.daysPass(1);
        app.runJob(THE_SWEEP);

        assertThat(theClaimFor(claimed.voucherCode()).state()).isEqualTo("EXPIRED");
    }

    /** Points to spend, and then the claim that turns them into the voucher under test. */
    private ClaimedRewardView claimTheShortLivedOne() {
        return claim(A_SHORT_LIVED_OFFER, WHAT_IT_COSTS);
    }

    private ClaimedRewardView claim(String offer, long cost) {
        app.deposit(app.savingsAccountOf(ANKE), ANKE, cost + ".00");
        return app.claim(ANKE, offer);
    }

    /** The whole demonstration in one line, for the tests whose subject is what happens after it. */
    private ClaimedRewardView claimAndLetItRunOut() {
        ClaimedRewardView claimed = claimTheShortLivedOne();
        app.daysPass(DAYS_A_VOUCHER_LASTS);
        app.runJob(THE_SWEEP);
        return claimed;
    }

    private ClaimedRewardView theClaimFor(String voucherCode) {
        return app.claimsOf(ANKE).stream()
                .filter(one -> one.voucherCode().equals(voucherCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the customer's own list has no voucher " + voucherCode + " on it"));
    }

    private static String stateIn(List<ClaimedRewardView> list, String voucherCode) {
        return list.stream()
                .filter(one -> one.voucherCode().equals(voucherCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no voucher " + voucherCode + " in the list"))
                .state();
    }

    /** The sentence the backend wrote, which is the only thing a screen ever shows about a refusal. */
    private static String reasonGivenBy(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody()).as("a problem document with a reason in it").isNotNull();
        return response.getBody().path("detail").asText();
    }
}
