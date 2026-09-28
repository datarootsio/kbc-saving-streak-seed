package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

/**
 * An everyday account a deposit draws money from, identified to the customer by its IBAN and worth
 * whatever is left in it.
 *
 * <p>The balance is kept here, and it is a figure rather than a sum of anything. That is not because
 * nothing ever moves it — money both arrives in a current account and leaves it — but because the
 * figure is the account and the records beside it are the explanations. It is the opposite of a
 * savings account, whose balance <em>is</em> the deposits made into it and is worked out on every
 * read. The two are stored differently because only one of them is derivable.
 *
 * <p><strong>Money arrives here as well as leaving.</strong> This javadoc once said it never did,
 * and said that the application holds no record of where the money came from. Both stopped being
 * true when {@link MonthlyIncome} landed — a declared salary is credited by a nightly job and
 * {@link IncomePaid} is the record of every payday it credited — and the declaring of
 * {@link RecurringBill recurring bills} makes the second sentence doubly wrong, because what leaves
 * this account every month is now written down too. What is still true, and is the honest version of
 * the old claim, is that the balance is not reconstructed from those records: they explain it, they
 * do not define it, and a payday credited or a bill taken moves the figure rather than being summed
 * into it.
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

    /**
     * What stops two changes to this row at once from both being written.
     *
     * <p>Every change to a balance in this application is a read, a decision and then a write: the
     * amount is checked against what is there and the row is written back. Two of those interleaving
     * would both pass the check and both be allowed — money taken out twice on one balance, which is
     * the worst thing this application could be asked to explain.
     *
     * <p>Nothing can interleave today, and that is precisely why this is here. One line of
     * configuration keeps the connection pool at a single connection, so there is one transaction at
     * a time; the guarantee is a property of a datasource setting rather than of the model, and it
     * would leave with that line the day this runs on a database that serves more than one writer.
     * With a version on the row, the second write of a pair fails and is rolled back instead.
     *
     * <p>The column is added with a default so that rows written before it existed carry a nought
     * rather than a null, which is what lets an existing file be opened by this release without a
     * migration.
     */
    @Version
    @Column(columnDefinition = "integer not null default 0")
    private long version;

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
