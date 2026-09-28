package io.dataroots.savingstreak.sharedpots;

/**
 * Which way one member answered a proposal to take money out of a shared pot: yes, or no.
 *
 * <p>Two values and not three. Saying nothing is not an answer — it is the absence of one, and it is
 * what {@code whoseAssentItNeeds} reports — so a {@code PENDING} here would be a row written to say
 * that no row was written. A member who has not answered has no answer.
 *
 * <p>Its own enum rather than {@link ProposalState} reused, although the two share their words. What
 * a member said and where the proposal stands are different facts and they come apart in both
 * directions: a proposal is {@link ProposalState#REJECTED} the moment one member says {@link
 * #REJECTED} while everybody else has said {@link #APPROVED} or nothing at all, and a proposal every
 * member approved can still end {@code REJECTED} because the pot no longer holds the money. One enum
 * for both would make those two sentences unwritable.
 *
 * <p>Stored as its word rather than as its position, like every other enum this module keeps, so
 * that a value added later cannot silently change what every existing row means.
 */
public enum ProposalAnswer {

    /**
     * Yes. The money may go as far as this member is concerned — and when nobody is left to answer,
     * the approval that arrives last is the one the withdrawal happens in.
     */
    APPROVED,

    /**
     * No. One of these ends the proposal on its own, because the euros at stake are partly this
     * member's and a veto that had to be repeated would be no veto at all.
     */
    REJECTED
}
