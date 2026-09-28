package io.dataroots.savingstreak.whatifididthisinstead;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.SimulationView;
import io.dataroots.savingstreak.support.SimulationView.AMonthOfTheFutureView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.aScenarioCalled;
import static io.dataroots.savingstreak.whatifididthisinstead.AnAccountWithAFutureToAskAbout.takingOutOn;
import static io.dataroots.savingstreak.whatifididthisinstead.ASaverOnAProductWithAYearToPlayOut.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same test {@code TheSimulatorAgreesWithTheApplicationApiTest} is, asked of an account that is
 * <em>not</em> on free savings — which is the whole of what this ticket changed and the only thing
 * that can catch it having gone wrong.
 *
 * <p><strong>Why the existing conformance test cannot.</strong> Every account in this application
 * was on free savings until the savings-products feature, so the fold and the application agreed
 * about a multiplier of one, a tenth per whole euro on an anniversary, and no interest at all —
 * and they would go on agreeing about those three things however wrong the new per-product rules
 * were. A notice account pays 1.60% and a tenth more on every euro that lands; a fixed term pays
 * 2.40% and a quarter more. If the fold learned any of that in a second place rather than quoting
 * the one the sweep and the ledger read, this is where the two answers part company.
 *
 * <p><strong>Why a year rather than three months.</strong> Interest is monthly, so three months is
 * three postings and a year is twelve compounding into each other; and a deposit's first loyalty
 * anniversary falls at twelve months, at the rate the product names rather than at the flat tenth
 * that used to be the only rate there was. The twelfth row is the first one that has all three of
 * this feature's payments in it.
 *
 * <p><strong>Why one wind rather than a night at a time.</strong> The older conformance test walks
 * night by night and says at length why: a single catch-up credits every salary and then presents
 * every bill, which is not the same three months. Neither saver here has a salary, a bill or a
 * standing rule — each pays in once and is left alone — so there is nothing whose order a catch-up
 * could scramble. What accrues is interest periods and loyalty anniversaries, and both are settled
 * by ordinal counted from a fixed day, so a sweep that meets twelve at once settles the same twelve
 * a sweep meeting one a month would. That is what makes the single wind legitimate here and not
 * there, and it is the reason this test can afford a year.
 *
 * <p>Its own application and its own database, for the reason every clock-winding test has one: a
 * clock cannot be wound back, and a year cannot pass in the middle of somebody else's test.
 */
@TestMethodOrder(OrderAnnotation.class)
class TheSimulatorAgreesWithTheApplicationOnEveryProductApiTest extends ApiIntegrationTest {

    /** What each saver puts away, once, and never touches again. */
    private static final String WHAT_THEY_PUT_AWAY = "1200.00";

