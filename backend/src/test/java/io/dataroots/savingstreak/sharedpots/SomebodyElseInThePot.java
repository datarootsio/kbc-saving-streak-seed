package io.dataroots.savingstreak.sharedpots;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;

/**
 * Puts a second customer in a pot with a role, the way a second customer really gets into one: the
 * owner invites them, and they accept.
 *
 * <p><strong>It was not always able to do that.</strong> The slice that first needed two people in a
 * pot — a contribution earning its depositor and nobody else — landed before invitations existed,
 * and until they did the only way to reach the situation was to write the membership row the
 * acceptance would have written. That fixture said so at length and reached past the seam to do it.
 * Invitations exist now, so it does not have to, and the reach is gone: every line of these tests,
 * setting up as well as asserting, goes through the API again.
 *
 * <p>The tests using it say exactly what they said before. That is the point of having had the
 * fixture rather than the two calls inline — the situation it creates is the same situation, and
 * only the way of reaching it changed.
 *
 * <p>Still in this package rather than in {@code support} because it speaks in {@link PotRole},
 * which is the module's own word and is staying that way.
 */
final class SomebodyElseInThePot {

    private SomebodyElseInThePot() {
    }

    /**
     * Invites the customer into the pot with the given role and has them accept, leaving them a
     * member of it.
     *
     * @param app         the application the pot lives in
     * @param potId       the pot to put them in, which is expected to exist
     * @param ownerName   the member who owns it, since only an owner may invite
     * @param invitedName the customer to put in it, who is expected to exist and not already be in it
     * @param role        what they are to be to the pot
     */
    static void joins(AnApplicationWithAClockToMove app, long potId, String ownerName,
                      String invitedName, PotRole role) {
        long invitation = app.invite(potId, ownerName, invitedName, role.name()).id();
        app.accept(potId, invitation, invitedName);
    }
}
