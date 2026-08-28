package io.dataroots.savingstreak.accounts;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * An account a customer saves into, and the thing that earns points.
 *
 * <p>It holds neither balance: the money balance is the sum of the deposits made into it and the
 * points balance the sum of the points credited to it, both derived when asked for rather than kept
 * here where they could drift away from the records underneath them.
 */
@Entity
public class SavingsAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Customer customer;

    protected SavingsAccount() {
        // for JPA
    }

    public SavingsAccount(Customer customer) {
        this.customer = customer;
    }

    public Long getId() {
        return id;
    }
}
