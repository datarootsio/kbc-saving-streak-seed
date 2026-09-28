package io.dataroots.savingstreak.savingsproducts;

import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingsAccountOnTheOverviewView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The decision a real saver makes first: a customer picks a product off the catalogue and opens a
 * savings account on it, pinned to the version of that product's terms the bank is selling that
 * day — and they may hold several of them, on several products, at once.
 *
 * <p><strong>The version is asked of the catalogue rather than written down here.</strong> Free
 * savings was repriced two months before the seed was written, so an account opened this morning is
 * on version 2 of free savings and on version 1 of the other three; a test that said "2" would be
 * asserting what the seed happens to contain instead of asserting the rule, which is that an account
 * is written under the terms that were on offer on the day it was opened. The day the rate on a
 * product changes, this test goes on passing and goes on meaning the same thing.
 *
 * <p><strong>A customer of this test's own.</strong> How many savings accounts somebody holds is
 * exactly what this test changes, and the seeded pair are counted by a hundred other classes.
 *
 * <p>The shared application and the shared database, because nothing here winds a clock, publishes
 * a version or closes anything to new accounts.
 */
class OpeningASavingsAccountOnTheProductYouChooseApiTest extends ApiIntegrationTest {

    /**
     * The whole of the first criterion: an account is opened on a named product and is on that
     * product, under the version being sold today.
     *
     * <p>Asserted twice over — once on what came back from the press, and once on the account's own
     * page afterwards — because those are two code paths and a button that answered correctly while
     * writing something else would pass a test that only looked at one of them.
     */
    @Test
    void a_customer_opens_a_savings_account_on_the_product_they_chose() {
        ASaverChoosingAProduct saver = aSaver("somebody choosing a notice account");
        LocalDate beforeTheyOpenedIt = saver.theDayTheApplicationIsStandingOn();

        SavingsAccountOnTheOverviewView opened = saver.open("NOTICE32");

        LocalDate afterTheyOpenedIt = saver.theDayTheApplicationIsStandingOn();
        assertThat(opened.id()).isNotNull();
        assertThat(opened.productCode()).isEqualTo("NOTICE32");
        assertThat(opened.productName()).isEqualTo("32-day notice");
        assertThat(opened.moneyBalance()).isEqualByComparingTo("0.00");
        assertThat(opened.closedOn()).as("a freshly opened account is not closed").isNull();

        AnAgreementView agreement = saver.agreementOn(opened.id());
        assertThat(agreement.productCode()).isEqualTo("NOTICE32");
        assertThat(agreement.productKind()).isEqualTo("NOTICE");
        assertThat(agreement.version()).isEqualTo(saver.whatIsBeingSoldToday("NOTICE32"));
        // A pair of readings rather than one, because a test that opened an account a millisecond
        // before midnight would otherwise fail for the one reason nobody could reproduce.
        assertThat(agreement.openedOn()).isIn(beforeTheyOpenedIt, afterTheyOpenedIt);
    }

    /**
     * The account is pinned to the version on offer <em>that day</em>, which for free savings is the
     * second one and not the first.
     *
     * <p>This is the assertion that separates "on a product" from "on an agreement". Free savings
     * has published two versions, and an account opened this morning has to be on the one the bank
     * is advertising this morning — putting it on version 1 would be writing somebody an agreement
     * the bank stopped offering two months ago, and it is the failure that would be invisible until
     * the day the rate mattered.
     */
    @Test
    void the_account_is_pinned_to_the_version_that_product_is_selling_today() {
        ASaverChoosingAProduct saver = aSaver("somebody opening on free savings");

        SavingsAccountOnTheOverviewView opened = saver.open("INSTANT");

        int beingSoldToday = saver.whatIsBeingSoldToday("INSTANT");
        assertThat(beingSoldToday).as("free savings was repriced before the seed was written")
                .isGreaterThan(1);
        assertThat(saver.agreementOn(opened.id()).version()).isEqualTo(beingSoldToday);
    }

