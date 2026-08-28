package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.points.PointsService;
import io.dataroots.savingstreak.rewards.Reward;
import io.dataroots.savingstreak.rewards.RewardsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * A savings account, its two balances, the deposits made into it and the rewards claimed out of it.
 *
 * <p>The balances come from three modules that do not know about each other — who owns the account,
 * what has been paid into it, and what that earned — and are assembled here. Assembling an answer is
 * not a rule: no decision about money, points or rewards is taken in this class.
 */
@RestController
@RequestMapping("/api/savings-accounts")
class SavingsAccountController {

    private final AccountsService accounts;
    private final DepositsService deposits;
    private final PointsService points;
    private final RewardsService rewards;

    SavingsAccountController(AccountsService accounts, DepositsService deposits, PointsService points,
                             RewardsService rewards) {
        this.accounts = accounts;
        this.deposits = deposits;
        this.points = points;
        this.rewards = rewards;
    }

    @GetMapping("/{savingsAccountId}")
    SavingsAccountResponse savingsAccount(@PathVariable long savingsAccountId) {
        String owner = accounts.ownerNameOfSavingsAccount(savingsAccountId)
                .orElseThrow(() -> noSuchSavingsAccount(savingsAccountId));
        return new SavingsAccountResponse(
                savingsAccountId,
                owner,
                deposits.moneyBalanceOf(savingsAccountId),
                points.balanceOf(savingsAccountId));
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

    @GetMapping("/{savingsAccountId}/redemptions")
    List<ClaimedRewardResponse> claimedOutOf(@PathVariable long savingsAccountId) {
        // Asked before the claims are, so that an account nobody has heard of is refused rather than
        // answered with the empty history of an account that has simply never claimed anything.
        if (!accounts.savingsAccountExists(savingsAccountId)) {
            throw noSuchSavingsAccount(savingsAccountId);
        }
        return rewards.claimedIn(savingsAccountId).stream().map(ClaimedRewardResponse::of).toList();
    }

    @PostMapping("/{savingsAccountId}/redemptions")
    @ResponseStatus(HttpStatus.CREATED)
    ClaimedRewardResponse claim(@PathVariable long savingsAccountId, @RequestBody ClaimRequest request) {
        // Reading the request, not judging it. Whether the account can afford the reward is a rule,
        // and it belongs to Rewards, which refuses on its own.
        if (request == null || request.reward() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A claim needs to name the reward being claimed.");
        }
        return ClaimedRewardResponse.of(rewards.claim(savingsAccountId, rewardIn(request)));
    }

    /**
     * The reward the claim named, or a refusal naming what is not in the catalogue. Named back to
     * whoever sent it, so a page that sent an old code can see which one it was.
     */
    private Reward rewardIn(ClaimRequest request) {
        String code = request.reward().trim();
        try {
            return Reward.valueOf(code);
        } catch (IllegalArgumentException notInTheCatalogue) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "There is nothing called \"" + code + "\" in the rewards catalogue.");
        }
    }

    /**
     * The amount as a figure, or a refusal naming what could not be read as one. Named back to
     * whoever sent it, because a person who typed a comma has to see the comma to see the mistake.
     */
    private BigDecimal amountIn(DepositRequest request) {
        try {
            return new BigDecimal(request.amount().trim());
        } catch (NumberFormatException notANumber) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\"" + request.amount()
                    + "\" is not an amount of money. Write it in digits with a full stop, like 25.00.");
        }
    }

    /** Worded for whoever reads it: a refusal reaches the screen with its reason unchanged. */
    private ResponseStatusException noSuchSavingsAccount(long savingsAccountId) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, AccountsService.noSuchSavingsAccount(savingsAccountId));
    }
}
