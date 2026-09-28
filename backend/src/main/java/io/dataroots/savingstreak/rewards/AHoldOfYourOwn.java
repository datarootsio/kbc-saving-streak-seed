package io.dataroots.savingstreak.rewards;

import java.time.Instant;

/**
 * The hold <em>this</em> customer has on <em>this</em> offer, as one reading needs to know about
 * it: when it runs out, and whether they took it inside the savings week being counted.
 *
 * <p>Two facts and not the row, because two facts is all any of the rules want and a reading that
 * carried the entity would let a rule reach for a column nobody meant it to have. The row stays
 * behind {@link RewardsService}.
 *
 * <p><strong>{@code takenThisWeek} is decided by the service rather than worked out here.</strong>
 * Which week a moment falls in is {@code SavingsWeek}'s answer, taken once for a whole reading
 * against the day that reading is being made on — the argument is the one
 * {@code RewardsService.whatTheyHaveHadAlready} makes about counting claims. A record that worked
 * it out for itself would be a second Monday living in a second place, and a list long enough to
 * cross midnight could then count two different weeks half way down.
 *
 * <p>Null, as a whole, is the answer for a customer who holds none of this offer — which is every
 * customer for every offer nearly all of the time — and it is the one thing every rule that cares
 * about holds tests first.
 */
record AHoldOfYourOwn(Instant lapsesAt, boolean takenThisWeek) {
}
