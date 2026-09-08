package io.dataroots.savingstreak.moneymovements;

import java.util.Arrays;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove.WithdrawalView;
import io.dataroots.savingstreak.support.MoneyMovementView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Everything a customer has moved, in or out, across every savings account they hold, as one list
 * newest first.
 *
 * <p>The question a person asks before they ask anything else about their money, and until now the
 * application could only answer it one account and one direction at a time. Somebody saving towards
 * two goals moved their money once; reading it back as four lists to interleave by eye is not an
 * answer.
 *
 * <p>Its own application on a database nothing has been written to, because "newest first" and "every
 * account" are claims about the whole of a customer's ledger and a shared database would have other
 * tests' deposits in it.
 */
class TheMoneyMovementLedgerApiTest extends ApiIntegrationTest {

    private static final String INTO_SAVINGS = "INTO_SAVINGS";
    private static final String OUT_OF_SAVINGS = "OUT_OF_SAVINGS";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWithALedgerOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-money-movements"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void every_movement_across_every_account_is_in_the_ledger_newest_first() {
        long onePot = app.savingsAccountOf(ANKE);

        // Empty before anything has moved, rather than refused: somebody who has never paid anything
        // in has moved nothing, which is an answer, and being told there is no such customer would
        // not be. Asserted here rather than in a test of its own because this application's ledger is
        // only empty once, and a second test would be asserting whichever order the runner chose.
        assertThat(app.moneyMovementsOf(ANKE))
                .as("nothing has moved yet")
                .isEmpty();

        long theOtherPot = app.otherSavingsAccountOf(ANKE);

        DepositView intoOnePot = app.deposit(onePot, ANKE, "60.00");
        DepositView intoTheOther = app.deposit(theOtherPot, ANKE, "25.50");
        WithdrawalView back = app.withdraw(onePot, ANKE, "10.00");

        // Said out loud rather than assumed. A moment is only kept to the millisecond and the ledger
        // cannot order two movements that share one — so if these three requests ever landed inside
        // a millisecond of each other, the ordering asserted below would be arbitrary and this line
        // is what would say so, instead of the assertion failing for a reason nobody could see.
        assertThat(back.withdrawnAt())
                .as("newest-first is only assertable if these movements are separate moments")
                .isAfter(intoTheOther.depositedAt());
        assertThat(intoTheOther.depositedAt()).isAfter(intoOnePot.depositedAt());

        MoneyMovementView[] ledger = app.moneyMovementsOf(ANKE);

        // Three movements, both accounts, both directions, and no fourth thing invented.
        assertThat(ledger).hasSize(3);
        assertThat(Arrays.stream(ledger).map(MoneyMovementView::savingsAccountId).distinct())
                .as("both of the customer's savings accounts are in one ledger")
                .containsExactlyInAnyOrder(onePot, theOtherPot);

        // Newest first, which is what a person reads: the withdrawal happened last and is first.
        MoneyMovementView newest = ledger[0];
        assertThat(newest.direction()).isEqualTo(OUT_OF_SAVINGS);
        assertThat(newest.savingsAccountId()).isEqualTo(onePot);
        assertThat(newest.amount()).isEqualByComparingTo("10.00");
        // A withdrawal earns nothing, which is the rule rather than a gap in the record.
        assertThat(newest.pointsEarned()).isZero();

        MoneyMovementView middle = ledger[1];
        assertThat(middle.direction()).isEqualTo(INTO_SAVINGS);
        assertThat(middle.id()).isEqualTo(intoTheOther.id());
        assertThat(middle.savingsAccountId()).isEqualTo(theOtherPot);
        assertThat(middle.amount()).isEqualByComparingTo("25.50");
        // What the deposit earned, as the deposit itself reported it: the money and the points are
        // one event and this list is the one place they are read side by side.
        assertThat(middle.pointsEarned()).isEqualTo(intoTheOther.pointsEarned()).isEqualTo(25);

        MoneyMovementView oldest = ledger[2];
        assertThat(oldest.direction()).isEqualTo(INTO_SAVINGS);
        assertThat(oldest.id()).isEqualTo(intoOnePot.id());
        assertThat(oldest.savingsAccountId()).isEqualTo(onePot);
        assertThat(oldest.amount()).isEqualByComparingTo("60.00");
        assertThat(oldest.pointsEarned()).isEqualTo(intoOnePot.pointsEarned()).isEqualTo(60);

        // Every movement names the everyday account at the other end of it, and in this seed the
        // customer has one — so all three name it and none of them names an account of somebody
        // else's.
        assertThat(Arrays.stream(ledger).map(MoneyMovementView::currentAccountId).distinct())
                .hasSize(1);

        // And nothing of anybody else's is in it. Asserted with a real movement of Bram's to exclude
        // rather than by his simply having none: a ledger that had forgotten to filter by account at
        // all would answer an empty question correctly and this one wrongly.
        long bramsPot = app.savingsAccountOf(BRAM);
        app.deposit(bramsPot, BRAM, "13.00");
        MoneyMovementView[] hers = app.moneyMovementsOf(ANKE);
        assertThat(Arrays.stream(hers).map(MoneyMovementView::savingsAccountId))
                .as("her ledger is hers, and Bram's pot is not one of her accounts")
                .doesNotContain(bramsPot);
        assertThat(hers).hasSize(3);
        MoneyMovementView[] his = app.moneyMovementsOf(BRAM);
        assertThat(his).hasSize(1);
        assertThat(his[0].savingsAccountId()).isEqualTo(bramsPot);
        assertThat(his[0].amount()).isEqualByComparingTo("13.00");

        // And the moments are in the order the list claims, which is the assertion a merge of two
        // sorted lists most easily gets wrong.
        assertThat(Arrays.stream(ledger).map(MoneyMovementView::movedAt).toList())
                .isSortedAccordingTo((one, another) -> another.compareTo(one));
    }
}
