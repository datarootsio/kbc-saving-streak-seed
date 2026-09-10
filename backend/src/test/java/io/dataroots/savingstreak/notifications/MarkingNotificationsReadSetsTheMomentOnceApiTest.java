package io.dataroots.savingstreak.notifications;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.NotificationView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opening the panel marks everything in it read, in one call, and a notification keeps the moment it
 * was first read at.
 *
 * <p>The fourteenth user story of this feature: clearing the count is one action rather than one per
 * row, so every unread notification the customer holds is marked by the one call and the same list
 * comes straight back — a page that had to ask again afterwards would go on showing the count it just
 * cleared for as long as the second request took.
 *
 * <p>And the twenty-third story's rule, applied to a customer rather than to a sweep: sending the
 * call twice is safe. {@code readAt} is a moment and it is set once, exactly as
 * {@code PointsCredit.expire} keeps the moment a batch originally went, so a second call marks
 * nothing, leaves every moment where it was and answers the same list. A moment that was overwritten
 * would stop being a record of when the customer looked and become a record of when they last
 * asked.
 *
 * <p>Both halves matter and only one of them is obvious. That marking read sets a moment is easy;
 * that a notification read a week ago still says a week ago after tonight's call is the half a
 * straight {@code update ... set read_at = now} would break, and the half this test exists for.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test
 * counts a customer's notifications from zero, and the shared application's accounts hold whatever
 * the rest of the suite left in them.
 */
class MarkingNotificationsReadSetsTheMomentOnceApiTest extends ApiIntegrationTest {

    /** Exactly the lowest rung, so the first sweep has something to say. */
    private static final String ONTO_THE_LOWEST_RUNG = "100.00";

    /** Enough on top of it to land on the next rung, so a second sweep has something to say too. */
    private static final String ONTO_THE_RUNG_ABOVE_IT = "400.00";

    /** And enough again for a third, raised after the first two have already been read. */
    private static final String ONTO_THE_RUNG_ABOVE_THAT = "500.00";

    /**
     * One night between sweeps. A sweep stamps one moment on everything one run raises, so two
     * occasions have to be a night apart to be two moments — and a night is also what puts real
     * distance between the moment the first two were read and the moment the third was.
     */
    private static final int A_NIGHT = 1;

    /**
     * How near a moment this test judges "the moment the application thinks it is". The clock is
     * wound in whole days and a request takes milliseconds, so anything looser would still be
     * decisive and anything tighter would be asserting on how long a test took to run.
     */
    private static final Duration NEAR_ENOUGH_TO_NOW = Duration.ofMinutes(5);

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationNothingHasEverBeenSaidIn() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-marking-notifications-read"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * One story rather than four tests, because every step of it depends on the one before: an
     * already-read notification only exists once something has been read, and "changes nothing"
     * only means anything held against a record that was already there.
     */
    @Test
    void one_call_marks_every_unread_notification_and_a_second_call_changes_nothing() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        app.deposit(savingsAccount, ANKE, ONTO_THE_LOWEST_RUNG);
        sweep.runs();
        app.daysPass(A_NIGHT);
        app.deposit(savingsAccount, ANKE, ONTO_THE_RUNG_ABOVE_IT);
        sweep.runs();
        assertThat(app.notificationsOf(ANKE))
                .as("two occasions, neither of them looked at, which is what makes one call "
                        + "marking both of them worth asserting")
                .hasSize(2)
                .allSatisfy(unread -> assertThat(unread.readAt()).isNull());
        Instant whenTheCustomerLooked = app.theClockReads();

        List<NotificationView> answered = List.of(app.marksTheirNotificationsRead(ANKE));

        assertThat(answered)
                .as("the same list comes straight back, so opening the panel is one round trip "
                        + "rather than two")
                .hasSize(2);
        assertThat(answered)
                .as("every unread notification the customer held, marked by the one call: "
                        + "clearing the count is one action and not one per row")
                .allSatisfy(read -> {
                    assertThat(read.readAt()).isNotNull();
                    assertThat(Duration.between(whenTheCustomerLooked, read.readAt()).abs())
                            .as("read at the moment the application's clock reads, which is what "
                                    + "makes a wound-forward clock demonstrable")
                            .isLessThan(NEAR_ENOUGH_TO_NOW);
                });
        assertThat(answered.get(0).readAt())
                .as("one call is one moment, so everything it marked was read at the same moment")
                .isEqualTo(answered.get(1).readAt());

        List<NotificationView> asStored = List.of(app.notificationsOf(ANKE));

        assertThat(asStored).map(NotificationView::id)
                .as("the list the call answered with is the record it wrote, same rows and same "
                        + "order, and not a list assembled only for the reply")
                .containsExactlyElementsOf(answered.stream().map(NotificationView::id).toList());
        assertThat(asStored)
                .as("and the moment reached the record rather than only the answer")
                .allSatisfy(stored -> assertThat(stored.readAt()).isNotNull());
        // Taken from the record rather than from the answer above, and every later comparison in
        // this test is against this value. What the call held in memory and what came back out of
        // SQLite are two readings of one moment, and this test is about a moment not moving rather
        // than about how many decimals of a second survive a round trip.
        Instant theMomentTheFirstTwoWereRead = asStored.get(0).readAt();
        assertThat(asStored.get(1).readAt()).isEqualTo(theMomentTheFirstTwoWereRead);

        // A third occasion, a night later, so that the next call has one unread notification to
        // mark and two already-read ones to leave alone.
        app.daysPass(A_NIGHT);
        app.deposit(savingsAccount, ANKE, ONTO_THE_RUNG_ABOVE_THAT);
        sweep.runs();
        app.marksTheirNotificationsRead(ANKE);

        List<NotificationView> afterLookingAgain = List.of(app.notificationsOf(ANKE));

        assertThat(afterLookingAgain).hasSize(3);
        assertThat(afterLookingAgain.get(0).readAt())
                .as("the newest one was unread, so this call is when it was read")
                .isAfter(theMomentTheFirstTwoWereRead);
        assertThat(afterLookingAgain.subList(1, 3))
                .as("marking an already-read notification leaves the original moment alone: a "
                        + "moment set once is a record of when the customer looked, and "
                        + "overwriting it would make it a record of when they last asked")
                .allSatisfy(alreadyRead -> assertThat(alreadyRead.readAt())
                        .isEqualTo(theMomentTheFirstTwoWereRead));

        // A third call, with nothing left unread for it to mark. Every moment in the answer is one
        // this test has already seen in the record, so the two can be compared outright.
        List<NotificationView> answeredAThirdTime =
                List.of(app.marksTheirNotificationsRead(ANKE));

        assertThat(answeredAThirdTime)
                .as("a call over rows that have all been read changes nothing at all and reports "
                        + "the same list, so a retry is safe")
                .isEqualTo(afterLookingAgain);
        assertThat(app.notificationsOf(ANKE))
                .as("and nothing was written down either: read and unread is the whole state "
                        + "machine, and nothing was added, dropped or moved")
                .containsExactlyElementsOf(afterLookingAgain);
    }
}
