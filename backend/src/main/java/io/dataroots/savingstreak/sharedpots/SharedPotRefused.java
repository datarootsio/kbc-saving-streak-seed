package io.dataroots.savingstreak.sharedpots;

/**
 * Something about a shared pot the application will not do, carrying the reason in words the person
 * who asked can act on — a pot nobody has heard of, a customer nobody has heard of, or a pot that is
 * not one this application will open.
 *
 * <p>This module's own refusal rather than one borrowed from Accounts or Goals, for the reason
 * Gifting gives about Rewards: a pot refuses for its own reasons and they will grow apart from
 * everybody else's. It already has a kind about a thing no other module has — the pot — and the
 * kinds this feature is going to need next are about roles and about membership, which nothing else
 * in this application has any concept of.
 *
 * <p>A kind arrives with the rule that raises it, and never before: a kind added ahead of anything
 * that can raise it is a status nobody can check and a sentence nobody has read. The first three
 * below are the whole of what opening and reading a pot can be refused for; the rest arrived with
 * invitations, which is where this module first had a role to refuse and first had something that
 * could be answered twice. Proposals, settlement and closing will each bring their own.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer.
 */
public class SharedPotRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal can be about. They are different because the person reading it
     * has a different thing to do next: find the right pot, check who they are signed in as, or fix
     * what they typed.
     */
    public enum Kind {

        /** No pot answers to that identifier. */
        NO_SUCH_POT,

        /** The customer said to be doing this is not one this application has heard of. */
        NO_SUCH_CUSTOMER,

        /**
         * A pot this application will not open as it was described — a pot with no name, or a name
         * of nothing but spaces. A form to fix rather than a state to wait out.
         */
        AGAINST_THE_RULES,

        /** No invitation answers to that identifier, or none that this pot ever issued. */
        NO_SUCH_INVITATION,

        /**
         * No customer banks under the address an invitation was sent to — including no address given
         * at all. Gifting's own {@code NO_SUCH_RECIPIENT}, in this module's words, because it is the
         * same mistake: a typo in an email address, made about somebody else.
         */
        NO_SUCH_RECIPIENT,

        /**
         * Somebody asked to do something a member with their role may not do — a contributor or a
         * viewer inviting, or revoking. The first kind in this application that is about a role, and
         * the one the 403 arrived for: the request is understood, the caller is known, and they are
         * not allowed.
         */
        NOT_ALLOWED,

        /**
         * An invitation answered by somebody it was not addressed to. Not allowed rather than absent
         * — a member can see the invitation in the pot's own list, so pretending it is not there
         * would be a lie they could disprove by looking.
         */
        NOT_THEIR_INVITATION,

        /**
         * An invitation to somebody who is in the pot already. A conflict rather than a form to fix:
         * what was typed is perfectly good and it is the pot's state that will not allow it — and a
         * second invitation must never be a quiet way of changing the role somebody holds.
         */
        ALREADY_A_MEMBER,

        /**
         * A second invitation to somebody who is already waiting to answer the first. Two questions
         * where there was one, and two acceptances where the database allows one membership.
         */
        ALREADY_INVITED,

        /**
         * Something that has been answered already and is answered once: an invitation that was
         * accepted, declined or revoked, or a member answering a withdrawal proposal they have
         * already approved or rejected. The sentence says where it stands, or which way they went.
         *
         * <p>The two share a kind because they are the same mistake and the same thing to do about
         * it — a page showing a stale row, refreshed and the answer already there. Where they differ
         * is what is answered once: an invitation is answered once by anybody, and a proposal is
         * answered once <em>by each member</em>, which is why a proposal still waiting on somebody
         * else refuses a second answer from a member who has already given one.
         */
        ALREADY_ANSWERED,

        /**
         * An invitation somebody addressed to themselves. Gifting's own kind, in this module's
         * words and for the same reason it has one: a request that would do nothing and report
         * success is worse than one that explains itself.
         */
        TO_YOURSELF,

        /**
         * A role change about somebody who is not in the pot — a customer who exists and belongs to
         * something else, or a customer number nobody answers to at all. An absence rather than a
         * refusal about what they may do: there is no membership here to change, and the two are
         * the same news to the owner who asked.
         *
         * <p>Its own kind rather than {@code NO_SUCH_CUSTOMER}, because the two are different
         * mistakes with different things to do next: one is a person this application has never
         * heard of, and the other is a person it knows perfectly well who is not in this pot.
         */
        NO_SUCH_MEMBER,

        /**
         * A change that would leave the pot with nobody able to administer it — the last owner
         * giving up the role. A conflict rather than a form to fix: what was typed is perfectly
         * good and it is the state of the pot that will not allow it, and the sentence says what to
         * do about the pot instead.
         *
         * <p>The kind this application can least afford to get wrong. A pot whose last owner
         * stepped down has nobody who may invite, promote or close it, and no way back.
         */
        LAST_OWNER,

        /** No proposal answers to that identifier, or none that this pot ever had. */
        NO_SUCH_PROPOSAL,

        /**
         * A proposal to take out more than the pot has in it. Its own kind rather than one more
         * thing that is against the rules, because it is the one refusal here that is about the
         * money and not about the request: what was typed is a perfectly good figure and the pot
         * simply does not hold it. The sentence names what it does hold, so the member can type a
         * smaller one — and the same objection is raised a second time when a proposal that was
         * affordable when it was made is approved after a settlement has emptied the pot.
         */
        MORE_THAN_THE_POT_HOLDS,

        /**
         * A proposal that has already been approved, rejected or taken back. A proposal is answered
         * once, and the sentence says where this one stands. A conflict rather than a form to fix:
         * what was asked for is perfectly good and it is the proposal's state that will not allow
         * it — the same distinction {@code ALREADY_ANSWERED} draws for an invitation.
         */
        THE_PROPOSAL_IS_CLOSED,

        /**
         * An action on a pot an owner has closed — paying in, inviting, answering an invitation,
         * changing a role, proposing, approving, leaving, or closing it a second time. A conflict
         * rather than a form to fix, the same distinction {@code THE_PROPOSAL_IS_CLOSED} and an
         * abandoned goal are drawn at: what was asked for is perfectly good and it is the state of
         * the pot that will not allow it, so a page that told somebody to correct what they typed
         * would send them looking for a mistake they did not make.
         *
         * <p><strong>It is never raised in front of a read.</strong> A closed pot is a record and
         * the record is the point of it: its membership, its contributions, its money movements and
         * its proposals stay readable for ever, and the only thing that stops is doing anything new
         * to it. A kind that guarded the reads too would make the feature end by deleting itself.
         */
        POT_IS_CLOSED
    }

    private final Kind kind;

    SharedPotRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
