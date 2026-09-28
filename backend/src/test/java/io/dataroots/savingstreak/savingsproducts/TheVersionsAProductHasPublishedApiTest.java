package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.SavingsProductView;
import io.dataroots.savingstreak.support.TermsVersionView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * A product's terms are versioned, and the versions read as a history: what it said at the start,
 * what it says now, and one line saying what changed between them.
 *
 * <p><strong>This is the test the whole feature is visible through.</strong> Free savings has
 * published twice — 0.60% when the bank opened and 0.50% two months ago — so "what this product
 * pays" is already a different sentence from "what it paid when you opened your account", before a
 * single account has been put on a product. Everything the later tickets build on top of that
 * difference is being asserted here for the first time.
 *
 * <p>Read over HTTP off the shared application. Nothing here moves the clock or writes anything:
 * the history is the same answer for everybody, and it is the same answer tomorrow.
 */
class TheVersionsAProductHasPublishedApiTest extends ApiIntegrationTest {

    /**
     * Free savings has published two versions, oldest first, and the second pays less than the
     * first.
     *
     * <p>Oldest first because this reads as a story rather than as a feed: what the product said at
     * the start, and what each change did to it.
     */
    @Test
    void free_savings_has_published_two_versions_oldest_first() {
        List<TermsVersionView> history = versionsOf("INSTANT");

        assertThat(history)
                .extracting(TermsVersionView::version, TermsVersionView::annualRatePercent)
                .containsExactly(
                        tuple(1, new BigDecimal("0.60")),
                        tuple(2, new BigDecimal("0.50")));
    }

    /**
     * The second version took effect after the first, and both took effect in the past — which is
     * what makes the repricing a thing that happened rather than a thing being announced today,
     * and what makes version 2 the one on offer.
     *
     * <p>The day is read off the application's own clock rather than the machine's, because this
     * suite shares one application whose clock other tests wind forward. A version dated ahead of
     * that day would be published and not yet sold, and the catalogue would still be offering the
     * first — so "both are in the past" is the premise the test above rests on rather than a
     * restatement of it.
     */
    @Test
    void the_second_version_took_effect_after_the_first_and_both_are_in_the_past() {
        List<TermsVersionView> history = versionsOf("INSTANT");
        LocalDate today = LocalDate.ofInstant(
                http.getForObject("/api/dev/clock", ClockView.class).now(), ZoneOffset.UTC);

        assertThat(history.get(1).effectiveFrom()).isAfter(history.get(0).effectiveFrom());
        assertThat(history).allSatisfy(version ->
                assertThat(version.effectiveFrom()).isBeforeOrEqualTo(today));
        assertThat(theTermsOnOfferFor("INSTANT").effectiveFrom())
                .isEqualTo(history.get(1).effectiveFrom());
    }

    /**
     * The second version says what changed and why, in a sentence rather than a diff — because a
     * customer deciding whether to move to it is reading an explanation.
     *
     * <p>It quotes both rates, which is the part they can act on: the figure they are on and the
     * figure they would be on.
     */
    @Test
    void the_second_version_says_what_changed_in_words() {
        TermsVersionView repricing = versionsOf("INSTANT").get(1);

        assertThat(repricing.whatChanged()).isNotBlank();
        assertThat(repricing.whatChanged()).contains("0.60%", "0.50%");
    }

    /**
     * A first version says nothing changed, because nothing did. That is what a first version is,
     * and a sentence there would be the backend inventing one.
     */
    @Test
    void a_first_version_says_nothing_changed_because_nothing_did() {
        assertThat(versionsOf("INSTANT").get(0).whatChanged()).isNull();
        assertThat(versionsOf("NOTICE32")).singleElement()
                .satisfies(only -> assertThat(only.whatChanged()).isNull());
    }

    /** The other three products have published once, which is why their cards read as they always did. */
    @Test
    void the_other_three_products_have_published_one_version_each() {
        assertThat(versionsOf("NOTICE32")).extracting(TermsVersionView::version)
                .containsExactly(1);
        assertThat(versionsOf("CORE")).extracting(TermsVersionView::version).containsExactly(1);
        assertThat(versionsOf("FIXED12")).extracting(TermsVersionView::version).containsExactly(1);
    }

    /**
     * The version a product is offering today is the last one it has published — asserted across
     * the two endpoints rather than inside one, because the catalogue and the history are two reads
     * that must not be able to disagree about which agreement is current.
     */
    @Test
    void the_terms_on_offer_are_the_last_version_the_product_published() {
        for (String code : List.of("INSTANT", "NOTICE32", "CORE", "FIXED12")) {
            List<TermsVersionView> history = versionsOf(code);

            // By every figure rather than by identity. The one field that differs by design is the
            // list of sentences saying what the version changed about the one before it: a history
            // entry carries them and a card does not, because a card is what the bank is selling
            // rather than a comparison with anything.
            assertThat(theTermsOnOfferFor(code))
                    .as("the terms %s is offering today", code)
                    .usingRecursiveComparison()
                    .ignoringFields("whatIsDifferent")
                    .isEqualTo(history.get(history.size() - 1));
        }
    }

    /**
     * Every version carries every figure the agreement is made of, including the ones that are
     * nothing — because zero is the absence of the rule and a page renders one rule for all six
     * absences.
     */
    @Test
    void every_version_carries_every_figure_the_agreement_is_made_of() {
        assertThat(versionsOf("FIXED12")).singleElement().satisfies(fixed -> {
            assertThat(fixed.productCode()).isEqualTo("FIXED12");
            assertThat(fixed.annualRatePercent()).isEqualByComparingTo("2.40");
            assertThat(fixed.bonusRatePercent()).isEqualByComparingTo("0.00");
            assertThat(fixed.noticeDays()).isZero();
            assertThat(fixed.termMonths()).isEqualTo(12);
            assertThat(fixed.minimumBalance()).isEqualByComparingTo("0.00");
            assertThat(fixed.earlyExitPenaltyDays()).isEqualTo(90);
            assertThat(fixed.pointsMultiplier()).isEqualByComparingTo("1.25");
            assertThat(fixed.anniversaryRatePercent()).isEqualByComparingTo("15.00");
            assertThat(fixed.maturityAction()).isEqualTo("ROLL_OVER");
        });
    }

    private List<TermsVersionView> versionsOf(String code) {
        return List.of(http.getForObject("/api/savings-products/{code}/versions",
                TermsVersionView[].class, code));
    }

    private TermsVersionView theTermsOnOfferFor(String code) {
        return http.getForObject("/api/savings-products/{code}", SavingsProductView.class, code)
                .currentTerms();
    }
}
