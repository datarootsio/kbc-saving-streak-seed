package io.dataroots.savingstreak.notifications;

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
 * A customer reads everything that has been said to them, newest first, read and unread together.
 *
 * <p>The thirteenth and fifteenth user stories of this feature at once. Newest first because the
 * newest thing a rule decided is the one worth acting on. Read and unread together because the panel
 * is a record rather than an inbox that empties: a training application whose whole point is showing
 * a rule fire should not hide the evidence that it did the moment somebody glances at it, and there
 * is no dismissing a notification anywhere in this feature.
 *
 * <p>Also the seventeenth story, which is the one easy to get wrong in the direction that matters: a
 * customer nothing has ever been said to is answered with an empty list rather than with a refusal,
 * so that a quiet panel can be told apart from a page that failed to load.
 *
 * <p>And the field-for-field check on the contract. {@link TheNotificationSweep} reads what the
 * module holds and {@link AnApplicationWithAClockToMove#notificationsOf} reads what the endpoint
 * sends, so this test can hold one against the other: a figure the sweep wrote down and the response
 * record forgot to carry is invisible to every other test of this feature, because they all read the
 * module.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test
 * counts a customer's notifications from zero and asserts that a customer starts with none, and the
 * shared application's accounts hold whatever the rest of the suite left in them.
 */
class ACustomerReadsTheirNotificationsNewestFirstApiTest extends ApiIntegrationTest {

    /** Exactly the lowest rung, so the first sweep has one thing to say. */
    private static final String ONTO_THE_LOWEST_RUNG = "100.00";

    /** Enough on top of it to land on the next rung up, so the second sweep has another. */
    private static final String ONTO_THE_RUNG_ABOVE_IT = "400.00";

    /** The rung that second deposit lands on: EUR 100 already in, plus EUR 400. */
    private static final String THE_RUNG_ABOVE_IT = "500.00";

    /**
     * One night between the two sweeps, so that the second stamps a later moment than the first. A
     * sweep stamps one moment on everything one run raises, so two notifications from one run would
     * be ordered by identifier alone and this test would be asserting on a tie-break rather than on
     * newest-first.
     */
    private static final int A_NIGHT = 1;

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationNothingHasEverBeenSaidIn() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-a-customer-reads-their-notifications"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * A customer of this test's own, opened with nothing on them: no savings to stand on a rung, no
     * deposit with an anniversary to reach, no budget to go over. So the sweep has nothing to say
     * about them however many times the test below runs it, and "nothing has been said" stays a
     * fact about this customer rather than a fact about whichever household the seed wrote.
     *
     * <p>It used to be Bram, and he is no longer such a customer: the seeded households arrive
     * budgeted, and his groceries are over their envelope from the first sweep onwards. That is the
     * demonstration working, and the claim here is not about the demonstration — it is that an
     * inbox with nothing in it answers with an empty list rather than with a refusal, so the panel
     * can be told apart from a page that failed to load.
     */
    @Test
    void a_customer_nothing_has_been_said_to_reads_an_empty_list_rather_than_an_error() {
        assertThat(app.notificationsOf(app.aCustomerOfItsOwn("nothing said to them")))
                .as("a quiet panel has to be tellable apart from a page that failed to load, so "
                        + "nothing to say is an empty list and not a refusal")
                .isEmpty();
    }

    /**
     * One story rather than three tests, because each step of it depends on the one before: a read
     * notification only exists once something has been read, and a second row only exists once a
     * second night has passed. Split into separate methods it would be asserting on whatever order
     * JUnit happened to run them in.
     */
    @Test
    void everything_said_to_a_customer_comes_back_newest_first_read_and_unread_together() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        app.deposit(savingsAccount, ANKE, ONTO_THE_LOWEST_RUNG);
        sweep.runs();

        // Looked at, so that the older of the two rows below is a read one. Without this the test
        // would only be saying that unread notifications come back.
        app.marksTheirNotificationsRead(ANKE);

        app.daysPass(A_NIGHT);
        app.deposit(savingsAccount, ANKE, ONTO_THE_RUNG_ABOVE_IT);
        sweep.runs();

        List<NotificationView> panel = List.of(app.notificationsOf(ANKE));

        assertThat(panel)
                .as("both occasions are in the record, and the one that has been seen did not "
                        + "disappear from it")
                .hasSize(2);
        assertThat(panel.get(0).amount())
                .as("newest first: the rung reached last night comes before the one reached the "
                        + "night before")
                .isEqualByComparingTo(THE_RUNG_ABOVE_IT);
        assertThat(panel.get(0).readAt())
                .as("nobody has looked since it was raised")
                .isNull();
        assertThat(panel.get(1).amount())
                .isEqualByComparingTo(ONTO_THE_LOWEST_RUNG);
        assertThat(panel.get(1).readAt())
                .as("read, and still in the panel: the record survives having been seen")
                .isNotNull();
        assertThat(panel.get(0).raisedAt())
                .as("the order is the order the moments are in, and not the order the rows happen "
                        + "to be stored in")
                .isAfter(panel.get(1).raisedAt());

        theFiguresMatchTheSweepThatRaisedThemFieldForField(savingsAccount, panel);
    }

    /**
     * The contract carries every figure the sweep wrote down, and the same values.
     *
     * <p>Held against the module's own answer rather than against figures written out here again,
     * because the point is that nothing is lost between the two: a field the response record forgot
     * would be a field the page can never render, and every other test of this feature reads the
     * module and would never notice.
     *
     * <p>Ordered together as well as valued together, because newest-first is part of the contract
     * and a response carrying the right rows in the wrong order would pass a comparison that only
     * checked the set of them.
     */
    private static void theFiguresMatchTheSweepThatRaisedThemFieldForField(
            long savingsAccount, List<NotificationView> whatTheEndpointSent) {
        List<RaisedNotification> whatTheModuleHolds = sweep.whatWasSaidAbout(savingsAccount, ANKE);

        assertThat(whatTheEndpointSent)
                .as("every row the module holds is a row the customer is sent")
                .hasSameSizeAs(whatTheModuleHolds);
        for (int position = 0; position < whatTheModuleHolds.size(); position++) {
            RaisedNotification held = whatTheModuleHolds.get(position);
            NotificationView sent = whatTheEndpointSent.get(position);
            assertThat(sent.id()).isEqualTo(held.id());
            assertThat(sent.reason())
                    .as("the reason travels as its own enum name, which is the word the page "
                            + "switches on to write the sentence")
                    .isEqualTo(held.reason().name());
            assertThat(sent.savingsAccountId())
                    .as("which pot it is about, so the panel can name it and the account's own "
                            + "page can find the notice that concerns it")
                    .isEqualTo(held.savingsAccountId());
            assertThat(sent.depositId()).isEqualTo(held.depositId());
            // Compared rather than equalled, and only when there is one: which figures a
            // notification carries is decided by its reason, and a rung of EUR 100 written
            // "100.00" and read back "100.0" is the same rung.
            if (held.amount() == null) {
                assertThat(sent.amount())
                        .as("a figure the module left empty is a figure the contract leaves empty")
                        .isNull();
            } else {
                assertThat(sent.amount()).isEqualByComparingTo(held.amount());
            }
            assertThat(sent.points()).isEqualTo(held.points());
            assertThat(sent.occursOn()).isEqualTo(held.occursOn());
            assertThat(sent.raisedAt()).isEqualTo(held.raisedAt());
            assertThat(sent.readAt()).isEqualTo(held.readAt());
        }
    }
}
