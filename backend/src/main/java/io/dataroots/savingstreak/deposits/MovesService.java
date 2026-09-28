package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import io.dataroots.savingstreak.accounts.AccountHolder;
import io.dataroots.savingstreak.accounts.AccountsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.deposits.AmountOfMoney.asMoney;

/**
 * This module's fourth face: moving money from one of a customer's savings accounts to another of
 * their own, as one operation.
 *
 * <p><strong>Why it is an operation at all, rather than two presses.</strong> Left as a withdrawal
 * followed by a deposit, moving five thousand euros from instant access to a better product costs
 * the customer twice over, and both charges are the right answer to the wrong question. The euros
 * earn nothing on arrival, because they have been saved once already and a euro saved twice is one
 * euro — which is correct, and is {@link TheMostEverSaved}'s whole rule. And the week nets to
 * nothing, because five thousand out and five thousand in is no new saving — which is also correct,
 * and is what stops a run of weeks being kept alive by money going round in a circle. Each rule is
 * individually right and the combination punishes somebody for taking the bank's better offer, so
 * the answer is neither to weaken a rule nor to make an exception inside one: it is to say that
 * this is not two movements, and to record it as what it is.
 *
 * <p><strong>What a move is not is a way round anything.</strong> The source account's agreement
 * refuses a move in exactly the words it refuses a withdrawal — a notice period that has not run, a
 * term that has not matured, a floor to keep — and it refuses it through exactly the same lines of
 * code, in {@link WithdrawalsService#refuseUnlessThatMuchMayLeaveForAMove}. A goal that has spoken
 * for the money refuses it too, for the same reason a withdrawal is refused: this application does
 * not reallocate somebody's money on their behalf, and it does not matter which button was pressed.
 * A second copy of those rules is the thing this class most carefully does not have.
 *
 * <p><strong>What a move does cost is the loyalty clock, and it is quoted first.</strong> The euros
 * arrive as a new deposit dated today, so the anniversary they were part-way towards is gone and a
 * fresh twelve months begins. That is honest, it is the genuine price of switching, and it is the
 * thing the customer is being asked to weigh — so it is read out by
 * {@code LoyaltyService.whatMovingWouldCost} before the button rather than explained afterwards.
 *
 * <p><strong>Its own face rather than a method on one of the three beside it</strong>, because a
 * move is the one thing this module does that writes into both of its ledgers at once. Put on
 * {@link WithdrawalsService} it would need to write a deposit; put on {@link DepositsService} it
 * would need the withdrawal gates, which live a service away. Here it needs neither: it settles
 * the one question that is genuinely its own — are these two accounts really one customer's — and
 * then asks each of the other two faces to do the half it already knows how to do.
 */
@Service
public class MovesService {

    private static final Logger log = LoggerFactory.getLogger(MovesService.class);

    /** What this operation is called in the sentence refusing a figure that is not an amount. */
    private static final String A_MOVE = "move";

    private final AccountsService accounts;
    private final DepositsService deposits;
    private final WithdrawalsService withdrawals;
    private final Clock clock;

    MovesService(AccountsService accounts, DepositsService deposits,
                 WithdrawalsService withdrawals, Clock clock) {
        this.accounts = accounts;
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.clock = clock;
    }

