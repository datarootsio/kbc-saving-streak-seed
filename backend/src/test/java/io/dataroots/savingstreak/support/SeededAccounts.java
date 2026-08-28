package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * Finds the seeded customers' accounts over the API, so that a test about depositing can start from
 * "an account to deposit into" instead of from the customer list. It drives the same endpoints a
 * frontend does, so no test using it knows anything about the database underneath.
 */
public class SeededAccounts {

    public static final String ANKE = "Anke Peeters";
    public static final String BRAM = "Bram De Vos";

    private final TestRestTemplate http;

    public SeededAccounts(TestRestTemplate http) {
        this.http = http;
    }

    /** The first savings account the customer holds, for tests that just need somewhere to save. */
    public long savingsAccountOf(String customerName) {
        return savingsAccountsOf(customerName).get(0);
    }

    /**
     * A second savings account the same customer holds. Someone saving towards two goals at once is
     * the only way to ask whether the two stay apart.
     */
    public long otherSavingsAccountOf(String customerName) {
        List<Long> savingsAccounts = savingsAccountsOf(customerName);
        if (savingsAccounts.size() < 2) {
            throw new AssertionError(customerName + " holds one savings account, and telling two of "
                    + "them apart needs two");
        }
        return savingsAccounts.get(1);
    }

    private List<Long> savingsAccountsOf(String customerName) {
        return accountsOf(customerName).savingsAccounts().stream().map(SavingsAccountView::id).toList();
    }

    public long currentAccountOf(String customerName) {
        return currentAccountsOf(customerName).get(0);
    }

    /**
     * What is in that current account right now. Read fresh on every call, because the run shares
     * one database and a deposit made by any test has taken money out of it.
     */
    public BigDecimal currentAccountBalanceOf(String customerName) {
        return accountsOf(customerName).currentAccounts().get(0).balance();
    }

    /** What the customer would sign in with, for tests about signing in as somebody who exists. */
    public String contactDetailsOf(String customerName) {
        return customerNamed(customerName).contactDetails();
    }

    private List<Long> currentAccountsOf(String customerName) {
        return accountsOf(customerName).currentAccounts().stream().map(CurrentAccountView::id).toList();
    }

    /**
     * An identifier no savings account has: one past the highest that does, and savings accounts are
     * numbered from a single sequence. For tests about being told that something does not exist.
     */
    public long anIdNoSavingsAccountHas() {
        return oneMoreThanTheHighestOf(this::savingsAccountsOf);
    }

    /** An identifier no current account has, arrived at the same way. */
    public long anIdNoCurrentAccountHas() {
        return oneMoreThanTheHighestOf(this::currentAccountsOf);
    }

    private long oneMoreThanTheHighestOf(Function<String, List<Long>> accountsOfCustomer) {
        return Stream.of(ANKE, BRAM)
                .flatMap(customer -> accountsOfCustomer.apply(customer).stream())
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L) + 1;
    }

    private AccountsView accountsOf(String customerName) {
        return http.getForObject(
                "/api/customers/{id}/accounts", AccountsView.class, customerNamed(customerName).id());
    }

    private CustomerView customerNamed(String name) {
        return Arrays.stream(http.getForObject("/api/customers", CustomerView[].class))
                .filter(customer -> name.equals(customer.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no seeded customer named " + name));
    }

    record CustomerView(Long id, String name, String contactDetails) {
    }

    record AccountsView(List<CurrentAccountView> currentAccounts, List<SavingsAccountView> savingsAccounts) {
    }

    record CurrentAccountView(Long id, String iban, BigDecimal balance) {
    }

    record SavingsAccountView(Long id, BigDecimal moneyBalance, long pointsBalance) {
    }
}
