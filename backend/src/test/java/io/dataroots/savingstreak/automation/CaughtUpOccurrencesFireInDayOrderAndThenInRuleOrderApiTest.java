package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RuleOccurrenceView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * A catch-up covering two rules fires every occurrence in the order the days fell, and the two that
 * fall on one morning in the order their rules were created.
 *
 * <p>User story 38, across rules rather than within one. The order is a promise rather than an
 * accident of which rule was read out of the database first: it is deterministic, it is
 * reconstructable from the log, and it needs no priority field anybody would have to maintain. It
 * also decides who gets the money when there is not enough for both — the second rule of a morning
 * is the one that finds the account short — so a run that dealt with the rules in whatever order
 * they came back in would be deciding that by accident.
 *
 * <p><strong>The whole run is asserted as one sequence, not as two histories.</strong> Each rule's
 * own history read on its own is in day order whatever order the run actually fired them in, so a
 * test that read them separately would pass against a run that fired all of one rule's months and
 * then all of the other's — which is exactly the arrangement this is here to rule out. The rows are
 * therefore merged and put back into the order they were written, which is what the identifier the
 * API already reports says.
 *
 * <p>{@link TwoRulesDueOnOneMorningFireInTheOrderTheyWereCreatedApiTest} asserts the same rule for a
 * single morning on an application that has not been wound; this is the half only a catch-up can
 * show, where interleaving and batching look identical from any one rule's history.
 *
 * <p>Fixed amounts rather than sweeps, and two different ones, so that the two rules can be told
 * apart by what moved as well as by which rule the row names.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class CaughtUpOccurrencesFireInDayOrderAndThenInRuleOrderApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final int MONTHS_OF_DOWNTIME = 3;

    private static final String THE_FIRST_RULES_AMOUNT = "10.00";
    private static final String THE_SECOND_RULES_AMOUNT = "7.00";

    /** The latest day of the month every month has, so that nothing here is about the clamp. */
    private static final int A_DAY_EVERY_MONTH_HAS = 28;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseDowntimeThisTestStages() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-caught-up-in-order"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_catch_up_fires_by_the_day_it_fell_and_then_by_the_order_the_rules_were_written() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        LocalDate theDayTheyWereLeftStanding = app.theDateTheClockReads();
        int dayOfMonth =
                Math.min(theDayTheyWereLeftStanding.getDayOfMonth(), A_DAY_EVERY_MONTH_HAS);

        // Both on the same day of the month, so that every day of this catch-up is a morning two
        // rules fall due on. Two rules on different days would never test the second half.
        SavingRuleView writtenFirst = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryMonth(app.currentAccountOf(ANKE),
                        "The one written first", String.valueOf(dayOfMonth), THE_FIRST_RULES_AMOUNT));
        SavingRuleView writtenSecond = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryMonth(app.currentAccountOf(ANKE),
                        "The one written second", String.valueOf(dayOfMonth), THE_SECOND_RULES_AMOUNT));

        windTo(theDayTheyWereLeftStanding.plusMonths(MONTHS_OF_DOWNTIME));
        app.runJob(THE_JOB);

        List<RuleOccurrenceView> asTheyWereFired = Stream.concat(
                        app.historyOf(savingsAccount, writtenFirst.id()).stream(),
                        app.historyOf(savingsAccount, writtenSecond.id()).stream())
                .sorted(Comparator.comparing(RuleOccurrenceView::id))
                .toList();

        assertThat(asTheyWereFired)
                .as("three months of downtime and two rules is six occurrences")
                .hasSize(MONTHS_OF_DOWNTIME * 2);
        assertThat(asTheyWereFired)
                .extracting(RuleOccurrenceView::dueOn, RuleOccurrenceView::ruleId)
                .as("day order first, and within a day the order the customer wrote the rules in — "
                        + "the whole night worked out and sorted before any of it fired, rather "
                        + "than each rule's months fired as that rule was read")
                .containsExactlyElementsOf(theMorningsTheyBothFellDueOn(
                        theDayTheyWereLeftStanding, dayOfMonth, writtenFirst, writtenSecond));

        assertThat(asTheyWereFired)
                .filteredOn(occurrence -> occurrence.ruleId() == writtenFirst.id())
                .allSatisfy(occurrence -> assertThat(occurrence.amount())
                        .isEqualByComparingTo(new BigDecimal(THE_FIRST_RULES_AMOUNT)));
        assertThat(asTheyWereFired)
                .filteredOn(occurrence -> occurrence.ruleId() == writtenSecond.id())
                .allSatisfy(occurrence -> assertThat(occurrence.amount())
                        .isEqualByComparingTo(new BigDecimal(THE_SECOND_RULES_AMOUNT)));
        assertThat(asTheyWereFired)
                .extracting(RuleOccurrenceView::depositId)
                .as("the deposits were made in that order too, which is the money side of the "
                        + "same promise: the identifiers the ledger handed out only ever go up")
                .doesNotContainNull()
                .isSorted();
    }

    /**
     * The six rows the run should have written, in order: for each of the three mornings, the rule
     * written first and then the rule written second.
     */
    private static List<Tuple> theMorningsTheyBothFellDueOn(
            LocalDate theDayTheyWereLeftStanding, int dayOfMonth,
            SavingRuleView writtenFirst, SavingRuleView writtenSecond) {
        return IntStream.rangeClosed(1, MONTHS_OF_DOWNTIME)
                .mapToObj(month ->
                        theDayTheyWereLeftStanding.plusMonths(month).withDayOfMonth(dayOfMonth))
                .flatMap(day -> Stream.of(
                        tuple(day, writtenFirst.id()),
                        tuple(day, writtenSecond.id())))
                .toList();
    }

    /** The clock forward to that day, through the endpoint a trainer would use. */
    private static void windTo(LocalDate day) {
        app.daysPass(ChronoUnit.DAYS.between(app.theDateTheClockReads(), day));
        assertThat(app.theDateTheClockReads()).isEqualTo(day);
    }
}
