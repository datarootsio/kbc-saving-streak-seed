package io.dataroots.savingstreak.sharedpots;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A member's departure from a shared pot, as the rest of the application reads it: who left, what
 * they were to the pot, how much of it was still theirs, where that money went, and what the pot
 * holds now they have gone.
 *
 * <p><strong>The figure is their own remaining euros, exact to the cent.</strong> Not a share of the
 * pot, not a proportion of what they paid in, and not a rounded anything: the sum of what is left of
 * the deposits this member made into this pot. That is what makes a settlement need nobody's
 * approval — it moves only money that was theirs all along — and it is why there is no rounding rule
 * to state here. A member who never paid in, or whose contributions have already gone out of the pot
 * in an approved withdrawal, is settled nought and no money moves at all.
 *
 * <p>The role is what they held at the moment they stopped being a member, because that is what the
 * departure was: the membership is gone by the time anybody reads this, and a page that could not
 * say what the person it is reporting on used to be would be reporting a customer identifier and a
 * figure. When they joined is here for the same reason.
 *
 * <p>The withdrawal is the movement of money in the deposits module, and it is absent when there was
 * none. An absence therefore says something rather than nothing: this member had nothing left in the
 * pot, which is a perfectly ordinary way to leave one and not a failure to settle.
 *
 * <p><strong>And the current account is absent in exactly the same case.</strong> A member who
 * leaves names the account their money should come back to; a member settled by the pot closing has
 * it read off their most recent contribution, because closing is one act rather than a form per
 * person. Somebody who never paid in has shown the pot no account and needs none — there is nothing
 * to send anywhere — so the two absences travel together and say one thing: nothing of theirs was in
 * the pot, and nothing moved.
 *
 * <p>What the pot now holds is the other members' money and only theirs, quoted beside the
 * settlement so that whoever reads it can see that the two figures are the one arithmetic: what the
 * pot held, less what was this member's, is what is left for everybody else.
 *
 * <p>Who decided is deliberately not here. Leaving and being removed are the same act with the same
 * consequences for the money, and the difference between them is in the log line and in which
 * request arrived — not in a flag a screen would have to render differently.
 */
public record ASettlement(long potId, String potName, Long customerId, String name, PotRole role,
                          Instant joinedAt, BigDecimal settled, Long toCurrentAccountId,
                          Long withdrawalId, Instant settledAt, BigDecimal thePotNowHolds) {
}
