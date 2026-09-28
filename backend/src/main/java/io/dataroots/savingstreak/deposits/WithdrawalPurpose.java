package io.dataroots.savingstreak.deposits;

/**
 * Why money left a savings account: the customer took it, or the bank charged for something they
 * agreed to when they opened the account.
 *
 * <p><strong>The mirror of {@link DepositOrigin}, and it exists for the same reason.</strong> That
 * column is what made a month's interest a row of the savings ledger rather than a table of its
 * own; this one is what makes the charge for breaking a fixed term early a row of the same ledger
 * rather than a balance quietly adjusted by somebody. The whole of the argument carries over: the
 * balance is still the sum of what the rows still hold, the charge draws those rows down and writes
 * an allocation per row it touches, and the money-movement ledger reports it without anything being
 * added up twice. A balance that fell by ninety days of interest and had no row to point at would
 * be the one figure in this application nobody could explain, and the ticket that asks for this
 * charge says so in as many words.
 *
 * <p><strong>Two values and not a boolean, for {@link DepositOrigin}'s reason.</strong>
 * {@code chargedByTheBank} would read as a fact about one row; this reads as which of two kinds a
 * row is, which is what the queries switch on and what a third kind — a fee somebody invents later
 * — would join rather than negate.
 *
 * <p><strong>It is what the week's arithmetic leaves out.</strong> A run of weeks counts what the
 * customer moved: money they put away less money they took back. A charge is neither. The spec
 * already holds this line from the other side — interest is not new saving for the week because the
 * bank added it — and a charge that broke somebody's streak would be the same mistake with the sign
 * reversed, and a crueller one, because the penalty they agreed to was ninety days of interest and
 * not ninety days of interest plus a streak. {@link WithdrawalRepository#takenOutBetween} and its
 * neighbour are where that is kept true, by asking for the customer's own rows.
 *
 * <p>Package-private, like the entity it is a column of. Nothing outside this module names a
 * purpose: what leaves is a movement with a direction on it, and the rest of the application reads
 * that word.
 */
enum WithdrawalPurpose {

    /**
     * The customer took it back out, into a current account of their own.
     *
     * <p>Every row this ledger has ever held until an early-exit charge existed, which is why
     * {@link WithdrawalsOnStartUp} writes it onto the rows that say nothing: a withdrawal recorded
     * before the column existed was money somebody took out, and there was no other kind.
     */
    CUSTOMER,

    /**
     * The bank charged it, as the stated price of breaking a fixed term before it matured.
     *
     * <p>It has no current account at the other end — the euros do not go back to the customer,
     * which is the whole of what a charge is — which is why
     * {@link Withdrawal#getDestinationCurrentAccountId} answers nothing at all for a row of this
     * kind, and why the money-movement ledger reports it as its own direction rather than as a
     * transfer to somewhere. This value is what that reading is decided by, so a column that cannot
     * hold an absence does not have to.
     */
    AN_EARLY_EXIT_CHARGE,

    /**
     * The customer moved it to another savings account of their own, in one operation rather than
     * in two presses.
     *
     * <p><strong>A third kind rather than an ordinary withdrawal, for the reason the week's
     * arithmetic gives above.</strong> A run of weeks counts what the customer put away less what
     * they took back out, and a move is neither: the euros never left savings. Recorded as a
     * customer's withdrawal it would net the week to nothing, so somebody moving five thousand
     * euros to a better product would lose a week they had already secured — which is exactly the
     * punishment the move exists to lift. {@link WithdrawalRepository#takenOutBetween} and its
     * neighbour ask for the customer's own rows, so this row is blind to them without either query
     * learning what a move is.
     *
     * <p><strong>It has no current account at the other end</strong>, like the charge above it:
     * the euros did not go back to the customer's everyday money, they went into another savings
     * account. {@link Withdrawal#getDestinationCurrentAccountId} answers nothing at all for a row
     * of this kind and {@link Withdrawal#getDestinationSavingsAccountId} answers the account that
     * received it, which is what lets the money-movement ledger report the whole move as one row
     * rather than as two halves a reader has to pair up by eye.
     *
     * <p>It is left out of the account's own list of what its holder took back out, for the reason
     * the charge is: that list answers "what have I done with this money", and the honest answer
     * about a move is one entry in the record of money that moved rather than a withdrawal here
     * and a deposit next door.
     */
    A_MOVE_TO_ANOTHER_SAVINGS_ACCOUNT
}
