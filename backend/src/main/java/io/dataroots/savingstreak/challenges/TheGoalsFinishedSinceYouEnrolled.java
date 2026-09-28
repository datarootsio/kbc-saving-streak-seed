package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.CustomerAccounts;
import io.dataroots.savingstreak.accounts.SavingsAccount;
import io.dataroots.savingstreak.goals.GoalStatus;
import io.dataroots.savingstreak.goals.GoalsService;
import io.dataroots.savingstreak.goals.RecordedGoal;
import io.dataroots.savingstreak.goals.RecordedGoalMove;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The reading for {@link ChallengeKind#GOALS_COMPLETED}: how many of the customer's savings goals
 * this enrolment has seen finished since it began.
 *
 * <p><strong>This is the one kind that writes, and the reason is that the goals module derives
 * completion rather than dating it.</strong> A goal is finished when its allocation has reached its
 * target, and both of those are figures that move — so the goals ledger can say whether a goal is
 * finished now, and can never say when it became so. Every other kind in this module differences two
 * marks or walks a dated ledger; this one has neither to work from, so it looks, and writes down
 * what it saw. {@link GoalSeenFinished} argues the storage out; what follows is the rule.
 *
 * <p><strong>Look, write what is new, count what counts.</strong> Each pass asks every savings
 * account the customer holds for its live goals, and for each one standing at
 * {@link GoalStatus#COMPLETED} that this enrolment has no sighting of, writes one. The reading is
 * the number of sightings that count. Running twice changes nothing, because the second pass finds
 * every sighting already written — the same shape of idempotence the judging pass itself has, and
 * for the same reason: the rows are the record, so the pass cannot disagree with them.
 *
 * <p><strong>A goal counts once because the sighting is keyed by the goal.</strong> Finish a goal,
 * empty it, finish it again, and there is still one row with its identifier on it — the second
 * sighting is not taken, and the reading does not move. Two different goals are two rows. That is
 * user story 29 written as a primary key rather than as a rule something has to remember to apply.
 *
 * <p><strong>A goal already finished when they enrolled counts for nothing, and the first pass runs
 * after the enrolment rather than at it.</strong> That gap is the hard part of this kind, and it is
 * settled by asking the goal's own money moves rather than by whatever the first pass happens to
 * find: a goal is treated as already finished exactly when what had been allocated to it <em>as of
 * the enrolment's moment</em> had already reached its target. The moves ledger is dated and
 * append-only, so that question has one answer however late anybody gets round to asking it — which
 * is what a lazy baseline of "everything complete the first time we looked" would not have. A
 * customer who enrols and finishes a goal before they ever open the tab is counted, and that is the
 * whole point of not taking the cheap answer.
 *
 * <p>Two things the walk does not pretend about, said out loud because they are the seams in it. The
 * target compared against is the goal's target as it stands, not as it stood then, because nothing
 * dates a target either; a bank that has not retuned somebody's goal behind their back sees no
 * difference, and a customer who raised their own target has an unfinished goal now by the only
 * definition the application has. And a move landing in the same millisecond as the enrolment counts
 * as having been there first, which is the same way round as the mark a {@code NEW_SAVINGS}
 * enrolment records.
 *
 * <p><strong>Writing during a read is safe here, and it is worth saying why.</strong> Every path
 * into this class is {@code ChallengesService.judge} or a card read that judges before it answers,
 * and all of them are plain {@code @Transactional} — read-write. There is no read-only transaction
 * anywhere on the way in, so the sighting written while somebody merely opens the Challenges tab
 * commits with the rest of that pass. That is the same trade the tab already makes by awarding rungs
 * on a read, and it is what stops the card from showing a goal finished and the badge from waiting
 * until three tomorrow morning.
 *
 * <p><strong>Across every savings account the customer holds, and none they do not.</strong> A goal
 * belongs to an account and a challenge belongs to a person, so the reading spans the lot —
 * answering otherwise would make "which account did you happen to open the goal on" part of the
 * rule. {@code accountsOf} lists what somebody holds, so a shared pot's account, which nobody holds,
 * is in nobody's reading.
 */
@Component
class TheGoalsFinishedSinceYouEnrolled implements HowAChallengeIsRead {

    private static final Logger log =
            LoggerFactory.getLogger(TheGoalsFinishedSinceYouEnrolled.class);

    private final AccountsService accounts;
    private final GoalsService goals;
    private final GoalSeenFinishedRepository sightings;

    TheGoalsFinishedSinceYouEnrolled(AccountsService accounts, GoalsService goals,
                                     GoalSeenFinishedRepository sightings) {
        this.accounts = accounts;
        this.goals = goals;
        this.sightings = sightings;
    }

    @Override
    public ChallengeKind kind() {
        return ChallengeKind.GOALS_COMPLETED;
    }

    @Override
    public BigDecimal readingOf(ChallengeDefinition definition, ChallengeEnrolment enrolment,
                                Instant now) {
        Map<Long, GoalSeenFinished> seen = new HashMap<>();
        for (GoalSeenFinished sighting : sightings.findByEnrolmentId(enrolment.id())) {
            seen.put(sighting.goalId(), sighting);
        }
        for (long savingsAccountId : savingsAccountsHeldBy(enrolment.customerId())) {
            for (RecordedGoal goal : goals.goalsOn(savingsAccountId)) {
                if (goal.status() != GoalStatus.COMPLETED || seen.containsKey(goal.id())) {
                    continue;
                }
                seen.put(goal.id(), takeTheSightingOf(definition, enrolment, goal, now));
            }
        }
        long counted = seen.values().stream()
                .filter(GoalSeenFinished::countsTowardsTheChallenge)
                .count();
        // The inputs behind the figure: how many goals of theirs this enrolment has ever seen
        // finished and how many of those it is allowed to count, so that a customer told their
        // reading is one when they have finished three goals can be answered from the log alone.
        log.debug("goals finished reading customerId={} challenge={} enrolmentId={} "
                        + "enrolledAt={} goalsSeenFinished={} alreadyFinishedAtEnrolment={} "
                        + "reading={}",
                enrolment.customerId(), definition.code(), enrolment.id(), enrolment.enrolledAt(),
                seen.size(), seen.size() - counted, counted);
        return BigDecimal.valueOf(counted);
    }

    /** Every savings account the customer holds, and nothing for a customer who is not there. */
    private List<Long> savingsAccountsHeldBy(long customerId) {
        return accounts.accountsOf(customerId)
                .map(CustomerAccounts::savingsAccounts)
                .orElse(List.of())
                .stream()
                .map(SavingsAccount::getId)
                .toList();
    }

    /**
     * Writes down a goal this enrolment has not seen finished before, with the verdict on whether it
     * counts settled at the same time.
     *
     * <p>One INFO line for a goal that counts, because that is a business event — it is the moment
     * the customer's challenge moved, and the line beside it in the log is the rung it may have just
     * cleared. A goal that was already finished when they joined is not an event and says so at
     * DEBUG: nothing happened, something was ruled out.
     */
    private GoalSeenFinished takeTheSightingOf(ChallengeDefinition definition,
                                               ChallengeEnrolment enrolment, RecordedGoal goal,
                                               Instant now) {
        boolean already = hadAlreadyReachedItsTargetBy(goal, enrolment.enrolledAt());
        GoalSeenFinished written = sightings.save(GoalSeenFinished.seen(
                enrolment.customerId(), enrolment.id(), definition.code(), goal.savingsAccountId(),
                goal.id(), now, already));
        if (already) {
            log.debug("goal not counted towards a challenge customerId={} challenge={} "
                            + "enrolmentId={} goalId={} savingsAccountId={} target={} "
                            + "reason=it had already been finished when they enrolled enrolledAt={}",
                    enrolment.customerId(), definition.code(), enrolment.id(), goal.id(),
                    goal.savingsAccountId(), goal.target(), enrolment.enrolledAt());
            return written;
        }
        log.info("challenge goal seen finished customerId={} challenge={} enrolmentId={} goalId={} "
                        + "savingsAccountId={} goalName={} target={} seenAt={}",
                enrolment.customerId(), definition.code(), enrolment.id(), goal.id(),
                goal.savingsAccountId(), goal.name(), goal.target(), now);
        return written;
    }

    /**
     * Whether the goal had already been finished at that moment: what the moves ledger had put into
     * it, less what it had taken back out, as of then, against the target it carries.
     *
     * <p>Asked of the goal's own history rather than of a completion date, because there is no
     * completion date — that is the whole reason this class stores anything. The history is dated and
     * append-only, so the answer does not depend on when it is asked, which is exactly what is needed
     * of a question whose answer decides whether something counts for ever.
     *
     * <p>One read per goal, and only for a goal being written down for the first time. A goal already
     * sighted is never asked about again, and a goal that has never been finished is never asked
     * about at all — so the ordinary read of a tab asks nothing, and the pass that does ask asks once
     * per goal per enrolment in the life of the enrolment.
     */
    private boolean hadAlreadyReachedItsTargetBy(RecordedGoal goal, Instant enrolledAt) {
        BigDecimal held = BigDecimal.ZERO;
        for (RecordedGoalMove move : goals.historyOf(goal.savingsAccountId(), goal.id())) {
            if (move.movedAt().isAfter(enrolledAt)) {
                continue;
            }
            held = Objects.equals(goal.id(), move.intoGoalId())
                    ? held.add(move.amount())
                    : held.subtract(move.amount());
        }
        return held.compareTo(goal.target()) >= 0;
    }
}
