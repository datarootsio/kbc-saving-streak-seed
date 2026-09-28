package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.time.Instant;

import io.dataroots.savingstreak.deposits.DepositsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The reading for {@link ChallengeKind#NEW_SAVINGS}: the most the customer has ever saved now, less
 * the most they had ever saved when they joined.
 *
 * <p><strong>This class fetches; it does not decide.</strong> The subtraction and its floor at
 * nothing are {@link TheReadingSinceYouEnrolled}'s, and they stay there because that is where the
 * anti-farming rule is written down and argued for. All that happens here is asking Deposits for the
 * mark as it stands and handing the two figures to the one place that knows what to do with them. A
 * second copy of {@code max(0, now - then)} would be a second answer to the question this module most
 * needs one answer to.
 *
 * <p><strong>The mark is read per enrolment rather than once per customer.</strong> It used to be
 * read once for a whole card list or a whole judging pass and threaded down through every method
 * between, which is how one kind's dependency became every kind's parameter — and with four more
 * kinds arriving, none of which wants the mark, that parameter would have grown a companion for each
 * of them. A reading asks for what it needs. Deposits derives the mark from the ledger on every read
 * anyway, and this application already scans per account on read in half a dozen places for the same
 * reason: a figure that cannot drift is worth a query.
 *
 * <p><strong>The moment is not used, and that is a fact about this kind rather than an oversight.
 * </strong> New saving since enrolment is a difference between two marks and neither of them is
 * dated; the mark never falls, so there is no "as of" to ask it about. The kinds that count days or
 * weeks are the ones the parameter is there for.
 */
@Component
class TheNewSavingsSinceYouEnrolled implements HowAChallengeIsRead {

    private static final Logger log = LoggerFactory.getLogger(TheNewSavingsSinceYouEnrolled.class);

    private final DepositsService deposits;

    TheNewSavingsSinceYouEnrolled(DepositsService deposits) {
        this.deposits = deposits;
    }

    @Override
    public ChallengeKind kind() {
        return ChallengeKind.NEW_SAVINGS;
    }

    @Override
    public BigDecimal readingOf(ChallengeDefinition definition, ChallengeEnrolment enrolment,
                                Instant now) {
        BigDecimal markNow = deposits.mostEverSavedBy(enrolment.customerId());
        BigDecimal reading = TheReadingSinceYouEnrolled.above(markNow, enrolment.measuringFrom());
        // The two figures the subtraction was made from, so that a customer told their progress is
        // EUR 40 when they have saved EUR 400 this year can be answered from the log alone: the mark
        // as it stands and the mark they joined at.
        log.debug("new savings reading customerId={} challenge={} enrolmentId={} mostEverSaved={} "
                        + "measuringFrom={} reading={}",
                enrolment.customerId(), definition.code(), enrolment.id(), markNow,
                enrolment.measuringFrom(), reading);
        return reading;
    }
}
