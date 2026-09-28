package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AMoveView;
import io.dataroots.savingstreak.support.AllocationsView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.MoneyMovementView;
import io.dataroots.savingstreak.support.SharedPotView;
import io.dataroots.savingstreak.support.WhatMovingWouldCostView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsproducts.ASaverChoosingAProduct.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Changing your mind about a savings product stops being punished: money moves from one of a
 * customer's savings accounts to another of their own in one operation, and the operation costs
 * neither the points those euros have already earned nor the week they were counted into.
 *
 * <p><strong>What these tests are really about is a combination rather than a rule.</strong> Left
 * as a withdrawal followed by a deposit, moving five thousand euros to a better product earns
 * nothing on arrival — correct, because a euro saved twice is one euro — and nets the week to
 * nothing — also correct, because five thousand out and five thousand back in is no new saving.
 * Every rule involved is individually right and together they charge somebody a week and a streak
 * for taking the bank's better offer. So the assertions here are mostly about figures that have
 * <em>not</em> moved: the week, the streak and the most the customer has ever saved, each read
 * either side of a move and found where it was.
 *
 * <p>The one thing a move does cost is the loyalty clock, and that is asserted as a cost rather
 * than hidden: the arriving money is a new deposit with a new anniversary, and the reading says so
 * before the button.
 *
 * <p>Run against the shared application, because nothing here needs a clock wound: a move is a
 * press, and what it does or does not change is visible the same second. The tests whose subject is
 * a source product's conditions need months to pass and run an application of their own, in
 * {@link AMoveIsRefusedByTheSourceProductsConditionsApiTest}.
 */
class MovingMoneyBetweenYourOwnSavingsAccountsApiTest extends ApiIntegrationTest {

    @Test
    void money_moves_between_two_savings_accounts_the_same_customer_holds_in_one_press() {
        ASaverChoosingAProduct saver = new ASaverChoosingAProduct(http, "Marieke who switches");
        long instant = saver.theAccountTheyWereOpenedWith();
        long fixed = saver.open("FIXED12").id();
        saver.payIn(instant, "400.00");
        BigDecimal everydayMoney = saver.whatTheirCurrentAccountHolds();

        AMoveView moved = saver.move(instant, fixed, "250.00");

        assertThat(moved.fromSavingsAccountId()).isEqualTo(instant);
        assertThat(moved.toSavingsAccountId()).isEqualTo(fixed);
        assertThat(moved.customerId()).isEqualTo(saver.customerId());
        assertThat(moved.amount()).isEqualByComparingTo("250.00");
        // The point of the whole operation: money that has been earned on once earns nothing for
        // changing which of its holder's accounts it sits in, and it is not charged for doing so
        // either.
        assertThat(moved.pointsEarned()).isZero();
        assertThat(saver.account(instant).moneyBalance()).isEqualByComparingTo("150.00");
        assertThat(saver.account(fixed).moneyBalance()).isEqualByComparingTo("250.00");
        // And nothing went near an everyday account on the way, which is what makes it a move
        // rather than a withdrawal somebody happened to follow with a deposit: the balance the
        // deposit left behind is still there, untouched by two hundred and fifty euros travelling
        // between two savings accounts.
        assertThat(saver.whatTheirCurrentAccountHolds()).isEqualByComparingTo(everydayMoney);
    }

    @Test
    void a_move_carries_the_earned_on_figure_across_so_the_mark_does_not_move() {
        ASaverChoosingAProduct saver = new ASaverChoosingAProduct(http, "Joris who keeps his mark");
        long instant = saver.theAccountTheyWereOpenedWith();
        long core = saver.open("CORE").id();
        saver.payIn(instant, "600.00");
        BigDecimal markBefore = saver.account(instant).mostEverSaved();
        long pointsBefore = saver.account(instant).pointsBalance();

        AMoveView moved = saver.move(instant, core, "600.00");

        // The figure that explains why nothing was earned: every euro that moved had already been
        // paid for, so all six hundred of them arrive already spoken for.
        assertThat(moved.earnedOnCarriedAcross()).isEqualByComparingTo("600.00");
        assertThat(moved.pointsEarned()).isZero();
        assertThat(saver.account(core).mostEverSaved()).isEqualByComparingTo(markBefore);
        assertThat(saver.account(core).pointsBalance()).isEqualTo(pointsBefore);

        // And the reading that proves the mark really is intact rather than merely printing the
        // same figure: take the six hundred back out and pay it in again, and it earns nothing,
        // because those euros have been saved once and a euro saved twice is one euro. Had the move
        // dropped the earned-on figure on the way across, the mark would have fallen with it and
        // this deposit would have been paid for a second time.
        saver.takeOut(core, "600.00");
        saver.payIn(core, "600.00");

        assertThat(saver.account(core).pointsBalance()).isEqualTo(pointsBefore);
        assertThat(saver.account(core).mostEverSaved()).isEqualByComparingTo(markBefore);
    }

