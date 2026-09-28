package io.dataroots.savingstreak.accounts;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Presents the recurring bills whose dates have fallen, once a night.
 *
 * <p><strong>At half past two in the morning, and the half hour is the feature rather than a
 * detail.</strong> The night runs income at one, the saving rules at two, these bills at half past
 * two, points expiry at three, loyalty at half past and the notifications sweep at four. Bills go
 * <em>after</em> the saving rules, deliberately and permanently: the salary lands, the rules take
 * their cut, and the bills are then presented against whatever is left.
 *
 * <p>Put them before the rules and a sweep would only ever move the surplus that survived the rent.
 * No bill could then fail, nothing would ever be owed, and the whole point of declaring what leaves
 * an account would be gone — the application would be back to teaching that saving is free. The
 * lesson this feature exists for is that a customer who swept their balance into savings on payday
 * finds the rent cannot be paid, and that lesson is made entirely of these two jobs running in this
 * order. <strong>A later tidy-up of cron expressions that moves this job earlier does not make the
 * night neater; it deletes the feature.</strong>
 *
 * <p>And it runs before the notifications sweep at four, so that a bill that could not be paid is a
 * fact in the record by the time the thing that tells the customer about it looks. This job records
 * what happened; the sweep reads it and writes notifications, which is the separation every other
 * reason in that module observes.
 *
 * <p><strong>It catches up, because on a wound clock catching up is the only thing it ever
 * does.</strong> {@code MovableClock} moves in whole calendar days and a cron expression never fires
 * for the days it skipped, so a trainer who winds two months forward and runs this once has to see
 * both rents presented or the feature cannot be demonstrated at all. Every due date between each
 * bill's cursor and now, oldest first; {@link WhenABillIsDue} is the calendar and
 * {@link AccountsService} is the bookkeeping.
 *
 * <p>The moment comes off the application's clock rather than the machine's, which is what makes a
 * monthly bill demonstrable in an afternoon. A job that read {@code Instant.now()} would find
 * nothing to do and say so convincingly.
 *
 * <p>In every profile, like everything else that is part of the application. Only the ability to run
 * it out of turn is a lab affordance, and that lives in the web layer.
 *
 * <p>Package-private: the job is the Accounts module's, and nothing outside this package needs a
 * handle on the thing that triggers it.
 */
@Component
class BillsAreTakenWhenTheyFallDue {

    private static final Logger log = LoggerFactory.getLogger(BillsAreTakenWhenTheyFallDue.class);

    /**
     * Half past two in the morning, every day — after the saving rules have taken their cut and
     * before the points expire.
     *
     * <p>A named constant because it is also what the jobs endpoint reports as this job's schedule,
     * and a trainer deciding whether to run it by hand is reading that line. The name says the
     * ordering out loud so that the expression and the reason for it cannot be separated by anybody
     * reading only this line: it is <em>after two</em>, and the class documentation says what
     * happens to the feature if it stops being.
     */
    private static final String EVERY_NIGHT_AT_HALF_PAST_TWO_AFTER_THE_SAVING_RULES =
            "0 30 2 * * *";

    private final AccountsService accounts;
    private final Clock clock;

    BillsAreTakenWhenTheyFallDue(AccountsService accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * Named for what it does rather than for when it does it, because the name is what a trainer
     * types into {@code POST /api/dev/jobs/{name}/run}.
     *
     * <p>Nothing is logged here about the outcome, and nothing is returned to be logged. The module
     * writes one INFO line per debit and one per run, which are the lines worth grepping; a second
     * line here would say the same thing from further away.
     */
    @Scheduled(cron = EVERY_NIGHT_AT_HALF_PAST_TWO_AFTER_THE_SAVING_RULES)
    void takeBillsDue() {
        Instant now = clock.instant();
        // The reading, so that a run that took nothing on a clock nobody had wound forward is
        // explainable without anybody having to ask the clock endpoint what day it thought it was.
        log.debug("bills run takes its moment from the application clock clockReads={}", now);
        accounts.takeBillsDueBy(now);
    }
}
