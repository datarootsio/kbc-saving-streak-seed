package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SavingsAccountOnTheOverviewView;
import io.dataroots.savingstreak.support.SavingsProductView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A savings account stops being a shape nobody chose: it names the product it is on, the version of
 * that product's terms it was opened under, and the day it was opened on them — and it says so on
 * its own page, on the overview beside every other account, and on every deposit that lands in it.
 *
 * <p><strong>What an account is on is not what its product is selling.</strong> Free savings has
 * published a second version, and the two are deliberately different answers from two endpoints.
 * This test asserts the case where they agree — an account opened this morning is on the terms
 * being sold this morning — and the migration test asserts the case where they must not, which is
 * an account that was already open before the second version existed.
 *
 * <p>The shared application and the shared database, because nothing here winds a clock or counts a
 * run of weeks. A customer of this test's own is opened all the same, for the two assertions that
 * are about a particular day and a particular current account: the seeded pair are shared with a
 * hundred other classes, and "opened today" and "there is money to pay in with" are both things
 * another test can have changed.
 */
class WhatASavingsAccountIsLivingUnderApiTest extends ApiIntegrationTest {

    /** The zone this application counts its days in, which is the zone an agreement is dated in. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    private static long theirSavingsAccount;
    private static long theirCurrentAccount;
    private static LocalDate theDayTheyOpenedIt;

    /**
     * The account's own page says what it is living under, in the four words a customer would use:
     * the product, the version, the day, and what the product asks of them.
     *
     * <p>Free savings asks for nothing, which is why the three conditions read as absences — no
     * notice to give, no floor to keep, and no day it matures on. That is the reading a page draws
     * "money in and out whenever you like" from, and it is asserted here rather than left implied,
     * because three absences said three different ways would be three things for a screen to get
     * wrong.
     */
    @Test
    void a_savings_account_names_the_product_and_the_version_it_is_on() {
        long savingsAccountId = theFirstSavingsAccountOf(ANKE);

        AnAgreementView agreement = theAccount(savingsAccountId).agreement();

        assertThat(agreement).isNotNull();
        assertThat(agreement.productCode()).isEqualTo("INSTANT");
        assertThat(agreement.productName()).isEqualTo("Free savings");
        assertThat(agreement.productKind()).isEqualTo("INSTANT_ACCESS");
        assertThat(agreement.version()).isPositive();
        assertThat(agreement.openedOn()).isNotNull();
        assertThat(agreement.noticeDays()).isZero();
        assertThat(agreement.minimumBalance()).isEqualByComparingTo("0.00");
        assertThat(agreement.maturesOn()).isNull();
    }

    /**
     * An account opened today is dated today and is on the version being sold today, which is the
     * second one.
     *
     * <p>Asserted across two endpoints so that they cannot quietly disagree: the catalogue says
     * which version free savings is offering, the account says which version it is on, and somebody
     * who opened an account this morning is on the terms the bank is selling this morning. The
     * alternative — putting every new account on version 1 — would be the bank writing somebody an
     * agreement it stopped offering two months ago.
     *
     * <p>The day is the application's own, read off the clock it is standing on rather than off the
     * machine, because everything else about this application's calendar is.
     */
    @Test
    void an_account_opened_today_is_dated_today_and_on_the_version_being_sold_today() {
        LocalDate beforeTheyOpenedIt = theDayTheApplicationIsStandingOn();
        long savingsAccountId = theirSavingsAccount();
        LocalDate afterTheyOpenedIt = theDayTheApplicationIsStandingOn();

        AnAgreementView agreement = theAccount(savingsAccountId).agreement();

        assertThat(agreement.version()).isEqualTo(whatFreeSavingsIsSellingToday());
        // A pair of readings rather than one, because a test that opened an account a millisecond
        // before midnight would otherwise fail for the one reason nobody could reproduce.
        assertThat(agreement.openedOn()).isIn(beforeTheyOpenedIt, afterTheyOpenedIt);
    }

    /**
     * The overview says which product each savings account is on, beside what each one holds.
     *
     * <p>On the list rather than only on the page behind it, because the list is where somebody
     * holding two accounts finds out whether they are the same kind of account — and from the day a
     * notice account exists, "which of these can I take money out of today" is a question this
     * screen has to be able to answer without being opened.
     */
    @Test
    void the_overview_says_which_product_each_savings_account_is_on() {
        List<SavingsAccountOnTheOverviewView> held = theOverviewOf(ANKE).savingsAccounts();

        assertThat(held).hasSizeGreaterThan(1);
        assertThat(held).allSatisfy(account -> {
            assertThat(account.productCode()).isEqualTo("INSTANT");
            assertThat(account.productName()).isEqualTo("Free savings");
        });
    }

