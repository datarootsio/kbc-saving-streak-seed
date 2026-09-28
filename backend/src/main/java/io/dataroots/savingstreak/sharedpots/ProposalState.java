package io.dataroots.savingstreak.sharedpots;

/**
 * Where a proposal to take money out of a shared pot stands: waiting for the members whose euros are
 * at stake, or closed by one of the three things that can close it.
 *
 * <p>A state rather than a pair of flags or a nullable moment, for the reason {@link InvitationState}
 * gives about the same shape of question: the three ways a proposal ends are three different facts
 * about what happened and a page shows them differently — the money went, somebody said no, or the
 * person who asked thought better of it. A row carrying only "closed at" could say none of that.
 *
 * <p><strong>Answered once.</strong> Only {@link #PROPOSED} may be answered at all; every other value
 * here is final, and there is no request in this application that moves a proposal out of one of
 * them. That is what stops a rejected proposal from being approved by somebody who was slower to
 * click, and what stops a withdrawal that has already moved money from moving it twice.
 *
 * <p><strong>All four are here although this slice only reaches two of them.</strong> Proposing
 * makes a {@link #PROPOSED} one and the proposer may take it back to {@link #WITHDRAWN}; approving
 * and rejecting are the slice after this one, and the two states they lead to are named now for the
 * reason {@link PotRole} names three roles before anything can grant two of them — they are the
 * vocabulary this whole feature is written in, and a listing that promises "every proposal in any
 * state" has to be able to say which state it means. Nothing here is a claim that the application can
 * yet put a proposal into them.
 *
 * <p>It travels out of the module as a word rather than as a number, the idiom a goal's state and an
 * invitation's already set: whoever renders it decides what to call each one.
 */
public enum ProposalState {

    /**
     * Made, and waiting for every other member with money still in the pot. The only state that can
     * be answered, and the only one in which nothing has happened to anybody's money.
     */
    PROPOSED,

    /**
     * Every member whose assent was needed said yes, and the money left the pot in the same
     * transaction as the last of those answers.
     *
     * <p>Written in the same transaction as the movement, never before it and never after: a
     * proposal that read {@code APPROVED} beside a pot that still held the money would be a receipt
     * for something that had not happened.
     */
    APPROVED,

    /**
     * One of the members whose assent was needed said no, which ends it. One rejection is enough:
     * the money is partly theirs, and a "no" that had to be repeated would be a veto in name only.
     *
     * <p><strong>And one thing that is not a member says it too.</strong> A proposal that was
     * affordable when it was made and is not when the last approval lands ends here as well, because
     * the pot cannot pay it — the spec's own word for that outcome, and the alternative was a
     * proposal left waiting for an approval that had already arrived. Which of the two happened is
     * read off who answered it: a rejection has a member's {@code REJECTED} answer against it, and a
     * proposal the pot could not afford has nothing but approvals.
     */
    REJECTED,

    /**
     * The member who proposed it took it back — a change of mind, or a figure they typed wrong.
     *
     * <p>Distinct from {@link #REJECTED} because the two say different things about who decided, and
     * a pot's record that could not tell them apart would have the other members refusing something
     * they were never asked about.
     */
    WITHDRAWN;

    /**
     * Whether a proposal in this state is still waiting to be answered, which is the whole of "a
     * proposal is answered once".
     *
     * <p>Asked of the state rather than compared at the places that ask, because it is read by every
     * rule that acts on a proposal — taking one back now, approving or rejecting one next — and a
     * comparison written at each of those would be this decision kept in three places that could
     * quietly disagree.
     */
    public boolean isStillWaiting() {
        return this == PROPOSED;
    }
}