    @Test
    void a_move_is_left_out_of_the_week_on_both_sides_so_it_neither_secures_a_week_nor_breaks_one() {
        ASaverChoosingAProduct saver = new ASaverChoosingAProduct(http, "Sofie mid-week");
        long instant = saver.theAccountTheyWereOpenedWith();
        long core = saver.open("CORE").id();
        saver.payIn(instant, "900.00");
        BigDecimal thisWeek = saver.account(instant).newSavingsThisWeek();
        int streak = saver.account(instant).currentStreakWeeks();

        saver.move(instant, core, "900.00");

        // Nine hundred euros left one account and nine hundred arrived in the other, and the week
        // counts neither. Counted as a deposit it would have secured the week out of euros that
        // were already saved; counted as a withdrawal it would have netted the week to nothing and
        // taken a run of weeks down with it.
        assertThat(saver.account(core).newSavingsThisWeek()).isEqualByComparingTo(thisWeek);
        assertThat(saver.account(instant).newSavingsThisWeek()).isEqualByComparingTo(thisWeek);
        assertThat(saver.account(core).currentStreakWeeks()).isEqualTo(streak);
        // The account's own two histories say the same thing from the other side: a move is not a
        // deposit anybody made and not a withdrawal anybody took.
        assertThat(saver.read("/api/savings-accounts/{id}/deposits", core).getBody().isEmpty())
                .as("the deposits paid into the account the money arrived in").isTrue();
        assertThat(saver.read("/api/savings-accounts/{id}/withdrawals", instant).getBody().isEmpty())
                .as("the withdrawals taken out of the account the money left").isTrue();
    }

    @Test
    void a_move_appears_in_the_record_of_money_that_moved_as_one_move() {
        ASaverChoosingAProduct saver = new ASaverChoosingAProduct(http, "Pieter reading back");
        long instant = saver.theAccountTheyWereOpenedWith();
        long fixed = saver.open("FIXED12").id();
        saver.payIn(instant, "300.00");

        saver.move(instant, fixed, "120.00");

        List<MoneyMovementView> ledger = saver.theMoneyThatMoved();
        // Two things happened to this customer's money: one deposit, and one move. Not three.
        assertThat(ledger).hasSize(2);
        MoneyMovementView move = ledger.get(0);
        assertThat(move.direction()).isEqualTo("BETWEEN_SAVINGS_ACCOUNTS");
        assertThat(move.savingsAccountId()).isEqualTo(instant);
        assertThat(move.toSavingsAccountId()).isEqualTo(fixed);
        assertThat(move.amount()).isEqualByComparingTo("120.00");
        assertThat(move.pointsEarned()).isZero();
        // No everyday account at either end, which is what a page draws its arrow from.
        assertThat(move.currentAccountId()).isNull();
        assertThat(ledger.get(1).direction()).isEqualTo("INTO_SAVINGS");
    }

