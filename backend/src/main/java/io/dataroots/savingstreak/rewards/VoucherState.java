package io.dataroots.savingstreak.rewards;

/**
 * Where a voucher is in its life: handed out, handed over, run out of time, or revoked.
 *
 * <p><strong>This is the column that gives a voucher a life at all.</strong> Until now a claim was
 * the moment it was made and nothing afterwards — {@code ClaimedReward} said so in as many words,
 * "there is no way to undo one, because redemption is final" — and what that meant in practice was
 * that the application printed six characters and then forgot about them. The same code worked at
 * the same counter every day for ever, nobody could tell a voucher that had been handed over from
 * one that had not, and the scheme was issuing liabilities it could neither see nor retire. A state
 * is the smallest thing that fixes all three.
 *
 * <p><strong>All four values existed from the start, and the transitions arrived one at a
 * time.</strong> That was the whole argument for naming all four up front — a state added to a
 * stored enum is a schema change and a backfill, whereas a transition added to a state that
 * already exists is a method — and it paid for itself three times over. A counter marks a
 * voucher {@link #USED}; the scheme's nightly sweep marks one {@link #EXPIRED} once it has
 * outlived the shelf life its offer gave it; an administrator marks one {@link #CANCELLED} with
 * a reason, which is the only one of the three that gives the points back. Not one of them
 * needed a migration, and the set is now complete.
 *
 * <p><strong>All three ends are terminal.</strong> There is no un-using, no un-expiring and no
 * un-cancelling, for the reason the finality of a claim was already argued from: a voucher is
 * something a person is holding, and a scheme that could quietly put one back into play would be a
 * scheme in which the thing in somebody's hand does not mean what it says.
 *
 * <p>An enum rather than rows, unlike the catalogue beside it, because this genuinely is a closed
 * set the code owns: these four are the whole of what can happen to a voucher, and a fifth would be
 * a change to how the application works rather than to what it sells.
 */
public enum VoucherState {

    /** Handed to the customer and good at a counter. Every voucher starts here. */
    ISSUED,

    /** Handed over at a counter, which recorded the moment and said which counter it was. */
    USED,

    /**
     * Outlived the shelf life its offer gave it, and written by the scheme's nightly sweep.
     *
     * <p>Only ever reached by a voucher whose offer named a number of days, because the day it
     * runs out is written onto the voucher when it is claimed and a voucher without one is
     * invisible to the sweep. Nothing is refunded and nothing is returned: no points go back into
     * the ledger, no stock goes back into the window, and the claim goes on counting against
     * every limit it counted against before. An expiry is the customer's own miss, and a shelf
     * life somebody is refunded for is not a shelf life at all — nobody would ever have a reason
     * to use a voucher in time. That is exactly what makes it a different state from
     * {@link #CANCELLED} rather than the same one under another name, and the two paragraphs are
     * meant to be read against each other.
     */
    EXPIRED,

    /**
     * Revoked by an administrator, who had to say why, and the only end of a voucher's life that
     * gives anything back.
     *
     * <p><strong>A cancellation is the scheme's own mistake, which is the whole of why it is a
     * different state from {@link #EXPIRED} rather than the same one under another
     * name.</strong> The points come back — as a fresh batch with twelve months of its own, never
     * as a top-up of the batches that were spent, because those may since have expired and points
     * returned to a dead batch would be unspendable while looking like a refund — and the stock
     * goes back in the window, because a claim in this state stops counting against it. It stops
     * counting against how many of something one customer may have, too, and for the same reason:
     * an allowance spent on a claim nobody ended up making would be the customer paying for
     * somebody else's mistake twice.
     *
     * <p>Terminal like the other two, and this is the one where that matters most. There is no
     * un-cancelling and deliberately no way to put a voucher back into play once the points for
     * it have been handed back, because that would be one customer holding both.
     *
     * <p>The reason is stored beside the state and is required. "A mistake can be undone" is only
     * half of what whoever runs the scheme is promised; the other half is "and explained", and a
     * cancelled voucher with no sentence on it is a code that simply stopped working.
     */
    CANCELLED;

    /**
     * Whether somebody at a counter should hand the thing over.
     *
     * <p>Derived from the state rather than stored beside it, which is the line this application
     * already holds for a points balance and a goal's status: a second column saying "good" would
     * be a copy of this answer that could disagree with it, and the day it disagreed a counter
     * would be reading the wrong one. It lives on the enum rather than in the service because it
     * is a fact about the state itself, and because the sentence a counter reads and the refusal a
     * counter gets have to be decided from the same place or they will sooner or later differ.
     */
    public boolean isGoodAtACounter() {
        return this == ISSUED;
    }
}