    private static ASaverOnAProductWithAYearToPlayOut app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestPlaysOut() {
        app = new ASaverOnAProductWithAYearToPlayOut(
                aDatabaseFileThatDoesNotExistYet("saving-streak-simulator-agrees-per-product"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * First, because it is the one assertion that needs a term still locked — and the test below
     * winds a year, after which no term in this application is.
     *
     * <p>The point is not that the simulator refuses. It is that it refuses <em>in the same
     * sentence</em>, because a branch that said something of its own would be a second statement of
     * the rule, which is the failure this whole ticket exists to prevent. So the sentence is not
     * written down here: it is taken from a withdrawal really attempted against the same account on
     * the same day, and the two are compared.
     */
    @Test
    @Order(1)
    void a_locked_term_refuses_a_branch_in_the_words_it_refuses_a_withdrawal() {
        ASaverOnAProductWithAYearToPlayOut.ASaverOnAProduct locked =
                app.aSaverOn("FIXED12", "Locked away", WHAT_THEY_PUT_AWAY);

        ResponseEntity<JsonNode> reallyTried = locked.tryingToTakeOut("100.00");
        assertThat(reallyTried.getStatusCode())
                .describedAs("a withdrawal out of a term that has not matured: %s",
                        reallyTried.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> askedAboutToday = locked.askingAbout(aScenarioCalled(
                "If I took a hundred out today",
                takingOutOn("100.00", app.theDateTheClockReads())));

        assertThat(reasonGivenBy(askedAboutToday))
                .describedAs("the branch is refused in the words the withdrawal screen uses, "
                        + "because there is one rule about what a term lets go and asking about a "
                        + "future is not a way round it")
                .isEqualTo(reasonGivenBy(reallyTried));

        // And the same rule met on a later day answers about that day rather than about today,
        // which is the only honest reading of a question about the future: a term that is up in
        // four months does not refuse a withdrawal somebody has asked about in six. The sentence is
        // the same sentence; the countdown inside it is the one the branch would actually meet.
        ResponseEntity<JsonNode> askedAboutNextMonth = locked.askingAbout(aScenarioCalled(
                "If I took a hundred out next month",
                takingOutOn("100.00", app.theDateTheClockReads().plusMonths(1))));

        assertThat(reasonGivenBy(askedAboutNextMonth))
                .describedAs("a month's worth of the wait has already gone by the time the branch "
                        + "reaches that day, and the refusal counts down to the same maturity")
                .isNotEqualTo(reasonGivenBy(askedAboutToday))
                .contains("matures on " + app.theDateTheClockReads().plusMonths(12));
    }

    /**
     * The other half of the same rule: a notice account refuses what the notice already given does
     * not cover, and it refuses a branch in the same words it refuses a withdrawal.
     *
     * <p>No notice has been given on this account at all, so there is nothing to cover anything —
     * which is the plainest case and the one a customer meets first, before they have learned that
     * giving notice is a thing they have to do.
     */
    @Test
    @Order(2)
    void a_notice_account_refuses_a_branch_in_the_words_it_refuses_a_withdrawal() {
        ASaverOnAProductWithAYearToPlayOut.ASaverOnAProduct waiting =
                app.aSaverOn("NOTICE32", "No notice given", WHAT_THEY_PUT_AWAY);

        ResponseEntity<JsonNode> reallyTried = waiting.tryingToTakeOut("100.00");
        assertThat(reallyTried.getStatusCode())
                .describedAs("a withdrawal with no notice standing on it: %s", reallyTried.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> onlyAsked = waiting.askingAbout(aScenarioCalled(
                "If I took a hundred out today",
                takingOutOn("100.00", app.theDateTheClockReads())));

        assertThat(reasonGivenBy(onlyAsked))
                .describedAs("one rule about what a notice account lets go, met in the branch and "
                        + "met at the withdrawal screen, wording it once")
                .isEqualTo(reasonGivenBy(reallyTried));
    }

    /**
     * Two products, one year, both asked before a cent moves and both checked after it has.
     *
     * <p>Both savers are opened and both projections are taken <em>first</em>, so that the single
     * wind below is the same year for each of them and neither is answered about a future the other
     * has already lived through.
     */
    @Test
    @Order(3)
    void a_year_pays_what_the_twelfth_month_promised_on_a_notice_account_and_on_a_fixed_term() {
        ASaverOnAProductWithAYearToPlayOut.ASaverOnAProduct onNotice =
                app.aSaverOn("NOTICE32", "Waiting a month", WHAT_THEY_PUT_AWAY);
        ASaverOnAProductWithAYearToPlayOut.ASaverOnAProduct onATerm =
                app.aSaverOn("FIXED12", "Locked for a year", WHAT_THEY_PUT_AWAY);
        LocalDate theDayTheyBothPaidIn = app.theDateTheClockReads();

        SimulationView noticeWasPromised = onNotice.theYearAhead();
        SimulationView theTermWasPromised = onATerm.theYearAhead();

        app.aWholeYearPassesFrom(theDayTheyBothPaidIn);
        app.everyNightlyRunThatAProjectionCanSee();

        theTwelfthMonthCameTrue(noticeWasPromised, onNotice);
        theTwelfthMonthCameTrue(theTermWasPromised, onATerm);
    }

    /**
     * The balance and the points the twelfth row promised, against the two figures the account now
     * reports.
     *
     * <p>Both halves matter and the second is the harder one: the euros go through the average
     * daily balance, the product's own annual rate and twelve floorings to the cent, while the
     * points go through the high-water mark, the streak ladder, the product's multiplier, the
     * double flooring and then a loyalty anniversary at a rate that is no longer a tenth. Each of
     * those is written down in exactly one place, and these two figures are the only evidence that
     * the fold is still reading those places rather than a copy of them.
     */
    private void theTwelfthMonthCameTrue(SimulationView promised,
                                         ASaverOnAProductWithAYearToPlayOut.ASaverOnAProduct saver) {
        LocalDate theTwelfthMonthClosesOn =
                promised.whereThisAccountStands().asAt().plusMonths(12);
        AMonthOfTheFutureView twelfth =
                promised.theYearAlreadyUnderWay().closingOn(theTwelfthMonthClosesOn);
        assertThat(twelfth)
                .describedAs("the twelfth row of the year %s was promised, counted from the day "
                        + "the window opened", saver.product())
                .isNotNull();

        BalancesView actually = saver.asTheAccountReportsItself();
        assertThat(actually.moneyBalance())
                .describedAs("the euros %s now holds are the euros its twelfth month promised — "
                        + "twelve months of interest at the rate its own agreement names, "
                        + "compounding into each other, and neither the fold nor the sweep got to "
                        + "choose the rate", saver.product())
                .isEqualByComparingTo(twelfth.balance());
        assertThat(actually.pointsBalance())
                .describedAs("and the points %s now holds, which is where a product's multiplier "
                        + "on the way in and its own anniversary rate for staying both land",
                        saver.product())
                .isEqualTo(twelfth.pointsStanding());
    }
}
