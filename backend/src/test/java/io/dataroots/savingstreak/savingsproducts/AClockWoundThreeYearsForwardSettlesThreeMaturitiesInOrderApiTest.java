package io.dataroots.savingstreak.savingsproducts;

import java.nio.file.Path;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.TheTermOnAnAccountView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsproducts.ATermThatReachesItsDay.THE_MATURITY_SWEEP;
import static io.dataroots.savingstreak.savingsproducts.ATermThatReachesItsDay.THE_ROLLING_TERM;
import static io.dataroots.savingstreak.savingsproducts.ATermThatReachesItsDay.TWELVE_MONTHS;
import static io.dataroots.savingstreak.savingsproducts.ATermThatReachesItsDay.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A trainer winds the clock three years forward through a rolling twelve-month term and sees three
 * maturities settled, in order, each pinned to the version current on its own day.
 *
 * <p><strong>The ticket's demonstration, said as a test.</strong> The point is not that one maturity
 * is settled but that three are, in one run, in the order they fell — because each roll-over
 * produces the next maturity, and a sweep that settled only the one it could see would leave the
 * account two terms behind and permanently unlocked. Winding the clock rather than waiting is the
 * whole of what makes a twelve-month rule demonstrable in an afternoon.
 *
 * <p><strong>An application and a database of its own, and the twelve-month fixed term is left on
 * version 1 in it.</strong> That is deliberate and it is why this is not folded into the class
 * beside it: a roll-over pins whatever version the product is selling on the morning it rolls, so a
 * test that published a version of the fixed term partway through would be watching the account roll
 * onto a different ending rather than round again. Here the product sells one thing for three years,
 * and the account renews it three times.
 *
 * <p><strong>Every date is derived from the day the account was opened, read off the application's
 * own clock.</strong> Three maturities a year apart is the claim, and a test that wrote the dates
 * down would be asserting what day it was compiled on.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AClockWoundThreeYearsForwardSettlesThreeMaturitiesInOrderApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-three-maturities");

    /** What the account is opened with, out of the EUR 1 500 a new current account holds. */
    private static final String WHAT_IT_HOLDS = "400.00";

    /**
     * Three years and a little, in days, wound in one press.
     *
     * <p>Three whole terms have to be plainly behind the clock for three maturities to be due, and
     * the fourth has to be plainly ahead of it — otherwise this would be a test about where a
     * boundary falls rather than about a sweep settling every maturity it finds. Three years is a
     * little under 1 096 days, so this lands three or four weeks past the third maturity and
     * eleven months short of the fourth, whichever leap years the run happens to cross.
     */
    private static final int A_LITTLE_OVER_THREE_YEARS = 1_120;

    private static ATermThatReachesItsDay theirs;

    private static long theRollingTerm;
    private static LocalDate theDayItWasOpened;

    @BeforeAll
    static void openARollingTermAndLeaveIt() {
        theirs = new ATermThatReachesItsDay(DATABASE, "somebody who renews without thinking");
        theDayItWasOpened = theirs.theDateTheClockReads();
        theRollingTerm = theirs.openAnAccountOn(THE_ROLLING_TERM);
        theirs.payIn(theRollingTerm, WHAT_IT_HOLDS);
    }

    @AfterAll
    static void stopIt() {
        if (theirs != null) {
            theirs.close();
        }
    }

    /**
     * Three years pass and one sweep settles three maturities: the term is on its fourth, maturing a
     * year after the third, and the money is locked again.
     *
     * <p><strong>The fourth maturity is what proves all three were settled.</strong> Nothing is
     * counted here and nothing needs to be: a maturity four years after the account was opened can
     * only be reached by rolling three times, and a sweep that settled one would leave a maturity two
     * years in the past. The intermediate dates are unreachable through the API by design — what a
     * customer reads is the term they are on now — so the arithmetic is the assertion.
     *
     * <p>The day the account began is asserted to be exactly where it was, because re-dating it was
     * the alternative this feature rejected and it would have restarted the interest periods.
     */
    @Test
    @Order(1)
    void three_years_wound_forward_settle_three_maturities_and_leave_the_term_on_its_fourth() {
        theirs.daysPass(A_LITTLE_OVER_THREE_YEARS);

        theirs.run(THE_MATURITY_SWEEP);

        TheTermOnAnAccountView rolled = theirs.theTermOn(theRollingTerm);
        assertThat(rolled.termMonths()).isEqualTo(TWELVE_MONTHS);
        assertThat(rolled.maturesOn())
                .as("three terms served and a fourth running, all counted from the day it opened")
                .isEqualTo(theDayItWasOpened.plusMonths(4L * TWELVE_MONTHS));
        assertThat(rolled.matured()).isFalse();
        assertThat(rolled.locked()).isTrue();
        assertThat(rolled.daysLeft()).isGreaterThan(300);

        assertThat(theirs.theAgreementOf(theRollingTerm).productCode()).isEqualTo(THE_ROLLING_TERM);
        assertThat(theirs.theAgreementOf(theRollingTerm).openedOn()).isEqualTo(theDayItWasOpened);
        assertThat(theirs.theAgreementOf(theRollingTerm).maturesOn())
                .isEqualTo(theDayItWasOpened.plusMonths(4L * TWELVE_MONTHS));
    }

    /**
     * Each of the three was pinned to the version the product was selling on its own day, which here
     * is the one version it has sold throughout.
     *
     * <p>Asked of the catalogue rather than written down: the claim is "the version current on its
     * own day", and in a run where the product published nothing that is the version it is still
     * selling. A roll-over that carried the old version number forward would pass this and fail the
     * moment a version was published, which is why the class beside this one publishes one.
     */
    @Test
    @Order(2)
    void each_roll_over_is_pinned_to_the_version_the_product_was_selling_that_day() {
        assertThat(theirs.theAgreementOf(theRollingTerm).version())
                .isEqualTo(theirs.whatIsBeingSoldToday(THE_ROLLING_TERM));
    }

    /**
     * The money is locked away again, refused in a sentence naming the fourth maturity rather than
     * the first.
     *
     * <p>The refusal is the reading that matters, because it is the one a customer meets. A term
     * that had rolled in the agreement but not in the gate would answer this withdrawal with the
     * date it first matured — three years ago — which is the failure the whole "a roll-over must move
     * something" argument exists to prevent.
     */
    @Test
    @Order(3)
    void the_money_is_locked_again_and_the_refusal_names_the_fourth_maturity() {
        ResponseEntity<JsonNode> refused = theirs.tryToTakeOut(theRollingTerm, "1.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains(theDayItWasOpened.plusMonths(4L * TWELVE_MONTHS).toString())
                .doesNotContain(theDayItWasOpened.plusMonths(TWELVE_MONTHS).toString());
        assertThat(theirs.balanceOf(theRollingTerm)).isEqualByComparingTo(WHAT_IT_HOLDS);
    }

    /**
     * A second run over the same three mornings settles nothing.
     *
     * <p>Said here as well as in the class beside this one, because the failure looks different from
     * this side: a sweep that did not know the three maturities had been dealt with would not roll
     * the account a fourth time — the fourth maturity is in the future — but one that recomputed the
     * ordinals could write a second set of rows for the same three mornings, and the unique index is
     * what stops it. A run that threw would fail this test rather than a nightly job at a quarter
     * past three.
     */
    @Test
    @Order(4)
    void running_the_sweep_again_settles_nothing_and_throws_nothing() {
        theirs.run(THE_MATURITY_SWEEP);

        assertThat(theirs.theTermOn(theRollingTerm).maturesOn())
                .isEqualTo(theDayItWasOpened.plusMonths(4L * TWELVE_MONTHS));
        assertThat(theirs.theAgreementOf(theRollingTerm).version())
                .isEqualTo(theirs.whatIsBeingSoldToday(THE_ROLLING_TERM));
    }
}
