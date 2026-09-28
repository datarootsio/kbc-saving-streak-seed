package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * How one {@link ChallengeKind}'s question is answered: given the challenge, somebody's enrolment in
 * it and the moment being asked about, the figure that enrolment has reached.
 *
 * <p><strong>A new kind is a new file.</strong> Adding one means a value on {@link ChallengeKind}, an
 * implementation of this interface beside it, and a seeded row in {@link ChallengesOnStartUp} that
 * names the kind. Nothing in {@link ChallengesService} is edited to add a kind — not a switch arm,
 * not a constructor parameter, not a method signature. Spring finds the implementations and the
 * service indexes them by the kind each one answers for, so the only thing the service knows about
 * any kind is that something answers for it.
 *
 * <p><strong>That is a decision about who is allowed to break whom, and it was taken because four
 * more kinds are coming at once.</strong> A switch inside the service would have made every new kind
 * a change to one method that every other kind already depends on: four tickets editing four arms of
 * the same expression, each needing a different dependency threaded through the same parameter list —
 * a streak service for one, a balance and a clock for two more, a goals service and a stored
 * observation for the fourth. Every one of those would have widened a signature the other three were
 * also standing on. One file each cannot collide with one file each, and a reading that needs
 * something new asks for it in its own constructor rather than in everybody's.
 *
 * <p><strong>The card and the judging pass must never be able to give different answers.</strong>
 * That is the other half of what this buys, and it is the half a customer would notice. A page
 * showing a reading past gold beside no gold badge, or a badge minted for a figure the card never
 * displayed, are both the same bug: two derivations of one number. Both paths go through the reader
 * for the kind — {@code challengesFor} to draw the card, {@code judge} to decide what has been won —
 * so there is one derivation per kind and there is nowhere for a second one to live.
 *
 * <p><strong>Nothing is stored and the moment is a parameter.</strong> The reading is worked out on
 * every read, the same bargain every derived figure in this application makes, which is what lets the
 * development clock be wound in either direction without leaving a counter behind describing a week
 * that is now in the future. The moment comes in rather than being read from a clock inside an
 * implementation, so that the card and the pass that judges it are asked about the same instant.
 *
 * <p><strong>Each implementation fetches what it needs.</strong> Nothing is handed in beyond the
 * definition, the enrolment and the moment: a reading that wants the high-water mark asks Deposits
 * for it, one that wants a run of weeks asks Streaks. That is a query per live enrolment rather than
 * one per customer per pass, and it is the right way round — a figure read for the whole customer and
 * passed down is a parameter every kind has to accept and most kinds have no use for, which is the
 * coupling this interface exists to be rid of. The cost is the cost this application already accepts
 * everywhere it derives on read.
 *
 * <p>Package-private, like the definitions and the enrolments it is asked about. What a challenge
 * means is this module's business; {@link AChallengeAsItStands} is what leaves.
 */
interface HowAChallengeIsRead {

    /**
     * The one kind this answers for.
     *
     * <p>One reading per kind and one kind per reading, both directions checked when
     * {@link ChallengesService} is built: two readings claiming a kind is an ambiguity nothing later
     * could resolve, and a kind with no reading is a challenge the bank can offer on a card and this
     * application cannot read. Neither is allowed to start.
     */
    ChallengeKind kind();

    /**
     * How far this enrolment has got, as of that moment.
     *
     * <p>Never negative and never a null: a reading is what a progress bar is drawn from and what a
     * threshold is compared against, and both of those want a number. A kind whose honest answer is
     * "none yet" answers zero.
     *
     * @param definition the challenge as the bank describes it, including its
     *                   {@link ChallengeDefinition#kindParameter()} where the kind asks a figure of
     *                   its own
     * @param enrolment  whose enrolment, in what, measuring from what, since when
     * @param now        the moment being asked about, taken from the application's clock by the
     *                   caller so that a trainer winding time forward is answered in the week they
     *                   have wound to
     */
    BigDecimal readingOf(ChallengeDefinition definition, ChallengeEnrolment enrolment, Instant now);
}
