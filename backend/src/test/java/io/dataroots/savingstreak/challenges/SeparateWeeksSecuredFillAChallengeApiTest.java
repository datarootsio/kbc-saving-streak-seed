package io.dataroots.savingstreak.challenges;

import java.util.List;

import io.dataroots.savingstreak.support.AchievementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.ChallengeView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Five weeks of real saving, counted as separate weeks rather than as a run: the weeks add up as
 * they are secured, a week that falls short costs nothing, and the weeks that count need not be
 * next to each other.
 *
 * <p><strong>The claim worth reading twice is the one made in week three</strong>, where the streak
 * says one week and this challenge says two. They are different games on purpose. A streak is a run
 * and a broken week ends it; this counts weeks the customer secured, and nothing takes a secured
 * week back. A customer who has to dip into their savings loses their run and loses nothing here,
 * which is the whole reason the bank offers both.
 *
 * <p><strong>And the week that falls short falls short by the existing rule.</strong> Week two takes
 * EUR 60 in and lets EUR 20 back out, so it put EUR 40 away and is not secured — the net-of-
 * withdrawals rule the streak already runs on, read rather than restated. Asserting it through a
 * withdrawal rather than through a small deposit is what makes that visible: a challenge counting
 * gross would have called week two secured.
 *
 * <p>Its own application on a database nothing has ever been written to, because a count of weeks
 * has to start from an account no week has ever been secured in — see
 * {@link AnApplicationWithAClockToMove}.
 *
 * <p>One test, because the clock only goes forward: a second method in this class would find the
 * weeks already wound on and would be asserting against whatever order the two happened to run in.
 * The narrative is asserted on after every step instead, which is what a challenge measured in
 * weeks is.
 */
class SeparateWeeksSecuredFillAChallengeApiTest extends ApiIntegrationTest {

    private static final String FIVE_WEEKS = "SECURE_FIVE_WEEKS";

    /** What the seed prices the rungs of {@code SECURE_FIVE_WEEKS} at, and where they sit. */
    private static final long BRONZE_PAYS = 50;
    private static final long SILVER_PAYS = 150;
    private static final long GOLD_PAYS = 400;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseWeeksThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenge-weeks-that-count"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void separate_weeks_secured_since_enrolling_fill_the_challenge_and_a_missed_week_costs_nothing() {
        long savings = app.savingsAccountOf(ANKE);

        // The card before anybody joins: rungs counted in weeks rather than in euros, and nothing
        // read off it, because a challenge counts nothing until it is taken on.
        ChallengeView onOffer = app.challengeOf(ANKE, FIVE_WEEKS);
        assertThat(onOffer.kind()).isEqualTo("SECURED_WEEKS");
        assertThat(onOffer.repeatable()).isTrue();
        assertThat(onOffer.rungs()).extracting(rung -> rung.threshold().intValueExact())
                .containsExactly(1, 3, 5);
        assertThat(onOffer.enrolled()).isFalse();
        assertThat(onOffer.reading()).isNull();

        app.enrolIn(ANKE, FIVE_WEEKS);

        // Nothing has been secured since the enrolment began, and the first rung is one week away.
        ChallengeView justJoined = app.challengeOf(ANKE, FIVE_WEEKS);
        assertThat(justJoined.reading()).isEqualByComparingTo("0");
        assertThat(justJoined.nextRung()).isEqualTo("BRONZE");
        assertThat(justJoined.stillNeeded()).isEqualByComparingTo("1");
        assertThat(app.achievementsOf(ANKE)).isEmpty();

        // Week one, secured by exactly what a week asks for. The week the customer enrolled in
        // counts: it was secured after they joined it.
        long pointsBeforeTheFirstWeekWasSecured = app.pointsBalanceOf(ANKE);
        app.deposit(savings, ANKE, "50.00");
        assertThat(app.challengeOf(ANKE, FIVE_WEEKS).reading()).isEqualByComparingTo("1");
        assertThat(app.achievementsOf(ANKE))
                .extracting(AchievementView::rung)
                .containsExactly("BRONZE");
        // The rung paid on top of the fifty points the deposit itself earned, which is why this is
        // asserted as a difference rather than as a total.
        assertThat(app.pointsBalanceOf(ANKE) - pointsBeforeTheFirstWeekWasSecured)
                .isEqualTo(50 + BRONZE_PAYS);

