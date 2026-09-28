package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;

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

    /** Which customer this is, for anything the application keeps per customer — their points. */
    public long customerIdOf(String customerName) {
        return customerNamed(customerName).id();
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

    /** Every savings account the customer holds, for tests about what the whole of their saving did. */
    public List<Long> savingsAccountsOf(String customerName) {
        return accountsOf(customerName).savingsAccounts().stream().map(SavingsAccountView::id).toList();
    }

    /**
     * What the customer has to spend. Read off the overview, which is where the figure belongs: the
     * points are the customer's, not any one savings account's.
     */
    public long pointsBalanceOf(String customerName) {
        return accountsOf(customerName).pointsBalance();
    }

    /**
     * The most the customer has ever had in savings: the mark their next deposit is judged against.
     * Read off the overview, which is where the figure belongs — the mark runs against everything
     * they hold rather than against any one account.
     */
    public BigDecimal mostEverSavedOf(String customerName) {
        return accountsOf(customerName).mostEverSaved();
    }

    /**
     * Everything the customer currently holds in savings, across every account of theirs.
     *
     * <p>Summed here rather than asked for, because no endpoint answers it: the mark is the
     * customer's and the balances are their accounts', and putting the two side by side is what says
     * how far below their best they are.
     */
    public BigDecimal stillSavedBy(String customerName) {
        return accountsOf(customerName).savingsAccounts().stream()
                .map(SavingsAccountView::moneyBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Pays in whatever it takes to put the customer's savings back at the most they have ever held,
     * and answers nothing, because a test that calls this is not measuring it.
     *
     * <p>Tests share one database, and an earlier test that withdrew leaves the next deposit filling
     * a gap rather than saving anything new — which earns nothing, and rightly. A test measuring
     * what a deposit earns has to start from savings at their peak, or it is measuring the order the
     * test classes happened to run in.
     *
     * <p>Nothing at all is paid in for a customer already at their peak, which is the ordinary case:
     * a deposit of nothing is refused, and rightly, so it is not made.
     */
    public void savingsBackAtTheirPeak(long savingsAccountId, String customerName) {
        BigDecimal gap = mostEverSavedOf(customerName).subtract(stillSavedBy(customerName));
        if (gap.signum() <= 0) {
            return;
        }
        ResponseEntity<String> filled = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", gap.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        "fromCurrentAccountId", currentAccountOf(customerName)),
                String.class, savingsAccountId);
        assertThat(filled.getStatusCode())
                .describedAs("topping " + customerName + "'s savings back up to EUR "
                        + mostEverSavedOf(customerName) + " before measuring what a deposit earns")
                .isEqualTo(HttpStatus.CREATED);
    }

    public long currentAccountOf(String customerName) {
        return currentAccountsOf(customerName).get(0);
    }

    /**
     * How many of the customer's points are the next to expire, and null when they have none left to
     * lose. Read off the overview, which is where the figure belongs: the twelve months run against
     * the customer's points rather than against any one account's saving.
     */
    public Long pointsExpiringNextOf(String customerName) {
        return accountsOf(customerName).pointsExpiringNext();
    }

    /** The day those points go, and null when there are none. */
    public LocalDate pointsExpiringNextOnOf(String customerName) {
        return accountsOf(customerName).pointsExpiringNextOn();
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

    /**
     * An identifier no customer has: one past the highest that does. For tests about being told that
     * the customer something was asked for is not one this application has heard of.
     */
    public long anIdNoCustomerHas() {
        return Arrays.stream(read("/api/customers", CustomerView[].class))
                .mapToLong(CustomerView::id)
                .max()
                .orElse(0L) + 1;
    }

    /**
     * Walks every customer rather than only the seeded two, because a customer added by a test is
     * a customer whose accounts are numbered above theirs. Asking the seeded pair alone would
     * answer with an identifier that an added customer's account already holds, and the test that
     * asked for one no account has would quietly be handed one that exists — a failure in whichever
     * test happened to run second, about a row it never created.
     */
    private long oneMoreThanTheHighestOf(Function<String, List<Long>> accountsOfCustomer) {
        return Arrays.stream(read("/api/customers", CustomerView[].class))
                .map(CustomerView::name)
                .flatMap(customer -> accountsOfCustomer.apply(customer).stream())
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L) + 1;
    }

    private AccountsView accountsOf(String customerName) {
        return read("/api/customers/{id}/accounts", AccountsView.class, customerNamed(customerName).id());
    }

    private CustomerView customerNamed(String name) {
        return Arrays.stream(read("/api/customers", CustomerView[].class))
                .filter(customer -> name.equals(customer.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no seeded customer named " + name));
    }

    /**
     * A read that says what actually came back when it could not be read as what was asked for.
     *
     * <p>{@link TestRestTemplate} deliberately does not throw on an error status, so an application
     * that answered one of these lookups with an error hands the body to Jackson instead, and the
     * test fails with "error while extracting response for type CustomerView[]" — which names the
     * type it was trying to read and nothing at all about what went wrong. Every lookup in this class
     * comes through here, so this is the one place worth explaining.
     *
     * <p>The second request is made only to explain the first, so the ordinary path is still one
     * request. If that second read succeeds, the message says so, which is the most useful thing it
     * could say: the first failure was then transient, and a reader is looking for a race rather than
     * for a broken endpoint.
     *
     * <p>Nothing is retried into a pass. A read that fails mid-test is something the application did,
     * and a test that quietly had another go would be hiding it.
     */
    private <T> T read(String path, Class<T> shape, Object... variables) {
        try {
            return http.getForObject(path, shape, variables);
        } catch (RestClientException couldNotBeReadAsThat) {
            ResponseEntity<String> raw = http.getForEntity(path, String.class, variables);
            throw new AssertionError("GET " + path + " " + Arrays.toString(variables)
                    + " could not be read as " + shape.getSimpleName() + ". Asked again straight "
                    + "away, it answered " + raw.getStatusCode() + " and this body: " + raw.getBody(),
                    couldNotBeReadAsThat);
        }
    }

    record CustomerView(Long id, String name, String contactDetails) {
    }

    record AccountsView(long pointsBalance, BigDecimal mostEverSaved,
                        Long pointsExpiringNext, LocalDate pointsExpiringNextOn,
                        List<CurrentAccountView> currentAccounts,
                        List<SavingsAccountView> savingsAccounts) {
    }

    record CurrentAccountView(Long id, String iban, BigDecimal balance) {
    }

    record SavingsAccountView(Long id, BigDecimal moneyBalance) {
    }
}
