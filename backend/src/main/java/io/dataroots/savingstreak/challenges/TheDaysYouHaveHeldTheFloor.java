package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.dataroots.savingstreak.deposits.DepositLanded;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.WithdrawalMade;
import io.dataroots.savingstreak.deposits.WithdrawalsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The reading for {@link ChallengeKind#BALANCE_HELD}: how many consecutive days the customer's
 * savings balance has stayed at or above the challenge's floor.
 *
 * <p><strong>This class fetches; it does not decide.</strong> The walk over the movements, the rule
 * that a dip resets the run and the counting of whole days are all
 * {@link WhenTheBalanceLastRoseToTheFloor}'s, and they stay there because that is where they are
 * argued for. All that happens here is asking Deposits and Withdrawals for the two halves of the
 * ledger and handing them over — the same division of labour {@link TheNewSavingsSinceYouEnrolled}
 * makes with the subtraction it does not do itself.
 *
 * <p><strong>Both halves, and both of them the customer's.</strong> A balance is moved by money
 * going in and by money coming out, and each of these two reads already spans every savings account
 * the customer holds: the deposits are theirs, and the withdrawals are the ones taken out of the
 * accounts their deposits went into. So two accounts are two pockets of one buffer, and the module
 * goes on being keyed by customer without learning that savings accounts exist.
 *
 * <p><strong>Bounded at the moment being asked about, which is what the parameter is for.</strong>
 * Both reads stop short of {@code now}, so a trainer who wound the clock forward, paid money in and
 * wound it back is answered about the part of the ledger that has actually happened rather than
 * about a deposit dated in the future. This is the kind {@link HowAChallengeIsRead}'s moment
 * parameter exists for.
 *
 * <p><strong>What it costs, said plainly.</strong> Two queries and a walk over the customer's whole
 * movement history on every read of the card and every judging pass, in exchange for a figure that
 * cannot drift and cannot be left behind by the clock. That is the bargain this application makes
 * everywhere it derives on read, and a savings customer's lifetime of movements is a list a training
 * application can walk in a page load.
 *
 * <p><strong>Counted from the later of the two moments, which is the one thing here that is not
 * purely a reading of the ledger.</strong> The days held are the days since the later of the moment
 * the balance last rose to the floor and the moment the customer enrolled. Without the second half
 * of that, somebody who had quietly held a thousand euros for a year would enrol in "hold EUR 1,000
 * for ninety days" and be ninety days in before they had done anything at all — gold on the spot,
 * for a year of saving the bank never asked them for. The spec rules that out twice over: progress
 * counts from the moment you enrol, and no challenge looks backwards at saving done before
 * enrolment. {@link TheNewSavingsSinceYouEnrolled} measures from the mark the enrolment recorded,
 * {@link TheGoalsFinishedSinceYouEnrolled} discounts the goals already finished by then and
 * {@link TheWeeksYouHaveSecuredSinceEnrolling} counts from the enrolment onwards; this is the same
 * rule said in the vocabulary of a run of days.
 *
 * <p><strong>It is still a stock kind, and the clamp does not make it a flow.</strong> A flow kind
 * accumulates what happened since the enrolment; this one still asks a question about now — is the
 * money there, and has it been there without interruption — and still answers nothing the moment
 * the balance dips, however long the enrolment has run. All the enrolment does is refuse to count
 * days the customer served before anybody was watching. That is why it stays once in a lifetime:
 * the clamp stops a fresh enrolment being worth an instant badge, and not re-enrolling stops it
 * being worth a fresh ninety days of the same untouched thousand every quarter.
 *
 * <p><strong>{@link ChallengeKind#BALANCE_REACHED} is deliberately not clamped this way.</strong>
 * "Have you got a buffer" is an honest question to put to a balance exactly as it stands, and
 * subtracting anything the customer was already holding would make the card mean something other
 * than the words on it. The asymmetry is between a question about a quantity and a question about a
 * duration: a duration is the only one of the two that can have been running before the customer
 * signed up for it.
 */
@Component
class TheDaysYouHaveHeldTheFloor implements HowAChallengeIsRead {

    private static final Logger log = LoggerFactory.getLogger(TheDaysYouHaveHeldTheFloor.class);

    private final DepositsService deposits;
    private final WithdrawalsService withdrawals;

    TheDaysYouHaveHeldTheFloor(DepositsService deposits, WithdrawalsService withdrawals) {
        this.deposits = deposits;
        this.withdrawals = withdrawals;
    }

    @Override
    public ChallengeKind kind() {
        return ChallengeKind.BALANCE_HELD;
    }

    @Override
    public BigDecimal readingOf(ChallengeDefinition definition, ChallengeEnrolment enrolment,
                                Instant now) {
        BigDecimal floor = theFloorOrRefuse(definition, enrolment);
        List<DepositLanded> paidIn = deposits.depositsLandedBefore(enrolment.customerId(), now);
        List<WithdrawalMade> takenBackOut =
                withdrawals.withdrawalsMadeBefore(enrolment.customerId(), now);

        Optional<Instant> roseToIt =
                WhenTheBalanceLastRoseToTheFloor.walkingTheMovements(floor, paidIn, takenBackOut);
        Optional<Instant> countingFrom = roseToIt.map(moment -> theLaterOf(moment, enrolment));
        long days = countingFrom
                .map(moment -> WhenTheBalanceLastRoseToTheFloor.wholeDaysBetween(moment, now))
                .orElse(0L);
        // Both moments and which of them won, beside the floor, the days that gives and the size of
        // the ledger they were walked out of. A reviewer asked why a customer plainly sitting on the
        // floor is being told they have held it for nothing has all three answers here and needs no
        // second look: the walk found a dip, and heldSince says when it ended; or it found no rise
        // at all, and heldSince is empty; or they only joined this morning, and countingFrom is
        // their enrolment rather than their money.
        log.debug("balance held reading customerId={} challenge={} enrolmentId={} floor={} "
                        + "heldSince={} enrolledAt={} countingFrom={} governedBy={} days={} "
                        + "depositsWalked={} withdrawalsWalked={} now={}",
                enrolment.customerId(), definition.code(), enrolment.id(), floor,
                roseToIt.orElse(null), enrolment.enrolledAt(), countingFrom.orElse(null),
                whatDecidedIt(roseToIt, enrolment), days, paidIn.size(), takenBackOut.size(), now);
        return BigDecimal.valueOf(days);
    }

    /**
     * The later of the moment the balance rose to the floor and the moment the customer enrolled,
     * which is where the run of days this challenge pays for actually begins.
     *
     * <p>The two orderings are two different stories and both are ordinary. Money that arrives after
     * the enrolment is the case the challenge was written for, and the floor moment governs. Money
     * that was already there is the case that used to pay gold for nothing, and the enrolment
     * governs. Taking the later of the two is the whole of the rule, and it needs no branch on which
     * story this is.
     */
    private Instant theLaterOf(Instant roseToIt, ChallengeEnrolment enrolment) {
        Instant enrolledAt = enrolment.enrolledAt();
        return roseToIt.isAfter(enrolledAt) ? roseToIt : enrolledAt;
    }

    /**
     * Which of the two moments the count is running from, in a word, for the log line only.
     *
     * <p>A reading of nothing has three quite different causes and the log is where somebody finds
     * out which one they are looking at, so the line says so rather than leaving two instants side
     * by side to be compared by eye.
     */
    private String whatDecidedIt(Optional<Instant> roseToIt, ChallengeEnrolment enrolment) {
        if (roseToIt.isEmpty()) {
            return "neither: the floor has never been reached";
        }
        return roseToIt.get().isAfter(enrolment.enrolledAt()) ? "the floor" : "the enrolment";
    }

    /**
     * The floor this challenge is about, which is the kind's own figure on the definition.
     *
     * <p>A row of this kind without one was seeded wrong, and there is no reading to give for it: a
     * floor of nothing would be held by everybody from their first cent, so guessing would mint gold
     * badges rather than fail. Refused out loud, with the challenge named, because the thing to fix
     * is the row.
     */
    private BigDecimal theFloorOrRefuse(ChallengeDefinition definition,
                                        ChallengeEnrolment enrolment) {
        BigDecimal floor = definition.kindParameter();
        if (floor == null) {
            String reason = definition.code() + " asks how long a balance has been held and names "
                    + "no floor to hold: a " + ChallengeKind.BALANCE_HELD
                    + " challenge needs its kindParameter set to the amount in euros.";
            log.warn("balance held reading refused customerId={} challenge={} enrolmentId={} "
                            + "reason={}",
                    enrolment.customerId(), definition.code(), enrolment.id(), reason);
            throw new IllegalStateException(reason);
        }
        return floor;
    }
}
