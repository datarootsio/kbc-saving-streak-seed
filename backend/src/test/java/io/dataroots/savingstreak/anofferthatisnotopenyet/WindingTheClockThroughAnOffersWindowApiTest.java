package io.dataroots.savingstreak.anofferthatisnotopenyet;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A season that runs itself: one offer, one window, and a clock wound across both ends of it.
 *
 * <p><strong>This is the test the ticket is actually about.</strong> Every other assertion in this
 * package fixes the clock and moves the dates, which proves the arithmetic; this one fixes the
 * dates and moves the clock, which proves the thing an administrator is being sold — "give it a
 * day it opens and a day it closes, and the scheme runs the season without you". Three readings of
 * the same offer, on three days, with nothing touched in between but the day.
 *
 * <p><strong>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.</strong>
 * Winding a clock cannot be undone, and an application whose clock one test moved is an
 * application every later test is asserting against a day it did not choose. It is also the only
 * way to have an offer whose window is positioned relative to a clock nobody else is using — the
 * shape the points-expiry tests already have, and the shape a trainer's afternoon has.
 *
 * <p>The three readings are in one test rather than three, on purpose. "Locked before, open
 * during, locked after" is one fact about one offer told in sequence, and three tests would each
 * need their own application and their own wind to say a third of it — and none of them would
 * catch a window that opened and then never closed.
 */
class WindingTheClockThroughAnOffersWindowApiTest extends ApiIntegrationTest {

    /**
     * How far ahead the season is put, and how long it lasts. Both comfortably more than a day, so
     * that no single wind of the clock can land the test on the far side of a boundary it meant to
     * stop short of — and so that the "still closed the day before" reading has a day to be on.
     */
    private static final int DAYS_UNTIL_IT_OPENS = 3;

    private static final int DAYS_IT_STAYS_OPEN = 4;

    /** Cheap, and earned inside the test, because what this test spends is not the point of it. */
    private static final int WHAT_THE_HAMPER_COSTS = 5;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseDaysThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-offers-window"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void winding_the_clock_across_the_window_opens_the_offer_and_then_closes_it_again() {
        LocalDate today = app.theDateTheClockReads();
        LocalDate opensOn = today.plusDays(DAYS_UNTIL_IT_OPENS);
        LocalDate closesOn = opensOn.plusDays(DAYS_IT_STAYS_OPEN);
        app.anOfferOnSale(anOffer("WINTER_HAMPER", "Winter hamper", opensOn, closesOn));
        // Earned before anything else, so that no reading below is confused by a customer who
        // simply could not afford the thing: this test is about the window and nothing else.
        long pointsBefore = app.pointsBalanceOf(ANKE);
        app.deposit(app.savingsAccountOf(ANKE), ANKE, WHAT_THE_HAMPER_COSTS + ".00");

        // Before the season. On the page, greyed, saying the day — and refused if pressed.
        RewardForACustomerView beforeItOpens = app.theOfferAsReadBy(ANKE, "WINTER_HAMPER");
        assertThat(beforeItOpens.claimable()).isFalse();
        assertThat(beforeItOpens.lockedBecause()).isEqualTo("NOT_OPEN_YET");
        assertThat(beforeItOpens.whyItIsLocked()).contains(opensOn.toString());
        assertThat(beforeItOpens.opensOn()).isEqualTo(opensOn);
        assertThat(beforeItOpens.closesOn()).isEqualTo(closesOn);
        ResponseEntity<JsonNode> tooEarly = app.tryToClaim(ANKE, "WINTER_HAMPER");
        assertThat(tooEarly.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(tooEarly))
                .as("the card and the refusal are the same sentence, because they are the same "
                        + "fact said at two moments")
                .isEqualTo(beforeItOpens.whyItIsLocked());

        // The day before it opens: still shut, and this is the fencepost worth standing on.
        app.daysPass(DAYS_UNTIL_IT_OPENS - 1L);
        assertThat(app.theOfferAsReadBy(ANKE, "WINTER_HAMPER").claimable())
                .as("a season opening on the third is shut all through the second")
                .isFalse();

        // The day it opens. Nothing was edited; the day moved.
        app.daysPass(1);
        assertThat(app.theDateTheClockReads()).isEqualTo(opensOn);
        RewardForACustomerView theDayItOpens = app.theOfferAsReadBy(ANKE, "WINTER_HAMPER");
        assertThat(theDayItOpens.claimable()).isTrue();
        assertThat(theDayItOpens.lockedBecause()).isNull();
        assertThat(theDayItOpens.whyItIsLocked()).isNull();

        // The last day of it, which is a day it can still be claimed on.
        app.daysPass(DAYS_IT_STAYS_OPEN);
        assertThat(app.theDateTheClockReads()).isEqualTo(closesOn);
        assertThat(app.theOfferAsReadBy(ANKE, "WINTER_HAMPER").claimable())
                .as("the closing day is inclusive: a season closing on the thirtieth is open all "
                        + "through the thirtieth")
                .isTrue();

        // And the morning after. Shown, locked, with the day it closed, and refused.
        app.daysPass(1);
        RewardForACustomerView theMorningAfter = app.theOfferAsReadBy(ANKE, "WINTER_HAMPER");
        assertThat(theMorningAfter.claimable()).isFalse();
        assertThat(theMorningAfter.lockedBecause()).isEqualTo("CLOSED");
        assertThat(theMorningAfter.whyItIsLocked()).contains(closesOn.toString());
        ResponseEntity<JsonNode> tooLate = app.tryToClaim(ANKE, "WINTER_HAMPER");
        assertThat(tooLate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(tooLate)).isEqualTo(theMorningAfter.whyItIsLocked());

        // Nothing was spent in any of it, which is the half a refusal has to get right.
        assertThat(app.pointsBalanceOf(ANKE))
                .as("two refused claims and four winds of the clock, and the points are all "
                        + "still there")
                .isEqualTo(pointsBefore + WHAT_THE_HAMPER_COSTS);
    }

    /**
     * An offer with neither day set is claimable on every one of those days too, which is the
     * safety argument of the ticket read against a clock rather than against a calendar.
     *
     * <p>Its own test but the same application, deliberately, because the point is that the days
     * this test wound past did nothing to it: the seeded four have no window and a wound clock has
     * to leave them exactly where it found them.
     */
    @Test
    void an_offer_with_neither_day_set_is_claimable_whatever_the_clock_reads() {
        app.daysPass(200);

        assertThat(app.theOfferAsReadBy(ANKE, "CINEMA_TICKET").claimable()).isTrue();
        assertThat(app.theOfferAsReadBy(ANKE, "CINEMA_TICKET").opensOn()).isNull();
        assertThat(app.theOfferAsReadBy(ANKE, "CINEMA_TICKET").closesOn()).isNull();
    }

    /** An offer as an administration form would send one, with its season on it. */
    private static Map<String, Object> anOffer(String code, String title, LocalDate opensOn,
                                               LocalDate closesOn) {
        Map<String, Object> offer = new HashMap<>();
        offer.put("code", code);
        offer.put("title", title);
        offer.put("description", "A box of things for the winter.");
        offer.put("costInPoints", WHAT_THE_HAMPER_COSTS);
        offer.put("voucherPrefix", "WIN");
        offer.put("opensOn", opensOn.toString());
        offer.put("closesOn", closesOn.toString());
        return offer;
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private static String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }
}
