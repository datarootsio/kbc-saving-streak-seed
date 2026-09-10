package io.dataroots.savingstreak.giftingpoints;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.GiftView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * One gift, two readings of it. Everybody who was part of a gift can read it in their own list, and
 * the only thing that differs between the two readings is the word that says which end they were
 * on: the person who gave it reads {@code SENT} and the person who got it reads {@code RECEIVED},
 * and both rows name both people, the same figure, the same moment and the same gift.
 *
 * <p>Sent and received are one list rather than two, so a second gift going the other way lands in
 * the same list as the first — above it, because the list reads newest first. That is what makes
 * direction a property of who is reading rather than of the row: the pair of gifts here reads
 * {@code RECEIVED} over {@code SENT} for one customer and {@code SENT} over {@code RECEIVED} for the
 * other, out of the same two records, and a list that had stored a direction would have to agree
 * with only one of them.
 *
 * <p>Both lists are read before anything is given, because "no gifts" is an empty list and not a
 * refusal: a customer who has never given or received anything exists just as much as one who has,
 * and answering them a 404 would be this application telling them they are nobody.
 *
 * <p>The names are asserted as the names, not as identifiers, because a list of gifts should read
 * as people — "you gave Bram De Vos 12 points" is the row, and an identifier where a name should be
 * is the failure this catches. They are looked up against Accounts and nothing about them is stored
 * on a gift.
 *
 * <p>Its own application, on a database nothing has ever been written to: the first assertion here
 * is that both lists are empty, which is only true where no other test has ever given anything, and
 * it moves the clock between the two gifts so that "newest first" is an order and not a coin toss.
 */
class BothPartiesReadTheSameGiftFromTheirOwnEndApiTest extends ApiIntegrationTest {

    /** What she gives him out of what she earned, leaving her holding some of it. */
    private static final String THE_FIRST_GIFT = "12";

    /** What he gives back out of what she gave him, which is the second gift and the newer one. */
    private static final String THE_GIFT_BACK = "5";

    /**
     * A day between the two gifts, so the pair has an unarguable order. Two gifts in the same
     * millisecond are ordered by their identifiers, which is a rule of its own and not the one this
     * test is about.
     */
    private static final int DAYS_BETWEEN_THE_TWO_GIFTS = 1;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationNobodyHasEverGivenAnythingIn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-both-parties-read-the-gift"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void one_gift_reads_as_sent_to_the_one_who_gave_it_and_received_by_the_one_who_got_it() {
        long herId = app.customerIdOf(ANKE);
        long hisId = app.customerIdOf(BRAM);

        assertThat(app.giftsOf(ANKE))
                .as("a customer who has been part of no gifts has an empty list, not a refusal")
                .isEmpty();
        assertThat(app.giftsOf(BRAM)).isEmpty();

        DepositView hers = app.deposit(app.savingsAccountOf(ANKE), ANKE, "30.00");
        assertThat(hers.pointsEarned()).isEqualTo(30);

        Instant beforeSheGave = app.theClockReads();
        GiftView given = app.give(ANKE, BRAM, THE_FIRST_GIFT);
        Instant afterSheGave = app.theClockReads();

        GiftView asSheReadsIt = theOnlyGiftOf(ANKE);
        assertThat(asSheReadsIt.id())
                .as("the same gift she was just answered with, read back out of her list")
                .isEqualTo(given.id());
        assertThat(asSheReadsIt.direction()).isEqualTo("SENT");
        assertThat(asSheReadsIt.senderId()).isEqualTo(herId);
        assertThat(asSheReadsIt.senderName()).isEqualTo(ANKE);
        assertThat(asSheReadsIt.recipientId()).isEqualTo(hisId);
        assertThat(asSheReadsIt.recipientName())
                .as("the other person by name, so the row reads as a person and not as a number")
                .isEqualTo(BRAM);
        assertThat(asSheReadsIt.points()).isEqualTo(Long.parseLong(THE_FIRST_GIFT));
        assertThat(asSheReadsIt.givenAt())
                .as("the moment it happened, off the application's clock")
                // A gift keeps its moment to the millisecond, while the clock is read here at
                // the sub-millisecond precision it reports, so the lower end of the window has
                // to be brought down to the precision the gift is kept at: a gift made in the
                // same millisecond as that read otherwise sorts just below it.
                .isAfterOrEqualTo(beforeSheGave.truncatedTo(ChronoUnit.MILLIS))
                .isBeforeOrEqualTo(afterSheGave);

        GiftView asHeReadsIt = theOnlyGiftOf(BRAM);
        assertThat(asHeReadsIt.id())
                .as("one gift and one record of it, read from the other end")
                .isEqualTo(given.id());
        assertThat(asHeReadsIt.direction())
                .as("the same row is received by the person it went to")
                .isEqualTo("RECEIVED");
        assertThat(asHeReadsIt.senderId()).isEqualTo(herId);
        assertThat(asHeReadsIt.senderName())
                .as("the other person named on his side too, which is the one who gave it")
                .isEqualTo(ANKE);
        assertThat(asHeReadsIt.recipientId()).isEqualTo(hisId);
        assertThat(asHeReadsIt.recipientName()).isEqualTo(BRAM);
        assertThat(asHeReadsIt.points())
                .as("what it was worth, the same figure on both sides")
                .isEqualTo(Long.parseLong(THE_FIRST_GIFT));
        assertThat(asHeReadsIt.givenAt()).isEqualTo(asSheReadsIt.givenAt());

        // A day on, and a gift the other way. It goes into the same list as the first for both of
        // them, above it, and reads in the opposite direction to it from each end.
        app.daysPass(DAYS_BETWEEN_THE_TWO_GIFTS);
        GiftView backAgain = app.give(BRAM, ANKE, THE_GIFT_BACK);

        GiftView[] herList = app.giftsOf(ANKE);
        assertThat(herList).hasSize(2);
        assertThat(herList[0].id())
                .as("newest first: the gift he made a day later is at the top of her list")
                .isEqualTo(backAgain.id());
        assertThat(herList[0].direction()).isEqualTo("RECEIVED");
        assertThat(herList[0].points()).isEqualTo(Long.parseLong(THE_GIFT_BACK));
        assertThat(herList[0].senderName()).isEqualTo(BRAM);
        assertThat(herList[1].id()).isEqualTo(given.id());
        assertThat(herList[1].direction())
                .as("sent and received in one list, marked by which end she was on")
                .isEqualTo("SENT");
        assertThat(herList[0].givenAt()).isAfter(herList[1].givenAt());

        GiftView[] hisList = app.giftsOf(BRAM);
        assertThat(hisList).hasSize(2);
        assertThat(hisList[0].id()).isEqualTo(backAgain.id());
        assertThat(hisList[0].direction())
                .as("the very row that is received in her list is sent in his")
                .isEqualTo("SENT");
        assertThat(hisList[0].recipientName()).isEqualTo(ANKE);
        assertThat(hisList[1].id()).isEqualTo(given.id());
        assertThat(hisList[1].direction()).isEqualTo("RECEIVED");
        assertThat(hisList[0].givenAt()).isAfter(hisList[1].givenAt());
    }

    /**
     * The one gift the customer has been part of, insisted on as the only one: an assertion about
     * "the gift" that quietly read the first of several would be asserting about whichever one
     * sorted highest.
     */
    private GiftView theOnlyGiftOf(String customerName) {
        GiftView[] gifts = app.giftsOf(customerName);
        assertThat(gifts)
                .as("the one gift " + customerName + " has been part of")
                .hasSize(1);
        return gifts[0];
    }
}
