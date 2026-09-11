package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.accounts.AccountHolder;
import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.WithdrawalsService;
import io.dataroots.savingstreak.points.PointsService;
import io.dataroots.savingstreak.streaks.StreaksService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * A savings account, what is in it, what its holder has to spend, and the money that has moved in
 * and out of it.
 *
 * <p>The figures come from four modules that do not know about each other — who holds the account,
 * what has been paid into it, what its holder has earned, and how their week and their run of weeks
 * are going and what that run pays — and are assembled here. Assembling an answer is not a rule: no
 * decision about money, points, weeks or rates is taken in this class.
 *
 * <p>Only the money is the account's. The points, the week and the run of weeks all belong to the
 * customer who holds it and read the same beside every account they hold; what paying in <em>here</em>
 * earned is on each deposit in the history.
 *
 * <p>Rewards are claimed against the customer and not here. The points that pay for one are the
 * holder's rather than this account's, so there is no account for a claim to come out of and no
 * account-shaped list of what has been claimed.
 */
@RestController
@RequestMapping("/api/savings-accounts")
class SavingsAccountController {

    private static final Logger log = LoggerFactory.getLogger(SavingsAccountController.class);

    private final AccountsService accounts;
    private final DepositsService deposits;
    private final WithdrawalsService withdrawals;
    private final PointsService points;
    private final StreaksService streaks;

    SavingsAccountController(AccountsService accounts, DepositsService deposits, WithdrawalsService withdrawals,
                             PointsService points,
                             StreaksService streaks) {
        this.accounts = accounts;
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.points = points;
        this.streaks = streaks;
    }

    /**
     * The figures in one read transaction, so that they describe the same instant of the ledger. Each
     * module opens a read of its own otherwise, and a deposit committing between two of them would
     * have the page state a balance of EUR 0,00 beside a week that has taken EUR 60,00 in — an answer
     * that contradicts itself and that neither module is wrong about.
     *
     * <p>The week and the streak come back together from one call for the same reason one step
     * further in: they are one derivation off one reading of the clock, and asking for them
     * separately is what would have them describe two different weeks.
     */
    @Transactional(readOnly = true)
    @GetMapping("/{savingsAccountId}")
    SavingsAccountResponse savingsAccount(@PathVariable long savingsAccountId) {
        AccountHolder holder = accounts.holderOfSavingsAccount(savingsAccountId)
                .orElseThrow(() -> noSuchSavingsAccount(savingsAccountId));
        return SavingsAccountResponse.of(
                savingsAccountId,
                holder.name(),
                deposits.moneyBalanceOf(savingsAccountId),
                // The holder's points, not the account's. Paying in here earns them and they are
                // reported beside this balance because that is the connection the page is about —
                // but they are the same figure whichever of the customer's accounts is open.
                points.balanceOf(holder.customerId()),
                // And what the holder stands to lose next, for the same reason: the twelve months
                // run against their points rather than against this account's saving.
                points.whatExpiresNextFor(holder.customerId()),
                // And their week and their run of weeks, for the same reason: a week counts what
                // they put away, wherever they put it.
                streaks.weekAndStreakOf(holder.customerId()));
    }

    @GetMapping("/{savingsAccountId}/deposits")
    List<DepositResponse> depositsInto(@PathVariable long savingsAccountId) {
        // Asked before the deposits are, so that an account nobody has heard of is refused rather
        // than answered with the empty history of an account that has simply never been paid into.
        if (!accounts.savingsAccountExists(savingsAccountId)) {
            throw noSuchSavingsAccount(savingsAccountId);
        }
        return deposits.depositsInto(savingsAccountId).stream().map(DepositResponse::of).toList();
    }

    @PostMapping("/{savingsAccountId}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    DepositResponse deposit(@PathVariable long savingsAccountId, @RequestBody DepositRequest request) {
        // Reading the request, not judging it. Whether a figure is a number at all is a question
        // about the characters that arrived; whether the number is an amount this application will
        // move is a rule, and it belongs to Deposits, which refuses on its own.
        if (request.amount() == null || request.fromCurrentAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A deposit needs an amount and the current account it comes from.");
        }
        return DepositResponse.of(deposits.deposit(
                savingsAccountId, request.fromCurrentAccountId(), amountIn(request)));
    }

    @PostMapping("/{savingsAccountId}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    WithdrawalResponse withdraw(@PathVariable long savingsAccountId, @RequestBody WithdrawalRequest request) {
        if (request == null || request.amount() == null || request.toCurrentAccountId() == null) {
            String reason = "A withdrawal needs an amount and the current account it returns to.";
            log.warn("withdrawal rejected savingsAccountId={} reason={}", savingsAccountId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return WithdrawalResponse.of(withdrawals.withdraw(
                savingsAccountId, request.toCurrentAccountId(), withdrawalAmountIn(savingsAccountId, request.amount())));
    }

    @GetMapping("/{savingsAccountId}/withdrawals")
    List<WithdrawalResponse> withdrawalsFrom(@PathVariable long savingsAccountId) {
        if (!accounts.savingsAccountExists(savingsAccountId)) {
            log.warn("withdrawal history rejected savingsAccountId={} reason={}", savingsAccountId,
                    AccountsService.noSuchSavingsAccount(savingsAccountId));
            throw noSuchSavingsAccount(savingsAccountId);
        }
        return withdrawals.withdrawalsFrom(savingsAccountId).stream().map(WithdrawalResponse::of).toList();
    }

    /**
     * The amount as a figure, or a refusal naming what could not be read as one. Named back to
     * whoever sent it, because a person who typed a comma has to see the comma to see the mistake.
     */
    private BigDecimal amountIn(DepositRequest request) {
        return amountIn(request.amount());
    }

    private BigDecimal amountIn(String amount) {
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException notANumber) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\"" + amount
                    + "\" is not an amount of money. Write it in digits with a full stop, like 25.00.");
        }
    }

    /**
     * The same reading a deposit gets, logged on the way out. Every other way a withdrawal can be
     * refused says so in the Withdrawals module's own log; this one is decided here, before the
     * module is called at all, and would otherwise be the single refusal a reviewer reading the
     * application's log could not find. The sentence is logged rather than a summary of it, so that
     * what the log says and what the customer was told are the same words.
     */
    private BigDecimal withdrawalAmountIn(long savingsAccountId, String amount) {
        try {
            return amountIn(amount);
        } catch (ResponseStatusException notAnAmountOfMoney) {
            log.warn("withdrawal rejected savingsAccountId={} amount={} reason={}",
                    savingsAccountId, amount, notAnAmountOfMoney.getReason());
            throw notAnAmountOfMoney;
        }
    }

    /** Worded for whoever reads it: a refusal reaches the screen with its reason unchanged. */
    private ResponseStatusException noSuchSavingsAccount(long savingsAccountId) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, AccountsService.noSuchSavingsAccount(savingsAccountId));
    }
}
