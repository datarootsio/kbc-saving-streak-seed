package io.dataroots.savingstreak.savingsproducts;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.TheNoticeOnAnAccountView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsproducts.ACustomerMovingBetweenProducts.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Moving money to another of your own savings accounts is not a way round anything the account it
 * leaves has to say about money leaving it.
 *
 * <p><strong>The whole feature turns on this, and it is the part that would be easiest to get
 * plausibly wrong.</strong> A move exists because the ordinary route punished somebody for taking a
 * better offer; a move that got round a notice period would be a way for anybody to have their
 * money in thirty-two seconds rather than thirty-two days, by moving it to an instant-access
 * account of their own and withdrawing it from there. So the source's conditions refuse a move in
 * exactly the sentences they refuse a withdrawal — asserted here by comparing the two refusals
 * character for character rather than by quoting either.
 *
 * <p><strong>And the notice a move runs on is spent by running on it</strong>, which is the other
 * half and the one a refusal alone cannot prove. Notice is consumed by being met: a withdrawal that
 * ran on it uses it up, oldest first. A move that asked the agreement and never told it would leave
 * the notice standing, and one month's notice would be good for an unlimited number of moves — so
 * there is a test here that gives notice once, moves once, and watches the second move be refused.
 *
 * <p>An application of its own with a clock to wind, because a notice period only becomes
 * interesting thirty-two days later.
 */
class AMoveIsRefusedByTheSourceProductsConditionsApiTest extends ApiIntegrationTest {

    @Test
    void a_move_out_of_a_notice_account_is_refused_in_the_words_a_withdrawal_is() {
        try (ACustomerMovingBetweenProducts saver = new ACustomerMovingBetweenProducts(
                aDatabaseFileThatDoesNotExistYet("moving-and-notice"), "Griet on notice")) {
            long notice = saver.open("NOTICE32");
            long instant = saver.theAccountTheyWereOpenedWith();
            saver.payIn(notice, "500.00");

            ResponseEntity<JsonNode> refusedMove = saver.tryToMove(notice, instant, "500.00");
            ResponseEntity<JsonNode> refusedWithdrawal = saver.tryToTakeOut(notice, "500.00");

            assertThat(refusedMove.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            // Word for word. Not "a sentence about notice" — the same sentence, because both come
            // out of the one place that knows what this account's agreement asks for.
            assertThat(reasonGivenBy(refusedMove)).isEqualTo(reasonGivenBy(refusedWithdrawal));
            assertThat(reasonGivenBy(refusedMove))
                    .contains(String.valueOf(ACustomerMovingBetweenProducts.THE_DAYS_NOTICE_ASKS_FOR));
            // Nothing moved anywhere, which is the point of the gate being in front of the money
            // rather than behind it.
            assertThat(saver.balanceOf(notice)).isEqualByComparingTo("500.00");
            assertThat(saver.balanceOf(instant)).isEqualByComparingTo("0.00");
            // And the question a customer would have asked first is refused too, rather than
            // quoting a price for a move that was never going to happen.
            ResponseEntity<JsonNode> refusedQuote =
                    saver.tryToAskWhatMovingWouldCost(notice, instant, "500.00");
            assertThat(refusedQuote.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(reasonGivenBy(refusedQuote)).isEqualTo(reasonGivenBy(refusedMove));
        }
    }

    @Test
    void moving_on_notice_spends_that_notice_so_it_cannot_be_used_a_second_time() {
        try (ACustomerMovingBetweenProducts saver = new ACustomerMovingBetweenProducts(
                aDatabaseFileThatDoesNotExistYet("moving-spends-notice"), "Hendrik who waited")) {
            long notice = saver.open("NOTICE32");
            long instant = saver.theAccountTheyWereOpenedWith();
            saver.payIn(notice, "400.00");
            saver.giveNoticeOn(notice, "100.00");

            // Given but not run: a move is refused exactly as a withdrawal would be, and the
            // sentence names the days that are left.
            assertThat(saver.tryToMove(notice, instant, "100.00").getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            saver.daysPass(ACustomerMovingBetweenProducts.THE_DAYS_NOTICE_ASKS_FOR);
            assertThat(saver.theNoticeOn(notice).readyToTakeToday()).isEqualByComparingTo("100.00");

            saver.move(notice, instant, "100.00");

            assertThat(saver.balanceOf(notice)).isEqualByComparingTo("300.00");
            assertThat(saver.balanceOf(instant)).isEqualByComparingTo("100.00");
            // The notice is gone, spent by the move that ran on it. This is the assertion that says
            // the move told the agreement the money had left rather than only asking whether it
            // could — and without it, one month's notice would be good for moving the whole account
            // out, a hundred euros at a time, for ever.
            TheNoticeOnAnAccountView afterwards = saver.theNoticeOn(notice);
            assertThat(afterwards.readyToTakeToday()).isEqualByComparingTo("0.00");
            assertThat(afterwards.stillWaiting()).isEqualByComparingTo("0.00");

            ResponseEntity<JsonNode> refusedSecondMove = saver.tryToMove(notice, instant, "100.00");

            assertThat(refusedSecondMove.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(reasonGivenBy(refusedSecondMove))
                    .isEqualTo(reasonGivenBy(saver.tryToTakeOut(notice, "100.00")));
            assertThat(saver.balanceOf(notice)).isEqualByComparingTo("300.00");
        }
    }

    @Test
    void a_move_out_of_a_fixed_term_is_refused_until_it_matures_in_the_words_a_withdrawal_is() {
        try (ACustomerMovingBetweenProducts saver = new ACustomerMovingBetweenProducts(
                aDatabaseFileThatDoesNotExistYet("moving-and-a-term"), "Ines locked in")) {
            long fixed = saver.open("FIXED12");
            long instant = saver.theAccountTheyWereOpenedWith();
            saver.payIn(fixed, "1000.00");

            ResponseEntity<JsonNode> refusedMove = saver.tryToMove(fixed, instant, "1000.00");
            ResponseEntity<JsonNode> refusedWithdrawal = saver.tryToTakeOut(fixed, "1000.00");

            assertThat(refusedMove.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(reasonGivenBy(refusedMove)).isEqualTo(reasonGivenBy(refusedWithdrawal));
            assertThat(saver.balanceOf(fixed)).isEqualByComparingTo("1000.00");
            assertThat(saver.balanceOf(instant)).isEqualByComparingTo("0.00");

            // A year later the term has matured and the same press goes through — which is what
            // says the refusal was the agreement talking rather than moving being forbidden out of
            // a fixed term for ever.
            saver.daysPass(366);

            saver.move(fixed, instant, "1000.00");

            assertThat(saver.balanceOf(fixed)).isEqualByComparingTo("0.00");
            assertThat(saver.balanceOf(instant)).isEqualByComparingTo("1000.00");
        }
    }
}
