package io.dataroots.savingstreak.automation;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.MoneyMovementView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The money history marks the deposits a saving rule made, and marks nothing else.
 *
 * <p>User story 49, and the one thing that lets a customer tell what they did from what the
 * application did for them. A ledger in which a transfer a rule made on a Tuesday morning reads
 * exactly like a deposit the customer typed is a ledger that cannot answer "did I do that?".
 *
 * <p><strong>Read from the ledger the page reads</strong>, over HTTP, rather than from the rule's
 * own history: the claim is about what somebody looking at their money history is shown, and the
 * occurrence record has always known which deposit it made. The rule's history is used for one
 * thing only — to learn which deposit identifier the rule is behind — so that the assertion below
 * is about a specific row rather than about whichever row happened to carry the right amount.
 *
 * <p>All three kinds of movement are in the ledger at once, because the mark is only worth
 * anything if it separates them: an automatic deposit, a manual deposit of the same amount from the
 * same account, and a withdrawal. Two of the three must be unmarked or the flag says nothing.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test
 * winds the clock, and the ledger it reads has to be one nothing else has written to. It still is
 * not an empty one: the seeded household arrives with a few weeks of its own spending already
 * recorded, in the same list, which is what the ledger is for. Those rows are none of this test's
 * business — a spend is nobody's automatic deposit — so what is counted below is the savings half
 * of the list, which is exactly the three movements this test made.
 */
class ADepositARuleMadeIsMarkedAutomaticInTheMoneyHistoryApiTest extends ApiIntegrationTest {

    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String INTO_SAVINGS = "INTO_SAVINGS";

    private static final String OUT_OF_SAVINGS = "OUT_OF_SAVINGS";

    private static final String WHAT_THE_RULE_MOVES = "40.00";

    private static final String WHAT_SHE_MOVES_HERSELF = "40.00";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWithALedgerOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-automatic-deposit-is-marked"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_deposit_a_rule_made_is_automatic_and_the_ones_she_made_herself_are_not() {
        long hers = app.savingsAccountOf(ANKE);

        // Enough in savings for the withdrawal below to be possible, and the first of the two rows
        // that have to come back unmarked.
        app.deposit(hers, ANKE, WHAT_SHE_MOVES_HERSELF);

        LocalDate theDayItMovesOn = app.theDateTheClockReads().plusWeeks(1);
        SavingRuleView rule = app.leaveARuleStanding(hers,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "Forty a week", theDayItMovesOn.getDayOfWeek().name(), WHAT_THE_RULE_MOVES));

        app.aWeekPasses();
        app.runJob(THE_JOB);
        app.withdraw(hers, ANKE, "10.00");

        Long whatTheRuleDeposited = app.historyOf(hers, rule.id()).get(0).depositId();
        assertThat(whatTheRuleDeposited)
                .as("the rule fired and made a deposit, or there is nothing for the ledger to mark")
                .isNotNull();

        List<MoneyMovementView> ledger = savingsMovementsIn(app.moneyMovementsOf(ANKE));
        assertThat(ledger).as("two deposits and a withdrawal have happened").hasSize(3);

        MoneyMovementView automatically = ledger.stream()
                .filter(moved -> INTO_SAVINGS.equals(moved.direction()))
                .filter(moved -> moved.id() == whatTheRuleDeposited)
                .findFirst()
                .orElseThrow();
        assertThat(automatically.automatic())
                .as("the deposit the rule made is marked as the application's doing")
                .isTrue();

        assertThat(ledger.stream()
                .filter(moved -> !(INTO_SAVINGS.equals(moved.direction())
                        && moved.id() == whatTheRuleDeposited))
                .map(MoneyMovementView::automatic))
                .as("and every other movement — the deposit she made herself, of the same amount "
                        + "from the same account, and the withdrawal — is not marked, or the flag "
                        + "would be saying nothing about who did what")
                .containsOnly(false)
                .hasSize(2);
    }

    /**
     * What was paid into savings and what was taken out of them, without the spending the seeded
     * household arrived carrying.
     *
     * <p>Filtering on the direction rather than on a null savings account, because the direction is
     * the word the endpoint sends and a reader of the assertion should not have to know which fields
     * a spend leaves empty.
     */
    private static List<MoneyMovementView> savingsMovementsIn(MoneyMovementView[] ledger) {
        return Arrays.stream(ledger)
                .filter(moved -> INTO_SAVINGS.equals(moved.direction())
                        || OUT_OF_SAVINGS.equals(moved.direction()))
                .toList();
    }
}
