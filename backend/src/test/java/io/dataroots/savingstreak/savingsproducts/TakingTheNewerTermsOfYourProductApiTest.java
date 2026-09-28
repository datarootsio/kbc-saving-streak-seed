package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.TermsVersionView;
import io.dataroots.savingstreak.support.TheNewerTermsView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer on an older agreement sees exactly what a newer one would change, line by line, and
 * takes it on purpose — or does not, and carries on under the version they have.
 *
 * <p><strong>The load-bearing sentence is that nothing adopts newer terms on anybody's
 * behalf.</strong> A version is published in the middle of this class, a month goes by, money lands
 * in the account, and the account is still on the version it was opened under. Newer is not the same
 * as better, and the catalogue proves it without anybody having to publish anything: free savings'
 * second version <em>cut</em> the rate from 0.60% to 0.50%. An application that moved accounts
 * forward would have moved every one of those customers onto less money.
 *
 * <p><strong>The second is that one function words a difference.</strong> The sentences the
 * account's own page shows about the step from the version it is on to the version on offer are
 * asserted to be the very same strings, in the very same order, that the product's version history
 * shows about the same step. Two functions that agreed today would pass an assertion that the
 * figures match; they cannot pass this one.
 *
 * <p><strong>An application and a database of its own, because this class publishes.</strong>
 * Publishing cannot be undone — that is the promise of the whole catalogue — so a version written
 * into the run's shared database would still be there for the tests that pin the shelf to four
 * products at four rates. {@link AnApplicationWithAClockToMove} is an application on a file nothing
 * has ever been written to, and it already carries the three helpers ticket 09 added for exactly
 * this: reading the versions, publishing one, and filling in the form from the version before it.
 *
 * <p><strong>In order, because it is one story about two accounts.</strong> Nothing newer, then
 * something newer, then a month of the application ignoring it, then the press, then nothing newer
 * again. The clock only moves forward and a version cannot be unpublished, so the order is not a
 * convenience.
 *
 * <p><strong>Not one figure is written down as an expected sentence.</strong> The assertions look
 * for the figures inside the backend's own words rather than for the words themselves, because the
 * wording is the backend's to own and a test that pinned it would fail on a comma. What the test
 * does pin is that both readings produce the same list.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TakingTheNewerTermsOfYourProductApiTest extends ApiIntegrationTest {

    private static final Path DATABASE = aDatabaseFileThatDoesNotExistYet("saving-streak-newer");

    /** Free savings, which every account nobody chose a product for is opened on. */
    private static final String FREE_SAVINGS = "INSTANT";

    /** The twelve-month fixed term, which is the one product that locks an agreement as well. */
    private static final String THE_FIXED_TERM = "FIXED12";

    /** What lands in the account, so that "no money moved" is a statement about a real balance. */
    private static final String WHAT_THE_ACCOUNT_HOLDS = "200.00";

    /** And what is spoken for by a goal, so that "no allocation moved" is about a real one. */
    private static final String WHAT_THE_GOAL_HOLDS = "50.00";

    /** The rate the newer version pays, which is a rise — so that nothing can be read as a cut. */
    private static final String THE_NEWER_RATE = "0.75";

    /** What a euro is worth in points under it, which is a second figure to find in the sentences. */
    private static final String THE_NEWER_MULTIPLIER = "1.2500";

    /** Far enough for a month to have passed without anything being near a maturity. */
    private static final int DAYS_THAT_GO_BY_WITHOUT_ANYBODY_PRESSING_ANYTHING = 40;

    private static AnApplicationWithAClockToMove application;
    private static String whoseAccountItIs;
    private static long theirInstantAccess;
    private static long theirFixedTerm;
    private static int theVersionTheyWereOpenedOn;

    @BeforeAll
    static void openAnAccountOnFreeSavingsAndOneOnAFixedTerm() {
        application = new AnApplicationWithAClockToMove(DATABASE);
        whoseAccountItIs = application.aCustomerOfItsOwn("taking newer terms");
        theirInstantAccess = application.savingsAccountOf(whoseAccountItIs);
        theirFixedTerm = application.openASavingsAccountOn(whoseAccountItIs, THE_FIXED_TERM);
        theVersionTheyWereOpenedOn = application.theAgreementOf(theirInstantAccess).version();
        application.deposit(theirInstantAccess, whoseAccountItIs, WHAT_THE_ACCOUNT_HOLDS);
        long goal = application.openAGoal(theirInstantAccess, "Something to save for", "500.00")
                .id();
        application.allocate(theirInstantAccess, goal, WHAT_THE_GOAL_HOLDS);
    }

    @AfterAll
    static void closeTheApplication() {
        application.close();
    }

    /**
     * An account opened this morning is on the version being sold this morning, is offered nothing,
     * and refuses the press in a sentence saying so.
     *
     * <p>Both halves in one test because they are one fact read two ways: the reading says there is
     * nothing newer and the door says the same thing when somebody presses anyway — which is what a
     * page that has been open since before somebody else took the terms would do.
     */
    @Test
    @Order(1)
    void an_account_already_on_the_version_on_offer_is_offered_nothing_and_refuses_the_taking() {
        TheNewerTermsView newer = application.theNewerTermsFor(theirInstantAccess);

        assertThat(newer.productCode()).isEqualTo(FREE_SAVINGS);
        assertThat(newer.theVersionYouAreOn()).isEqualTo(theVersionTheyWereOpenedOn);
        assertThat(newer.theVersionOnOfferToday()).isEqualTo(theVersionTheyWereOpenedOn);
        assertThat(newer.newerTermsExist()).isFalse();
        assertThat(newer.whatWouldChange()).isEmpty();

        ResponseEntity<JsonNode> refused =
                application.tryToTakeTheNewerTerms(theirInstantAccess);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .contains("already on")
                .contains("version " + theVersionTheyWereOpenedOn)
                .contains("nothing newer");
    }

    /**
     * A version published after the account was opened gives it a difference to read, one sentence
     * per figure that moved.
     *
     * <p>Three figures move and a fourth deliberately does not: the rate, the points multiplier and
     * the notice period change, and the anniversary rate is republished exactly as it was. The
     * sentences are asserted to name the three and to say nothing about the fourth, because "one
     * sentence per figure that moved" is a claim about what is <em>absent</em> from the list as much
     * as about what is in it.
     *
     * <p>A rise rather than a cut, so that no assertion here could be passing because the seed
     * happens to contain a cut two months old.
     */
    @Test
    @Order(2)
    void a_version_published_after_the_account_was_opened_gives_it_a_difference_to_read() {
        aNewVersionOfFreeSavings();

        TheNewerTermsView newer = application.theNewerTermsFor(theirInstantAccess);

        assertThat(newer.newerTermsExist()).isTrue();
        assertThat(newer.theVersionYouAreOn()).isEqualTo(theVersionTheyWereOpenedOn);
        assertThat(newer.theVersionOnOfferToday()).isEqualTo(theVersionTheyWereOpenedOn + 1);
        assertThat(newer.whatWouldChange()).hasSize(3);
        assertThat(newer.whatWouldChange())
                .as("the rate moved, and both figures are in the sentence")
                .anySatisfy(sentence -> assertThat(sentence)
                        .contains("rate")
                        .contains(THE_NEWER_RATE));
        assertThat(newer.whatWouldChange())
                .as("what a euro is worth in points moved")
                .anySatisfy(sentence -> assertThat(sentence)
                        .contains("points")
                        .contains(THE_NEWER_MULTIPLIER));
        assertThat(newer.whatWouldChange())
                .as("the notice period moved, and a page reads it in days")
                .anySatisfy(sentence -> assertThat(sentence)
                        .contains("notice")
                        .contains("32 days"));
        assertThat(newer.whatWouldChange())
                .as("nothing is said about the anniversary rate, which was republished unchanged")
                .noneSatisfy(sentence -> assertThat(sentence).contains("anniversary"));
    }

    /**
     * The product's version history words the same difference, in the same sentences, in the same
     * order.
     *
     * <p><strong>The assertion that says one function does the wording.</strong> The account is on
     * the version before the one just published, so "what taking the newer terms would change" and
     * "what the newest version changed about the one before it" are the same comparison asked from
     * two ends of the application. Equal lists of equal strings is the only assertion that can tell
     * one function from two that agree today: a second implementation would have to get every word,
     * every figure and every position right to pass it.
     *
     * <p>And the first version in the history says nothing at all, because nothing changed — that is
     * what a first version is.
     */
    @Test
    @Order(3)
    void the_version_history_words_the_same_difference_as_the_account_does() {
        List<TermsVersionView> history =
                application.versionsOfTheSavingsProduct(FREE_SAVINGS);
        TermsVersionView theNewest = history.get(history.size() - 1);

        assertThat(theNewest.version()).isEqualTo(theVersionTheyWereOpenedOn + 1);
        assertThat(theNewest.whatIsDifferent())
                .isEqualTo(application.theNewerTermsFor(theirInstantAccess).whatWouldChange());
        assertThat(history.get(0).whatIsDifferent())
                .as("the first version changed nothing, because there was nothing before it")
                .isEmpty();
    }

    /**
     * A month goes by, money lands, and the account is still on the version it was opened under.
     *
     * <p><strong>The sentence the whole feature exists for.</strong> Nothing in this application
     * moves an account onto newer terms: not a night passing, not a deposit landing, not a page being
     * read. The account here has had all three since a newer version was published and it is where it
     * was — and the difference it is being shown is still the same difference.
     */
    @Test
    @Order(4)
    void nothing_moves_the_account_onto_the_newer_terms_on_its_own() {
        application.daysPass(DAYS_THAT_GO_BY_WITHOUT_ANYBODY_PRESSING_ANYTHING);
        application.deposit(theirInstantAccess, whoseAccountItIs, "10.00");

        AnAgreementView agreement = application.theAgreementOf(theirInstantAccess);

        assertThat(agreement.version()).isEqualTo(theVersionTheyWereOpenedOn);
        assertThat(application.theNewerTermsFor(theirInstantAccess).newerTermsExist()).isTrue();
    }

    /**
     * Taking the newer terms pins the version on offer and moves no money, no points and no
     * allocations.
     *
     * <p><strong>Three figures read before and after, because "it is an agreement and not a
     * transaction" is a claim that can only be made about figures that did not move.</strong> The
     * balance is the account's, the points are the holder's and the allocation is the goal's, and a
     * press that touched any of them would be this application charging for a change of terms.
     *
     * <p>And afterwards the offer is empty again: the account is on the version on offer, so there is
     * nothing newer, which is the same reading the first test made of an account that had never
     * moved.
     */
    @Test
    @Order(5)
    void taking_the_newer_terms_pins_the_version_on_offer_and_moves_nothing_else() {
        BigDecimal whatItHeld = application.moneyBalanceOf(theirInstantAccess);
        long whatTheyHadInPoints = application.pointsBalanceOf(whoseAccountItIs);
        BigDecimal whatWasSpokenFor = application.allocationsOn(theirInstantAccess).allocated();
        int theVersionOnOffer =
                application.theNewerTermsFor(theirInstantAccess).theVersionOnOfferToday();

        AnAgreementView nowOn = application.takeTheNewerTerms(theirInstantAccess);

        assertThat(nowOn.version()).isEqualTo(theVersionOnOffer);
        assertThat(nowOn.productCode()).isEqualTo(FREE_SAVINGS);
        assertThat(application.moneyBalanceOf(theirInstantAccess)).isEqualByComparingTo(whatItHeld);
        assertThat(application.pointsBalanceOf(whoseAccountItIs)).isEqualTo(whatTheyHadInPoints);
        assertThat(application.allocationsOn(theirInstantAccess).allocated())
                .isEqualByComparingTo(whatWasSpokenFor);

        TheNewerTermsView afterwards = application.theNewerTermsFor(theirInstantAccess);
        assertThat(afterwards.newerTermsExist()).isFalse();
        assertThat(afterwards.whatWouldChange()).isEmpty();
    }

    /**
     * A fixed term that is still running refuses the newer terms, in a sentence naming the day it
     * matures.
     *
     * <p><strong>That is what being locked in means.</strong> The term already refuses a withdrawal
     * until the day it is up; it refuses a change of the agreement around the money for the same
     * reason, and the refusal names the same morning — a lock that held the money but let the rate,
     * the penalty days and the ending move would be half a lock.
     *
     * <p>The reading is <em>not</em> refused: the account can see perfectly well that its product has
     * published something newer, and the date in the sentence is when it may have it. The refusal is
     * a date rather than a door.
     */
    @Test
    @Order(6)
    void a_fixed_term_that_is_still_running_refuses_the_newer_terms() {
        aNewVersionOfTheFixedTerm();

        TheNewerTermsView newer = application.theNewerTermsFor(theirFixedTerm);
        assertThat(newer.newerTermsExist()).isTrue();
        assertThat(newer.whatWouldChange()).isNotEmpty();

        ResponseEntity<JsonNode> refused = application.tryToTakeTheNewerTerms(theirFixedTerm);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .contains("fixed term")
                .contains("locked")
                .contains(application.theAgreementOf(theirFixedTerm).maturesOn().toString());
        assertThat(application.theAgreementOf(theirFixedTerm).version())
                .as("a refused press changes nothing")
                .isEqualTo(newer.theVersionYouAreOn());
    }

    /**
     * A version of free savings that moves three figures and republishes the rest exactly as they
     * were.
     *
     * <p>Filled in from the version actually on offer rather than written out here, through the
     * helper ticket 09 left for it: a version carries nothing over from the one before it, so every
     * figure has to be sent, and building the form from a version that was served is what stops this
     * test asserting against a shape the API does not have.
     */
    private static void aNewVersionOfFreeSavings() {
        List<TermsVersionView> published =
                application.versionsOfTheSavingsProduct(FREE_SAVINGS);
        Map<String, Object> form = AnApplicationWithAClockToMove.theSameTermsAgain(
                published.get(published.size() - 1), application.theDateTheClockReads(),
                "Rate raised, points multiplied and a month's notice asked for in return.");
        form.put("annualRatePercent", THE_NEWER_RATE);
        form.put("pointsMultiplier", THE_NEWER_MULTIPLIER);
        form.put("noticeDays", "32");
        application.publishAVersionOf(FREE_SAVINGS, form);
    }

    /** The same, for the fixed term, so that the locked account has something newer to refuse. */
    private static void aNewVersionOfTheFixedTerm() {
        List<TermsVersionView> published =
                application.versionsOfTheSavingsProduct(THE_FIXED_TERM);
        Map<String, Object> form = AnApplicationWithAClockToMove.theSameTermsAgain(
                published.get(published.size() - 1), application.theDateTheClockReads(),
                "Rate raised for new fixed terms opened from today.");
        form.put("annualRatePercent", "2.60");
        application.publishAVersionOf(THE_FIXED_TERM, form);
    }

    /** The sentence the domain wrote, carried into the problem detail untouched. */
    private static String reasonGivenBy(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody()).as("the problem detail").isNotNull();
        return response.getBody().path("detail").asText();
    }
}
