package io.dataroots.savingstreak.deposithistory;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a deposit earned is history, and history does not move when the clock does.
 *
 * <p>This is the reason the rate a deposit was paid at is written down rather than worked out again
 * on every read. The run of weeks an account is on is derived from the ledger and the calendar, so it
 * moves the moment either of them does — a trainer who winds the clock past a few empty weeks has
 * lapsed the run, and the account is back to the ordinary rate. A deposit made while the run was
 * paying 1.10 was paid 1.10, and has to still say so afterwards: a history that re-derived its rates
 * would quietly rewrite what a customer was told.
 *
 * <p>Forwards and backwards, because participants do both. Forwards is the clock endpoint a trainer
 * uses. Backwards has no endpoint — the clock refuses to move that way, so that records already
 * written are never left dated in a future the application has walked back out of — and the way a
 * trainer actually rewinds is to clear the position the application wrote down and start it again,
 * which is what this test does. The deposits are then dated weeks ahead of the clock reading them,
 * which is the hardest thing the history is ever asked to survive.
 *
 * <p>Its own application and its own database, for the reasons {@link AnApplicationWithAClockToMove}
 * gives: a run of weeks can only be counted from zero on an account with no history, and a clock two
 * tests were both moving would leave each of them asserting against the other's week.
 */
class APastDepositKeepsWhatItEarnedApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-history-through-a-moving-clock");

    /** Empty weeks to wind past. One is already a lapse; three is a trainer demonstrating one. */
    private static final int WEEKS_OF_NOTHING = 3;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(DATABASE);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * One narrative in one test, because it is one claim: these figures, then time moved both ways,
     * then the same figures. Split into three tests it would depend on the order they ran in, and
     * the clock cannot be put back between them.
     */
    @Test
    void a_deposit_reports_what_it_was_paid_however_far_the_clock_is_wound_afterwards() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        // Two weeks in a row secured, so that the account has been paid at two different rates and a
        // history quoting one rate against both entries would be visibly wrong. The first week of a
        // run pays the ordinary rate by definition; the second pays a step more, and fifty euros at
        // 1.10 is fifty base points with five of uplift on top.
        DepositView firstWeek = app.deposit(savingsAccount, ANKE, "50.00");
        assertThat(firstWeek.multiplierApplied()).isEqualByComparingTo("1.00");
        assertThat(firstWeek.basePoints()).isEqualTo(50);
        assertThat(firstWeek.streakBonusPoints()).isZero();

        app.aWeekPasses();
        DepositView secondWeek = app.deposit(savingsAccount, ANKE, "50.00");
        assertThat(secondWeek.multiplierApplied()).isEqualByComparingTo("1.10");
        assertThat(secondWeek.basePoints()).isEqualTo(50);
        assertThat(secondWeek.streakBonusPoints()).isEqualTo(5);

        List<DepositView> historyAsItWas = List.of(app.depositsInto(savingsAccount));

        // Forwards, past enough empty weeks to lapse the run. What the account is worth now does
        // change: that is the derivation doing its job, and it is what makes the rest of this test
        // worth asserting.
        for (int week = 0; week < WEEKS_OF_NOTHING; week++) {
            app.aWeekPasses();
        }
        assertThat(app.balancesOf(savingsAccount).currentMultiplier())
                .as("the rate the account is on now, which the lapse takes back to the ordinary one")
                .isEqualByComparingTo("1.00");

        // And what the two deposits say they were paid does not change with it — neither the entry
        // paid at the ordinary rate nor the one paid a step above it.
        assertThat(app.depositsInto(savingsAccount)).satisfies(theHistoryThatWas(historyAsItWas));
        stillReadsAsItWasAnswered(firstWeek, savingsAccount);
        stillReadsAsItWasAnswered(secondWeek, savingsAccount);

        // Backwards: the position the clock wrote down is cleared while nothing is running, and the
        // application comes back up standing at the real moment — weeks behind the deposits it is
        // now being asked about.
        rewindTheClockByForgettingWhereItWas();
        assertThat(app.depositsInto(savingsAccount)).satisfies(theHistoryThatWas(historyAsItWas));
        stillReadsAsItWasAnswered(firstWeek, savingsAccount);
        stillReadsAsItWasAnswered(secondWeek, savingsAccount);
    }

    /**
     * The entry for a deposit, still saying the four figures the deposit was answered with — and the
     * moment it happened, so that a history rewritten to suit the clock could not pass by moving the
     * deposit instead of its points.
     */
    private static void stillReadsAsItWasAnswered(DepositView made, long savingsAccountId) {
        DepositView entry = Arrays.stream(app.depositsInto(savingsAccountId))
                .filter(listed -> made.id().equals(listed.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "deposit " + made.id() + " has gone from the history of savings account "
                                + savingsAccountId));
        assertThat(entry.basePoints()).isEqualTo(made.basePoints());
        assertThat(entry.streakBonusPoints()).isEqualTo(made.streakBonusPoints());
        assertThat(entry.multiplierApplied()).isEqualByComparingTo(made.multiplierApplied());
        assertThat(entry.pointsEarned()).isEqualTo(made.pointsEarned());
        assertThat(entry.depositedAt()).isEqualTo(made.depositedAt());
    }

    /**
     * The history it was, entry for entry and in the order it was in — so that "unchanged" means the
     * whole list and not only the two deposits this test can name.
     *
     * <p>Figures out to SQLite and back carry whatever scale a float kept, so 1.10 and 1.1 are
     * compared as the same rate and 50.00 and 50 as the same amount of money.
     */
    private static Consumer<DepositView[]> theHistoryThatWas(List<DepositView> asItWas) {
        return history -> assertThat(history)
                .usingRecursiveFieldByFieldElementComparator(RecursiveComparisonConfiguration.builder()
                        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                        .build())
                .containsExactlyElementsOf(asItWas);
    }

    /**
     * Stops the application, clears the one row that says where the clock was left, and starts it
     * again on the same file — a rewind, arrived at the only way this application allows one.
     *
     * <p>The row is deleted while nothing is up. SQLite serialises writers, and a second connection
     * writing to a live database is how a test earns an intermittent SQLITE_BUSY.
     */
    private static void rewindTheClockByForgettingWhereItWas() {
        app.close();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE);
             Statement statement = connection.createStatement()) {
            assertThat(statement.executeUpdate("delete from clock_offset"))
                    .as("the recorded clock position this test is clearing")
                    .isEqualTo(1);
        } catch (SQLException e) {
            throw new AssertionError("could not clear the clock position in " + DATABASE, e);
        }
        app = new AnApplicationWithAClockToMove(DATABASE);
    }
}
