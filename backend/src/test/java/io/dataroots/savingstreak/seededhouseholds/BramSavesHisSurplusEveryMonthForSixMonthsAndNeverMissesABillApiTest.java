package io.dataroots.savingstreak.seededhouseholds;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.seededhouseholds.TheMonthTheseTestsWatch.nightsInTheMonthFrom;
import static io.dataroots.savingstreak.seededhouseholds.TheMonthTheseTestsWatch.startsOnTheTwentySixth;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tight household can save a fixed amount the size of its own surplus, every month for six
 * months, and never miss a bill.
 *
 * <p>This is the half of the seed that is easy to get wrong and impossible to see by reading the
 * numbers. Bram exists so that a trainer can show a saving rule <em>failing</em>, and the temptation
 * when writing his figures is to leave him so little room that the failure is guaranteed. An
 * application whose tight household misses the rent whatever its holder does is not teaching that
 * saving is a judgement about how large a claim you can afford; it is teaching that saving is
 * impossible, which is the opposite lesson and a worse one.
 *
 * <p>So the claim is a deliberately demanding one: a rule sized at the whole of his surplus — 805.00
 * a month against 1750.00 in and 945.00 out — taking its cut on the morning his salary lands, which
 * is the earliest and least forgiving moment it could take it. Six months, because one is a month he
 * could coast through on his opening balance alone; six says the month pays for itself.
 *
 * <p>Six months of nights rather than six winds, for the reason
 * {@code AnApplicationWithAClockToMove.aNightPasses} gives: the rule is only a real test of the
 * household if the bills meet the balance the rule actually left that morning. In one wind the rule
 * would take its cut from a stretch of salaries that had all arrived first, which is a month nobody
 * lives.
 */
class BramSavesHisSurplusEveryMonthForSixMonthsAndNeverMissesABillApiTest extends ApiIntegrationTest {

    /** What is left after 1750.00 of salary has met 945.00 of bills: the whole of his room. */
    private static final String HIS_WHOLE_SURPLUS = "805.00";

    private static final int SIX_MONTHS = 6;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-bram-saves-his-surplus"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_fixed_rule_the_size_of_his_surplus_runs_for_six_months_without_starving_a_bill() {
        LocalDate month = startsOnTheTwentySixth(app, BRAM);
        BigDecimal heldAtTheStart = app.currentAccountBalanceOf(BRAM);

        long savingsAccount = app.savingsAccountOf(BRAM);
        app.leaveARuleStanding(savingsAccount, hisWholeSurplusOnPayday(app.currentAccountOf(BRAM)));
        assertThat(app.balancesOf(savingsAccount).moneyBalance())
                .as("he has saved nothing yet, so every euro below is one this test watched move")
                .isEqualByComparingTo(BigDecimal.ZERO);

        for (int monthsSoFar = 1; monthsSoFar <= SIX_MONTHS; monthsSoFar++) {
            app.nightsPass(nightsInTheMonthFrom(month));
            month = month.plusMonths(1);
            assertThat(app.theDateTheClockReads()).isEqualTo(month);

            assertThat(app.arrearsOf(BRAM))
                    .as("month " + monthsSoFar + " of six: a rule sized inside his surplus leaves "
                            + "every bill payable, so nothing is owed")
                    .isEmpty();
            assertThat(app.balancesOf(savingsAccount).moneyBalance())
                    .as("month " + monthsSoFar + " of six: " + HIS_WHOLE_SURPLUS + " a month has "
                            + "gone into savings and stayed there")
                    .isEqualByComparingTo(
                            new BigDecimal(HIS_WHOLE_SURPLUS).multiply(new BigDecimal(monthsSoFar)));
            assertThat(app.currentAccountBalanceOf(BRAM))
                    .as("month " + monthsSoFar + " of six: salary in, surplus saved, bills out — a "
                            + "month that pays for itself leaves the current account where it was")
                    .isEqualByComparingTo(heldAtTheStart);
        }
    }

    /**
     * Everything he can afford, moved on the morning it arrives.
     *
     * <p>On payday rather than on a day of the month he chose, because that is the least forgiving
     * moment a fixed rule can take its cut at: the salary lands at one, the rule takes its share at
     * two, and the bills are presented at half past against whatever is left. A rule placed after
     * his rent would prove much less.
     */
    private static Map<String, Object> hisWholeSurplusOnPayday(long fromCurrentAccountId) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("name", "All I can spare");
        rule.put("fromCurrentAccountId", fromCurrentAccountId);
        rule.put("trigger", "ON_PAYDAY");
        rule.put("howMuchMoves", "A_FIXED_AMOUNT");
        rule.put("amount", HIS_WHOLE_SURPLUS);
        return rule;
    }
}