    /**
     * Several accounts, on several products, held at once — and each one reads its own agreement.
     *
     * <p>The point of the whole catalogue: money that might be needed next week beside money that is
     * not being touched for a year. The three conditions are asserted as well as the three names,
     * because "which of these can I take money out of today" is the question the list exists to
     * answer, and a card that named four products while reading one product's condition would look
     * right and be useless.
     */
    @Test
    void a_customer_may_hold_several_savings_accounts_on_different_products_at_once() {
        ASaverChoosingAProduct saver = aSaver("somebody holding four agreements");

        long instant = saver.open("INSTANT").id();
        long notice = saver.open("NOTICE32").id();
        long core = saver.open("CORE").id();
        long fixed = saver.open("FIXED12").id();

        List<SavingsAccountOnTheOverviewView> held = saver.savingsAccounts();
        assertThat(held).extracting(SavingsAccountOnTheOverviewView::id)
                .contains(instant, notice, core, fixed);
        assertThat(held).extracting(SavingsAccountOnTheOverviewView::productCode)
                .contains("INSTANT", "NOTICE32", "CORE", "FIXED12");

        assertThat(saver.agreementOn(instant).noticeDays()).isZero();
        assertThat(saver.agreementOn(notice).noticeDays()).isEqualTo(32);
        assertThat(saver.agreementOn(core).minimumBalance()).isEqualByComparingTo("500.00");
        assertThat(saver.agreementOn(fixed).maturesOn())
                .isEqualTo(saver.agreementOn(fixed).openedOn().plusMonths(12));
    }

    /**
     * Opening one leaves every account the customer already held exactly as it was.
     *
     * <p>The quiet half of the feature. A second agreement must not touch the first: not its
     * product, not its version, not the day it began. Nothing in the implementation goes near the
     * other rows, which is precisely why it is worth a test — a rule nothing enforces is a rule the
     * next slice can break without noticing.
     */
    @Test
    void opening_another_account_leaves_the_ones_already_held_exactly_as_they_were() {
        ASaverChoosingAProduct saver = aSaver("somebody adding a second account");
        long first = saver.theAccountTheyWereOpenedWith();
        AnAgreementView before = saver.agreementOn(first);

        saver.open("FIXED12");

        assertThat(saver.agreementOn(first)).isEqualTo(before);
    }

    /**
     * The account they were opened with is on free savings, which nobody chose.
     *
     * <p>Asserted here rather than taken on trust, because the door this ticket builds is a second
     * way an account comes into existence and the first one must go on behaving as it always did: a
     * new customer gets an instant-access account, and being able to choose does not mean being
     * asked to.
     */
    @Test
    void the_account_a_new_customer_is_opened_with_is_still_on_free_savings() {
        ASaverChoosingAProduct saver = aSaver("somebody who chose nothing at all");

        AnAgreementView agreement = saver.agreementOn(saver.theAccountTheyWereOpenedWith());

        assertThat(agreement.productCode()).isEqualTo("INSTANT");
        assertThat(agreement.version()).isEqualTo(saver.whatIsBeingSoldToday("INSTANT"));
    }

    /**
     * A freshly opened account holds nothing and has earned nothing, and the customer's points are
     * exactly where they were.
     *
     * <p>Opening an account is not saving. It moves no money, secures no week and earns no point,
     * and the one figure that could have been quietly wrong here is the customer's points balance —
     * which belongs to the person rather than to any account, so a second account must not look like
     * a second pot of them.
     */
    @Test
    void opening_an_account_moves_no_money_and_earns_nothing() {
        ASaverChoosingAProduct saver = aSaver("somebody opening an empty account");
        long pointsBefore = saver.account(saver.theAccountTheyWereOpenedWith()).pointsBalance();

        SavingsAccountOnTheOverviewView opened = saver.open("CORE");

        assertThat(saver.account(opened.id()).moneyBalance()).isEqualByComparingTo("0.00");
        assertThat(saver.account(opened.id()).pointsBalance()).isEqualTo(pointsBefore);
    }

    private ASaverChoosingAProduct aSaver(String whoTheyAre) {
        return new ASaverChoosingAProduct(http, whoTheyAre);
    }
}
