package io.dataroots.savingstreak.deposits;

/**
 * Which way money went across the boundary between an everyday account and savings.
 *
 * <p>Named from the savings account's point of view, because that is the account the ledger is about
 * and the one whose balance the movement changed. "In" and "out" on their own would be a pair of
 * words that read the opposite way depending on which end of the transfer the reader had in mind.
 *
 * <p>Two kinds rather than a signed amount. An amount of money in this application is always a
 * positive figure — {@link AmountOfMoney} refuses anything else — and a ledger that carried the
 * direction in the sign of the number would be the one place that stopped being true.
 */
public enum MoneyMovementDirection {

    /** A deposit: money left a current account and landed in savings, and earned points doing it. */
    INTO_SAVINGS,

    /** A withdrawal: money left savings and went back to a current account, earning nothing. */
    OUT_OF_SAVINGS
}
