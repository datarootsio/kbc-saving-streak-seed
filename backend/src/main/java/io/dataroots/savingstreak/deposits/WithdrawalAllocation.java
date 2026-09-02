package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** The part of one withdrawal that came from one deposit, retained for the loyalty-bonus exercise. */
@Entity
class WithdrawalAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long withdrawalId;
    private long depositId;
    private BigDecimal amount;

    protected WithdrawalAllocation() {
        // for JPA
    }

    WithdrawalAllocation(long withdrawalId, long depositId, BigDecimal amount) {
        this.withdrawalId = withdrawalId;
        this.depositId = depositId;
        this.amount = amount;
    }

    long getDepositId() { return depositId; }
    BigDecimal getAmount() { return amount; }
}
