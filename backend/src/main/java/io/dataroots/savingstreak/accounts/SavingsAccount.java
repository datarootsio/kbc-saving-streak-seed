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
 *
 * <p><strong>The holding customer is optional, and that is load-bearing rather than lax.</strong>
 * An account with no holder is one that belongs to something other than a person — today a shared
 * pot, which points at its account rather than the account pointing at the pot, so that this module
 * never learns that pots exist. Everything that makes a personal account personal is read through
 * this link: what a customer holds, who holds an account, and whether one customer holds both ends
 * of a transfer. Leaving it empty is therefore what keeps a pot's account out of somebody's list of
 * accounts, out of their totals and out of the pairing rule for free, rather than by four screens
 * each remembering to leave it out.
 *
 * <p>What it is <em>not</em> is a way for a personal account to lose its holder. Nothing in this
 * application empties the link once it is set: an account is opened with a holder or opened without
 * one, and it stays whichever it was.
 */
@Entity
public class SavingsAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The customer who holds it, or nothing at all when nobody does.
     *
     * <p>Null is read as "nobody holds this" by every question this module answers about a holder,
     * and those are the only four places the link is read at all — which is what makes an account
     * that belongs to a pot rather than a person a small change rather than a wide one.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;

    protected SavingsAccount() {
        // for JPA
    }

    public SavingsAccount(Customer customer) {
        this.customer = customer;
    }

    /**
     * An account nobody holds, for whatever is going to hold it instead.
     *
     * <p>A factory rather than {@code new SavingsAccount(null)}, so that an account opened with no
     * holder says so where it is opened. A null handed to the constructor reads like a customer that
     * failed to be found, and this is the opposite: it is the answer.
     */
    static SavingsAccount heldByNobody() {
        return new SavingsAccount(null);
    }

    public Long getId() {
        return id;
    }
}
