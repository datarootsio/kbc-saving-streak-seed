package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ATermBrokenView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.MoneyMovementView;
import io.dataroots.savingstreak.support.TheTermOnAnAccountView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsproducts.AFixedTermSomebodyHolds.FREE_SAVINGS;
import static io.dataroots.savingstreak.savingsproducts.AFixedTermSomebodyHolds.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The twelve-month fixed term starts being worth its rate: the money is not the customer's again
 * until the year is up, a withdrawal before then is refused in a sentence naming the day it
 * matures, and a customer who must have the money anyway can break the term for a price they are
 * told before they confirm.
 *
 * <p><strong>The whole arc in one class, in order.</strong> Locked, refused, priced, broken, moved
 * onto free savings, and free to withdraw: they are one story about one account, and told out of
 * order they would be six unrelated assertions that happened to pass. The clock only moves forward,
 * which is the other reason this class is ordered — nothing here can be put back, and breaking a
 * term is one-way by design.
 *
 * <p><strong>An application and a database of its own</strong>, because the last two tests wind the
 * clock past a maturity a year away. {@link AFixedTermSomebodyHolds} argues that, and argues why
 * there are two accounts rather than one.
 *
 * <p><strong>Every date is read off the application's own clock</strong> and never off the
 * machine's. A test that wrote a date down would be asserting what day it was compiled on, and this
 * one runs on a clock that has been wound more than a year forward by the time it finishes.
 *
 * <p><strong>The one figure written down here is the charge</strong>, and it is written down on
 * purpose. EUR 2.95 is ninety days of 2.40% on EUR 500, floored to the cent — a sum a reader can
 * do on paper. Deriving it in the test from the rate and the days would be the test restating the
 * arithmetic it is meant to be checking, and would pass just as happily if both copies were wrong.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AFixedTermIsLockedUntilMaturityAndBrokenEarlyForAPriceApiTest extends ApiIntegrationTest {

    private static final Path DATABASE = aDatabaseFileThatDoesNotExistYet("saving-streak-term");

    /** What each of the two accounts is opened with, and the balance the charge is priced on. */
    private static final String WHAT_EACH_ONE_HOLDS = "500.00";

    /**
     * Ninety days of 2.40% a year on EUR 500, floored to the cent.
     *
     * <p>500 × 240 × 90 ÷ (10 000 × 365) is 2.9589…, and the flooring makes it EUR 2.95. The sum is
     * spelled out here rather than computed, because a test that computed it would agree with
     * whatever the application did.
     *
     * <p>EUR 500 rather than a rounder thousand because the current account each of these customers
     * is opened with holds EUR 1 500, and two terms have to be funded out of it. The arithmetic is
     * no less checkable for it.
     */
    private static final String WHAT_BREAKING_COSTS = "2.95";

    /** What is left after the charge, which is the balance the customer is actually holding. */
    private static final String WHAT_IS_LEFT_AFTER_THE_CHARGE = "497.05";

    /** Far enough past a twelve-month maturity to be plainly past it, and no further. */
    private static final int DAYS_WOUND_PAST_MATURITY = 370;

    private static AFixedTermSomebodyHolds theirs;

    /** The term that gets broken early, and the term that is left alone to mature. */
    private static long theOneBrokenEarly;
    private static long theOneLeftAlone;

    private static LocalDate theDayTheyWereOpened;

    @BeforeAll
    static void openTwoFixedTermsWithMoneyInThem() {
        theirs = new AFixedTermSomebodyHolds(DATABASE, "somebody locking money away");
        theDayTheyWereOpened = theirs.theDateTheClockReads();
        theOneBrokenEarly = theirs.openAFixedTerm();
        theOneLeftAlone = theirs.openAFixedTerm();
        theirs.payIn(theOneBrokenEarly, WHAT_EACH_ONE_HOLDS);
        theirs.payIn(theOneLeftAlone, WHAT_EACH_ONE_HOLDS);
    }

    @AfterAll
    static void stopIt() {
        if (theirs != null) {
            theirs.close();
        }
    }

    /**
     * Both accounts are living under a twelve-month term that matures a year after they were
     * opened, which is the premise everything below rests on.
     *
     * <p>Read off the agreement the account is living under rather than off the product's card,
     * because those are two different readings and the difference is the feature: what the bank is
     * selling today and what this account agreed to are the same sentence only on the day it was
     * opened.
     */
    @Test
    @Order(1)
    void the_account_is_living_under_a_twelve_month_term_that_matures_a_year_from_today() {
        assertThat(theirs.theAccount(theOneBrokenEarly).agreement().maturesOn())
                .isEqualTo(theDayTheyWereOpened.plusMonths(12));

        TheTermOnAnAccountView term = theirs.theTermOn(theOneBrokenEarly);
        assertThat(term.termMonths()).isEqualTo(12);
        assertThat(term.maturesOn()).isEqualTo(theDayTheyWereOpened.plusMonths(12));
        assertThat(term.matured()).isFalse();
        assertThat(term.locked()).isTrue();
        assertThat(term.daysLeft()).isGreaterThan(360);
        assertThat(theirs.balanceOf(theOneBrokenEarly)).isEqualByComparingTo(WHAT_EACH_ONE_HOLDS);
    }

    /**
     * The money is not the customer's again until the year is up, and the refusal names the day it
     * matures and how long is left rather than stopping at no.
     *
     * <p>The balance afterwards is asserted as well as the status, which is the shape every refusal
     * test in this application takes: a refusal that had already moved the money would be a refusal
     * in name only.
     *
     * <p>One euro is asked for rather than the whole balance, deliberately. A term is a lock over
     * every euro in the account at once, so the smallest possible withdrawal is refused in exactly
     * the words the largest one is — and a test that only ever asked for the whole balance could
     * not tell that rule from a balance check.
     */
    @Test
    @Order(2)
    void a_withdrawal_before_maturity_is_refused_naming_the_day_it_matures_and_how_long_is_left() {
        ResponseEntity<JsonNode> refused = theirs.tryToTakeOut(theOneBrokenEarly, "1.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains(theDayTheyWereOpened.plusMonths(12).toString())
                .contains("days away")
                .contains("break the term");
        assertThat(theirs.balanceOf(theOneBrokenEarly)).isEqualByComparingTo(WHAT_EACH_ONE_HOLDS);
    }

    /**
     * The reading says what breaking would cost, and says it before anything is confirmed.
     *
     * <p>The point of the assertion is the pair: the figure is read here, and the test below finds
     * the same figure charged. A price a customer is shown and a price they are charged that are
     * worked out in two places would agree until the day they did not.
     */
    @Test
    @Order(3)
    void the_reading_says_what_breaking_the_term_would_cost_before_anything_is_confirmed() {
        TheTermOnAnAccountView term = theirs.theTermOn(theOneBrokenEarly);

        assertThat(term.earlyExitPenaltyDays()).isEqualTo(90);
        assertThat(term.balance()).isEqualByComparingTo(WHAT_EACH_ONE_HOLDS);
        assertThat(term.whatBreakingWouldCost()).isEqualByComparingTo(WHAT_BREAKING_COSTS);
        // Reading a price moves nothing, which is the whole of "before anything is confirmed".
        assertThat(theirs.balanceOf(theOneBrokenEarly)).isEqualByComparingTo(WHAT_EACH_ONE_HOLDS);
    }

    /**
     * Breaking is a deliberate press, it charges exactly what the reading quoted, and the charge is
     * its own movement in the record of money that moved.
     *
     * <p><strong>The movement is the assertion that matters.</strong> A balance that fell by two
     * euros and ninety-five cents with nothing to point at would be the one figure in this
     * application nobody could explain; a row in the ledger, under a direction of its own, is what
     * the ticket asks for in as many words.
     *
     * <p>It is <em>not</em> in the account's own list of withdrawals, and that is asserted too. That
     * list answers "what have I taken out of this account", and the customer took none of it — the
     * same reading the deposit history takes when it leaves out the interest the bank paid in.
     */
    @Test
    @Order(4)
    void breaking_the_term_charges_the_stated_price_as_its_own_money_movement() {
        ATermBrokenView broken = theirs.breakTheTerm(theOneBrokenEarly);

        assertThat(broken.charge()).isEqualByComparingTo(WHAT_BREAKING_COSTS);
        assertThat(broken.earlyExitPenaltyDays()).isEqualTo(90);
        assertThat(broken.balanceItWasChargedOn()).isEqualByComparingTo(WHAT_EACH_ONE_HOLDS);
        assertThat(broken.wouldHaveMaturedOn()).isEqualTo(theDayTheyWereOpened.plusMonths(12));
        assertThat(theirs.balanceOf(theOneBrokenEarly))
                .isEqualByComparingTo(WHAT_IS_LEFT_AFTER_THE_CHARGE);

        List<MoneyMovementView> charges = theirs.theMoneyThatMoved().stream()
                .filter(moved -> "AN_EARLY_EXIT_CHARGE".equals(moved.direction()))
                .toList();
        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount()).isEqualByComparingTo(WHAT_BREAKING_COSTS);
        assertThat(charges.get(0).savingsAccountId()).isEqualTo(theOneBrokenEarly);
        // Nothing was debited to pay it and nobody was credited by it, which is what makes it a
        // charge rather than a transfer.
        assertThat(charges.get(0).currentAccountId()).isNull();
        assertThat(charges.get(0).pointsEarned()).isZero();

        assertThat(theirs.theWithdrawalsFrom(theOneBrokenEarly)).isEmpty();
    }

    /**
     * Breaking ends the term: the account is on free savings at the version being sold today, and
     * the maturity date is gone from its reading.
     *
     * <p>The version is asked of the catalogue rather than written down, because free savings has
     * published more than one and an account entering the agreement today enters the one on offer
     * today — which is the rule every newly opened account follows and the rule this move follows.
     */
    @Test
    @Order(5)
    void breaking_moves_the_account_onto_free_savings_and_the_maturity_date_is_gone() {
        assertThat(theirs.theAccount(theOneBrokenEarly).agreement().productCode())
                .isEqualTo(FREE_SAVINGS);
        assertThat(theirs.theAccount(theOneBrokenEarly).agreement().version())
                .isEqualTo(theirs.whatIsBeingSoldToday(FREE_SAVINGS));
        assertThat(theirs.theAccount(theOneBrokenEarly).agreement().maturesOn()).isNull();

        TheTermOnAnAccountView term = theirs.theTermOn(theOneBrokenEarly);
        assertThat(term.termMonths()).isZero();
        assertThat(term.maturesOn()).isNull();
        assertThat(term.locked()).isFalse();
        assertThat(term.whatBreakingWouldCost()).isEqualByComparingTo("0.00");
    }

    /**
     * A term broken cannot be broken twice, and the account afterwards refuses nothing.
     *
     * <p>Both halves in one test because they are one claim about the same account: it is not on a
     * term any more, so there is nothing to break and nothing standing in the way of the money. A
     * second charge would be the worse of the two failures, and the balance afterwards is what
     * rules it out.
     */
    @Test
    @Order(6)
    void a_term_broken_cannot_be_broken_twice_and_the_account_afterwards_refuses_nothing() {
        ResponseEntity<JsonNode> again = theirs.tryToBreakTheTerm(theOneBrokenEarly);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(again)).contains("not locked into a fixed term");

        theirs.takeOut(theOneBrokenEarly, "100.00");
        assertThat(theirs.balanceOf(theOneBrokenEarly)).isEqualByComparingTo("397.05");
    }

    /**
     * The other account is untouched by any of that: still locked, still refusing, still a year
     * from maturing.
     *
     * <p>Worth its own test because breaking a term moves one account onto another product, and an
     * implementation that moved the customer rather than the account would pass every assertion
     * above and fail this one.
     */
    @Test
    @Order(7)
    void the_other_term_is_untouched_and_still_refuses_a_withdrawal() {
        ResponseEntity<JsonNode> refused = theirs.tryToTakeOut(theOneLeftAlone, "1.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains(theDayTheyWereOpened.plusMonths(12).toString());
        assertThat(theirs.theTermOn(theOneLeftAlone).locked()).isTrue();
        assertThat(theirs.balanceOf(theOneLeftAlone)).isEqualByComparingTo(WHAT_EACH_ONE_HOLDS);
    }

    /**
     * Winding the clock past the maturity date turns the refusal into an allowed withdrawal, and
     * nothing is charged for it.
     *
     * <p><strong>The same request, the same account, a different day.</strong> Nothing was swept and
     * no flag was set: whether a term has matured is the day it was opened plus its months against
     * the clock the application is standing on, read at the moment somebody asks. That is the claim,
     * and a test that moved a clock is the only way to make it.
     *
     * <p>The balance falls by exactly what was taken, which is what "with no charge" means when it
     * is said as a figure. A charge would show up here as the money that went missing.
     */
    @Test
    @Order(8)
    void winding_the_clock_past_the_maturity_date_turns_the_refusal_into_an_allowed_withdrawal() {
        theirs.daysPass(DAYS_WOUND_PAST_MATURITY);

        TheTermOnAnAccountView term = theirs.theTermOn(theOneLeftAlone);
        assertThat(term.matured()).isTrue();
        assertThat(term.locked()).isFalse();
        assertThat(term.daysLeft()).isZero();
        assertThat(term.whatBreakingWouldCost()).isEqualByComparingTo("0.00");

        BigDecimal before = theirs.balanceOf(theOneLeftAlone);
        theirs.takeOut(theOneLeftAlone, "250.00");
        assertThat(theirs.balanceOf(theOneLeftAlone))
                .isEqualByComparingTo(before.subtract(new BigDecimal("250.00")));
        assertThat(theirs.theMoneyThatMoved().stream()
                .filter(moved -> "AN_EARLY_EXIT_CHARGE".equals(moved.direction()))
                .toList())
                .as("no second charge was taken for a withdrawal after maturity")
                .hasSize(1);
    }

    /**
     * A term that has already matured cannot be broken, and the refusal says when it came free.
     *
     * <p>Told apart from "that account is not on a term" because the sentences point somewhere
     * different: knowing the money has been theirs since a date they can read is the difference
     * between "why can I not break this" and "I did not need to".
     */
    @Test
    @Order(9)
    void a_term_that_has_already_matured_cannot_be_broken() {
        ResponseEntity<JsonNode> refused = theirs.tryToBreakTheTerm(theOneLeftAlone);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains(theDayTheyWereOpened.plusMonths(12).toString())
                .contains("nothing to pay");
    }
}