    /**
     * Moves money from one savings account to another of the same customer's, in one transaction.
     *
     * <p>The order of what follows is the whole of the operation, and every line of it is a
     * refusal that has to arrive before a cent moves.
     *
     * <p><strong>Whose the two accounts are, first.</strong> A move is a movement between two of
     * them, and if there is no such movement to make then the amount is beside the point — the
     * order a deposit and a withdrawal both ask their two questions in. Two different holders is
     * refused, an account behind a shared pot is refused at either end, and an account moving to
     * itself is refused: the pot's money leaves by proposal and a move to itself is a row saying
     * nothing happened.
     *
     * <p><strong>Then whether the destination will take money at all</strong>, which is asked of
     * {@link DepositsService#whatStopsMoneyBeingPaidInto} and refused in that method's own
     * sentence. A closed account turns money away whoever is sending it and however they send it,
     * and this is the one refusal in the whole move that is a fact about the destination — so it is
     * the destination's existing refusal, word for word, rather than a second sentence about the
     * same fact.
     *
     * <p><strong>Then the amount, and then everything the source's agreement has to say</strong>,
     * the second of which is not asked here at all: {@link WithdrawalsService} asks it, because a
     * move is refused exactly as a withdrawal is and the only way to be certain of that is for the
     * same lines to do the asking.
     *
     * <p><strong>Both halves in one transaction and at one moment.</strong> Money that left one
     * account and arrived in neither is the worst balance in this application to be asked to
     * explain, and it cannot happen here: if the arriving row cannot be written, the row that left
     * is rolled back with it, and the notice the move spent is rolled back with both. The moment is
     * read once and handed to each half, so the two rows of one move are dated identically rather
     * than a millisecond apart.
     *
     * @throws WithdrawalRefused if the two accounts are not one customer's own savings accounts, if
     *                           the figure is not an amount of money, if the source does not hold
     *                           it, if the agreement will not let it go, or if a goal has spoken
     *                           for it
     * @throws DepositRefused    if the destination's agreement has ended, in the sentence a deposit
     *                           into a closed account is already refused in
     */
    @Transactional
    public AMoveBetweenSavingsAccounts move(long fromSavingsAccountId, long toSavingsAccountId,
                                            BigDecimal amount) {
        log.debug("move requested fromSavingsAccountId={} toSavingsAccountId={} amount={}",
                fromSavingsAccountId, toSavingsAccountId, amount);
        long customerId = theCustomerWhoHoldsBothOrRefuse(fromSavingsAccountId, toSavingsAccountId);
        refuseIfTheDestinationTakesNoMoreMoney(fromSavingsAccountId, toSavingsAccountId);
        refuseUnlessAnAmountOfMoney(fromSavingsAccountId, toSavingsAccountId, amount);

        // One moment for both rows, read from the application's clock rather than the machine's and
        // truncated the way every other movement here is, so that a move and the deposits and
        // withdrawals around it are dated in the same units.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        // The source half first, because it is the half that can still be refused. It asks the
        // agreement before it moves anything and tells the agreement afterwards, inside this
        // transaction, which is what stops a move being a way round a notice period.
        AMoveOutOfSavings left = withdrawals.takeOutForAMoveTo(
                fromSavingsAccountId, toSavingsAccountId, amount, now);
        // And the arriving half, carrying across what the rows it emptied had already been paid
        // for, so the most this customer has ever saved is exactly what it was a moment ago.
        long arrivedAs = deposits.landAMoveFrom(toSavingsAccountId, customerId,
                fromSavingsAccountId, amount, left.earnedOnCarriedAcross(), now);

        // Everything that decided the move on one line: both accounts, the amount, the customer,
        // the two rows it became, and the figure that says why it earned nothing. A reviewer can
        // read the whole operation from here and find each half's own line underneath it.
        log.info("move accepted fromSavingsAccountId={} toSavingsAccountId={} customerId={} "
                        + "amount={} earnedOnCarriedAcross={} withdrawalId={} depositId={} "
                        + "pointsEarned=0 movedAt={}",
                fromSavingsAccountId, toSavingsAccountId, customerId, asMoney(amount),
                asMoney(left.earnedOnCarriedAcross()), left.withdrawalId(), arrivedAs, now);
        // Both amounts quoted to the cent here, once, because this is where they leave the module:
        // SQLite has no decimal type and hands EUR 12.50 back as 12.5, and an answer that quoted
        // one of the two to the cent and not the other would read as two kinds of money.
        return new AMoveBetweenSavingsAccounts(fromSavingsAccountId, toSavingsAccountId, customerId,
                AmountOfMoney.quotedToTheCent(amount),
                AmountOfMoney.quotedToTheCent(left.earnedOnCarriedAcross()),
                left.withdrawalId(), arrivedAs, now);
    }

