package io.dataroots.savingstreak.customeraccounts;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Signing in shows what that customer holds: the current accounts a deposit can draw from, each named
 * by its IBAN and worth what is left in it, the savings accounts the money goes into, each worth what
 * it holds, the points all of that saving has earned them, and how the saving is going — the week
 * they are part-way through and the run of weeks behind it.
 *
 * <p>The points, the week and the run sit beside the two lists rather than inside either of them,
 * because they are the customer's: one set of figures for the person, however many accounts they
 * save into.
 *
 * <p>The expected IBANs are the seeded ones. A test that only checked the shape of the field would
 * pass just as happily on an account that had lost track of which IBAN was its own.
 */
class CustomerAccountsApiTest extends ApiIntegrationTest {

    private static final String ANKE = "Anke Peeters";
    private static final String ANKES_IBAN = "BE68539007547034";
    private static final String BRAM = "Bram De Vos";
    private static final String BRAMS_IBAN = "BE87734291658494";

    record AccountsView(long pointsBalance,
                        BigDecimal newSavingsThisWeek, BigDecimal weeklyMinimum,
                        BigDecimal stillNeededThisWeek,
                        int currentStreakWeeks, int bestStreakWeeks, BigDecimal currentMultiplier,
                        List<CurrentAccountView> currentAccounts,
                        List<SavingsAccountView> savingsAccounts) {
    }

    record CurrentAccountView(Long id, String iban, BigDecimal balance) {
    }

    record SavingsAccountView(Long id, BigDecimal moneyBalance) {
    }

    record CustomerView(Long id, String name, String contactDetails) {
    }

