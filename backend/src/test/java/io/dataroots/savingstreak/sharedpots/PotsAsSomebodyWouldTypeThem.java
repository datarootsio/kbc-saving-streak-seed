package io.dataroots.savingstreak.sharedpots;

import java.util.HashMap;
import java.util.Map;

/**
 * The bodies a customer's form would send when they open a shared pot, built once so that a test
 * about one field does not have to restate the other.
 *
 * <p>Static and free of any application, in the shape {@code RulesAsSomebodyWouldTypeThem} set: the
 * pots that are opened and read back and the pots that are refused are asked of different
 * applications, and one copy of "a pot is a name and the customer opening it" keeps those from
 * drifting into disagreeing about what a pot looks like on the wire.
 *
 * <p>{@code HashMap} rather than {@code Map.of}, so that a test about a missing field can send a
 * null, which {@code Map.of} will not hold. That is the whole reason this class exists at all: the
 * three ways a pot can arrive unopenable are a name that is nothing, a name that was never sent, and
 * nobody to open it.
 */
final class PotsAsSomebodyWouldTypeThem {

    private PotsAsSomebodyWouldTypeThem() {
    }

    /** The ordinary pot this feature is for: a name somebody chose, and the customer opening it. */
    static Map<String, Object> aPotCalled(String name, long customerId) {
        Map<String, Object> pot = new HashMap<>();
        pot.put("name", name);
        pot.put("customerId", customerId);
        return pot;
    }

    /** A form sent with the name box never filled in, which is not the same as filling in nothing. */
    static Map<String, Object> aPotWithNoNameAtAll(long customerId) {
        Map<String, Object> pot = new HashMap<>();
        pot.put("name", null);
        pot.put("customerId", customerId);
        return pot;
    }

    /** A form that says what to call the pot and nothing about who is opening it. */
    static Map<String, Object> aPotNobodyIsOpening(String name) {
        Map<String, Object> pot = new HashMap<>();
        pot.put("name", name);
        pot.put("customerId", null);
        return pot;
    }
}
