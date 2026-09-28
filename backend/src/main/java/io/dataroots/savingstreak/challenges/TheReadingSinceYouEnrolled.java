package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;

/**
 * How far a {@link ChallengeKind#NEW_SAVINGS} enrolment has got: the most the customer has ever
 * saved now, less the most they had ever saved at the moment they joined.
 *
 * <p><strong>This subtraction is the whole anti-farming rule, and it is a rule by construction
 * rather than by policing.</strong> The mark it measures against is {@code
 * DepositsService.mostEverSavedBy} — the same high-water mark a deposit already earns points
 * against. That mark never falls: taking money out lowers what a customer holds and leaves the mark
 * exactly where it was. So money that only refills the gap a withdrawal left raises no mark, and
 * therefore moves no challenge. Pay in, collect, take it out, pay it back in, and the reading is the
 * same number it was three steps ago — not because anything noticed, but because there is no other
 * answer the arithmetic could give.
 *
 * <p><strong>Why the mark and not a notion of its own.</strong> {@code TheMostEverSaved} was built
 * for exactly this problem one feature ago, it is already per-customer across every savings account
 * they hold, and its semantics are the ones this module needs. A second notion of "money that
 * counts" would give the application two answers to one question and would guarantee they drift —
 * and the euros that fill a challenge would stop being the euros that earned points, which is the
 * one sentence this feature most needs to stay true. If a reviewer reads one thing in this module,
 * it should be this paragraph.
 *
 * <p><strong>And the mark is the customer's, never an account's.</strong> Two savings accounts are
 * two ways of paying into one challenge rather than a loophole, because the figure on both sides of
 * the subtraction already spans everything they hold.
 *
 * <p>Nothing here is stored. The reading is worked out on every read, which is what lets the
 * development clock be wound in either direction without leaving a counter behind describing a week
 * that is now in the future — the same bargain every derived figure in this application makes.
 */
final class TheReadingSinceYouEnrolled {

    private TheReadingSinceYouEnrolled() {
    }

    /**
     * What the enrolment has counted so far: what the mark has risen by since it was recorded, and
     * never less than nothing.
     *
     * <p>The floor at nothing is belt and braces rather than a case that happens. The mark cannot
     * fall, so the difference cannot be negative — but a reading below zero would be a progress bar
     * pointing backwards, and the cost of being sure is one comparison.
     *
     * @param markNow       the most the customer has ever saved, as it stands
     * @param measuringFrom the same figure as it stood when they enrolled
     */
    static BigDecimal above(BigDecimal markNow, BigDecimal measuringFrom) {
        return markNow.subtract(measuringFrom).max(BigDecimal.ZERO);
    }
}