    @Test
    void the_reading_says_the_money_arrives_with_a_new_anniversary_before_the_move_is_confirmed() {
        ASaverChoosingAProduct saver = new ASaverChoosingAProduct(http, "Lotte weighing it up");
        long instant = saver.theAccountTheyWereOpenedWith();
        long fixed = saver.open("FIXED12").id();
        saver.payIn(instant, "500.00");

        WhatMovingWouldCostView cost = saver.whatMovingWouldCost(instant, fixed, "500.00");

        assertThat(cost.fromSavingsAccountId()).isEqualTo(instant);
        assertThat(cost.toSavingsAccountId()).isEqualTo(fixed);
        assertThat(cost.amount()).isEqualByComparingTo("500.00");
        // The clock starts again, which is the whole price: twelve months from today rather than
        // whatever the euros were part-way through.
        assertThat(cost.newAnniversary())
                .isEqualTo(saver.theDayTheApplicationIsStandingOn().plusMonths(12));
        assertThat(cost.pointsOnTheNewAnniversary()).isPositive();
        // And what is being given up, named as a day rather than only as a number of points:
        // somebody eleven months into a clock is being asked a different question from somebody
        // eleven days in.
        assertThat(cost.depositsItWouldDrawDown()).isEqualTo(1);
        assertThat(cost.soonestAnniversaryGivenUp())
                .isEqualTo(saver.theDayTheApplicationIsStandingOn().plusMonths(12));
        assertThat(cost.pointsGivenUp()).isPositive();
        // Nothing moved. It was a question.
        assertThat(saver.account(instant).moneyBalance()).isEqualByComparingTo("500.00");
        assertThat(saver.account(fixed).moneyBalance()).isEqualByComparingTo("0.00");

        AMoveView moved = saver.move(instant, fixed, "500.00");

        // The day quoted and the day the money actually got are the same day, because both are
        // read the same way rather than worked out twice.
        assertThat(moved.newAnniversary()).isEqualTo(cost.newAnniversary());
        assertThat(moved.pointsOnTheNewAnniversary()).isEqualTo(cost.pointsOnTheNewAnniversary());
    }

    @Test
    void a_move_between_accounts_held_by_different_customers_is_refused() {
        ASaverChoosingAProduct mine = new ASaverChoosingAProduct(http, "Anneke with her own");
        ASaverChoosingAProduct theirs = new ASaverChoosingAProduct(http, "Bram with his own");
        mine.payIn(mine.theAccountTheyWereOpenedWith(), "100.00");

        ResponseEntity<JsonNode> refused = mine.tryToMove(mine.theAccountTheyWereOpenedWith(),
                theirs.theAccountTheyWereOpenedWith(), "100.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).isEqualTo(
                "Money can only be moved between two savings accounts held by the same customer.");
        // Whose the other account is is never named, so a refusal tells nobody anything about a
        // customer who is not them.
        assertThat(reasonGivenBy(refused)).doesNotContain("Bram");
        assertThat(mine.account(mine.theAccountTheyWereOpenedWith()).moneyBalance())
                .isEqualByComparingTo("100.00");
        assertThat(theirs.account(theirs.theAccountTheyWereOpenedWith()).moneyBalance())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void a_move_into_or_out_of_an_account_behind_a_shared_pot_is_refused() {
        ASaverChoosingAProduct saver = new ASaverChoosingAProduct(http, "Katrien with a pot");
        long own = saver.theAccountTheyWereOpenedWith();
        saver.payIn(own, "200.00");
        long potAccount = aPotOpenedBy(saver.customerId()).savingsAccountId();

        ResponseEntity<JsonNode> intoThePot = saver.tryToMove(own, potAccount, "50.00");
        ResponseEntity<JsonNode> outOfThePot = saver.tryToMove(potAccount, own, "50.00");

        assertThat(intoThePot.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(intoThePot)).isEqualTo("Savings account " + potAccount
                + " belongs to a shared pot, so money cannot be moved into it this way. A pot is "
                + "paid into by its members and its money leaves it by proposal.");
        assertThat(outOfThePot.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(outOfThePot)).isEqualTo("Savings account " + potAccount
                + " belongs to a shared pot, so money cannot be moved out of it this way. A pot is "
                + "paid into by its members and its money leaves it by proposal.");
        assertThat(saver.account(own).moneyBalance()).isEqualByComparingTo("200.00");
    }

    @Test
    void a_move_into_a_closed_savings_account_is_refused_in_the_words_a_deposit_into_one_is() {
        ASaverChoosingAProduct saver = new ASaverChoosingAProduct(http, "Wouter who tidied up");
        long open = saver.theAccountTheyWereOpenedWith();
        long shut = saver.open("NOTICE32").id();
        saver.payIn(open, "80.00");
        saver.close(shut);

        ResponseEntity<JsonNode> refusedMove = saver.tryToMove(open, shut, "80.00");
        ResponseEntity<JsonNode> refusedDeposit = saver.tryToPayIn(shut, "80.00");

        // One fact, one sentence, one status — the refusal a deposit already had rather than a
        // second one invented for the same closed account.
        assertThat(refusedMove.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refusedMove)).isEqualTo(reasonGivenBy(refusedDeposit));
        assertThat(reasonGivenBy(refusedMove)).contains("was closed on", "no more money can be paid "
                + "into it");
        assertThat(saver.account(open).moneyBalance()).isEqualByComparingTo("80.00");
    }

