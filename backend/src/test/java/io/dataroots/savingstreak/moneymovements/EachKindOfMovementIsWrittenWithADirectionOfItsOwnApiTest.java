package io.dataroots.savingstreak.moneymovements;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.deposits.MoneyMovementDirection;
import io.dataroots.savingstreak.support.AMoveView;
import io.dataroots.savingstreak.support.ATermBrokenView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.MoneyMovementView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every kind of movement this application can write is written with the direction that says what it
 * was, and the ledger is read back one direction at a time to prove it.
 *
 * <p><strong>The direction and not the balance, which is the whole point of this class.</strong>
 * Every other test that moves money asserts what the account was left holding afterwards, and a
 * balance is blind to the word the movement was filed under: euros taken off an account subtract
 * the same whether the row calls them a withdrawal or a charge. So a movement written with the
 * wrong direction passes every balance assertion in the suite and is wrong in every reader
 * downstream at once — the page draws an arrow to an account that was never credited, and the walk
 * that reconstructs a daily balance signs it the wrong way and pays interest on money the bank has
 * taken. That last one is not hypothetical: it is the defect
 * {@code InterestAfterABrokenTermIsPaidOnWhatTheChargeLeftBehindApiTest} was written for, one level
 * below the service this class reads.
 *
 * <p><strong>Five directions, five ways of moving money, and the last test says they are all of
 * them.</strong> The four before it each name one word and assert the row filed under it; the last
 * compares the words this ledger actually produced against the whole of
 * {@link MoneyMovementDirection}, so a sixth direction added to that enum fails here until
 * something in this class produces one. That is the reading half of what the service now does with
 * a switch: the compiler refuses a new origin or purpose that has not been told which way it moves,
 * and this test refuses a new direction that nothing has been shown writing.
 *
 * <p>One application, one customer and one run of presses in {@code @BeforeAll}, because the five
 * movements are one sequence: the term has to be broken before the account it was broken on can be
 * withdrawn from or moved out of, and a month of interest needs a clock wound past the account's
 * first month, which cannot be wound back. Each test then reads the same ledger and asserts about
 * its own row, so a failure names the direction that is wrong rather than the press that came
 * before it.
 *
 * <p>Its own application on a database nothing has ever been written to, for the reason every
 * clock-moving test gives: a wound clock cannot be shared.
 */
class EachKindOfMovementIsWrittenWithADirectionOfItsOwnApiTest extends ApiIntegrationTest {

    /** The twelve-month fixed term the catalogue seeds, which is the one product that can be broken. */
    private static final String THE_FIXED_TERM = "FIXED12";

    /** The sweep that pays every account every month it has passed, by the name it is run under. */
    private static final String THE_INTEREST_SWEEP = "postMonthlyInterest";

    private static final String WHAT_WAS_PAID_IN = "1200.00";
    private static final String WHAT_WAS_TAKEN_BACK = "100.00";
    private static final String WHAT_WAS_MOVED = "250.00";

    /**
     * Comfortably past the end of the account's first month, so that the sweep has a whole period
     * to pay and the ledger has a month's interest in it.
     */
    private static final int DAYS_ENOUGH_FOR_A_MONTH_TO_END = 40;

    private static AnApplicationWithAClockToMove app;
    private static String them;
    private static long theEverydayAccount;
    private static long theTermTheyBroke;
    private static long theAccountTheMoveReached;
    private static ATermBrokenView theBreak;
    private static AMoveView theMove;
    private static List<MoneyMovementView> ledger;

    @BeforeAll
    static void moveMoneyEveryWayThisApplicationCanAndThenReadItBack() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-movement-directions"));
        them = app.aCustomerOfItsOwn("every direction");
        theEverydayAccount = app.currentAccountOf(them);
        theAccountTheMoveReached = app.savingsAccountOf(them);
        theTermTheyBroke = app.openASavingsAccountOn(them, THE_FIXED_TERM);

        // A deposit, a charge, a withdrawal and a move, in the only order they can happen in: the
        // term has to hold money before breaking it costs anything, and breaking it is what moves
        // the account onto free savings and lets the last two presses through.
        app.deposit(theTermTheyBroke, them, WHAT_WAS_PAID_IN);
        theBreak = app.breakTheTermOn(theTermTheyBroke);
        app.withdraw(theTermTheyBroke, them, WHAT_WAS_TAKEN_BACK);
        theMove = app.move(theTermTheyBroke, theAccountTheMoveReached, WHAT_WAS_MOVED);

        // And a month of interest, which is the one movement nobody presses a button for.
        app.daysPass(DAYS_ENOUGH_FOR_A_MONTH_TO_END);
        app.runJob(THE_INTEREST_SWEEP);

        ledger = List.of(app.moneyMovementsOf(them));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void money_paid_in_out_of_an_everyday_account_is_written_into_savings() {
        assertThat(theRowsWrittenWith(MoneyMovementDirection.INTO_SAVINGS))
                .singleElement()
                .satisfies(paidIn -> {
                    assertThat(paidIn.amount()).isEqualByComparingTo(WHAT_WAS_PAID_IN);
                    assertThat(paidIn.savingsAccountId()).isEqualTo(theTermTheyBroke);
                    // The other end of it, which is what makes this direction and no other one
                    // true: euros crossed the boundary out of an everyday account the customer
                    // could name on a bank statement.
                    assertThat(paidIn.currentAccountId()).isEqualTo(theEverydayAccount);
                    assertThat(paidIn.automatic()).isFalse();
                });
    }

