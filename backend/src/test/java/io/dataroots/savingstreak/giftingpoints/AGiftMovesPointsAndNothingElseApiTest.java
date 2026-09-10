package io.dataroots.savingstreak.giftingpoints;

import java.math.BigDecimal;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.MoneyMovementView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A gift moves points, and nothing else moves with them. No euros leave either account, no week is
 * secured at either end, neither streak nor multiplier changes, the ledger of money that moved has
 * nothing new in it, no deposit's breakdown grows, and the account overview gains no figure.
 *
 * <p>This is the negative space around the feature, and it is worth stating out loud because every
 * item on it is a thing a plausible implementation gets wrong by being helpful. Crediting a gift
 * against the recipient's most recent deposit, counting a received gift as this week's saving so
 * that generosity keeps a streak alive, writing a gift into the ledger so that "everything that
 * happened" is in one place, adding a "points received" total to the front page — each is a small
 * kindness, and each would make one of this application's existing sentences false. Nothing else in
 * the application would notice; these tests are what notices.
 *
 * <p>Both customers are asserted on every time, because these are two-sided promises and the two
 * sides fail differently: the sender is the one whose euros could be touched and whose deposits
 * could be rewritten downwards, and the recipient is the one whose week could be secured and whose
 * deposits could be credited with somebody else's points.
 *
 * <p>Its own application on a database nothing has ever been written to, for the reason
 * {@link AGiftMovesPointsFromOneCustomerToAnotherApiTest} gives: a gift needs two customers with
 * pots, and the run's shared database is where other tests assert that Bram has never earned a point
 * in his life. Every test here reads what it is asserting about before the gift and compares against
 * that, rather than against absolute figures, so no test depends on which of them ran first.
 *
 * <p>Anke is the sender throughout, because she is the customer these tests can earn points for.
 */
class AGiftMovesPointsAndNothingElseApiTest extends ApiIntegrationTest {

    /** A deposit comfortably over the weekly minimum, so that the week it lands in is secured. */
    private static final String A_DEPOSIT_THAT_SECURES_A_WEEK = "60.00";

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-gift-moves-nothing-else"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * The euros stay exactly where they were, at all four ends of them — both savings accounts and
     * both current accounts — and the ledger of money that moved has nothing new in it for either
     * customer.
     *
     * <p>Both current accounts are read because that is where money touched by a gift would have to
     * have come from or gone to: a savings balance that had not changed while a current account had
     * would be euros moving in a direction nobody asked for. And the ledger is compared entry by
     * entry rather than only counted, so a gift written into it as an amount of zero — the shape a
     * helpful implementation would take, "so that everything that happened is in one place" — is
     * caught as well as one written in for the points.
     */
    @Test
    void a_gift_moves_no_euros_and_appears_in_neither_ledger_of_money() {
        long ankesSavings = app.savingsAccountOf(ANKE);
        long bramsSavings = app.savingsAccountOf(BRAM);
        app.deposit(ankesSavings, ANKE, A_DEPOSIT_THAT_SECURES_A_WEEK);

        BalancesView ankeBefore = app.balancesOf(ankesSavings);
        BalancesView bramBefore = app.balancesOf(bramsSavings);
        BigDecimal ankesCurrentAccount = app.currentAccountBalanceOf(ANKE);
        BigDecimal bramsCurrentAccount = app.currentAccountBalanceOf(BRAM);
        MoneyMovementView[] ankesLedger = app.moneyMovementsOf(ANKE);
        MoneyMovementView[] bramsLedger = app.moneyMovementsOf(BRAM);

        app.give(ANKE, BRAM, "25");

        BalancesView ankeAfter = app.balancesOf(ankesSavings);
        BalancesView bramAfter = app.balancesOf(bramsSavings);

        // The points moved, which is what says the gift did anything at all. Without this the rest
        // of the test would pass just as well against an application that refused every gift.
        assertThat(ankeAfter.pointsBalance())
                .as("the sender's pot is lighter by exactly the gift")
                .isEqualTo(ankeBefore.pointsBalance() - 25);
        assertThat(bramAfter.pointsBalance())
                .as("and the recipient's is heavier by exactly the gift")
                .isEqualTo(bramBefore.pointsBalance() + 25);

        assertThat(ankeAfter.moneyBalance())
                .as("a gift is paid in points, so the sender's savings are untouched")
                .isEqualByComparingTo(ankeBefore.moneyBalance());
        assertThat(bramAfter.moneyBalance())
                .as("and so are the recipient's")
                .isEqualByComparingTo(bramBefore.moneyBalance());
        assertThat(app.currentAccountBalanceOf(ANKE))
                .as("nothing came out of the sender's current account to pay for it")
                .isEqualByComparingTo(ankesCurrentAccount);
        assertThat(app.currentAccountBalanceOf(BRAM))
                .as("and nothing arrived in the recipient's")
                .isEqualByComparingTo(bramsCurrentAccount);

        assertThat(app.moneyMovementsOf(ANKE))
                .as("the sender's ledger is a record of euros that moved, and none did")
                .containsExactly(ankesLedger);
        assertThat(app.moneyMovementsOf(BRAM))
                .as("and so is the recipient's")
                .containsExactly(bramsLedger);
    }

