package io.dataroots.savingstreak.whatanaccounthascoming;

import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.TimelineView;
import io.dataroots.savingstreak.support.TimelineView.Event;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A deposit holding under ten euros has an anniversary and is paid nothing on it, so the bar carries
 * no marker for it — and the deposit still says so in the history.
 *
 * <p>The bar is a list of things that happen. A marker with a zero on it would be a promise of
 * nothing drawn as an event, and a customer counting markers would count one that costs and pays
 * them nothing at all.
 *
 * <p>Which is not the same as hiding the rule, and this asserts that too: the deposit reports its
 * anniversary and reports that it is worth nothing, beside the sentence on the history that says a
 * tenth of the euros is rounded down. The rounding stays a rule a customer can see rather than a bug
 * they suspect — it is simply stated where it belongs to the deposit that explains it rather than
 * drawn on a bar as an occasion.
 *
 * <p>The points those nine euros earned are on the bar all the same. They were earned and they have
 * twelve months of their own to run, whatever their deposit's anniversary is worth.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class AnAnniversaryWorthNothingIsNotOnTheBarApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationForADepositWorthNoBonus() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-anniversary-worth-nothing"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_deposit_under_ten_euros_puts_its_points_on_the_bar_and_no_anniversary() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        LocalDate paidInOn = app.theDateTheClockReads();
        assertThat(app.deposit(savingsAccount, ANKE, "9.00").pointsEarned())
                .as("nine whole euros earn nine points at the ordinary rate")
                .isEqualTo(9);

        TimelineView bar = app.timelineOf(savingsAccount);

        // One marker, not two. The nine points are going in twelve months and that is a thing that
        // happens; a tenth of nine euros rounds down to nothing, and that is not.
        assertThat(bar.events())
                .as("the points go, and nothing arrives, because nothing is what the anniversary pays")
                .containsExactly(new Event(paidInOn.plusYears(1), "POINTS_EXPIRE", 9));
        assertThat(bar.daysBonusesArrive())
                .as("no day on this bar is a day a bonus arrives")
                .isEmpty();

        // And the rule is still shown where it can be explained: the deposit has an anniversary, on
        // the day a calendar would give, and it is plainly worth nothing.
        DepositView[] history = app.depositsInto(savingsAccount);
        assertThat(history).hasSize(1);
        assertThat(history[0].nextAnniversaryOn())
                .as("the deposit still has an anniversary coming, and says which day it is")
                .isEqualTo(paidInOn.plusYears(1));
        assertThat(history[0].nextAnniversaryPoints())
                .as("worth nothing, said out loud rather than left off")
                .isEqualTo(0);
    }
}
