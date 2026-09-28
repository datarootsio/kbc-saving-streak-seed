package io.dataroots.savingstreak.avouchercancelledandthepointscomeback;

import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.VoucherAtTheCounterView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <strong>The half of the refund a balance cannot show: the points come back as a fresh batch
 * with twelve months of its own, and never as a top-up of the batches that were spent.</strong>
 *
 * <p>This is the decision the whole ticket turns on and it is invisible from a single reading.
 * Points are spent oldest-first, so a claim takes from the batches nearest the end of their own
 * twelve months; a cancellation arrives weeks or months later, by which time some of those
 * batches have gone. A refund that put the points back where they came from would put them into
 * an expired batch — counted by nothing, spendable by nobody — while the balance the customer was
 * shown immediately afterwards said they had them. The failure is silent, and the only way to
 * catch it is to let the original batch actually die and then look.
 *
 * <p>So this class does exactly that, over HTTP and through the endpoints a trainer would use:
 * earn, claim, wind the clock most of a year on, cancel, wind past the original batch's
 * anniversary, run the expiry sweep by hand, and ask what is left. A refund credited to the dead
 * batch would leave nothing. A refund credited as a batch of its own survives, because its own
 * twelve months started on the day of the cancellation.
 *
 * <p><strong>Its own application, and that is not merely the usual reason.</strong> The only way
 * to reach the far side of an anniversary is to wind the development clock forward, which cannot
 * be undone and is the whole run's clock — so a test about a twelve-month rule cannot share one.
 * It also needs a catalogue entry to claim from, and writing one into the shared database would
 * put a fifth entry in a catalogue another test asserts is exactly four.
 *
 * <p>Its own customer too, opened on that application, because a balance asserted as an absolute
 * figure is only honest for somebody with no other history at all.
 */
class TheRefundCarriesTwelveMonthsOfItsOwnApiTest extends ApiIntegrationTest {

    /** The name a trainer types to run the points sweep out of turn. */
    private static final String THE_POINTS_SWEEP = "expireOldPoints";

    /** The offer this class is about: something with a round price and no scarcity in the way. */
    private static final String THE_OFFER = "A_CLAIM_TO_UNDO";

    private static final long WHAT_IT_COSTS = 40;

    /** The name a trainer types to run the scheme's own sweep, which is what expires a voucher. */
    private static final String THE_REWARDS_SWEEP = "sweepTheRewardsScheme";

    /** A second offer, whose vouchers do not last — the only way to reach an expired one. */
    private static final String THE_SHORT_LIVED_OFFER = "A_CLAIM_THAT_RUNS_OUT";

    /** A week, counted the way a person counts one: the day it was claimed and the six after. */
    private static final int DAYS_A_VOUCHER_LASTS = 7;

    /**
     * Most of a year, and deliberately short of one: the claim is cancelled here, while the batch
     * that paid for it is still alive, which is the case a naive refund would appear to survive.
     */
    private static final int DAYS_BEFORE_ANYBODY_NOTICES = 300;