    /**
     * Refuses a move whose two ends are not two savings accounts held by one and the same customer,
     * and answers whose they are when they are.
     *
     * <p><strong>The one rule in a move that is genuinely this class's own</strong>, and the only
     * one of the gates that a withdrawal's version of could not be reused for: a withdrawal's far
     * end is a current account, so {@code AccountsService.pairingFor} is the wrong question asked
     * of the wrong pair. What is asked instead is who holds each of the two savings accounts, which
     * Accounts answers directly.
     *
     * <p><strong>Two holders is refused, and so is a shared pot at either end.</strong> An account
     * nobody holds belongs to a pot, and a pot's money moves by proposal, with every member whose
     * euros are at stake having agreed to it — so a move out of one would spend other people's
     * money on one person's press, and a move into one would be a contribution that dodged the
     * pot's own door. Whether the account exists at all is told apart from who holds it, because an
     * identifier nobody has heard of and a pot are different mistakes with different things to do
     * about them.
     *
     * <p><strong>An account moving to itself is refused as well.</strong> Nothing about it is
     * dangerous and nothing about it is useful: it would draw the account's own rows down and write
     * them straight back with today's date on them, which is a loyalty clock reset for nothing at
     * all. Refused in a sentence that says so rather than allowed to happen quietly, because a
     * customer who did it by accident would otherwise lose a year of anniversaries and never find
     * out why.
     *
     * <p>Whose the other account is is never named, for the reason a withdrawal's refusal gives:
     * whoever asked already knew the identifier they sent, and saying who holds it would tell them
     * something new about a customer who is not them.
     */
    private long theCustomerWhoHoldsBothOrRefuse(long fromSavingsAccountId,
                                                 long toSavingsAccountId) {
        if (fromSavingsAccountId == toSavingsAccountId) {
            refuse(fromSavingsAccountId, toSavingsAccountId, WithdrawalRefused.Kind.AGAINST_THE_RULES,
                    "Money can only be moved to a different savings account. Savings account "
                            + fromSavingsAccountId + " is the one it is already in.");
        }
        long from = theHolderOrRefuse(fromSavingsAccountId, toSavingsAccountId,
                fromSavingsAccountId, "moved out of");
        long to = theHolderOrRefuse(fromSavingsAccountId, toSavingsAccountId, toSavingsAccountId,
                "moved into");
        if (from != to) {
            refuse(fromSavingsAccountId, toSavingsAccountId, WithdrawalRefused.Kind.AGAINST_THE_RULES,
                    "Money can only be moved between two savings accounts held by the same "
                            + "customer.");
        }
        log.debug("a move is between two accounts of one customer's fromSavingsAccountId={} "
                + "toSavingsAccountId={} customerId={}", fromSavingsAccountId, toSavingsAccountId,
                from);
        return from;
    }

    /**
     * Who holds one end of the move, refusing an account that is not there and an account nobody
     * holds in two different sentences.
     *
     * <p>{@code AccountsService.holderOfSavingsAccount} answers the same empty for both, on the
     * argument that every caller so far has had the same thing to do about them. This is the caller
     * that does not: an identifier nobody has heard of is a mistake in the request and a pot's
     * account is a real account with a rule attached, so the two are told apart here by asking
     * whether the account exists at all.
     *
     * @param end which end of the move this account is, so the sentence names it back
     */
    private long theHolderOrRefuse(long fromSavingsAccountId, long toSavingsAccountId,
                                   long savingsAccountId, String end) {
        Optional<AccountHolder> holder = accounts.holderOfSavingsAccount(savingsAccountId);
        if (holder.isPresent()) {
            return holder.get().customerId();
        }
        if (!accounts.savingsAccountExists(savingsAccountId)) {
            refuse(fromSavingsAccountId, toSavingsAccountId, WithdrawalRefused.Kind.NO_SUCH_ACCOUNT,
                    AccountsService.noSuchSavingsAccount(savingsAccountId));
        }
        refuse(fromSavingsAccountId, toSavingsAccountId, WithdrawalRefused.Kind.AGAINST_THE_RULES,
                "Savings account " + savingsAccountId + " belongs to a shared pot, so money cannot "
                        + "be " + end + " it this way. A pot is paid into by its members and its "
                        + "money leaves it by proposal.");
        throw new IllegalStateException("unreachable: the refusal above always throws");
    }