    @Test
    void a_move_of_money_a_goal_has_spoken_for_is_refused_as_a_withdrawal_of_it_would_be() {
        ASaverChoosingAProduct saver = new ASaverChoosingAProduct(http, "Ruben saving for a bike");
        long instant = saver.theAccountTheyWereOpenedWith();
        long fixed = saver.open("FIXED12").id();
        saver.payIn(instant, "500.00");
        long bike = saver.openAGoal(instant, "A bike", "400.00");
        saver.putTowards(instant, bike, "400.00");

        ResponseEntity<JsonNode> refusedMove = saver.tryToMove(instant, fixed, "300.00");
        ResponseEntity<JsonNode> refusedWithdrawal = saver.tryToTakeOut(instant, "300.00");

        assertThat(refusedMove.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Word for word what taking the same euros out would be refused with: a move is not a way
        // round a goal any more than it is a way round a notice period.
        assertThat(reasonGivenBy(refusedMove)).isEqualTo(reasonGivenBy(refusedWithdrawal));
        // And both accounts are left consistent: nothing was reallocated on the customer's behalf.
        AllocationsView left = saver.allocationsOn(instant);
        assertThat(left.balance()).isEqualByComparingTo("500.00");
        assertThat(left.allocated()).isEqualByComparingTo("400.00");
        assertThat(left.unallocated()).isEqualByComparingTo("100.00");
        assertThat(left.goal(bike).allocation()).isEqualByComparingTo("400.00");

        // What is spare moves, and the goal keeps exactly what it had — on both accounts.
        saver.move(instant, fixed, "100.00");

        AllocationsView after = saver.allocationsOn(instant);
        assertThat(after.balance()).isEqualByComparingTo("400.00");
        assertThat(after.allocated()).isEqualByComparingTo("400.00");
        assertThat(after.unallocated()).isEqualByComparingTo("0.00");
        assertThat(after.goal(bike).allocation()).isEqualByComparingTo("400.00");
        AllocationsView arrived = saver.allocationsOn(fixed);
        assertThat(arrived.balance()).isEqualByComparingTo("100.00");
        assertThat(arrived.allocated()).isEqualByComparingTo("0.00");
        assertThat(arrived.unallocated()).isEqualByComparingTo("100.00");
    }

    @Test
    void a_move_of_more_than_the_account_holds_and_a_move_to_itself_are_both_refused() {
        ASaverChoosingAProduct saver = new ASaverChoosingAProduct(http, "Els who mistyped");
        long instant = saver.theAccountTheyWereOpenedWith();
        long fixed = saver.open("FIXED12").id();
        saver.payIn(instant, "40.00");

        ResponseEntity<JsonNode> tooMuch = saver.tryToMove(instant, fixed, "41.00");
        ResponseEntity<JsonNode> toItself = saver.tryToMove(instant, instant, "10.00");
        ResponseEntity<JsonNode> nowhere = saver.tryToMove(instant, 9_999_999L, "10.00");

        assertThat(tooMuch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(tooMuch)).isEqualTo("There is not enough in that savings account "
                + "to move EUR 41.00. It holds EUR 40.00.");
        assertThat(toItself.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(toItself)).isEqualTo("Money can only be moved to a different "
                + "savings account. Savings account " + instant + " is the one it is already in.");
        assertThat(nowhere.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(nowhere)).isEqualTo("There is no savings account 9999999.");
        assertThat(saver.account(instant).moneyBalance()).isEqualByComparingTo("40.00");
    }

    /** A pot of this customer's, opened through the door a pot is actually opened through. */
    private SharedPotView aPotOpenedBy(long customerId) {
        ResponseEntity<SharedPotView> opened = http.postForEntity("/api/shared-pots",
                Map.of("name", "A weekend away", "customerId", customerId), SharedPotView.class);
        assertThat(opened.getStatusCode()).as("opening a shared pot").isEqualTo(HttpStatus.CREATED);
        return opened.getBody();
    }
}
