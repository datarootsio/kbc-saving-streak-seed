package io.dataroots.savingstreak.rewards;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The rewards scheme's one nightly sweep: everything that has to happen to a catalogue and its
 * vouchers overnight, in the order it has to happen in.
 *
 * <p><strong>One job and not three, and the order inside it is the correctness.</strong> All
 * three steps are here now, because the whole reason they belong together is that they have to
 * run in a particular order:
 *
 * <ol>
 *   <li><strong>Vouchers past their day are expired.</strong> It returns no stock and refunds no
 *       points, so nothing downstream of it moves as a result — which is why it is safe for it
 *       to be first.</li>
 *   <li><strong>Holds that have run out are lapsed.</strong> The second step, added by the
 *       slice that introduced holds. A lapsing hold puts the stock it was holding back into the
 *       window, which is exactly what the step below it needs to find.</li>
 *   <li><strong>Waiters are promoted into holds</strong>, and this is last: promotion hands the
 *       oldest waiter whatever stock there is, so it has to run against the tidiest and most
 *       complete picture of the stock the night can give it. It adds its count to the summary
 *       line rather than writing a line of its own.</li>
 * </ol>
 *
 * <p><strong>Nothing tells step three that stock came back.</strong> The spec names three ways
 * it does — an administrator raises it, a hold lapses, a voucher is cancelled — and says the
 * same promotion path serves all three. It does, by knowing about none of them: it reads what
 * is left of every offer somebody is waiting for, and all three routes change exactly that
 * subtraction. So the lapse above is not the promotion's trigger; it is one of three things
 * that will already have moved the figure it reads.
 *
 * <p><strong>The second step is deliberately bookkeeping rather than the thing that ends a
 * hold, and the third step's correctness does not depend on it in the way it looks like it
 * does.</strong> A hold is over the instant the clock passes the moment written on it: the
 * stock has been back since then, every reading of what is left has said so, and a conversion
 * attempted since has been refused. So step three could not literally observe less stock by
 * running first. What step two does is write that down, so that the rows and the clock agree
 * and so that promotion has a tidy table to read — no stale {@code HELD} row for the
 * "is this customer already holding one" question to trip over, and a defined order a night's
 * log can be read in. That is a guarantee about the <em>rows</em> rather than about the stock,
 * and it is worth having; the stronger claim is not made here because it is not true. The
 * argument for the clock being the authority is on {@code HoldState}.
 *
 * <p>It is nevertheless one job and not three. Three separately scheduled jobs would express
 * the ordering as three cron expressions a few minutes apart, which is an ordering that holds
 * until a step takes longer than the gap — and the failure would be a customer who was first in
 * the queue being passed over for one night and nobody ever finding out why. One job also means
 * one transaction boundary per step and one line in the log for the whole night, which is what
 * makes a stock figure that moved overnight accountable at all.
 *
 * <p><strong>The day comes off the application's clock rather than the machine's</strong>, which
 * is what makes a shelf life demonstrable in an afternoon: a trainer winds the clock past the
 * deadline, runs this job through the development jobs endpoint, and watches the voucher go. A job
 * that read {@code LocalDate.now()} would find nothing to do on a wound-forward clock and would
 * say so convincingly. There is not a live {@code now()} call anywhere in this application's main
 * source and this job is not the one that introduces one.
 *
 * <p>A calendar day <em>and</em> the moment it was read from, both taken once at the top and
 * handed to the steps that want them, so that everything one night does is judged against one
 * reading of the clock rather than against the clock as it stood a few milliseconds into the run.
 * Two shapes because the two deadlines are genuinely different: a voucher's shelf life is a date
 * the customer was promised, argued on {@link VoucherShelfLife}, and a hold's is seventy-two
 * hours from the moment they took it, argued on {@code TheShelfLifeOfAHold}. A hold rounded to a
 * calendar day would lapse up to a day early or late depending on which side of midnight
 * somebody pressed.
 *
 * <p>In every profile, like everything else that is part of the application. Only the ability to
 * run it out of turn is a lab affordance, and that lives in the web layer.
 *
 * <p>Package-private, beside the service, like {@code OldPointsExpireNightly} is beside the
 * ledger: the sweep is the rewards module's own, and nothing outside this package needs a handle
 * on the thing that triggers it.
 */