    /**
     * Refuses a move into an account whose agreement has ended, in the words a deposit into it is
     * already refused in.
     *
     * <p><strong>The existing refusal rather than a second one about the same fact.</strong> A
     * closed savings account turns money away, and it says so in one sentence naming the day it
     * closed — a sentence {@link DepositsService} owns and hands back through
     * {@link DepositsService#whatStopsMoneyBeingPaidInto}. Written again here, the two would be one
     * reword apart from disagreeing, and a customer who met the refusal through a deposit and again
     * through a move would be told two different things about one closed account. It is asked
     * rather than attempted for the same reason the nightly run asks: the answer is needed before
     * the money leaves the source, and by the time a deposit could refuse it the source has already
     * been drawn down.
     *
     * <p>Above the check that the amount is an amount, which is the order a deposit into a closed
     * account is refused in too: nothing the customer types can make a closed account take money,
     * so sending them off to correct a figure first would be asking them to fix something that was
     * never going to help.
     */
    private void refuseIfTheDestinationTakesNoMoreMoney(long fromSavingsAccountId,
                                                        long toSavingsAccountId) {
        deposits.whatStopsMoneyBeingPaidInto(toSavingsAccountId).ifPresent(reason -> {
            log.warn("move rejected fromSavingsAccountId={} toSavingsAccountId={} kind={} reason={}",
                    fromSavingsAccountId, toSavingsAccountId,
                    DepositRefused.Kind.THE_ACCOUNT_IS_CLOSED, reason);
            throw new DepositRefused(DepositRefused.Kind.THE_ACCOUNT_IS_CLOSED, reason);
        });
    }

    /**
     * Refuses anything that is not an amount of money to move.
     *
     * <p>What counts as one is {@link AmountOfMoney}'s answer, so a figure quoted more finely than
     * to the cent is refused here in the same words a deposit or a withdrawal of it would be
     * refused in — with the word "move" in place of the other two, which is the one thing that
     * method takes an argument for.
     */
    private void refuseUnlessAnAmountOfMoney(long fromSavingsAccountId, long toSavingsAccountId,
                                             BigDecimal amount) {
        AmountOfMoney.whyItIsNotOne(A_MOVE, amount).ifPresent(reason -> {
            log.warn("move rejected fromSavingsAccountId={} toSavingsAccountId={} amount={} "
                            + "reason={}", fromSavingsAccountId, toSavingsAccountId,
                    amount.toPlainString(), reason);
            throw new WithdrawalRefused(WithdrawalRefused.Kind.AGAINST_THE_RULES, reason);
        });
    }

    /**
     * Says why a move was refused and refuses it, so that what the log says and what the person at
     * the keyboard was told are the same words rather than a summary and a sentence.
     *
     * <p>{@link WithdrawalRefused} rather than a refusal of this operation's own, and that is a
     * decision worth defending. A move's source half <em>is</em> a withdrawal as far as everything
     * that can refuse it is concerned, and the ticket's rule is that a product's conditions turn a
     * move away in exactly the sentences they turn a withdrawal away in. A third exception type
     * would have to be mapped to statuses somebody chose again, would break the exhaustive switch
     * in the web layer, and would invite the day when the two carry the same sentence with two
     * different statuses on it. The one refusal that is genuinely about the destination arrives as
     * a {@link DepositRefused}, because that is what it already is.
     */
    private void refuse(long fromSavingsAccountId, long toSavingsAccountId,
                        WithdrawalRefused.Kind kind, String reason) {
        log.warn("move rejected fromSavingsAccountId={} toSavingsAccountId={} kind={} reason={}",
                fromSavingsAccountId, toSavingsAccountId, kind, reason);
        throw new WithdrawalRefused(kind, reason);
    }

    /**
     * Which deposits a move would draw down and how much of each, refusing first everything the
     * move itself would be refused for — both the source's conditions and the destination's.
     *
     * <p>The reading behind the figure a customer is shown before they press. It writes nothing and
     * it refuses everything, which is the pair of properties it needs: a cost quoted for a move
     * that would have been turned away is worse than no quote at all.
     *
     * <p>Handed out as euros rather than as points, because what those euros were worth on their
     * next anniversary is the Loyalty module's answer and this one has no opinion about it.
     *
     * @throws WithdrawalRefused for every reason the move would be refused on either side
     * @throws DepositRefused    if the destination's agreement has ended
     */
    @Transactional(readOnly = true)
    public List<MoneyAMoveWouldTake> whatAMoveWouldTake(long fromSavingsAccountId,
                                                        long toSavingsAccountId,
                                                        BigDecimal amount) {
        theCustomerWhoHoldsBothOrRefuse(fromSavingsAccountId, toSavingsAccountId);
        refuseIfTheDestinationTakesNoMoreMoney(fromSavingsAccountId, toSavingsAccountId);
        refuseUnlessAnAmountOfMoney(fromSavingsAccountId, toSavingsAccountId, amount);
        return deposits.whatAMoveWouldDrawDown(fromSavingsAccountId, toSavingsAccountId, amount);
    }
}
