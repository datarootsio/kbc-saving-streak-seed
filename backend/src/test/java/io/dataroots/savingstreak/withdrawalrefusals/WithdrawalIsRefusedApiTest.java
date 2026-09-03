package io.dataroots.savingstreak.withdrawalrefusals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A withdrawal the application cannot make is refused with a reason, and nothing is moved.
 *
 * <p>The mirror of the deposit refusal test, and deliberately the same shape: a withdrawal is the
 * same movement turned around, so a participant who has read one of these two files has read both.
 * Every refusal is asserted three ways by {@link #assertRefused} and {@link #assertNothingMoved} —
 * the status, a problem document carrying words a person can read, and both balances plus the
 * records behind them exactly where they were. A refusal that took the money anyway would leave a
 * balance nobody could explain, and one that came back as a bare status would leave a person to
 * guess which of the things they typed was being objected to; neither is something a status code on
 * its own can rule out.
 *
 * <p>Anke's account is the one withdrawn from, and every test pays into it first. A refusal that
 * leaves a zero balance, no deposits and no points behind is not evidence of anything: there was
 * nothing there to move. Bram's savings account is deliberately not used — no test in the run
 * deposits into it successfully, and the deposit history test reads that emptiness.
 */
class WithdrawalIsRefusedApiTest extends ApiIntegrationTest {

    /** More than either seeded current account was opened with, so no run can make it affordable. */
    private static final String MORE_THAN_ANY_SEEDED_ACCOUNT_HOLDS = "1000000.00";

    private SeededAccounts seeded;
    private long savingsAccount;
    private long currentAccount;

    @BeforeEach
    void findTheSeededAccountsAndLeaveSomethingInThemToProtect() {
        seeded = new SeededAccounts(http);
        savingsAccount = seeded.savingsAccountOf(ANKE);
        currentAccount = seeded.currentAccountOf(ANKE);
        deposit("5.00");
        assertThat(balances().moneyBalance()).isGreaterThan(BigDecimal.ZERO);
    }

    /**
     * Taking money out of an account that does not exist is a mistake about which account, not about
     * the money, and it is reported as one.
     *
     * <p>The one refusal here that does not go on to assert nothing was recorded, because there are
     * no records to read: every endpoint that would report them answers not-found for this
     * identifier whatever happened, so the refusal itself has to carry the test.
     */
    @Test
    void a_withdrawal_from_a_savings_account_that_does_not_exist_is_not_found() {
        long noSuchAccount = seeded.anIdNoSavingsAccountHas();

        ResponseEntity<JsonNode> response = withdraw(noSuchAccount, currentAccount, "1.00");

        assertRefused(response, HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(response)).contains(String.valueOf(noSuchAccount));
    }

    /**
     * A destination nobody holds. Accepting it would move money out of a real savings account and
     * into no account at all, which is the one outcome a transfer must never have.
     */
    @Test
    void a_withdrawal_into_a_current_account_that_does_not_exist_is_not_found_and_moves_nothing() {
        long noSuchAccount = seeded.anIdNoCurrentAccountHas();
        Held before = whatIsHeld();

        ResponseEntity<JsonNode> response = withdraw(savingsAccount, noSuchAccount, "1.00");

        assertRefused(response, HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(response)).contains(String.valueOf(noSuchAccount));
        assertNothingMoved(before);
    }

    /**
     * The refusal the requirements are most emphatic about, in the direction that matters most: a
     * customer's savings must not be emptied into somebody else's account. Both accounts are real
     * and the amount is fine, so nothing but the pairing is wrong, which is the only way to be sure
     * it is the pairing that was refused.
     */
    @Test
    void a_withdrawal_into_another_customers_current_account_is_refused_and_moves_nothing() {
        Held before = whatIsHeld();

        ResponseEntity<JsonNode> response = withdraw(savingsAccount, seeded.currentAccountOf(BRAM), "1.00");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertNothingMoved(before);
    }

    /**
     * An amount that is not a number at all, which is what a decimal comma arrives as. The page
     * prints balances the Belgian way and sends back whatever was typed, so "25,00" is the mistake
     * somebody here is most likely to make, and it has to come back as a refusal about the amount
     * rather than as whatever the machinery says when it cannot read a request at all.
     */
    @Test
    void a_withdrawal_of_something_that_is_not_a_number_is_refused_and_moves_nothing() {
        Held before = whatIsHeld();

        ResponseEntity<JsonNode> response = withdraw(savingsAccount, currentAccount, "25,00");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("25,00");
        assertNothingMoved(before);
    }

    /** A withdrawal of nothing is not a withdrawal, and saying so is cheaper than recording one. */
    @Test
    void a_withdrawal_of_zero_is_refused_and_moves_nothing() {
        Held before = whatIsHeld();

        ResponseEntity<JsonNode> response = withdraw(savingsAccount, currentAccount, "0.00");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("0.00");
        assertNothingMoved(before);
    }

    /**
     * A negative withdrawal is a deposit nobody asked for. Refused rather than turned around: a
     * minus sign is a typo far more often than it is an intention.
     */
    @Test
    void a_withdrawal_of_a_negative_amount_is_refused_and_moves_nothing() {
        Held before = whatIsHeld();

        ResponseEntity<JsonNode> response = withdraw(savingsAccount, currentAccount, "-1.00");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("-1.00");
        assertNothingMoved(before);
    }

    /**
     * Three decimal places is not an amount of money, and this asserts the two directions answer
     * that in the same words rather than only that each answers something. Two services wording one
     * rule twice is how a customer ends up told that a deposit of 10.001 has too many decimal places
     * and a withdrawal of it is "invalid" — the same objection, unrecognisably. Sending both and
     * comparing the sentences is the only way a test can hold them together.
     */
    @Test
    void a_withdrawal_finer_than_a_cent_is_refused_the_way_a_deposit_of_one_is() {
        Held before = whatIsHeld();

        ResponseEntity<JsonNode> refusedWithdrawal = withdraw(savingsAccount, currentAccount, "10.001");
        ResponseEntity<JsonNode> refusedDeposit = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", "10.001", "fromCurrentAccountId", currentAccount),
                JsonNode.class, savingsAccount);

        assertRefused(refusedWithdrawal, HttpStatus.BAD_REQUEST);
        assertRefused(refusedDeposit, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refusedWithdrawal))
                .contains("10.001")
                .isEqualTo(reasonGivenBy(refusedDeposit));
        assertNothingMoved(before);
    }

    /**
     * More than the account holds. Kept here as well as beside the successful withdrawals because
     * this file is the one place every way a withdrawal can be refused is listed, and a reader
     * checking that list against the requirements should not have to find one of them elsewhere.
     */
    @Test
    void a_withdrawal_larger_than_the_balance_is_refused_and_moves_nothing() {
        Held before = whatIsHeld();

        ResponseEntity<JsonNode> response =
                withdraw(savingsAccount, currentAccount, MORE_THAN_ANY_SEEDED_ACCOUNT_HOLDS);

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .contains(MORE_THAN_ANY_SEEDED_ACCOUNT_HOLDS)
                // To the cent, which is how the refusal writes it and not how the balance
                // necessarily comes back: SQLite keeps an amount as a float and hands back whatever
                // scale it kept, so 5.00 arrives here as 5.0.
                .contains(before.moneyBalance().setScale(2, RoundingMode.HALF_UP).toPlainString());
        assertNothingMoved(before);
    }

    /**
     * A body that is not a withdrawal request at all. Refused rather than allowed to fail somewhere
     * inside: an amount that was never sent is not an amount this application should be reasoning
     * about, and a server error would tell whoever sent it that the fault was here.
     */
    @Test
    void a_request_naming_neither_an_amount_nor_a_destination_is_refused_and_moves_nothing() {
        Held before = whatIsHeld();

        ResponseEntity<JsonNode> response = http.postForEntity(
                "/api/savings-accounts/{id}/withdrawals", Map.of(), JsonNode.class, savingsAccount);

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertNothingMoved(before);
    }

    /**
     * A refusal in the one shape every refusal in this application answers in: a problem document
     * (RFC 9457) whose status agrees with the response's own, carrying the reason in the field the
     * frontend reads it out of. Asserted on every refusal above rather than once, because the shape
     * is only worth anything if it holds for all of them — a page that renders the detail field
     * shows nothing at all for the one refusal that forgot it.
     *
     * <p>The words are asserted to be there rather than to say any particular thing, except where a
     * test names the figure a person needs to see. What they say is for a person to read, and
     * pinning every sentence here would turn each rewording into a failing test without making one
     * refusal clearer.
     */
    private void assertRefused(ResponseEntity<JsonNode> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("status").asInt()).isEqualTo(expected.value());
        assertThat(reasonGivenBy(response)).isNotBlank();
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }

    /**
     * Everything a refused withdrawal has to leave exactly as it found it: both of the savings
     * account's balances, the balance of the account the money would have landed in, every deposit
     * with the points it earned, and every withdrawal already recorded.
     *
     * <p>The deposits are compared one by one rather than counted. A withdrawal draws deposits down,
     * so a refusal that ran halfway would reduce one of them while leaving the count alone, and the
     * money balance is summed from exactly those figures — counting would miss the very thing a
     * half-applied withdrawal does.
     */
    private record Held(BigDecimal moneyBalance, long pointsBalance, List<String> deposits,
                        int withdrawalsRecorded, BigDecimal destinationBalance) {
    }

    private Held whatIsHeld() {
        BalancesView balances = balances();
        return new Held(
                balances.moneyBalance(),
                balances.pointsBalance(),
                Arrays.stream(http.getForObject("/api/savings-accounts/{id}/deposits",
                                DepositView[].class, savingsAccount))
                        .map(made -> made.id() + " of " + made.amount().setScale(2, RoundingMode.HALF_UP)
                                + " earning " + made.pointsEarned())
                        .toList(),
                http.getForObject("/api/savings-accounts/{id}/withdrawals", JsonNode.class, savingsAccount)
                        .size(),
                seeded.currentAccountBalanceOf(ANKE));
    }

    private void assertNothingMoved(Held before) {
        Held after = whatIsHeld();
        assertThat(after.moneyBalance()).isEqualByComparingTo(before.moneyBalance());
        assertThat(after.pointsBalance()).isEqualTo(before.pointsBalance());
        assertThat(after.deposits()).isEqualTo(before.deposits());
        assertThat(after.withdrawalsRecorded()).isEqualTo(before.withdrawalsRecorded());
        assertThat(after.destinationBalance()).isEqualByComparingTo(before.destinationBalance());
    }

    private BalancesView balances() {
        return http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccount);
    }

    private void deposit(String amount) {
        ResponseEntity<DepositView> made = http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccount), DepositView.class,
                savingsAccount);
        assertThat(made.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /**
     * Read as unshaped JSON, because a refusal answers with the reason rather than with a
     * withdrawal: asking for the response as a withdrawal would fail to read it before the status
     * could be looked at, and asking for it as text would leave the reason to be picked out of a
     * string.
     */
    private ResponseEntity<JsonNode> withdraw(long savingsAccountId, long toCurrentAccountId, String amount) {
        return http.postForEntity(
                "/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", toCurrentAccountId),
                JsonNode.class,
                savingsAccountId);
    }
}
