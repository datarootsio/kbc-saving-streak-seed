package io.dataroots.savingstreak.giftingpoints;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GiftView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exactly four things are refused, each in words the customer who caused it can read, and a refused
 * gift leaves both pots and both records exactly as they were.
 *
 * <p>Every test here asserts three things, because a refusal that took the points anyway or wrote
 * the row anyway is not something a later correction could tidy up: the status a page can act on,
 * the sentence a person can read, and both customers untouched — balances and gift lists alike. The
 * gift list is the half that matters most: the row is saved <em>before</em> the points move, so a
 * refusal for want of points is thrown with a row already written, and only the transaction rolling
 * back keeps it from surviving. {@link #assertNothingMoved} is where all of that is said.
 *
 * <p>A fifth refusal is asserted alongside the four: a sender this application has never heard of,
 * which is a 404 like every other per-customer route rather than anything to do with gifting.
 *
 * <p>Two of the sentences are deliberately not this module's own. An address nobody banks under is
 * refused in the words signing in already uses for one, and both are asserted to be the same
 * sentence rather than merely to be similar — two copies are one rewording away from disagreeing
 * about what absence sounds like. A pot with less in it than the customer meant to give is refused
 * in the shape a reward claim already uses: what it costs, and what you have.
 *
 * <p>Bram is the sender for the shortfall, because his pot is the one in this database that has
 * never been paid into — but the figure the refusal has to quote is read off the API first rather
 * than assumed to be zero, so the test still says what it means if that ever changes.
 *
 * <p>The shared application is enough for all of it, unlike the tests about gifts that go through: a
 * refused gift moves nothing, so nothing here disturbs a database other tests are asserting on. That
 * is itself the point being made.
 */
class AGiftIsRefusedInWordsApiTest extends ApiIntegrationTest {

    /** An address that is well formed and belongs to nobody, which is how a typo arrives. */
    private static final String NOBODY_BANKS_UNDER_THIS = "someone.else@example.be";

    private SeededAccounts seeded;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
    }

    /**
     * A typo in the address, which is the mistake this refusal exists for: the points must not go
     * silently nowhere.
     *
     * <p>Not-found rather than a bad request, because the request is fine and the person named in it
     * is not there — and the sentence is signing in's own, asserted by asking signing in the same
     * question and comparing the two answers.
     */
    @Test
    void a_gift_to_an_address_nobody_banks_under_is_not_found() {
        Held anke = whatIsHeldBy(ANKE);
        Held bram = whatIsHeldBy(BRAM);

        ResponseEntity<JsonNode> response = give(ANKE, NOBODY_BANKS_UNDER_THIS, "1");

        assertRefused(response, HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(response))
                .as("the same words signing in gives for an address it does not know")
                .isEqualTo(whySigningInWasRefused(NOBODY_BANKS_UNDER_THIS));
        assertNothingMoved(ANKE, anke);
        assertNothingMoved(BRAM, bram);
    }

    /**
     * A gift to yourself. It would move nothing, and a no-op that reports success is worse than a
     * refusal that explains itself — so it is refused, and the sentence says who they are signed in
     * as.
     */
    @Test
    void a_gift_to_yourself_is_refused() {
        Held anke = whatIsHeldBy(ANKE);

        ResponseEntity<JsonNode> response = give(ANKE, seeded.contactDetailsOf(ANKE), "1");

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .as("and it says who the person asking is, so they can see what went wrong")
                .contains(ANKE);
        assertNothingMoved(ANKE, anke);
    }

    /**
     * A figure that is not a positive whole number of points, in the four shapes a customer produces
     * — nothing, less than nothing, half a point, and a word — each answered with what was wrong
     * with it rather than with one sentence covering all four.
     *
     * <p>Each case names a word its sentence has to carry, and they are different words on purpose:
     * a customer who typed {@code 2.5} is told points are whole, and one who typed {@code -5} is
     * told a gift has to be more than zero. One generic "that is not a valid number of points" would
     * pass a test that only asked for words, and would tell somebody nothing they did not know.
     *
     * <p>The figure travels as the customer typed it, which is what makes these answerable in words
     * at all: {@code 2.5} reaches the backend as {@code 2.5} rather than being coerced in the browser
     * into something plausible.
     */
    @ParameterizedTest
    @CsvSource({
            "0, zero",
            "-5, zero",
            "2.5, whole",
            "abc, not a number"
    })
    void a_figure_that_is_not_a_positive_whole_number_of_points_is_refused(String typed,
                                                                          String whatItHasToSay) {
        Held anke = whatIsHeldBy(ANKE);
        Held bram = whatIsHeldBy(BRAM);

        ResponseEntity<JsonNode> response = give(ANKE, seeded.contactDetailsOf(BRAM), typed);

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .as("the sentence says what was wrong with this figure in particular")
                .contains(whatItHasToSay)
                .as("and quotes the characters that were typed, not what they parsed to")
                .contains(typed);
        assertNothingMoved(ANKE, anke);
        assertNothingMoved(BRAM, bram);
    }

    /**
     * A wall of letters where the points go. It is refused for not being a number, which is what is
     * wrong with it — the length is why it is never read as one, and not what the person is told.
     *
     * <p>Here rather than beside the other hostile figures in
     * {@link APointsFigureIsAnsweredInWordsApiTest}, which settles that such a thing is refused
     * promptly and in words and leaves the wording to this ticket. This is the wording: a shortfall
     * would have told somebody to give away fewer letters.
     */
    @Test
    void a_long_thing_that_was_never_a_number_is_refused_for_not_being_one() {
        String aWallOfLetters = "abc".repeat(1_000);
        Held anke = whatIsHeldBy(ANKE);

        ResponseEntity<JsonNode> response = give(ANKE, seeded.contactDetailsOf(BRAM), aWallOfLetters);

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .as("told what is actually wrong with it, rather than that they cannot afford it")
                .contains("not a number")
                .as("in words short enough to read, rather than the whole of it handed back")
                .hasSizeLessThan(200);
        assertNothingMoved(ANKE, anke);
    }

    /**
     * More points than the sender holds. The refusal quotes what they do hold, because the person
     * reading it is correcting the figure and should not have to go and look it up.
     *
     * <p>Asserted against the balance read off the API a moment earlier rather than against a figure
     * this test believes in, which is also the only honest way to ask it on a shared database.
     */
    @Test
    void a_gift_of_more_points_than_the_sender_holds_is_refused_and_quotes_the_balance() {
        Held bram = whatIsHeldBy(BRAM);
        Held anke = whatIsHeldBy(ANKE);
        long onePointMoreThanHeHas = bram.pointsBalance() + 1;

        ResponseEntity<JsonNode> response =
                give(BRAM, seeded.contactDetailsOf(ANKE), String.valueOf(onePointMoreThanHeHas));

        assertRefused(response, HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(response))
                .as("what the gift would cost, and what they have, in the shape a claim already uses")
                .contains(String.valueOf(onePointMoreThanHeHas))
                .contains("you have " + bram.pointsBalance());
        assertNothingMoved(BRAM, bram);
        assertNothingMoved(ANKE, anke);
    }

    /**
     * A gift from somebody this application has never heard of. A mistake about who, not about
     * points, and reported as one — a 404 like every other route that names a customer in its path.
     *
     * <p>The one refusal here that cannot go on to assert nothing moved for the sender: there is no
     * pot and no gift list to read for an identifier no customer has. The recipient's is read
     * instead, which is where anything wrongly credited would have landed.
     */
    @Test
    void a_gift_from_a_customer_that_does_not_exist_is_not_found() {
        Held anke = whatIsHeldBy(ANKE);

        ResponseEntity<JsonNode> response = http.postForEntity(
                "/api/customers/{id}/gifts",
                Map.of("recipientContactDetails", seeded.contactDetailsOf(ANKE), "points", "1"),
                JsonNode.class,
                seeded.anIdNoCustomerHas());

        assertRefused(response, HttpStatus.NOT_FOUND);
        assertNothingMoved(ANKE, anke);
    }

    /**
     * A body that is not a gift at all: no recipient, or no points. Refused rather than allowed to
     * fail somewhere inside, and refused as a bad request rather than as one of the four — a field
     * that was never filled in is a malformed request, and this is the line signing in already draws
     * for itself, where a blank address is a bad request and an address nobody banks under is a 404.
     *
     * <p>Written down as a test because it is a judgement call rather than an obvious answer, and a
     * previous review left it to be settled here.
     */
    @Test
    void a_gift_naming_no_recipient_or_no_points_is_a_bad_request() {
        Held anke = whatIsHeldBy(ANKE);

        assertRefused(give(ANKE, "   ", "1"), HttpStatus.BAD_REQUEST);
        assertRefused(give(ANKE, seeded.contactDetailsOf(BRAM), "  "), HttpStatus.BAD_REQUEST);
        assertThat(whySigningInWasRefused("   "))
                .as("the same line signing in draws: a blank address is a bad request too")
                .isNotBlank();

        assertNothingMoved(ANKE, anke);
    }

    /** A refusal, and words to go with it. Which words is each test's own business. */
    private void assertRefused(ResponseEntity<JsonNode> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(reasonGivenBy(response)).isNotBlank();
    }

    /** The words a refusal came with, from the field the frontend reads them out of. */
    private String reasonGivenBy(ResponseEntity<JsonNode> refusal) {
        return refusal.getBody().path("detail").asText();
    }

    /**
     * What a refused gift has to leave exactly as it found it, for one of the two customers: the
     * points in their pot, and the gifts they have been part of.
     */
    private record Held(long pointsBalance, int giftsListed) {
    }

    private Held whatIsHeldBy(String customerName) {
        GiftView[] listed = http.getForObject("/api/customers/{id}/gifts", GiftView[].class,
                seeded.customerIdOf(customerName));
        return new Held(seeded.pointsBalanceOf(customerName), listed.length);
    }

    private void assertNothingMoved(String customerName, Held before) {
        Held after = whatIsHeldBy(customerName);
        assertThat(after.pointsBalance())
                .as(customerName + "'s balance is exactly what it was")
                .isEqualTo(before.pointsBalance());
        assertThat(after.giftsListed())
                .as(customerName + "'s gift list has not grown, so no row survived the refusal")
                .isEqualTo(before.giftsListed());
    }

    /**
     * Why signing in with this address was refused, in the words somebody signing in is given. The
     * gift refusal for an unknown address is asserted to be this same sentence.
     */
    private String whySigningInWasRefused(String contactDetails) {
        ResponseEntity<JsonNode> refused = http.postForEntity("/api/customers/sign-in",
                Map.of("contactDetails", contactDetails), JsonNode.class);
        assertThat(refused.getStatusCode().isError())
                .as("signing in with an address like this is refused, or there is nothing to compare")
                .isTrue();
        return reasonGivenBy(refused);
    }

    /**
     * Read as unshaped JSON, because a refusal answers with the reason rather than with a gift:
     * asking for the response as a gift would read the error body as an empty one and lose the
     * sentence before the status could be looked at.
     */
    private ResponseEntity<JsonNode> give(String senderName, String recipientAsTyped, String points) {
        return http.postForEntity(
                "/api/customers/{id}/gifts",
                Map.of("recipientContactDetails", recipientAsTyped, "points", points),
                JsonNode.class,
                seeded.customerIdOf(senderName));
    }
}
