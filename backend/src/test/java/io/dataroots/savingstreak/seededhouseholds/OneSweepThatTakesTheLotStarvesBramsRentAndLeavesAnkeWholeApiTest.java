package io.dataroots.savingstreak.seededhouseholds;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ArrearView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.seededhouseholds.TheMonthTheseTestsWatch.nightsInTheMonthFrom;
import static io.dataroots.savingstreak.seededhouseholds.TheMonthTheseTestsWatch.startsOnTheTwentySixth;
import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same over-commitment — sweep the current account down to nothing on the morning the salary
 * lands — starves Bram's rent inside one cycle and leaves Anke owing nothing at all.
 *
 * <p>User story 39: two households with different room to manoeuvre, so that a trainer can show
 * saving working and saving failing without changing anything but whose account the rule stands on.
 * If the same rule did the same thing to both of them, the second household would be two more rows
 * in the database and nothing else, and the seed would teach by assertion rather than by comparison.
 *
 * <p><strong>What actually makes the difference, since it is not the size of the numbers.</strong> A
 * sweep with a floor of nothing empties whatever it finds, so a household survives a bill only if a
 * salary lands between the last sweep and that bill. Bram's rent falls four days after his payday:
 * the sweep takes the salary on the 28th and the rent on the 1st meets an empty account. Anke's
 * bills for the month ahead fall on the 1st, 5th, 12th and 20th — all of them before her payday on
 * the 25th — so the month she is about to live is paid for by the salary she has already had, and
 * the sweep does not reach her until the end of it. Both facts are properties of the seeded shapes
 * and of nothing else, which is what this test is for.
 *
 * <p>Both rules are written the same way and both accounts are watched over the same month, from the
 * 26th — see {@code TheMonthTheseTestsWatch} for why the calendar has to be pinned down at all.
 *
 * <p>Night by night, and here it is the whole point. The 02:00 rules run empties the account and the
 * 02:30 bills run finds nothing there; a test that wound the month on in one move and ran each job
 * once would sweep against salaries that had all already arrived and would leave every bill of both
 * households unpaid, which is a month neither of them lives.
 */
class OneSweepThatTakesTheLotStarvesBramsRentAndLeavesAnkeWholeApiTest extends ApiIntegrationTest {

    /** Sweeping to nothing: the over-commitment this application exists to let somebody make. */
    private static final String A_FLOOR_OF_NOTHING = "0.00";

    private static AnApplicationWithAClockToMove app;
    private static LocalDate startOfTheCycle;

    @BeforeAll
    static void startAnApplicationWhoseMonthThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-one-sweep-two-households"));
        startOfTheCycle = startsOnTheTwentySixth(app, ANKE, BRAM);
        app.leaveARuleStanding(app.savingsAccountOf(ANKE),
                everythingOnPaydayFrom(app.currentAccountOf(ANKE)));
        app.leaveARuleStanding(app.savingsAccountOf(BRAM),
                everythingOnPaydayFrom(app.currentAccountOf(BRAM)));
        app.nightsPass(nightsInTheMonthFrom(startOfTheCycle));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void one_cycle_of_it_leaves_brams_rent_unpaid() {
        assertThat(app.theDateTheClockReads())
                .as("one cycle, counted as the calendar month the two rules were left standing for")
                .isEqualTo(startOfTheCycle.plusMonths(1));

        assertThat(app.arrearsOf(BRAM))
                .as("the salary went into savings on the 28th and the rent on the 1st met an empty "
                        + "account: it is not waived, not partly taken, and not forgotten")
                .isNotEmpty();
        ArrearView theOldest = app.arrearsOf(BRAM).get(0);
        assertThat(theOldest.billName())
                .as("the rent is the first thing his over-commitment costs him, being the first "
                        + "thing due after payday")
                .isEqualTo("Rent");
        assertThat(theOldest.amount())
                .as("and it stays owed at exactly what it was worth — no fee, no interest")
                .isEqualByComparingTo(new BigDecimal("820.00"));
        assertThat(theOldest.dueOn().getDayOfMonth()).isEqualTo(1);
        assertThat(app.currentAccountBalanceOf(BRAM))
                .as("a bill that could not be paid takes nothing at all, so the balance is at the "
                        + "floor the sweep left it at and not below it")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void the_same_cycle_of_the_same_rule_leaves_anke_owing_nothing() {
        assertThat(app.arrearsOf(ANKE))
                .as("her bills for the month all fall before her payday, so the month is paid for "
                        + "out of the salary she had already been given when the rule was written — "
                        + "sweeping the lot is a mistake this household survives for a while")
                .isEmpty();
        assertThat(app.billsOf(ANKE))
                .as("and every one of them really did go out, rather than there being nothing left "
                        + "standing to present")
                .allSatisfy(bill -> assertThat(bill.lastTakenOn())
                        .as(bill.name() + " left the account inside the cycle")
                        .isNotNull()
                        .isAfter(startOfTheCycle));
    }

    @Test
    void both_of_them_really_did_sweep_the_lot_so_the_difference_is_the_household_and_not_the_rule() {
        assertThat(app.balancesOf(app.savingsAccountOf(ANKE)).moneyBalance())
                .as("Anke's salary went the same way Bram's did: the rule is the same rule")
                .isPositive();
        assertThat(app.balancesOf(app.savingsAccountOf(BRAM)).moneyBalance())
                .isPositive();
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("and it emptied her account too — she is whole because of when her bills fall, "
                        + "not because the sweep spared her")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Everything above nothing, on the morning the salary lands. The same rule for both households. */
    private static Map<String, Object> everythingOnPaydayFrom(long fromCurrentAccountId) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("name", "Every euro, every payday");
        rule.put("fromCurrentAccountId", fromCurrentAccountId);
        rule.put("trigger", "ON_PAYDAY");
        rule.put("howMuchMoves", "EVERYTHING_ABOVE");
        rule.put("floor", A_FLOOR_OF_NOTHING);
        return rule;
    }
}
