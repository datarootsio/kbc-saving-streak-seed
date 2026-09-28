package io.dataroots.savingstreak.deposits;

/**
 * Where the money in a savings ledger row came from: the customer moved it, or the bank added it.
 *
 * <p><strong>The one column that makes interest a row in this ledger rather than a table of its
 * own.</strong> An interest payment is money in a savings account, so it has to be a row of the
 * thing that says what a savings account holds — otherwise the balance is a sum of two tables, a
 * withdrawal has a second kind of row to learn how to draw down, and next month's interest is
 * worked out on a figure that has to be assembled from both. Writing it here buys all three for
 * nothing: the balance is still the sum of what the rows still hold, a withdrawal still draws rows
 * down and still writes an allocation per row it touched, and interest sits in the balance so that
 * it is in the next period's average and compounds without anybody arranging it.
 *
 * <p><strong>It costs the three rules the spec names, and every one of them is a query in
 * {@link DepositRepository} rather than a promise.</strong> Money the bank added is not money the
 * customer put away: it does not raise the most they have ever saved, it does not secure a week,
 * and it pays no loyalty anniversary. Each of those is a figure summed or walked over this ledger,
 * so each of them asks for {@link #CUSTOMER} rows and says why.
 *
 * <p><strong>Two values and not a boolean.</strong> {@code paidByTheBank} would read as a fact
 * about one row; this reads as which of two kinds a row is, which is what the queries switch on and
 * what a third kind — a charge for breaking a term early, in a later slice — would join rather than
 * negate.
 *
 * <p>Package-private, like the entity it is a column of. Nothing outside this module names an
 * origin: what leaves is a movement with a direction on it, and the rest of the application reads
 * that word.
 */
enum DepositOrigin {

    /**
     * The customer moved it out of a current account of their own.
     *
     * <p>Every row this ledger has ever held until interest existed, which is why
     * {@link DepositsOnStartUp} writes it onto the rows that say nothing: a deposit recorded before
     * the column existed was money somebody paid in, and there was no other kind.
     */
    CUSTOMER,

    /**
     * The bank added it, as a month's interest on what the account held.
     *
     * <p>It has no current account at either end — nothing was debited to pay it — which is why
     * {@link Deposit#getSourceCurrentAccountId} answers nothing at all for a row of this kind, and
     * why the money-movement ledger reports it as its own direction rather than as a transfer from
     * somewhere. This value is what that reading is decided by, so a column that cannot hold an
     * absence does not have to.
     */
    INTEREST,

    /**
     * It arrived from another savings account of the same customer's, as one half of a move.
     *
     * <p><strong>A third kind rather than a deposit with a flag on it, and the reason is the three
     * rules the kinds are asked about.</strong> A move is not new saving for the week — the euros
     * were already saved, in the account next door — so the week's two queries have to be blind to
     * it, exactly as they are blind to interest; and it is emphatically the customer's own money,
     * so the mark, what they still hold and the anniversaries have to count it, exactly as they do
     * not count interest. No single existing value answers both halves of that, which is what makes
     * this a kind rather than a column beside {@link #CUSTOMER}.
     *
     * <p><strong>It has no current account at either end</strong>, like interest and for a
     * neighbouring reason: nothing was debited to pay it, because the euros came out of a savings
     * account. {@link Deposit#getSourceCurrentAccountId} answers nothing at all for a row of this
     * kind, and {@link Deposit#getMovedFromSavingsAccountId} answers the account that actually
     * paid it — which is what lets the money-movement ledger report the whole move as one row
     * rather than as two halves a reader has to pair up by eye.
     *
     * <p><strong>The euros arrive already spoken for, which is the point of the move.</strong> The
     * row carries the earned-on figure the source rows gave up, so the customer's mark does not
     * move and the arriving euros earn nothing they have already earned. That is written on the
     * row at the moment it is made, the way interest writes its nought there, and it is the whole
     * of why a move costs no points and pays none.
     */
    MOVED_FROM_ANOTHER_SAVINGS_ACCOUNT
}