        app.aWeekPasses();

        // Week two puts EUR 60 in and takes EUR 20 back out, so it put EUR 40 away and is short of
        // what a week asks for. The count does not move, and — this is the point — it does not fall
        // back either.
        app.deposit(savings, ANKE, "60.00");
        app.withdraw(savings, ANKE, "20.00");
        BalancesView weekTwo = app.balancesOf(savings);
        assertThat(weekTwo.newSavingsThisWeek()).isEqualByComparingTo("40.00");
        assertThat(app.challengeOf(ANKE, FIVE_WEEKS).reading()).isEqualByComparingTo("1");

        app.aWeekPasses();

        // Week three is secured, and it is not next to week one. The challenge counts two; the
        // streak counts one, because week two broke the run. Two games, one ledger.
        app.deposit(savings, ANKE, "50.00");
        assertThat(app.challengeOf(ANKE, FIVE_WEEKS).reading()).isEqualByComparingTo("2");
        BalancesView weekThree = app.balancesOf(savings);
        assertThat(weekThree.currentStreakWeeks()).isEqualTo(1);
        assertThat(weekThree.bestStreakWeeks()).isEqualTo(1);

        app.aWeekPasses();

        // Week four takes it to three weeks secured, which is silver.
        app.deposit(savings, ANKE, "50.00");
        ChallengeView atSilver = app.challengeOf(ANKE, FIVE_WEEKS);
        assertThat(atSilver.reading()).isEqualByComparingTo("3");
        assertThat(atSilver.nextRung()).isEqualTo("GOLD");
        assertThat(atSilver.stillNeeded()).isEqualByComparingTo("2");
        assertThat(app.achievementsOf(ANKE))
                .extracting(AchievementView::rung)
                .containsExactly("SILVER", "BRONZE");

        app.aWeekPasses();
        app.deposit(savings, ANKE, "50.00");
        assertThat(app.challengeOf(ANKE, FIVE_WEEKS).reading()).isEqualByComparingTo("4");

        app.aWeekPasses();

        // The fifth secured week finishes it, in the sixth week of the enrolment: five weeks that
        // count, not five weeks in a row.
        app.deposit(savings, ANKE, "50.00");
        ChallengeView finished = app.challengeOf(ANKE, FIVE_WEEKS);
        assertThat(finished.state()).isEqualTo("COMPLETED");
        assertThat(finished.reading()).isEqualByComparingTo("5");
        List<AchievementView> won = app.achievementsOf(ANKE);
        assertThat(won).extracting(AchievementView::rung).containsExactly("GOLD", "SILVER", "BRONZE");
        assertThat(won).extracting(AchievementView::points)
                .containsExactly(GOLD_PAYS, SILVER_PAYS, BRONZE_PAYS);
        // The reading each rung was won at, written down as it stood and never worked out again.
        assertThat(won).extracting(achievement -> achievement.reading().intValueExact())
                .containsExactly(5, 3, 1);
        // The run behind her is four weeks, because week two is still broken. The badge says five.
        assertThat(app.balancesOf(savings).currentStreakWeeks()).isEqualTo(4);

        app.aWeekPasses();

        // Taken on again in a week nothing has landed in yet, and the second round starts at
        // nothing: the five weeks behind her belong to the enrolment that counted them.
        app.enrolIn(ANKE, FIVE_WEEKS);
        ChallengeView roundTwo = app.challengeOf(ANKE, FIVE_WEEKS);
        assertThat(roundTwo.state()).isEqualTo("ACTIVE");
        assertThat(roundTwo.reading()).isEqualByComparingTo("0");

        app.deposit(savings, ANKE, "50.00");
        assertThat(app.challengeOf(ANKE, FIVE_WEEKS).reading()).isEqualByComparingTo("1");
        // A second bronze, on a second enrolment: the first one's badges are untouched and the new
        // round earns its own.
        assertThat(app.achievementsOf(ANKE))
                .extracting(AchievementView::rung)
                .containsExactly("BRONZE", "GOLD", "SILVER", "BRONZE");
    }
}
