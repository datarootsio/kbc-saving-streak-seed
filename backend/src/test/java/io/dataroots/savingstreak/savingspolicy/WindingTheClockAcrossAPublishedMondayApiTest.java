package io.dataroots.savingstreak.savingspolicy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import io.dataroots.savingstreak.scheme.SchemeService;
import io.dataroots.savingstreak.scheme.TheSchemeAsPublished;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SchemeView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.savingspolicy.ASchemeSomebodyAdministers.theSameSchemeAgain;
import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The feature as a trainer demonstrates it in a room: wind the clock across the Monday a version was
 * announced for and watch what the next deposit is paid change, then put the clock back and watch it
 * change back.
 *
 * <p><strong>Putting a clock back is not something this application has a door for, and that is
 * deliberate.</strong> The development clock moves forward only — {@code MovableClock} argues at
 * length that backwards would let a demonstration produce records dated before ones already on the
 * ledger — so a trainer who wants the exercise again does what this test does: stops the
 * application, puts the recorded position back to where it started, and opens the file again. The
 * clock's position is one row, written so that an application stopped half way through an exercise
 * comes back up where it was in time; clearing it is the only meaning "wound back" has here. The
 * ledger is untouched by it, which is the interesting part: the deposit made in a week that is now
 * in the future is still on the file, and the walk has to leave it there.
 *
 * <p><strong>What the third deposit is paid is the whole test.</strong> Version 2 raises the first
 * rung of the ladder from 1,0000 to 1,4000, so a run of one week pays 1,00× under version 1 and
 * 1,40× under version 2. Three deposits, each securing the week it lands in and each with a run of
 * exactly one behind it:
 *
 * <ul>
 *   <li>before the Monday — <strong>1,00×</strong>, because version 2 is published, readable, and
 *       deciding nothing;</li>
 *   <li>after it — <strong>1,40×</strong>, with no job having run and nobody having pressed
 *       anything: the date on the row is the activation;</li>
 *   <li>with the clock put back — <strong>1,00×</strong> again, because which version is in force is
 *       derived from the day at read time rather than stored anywhere that would have to be put back
 *       with it.</li>
 * </ul>
 *
 * <p>The run is one in all three cases and is checked to be: the first and third deposits land in the
 * same week and the second lands two weeks later with an empty week between, so nothing about the
 * run is doing any of the work and the rate is a reading of the ladder alone.
 *
 * <p>Its own application on its own file, for the reason every test in this package gives.
 */
class WindingTheClockAcrossAPublishedMondayApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-winding-across-a-published-monday");

    /** Two weeks, which steps over the Monday the version takes effect on and lands on a Monday. */
    private static final long TWO_WEEKS = 14;

    /** Enough to secure a week under either version, since neither of them moves the threshold. */
    private static final String ENOUGH_TO_SECURE_A_WEEK = "60.00";

    /** What the first rung becomes, so that a run of one week pays a figure nothing else could. */
    private static final String THE_RAISED_ORDINARY_RATE = "1.4000";

    /**
     * A Monday a quarter of a century before this application's clock can be wound back to, and
     * before the Monday the scheme was seeded on.
     *
     * <p>The seed dates version 1 on the first Monday of the century precisely so that no week
     * anybody can produce falls before it. This is a week that does, asked about directly, because
     * no sequence of requests can arrange one — and the rule about it is real: a week judged under no
     * scheme at all would have no threshold, no ladder and no answer to "did I secure it".
     */
    private static final LocalDate A_MONDAY_BEFORE_THE_SCHEME_WAS_EVER_WRITTEN_DOWN =
            LocalDate.of(1975, 1, 6);

    private static ASchemeSomebodyAdministers bank;

    private static DepositView beforeTheMonday;
    private static DepositView afterTheMonday;
    private static DepositView withTheClockPutBack;
    private static TheSchemeAsPublished judgingAWeekBeforeTheSeededOne;
    private static LocalDate theMondayItTakesEffectOn;
    private static LocalDate theDayTheClockCameBackTo;
    private static LocalDate theDayTheExerciseStartedOn;

    @BeforeAll
    static void forwardAcrossTheMondayAndThenBackAgain() {
        bank = new ASchemeSomebodyAdministers(DATABASE);
        long savingsAccount = bank.savingsAccountOf(ANKE);

        bank.theClockReaches(bank.theNextMondayStillToCome());
        theDayTheExerciseStartedOn = bank.theDateTheClockReads();

        SchemeView asItStood = bank.theSchemeInForce();
        theMondayItTakesEffectOn = bank.theNextMondayStillToCome();
        Map<String, Object> aRicherFirstRung = theSameSchemeAgain(asItStood, theMondayItTakesEffectOn,
                "The first week of a run pays 1,40 rather than 1,00 from the date shown, because "
                        + "the bank wants saving to be worth something from the first week.");
        aRicherFirstRung.put("theOrdinaryRate", THE_RAISED_ORDINARY_RATE);
        bank.publish(aRicherFirstRung);

        // Published and deciding nothing: this deposit is priced at the ladder version 1 published.
        beforeTheMonday = bank.deposit(savingsAccount, ANKE, ENOUGH_TO_SECURE_A_WEEK);

        // Two weeks on, which is across the Monday and onto a Monday, with an empty week in between
        // so that the run behind this deposit is one week and not two.
        bank.daysPass(TWO_WEEKS);
        afterTheMonday = bank.deposit(savingsAccount, ANKE, ENOUGH_TO_SECURE_A_WEEK);

        // The one question no endpoint asks, asked while this application is still up.
        judgingAWeekBeforeTheSeededOne = bank.theApplicationsOwn(SchemeService.class)
                .theSchemeThatJudged(new SavingsWeek(A_MONDAY_BEFORE_THE_SCHEME_WAS_EVER_WRITTEN_DOWN));

        bank.close();
        putTheRecordedPositionOfTheClockBack();
        bank = new ASchemeSomebodyAdministers(DATABASE);
        theDayTheClockCameBackTo = bank.theDateTheClockReads();
        withTheClockPutBack = bank.deposit(savingsAccount, ANKE, ENOUGH_TO_SECURE_A_WEEK);
    }

    @AfterAll
    static void stopIt() {
        if (bank != null) {
            bank.close();
        }
    }

    /**
     * The arrangement, asserted rather than assumed: the clock really did cross the Monday and
     * really did come back behind it.
     *
     * <p>Without this the three rates below would be three readings of a clock nobody had checked,
     * and a test whose wind-back had silently failed would go on reporting 1,40× twice and passing
     * the first assertion of the three.
     */
    @Test
    void the_clock_crossed_the_monday_and_then_came_back_behind_it() {
        assertThat(theMondayItTakesEffectOn.getDayOfWeek())
                .as("a version takes effect on a Monday and on no other day")
                .isEqualTo(DayOfWeek.MONDAY);
        assertThat(theDayTheExerciseStartedOn)
                .as("the day the exercise started on, which is before the Monday")
                .isBefore(theMondayItTakesEffectOn);
        assertThat(theDayTheClockCameBackTo)
                .as("the day the clock came back to, which is behind the Monday again")
                .isBefore(theMondayItTakesEffectOn);
    }

    /**
     * Before the Monday: the version is published and readable and is deciding nothing, so a deposit
     * is paid what the version in force pays.
     */
    @Test
    void a_deposit_before_the_monday_is_paid_at_the_ladder_still_in_force() {
        assertThat(beforeTheMonday.multiplierApplied())
                .as("the first week of a run, on the ladder version 1 published")
                .isEqualByComparingTo("1.00");
    }

    /**
     * Winding forward across the Monday changes what the next deposit is paid, with no job having run
     * and nobody having pressed anything.
     *
     * <p>Which is the whole of how this feature is demonstrated in a room, and the whole of why there
     * is no activation step: the date on the row is the activation, resolved every time anybody
     * reads the scheme.
     */
    @Test
    void winding_the_clock_forward_across_a_published_monday_changes_what_the_next_deposit_is_paid() {
        assertThat(afterTheMonday.multiplierApplied())
                .as("the first week of a run, on the ladder version 2 published")
                .isEqualByComparingTo("1.40");
    }

    /**
     * And winding it back changes it back, because nothing was switched on that would have to be
     * switched off.
     *
     * <p>The deposit made in what is now the future is still on the ledger and is still not counted:
     * the walk is bounded at the start of the week it is standing in, so a week nobody has lived
     * through is not a week in anybody's run. That is why the run behind this deposit is one and the
     * rate is the ordinary one rather than the second rung of anything.
     */
    @Test
    void winding_the_clock_back_behind_it_changes_what_the_next_deposit_is_paid_back() {
        assertThat(withTheClockPutBack.multiplierApplied())
                .as("the first week of a run again, on the ladder version 1 published")
                .isEqualByComparingTo("1.00")
                .isEqualByComparingTo(beforeTheMonday.multiplierApplied());
    }

    /**
     * And a week from before the scheme was ever written down is judged under something rather than
     * under nothing.
     *
     * <p>The lowest version is the only honest thing to fall back to, because it is what the scheme
     * was written with — and the alternative is a week with no threshold, no ladder and no answer to
     * "did I secure it", which would surface as an exception in the middle of reading somebody's
     * account rather than as a wrong figure on it.
     *
     * <p>Asked of the module rather than over HTTP, which this package does exactly once and says why
     * in {@link ASchemeSomebodyAdministers#theApplicationsOwn}: the clock only moves forward, so no
     * sequence of requests can put a week behind the first Monday of the century.
     */
    @Test
    void a_week_behind_the_seeded_versions_date_still_finds_a_scheme_in_force() {
        assertThat(judgingAWeekBeforeTheSeededOne.version())
                .as("the version judging a week from before any version had been published")
                .isEqualTo(1);
        assertThat(judgingAWeekBeforeTheSeededOne.weeklyThreshold())
                .as("and it is a real scheme with a real threshold on it")
                .isEqualByComparingTo("50.00");
        assertThat(judgingAWeekBeforeTheSeededOne.effectiveFrom())
                .as("dated after the week it is judging, which is the fallback doing its work")
                .isAfter(A_MONDAY_BEFORE_THE_SCHEME_WAS_EVER_WRITTEN_DOWN);
    }

    /**
     * Puts the clock's recorded position back to where a file nobody has wound has it: no row at all.
     *
     * <p>Through the database because there is no door, and there is no door on purpose. The row is
     * the whole of what a restart reads to come back up where it was in time, so removing it is
     * exactly "the trainer started the exercise again" and nothing else about the file moves — the
     * deposits, the published versions and the seeded households are all still there, which is what
     * makes the reading afterwards worth taking.
     */
    private static void putTheRecordedPositionOfTheClockBack() {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("delete from clock_offset");
        } catch (SQLException e) {
            throw new AssertionError("could not put the clock's recorded position back in " + DATABASE, e);
        }
    }
}
