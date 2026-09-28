package io.dataroots.savingstreak.potsettlements;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;

/**
 * Puts a second customer in a pot with a role, the way a second customer really gets into one: the
 * owner invites them, and they accept.
 *
 * <p>Two calls rather than a membership written behind the API's back. Every line of these tests,
 * setting up as well as asserting, goes through the endpoints somebody else drives — which matters
 * more here than anywhere else in this feature, because what these tests assert is that a membership
 * written by an acceptance can be ended by a departure, and a row this package put there itself
 * would be proving something about its own fixture.
 *
 * <p>The role travels as the word the API takes rather than as the module's enum, because that is
 * what the wire carries and because a test package named after the feature has no business reaching
 * into the production package for a vocabulary. {@code AnotherMemberOfThePot} does the same two
 * calls for the tests about proposals, and {@code SomebodyElseInThePot} does them in the module's
 * own words for the tests that live beside it: the same situation reached the same way, said in the
 * words each package can see.
 */
final class AMemberOfThePot {

    private AMemberOfThePot() {
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