    /**
     * A gift secures no week and starts no run of weeks, at either end. The streak still measures
     * money the customer actually paid in.
     *
     * <p>Asserted in a week nothing was paid into, which is what makes it sharp: two weeks pass with
     * no deposit, so both customers reach the gift with an empty week and a lapsed run, and a gift
     * that counted as saving would have nowhere to hide. Both halves are checked before the gift is
     * made, because "still nothing" is only worth asserting where nothing was true to begin with.
     *
     * <p>The recipient matters at least as much as the sender here. Receiving points is the side
     * that looks like income, and a run of weeks kept alive by being given points would be a streak
     * that no longer means what the front page says it means.
     */
    @Test
    void a_gift_secures_no_week_and_changes_neither_streak() {
        long ankesSavings = app.savingsAccountOf(ANKE);
        long bramsSavings = app.savingsAccountOf(BRAM);
        app.deposit(ankesSavings, ANKE, A_DEPOSIT_THAT_SECURES_A_WEEK);
        // Two weeks in which neither of them paid anything in: the first ends any run either was on,
        // and the second is the empty week the gift is made in.
        app.aWeekPasses();
        app.aWeekPasses();

        BalancesView ankeBefore = app.balancesOf(ankesSavings);
        BalancesView bramBefore = app.balancesOf(bramsSavings);
        assertThat(ankeBefore.newSavingsThisWeek())
                .as("the sender starts the week having paid nothing in")
                .isEqualByComparingTo("0.00");
        assertThat(ankeBefore.currentStreakWeeks())
                .as("and a fortnight of paying nothing in has ended any run she was on")
                .isZero();
        assertThat(bramBefore.newSavingsThisWeek())
                .as("and so does the recipient")
                .isEqualByComparingTo("0.00");
        assertThat(bramBefore.currentStreakWeeks())
                .as("with no run of his own left either")
                .isZero();

        app.give(ANKE, BRAM, "10");

        BalancesView ankeAfter = app.balancesOf(ankesSavings);
        BalancesView bramAfter = app.balancesOf(bramsSavings);

        assertThat(ankeAfter.pointsBalance())
                .as("the gift went through, which is what makes the rest of this worth asserting")
                .isEqualTo(ankeBefore.pointsBalance() - 10);
        assertThat(bramAfter.pointsBalance()).isEqualTo(bramBefore.pointsBalance() + 10);

        theWeekAndTheRunAreExactlyWhatTheyWere("the sender", ankeBefore, ankeAfter);
        theWeekAndTheRunAreExactlyWhatTheyWere("the recipient", bramBefore, bramAfter);
    }

    /**
     * No deposit of either customer's says anything different afterwards: not its total, not the
     * three parts the total is made of, not the rate it was paid at, and not what its next
     * anniversary is worth.
     *
     * <p>The recipient is the obvious side — points credited against his last deposit would have
     * that deposit claiming to have earned points it had nothing to do with — but the sender is the
     * side more easily forgotten. What a deposit earned is what it earned at the time; giving those
     * points away afterwards spends them out of the pot and does not rewrite the history of how they
     * got there. A deposit whose total fell when its owner was generous would make a customer's own
     * history depend on what they did with the proceeds.
     *
     * <p>Every entry is compared whole rather than field by field, which is the assertion that keeps
     * meaning something when the deposit view grows a field: a gift that showed up anywhere in one of
     * these entries fails this test without anybody having to remember to look for it there.
     */
    @Test
    void a_gift_is_no_part_of_what_any_deposit_of_either_customers_earned() {
        long ankesSavings = app.savingsAccountOf(ANKE);
        long bramsSavings = app.savingsAccountOf(BRAM);
        app.deposit(ankesSavings, ANKE, A_DEPOSIT_THAT_SECURES_A_WEEK);
        app.deposit(bramsSavings, BRAM, "20.00");

        DepositView[] hers = app.depositsInto(ankesSavings);
        DepositView[] his = app.depositsInto(bramsSavings);
        long bramHeld = app.pointsBalanceOf(BRAM);

        app.give(ANKE, BRAM, "7");

        assertThat(app.pointsBalanceOf(BRAM))
                .as("the gift is in the recipient's balance")
                .isEqualTo(bramHeld + 7);
        assertThat(app.depositsInto(bramsSavings))
                .as("and in none of his deposits, which say exactly what they said before")
                .containsExactly(his);
        assertThat(app.depositsInto(ankesSavings))
                .as("and giving them away rewrote none of the sender's deposits either")
                .containsExactly(hers);

        // The invariant underneath all of it, stated once for every deposit either of them holds: a
        // total is its three parts and nothing else, so nothing was added to the breakdown and left
        // out of the total, or the other way round.
        for (DepositView entry : app.depositsInto(ankesSavings)) {
            theThreePartsAddUpTo(entry);
        }
        for (DepositView entry : app.depositsInto(bramsSavings)) {
            theThreePartsAddUpTo(entry);
        }
    }

