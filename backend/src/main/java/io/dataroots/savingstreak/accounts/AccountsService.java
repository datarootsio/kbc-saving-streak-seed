package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Accounts module's face to the rest of the application. It answers who exists, which accounts
 * belong to whom, and what is in the ones this application keeps a figure for.
 */
@Service
public class AccountsService {

    private final CustomerRepository customers;
    private final CurrentAccountRepository currentAccounts;
    private final SavingsAccountRepository savingsAccounts;

    AccountsService(CustomerRepository customers,
                    CurrentAccountRepository currentAccounts,
                    SavingsAccountRepository savingsAccounts) {
        this.customers = customers;
        this.currentAccounts = currentAccounts;
        this.savingsAccounts = savingsAccounts;
    }

    @Transactional(readOnly = true)
    public List<Customer> customers() {
        return customers.findAll();
    }

    /**
     * The customer who gave this address, or nothing at all if no customer did.
     *
     * <p>Recognising somebody is not the same as letting them in, and this method does only the
     * first. Nothing is checked beyond the address — there is no password to check — so what comes
     * back is "this address belongs to a customer we have", and a caller that treats that as proof
     * of who is at the keyboard is deciding something this module never said.
     */
    @Transactional(readOnly = true)
    public Optional<Customer> customerIdentifiedBy(String contactDetails) {
        return customers.findByContactDetailsIgnoreCase(contactDetails.trim());
    }

    /**
     * Who holds the given savings account, or nothing at all if there is no such account.
     *
     * <p>The holder rather than the customer record: whoever asks wants to say whose account this is
     * and to name the customer whose points a deposit into it earns, and a module that hands out its
     * entities to be read elsewhere has no boundary left to speak of.
     */
    @Transactional(readOnly = true)
    public Optional<AccountHolder> holderOfSavingsAccount(long savingsAccountId) {
        return savingsAccounts.findHolderById(savingsAccountId);
    }

    /** Whether there is a customer with this identifier at all, for callers that only ask. */
    @Transactional(readOnly = true)
    public boolean customerExists(long customerId) {
        return customers.existsById(customerId);
    }

    /** Whether there is a savings account with this identifier at all, for callers that only ask. */
    @Transactional(readOnly = true)
    public boolean savingsAccountExists(long savingsAccountId) {
        return savingsAccounts.existsById(savingsAccountId);
    }

    /**
     * How to tell somebody that the savings account they named is not there, in words they can act
     * on. Every module that answers for a savings account has to say this at some point — reading
     * one, paying into one, spending what one has earned — and three modules wording it three ways
     * is three sentences that will drift apart. Accounts owns what a savings account is, so it owns
     * what its absence is called.
     *
     * <p>A sentence rather than a refusal, because who refuses and how it is reported differ: each
     * caller wraps this in its own module's refusal, and the web layer decides the status.
     */
    public static String noSuchSavingsAccount(long savingsAccountId) {
        return "There is no savings account " + savingsAccountId + ".";
    }

    /**
     * The same for a current account, which money moves into as well as out of. Both directions of a
     * transfer have to be able to say this, and a sentence written out in each of them is two
     * sentences one rewording away from disagreeing about what absence sounds like.
     */
    public static String noSuchCurrentAccount(long currentAccountId) {
        return "There is no current account " + currentAccountId + ".";
    }

    /**
     * And for a customer, which anything kept per customer has to be able to say. Points are kept
     * that way and rewards are claimed that way, so the sentence would otherwise be written out in
     * the Rewards module as well as here.
     */
    public static String noSuchCustomer(long customerId) {
        return "There is no customer " + customerId + ".";
    }

    /**
     * How the two accounts a transfer would run between stand to each other: whether each one is
     * real, and whether one customer holds both. Money moves only across the last of those, because
     * a savings account is funded from its own holder's money and from nobody else's.
     *
     * <p>Every way the pair can be wrong comes back distinguished, so the caller can say which it
     * was rather than only that something was. Whoever asks decides what to do about it; this module
     * only knows who holds what.
     */
    @Transactional(readOnly = true)
    public AccountPairing pairingFor(long savingsAccountId, long currentAccountId) {
        Optional<Long> saver = savingsAccounts.findHolderIdById(savingsAccountId);
        if (saver.isEmpty()) {
            return AccountPairing.NO_SUCH_SAVINGS_ACCOUNT;
        }
        Optional<Long> payer = currentAccounts.findHolderIdById(currentAccountId);
        if (payer.isEmpty()) {
            return AccountPairing.NO_SUCH_CURRENT_ACCOUNT;
        }
        return saver.equals(payer)
                ? AccountPairing.HELD_BY_ONE_CUSTOMER
                : AccountPairing.HELD_BY_DIFFERENT_CUSTOMERS;
    }

    /**
     * Takes an amount out of a current account, and answers whether there was enough to take.
     * Nothing leaves unless the whole amount can.
     *
     * <p>Taken rather than checked and then taken. "Can this account afford it" and "take it" as two
     * questions is a gap between them, and the gap is where the same money gets spent twice; asked
     * as one, the balance that was tested is the balance that changed.
     *
     * <p>Why it could not be taken is not reported: the caller knows which account it named, and
     * {@link #balanceOfCurrentAccount} says what is in it. What to tell the person who asked is for
     * whoever was moving the money, who is the only one who knows what they were moving it for.
     *
     * @throws IllegalArgumentException if asked to withdraw nothing or less, which is a mistake in
     *                                  the caller rather than a refusal to report to anybody
     */
    @Transactional
    public boolean withdrawFrom(long currentAccountId, BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "an amount to withdraw has to be more than zero, was " + amount.toPlainString());
        }
        return currentAccounts.findById(currentAccountId)
                .map(account -> {
                    boolean taken = account.withdraw(amount);
                    if (taken) {
                        // The account is managed and would be written out at the end of the
                        // transaction anyway. Saying so leaves nothing for a reader to infer from
                        // Hibernate's behaviour.
                        currentAccounts.save(account);
                    }
                    return taken;
                })
                .orElse(false);
    }

    /**
     * Puts money into a current account after another module has established that it belongs to the
     * customer making the transfer. The pairing check and the movement rule belong to that module;
     * Accounts only keeps this account's stored balance truthful.
     */
    @Transactional
    public void depositInto(long currentAccountId, BigDecimal amount) {
        CurrentAccount account = currentAccounts.findById(currentAccountId)
                .orElseThrow(() -> new IllegalArgumentException("no current account " + currentAccountId));
        account.deposit(amount);
        currentAccounts.save(account);
    }

    /** What is in a current account, or nothing at all if there is no such account. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> balanceOfCurrentAccount(long currentAccountId) {
        return currentAccounts.findById(currentAccountId).map(CurrentAccount::getBalance);
    }

    /**
     * What the given customer holds, or nothing at all if there is no such customer. The two cases
     * the caller has to tell apart are exactly these: a customer nobody has heard of, and a customer
     * who happens to hold no accounts — the second is an answer, and comes back with empty lists.
     */
    @Transactional(readOnly = true)
    public Optional<CustomerAccounts> accountsOf(long customerId) {
        if (!customers.existsById(customerId)) {
            return Optional.empty();
        }
        return Optional.of(new CustomerAccounts(
                currentAccounts.findByCustomerId(customerId),
                savingsAccounts.findByCustomerId(customerId)));
    }
}
