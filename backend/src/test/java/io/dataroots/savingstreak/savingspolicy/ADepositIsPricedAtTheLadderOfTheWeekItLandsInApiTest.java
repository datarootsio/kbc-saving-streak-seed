package io.dataroots.savingstreak.savingspolicy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
 * The other half of the rule: a deposit is priced at the ladder in force in the week it lands,
 * applied to a run counted week by week under each week's own scheme — and a deposit already made
 * keeps the rate it was paid at whatever is published afterwards.
 *
 * <p><strong>One number carries the whole claim, and it is chosen so that it can.</strong> The
 * version published here does two things at once: it raises what a week asks for from EUR 50 to
 * EUR 80, and it steepens the ladder from 0,10 a week to 0,25 a week. The first week took in EUR 60
 * — enough under the scheme it was lived under, twenty euros short under the scheme published after
 * it. The second week takes in EUR 90, which is enough under either.
 *
 * <p>So the deposit in the second week is paid one of three figures, and each of them means
 * something different:
 *
 * <ul>
 *   <li><strong>1,25×</strong> — a run of two, on the new ladder. Both halves of the rule held: the
 *       EUR 60 week kept the verdict its own Monday gave it, and this week's deposit was priced at
 *       the ladder published for this week.</li>
 *   <li><strong>1,00×</strong> — a run of one, on the new ladder. The EUR 60 week was re-judged
 *       against today's EUR 80 and un-secured itself, which is precisely the bug.</li>
 *   <li><strong>1,10×</strong> — a run of two, on the old ladder. The week was judged right and the
 *       deposit was priced at a scheme that is no longer in force.</li>
 * </ul>
 *
 * <p>And the first deposit, read back out of the history afterwards, still says 1,00×. Nothing
 * published can reach a deposit that has landed: the rate was written on it at the moment the money
 * moved, and the points it was credited are the points the customer keeps.
 *
 * <p><strong>Its own application on its own file</strong>, for the reason every test in this package
 * gives: publishing cannot be undone and neither can winding a clock. The weeks are built on Mondays
 * for the reason {@link AWeekKeepsTheVerdictItsOwnMondayGaveItApiTest} gives.
 */
