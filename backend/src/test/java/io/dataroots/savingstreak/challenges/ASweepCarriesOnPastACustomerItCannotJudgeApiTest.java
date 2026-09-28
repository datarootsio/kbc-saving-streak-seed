package io.dataroots.savingstreak.challenges;

import io.dataroots.savingstreak.support.AchievementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer the night cannot judge costs the bank that customer's badges, and nobody else's.
 *
 * <p>The sweep is a loop over everybody holding a live enrolment, and the whole question is what it
 * does when one turn of that loop throws. Abandoning the night would let a single unreadable row
 * hold up every badge in the bank — and hold it up again to-morrow night, and the night after,
 * because nothing about the row would have changed. So the failure is logged with its exception and
 * the next customer is judged.
 *
 * <p><strong>The failure is brought rather than arranged.</strong> There is no sequence of requests
 * that makes a customer genuinely unjudgeable: every way the module says no is a refusal made before
 * any judging happens, and the one damaged case the pass already knows about — an enrolment in a
 * challenge that is no longer offered — it warns about and steps over. {@link TheJudgingThisTestBreaks}
 * is therefore brought into this one application, and the argument for doing it that way rather than
 * putting a hook in the application is made there.
 *
 * <p>Everything else is the real thing, driven and asserted through the endpoints: two customers
 * enrol, both clear bronze, the earlier of the two is made unjudgeable, and the night is run by
 * hand. The later one is the assertion — a sweep that gave up on the first would leave their balance
 * exactly where it was and this test would say so.
 */
class ASweepCarriesOnPastACustomerItCannotJudgeApiTest extends ApiIntegrationTest {

    private static final String THE_NIGHTLY_JUDGING = "judgeTheChallenges";

    private static final String FIVE_HUNDRED = "SAVE_FIVE_HUNDRED";

    /** What the seed prices the bronze rung of {@code SAVE_FIVE_HUNDRED} at. */
    private static final long BRONZE_PAYS = 25;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWithABrokenJudgingPassInIt() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-a-sweep-that-carries-on"),
                "dev," + TheJudgingThisTestBreaks.PROFILE,
                TheJudgingThisTestBreaks.class);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_night_judges_everybody_after_the_customer_it_could_not_judge() {
        // Opened first, so that the sweep — which takes people in the order they enrolled — meets
        // the failure before it meets anybody it can pay. A sweep that fell over on the last
        // customer would prove nothing at all.
        String unjudgeable = app.aCustomerOfItsOwn("a-customer-nothing-can-judge");
        String behindThem = app.aCustomerOfItsOwn("a-customer-behind-the-failure");
        app.enrolIn(unjudgeable, FIVE_HUNDRED);
        app.enrolIn(behindThem, FIVE_HUNDRED);
        app.deposit(app.savingsAccountOf(unjudgeable), unjudgeable, "100.00");
        app.deposit(app.savingsAccountOf(behindThem), behindThem, "100.00");

        app.theApplicationsOwn(TheJudgingThisTestBreaks.class)
                .nothingCanJudge(app.customerIdOf(unjudgeable));

        long theirBalanceBefore = app.pointsBalanceOf(unjudgeable);
        long balanceBehindThem = app.pointsBalanceOf(behindThem);
        // Insisted on: the job itself must come back having run. A sweep that let one customer's
        // exception out would be reported as a job that threw, and this line is where that is caught.
        app.runJob(THE_NIGHTLY_JUDGING);

        assertThat(app.pointsBalanceOf(behindThem) - balanceBehindThem)
                .as("the customer after the failure was judged and paid, which is the whole promise")
                .isEqualTo(BRONZE_PAYS);
        assertThat(app.achievementsOf(behindThem))
                .extracting(AchievementView::rung)
                .containsExactly("BRONZE");

        assertThat(app.pointsBalanceOf(unjudgeable))
                .as("and the one that threw was paid nothing, which is what makes the line above "
                        + "worth reading — half a judging pass must not pay half a rung")
                .isEqualTo(theirBalanceBefore);
    }
}
