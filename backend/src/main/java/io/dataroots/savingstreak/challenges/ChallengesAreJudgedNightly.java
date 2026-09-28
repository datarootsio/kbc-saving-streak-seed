package io.dataroots.savingstreak.challenges;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Judges everybody's live enrolments, once a night, so that a badge does not wait for its owner to
 * go looking for it.
 *
 * <p><strong>It exists for the customer who never opens the tab.</strong> The challenge endpoints
 * judge before they answer, which is what puts a badge on the screen somebody is already looking at
 * — and it is also the whole of the judging a customer who never visits would ever get. Their rung
 * would be cleared, their points unpaid and their trophy case empty until the day curiosity struck.
 * A nightly sweep makes the reward follow the saving rather than the visit.
 *
 * <p><strong>It reimplements nothing.</strong> It calls the same {@link ChallengesService#judge}
 * pass the endpoints call, per customer, so what a night does and what a read would have done are
 * the same code arriving at the same answer. A sweep with judging logic of its own would be a second
 * opinion about when a rung is cleared, and the two would eventually disagree in front of a
 * customer.
 *
 * <p><strong>Everybody with a live enrolment, one pass each.</strong> The pass is about a person: it
 * takes one mark for them and measures every challenge they are in against it, so the sweep asks for
 * the customers rather than the enrolments and judges each of them once.
 *
 * <p><strong>One customer's failure is not the night's.</strong> Judging is per-customer work and
 * nothing about it is shared, so a pass that throws — a row nothing can be made of, a ledger that
 * would not answer — costs exactly the customer it threw for. It is logged at ERROR with the
 * exception, because it is a thing nobody asked for and nobody will be told about otherwise, and the
 * loop carries on. Abandoning the sweep would let one bad row hold up every badge in the bank, and
 * the next night would find the same row and do it again.
 *
 * <p>The moment comes off the application's clock rather than the machine's, which is what makes a
 * challenge counted in months demonstrable in an afternoon: a trainer winds the clock forward, runs
 * this job through the development jobs endpoint, and watches the badges arrive dated where the
 * clock says they were won. A job that read {@code Instant.now()} would award nothing and say so
 * convincingly. The reading is logged before anything is judged, so a sweep that awarded nothing is
 * explainable without anybody having to ask the clock endpoint what year it thought it was.
 *
 * <p>Half past four in the morning, and the hour is load-bearing in one direction: it is after
 * everything in the night that moves euros — the salary at one, the saving rules at two, the bills
 * at half past — so that money a standing rule moved into savings while the customer slept is judged
 * the same night rather than the next one. It is also behind the points sweep at three and the
 * loyalty bonus at half past, which means a rung's points are credited into a ledger the night has
 * already settled and cannot be swept by the same night's expiry.
 *
 * <p>In every profile, like everything else that is part of the application. Only the ability to run
 * it out of turn is a lab affordance, and that lives in the web layer.
 *
 * <p>Package-private, like {@link ChallengesOnStartUp}: the sweep is this module's, and nothing
 * outside the package needs a handle on the thing that triggers it.
 */
@Component
class ChallengesAreJudgedNightly {

    private static final Logger log = LoggerFactory.getLogger(ChallengesAreJudgedNightly.class);

    /**
     * Half past four in the morning, every day — last of the night's runs.
     *
     * <p>Daily rather than weekly, so that a rung cleared on a Tuesday is paid on the Wednesday and
     * not at the weekend. A named constant because it is also what the jobs endpoint reports as this
     * job's schedule, and a trainer deciding whether to run it by hand is reading that line.
     */
    private static final String EVERY_NIGHT_AT_HALF_PAST_FOUR = "0 30 4 * * *";

    private final ChallengesService challenges;
    private final ChallengeEnrolmentRepository enrolments;
    private final Clock clock;

    ChallengesAreJudgedNightly(ChallengesService challenges, ChallengeEnrolmentRepository enrolments,
                               Clock clock) {
        this.challenges = challenges;
        this.enrolments = enrolments;
        this.clock = clock;
    }

    /**
     * Named for what it does rather than for when it does it, because the name is what a trainer
     * types into {@code POST /api/dev/jobs/{name}/run} to make it happen now.
     *
     * <p>One moment for the whole sweep, read once at the top. Everybody judged in this run is
     * judged as of the same instant, so two customers who cleared the same rung on the same night
     * carry the same date on their badges rather than dates a few milliseconds apart that say
     * nothing except how long the loop took.
     *
     * <p>The line at the end is the one worth grepping: how many people the sweep had to judge and
     * how many of them it could not. What each judgement decided is the pass's own business and it
     * writes that itself, one line per rung awarded; repeating any of it here would say the same
     * thing from further away.
     */
    @Scheduled(cron = EVERY_NIGHT_AT_HALF_PAST_FOUR)
    void judgeTheChallenges() {
        Instant now = clock.instant();
        // The reading, so that a sweep that awarded nothing on a clock nobody had wound forward is
        // explainable without anybody having to ask the clock endpoint what year it thought it was.
        log.debug("challenges sweep takes its moment from the application clock clockReads={}", now);

        List<Long> everybody = enrolments.everybodyHoldingAnEnrolment(EnrolmentState.ACTIVE);
        log.debug("challenges sweep has customers to judge now={} customers={}", now, everybody);

        int couldNotBeJudged = 0;
        for (long customerId : everybody) {
            try {
                challenges.judge(customerId, now);
            } catch (RuntimeException thrown) {
                couldNotBeJudged++;
                // With the exception, because nobody asked for this sweep and this line is the only
                // account of it there will ever be — and the next customer is judged regardless.
                log.error("challenges not judged customerId={} now={} "
                                + "reason=the judging pass threw and the sweep carried on",
                        customerId, now, thrown);
            }
        }

        log.info("challenges sweep finished now={} customers={} judged={} couldNotBeJudged={}",
                now, everybody.size(), everybody.size() - couldNotBeJudged, couldNotBeJudged);
    }
}
