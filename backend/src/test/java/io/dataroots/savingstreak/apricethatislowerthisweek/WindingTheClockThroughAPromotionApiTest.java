package io.dataroots.savingstreak.apricethatislowerthisweek;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A sale that starts and stops on its own: one offer, one promotion window, and a clock wound
 * across both ends of it.
 *
 * <p><strong>This is the test the ticket is actually about.</strong> Everything in the class next
 * door fixes the clock and moves the dates, which proves the arithmetic; this one fixes the dates
 * and moves the clock, which proves the thing an administrator is being sold — "set a price and
 * two dates, and the price goes back up on its own". Nothing is edited between the readings. The
 * day moves and the card changes.
 *
 * <p><strong>Its own application, for the reason {@link AnApplicationWithAClockToMove}
 * gives.</strong> Winding a clock cannot be undone, and an application whose clock one test moved
 * is one every later test is asserting against a day it did not choose. It is also the only way
 * to spend a customer down to nothing, which the second test here does on purpose: a claim that
 * only goes through because the sale is on is the whole of "affordability uses the effective
 * price", and it cannot be demonstrated without leaving somebody poor afterwards.
 *
 * <p>The readings are in one test each rather than one per day, deliberately. "Full price before,
 * cheaper during, full price after" is one fact about one offer told in sequence, and three tests
 * would each need their own application and their own wind to say a third of it — and none of
 * them would catch a sale that started and then never ended.
 */
class WindingTheClockThroughAPromotionApiTest extends ApiIntegrationTest {

    /**
     * How far ahead the sale is put and how long it lasts. Both comfortably more than a day, so
     * that no single wind of the clock can land the test on the far side of a boundary it meant
     * to stop short of.
     */
    private static final int DAYS_UNTIL_THE_SALE_STARTS = 3;

    private static final int DAYS_THE_SALE_LASTS = 4;

    private static final int THE_ORDINARY_PRICE = 9;

    private static final int THE_SALE_PRICE = 4;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseDaysThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-lower-price"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void winding_the_clock_across_the_sale_lowers_the_price_and_then_puts_it_back() {
        LocalDate today = app.theDateTheClockReads();
        LocalDate startsOn = today.plusDays(DAYS_UNTIL_THE_SALE_STARTS);
        LocalDate endsOn = startsOn.plusDays(DAYS_THE_SALE_LASTS);
        app.anOfferOnSale(anOfferWithASale("WINTER_HAMPER", "Winter hamper", startsOn, endsOn));

        // Before the sale. One figure on the card, and it is the ordinary one.
        RewardForACustomerView beforeIt = app.theOfferAsReadBy(ANKE, "WINTER_HAMPER");
        assertThat(beforeIt.costInPoints()).isEqualTo(THE_ORDINARY_PRICE);
        assertThat(beforeIt.ordinaryCostInPoints())
                .as("nothing is struck through before a sale starts")
                .isNull();
        assertThat(beforeIt.discountClosesOn()).isNull();
        assertThat(beforeIt.claimable())
                .as("a sale is never a lock: a discount changes what an offer costs and never "
                        + "whether it can be had")
                .isTrue();

        // The day before it starts: still full price, and this is the fencepost worth standing on.
        app.daysPass(DAYS_UNTIL_THE_SALE_STARTS - 1L);
        assertThat(app.theOfferAsReadBy(ANKE, "WINTER_HAMPER").costInPoints())
                .as("a sale starting on the third is not on during the second")
                .isEqualTo(THE_ORDINARY_PRICE);

        // The day it starts. Nothing was edited; the day moved.
        app.daysPass(1);
        assertThat(app.theDateTheClockReads()).isEqualTo(startsOn);
        RewardForACustomerView theDayItStarts = app.theOfferAsReadBy(ANKE, "WINTER_HAMPER");
        assertThat(theDayItStarts.costInPoints()).isEqualTo(THE_SALE_PRICE);
        assertThat(theDayItStarts.ordinaryCostInPoints())
                .as("and now there are two figures, so the card can show the saving without "
                        + "working it out")
                .isEqualTo(THE_ORDINARY_PRICE);
        assertThat(theDayItStarts.discountClosesOn()).isEqualTo(endsOn);

        // The last day of it, which is a day it is still cheaper on.
        app.daysPass(DAYS_THE_SALE_LASTS);
        assertThat(app.theDateTheClockReads()).isEqualTo(endsOn);
        assertThat(app.theOfferAsReadBy(ANKE, "WINTER_HAMPER").costInPoints())
                .as("the last day of a sale is inclusive, because that is what everybody reading "
                        + "\"half price until the eighth\" assumes")
                .isEqualTo(THE_SALE_PRICE);