    /**
     * A deposit is stamped with the version its account was on when the money arrived, and says so
     * both in the answer to the deposit and in the history afterwards.
     *
     * <p>Both, and asserted together, for the reason the rate beside it is: a deposit just made and
     * the same deposit read back have to say the same thing, and one code path is what makes that
     * true rather than hoped for. The version is read off the account's own agreement rather than
     * written into this test, so this stays an assertion about the two agreeing on the day an
     * account is opened on something else.
     */
    @Test
    void a_deposit_says_which_version_of_the_terms_it_landed_under() {
        long savingsAccountId = theirSavingsAccount();
        int whatTheAccountIsOn = theAccount(savingsAccountId).agreement().version();

        DepositView made = deposit(savingsAccountId, "25.00");

        assertThat(made.termsVersion()).isEqualTo(whatTheAccountIsOn);
        assertThat(theHistoryOf(savingsAccountId))
                .filteredOn(row -> row.id().equals(made.id()))
                .singleElement()
                .satisfies(row -> assertThat(row.termsVersion()).isEqualTo(whatTheAccountIsOn));
    }

    /** And every row in the history says it, not only the newest one. */
    @Test
    void every_deposit_in_the_history_says_what_it_landed_under() {
        long savingsAccountId = theirSavingsAccount();
        deposit(savingsAccountId, "30.00");

        assertThat(theHistoryOf(savingsAccountId)).isNotEmpty();
        assertThat(theHistoryOf(savingsAccountId))
                .allSatisfy(row -> assertThat(row.termsVersion()).isNotNull());
    }

    private BalancesView theAccount(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
    }

    private List<DepositView> theHistoryOf(long savingsAccountId) {
        return List.of(http.getForObject("/api/savings-accounts/{id}/deposits",
                DepositView[].class, savingsAccountId));
    }

    private DepositView deposit(long savingsAccountId, String amount) {
        ResponseEntity<DepositView> made = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", theirCurrentAccount),
                DepositView.class, savingsAccountId);
        assertThat(made.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return made.getBody();
    }

    /**
     * Which version free savings is offering today, asked of the catalogue rather than written down
     * here. The seed dates the second version two months before the file was made, so the figure is
     * two — but a test that said two would be asserting what the seed happens to write instead of
     * asserting that the two readings agree.
     */
    private int whatFreeSavingsIsSellingToday() {
        return http.getForObject("/api/savings-products/{code}", SavingsProductView.class, "INSTANT")
                .currentTerms().version();
    }

    private LocalDate theDayTheApplicationIsStandingOn() {
        return LocalDate.ofInstant(http.getForObject("/api/dev/clock", ClockView.class).now(),
                BRUSSELS);
    }

    /**
     * A customer this test opened for itself, and the savings account they were opened with.
     *
     * <p>Opened once and remembered, rather than once per test method: two customers would be two
     * accounts, and the second of them would be opened after the first had already been deposited
     * into — which is exactly the sort of thing that makes a figure in one test depend on the order
     * the methods ran in.
     */
    private long theirSavingsAccount() {
        if (theDayTheyOpenedIt == null) {
            String name = "somebody reading their agreement";
            ResponseEntity<CustomerView> added = http.postForEntity("/api/customers",
                    Map.of("name", name, "contactDetails", name.replace(' ', '.') + "@example.be"),
                    CustomerView.class);
            assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            OverviewView theirs = theOverviewOf(added.getBody().name());
            theirSavingsAccount = theirs.savingsAccounts().get(0).id();
            theirCurrentAccount = theirs.currentAccounts().get(0).id();
            theDayTheyOpenedIt = theDayTheApplicationIsStandingOn();
        }
        return theirSavingsAccount;
    }

    private long theFirstSavingsAccountOf(String customerName) {
        return theOverviewOf(customerName).savingsAccounts().get(0).id();
    }

    private OverviewView theOverviewOf(String customerName) {
        long customerId = List.of(http.getForObject("/api/customers", CustomerView[].class)).stream()
                .filter(customer -> customerName.equals(customer.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no customer named " + customerName))
                .id();
        return http.getForObject("/api/customers/{id}/accounts", OverviewView.class, customerId);
    }

    private record CustomerView(Long id, String name, String contactDetails) {
    }

    /**
     * Only the two lists, because that is all this test reads off the overview. The nine figures
     * beside them belong to the customer and have their own tests; naming them here would be a
     * second copy of a shape that already has one.
     */
    private record OverviewView(List<CurrentAccountView> currentAccounts,
                                List<SavingsAccountOnTheOverviewView> savingsAccounts) {
    }

    private record CurrentAccountView(Long id, String iban, BigDecimal balance) {
    }
}
