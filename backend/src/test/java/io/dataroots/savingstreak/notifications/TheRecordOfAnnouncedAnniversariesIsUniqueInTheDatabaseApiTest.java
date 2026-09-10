package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.time.Instant;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One announcement per deposit, reason and anniversary is a rule the database keeps, not one this
 * application merely intends.
 *
 * <p>User story 24. The sweep also checks in Java, so a second run announces nothing without the
 * index; but the check and the guarantee are different things. Two runs of the job at the same
 * moment would both read the same empty set of already-announced anniversaries, and only a rule the
 * database keeps stops both of them from writing the row. So this test writes to the record
 * directly, past the sweep and past the check, and insists that the write is refused.
 *
 * <p>Which is the one test in this feature that reaches below the HTTP seam on purpose rather than
 * for want of an endpoint: its subject <em>is</em> the storage. Everything it sets up first — the
 * deposit, the wound clock, the sweep — goes through the endpoints, and the row it tries to
 * duplicate is one the sweep itself wrote.
 *
 * <p>The other two writes matter as much as the refusal. The index is partial, over the anniversary
 * rows only, so a second balance notification naming a rung already named has to be accepted: a
 * balance that crosses EUR 1.000, falls back and crosses again is three occasions, and an index over
 * the whole table would refuse the third. And the <em>other</em> reason for the same deposit and the
 * same day has to be accepted too, because that pairing is the escalation this feature exists for.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives, and because
 * this test leaves rows in the record that no rule would have written.
 */
class TheRecordOfAnnouncedAnniversariesIsUniqueInTheDatabaseApiTest extends ApiIntegrationTest {

    private static final String WHAT_THE_DEPOSIT_HOLDS = "200.00";

    /** Twenty-five days short of a year, as the other anniversary tests use. */
    private static final int DAYS_UNTIL_THE_ANNIVERSARY_IS_NEAR = 340;

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationWhoseRecordThisTestWritesToDirectly() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-one-announcement-per-anniversary"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_index_refuses_a_second_announcement_of_one_anniversary_under_one_reason() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        app.deposit(savingsAccount, ANKE, WHAT_THE_DEPOSIT_HOLDS);
        app.daysPass(DAYS_UNTIL_THE_ANNIVERSARY_IS_NEAR);
        sweep.runs();

        RaisedNotification announced =
                sweep.whatWasSaidAboutAnAnniversaryIn(savingsAccount, ANKE).get(0);
        assertThat(announced.reason())
                .as("the deposit is the only one in the pot, so it is first in line")
                .isEqualTo(NotificationReason.LOYALTY_BONUS_AT_RISK);
        NotificationRepository record = sweep.theRecord();
        long customerId = app.customerIdOf(ANKE);
        Instant now = app.theClockReads();

        assertThatThrownBy(() -> record.saveAndFlush(Notification.anniversaryAtRiskFor(
                customerId, savingsAccount, announced.depositId(), announced.occursOn(),
                announced.points(), now)))
                .as("the same deposit, the same reason and the same day, written past the sweep's "
                        + "own check — which is what two sweeps racing each other would do")
                .isInstanceOf(DataAccessException.class)
                // SQLite's own words, and the reason they are asserted on rather than only the
                // exception type: the dialect hands this back as a JpaSystemException rather than a
                // DataIntegrityViolationException, so the type alone would also be satisfied by a
                // write that failed for some entirely different reason. The three columns named in
                // the message are the guarantee this test is about.
                .hasMessageContaining("UNIQUE constraint failed")
                .hasMessageContaining("notification.deposit_id")
                .hasMessageContaining("notification.reason")
                .hasMessageContaining("notification.occurs_on");

        assertThatCode(() -> record.saveAndFlush(Notification.anniversaryComingFor(
                customerId, savingsAccount, announced.depositId(), announced.occursOn(),
                announced.points(), now)))
                .as("the other reason for the same day is the escalation, which is the "
                        + "notification worth having and is why the reason is part of the key")
                .doesNotThrowAnyException();

        assertThatCode(() -> {
            record.saveAndFlush(Notification.balanceRungReached(
                    customerId, savingsAccount, new BigDecimal("100.00"), now));
            record.saveAndFlush(Notification.balanceRungReached(
                    customerId, savingsAccount, new BigDecimal("100.00"), now));
        })
                .as("the index is partial, over the anniversary rows only: a rung crossed twice "
                        + "must be announced twice, and an index over the whole table would refuse "
                        + "the second")
                .doesNotThrowAnyException();
    }
}
