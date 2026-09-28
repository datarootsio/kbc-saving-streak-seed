package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.TheNoticeOnAnAccountView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The other half of the ticket, and the half a reviewer should read first: an account whose
 * agreement asks for no notice is refused absolutely nothing, and behaves exactly as it did before
 * any of this existed.
 *
 * <p><strong>On the shared application and the shared database, deliberately.</strong> Every other
 * test in this run is banking on these accounts, and this one asserts that the condition on the way
 * out changed nothing for them. Booting an application of its own would have proved it about an
 * account nobody else uses; proving it here proves it about the accounts a hundred other tests
 * depend on.
 *
 * <p>Nothing here winds a clock, and nothing needs to: an account with nought notice days is
 * answered before a single notice row is read, on whatever day it happens to be.
 *
 * <p>A customer of this test's own for the withdrawal, because "there is money to pay in with and
 * nothing has claimed it" is a thing another test can have changed on the seeded pair.
 */
class AnAccountWithNothingToGiveNoticeOfIsRefusedNothingApiTest extends ApiIntegrationTest {

    /**
     * Free savings asks for nothing, and says so as figures rather than as an absence: nought days,
     * nought ready, nought waiting, no notices.
     *
     * <p>Answered rather than refused, which is the decision worth the test. A screen asking about
     * an account it has just opened is entitled to an answer, and nought days is what tells it to
     * draw no panel at all — an endpoint that refused would make every page ask what kind of
     * account it was holding before it dared ask this.
     */
    @Test
    void an_account_with_no_notice_period_reads_as_nought_everywhere() {
        long savingsAccountId = theFirstSavingsAccountOf(ANKE);

        TheNoticeOnAnAccountView standing = theNoticeOn(savingsAccountId);

        assertThat(standing.savingsAccountId()).isEqualTo(savingsAccountId);
        assertThat(standing.noticeDays()).isZero();
        assertThat(standing.readyToTakeToday()).isEqualByComparingTo("0.00");
        assertThat(standing.stillWaiting()).isEqualByComparingTo("0.00");
        assertThat(standing.notices()).isEmpty();
    }

    /**
     * Money still comes straight back out of free savings, with no notice given and none to give.
     *
     * <p>This is the regression the whole slice is judged by. A condition on the way out that
     * accidentally applied to an account with no condition would break every account this
     * application has ever opened, and it would break them silently — the withdrawal would simply
     * stop working.
     */
    @Test
    void money_comes_straight_back_out_of_an_account_with_no_notice_period() {
        TheirAccounts theirs = openACustomer("somebody with nothing to give notice of");
        deposit(theirs, "120.00");

        ResponseEntity<JsonNode> taken = http.postForEntity(
                "/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", "40.00", "toCurrentAccountId", theirs.currentAccountId()),
                JsonNode.class, theirs.savingsAccountId());

        assertThat(taken.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(theNoticeOn(theirs.savingsAccountId()).notices()).isEmpty();
    }

    /**
     * Giving notice on an account with nothing to give notice of is refused in a sentence that is
     * good news.
     *
     * <p>Refused rather than quietly accepted, because a notice recorded against an account that
     * pays no attention to notices would leave somebody waiting thirty-two days for money that was
     * already theirs. A bad request rather than a conflict: nothing is in an unexpected state, the
     * account is exactly what it has always been, and what they do next is take the money out.
     */
    @Test
    void giving_notice_on_an_account_with_nothing_to_give_notice_of_is_refused_in_words() {
        long savingsAccountId = theFirstSavingsAccountOf(ANKE);

        ResponseEntity<JsonNode> refused = http.postForEntity(
                "/api/savings-accounts/{id}/notices", Map.of("amount", "25.00"), JsonNode.class,
                savingsAccountId);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().path("detail").asText())
                .isEqualTo("That savings account has nothing to give notice of — money leaves it "
                        + "whenever you like.");
        assertThat(theNoticeOn(savingsAccountId).notices()).isEmpty();
    }

    /**
     * And a savings account nobody has heard of is an absence, answered in the words Accounts owns
     * for one rather than with an empty reading.
     */
    @Test
    void notice_on_a_savings_account_nobody_has_heard_of_is_not_found() {
        ResponseEntity<JsonNode> refused = http.getForEntity(
                "/api/savings-accounts/{id}/notices", JsonNode.class, 9_876_543L);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().path("detail").asText()).contains("9876543");
    }

    private TheNoticeOnAnAccountView theNoticeOn(long savingsAccountId) {
        return http.getForObject("/api/savings-accounts/{id}/notices",
                TheNoticeOnAnAccountView.class, savingsAccountId);
    }

    private void deposit(TheirAccounts theirs, String amount) {
        ResponseEntity<JsonNode> made = http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", theirs.currentAccountId()),
                JsonNode.class, theirs.savingsAccountId());
        assertThat(made.getStatusCode()).as("paying in EUR %s", amount)
                .isEqualTo(HttpStatus.CREATED);
    }

    private TheirAccounts openACustomer(String name) {
        ResponseEntity<CustomerView> added = http.postForEntity("/api/customers",
                Map.of("name", name, "contactDetails", name.replace(' ', '.') + "@example.be"),
                CustomerView.class);
        assertThat(added.getStatusCode()).as("opening %s", name).isEqualTo(HttpStatus.CREATED);
        OverviewView theirs = http.getForObject("/api/customers/{id}/accounts", OverviewView.class,
                added.getBody().id());
        return new TheirAccounts(theirs.savingsAccounts().get(0).id(),
                theirs.currentAccounts().get(0).id());
    }

    private long theFirstSavingsAccountOf(String customerName) {
        long customerId = List.of(http.getForObject("/api/customers", CustomerView[].class)).stream()
                .filter(customer -> customerName.equals(customer.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no customer named " + customerName))
                .id();
        return http.getForObject("/api/customers/{id}/accounts", OverviewView.class, customerId)
                .savingsAccounts().get(0).id();
    }

    private record TheirAccounts(long savingsAccountId, long currentAccountId) {
    }

    private record CustomerView(Long id, String name, String contactDetails) {
    }

    private record OverviewView(List<AnAccountView> currentAccounts,
                                List<AnAccountView> savingsAccounts) {
    }

    private record AnAccountView(Long id, BigDecimal moneyBalance) {
    }
}
