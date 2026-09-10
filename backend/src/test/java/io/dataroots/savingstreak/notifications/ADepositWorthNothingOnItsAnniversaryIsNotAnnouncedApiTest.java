package io.dataroots.savingstreak.notifications;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A deposit whose anniversary would pay nothing says nothing, and neither does a deposit whose money
 * has gone.
 *
 * <p>User story 10, and the guard on the rest of the feature: a EUR 9 deposit never warns anybody
 * about a bonus of zero points. The rounding is the same one the loyalty sweep logs as "a tenth of
 * what it still holds rounds down to no points" — points are whole here as they are everywhere else,
 * so a deposit holding under ten euros is worth nothing on any anniversary. It is not this module's
 * rounding: the rate is written down once, in {@code LoyaltyRate}, and this module only reads the
 * figure that comes out of it.
 *
 * <p>The two silences are different statements and both are tested here, because they arrive by
 * different routes. A deposit holding nine euros <em>has</em> a coming anniversary with nothing on
 * it, and it is the points figure that stops it being announced. A deposit that has been emptied has
 * no anniversary at all — the money has gone and there is nothing left for one to be paid on — and
 * it is outside the sweep's listing altogether.
 *
 * <p>The control is that the sweep announced something on the same run. Both cases here are a sweep
 * saying nothing, and a sweep that had thrown, or one whose window happened to reach nothing that
 * night, would satisfy them both; a third account whose deposit is near its anniversary and worth
 * something is what says the run was alive.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ADepositWorthNothingOnItsAnniversaryIsNotAnnouncedApiTest extends ApiIntegrationTest {

    /** A cent under the ten euros a single point needs, which is the boundary of the rounding. */
    private static final String LESS_THAN_A_POINT_IS_WORTH = "9.99";

    /** Enough to be worth something, so the account it is emptied out of had something to lose. */
    private static final String ENOUGH_TO_BE_WORTH_SOMETHING = "200.00";

    /** Twenty-five days short of a year, as the window test uses and for the same reason. */
    private static final int DAYS_UNTIL_THE_ANNIVERSARY_IS_NEAR = 340;

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-anniversary-worth-nothing"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void an_anniversary_that_rounds_down_to_nothing_and_one_whose_money_has_gone_are_both_silent() {
        long tooSmallToBeWorthAPoint = app.savingsAccountOf(ANKE);
        DepositView nineEuros = app.deposit(tooSmallToBeWorthAPoint, ANKE, LESS_THAN_A_POINT_IS_WORTH);
        long emptiedBeforeItsAnniversary = app.otherSavingsAccountOf(ANKE);
        app.deposit(emptiedBeforeItsAnniversary, ANKE, ENOUGH_TO_BE_WORTH_SOMETHING);
        // Bram's pot is the control: a deposit of the same age, still holding its money, whose
        // anniversary the same sweep does announce.
        long stillWorthSomething = app.savingsAccountOf(BRAM);
        app.deposit(stillWorthSomething, BRAM, ENOUGH_TO_BE_WORTH_SOMETHING);

        app.daysPass(DAYS_UNTIL_THE_ANNIVERSARY_IS_NEAR);

        // The money leaves the second pot, days short of the anniversary it would have been paid on.
        app.withdraw(emptiedBeforeItsAnniversary, ANKE, ENOUGH_TO_BE_WORTH_SOMETHING);
        assertThat(app.balancesOf(emptiedBeforeItsAnniversary).moneyBalance())
                .as("a test about a deposit holding no money needs the money to have actually left")
                .isEqualByComparingTo("0.00");

        sweep.runs();

        assertThat(app.depositsInto(tooSmallToBeWorthAPoint)[0].nextAnniversaryPoints())
                .as("the nine-euro deposit does have an anniversary coming, and it is worth nothing "
                        + "— which is the figure that decides the silence rather than an absence")
                .isEqualTo(0);
        assertThat(nineEuros.pointsEarned())
                .as("it earned its base points when it landed, so this is the anniversary rounding "
                        + "and not a deposit the application ignored")
                .isEqualTo(9);
        assertThat(sweep.whatWasSaidAboutAnAnniversaryIn(tooSmallToBeWorthAPoint, ANKE))
                .as("a bonus of zero points is not worth telling anybody about")
                .isEmpty();

        assertThat(app.depositsInto(emptiedBeforeItsAnniversary)[0].nextAnniversaryOn())
                .as("money that has gone has no anniversary left to reach")
                .isNull();
        assertThat(sweep.whatWasSaidAboutAnAnniversaryIn(emptiedBeforeItsAnniversary, ANKE))
                .as("a deposit holding no money has nothing at stake and nothing to be told about")
                .isEmpty();

        assertThat(sweep.whatWasSaidAboutAnAnniversaryIn(stillWorthSomething, BRAM))
                .as("the same run of the sweep did announce an anniversary, so the two silences "
                        + "above are the rule and not a sweep that said nothing at all")
                .hasSize(1);
    }
}
