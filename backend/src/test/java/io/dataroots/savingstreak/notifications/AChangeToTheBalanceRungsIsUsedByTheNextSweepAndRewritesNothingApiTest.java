package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.savingspolicy.ASchemeSomebodyAdministers;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rungs a balance is congratulated on reaching come from the scheme in force on the night the
 * sweep runs, and a notification raised under the old ladder is left exactly as it was said.
 *
 * <p>Three claims in one narrative, because they are three readings of one night. A customer is told
 * they reached EUR 100 under the ladder this application has always had. The bank then publishes a
 * ladder that has no EUR 100 rung at all — EUR 50 and EUR 150 where EUR 100 and EUR 500 were — and
 * the Monday comes. What the customer was told still reads EUR 100, word for word and figure for
 * figure, because an inbox is a log of the nights it was written on and no sweep reaches back into
 * one. The sweep that runs under the new ladder nevertheless raises nothing, because a balance of
 * EUR 100 stands on EUR 50 and the row saying EUR 100 reads back as EUR 50 too: the account has not
 * moved, and the only reason that comparison can be made at all is that the three rung functions
 * stay total across a change of rungs. And the next fifty euros put the balance on EUR 150, a rung
 * that did not exist last week, which is the sweep announcing against the figures published rather
 * than against the ones it was compiled with.
 *
 * <p><strong>A second sweep over the unchanged balance still raises nothing</strong>, which is the
 * operator's retry asked again on the other side of a published change: nothing about a figure
 * having moved makes a night worth repeating.
 *
 * <p>An application of its own, for two reasons that point the same way:
 * {@link AnApplicationWithAClockToMove} gives one about a balance counted from zero on a clock this
 * test winds, and {@code ASchemeSomebodyAdministers} gives the other about a published version being
 * a thing no tidying-up can take back. The form is built by that fixture rather than written out
 * here, because a version carries nothing over from the one before it and a second copy of the list
 * of figures is the copy that stops matching the door.
 */
class AChangeToTheBalanceRungsIsUsedByTheNextSweepAndRewritesNothingApiTest
        extends ApiIntegrationTest {

    /** The lowest rung this application has always had, and the one the customer is told about. */
    private static final String THE_OLD_LOWEST_RUNG = "100.00";

    /** What the balance reaches next, under a ladder that has a rung there and one that has not. */
    private static final String A_NEW_RUNG_THE_OLD_LADDER_NEVER_HAD = "150.00";

    /** What it takes to get from the one to the other. */
    private static final String THE_FIFTY_EUROS_BETWEEN_THEM = "50.00";

    /**
     * A ladder with neither of the two lowest rungs the seeded one has, so that the row already
     * written names a figure that is no longer a rung at all — which is the case the three rung
     * functions promise to stay readable across.
     */
    private static final List<String> A_LADDER_WITH_NEITHER_OLD_RUNG_ON_IT =
            List.of("50.00", "150.00", "1000.00", "2500.00", "5000.00", "10000.00");

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationWhoseSchemeThisTestMayPublishVersionsOf() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-rungs-that-move-under-a-sweep"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_sweep_judges_by_the_published_ladder_and_leaves_what_was_already_said_alone() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        app.deposit(savingsAccount, ANKE, THE_OLD_LOWEST_RUNG);

        sweep.runs();

        List<RaisedNotification> saidUnderTheOldLadder =
                sweep.whatWasSaidAboutTheBalanceOf(savingsAccount, ANKE);
        assertThat(saidUnderTheOldLadder)
                .as("the ladder in force tonight has a rung at a hundred euros, and the balance has "
                        + "reached it")
                .singleElement()
                .satisfies(reached -> {
                    assertThat(reached.reason())
                            .isEqualTo(NotificationReason.BALANCE_THRESHOLD_REACHED);
                    assertThat(reached.amount()).isEqualByComparingTo(THE_OLD_LOWEST_RUNG);
                });

        LocalDate theMondayTheLadderMoves = app.theNextMondayStillToCome();
        Map<String, Object> aLadderWithoutAHundredOnIt = ASchemeSomebodyAdministers
                .theSameSchemeAgain(app.theSchemeInForce(), theMondayTheLadderMoves,
                        "The rungs a balance is congratulated on reaching move down: fifty and a "
                                + "hundred and fifty where a hundred and five hundred were.");
        aLadderWithoutAHundredOnIt.put("balanceRungs", A_LADDER_WITH_NEITHER_OLD_RUNG_ON_IT);
        app.publishAVersionOfTheScheme(aLadderWithoutAHundredOnIt);
        app.theClockReaches(theMondayTheLadderMoves);

        sweep.runs();

        assertThat(sweep.whatWasSaidAboutTheBalanceOf(savingsAccount, ANKE))
                .as("what somebody was told is what they were told: the row still names a hundred "
                        + "euros, a figure this bank no longer publishes as a rung — and the sweep "
                        + "raises nothing, because a balance of a hundred stands on fifty and so "
                        + "does the row, read back through the ladder in force tonight")
                .isEqualTo(saidUnderTheOldLadder);

        sweep.runs();

        assertThat(sweep.whatWasSaidAboutTheBalanceOf(savingsAccount, ANKE))
                .as("and a second sweep over a balance that has not moved says nothing either, "
                        + "whichever ladder it is judged against")
                .isEqualTo(saidUnderTheOldLadder);

        app.deposit(savingsAccount, ANKE, THE_FIFTY_EUROS_BETWEEN_THEM);

        sweep.runs();

        assertThat(sweep.whatWasSaidAboutTheBalanceOf(savingsAccount, ANKE))
                .as("a hundred and fifty was not a rung last week and is one tonight, which is the "
                        + "sweep announcing against the ladder the bank published")
                .hasSize(2)
                .first()
                .satisfies(reached -> {
                    assertThat(reached.reason())
                            .isEqualTo(NotificationReason.BALANCE_THRESHOLD_REACHED);
                    assertThat(reached.amount())
                            .isEqualByComparingTo(new BigDecimal(A_NEW_RUNG_THE_OLD_LADDER_NEVER_HAD));
                });
    }
}
