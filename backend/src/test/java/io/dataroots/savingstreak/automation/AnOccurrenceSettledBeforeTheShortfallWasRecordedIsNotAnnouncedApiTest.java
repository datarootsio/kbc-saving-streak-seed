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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * An occurrence that could not be honoured but carries no shortfall is refused by the nightly sweep,
 * out loud, and the occurrence beside it that does carry one is still announced.
 *
 * <p><strong>Why such a row exists at all.</strong> The shortfall is a new column on a table this
 * application had already created, and {@code ddl-auto=update} adds a new column to the rows already
 * in the file as null. An application that recorded {@code NOT_ENOUGH_MONEY} before this rule
 * existed — and the demonstration database a trainer runs the whole of this feature against is
 * exactly such a file — therefore holds occurrences that could not be honoured and have no figure
 * on them. The figure cannot be recovered afterwards either: it is the difference between what the
 * rule asked for on a day that has passed and what the account held that morning, and a rule's
 * amount is an instruction its holder can change the next day while a past balance is written down
 * nowhere. That irrecoverability is this feature's own argument for storing the figure, and it is
 * what makes this row permanent rather than a gap that fills in.
 *
 * <p><strong>What went wrong before this test existed.</strong> The sweep raised a notification for
 * such an occurrence with no amount on it. The panel writes its sentence out of the day and the
 * figure together, so the customer got the red icon, the unread tint, the timestamp and the account
 * name with not one word of what had happened — the exact opposite of a rule that cannot be honoured
 * saying so. So the sweep refuses it instead, and says why at WARN, as this application says every
 * refusal.
 *
 * <p><strong>The row is nulled through the database rather than through an endpoint</strong>,
 * because no endpoint can make one: every occurrence this application writes goes through
 * {@code RuleOccurrence.couldNotBeHonoured}, which cannot be given an outcome without a figure. The
 * one write below stands in for the upgrade that makes these rows, and everything around it — the
 * rules, the wound clock, both job runs, the notifications — goes through the endpoints a trainer
 * types. The counts are read out of the file for the same reason: what is asserted is which rows
 * exist, not what a response body says about them.
 *
 * <p><strong>The second sweep is the other half of the claim.</strong> One refusal is a refusal; a
 * refusal that quietly turns into an announcement on the next night would be the same defect a night
 * later. And its count says the read is bounded: the announced occurrence is behind the sweep's
 * place in the record and is not read again, so the second night considers one occurrence where the
 * first considered two.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
