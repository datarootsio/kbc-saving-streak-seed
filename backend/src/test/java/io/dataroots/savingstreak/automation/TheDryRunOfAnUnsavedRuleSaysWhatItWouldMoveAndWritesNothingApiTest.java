package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DryRunView;
import io.dataroots.savingstreak.support.GoalView;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer can ask what a rule they have not saved would move today, and asking writes nothing —
 * no rule, no occurrence, no money moved.
 *
 * <p>User story 46, and the preview people actually want: the one they want before pressing save
 * rather than after.
 *
 * <p><strong>"Writes nothing" is asserted against the SQLite file rather than against the API.</strong>
 * Every table in the database is counted before and after, and the counts have to be identical. A
 * test that read the rule list back instead would be asking the application whether it had written
 * anything, which is the question it would get wrong if it had — and it would say nothing at all
 * about an occurrence, a deposit, a goal allocation or a points batch, any one of which a dry run
 * quietly firing the rule would leave behind.
 *
 * <p>Its own application for exactly that reason: the file the rest of the run shares has other
 * classes writing to it, and a count of every table in it would be a count of their work.
 *
 * <p><strong>A sweep is a promise here rather than an illustration</strong>, which is the one thing
 * this answers that the twelve-month preview cannot. The question is about today, so the balance is
 * the one in the account right now.
 */
class TheDryRunOfAnUnsavedRuleSaysWhatItWouldMoveAndWritesNothingApiTest extends ApiIntegrationTest {

    private static final String FIFTY_EUROS = "50.00";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWholeDatabaseThisTestCounts() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-dry-run-writes-nothing"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_fixed_amount_the_account_can_cover_says_what_it_would_move_and_leaves_the_file_alone() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        JdbcTemplate theFile = app.theApplicationsOwn(JdbcTemplate.class);
        Map<String, Long> before = everyRowInTheFile(theFile);
        BigDecimal balanceBefore = app.currentAccountBalanceOf(ANKE);

        DryRunView answer = app.dryRun(savingsAccount, RulesAsSomebodyWouldTypeThem
                .aFixedAmountEveryWeek(app.currentAccountOf(ANKE), "Fifty a week", "MONDAY",
                        FIFTY_EUROS));

        assertThat(answer.asAt())
                .as("the day the application's clock reads, so a trainer who wound it can see "
                        + "which day the answer is about")
                .isEqualTo(app.theDateTheClockReads());
        assertThat(answer.balance())
                .as("out of the balance actually in the account, sent so the subtraction can be "
                        + "read rather than taken on trust")
                .isEqualByComparingTo(balanceBefore);
        assertThat(answer.outcome())
                .as("the same word the occurrence would carry on the night it fired")
                .isEqualTo("MOVED");
        assertThat(answer.wouldMove().amount()).isEqualByComparingTo(FIFTY_EUROS);
        assertThat(answer.wouldMove().anIllustrationRatherThanAPromise())
                .as("a dry run is about today, so there is nothing left to be uncertain about")
                .isFalse();
        assertThat(answer.shortfall())
                .as("short of nothing, which is a different sentence from short of EUR 0,00")
                .isNull();
        assertThat(answer.wouldMove().leftUnallocated())
                .as("a rule with no split would deposit unallocated, exactly as a manual deposit "
                        + "does")
                .isEqualByComparingTo(FIFTY_EUROS);

