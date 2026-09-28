package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.NotificationView;
import io.dataroots.savingstreak.support.RuleOccurrenceView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The nightly notifications sweep tells the customer about the transfer that did not happen, once,
 * and says nothing at all about the one that moved money or the sweep that found nothing above its
 * floor.
 *
 * <p>User stories 28 and 29, which are one decision read from both sides. A customer finds out from
 * the application rather than from a balance — and only about the thing worth finding out. Automation
 * is quiet by design: being told about every transfer that worked is exactly what somebody set up a
 * standing order to stop happening, and a sweep that moved nothing because the balance was already at
 * its floor did arithmetic rather than fail. Announcing either would train a customer to ignore the
 * one line that matters.
 *
 * <p><strong>Three rules on one morning, one notification.</strong> All three fall due on the same
 * day and all three are settled, so what separates them is the outcome and nothing else. A sweep that
 * announced "an occurrence that moved nothing" rather than "an occurrence that could not be honoured"
 * would raise two and fail here, which is the defect this arrangement exists to catch.
 *
 * <p><strong>The rules job writes no notification itself, and that is asserted before the sweep
 * runs.</strong> One producer of notifications is the whole of {@code NotificationsAreRaisedNightly}'s
 * argument, and a second writer in a second module would be invisible to any test that only looked
 * after both jobs had run.
 *
 * <p>Driven entirely over HTTP — both jobs by the names a trainer types, and the notifications
 * through the customer's own endpoint — because what this feature promises is that the customer is
 * told, and the panel is where they are told it.
 *
 * <p>In the automation package although the rule under test is the notifications sweep's: the setup
 * is three saving rules, and what a rule looks like is written down once, in
 * {@link RulesAsSomebodyWouldTypeThem}, which is this package's. Nothing here reaches into either
 * module.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class ATransferThatDidNotHappenIsAnnouncedOnceAndTheOnesThatWorkedAreNotApiTest
        extends ApiIntegrationTest {

    private static final String THE_RULES_JOB = "fireSavingRulesDue";

    private static final String THE_NOTIFICATIONS_SWEEP = "raiseNotifications";

    private static final String THE_REASON = "AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN";

    /** What the account will be short, and therefore the figure the notification has to carry. */
    private static final BigDecimal MORE_THAN_IT_HOLDS = new BigDecimal("250.00");

    /** Small enough that it moves whatever else has happened that morning. */
    private static final String AN_AMOUNT_IT_CAN_COVER = "10.00";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-a-transfer-that-did-not-happen-is-announced"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_sweep_announces_the_one_that_could_not_be_honoured_and_says_it_once() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        long currentAccount = app.currentAccountOf(ANKE);
        BigDecimal held = app.currentAccountBalanceOf(ANKE);
        LocalDate theDayTheyFallDue = app.theDateTheClockReads().plusDays(2);
        String theDayOfTheWeek = theDayTheyFallDue.getDayOfWeek().name();

        // A floor above what the account holds, so this one has nothing above it to sweep. Left
        // standing first so that it is settled before either of the others touches the balance.
        SavingRuleView theSweepWithNothingAboveIt = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.everythingAboveAFloorEveryWeek(currentAccount,
                        "Everything above more than I have", theDayOfTheWeek,
                        held.add(MORE_THAN_IT_HOLDS).toPlainString()));
        SavingRuleView theOneThatCannotBeHonoured = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(currentAccount,
                        "More than I have", theDayOfTheWeek,
                        held.add(MORE_THAN_IT_HOLDS).toPlainString()));
        SavingRuleView theOneThatWorks = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(currentAccount,
                        "Ten euros I do have", theDayOfTheWeek, AN_AMOUNT_IT_CAN_COVER));

        app.daysPass(2);
        app.runJob(THE_RULES_JOB);

        assertThat(onlyTheFailedTransfersToldTo(ANKE))
                .as("the rules job records what happened and writes no notification of its own: "
                        + "one producer is the whole point, and a second writer in a second module "
                        + "would split the one place this feature logs")
                .isEmpty();

        RuleOccurrenceView couldNotBeHonoured =
                theOnly(app.historyOf(savingsAccount, theOneThatCannotBeHonoured.id()));
        assertThat(couldNotBeHonoured.outcome()).isEqualTo("NOT_ENOUGH_MONEY");
        assertThat(theOnly(app.historyOf(savingsAccount, theSweepWithNothingAboveIt.id())).outcome())
                .as("the sweep's balance was under its floor, which is arithmetic and not a failure")
                .isEqualTo("NOTHING_TO_MOVE");
        assertThat(theOnly(app.historyOf(savingsAccount, theOneThatWorks.id())).outcome())
                .isEqualTo("MOVED");

        app.runJob(THE_NOTIFICATIONS_SWEEP);

        List<NotificationView> afterTheFirstSweep = onlyTheFailedTransfersToldTo(ANKE);
        assertThat(afterTheFirstSweep)
                .as("one of the three occurrences could not be honoured, and it is the only one the "
                        + "customer hears about — being told about the transfers that worked is "
                        + "what automation was set up to stop")
                .hasSize(1);
        NotificationView said = afterTheFirstSweep.get(0);
        assertThat(said.occurrenceId())
                .as("and it names the occurrence that could not be honoured, not one of the other "
                        + "two settled the same morning")
                .isEqualTo(couldNotBeHonoured.id());
        assertThat(said.savingsAccountId()).isEqualTo(savingsAccount);
        assertThat(said.occursOn())
                .as("the day the transfer was due, which is the day the customer is placing")
                .isEqualTo(theDayTheyFallDue);
        assertThat(said.amount())
                .as("and what the current account was short, which is the one figure they can act on")
                .isEqualByComparingTo(MORE_THAN_IT_HOLDS);
        assertThat(said.depositId())
                .as("nothing moved, so there is no deposit to name")
                .isNull();
        assertThat(said.points())
                .as("nothing moved, so nothing was earned")
                .isNull();
        assertThat(said.readAt())
                .as("nobody has looked at it yet")
                .isNull();

        app.runJob(THE_NOTIFICATIONS_SWEEP);
        app.runJob(THE_NOTIFICATIONS_SWEEP);

        List<NotificationView> afterThreeSweeps = onlyTheFailedTransfersToldTo(ANKE);
        assertThat(afterThreeSweeps)
                .as("one transfer that did not happen, one notification, however many nights the "
                        + "sweep runs afterwards — the occurrence stays in the record for ever, so "
                        + "a sweep with no memory of what it had said would announce it nightly")
                .hasSize(1);
        assertThat(afterThreeSweeps.get(0).id())
                .as("and it is the same row, not a fresh one written over the top of it")
                .isEqualTo(said.id());
    }

    /**
     * Everything the customer has been told about a transfer that did not happen, and nothing else.
     *
     * <p>Filtered by reason because the deposit the third rule made moves a savings balance, and a
     * balance that has landed on a rung is announced too. A test about this feature should not count
     * rows another rule raised, and would otherwise go green or red the day the rung ladder changed.
     */
    private static List<NotificationView> onlyTheFailedTransfersToldTo(String customerName) {
        return Arrays.stream(app.notificationsOf(customerName))
                .filter(said -> THE_REASON.equals(said.reason()))
                .toList();
    }

    /** The one occurrence a rule that has fallen due exactly once has, insisted on as exactly one. */
    private static RuleOccurrenceView theOnly(List<RuleOccurrenceView> history) {
        assertThat(history)
                .as("each of these rules fell due once, and a rule that fell due twice would make "
                        + "every outcome below ambiguous")
                .hasSize(1);
        return history.get(0);
    }
}
