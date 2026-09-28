package io.dataroots.savingstreak.automation;

/**
 * What happened when a saving rule fell due: the money moved, there was nothing above the floor to
 * move, there was not enough in the account to move what was asked for, or the savings account it
 * pays into had been closed.
 *
 * <p>Three values, and the last two are deliberately not one. A sweep on a balance at or under its
 * floor has moved nothing because the arithmetic said nothing, and reporting that as a failure would
 * be reporting arithmetic as an error — and would train a customer to ignore the thing that tells
 * them about real ones. A fixed amount that could not be honoured is the real one: the customer was
 * relying on a transfer and it did not happen.
 *
 * <p>Every occurrence carries one of them, including the ones where no money moved, which is the
 * half of the history a deposits ledger can never hold: a ledger records what happened, and "this
 * rule fell due and moved nothing" is a thing that happened with no row in it.
 *
 * <p>Public, because {@link RecordedOccurrence} carries it out of the module: whoever renders a
 * rule's history has to be able to say which of the three it is looking at.
 */
public enum OccurrenceOutcome {

    /** The money moved: there is a deposit, and the occurrence names it. */
    MOVED,

    /**
     * There was nothing to move. A sweep whose account was already at or under its floor, which is
     * an ordinary outcome rather than a failure of any kind.
     */
    NOTHING_TO_MOVE,

    /**
     * The account did not hold what the rule asked for, so nothing moved at all. A fixed amount is
     * all or nothing — a standing order never half-happens — and the occurrence is settled rather
     * than retried, because a transfer that fires on a day the customer did not choose is a worse
     * surprise than one that did not fire.
     */
    NOT_ENOUGH_MONEY,

    /**
     * The savings account the rule pays into has been closed, so there was nowhere for the money to
     * go and none of it left the current account.
     *
     * <p><strong>A fourth value rather than a fourth reading of {@link #NOT_ENOUGH_MONEY}</strong>,
     * and the customer is the reason. The two are the same thing to the ledger — a day that fell
     * due and moved nothing — and completely different things to the person reading the history:
     * one says put more money in the current account, and the other says this rule is pointing at
     * an account that no longer takes any, so change it or end it. A history that called this "not
     * enough money" would be sending somebody to top up an account that was never the problem, and
     * the shortfall beside it would have to be nought or a lie.
     *
     * <p><strong>It is a refusal and not an ordinary outcome</strong>, unlike
     * {@link #NOTHING_TO_MOVE}: the arithmetic had something to move and the destination would not
     * have it. The rule is settled rather than left due, for the reason a shortfall is settled —
     * money moving on a morning the customer did not choose is the one surprise this feature
     * promises never to spring — and here the account cannot reopen, so a day left due would be
     * retried every night for ever.
     */
    THE_ACCOUNT_IS_CLOSED
}
