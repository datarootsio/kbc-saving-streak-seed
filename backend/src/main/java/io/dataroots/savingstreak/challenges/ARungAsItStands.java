package io.dataroots.savingstreak.challenges;

import java.time.Instant;
import java.util.Optional;

/**
 * One rung of a challenge on a customer's card: what it asks for and what it pays, together with
 * the moment this customer won it — if they have.
 *
 * <p><strong>Beside the rung rather than in a list of its own.</strong> The card draws a ladder, and
 * a ladder is three rungs each of which is either lit or not; a parallel list of the rungs won would
 * make a page match two collections up by name in order to colour one of them, which is the sort of
 * arithmetic this API does on the customer's behalf everywhere else.
 *
 * <p><strong>The enrolment on the card, and no other.</strong> {@code wonAt} is empty for a rung
 * this customer has never reached <em>and</em> for one they reached in an earlier round of a
 * repeatable challenge: taking "save EUR 500" on again is a fresh EUR 500 measured from a fresh
 * mark, so a ladder drawn with the first round's badges still lit would be telling somebody they
 * were two thirds of the way through something they have only just started. The trophy case is
 * where an old badge goes on being true, and it is never emptied by any of this.
 *
 * <p>An award is a fact and is never revoked, so a rung won on an enrolment that has since been
 * left or has lapsed stays lit on that card. Quitting costs nobody anything they had already
 * earned, and the ladder has to say so.
 */
public record ARungAsItStands(ChallengeRung rung, Optional<Instant> wonAt) {

    static ARungAsItStands notWonYet(ChallengeRung rung) {
        return new ARungAsItStands(rung, Optional.empty());
    }
}
