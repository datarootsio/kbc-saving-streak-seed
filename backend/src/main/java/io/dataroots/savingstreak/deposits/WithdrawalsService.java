package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import io.dataroots.savingstreak.accounts.AccountPairing;
import io.dataroots.savingstreak.accounts.AccountsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records money leaving savings, including the oldest deposits it reduced to make that possible. */
@Service
public class WithdrawalsService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalsService.class);
    private static final int DECIMAL_PLACES_IN_AN_AMOUNT_OF_MONEY = 2;

    private final DepositRepository deposits;
    private final WithdrawalRepository withdrawals;
    private final WithdrawalAllocationRepository allocations;
    private final AccountsService accounts;
    private final Clock clock;

    WithdrawalsService(DepositRepository deposits, WithdrawalRepository withdrawals,
                       WithdrawalAllocationRepository allocations, AccountsService accounts, Clock clock) {
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.allocations = allocations;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public RecordedWithdrawal withdraw(long savingsAccountId, long toCurrentAccountId, BigDecimal amount) {
        log.debug("withdrawal requested savingsAccountId={} toCurrentAccountId={} amount={}",
                savingsAccountId, toCurrentAccountId, amount);
        refuseUnlessOneCustomersOwnAccounts(savingsAccountId, toCurrentAccountId);
        refuseUnlessAnAmountOfMoney(amount);

        List<Deposit> oldestFirst = deposits.findBySavingsAccountIdOrderByDepositedAtAscIdAsc(savingsAccountId);
        BigDecimal balance = oldestFirst.stream().map(Deposit::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (balance.compareTo(amount) < 0) {
            String reason = "There is not enough in that savings account to move EUR " + asMoney(amount)
                    + ". It holds EUR " + asMoney(balance) + ".";
            log.warn("withdrawal rejected savingsAccountId={} toCurrentAccountId={} amount={} balance={} reason={}",
                    savingsAccountId, toCurrentAccountId, asMoney(amount), asMoney(balance), reason);
            throw new WithdrawalRefused(WithdrawalRefused.Kind.NOT_ENOUGH_MONEY, reason);
        }

        Instant clockReads = clock.instant();
        Instant now = clockReads.truncatedTo(ChronoUnit.MILLIS);
        log.debug("withdrawal takes its moment from the application clock savingsAccountId={} clockReads={} "
                + "recordedMoment={}", savingsAccountId, clockReads, now);
        Withdrawal withdrawal = withdrawals.save(new Withdrawal(savingsAccountId, toCurrentAccountId, amount, now));
        BigDecimal stillToAllocate = amount;
        List<WithdrawalAllocation> made = new ArrayList<>();
        for (Deposit deposit : oldestFirst) {
            if (stillToAllocate.signum() == 0) {
                break;
            }
            BigDecimal taken = deposit.getRemainingAmount().min(stillToAllocate);
            if (taken.signum() > 0) {
                deposit.reduceBy(taken);
                made.add(new WithdrawalAllocation(withdrawal.getId(), deposit.getId(), taken));
                stillToAllocate = stillToAllocate.subtract(taken);
            }
        }
        allocations.saveAll(made);
        accounts.depositInto(toCurrentAccountId, amount);
        log.debug("withdrawal allocated savingsAccountId={} withdrawalId={} depositsTouched={} cents={}",
                savingsAccountId, withdrawal.getId(), made.size(), amount.movePointRight(2).longValueExact());
        log.info("withdrawal accepted withdrawalId={} savingsAccountId={} toCurrentAccountId={} amount={} "
                        + "withdrawnAt={}", withdrawal.getId(), savingsAccountId, toCurrentAccountId,
                asMoney(amount), withdrawal.getWithdrawnAt());
        return recorded(withdrawal);
    }

    @Transactional(readOnly = true)
    public List<RecordedWithdrawal> withdrawalsFrom(long savingsAccountId) {
        return withdrawals.findBySavingsAccountIdOrderByWithdrawnAtDescIdDesc(savingsAccountId).stream()
                .map(this::recorded)
                .toList();
    }

    private void refuseUnlessOneCustomersOwnAccounts(long savingsAccountId, long toCurrentAccountId) {
        AccountPairing pairing = accounts.pairingFor(savingsAccountId, toCurrentAccountId);
        String reason = switch (pairing) {
            case HELD_BY_ONE_CUSTOMER -> null;
            case NO_SUCH_SAVINGS_ACCOUNT -> AccountsService.noSuchSavingsAccount(savingsAccountId);
            case NO_SUCH_CURRENT_ACCOUNT -> "There is no current account " + toCurrentAccountId + ".";
            case HELD_BY_DIFFERENT_CUSTOMERS -> "A withdrawal can only return money to a current account "
                    + "held by the same customer.";
        };
        if (reason == null) {
            return;
        }
        WithdrawalRefused.Kind kind = pairing == AccountPairing.HELD_BY_DIFFERENT_CUSTOMERS
                ? WithdrawalRefused.Kind.AGAINST_THE_RULES : WithdrawalRefused.Kind.NO_SUCH_ACCOUNT;
        log.warn("withdrawal rejected savingsAccountId={} toCurrentAccountId={} reason={}",
                savingsAccountId, toCurrentAccountId, reason);
        throw new WithdrawalRefused(kind, reason);
    }

    private void refuseUnlessAnAmountOfMoney(BigDecimal amount) {
        String reason = null;
        if (amount.signum() <= 0) {
            reason = "A withdrawal has to be an amount of more than zero, and " + amount.toPlainString()
                    + " is not.";
        } else if (amount.scale() > DECIMAL_PLACES_IN_AN_AMOUNT_OF_MONEY) {
            reason = "An amount of money has at most two decimal places, and " + amount.toPlainString()
                    + " has " + amount.scale() + ".";
        }
        if (reason != null) {
            log.warn("withdrawal rejected amount={} reason={}", amount, reason);
            throw new WithdrawalRefused(WithdrawalRefused.Kind.AGAINST_THE_RULES, reason);
        }
    }

    private RecordedWithdrawal recorded(Withdrawal withdrawal) {
        return new RecordedWithdrawal(withdrawal.getId(), withdrawal.getAmount(),
                withdrawal.getDestinationCurrentAccountId(), withdrawal.getWithdrawnAt(),
                allocations.findByWithdrawalIdOrderByIdAsc(withdrawal.getId()).stream()
                        .map(allocation -> new RecordedWithdrawalAllocation(
                                allocation.getDepositId(), allocation.getAmount()))
                        .toList());
    }

    private static String asMoney(BigDecimal amount) {
        return amount.setScale(DECIMAL_PLACES_IN_AN_AMOUNT_OF_MONEY, RoundingMode.HALF_UP).toPlainString();
    }
}