    /**
     * And far enough past the original batch's twelve months that it is certainly gone — a year
     * and a month from the deposit, whichever way the calendar falls.
     */
    private static final int DAYS_UNTIL_THE_FIRST_BATCH_IS_CERTAINLY_GONE = 100;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWithSomethingToClaim() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-refund-with-its-own-year"));
        app.writeAnOffer(Map.of(
                "code", THE_OFFER,
                "title", "A claim to undo",
                "description", "Something to spend points on, and then to have undone.",
                "costInPoints", WHAT_IT_COSTS,
                "voucherPrefix", "UND"));
        app.publishTheOffer(THE_OFFER);
        app.writeAnOffer(Map.of(
                "code", THE_SHORT_LIVED_OFFER,
                "title", "A claim that runs out",
                "description", "Something to spend points on, while it lasts.",
                "costInPoints", WHAT_IT_COSTS,
                "voucherPrefix", "RAN",
                "voucherValidForDays", DAYS_A_VOUCHER_LASTS));
        app.publishTheOffer(THE_SHORT_LIVED_OFFER);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * <strong>The test the whole decision exists for.</strong> The batch that paid for the claim
     * is dead by the end of it, and the refunded points are still there — which can only be true
     * if they were never put back into that batch.
     *
     * <p>Every figure is an absolute rather than a delta, and that is safe here for the one
     * reason it is usually not: this customer was opened by this test on an application nobody
     * else is running, and has earned nothing else, claimed nothing else and been given nothing.
     */
    @Test
    void the_refunded_points_outlive_the_batch_that_paid_for_the_claim() {
        String customer = app.aCustomerOfItsOwn("a refund of its own");
        app.deposit(app.savingsAccountOf(customer), customer, WHAT_IT_COSTS + ".00");
        assertThat(app.pointsBalanceOf(customer)).isEqualTo(WHAT_IT_COSTS);
        ClaimedRewardView claimed = app.claim(customer, THE_OFFER);
        assertThat(app.pointsBalanceOf(customer))
                .as("the claim took the only batch they had")
                .isEqualTo(0);

        app.daysPass(DAYS_BEFORE_ANYBODY_NOTICES);
        VoucherAtTheCounterView cancelled =
                app.cancelTheVoucher(claimed.voucherCode(), "Issued against the wrong customer.");
        assertThat(cancelled.state()).isEqualTo("CANCELLED");
        assertThat(app.pointsBalanceOf(customer))
                .as("the refund is there the moment it is made")
                .isEqualTo(WHAT_IT_COSTS);

        app.daysPass(DAYS_UNTIL_THE_FIRST_BATCH_IS_CERTAINLY_GONE);
        app.runJob(THE_POINTS_SWEEP);

        assertThat(app.pointsBalanceOf(customer))
                .as("the batch that paid for the claim is more than twelve months old and is "
                        + "gone; the refund was never in it, so it is still here")
                .isEqualTo(WHAT_IT_COSTS);
    }

