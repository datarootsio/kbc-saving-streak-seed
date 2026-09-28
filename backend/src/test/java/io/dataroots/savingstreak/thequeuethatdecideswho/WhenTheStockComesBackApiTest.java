package io.dataroots.savingstreak.thequeuethatdecideswho;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.HoldView;
import io.dataroots.savingstreak.support.NotificationView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import io.dataroots.savingstreak.support.WaitingListEntryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The other end of a queue: stock coming back, and the sweep that decides who gets it.
 *
 * <p><strong>Its own application, for the reason {@code AHoldThatRanOutApiTest} gives about
 * its own.</strong> Two of the three ways stock comes back can only be reached by winding the
 * development clock forward — a hold has to run out first — and the clock cannot be unwound
 * and is the whole run's. It also needs scarce offers, and there is no scarce offer in the seed
 * and deliberately never will be: an offer written into the shared database would put a fifth
 * entry in a catalogue another test asserts is exactly four.
 *
 * <p>So this class starts an application on a file nothing has ever been written to, and drives
 * everything over HTTP the way a trainer would: write the offer, get people into the queue,
 * make the stock come back, run the job by name, and look at what happened.
 *
 * <p><strong>Four days rather than three, everywhere, and that is not slack.</strong>
 * Seventy-two hours is seventy-two hours of real time, and the development clock advances in
 * whole <em>calendar</em> days — which in this application's zone are twenty-three hours long
 * once a year and twenty-five once more. Three winds across the last Sunday in March is
 * seventy-one hours, and a suite that ran that weekend would fail for a reason no reader could
 * reconstruct.
 *
 * <p><strong>The three ways stock comes back are three tests and one code path.</strong> An
 * administrator raising the stock, a hold lapsing and a voucher being cancelled are asserted
 * separately here because they are three things a person does; nothing in the application
 * distinguishes them, because the promotion reads what is left of an offer rather than being
 * told why it changed. That is the design, and the three tests are what keep it honest.
 *
 * <p>Each test writes its own offer and gets its own people into the queue, because a queue is
 * a sequence of one-way doors and the clock only moves forward: a test that reused another
 * one's would depend on the order the class happened to run in.
 */
class WhenTheStockComesBackApiTest extends ApiIntegrationTest {

    /** The name a trainer types to run the scheme's one sweep out of turn. */
    private static final String THE_SWEEP = "sweepTheRewardsScheme";

    /** Comfortably past seventy-two hours whatever the calendar did in the middle of them. */
    private static final int DAYS_THAT_TAKE_A_HOLD_PAST_ITS_MOMENT = 4;

    private static final long WHAT_IT_COSTS = 5;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWithSomethingScarce() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-queue-that-decides-who"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * <strong>The demonstration, end to end.</strong> An administrator restocks something two
     * people are queued for, the sweep runs, and the oldest waiter has it put aside for them.
     *
     * <p>This is the whole of what a trainer does in a session, which is why it is asserted as
     * one sequence rather than split. Every part of it matters: the oldest waiter and not the
     * newest, a hold and not a claim, out of the queue rather than still in it, and the person
     * behind them moved up rather than left where they were.
     */
    @Test
    void restocking_and_running_the_sweep_gives_the_oldest_waiter_a_hold() {
        String code = somethingSoldOut("Restocked overnight");
        app.joinTheQueue(ANKE, code);
        app.joinTheQueue(BRAM, code);

        restockTo(code, 1);
        app.runJob(THE_SWEEP);

        RewardForACustomerView hers = app.theOfferAsReadBy(ANKE, code);
        assertThat(hers.yourHoldLapsesAt())
                .as("the oldest waiter has the thing put aside for her")
                .isNotNull();
        assertThat(hers.yourPlaceInTheQueue())
                .as("and is out of the queue, because the queue has done its job")
                .isNull();
        assertThat(hers.claimable()).as("a hold is an affordance and never a lock").isTrue();

        RewardForACustomerView his = app.theOfferAsReadBy(BRAM, code);
        assertThat(his.yourHoldLapsesAt()).as("one back, one hold").isNull();
        assertThat(his.yourPlaceInTheQueue())
                .as("and the man behind her is now the man in front")
                .isEqualTo(1);
        assertThat(app.theWaitingListFor(code))
                .extracting(WaitingListEntryView::customerName)
                .containsExactly(BRAM);
    }

