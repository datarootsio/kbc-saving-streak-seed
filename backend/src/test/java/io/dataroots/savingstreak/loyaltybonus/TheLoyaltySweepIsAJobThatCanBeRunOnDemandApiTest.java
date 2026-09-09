package io.dataroots.savingstreak.loyaltybonus;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.JobRunView;
import io.dataroots.savingstreak.support.ScheduledJobView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sweep is a scheduled job of the application's own, findable by name, and a trainer can run it
 * now.
 *
 * <p>Which is the point of the whole arrangement. A reward measured in twelve months cannot be
 * reached inside a training day by waiting, so it is reached by winding the clock forward and
 * running the job out of turn — and for that to work the job has to appear in the list of jobs that
 * can be run, has to answer to the name it appears under, and has to judge anniversaries against the
 * application's clock rather than the machine's. A job that read {@code Instant.now()} would find
 * nothing to do on a clock wound a year forward and would say so convincingly.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class TheLoyaltySweepIsAJobThatCanBeRunOnDemandApiTest extends ApiIntegrationTest {

    private static final String THE_SWEEP = "payLoyaltyBonuses";

    /** Comfortably the far side of any anniversary the clock could land near. */
    private static final int DAYS_WELL_PAST_A_YEAR = 379;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-the-loyalty-sweep-is-a-job"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_sweep_is_listed_among_the_jobs_that_can_be_run() {
        ScheduledJobView[] jobs = app.whatCanBeRun();

        assertThat(Arrays.stream(jobs).map(ScheduledJobView::name))
                .as("nobody should have to guess the name of the job that pays loyalty")
                .contains(THE_SWEEP);
        ScheduledJobView sweep = Arrays.stream(jobs)
                .filter(job -> job.name().equals(THE_SWEEP)).findFirst().orElseThrow();
        assertThat(sweep.definedBy()).isEqualTo("LoyaltyBonusesArePaidNightly");
        assertThat(sweep.schedule())
                .as("nightly, half an hour after the points-expiry sweep, so a night that both "
                        + "pays an anniversary and retires an old batch does them in that order")
                .isEqualTo("cron 0 30 3 * * *");
    }

    @Test
    void the_sweep_judges_anniversaries_against_the_clock_the_application_is_running_on() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        long before = app.pointsBalanceOf(ANKE);
        app.deposit(savingsAccount, ANKE, "180.00");
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(before + 180);

        app.daysPass(DAYS_WELL_PAST_A_YEAR);
        Instant theApplicationThinksItIs = app.theClockReads();

        JobRunView ran = app.runJob(THE_SWEEP);

        // The answer came back after the job had finished rather than promising it would, and the
        // moment it reports is the one the application's clock reads — a year on from the machine's.
        assertThat(ran.name()).isEqualTo(THE_SWEEP);
        assertThat(ran.definedBy()).isEqualTo("LoyaltyBonusesArePaidNightly");
        assertThat(Duration.between(theApplicationThinksItIs, ran.ranAt()).abs())
                .as("the job ran at the moment the wound-forward clock reads, not at the machine's")
                .isLessThan(Duration.ofMinutes(5));

        // And it acted on that moment: a deposit of 180 euros a year old has paid its tenth.
        assertThat(app.pointsBalanceOf(ANKE))
                .as("winding the clock a year on and running the job by name is how a twelve-month "
                        + "reward is demonstrated in an afternoon")
                .isEqualTo(before + 180 + 18);
    }
}