        // And the morning after. Back to one figure, at the price it always was.
        app.daysPass(1);
        RewardForACustomerView theMorningAfter = app.theOfferAsReadBy(ANKE, "WINTER_HAMPER");
        assertThat(theMorningAfter.costInPoints()).isEqualTo(THE_ORDINARY_PRICE);
        assertThat(theMorningAfter.ordinaryCostInPoints())
                .as("nothing left to strike through, because there is no longer a saving")
                .isNull();
        assertThat(theMorningAfter.discountClosesOn()).isNull();
        assertThat(theMorningAfter.claimable()).isTrue();
    }

    /**
     * A claim that only goes through because the sale is on, and a claim that still reads back at
     * the price it paid once the sale is over.
     *
     * <p><strong>Two promises in one sequence, because the second is only interesting after the
     * first.</strong> The customer is left with less than the ordinary price on purpose: it is
     * the only arrangement in which "affordability uses the effective price" can fail visibly,
     * because a customer who could afford both figures would claim either way. And then the clock
     * is wound past the end of the sale and the claim is read again — what somebody paid is a
     * fact about the day they paid it, and a promotion ending must never reprice their history.
     *
     * <p>The prices are positioned against the balance rather than written down, because the
     * seeded balance is the application's business and a test that hard-coded a figure either
     * side of it would be asserting against the seed.
     */
    @Test
    void a_claim_made_during_the_sale_goes_through_and_keeps_its_price_afterwards() {
        app.deposit(app.savingsAccountOf(ANKE), ANKE, "20.00");
        long whatTheyHave = app.pointsBalanceOf(ANKE);
        long ordinaryPrice = whatTheyHave + 50;
        long salePrice = whatTheyHave - 5;
        LocalDate today = app.theDateTheClockReads();
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", "SPRING_HAMPER");
        offer.put("title", "Spring hamper");
        offer.put("description", "A box of things for the spring.");
        offer.put("costInPoints", ordinaryPrice);
        offer.put("voucherPrefix", "SPR");
        offer.put("discountedCostInPoints", salePrice);
        offer.put("discountOpensOn", today.toString());
        offer.put("discountClosesOn", today.plusDays(1).toString());
        app.anOfferOnSale(offer);

        RewardForACustomerView whileItIsOn = app.theOfferAsReadBy(ANKE, "SPRING_HAMPER");
        assertThat(whileItIsOn.costInPoints())
                .as("the card quotes the figure they would actually be charged")
                .isEqualTo(salePrice);
        assertThat(whileItIsOn.ordinaryCostInPoints()).isEqualTo(ordinaryPrice);

        ClaimedRewardView claimed = app.claim(ANKE, "SPRING_HAMPER");

        assertThat(claimed.pointsSpent())
                .as("a claim they could not have afforded at the ordinary price went through at "
                        + "the sale price, and took the sale price")
                .isEqualTo(salePrice);
        assertThat(app.pointsBalanceOf(ANKE)).isEqualTo(whatTheyHave - salePrice);

        // Two days on, which is one day past the end of the sale.
        app.daysPass(2);

        RewardForACustomerView afterwards = app.theOfferAsReadBy(ANKE, "SPRING_HAMPER");
        assertThat(afterwards.costInPoints())
                .as("the price went back up on its own, with nobody editing anything")
                .isEqualTo(ordinaryPrice);
        assertThat(afterwards.ordinaryCostInPoints()).isNull();
        assertThat(app.claimsOf(ANKE))
                .filteredOn(claim -> "SPRING_HAMPER".equals(claim.code()))
                .singleElement()
                .extracting(ClaimedRewardView::pointsSpent)
                .as("and what they paid is what their history says they paid, because a claim "
                        + "writes the price down rather than reading it back off the catalogue")
                .isEqualTo(salePrice);

        ResponseEntity<JsonNode> tooDearNow = app.tryToClaim(ANKE, "SPRING_HAMPER");
        assertThat(tooDearNow.getStatusCode())
                .as("being short of points is answered the way it has always been answered; what "
                        + "changed with the sale ending is the figure in the sentence")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(tooDearNow))
                .as("and the refusal quotes the price that applies today, which is the ordinary "
                        + "one again")
                .contains(String.valueOf(ordinaryPrice));
    }

    /** An offer as an administration form would send one, with a sale written into it. */
    private static Map<String, Object> anOfferWithASale(String code, String title,
                                                        LocalDate startsOn, LocalDate endsOn) {
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", title);
        offer.put("description", "A box of things for the winter.");
        offer.put("costInPoints", THE_ORDINARY_PRICE);
        offer.put("voucherPrefix", "WIN");
        offer.put("discountedCostInPoints", THE_SALE_PRICE);
        offer.put("discountOpensOn", startsOn.toString());
        offer.put("discountClosesOn", endsOn.toString());
        return offer;
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private static String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }
}
