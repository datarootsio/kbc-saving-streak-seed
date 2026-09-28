package io.dataroots.savingstreak.accounts;

/**
 * What happened when a recurring bill fell due: the money left the account, or there was not enough
 * in it and nothing left at all.
 *
 * <p>Two values rather than three, which is where this parts company with
 * {@code OccurrenceOutcome}. A saving rule has a third — "there was nothing above the floor to move"
 * — because a sweep's figure is derived from the balance and can honestly come out at nothing. A
 * bill's figure is a number its holder typed: it is either taken or it is not, and there is no
 * arithmetic in between to report.
 *
 * <p><strong>{@link #UNPAID} is not a partial payment and never will be.</strong> A bill of nine
 * hundred against a balance of six hundred takes nothing and leaves six hundred. A half-paid rent is
 * neither a lesson nor a thing that happens, and the existing current-account withdrawal is
 * all-or-nothing for exactly that reason.
 *
 * <p>Every due date carries one of them, including the ones where no money moved, which is the half
 * of the history a money-movements ledger can never hold on its own: a bill that met an empty
 * account is as much a part of the record as one that took nine hundred euros.
 *
 * <p>Public, because {@link ABillThatFellDue} carries it out of the module: whoever draws a bill's
 * history has to be able to say which of the two it is looking at.
 */
public enum BillOutcome {

    /** The money left the current account, all of it, on the run that settled this due date. */
    PAID,

    /**
     * The account did not hold what the bill asked for, so nothing left at all and the balance
     * stayed exactly where it was.
     *
     * <p>Recorded rather than retried on the spot. What becomes of a bill that stayed owed is the
     * next slice's question; what this value promises is that the date was presented, that nothing
     * was taken, and that nobody has to infer either from a balance that did not move.
     */
    UNPAID
}
