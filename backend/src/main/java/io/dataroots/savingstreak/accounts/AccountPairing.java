package io.dataroots.savingstreak.accounts;

/**
 * How a savings account and a current account stand to each other — the one thing anything moving
 * money between them needs to know before it does.
 *
 * <p>One answer rather than three questions. Asked separately, "does this exist", "does that exist"
 * and "is it the same customer" have to be asked in the right order to mean anything, and the module
 * would be handing callers a rule about how to use it instead of an answer.
 *
 * <p><strong>The last four are about a savings account no customer holds.</strong> Such an account
 * belongs to something other than a person — a shared pot — and whether money may move into it is
 * that thing's rule rather than this module's: Accounts asks
 * {@link WhoMayPayIntoAnAccountNobodyHolds} and reports what it is told. The word "pot" appears here
 * because whoever is refused has to be told what they ran into, and it is the whole of what this
 * module knows about one. Nothing here can name a pot, read its members or say what a role is.
 */
public enum AccountPairing {

    /** One customer holds both, which is the only pairing a personal deposit can move across. */
    HELD_BY_ONE_CUSTOMER,

    /**
     * No savings account answers to that identifier — or one does, and nothing this application
     * knows of holds it. The second is a record that has gone wrong rather than a state anybody can
     * reach: every account opened with no holder is opened for a pot, in the same transaction as the
     * pot. The two arrive as one answer because there is nothing different for a caller to do about
     * them, and an account nobody and nothing holds is not one money can move into either way.
     */
    NO_SUCH_SAVINGS_ACCOUNT,

    NO_SUCH_CURRENT_ACCOUNT,

    /** Both accounts are real and held by two different people. */
    HELD_BY_DIFFERENT_CUSTOMERS,

    /**
     * The savings account belongs to a shared pot, and whoever holds the current account is a member
     * of that pot with a role that may pay in. The pot variant of {@link #HELD_BY_ONE_CUSTOMER}, and
     * the second pairing money can move across.
     *
     * <p>Told apart from that one because the two do not name the same customer. A personal deposit
     * is the savings account holder's and the current account holder's at once; a contribution to a
     * pot has one person on it — the one whose current account the euros came out of — and that is
     * whose points, week and mark it is judged against. A caller that could not tell the two
     * pairings apart would have to credit the holder of an account nobody holds.
     */
    HELD_BY_A_POT_THE_PAYER_MAY_PAY_INTO,

    /**
     * The savings account belongs to a shared pot and whoever holds the current account is not in
     * it. A pot is not a public collection box: money goes in from its members and from nobody else.
     */
    HELD_BY_A_POT_THE_PAYER_DOES_NOT_BELONG_TO,

    /**
     * The savings account belongs to a shared pot, and whoever holds the current account is in it
     * with a role that only watches. Told apart from the case above because what the person does
     * next is different — one of them asks to be let in, the other asks to be allowed to pay — and a
     * refusal that could not say which would be a sentence neither of them could act on.
     */
    HELD_BY_A_POT_THE_PAYER_ONLY_WATCHES,

    /**
     * The savings account belongs to a shared pot that has been closed. Nobody may pay into it,
     * whatever they are to it and whether or not they are in it at all.
     *
     * <p>Answered ahead of the three above rather than after them, and that order is the whole of
     * the case: a closed pot has already returned every member's euros to them, so telling a member
     * they may pay in — or telling a stranger to ask for an invitation — would be sending somebody
     * off to do something that cannot be done. What is wrong is the pot rather than the person, and
     * only a pairing of its own can say so.
     */
    HELD_BY_A_POT_THAT_IS_CLOSED
}
