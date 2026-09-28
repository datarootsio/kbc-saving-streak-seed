package io.dataroots.savingstreak.alimitalreadyreached;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A weekly allowance that comes back on a Monday, and a lifetime one that never does — both
 * demonstrated the only way either can be: by winding the clock.
 *
 * <p><strong>This is the test the weekly half of the ticket is actually about.</strong> Every
 * other assertion in this package fixes the day and moves the claims, which proves the counting;
 * this one fixes the claims and moves the day, which proves the thing an administrator is being
 * sold — "one a week, and the scheme does the weeks". The week it counts in is the application's
 * own savings week, Monday to Sunday in the zone {@code SavingsWeek} names, which is the week
 * the streak, the loyalty bonus and the challenges are all already counted in; this test stands
 * on the Sunday before asserting anything about the Monday, because a rule that turned over on
 * "seven days after you claimed" would pass a laxer version of this test and would be a
 * different promise.
 *
 * <p><strong>Its own application, for the reason {@link AnApplicationWithAClockToMove}
 * gives.</strong> Winding a clock cannot be undone, and an application whose clock one test
 * moved is an application every later test is asserting against a day it did not choose.
 *
 * <p><strong>A claim whose voucher has expired still counts, and that is the second test
 * here.</strong> An expiry is the customer's own miss; an allowance handed back for one would
 * make a shelf life cost nothing, and would let somebody at a cap of one have a second by simply
 * waiting for the first to go stale. It needs the clock as much as the week does, because the
 * only way to expire a voucher is to wind past its day and run the sweep.
 *
 * <p><strong>A cancelled claim does not count, and there is deliberately no test of it.</strong>
 * Nothing in this release can put a voucher into {@code CANCELLED} — the slice that lets an
 * administrator revoke one has not landed — so a test would have to reach into the database to
 * write a state the API cannot produce, which is exactly the seam this whole suite is written
 * not to use. The rule is the spec's and is implemented in the query that counts, with the
 * argument written out beside it; the test arrives with the transition that makes it reachable.
 */
class AWeeklyLimitResetsWhenTheWeekTurnsApiTest extends ApiIntegrationTest {

    /** One a week and no lifetime cap: the allowance this whole class is about. */
    private static final String ONE_A_WEEK = "ONE_A_WEEK";

    /** One ever, and a voucher that does not last, for the expiry half. */
    private static final String ONE_THAT_RUNS_OUT = "ONE_IN_ALL_THAT_RUNS_OUT";

    /** Cheap, and earned inside the test, because what this test spends is not the point of it. */
    private static final long WHAT_IT_COSTS = 5;

    /** Short enough to wind past twice without the winding becoming the test. */
    private static final int DAYS_THE_VOUCHER_LASTS = 1;

    /** The name a trainer types to run the scheme's nightly sweep out of turn. */
    private static final String THE_SWEEP = "sweepTheRewardsScheme";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-limit-already-reached"));
        app.anOfferOnSale(anOffer(ONE_A_WEEK, "One a week", Map.of("maxPerCustomerPerWeek", 1)));
        app.anOfferOnSale(anOffer(ONE_THAT_RUNS_OUT, "One, while it lasts",
                Map.of("maxPerCustomer", 1, "voucherValidForDays", DAYS_THE_VOUCHER_LASTS)));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * The demonstration, end to end: one claim uses this week's allowance up, the Sunday is
     * still shut, and the Monday hands it back — with nothing edited in between but the day.
     *
     * <p>All of it in one test rather than three, on purpose. "Locked this week, still locked on
     * the Sunday, open on the Monday" is one fact about one allowance told in sequence, and
     * three tests would each need their own application and their own wind to say a third of it
     * — and none of them would catch an allowance that came back and then never ran out again.
     */
    @Test
    void a_weekly_allowance_comes_back_when_the_week_turns_and_not_before() {
        LocalDate today = app.theDateTheClockReads();
        SavingsWeek thisWeek = SavingsWeek.containing(today);
        earnEnoughForOne();
        app.claim(ANKE, ONE_A_WEEK);

        RewardForACustomerView usedUp = app.theOfferAsReadBy(ANKE, ONE_A_WEEK);
        assertThat(usedUp.claimable()).isFalse();
        assertThat(usedUp.lockedBecause()).isEqualTo("YOU_HAVE_HAD_YOUR_LIMIT");
        assertThat(usedUp.whyItIsLocked()).contains("this week").contains("Monday");
        assertThat(usedUp.howManyYouMayStillHave()).isZero();
        ResponseEntity<JsonNode> refused = app.tryToClaim(ANKE, ONE_A_WEEK);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .as("the card and the refusal are the same sentence, because they are the same "
                        + "fact said at two moments")
                .isEqualTo(usedUp.whyItIsLocked());

        // The Sunday, which is the fencepost worth standing on: an allowance that reset seven
        // days after the claim rather than on the Monday would already be back by now on most
        // days of the week, and this is the assertion that tells the two apart.
        long daysUntilTheWeekTurns =
                ChronoUnit.DAYS.between(today, thisWeek.startsOn().plusWeeks(1));
        if (daysUntilTheWeekTurns > 1) {
            app.daysPass(daysUntilTheWeekTurns - 1);
            assertThat(SavingsWeek.containing(app.theDateTheClockReads()))
                    .as("still standing inside the week the claim was made in")
                    .isEqualTo(thisWeek);
            assertThat(app.theOfferAsReadBy(ANKE, ONE_A_WEEK).claimable())
                    .as("an allowance for the week is gone for all of the week")
                    .isFalse();
        }

        // And the Monday. Nothing was edited; the week turned.
        app.daysPass(1);
        assertThat(SavingsWeek.containing(app.theDateTheClockReads()))
                .isEqualTo(new SavingsWeek(thisWeek.startsOn().plusWeeks(1)));
        RewardForACustomerView theNewWeek = app.theOfferAsReadBy(ANKE, ONE_A_WEEK);
        assertThat(theNewWeek.claimable()).isTrue();
        assertThat(theNewWeek.lockedBecause()).isNull();
        assertThat(theNewWeek.whyItIsLocked()).isNull();
        assertThat(theNewWeek.howManyYouMayStillHave()).isEqualTo(1);
        assertThat(theNewWeek.howManyYouHaveHad())
                .as("the week turning gives the allowance back and forgets nothing: they have "
                        + "still had one of these in their life")
                .isEqualTo(1);

        // And it is a real allowance rather than a card that merely looks pressable.
        earnEnoughForOne();
        app.claim(ANKE, ONE_A_WEEK);
        RewardForACustomerView usedUpAgain = app.theOfferAsReadBy(ANKE, ONE_A_WEEK);
        assertThat(usedUpAgain.claimable())
                .as("the new week's allowance runs out exactly as the old one did")
                .isFalse();
        assertThat(usedUpAgain.howManyYouHaveHad()).isEqualTo(2);
    }

