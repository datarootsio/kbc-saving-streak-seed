package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.TermsVersionView;
import io.dataroots.savingstreak.support.TheNewerTermsView;
import io.dataroots.savingstreak.support.TheTermOnAnAccountView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A term that reached its day and was left waiting is offered newer terms, refuses them in a
 * sentence naming the day it matured, and is still sitting on exactly the same agreement three
 * wound-forward years later.
 *
 * <p><strong>What this class is really pinning is the collision between two tickets that were
 * right on their own.</strong> One settles a matured term by the ending its terms name, and the
 * ending that waits keeps a maturity date that has passed. The other lets a holder take the newer
 * terms of their product, refused only while a term is <em>running</em> — and a waiting term is not
 * running, so the door stood open to it. What came through that door was never a repricing. Before
 * this ticket, a waiting twelve-month term offered a twenty-four-month version took it and was
 * locked again for another three hundred and thirty-one days, with a settlement already written
 * against a maturity the account's calendar no longer produced; and the same press with a version
 * of the same length but a different ending left an account whose own panel promised a roll-over on
 * a day in the past, which three further years of nightly sweeps never delivered because that day
 * had been settled. Both are the same mistake, and the decision that removes it is argued on
 * {@code WhatEachSavingsAccountIsOn#takeTheNewerTerms}: the version an account is on stops moving
 * on the day its term is up.
 *
 * <p><strong>An application and a database of its own, because this class publishes.</strong>
 * Publishing cannot be undone — that is the promise of the catalogue — so a version written into
 * the run's shared database would still be there for the tests that pin the shelf to four products
 * at four rates. It is also a class that winds the clock past five maturities, which nothing
 * sharing a clock could survive.
 *
 * <p><strong>The waiting term is a republished notice account, exactly as the maturity tests build
 * one.</strong> The bank seeds one product with a term and its terms say to roll over, so a term
 * that <em>waits</em> is reached by publishing a version of a product that has no term today. That
 * is the administration door doing the thing it exists for, and it keeps the assertions against
 * terms somebody actually published.
 *
 * <p><strong>The rolling account beside it is the control, and it is what makes the last test say
 * something.</strong> A settlement record is this module's own row with no way out of it — which is
 * deliberate, and means a second settlement of a waiting maturity would be invisible here except
 * through its consequences. So the test asserts the consequences that would show: the waiting
 * account never changes and its money never stops being free, while a rolling account in the same
 * application, swept on the same mornings, settles a maturity every year. A sweep that had gone
 * quiet would fail the second half; a sweep that re-settled the waiting account into anything at all
 * would fail the first.
 *
 * <p><strong>In order, because it is one story about one account.</strong> The clock only moves
 * forward and a version cannot be unpublished.
 *
 * <p>Not one sentence of the backend's is written down as an expected string: the assertions look
 * for the figures — the maturity date, the product's name — inside the words, because the wording
 * is the backend's to own and a test that pinned it would fail on a comma.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ATermThatHasMaturedTakesNoNewerTermsApiTest extends ApiIntegrationTest {

    private static final Path DATABASE = aDatabaseFileThatDoesNotExistYet("saving-streak-waiting");

    /** The notice account, republished here as a term that waits at the end of itself. */
    private static final String THE_ONE_REPUBLISHED_TO_WAIT = "NOTICE32";

    /** The twelve-month fixed term the catalogue seeds, whose own terms say to roll over. */
    private static final String THE_ROLLING_TERM = "FIXED12";

    /** The name the maturity sweep answers to on the development jobs endpoint. */
    private static final String THE_MATURITY_SWEEP = "settleMaturedTerms";

    /** How long the published term runs for, matching the one the bank seeds. */
    private static final int TWELVE_MONTHS = 12;

    /** Twice that, so that a version taken would move the maturity into the future. */
    private static final int TWENTY_FOUR_MONTHS = 24;

    /** Enough to be past a twelve-month maturity without being near the next one. */
    private static final int A_YEAR_AND_A_BIT = 400;

    /** What goes into the account, so that "the money is free" is about a real balance. */
    private static final String WHAT_THE_ACCOUNT_HOLDS = "200.00";

    /** And what comes back out of it each time freedom is asserted rather than assumed. */
    private static final String WHAT_IS_TAKEN_OUT_TO_PROVE_IT_IS_FREE = "10.00";

    private static AnApplicationWithAClockToMove application;
    private static String whoseAccountItIs;
    private static long theWaitingTerm;
    private static long theRollingTerm;
    private static LocalDate theDayItMatured;
    private static int theVersionItAgreedTo;

    @BeforeAll
    static void openATermThatWaitsAndOneThatRolls() {
        application = new AnApplicationWithAClockToMove(DATABASE);
        whoseAccountItIs = application.aCustomerOfItsOwn("a term that waits");
        republishTheNoticeAccountAsATermOf(TWELVE_MONTHS, "HOLD", "0.60");
        theWaitingTerm = application.openASavingsAccountOn(whoseAccountItIs,
                THE_ONE_REPUBLISHED_TO_WAIT);
        theRollingTerm = application.openASavingsAccountOn(whoseAccountItIs, THE_ROLLING_TERM);
        application.deposit(theWaitingTerm, whoseAccountItIs, WHAT_THE_ACCOUNT_HOLDS);
        application.deposit(theRollingTerm, whoseAccountItIs, WHAT_THE_ACCOUNT_HOLDS);
        AnAgreementView agreement = application.theAgreementOf(theWaitingTerm);
        theVersionItAgreedTo = agreement.version();
        theDayItMatured = agreement.maturesOn();
    }

    @AfterAll
    static void closeTheApplication() {
        application.close();
    }

    /**
     * The day comes, the sweep settles it, and the account is exactly where it was with the money
     * free to move.
     *
     * <p>The starting position the rest of this class is about, asserted rather than assumed: a
     * waiting term reads matured and unlocked, which is precisely why the door to newer terms was
     * open to it, and it is still on the version it agreed to because holding moves nothing.
     */
    @Test
    @Order(1)
    void a_term_that_matured_and_waited_is_free_money_on_the_version_it_agreed_to() {
        application.daysPass(A_YEAR_AND_A_BIT);
        application.runJob(THE_MATURITY_SWEEP);

        TheTermOnAnAccountView term = application.theTermOn(theWaitingTerm);
        assertThat(term.matured()).as("its day has come").isTrue();
        assertThat(term.locked()).as("so nothing is locking the money").isFalse();
        assertThat(term.maturesOn()).isEqualTo(theDayItMatured);
        assertThat(term.maturityAction()).isEqualTo("HOLD");
        assertThat(application.theAgreementOf(theWaitingTerm).version())
                .as("holding moves nothing at all")
                .isEqualTo(theVersionItAgreedTo);

        assertThat(theMoneyIsFree()).as("the money came out, which is what waiting means").isTrue();
    }

    /**
     * The product publishes a longer term at a better rate, the account is shown the difference, and
     * the press is refused in a sentence naming the day it matured and what to do instead.
     *
     * <p><strong>This is the test the whole ticket is about.</strong> The version on offer is
     * genuinely newer and genuinely better on the headline rate, so nothing here is refused for want
     * of something to take: what is refused is re-dating a maturity that has already happened. The
     * assertion that matters most is the last one — the money is still free afterwards, because the
     * failure this replaced was free money quietly becoming locked money on one press.
     *
     * <p>The reading is not refused. The account can see perfectly well what its product is selling;
     * the refusal is what that press leads to, and it says where to go instead.
     */
    @Test
    @Order(2)
    void a_waiting_term_refuses_newer_terms_naming_the_day_it_matured_and_what_to_do_instead() {
        republishTheNoticeAccountAsATermOf(TWENTY_FOUR_MONTHS, "HOLD", "0.90");

        TheNewerTermsView newer = application.theNewerTermsFor(theWaitingTerm);
        assertThat(newer.newerTermsExist()).as("there is genuinely something newer").isTrue();
        assertThat(newer.theVersionOnOfferToday()).isGreaterThan(newer.theVersionYouAreOn());
        assertThat(newer.whatWouldChange()).isNotEmpty();

        ResponseEntity<JsonNode> refused = application.tryToTakeTheNewerTerms(theWaitingTerm);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .as("the refusal names the day it matured and where to go instead")
                .contains(theDayItMatured.toString())
                .contains(newer.productName());

        AnAgreementView agreement = application.theAgreementOf(theWaitingTerm);
        assertThat(agreement.version())
                .as("a refused press moves nothing")
                .isEqualTo(theVersionItAgreedTo);
        assertThat(agreement.maturesOn())
                .as("so the day the settlement was written against cannot have moved")
                .isEqualTo(theDayItMatured);
        TheTermOnAnAccountView term = application.theTermOn(theWaitingTerm);
        assertThat(term.matured()).isTrue();
        assertThat(term.locked()).as("and free money did not become locked money").isFalse();
        assertThat(theMoneyIsFree()).isTrue();
    }

    /**
     * A version of the same length that ends differently cannot be taken either, which is the half
     * of this that has nothing to do with the date moving.
     *
     * <p><strong>The date would not have moved here, and the press was wrong anyway.</strong>
     * Twenty-four months against twenty-four months leaves the maturity exactly where it is — and
     * that is the trap: the sweep has already settled that day, so the ending the new version names
     * would simply never happen. An account promising a roll-over that the sweep will never perform
     * is a maturity silently skipped, and it is refused for the same reason the other is: a maturity
     * is settled by the terms it arrived under.
     */
    @Test
    @Order(3)
    void terms_that_only_change_the_ending_cannot_be_taken_either() {
        republishTheNoticeAccountAsATermOf(TWENTY_FOUR_MONTHS, "ROLL_OVER", "1.10");

        assertThat(application.theNewerTermsFor(theWaitingTerm).newerTermsExist()).isTrue();

        ResponseEntity<JsonNode> refused = application.tryToTakeTheNewerTerms(theWaitingTerm);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains(theDayItMatured.toString());
        TheTermOnAnAccountView term = application.theTermOn(theWaitingTerm);
        assertThat(term.maturityAction())
                .as("the ending stays the one this account's maturity was settled by")
                .isEqualTo("HOLD");
        assertThat(term.maturesOn()).isEqualTo(theDayItMatured);
        assertThat(term.termMonths()).isEqualTo(TWELVE_MONTHS);
    }

    /**
     * Three more years of nightly sweeps leave the waiting term precisely where it was, while the
     * rolling term beside it settles a maturity every one of them.
     *
     * <p><strong>Which is how this class says "settled once, and neither re-settled for ever nor
     * skipped".</strong> A settlement row cannot be read over the API — it is the module's own
     * record and nothing lists it — so what is asserted is the pair of things that would show. If
     * the waiting maturity were being settled afresh, something about that account would move, and
     * nothing does; if the sweep had stopped settling maturities at all, the rolling account would
     * stand still, and it does not. Three years, because one would be a night and two would be a
     * coincidence.
     */
    @Test
    @Order(4)
    void three_years_of_sweeps_settle_the_waiting_maturity_once_while_the_rolling_term_keeps_rolling() {
        LocalDate theRollingMaturity = application.theTermOn(theRollingTerm).maturesOn();

        for (int year = 1; year <= 3; year++) {
            application.daysPass(A_YEAR_AND_A_BIT);
            application.runJob(THE_MATURITY_SWEEP);

            TheTermOnAnAccountView waiting = application.theTermOn(theWaitingTerm);
            assertThat(waiting.maturesOn())
                    .as("year %d: the waiting maturity is still the day it always was", year)
                    .isEqualTo(theDayItMatured);
            assertThat(waiting.matured()).isTrue();
            assertThat(waiting.locked())
                    .as("year %d: and the money was never locked away again", year)
                    .isFalse();
            assertThat(waiting.termMonths()).isEqualTo(TWELVE_MONTHS);
            assertThat(waiting.maturityAction()).isEqualTo("HOLD");
            assertThat(application.theAgreementOf(theWaitingTerm).version())
                    .as("year %d: on the version it agreed to", year)
                    .isEqualTo(theVersionItAgreedTo);
            assertThat(theMoneyIsFree()).as("year %d: still free to take out", year).isTrue();

            TheTermOnAnAccountView rolling = application.theTermOn(theRollingTerm);
            assertThat(rolling.maturesOn())
                    .as("year %d: the rolling term settled its maturity and started another", year)
                    .isEqualTo(theRollingMaturity.plusMonths(TWELVE_MONTHS * year));
            assertThat(rolling.locked())
                    .as("year %d: which is a lock, on a term nobody had to press for", year)
                    .isTrue();
        }
    }

    /**
     * Takes a little money out of the waiting account and answers whether it came.
     *
     * <p>The customer-visible reading of "the money is free", rather than the {@code locked} flag
     * beside it: a term that refused a withdrawal while calling itself unlocked would pass an
     * assertion about the flag and fail the only question a holder is actually asking. The balance
     * is read before and after, so that a withdrawal accepted and not carried out would fail too.
     */
    private static boolean theMoneyIsFree() {
        BigDecimal before = application.moneyBalanceOf(theWaitingTerm);
        ResponseEntity<JsonNode> out = application.tryToWithdraw(theWaitingTerm, whoseAccountItIs,
                WHAT_IS_TAKEN_OUT_TO_PROVE_IT_IS_FREE);
        if (out.getStatusCode() != HttpStatus.CREATED) {
            return false;
        }
        return application.moneyBalanceOf(theWaitingTerm)
                .compareTo(before.subtract(new BigDecimal(WHAT_IS_TAKEN_OUT_TO_PROVE_IT_IS_FREE)))
                == 0;
    }

    /**
     * Publishes a version of the notice account that turns it into a term of the length and ending
     * named, effective today.
     *
     * <p>Filled in from the version the product is offering now, figure for figure, because a
     * version carries nothing over from the one before it — so this changes the four figures the
     * test is about and sends the other seven back exactly as they were, which is what the
     * administration screen's pre-filled form does. The notice is set to nothing on purpose: two
     * conditions would stack, and what this class is about is the ending rather than the way out.
     */
    private static TermsVersionView republishTheNoticeAccountAsATermOf(int months, String ending,
                                                                       String annualRatePercent) {
        List<TermsVersionView> versions =
                application.versionsOfTheSavingsProduct(THE_ONE_REPUBLISHED_TO_WAIT);
        Map<String, Object> form = AnApplicationWithAClockToMove.theSameTermsAgain(
                versions.get(versions.size() - 1), application.theDateTheClockReads(),
                "Now a " + months + "-month term, and what happens at the end of it is " + ending
                        + ".");
        form.put("annualRatePercent", annualRatePercent);
        form.put("termMonths", String.valueOf(months));
        form.put("noticeDays", "0");
        form.put("earlyExitPenaltyDays", "90");
        form.put("maturityAction", ending);
        return application.publishAVersionOf(THE_ONE_REPUBLISHED_TO_WAIT, form);
    }

    /** The sentence the domain wrote, carried into the problem detail untouched. */
    private static String reasonGivenBy(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody()).as("the problem detail").isNotNull();
        return response.getBody().path("detail").asText();
    }
}