    /**
     * <strong>A hold and never a claim: no points move and no voucher is issued.</strong>
     *
     * <p>The decision the whole ticket turns on, asserted rather than described. The
     * application does not spend somebody's points while they are asleep, so what arrives
     * overnight is seventy-two hours to decide in — and the customer's balance and their list
     * of vouchers are both exactly as they were.
     */
    @Test
    void a_promotion_takes_no_points_and_issues_no_voucher() {
        String code = somethingSoldOut("Nothing spent while she slept");
        app.joinTheQueue(ANKE, code);
        earn(WHAT_IT_COSTS);
        long before = app.pointsBalanceOf(ANKE);
        int vouchersBefore = app.claimsOf(ANKE).size();

        restockTo(code, 1);
        app.runJob(THE_SWEEP);

        assertThat(app.theOfferAsReadBy(ANKE, code).yourHoldLapsesAt()).isNotNull();
        assertThat(app.pointsBalanceOf(ANKE))
                .as("a hold takes stock and never points")
                .isEqualTo(before);
        assertThat(app.claimsOf(ANKE))
                .as("and nothing was claimed on her behalf")
                .hasSize(vouchersBefore);
    }

    /**
     * And the hold a promotion hands over is an ordinary hold: she can convert it, and doing so
     * spends the points at the price in force then.
     */
    @Test
    void a_promoted_waiter_can_convert_the_hold_into_the_claim_it_was_kept_for() {
        String code = somethingSoldOut("Converted in the morning");
        app.joinTheQueue(ANKE, code);
        earn(WHAT_IT_COSTS);
        long before = app.pointsBalanceOf(ANKE);

        restockTo(code, 1);
        app.runJob(THE_SWEEP);
        ClaimedRewardView claimed = app.convertTheHold(ANKE, code);

        assertThat(claimed.voucherCode()).isNotBlank();
        assertThat(claimed.pointsSpent()).isEqualTo(WHAT_IT_COSTS);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(before - WHAT_IT_COSTS);
    }

