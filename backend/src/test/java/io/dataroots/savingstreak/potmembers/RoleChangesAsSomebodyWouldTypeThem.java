package io.dataroots.savingstreak.potmembers;

import java.util.HashMap;
import java.util.Map;

/**
 * The bodies an owner's form would send when they change what somebody is to a shared pot, built
 * once so that a test about one field does not have to restate the other.
 *
 * <p>Static and free of any application, in the shape {@code PotsAsSomebodyWouldTypeThem} and
 * {@code InvitationsAsSomebodyWouldTypeThem} set: one copy of "a role change is a role and the owner
 * making it" keeps the tests that refuse one from drifting into disagreeing about what a role change
 * looks like on the wire.
 *
 * <p>{@code HashMap} rather than {@code Map.of}, so that a test about a missing field can send a
 * null, which {@code Map.of} will not hold. That is the whole reason this class exists at all: the
 * two ways a role change can arrive unmakeable are a role that is nothing and nobody making it.
 */
final class RoleChangesAsSomebodyWouldTypeThem {

    private RoleChangesAsSomebodyWouldTypeThem() {
    }

    /** The ordinary change: the role the member is to hold, and the owner making it. */
    static Map<String, Object> aRoleOf(String role, long customerId) {
        Map<String, Object> change = new HashMap<>();
        change.put("role", role);
        change.put("customerId", customerId);
        return change;
    }

    /** A form sent with the role box never filled in, which is not the same as filling in nothing. */
    static Map<String, Object> aChangeWithNoRoleAtAll(long customerId) {
        Map<String, Object> change = new HashMap<>();
        change.put("role", null);
        change.put("customerId", customerId);
        return change;
    }

    /** A form that says which role and nothing about who is making the change. */
    static Map<String, Object> aRoleChangeNobodyIsMaking(String role) {
        Map<String, Object> change = new HashMap<>();
        change.put("role", role);
        change.put("customerId", null);
        return change;
    }
}