    /**
     * The account overview gains nothing: no "points given away" total, no "points received" total,
     * no count of gifts. The balance already includes points somebody was given, and the gift list
     * is the record — this follows the loyalty bonus's precedent of showing a thing where it happened
     * rather than adding a figure to the front page.
     *
     * <p>Read as the text the API actually sends rather than through a view, for the reason
     * {@link AnApplicationWithAClockToMove#theAccountOverviewAsItIsSent} gives: a record binds the
     * fields it knows about and says nothing about the ones it does not, so a test asserting that
     * nothing was added cannot ask a record that would have to be changed first in order to notice.
     *
     * <p>Both overviews, because there are two: the customer's own, which is what the home screen
     * reads, and the savings account's, which repeats the same figures beside one account. A figure
     * added to either would be a figure on the front page.
     */
    @Test
    void nothing_about_a_gift_appears_on_either_customers_account_overview() {
        long ankesSavings = app.savingsAccountOf(ANKE);
        long bramsSavings = app.savingsAccountOf(BRAM);
        app.deposit(ankesSavings, ANKE, A_DEPOSIT_THAT_SECURES_A_WEEK);
        long ankeHeld = app.pointsBalanceOf(ANKE);
        long bramHeld = app.pointsBalanceOf(BRAM);

        app.give(ANKE, BRAM, "5");

        // The balance is the whole of what an overview says about a gift, at both ends.
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(ankeHeld - 5);
        assertThat(app.pointsBalanceOf(BRAM)).isEqualTo(bramHeld + 5);

        theOverviewMentionsNoGift("the sender's own overview",
                app.theCustomerOverviewAsItIsSent(ANKE));
        theOverviewMentionsNoGift("the recipient's own overview",
                app.theCustomerOverviewAsItIsSent(BRAM));
        theOverviewMentionsNoGift("the sender's savings account",
                app.theAccountOverviewAsItIsSent(ankesSavings));
        theOverviewMentionsNoGift("the recipient's savings account",
                app.theAccountOverviewAsItIsSent(bramsSavings));
    }

    /**
     * The four weekly figures, before against after. All four together, because a gift that had been
     * counted as saving could show up in any of them: in what has landed this week, in what is still
     * needed to secure it, in the run of weeks behind it, or in what that run pays per euro.
     */
    private static void theWeekAndTheRunAreExactlyWhatTheyWere(String whose, BalancesView before,
                                                               BalancesView after) {
        assertThat(after.newSavingsThisWeek())
                .as(whose + " paid nothing into this week, and a gift is not a payment in")
                .isEqualByComparingTo(before.newSavingsThisWeek());
        assertThat(after.stillNeededThisWeek())
                .as(whose + " is exactly as far off securing the week as before")
                .isEqualByComparingTo(before.stillNeededThisWeek());
        assertThat(after.currentStreakWeeks())
                .as(whose + " is on the same run of weeks as before, which is none")
                .isEqualTo(before.currentStreakWeeks());
        assertThat(after.bestStreakWeeks())
                .as(whose + " has the same best-ever run as before")
                .isEqualTo(before.bestStreakWeeks());
        assertThat(after.currentMultiplier())
                .as(whose + " earns at the same rate as before")
                .isEqualByComparingTo(before.currentMultiplier());
    }

    /**
     * An overview with no gift-shaped figure anywhere in it, read as the text it is sent as. Lowered
     * in case first, so that a field named {@code pointsGifted} is caught as surely as one named
     * {@code gifts}, and asked about the two words a total of this kind would have to be named with.
     */
    private static void theOverviewMentionsNoGift(String which, String asItIsSent) {
        assertThat(asItIsSent.toLowerCase())
                .as("no gift figure on " + which)
                .doesNotContain("gift")
                .doesNotContain("given")
                .doesNotContain("received");
    }

    /** A deposit's total against the three parts it is made of, which always sum to it. */
    private static void theThreePartsAddUpTo(DepositView entry) {
        assertThat(entry.basePoints() + entry.streakBonusPoints() + entry.loyaltyBonusPoints())
                .as("the three parts of deposit " + entry.id() + " add up to its total")
                .isEqualTo(entry.pointsEarned());
    }
}
