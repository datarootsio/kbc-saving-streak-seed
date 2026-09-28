package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;

/**
 * One move worth making, as the rest of the application sees one: out of that goal, into this one,
 * this much, and the reason it is being suggested.
 *
 * <p>Both ends carry a name beside their identifier, for the reason {@link RecordedGoalMove}'s do: a
 * page saying "out of Holiday, into House" should not have to fetch every goal on the account to look
 * two numbers up. Both ends are always a goal — this suggestion never reaches for the part of the
 * balance no goal has claimed, and {@link AReallocationWorthSuggesting} says why — so neither
 * identifier is ever null, and they are boxed only because that is the shape a goal identifier has
 * everywhere else in this module.
 *
 * <p>{@code reason} names the goal the move helps and where that goal stands, because that is the
 * question a customer asks about the row in front of them. It travels on the move rather than on the
 * suggestion as a whole for the same reason: a sentence kept at the top of a page is not a sentence
 * beside the line somebody is reading.
 *
 * <p><strong>Nothing here is stored.</strong> A move is worked out on the read that answers with it
 * and forgotten; accepting one works the whole suggestion out again. So an identifier in here is a
 * goal's, and never a suggestion's — there is no such row to have one.
 */
public record ASuggestedMove(Long outOfGoalId, String outOfGoalName, Long intoGoalId,
                             String intoGoalName, BigDecimal amount, String reason) {
}