    @Test
    void money_taken_back_out_is_written_out_of_savings() {
        assertThat(theRowsWrittenWith(MoneyMovementDirection.OUT_OF_SAVINGS))
                .singleElement()
                .satisfies(takenBack -> {
                    assertThat(takenBack.amount()).isEqualByComparingTo(WHAT_WAS_TAKEN_BACK);
                    assertThat(takenBack.savingsAccountId()).isEqualTo(theTermTheyBroke);
                    // The euros went back to the customer, which is exactly what this word says
                    // and exactly what the charge below it does not.
                    assertThat(takenBack.currentAccountId()).isEqualTo(theEverydayAccount);
                    assertThat(takenBack.pointsEarned())
                            .as("nothing has ever earned a point for leaving savings")
                            .isZero();
                });
    }

    /**
     * The charge for breaking a term early is its own word, and the row says so where it would be
     * cheapest to have called it a withdrawal.
     *
     * <p>The amount is read back off what the break reported rather than worked out here, because
     * the amount is only how this row is picked out of the ledger — what is under test is the word
     * it was filed under. The figure itself has a test of its own.
     */
    @Test
    void the_price_of_breaking_a_term_early_is_written_as_a_charge_rather_than_as_a_withdrawal() {
        assertThat(theRowsWrittenWith(MoneyMovementDirection.AN_EARLY_EXIT_CHARGE))
                .singleElement()
                .satisfies(charge -> {
                    assertThat(charge.amount()).isEqualByComparingTo(theBreak.charge());
                    assertThat(charge.savingsAccountId()).isEqualTo(theTermTheyBroke);
                    // Nothing at all, and it is the null that carries the difference: the euros
                    // reached nobody, so a row naming a current account would have a page drawing
                    // an arrow to an account that was never credited.
                    assertThat(charge.currentAccountId())
                            .as("a charge has no account at the other end of it")
                            .isNull();
                    assertThat(charge.pointsEarned()).isZero();
                });
    }

    @Test
    void a_months_interest_is_written_as_interest_arriving_rather_than_as_a_deposit() {
        assertThat(theRowsWrittenWith(MoneyMovementDirection.INTEREST_INTO_SAVINGS))
                .isNotEmpty()
                .allSatisfy(interest -> {
                    assertThat(interest.amount()).isPositive();
                    // Nothing was debited to pay it, which is the whole of why it is not a deposit
                    // with a flag on it, and it earned nothing, which is the rule rather than a gap.
                    assertThat(interest.currentAccountId())
                            .as("the bank added it, so there is no account it came out of")
                            .isNull();
                    assertThat(interest.pointsEarned())
                            .as("a euro the bank added has never earned a point here")
                            .isZero();
                    assertThat(interest.automatic())
                            .as("that word means a saving rule, and no rule pays interest")
                            .isFalse();
                });
    }

    /**
     * A move is one entry naming both savings accounts, which is the direction no other kind of
     * movement could be written with: it is the only one with savings at both ends.
     */
    @Test
    void a_move_between_two_savings_accounts_is_written_as_one_entry_naming_both_of_them() {
        assertThat(theRowsWrittenWith(MoneyMovementDirection.BETWEEN_SAVINGS_ACCOUNTS))
                .as("one thing the customer did, although the ledgers underneath hold a row at "
                        + "each end of it")
                .singleElement()
                .satisfies(move -> {
                    assertThat(move.amount()).isEqualByComparingTo(WHAT_WAS_MOVED);
                    assertThat(move.savingsAccountId())
                            .as("the account the euros left")
                            .isEqualTo(theMove.fromSavingsAccountId());
                    assertThat(move.toSavingsAccountId())
                            .as("and the account they arrived in")
                            .isEqualTo(theMove.toSavingsAccountId());
                    assertThat(move.currentAccountId())
                            .as("no everyday account was touched at either end")
                            .isNull();
                    assertThat(move.pointsEarned())
                            .as("the euros were earned on once already, next door")
                            .isZero();
                });
    }

    /**
     * The four tests above name every direction a movement can be written with, and this is what
     * keeps that true.
     *
     * <p>Asserted against the enum itself rather than against a list written down here, because a
     * list written down here would be the same fall-through this ticket went round removing: it
     * would go on passing on the day a sixth direction was added and nothing was ever shown writing
     * one. The service can no longer compile a deposit origin or a withdrawal purpose it has not
     * been told the direction of; this is the other half of the same guarantee, and it fails rather
     * than compiles because a direction nothing produces is not something a compiler can see.
     */
    @Test
    void every_direction_a_movement_can_be_written_with_is_one_of_the_tests_above() {
        Set<String> written = ledger.stream()
                .map(MoneyMovementView::direction)
                .collect(Collectors.toSet());

        assertThat(written)
                .as("the ledger this customer's presses wrote, against the whole of the enum the "
                        + "application writes movements with")
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(MoneyMovementDirection.values())
                        .map(Enum::name)
                        .toList());
    }

    /** The rows of the ledger filed under one word, read as the API sends the word rather than mapped. */
    private static List<MoneyMovementView> theRowsWrittenWith(MoneyMovementDirection direction) {
        return ledger.stream()
                .filter(moved -> moved.direction().equals(direction.name()))
                .toList();
    }
}
