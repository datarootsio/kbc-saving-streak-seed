package io.dataroots.savingstreak.rewards;

/**
 * Whatever tells a customer something, asked to tell one of them that their turn in a queue came
 * and the thing is being held for them.
 *
 * <p><strong>Declared here and implemented elsewhere, the same shape and for the same reason as
 * {@link WhereAWaiterStands} beside it and {@code accounts.WhoMayPayIntoAnAccountNobodyHolds}
 * before either.</strong> This module writes no notification and imports nothing that does. It
 * states what happened in its own vocabulary — this customer, this offer, this deadline — and
 * the Notifications module, which already reads half the application in order to have something
 * to say, answers it. Rewards gains no outbound dependency, and a Rewards that called a
 * notifications service would have gained the one this whole feature has spent fifteen slices
 * not gaining.
 *
 * <p><strong>Said at the moment of the promotion rather than waited for, and that is a
 * departure worth arguing.</strong> The Notifications module's own sweep exists so that there
 * is <em>one producer</em> of notifications, and it states that plainly: a rung lost at noon is
 * announced at four the next morning, and raising it inside the withdrawal would put a second
 * writer in a second module. Nothing here breaks that. The writer is still the Notifications
 * module — this is its bean, implementing this module's interface — and the only thing that
 * moves is when it is asked. It has to move, because the two halves of the argument that make a
 * nightly delay cheap are both false here. A rung is a standing fact, so hearing about it half
 * a day late costs nothing; a hold is seventy-two hours long and starts running the instant it
 * is created, so a customer told at four the following morning has lost a third of it to the
 * schedule. And the notifications sweep runs at four while the rewards sweep runs at five, so
 * "the next run" is not half a day later but twenty-three hours — most of the hold, to a
 * customer who never asked to wait.
 *
 * <p><strong>Nothing this raises may stop a promotion.</strong> The hold is written, the place
 * in the queue is closed, and then the customer is told; an implementation that threw would
 * roll back a promotion that had already been decided and would hand the stock to the same
 * person again the following night. That is a promise the caller keeps rather than one this
 * interface can enforce, and it is written down on {@code RewardsService} where the call is
 * made.
 *
 * <p>One implementation today and no registry of them, for the reason the accounts interface
 * gives: this is here for the direction of the dependency and not for the plurality.
 */
public interface TellingAPromotedWaiter {

    /**
     * Tell this customer that the offer they were waiting for is theirs until the moment on the
     * hold.
     *
     * <p>The hold itself rather than a customer and a code, because every fact worth saying is
     * on it and because the deadline is the part that matters: "one is being kept for you" with
     * no "until when" is the deadline nobody saw coming that a hold must never be. The title is
     * on it too, so that whatever writes the sentence does not have to go back to the catalogue
     * for the one word a person would recognise.
     *
     * @param hold the hold the sweep has just created, always live and always {@code HELD}
     */
    void aHoldIsWaitingFor(AHoldOnAnOffer hold);
}
