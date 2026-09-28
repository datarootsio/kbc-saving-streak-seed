package io.dataroots.savingstreak.sharedpots;

/**
 * Where an invitation to a shared pot stands: waiting for an answer, or closed by one of the three
 * things that can close it.
 *
 * <p>A state rather than a pair of flags or a nullable moment, because the three ways an invitation
 * ends are three different facts about what happened and a page shows them differently: somebody
 * said yes, somebody said no, or the person who asked took the question back. A row carrying only
 * "answered at" could say none of that, and a page would be left guessing from whether a membership
 * turned up beside it.
 *
 * <p><strong>Answered once.</strong> Only {@link #PENDING} can be answered at all; every other value
 * here is final, and there is no request in this application that moves an invitation out of one of
 * them. That is what stops an old email from being a second vote — accepting twice would be a
 * membership written twice, and a re-answered invitation would be a way of changing a role that
 * nobody agreed to change.
 *
 * <p><strong>And it never expires.</strong> There is no state for an invitation that timed out,
 * because nothing in this application times one out: points expire and nothing else does, and a
 * nightly job to close invitations would be a cost with no story behind it. An invitation waits for
 * as long as it takes, and an owner who has changed their mind revokes it.
 *
 * <p>It travels out of the module as a word rather than as a number, the idiom a goal's state and a
 * gift's direction already set: whoever renders it decides what to call each one.
 */
public enum InvitationState {

    /** Sent, and waiting for the customer it was addressed to. The only state that can be answered. */
    PENDING,

    /** The invited customer said yes, and is a member of the pot with the role it named. */
    ACCEPTED,

    /** The invited customer said no, and is a member of nothing. The invitation stays as the record. */
    DECLINED,

    /**
     * The owner took it back before anybody answered — a mistyped address, or a change of mind.
     *
     * <p>Distinct from {@link #DECLINED} because the two say different things about who decided, and
     * a pot's history that could not tell them apart would have the invited customer refusing
     * something they were never shown.
     */
    REVOKED
}
