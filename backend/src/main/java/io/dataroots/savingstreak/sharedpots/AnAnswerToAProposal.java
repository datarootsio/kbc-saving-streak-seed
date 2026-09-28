package io.dataroots.savingstreak.sharedpots;

import java.time.Instant;

/**
 * One member's answer to a proposal, as the rest of the application sees it: who answered, what they
 * are to the pot, which way they went, and when.
 *
 * <p>The person and not merely their identifier, because a page showing who has answered is showing
 * people: "Bram approved this on Tuesday" is the sentence, and a caller that had to ask Accounts for
 * a name in order to write it would be making another call to say one thing. The name is looked up
 * when the answer is read and never stored on the row, so somebody renamed is renamed in every
 * answer they have ever given.
 *
 * <p><strong>Which way, and not only that they answered.</strong> A list of people who have spoken
 * would leave a member looking at a proposal unable to tell who is holding it up from who has
 * already agreed, and a rejected proposal would read exactly like an approved one with somebody
 * still to answer. That the two are different is the whole point of keeping the answers.
 *
 * <p>The role may be absent, and that absence is a fact rather than a gap: an answer outlives the
 * membership that gave it, so a member who has since left the pot is still the person who answered
 * and the pot's record still says what they said. What they are to the pot <em>now</em> is nothing,
 * which is what a missing role means. Everything that renders one treats it that way rather than
 * dropping the answer, because a decision with the decider erased would be a vote counted in
 * private.
 *
 * <p>The moment comes off the application's clock, like every other moment this module records, so
 * that an answer given against a wound clock is dated where the trainer wound it to.
 */
public record AnAnswerToAProposal(Long customerId, String name, PotRole role, ProposalAnswer answer,
                                  Instant answeredAt) {
}
