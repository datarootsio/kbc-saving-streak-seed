package io.dataroots.savingstreak.simulation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.accounts.ADeclaredBill;
import io.dataroots.savingstreak.accounts.DeclaredIncome;

/**
 * One of the everyday accounts a branch's saving would have to come out of: what is in it now, what
 * its holder says lands in it every month, and the bills standing against it.
 *
 * <p>Here because <strong>a rule can only move money that is there</strong>. A fold that fired the
 * saving rules against an imaginary current account would promise twelve transfers a customer's
 * salary could never cover, and the branches this feature exists for — another twenty-five a week,
 * and stopping for two months — are precisely the ones whose answer turns on whether the money was
 * available on the morning. So the three facts that decide it travel together: the balance the
 * branch starts the year with, the salary that tops it up, and the rent that takes it away again.
 *
 * <p>Every account the holder has, rather than only the ones this savings account's rules happen to
 * draw from. That is the reading {@code AutomationService.whatTheRulesWillDo} already settled for
 * the bills on its own bar, and the argument is the same one: the question is asked hardest by
 * somebody who has written no rule yet, and an answer that appeared only once a rule existed would
 * arrive after the decision it exists to inform.
 *
 * <p>The three figures are Accounts' own types and are not repacked. {@link DeclaredIncome} already
 * says whether anything was declared at all and which day it lands on; {@link ADeclaredBill} already
 * says which day it falls on, whether it is still standing and when it was last taken. Flattening
 * either into fields of this record would be a second statement of what a declaration is, in a
 * module that has no business having an opinion about one.
 *
 * <p>A declaration that was never made is an answer rather than an absence, exactly as it is on the
 * account's own screen: {@code income.isDeclared()} is false and the fold credits nothing on any
 * payday. "Nobody has said what lands here" and "nothing lands here" are the same arithmetic and
 * different sentences, and only Accounts gets to write either of them.
 *
 * <p><strong>The two cursors are here although neither declaration carries one, and that is the
 * correction this slice made.</strong> {@link DeclaredIncome} and {@link ADeclaredBill} each keep
 * their cursor out of themselves on purpose — which paydays and which due dates have been settled is
 * this application's own bookkeeping rather than anything about somebody's salary or their rent, and
 * a page that showed one would be showing a customer their database. A fold is not a page. It is
 * predicting the nightly run, and the run counts from exactly these two moments, so a fold without
 * them either credits a salary the run has already credited or skips one it still owes. Both put a
 * branch a morning out of step with the ledger it claims to be predicting, and one of them does it
 * in the customer's favour.
 *
 * <p>So they arrive here as their own narrow reads — {@code
 * AccountsService.howFarTheIncomeOnAnAccountIsPaid} and {@code
 * AccountsService.howFarEachBillOnAnAccountIsSettled} — rather than as fields bolted onto two
 * records that argue against carrying them. The shape is {@code DepositsService.mostEverSavedBy}'s:
 * one fact, asked for on its own, by the one caller whose arithmetic turns on it.
 *
 * <p>{@code incomePaidThrough} is null exactly when nothing has been declared, because nothing can
 * have been settled; {@code billsSettledThrough} holds one moment per standing bill, keyed by the
 * identifier {@link ADeclaredBill#billId} carries.
 */
public record ACurrentAccountBehindIt(long currentAccountId, BigDecimal balance,
                                      DeclaredIncome income, List<ADeclaredBill> bills,
                                      Instant incomePaidThrough,
                                      Map<Long, Instant> billsSettledThrough) {

    /** Copied on the way in, for the reason {@link TheStartingPoint} gives about its own lists. */
    public ACurrentAccountBehindIt {
        bills = List.copyOf(bills);
        billsSettledThrough = Map.copyOf(billsSettledThrough);
    }
}