@Component
class TheRewardsSweepRunsNightly {

    private static final Logger log = LoggerFactory.getLogger(TheRewardsSweepRunsNightly.class);

    /**
     * Five in the morning, every day, and last of the night's runs.
     *
     * <p>After the points sweep at three rather than before it, and the ordering is worth a
     * sentence even though nothing connects the two: expiring a voucher moves no points and
     * expiring points cannot reach a voucher already issued, so the two are genuinely
     * independent. What the late hour buys is that a night's story reads in one direction — the
     * money moves, then the ledger is swept, then the scheme is swept — and that a step added
     * here later, one which does touch the ledger, finds the ledger already settled for the day.
     *
     * <p>Daily rather than hourly, because a shelf life is a date: a voucher whose last good day
     * was yesterday is retired this morning, and retiring it a few hours earlier or later within
     * the day is a distinction nobody holding it can see. A named constant because it is also
     * what the jobs endpoint reports as this job's schedule, and a trainer deciding whether to
     * run it by hand is reading that line.
     */
    private static final String EVERY_NIGHT_AT_FIVE = "0 0 5 * * *";

    private final RewardsService rewards;
    private final Clock clock;

    TheRewardsSweepRunsNightly(RewardsService rewards, Clock clock) {
        this.rewards = rewards;
        this.clock = clock;
    }

    /**
     * Named for what it does rather than for when it does it, because the name is what a trainer
     * types into {@code POST /api/dev/jobs/{name}/run} to make it happen now.
     *
     * <p>Named for the scheme rather than for vouchers, deliberately, even though vouchers are
     * the whole of what it does today. A trainer who learned this job as "expire the vouchers"
     * would have to learn a new name the week holds arrive, and the jobs list a demonstration is
     * driven from is not a thing worth renaming under people.
     *
     * <p>The line at the end is the one worth grepping, and it is the whole sweep's: how many
     * vouchers went, how many holds ran out, how many waiters were given one, and the day it
     * judged them all against. Which vouchers, which holds and which waiters those were is each
     * step's own business and each writes that itself, a line per row; repeating any of it here
     * would say the same thing from further away. All three counts are on this one line rather
     * than on three of their own, so that a balance or a stock figure that moved overnight is
     * explained by one line rather than by three that have to be found together.
     */
    @Scheduled(cron = EVERY_NIGHT_AT_FIVE)
    void sweepTheRewardsScheme() {
        Instant now = clock.instant();
        LocalDate today = now.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
        // The reading, so that a sweep that retired nothing on a clock nobody had wound forward is
        // explainable without anybody having to ask the clock endpoint what year it thought it was.
        log.debug("rewards sweep takes its day from the application clock clockReads={} today={}",
                now, today);

        // One: the vouchers. Nothing it does moves any stock, so nothing below it depends on it.
        int vouchersExpired = rewards.expireVouchersPastTheirDay(today);

        // Two: the holds. This is the step that puts stock back into the window, which is why
        // the promotion step has to come after it and not before. The moment rather than the
        // day, because seventy-two hours is not a calendar quantity.
        int holdsLapsed = rewards.lapseHoldsPastTheirMoment(now);

        // Three: the waiting lists, promoted into holds against the stock as it now stands —
        // which includes everything the step above has just written back, everything an
        // administrator restocked during the day and everything a cancelled voucher returned.
        // It is told about none of those and asks about none of them: it reads what is left of
        // every offer somebody is waiting for, which is the one subtraction all three routes
        // change. Both readings of the clock, because a hold is created at a moment and an
        // offer is judged open on a day.
        int waitersPromoted = rewards.promoteWhoeverIsNextInLine(now, today);

        log.info("rewards sweep finished today={} vouchersExpired={} holdsLapsed={} "
                        + "waitersPromoted={}",
                today, vouchersExpired, holdsLapsed, waitersPromoted);
    }
}
