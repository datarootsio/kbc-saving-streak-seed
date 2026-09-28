package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingsProductView;
import io.dataroots.savingstreak.support.TermsVersionView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.dataroots.savingstreak.savingsproducts.ACatalogueSomebodyAdministers.theSameTermsAgain;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Changing what the bank offers becomes something somebody does rather than something somebody
 * deploys: an administrator publishes a new version of a product's terms, and the version they
 * published is the version the product is selling.
 *
 * <p><strong>Its own application on its own file</strong>, because publishing cannot be undone —
 * which is the promise of the ticket rather than an inconvenience of testing it.
 * {@link ACatalogueSomebodyAdministers} argues that at length.
 *
 * <p><strong>Ordered, unusually and deliberately</strong>, for the same reason the weeks-ahead test
 * is: nothing here can be put back. Each test publishes into a catalogue the ones before it have
 * already moved, and one of them winds the clock a week on, so the order these run in is part of
 * what they mean. Without it, a test backdating a version would pass or fail depending on whether
 * another test had already published one dated today — which is a true rule being asserted by
 * accident.
 *
 * <p>Each test works on a product of its own where it can, so that the version numbers it counts
 * are its own rather than whatever the class has done so far.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnAdministratorPublishesANewVersionOfATermsApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-publishing");

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
     * The heart of the ticket: a rate is cut without a release, the version is numbered one higher
     * than the last, and the figure the product is selling is the figure that was typed.
     *
     * <p>Free savings is the product, because it is the one that arrives with a history — two
     * versions seeded — so what this publishes is a third, and the number it gets is a fact about
     * counting rather than about a fresh product's only row.
     *
     * <p>Asserted from the publish's own answer <em>and</em> from the catalogue afterwards, because
     * those are two different claims. The first says the version was written as asked; the second
     * says the shelf now sells it, which is the half that would still pass against an application
     * that wrote a row nothing reads.
     */
    @Test
    @Order(1)
    void a_new_version_is_numbered_one_higher_and_becomes_the_terms_on_offer() {
        TermsVersionView theLast = last(bank.versionsOf("INSTANT"));
        Map<String, Object> form = theSameTermsAgain(theLast, bank.theDateTheClockReads(),
                "Rate cut from 0.50% to 0.40% a year as the market moved.");
        form.put("annualRatePercent", "0.40");

        TermsVersionView published = bank.publish("INSTANT", form);

        assertThat(published.version()).isEqualTo(theLast.version() + 1);
        assertThat(published.productCode()).isEqualTo("INSTANT");
        assertThat(published.annualRatePercent()).isEqualByComparingTo("0.40");
        assertThat(published.whatChanged()).contains("0.50%", "0.40%");
        assertThat(bank.product("INSTANT").currentTerms()).isEqualTo(published);
        // Every figure, but not the sentences saying what this version moved about the one before
        // it: those are the history's and the history's alone. A card is an offer and the answer to
        // a publish is a receipt, and neither of them has a predecessor in view — which is exactly
        // why the field is empty on both and filled in here.
        assertThat(last(bank.versionsOf("INSTANT")))
                .usingRecursiveComparison()
                .ignoringFields("whatIsDifferent")
                .isEqualTo(published);
    }

    /**
     * Every number in a set of terms can be set, and what comes back is what was typed in each of
     * them.
     *
     * <p>All nine figures and the ending moved at once, deliberately. A test that changed one would
     * pass against a backend that read one field and carried the rest over from the version before,
     * and carrying anything over is exactly the design this contract rejects: a version is the
     * complete list of numbers, published outright.
     *
     * <p>The 32-day notice account is the product, because it starts with figures in several of the
     * boxes rather than in one, so a version that quietly kept an old value would show up as an old
     * value rather than as a nought.
     */
    @Test
    @Order(2)
    void every_number_in_a_set_of_terms_can_be_set() {
        LocalDate today = bank.theDateTheClockReads();
        Map<String, Object> form = theSameTermsAgain(last(bank.versionsOf("NOTICE32")), today,
                "Repriced and reshaped: a shorter notice period, a floor to keep, a bonus for "
                        + "keeping it, and a better anniversary.");
        form.put("annualRatePercent", "1.75");
        form.put("bonusRatePercent", "0.25");
        form.put("noticeDays", "14");
        form.put("termMonths", "3");
        form.put("minimumBalance", "250.50");
        form.put("earlyExitPenaltyDays", "45");
        form.put("pointsMultiplier", "1.3750");
        form.put("anniversaryRatePercent", "13.50");
        form.put("maturityAction", "MOVE_TO_INSTANT");

        TermsVersionView published = bank.publish("NOTICE32", form);

        assertThat(published.effectiveFrom()).isEqualTo(today);
        assertThat(published.annualRatePercent()).isEqualByComparingTo("1.75");
        assertThat(published.bonusRatePercent()).isEqualByComparingTo("0.25");
        assertThat(published.noticeDays()).isEqualTo(14);
        assertThat(published.termMonths()).isEqualTo(3);
        assertThat(published.minimumBalance()).isEqualByComparingTo("250.50");
        assertThat(published.earlyExitPenaltyDays()).isEqualTo(45);
        assertThat(published.pointsMultiplier()).isEqualByComparingTo("1.3750");
        assertThat(published.anniversaryRatePercent()).isEqualByComparingTo("13.50");
        assertThat(published.maturityAction()).isEqualTo("MOVE_TO_INSTANT");
        assertThat(published.whatChanged()).contains("shorter notice period");
    }

    /**
     * Nought goes in every box that has an absence to say, and comes back as nought rather than as
     * whatever the version before it said.
     *
     * <p>The other half of the test above and the half that actually catches the bug: a backend
     * that read a figure it did not recognise as "leave it alone" would pass everything above and
     * would quietly keep the core saver's floor on a version published to take it away.
     */
    @Test
    @Order(3)
    void nought_is_published_as_nought_in_every_box_that_has_an_absence_to_say() {
        TermsVersionView had = last(bank.versionsOf("CORE"));
        assertThat(had.minimumBalance())
                .as("the floor the core saver started with, which this version removes")
                .isGreaterThan(BigDecimal.ZERO);
        Map<String, Object> form = theSameTermsAgain(had, bank.theDateTheClockReads(),
                "The floor comes off and the bonus with it: one flat rate, nothing to keep.");
        form.put("bonusRatePercent", "0.00");
        form.put("minimumBalance", "0.00");
        form.put("noticeDays", "0");
        form.put("termMonths", "0");
        form.put("earlyExitPenaltyDays", "0");

        TermsVersionView published = bank.publish("CORE", form);

        assertThat(published.bonusRatePercent()).isEqualByComparingTo("0.00");
        assertThat(published.minimumBalance()).isEqualByComparingTo("0.00");
        assertThat(published.noticeDays()).isZero();
        assertThat(published.termMonths()).isZero();
        assertThat(published.earlyExitPenaltyDays()).isZero();
    }

    /**
     * A version effective in the past is accepted, and the product is selling it straight away —
     * because the version on offer is the highest one whose day has come, and this one's day came a
     * month ago.
     *
     * <p>Backdating is an ordinary thing for a bank to do: a rate announced on the first and
     * published on the third has to be able to say the first. Refusing it would leave somebody who
     * published a day late with no way to write down what actually happened.
     */
    @Test
    @Order(4)
    void a_version_effective_in_the_past_is_accepted_and_is_the_one_on_offer() {
        LocalDate aMonthAgo = bank.theDateTheClockReads().minusMonths(1);
        Map<String, Object> form = theSameTermsAgain(bank.product("FIXED12").currentTerms(),
                aMonthAgo, "Backdated to the day the change was actually announced.");
        form.put("annualRatePercent", "2.50");

        TermsVersionView backdated = bank.publish("FIXED12", form);

        assertThat(backdated.effectiveFrom()).isEqualTo(aMonthAgo);
        assertThat(bank.product("FIXED12").currentTerms()).isEqualTo(backdated);
    }

    /**
     * A version dated ahead of today is published and is not being sold yet; the product goes on
     * offering the version it was offering, and starts offering the new one on the morning it takes
     * effect.
     *
     * <p><strong>This is the test that stands in for "publishing changes nothing for an existing
     * account".</strong> No account is attached to a product in this branch — that is ticket 2 — so
     * no account can be asked what rate it is on. What can be asked, and is the same rule
     * underneath, is what the catalogue answers for a day that is not today: the version on offer is
     * the highest one whose day has come, so a version published for next month does not reach back
     * and change what was being sold this morning. When accounts arrive, the account opened this
     * morning names the version this test watches stay current, and the criterion's own sentence
     * becomes a line added to this rather than a new rule.
     *
     * <p>The clock is wound rather than the dates being chosen to have already passed, because the
     * claim is about a day arriving: an application that ignored the effective date entirely would
     * fail the first half of this, and one that never noticed a day arriving would fail the second.
     */
    @Test
    @Order(5)
    void a_version_dated_ahead_of_today_is_published_and_is_not_on_offer_until_its_day_comes() {
        TermsVersionView wasOnOffer = bank.product("FIXED12").currentTerms();
        LocalDate inAWeek = bank.theDateTheClockReads().plusDays(7);
        Map<String, Object> form = theSameTermsAgain(wasOnOffer, inAWeek,
                "Rate rising to 2.75% from the date shown, announced a week ahead.");
        form.put("annualRatePercent", "2.75");

        TermsVersionView announced = bank.publish("FIXED12", form);

        // By every figure rather than by identity, for the reason the first test of this class
        // gives: a version listed in the history carries the sentences saying what it changed, and
        // the answer to the publish that wrote it does not.
        assertThat(bank.versionsOf("FIXED12"))
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("whatIsDifferent")
                .contains(announced);
        assertThat(bank.product("FIXED12").currentTerms())
                .as("what the product is selling while the new version is still ahead of today")
                .isEqualTo(wasOnOffer);

        bank.daysPass(7);

        assertThat(bank.product("FIXED12").currentTerms())
                .as("what the product is selling on the morning the new version takes effect")
                .isEqualTo(announced);
    }

    /**
     * Publishing leaves every version already published exactly as it was, and leaves every
     * product's identity alone.
     *
     * <p><strong>The weakest true statement of "publishing changes nothing for anybody
     * else".</strong> The whole of what a publish may do is add a row. The versions before it are
     * the agreements people are living under, and the products are what their accounts will name,
     * so both are read before and after and compared — including the three products this publish
     * was not about, because a publish that reached sideways into another product would be a worse
     * bug than one that edited its own history.
     *
     * <p>The products are compared on what they are rather than on what they are selling, because
     * what one of them is selling is precisely what this test just changed. Everything else about a
     * product — its code, its name, its shape, its words, its place in the list and whether its
     * door is open — is untouched by a publish and is asserted to be.
     */
    @Test
    @Order(6)
    void publishing_leaves_every_version_already_published_and_every_product_untouched() {
        List<TermsVersionView> historyBefore = bank.versionsOf("INSTANT");
        List<String> whoTheProductsWere = whoEachProductIs(bank.shelf());

        Map<String, Object> form = theSameTermsAgain(last(historyBefore),
                bank.theDateTheClockReads(),
                "A further cut, published over the top of everything already published.");
        form.put("annualRatePercent", "0.30");
        bank.publish("INSTANT", form);

        List<TermsVersionView> historyAfter = bank.versionsOf("INSTANT");
        assertThat(historyAfter).hasSize(historyBefore.size() + 1);
        assertThat(historyAfter.subList(0, historyBefore.size()))
                .as("free savings' already-published versions, after a publish")
                .isEqualTo(historyBefore);
        assertThat(whoEachProductIs(bank.shelf()))
                .as("what each product is, and whether its door is open, after a publish")
                .isEqualTo(whoTheProductsWere);
    }

    /**
     * Two versions taking effect on the same day is a settled question rather than a coin toss: the
     * higher number wins, because it is the one published later.
     *
     * <p>A correction announced and superseded on one morning is a real thing, and the rule that
     * decides it already existed. This is that rule asked through the door that can now create the
     * situation it was written for.
     */
    @Test
    @Order(7)
    void two_versions_on_one_day_are_settled_by_the_higher_version_number() {
        LocalDate today = bank.theDateTheClockReads();
        Map<String, Object> first = theSameTermsAgain(bank.product("CORE").currentTerms(), today,
                "Rate to 0.90%.");
        first.put("annualRatePercent", "0.90");
        TermsVersionView thisMorning = bank.publish("CORE", first);

        Map<String, Object> corrected = theSameTermsAgain(thisMorning, today,
                "Correcting this morning's version: the rate is 0.95%, not 0.90%.");
        corrected.put("annualRatePercent", "0.95");
        TermsVersionView correction = bank.publish("CORE", corrected);

        assertThat(correction.effectiveFrom()).isEqualTo(thisMorning.effectiveFrom());
        assertThat(correction.version()).isEqualTo(thisMorning.version() + 1);
        assertThat(bank.product("CORE").currentTerms()).isEqualTo(correction);
    }

    /** The last version in a history, which is the highest-numbered one, because it is served oldest first. */
    private static TermsVersionView last(List<TermsVersionView> history) {
        assertThat(history).as("a product's published versions").isNotEmpty();
        return history.get(history.size() - 1);
    }

    /**
     * What each product on the shelf <em>is</em>, as one comparable line each — everything about it
     * except the terms it happens to be selling today.
     */
    private static List<String> whoEachProductIs(List<SavingsProductView> shelf) {
        return shelf.stream()
                .map(product -> String.join("|", product.code(), product.name(), product.kind(),
                        product.description(), String.valueOf(product.sortOrder()),
                        String.valueOf(product.openToNewAccounts())))
                .toList();
    }
}
