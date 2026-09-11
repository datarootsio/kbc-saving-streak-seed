package io.dataroots.savingstreak.depositrefusals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A deposit the application cannot honour is refused with a reason, and nothing is written down.
 *
 * <p>All three are asserted every time, by {@link #assertRefused} and {@link #assertNothingMoved}.
 * A refusal that recorded something anyway would leave a balance nobody could explain, and one that
 * came back as a bare status would leave a person to guess which of the things they typed was being
 * objected to — neither is something a status code on its own can rule out.
 *
 * <p>Bram's savings account is the one paid into: no test in the run deposits into it successfully,
 * so a refusal that did land shows up here and in the empty-history test rather than disappearing
 * into a balance another test had already moved. His current account is left as full as it was
 * seeded for the same reason — a refusal for want of money can only be told apart from a refusal
 * that quietly took some if the balance it was refused against has not been moved by anybody else.
 */
class DepositIsRefusedApiTest extends ApiIntegrationTest {

    /**
     * More than either seeded current account was opened with, so it stays more than either holds
     * however many deposits the rest of the run has taken out of them.
     */
    private static final String MORE_THAN_ANY_SEEDED_ACCOUNT_HOLDS = "1000000.00";

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    @Test
    void a_deposit_of_zero_is_refused_and_records_nothing() {
        long savingsAccount = seeded.savingsAccountOf(BRAM);
        Held before = whatIsHeldIn(savingsAccount);

        ResponseEntity<JsonNode> response = deposit(savingsAccount, seeded.currentAccountOf(BRAM), "0.00");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertNothingMoved(savingsAccount, before);
    }

    @Test
    void a_deposit_of_a_negative_amount_is_refused_and_records_nothing() {
        long savingsAccount = seeded.savingsAccountOf(BRAM);
        Held before = whatIsHeldIn(savingsAccount);

        ResponseEntity<JsonNode> response = deposit(savingsAccount, seeded.currentAccountOf(BRAM), "-25.00");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertNothingMoved(savingsAccount, before);
    }

    /**
     * Three decimal places is not an amount of money. Refused rather than rounded: rounding would
     * move an amount the person never typed, and rounding down by a tenth of a cent is still a bank
     * deciding on its own what somebody meant.
     */
    @Test
    void a_deposit_finer_than_a_cent_is_refused_and_records_nothing() {
        long savingsAccount = seeded.savingsAccountOf(BRAM);
        Held before = whatIsHeldIn(savingsAccount);

        ResponseEntity<JsonNode> response = deposit(savingsAccount, seeded.currentAccountOf(BRAM), "10.001");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertNothingMoved(savingsAccount, before);
    }

    /**
     * An amount that is not a number at all, which is what a decimal comma arrives as. The page
     * prints balances the Belgian way and sends back whatever was typed, so "25,00" is the mistake
     * somebody here is most likely to make, and it has to come back as a refusal about the amount
     * rather than as whatever the machinery says when it cannot read a request at all.
     */
    @Test
    void a_deposit_of_something_that_is_not_a_number_is_refused_and_records_nothing() {
        long savingsAccount = seeded.savingsAccountOf(BRAM);
        Held before = whatIsHeldIn(savingsAccount);

        ResponseEntity<JsonNode> response = deposit(savingsAccount, seeded.currentAccountOf(BRAM), "25,00");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response)).contains("25,00");
        assertNothingMoved(savingsAccount, before);
    }

    /**
     * Paying into an account that does not exist is a mistake about which account, not about the
     * money, and it is reported as one. Accepting it would write down a deposit belonging to nobody:
     * a deposit names its accounts by identifier, so there is no account that would have refused it.
     *
     * <p>The one refusal here that does not go on to assert nothing was recorded, because there are
     * no records to read: both endpoints that would report them answer not-found for this identifier
     * whatever happened. A deposit written against it would be invisible from outside, which is why
     * the refusal itself has to carry the test.
     */
    @Test
    void a_deposit_into_a_savings_account_that_does_not_exist_is_not_found() {
        ResponseEntity<JsonNode> response =
                deposit(seeded.anIdNoSavingsAccountHas(), seeded.currentAccountOf(BRAM), "25.00");

        assertRefused(response, HttpStatus.NOT_FOUND);
    }

    @Test
    void a_deposit_from_a_current_account_that_does_not_exist_is_not_found_and_records_nothing() {
        long savingsAccount = seeded.savingsAccountOf(BRAM);
        Held before = whatIsHeldIn(savingsAccount);

        ResponseEntity<JsonNode> response =
                deposit(savingsAccount, seeded.anIdNoCurrentAccountHas(), "25.00");

        assertRefused(response, HttpStatus.NOT_FOUND);
        assertNothingMoved(savingsAccount, before);
    }

    /**
     * The refusal the requirements are most emphatic about: a savings account must not be fundable
     * from somebody else's money. Both accounts are real and the amount is fine, so nothing but the
     * pairing is wrong, which is the only way to be sure it is the pairing that was refused.
     */
    @Test
    void a_deposit_from_another_customers_current_account_is_refused_and_records_nothing() {
        long bramsSavings = seeded.savingsAccountOf(BRAM);
        Held before = whatIsHeldIn(bramsSavings);

        ResponseEntity<JsonNode> response = deposit(bramsSavings, seeded.currentAccountOf(ANKE), "25.00");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertNothingMoved(bramsSavings, before);
    }

    /**
     * A deposit bigger than the account it would come out of. Both accounts are real, the amount is
     * an amount, one customer holds both — nothing is wrong with the request except that the money
     * is not there, which is the only way to be sure that is what was refused.
     *
     * <p>The reason has to carry both figures, because a person told only that there is not enough
     * has to go and look up how much there was. This is the one refusal whose exact words are
     * asserted: they are the two numbers the customer needs, and a rewording that dropped one of
     * them should fail here rather than quietly stop being useful.
     */
    @Test
    void a_deposit_larger_than_the_current_account_is_refused_and_records_nothing() {
        long savingsAccount = seeded.savingsAccountOf(BRAM);
        Held before = whatIsHeldIn(savingsAccount);
        BigDecimal inTheCurrentAccount = seeded.currentAccountBalanceOf(BRAM);

        ResponseEntity<JsonNode> response =
                deposit(savingsAccount, seeded.currentAccountOf(BRAM), MORE_THAN_ANY_SEEDED_ACCOUNT_HOLDS);

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .contains(MORE_THAN_ANY_SEEDED_ACCOUNT_HOLDS)
                // To the cent, which is how the refusal writes it and not how the balance
                // necessarily comes back: SQLite keeps an amount as a float and hands back whatever
                // scale it kept, so 1150.00 arrives here as 1150.0.
                .contains(inTheCurrentAccount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        assertNothingMoved(savingsAccount, before);
    }

    /**
     * The other half of that refusal, and the half a savings account cannot show: the money stays
     * where it was. A withdrawal that went through and then failed to arrive would leave the savings
     * side looking exactly like this one does.
     */
    @Test
    void a_deposit_refused_for_want_of_money_leaves_the_current_account_untouched() {
        BigDecimal before = seeded.currentAccountBalanceOf(BRAM);

        deposit(seeded.savingsAccountOf(BRAM), seeded.currentAccountOf(BRAM),
                MORE_THAN_ANY_SEEDED_ACCOUNT_HOLDS);

        assertThat(seeded.currentAccountBalanceOf(BRAM)).isEqualByComparingTo(before);
    }

    /**
     * A body that is not a deposit request at all. Refused rather than allowed to fail somewhere
     * inside: an amount that was never sent is not an amount this application should be reasoning
     * about, and a server error would tell whoever sent it that the fault was here.
     */
    @Test
    void a_request_naming_neither_an_amount_nor_an_account_is_refused() {
        long savingsAccount = seeded.savingsAccountOf(BRAM);
        Held before = whatIsHeldIn(savingsAccount);

        ResponseEntity<JsonNode> response = http.postForEntity(
                "/api/savings-accounts/{id}/deposits", Map.of(), JsonNode.class, savingsAccount);

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertNothingMoved(savingsAccount, before);
    }

    /**
     * A refusal, and words to go with it — asserted together because a refusal without them is only
     * half of what was asked for. The words are asserted to be there rather than to say any
     * particular thing: what they say is for a person to read, and pinning the sentences here would
     * turn every rewording into a failing test without making one refusal clearer.
     */
    private void assertRefused(ResponseEntity<JsonNode> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(reasonGivenBy(response)).isNotBlank();
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }

    /** What a refusal has to leave exactly as it found it: both balances, and the deposits behind them. */
    private record Held(BigDecimal moneyBalance, long pointsBalance, int depositsRecorded) {
    }

    private Held whatIsHeldIn(long savingsAccountId) {
        BalancesView balances =
                http.getForObject("/api/savings-accounts/{id}", BalancesView.class, savingsAccountId);
        DepositView[] recorded = http.getForObject(
                "/api/savings-accounts/{id}/deposits", DepositView[].class, savingsAccountId);
        return new Held(balances.moneyBalance(), balances.pointsBalance(), recorded.length);
    }

    private void assertNothingMoved(long savingsAccountId, Held before) {
        Held after = whatIsHeldIn(savingsAccountId);
        assertThat(after.moneyBalance()).isEqualByComparingTo(before.moneyBalance());
        assertThat(after.pointsBalance()).isEqualTo(before.pointsBalance());
        assertThat(after.depositsRecorded()).isEqualTo(before.depositsRecorded());
    }

    /**
     * Read as unshaped JSON, because a refusal answers with the reason rather than with a deposit:
     * asking for the response as a deposit would fail to read it before the status could be looked
     * at, and asking for it as text would leave the reason to be picked out of a string.
     */
    private ResponseEntity<JsonNode> deposit(long savingsAccountId, long currentAccountId, String amount) {
        return http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId),
                JsonNode.class,
                savingsAccountId);
    }
}
