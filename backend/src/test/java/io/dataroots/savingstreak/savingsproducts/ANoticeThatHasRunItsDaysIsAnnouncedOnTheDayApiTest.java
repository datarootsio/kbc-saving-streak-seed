package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.NoticeView;
import io.dataroots.savingstreak.support.NotificationView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Notice given on an amount says so on the day it comes free, once, and says how much and which
 * notice.
 *
 * <p><strong>Why it is worth saying at all.</strong> A notice account's whole bargain is that the
 * customer counts thirty-two days and then the money is theirs. Nothing in this application counts
 * for them and nothing changes on the morning it runs out — readiness is a subtraction done at the
 * moment somebody asks, with no flag set and no sweep behind it — so without this notification the
 * only way to find out is to keep opening the page. This is the application doing the counting.
 *
 * <p><strong>The two nights this test names are the rule.</strong> One day short is silence and the
 * day itself is one line, which is the criterion said exactly: on the day, once. The nights
 * afterwards are what "once" means for a fact that never goes away — ready notice does not lapse, so
 * the same standing notice is read by every sweep from now until its money is spent.
 *
 * <p><strong>The figure is what the notice still covers.</strong> Not what it was given on: a notice
 * that has already paid for part of a withdrawal is good for what is left of it, and that is the
 * amount the customer can act on tonight.
 *
 * <p>Its own application with a clock to wind, because a notice period is thirty-two days and the
 * only way to stand on the far side of one is to move the clock there.
 */
class ANoticeThatHasRunItsDaysIsAnnouncedOnTheDayApiTest extends ApiIntegrationTest {

    private static final String THE_NOTIFICATIONS_JOB = "raiseNotifications";

    private static final String THE_NOTICE_ACCOUNT = "NOTICE32";

    private static final String A_NOTICE_HAS_BECOME_READY = "A_NOTICE_HAS_BECOME_READY";

    /** Well clear of any rung on the balance ladder, so nothing else this account does is news. */
    private static final String WHAT_THEY_PUT_IN = "640.00";

    /** Less than the balance, so the notice is a notice rather than the whole account leaving. */
    private static final String WHAT_THEY_GIVE_NOTICE_ON = "220.00";

    /** Nights after the one it came free on, each with the job running. */
    private static final int NIGHTS_READY_NOTICE_GOES_ON_STANDING = 4;

    private static AnApplicationWithAClockToMove app;

    private static String whoGaveNotice;

    private static long theirNoticeAccount;

    private static NoticeView theNotice;

    @BeforeAll
    static void startAnApplicationWhoseDaysThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-a-notice-that-has-run-its-days"));
        whoGaveNotice = app.aCustomerOfItsOwn("a notice coming free");
        theirNoticeAccount = app.openASavingsAccountOn(whoGaveNotice, THE_NOTICE_ACCOUNT);
        app.deposit(theirNoticeAccount, whoGaveNotice, WHAT_THEY_PUT_IN);
        theNotice = app.giveNoticeOn(theirNoticeAccount, WHAT_THEY_GIVE_NOTICE_ON);
        assertThat(theNotice.ready())
                .as("notice given this morning has its days still to run, which is what the rest of "
                        + "this test winds the clock through")
                .isFalse();
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void it_is_said_on_the_day_it_comes_free_and_never_again_while_it_stands() {
        app.runJob(THE_NOTIFICATIONS_JOB);
        assertThat(noticesAnnouncedOnTheAccount())
                .as("notice still running is not news")
                .isEmpty();

        // One day short of the day it comes free, which is the only night that tells "on the day"
        // apart from "some time around then".
        app.daysPass(theNotice.daysLeft() - 1L);
        app.runJob(THE_NOTIFICATIONS_JOB);
        assertThat(noticesAnnouncedOnTheAccount())
                .as("a notice with a day still to run has a day still to run")
                .isEmpty();
        assertThat(app.theNoticeOn(theirNoticeAccount).readyToTakeToday())
                .as("and the account agrees: nothing is ready yet")
                .isEqualByComparingTo(BigDecimal.ZERO);

        app.daysPass(1);
        app.runJob(THE_NOTIFICATIONS_JOB);

        List<NotificationView> said = noticesAnnouncedOnTheAccount();
        assertThat(said).hasSize(1);
        NotificationView announced = said.get(0);
        assertThat(announced.savingsAccountId()).isEqualTo(theirNoticeAccount);
        assertThat(announced.noticeId())
                .as("it names the notice it is about, which is what makes two notices given on one "
                        + "morning two things to say rather than one")
                .isEqualTo(theNotice.id());
        assertThat(announced.occursOn())
                .as("and the day it came free, which is the day the account said it would")
                .isEqualTo(theNotice.readyOn());
        assertThat(announced.amount())
                .as("with what the notice still covers, which is the figure the customer can act on")
                .isEqualByComparingTo(new BigDecimal(WHAT_THEY_GIVE_NOTICE_ON));
        assertThat(announced.productCode())
                .as("no product on it: this is about one notice inside an account, not about the "
                        + "agreement the account is on")
                .isNull();

        // A second run on the same night, and then four more nights with the job running. Ready
        // notice never lapses, so this notice is standing and ready on every one of them — a rule
        // raised on the state rather than on the first sight of it would say the same thing five
        // more times about money that has been free since Tuesday.
        app.runJob(THE_NOTIFICATIONS_JOB);
        for (int night = 0; night < NIGHTS_READY_NOTICE_GOES_ON_STANDING; night++) {
            app.daysPass(1);
            app.runJob(THE_NOTIFICATIONS_JOB);
        }

        assertThat(noticesAnnouncedOnTheAccount())
                .as("one notice, one announcement, however long it goes on standing")
                .hasSize(1);
        assertThat(noticesAnnouncedOnTheAccount().get(0).id())
                .as("and it is the row written on the day it came free")
                .isEqualTo(announced.id());
        assertThat(app.theNoticeOn(theirNoticeAccount).readyToTakeToday())
                .as("the money really is ready, which is what the announcement claimed")
                .isEqualByComparingTo(new BigDecimal(WHAT_THEY_GIVE_NOTICE_ON));
    }

    /**
     * What has been said to this customer about notice on this account coming free, and nothing
     * else — filtered by reason as well as by account, because one sweep runs every rule over every
     * account and this customer's deposit has a balance rung of its own to cross.
     */
    private static List<NotificationView> noticesAnnouncedOnTheAccount() {
        return Arrays.stream(app.notificationsOf(whoGaveNotice))
                .filter(said -> A_NOTICE_HAS_BECOME_READY.equals(said.reason()))
                .filter(said -> said.savingsAccountId() != null
                        && said.savingsAccountId() == theirNoticeAccount)
                .toList();
    }
}