class ADepositIsPricedAtTheLadderOfTheWeekItLandsInApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-a-deposit-and-the-ladder-of-its-week");

    /** Seven days on from a Monday, which is the next Monday whatever the clocks did in between. */
    private static final long A_WEEK = 7;

    /** Enough for the week it lands in, and twenty euros short of what the week after it asks for. */
    private static final String SIXTY = "60.00";

    /** Enough under either version, so that the second week's own verdict is never in question. */
    private static final String NINETY = "90.00";

    private static ASchemeSomebodyAdministers bank;

    private static DepositView theWeekBeforeTheChange;
    private static DepositView theWeekAfterIt;
    /** Read after both deposits, so that the first one is being read back rather than remembered. */
    private static List<DepositView> theHistoryAsItReadsNow;

    @BeforeAll
    static void oneWeekUnderEachVersion() {
        bank = new ASchemeSomebodyAdministers(DATABASE);
        long savingsAccount = bank.savingsAccountOf(ANKE);

        bank.theClockReaches(bank.theNextMondayStillToCome());
        theWeekBeforeTheChange = bank.deposit(savingsAccount, ANKE, SIXTY);

        SchemeView asItStood = bank.theSchemeInForce();
        Map<String, Object> aHarderWeekAndASteeperLadder = theSameSchemeAgain(asItStood,
                bank.theNextMondayStillToCome(),
                "A week asks for EUR 80 from the date shown, and every further week of a run adds "
                        + "0,25 rather than 0,10. Weeks already secured keep their verdict and "
                        + "deposits already made keep their rate.");
        aHarderWeekAndASteeperLadder.put("weeklyThreshold", "80.00");
        aHarderWeekAndASteeperLadder.put("extraForEachFurtherWeek", "0.2500");
        bank.publish(aHarderWeekAndASteeperLadder);

        bank.daysPass(A_WEEK);
        theWeekAfterIt = bank.deposit(savingsAccount, ANKE, NINETY);

        theHistoryAsItReadsNow = bank.depositsInto(savingsAccount);
    }

    @AfterAll
    static void stopIt() {
        if (bank != null) {
            bank.close();
        }
    }

    /**
     * The first deposit was paid the ordinary rate, which is what the first week of a run pays under
     * the scheme it landed under.
     *
     * <p>Asserted so that the second deposit's figure means something: a first deposit that had
     * somehow been paid 1,25× would make every claim below unreadable.
     */
    @Test
    void the_deposit_before_the_change_was_paid_the_ladder_in_force_when_it_landed() {
        assertThat(theWeekBeforeTheChange.multiplierApplied())
                .as("the first week of a run, on the ladder version 1 published")
                .isEqualByComparingTo("1.00");
        assertThat(theWeekBeforeTheChange.pointsEarned())
                .as("one point per whole euro of EUR 60 at 1,00×").isEqualTo(60);
    }

    /**
     * The heart of the ticket, priced: a run of two weeks, on the ladder published for the week the
     * deposit landed in.
     *
     * <p>1,25× is 1,0000 plus one further week at 0,2500. It is only reachable if the EUR 60 week
     * behind it still counts under the EUR 50 its own Monday asked for <em>and</em> this week's
     * deposit is priced at the steeper ladder this week's Monday published. Either half missing and
     * the figure is 1,00× or 1,10×.
     */
    @Test
    void a_deposit_after_a_change_is_priced_at_the_new_ladder_against_a_run_counted_week_by_week() {
        assertThat(theWeekAfterIt.multiplierApplied())
                .as("a run of two weeks, on the ladder version 2 published")
                .isEqualByComparingTo("1.25");
        assertThat(theWeekAfterIt.productMultiplierApplied())
                .as("free savings changes nothing, so the combined rate is the run's own")
                .isEqualByComparingTo("1.00");
        assertThat(theWeekAfterIt.pointsEarned())
                .as("ninety whole euros at 1,25×, floored").isEqualTo(112);
    }

    /**
     * And the deposit made before the change still names the rate it was paid at, read back out of
     * the ledger after the ladder underneath it has moved.
     *
     * <p>The history rather than the answer to the deposit itself, because the two are different
     * reads of the same fact and only the second one is capable of going wrong: the ledger stores the
     * rate on the row, and a history that recomputed it would quietly reprice every deposit anybody
     * had ever made the morning a new version took effect.
     */
    @Test
    void a_deposit_already_made_keeps_the_rate_it_was_paid_at_and_its_history_still_names_it() {
        assertThat(theHistoryAsItReadsNow)
                .as("both deposits, read back after the change").hasSize(2);

        // Found by identifier rather than by position, because what the history is ordered by is
        // that resource's own decision and this test has no business restating it.
        DepositView first = inTheHistory(theWeekBeforeTheChange.id());
        assertThat(first.multiplierApplied())
                .as("the rate the first deposit was paid at, after a steeper ladder took effect")
                .isEqualByComparingTo("1.00");
        assertThat(first.pointsEarned())
                .as("and the points it was credited, which are never taken back").isEqualTo(60);

        DepositView second = inTheHistory(theWeekAfterIt.id());
        assertThat(second.multiplierApplied())
                .as("and the second one still reads what it was paid too")
                .isEqualByComparingTo("1.25");
    }

    /** One deposit out of the history by its identifier, insisted on rather than searched for. */
    private static DepositView inTheHistory(long depositId) {
        return theHistoryAsItReadsNow.stream()
                .filter(deposit -> deposit.id() == depositId)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "deposit " + depositId + " is not in the history this account reports"));
    }
}
