package io.dataroots.savingstreak.challenges;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import io.dataroots.savingstreak.support.AchievementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ScheduledJobView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer who never opens the Challenges tab still gets their badges, because a job judges every
 * live enrolment overnight.
 *
 * <p><strong>The whole claim is about somebody who is not looking.</strong> Every read of the
 * challenges tab or the trophy case judges before it answers, so a test that opens either of them
 * has already made the thing it is about to assert happen. The assertions here are therefore made
 * against the <em>points balance</em>, which is read off the overview and judges nothing: the
 * balance before the job ran and the balance after it ran are two readings of a figure that only
 * the job could have moved. The trophy case is opened afterwards, once the sweep has already been
 * caught doing its work.
 *
 * <p><strong>Run by hand, through the development jobs endpoint</strong>, exactly as a trainer would
 * — which is also the only honest way to test a job whose schedule is half past four in the morning.
 * That it is listed there at all is a claim of its own, because a job nobody can name is a job
 * nobody can demonstrate.
 *
 * <p><strong>The moment it judges as comes off the application's clock.</strong> A sweep that read
 * the machine's clock would date every badge it minted to-day, and the demonstration it exists for —
 * wind the clock, run the job, watch the badges arrive — would be showing something else. The
 * clock is wound four hundred days forward and the badge is asked when it thinks it was won.
 *
 * <p>Its own application on a database nothing has ever been written to, because a sweep judges
 * <em>everybody</em>: on the shared one it would judge whatever enrolments other tests happen to
 * have left lying about, and a customer count asserted here would really be an assertion about the
 * order the classes ran in.
 */
class ChallengesAreJudgedNightlyApiTest extends ApiIntegrationTest {

    /** The name a trainer types to run the sweep now, and the class the jobs endpoint names it by. */
    private static final String THE_NIGHTLY_JUDGING = "judgeTheChallenges";
    private static final String DEFINED_BY = "ChallengesAreJudgedNightly";

    private static final String FIVE_HUNDRED = "SAVE_FIVE_HUNDRED";

    /** What the seed prices the bronze rung of {@code SAVE_FIVE_HUNDRED} at. */
    private static final long BRONZE_PAYS = 25;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-judged-nightly"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * The point of the slice, in one narrative: a rung is cleared by somebody who then goes to bed,
     * the night pays them for it, and a second night pays them nothing more.
     */
    @Test
    void the_night_pays_a_rung_for_somebody_who_never_opened_the_tab_and_pays_it_once() {
        String customer = app.aCustomerOfItsOwn("judged-while-asleep");
        long savings = app.savingsAccountOf(customer);
        app.enrolIn(customer, FIVE_HUNDRED);
        app.deposit(savings, customer, "100.00");

        long beforeTheNight = app.pointsBalanceOf(customer);
        app.runJob(THE_NIGHTLY_JUDGING);

        assertThat(app.pointsBalanceOf(customer) - beforeTheNight)
                .as("bronze was cleared and the sweep paid for it, with nobody having looked at "
                        + "anything")
                .isEqualTo(BRONZE_PAYS);
        assertThat(app.achievementsOf(customer))
                .as("and the badge is waiting in the trophy case the first time they do look")
                .extracting(AchievementView::rung)
                .containsExactly("BRONZE");

        long afterTheFirstNight = app.pointsBalanceOf(customer);
        app.runJob(THE_NIGHTLY_JUDGING);

        assertThat(app.pointsBalanceOf(customer))
                .as("a second sweep over the same standing pays nothing: the awards are the record "
                        + "of what has been paid and there is one already")
                .isEqualTo(afterTheFirstNight);
        assertThat(app.achievementsOf(customer))
                .as("and mints nothing either")
                .hasSize(1);
    }

