package io.dataroots.savingstreak.savingsproducts;

import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.TermsVersionView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The flat tenth every anniversary used to pay becomes the rate the account's own terms name, and
 * three accounts sitting side by side are paid three different figures on the same EUR 500.
 *
 * <p><strong>Twelve percent of EUR 500 is 60 points, and the three wrong answers are worth naming.
 * </strong> The rate lives in the products module as 1 200 basis points and leaves it as 12.00 on a
 * card, and the figure the loyalty rule multiplies whole euros by is 0.1200. Hand it the basis
 * points and the deposit pays 600 000; hand it the percentage and it pays 6 000; leave the rule at
 * its old constant and it pays 50. Only one arrangement of the units pays 60, and that is what this
 * test pins.
 *
 * <p><strong>Nought is a rate a product may name, and this says so.</strong> An account on terms
 * whose anniversary pays nothing is promised nothing and paid nothing — on any amount, for ever,
 * because there is no threshold it could clear rather than a threshold of ten euros it falls under.
 * The catalogue refuses a points multiplier of nought, because nought <em>times</em> is not an
 * offer; it accepts an anniversary rate of nought, because a product that pays nothing for money
 * staying put is an agreement somebody could honestly publish.
 *
 * <p><strong>Anke is the control.</strong> Her account was opened before either version was
 * published, so it carries on under the version it was opened with, and her EUR 500 pays the 50
 * points it has always paid. What the page promises her and what the sweep pays her are the same
 * figure, which is the other half of what this test watches: the promise is read for each account
 * before the year passes and the payment is checked against it afterwards.
 *
 * <p>One test, because an anniversary is a year of clock away from a deposit and the clock only
 * goes forward. Its own application, because publishing cannot be undone and a fourth version of
 * free savings left in the shared database would be there for every test afterwards.
 */
class AnAnniversaryPaysAtTheRateOfTheProductTheMoneyIsSittingInApiTest extends ApiIntegrationTest {

    /** Free savings, which is what every savings account in this application is on. */
    private static final String FREE_SAVINGS = "INSTANT";

    private static final String THE_SWEEP = "payLoyaltyBonuses";

    /** Past the first anniversary of every deposit below, and short of the second. */
    private static final int DAYS_PAST_THE_FIRST_ANNIVERSARY = 366;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseCatalogueAndYearsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-anniversary-at-its-own-rate"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void an_anniversary_pays_twelve_percent_where_the_terms_say_twelve_and_nothing_where_they_say_nothing() {
        // Opened before anything was repriced, so it stays on the terms it was opened under: the
        // tenth this application has always paid.
        long theTenthItHasAlwaysPaid = app.savingsAccountOf(ANKE);

        anAnniversaryRateOf("12.00", "Anniversaries on free savings now pay 12% of the euros left "
                + "sitting in a deposit, up from 10%.");
        String theTwelvePercentCustomer = app.aCustomerOfItsOwn("twelve percent");
        long atTwelvePercent = app.savingsAccountOf(theTwelvePercentCustomer);

        anAnniversaryRateOf("0.00", "Free savings no longer pays anything for money simply staying "
                + "put. The rate on the money itself is unchanged.");
        String theNothingCustomer = app.aCustomerOfItsOwn("nothing for staying");
        long atNothing = app.savingsAccountOf(theNothingCustomer);

        // The same EUR 500 into each, so the only thing that can differ afterwards is the rate the
        // account's own terms name.
        DepositView paidAtATenth = app.deposit(theTenthItHasAlwaysPaid, ANKE, "500.00");
        DepositView paidAtTwelve = app.deposit(atTwelvePercent, theTwelvePercentCustomer, "500.00");
        DepositView paidAtNothing = app.deposit(atNothing, theNothingCustomer, "500.00");

        // None of the three versions touched the points multiplier, so all three deposits earned
        // exactly their euros on the way in. What follows is about the anniversary and nothing else.
        assertThat(paidAtATenth.pointsEarned()).isEqualTo(500);
        assertThat(paidAtTwelve.pointsEarned()).isEqualTo(500);
        assertThat(paidAtNothing.pointsEarned()).isEqualTo(500);

        // What each account is promised before a day of it has passed, which is the same rule the
        // sweep will apply read from the other end.
        assertThat(whatTheNextAnniversaryPromises(theTenthItHasAlwaysPaid)).isEqualTo(50);
        assertThat(whatTheNextAnniversaryPromises(atTwelvePercent))
                .as("twelve percent of five hundred euros, which is sixty points")
                .isEqualTo(60);
        assertThat(whatTheNextAnniversaryPromises(atNothing))
                .as("no amount at all earns a point where the terms pay nothing")
                .isEqualTo(0);

        long ankeBefore = app.pointsBalanceOf(ANKE);
        long twelveBefore = app.pointsBalanceOf(theTwelvePercentCustomer);
        long nothingBefore = app.pointsBalanceOf(theNothingCustomer);

        app.daysPass(DAYS_PAST_THE_FIRST_ANNIVERSARY);
        app.runJob(THE_SWEEP);

        assertThat(app.pointsBalanceOf(ANKE))
                .as("an account on the terms it was opened under is paid the tenth it always was")
                .isEqualTo(ankeBefore + 50);
        assertThat(app.pointsBalanceOf(theTwelvePercentCustomer))
                .as("sixty, not six and not six hundred: the rate is a fraction per whole euro")
                .isEqualTo(twelveBefore + 60);
        assertThat(app.pointsBalanceOf(theNothingCustomer))
                .as("a product that pays nothing for staying pays nothing")
                .isEqualTo(nothingBefore);
    }

    /** What the account's one deposit is promised on its next anniversary, off the history row. */
    private static long whatTheNextAnniversaryPromises(long savingsAccountId) {
        List<DepositView> history = List.of(app.depositsInto(savingsAccountId));
        assertThat(history).as("the one deposit in account " + savingsAccountId).hasSize(1);
        Long promised = history.get(0).nextAnniversaryPoints();
        assertThat(promised)
                .as("a deposit still holding money is promised a figure, even when it is nothing")
                .isNotNull();
        return promised;
    }

    /**
     * Publishes a version of free savings whose anniversary pays that percentage and which changes
     * nothing else, effective today.
     *
     * <p>Through the administration door a person would use, and built from the version the
     * catalogue is actually serving, so that the one figure this test is about is the one figure
     * that moved. An account opened after it is opened on it, which is how three accounts on one
     * product come to be living under three different agreements.
     */
    private static void anAnniversaryRateOf(String percentage, String whatChanged) {
        List<TermsVersionView> published = app.versionsOfTheSavingsProduct(FREE_SAVINGS);
        TermsVersionView theOneBefore = published.get(published.size() - 1);
        Map<String, Object> form = AnApplicationWithAClockToMove.theSameTermsAgain(
                theOneBefore, app.theDateTheClockReads(), whatChanged);
        form.put("anniversaryRatePercent", percentage);
        TermsVersionView repriced = app.publishAVersionOf(FREE_SAVINGS, form);
        assertThat(repriced.anniversaryRatePercent()).isEqualByComparingTo(percentage);
        assertThat(repriced.pointsMultiplier())
                .as("what a euro earns on the way in is not what this test moved")
                .isEqualByComparingTo(theOneBefore.pointsMultiplier());
    }
}