    /**
     * A claim whose voucher outlived its shelf life still counts against the limit.
     *
     * <p>An expiry is the customer's own miss. An allowance handed back for one would make a
     * shelf life cost nothing, and — worse — would turn "one per customer" into "one at a time":
     * anybody could have a second by letting the first go stale, which is the opposite of what
     * the cap was set for. The limit counts claims, not vouchers still alive, and this is where
     * that shows.
     */
    @Test
    void a_claim_whose_voucher_expired_still_counts_against_the_limit() {
        earnEnoughForOne();
        ClaimedRewardView claimed = app.claim(ANKE, ONE_THAT_RUNS_OUT);
        assertThat(app.theOfferAsReadBy(ANKE, ONE_THAT_RUNS_OUT).claimable()).isFalse();

        app.daysPass(DAYS_THE_VOUCHER_LASTS + 1L);
        app.runJob(THE_SWEEP);

        assertThat(theClaimFor(claimed.voucherCode()).state())
                .as("wound past its day and swept, so the voucher is genuinely gone")
                .isEqualTo("EXPIRED");
        RewardForACustomerView reading = app.theOfferAsReadBy(ANKE, ONE_THAT_RUNS_OUT);
        assertThat(reading.claimable())
                .as("the voucher expired; the claim still happened, and one per customer means "
                        + "one claim rather than one live voucher at a time")
                .isFalse();
        assertThat(reading.lockedBecause()).isEqualTo("YOU_HAVE_HAD_YOUR_LIMIT");
        assertThat(reading.howManyYouHaveHad()).isEqualTo(1);
        assertThat(reading.howManyYouMayStillHave()).isZero();
        earnEnoughForOne();
        assertThat(app.tryToClaim(ANKE, ONE_THAT_RUNS_OUT).getStatusCode())
                .as("and they cannot simply claim another now that the first has gone")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /** Anke's claim behind one voucher code, out of the list her own page draws. */
    private static ClaimedRewardView theClaimFor(String voucherCode) {
        return app.claimsOf(ANKE).stream()
                .filter(claim -> voucherCode.equals(claim.voucherCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no claim of Anke's carries the voucher " + voucherCode));
    }

    /** Earns what one of these costs, at a point per euro, because a test that spends earns. */
    private static void earnEnoughForOne() {
        app.deposit(app.savingsAccountOf(ANKE), ANKE, WHAT_IT_COSTS + ".00");
    }

    /** An offer as an administration form would send one, with whatever rule this test needs. */
    private static Map<String, Object> anOffer(String code, String title,
                                               Map<String, Object> rules) {
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", title);
        offer.put("description", "Something to spend points on, but not endlessly.");
        offer.put("costInPoints", WHAT_IT_COSTS);
        offer.put("voucherPrefix", "LIM");
        offer.putAll(rules);
        return offer;
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private static String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }
}
