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

    /**
     * How long a run has to be before "unchanged" is a claim with two sides. The ladder pays the
     * ordinary rate for a run of one week and climbs from the second, so three weeks puts both the
     * count and the rate visibly off the floor, with somewhere to fall to.
     */
    private static final int WEEKS_OF_A_RUN_WORTH_LOSING = 3;

    /** What a euro earns with no run behind it, which is also the floor a broken run falls back to. */
    private static final BigDecimal THE_ORDINARY_RATE = new BigDecimal("1.00");

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
     * The euros stay exactly where they were, at every end of them — every savings account either
     * customer holds and both current accounts — and the ledger of money that moved has nothing new
     * in it for either customer.
     *
     * <p>Both current accounts are read because that is where money touched by a gift would have to
     * have come from or gone to: a savings balance that had not changed while a current account had
     * would be euros moving in a direction nobody asked for. The sender's second savings account is
     * read for the plainer reason that she has one: "her savings are untouched" is a sentence about
     * all of them, and euros taken out of the account nobody was looking at would be euros taken
     * out. Every one of the three is given money to lose first, because a balance of nothing can
     * only fall by going negative.
     *
     * <p>The ledger is asserted twice over, and the two catch different things. Compared entry by
     * entry rather than only counted, so a gift written into it as an amount of zero — the shape a
     * helpful implementation would take, "so that everything that happened is in one place" — is
     * caught as well as one written in for the points. And read as the text it is sent as, because
     * {@link MoneyMovementView} is filled in by Jackson and drops properties it has no component
     * for: a {@code giftedPoints} the ledger's entries had grown would never reach the records being
     * compared, and is only visible in the body.
     */
    @Test
    void a_gift_moves_no_euros_and_appears_in_neither_ledger_of_money() {
        long ankesSavings = app.savingsAccountOf(ANKE);
        long ankesOtherSavings = app.otherSavingsAccountOf(ANKE);
        long bramsSavings = app.savingsAccountOf(BRAM);
        // Money in all three, and a movement in both ledgers, so that every "unchanged" below is a
        // statement about a figure with somewhere to go and a list with something in it.
        app.deposit(ankesSavings, ANKE, A_DEPOSIT_THAT_SECURES_A_WEEK);
        app.deposit(ankesOtherSavings, ANKE, "20.00");
        app.deposit(bramsSavings, BRAM, "20.00");

        BalancesView ankeBefore = app.balancesOf(ankesSavings);
        BalancesView ankesOtherBefore = app.balancesOf(ankesOtherSavings);
        BalancesView bramBefore = app.balancesOf(bramsSavings);
        BigDecimal ankesCurrentAccount = app.currentAccountBalanceOf(ANKE);
        BigDecimal bramsCurrentAccount = app.currentAccountBalanceOf(BRAM);
        MoneyMovementView[] ankesLedger = app.moneyMovementsOf(ANKE);
        MoneyMovementView[] bramsLedger = app.moneyMovementsOf(BRAM);
        thereIsMoneyToLoseIn("the sender's first savings account", ankeBefore);
        thereIsMoneyToLoseIn("the sender's other savings account", ankesOtherBefore);
        thereIsMoneyToLoseIn("the recipient's savings account", bramBefore);

        app.give(ANKE, BRAM, "25");

        BalancesView ankeAfter = app.balancesOf(ankesSavings);
        BalancesView ankesOtherAfter = app.balancesOf(ankesOtherSavings);
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
        assertThat(ankesOtherAfter.moneyBalance())
                .as("including the savings account of hers nobody was looking at")
                .isEqualByComparingTo(ankesOtherBefore.moneyBalance());
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

        // And the same question of the bodies themselves, which is the half the comparison above
        // cannot answer: a field added to an entry never reaches the view it is compared through, so
        // a gift figure inside the ledger is only visible in the text.
        theLedgerNamesNoGift("the sender's ledger of money that moved",
                app.theMoneyMovementLedgerAsItIsSent(ANKE));
        theLedgerNamesNoGift("the recipient's ledger of money that moved",
                app.theMoneyMovementLedgerAsItIsSent(BRAM));
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
     *
     * <p>Then the week the gift was made in is left behind and each of them makes a deposit in the
     * next one, which asks the same question of the number that is <em>applied</em> rather than the
     * number that is reported. A deposit is priced by the run its own week is the end of, so a
     * first deposit after an empty week is paid at the ordinary rate — unless the gift secured that
     * empty week, in which case the run is two weeks long and the deposit is paid a step above.
     * Everything above this reads the figures off the overview, and a gift that had secured a week
     * only where euros are priced would not be in any of them.
     *
     * <p>This is half of the promise — the half about a gift <em>adding</em> to a week or a run.
     * The half about a gift taking one away is
     * {@link #a_gift_costs_neither_customer_the_run_of_weeks_they_are_on}, which cannot be asserted
     * from down here: at zero weeks and the ordinary rate, "unchanged" has only one direction.
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

        // And the same question where the rate is spent rather than reported. The week the gift was
        // made in ends unsecured, so the deposit each of them makes in the next one is the first
        // week of a run and is paid at the ordinary rate. A gift that had secured the week just
        // gone would make it the second, and this is the only assertion in the class that would
        // notice.
        app.aWeekPasses();
        assertThat(app.deposit(ankesSavings, ANKE, A_DEPOSIT_THAT_SECURES_A_WEEK).multiplierApplied())
                .as("the sender's next deposit is priced as the first week of a run, because the "
                        + "week she made the gift in secured nothing")
                .isEqualByComparingTo(THE_ORDINARY_RATE);
        assertThat(app.deposit(bramsSavings, BRAM, A_DEPOSIT_THAT_SECURES_A_WEEK).multiplierApplied())
                .as("and so is the recipient's, because being given points secured no week of his "
                        + "either")
                .isEqualByComparingTo(THE_ORDINARY_RATE);
    }

    /**
     * The same promise from the other side: a gift takes nothing away from a run either. Both
     * customers arrive at the gift on a live run of weeks paying above the ordinary rate, and both
     * leave it on the same run at the same rate.
     *
     * <p>Both directions are needed and neither implies the other, which is the whole reason this
     * test exists beside {@link #a_gift_secures_no_week_and_changes_neither_streak}. That one makes
     * the gift with both figures at the floor — nothing paid in, no run, the ordinary rate — where
     * "unchanged" can only catch a run being created or a week being secured, because there is
     * nowhere below zero to fall to. Losing a live streak because a friend sent you points is the
     * more damaging half of the promise and is only assertable from up here.
     *
     * <p>Three consecutive secured weeks each rather than the two a rate above the ordinary needs,
     * so that the run and the rate are both plainly mid-ladder and something that clipped either
     * has room to show. Both are checked to be up there before the gift is made, because "still
     * three weeks at 1.20×" is only worth asserting where that was true to begin with.
     *
     * <p>And the rate is asked for twice, because this application has two of them and only one is
     * on the screen. {@code currentMultiplier} on the overview is what a customer is <em>told</em>
     * they earn at; what a euro is actually <em>paid</em> at is the rate the Deposits module works
     * out for itself when it prices a deposit. The two come from the same derivation and are meant
     * never to disagree, which is exactly why a gift that moved one of them and not the other would
     * be invisible: the front page would go on promising 1,20× while the euros were paid at 1,00×,
     * and the customer's only evidence would be arithmetic they did by hand. So each of them makes
     * one more deposit <em>after</em> the gift — after, or it says nothing — and the rate that
     * deposit reports being paid at is held to the rate they were promised before it.
     *
     * <p>On an application of its very own, which is the one thing this test cannot borrow from the
     * class. Every other test here starts from wherever the last one left off, and that is fine for
     * them because each compares against its own before-state. This one has to build a live run
     * <em>first</em>, and a gift made by an earlier test would already have taken the run away
     * before the building started if giving points cost a run at all. The failure would then land
     * on the setup rather than on the promise, and the test would be reporting the wrong thing
     * about the right bug. A database where no gift has ever been made is the only place the run
     * this test builds is certainly there when its own gift is made.
     */
    @Test
    void a_gift_costs_neither_customer_the_run_of_weeks_they_are_on() {
        try (AnApplicationWithAClockToMove untouched = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-gift-and-a-live-run"))) {
            long ankesSavings = untouched.savingsAccountOf(ANKE);
            long bramsSavings = untouched.savingsAccountOf(BRAM);
            for (int week = 1; week <= WEEKS_OF_A_RUN_WORTH_LOSING; week++) {
                if (week > 1) {
                    untouched.aWeekPasses();
                }
                untouched.deposit(ankesSavings, ANKE, A_DEPOSIT_THAT_SECURES_A_WEEK);
                untouched.deposit(bramsSavings, BRAM, A_DEPOSIT_THAT_SECURES_A_WEEK);
            }

            BalancesView ankeBefore = untouched.balancesOf(ankesSavings);
            BalancesView bramBefore = untouched.balancesOf(bramsSavings);
            theRunIsLiveAndPayingAboveTheOrdinaryRate("the sender", ankeBefore);
            theRunIsLiveAndPayingAboveTheOrdinaryRate("the recipient", bramBefore);

            untouched.give(ANKE, BRAM, "10");

            BalancesView ankeAfter = untouched.balancesOf(ankesSavings);
            BalancesView bramAfter = untouched.balancesOf(bramsSavings);

            assertThat(ankeAfter.pointsBalance())
                    .as("the gift went through, which is what makes the rest of this worth asserting")
                    .isEqualTo(ankeBefore.pointsBalance() - 10);
            assertThat(bramAfter.pointsBalance()).isEqualTo(bramBefore.pointsBalance() + 10);

            theWeekAndTheRunAreExactlyWhatTheyWere("the sender", ankeBefore, ankeAfter);
            theWeekAndTheRunAreExactlyWhatTheyWere("the recipient", bramBefore, bramAfter);

            // The rate a euro is paid at, which is the other multiplier and the one that costs
            // money. Both deposits land in the week the run already secured, so neither the run nor
            // the rate has any business changing — and a gift that had quietly repriced either
            // customer shows up here and nowhere else.
            theRateTheyWerePromisedIsTheRateTheyArePaid("the sender", ankeBefore,
                    untouched.deposit(ankesSavings, ANKE, A_DEPOSIT_THAT_SECURES_A_WEEK));
            theRateTheyWerePromisedIsTheRateTheyArePaid("the recipient", bramBefore,
                    untouched.deposit(bramsSavings, BRAM, A_DEPOSIT_THAT_SECURES_A_WEEK));
        }
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
     * <p>Asserted twice over, because the two assertions fail at different things and neither one
     * covers the other. Every entry is compared whole rather than field by field, which catches any
     * figure the history already reports moving — a gift credited into a deposit's total, its base,
     * its bonus or its rate. That comparison is blind to a field being <em>added</em>, though:
     * {@link DepositView} is filled in by Jackson, which drops properties it has no component for,
     * so a {@code giftedPoints} the API started sending would never reach the record being compared.
     * So the history is also read as the text it is sent as and asked whether a gift is named
     * anywhere in it, which is what catches the breakdown growing a field nobody here anticipated.
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

        // And the same question of the bodies themselves, which is the half the comparison above
        // cannot answer: a field added to a deposit's breakdown never reaches the view it is
        // compared through, so a gift figure inside an entry is only visible in the text.
        theHistoryNamesNoGift("the recipient's deposit history",
                app.theDepositHistoryAsItIsSent(bramsSavings));
        theHistoryNamesNoGift("the sender's deposit history",
                app.theDepositHistoryAsItIsSent(ankesSavings));

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
                .as(whose + " is on the same run of weeks as before, however long that was")
                .isEqualTo(before.currentStreakWeeks());
        assertThat(after.bestStreakWeeks())
                .as(whose + " has the same best-ever run as before")
                .isEqualTo(before.bestStreakWeeks());
        assertThat(after.currentMultiplier())
                .as(whose + " earns at the same rate as before")
                .isEqualByComparingTo(before.currentMultiplier());
    }

    /**
     * The rate the overview promised before the gift, against the rate a deposit made after it says
     * it was actually paid at. The one figure in this class that comes from the pricing rather than
     * from the reporting, and the only way in from outside: a deposit is the moment the applied rate
     * becomes visible, and it has to be made after the gift for its answer to be about the gift.
     */
    private static void theRateTheyWerePromisedIsTheRateTheyArePaid(String whose,
                                                                    BalancesView before,
                                                                    DepositView paid) {
        assertThat(paid.multiplierApplied())
                .as(whose + " is paid at the rate " + whose + " was promised before the gift, "
                        + "because the rate on the overview and the rate a euro earns are the same "
                        + "rate")
                .isEqualByComparingTo(before.currentMultiplier());
    }

    /**
     * An account with euros in it. The precondition of "the euros are where they were": a balance of
     * nothing is unchanged by anything that does not push it below zero, so an account with nothing
     * in it cannot say whether a gift took money out.
     */
    private static void thereIsMoneyToLoseIn(String which, BalancesView now) {
        assertThat(now.moneyBalance())
                .as(which + " holds money before the gift, so that a gift taking euros out of it "
                        + "would have somewhere to show")
                .isGreaterThan(BigDecimal.ZERO);
    }

    /**
     * A run that is actually running, and a rate that is actually above the floor. The precondition
     * of the test above: without it, "the run is what it was" would be a sentence about zero.
     */
    private static void theRunIsLiveAndPayingAboveTheOrdinaryRate(String whose, BalancesView now) {
        assertThat(now.currentStreakWeeks())
                .as(whose + " reaches the gift on a run of weeks there is something to lose")
                .isGreaterThanOrEqualTo(WEEKS_OF_A_RUN_WORTH_LOSING);
        assertThat(now.currentMultiplier())
                .as(whose + " reaches it earning above the ordinary rate, so a rate that fell back "
                        + "to the floor would show")
                .isGreaterThan(THE_ORDINARY_RATE);
    }

    /**
     * An overview with no gift-shaped figure anywhere in it, read as the text it is sent as. Lowered
     * in case first, so that a field named {@code pointsGifted} is caught as surely as one named
     * {@code gifts}, and asked about the two words a total of this kind would have to be named with.
     */
    private static void theOverviewMentionsNoGift(String which, String asItIsSent) {
        nothingGiftShapedIn("no gift figure on " + which, "pointsBalance", asItIsSent);
    }

    /**
     * A deposit history with no gift-shaped figure in any of its entries, read the same way and for
     * the sharper version of the same reason: a breakdown that had grown a {@code giftedPoints}
     * would be a gift figure inside a statement about one deposit, which is exactly what this
     * ticket forbids, and no comparison made through a view can see a field the view has not got.
     */
    private static void theHistoryNamesNoGift(String which, String asItIsSent) {
        nothingGiftShapedIn("no gift figure anywhere in " + which, "pointsEarned", asItIsSent);
    }

    /**
     * A ledger of money that moved with no gift-shaped figure in any of its entries, read the same
     * way and for the same reason one more time. This is the read the ticket's third promise is
     * actually about: the ledger stays a record of euros, and a {@code giftedPoints} on its entries
     * would be a gift figure inside it that no comparison of views could see.
     */
    private static void theLedgerNamesNoGift(String which, String asItIsSent) {
        nothingGiftShapedIn("no gift figure anywhere in " + which, "direction", asItIsSent);
    }

    /**
     * The words a gift figure would have to be named with, asked of a body once — and first, the
     * word that says the body is the thing it was asked for.
     *
     * <p>That first assertion is what keeps the other three from being free. "This text does not
     * mention a gift" is true of an empty list, of a page of nothing and of a document about
     * something else entirely, so a read that had quietly stopped answering with a deposit history
     * or a ledger of entries would satisfy every question below it. The named field is one every
     * entry of the body carries, so it says both that the read succeeded and that there was
     * something in it to look through.
     */
    private static void nothingGiftShapedIn(String because, String aFieldTheBodyMustCarry,
                                            String asItIsSent) {
        assertThat(asItIsSent)
                .as(because + " — asked of a body that is the thing it was asked for, with at "
                        + "least one entry in it to look through")
                .contains(aFieldTheBodyMustCarry);
        assertThat(asItIsSent.toLowerCase())
                .as(because)
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
