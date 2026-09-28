package io.dataroots.savingstreak.savingspolicy;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.SchemeView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.savingspolicy.ASchemeSomebodyAdministers.theSameSchemeAgain;
import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The load-bearing rule of the whole feature, watched happening: a week is judged by the scheme in
 * force on that week's own Monday, so raising the weekly minimum next Monday leaves every week
 * behind it exactly as it stood.
 *
 * <p><strong>What would go wrong without it is the reason this test exists.</strong> A run of weeks
 * is not stored anywhere — it is re-derived from the whole ledger on every read. Judge that walk
 * against whatever the threshold happens to be right now and every EUR 60 week a customer ever
 * secured un-secures itself the morning the minimum rises to EUR 80: their current run shortens,
 * their best-ever run shortens, and their next deposit is priced at the wrong rate, with nothing
 * logged and nothing able to explain it. The three weeks below take in EUR 60 each. Under the scheme
 * as published for those weeks that is comfortably enough; under the scheme published for the week
 * after them it is twenty euros short of each. The run has to read three either way.
 *
 * <p><strong>The three weeks are built on Mondays, deliberately.</strong> The clock is first wound to
 * the next Monday it has not reached, and every week after that is seven days on from a Monday, so
 * which savings week each deposit lands in is not a function of which weekday the test run happened
 * to start on. A version may only ever take effect on a Monday, so a test whose weeks did not line
 * up with Mondays would be asserting about a boundary the module does not have.
 *
 * <p><strong>Its own application on its own file</strong>, because publishing cannot be undone and
 * neither can winding a clock. {@link ASchemeSomebodyAdministers} argues both at length.
 *
 * <p>Everything is arranged once, in {@link #threeWeeksSecuredAndThenTheBarIsRaised()}, because the
 * clock only goes forward: the three readings below are three photographs of one sequence and each
 * of them is only an answer while nothing has moved since it was taken.
 */
class AWeekKeepsTheVerdictItsOwnMondayGaveItApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-a-week-keeps-its-verdict");

    /** Seven days on from a Monday, which is the next Monday whatever the clocks did in between. */
    private static final long A_WEEK = 7;

    /**
     * EUR 60 a week: over the EUR 50 the scheme asks for while these weeks are being lived through,
     * and under the EUR 80 it asks for afterwards. The whole test is the gap between those two
     * figures.
     */
    private static final String SIXTY_A_WEEK = "60.00";

    /** What a week is raised to, from the Monday after the three weeks have been secured. */
    private static final String THE_RAISED_MINIMUM = "80.00";

    /** What a week asked for while those three weeks were running. */
    private static final String THE_MINIMUM_THEY_WERE_JUDGED_UNDER = "50.00";

    /** Three consecutive secured weeks, which the ladder pays 1,00 + two steps of 0,10 for. */
    private static final int THREE_WEEKS = 3;

    private static ASchemeSomebodyAdministers bank;
    private static long savingsAccount;

    private static BalancesView afterThreeSecuredWeeks;
    private static BalancesView theMorningAfterThePublish;
    private static BalancesView theMorningTheRaiseTookEffect;
    private static LocalDate theMondayTheRaiseTakesEffectOn;

    @BeforeAll
    static void threeWeeksSecuredAndThenTheBarIsRaised() {
        bank = new ASchemeSomebodyAdministers(DATABASE);
        savingsAccount = bank.savingsAccountOf(ANKE);

        // Onto a Monday first, so that every week below begins and ends where the module says a week
        // begins and ends rather than wherever the run started.
        bank.theClockReaches(bank.theNextMondayStillToCome());
        bank.deposit(savingsAccount, ANKE, SIXTY_A_WEEK);
        bank.daysPass(A_WEEK);
        bank.deposit(savingsAccount, ANKE, SIXTY_A_WEEK);
        bank.daysPass(A_WEEK);
        bank.deposit(savingsAccount, ANKE, SIXTY_A_WEEK);
        afterThreeSecuredWeeks = bank.balancesOf(savingsAccount);

        SchemeView asItStood = bank.theSchemeInForce();
        theMondayTheRaiseTakesEffectOn = bank.theNextMondayStillToCome();
        Map<String, Object> raisingTheBar = theSameSchemeAgain(asItStood,
                theMondayTheRaiseTakesEffectOn,
                "A week asks for EUR 80 instead of EUR 50 from the date shown. Weeks already "
                        + "secured keep the verdict they were given.");
        raisingTheBar.put("weeklyThreshold", THE_RAISED_MINIMUM);
        bank.publish(raisingTheBar);
        theMorningAfterThePublish = bank.balancesOf(savingsAccount);

        // And the Monday arrives, which is the only thing that makes a published version the scheme
        // the bank runs on.
        bank.theClockReaches(theMondayTheRaiseTakesEffectOn);
        theMorningTheRaiseTookEffect = bank.balancesOf(savingsAccount);
    }

    @AfterAll
    static void stopIt() {
        if (bank != null) {
            bank.close();
        }
    }

    /**
     * Where the customer stands before anybody publishes anything: three weeks of EUR 60, three
     * consecutive secured weeks, and the rate the third rung of the ladder pays.
     *
     * <p>Asserted rather than assumed, because every claim below is a claim that these figures do
     * not move. A test whose arrangement had quietly produced a run of one would go on passing while
     * proving nothing at all.
     */
    @Test
    void three_weeks_of_sixty_euros_are_three_secured_weeks_under_the_scheme_they_were_lived_under() {
        assertThat(afterThreeSecuredWeeks.weeklyMinimum())
                .as("what a week asked for while those three weeks were running")
                .isEqualByComparingTo(THE_MINIMUM_THEY_WERE_JUDGED_UNDER);
        assertThat(afterThreeSecuredWeeks.currentStreakWeeks())
                .as("the run of consecutive secured weeks").isEqualTo(THREE_WEEKS);
        assertThat(afterThreeSecuredWeeks.bestStreakWeeks())
                .as("the longest run there has ever been").isEqualTo(THREE_WEEKS);
        assertThat(afterThreeSecuredWeeks.currentMultiplier())
                .as("1,00 and two further weeks at 0,10 each")
                .isEqualByComparingTo("1.20");
        assertThat(afterThreeSecuredWeeks.schemeVersion())
                .as("the version those figures came out of").isEqualTo(1);
    }

    /**
     * Publishing changes nothing on the morning it is published, because a version takes effect on
     * its Monday and not when somebody presses the button.
     *
     * <p>The whole reading is compared rather than a figure or two, which is the strongest form the
     * claim has: an announcement is not an event a customer's account can see.
     */
    @Test
    void the_morning_after_a_version_is_published_the_account_reads_exactly_as_it_did() {
        assertThat(theMorningAfterThePublish)
                .as("the savings account either side of a version being published for next Monday")
                .isEqualTo(afterThreeSecuredWeeks);
    }

    /**
     * The heart of the ticket: the Monday arrives, the bar is EUR 80, and the three EUR 60 weeks
     * behind it are still three secured weeks.
     *
     * <p>Judged against today's threshold they would be nothing: EUR 60 is twenty short of EUR 80,
     * three times over, so the run would read zero and the record would read zero with it. They read
     * three because each of them was judged against the figure published for its own Monday, which
     * is the one rule this whole feature is built on.
     */
    @Test
    void a_week_secured_under_fifty_euros_stays_secured_after_a_later_monday_raises_it_to_eighty() {
        assertThat(theMorningTheRaiseTookEffect.weeklyMinimum())
                .as("what the week the customer is now in asks for")
                .isEqualByComparingTo(THE_RAISED_MINIMUM);
        assertThat(theMorningTheRaiseTookEffect.schemeVersion())
                .as("the version in force now the Monday has come").isEqualTo(2);

        assertThat(theMorningTheRaiseTookEffect.currentStreakWeeks())
                .as("three weeks of EUR 60, each judged against the EUR 50 its own Monday asked for")
                .isEqualTo(THREE_WEEKS);
    }

    /**
     * And the record does not move either, which is the half of the promise a customer notices last
     * and minds most.
     *
     * <p>The best-ever run is worked out independently of the current one, over every week the
     * customer has ever moved money in, so it is the figure a threshold applied to the whole of
     * history would wreck most thoroughly. A lapse costs the run and not the record; a repricing
     * costs neither.
     */
    @Test
    void the_best_ever_run_does_not_shorten_when_the_bar_is_raised_behind_it() {
        assertThat(theMorningTheRaiseTookEffect.bestStreakWeeks())
                .as("the longest run there has ever been, after the raise")
                .isEqualTo(THREE_WEEKS)
                .isEqualTo(afterThreeSecuredWeeks.bestStreakWeeks());
    }

    /**
     * And what a euro is worth does not move, because the run it is a function of did not.
     *
     * <p>This version raised the bar and left the ladder alone, so a rate that had changed could only
     * have changed because the run underneath it had — which is exactly the failure this ticket
     * exists to prevent, arriving on the customer's screen as a rate cut nobody published.
     */
    @Test
    void the_rate_does_not_fall_because_a_threshold_rose_behind_the_run() {
        assertThat(theMorningTheRaiseTookEffect.currentMultiplier())
                .as("the rate three consecutive secured weeks pays, after the raise")
                .isEqualByComparingTo(new BigDecimal("1.20"))
                .isEqualByComparingTo(afterThreeSecuredWeeks.currentMultiplier());
    }

    /**
     * What the week the customer is standing in now asks for comes from the version in force, and the
     * screen is told both figures rather than one of them and a constant.
     *
     * <p>Nothing has landed in the new week, so what it still needs is the whole of the new
     * threshold. A page that had EUR 50 written into its own markup would say the customer was
     * thirty euros closer than they are.
     */
    @Test
    void what_the_week_still_needs_is_quoted_from_the_threshold_that_week_is_judged_against() {
        assertThat(theMorningTheRaiseTookEffect.newSavingsThisWeek())
                .as("nothing has landed in the week the raise took effect in")
                .isEqualByComparingTo("0.00");
        assertThat(theMorningTheRaiseTookEffect.stillNeededThisWeek())
                .as("the whole of what the new version asks for")
                .isEqualByComparingTo(THE_RAISED_MINIMUM);
    }
}
