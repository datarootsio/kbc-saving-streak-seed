package io.dataroots.savingstreak.web;

import java.time.Instant;

import io.dataroots.savingstreak.sharedpots.AnAnswerToAProposal;
import io.dataroots.savingstreak.sharedpots.PotRole;

/**
 * One member's answer to a withdrawal proposal as the API reports it: who answered, what they are to
 * the pot, which way they went, and when.
 *
 * <p>Flat rather than a member with an answer hung off them, because that is how a page reads it:
 * one row per answer, a face and a word. Nesting the member would make every renderer reach through
 * a level to write "Bram approved this", and the pot's list of members is a different list with a
 * different job.
 *
 * <p>The answer travels as its own word — {@code APPROVED} or {@code REJECTED} — the idiom a goal's
 * state and an invitation's already set: whoever renders it decides which of them gets the tick.
 *
 * <p><strong>The role may be null, and that is a fact rather than a gap.</strong> An answer outlives
 * the membership that gave it, so a member who has since left the pot still appears here as the
 * person who decided, with nothing where their role was. A page shows the name and leaves the badge
 * off. Dropping the answer instead would leave a proposal that had plainly been decided with nobody
 * having decided it.
 *
 * <p>The moment comes off the application's clock, so an answer given against a wound-forward clock
 * reads where the trainer wound it to.
 */
record ProposalAnswerResponse(Long customerId, String name, String role, String answer,
                              Instant answeredAt) {

    static ProposalAnswerResponse of(AnAnswerToAProposal answer) {
        return new ProposalAnswerResponse(answer.customerId(), answer.name(),
                nameOfTheRole(answer.role()), answer.answer().name(), answer.answeredAt());
    }

    /** The role as its word, and nothing at all for somebody who is no longer in the pot. */
    private static String nameOfTheRole(PotRole role) {
        return role == null ? null : role.name();
    }
}
