package io.dataroots.savingstreak.potwithdrawals;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;

/**
 * Puts a second customer in a pot with a role, the way a second customer really gets into one: the
 * owner invites them, and they accept.
 *
 * <p>Two calls rather than a membership written behind the API's back. Every line of these tests,
 * setting up as well as asserting, goes through the endpoints somebody else drives — which is what
 * makes a test that passes evidence that the feature works rather than evidence that a row exists.
 *
 * <p>The role travels as the word the API takes rather than as the module's enum, because that is
 * what the wire carries and because a test package named after the feature has no business reaching
 * into the production package for a vocabulary. {@code SomebodyElseInThePot} does the same two calls
 * for the tests that live beside the module and speaks its enum; this is the same situation reached
 * the same way, said in the words this package can see.
 */
final class AnotherMemberOfThePot {

    private AnotherMemberOfThePot() {
    }

    /**
     * Invites the customer into the pot with the given role and has them accept, leaving them a
     * member of it.
     *
     * @param app         the application the pot lives in
     * @param potId       the pot to put them in, which is expected to exist
     * @param ownerName   the member who owns it, since only an owner may invite
     * @param invitedName the customer to put in it, who is expected to exist and not already be in it
     * @param role        what they are to be to the pot: {@code OWNER}, {@code CONTRIBUTOR} or
     *                    {@code VIEWER}
     */
    static void joins(AnApplicationWithAClockToMove app, long potId, String ownerName,
                      String invitedName, String role) {
        long invitation = app.invite(potId, ownerName, invitedName, role).id();
        app.accept(potId, invitation, invitedName);
    }
}
