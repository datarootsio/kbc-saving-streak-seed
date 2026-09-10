package io.dataroots.savingstreak.notifications;

import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A deposit whose anniversary is near is announced, carrying the day and what that day is worth at
 * what the deposit holds now — and a deposit whose anniversary is further off is not announced at
 * all.
 *
 * <p>User stories 6 and 7 of this feature. A customer eleven and a half months into a promise worth
 * twenty points has, until now, had exactly one way to learn that: open the savings account page,
 * scroll to the deposits table and read the small print under a points figure. This is the rule
 * speaking first.
 *
 * <p>The figures are the ones the deposits table already shows, and this test insists on that by
 * reading them off the deposit history and comparing. Nothing in the notifications module works out
 * what an anniversary is worth; the rate is written down once, in {@code LoyaltyRate}, and a second
 * tenth computed here would be a second answer to disagree with the first.
 *
 * <p>Both halves in one run of the sweep, on two accounts of the same customer: one deposit twenty-
 * five days short of its anniversary and one that landed today and is a year off. A single sweep
 * that speaks about one and stays silent about the other is the window doing the work, rather than
 * two runs either of which could have been silent for its own reasons.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test
 * winds the clock the better part of a year, which cannot be undone.
 */
class ADepositWithinThirtyDaysOfItsAnniversaryIsAnnouncedApiTest extends ApiIntegrationTest {

    /**
     * Enough euros to be worth something on an anniversary and not enough to reach the second rung,
     * so the account's balance notification is one row and the anniversary rows are the only others.
     */
    private static final String WHAT_THE_DEPOSIT_HOLDS = "200.00";

    /** A tenth of those euros, which is what its anniversary pays. */
    private static final long WHAT_THE_ANNIVERSARY_IS_WORTH = 20;

    /**
     * Twenty-five days short of a year, so the deposit's first anniversary is inside the thirty-day
     * window with five days to spare — near enough that no leap year or daylight-saving hour can
     * carry it over the boundary, which is asserted to the day in
     * {@link AnAnniversaryComingSoonTest}.
     */
    private static final int DAYS_UNTIL_THE_ANNIVERSARY_IS_NEAR = 340;

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-anniversary-coming-soon"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_anniversary_in_twenty_five_days_is_announced_and_the_one_a_year_off_is_not() {
        long nearItsAnniversary = app.savingsAccountOf(ANKE);
        DepositView aboutToPay = app.deposit(nearItsAnniversary, ANKE, WHAT_THE_DEPOSIT_HOLDS);

        app.daysPass(DAYS_UNTIL_THE_ANNIVERSARY_IS_NEAR);

        // A deposit made today into the customer's other pot, whose anniversary is a whole year off.
        long nowhereNearItsAnniversary = app.otherSavingsAccountOf(ANKE);
        app.deposit(nowhereNearItsAnniversary, ANKE, WHAT_THE_DEPOSIT_HOLDS);

        sweep.runs();

        List<RaisedNotification> said =
                sweep.whatWasSaidAboutAnAnniversaryIn(nearItsAnniversary, ANKE);
        assertThat(said)
                .as("one deposit, one anniversary, one notification")
                .hasSize(1);
        RaisedNotification announced = said.get(0);
        assertThat(announced.depositId())
                .as("which deposit's anniversary it is about")
                .isEqualTo(aboutToPay.id());
        assertThat(announced.points())
                .as("what the day is worth at what the deposit holds now — a tenth of the euros, "
                        + "worked out by Loyalty and carried through unchanged")
                .isEqualTo(WHAT_THE_ANNIVERSARY_IS_WORTH);
        assertThat(announced.occursOn())
                .as("the day, and the same day the deposits table already shows against it")
                .isEqualTo(app.depositsInto(nearItsAnniversary)[0].nextAnniversaryOn());
        assertThat(announced.points())
                .as("the same figure the deposits table already shows, because there is one place "
                        + "the rate is written down and this module is not it")
                .isEqualTo(app.depositsInto(nearItsAnniversary)[0].nextAnniversaryPoints());
        assertThat(announced.amount())
                .as("an anniversary is not a rung, so it carries no amount of money")
                .isNull();
        assertThat(announced.savingsAccountId())
                .as("which pot it is about, so the account's own page can carry the notice")
                .isEqualTo(nearItsAnniversary);
        assertThat(announced.readAt())
                .as("nobody has looked at it yet")
                .isNull();

        assertThat(sweep.whatWasSaidAboutAnAnniversaryIn(nowhereNearItsAnniversary, ANKE))
                .as("a deposit a year short of its anniversary has nothing worth saying yet, and "
                        + "the same sweep that announced the other one stayed silent about it")
                .isEmpty();
    }
}
