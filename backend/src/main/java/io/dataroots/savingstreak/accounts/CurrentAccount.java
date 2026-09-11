package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * An everyday account a deposit draws money from, identified to the customer by its IBAN and worth
 * whatever is left in it.
 *
 * <p>The balance is kept here, and it is a figure rather than a sum of anything: this application
 * holds no record of where the money in a current account came from — salary, transfers, card
 * refunds, none of that happens here — so there is nothing to derive it from. That is the opposite
 * of a savings account, whose balance is the deposits made into it and is worked out on every read.
 * The two are stored differently because only one of them has records underneath it.
 *
 * <p>Taking money out is {@link #withdraw}'s job and not a setter's. An amount that will not fit has
 * to leave the balance exactly as it was, and a caller that could write the field could take half.
 */
@Entity
public class CurrentAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Customer customer;

    private String iban;

    private BigDecimal balance;

    protected CurrentAccount() {
        // for JPA
    }

    public CurrentAccount(Customer customer, String iban, BigDecimal openingBalance) {
        this.customer = customer;
        this.iban = iban;
        this.balance = openingBalance;
    }

    public Long getId() {
        return id;
    }

    public String getIban() {
        return iban;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    /**
     * Takes the amount out of the account, and answers whether there was enough to take. Nothing
     * leaves unless the whole amount can: a partial withdrawal is not a smaller version of what was
     * asked for, it is money gone from one account without arriving in the other.
     *
     * <p>A yes or no rather than a refusal, for the same reason the points ledger reports a spend
     * that way: what the money was for, and how to say so to the person who asked, belongs to
     * whoever is moving it and not to the account it left.
     */
    boolean withdraw(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            return false;
        }
        balance = balance.subtract(amount);
        return true;
    }

    /** Adds money that arrived from the holder's savings account. */
    void deposit(BigDecimal amount) {
        balance = balance.add(amount);
    }
}