    /**
     * Everybody, not merely the customer who happens to be looking. Two people who have never met
     * are enrolled and left alone, and one run of the job pays them both.
     */
    @Test
    void one_sweep_judges_every_customer_holding_a_live_enrolment() {
        String one = app.aCustomerOfItsOwn("judged-in-a-crowd-one");
        String another = app.aCustomerOfItsOwn("judged-in-a-crowd-two");
        app.enrolIn(one, FIVE_HUNDRED);
        app.enrolIn(another, FIVE_HUNDRED);
        app.deposit(app.savingsAccountOf(one), one, "100.00");
        app.deposit(app.savingsAccountOf(another), another, "100.00");

        long oneBefore = app.pointsBalanceOf(one);
        long anotherBefore = app.pointsBalanceOf(another);
        app.runJob(THE_NIGHTLY_JUDGING);

        assertThat(app.pointsBalanceOf(one) - oneBefore).isEqualTo(BRONZE_PAYS);
        assertThat(app.pointsBalanceOf(another) - anotherBefore)
                .as("the sweep is over everybody with a live enrolment, not over whoever asked")
                .isEqualTo(BRONZE_PAYS);
    }

    /**
     * Somebody who left the challenge is not judged again, which is the other half of "every customer
     * holding a <em>live</em> enrolment": a sweep that judged an abandoned one would go on paying a
     * customer who is no longer in it.
     */
    @Test
    void somebody_who_left_is_not_judged_by_the_night() {
        String customer = app.aCustomerOfItsOwn("left-before-the-night");
        long savings = app.savingsAccountOf(customer);
        app.enrolIn(customer, FIVE_HUNDRED);
        app.leave(customer, FIVE_HUNDRED);
        app.deposit(savings, customer, "100.00");

        long beforeTheNight = app.pointsBalanceOf(customer);
        app.runJob(THE_NIGHTLY_JUDGING);

        assertThat(app.pointsBalanceOf(customer))
                .as("they are not in it any more, so there was nothing for the night to pay")
                .isEqualTo(beforeTheNight);
        assertThat(app.achievementsOf(customer)).isEmpty();
    }

    /** So that nobody has to read the source for a name, or wonder when it would otherwise run. */
    @Test
    void the_nightly_judging_is_one_of_the_jobs_that_can_be_run_by_hand() {
        ScheduledJobView judging = Arrays.stream(app.whatCanBeRun())
                .filter(job -> job.name().equals(THE_NIGHTLY_JUDGING))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the jobs endpoint does not list "
                        + THE_NIGHTLY_JUDGING + "; it lists "
                        + Arrays.stream(app.whatCanBeRun()).map(ScheduledJobView::name).toList()));

        assertThat(judging.definedBy()).isEqualTo(DEFINED_BY);
        assertThat(judging.schedule())
                .as("the cron as its author wrote it, which is what somebody deciding whether to "
                        + "run it by hand reads")
                .isEqualTo("cron 0 30 4 * * *");
    }

    /**
     * The sweep judges as of the application's clock, and the badge it mints says so.
     *
     * <p>The clock this moves is the one every other test in the class shares, and it only ever goes
     * forward. Nothing here minds: every test brings a customer of its own and asserts what a night
     * changed rather than what day it was, so the order these run in cannot decide any of them.
     */
    @Test
    void a_sweep_run_after_the_clock_moved_dates_its_badges_on_the_moved_clock() {
        app.daysPass(400);
        String customer = app.aCustomerOfItsOwn("judged-a-year-from-now");
        long savings = app.savingsAccountOf(customer);
        app.enrolIn(customer, FIVE_HUNDRED);
        app.deposit(savings, customer, "100.00");
        Instant theApplicationThinksItIs = app.theClockReads();

        app.runJob(THE_NIGHTLY_JUDGING);

        AchievementView bronze = app.achievementsOf(customer).get(0);
        assertThat(bronze.awardedAt())
                .as("dated on the clock the trainer wound, not on the machine's — a sweep reading "
                        + "Instant.now() would date this badge to-day and the demonstration would "
                        + "show nothing")
                .isAfter(Instant.now().plus(300, ChronoUnit.DAYS))
                .isBetween(theApplicationThinksItIs.minusSeconds(60),
                        theApplicationThinksItIs.plusSeconds(600));
    }
}
