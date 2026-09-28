package io.dataroots.savingstreak.savingsproducts;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.WhatCanLeaveTodayView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

import static io.dataroots.savingstreak.savingsproducts.ASaverChoosingAProduct.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * "How much of this can I actually take out today" answered as one figure, for an account on each
 * of the four products — with the reason, in the words a withdrawal is refused in, whenever it is
 * less than the balance.
 *
 * <p><strong>The point of the reading is that nobody has to compose it.</strong> The figure used to
 * be three answers put together by whoever was drawing a screen: the balance, whether a term has
 * the account locked, and how much ready notice covers. Composing them means holding the order the
 * conditions compose in, and a screen holding that order is a second copy of the rule. These tests
 * are about the composition being right, which is why every one of them is asserted against
 * something the application says elsewhere rather than against a number typed here.
 *
 * <p><strong>The sentence is compared character for character with the refusal.</strong> That is
 * the assertion worth making: a panel that told somebody they could take nothing, and a withdrawal
 * that then refused them in different words, would be two statements of one rule. Asserting the
 * prose here would be a third.
 *
 * <p><strong>A customer of this test's own, and no clock is wound.</strong> Every case is true on
 * the day the account is opened — notice that has not been given, a term that has not matured, a
 * floor that holds nothing back — so nothing here needs an application of its own, and nothing here
 * disturbs one.
 */
class WhatIsFreeToTakeOutOfAnAccountTodayApiTest extends ApiIntegrationTest {

    /**
     * Free savings lets the whole balance go and names no condition at all.
     *
     * <p>The case that matters most, because it is the one every account that existed before
     * products did is in: no condition, no sentence, and a figure equal to the balance. A reading
     * that invented a reason for an account with nothing attached to it would put a warning on
     * every screen in the application.
     */
    @Test
    void free_savings_lets_the_whole_balance_go_and_names_nothing_in_the_way() {
        ASaverChoosingAProduct saver = aSaver("somebody on free savings");
        long savingsAccountId = saver.open("INSTANT").id();
        saver.payIn(savingsAccountId, "120.00");

        WhatCanLeaveTodayView free = saver.whatCanLeaveToday(savingsAccountId);

        assertThat(free.savingsAccountId()).isEqualTo(savingsAccountId);
        assertThat(free.balance()).isEqualByComparingTo("120.00");
        assertThat(free.freeToTakeToday()).isEqualByComparingTo("120.00");
        assertThat(free.condition()).isNull();
        assertThat(free.whyItIsLess()).isNull();
    }

    /**
     * A core saver's floor holds no money back, even standing under it.
     *
     * <p>The account is deliberately left below the five hundred euros its agreement asks to be
     * kept in, which is the case a reading built out of a switch on the product would get wrong:
     * this bank's minimum balance costs the month's bonus rate and never a euro of anybody's money.
     * The whole balance is free and nothing is in the way, which is what the withdrawal gate says
     * too.
     */
    @Test
    void a_floor_holds_no_money_back_even_when_the_account_is_standing_under_it() {
        ASaverChoosingAProduct saver = aSaver("somebody under their core saver floor");
        long savingsAccountId = saver.open("CORE").id();
        saver.payIn(savingsAccountId, "100.00");

        WhatCanLeaveTodayView free = saver.whatCanLeaveToday(savingsAccountId);

        assertThat(free.freeToTakeToday()).isEqualByComparingTo("100.00");
        assertThat(free.condition()).isNull();
        assertThat(free.whyItIsLess()).isNull();
        // And the gate agrees: the money actually comes out, which is the half of the claim a
        // reading on its own could not make.
        saver.takeOut(savingsAccountId, "100.00");
    }

    /**
     * A notice account with no notice given lets nothing go, in the sentence the withdrawal is
     * refused in.
     */
    @Test
    void a_notice_account_lets_go_only_what_notice_covers_in_the_words_a_withdrawal_is_refused_in() {
        ASaverChoosingAProduct saver = aSaver("somebody who has given no notice");
        long savingsAccountId = saver.open("NOTICE32").id();
        saver.payIn(savingsAccountId, "200.00");

        WhatCanLeaveTodayView free = saver.whatCanLeaveToday(savingsAccountId);

        assertThat(free.balance()).isEqualByComparingTo("200.00");
        assertThat(free.freeToTakeToday()).isEqualByComparingTo("0.00");
        assertThat(free.condition()).isEqualTo("NOTICE_THAT_HAS_NOT_RUN");
        assertThat(free.whyItIsLess())
                .isEqualTo(reasonGivenBy(saver.tryToTakeOut(savingsAccountId, "200.00")));
    }

    /**
     * A fixed term that has not matured lets nothing go, in the sentence the withdrawal is refused
     * in — and the lock answers, not the notice.
     *
     * <p>The condition is asserted by name because that is the half of the ordering this reading
     * could get wrong: a term is one lock over every euro at once, so an account inside one is
     * answered by the lock however much notice has been given on it.
     */
    @Test
    void a_fixed_term_that_has_not_matured_lets_nothing_go_and_says_so_as_the_lock() {
        ASaverChoosingAProduct saver = aSaver("somebody inside a fixed term");
        long savingsAccountId = saver.open("FIXED12").id();
        saver.payIn(savingsAccountId, "300.00");

        WhatCanLeaveTodayView free = saver.whatCanLeaveToday(savingsAccountId);

        assertThat(free.balance()).isEqualByComparingTo("300.00");
        assertThat(free.freeToTakeToday()).isEqualByComparingTo("0.00");
        assertThat(free.condition()).isEqualTo("A_TERM_THAT_HAS_NOT_MATURED");
        assertThat(free.whyItIsLess())
                .isEqualTo(reasonGivenBy(saver.tryToTakeOut(savingsAccountId, "300.00")));
    }

    /**
     * An account holding nothing has nothing free and nothing in the way — even one inside a fixed
     * term, which would refuse a withdrawal of nought if it were asked.
     *
     * <p>The reading deliberately does not ask. There is no money for a condition to hold, so a
     * screen would be given a warning about a withdrawal nobody could make, beside a panel about
     * the term that already says when it matures.
     */
    @Test
    void an_account_holding_nothing_has_nothing_free_and_names_nothing_in_the_way() {
        ASaverChoosingAProduct saver = aSaver("somebody whose term account is still empty");
        long savingsAccountId = saver.open("FIXED12").id();

        WhatCanLeaveTodayView free = saver.whatCanLeaveToday(savingsAccountId);

        assertThat(free.balance()).isEqualByComparingTo("0.00");
        assertThat(free.freeToTakeToday()).isEqualByComparingTo("0.00");
        assertThat(free.condition()).isNull();
        assertThat(free.whyItIsLess()).isNull();
    }

    /** An account nobody has heard of is a 404 in the words Accounts owns for an absent account. */
    @Test
    void an_account_nobody_has_heard_of_is_refused_rather_than_answered_with_nought() {
        ASaverChoosingAProduct saver = aSaver("somebody asking about a number");

        ResponseEntity<JsonNode> answered =
                saver.read("/api/savings-accounts/{id}/free-to-take-today", 9_999_999L);

        assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(answered)).contains("9999999");
    }

    private ASaverChoosingAProduct aSaver(String whoTheyAre) {
        return new ASaverChoosingAProduct(http, whoTheyAre);
    }
}
