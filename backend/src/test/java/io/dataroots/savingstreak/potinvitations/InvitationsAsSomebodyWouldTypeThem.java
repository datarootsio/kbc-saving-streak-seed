package io.dataroots.savingstreak.potinvitations;

import java.util.HashMap;
import java.util.Map;

/**
 * The bodies a customer's form would send when they invite somebody into a shared pot, built once so
 * that a test about one field does not have to restate the other two.
 *
 * <p>Static and free of any application, in the shape {@code PotsAsSomebodyWouldTypeThem} set: one
 * copy of "an invitation is an address, a role and the person sending it" keeps the tests that
 * refuse one from drifting into disagreeing about what an invitation looks like on the wire.
 *
 * <p>{@code HashMap} rather than {@code Map.of}, so that a test about a missing field can send a
 * null, which {@code Map.of} will not hold. That is the whole reason this class exists at all: the
 * three ways an invitation can arrive unsendable are an address that is nothing, a role that is
 * nothing, and nobody sending it.
 */
final class InvitationsAsSomebodyWouldTypeThem {

    private InvitationsAsSomebodyWouldTypeThem() {
    }

    /** The ordinary invitation: an address, the role it grants, and the owner sending it. */
    static Map<String, Object> anInvitationTo(String contactDetails, String role, long customerId) {
        Map<String, Object> invitation = new HashMap<>();
        invitation.put("contactDetails", contactDetails);
        invitation.put("role", role);
        invitation.put("customerId", customerId);
        return invitation;
    }

    /** A form sent with the address box never filled in, which is not the same as filling in nothing. */
    static Map<String, Object> anInvitationAddressedToNobody(String role, long customerId) {
        Map<String, Object> invitation = new HashMap<>();
        invitation.put("contactDetails", null);
        invitation.put("role", role);
        invitation.put("customerId", customerId);
        return invitation;
    }

    /** A form that says who to invite and nothing about what they would be to the pot. */
    static Map<String, Object> anInvitationWithNoRole(String contactDetails, long customerId) {
        Map<String, Object> invitation = new HashMap<>();
        invitation.put("contactDetails", contactDetails);
        invitation.put("role", null);
        invitation.put("customerId", customerId);
        return invitation;
    }

    /** A form that says who to invite and nothing about who is doing the inviting. */
    static Map<String, Object> anInvitationNobodyIsSending(String contactDetails, String role) {
        Map<String, Object> invitation = new HashMap<>();
        invitation.put("contactDetails", contactDetails);
        invitation.put("role", role);
        invitation.put("customerId", null);
        return invitation;
    }
}
