package io.dataroots.savingstreak.accounts;

/**
 * How a savings account and a current account stand to each other — the one thing anything moving
 * money between them needs to know before it does.
 *
 * <p>One answer rather than three questions. Asked separately, "does this exist", "does that exist"
 * and "is it the same customer" have to be asked in the right order to mean anything, and the module
 * would be handing callers a rule about how to use it instead of an answer.
 */
public enum AccountPairing {

    /** One customer holds both, which is the only pairing money can move across. */
    HELD_BY_ONE_CUSTOMER,

    NO_SUCH_SAVINGS_ACCOUNT,

    NO_SUCH_CURRENT_ACCOUNT,

    /** Both accounts are real and held by two different people. */
    HELD_BY_DIFFERENT_CUSTOMERS
}
