package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingsProductView;
import io.dataroots.savingstreak.support.TermsVersionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * The bank has something to sell: four savings products, side by side, each saying what it pays and
 * what it asks for.
 *
 * <p>Read over HTTP off the shared application, because that is the seam this codebase tests at and
 * because the catalogue is the same answer for everybody — there is no customer to arrange, no
 * clock to move, and nothing this test writes. Every other test class in this run is reading the
 * same four rows.
 *
 * <p><strong>The figures are asserted to the basis point, and that is deliberate.</strong> A rate
 * is the one thing in this feature that a later slice will multiply a balance by; a catalogue that
 * quietly served 0.06 instead of 0.60 would be a demonstration in which nobody could tell that
 * anything was wrong until a year of interest had been posted. The spec names all seven figures per
 * product, and all seven are checked here against what actually goes over the wire.
 */
class TheCatalogueOfSavingsProductsApiTest extends ApiIntegrationTest {

    private List<SavingsProductView> shelf;

    @BeforeEach
    void readTheShelf() {
        shelf = List.of(http.getForObject("/api/savings-products", SavingsProductView[].class));
    }

    /**
     * Four products, in the order somebody chose: from the account that asks nothing of you to the
     * one that asks for a year.
     *
     * <p>Asserted as the whole list in order rather than as four contains-checks, because a seed
     * that wrote a fifth row or wrote one of them twice would still have the right four somewhere
     * in it — and the order is the thing a comparison screen is drawn from.
     */
    @Test
    void the_bank_sells_four_savings_products_in_the_order_somebody_chose() {
        assertThat(shelf)
                .extracting(SavingsProductView::code, SavingsProductView::kind,
                        SavingsProductView::sortOrder)
                .containsExactly(
                        tuple("INSTANT", "INSTANT_ACCESS", 1),
                        tuple("NOTICE32", "NOTICE", 2),
                        tuple("CORE", "MINIMUM_BALANCE", 3),
                        tuple("FIXED12", "FIXED_TERM", 4));
    }

    /** Every one of them has a name and a sentence, because a card with neither is not an offer. */
    @Test
    void every_product_says_what_it_is_called_and_explains_itself() {
        assertThat(shelf).allSatisfy(product -> {
            assertThat(product.name()).isNotBlank();
            assertThat(product.description()).isNotBlank();
        });
    }

    /**
     * What each one pays and what each one asks for, to the figure: the rate, the bonus, the notice
     * period, the term, the floor and the price of leaving early.
     *
     * <p>Free savings reads 0.50% rather than the 0.60% the bank opened with, because it has
     * published a second version and this is the catalogue of what is on offer <em>today</em>. That
     * one figure is the whole point of the feature and it is asserted here rather than only in the
     * version history, because the card is where a customer would see it.
     */
    @Test
    void each_product_says_what_it_pays_and_what_it_asks_for() {
        assertThat(shelf)
                .extracting(SavingsProductView::code,
                        product -> product.currentTerms().annualRatePercent(),
                        product -> product.currentTerms().bonusRatePercent(),
                        product -> product.currentTerms().noticeDays(),
                        product -> product.currentTerms().termMonths(),
                        product -> product.currentTerms().minimumBalance(),
                        product -> product.currentTerms().earlyExitPenaltyDays())
                .containsExactly(
                        tuple("INSTANT", figure("0.50"), figure("0.00"), 0, 0, figure("0.00"), 0),
                        tuple("NOTICE32", figure("1.60"), figure("0.00"), 32, 0, figure("0.00"), 0),
                        tuple("CORE", figure("0.80"), figure("0.70"), 0, 0, figure("500.00"), 0),
                        tuple("FIXED12", figure("2.40"), figure("0.00"), 0, 12, figure("0.00"), 90));
    }

    /**
     * The two loyalty benefits each product competes on: what a euro saved there is worth in points,
     * and what an anniversary pays on money left sitting in it.
     *
     * <p>The multiplier is a multiple of one rather than a percentage, which is the one figure in
     * this record read on a different scale from the rest — and free savings and the core saver
     * carry exactly the multiple of one and the tenth that every deposit in this application has
     * always been paid, so nothing about what those customers earn changes because these rows
     * exist.
     */
    @Test
    void each_product_says_what_it_pays_in_points_and_what_an_anniversary_is_worth_on_it() {
        assertThat(shelf)
                .extracting(SavingsProductView::code,
                        product -> product.currentTerms().pointsMultiplier(),
                        product -> product.currentTerms().anniversaryRatePercent())
                .containsExactly(
                        tuple("INSTANT", figure("1.0000"), figure("10.00")),
                        tuple("NOTICE32", figure("1.1000"), figure("12.00")),
                        tuple("CORE", figure("1.0000"), figure("10.00")),
                        tuple("FIXED12", figure("1.2500"), figure("15.00")));
    }

    /**
     * What happens at the end is part of what was agreed at the beginning, so every set of terms
     * says it — including the three products that have no term to reach one, where holding is the
     * reading that does nothing.
     */
    @Test
    void only_the_fixed_term_has_an_ending_to_settle() {
        assertThat(shelf)
                .extracting(SavingsProductView::code,
                        product -> product.currentTerms().maturityAction())
                .containsExactly(
                        tuple("INSTANT", "HOLD"),
                        tuple("NOTICE32", "HOLD"),
                        tuple("CORE", "HOLD"),
                        tuple("FIXED12", "ROLL_OVER"));
    }

    /** All four are on sale, which is what the seed writes and what a restart leaves alone. */
    @Test
    void every_seeded_product_is_open_to_new_accounts() {
        assertThat(shelf).allMatch(SavingsProductView::openToNewAccounts);
    }

    /**
     * Free savings is offering version 2, and every other product is still on its first — so the
     * catalogue already carries the difference this feature is built to make visible.
     */
    @Test
    void free_savings_is_offering_the_second_version_it_published() {
        assertThat(shelf)
                .extracting(SavingsProductView::code,
                        product -> product.currentTerms().version())
                .containsExactly(
                        tuple("INSTANT", 2),
                        tuple("NOTICE32", 1),
                        tuple("CORE", 1),
                        tuple("FIXED12", 1));
    }

    /**
     * Every set of terms names the product it belongs to, because "version 2" is not an address:
     * free savings has one and so does the notice account.
     */
    @Test
    void every_set_of_terms_names_the_product_it_belongs_to() {
        assertThat(shelf).allSatisfy(product ->
                assertThat(product.currentTerms().productCode()).isEqualTo(product.code()));
    }

    /** One product on its own reads exactly as it reads on the shelf. */
    @Test
    void one_product_asked_for_by_code_reads_as_it_does_on_the_shelf() {
        SavingsProductView core =
                http.getForObject("/api/savings-products/{code}", SavingsProductView.class, "CORE");

        assertThat(core).isEqualTo(
                shelf.stream().filter(product -> product.code().equals("CORE")).findFirst()
                        .orElseThrow());
        assertThat(core.currentTerms())
                .extracting(TermsVersionView::annualRatePercent, TermsVersionView::bonusRatePercent)
                .containsExactly(figure("0.80"), figure("0.70"));
    }

    /**
     * A figure written the way the API sends it, so that a comparison is about the value rather
     * than about how many places it happens to carry.
     */
    private static BigDecimal figure(String amount) {
        return new BigDecimal(amount);
    }
}
