package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** One movement out of a savings account and back to one of its holder's current accounts. */
@Entity
class Withdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long savingsAccountId;
    private long destinationCurrentAccountId;
    private BigDecimal amount;
    private Instant withdrawnAt;

    protected Withdrawal() {
        // for JPA
    }

    Withdrawal(long savingsAccountId, long destinationCurrentAccountId, BigDecimal amount, Instant withdrawnAt) {
        this.savingsAccountId = savingsAccountId;
        this.destinationCurrentAccountId = destinationCurrentAccountId;
        this.amount = amount;
        this.withdrawnAt = withdrawnAt;
    }

    Long getId() { return id; }
    BigDecimal getAmount() { return amount; }
    long getDestinationCurrentAccountId() { return destinationCurrentAccountId; }
    Instant getWithdrawnAt() { return withdrawnAt; }
}
