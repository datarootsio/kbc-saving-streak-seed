package io.dataroots.savingstreak.anofferthatisnotopenyet;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.OfferView;
import io.dataroots.savingstreak.support.RewardForACustomerView;
import io.dataroots.savingstreak.support.RewardView;
import io.dataroots.savingstreak.support.SeededAccounts;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * An offer with a day it opens on and a day it closes on, and the reading that tells each customer
 * where they stand with it.
 *
 * <p><strong>The reading is the bigger half of this ticket.</strong> A window that only ever
 * refused a claim would be a rule a customer meets at the last step, after they have decided they
 * want the thing — which is the failure the whole spec is written against. So every test below
 * asserts the card and the refusal together where both exist: what the customer is shown before
 * they press, and what they are told if they press anyway. They are the same fact at two moments
 * and they have to say the same sentence.
 *
 * <p><strong>Days are taken from the application's own clock, never from this machine's.</strong>
 * The clock is the development clock, which a trainer winds; a test that computed tomorrow from
 * {@code LocalDate.now()} would be asserting against a different day from the one the application
 * is judging by the moment anybody has wound it. Winding it is the other test in this package,
 * which has an application of its own to wind.
 *
 * <p><strong>Every test asserts on what its own requests changed, and puts the catalogue
 * back.</strong> The run shares one database and the catalogue is the most shared thing in it.
 * Nothing here counts the offers; what it asserts is what happened to the one it wrote, and
 * {@link OffersWithAWindowThisTestWrites#putTheCatalogueBack} withdraws it afterwards so that the
 * test pinning the customer's catalogue to four entries at four prices goes on passing.
 *
 * <p>Anke is the customer, because she is the one the claiming tests already spend from; what she
 * spends here, she earns here.
 */
class AnOfferThatOpensAndClosesOnADayApiTest extends ApiIntegrationTest {

    private SeededAccounts seeded;

    private OffersWithAWindowThisTestWrites offers;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
        offers = new OffersWithAWindowThisTestWrites(http);
    }

    @AfterEach
    void leaveTheCatalogueAsItWasFound() {
        offers.putTheCatalogueBack();
    }

    /**
     * The half of the ticket a customer would notice first: an offer that has not opened is on the
     * page, greyed, saying the day it opens.
     *
     * <p>Shown and locked asserted together, on purpose. "It says it is not open yet" would pass
     * just as well against a catalogue that left it out entirely, and leaving it out is precisely
     * the thing this feature refuses to do — an offer nobody can see teaches nobody to come back
     * for it.
     */
    @Test
    void an_offer_that_has_not_opened_is_shown_locked_with_the_day_it_opens() {
        LocalDate tomorrow = theDayTheApplicationThinksItIs().plusDays(1);
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Spring hamper", 5, tomorrow, null);

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);

        assertThat(reading.claimable()).isFalse();
        assertThat(reading.lockedBecause()).isEqualTo("NOT_OPEN_YET");
        assertThat(reading.whyItIsLocked())
                .as("the sentence has the day in it, because the day is the only part of it "
                        + "somebody can act on")
                .contains("Spring hamper")
                .contains(tomorrow.toString());
        assertThat(reading.opensOn()).isEqualTo(tomorrow);
        assertThat(reading.closesOn()).isNull();
    }

    /**
     * And pressing it anyway is refused, in the same words, with nothing spent.
     *
     * <p>The balance is the load-bearing half. A refusal that had already taken the points would
     * be worse than no rule at all, and the order the module checks in — the window before the
     * customer and well before the points — is what makes that impossible rather than unlikely.
     */
    @Test
    void claiming_an_offer_that_has_not_opened_is_refused_and_takes_nothing() {
        LocalDate tomorrow = theDayTheApplicationThinksItIs().plusDays(1);
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Spring hamper", 5, tomorrow, null);
        earn(5);
        long before = pointsBalance();

        ResponseEntity<JsonNode> refused = tryToClaim(code);

        assertThat(refused.getStatusCode())
                .as("the offer is real and the request is well formed; what says no is the state "
                        + "of the world, which is what a conflict means")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .isEqualTo(offers.asReadBy(seeded.customerIdOf(ANKE), code).whyItIsLocked());
        assertThat(reasonGivenBy(refused)).contains(tomorrow.toString());
        assertThat(pointsBalance()).isEqualTo(before);
    }

    /**
     * The other end of the window: an offer whose last day has gone past is shown, locked, and
     * refused.
     *
     * <p>Locked rather than removed, for the reason a not-yet-open offer is: a customer who was
     * saving for something has to be told it has gone rather than left wondering where it went,
     * and "closed on the fourteenth" is a different sentence from "there is nothing called that".
     */
    @Test
    void an_offer_past_its_closing_day_is_shown_locked_and_refused() {
        LocalDate yesterday = theDayTheApplicationThinksItIs().minusDays(1);
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Last week's hamper", 5, null, yesterday);
        earn(5);
        long before = pointsBalance();

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);
        ResponseEntity<JsonNode> refused = tryToClaim(code);

        assertThat(reading.claimable()).isFalse();
        assertThat(reading.lockedBecause()).isEqualTo("CLOSED");
        assertThat(reading.whyItIsLocked()).contains(yesterday.toString());
        assertThat(reading.closesOn()).isEqualTo(yesterday);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused)).isEqualTo(reading.whyItIsLocked());
        assertThat(pointsBalance()).isEqualTo(before);
    }

    /**
     * The closing day is inclusive, and this is the test that says so.
     *
     * <p>The boundary everybody reading a poster assumes, and the one a fencepost error takes
     * away silently: an offer closing on the thirtieth is claimable all through the thirtieth.
     * Asserted as a card <em>and</em> a claim that actually goes through, because a reading that
     * said claimable while the claim refused would be the worst of the two possible bugs.
     */
    @Test
    void an_offer_closing_today_can_still_be_claimed_today() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Today only", 5, null, today);
        earn(5);
        long before = pointsBalance();

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);

        assertThat(reading.claimable()).isTrue();
        assertThat(reading.lockedBecause()).isNull();
        assertThat(reading.whyItIsLocked()).isNull();
        assertThat(reading.closesOn())
                .as("the day it closes is on the card while it is still claimable, because that "
                        + "is the date that decides whether saving for it is worth starting")
                .isEqualTo(today);

        ClaimedRewardView claimed = claim(code);

        assertThat(claimed.code()).isEqualTo(code);
        assertThat(pointsBalance()).isEqualTo(before - 5);
    }

    /**
     * An offer whose window opened today is claimable today, which is the other fencepost.
     *
     * <p>Two off-by-ones, not one. A window read with the wrong comparison at either end is
     * invisible until somebody's season is a day short, and a test that only checked the far side
     * of each boundary would pass against both mistakes.
     */
    @Test
    void an_offer_opening_today_can_be_claimed_today() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "Open from today", 5, today, today.plusDays(7));
        earn(5);

        RewardForACustomerView reading = offers.asReadBy(seeded.customerIdOf(ANKE), code);

        assertThat(reading.claimable()).isTrue();
        assertThat(reading.opensOn()).isEqualTo(today);
        assertThat(claim(code).code()).isEqualTo(code);
    }

    /**
     * An offer with neither day set behaves exactly as it always has, for everybody — which is
     * the safety argument of the whole ticket.
     *
     * <p>Asserted against the four seeded entries rather than against something this test wrote,
     * because they are the ones a customer knew yesterday. Not by counting them: the run shares a
     * database and another class's offer may be on sale while this one looks. What is asserted is
     * that each of the four is there, claimable, with nothing locking it and no window on it.
     */
    @Test
    void the_four_offers_that_have_always_been_there_read_as_claimable_with_no_window() {
        long customerId = seeded.customerIdOf(ANKE);

        assertThat(offers.theCatalogueAsReadBy(customerId))
                .filteredOn(entry -> theFourThatHaveAlwaysBeenThere().contains(entry.code()))
                .hasSize(4)
                .allSatisfy(entry -> {
                    assertThat(entry.claimable()).isTrue();
                    assertThat(entry.lockedBecause()).isNull();
                    assertThat(entry.whyItIsLocked()).isNull();
                    assertThat(entry.opensOn()).isNull();
                    assertThat(entry.closesOn()).isNull();
                });
    }

    /**
     * The customer-shaped reading is the published catalogue and nothing else: a draft is not
     * shown locked, and neither is a withdrawn offer.
     *
     * <p>Worth its own test because "locked with the reason, never hidden" is a rule about the
     * offers being <em>run</em>, and reading it as a rule about every row would put a half-written
     * reward on a customer's screen with an explanation attached. A draft is not locked; it is not
     * being sold.
     */
    @Test
    void a_draft_and_a_withdrawn_offer_are_not_in_the_customer_shaped_reading_at_all() {
        long customerId = seeded.customerIdOf(ANKE);
        String draft = offers.aCodeNobodyHasUsed();
        offers.writes(draft, "Still being written", 5, null, null);
        String gone = offers.aCodeNobodyHasUsed();
        offers.onSale(gone, "Taken down", 5, null, null);
        assertThat(offers.asReadBy(customerId, gone).claimable()).isTrue();

        offers.triesToWithdraw(gone);

        assertThat(offers.theCatalogueAsReadBy(customerId))
                .extracting(RewardForACustomerView::code)
                .doesNotContain(draft, gone);
    }

    /**
     * The window is set from the administration screen, read back there, and changed there — and
     * a day can be taken off again.
     *
     * <p>The taking-off is the half worth insisting on. A season somebody announced and then
     * called off leaves an offer that should go back to being open all the time, and a form that
     * could set a date but never clear it would show an administrator an edit that never happened
     * — which is the failure this codebase refuses to ship anywhere else either.
     */
    @Test
    void a_window_is_set_read_back_changed_and_taken_off_again() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();

        OfferView written = offers.writes(code, "A season", 5, today.plusDays(3), today.plusDays(9));
        assertThat(written.opensOn()).isEqualTo(today.plusDays(3));
        assertThat(written.closesOn()).isEqualTo(today.plusDays(9));

        OfferView movedOn = offers.changes(code, Map.of("closesOn", today.plusDays(20).toString()));
        assertThat(movedOn.opensOn())
                .as("a change names one day and leaves the other exactly as it was")
                .isEqualTo(today.plusDays(3));
        assertThat(movedOn.closesOn()).isEqualTo(today.plusDays(20));

        OfferView openEnded = offers.changes(code, Map.of("closesOn", ""));
        assertThat(openEnded.closesOn())
                .as("an emptied date box is an instruction, and the instruction is that there is "
                        + "no longer a day")
                .isNull();
        assertThat(openEnded.opensOn()).isEqualTo(today.plusDays(3));
    }

    /**
     * A window that runs backwards is refused where it is written, in a sentence.
     *
     * <p>Not a short season but a season nobody could ever claim in, which is a row that can only
     * be a mistake — and the season machinery next door refuses the same shape for the same
     * reason. Answered at the form rather than left for somebody to puzzle over a fortnight later
     * wondering why nobody claimed the hamper.
     */
    @Test
    void a_window_whose_close_is_before_its_open_is_refused_when_it_is_written() {
        LocalDate today = theDayTheApplicationThinksItIs();
        Map<String, Object> backwards = new HashMap<>();
        backwards.put("code", offers.aCodeNobodyHasUsed());
        backwards.put("title", "A season nobody can join");
        backwards.put("costInPoints", 5);
        backwards.put("voucherPrefix", "NOP");
        backwards.put("opensOn", today.plusDays(10).toString());
        backwards.put("closesOn", today.plusDays(2).toString());

        ResponseEntity<JsonNode> refused = offers.triesToWrite(backwards);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains(today.plusDays(2).toString())
                .contains(today.plusDays(10).toString());
    }

    /**
     * A single day is a legal window, because "today only" is a thing a scheme says.
     *
     * <p>The other side of the rule above, and the reason the comparison there is strictly
     * before: a rule that refused a window whose ends are equal would refuse the one-day
     * promotion that windows are most obviously for.
     */
    @Test
    void a_window_that_opens_and_closes_on_the_same_day_is_a_day_it_can_be_claimed_on() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "One day only", 5, today, today);
        earn(5);

        assertThat(offers.asReadBy(seeded.customerIdOf(ANKE), code).claimable()).isTrue();
        assertThat(claim(code).code()).isEqualTo(code);
    }

    /**
     * A day nobody could read as a day is answered about the day, and the offer is not written.
     *
     * <p>A fact about the form rather than a rule about the catalogue, which is why it is a bad
     * request and why the sentence is the one a goal's deadline already gets: a date box is a date
     * box wherever it is on the screen.
     */
    @Test
    void a_day_that_is_not_a_day_is_refused_in_words_about_the_day() {
        Map<String, Object> notADay = new HashMap<>();
        notADay.put("code", offers.aCodeNobodyHasUsed());
        notADay.put("title", "Whenever");
        notADay.put("costInPoints", 5);
        notADay.put("voucherPrefix", "WHN");
        notADay.put("opensOn", "next Tuesday");

        ResponseEntity<JsonNode> refused = offers.triesToWrite(notADay);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("next Tuesday").contains("2026-12-31");
    }

    /**
     * The catalogue with nobody in it says nothing about windows and is not changed by one.
     *
     * <p>{@code /api/rewards} is the shape this whole feature is built not to move: four fields, a
     * price and no derivation. An offer with a season on it is published into it like any other —
     * the endpoint has no customer and no clock in it to decide otherwise — and what must stay
     * true is that the entries carry no new field for a page to start depending on.
     */
    @Test
    void the_catalogue_with_nobody_in_it_gains_no_field_when_an_offer_has_a_window() {
        LocalDate today = theDayTheApplicationThinksItIs();
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "A season", 5, today.plusDays(1), today.plusDays(8));

        assertThat(offers.theCatalogueWithNobodyInIt())
                .extracting(RewardView::code)
                .contains(code);
        assertThat(theCatalogueAsItIsSent())
                .as("no window, no lock and no verdict leaks into the catalogue that has always "
                        + "been four fields wide")
                .doesNotContain("opensOn")
                .doesNotContain("closesOn")
                .doesNotContain("claimable")
                .doesNotContain("lockedBecause");
    }

    /** A customer nobody has heard of has no catalogue of their own, and is told so. */
    @Test
    void the_customer_shaped_reading_is_refused_for_a_customer_nobody_has_heard_of() {
        ResponseEntity<JsonNode> refused = http.getForEntity("/api/customers/{id}/rewards",
                JsonNode.class, 999_999_999L);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * What day the application thinks it is, in the zone it counts its calendars in.
     *
     * <p>Read off the development clock rather than off this machine, because those are two
     * different days the moment anybody winds one of them — and a test that wrote "tomorrow"
     * against the wrong one would assert a lock the application had no reason to apply.
     */
    private LocalDate theDayTheApplicationThinksItIs() {
        ClockView clock = http.getForObject("/api/dev/clock", ClockView.class);
        return clock.now().atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
    }

    /** The four the application has always offered, which no window is ever set on. */
    private static List<String> theFourThatHaveAlwaysBeenThere() {
        return List.of("CHARITY_DONATION", "SNACK_VOUCHER", "CINEMA_TICKET",
                "FAMILY_CINEMA_PACK");
    }

    /** Claimed by the customer, out of the one pot of points every account of theirs earns into. */
    private ClaimedRewardView claim(String code) {
        ResponseEntity<ClaimedRewardView> claimed = http.postForEntity(
                "/api/customers/{id}/redemptions", Map.of("reward", code),
                ClaimedRewardView.class, seeded.customerIdOf(ANKE));
        assertThat(claimed.getStatusCode()).describedAs("claiming \"" + code + "\"")
                .isEqualTo(HttpStatus.CREATED);
        return claimed.getBody();
    }

    /** The same claim read as unshaped JSON, for the ones this ticket refuses. */
    private ResponseEntity<JsonNode> tryToClaim(String code) {
        return http.postForEntity("/api/customers/{id}/redemptions", Map.of("reward", code),
                JsonNode.class, seeded.customerIdOf(ANKE));
    }

    /**
     * Earns at least the points this test is about to spend, at a point per euro. A test that
     * needs points is the test that earns them — leaning on what an earlier test left in the
     * account is how a failure lands in a class that never mentioned this one.
     */
    private void earn(long points) {
        ResponseEntity<DepositView> deposit = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", points + ".00", "fromCurrentAccountId",
                        seeded.currentAccountOf(ANKE)),
                DepositView.class, seeded.savingsAccountOf(ANKE));
        assertThat(deposit.getStatusCode())
                .describedAs("a deposit this test needs in order to have points to spend")
                .isEqualTo(HttpStatus.CREATED);
    }

    /** What the customer has to spend, for the tests that say a refusal took nothing. */
    private long pointsBalance() {
        return seeded.pointsBalanceOf(ANKE);
    }

    /** The plain catalogue exactly as it goes over the wire, for saying what is not in it. */
    private String theCatalogueAsItIsSent() {
        ResponseEntity<String> read = http.getForEntity("/api/rewards", String.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read.getBody();
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }
}
