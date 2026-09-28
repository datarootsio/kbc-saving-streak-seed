package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RuleOccurrenceView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One occurrence per rule per day due is a rule the database keeps, not one this application merely
 * intends — and the rule is put there by a start-up step that says whether it had to.
 *
 * <p>The job keeps a cursor and checks the record in Java, so a second run moves nothing without the
 * index; but the check and the guarantee are different things. Two runs of the job at the same moment
 * would both read the same cursor and the same empty set of settled days, and only a rule the
 * database keeps stops both of them from depositing — which on this table means money. So this test
 * writes to the record directly, past the job and past the check, and insists that the write is
 * refused.
 *
 * <p>Which makes it the one test in this ticket that reaches below the HTTP seam on purpose rather
 * than for want of an endpoint: its subject <em>is</em> the storage. Everything it sets up first —
 * the rule, the wound clock, the run — goes through the endpoints, and the row it tries to duplicate
 * is one the job itself wrote.
 *
 * <p>The two writes that have to be <em>accepted</em> matter as much as the refusal. Another day for
 * the same rule is next week's transfer, and the same day for another rule is the second rule of a
 * morning — an index over either column alone would refuse one of them.
 *
 * <p>And the second start is the other half of the claim. The index is created by a step that runs on
 * every start; all but the first must find it already there and do nothing, and say so, or a reviewer
 * has no way to confirm on any start but the first that the guarantee is in the file.
 */
@ExtendWith(OutputCaptureExtension.class)
class TheRecordOfWhatTheRulesDidIsUniqueInTheDatabaseApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String TWENTY_EUROS = "20.00";

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-one-occurrence-per-rule-per-day");

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseRecordThisTestWritesToDirectly() {
        app = new AnApplicationWithAClockToMove(DATABASE);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_index_refuses_a_second_occurrence_of_one_day_and_is_found_again_on_the_next_start(
            CapturedOutput startUpLog) {
        long savingsAccount = app.savingsAccountOf(ANKE);
        long currentAccount = app.currentAccountOf(ANKE);
        LocalDate theDayTheyMoveOn = app.theDateTheClockReads().plusDays(1);
        String itsDay = theDayTheyMoveOn.getDayOfWeek().name();
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(
                        currentAccount, "Twenty a week", itsDay, TWENTY_EUROS));
        // A second rule, on a day of its own that has not come round yet, so that the row this test
        // writes under it below is genuinely a pair the record has never held.
        SavingRuleView anotherRule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(currentAccount,
                        "Twenty a week, later in the week",
                        theDayTheyMoveOn.plusDays(3).getDayOfWeek().name(), TWENTY_EUROS));

        app.daysPass(1);
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> fired = app.historyOf(savingsAccount, rule.id());
        assertThat(fired)
                .as("the run this test is about to try to duplicate settled exactly one occurrence")
                .hasSize(1);
        LocalDate theDayAlreadySettled = fired.get(0).dueOn();
        assertThat(theDayAlreadySettled).isEqualTo(theDayTheyMoveOn);

        RuleOccurrenceRepository record = app.theApplicationsOwn(RuleOccurrenceRepository.class);
        Instant now = app.theClockReads();
        BigDecimal amount = new BigDecimal(TWENTY_EUROS);

        assertThatThrownBy(() -> record.saveAndFlush(RuleOccurrence.moved(
                rule.id(), theDayAlreadySettled, now, amount, 1L)))
                .as("the same rule and the same day due, written past the job's own check — which "
                        + "is what two runs racing each other would do")
                .isInstanceOf(DataAccessException.class)
                // SQLite's own words, and the reason they are asserted on rather than only the
                // exception type: the dialect hands this back as a JpaSystemException rather than a
                // DataIntegrityViolationException, so the type alone would also be satisfied by a
                // write that failed for some entirely different reason. The two columns named in
                // the message are the guarantee this test is about.
                .hasMessageContaining("UNIQUE constraint failed")
                .hasMessageContaining("rule_occurrence.saving_rule_id")
                .hasMessageContaining("rule_occurrence.due_on");

        assertThatCode(() -> record.saveAndFlush(RuleOccurrence.moved(
                rule.id(), theDayAlreadySettled.plusWeeks(1), now, amount, 2L)))
                .as("next week's transfer under the same rule is a different day, and an index over "
                        + "the rule alone would refuse it")
                .doesNotThrowAnyException();

        assertThatCode(() -> record.saveAndFlush(RuleOccurrence.moved(
                anotherRule.id(), theDayAlreadySettled, now, amount, 3L)))
                .as("the second rule of a morning is a different rule on the same day, and an "
                        + "index over the day alone would refuse it")
                .doesNotThrowAnyException();

        // Stopped before the second start, so that one application at a time has the file open: the
        // reopening is meant to be the ordinary "somebody restarted it" and not a test of what two
        // SQLite writers do to each other.
        app.close();
        app = null;

        try (ConfigurableApplicationContext secondStart = aSecondStartAgainst(DATABASE)) {
            assertThat(secondStart.getBean(RuleOccurrenceRepository.class)
                    .theRecordIsAlreadyUniquePerDayDue())
                    .as("the uniqueness is in the database file rather than only in this "
                            + "application's intentions, and the second start finds it there")
                    .isEqualTo(1);
        }

        assertThat(startUpLog.getOut())
                .as("and it says so, which is what makes the guarantee something a reviewer can "
                        + "confirm on any start rather than only on the first")
                .contains("the record of what the saving rules did is already unique per rule and "
                        + "day due index=one_occurrence_per_rule_per_day");
    }

    /**
     * A second application against the same file, with this module's own logging turned up so that
     * what its start-up step says is in the output this test reads.
     *
     * <p>Started here with its own arguments rather than through {@link AnApplicationWithAClockToMove},
     * because the level is the point: the ordinary start finds the index already there and says so
     * at DEBUG, and a reviewer reading a DEBUG start-up log is exactly who that line is for.
     *
     * <p>Command-line arguments rather than default properties, for the reason the walking skeleton
     * gives: defaults lose to {@code application.properties}, which would point this instance at the
     * real database.
     */
    private static ConfigurableApplicationContext aSecondStartAgainst(Path database) {
        return new SpringApplicationBuilder(SavingStreakApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + database,
                        "--spring.profiles.active=dev",
                        "--server.port=0",
                        "--logging.level.io.dataroots.savingstreak.automation=DEBUG");
    }
}
