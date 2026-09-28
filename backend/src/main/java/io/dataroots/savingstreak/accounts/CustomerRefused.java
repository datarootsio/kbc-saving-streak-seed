package io.dataroots.savingstreak.accounts;

/**
 * A customer this application will not open, carrying the reason in words the person who tried to
 * open one can act on — a person with no name, a person with no address to bank under, or an
 * address somebody already banks under.
 *
 * <p>Accounts' own refusal, because opening a customer is Accounts' rule: this module owns what a
 * customer is, and therefore owns what it means for one not to be openable. No other module has an
 * opinion about it — Gifting cares only that a recipient exists, and it asks this module.
 *
 * <p>Three kinds and no more. There is no check that the address looks like an email, no minimum
 * length on a name, no reserved names and no limit on how many people may be added — a stated
 * absence rather than an oversight. This is a training application whose participants type
 * deliberately odd things to see what happens, and the two rules that are here are the two that
 * protect something real: a customer with no address could never sign in, and a duplicate address
 * would make signing in ambiguous for both of them.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer.
 */
public class CustomerRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal can be about. They are different because the person reading it
     * has a different thing to do next: type a name, type an address, or use a different address.
     */
    public enum Kind {

        /** No name was given, and a person in a list of people to give points to needs one. */
        NO_NAME,

        /**
         * No contact details were given. Refused rather than allowed and left blank, because the
         * address is the one thing a customer signs in with and is how a gift names them: a
         * customer without one could be created and then never reached.
         */
        NO_CONTACT_DETAILS,

        /**
         * Somebody already banks under those contact details. Refused rather than allowed as a
         * second customer, because signing in and addressing a gift both find a customer <em>by</em>
         * that address and expect to find one at most — two customers sharing one would make both
         * of them unreachable rather than only the new one.
         */
        ALREADY_BANKS_HERE
    }

    private final Kind kind;

    CustomerRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