    /**
     * <strong>An expired voucher cannot be cancelled, and nothing is refunded.</strong>
     *
     * <p>Here rather than with the other refusals because it is the one that needs a clock: the
     * only way to reach an expired voucher is to wind past the day its offer promised and let
     * the scheme's own sweep retire it. The rule itself is the meaning of a shelf life said from
     * the other end — an expiry is the customer's own miss, and a cancellation that refunded one
     * anyway would make the deadline cost nobody anything, which is exactly what having two
     * states instead of one exists to prevent.
     *
     * <p>The balance is the load-bearing assertion. A refusal that had already credited the
     * points would be worse than no rule at all, and it would be invisible on the voucher.
     */
    @Test
    void an_expired_voucher_cannot_be_cancelled_and_nothing_is_refunded() {
        String customer = app.aCustomerOfItsOwn("a voucher left too long");
        app.deposit(app.savingsAccountOf(customer), customer, WHAT_IT_COSTS + ".00");
        ClaimedRewardView claimed = app.claim(customer, THE_SHORT_LIVED_OFFER);

        app.daysPass(DAYS_A_VOUCHER_LASTS);
        app.runJob(THE_REWARDS_SWEEP);
        assertThat(app.voucherAtACounter(claimed.voucherCode()).state()).isEqualTo("EXPIRED");

        ResponseEntity<JsonNode> refused = app.tryToCancelTheVoucher(claimed.voucherCode(),
                "Somebody asked nicely after the fact.");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().path("detail").asText())
                .as("the day it ran out, and the sentence that says an expiry is not a "
                        + "cancellation")
                .contains(claimed.expiresOn().toString());
        assertThat(app.pointsBalanceOf(customer))
                .as("an expiry is the customer's own miss and the scheme keeps the points")
                .isEqualTo(0);
        assertThat(app.voucherAtACounter(claimed.voucherCode()).state())
                .as("and the refusal changed nothing")
                .isEqualTo("EXPIRED");
    }

    /**
     * <strong>The refund is spent oldest-first alongside everything else, which is the other
     * half of "it is an ordinary batch".</strong>
     *
     * <p>Two batches with a month between them and a claim that can only be paid for by both
     * ends of one of them: if spending takes the refund first, what is left is the newer
     * deposit and the date the customer is shown is that deposit's anniversary. If it took the
     * newer batch first — or treated the refund as special in any way — what would be left is
     * part of the refund, and the date would be a month earlier. The two are a month apart on
     * purpose, because a day apart would be a test that passed on rounding.
     *
     * <p>Asserted through the date rather than through the balance, because the balance is the
     * same either way round. Which batch survived is the whole question, and the day it expires
     * is the only thing this application says out loud about a batch.
     */
    @Test
    void the_refund_is_spent_oldest_first_like_every_other_batch() {
        String customer = app.aCustomerOfItsOwn("a refund spent in turn");
        app.deposit(app.savingsAccountOf(customer), customer, WHAT_IT_COSTS + ".00");
        ClaimedRewardView undone = app.claim(customer, THE_OFFER);
        app.cancelTheVoucher(undone.voucherCode(), "Claimed against the wrong offer.");

        app.daysPass(30);
        LocalDate theDayTheyDepositedAgain = app.theDateTheClockReads();
        app.deposit(app.savingsAccountOf(customer), customer, "10.00");
        assertThat(app.pointsBalanceOf(customer)).isEqualTo(WHAT_IT_COSTS + 10);

        app.claim(customer, THE_OFFER);

        assertThat(app.pointsBalanceOf(customer))
                .as("the claim took forty of the fifty they had")
                .isEqualTo(10);
        assertThat(app.pointsExpiringNextOnOf(customer))
                .as("the refund went first because it was the older batch, so what is left is "
                        + "the deposit made a month later")
                .isEqualTo(theDayTheyDepositedAgain.plusYears(1));
    }

    /**
     * And the twelve months are counted from the cancellation rather than inherited from the
     * deposit, which is the same fact said as a date instead of as a survival.
     *
     * <p>Asserted through "what expires next", because that is the figure the customer is
     * actually shown and the only place this application says a date about a batch out loud. By
     * the time it is read, the deposit's own batch has been swept away, so the next thing to go
     * is the refund — and the day it goes is a year after the day it was credited.
     *
     * <p>Not asserted to the exact day of the cancellation, but to the year: the sweep runs after
     * more time has passed, and what matters is that the refund's clock started at the
     * cancellation and not at the deposit. A date in the same month as the deposit's anniversary
     * would be the failure; one about ten months later is the rule holding.
     */
    @Test
    void the_refunds_own_twelve_months_are_counted_from_the_cancellation() {
        String customer = app.aCustomerOfItsOwn("a refund dated now");
        app.deposit(app.savingsAccountOf(customer), customer, WHAT_IT_COSTS + ".00");
        ClaimedRewardView claimed = app.claim(customer, THE_OFFER);

        app.daysPass(DAYS_BEFORE_ANYBODY_NOTICES);
        LocalDate theDayItWasCancelled = app.theDateTheClockReads();
        app.cancelTheVoucher(claimed.voucherCode(), "Issued against the wrong customer.");

        app.daysPass(DAYS_UNTIL_THE_FIRST_BATCH_IS_CERTAINLY_GONE);
        app.runJob(THE_POINTS_SWEEP);

        assertThat(app.pointsExpiringNextOf(customer))
                .as("the refund is the only batch they have left")
                .isEqualTo(WHAT_IT_COSTS);
        assertThat(app.pointsExpiringNextOnOf(customer))
                .as("twelve months from the day the refund was made, not from the deposit")
                .isEqualTo(theDayItWasCancelled.plusYears(1));
    }
}
