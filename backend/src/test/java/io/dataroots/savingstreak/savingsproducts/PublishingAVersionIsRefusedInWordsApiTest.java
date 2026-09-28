package io.dataroots.savingstreak.savingsproducts;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.TermsVersionView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsproducts.ACatalogueSomebodyAdministers.reasonGivenBy;
import static io.dataroots.savingstreak.savingsproducts.ACatalogueSomebodyAdministers.theSameTermsAgain;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every way a publish can be refused, in the domain's own sentence, with nothing written down.
 *
 * <p><strong>Every test here asserts both halves.</strong> A refusal that came back with the right
 * status and wrote the row anyway would be the worst bug this feature could have — an agreement
 * somebody is living under, written by a request the application said no to — so each of these
 * reads the history afterwards and insists it is exactly what it was. The shared
 * {@link #nothingWasPublished} does the second half, so no test can forget it.
 *
 * <p><strong>It needs no ordering, unlike the test next door</strong>, because nothing here is
 * supposed to change anything: that is the point of every one of them. It still gets an application
 * and a file of its own, because a test whose subject is "nothing was written" cannot share a
 * database with tests that write.
 *
 * <p>The sentences are asserted on for the part the person who typed them can act on — the figure
 * they typed, the box it came out of, the version that refused them — rather than word for word. A
 * test pinning a whole sentence is a test that fails when somebody improves the wording, which
 * teaches the next person to stop improving the wording.
 */
class PublishingAVersionIsRefusedInWordsApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-publishing-refused");

    /** The product every refusal here is aimed at, so that "nothing was written" is one history. */
    private static final String THE_PRODUCT = "NOTICE32";

    /** A code the bank has never sold, and is not one letter away from one it has. */
    private static final String NOT_A_PRODUCT = "SUPER_SAVER_9000";

    private static ACatalogueSomebodyAdministers bank;

    @BeforeAll
    static void startABankSomebodyRuns() {
        bank = new ACatalogueSomebodyAdministers(DATABASE);
    }

    @AfterAll
    static void stopIt() {
        if (bank != null) {
            bank.close();
        }
    }

    /**
     * Publishing to a product nobody has heard of is a mistake about what, not about how, and it is
     * reported as one — with the code that arrived quoted back, because a page open since before a
     * release is how this actually happens.
     */
    @Test
    void a_product_the_bank_does_not_sell_cannot_be_published_to() {
        ResponseEntity<JsonNode> refused =
                bank.tryToPublish(NOT_A_PRODUCT, aPerfectlyGoodForm());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(refused)).contains(NOT_A_PRODUCT);
        nothingWasPublished();
    }

    /**
     * The one line saying what changed is required, because that is what the whole ticket is for: a
     * customer comparing versions should be reading an explanation rather than a diff.
     *
     * <p>Blank counts as absent, in a test of its own, because a space is what a required field
     * gets filled with by somebody who has decided the rule does not apply to them.
     */
    @Test
    void a_version_that_does_not_say_what_changed_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.remove("whatChanged");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("what changed");
        nothingWasPublished();
    }

    @Test
    void a_line_saying_what_changed_that_is_only_spaces_is_refused_too() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("whatChanged", "   ");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("what changed");
        nothingWasPublished();
    }

    /**
     * A version needs the day it takes effect, and no day is assumed in its place. Today would be
     * an assumption, and this bank both announces changes ahead of time and backdates corrections.
     */
    @Test
    void a_version_with_no_day_it_takes_effect_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.remove("effectiveFrom");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("day it takes effect");
        nothingWasPublished();
    }

    /**
     * A date box somebody typed a European date into is answered with a sentence about the date,
     * quoting back what they typed — not with a request that could not be read.
     */
    @Test
    void a_day_that_is_not_a_day_is_quoted_back() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("effectiveFrom", "31/12/2026");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("31/12/2026", "2026-12-31");
        nothingWasPublished();
    }

    /**
     * A figure nobody filled in is refused by name rather than read as nought.
     *
     * <p><strong>The most important refusal here.</strong> Nought is a real and different agreement
     * in six of these boxes — no notice, no term, no floor, no penalty, no bonus, no interest at
     * all — so an empty rate box read as nought would publish a product paying nothing to somebody
     * who forgot a field, on money people are saving. The sentence names the box and says the thing
     * the administrator needs to know: a version carries nothing over from the one before it.
     */
    @Test
    void a_figure_nobody_filled_in_is_refused_by_name() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.remove("annualRatePercent");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("The annual rate", "was not sent");
        nothingWasPublished();
    }

    /** And the same for a figure in the middle of the form, so the reading is not the first box's. */
    @Test
    void a_count_nobody_filled_in_is_refused_by_name_too() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("noticeDays", "");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("The notice period", "was not sent");
        nothingWasPublished();
    }

    /**
     * A rate quoted more finely than the agreement can hold is refused rather than rounded.
     *
     * <p>Rounding would move a rate nobody typed, on an agreement people are about to be living
     * under, which is exactly the silent change the whole feature exists to prevent.
     */
    @Test
    void a_rate_quoted_more_finely_than_a_hundredth_of_a_percent_is_refused_and_not_rounded() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("annualRatePercent", "0.605");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("0.605", "hundredth of a percent");
        nothingWasPublished();
    }

    /** A rate less than nothing is not a rate, and the figure is quoted back. */
    @Test
    void a_rate_less_than_nothing_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("annualRatePercent", "-0.10");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("The annual rate", "-0.10");
        nothingWasPublished();
    }

    /** Neither is a notice period of less than nothing, and the sentence says to write 0 instead. */
    @Test
    void a_count_less_than_nothing_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("noticeDays", "-1");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("The notice period", "-1");
        nothingWasPublished();
    }

    /**
     * A points multiplier of nought is refused, which is the one figure here where nought is not the
     * absence of a rule.
     *
     * <p>Nought percent is a product that pays no interest, which somebody could honestly publish.
     * Nought <em>times</em> is not an offer at all — it is a deposit that silently earns no points
     * however much is saved — and {@code 1.0000} is what "changes nothing" spells for a factor.
     */
    @Test
    void a_points_multiplier_of_nothing_is_refused_because_it_is_a_factor_and_not_a_rate() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("pointsMultiplier", "0");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("The points multiplier", "1.0000");
        nothingWasPublished();
    }

    /** A floor that is not quoted the way money is gets the sentence a deposit box already gives. */
    @Test
    void a_floor_that_is_not_quoted_to_the_cent_is_refused() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("minimumBalance", "500.005");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("500.005", "two decimal places");
        nothingWasPublished();
    }

    /** A number box somebody typed a comma into is answered with a sentence about the number. */
    @Test
    void a_figure_that_is_not_a_number_is_quoted_back() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("annualRatePercent", "1,75");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("1,75", "0.50");
        nothingWasPublished();
    }

    /**
     * An ending this bank does not offer comes back listing the three it does — the list quoted
     * from the module's own vocabulary rather than written out in a sentence somebody has to keep
     * up to date.
     */
    @Test
    void an_ending_this_bank_does_not_offer_is_refused_with_the_three_that_exist() {
        Map<String, Object> form = aPerfectlyGoodForm();
        form.put("maturityAction", "MOVE_TO_CURRENT_ACCOUNT");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains("MOVE_TO_CURRENT_ACCOUNT", "ROLL_OVER", "MOVE_TO_INSTANT", "HOLD");
        nothingWasPublished();
    }

    /**
     * A version dated before the one it follows is a conflict rather than a bad form: everything
     * typed is a perfectly good answer, and what refuses it is the history the product already has.
     *
     * <p>The sentence names both days, because the fix is either to move this one forward or to go
     * and look at what somebody else published.
     */
    @Test
    void a_version_dated_before_the_one_it_follows_is_a_conflict() {
        TermsVersionView theOneBefore = last(bank.versionsOf(THE_PRODUCT));
        Map<String, Object> form = theSameTermsAgain(theOneBefore,
                theOneBefore.effectiveFrom().minusDays(1),
                "Dated the day before the version this follows, which reads backwards.");

        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, form);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .contains(theOneBefore.effectiveFrom().toString(),
                        theOneBefore.effectiveFrom().minusDays(1).toString());
        nothingWasPublished();
    }

    /**
     * A body that arrived empty is a fact about the request rather than a rule about agreements,
     * and it is answered in this application's words rather than in Spring's — the shape the offers
     * and the bills already use.
     */
    @Test
    void a_publish_with_nothing_in_the_body_is_refused_in_words() {
        ResponseEntity<JsonNode> refused = bank.tryToPublishNothingAtAll(THE_PRODUCT);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("what changed");
        nothingWasPublished();
    }

    /** And a form with every box empty is refused by the module, naming the first thing missing. */
    @Test
    void a_form_with_every_box_empty_is_refused_by_name() {
        ResponseEntity<JsonNode> refused = bank.tryToPublish(THE_PRODUCT, Map.of());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("day it takes effect");
        nothingWasPublished();
    }

    /**
     * A form filled in correctly in every box, effective today, which each test then breaks in one
     * place.
     *
     * <p>Built from the version the product is actually offering rather than typed out, so that a
     * test asserting a refusal is asserting about the one field it changed and not about a form
     * that was never any good.
     */
    private static Map<String, Object> aPerfectlyGoodForm() {
        LocalDate today = bank.theDateTheClockReads();
        return theSameTermsAgain(last(bank.versionsOf(THE_PRODUCT)), today,
                "A rate change that will never be written, because every test here breaks it.");
    }

    /**
     * Nothing was written: the product still has exactly the versions it was seeded with, and the
     * one on offer is still the one that was on offer.
     *
     * <p>The second half of every test in this file, in one place, so that no test can be written
     * without it. A refusal that answered correctly and wrote the row anyway would be the worst bug
     * this feature could have.
     */
    private static void nothingWasPublished() {
        List<TermsVersionView> history = bank.versionsOf(THE_PRODUCT);
        assertThat(history)
                .as("the versions %s has published, after a refusal", THE_PRODUCT)
                .hasSize(1)
                .allSatisfy(only -> assertThat(only.whatChanged()).isNull());
        assertThat(bank.product(THE_PRODUCT).currentTerms())
                .as("the terms %s is offering, after a refusal", THE_PRODUCT)
                .isEqualTo(history.get(0));
    }

    private static TermsVersionView last(List<TermsVersionView> history) {
        assertThat(history).as("a product's published versions").isNotEmpty();
        return history.get(history.size() - 1);
    }
}
