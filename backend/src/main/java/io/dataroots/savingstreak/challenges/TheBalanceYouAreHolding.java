package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.time.Instant;

import io.dataroots.savingstreak.deposits.DepositsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The reading for {@link ChallengeKind#BALANCE_REACHED}: what the customer is holding in savings
 * right now, across every savings account they have.
 *
 * <p><strong>The balance and not the mark, which is the whole difference between this kind and
 * {@link TheNewSavingsSinceYouEnrolled}.</strong> New saving is measured against a high-water mark
 * that never falls, so a withdrawal costs a customer nothing there. A buffer is the money itself: it
 * falls when money leaves, because a buffer that has been spent is not a buffer, and a challenge
 * that claimed otherwise would be telling somebody they have an emergency fund they do not have.
 * The two readings disagreeing after a withdrawal is the feature working rather than a bug in it.
 *
 * <p><strong>Nothing needs an anti-farming rule here.</strong> A balance cannot be inflated by
 * moving money about — a thousand euros paid in and taken out again leave the figure exactly where
 * it started — so the trick the mark exists to defeat has nothing to bite on. What this kind cannot
 * survive is being re-enrolled in, and that is answered by the definition being seeded as once in a
 * lifetime rather than by anything here.
 *
 * <p><strong>The customer's, never an account's.</strong> {@code stillSavedBy} already spans
 * everything they hold, which is what keeps two savings accounts from being a way of being asked for
 * less: a thousand euros split across two pots is a thousand euros held.
 *
 * <p><strong>The enrolment's mark is not read and the moment is not used.</strong> A stock kind is a
 * statement about now: what somebody was holding when they joined is not subtracted, because the
 * question is whether the buffer exists rather than whether it is new — and that, again, is why the
 * bank only ever asks it once.
 */
@Component
class TheBalanceYouAreHolding implements HowAChallengeIsRead {

    private static final Logger log = LoggerFactory.getLogger(TheBalanceYouAreHolding.class);

    private final DepositsService deposits;

    TheBalanceYouAreHolding(DepositsService deposits) {
        this.deposits = deposits;
    }

    @Override
    public ChallengeKind kind() {
        return ChallengeKind.BALANCE_REACHED;
    }

    @Override
    public BigDecimal readingOf(ChallengeDefinition definition, ChallengeEnrolment enrolment,
                                Instant now) {
        // Floored at nothing as belt and braces rather than as a case that happens: a balance summed
        // from what deposits still hold cannot go negative, and a progress bar pointing backwards
        // would cost more to explain than the comparison costs to make.
        BigDecimal reading = deposits.stillSavedBy(enrolment.customerId()).max(BigDecimal.ZERO);
        log.debug("balance reached reading customerId={} challenge={} enrolmentId={} reading={}",
                enrolment.customerId(), definition.code(), enrolment.id(), reading);
        return reading;
    }
}
