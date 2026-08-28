package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the customers a trainer demonstrates against, each with an account to deposit from and at
 * least one account to deposit into, so the app is useful seconds after it starts. Seeding only
 * happens when there are no customers at all, so restarting the app does not multiply them; deleting
 * the database file and starting again returns the app to exactly this state.
 */
@Component
@Profile("dev")
class DemoData implements CommandLineRunner {

    private final CustomerRepository customers;
    private final CurrentAccountRepository currentAccounts;
    private final SavingsAccountRepository savingsAccounts;

    DemoData(CustomerRepository customers,
             CurrentAccountRepository currentAccounts,
             SavingsAccountRepository savingsAccounts) {
        this.customers = customers;
        this.currentAccounts = currentAccounts;
        this.savingsAccounts = savingsAccounts;
    }

    /**
     * Spring calls this through the bean's proxy, so the check and the inserts genuinely share one
     * transaction. Splitting the work into a @Transactional method called from here would not:
     * self-invocation bypasses the proxy and the annotation would do nothing.
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (customers.count() > 0) {
            return;
        }
        // Anke saves towards two goals at once, so a demo can show that a deposit into one savings
        // account leaves the other where it was. Her current account is the deeper of the two, so
        // that a session of demonstrating deposits does not run it dry.
        seed("Anke Peeters", "anke.peeters@example.be", "BE68539007547034", "2480.00", 2);
        // Bram's is deliberately shallower: a deposit bigger than this is refused, and being told so
        // is part of what there is to demonstrate.
        seed("Bram De Vos", "bram.devos@example.be", "BE87734291658494", "1150.00", 1);
    }

    private void seed(String name, String contactDetails, String iban, String openingBalance,
                      int savingsAccountsHeld) {
        Customer customer = customers.save(new Customer(name, contactDetails));
        currentAccounts.save(new CurrentAccount(customer, iban, new BigDecimal(openingBalance)));
        for (int i = 0; i < savingsAccountsHeld; i++) {
            savingsAccounts.save(new SavingsAccount(customer));
        }
    }
}
