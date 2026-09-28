package io.dataroots.savingstreak.thequeuethatdecideswho;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.PlaceInTheQueueView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import io.dataroots.savingstreak.support.SeededAccounts;
import io.dataroots.savingstreak.support.WaitingListEntryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The queue itself: joining one, leaving one, being told where you stand, and the two ways a
 * request to join is turned down.
 *
 * <p><strong>Nothing here runs the sweep and nothing here winds the clock.</strong> What
 * happens when stock comes back is a nightly job's business and lives in
 * {@code WhenTheStockComesBackApiTest} beside this, which starts an application of its own for
 * the reason every clock-moving test does: the development clock is the whole run's and only
 * moves forward. What is left here is everything a customer can do to a queue with their own
 * hands, which is most of what a queue is.
 *
 * <p><strong>Every offer here is written with nought of it in stock.</strong> A queue may only
 * be joined for something that has genuinely run out, and putting nought on the shelf is the
 * shortest honest way to arrange that — it is also exactly the state an administrator writing
 * next season's hamper is in, and the state a restock is the answer to. Nothing in this class
 * needs a second customer to take the last one first, and nothing in it needs a single point:
 * waiting is free, which is itself asserted below.
 *
 * <p>Every test writes its own offer and withdraws it afterwards, because the catalogue a
 * customer reads is asserted elsewhere to be exactly the four seeded entries at their four
 * prices, and because none of those four can ever run out. One database serves the run, so
 * every assertion is about a delta this test caused.
 *
 * <p>Anke joins first and Bram is whoever came second, which is the pairing the rest of the
 * suite already uses for "somebody else got there first".
 */
class TheQueueThatDecidesWhoApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    private OffersToQueueForThisTestWrites offers;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
        offers = new OffersToQueueForThisTestWrites(http);
    }

    @AfterEach
    void leaveTheCatalogueAsItWasFound() {
        offers.putTheCatalogueBack();
    }

    /**
     * A customer can join the queue for something that has run out, and is told where they
     * stand in the very answer to the request that put them there.
     *
     * <p>The position is the load-bearing part. "You are in the queue" answers nothing anybody
     * asked; "you are first" is the thing the user story says waiting has to be reasonable
     * about, and a second request to find it out would be a page able to show somebody a queue
     * they were not in.
     */
    @Test
    void a_customer_can_join_the_queue_for_a_sold_out_offer_and_is_told_where_they_stand() {
        String code = offers.aCodeNobodyHasUsed();
        offers.soldOut(code, "The hampers that have not arrived");
        assertThat(readingOf(ANKE, code).lockedBecause())
                .as("a queue is only for something that has genuinely run out")
                .isEqualTo("NOTHING_LEFT");

        PlaceInTheQueueView joined = offers.joins(seeded.customerIdOf(ANKE), code);

        assertThat(joined.position()).isEqualTo(1);
        assertThat(joined.offerCode()).isEqualTo(code);
        assertThat(joined.title()).isEqualTo("The hampers that have not arrived");
        assertThat(joined.joinedAt()).isNotNull();
    }

    /**
     * And the card says so afterwards, which is where the customer actually looks.
     *
     * <p>The lock is deliberately unchanged. Joining a queue does not make a sold-out offer
     * claimable and must not pretend to: the card goes on saying there are none left, in the
     * same words, and gains a line saying where they stand. A card that unlocked itself for
     * somebody in a queue would be promising a thing nobody has.
     */
    @Test
    void the_card_carries_the_position_and_goes_on_saying_the_offer_has_run_out() {
        String code = offers.aCodeNobodyHasUsed();
        offers.soldOut(code, "Waiting is on the card");
        offers.joins(seeded.customerIdOf(ANKE), code);

        RewardForACustomerView theirs = readingOf(ANKE, code);

        assertThat(theirs.yourPlaceInTheQueue()).isEqualTo(1);
        assertThat(theirs.claimable()).as("a queue does not conjure stock").isFalse();
        assertThat(theirs.lockedBecause()).isEqualTo("NOTHING_LEFT");
        assertThat(theirs.whatIsLeft()).isEqualTo(0);
        assertThat(readingOf(BRAM, code).yourPlaceInTheQueue())
                .as("and it is nobody else's position")
                .isNull();
    }

    /** An offer nobody is waiting for says nothing about a queue on anybody's card. */
    @Test
    void an_offer_nobody_is_waiting_for_carries_no_position() {
        String code = offers.aCodeNobodyHasUsed();
        offers.soldOut(code, "Nobody wants this one");

        assertThat(readingOf(ANKE, code).yourPlaceInTheQueue()).isNull();
        assertThat(offers.theWaitingListFor(code)).isEmpty();
    }

    /**
     * <strong>Positions are the order people joined, and nothing else.</strong>
     *
     * <p>Not the order of the identifiers, not alphabetical, and not whatever the database
     * feels like returning: the whole of what a queue promises is that being first in line is
     * worth something, and a queue that could not be relied on to say who was first would be a
     * lottery with a number on it.
     */
    @Test
    void positions_are_the_order_people_joined() {
        String code = offers.aCodeNobodyHasUsed();
        offers.soldOut(code, "First come, first served");

        PlaceInTheQueueView first = offers.joins(seeded.customerIdOf(ANKE), code);
        PlaceInTheQueueView second = offers.joins(seeded.customerIdOf(BRAM), code);

        assertThat(first.position()).isEqualTo(1);
        assertThat(second.position()).isEqualTo(2);
        assertThat(readingOf(ANKE, code).yourPlaceInTheQueue()).isEqualTo(1);
        assertThat(readingOf(BRAM, code).yourPlaceInTheQueue()).isEqualTo(2);
    }

    /**
     * Leaving closes the gap behind, at once and with no job having run.
     *
     * <p>This is what makes the position a count rather than a column. Nothing rewrites
     * anybody's number when somebody in front of them goes: the number is worked out from the
     * order the people still waiting joined in, so it is right the next time anybody looks.
     */
    @Test
    void leaving_the_queue_moves_everybody_behind_forward() {
        String code = offers.aCodeNobodyHasUsed();
        offers.soldOut(code, "Changed her mind");
        offers.joins(seeded.customerIdOf(ANKE), code);
        offers.joins(seeded.customerIdOf(BRAM), code);

        PlaceInTheQueueView left = offers.leaves(seeded.customerIdOf(ANKE), code);

        assertThat(left.position()).as("the place she left from").isEqualTo(1);
        assertThat(readingOf(ANKE, code).yourPlaceInTheQueue())
                .as("she is out of it")
                .isNull();
        assertThat(readingOf(BRAM, code).yourPlaceInTheQueue())
                .as("and the man behind her is now the man in front")
                .isEqualTo(1);
    }

    /** Somebody who left can join again, at the back, because that is where they now are. */
    @Test
    void somebody_who_left_can_join_again_and_goes_to_the_back() {
        String code = offers.aCodeNobodyHasUsed();
        offers.soldOut(code, "Back again");
        offers.joins(seeded.customerIdOf(ANKE), code);
        offers.leaves(seeded.customerIdOf(ANKE), code);
        offers.joins(seeded.customerIdOf(BRAM), code);

        PlaceInTheQueueView again = offers.joins(seeded.customerIdOf(ANKE), code);

        assertThat(again.position())
                .as("her old place was given up, and the queue does not remember it")
                .isEqualTo(2);
    }

    /**
     * <strong>Joining a queue for something that has not run out is refused.</strong>
     *
     * <p>The mirror of being refused a claim for something that has. There is nothing to be
     * next for, no returning stock for the sweep to hand out, and the customer would be waiting
     * for a thing already theirs — so the sentence tells them the one thing they should do
     * instead, which is claim it.
     *
     * <p>A conflict rather than a bad request: the code is right, the customer is real, and
     * what says no is the state of the stock, which has changed in the direction they wanted
     * since the page was drawn.
     */
    @Test
    void joining_the_queue_for_an_offer_that_has_not_run_out_is_refused() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Three of them on the shelf", 5, 3);

        ResponseEntity<JsonNode> refused = offers.triesToJoin(seeded.customerIdOf(ANKE), code);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .contains("has not run out")
                .contains("There are 3 left")
                .contains("claim it now");
        assertThat(offers.theWaitingListFor(code))
                .as("and nobody was put in a queue by a refusal")
                .isEmpty();
    }

    /** And an offer that never runs out at all is refused in the same breath. */
    @Test
    void joining_the_queue_for_an_offer_that_never_runs_out_is_refused() {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "There will always be more", 5, null);

        ResponseEntity<JsonNode> refused = offers.triesToJoin(seeded.customerIdOf(ANKE), code);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains("It never runs out");
    }

    /**
     * <strong>Joining twice is refused, and the refusal says where they already stand.</strong>
     *
     * <p>The one refusal in this feature that is good news, which is why the position is in the
     * sentence: they wanted to be in the line and they are in it. Two places for one person
     * would be one customer with two turns ahead of somebody with one, which is the whole of
     * what a queue exists not to do.
     */
    @Test
    void joining_twice_is_refused_and_says_where_they_already_stand() {
        String code = offers.aCodeNobodyHasUsed();
        offers.soldOut(code, "Pressed twice");
        offers.joins(seeded.customerIdOf(BRAM), code);
        offers.joins(seeded.customerIdOf(ANKE), code);

        ResponseEntity<JsonNode> refused = offers.triesToJoin(seeded.customerIdOf(ANKE), code);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .contains("already in the queue")
                .contains("position 2");
        assertThat(offers.theWaitingListFor(code))
                .as("one person, one place")
                .hasSize(2);
    }

    /** A queue for an offer nobody is selling is refused before anything about the stock. */
    @Test
    void joining_the_queue_for_an_offer_that_is_not_on_sale_is_refused() {
        String code = offers.aCodeNobodyHasUsed();
        offers.soldOut(code, "Taken down this morning");
        offers.triesToWithdraw(code);

        ResponseEntity<JsonNode> refused = offers.triesToJoin(seeded.customerIdOf(ANKE), code);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains("not on sale");
    }

    /** And a queue for a code the catalogue has never heard of is the same bad word it always was. */
    @Test
    void joining_the_queue_for_something_that_is_not_in_the_catalogue_is_refused() {
        ResponseEntity<JsonNode> refused =
                offers.triesToJoin(seeded.customerIdOf(ANKE), "NO_SUCH_THING_AT_ALL");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("NO_SUCH_THING_AT_ALL");
    }

    /**
     * Leaving a queue they are not in is refused, and the sentence says which absence it is.
     *
     * <p>Three sentences rather than one, for the reason a missing hold gets four: never
     * joined, already left, and already promoted send somebody to do three quite different
     * things next.
     */
    @Test
    void leaving_a_queue_they_were_never_in_is_refused() {
        String code = offers.aCodeNobodyHasUsed();
        offers.soldOut(code, "Never joined this one");

        ResponseEntity<JsonNode> refused = offers.triesToLeave(seeded.customerIdOf(ANKE), code);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains("not in the queue");
    }

    /** And leaving twice says so, rather than saying they were never there. */
    @Test
    void leaving_twice_is_refused_in_words_that_say_they_already_left() {
        String code = offers.aCodeNobodyHasUsed();
        offers.soldOut(code, "Left it once already");
        offers.joins(seeded.customerIdOf(ANKE), code);
        offers.leaves(seeded.customerIdOf(ANKE), code);

        ResponseEntity<JsonNode> refused = offers.triesToLeave(seeded.customerIdOf(ANKE), code);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).contains("already left the queue");
    }

    /**
     * <strong>An administrator sees who is queued, in order, with their names on it.</strong>
     *
     * <p>"So that I know what to restock" is the user story, and it is answered by a column of
     * people rather than by a number: an offer twelve customers are waiting for is a different
     * decision from one that two are, and the person at the front is the one who has been
     * waiting longest.
     */
    @Test
    void an_administrator_sees_who_is_queued_for_an_offer_in_order() {
        String code = offers.aCodeNobodyHasUsed();
        offers.soldOut(code, "What to restock");
        offers.joins(seeded.customerIdOf(BRAM), code);
        offers.joins(seeded.customerIdOf(ANKE), code);

        List<WaitingListEntryView> queue = offers.theWaitingListFor(code);

        assertThat(queue).extracting(WaitingListEntryView::customerName)
                .containsExactly(BRAM, ANKE);
        assertThat(queue).extracting(WaitingListEntryView::position).containsExactly(1, 2);
        assertThat(queue.get(0).customerId()).isEqualTo(seeded.customerIdOf(BRAM));
        assertThat(queue.get(0).joinedAt()).isNotNull();
    }

    /** Somebody who left is not on the administrator's list either — it is the line as it stands. */
    @Test
    void the_administrators_list_shows_the_line_as_it_stands_and_not_its_history() {
        String code = offers.aCodeNobodyHasUsed();
        offers.soldOut(code, "One of them thought better of it");
        offers.joins(seeded.customerIdOf(ANKE), code);
        offers.joins(seeded.customerIdOf(BRAM), code);
        offers.leaves(seeded.customerIdOf(ANKE), code);

        assertThat(offers.theWaitingListFor(code))
                .extracting(WaitingListEntryView::customerName)
                .containsExactly(BRAM);
    }

    /** A waiting list for a code nobody has heard of is a page that is not there. */
    @Test
    void the_waiting_list_of_an_offer_that_does_not_exist_is_a_missing_page() {
        assertThat(offers.triesToReadTheWaitingListFor("NO_SUCH_THING_AT_ALL").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * <strong>Waiting costs nothing at all.</strong>
     *
     * <p>The same property a hold has, one step further back, and it is why somebody who cannot
     * afford the thing today is exactly the customer a queue is worth having: their turn may be
     * weeks away and the points are spent when they convert, if they convert.
     */
    @Test
    void joining_and_leaving_a_queue_move_no_points() {
        String code = offers.aCodeNobodyHasUsed();
        offers.soldOut(code, "Free to wait for");
        long before = seeded.pointsBalanceOf(ANKE);

        offers.joins(seeded.customerIdOf(ANKE), code);
        assertThat(seeded.pointsBalanceOf(ANKE)).isEqualTo(before);

        offers.leaves(seeded.customerIdOf(ANKE), code);
        assertThat(seeded.pointsBalanceOf(ANKE)).isEqualTo(before);
    }

    /**
     * A queue belongs to its own offer and to no other, which is the one thing a grouped count
     * could quietly get wrong.
     */
    @Test
    void two_queues_are_two_queues() {
        String one = offers.aCodeNobodyHasUsed();
        String other = offers.aCodeNobodyHasUsed();
        offers.soldOut(one, "One thing");
        offers.soldOut(other, "Another thing");
        offers.joins(seeded.customerIdOf(ANKE), one);
        offers.joins(seeded.customerIdOf(BRAM), one);

        PlaceInTheQueueView joined = offers.joins(seeded.customerIdOf(BRAM), other);

        assertThat(joined.position())
                .as("first in this queue, whatever the other one is doing")
                .isEqualTo(1);
        assertThat(readingOf(BRAM, one).yourPlaceInTheQueue()).isEqualTo(2);
        assertThat(readingOf(BRAM, other).yourPlaceInTheQueue()).isEqualTo(1);
    }

    /**
     * A restock alone changes nothing about anybody's place, and that is worth pinning: the
     * sweep is what moves a queue, and stock coming back during the day moves nobody until it
     * runs.
     */
    @Test
    void stock_coming_back_does_not_move_the_queue_until_the_sweep_runs() {
        String code = offers.aCodeNobodyHasUsed();
        offers.soldOut(code, "Restocked in the afternoon");
        offers.joins(seeded.customerIdOf(ANKE), code);

        offers.changes(code, Map.of("stock", 2));

        assertThat(readingOf(ANKE, code).yourPlaceInTheQueue())
                .as("still in the queue, because nothing has promoted her yet")
                .isEqualTo(1);
        assertThat(readingOf(ANKE, code).yourHoldLapsesAt())
                .as("and nothing is being held for her")
                .isNull();
        assertThat(offers.theWaitingListFor(code)).hasSize(1);
    }

    private RewardForACustomerView readingOf(String customer, String code) {
        return offers.asReadBy(seeded.customerIdOf(customer), code);
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        assertThat(refusal.getBody()).as("a problem document with a reason in it").isNotNull();
        return refusal.getBody().path("detail").asText();
    }
}
