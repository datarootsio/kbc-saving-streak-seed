package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.dataroots.savingstreak.accounts.AccountPairing;
import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.goals.GoalsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.deposits.AmountOfMoney.asMoney;

/**
 * Records money leaving savings, including the oldest deposits it reduced to make that possible.
 *
 * <p>What it may take is the part of the balance no savings goal has claimed. It asks Goals for that
 * figure, handing over the balance it has already summed, and refuses beyond it rather than reducing
 * a goal on the customer's behalf.
 *
 * <p>And what the agreement the account was opened under says about the money leaving at all, which
 * it asks {@link WhatAnAgreementSaysAboutMoneyLeaving} — an interface this module declares and
 * something else answers, so that nothing here learns what a notice period, a maturity date or a
 * floor is. The answer is one condition and one sentence, and this module carries both to whoever
 * asked without reading either.
 */
@Service
public class WithdrawalsService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalsService.class);

    /**
     * What the gates below call the thing they are refusing, when what they are refusing is a
     * customer taking their money back out.
     *
     * <p>A word rather than a boolean, and it reaches the log rather than the sentence: three of
     * the gates here are shared with a move between two of the customer's own savings accounts,
     * and a move refused for a notice period that had not run must be refused in <em>exactly</em>
     * the withdrawal's words — that is the whole rule — while a reviewer grepping a night's log
     * still has to be able to tell which of the two somebody actually pressed. So the sentence is
     * one and the line naming it is two.
     */
    private static final String A_WITHDRAWAL = "withdrawal";

    /** The same, for the operation that moves money from one savings account to another. */
    private static final String A_MOVE = "move between savings accounts";

    private final DepositRepository deposits;
    private final WithdrawalRepository withdrawals;
    private final WithdrawalAllocationRepository allocations;
    private final AccountsService accounts;

    /**
     * Asked what part of the balance the goals have spoken for, and asked nothing else.
     *
     * <p>This direction is the one the two modules can be wired in. Goals reads no module at all: it
     * is handed a balance rather than fetching one, so Deposits can ask it a question here without
     * the application context finding a cycle to refuse to start on.
     */
    private final GoalsService goals;

    /**
     * Asked what the account's own agreement says about money leaving, and told when it has.
     *
     * <p>Injected as the interface and never as whatever implements it, which is the whole of what
     * keeps this module ignorant that a savings product exists. The direction is forced: Products
     * reads this ledger to find out when an account was first paid into, so a service here holding
     * a {@code ProductsService} would be half of a cycle the application context would refuse to
     * start on.
     */
    private final WhatAnAgreementSaysAboutMoneyLeaving agreement;
    private final Clock clock;

    WithdrawalsService(DepositRepository deposits, WithdrawalRepository withdrawals,
                       WithdrawalAllocationRepository allocations, AccountsService accounts,
                       GoalsService goals, WhatAnAgreementSaysAboutMoneyLeaving agreement,
                       Clock clock) {
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.allocations = allocations;
        this.accounts = accounts;
        this.goals = goals;
        this.agreement = agreement;
        this.clock = clock;
    }

    @Transactional
    public RecordedWithdrawal withdraw(long savingsAccountId, long toCurrentAccountId, BigDecimal amount) {
        log.debug("withdrawal requested savingsAccountId={} toCurrentAccountId={} amount={}",
                savingsAccountId, toCurrentAccountId, amount);
        // The accounts before the amount, in the order a deposit asks the same two questions: a
        // withdrawal is a movement between two of them, and if there is no such movement to make,
        // the amount is beside the point.
        refuseUnlessOneCustomersOwnAccounts(savingsAccountId, toCurrentAccountId);
        refuseUnlessAnAmountOfMoney(savingsAccountId, toCurrentAccountId, amount);

        // Everything the account holds, in the order the money actually leaves it: the interest
        // the bank paid first, and then the customer's own deposits oldest first. The order is the
        // repository's rule rather than this method's, and it is argued where it is written.
        List<Deposit> inTheOrderItLeaves =
                deposits.whatItHoldsInTheOrderAWithdrawalTakesIt(savingsAccountId);
        BigDecimal balance = whatIsLeftIn(inTheOrderItLeaves);
        refuseUnlessItStillHoldsThatMuch(A_WITHDRAWAL, savingsAccountId, toCurrentAccountId, amount, balance);
        refuseUnlessTheAgreementLetsItGo(A_WITHDRAWAL, savingsAccountId, toCurrentAccountId, amount);
        refuseUnlessNoGoalHasSpokenForIt(A_WITHDRAWAL, savingsAccountId, toCurrentAccountId, amount, balance);

        RecordedWithdrawal made = drawTheOldestDepositsDownFirst(savingsAccountId,
                toCurrentAccountId, amount, inTheOrderItLeaves);
        // Told after the money has moved and inside the same transaction, so that whatever the
        // withdrawal ran on is spent exactly when the withdrawal is written and never when it is
        // only contemplated. A withdrawal that rolls back spends no notice, because there is
        // nothing left of either.
        agreement.moneyHasLeft(savingsAccountId, amount);
        return made;
    }

    /**
     * Moves money out of a shared pot's savings account, which the pot's members have already
     * approved of.
     *
     * <p><strong>The other door, and the only other one.</strong> {@link #withdraw} refuses a pot's
     * savings account outright, whoever is asking, in a sentence promising that a pot's money leaves
     * it by proposal — and this is where that promise is kept. It is not a hole in that refusal: the
     * decision this method acts on has already been made, by every member whose euros are at stake,
     * and Shared Pots is the only caller because Shared Pots is the only module that can know it.
     *
     * <p><strong>It judges nothing about who may take money out, because that judgement has
     * happened.</strong> Whether the account belongs to a pot, whether the caller is a member,
     * whether the destination is the proposer's own current account and whose approval was needed
     * are all rules {@code SharedPotsService} has already applied — several of them to a proposal
     * that has been waiting for days — and asking them a second time here would mean this module
     * knowing what a pot is. What it does keep is the arithmetic: an amount that is an amount, and
     * an account that holds it.
     *
     * <p><strong>What a goal has claimed is deliberately not asked either</strong>, and it is the
     * one gate a reader will miss. On a personal account that gate exists to stop the application
     * quietly reallocating the customer's own money behind their back; on a pot there is nobody to
     * do that to, and the sentence it would answer with — "free money from a goal first" — would go
     * to the member who clicked approve, who may be a contributor with no business touching what the
     * group is saving for. The two gates the spec puts in front of a pot's money are the members'
     * assent and what the pot holds, and they are both applied before this is called.
     *
     * <p>The movement itself is the same movement in every particular: the oldest deposits go first
     * whoever paid them in, which is the whole reason the approval gate exists, an allocation records
     * which deposit each euro came out of, and the destination current account is credited. One
     * recording of a withdrawal means one way of reading a pot's history and a personal account's.
     *
     * @throws WithdrawalRefused if the figure is not an amount of money, or the account no longer
     *                           holds it — the second being a backstop rather than the sentence
     *                           anybody reads, since Shared Pots weighs the same figure against the
     *                           same deposits first and refuses in words about the pot
     */
    @Transactional
    public RecordedWithdrawal takeOutOfAPotWhatItsMembersApprovedOf(long savingsAccountId,
                                                                    long toCurrentAccountId,
                                                                    BigDecimal amount) {
        log.debug("approved pot withdrawal requested savingsAccountId={} toCurrentAccountId={} "
                + "amount={}", savingsAccountId, toCurrentAccountId, amount);
        refuseUnlessAnAmountOfMoney(savingsAccountId, toCurrentAccountId, amount);
        List<Deposit> inTheOrderItLeaves =
                deposits.whatItHoldsInTheOrderAWithdrawalTakesIt(savingsAccountId);
        refuseUnlessItStillHoldsThatMuch(A_WITHDRAWAL, savingsAccountId, toCurrentAccountId, amount,
                whatIsLeftIn(inTheOrderItLeaves));
        return drawTheOldestDepositsDownFirst(savingsAccountId, toCurrentAccountId, amount,
                inTheOrderItLeaves);
    }

    /**
     * Returns one member of a shared pot the euros that are still their own, out of the deposits
     * they paid in themselves and out of nobody else's.
     *
     * <p><strong>The third door, and the one that needs nobody's permission.</strong>
     * {@link #takeOutOfAPotWhatItsMembersApprovedOf} is how a pot's money leaves when it is somebody
     * else's as well; this is how it leaves when it is not. A settlement walks only the deposits the
     * departing member paid into that account, so the euros it moves are exactly the euros they put
     * there and have not already taken back — which is why it is allowed to happen without anybody
     * approving it, and why no other member's remaining money changes by a cent.
     *
     * <p><strong>That is the approval gate seen from the other side rather than a hole in it.</strong>
     * The gate exists because an ordinary withdrawal draws the account's oldest deposits down first
     * whoever paid them in, so it spends other members' money and costs them the points on their
     * next contributions. A settlement cannot do either: hand it only one member's deposits and the
     * worst it can reach is that member's own. You can always take back your own, and never somebody
     * else's — one rule, applied here and at the gate.
     *
     * <p>Which deposits the member's own money comes out of is still oldest first, because it is the
     * same rule about the same account and there is no reason for a settlement to draw a person's
     * euros down in a different order from everything else that touches them. The walk, the
     * allocation per deposit and the credit to the current account are the movement every other
     * withdrawal makes, so a pot's history and a personal account's go on reading the same way.
     *
     * <p><strong>It judges nothing about who may leave a pot</strong>, in the same way the approved
     * route judges nothing about who may take money out: whether the account belongs to a pot,
     * whether this customer is in it, whether the pot would be left without an owner and whether the
     * destination is their own current account are all rules {@code SharedPotsService} has applied
     * before this is called. What is kept here is the arithmetic — an amount that is an amount, and
     * deposits of theirs that still hold it.
     *
     * @param memberCustomerId whose deposits may be drawn down, which is the whole of what makes
     *                         this safe to do without asking anybody
     * @param amount           what is still theirs, worked out by the caller from the same deposits
     *                         and handed in, because what a member is owed is a question about a pot
     * @throws WithdrawalRefused if the figure is not an amount of money, or that member's own
     *                           deposits no longer hold it — the second being a backstop rather than
     *                           a sentence anybody reads, since Shared Pots sums the same deposits
     *                           first and would have settled whatever it found
     */
    @Transactional
    public RecordedWithdrawal returnToAPotsMemberWhatIsStillTheirOwn(long savingsAccountId,
                                                                     long toCurrentAccountId,
                                                                     long memberCustomerId,
                                                                     BigDecimal amount) {
        log.debug("pot settlement requested savingsAccountId={} toCurrentAccountId={} "
                        + "memberCustomerId={} amount={}",
                savingsAccountId, toCurrentAccountId, memberCustomerId, amount);
        refuseUnlessAnAmountOfMoney(savingsAccountId, toCurrentAccountId, amount);
        // Theirs, filtered out of the account's deposits in the order the draw-down wants them,
        // rather than asked for in a query of their own: one ordering of an account's deposits means
        // one answer to "which euros go first", and a settlement is that answer asked of a shorter
        // list.
        List<Deposit> theirsOldestFirst =
                deposits.madeIntoOldestFirst(savingsAccountId).stream()
                        .filter(deposit -> deposit.getCustomerId() == memberCustomerId)
                        .toList();
        log.debug("pot settlement weighed against that member's own deposits savingsAccountId={} "
                        + "memberCustomerId={} amount={} theirDeposits={} stillTheirs={}",
                savingsAccountId, memberCustomerId, asMoney(amount), theirsOldestFirst.size(),
                asMoney(whatIsLeftIn(theirsOldestFirst)));
        refuseUnlessItStillHoldsThatMuch(A_WITHDRAWAL, savingsAccountId, toCurrentAccountId, amount,
                whatIsLeftIn(theirsOldestFirst));
        return drawTheOldestDepositsDownFirst(savingsAccountId, toCurrentAccountId, amount,
                theirsOldestFirst);
    }

    /**
     * Takes the stated price of breaking an agreement early out of the account that agreed to it,
     * and answers which row it became.
     *
     * <p><strong>A row of this ledger rather than an adjustment to a balance, and that is the
     * decision the whole of the early-exit charge rests on.</strong> The account's balance is the
     * sum of what its deposit rows still hold, so a charge that did not draw those rows down would
     * not change the balance at all, and a charge that changed the balance some other way would be
     * the one figure in this application nobody could point at a row to explain. Written here it
     * draws the rows down exactly as a withdrawal does, writes an allocation per row it touches,
     * and appears in the record of money that moved. {@link WithdrawalPurpose} is the column that
     * makes it possible and carries the argument in full.
     *
     * <p><strong>Nothing reaches a current account.</strong> Every other way money leaves here is a
     * transfer with two ends and Accounts is credited at the far one; a charge has one end, and
     * crediting the customer with the penalty they are paying would be this application handing
     * back the money it just charged for.
     *
     * <p><strong>It judges nothing, because the judging has happened.</strong> Whether this account
     * is on a term, whether that term has matured and what the price of breaking it is are all the
     * Products module's rules, applied before this is called — and the amount arrives already
     * worked out, because this module has no opinion about rates. What is kept here is the
     * arithmetic: the rows are drawn down in the order money leaves this account, and the walk
     * stops when the amount is met.
     *
     * <p><strong>It is not weighed against the balance, and that is deliberate.</strong> The charge
     * is a fraction of a year's interest on what the account holds, so it is smaller than the
     * balance it was worked out from by construction; and an account that somehow could not cover
     * it should have the rows it does have drawn to nothing rather than the whole break rolled back
     * over a cent. The draw-down takes what it is given and stops, which is exactly that reading.
     *
     * <p>Public because it is this module's door for the Products module, which owns what breaking
     * an agreement costs and knows nothing about how a savings ledger is written — the same shape,
     * and the same reason, as {@link DepositsService#payInterestInto} on the way in. It takes the
     * moment rather than reading the clock for that method's reason too: the break is one event,
     * and the charge is dated at the moment the break was.
     *
     * @param amount what breaking costs, already worked out and floored to the cent by whoever
     *               priced it
     * @return the identifier of the ledger row the charge became, so that whoever broke the term can
     *         name the movement it produced
     */
    @Transactional
    public long chargeAnEarlyExitFrom(long savingsAccountId, BigDecimal amount, Instant chargedAt) {
        log.debug("early exit charge requested savingsAccountId={} amount={} chargedAt={}",
                savingsAccountId, asMoney(amount), chargedAt);
        Withdrawal charge = withdrawals.save(
                Withdrawal.anEarlyExitCharge(savingsAccountId, amount, chargedAt));
        drawTheRowsDown(charge, amount,
                deposits.whatItHoldsInTheOrderAWithdrawalTakesIt(savingsAccountId));
        // One line per charge, with everything that makes it a row of this ledger and not a
        // withdrawal: what it took, and the fact that no current account was credited by it. A
        // reviewer grepping this module for where a balance fell without anybody withdrawing finds
        // exactly this.
        log.info("early exit charge taken out of savings withdrawalId={} savingsAccountId={} "
                        + "amount={} purpose={} toCurrentAccountId=none chargedAt={}",
                charge.getId(), savingsAccountId, asMoney(amount), charge.getPurpose(), chargedAt);
        return charge.getId();
    }

    /**
     * Refuses a move out of this savings account for every reason a withdrawal of the same amount
     * would be refused, and answers with the rows the move would come out of if nothing did.
     *
     * <p><strong>The same three gates, in the same order, with the same sentences — and that is the
     * rule rather than a convenience.</strong> Moving money to another of your own accounts is not
     * a way round a notice period, a fixed term or a floor, and the only way to be sure of that is
     * for the three questions to be asked by the same lines of code that ask them of a withdrawal.
     * Copied into a move, the copy would be the one that fell behind the day a fourth condition
     * arrived, and the sentence a customer met would depend on which button they had pressed.
     *
     * <p><strong>What it does not ask is who the money is going to</strong>, which is the one gate
     * a withdrawal has that a move cannot reuse: a withdrawal's far end is a current account and a
     * move's is a savings account, so the pairing is a different question with different sentences
     * and it is settled by {@link MovesService} before this is called.
     *
     * <p>It answers with the rows rather than with nothing, so that the walk that draws them down
     * is weighed against the same reading it takes from. Two reads a moment apart is how an account
     * comes to be refused against one balance and drawn down against another.
     *
     * <p>Package-private, and called from exactly two places in this package: the move itself, and
     * the reading that quotes what a move would cost before anybody presses anything. Both have to
     * refuse identically, which is why both go through this.
     *
     * @throws WithdrawalRefused with the agreement's own sentence untouched
     */
    List<Deposit> refuseUnlessThatMuchMayLeaveForAMove(long fromSavingsAccountId,
                                                       long toSavingsAccountId,
                                                       BigDecimal amount) {
        List<Deposit> inTheOrderItLeaves =
                deposits.whatItHoldsInTheOrderAWithdrawalTakesIt(fromSavingsAccountId);
        BigDecimal balance = whatIsLeftIn(inTheOrderItLeaves);
        refuseUnlessItStillHoldsThatMuch(A_MOVE, fromSavingsAccountId, toSavingsAccountId, amount,
                balance);
        refuseUnlessTheAgreementLetsItGo(A_MOVE, fromSavingsAccountId, toSavingsAccountId, amount);
        refuseUnlessNoGoalHasSpokenForIt(A_MOVE, fromSavingsAccountId, toSavingsAccountId, amount,
                balance);
        return inTheOrderItLeaves;
    }

    /**
     * Takes money out of one savings account because it is on its way to another of the same
     * customer's, and answers what the rows it emptied gave up.
     *
     * <p><strong>Both halves of {@link WhatAnAgreementSaysAboutMoneyLeaving} are used here, and
     * that is the single most load-bearing line in the whole move.</strong> The agreement is asked
     * before a cent moves, through the shared gate above, and told afterwards, inside this same
     * transaction — because some conditions are <em>spent</em> by being met: a withdrawal that ran
     * on notice consumes that notice, oldest first. A move that asked and never told would leave
     * the notice standing, and moving money between your own accounts would become the documented
     * way to withdraw an unlimited amount on one month's notice. Asking without telling is not a
     * missing feature here; it is the hole the two-method interface exists to close.
     *
     * <p><strong>No current account is credited</strong>, which is the whole of what makes this
     * not a withdrawal. The euros do not reach the customer's everyday money at all: they land in
     * another savings account, as a row written on the other side of the move and inside this same
     * transaction, so a move that fails to be written anywhere moves nothing anywhere.
     *
     * <p><strong>It carries the earned-on figure of the rows it emptied</strong>, worked out from
     * the allocations the draw-down actually wrote rather than from a second walk of its own. Two
     * kinds of euro leave already spoken for: the ones the row they were in had earned on, which
     * have been paid for once and must not be paid for again; and every euro of a month's interest,
     * which the bank added and which has never earned a point in this application. Both arrive on
     * the other side marked as earned on, so the customer's high-water mark is exactly what it was
     * before the move and the arriving euros earn nothing.
     *
     * <p>It takes the moment rather than reading the clock, for the reason the early-exit charge
     * does: a move is one event with two rows, and a second reading of the clock would date the
     * two halves of it a millisecond apart.
     *
     * <p>Package-private, because {@link MovesService} is the only thing entitled to call it — a
     * half of a move with nothing on the other side of it would be money that left an account and
     * arrived nowhere.
     *
     * @return which row the money left as, and what that money arrives already spoken for
     */
    AMoveOutOfSavings takeOutForAMoveTo(long fromSavingsAccountId, long toSavingsAccountId,
                                        BigDecimal amount, Instant movedAt) {
        log.debug("move out of savings requested fromSavingsAccountId={} toSavingsAccountId={} "
                + "amount={} movedAt={}", fromSavingsAccountId, toSavingsAccountId, asMoney(amount),
                movedAt);
        List<Deposit> inTheOrderItLeaves =
                refuseUnlessThatMuchMayLeaveForAMove(fromSavingsAccountId, toSavingsAccountId, amount);
        Withdrawal moved = withdrawals.save(Withdrawal.movedToAnotherSavingsAccount(
                fromSavingsAccountId, toSavingsAccountId, amount, movedAt));
        List<WithdrawalAllocation> allocated =
                drawTheRowsDown(moved, amount, inTheOrderItLeaves);
        BigDecimal carried = carryWhatTheseRowsWereAlreadyPaidFor(inTheOrderItLeaves, allocated);
        // Told after the money has left and inside the same transaction, exactly as a withdrawal
        // tells it. This is the call that stops a move being a way round a notice period, and it
        // is deliberately the last thing that happens on this side of the move.
        agreement.moneyHasLeft(fromSavingsAccountId, amount);
        log.info("money moved out of savings for a move withdrawalId={} fromSavingsAccountId={} "
                        + "toSavingsAccountId={} amount={} purpose={} depositsTouched={} "
                        + "earnedOnCarriedAcross={} movedAt={}",
                moved.getId(), fromSavingsAccountId, toSavingsAccountId, asMoney(amount),
                moved.getPurpose(), allocated.size(), asMoney(carried), movedAt);
        return new AMoveOutOfSavings(moved.getId(), carried);
    }

    /**
     * Takes the earned-on figure off the rows a move emptied and answers what came away with it.
     *
     * <p><strong>Two kinds of euro leave already paid for, and the arithmetic says both in one
     * line.</strong> A row the customer paid in has an earned-on figure — the part of it that took
     * them above the most they had ever saved — and the euros a move takes out of it carry that
     * figure with them, up to what the row was judged on. A row the bank wrote has an earned-on
     * figure of nought and never earned anybody anything, so every euro of it carries away its own
     * whole self instead: counted any other way, a customer could move a year of interest into
     * another account and have it arrive looking like money they had saved and never been paid for,
     * and the next deposit they made would earn points on euros the bank had given them.
     *
     * <p><strong>The row it comes off is reduced by exactly what the row it lands in is given.</strong>
     * That is what makes a move invisible to the high-water mark: the mark is the sum of what a
     * customer's rows have earned on, and this moves a figure between two rows of that sum without
     * changing it. A withdrawal deliberately does the opposite and leaves the figure where it is,
     * because the euros have left savings altogether and the mark must not fall — the two rules
     * agree, because both are saying "a euro that has been paid for is paid for once".
     *
     * <p>Walked over the allocations the draw-down wrote rather than over the rows again, so the
     * euros this carries are by construction the euros that actually moved.
     */
    private BigDecimal carryWhatTheseRowsWereAlreadyPaidFor(List<Deposit> inTheOrderTheyLeave,
                                                            List<WithdrawalAllocation> allocated) {
        Map<Long, Deposit> byId = new HashMap<>();
        for (Deposit deposit : inTheOrderTheyLeave) {
            byId.put(deposit.getId(), deposit);
        }
        BigDecimal carried = BigDecimal.ZERO;
        for (WithdrawalAllocation allocation : allocated) {
            Deposit row = byId.get(allocation.getDepositId());
            BigDecimal alreadyPaidFor =
                    row.getOrigin() == DepositOrigin.INTEREST
                            ? allocation.getAmount()
                            : row.getEarnedOnAmount().min(allocation.getAmount());
            // The bank's own rows carry no earned-on figure to give up — they were written with a
            // nought — so only the customer's rows are reduced. Both kinds still hand their euros
            // over as already paid for, which is the line above.
            if (row.getOrigin() != DepositOrigin.INTEREST) {
                row.carryEarnedOnAway(alreadyPaidFor);
            }
            carried = carried.add(alreadyPaidFor);
            log.debug("a move carried what a row was already paid for depositId={} origin={} "
                            + "took={} alreadyPaidFor={} stillJudgedOn={}",
                    row.getId(), row.getOrigin(), asMoney(allocation.getAmount()),
                    asMoney(alreadyPaidFor), asMoney(row.getEarnedOnAmount()));
        }
        return carried;
    }

    /**
     * Takes the money out of the rows it is standing in, in the order it was handed them, and puts
     * it in the current account.
     *
     * <p><strong>The order is the caller's and it is a rule.</strong> Two of the three doors hand
     * in everything the account holds in the order a withdrawal takes it — the interest the bank
     * paid first, because it is the cheapest money the customer has, and then their own deposits
     * oldest first, which is the promise this method was written about and which still holds for
     * every row it was written about. The walk itself has no opinion: it takes what it is given
     * until the amount is met, which is what lets one movement serve three doors that disagree
     * about which rows are in play.
     *
     * <p>One movement for both doors above, because it <em>is</em> one movement: which euros leave a
     * savings account is a rule about deposits rather than about who asked, and two copies of it
     * would be two answers to "which deposit did that come out of" — with the pot's copy the one
     * that drifted, since a pot's draw-down is the one nobody watches.
     *
     * <p>The deposits are handed in rather than fetched, which is what lets the caller decide which
     * of them are in play. Two of the three doors hand in all of them; the third,
     * {@link #returnToAPotsMemberWhatIsStillTheirOwn}, hands in one member's own — which is the
     * whole of what makes a settlement safe to do without anybody's approval, and it is that
     * argument rather than a second draw-down that the settlement adds.
     *
     * <p>Whoever calls it has already refused an account that does not hold the money. This walks
     * what it is given and stops when the amount is met.
     */
    private RecordedWithdrawal drawTheOldestDepositsDownFirst(long savingsAccountId,
                                                              long toCurrentAccountId,
                                                              BigDecimal amount,
                                                              List<Deposit> inTheOrderTheyLeave) {
        Instant clockReads = clock.instant();
        Instant now = clockReads.truncatedTo(ChronoUnit.MILLIS);
        log.debug("withdrawal takes its moment from the application clock savingsAccountId={} clockReads={} "
                + "recordedMoment={}", savingsAccountId, clockReads, now);
        Withdrawal withdrawal = withdrawals.save(
                Withdrawal.madeByTheCustomer(savingsAccountId, toCurrentAccountId, amount, now));
        drawTheRowsDown(withdrawal, amount, inTheOrderTheyLeave);
        accounts.depositInto(toCurrentAccountId, amount);
        log.info("withdrawal accepted withdrawalId={} savingsAccountId={} toCurrentAccountId={} amount={} "
                        + "withdrawnAt={}", withdrawal.getId(), savingsAccountId, toCurrentAccountId,
                asMoney(amount), withdrawal.getWithdrawnAt());
        return recorded(withdrawal);
    }

    /**
     * Reduces the rows the money is standing in, in the order they were handed over, and writes an
     * allocation per row it reached.
     *
     * <p><strong>Underneath both ways money leaves a savings account</strong>, because which euros
     * leave is one rule rather than two: a withdrawal the customer made and a charge the bank took
     * both spend the interest first and then the deposits oldest first, and two copies of that walk
     * would be two answers to "which deposit did that come out of" — with the charge's copy the one
     * that drifted, since a charge is the one nobody watches.
     *
     * <p>What differs between the two is the row that was written above it and what happens
     * afterwards: a withdrawal credits a current account and a charge credits nothing. Neither of
     * those is a decision this walk takes, which is why neither is in it.
     *
     * <p>Whoever calls it has already decided the amount may be taken. This walks what it is given
     * and stops when the amount is met.
     *
     * <p><strong>It answers with the allocations it wrote</strong>, which two of the three callers
     * throw away and the third needs. A move between two of the customer's own savings accounts has
     * to carry the earned-on figure of the rows it emptied across to the row the euros arrive in,
     * and which rows those were — and how much came out of each — is decided here and nowhere else.
     * Worked out a second time by the caller, from the same list in the same order, it would be a
     * second walk free to disagree with this one about the rounding of a cent, and the customer's
     * high-water mark would move by the difference.
     */
    private List<WithdrawalAllocation> drawTheRowsDown(Withdrawal withdrawal, BigDecimal amount,
                                                       List<Deposit> inTheOrderTheyLeave) {
        long savingsAccountId = withdrawal.getSavingsAccountId();
        List<WithdrawalAllocation> made = new ArrayList<>();
        // Asked once, before the loop, so that the gathering and the line that says it can never
        // disagree about whether anybody is listening — a level changed mid-withdrawal would
        // otherwise print a list missing its first deposits.
        boolean sayWhichDepositsItCameOutOf = log.isDebugEnabled();
        List<String> drawnDown = new ArrayList<>();
        for (ARowDrawnDown reached : whichRowsThisComesOutOf(amount, inTheOrderTheyLeave)) {
            Deposit deposit = reached.row();
            deposit.reduceBy(reached.taken());
            made.add(new WithdrawalAllocation(withdrawal.getId(), deposit.getId(), reached.taken()));
            // Gathered rather than logged here, so that a withdrawal spread over a long list of
            // deposits is still one line in the log. Guarded, because rendering a deposit is
            // work — four values per deposit the withdrawal reaches, two of them amounts to
            // format — and the string is thrown away when the application runs at INFO. The
            // same reasoning as the streak walk's derivation line, and the opposite of the
            // points sweep's per-batch line, which passes getters and renders nothing.
            if (sayWhichDepositsItCameOutOf) {
                drawnDown.add("[depositId=" + deposit.getId() + " landedAt=" + deposit.getDepositedAt()
                        + " took=" + asMoney(reached.taken())
                        + " leftInIt=" + asMoney(reached.leftInItAfterwards()) + "]");
            }
        }
        allocations.saveAll(made);
        log.debug("withdrawal allocated savingsAccountId={} withdrawalId={} depositsTouched={} cents={}",
                savingsAccountId, withdrawal.getId(), made.size(), amount.movePointRight(2).longValueExact());
        // Which deposits the money actually came out of, in the order it came out of them, and what
        // is left in each afterwards. The order is a rule rather than an accident of storage — the
        // deposit that has been there longest goes first — and it is what decides where a forfeited
        // loyalty anniversary falls, since what an anniversary pays is worked out from what is left
        // in that deposit. Said out loud at the moment it is decided, because the alternative is
        // inferring it a year later from what an anniversary did or did not pay.
        if (sayWhichDepositsItCameOutOf) {
            log.debug("withdrawal drew the rows down in the order it takes them savingsAccountId={} "
                    + "withdrawalId={} purpose={} drawnDown={}",
                    savingsAccountId, withdrawal.getId(), withdrawal.getPurpose(),
                    String.join(" ", drawnDown));
        }
        return made;
    }

    /**
     * Which of these rows an amount comes out of and how much of each, in the order they were
     * handed over — worked out, with nothing written down and nothing reduced.
     *
     * <p><strong>The one statement of that arithmetic, and it is shared on purpose.</strong> The
     * draw-down that actually empties the rows walks this, and so does the reading that says what
     * moving money to another account would cost before anybody presses anything. Written twice,
     * the reading would be free to disagree with the movement about which deposit the last cent
     * came out of — and since what an anniversary pays is worked out from what is left in a
     * deposit, the disagreement would surface as a customer being quoted one forfeit and charged
     * another.
     *
     * <p>Static and taking no service, because it decides nothing about permission: it is a
     * subtraction over a list somebody else chose and ordered. Whoever calls it has already settled
     * that the amount may leave.
     *
     * <p>Rows it does not reach are not in the answer, and a row it reaches for nothing never
     * happens: the walk stops the moment the amount is met.
     */
    private static List<ARowDrawnDown> whichRowsThisComesOutOf(BigDecimal amount,
                                                               List<Deposit> inTheOrderTheyLeave) {
        BigDecimal stillToTake = amount;
        List<ARowDrawnDown> reached = new ArrayList<>();
        for (Deposit deposit : inTheOrderTheyLeave) {
            if (stillToTake.signum() == 0) {
                break;
            }
            BigDecimal taken = deposit.getRemainingAmount().min(stillToTake);
            if (taken.signum() > 0) {
                reached.add(new ARowDrawnDown(deposit, taken,
                        deposit.getRemainingAmount().subtract(taken)));
                stillToTake = stillToTake.subtract(taken);
            }
        }
        return reached;
    }

    /**
     * One row an amount is coming out of: the row itself, what comes out of it, and what is left in
     * it afterwards.
     *
     * <p>The third figure is the one worth naming. What an anniversary pays is worked out from what
     * is still in a deposit, so a reading that wants to say what moving money would cost in loyalty
     * needs to know what each row will hold afterwards — and asking the row after it has been
     * reduced is not available to a reading that reduces nothing.
     */
    private record ARowDrawnDown(Deposit row, BigDecimal taken, BigDecimal leftInItAfterwards) {
    }

    /**
     * Which deposits a move out of this account would draw down and how much of each, refusing
     * first everything that would refuse the move itself.
     *
     * <p><strong>A reading that refuses, which is unusual here and is the point of it.</strong>
     * Whoever asks this is about to show a customer what moving would cost before they press
     * anything, and a cost quoted for a move that would have been turned away is worse than no
     * quote at all — somebody would weigh a loyalty clock against a notice period they were never
     * going to get past. So it goes through the same gate the move goes through, in the same order,
     * with the same sentences.
     *
     * <p>It writes nothing and reduces nothing. The rows come back as they stand, each with what
     * the move would take out of it and what would be left in it, so that whoever prices the
     * forfeit is pricing the same euros the move would actually take.
     *
     * <p>Package-private, and this module's answer to a question the Loyalty module asks: which
     * euros are about to have their clock restarted. It is asked for through
     * {@link DepositsService#whatAMoveWouldDrawDown}, because Loyalty is handed one face of this
     * module and not three.
     *
     * @throws WithdrawalRefused for every reason the move itself would be refused on this side
     */
    List<MoneyAMoveWouldTake> whatAMoveWouldDrawDown(long fromSavingsAccountId,
                                                     long toSavingsAccountId, BigDecimal amount) {
        List<Deposit> inTheOrderItLeaves =
                refuseUnlessThatMuchMayLeaveForAMove(fromSavingsAccountId, toSavingsAccountId, amount);
        List<MoneyAMoveWouldTake> wouldTake =
                whichRowsThisComesOutOf(amount, inTheOrderItLeaves).stream()
                        .map(reached -> new MoneyAMoveWouldTake(reached.row().getId(),
                                AmountOfMoney.quotedToTheCent(reached.taken()),
                                AmountOfMoney.quotedToTheCent(reached.leftInItAfterwards()),
                                reached.row().getDepositedAt()))
                        .toList();
        log.debug("what a move would draw down fromSavingsAccountId={} toSavingsAccountId={} "
                        + "amount={} depositsItWouldTouch={}", fromSavingsAccountId,
                toSavingsAccountId, asMoney(amount), wouldTake.size());
        return wouldTake;
    }

    /**
     * The money this customer took back out of savings inside a stretch of time, oldest first —
     * everything they withdrew, whichever of their savings accounts it left.
     *
     * <p>The other half of a week of saving. A week counts what somebody put away less what they
     * took back out of it, and {@link io.dataroots.savingstreak.deposits.DepositsService#depositsLandedBetween}
     * answers the first half over the same boundaries, in the same shape, for the same customer.
     *
     * <p>The stretch is half-open — the first moment counts, the last does not — so that whoever
     * splits time into adjacent stretches gets each withdrawal in exactly one of them.
     *
     * <p>The withdrawals rather than a total of them, because what a total means is the caller's
     * rule and not this module's: whoever is asking is the one who knows whether it is subtracting
     * them from something.
     *
     * @throws IllegalArgumentException if the stretch ends before it begins
     */
    @Transactional(readOnly = true)
    public List<WithdrawalMade> withdrawalsMadeBetween(long customerId, Instant from, Instant until) {
        if (from.isAfter(until)) {
            String reason = "a stretch of time runs forwards, and " + from + " is after " + until;
            log.warn("withdrawals not counted customerId={} reason={}", customerId, reason);
            throw new IllegalArgumentException(reason);
        }
        List<WithdrawalMade> taken = asMade(withdrawals.takenOutBetween(customerId, from, until));
        log.debug("withdrawals made in a stretch of time customerId={} from={} until={} "
                + "withdrawals={}", customerId, from, until, taken.size());
        return taken;
    }

    /**
     * Everything this customer took back out before a moment, oldest first — the other half of every
     * week of their saving up to that point, across every account they hold.
     *
     * <p>Exclusive of the moment, so that this and {@link #withdrawalsMadeBetween} split time at it
     * the same way, and bounded at all for the reason the deposits' own query gives: the application
     * clock moves, and a caller walking somebody's weeks back is entitled to ask for the part of it
     * that has actually happened.
     */
    @Transactional(readOnly = true)
    public List<WithdrawalMade> withdrawalsMadeBefore(long customerId, Instant until) {
        List<WithdrawalMade> taken = asMade(withdrawals.takenOutBefore(customerId, until));
        log.debug("withdrawals made before a moment customerId={} until={} withdrawals={}",
                customerId, until, taken.size());
        return taken;
    }

    /**
     * Withdrawals as they leave this module: quoted to the cent, once, here — SQLite has no decimal
     * type and hands EUR 12.50 back as 12.5, and a caller subtracting those from deposits would
     * either restate the rounding or print a figure that does not read as money.
     */
    private static List<WithdrawalMade> asMade(List<Withdrawal> withdrawals) {
        return withdrawals.stream()
                .map(withdrawal -> new WithdrawalMade(withdrawal.getId(),
                        AmountOfMoney.quotedToTheCent(withdrawal.getAmount()),
                        withdrawal.getWithdrawnAt()))
                .toList();
    }

    /**
     * What this account's holder has taken back out of it, newest first.
     *
     * <p>Their own withdrawals and never a charge the bank took, which is the reading
     * {@link DepositsService#depositsInto} gives on the way in: that listing leaves out the interest
     * the bank paid, and this one leaves out the charge it took, because both answer "what have I
     * done with this money" rather than "what has happened to this account". What has happened to
     * the account is the money-movement ledger, and both kinds of row are in that.
     */
    @Transactional(readOnly = true)
    public List<RecordedWithdrawal> withdrawalsFrom(long savingsAccountId) {
        return withdrawals.findBySavingsAccountIdAndPurposeOrderByWithdrawnAtDescIdDesc(
                        savingsAccountId, WithdrawalPurpose.CUSTOMER).stream()
                .map(this::recorded)
                .toList();
    }

    /**
     * What the deposits handed in still hold between them, which is the balance any withdrawal out
     * of them is weighed against.
     *
     * <p>Summed from the rows rather than asked for separately, so that the figure a withdrawal is
     * refused against and the figure it is taken out of are one reading. Two reads a moment apart is
     * how an account comes to be refused against one balance and drawn down against another.
     */
    private static BigDecimal whatIsLeftIn(List<Deposit> deposits) {
        return deposits.stream().map(Deposit::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Refuses a withdrawal of more than the account has in it, in the sentence that says how much it
     * does have.
     *
     * <p>Before the goals gate and never after it, because the balance is the more basic truth: an
     * account that does not hold the money at all is refused for that, and being told what is
     * unallocated first would send somebody to free money that was never going to be enough.
     *
     * <p>Asked of a pot's approved withdrawal as well, where it is a backstop rather than the
     * sentence anybody reads: Shared Pots weighs the same figure against the same deposits first and
     * refuses in words about the pot and what it now holds. This is what stops a pot paying out more
     * than it has if that check is ever got round.
     */
    private void refuseUnlessItStillHoldsThatMuch(String what, long savingsAccountId,
                                                  long theOtherAccountId,
                                                  BigDecimal amount, BigDecimal balance) {
        if (balance.compareTo(amount) >= 0) {
            return;
        }
        String reason = "There is not enough in that savings account to move EUR " + asMoney(amount)
                + ". It holds EUR " + asMoney(balance) + ".";
        log.warn("{} rejected savingsAccountId={} toAccountId={} amount={} balance={} reason={}",
                what, savingsAccountId, theOtherAccountId, asMoney(amount), asMoney(balance), reason);
        throw new WithdrawalRefused(WithdrawalRefused.Kind.NOT_ENOUGH_MONEY, reason);
    }

    /**
     * Refuses a withdrawal the agreement this account was opened under will not let go of yet.
     *
     * <p><strong>Nothing here knows what the condition is.</strong> Whether the account gives
     * notice, is locked until a maturity date or keeps a floor is the Products module's business,
     * and this module asks one question and reports what it is told: the condition for the log, and
     * the sentence for whoever asked, carried through untouched. That is the point of the seam —
     * two more conditions arrive in later slices and not one line of this method changes.
     *
     * <p><strong>After the balance gate and before the goals gate.</strong> The balance is still
     * the most basic truth, and an account that does not hold the money at all is refused for that
     * first. The agreement comes next because it is what the account <em>is</em>: a goal is a label
     * the customer put on their own money and can take off in one press, while a notice period is
     * eleven more days of waiting. Told about the goal first, somebody would free money from a goal
     * and then discover the money could not leave the account anyway, which is two trips for one
     * refusal.
     *
     * <p>Before anything is written, like every other refusal here, so a withdrawal that is not
     * allowed moves no money, reduces no deposit and — because the telling is on the other side of
     * the draw-down — spends no notice.
     *
     * <p>An account with nothing attached to it is refused nothing, and never reaches this at all:
     * the answer for free savings is an empty one, so a customer who never chose a product notices
     * no difference whatsoever.
     */
    private void refuseUnlessTheAgreementLetsItGo(String what, long savingsAccountId,
                                                  long theOtherAccountId, BigDecimal amount) {
        Optional<AConditionInTheWay> inTheWay = agreement.whatStopsTaking(savingsAccountId, amount);
        log.debug("withdrawal weighed against the agreement the account is on savingsAccountId={} "
                        + "amount={} condition={}", savingsAccountId, asMoney(amount),
                inTheWay.map(AConditionInTheWay::condition).orElse(null));
        inTheWay.ifPresent(condition -> {
            log.warn("{} rejected savingsAccountId={} toAccountId={} amount={} "
                            + "condition={} reason={}", what, savingsAccountId, theOtherAccountId,
                    asMoney(amount), condition.condition(), condition.reason());
            throw new WithdrawalRefused(WithdrawalRefused.Kind.AGAINST_THE_AGREEMENT,
                    condition.reason());
        });
    }

    /**
     * Refuses a withdrawal that would take money a savings goal is holding.
     *
     * <p>Beside the balance gate above and never before it, because the balance is the more basic
     * truth: an account that does not hold the money at all is refused for that, and being told what
     * is unallocated first would send somebody to free money that was never going to be enough.
     *
     * <p>Before anything is written, like every other refusal here, so a withdrawal that is not
     * allowed moves no money, reduces no deposit and leaves every goal's allocation exactly as it
     * was — there is nothing to reverse because nothing happened.
     *
     * <p><strong>No goal is quietly reduced to cover the shortfall.</strong> Reallocating a
     * customer's money without being asked is the thing this feature refuses to do, and doing it to
     * the lowest-ranked goal would be no better for being rule-governed. The customer frees the
     * money from the goal they choose, looking straight at the goal they are taking it from, and
     * then withdraws it.
     *
     * <p>An account with no goals has claimed nothing, so unallocated is the whole balance and this
     * gate never fires: nothing about withdrawing changes for a customer who never opens a goal.
     *
     * <p>The balance is the one this method's caller has already summed from the deposits, handed
     * across rather than fetched again — Goals never fetches one, which is what lets Deposits ask
     * Goals anything at all.
     */
    private void refuseUnlessNoGoalHasSpokenForIt(String what, long savingsAccountId,
                                                  long theOtherAccountId,
                                                  BigDecimal amount, BigDecimal balance) {
        BigDecimal unallocated = goals.allocationsOn(savingsAccountId, balance).unallocated();
        log.debug("withdrawal weighed against what the goals have claimed savingsAccountId={} amount={} "
                + "unallocated={} balance={}", savingsAccountId, asMoney(amount), asMoney(unallocated),
                asMoney(balance));
        if (amount.compareTo(unallocated) <= 0) {
            return;
        }
        String reason = "That savings account holds EUR " + asMoney(balance) + ", and EUR "
                + asMoney(unallocated) + " of it is not claimed by a goal. Free money from a goal "
                + "first if you want to take out EUR " + asMoney(amount) + ".";
        log.warn("{} rejected savingsAccountId={} toAccountId={} amount={} unallocated={} "
                        + "balance={} reason={}", what, savingsAccountId, theOtherAccountId,
                asMoney(amount), asMoney(unallocated), asMoney(balance), reason);
        throw new WithdrawalRefused(WithdrawalRefused.Kind.MONEY_IS_SPOKEN_FOR, reason);
    }

    /**
     * Refuses a withdrawal whose two ends are not one customer's own accounts.
     *
     * <p>The same question a deposit asks, in the opposite direction, and asked of the same module:
     * who holds what is Accounts' answer, and the sentences naming an account that is not there are
     * Accounts' words rather than two copies of them kept here.
     *
     * <p>Whose the other account was is never named. Whoever asked already knew the identifier they
     * sent; saying who it belongs to would tell them something new about a customer who is not them.
     *
     * <p><strong>A shared pot's savings account is refused outright, whoever is asking.</strong>
     * This is the one place money leaves a savings account, and a pot's money does not leave that
     * way: a withdrawal draws the account's oldest deposits down first regardless of whose they
     * are, so on a pot it spends other members' euros — and, because their mark does not fall with
     * what they hold, costs them the points on their next contributions. A pot's members decide
     * that together, through a proposal, and the door is shut here rather than left ajar until the
     * slice that builds proposals arrives. All three pot pairings are refused in the same sentence,
     * including the member's own: being allowed to pay in is not being allowed to take out, and a
     * member who was told "you are not a member" would go looking for the wrong mistake.
     */
    private void refuseUnlessOneCustomersOwnAccounts(long savingsAccountId, long toCurrentAccountId) {
        AccountPairing pairing = accounts.pairingFor(savingsAccountId, toCurrentAccountId);
        String reason = switch (pairing) {
            // The one pairing money can move across, so the only one that goes no further.
            case HELD_BY_ONE_CUSTOMER -> null;
            case NO_SUCH_SAVINGS_ACCOUNT -> AccountsService.noSuchSavingsAccount(savingsAccountId);
            case NO_SUCH_CURRENT_ACCOUNT -> AccountsService.noSuchCurrentAccount(toCurrentAccountId);
            case HELD_BY_DIFFERENT_CUSTOMERS -> "A withdrawal can only return money to a current account "
                    + "held by the same customer.";
            case HELD_BY_A_POT_THE_PAYER_MAY_PAY_INTO, HELD_BY_A_POT_THE_PAYER_DOES_NOT_BELONG_TO,
                 HELD_BY_A_POT_THE_PAYER_ONLY_WATCHES ->
                    "That savings account belongs to a shared pot, and a shared pot's money leaves "
                            + "it by proposal: propose a withdrawal, and it goes through when every "
                            + "other member with money still in the pot has approved it.";
            // A pot that is over, which has nothing left to take out and no proposal to take it
            // out with. Told apart from the three above because the sentence they carry is an
            // instruction, and sending somebody off to propose a withdrawal from a closed pot
            // would be an instruction that cannot be followed.
            case HELD_BY_A_POT_THAT_IS_CLOSED ->
                    "That savings account belongs to a shared pot that has been closed. Closing "
                            + "returned every member's own euros to them, so there is nothing left "
                            + "in it to take out.";
        };
        if (reason == null) {
            return;
        }
        // Which of the refusals this is, in the terms the API answers in: an identifier for
        // something that is not there, or two real accounts and a request they cannot be asked to
        // honour. A pot is the second — the account is plainly there, and what is being asked of it
        // is the thing this endpoint does not do.
        WithdrawalRefused.Kind kind = switch (pairing) {
            case NO_SUCH_SAVINGS_ACCOUNT, NO_SUCH_CURRENT_ACCOUNT ->
                    WithdrawalRefused.Kind.NO_SUCH_ACCOUNT;
            // Never reached: the pairing money moves across left this method above.
            case HELD_BY_ONE_CUSTOMER, HELD_BY_DIFFERENT_CUSTOMERS,
                 HELD_BY_A_POT_THE_PAYER_MAY_PAY_INTO, HELD_BY_A_POT_THE_PAYER_DOES_NOT_BELONG_TO,
                 HELD_BY_A_POT_THE_PAYER_ONLY_WATCHES, HELD_BY_A_POT_THAT_IS_CLOSED ->
                    WithdrawalRefused.Kind.AGAINST_THE_RULES;
        };
        log.warn("withdrawal rejected savingsAccountId={} toCurrentAccountId={} pairing={} reason={}",
                savingsAccountId, toCurrentAccountId, pairing, reason);
        throw new WithdrawalRefused(kind, reason);
    }

    /**
     * Refuses anything that is not an amount of money moving out.
     *
     * <p>What counts as one is {@link AmountOfMoney}'s answer, so a figure quoted more finely than
     * to the cent is refused here in the same words a deposit of it would be refused in. Checked
     * before a single record is written, so a refusal never has to be undone.
     */
    private void refuseUnlessAnAmountOfMoney(long savingsAccountId, long toCurrentAccountId, BigDecimal amount) {
        AmountOfMoney.whyItIsNotOne("withdrawal", amount).ifPresent(reason -> {
            log.warn("withdrawal rejected savingsAccountId={} toCurrentAccountId={} amount={} reason={}",
                    savingsAccountId, toCurrentAccountId, amount.toPlainString(), reason);
            throw new WithdrawalRefused(WithdrawalRefused.Kind.AGAINST_THE_RULES, reason);
        });
    }

    private RecordedWithdrawal recorded(Withdrawal withdrawal) {
        return new RecordedWithdrawal(withdrawal.getId(), withdrawal.getAmount(),
                withdrawal.getDestinationCurrentAccountId(), withdrawal.getWithdrawnAt(),
                allocations.findByWithdrawalIdOrderByIdAsc(withdrawal.getId()).stream()
                        .map(allocation -> new RecordedWithdrawalAllocation(
                                allocation.getDepositId(), allocation.getAmount()))
                        .toList());
    }
}