@ExtendWith(OutputCaptureExtension.class)
class AnOccurrenceSettledBeforeTheShortfallWasRecordedIsNotAnnouncedApiTest
        extends ApiIntegrationTest {

    private static final String THE_RULES_JOB = "fireSavingRulesDue";

    private static final String THE_NOTIFICATIONS_SWEEP = "raiseNotifications";

    private static final String THE_REASON = "AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN";

    /** More than the current account holds, so both rules fall due and neither can be honoured. */
    private static final BigDecimal MORE_THAN_IT_HOLDS = new BigDecimal("250.00");

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseRecordThisTestReachesInto() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-an-occurrence-with-no-shortfall-is-not-announced"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_sweep_refuses_the_occurrence_with_no_figure_and_still_announces_the_one_beside_it(
            CapturedOutput sweepLog) {
        long savingsAccount = app.savingsAccountOf(ANKE);
        long currentAccount = app.currentAccountOf(ANKE);
        BigDecimal held = app.currentAccountBalanceOf(ANKE);
        LocalDate theDayTheyFallDue = app.theDateTheClockReads().plusDays(2);
        String theDayOfTheWeek = theDayTheyFallDue.getDayOfWeek().name();
        String moreThanItHolds = held.add(MORE_THAN_IT_HOLDS).toPlainString();

        // Two rules that cannot be honoured on one morning. The older one keeps its figure and the
        // younger one loses it, so that the sweep's place in the record — the newest occurrence it
        // has announced — sits behind the refused row rather than in front of it, and the second
        // night genuinely reads the refused row again instead of never looking at it.
        SavingRuleView theOneThatKeepsItsFigure = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(currentAccount,
                        "More than I have", theDayOfTheWeek, moreThanItHolds));
        SavingRuleView theOneFromBeforeTheFigureExisted = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(currentAccount,
                        "More than I have, written down before the column did",
                        theDayOfTheWeek, moreThanItHolds));

        app.daysPass(2);
        app.runJob(THE_RULES_JOB);

        RuleOccurrenceView stillRemembered =
                theOnly(app.historyOf(savingsAccount, theOneThatKeepsItsFigure.id()));
        RuleOccurrenceView fromBeforeTheFigureExisted =
                theOnly(app.historyOf(savingsAccount, theOneFromBeforeTheFigureExisted.id()));
        assertThat(stillRemembered.outcome()).isEqualTo("NOT_ENOUGH_MONEY");
        assertThat(fromBeforeTheFigureExisted.outcome()).isEqualTo("NOT_ENOUGH_MONEY");
        assertThat(fromBeforeTheFigureExisted.id())
                .as("the row this test is about to strip is the younger of the two, so that the "
                        + "sweep's place in the record is left behind it")
                .isGreaterThan(stillRemembered.id());

        // The upgrade, stood in for: a NOT_ENOUGH_MONEY row written before the column existed.
        JdbcTemplate theFile = app.theApplicationsOwn(JdbcTemplate.class);
        theFile.update("update rule_occurrence set shortfall = null where id = ?",
                fromBeforeTheFigureExisted.id());
        assertThat(howManyRowsSay(theFile,
                "select count(*) from rule_occurrence where id = ? and shortfall is null",
                fromBeforeTheFigureExisted.id()))
                .as("the row really has no figure on it now, which is what the sweep is about to "
                        + "be handed")
                .isEqualTo(1);

        app.runJob(THE_NOTIFICATIONS_SWEEP);

        List<NotificationView> afterTheFirstSweep = onlyTheFailedTransfersToldTo(ANKE);
        assertThat(afterTheFirstSweep)
                .as("the occurrence with no figure on it cannot be described, so it is refused — "
                        + "and the one beside it, settled the same morning, is announced as it "
                        + "always was: one refusal does not silence the account")
                .hasSize(1);
        NotificationView said = afterTheFirstSweep.get(0);
        assertThat(said.occurrenceId()).isEqualTo(stillRemembered.id());
        assertThat(said.amount())
                .as("and it carries the figure, which is half of the sentence the panel writes")
                .isEqualByComparingTo(stillRemembered.shortfall());
        assertThat(said.occursOn()).isEqualTo(theDayTheyFallDue);

        assertThat(howManyRowsSay(theFile,
                "select count(*) from notification where occurrence_id = ?",
                fromBeforeTheFigureExisted.id()))
                .as("nothing was written about the occurrence with no figure — not a row with a "
                        + "null amount on it, which is what the panel would draw as a red alert "
                        + "with no words in it")
                .isZero();

        assertThat(sweepLog.getOut())
                .as("and the refusal is said out loud with what decided it, because a sweep that "
                        + "silently passed over a transfer that did not happen would be "
                        + "indistinguishable from one that never saw it")
                .contains("occurrence refused an automation notification occurrenceId="
                        + fromBeforeTheFigureExisted.id()
                        + " ruleId=" + theOneFromBeforeTheFigureExisted.id()
                        + " savingsAccountId=" + savingsAccount
                        + " dueOn=" + theDayTheyFallDue
                        + " reason=this occurrence was settled before what the account was short "
                        + "was recorded");

        app.runJob(THE_NOTIFICATIONS_SWEEP);

        assertThat(onlyTheFailedTransfersToldTo(ANKE))
                .as("a refusal that turned into an announcement on the next night would be the "
                        + "same empty row a night later: what cannot be recovered stays refused")
                .hasSize(1);
        assertThat(howManyRowsSay(theFile,
                "select count(*) from notification where occurrence_id is not null", null))
                .as("counted in the file rather than off the panel, because what is claimed is "
                        + "which rows exist")
                .isEqualTo(1);

        assertThat(sweepLog.getOut())
                .as("the first night read both occurrences and the second read only the one it had "
                        + "not spoken about: the sweep starts from where it got to rather than "
                        + "re-reading every failure an account has ever had, every night, for ever")
                .contains("occurrencesNotYetAnnouncedConsidered=2")
                .contains("occurrencesNotYetAnnouncedConsidered=1");
    }

    /** Everything the customer has been told about a transfer that did not happen, and nothing else. */
    private static List<NotificationView> onlyTheFailedTransfersToldTo(String customerName) {
        return Arrays.stream(app.notificationsOf(customerName))
                .filter(said -> THE_REASON.equals(said.reason()))
                .toList();
    }

    /** A count read straight out of the SQLite file, with or without one identifier to bind. */
    private static long howManyRowsSay(JdbcTemplate theFile, String question, Long binding) {
        Long count = binding == null
                ? theFile.queryForObject(question, Long.class)
                : theFile.queryForObject(question, Long.class, binding);
        return count == null ? 0L : count;
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
