package io.dataroots.savingstreak.notifications;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.NotificationView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

class ReachingOneHundredNotifiesImmediatelyApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startTheApplication() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-immediate-notifications"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void reaching_exactly_one_hundred_notifies_before_any_job_runs() {
        long account = app.savingsAccountOf(ANKE);
        app.deposit(account, ANKE, "99.99");
        assertThat(notificationsFor(account, ANKE)).isEmpty();

        app.deposit(account, ANKE, "0.01");

        assertThat(notificationsFor(account, ANKE)).singleElement().satisfies(notification -> {
            assertThat(notification.reason()).isEqualTo("BALANCE_THRESHOLD_REACHED");
            assertThat(notification.amount()).isEqualByComparingTo("100.00");
            assertThat(notification.readAt()).isNull();
        });
        app.deposit(account, ANKE, "1.00");
        app.runJob(TheNotificationSweep.THE_JOB);
        assertThat(notificationsFor(account, ANKE)).hasSize(1);
    }

    @Test
    void a_deposit_that_skips_several_milestones_announces_the_highest_one_immediately() {
        long account = app.otherSavingsAccountOf(ANKE);
        app.deposit(account, ANKE, "550.00");

        assertThat(notificationsFor(account, ANKE)).singleElement().satisfies(notification ->
                assertThat(notification.amount()).isEqualByComparingTo("500.00"));
        app.runJob(TheNotificationSweep.THE_JOB);
        assertThat(notificationsFor(account, ANKE)).hasSize(1);
    }

    @Test
    void falling_below_one_hundred_and_reaching_it_again_is_a_new_occasion() {
        long account = app.savingsAccountOf(BRAM);
        app.deposit(account, BRAM, "100.00");
        assertThat(notificationsFor(account, BRAM)).hasSize(1);
        app.withdraw(account, BRAM, "1.00");

        app.deposit(account, BRAM, "1.00");

        assertThat(notificationsFor(account, BRAM)).hasSize(2).allSatisfy(notification -> {
            assertThat(notification.reason()).isEqualTo("BALANCE_THRESHOLD_REACHED");
            assertThat(notification.amount()).isEqualByComparingTo("100.00");
        });
    }

    private NotificationView[] notificationsFor(long account, String customer) {
        return java.util.Arrays.stream(app.notificationsOf(customer))
                .filter(notification -> notification.savingsAccountId() == account)
                .toArray(NotificationView[]::new);
    }
}
