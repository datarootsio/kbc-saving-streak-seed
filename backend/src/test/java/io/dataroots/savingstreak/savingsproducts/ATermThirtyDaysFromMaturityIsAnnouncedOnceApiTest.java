package io.dataroots.savingstreak.savingsproducts;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.NotificationView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A fixed term whose day is a month off says so, once, and says which day and which product.
 *
 * <p><strong>Why the warning exists at all.</strong> What happens on a maturity morning was agreed
 * at the beginning: the twelve-month fixed term rolls into another twelve months at whatever the
 * bank is selling that day, and the maturities job does it at a quarter past three without asking
 * anybody. A customer who wants something else — the money in instant access, or simply out — has to
 * say so <em>before</em> that morning. So the notification is the month of warning that makes the
 * ending a decision rather than a discovery.
 *
 * <p><strong>The three nights this test names are the whole of the rule.</strong> Thirty-one days
 * out is silence, thirty days out is one line, and every night after that inside the window is
 * silence again. The first is the far edge of the window, the second is the moment, and the third is
 * what "once" means for a warning that stays true for a month.
 *
 * <p><strong>The date is read off the account rather than written into this test.</strong> When a
 * term is up is Products' answer — the day it started running plus the months its version names,
 * clamped — and a test that worked it out for itself would pass on a day the application disagreed
 * with it, which is exactly the class of bug the arithmetic living in one place exists to prevent.
 *
 * <p>Its own application with a clock to wind, for the reason every test that moves time has one: a
 * clock a year forward cannot be shared with tests standing on today.
 */
class ATermThirtyDaysFromMaturityIsAnnouncedOnceApiTest extends ApiIntegrationTest {

    private static final String THE_NOTIFICATIONS_JOB = "raiseNotifications";

    private static final String THE_FIXED_TERM = "FIXED12";

    private static final String A_TERM_IS_ABOUT_TO_MATURE = "A_TERM_IS_ABOUT_TO_MATURE";

    /** The window, and the one figure in this test that is the rule rather than a consequence. */
    private static final int DAYS_BEFORE_MATURITY_IT_IS_WORTH_SAYING = 30;

    /** Nights inside the window after the one it was first said on, each with the job running. */
    private static final int NIGHTS_THE_WARNING_STAYS_TRUE = 4;

    private static AnApplicationWithAClockToMove app;

    private static String whoHoldsTheTerm;

    private static long theirFixedTerm;

    private static LocalDate theDayItIsUp;

    @BeforeAll
    static void startAnApplicationWhoseDaysThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-a-term-about-to-mature"));
        whoHoldsTheTerm = app.aCustomerOfItsOwn("a term coming up for maturity");
        theirFixedTerm = app.openASavingsAccountOn(whoHoldsTheTerm, THE_FIXED_TERM);
        AnAgreementView agreement = app.theAgreementOf(theirFixedTerm);
        assertThat(agreement.maturesOn())
                .as("a twelve-month fixed term has a day it is up, and it is the whole subject of "
                        + "this test")
                .isNotNull();
        theDayItIsUp = agreement.maturesOn();
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void it_is_said_thirty_days_out_never_a_day_earlier_and_never_twice() {
        // A year off, which is where a term opened this morning stands. The sweep runs on a real
        // application with seeded customers in it, so the assertion is scoped to this account: what
        // it says is that nothing was said about *this* term, not that the night was quiet.
        app.runJob(THE_NOTIFICATIONS_JOB);
        assertThat(maturityWarningsAboutTheTerm())
                .as("a term a year off is not news")
                .isEmpty();

        windTheClockTo(theDayItIsUp.minusDays(DAYS_BEFORE_MATURITY_IT_IS_WORTH_SAYING + 1));
        app.runJob(THE_NOTIFICATIONS_JOB);
        assertThat(maturityWarningsAboutTheTerm())
                .as("thirty-one days is outside the window, and the far edge of a window is only a "
                        + "rule if the night before it is silent")
                .isEmpty();

        windTheClockTo(theDayItIsUp.minusDays(DAYS_BEFORE_MATURITY_IT_IS_WORTH_SAYING));
        app.runJob(THE_NOTIFICATIONS_JOB);

        List<NotificationView> said = maturityWarningsAboutTheTerm();
        assertThat(said).hasSize(1);
        NotificationView warning = said.get(0);
        assertThat(warning.savingsAccountId())
                .as("it is about the account whose term it is")
                .isEqualTo(theirFixedTerm);
        assertThat(warning.occursOn())
                .as("and it carries the day the term is up, which is the thing the customer has to "
                        + "decide before")
                .isEqualTo(theDayItIsUp);
        assertThat(warning.productCode()).isEqualTo(THE_FIXED_TERM);
        assertThat(warning.productName())
                .as("the product is named as well as coded, so a sentence can lead with the word "
                        + "the customer knows it by")
                .isEqualTo(app.theSavingsProduct(THE_FIXED_TERM).name());
        assertThat(warning.amount())
                .as("no money on it: what the term holds is on the account's own panel and is true "
                        + "now, and what this notice is about is a date")
                .isNull();
        assertThat(warning.readAt())
                .as("and nobody has read it yet")
                .isNull();

        // A second run on the same night, and then four more nights inside the window with the job
        // running on each. A term thirty days off is still coming on every one of them, so a rule
        // raised on the state rather than on the first sight of it would put five more identical
        // lines in front of somebody who has one decision to take.
        app.runJob(THE_NOTIFICATIONS_JOB);
        for (int night = 0; night < NIGHTS_THE_WARNING_STAYS_TRUE; night++) {
            app.daysPass(1);
            app.runJob(THE_NOTIFICATIONS_JOB);
        }

        assertThat(maturityWarningsAboutTheTerm())
                .as("one term, one maturity, one warning")
                .hasSize(1);
        assertThat(maturityWarningsAboutTheTerm().get(0).id())
                .as("and it is the very row that was written the first night, not a rewrite of it")
                .isEqualTo(warning.id());
    }

    /**
     * Moves the application's clock to a named day, saying how far that is rather than which day it
     * is, because the clock endpoint takes days and the day this test wants is always expressed
     * relative to a maturity it read off the account.
     */
    private static void windTheClockTo(LocalDate day) {
        long days = day.toEpochDay() - app.theDateTheClockReads().toEpochDay();
        assertThat(days)
                .as("this test only ever moves forwards, and a negative wind would mean the "
                        + "maturity it read is behind the clock it started on")
                .isPositive();
        app.daysPass(days);
    }

    /**
     * What has been said to this customer about this account's term coming up, and nothing else.
     *
     * <p>Filtered by the reason as well as by the account, because the sweep runs every rule over
     * every account on every run: a balance rung and a deposit's anniversary are raised by the same
     * job and would otherwise be counted as second warnings about a maturity.
     */
    private static List<NotificationView> maturityWarningsAboutTheTerm() {
        return Arrays.stream(app.notificationsOf(whoHoldsTheTerm))
                .filter(said -> A_TERM_IS_ABOUT_TO_MATURE.equals(said.reason()))
                .filter(said -> said.savingsAccountId() != null
                        && said.savingsAccountId() == theirFixedTerm)
                .toList();
    }
}
