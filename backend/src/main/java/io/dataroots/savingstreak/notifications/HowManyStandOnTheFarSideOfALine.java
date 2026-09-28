package io.dataroots.savingstreak.notifications;

/**
 * For one of the five lines the scheme draws: how many customers would have something said to them
 * under a candidate scheme that is not being said to them under the version in force, and how many
 * the other way about.
 *
 * <p><strong>Counted, never raised.</strong> Nothing behind this record writes a notification, and
 * that is the property an administrator is entitled to before they press anything: asking what a
 * repricing would shout at people must not itself shout at them. The sweep's once-only bookkeeping
 * is deliberately not consulted either — this is a count of who stands on the far side of a line
 * tonight, not a rehearsal of which rows tonight's four o'clock job would write, which would depend
 * on what last night's job already said and would answer a different question every night.
 *
 * <p><strong>Both numbers, because a scheme that says less is as consequential as one that says
 * more.</strong> Raising the arrears count from three to five quiets a warning for everybody owing
 * four dates, and a preview that reported only the new shouting would let that through in silence.
 *
 * <p><strong>One customer can be counted on both sides of one line, and that is right.</strong> A
 * ladder that moves its bottom rung from EUR 100 to EUR 200 leaves a customer with EUR 300 standing
 * on a rung they do not stand on now <em>and</em> off the rung they do — something new would be
 * said to them and something being said would stop. Counting them once, on whichever side happened
 * to be tested first, would hide half of what the change does. So the two figures are two counts
 * and are not meant to sum to the population.
 *
 * @param line                 which of the five the counts are about
 * @param wouldBeToldAndIsNot  how many customers have something to be told under the candidate that
 *                             they have not under the version in force
 * @param isToldAndWouldNotBe  how many have something under the version in force that they would
 *                             not have under the candidate
 */
public record HowManyStandOnTheFarSideOfALine(ALineTheSchemeDraws line, int wouldBeToldAndIsNot,
                                              int isToldAndWouldNotBe) {

    /** Whether this line moves anybody at all, which is what "nothing changes" is made of. */
    public boolean itWouldMoveNobody() {
        return wouldBeToldAndIsNot == 0 && isToldAndWouldNotBe == 0;
    }
}
