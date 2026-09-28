package io.dataroots.savingstreak.savingsproducts;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.NotificationView;
import io.dataroots.savingstreak.support.RuleOccurrenceView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A saving rule that fires into a savings account its holder has closed is announced as well as
 * recorded, and it is announced as its own thing rather than as a shortfall.
 *
 * <p><strong>The gap this closes.</strong> Ticket 18 gave the outcome a value of its own and
 * deliberately left the notifications sweep asking only for the not-enough-money one, on the ground
 * that announcing this wanted deciding rather than inheriting — so the occurrence sat in the rule's
 * history and nobody was told. It is worth telling: this is the one failure that will happen again
 * on every due day until somebody changes the rule, because an account that has been closed cannot
 * be opened again.
 *
 * <p><strong>Its own reason, and no money on it.</strong> Reusing the shortfall's reason would have
 * been free and would have sent the customer off to top up a current account that was never the
 * problem, with a figure beside it that had to be nought or a lie. What this notice says instead is
 * the one thing that fixes it, and it carries the day the transfer was due and the occurrence in the
 * rule's history that it is about.
 *
 * <p>Its own application with a clock to wind, because the rule has to actually fall due.
 */
class ASavingRuleThatFiredIntoAClosedAccountIsAnnouncedApiTest extends ApiIntegrationTest {

    private static final String THE_RULES_JOB = "fireSavingRulesDue";

    private static final String THE_NOTIFICATIONS_JOB = "raiseNotifications";

    private static final String AN_AUTOMATIC_TRANSFER_HAD_NOWHERE_TO_GO =
            "AN_AUTOMATIC_TRANSFER_HAD_NOWHERE_TO_GO";

    private static final String AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN =
            "AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN";

    /** Well inside what a new customer's current account holds, so the balance is never the reason. */
    private static final String AN_AMOUNT_THE_ACCOUNT_CAN_EASILY_COVER = "25.00";

    /** Nights with both jobs running after the one the announcement was made on. */
    private static final int NIGHTS_THE_RULE_GOES_ON_STANDING = 3;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseDaysThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-a-rule-that-had-nowhere-to-go"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void it_is_announced_once_naming_the_day_and_never_as_a_shortfall() {
        String saver = app.aCustomerOfItsOwn("a rule announced with nowhere to go");
        long savingsAccount = app.savingsAccountOf(saver);
        LocalDate theDayItFallsDue = app.theDateTheClockReads().plusDays(2);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                aFixedAmountEveryWeekOn(app.currentAccountOf(saver), theDayItFallsDue));
        // Closed after the rule was left standing, which is the only order this happens in: an
        // account is emptied and closed long after somebody automated saving into it.
        app.closeTheSavingsAccount(savingsAccount);

        app.daysPass(2);
        app.runJob(THE_RULES_JOB);
        app.runJob(THE_NOTIFICATIONS_JOB);

        List<RuleOccurrenceView> history = app.historyOf(savingsAccount, rule.id());
        assertThat(history)
                .as("the day fell due and was written down, whether money moved or not")
                .hasSize(1);
        RuleOccurrenceView theOneThatHadNowhereToGo = history.get(0);

        List<NotificationView> said = whatWasSaidAboutTheRuleTo(saver, savingsAccount);
        assertThat(said).hasSize(1);
        NotificationView announced = said.get(0);
        assertThat(announced.reason())
                .as("its own reason, because 'you were short' and 'there is nowhere for it to go' "
                        + "are two different things to do about it")
                .isEqualTo(AN_AUTOMATIC_TRANSFER_HAD_NOWHERE_TO_GO);
        assertThat(announced.occurrenceId())
                .as("it names the occurrence in the rule's own history that it is about")
                .isEqualTo(theOneThatHadNowhereToGo.id());
        assertThat(announced.occursOn())
                .as("and the day the transfer was due")
                .isEqualTo(theDayItFallsDue);
        assertThat(announced.savingsAccountId()).isEqualTo(savingsAccount);
        assertThat(announced.amount())
                .as("no money at all: the current account was never short of anything, and a "
                        + "figure here would have to be nought or a lie")
                .isNull();
        assertThat(announced.balance()).isNull();
        assertThat(announced.depositId())
                .as("nothing moved, so there is no deposit to name")
                .isNull();

        // Three more nights with both jobs running. The rule is still standing and the account is
        // still closed, so a sweep reading the state rather than the record would say the same
        // thing again every morning — and the day it fell due is settled, so there is no second
        // occurrence for it either.
        for (int night = 0; night < NIGHTS_THE_RULE_GOES_ON_STANDING; night++) {
            app.daysPass(1);
            app.runJob(THE_RULES_JOB);
            app.runJob(THE_NOTIFICATIONS_JOB);
        }

        assertThat(app.historyOf(savingsAccount, rule.id()))
                .as("no second row for a day that was settled")
                .hasSize(1);
        assertThat(whatWasSaidAboutTheRuleTo(saver, savingsAccount))
                .as("one occurrence, one announcement")
                .hasSize(1);
        assertThat(whatWasSaidAboutTheRuleTo(saver, savingsAccount).get(0).id())
                .isEqualTo(announced.id());
    }

    /**
     * Everything said to this customer about a transfer on this account that did not happen, under
     * either of the two reasons.
     *
     * <p>Both reasons on purpose. The claim is that this occurrence was announced <em>and</em> that
     * it was not announced as a shortfall, and a read filtered to the new reason alone could not
     * see the second half of that.
     */
    private static List<NotificationView> whatWasSaidAboutTheRuleTo(String saver,
                                                                    long savingsAccount) {
        return Arrays.stream(app.notificationsOf(saver))
                .filter(said -> AN_AUTOMATIC_TRANSFER_HAD_NOWHERE_TO_GO.equals(said.reason())
                        || AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN.equals(said.reason()))
                .filter(said -> said.savingsAccountId() != null
                        && said.savingsAccountId() == savingsAccount)
                .toList();
    }

    /**
     * A fixed amount every week on the day this test is about, as somebody would type it — restated
     * here rather than borrowed from the Automation tests' fixture, which is package-private to
     * them.
     */
    private static Map<String, Object> aFixedAmountEveryWeekOn(long fromCurrentAccountId,
                                                               LocalDate day) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("name", "Into the account I closed");
        rule.put("fromCurrentAccountId", fromCurrentAccountId);
        rule.put("trigger", "WEEKLY");
        rule.put("dayOfWeek", day.getDayOfWeek().name());
        rule.put("howMuchMoves", "A_FIXED_AMOUNT");
        rule.put("amount", AN_AMOUNT_THE_ACCOUNT_CAN_EASILY_COVER);
        return rule;
    }
}
