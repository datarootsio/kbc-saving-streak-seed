package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.streaks.StreaksService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The reading for {@link ChallengeKind#SECURED_WEEKS}: how many separate weeks the customer has
 * secured since their enrolment began, consecutive or not.
 *
 * <p><strong>This class asks; it does not decide.</strong> What a week is, when a week is secured
 * and what "net new savings" means are all the Streaks module's answers, and they stay there. All
 * that happens here is naming the stretch — from the enrolment to the moment being asked about — and
 * counting what comes back. That is the whole of the rule this ticket had to resist restating: the
 * EUR 50 minimum, the Monday-to-Sunday week and the Brussels zone appear nowhere in this module, so
 * a bank that retunes any of them retunes this challenge with it and cannot retune only half of it.
 * A count built here out of deposits and withdrawals would have been a second answer to "what did
 * that week put away", and two answers to that question is exactly the drift the seam exists to
 * prevent.
 *
 * <p><strong>A count, not a run, and the difference is the challenge.</strong> The streak walks
 * backwards and stops at the first week that fell short; this counts every secured week in the
 * stretch and nothing stops it. So a customer who secured week one, missed week two and secured week
 * three reads a streak of one and a challenge of two, and the week they missed set them back by
 * nothing. That asymmetry is the reason the bank offers both, and it is asserted head-on in
 * {@code SeparateWeeksSecuredFillAChallengeApiTest}.
 *
 * <p><strong>Counted in whole weeks from the week the enrolment began.</strong> The week somebody
 * joined in counts, in full, on the same figure the streak judges it by — the alternative is a
 * part-week with its own minimum, which is a rule this application has never had and which would be
 * this module restating the weekly one under another name. The spec is explicit that this kind
 * "counts weeks the existing net-of-withdrawals rule already calls secured", and what follows from
 * that is that a week already secured when somebody enrols mid-week counts for them. That is a
 * fortnight's head start at most, it can be had once per enrolment, and it costs far less than a
 * second definition of a secured week.
 *
 * <p><strong>Nothing is stored, and the moment is a parameter.</strong> The weeks are counted out of
 * the ledger on every read, so a trainer winding the clock forward is answered in the week they have
 * wound to and a wind backwards leaves no count behind describing weeks that are now in the future.
 * A re-enrolment names a later stretch and therefore starts again at nothing, which is what makes
 * this kind honestly repeatable.
 */
@Component
class TheWeeksYouHaveSecuredSinceEnrolling implements HowAChallengeIsRead {

    private static final Logger log =
            LoggerFactory.getLogger(TheWeeksYouHaveSecuredSinceEnrolling.class);

    private final StreaksService streaks;

    TheWeeksYouHaveSecuredSinceEnrolling(StreaksService streaks) {
        this.streaks = streaks;
    }

    @Override
    public ChallengeKind kind() {
        return ChallengeKind.SECURED_WEEKS;
    }

    @Override
    public BigDecimal readingOf(ChallengeDefinition definition, ChallengeEnrolment enrolment,
                                Instant now) {
        List<SavingsWeek> secured =
                streaks.securedWeeksBetween(enrolment.customerId(), enrolment.enrolledAt(), now);
        // A count of weeks as the figure a rung is compared against, so the thresholds on the card
        // read as weeks. Never negative and never null: an enrolment that has secured nothing yet
        // has secured no weeks, which is zero.
        BigDecimal reading = BigDecimal.valueOf(secured.size());
        // The stretch that was scanned and the weeks found secured in it, so that a customer told
        // they have three weeks when they believe they have four can be answered from the log alone:
        // the weeks are named, and the Streaks line just above this one says what each of them put
        // away and what it was short by.
        log.debug("secured weeks reading customerId={} challenge={} enrolmentId={} enrolledAt={} "
                        + "now={} securedWeeks=[{}] reading={}",
                enrolment.customerId(), definition.code(), enrolment.id(), enrolment.enrolledAt(),
                now, secured.stream().map(SavingsWeek::toString).collect(Collectors.joining("; ")),
                reading);
        return reading;
    }
}
