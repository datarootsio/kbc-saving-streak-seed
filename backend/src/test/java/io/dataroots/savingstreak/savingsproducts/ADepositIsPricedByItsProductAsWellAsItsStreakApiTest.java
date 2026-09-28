package io.dataroots.savingstreak.savingsproducts;

import java.time.LocalDate;
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
 * A euro saved into a product that pays more is worth more points the moment it lands, and the
 * product's multiplier multiplies with the run of weeks rather than replacing it.
 *
 * <p><strong>The arithmetic is checkable in the head, which is the point of the figures
 * chosen.</strong> EUR 500 into a product paying a quarter more, on the first secured week of a run,
 * is 500 base points and 625 altogether. A week later the run pays 1.10 and the product still pays
 * 1.25, so EUR 50 is 68 — sixty-eight and three quarters, floored — and the rate written on the
 * deposit is 1.375, which is the two factors multiplied and nothing rounded.
 *
 * <p><strong>The EUR 7 deposit is the one that would catch a second flooring.</strong> Whole euros
 * first, then both rates, then floor once: {@code floor(7 × 1.375)} is 9. Flooring between the two
 * factors, which is what folding the product in as a second call to the ledger would do, gives
 * {@code floor(floor(7 × 1.10) × 1.25)} — 8, because the 0.7 of a point the run earned is thrown
 * away before the product is allowed to multiply it. One point on one small deposit, and it is the
 * whole difference between pricing a deposit once and pricing it twice.
 *
 * <p><strong>Anke is the control, in the same application and on the same afternoon.</strong> Her
 * account was opened before this version was published, so it carries on under the version it was
 * opened with — where the multiplier is the multiple of one — and her EUR 500 earns exactly the 500
 * points it earned before this ticket existed, at a rate of 1.00 with 1.00 of it the product's. An
 * account on free savings earns what every account earned before, and the way to say that without
 * hoping is to say it beside an account that does not.
 *
 * <p>Its own application, for the two reasons that each demand one: a run of weeks can only be
 * counted from zero on a database nothing has been saved into, and publishing a version cannot be
 * undone — a third version of free savings left in the shared database would be there for every
 * test afterwards.
 */
class ADepositIsPricedByItsProductAsWellAsItsStreakApiTest extends ApiIntegrationTest {

    /** Free savings, which is what every savings account in this application is on. */
    private static final String FREE_SAVINGS = "INSTANT";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseCatalogueAndWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-deposit-priced-by-its-product"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_products_multiplier_multiplies_with_the_streak_and_the_points_are_floored_once() {
        // An account opened before anything was repriced, which is what every account in this
        // application is until somebody publishes. It is the control and it never moves.
        long anAccountOnTheOldTerms = app.savingsAccountOf(ANKE);

        aQuarterMoreForEveryEuroSavedIntoFreeSavings();

        // Opened after the publish, so it is opened on the version being sold today — which is what
        // makes this one account priced at a quarter more and Anke's account priced as it always was.
        String theirs = app.aCustomerOfItsOwn("a quarter more");
        long anAccountOnTheNewTerms = app.savingsAccountOf(theirs);

        // The first secured week of a run, so the ladder pays the ordinary rate and the only uplift
        // in the figure is the product's. 500 euros, a quarter more, 625 points.
        DepositView theFirstWeek = app.deposit(anAccountOnTheNewTerms, theirs, "500.00");
        assertThat(theFirstWeek.basePoints()).as("one point per whole euro, as ever").isEqualTo(500);
        assertThat(theFirstWeek.pointsEarned()).as("a quarter more than the euros").isEqualTo(625);
        assertThat(theFirstWeek.multiplierApplied()).isEqualByComparingTo("1.25");
        assertThat(theFirstWeek.productMultiplierApplied())
                .as("the whole of the uplift is the product's, because the run pays the ordinary rate")
                .isEqualByComparingTo("1.25");