    /**
     * <strong>The promoted waiter is told, with the deadline in the record.</strong>
     *
     * <p>A hold nobody knows about is a deadline nobody saw coming, which is the one thing this
     * feature says a hold must never be. The notification carries the offer, what it is called
     * and the moment the hold runs out, because those three are what the line on the screen is
     * written out of — and the moment is the same moment the card counts down to.
     */
    @Test
    void the_promoted_waiter_is_told_that_one_is_being_held_for_them() {
        String code = somethingSoldOut("Told the same night");
        app.joinTheQueue(ANKE, code);

        restockTo(code, 1);
        app.runJob(THE_SWEEP);

        RewardForACustomerView hers = app.theOfferAsReadBy(ANKE, code);
        NotificationView told = Arrays.stream(app.notificationsOf(ANKE))
                .filter(one -> "A_REWARD_IS_BEING_HELD_FOR_YOU".equals(one.reason()))
                .filter(one -> code.equals(one.offerCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("nothing told her that her turn had come; "
                        + "a hold she never heard about is a deadline she never saw"));

        assertThat(told.offerTitle()).isEqualTo("Told the same night");
        assertThat(told.lapsesAt())
                .as("the same deadline her card counts down to, and not a day rounded off it")
                .isEqualTo(hers.yourHoldLapsesAt());
        assertThat(told.readAt()).as("it is new to her").isNull();
        assertThat(app.notificationsOf(BRAM))
                .as("and nobody else is told about somebody else's hold")
                .noneMatch(one -> "A_REWARD_IS_BEING_HELD_FOR_YOU".equals(one.reason())
                        && code.equals(one.offerCode()));
    }

    /**
     * <strong>A hold lapsing and a promotion happen in one run of one sweep.</strong>
     *
     * <p>The ordering criterion, asserted as a trainer would see it: one wind of the clock, one
     * press of one button, and the thing goes from one customer to the next. The sweep is run
     * exactly once here on purpose — a second run would prove nothing about the order of the
     * steps inside the first.
     */
    @Test
    void a_hold_that_lapses_is_promoted_to_the_next_waiter_in_the_same_sweep() {
        String code = somethingScarce("Handed on in one night", 1);
        HoldView hers = app.takeAHold(ANKE, code);
        app.joinTheQueue(BRAM, code);
        assertThat(app.theOfferAsReadBy(BRAM, code).yourPlaceInTheQueue()).isEqualTo(1);

        app.daysPass(DAYS_THAT_TAKE_A_HOLD_PAST_ITS_MOMENT);
        app.runJob(THE_SWEEP);

        assertThat(app.theOfferAsReadBy(ANKE, code).yourHoldLapsesAt())
                .as("her seventy-two hours went by")
                .isNull();
        RewardForACustomerView his = app.theOfferAsReadBy(BRAM, code);
        assertThat(his.yourHoldLapsesAt())
                .as("and the stock her lapse returned went to the man behind her, that same run")
                .isNotNull();
        assertThat(his.yourHoldLapsesAt())
                .as("with three days of his own, counted from tonight")
                .isAfter(hers.lapsesAt());
        assertThat(his.yourPlaceInTheQueue()).isNull();
        assertThat(app.theWaitingListFor(code)).isEmpty();
    }

    /**
     * <strong>A promoted waiter who lets their hold lapse returns the stock to the next
     * waiter.</strong>
     *
     * <p>Which is the pipeline running twice, and the reason a promotion is terminal rather
     * than a turn somebody keeps: the customer who let three days go by does not go back to the
     * front of the line ahead of the person who has been waiting behind them the whole time.
     */
    @Test
    void a_promoted_waiter_who_lets_the_hold_lapse_returns_it_to_the_next_waiter() {
        String code = somethingSoldOut("Passed along the line");
        app.joinTheQueue(ANKE, code);
        app.joinTheQueue(BRAM, code);
        restockTo(code, 1);
        app.runJob(THE_SWEEP);
        assertThat(app.theOfferAsReadBy(ANKE, code).yourHoldLapsesAt()).isNotNull();

        app.daysPass(DAYS_THAT_TAKE_A_HOLD_PAST_ITS_MOMENT);
        app.runJob(THE_SWEEP);

        assertThat(app.theOfferAsReadBy(ANKE, code).yourHoldLapsesAt())
                .as("she did nothing with it")
                .isNull();
        assertThat(app.theOfferAsReadBy(ANKE, code).yourPlaceInTheQueue())
                .as("and she is not put back at the front of a queue she has already had a turn in")
                .isNull();
        assertThat(app.theOfferAsReadBy(BRAM, code).yourHoldLapsesAt())
                .as("the next waiter gets it instead")
                .isNotNull();
        assertThat(app.theWaitingListFor(code)).isEmpty();
    }

    /**
     * <strong>A cancelled voucher promotes the next waiter, by the same path as a restock.</strong>
     *
     * <p>The third of the three ways stock comes back. Nothing in the application knows that
     * this one is a cancellation rather than a restock: the voucher going back on the shelf
     * changes the same subtraction, and the sweep reads the answer.
     *
     * <p>The claim is made by a customer this test opened for itself rather than by one of the
     * seeded pair, which is the rule every cancellation test in this codebase follows. A
     * cancellation refunds as a fresh batch of points that no deposit explains, and two tests
     * elsewhere assert that a seeded customer's earned less spent is exactly their balance.
     */
    @Test
    void a_cancelled_voucher_promotes_the_next_waiter_by_the_same_path() {
        String code = somethingScarce("Cancelled and passed on", 1);
        String buyer = app.aCustomerOfItsOwn("cancelled claim");
        app.deposit(app.savingsAccountOf(buyer), buyer, WHAT_IT_COSTS + ".00");
        ClaimedRewardView claimed = app.claim(buyer, code);
        app.joinTheQueue(ANKE, code);
        assertThat(app.theOfferAsReadBy(ANKE, code).whatIsLeft())
                .as("the only one of them went out as a voucher")
                .isEqualTo(0);

        app.cancelTheVoucher(claimed.voucherCode(), "Issued against the wrong account.");
        app.runJob(THE_SWEEP);

        assertThat(app.theOfferAsReadBy(ANKE, code).yourHoldLapsesAt())
                .as("the thing came back and the person waiting for it got it")
                .isNotNull();
        assertThat(app.theWaitingListFor(code)).isEmpty();
    }

    /**
     * <strong>A waiter who no longer qualifies is passed over, and keeps their place.</strong>
     *
     * <p>The rule is changed under the queue, which is how this actually happens: somebody runs
     * the scheme, decides the thing is for customers who have earned something, and the person
     * at the front of the line no longer is one. They are not promoted, the next person who
     * does qualify is, and — the half worth arguing — they are not thrown out either. A place
     * taken away at five in the morning, with nothing to press and no way to find out, for a
     * rule that may be lifted tomorrow, would be the queue punishing somebody for a change they
     * did not make.
     */
    @Test
    void a_waiter_who_no_longer_qualifies_is_passed_over_and_keeps_their_place() {
        String code = somethingSoldOut("For customers who have earned something");
        String newcomer = app.aCustomerOfItsOwn("earned nothing");
        app.joinTheQueue(newcomer, code);
        app.joinTheQueue(ANKE, code);
        earn(WHAT_IT_COSTS);

        onlyForCustomersWhoHaveEarned(code, 1);
        restockTo(code, 1);
        app.runJob(THE_SWEEP);

        assertThat(app.theOfferAsReadBy(newcomer, code).yourHoldLapsesAt())
                .as("the front of the queue no longer qualifies, so nothing is put aside for them")
                .isNull();
        assertThat(app.theOfferAsReadBy(newcomer, code).yourPlaceInTheQueue())
                .as("and they keep the place they have been holding all along")
                .isEqualTo(1);
        assertThat(app.theOfferAsReadBy(ANKE, code).yourHoldLapsesAt())
                .as("the next waiter who does qualify gets it")
                .isNotNull();
        assertThat(app.theWaitingListFor(code))
                .extracting(WaitingListEntryView::customerName)
                .containsExactly(newcomer);
    }

    /**
     * Only as many waiters are promoted as there are things to promote them to, and the ones
     * behind them keep their places in order.
     */
    @Test
    void one_thing_coming_back_promotes_exactly_one_waiter() {
        String code = somethingSoldOut("One back, one promoted");
        String second = app.aCustomerOfItsOwn("second in line");
        String third = app.aCustomerOfItsOwn("third in line");
        app.joinTheQueue(ANKE, code);
        app.joinTheQueue(second, code);
        app.joinTheQueue(third, code);

        restockTo(code, 1);
        app.runJob(THE_SWEEP);

        assertThat(app.theOfferAsReadBy(ANKE, code).yourHoldLapsesAt()).isNotNull();
        assertThat(app.theOfferAsReadBy(second, code).yourHoldLapsesAt()).isNull();
        List<WaitingListEntryView> stillWaiting = app.theWaitingListFor(code);
        assertThat(stillWaiting).extracting(WaitingListEntryView::customerName)
                .containsExactly(second, third);
        assertThat(stillWaiting).extracting(WaitingListEntryView::position)
                .containsExactly(1, 2);
    }

    /** And two coming back promotes two, in the order they joined. */
    @Test
    void two_things_coming_back_promote_the_two_oldest_waiters() {
        String code = somethingSoldOut("Two back, two promoted");
        String second = app.aCustomerOfItsOwn("second of three");
        String third = app.aCustomerOfItsOwn("third of three");
        app.joinTheQueue(ANKE, code);
        app.joinTheQueue(second, code);
        app.joinTheQueue(third, code);

        restockTo(code, 2);
        app.runJob(THE_SWEEP);

        assertThat(app.theOfferAsReadBy(ANKE, code).yourHoldLapsesAt()).isNotNull();
        assertThat(app.theOfferAsReadBy(second, code).yourHoldLapsesAt()).isNotNull();
        assertThat(app.theOfferAsReadBy(third, code).yourHoldLapsesAt())
                .as("there were two of them and he was third")
                .isNull();
        assertThat(app.theWaitingListFor(code))
                .extracting(WaitingListEntryView::customerName)
                .containsExactly(third);
    }

    /**
     * A sweep run against a queue with nothing to hand out promotes nobody and leaves every
     * place exactly where it was, which is what a quiet night looks like.
     */
    @Test
    void a_sweep_with_no_stock_to_hand_out_promotes_nobody() {
        String code = somethingSoldOut("Still nothing to give");
        app.joinTheQueue(ANKE, code);

        app.runJob(THE_SWEEP);

        assertThat(app.theOfferAsReadBy(ANKE, code).yourHoldLapsesAt()).isNull();
        assertThat(app.theOfferAsReadBy(ANKE, code).yourPlaceInTheQueue()).isEqualTo(1);
        assertThat(app.theWaitingListFor(code)).hasSize(1);
    }

    /**
     * An offer somebody withdrew promotes nobody, however many are queued for it and however
     * much stock it has.
     *
     * <p>The one availability question the customer-facing reading does not ask for itself: a
     * draft and a withdrawn offer are filtered out of that list rather than locked in it, so an
     * offer taken down would otherwise read as perfectly ordinary to the sweep. Handing
     * somebody a hold on it would be the scheme reserving a thing nobody may claim.
     */
    @Test
    void a_withdrawn_offer_promotes_nobody() {
        String code = somethingSoldOut("Taken down while they waited");
        app.joinTheQueue(ANKE, code);
        restockTo(code, 1);
        withdraw(code);

        app.runJob(THE_SWEEP);

        assertThat(app.theWaitingListFor(code))
                .as("still in the line for a thing nobody is selling")
                .hasSize(1);
    }

    /**
     * Somebody who left the queue before their turn came is not promoted, which is the whole
     * point of being able to leave one.
     */
    @Test
    void somebody_who_left_the_queue_is_not_promoted() {
        String code = somethingSoldOut("Gone before her turn");
        app.joinTheQueue(ANKE, code);
        app.joinTheQueue(BRAM, code);
        app.leaveTheQueue(ANKE, code);

        restockTo(code, 1);
        app.runJob(THE_SWEEP);

        assertThat(app.theOfferAsReadBy(ANKE, code).yourHoldLapsesAt())
                .as("she asked to be let out of it")
                .isNull();
        assertThat(app.theOfferAsReadBy(BRAM, code).yourHoldLapsesAt()).isNotNull();
    }

    /**
     * The promotion is a step of the scheme's one sweep and not a job of its own, which is the
     * whole reason the three steps can be relied on to run in an order.
     */
    @Test
    void promoting_is_a_step_of_the_one_sweep_rather_than_a_job_of_its_own() {
        assertThat(Arrays.stream(app.whatCanBeRun())
                .filter(job -> job.definedBy().equals("TheRewardsSweepRunsNightly"))
                .map(job -> job.name()))
                .as("one job for the whole scheme, whatever it learns to do overnight")
                .containsExactly(THE_SWEEP);
    }

    /** Something nobody else's test has heard of, with nought of it: a thing that has run out. */
    private static String somethingSoldOut(String title) {
        return somethingScarce(title, 0);
    }

    /**
     * Something nobody else's test has heard of, with however many of it, on sale.
     *
     * <p>A fresh offer per test, because a queue is a sequence of one-way doors and the clock
     * only moves forward: two tests sharing an offer would be two tests whose result depended
     * on the order they ran in.
     */
    private static String somethingScarce(String title, int stock) {
        String code = "STOCK_BACK_" + Math.abs(title.hashCode());
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", title);
        offer.put("description", "Somebody is waiting for this one.");
        offer.put("costInPoints", WHAT_IT_COSTS);
        offer.put("voucherPrefix", "QUE");
        offer.put("stock", stock);
        app.writeAnOffer(offer);
        app.publishTheOffer(code);
        return code;
    }

    /** An administrator raising the stock, which is the first of the three ways it comes back. */
    private static void restockTo(String code, int stock) {
        assertThat(app.tryToChangeTheOffer(code, Map.of("stock", stock)).getStatusCode())
                .describedAs("restocking \"" + code + "\" to " + stock)
                .isEqualTo(HttpStatus.OK);
    }

    /** A rule arriving after people have already joined the queue. */
    private static void onlyForCustomersWhoHaveEarned(String code, long lifetimePoints) {
        assertThat(app.tryToChangeTheOffer(code,
                Map.of("minimumLifetimePointsEarned", lifetimePoints)).getStatusCode())
                .describedAs("restricting \"" + code + "\" to customers who have earned "
                        + lifetimePoints)
                .isEqualTo(HttpStatus.OK);
    }

    private static void withdraw(String code) {
        assertThat(app.withdrawTheOffer(code).state()).isEqualTo("WITHDRAWN");
    }

    /** Points to spend, for the tests whose subject is that nothing was spent. */
    private static void earn(long points) {
        app.deposit(app.savingsAccountOf(ANKE), ANKE, points + ".00");
    }
}