    @Test
    void a_customers_accounts_are_the_current_accounts_and_savings_accounts_they_hold() {
        ResponseEntity<AccountsView> response = accountsOf(idOfCustomerNamed(ANKE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().currentAccounts()).singleElement().satisfies(current -> {
            assertThat(current.id()).isNotNull();
            assertThat(current.iban()).isEqualTo(ANKES_IBAN);
        });
        // Two of them: this customer is seeded saving towards two goals, and each is its own account.
        assertThat(response.getBody().savingsAccounts())
                .hasSize(2)
                .allSatisfy(savings -> assertThat(savings.id()).isNotNull())
                .extracting(SavingsAccountView::id)
                .doesNotHaveDuplicates();
    }

    @Test
    void the_accounts_of_a_customer_that_does_not_exist_are_not_found() {
        ResponseEntity<AccountsView> response = accountsOf(anIdNoCustomerHas());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void each_customer_holds_their_own_accounts_and_none_of_anybody_elses() {
        AccountsView ankes = accountsOf(idOfCustomerNamed(ANKE)).getBody();
        AccountsView brams = accountsOf(idOfCustomerNamed(BRAM)).getBody();

        assertThat(brams.currentAccounts())
                .singleElement()
                .satisfies(current -> assertThat(current.iban()).isEqualTo(BRAMS_IBAN));
        assertThat(brams.savingsAccounts()).hasSize(1);
        assertThat(currentAccountIdsIn(brams)).doesNotContainAnyElementsOf(currentAccountIdsIn(ankes));
        assertThat(savingsAccountIdsIn(brams)).doesNotContainAnyElementsOf(savingsAccountIdsIn(ankes));
    }

    /**
     * A current account carries the money in it, because a customer looking at what they hold is
     * looking for exactly that figure. It is a real amount rather than a placeholder: it is what a
     * deposit is taken out of, and what a deposit too large for it is refused against.
     */
    @Test
    void a_current_account_carries_the_money_that_is_in_it() {
        AccountsView accounts = accountsOf(idOfCustomerNamed(ANKE)).getBody();

        assertThat(accounts.currentAccounts()).singleElement().satisfies(
                current -> assertThat(current.balance()).isNotNull().isGreaterThan(BigDecimal.ZERO));
    }

    /**
     * The overview reports the same figures as the savings account's own endpoint does: the money in
     * the account, and the points its holder has to spend. Two places that answer the same question
     * are two places that can disagree, and a customer comparing the number on the overview with the
     * number on the account is exactly who would notice. Asserted after a deposit, so both figures
     * have had to move to stay equal.
     *
     * <p>Anke's, not Bram's: no test in this run pays into Bram's savings account successfully, and
     * a test elsewhere reads it as the account with an empty history.
     */
    @Test
    void an_overview_is_worth_the_same_as_the_savings_accounts_own_page() {
        Long customerId = idOfCustomerNamed(ANKE);
        AccountsView before = accountsOf(customerId).getBody();
        long savingsAccountId = before.savingsAccounts().get(0).id();

        http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", "18.40", "fromCurrentAccountId", before.currentAccounts().get(0).id()),
                String.class,
                savingsAccountId);

        AccountsView after = accountsOf(customerId).getBody();
        BalancesView onItsOwnPage = http.getForObject(
                "/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
        assertThat(after.savingsAccounts().get(0).moneyBalance())
                .isEqualByComparingTo(onItsOwnPage.moneyBalance());
        // The customer's own figures, reported once on the overview and again beside the account
        // whose deposit just moved them. Every one of them, because every one of them is the
        // customer's and a page comparing two of them is a page that can catch either drifting.
        assertThat(after.pointsBalance()).isEqualTo(onItsOwnPage.pointsBalance());
        assertThat(after.newSavingsThisWeek()).isEqualByComparingTo(onItsOwnPage.newSavingsThisWeek());
        assertThat(after.weeklyMinimum()).isEqualByComparingTo(onItsOwnPage.weeklyMinimum());
        assertThat(after.stillNeededThisWeek())
                .isEqualByComparingTo(onItsOwnPage.stillNeededThisWeek());
        assertThat(after.currentStreakWeeks()).isEqualTo(onItsOwnPage.currentStreakWeeks());
        assertThat(after.bestStreakWeeks()).isEqualTo(onItsOwnPage.bestStreakWeeks());
        assertThat(after.currentMultiplier()).isEqualByComparingTo(onItsOwnPage.currentMultiplier());
    }

    /**
     * The overview's weekly figures agree with each other and with what the week asks for: what has
     * landed, the minimum, and the gap between them — the same three the savings account's own page
     * reports, and the reason the page never writes the EUR 50 into its own markup.
     */
    @Test
    void the_overview_says_what_the_week_asks_for_and_how_far_along_it_is() {
        AccountsView accounts = accountsOf(idOfCustomerNamed(ANKE)).getBody();

        assertThat(accounts.weeklyMinimum()).isGreaterThan(BigDecimal.ZERO);
        assertThat(accounts.newSavingsThisWeek()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(accounts.stillNeededThisWeek()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        // A run happening now is a run that has happened, so the record is never the smaller figure.
        assertThat(accounts.bestStreakWeeks()).isGreaterThanOrEqualTo(accounts.currentStreakWeeks());
        // The ordinary rate at worst: a run pays more per euro and never less.
        assertThat(accounts.currentMultiplier()).isGreaterThanOrEqualTo(BigDecimal.ONE);
    }

    private ResponseEntity<AccountsView> accountsOf(Long customerId) {
        return http.getForEntity("/api/customers/{id}/accounts", AccountsView.class, customerId);
    }

    private Long idOfCustomerNamed(String name) {
        return everyCustomer().stream()
                .filter(customer -> name.equals(customer.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no seeded customer named " + name))
                .id();
    }

    /** Larger than every identifier the database has handed out, so nothing can have claimed it. */
    private Long anIdNoCustomerHas() {
        return everyCustomer().stream().mapToLong(CustomerView::id).max().orElse(0L) + 1;
    }

    private List<CustomerView> everyCustomer() {
        return Arrays.asList(http.getForObject("/api/customers", CustomerView[].class));
    }

    /** The two kinds are compared separately: they are numbered from separate sequences. */
    private List<Long> currentAccountIdsIn(AccountsView accounts) {
        return accounts.currentAccounts().stream().map(CurrentAccountView::id).toList();
    }

    private List<Long> savingsAccountIdsIn(AccountsView accounts) {
        return accounts.savingsAccounts().stream().map(SavingsAccountView::id).toList();
    }
}
