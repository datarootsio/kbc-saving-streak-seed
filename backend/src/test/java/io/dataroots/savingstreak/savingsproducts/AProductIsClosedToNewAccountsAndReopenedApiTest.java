package io.dataroots.savingstreak.savingsproducts;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingsProductView;
import io.dataroots.savingstreak.support.TermsVersionView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.savingsproducts.ACatalogueSomebodyAdministers.theSameTermsAgain;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retiring an offer is a press rather than a release, and it disturbs nothing: a product can be
 * closed to new accounts and put back on sale, and while it is closed it goes on being a product
 * that reads.
 *
 * <p><strong>What "disturbs nothing" can honestly be asserted as, in this branch.</strong> The
 * criterion the ticket writes is about the accounts already on the product, and no account is on a
 * product yet — that is ticket 2. What is asserted instead is everything closing could have touched
 * and must not: the versions the product has published, the terms it is offering, the card it
 * shows, and every other product on the shelf. When accounts arrive, "and the accounts on it carry
 * on" is a line added to this file rather than a rule that was never stated.
 *
 * <p><strong>No ordering, unlike the publishing test</strong>, because closing is the one thing
 * here that can be undone — that is what makes it a flag rather than a lifecycle. Each test works
 * on a product of its own and puts the door back the way it found it, so they are independent in
 * fact rather than by arrangement.
 *
 * <p>An application and a file of its own all the same, because one of these publishes a version in
 * order to say that a closed product may still publish, and a published version cannot be taken
 * back out of a shared database.
 */
class AProductIsClosedToNewAccountsAndReopenedApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-closing");

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
     * The whole of the criterion: a product is closed to new accounts and opened again, and the
     * answer to each press says which it now is.
     *
     * <p>Answered with the product rather than with nothing, because the screen that pressed the
     * button has to redraw the card — and a card redrawn from what came back is a card showing what
     * actually happened rather than what the page hoped it had asked for.
     */
    @Test
    void a_product_can_be_closed_to_new_accounts_and_reopened() {
        assertThat(bank.product("INSTANT").openToNewAccounts())
                .as("every seeded product starts on sale").isTrue();

        assertThat(bank.close("INSTANT").openToNewAccounts()).isFalse();
        assertThat(bank.product("INSTANT").openToNewAccounts()).isFalse();

        assertThat(bank.reopen("INSTANT").openToNewAccounts()).isTrue();
        assertThat(bank.product("INSTANT").openToNewAccounts()).isTrue();
    }

    /**
     * A closed product is still in the catalogue, still carries its terms, and still has its whole
     * history — because a customer holding one has every right to see what they are holding.
     *
     * <p>Hiding it would be the cheaper thing to build and would leave somebody with an account
     * whose product the application would not admit to having.
     */
    @Test
    void a_closed_product_is_still_in_the_catalogue_and_still_reads() {
        SavingsProductView before = bank.product("NOTICE32");
        List<TermsVersionView> historyBefore = bank.versionsOf("NOTICE32");

        SavingsProductView closed = bank.close("NOTICE32");

        assertThat(closed.openToNewAccounts()).isFalse();
        assertThat(closed.currentTerms()).isEqualTo(before.currentTerms());
        assertThat(closed.name()).isEqualTo(before.name());
        assertThat(closed.description()).isEqualTo(before.description());
        assertThat(closed.kind()).isEqualTo(before.kind());
        assertThat(bank.shelf()).extracting(SavingsProductView::code).contains("NOTICE32");
        assertThat(bank.versionsOf("NOTICE32")).isEqualTo(historyBefore);

        bank.reopen("NOTICE32");
    }

    /**
     * Closing a product that is already closed is not refused, and neither is reopening one that is
     * already open.
     *
     * <p>There is no state here to be at the wrong point of — it is one flag with two readings —
     * and the administrator asked for the product to be shut and it is shut. The rewards catalogue
     * refuses a second withdrawal because withdrawing is the end of an offer's life; this can be
     * undone by the next press, which is exactly what makes it a different kind of thing.
     */
    @Test
    void closing_a_product_that_is_already_closed_is_not_refused() {
        bank.close("CORE");

        assertThat(bank.close("CORE").openToNewAccounts()).isFalse();
        assertThat(bank.product("CORE").openToNewAccounts()).isFalse();

        bank.reopen("CORE");

        assertThat(bank.reopen("CORE").openToNewAccounts()).isTrue();
    }

    /**
     * Closing one product leaves every other product and every published version on the shelf
     * exactly as they were.
     *
     * <p>The whole shelf and every product's whole history, read before and after. A press that
     * reached sideways into another product — or that touched a version row on its way past — would
     * be a worse bug than one that failed to close anything at all, and this is the only shape of
     * assertion that would notice.
     */
    @Test
    void closing_one_product_leaves_every_other_product_and_every_version_alone() {
        List<SavingsProductView> shelfBefore = bank.shelf();
        Map<String, List<TermsVersionView>> historiesBefore = everyHistory();

        bank.close("FIXED12");

        assertThat(bank.shelf())
                .filteredOn(product -> !product.code().equals("FIXED12"))
                .isEqualTo(shelfBefore.stream()
                        .filter(product -> !product.code().equals("FIXED12"))
                        .toList());
        assertThat(everyHistory())
                .as("every version of every product, after one product was closed")
                .isEqualTo(historiesBefore);

        bank.reopen("FIXED12");
    }

    /**
     * A closed product may still publish a new version, and publishing does not put it back on
     * sale.
     *
     * <p>Two decisions that read as one and are not. Closing stops the next account; it does not
     * stop the bank saying what it would sell if it reopened, and it emphatically does not stop the
     * bank correcting the terms the customers still on the product compare themselves against. A
     * publish that quietly reopened the door would be the application deciding that a rate change
     * means the product is back — which is nobody's decision but the administrator's, and they have
     * a button for it.
     */
    @Test
    void a_closed_product_can_still_publish_and_publishing_does_not_reopen_it() {
        bank.close("CORE");
        List<TermsVersionView> before = bank.versionsOf("CORE");
        Map<String, Object> form = theSameTermsAgain(before.get(before.size() - 1),
                bank.theDateTheClockReads(),
                "Repriced while closed to new accounts, for the customers still on it.");
        form.put("annualRatePercent", "0.85");

        TermsVersionView published = bank.publish("CORE", form);

        assertThat(published.annualRatePercent()).isEqualByComparingTo("0.85");
        SavingsProductView core = bank.product("CORE");
        assertThat(core.openToNewAccounts())
                .as("whether publishing put the product back on sale").isFalse();
        assertThat(core.currentTerms()).isEqualTo(published);

        bank.reopen("CORE");
    }

    /** Every product's whole history, by code, for comparing a shelf against itself. */
    private static Map<String, List<TermsVersionView>> everyHistory() {
        return bank.shelf().stream()
                .collect(Collectors.toMap(SavingsProductView::code,
                        product -> bank.versionsOf(product.code())));
    }
}
