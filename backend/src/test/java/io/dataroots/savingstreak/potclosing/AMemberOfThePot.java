package io.dataroots.savingstreak.potclosing;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;

/**
 * Puts a second customer in a pot with a role, the way a second customer really gets into one: the
 * owner invites them, and they accept.
 *
 * <p>Two calls rather than a membership written behind the API's back, for the reason the settlement
 * tests give and doubly so here: what these tests assert is that closing settles every member and
 * leaves every membership standing, and a row this package put there itself would be proving
 * something about its own fixture.
 *
 * <p>Its own copy in this package rather than a shared one, which is the arrangement
 * {@code AnotherMemberOfThePot}, {@code AMemberOfThePot} and {@code SomebodyElseInThePot} already
 * settle for the other pot packages: the same three lines, said in the words each package can see,
 * and none of them reaching into another test package for a fixture.
 *
 * <p>The role travels as the word the API takes rather than as the module's enum, because that is
 * what the wire carries and because a test package named after the feature has no business reaching
 * into the production package for a vocabulary.
 */
final class AMemberOfThePot {

    private AMemberOfThePot() {
    }

    /**
     * Invites the customer into the pot with the given role and has them accept, leaving them a
     * member of it.
     *
     * @param app         the application the pot lives in
     * @param potId       the pot to put them in, which is expected to exist and to be open
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
