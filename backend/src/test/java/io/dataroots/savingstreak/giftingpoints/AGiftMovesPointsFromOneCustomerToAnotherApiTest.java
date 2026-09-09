package io.dataroots.savingstreak.giftingpoints;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.GiftView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer gives points to another customer of the bank, and the points arrive: the sender's
 * balance falls by exactly what they gave, the recipient's rises by exactly the same, and nothing is
 * created, skimmed or destroyed on the way.
 *
 * <p>Conservation is the invariant worth asserting hardest here, and it is asserted on both pots
 * every time. A gift that credited a little more than it took, or took a little more than it
 * credited, would be a private currency quietly inflating or leaking — and neither would show up
 * anywhere a customer looks until the totals stopped adding up.
 *
 * <p>Its own application, on a database nothing has ever been written to, for a reason the shared
 * one cannot give: a gift needs two customers with pots, and the run's shared database is where two
 * other test classes assert that Bram has never earned a point in his life. Putting points in his
 * pot there would break them, so this feature is asked about somewhere it is the only thing that has
 * happened.
 *
 * <p>Anke is the sender throughout, because she is the customer these tests can earn points for; the
 * one test that gives in the other direction says so.
 */
class AGiftMovesPointsFromOneCustomerToAnotherApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-gift-moves-points"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * The whole point of the slice: points leave one pot and arrive in the other, and the gift that
     * comes back says what happened in the words a page would show.
     *
     * <p>The two balances are asserted against what they were rather than against figures this test
     * knows absolutely, because both customers may have been part of an earlier gift in this class —
     * and what a gift changed is the only thing a test about a gift can honestly claim.
     */
    @Test
    void a_gift_moves_points_out_of_one_pot_and_into_the_other() {
        long ankesSavings = app.savingsAccountOf(ANKE);
        app.deposit(ankesSavings, ANKE, "60.00");
        long ankeHeld = app.pointsBalanceOf(ANKE);
        long bramHeld = app.pointsBalanceOf(BRAM);

        GiftView gift = app.give(ANKE, BRAM, "25");

        assertThat(gift.id()).isNotNull();
        // Sent, because a gift just made is being reported to the person who made it. The same gift
        // reads as received in the other customer's list.
        assertThat(gift.direction()).isEqualTo("SENT");
        assertThat(gift.senderId()).isEqualTo(app.customerIdOf(ANKE));
        assertThat(gift.senderName()).isEqualTo(ANKE);
        assertThat(gift.recipientId()).isEqualTo(app.customerIdOf(BRAM));
        assertThat(gift.recipientName()).isEqualTo(BRAM);
        assertThat(gift.points()).isEqualTo(25);
        // Off the application's clock rather than the machine's, so a gift made against a clock a
        // trainer wound forward is dated where they wound it to.
        assertThat(gift.givenAt())
                .as("the moment comes off the clock the application judges everything else by")
                .isNotNull()
                .isBeforeOrEqualTo(app.theClockReads());

        assertThat(app.pointsBalanceOf(ANKE))
                .as("the sender's balance falls by exactly what they gave")
                .isEqualTo(ankeHeld - 25);
        assertThat(app.pointsBalanceOf(BRAM))
                .as("and the recipient's rises by exactly the same")
                .isEqualTo(bramHeld + 25);
        // Nothing created, skimmed or destroyed: there is no fee, no tax and no spread on a gift.
        assertThat(app.pointsBalanceOf(ANKE) + app.pointsBalanceOf(BRAM))
                .as("the two pots together hold what they held before")
                .isEqualTo(ankeHeld + bramHeld);
    }

    /**
     * The recipient is found the way signing in finds somebody: trimmed, and matched without regard
     * to case. A customer types the address the other person banks under, and it should not matter
     * that they capitalised it or that the field they typed it into kept a space.
     *
     * <p>Given in the other direction, because Bram is the one holding points somebody gave him by
     * the time this runs — and a gift onward from a pot that was filled by a gift is a gift like any
     * other.
     */
    @Test
    void the_recipient_is_found_by_their_address_however_it_was_typed() {
        app.deposit(app.savingsAccountOf(BRAM), BRAM, "12.00");
        long bramHeld = app.pointsBalanceOf(BRAM);
        long ankeHeld = app.pointsBalanceOf(ANKE);
        String shoutedWithSpacesAround = "  " + app.contactDetailsOf(ANKE).toUpperCase() + "  ";

        ResponseEntity<GiftView> given = app.giveNaming(BRAM, shoutedWithSpacesAround, "4");

        assertThat(given.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(given.getBody().recipientName()).isEqualTo(ANKE);
        assertThat(app.pointsBalanceOf(BRAM)).isEqualTo(bramHeld - 4);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(ankeHeld + 4);
    }

    /**
     * Gifted points are credited under a reason of their own, and it is not one of the reasons a
     * deposit can have earned under — so a deposit's breakdown stays a statement about that deposit.
     *
     * <p>Asserted from outside as the only place the difference is visible: the recipient's balance
     * rises, and the deposit they happen to have made says exactly what it said before, total and
     * breakdown alike. A ledger that had credited a gift against a deposit would have that deposit
     * claiming to have earned points it had nothing to do with.
     */
    @Test
    void a_gift_is_not_part_of_what_any_deposit_earned() {
        app.deposit(app.savingsAccountOf(ANKE), ANKE, "30.00");
        long bramsSavings = app.savingsAccountOf(BRAM);
        DepositView his = app.deposit(bramsSavings, BRAM, "20.00");
        long bramHeld = app.pointsBalanceOf(BRAM);

        app.give(ANKE, BRAM, "7");

        assertThat(app.pointsBalanceOf(BRAM))
                .as("the gift is in his balance")
                .isEqualTo(bramHeld + 7);
        assertThat(app.depositsInto(bramsSavings))
                .as("and in none of his deposits")
                .anySatisfy(listed -> {
                    assertThat(listed.id()).isEqualTo(his.id());
                    assertThat(listed.pointsEarned()).isEqualTo(his.pointsEarned());
                    assertThat(listed.basePoints()).isEqualTo(his.basePoints());
                    assertThat(listed.streakBonusPoints()).isEqualTo(his.streakBonusPoints());
                    assertThat(listed.loyaltyBonusPoints()).isEqualTo(his.loyaltyBonusPoints());
                    // The three parts still add up to the total, which is what says nothing was
                    // added to the breakdown and left out of it, or the other way round.
                    assertThat(listed.basePoints() + listed.streakBonusPoints()
                            + listed.loyaltyBonusPoints()).isEqualTo(listed.pointsEarned());
                });
    }
}
