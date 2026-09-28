package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;

import io.dataroots.savingstreak.challenges.ARungAsItStands;

/**
 * One rung of a challenge as the API reports it: which rung, what it asks for, what it pays, and
 * when this customer won it.
 *
 * <p>The threshold is an amount of money on the kinds that ask about money and a count of days,
 * weeks or goals on the ones that do not, which is why it is a plain number rather than anything
 * that calls itself an amount. What it is counted in is the kind's business, and the kind is on the
 * card above.
 *
 * <p><strong>{@code wonAt} is null for a rung that is not lit, and that covers two cases on
 * purpose.</strong> A rung nobody has reached has no moment, and neither has a rung won in an
 * earlier round of a repeatable challenge: the card is about the enrolment the customer is in now,
 * which measures from its own mark and asks for the whole of the rung again. An old badge is still
 * in the trophy case, which is the resource that answers "what have I ever done" and is never
 * emptied by anything here.
 *
 * <p>A moment rather than a flag, because a page drawing a ladder wants to say <em>when</em> beside
 * the rung it has lit, and a boolean would have sent it to the trophy case to find out.
 */
record ChallengeRungResponse(String rung, BigDecimal threshold, long points, Instant wonAt) {

    static ChallengeRungResponse of(ARungAsItStands rung) {
        return new ChallengeRungResponse(
                rung.rung().rung().name(),
                rung.rung().threshold(),
                rung.rung().points(),
                rung.wonAt().orElse(null));
    }
}