        DepositView theControl = app.deposit(anAccountOnTheOldTerms, ANKE, "500.00");
        assertThat(theControl.pointsEarned())
                .as("an account on the terms it was opened under earns what it always earned")
                .isEqualTo(500);
        assertThat(theControl.multiplierApplied()).isEqualByComparingTo("1.00");
        assertThat(theControl.productMultiplierApplied())
                .as("a product that changes nothing is the multiple that changes nothing")
                .isEqualByComparingTo("1.00");

        app.aWeekPasses();

        // A second consecutive secured week, so the run pays 1.10 and the product still pays 1.25:
        // 1.375 altogether, and EUR 50 is 68 points rather than 68.75.
        DepositView theSecondWeek = app.deposit(anAccountOnTheNewTerms, theirs, "50.00");
        assertThat(theSecondWeek.multiplierApplied())
                .as("the run times the product, with nothing rounded away")
                .isEqualByComparingTo("1.375");
        assertThat(theSecondWeek.productMultiplierApplied()).isEqualByComparingTo("1.25");
        assertThat(theSecondWeek.basePoints()).isEqualTo(50);
        assertThat(theSecondWeek.pointsEarned()).isEqualTo(68);

        // The deposit that tells one flooring from two. 7 × 1.10 × 1.25 is 9.625, and the customer
        // is paid 9; flooring the run's 7.7 down to 7 before the product multiplies it pays 8.
        DepositView theOneThatWouldCatchASecondFlooring =
                app.deposit(anAccountOnTheNewTerms, theirs, "7.00");
        assertThat(theOneThatWouldCatchASecondFlooring.basePoints()).isEqualTo(7);
        assertThat(theOneThatWouldCatchASecondFlooring.pointsEarned())
                .as("whole euros first, then both rates, then floored once")
                .isEqualTo(9);

        // And the history says the same thing the deposit said, with the two factors still apart on
        // the row — which is the whole reason both are written down rather than only their product.
        List<DepositView> history = List.of(app.depositsInto(anAccountOnTheNewTerms));
        assertThat(history).as("three deposits into the repriced account").hasSize(3);
        assertThat(history)
                .allSatisfy(row -> assertThat(row.productMultiplierApplied())
                        .as("what the product contributed, on every row")
                        .isEqualByComparingTo("1.25"));
        DepositView theSecondWeekAsHistory = history.stream()
                .filter(row -> row.id().equals(theSecondWeek.id()))
                .findFirst()
                .orElseThrow();
        assertThat(theSecondWeekAsHistory.multiplierApplied())
                .as("the combined rate, read back exactly as it was answered")
                .isEqualByComparingTo(theSecondWeek.multiplierApplied());
    }

    /**
     * Publishes a version of free savings that pays a quarter more per euro saved and changes
     * nothing else, effective today.
     *
     * <p>Through the administration door a person would use, and built from the version the
     * catalogue is actually serving, so that the one figure this test is about is the one figure
     * that moved.
     */
    private static void aQuarterMoreForEveryEuroSavedIntoFreeSavings() {
        List<TermsVersionView> published = app.versionsOfTheSavingsProduct(FREE_SAVINGS);
        TermsVersionView theOneBefore = published.get(published.size() - 1);
        LocalDate today = app.theDateTheClockReads();
        Map<String, Object> form = AnApplicationWithAClockToMove.theSameTermsAgain(
                theOneBefore, today,
                "Every euro saved into free savings is now worth a quarter more in points. The "
                        + "rate, the notice and everything else are unchanged.");
        form.put("pointsMultiplier", "1.2500");
        TermsVersionView repriced = app.publishAVersionOf(FREE_SAVINGS, form);
        assertThat(repriced.pointsMultiplier()).isEqualByComparingTo("1.2500");
        assertThat(repriced.anniversaryRatePercent())
                .as("the anniversary rate is not what this test moved")
                .isEqualByComparingTo(theOneBefore.anniversaryRatePercent());
    }
}
