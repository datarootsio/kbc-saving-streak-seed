package io.dataroots.savingstreak.deposits;

/**
 * Which way money went across the boundary between an everyday account and savings.
 *
 * <p>Named from the savings account's point of view, because that is the account the ledger is about
 * and the one whose balance the movement changed. "In" and "out" on their own would be a pair of
 * words that read the opposite way depending on which end of the transfer the reader had in mind.
 *
 * <p><strong>Interest is a third word rather than a deposit with a flag on it.</strong> It crosses
 * no boundary at all — nothing left a current account to pay it — so the one thing the other two
 * words both say about a movement is not true of it, and a page reading {@code INTO_SAVINGS} would
 * draw an arrow from an account that was never touched. It is also how a reader tells the euros the
 * customer moved from the euros the bank added without adding anything up: the ledger says which is
 * which, in the word every row is already read by.
 *
 * <p>Three kinds rather than a signed amount. An amount of money in this application is always a
 * positive figure — {@link AmountOfMoney} refuses anything else — and a ledger that carried the
 * direction in the sign of the number would be the one place that stopped being true.
 */
public enum MoneyMovementDirection {

    /** A deposit: money left a current account and landed in savings, and earned points doing it. */
    INTO_SAVINGS,

    /** A withdrawal: money left savings and went back to a current account, earning nothing. */
    OUT_OF_SAVINGS,

    /**
     * A month's interest: the bank added money to the savings account, out of nowhere the customer
     * holds, and it earned no points either.
     *
     * <p>Last in the enum rather than beside the deposit it resembles, because the order here is
     * also a tie-break — the ledger sorts by moment, then by direction, then by identifier — and
     * moving an existing value would reorder rows that have been read the same way for as long as
     * there have been two of them.
     */
    INTEREST_INTO_SAVINGS,

    /**
     * The price of breaking an agreement early: money left the savings account and none of it
     * reached the customer.
     *
     * <p>A fourth word rather than a withdrawal with a flag on it, and the argument is the one
     * interest already makes from the other side. What {@code OUT_OF_SAVINGS} says about a movement
     * — that the money went back to a current account — is exactly what is not true of a charge, so
     * a page reading that word would draw an arrow to an account that was never credited. It is
     * also how a customer tells the euros they took from the euros the bank kept, in the word every
     * row is already read by, without adding anything up.
     *
     * <p>Appended after {@link #INTEREST_INTO_SAVINGS} rather than filed beside the withdrawal it
     * resembles, for that value's own reason: the order here is a tie-break the ledger sorts by,
     * and moving an existing value would reorder rows that have read the same way for as long as
     * there have been two of them.
     */
    AN_EARLY_EXIT_CHARGE,

    /**
     * A move between two savings accounts the same customer holds: the euros left one and arrived
     * in the other, and no everyday account was touched at either end.
     *
     * <p><strong>One word for the whole move, which is the point of it.</strong> A move is written
     * into this module's ledgers as two rows — the euros leaving the source and the euros arriving
     * in the destination — because that is what a balance is summed from at each end. It is not
     * two events, and a customer reading back over what they have done with their money has done
     * one thing. So the ledger reports it once, under this word, with the account it left in
     * {@link MoneyMovement#savingsAccountId} and the account it reached in
     * {@link MoneyMovement#toSavingsAccountId} — the only kind of movement in this application
     * with a savings account at both ends, and the only one whose row names two of them.
     *
     * <p>{@code currentAccountId} is nothing at all on it, for the reason interest and a charge
     * carry the same null: no everyday account was debited or credited, so an identifier put there
     * to fill the column would have a page drawing an arrow to an account nothing reached.
     *
     * <p>Appended last rather than filed beside the deposit and the withdrawal it is made of, for
     * the reason the two words before it give: the order here is a tie-break the ledger sorts by,
     * and moving an existing value would reorder rows that have read the same way for as long as
     * there have been two of them.
     */
    BETWEEN_SAVINGS_ACCOUNTS
}