        assertThat(everyRowInTheFile(theFile))
                .as("and not one row anywhere in the database changed: no rule, no occurrence, no "
                        + "deposit, no allocation, no points")
                .isEqualTo(before);
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and no money moved")
                .isEqualByComparingTo(balanceBefore);
        assertThat(app.rulesOn(savingsAccount))
                .as("and nothing was left standing on the account")
                .isEmpty();
    }

    @Test
    void a_sweep_says_what_todays_balance_would_give_and_where_the_split_would_put_it() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        GoalView bike = app.openAGoal(savingsAccount, "Bike for the dry run", "5000.00");
        GoalView holiday = app.openAGoal(savingsAccount, "Holiday for the dry run", "5000.00");
        JdbcTemplate theFile = app.theApplicationsOwn(JdbcTemplate.class);
        BigDecimal balance = app.currentAccountBalanceOf(ANKE);
        BigDecimal floor = balance.subtract(new BigDecimal("300.00")).setScale(2);
        Map<String, Long> before = everyRowInTheFile(theFile);

        DryRunView answer = app.dryRun(savingsAccount, RulesAsSomebodyWouldTypeThem.spreadAcross(
                RulesAsSomebodyWouldTypeThem.everythingAboveAFloorEveryWeek(
                        app.currentAccountOf(ANKE), "Sweep the rest", "MONDAY",
                        floor.toPlainString()),
                RulesAsSomebodyWouldTypeThem.inTurn(
                        RulesAsSomebodyWouldTypeThem.aShareFor(bike.id(), "60"),
                        RulesAsSomebodyWouldTypeThem.aShareFor(holiday.id(), "40"))));

        assertThat(answer.outcome()).isEqualTo("MOVED");
        assertThat(answer.wouldMove().floor()).isEqualByComparingTo(floor);
        assertThat(answer.wouldMove().amount())
                .as("everything above the floor, out of the balance that is actually there")
                .isEqualByComparingTo("300.00");
        assertThat(answer.wouldMove().anIllustrationRatherThanAPromise())
                .as("and it is what would move rather than an illustration, because this question "
                        + "is about today")
                .isFalse();
        assertThat(answer.wouldMove().intoGoals())
                .extracting("goalId", "amount")
                .containsExactly(
                        Tuple.tuple(bike.id(), new BigDecimal("180.00")),
                        Tuple.tuple(holiday.id(), new BigDecimal("120.00")));
        assertThat(everyRowInTheFile(theFile))
                .as("and a split that named two goals moved nothing into either of them")
                .isEqualTo(before);
    }

    /**
     * A sweep whose account is already at or under its floor, which is arithmetic rather than a
     * failure, and a fixed amount the account cannot cover, which is the other way round. Being told
     * which of the two a rule would be is the whole reason for asking before saving it.
     */
    @Test
    void it_says_which_of_the_three_outcomes_the_rule_would_have_had() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        BigDecimal balance = app.currentAccountBalanceOf(ANKE);
        JdbcTemplate theFile = app.theApplicationsOwn(JdbcTemplate.class);
        Map<String, Long> before = everyRowInTheFile(theFile);

        DryRunView nothingAboveTheFloor = app.dryRun(savingsAccount, RulesAsSomebodyWouldTypeThem
                .everythingAboveAFloorEveryWeek(app.currentAccountOf(ANKE), "Too high a floor",
                        "MONDAY", balance.add(BigDecimal.ONE).setScale(2).toPlainString()));

        assertThat(nothingAboveTheFloor.outcome())
                .as("a balance at or under the floor has no surplus, and reporting arithmetic as a "
                        + "failure would train a customer to ignore the real ones")
                .isEqualTo("NOTHING_TO_MOVE");
        assertThat(nothingAboveTheFloor.wouldMove().amount()).isEqualByComparingTo("0.00");
        assertThat(nothingAboveTheFloor.shortfall()).isNull();

        BigDecimal moreThanIsThere = balance.add(new BigDecimal("100.00")).setScale(2);
        DryRunView notEnough = app.dryRun(savingsAccount, RulesAsSomebodyWouldTypeThem
                .aFixedAmountEveryWeek(app.currentAccountOf(ANKE), "More than is there", "MONDAY",
                        moreThanIsThere.toPlainString()));

        assertThat(notEnough.outcome())
                .as("a fixed amount moves all of itself or none of it")
                .isEqualTo("NOT_ENOUGH_MONEY");
        assertThat(notEnough.shortfall())
                .as("with what the account would have needed on top of what it holds")
                .isEqualByComparingTo("100.00");
        assertThat(notEnough.wouldMove().amount())
                .as("and the figure asked for, which is what the rule says rather than what it "
                        + "could have managed")
                .isEqualByComparingTo(moreThanIsThere);

        assertThat(everyRowInTheFile(theFile))
                .as("neither question wrote anything, including the one that would have failed")
                .isEqualTo(before);
    }

    /** How many rows every table in the database holds, so that "nothing was written" is a count. */
    private static Map<String, Long> everyRowInTheFile(JdbcTemplate theFile) {
        List<String> tables = theFile.queryForList(
                "select name from sqlite_master where type = 'table' and name not like 'sqlite_%' "
                        + "order by name", String.class);
        assertThat(tables)
                .as("a database with no tables in it would make every count below trivially equal")
                .contains("saving_rule", "rule_occurrence", "rule_split", "deposit");
        Map<String, Long> rows = new TreeMap<>();
        for (String table : tables) {
            Long count = theFile.queryForObject("select count(*) from \"" + table + "\"", Long.class);
            rows.put(table, count == null ? 0L : count);
        }
        return rows;
    }
}
