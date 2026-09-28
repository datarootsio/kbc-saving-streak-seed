package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.NotificationView;
import io.dataroots.savingstreak.support.TermsVersionView;
import io.dataroots.savingstreak.support.TheNewerTermsView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A product that publishes a better rate than the one an account is living under says so, once per
 * version; a product that publishes a worse one says nothing at all.
 *
 * <p><strong>The silence is the half that matters, and it is the seed's own case.</strong> Free
 * savings' second version <em>cut</em> the rate from 0.60% to 0.50% two months before this
 * application was written, and that cut is why the ticket exists in this shape: an application that
 * announced every newer version would have been advertising a worse agreement to everybody it had
 * ever repriced. The first test below is that cut, played through the administration door onto a
 * live account — the two rates it uses are read off the seeded history rather than written down
 * here, so it is the very move the catalogue already made and not a lookalike. It has to be played
 * rather than read off a seeded account because every account this application opens is opened on
 * the version being sold that morning: an account still on version 1 only exists on a database that
 * predates the catalogue, and that database is the migration test's subject rather than this one's.
 *
 * <p><strong>Where the judgement lives, and why it is not in the comparison.</strong> The sentences
 * a notification carries are the backend's own, worded by the one function in this application that
 * puts a difference between two agreements into words — the same function a product's version
 * history prints. That function says the rate goes from 0.60% a year to 0.50% a year and never says
 * whether that is better, deliberately, because a comparison with an opinion in it is how "nothing
 * adopts newer terms on your behalf" quietly becomes "and we decided this one was an improvement".
 * The opinion is Notifications' own and is taken on one figure: the headline annual rate went up.
 * These tests assert both halves — that the quoted sentences are the comparison's own wording, and
 * that whether anything is said at all is decided by the two rates and by nothing in those words.
 *
 * <p><strong>Its own application, because publishing cannot be undone.</strong> A version published
 * into the shared database would stay there for every test afterwards, and the two that pin the
 * shelf to four products at four rates would start failing on whatever order the run took. The
 * methods are ordered for the same reason: each of the three stands on the versions the one before
 * it published.
 */
@TestMethodOrder(OrderAnnotation.class)
class APublishedVersionThatBettersAnAccountsTermsIsAnnouncedOnceApiTest extends ApiIntegrationTest {

    private static final String THE_NOTIFICATIONS_JOB = "raiseNotifications";

    private static final String FREE_SAVINGS = "INSTANT";

    private static final String A_PRODUCT_HAS_BETTERED_YOUR_TERMS =
            "A_PRODUCT_HAS_BETTERED_YOUR_TERMS";

    /** The seeded opening rate, which version 2 cut away from. Read off the history, never assumed. */
    private static final int THE_VERSION_THE_BANK_OPENED_ON = 1;

    /** The version that cut it away, two months before this application was written. */
    private static final int THE_VERSION_THE_BANK_CUT_TO = 2;

    /** A rise, well clear of every rate the seed contains, so no assertion can be a coincidence. */
    private static final String A_RATE_THAT_BEATS_ANYTHING_SEEDED = "0.90";

    /** Another rise, published only so that it can be taken before any sweep has seen it. */
    private static final String A_RATE_THAT_BEATS_EVEN_THAT = "1.10";

    /** Nights with the job running after the version was first announced. */
    private static final int NIGHTS_THE_OFFER_GOES_ON_STANDING = 3;

    private static AnApplicationWithAClockToMove app;

    private static String whoseAccountItIs;

    private static long theirFreeSavings;

    private static int theVersionTheyWereOpenedOn;

    @BeforeAll
    static void startAnApplicationOfItsOwnToPublishInto() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-a-version-that-betters-your-terms"));
        // Free savings put back to the rate the bank opened at, and only then a customer opened on
        // it — in that order, because an account is written onto the version being sold the moment
        // it is opened, and this whole file is about an account that is living on the higher rate
        // when the bank publishes the lower one. The same order, and for the same reason, as the
        // notice-account fixture's.
        BigDecimal whatTheBankOpenedAt = rateOf(THE_VERSION_THE_BANK_OPENED_ON);
        republishFreeSavingsAt(whatTheBankOpenedAt.toPlainString(),
                "The opening rate again, so that there is an account living on it.");
        whoseAccountItIs = app.aCustomerOfItsOwn("a product that betters the terms");
        theirFreeSavings = app.savingsAccountOf(whoseAccountItIs);
        theVersionTheyWereOpenedOn = app.theAgreementOf(theirFreeSavings).version();
        assertThat(rateOf(theVersionTheyWereOpenedOn))
                .as("the account is on the rate the bank opened at, which is where the seeded cut "
                        + "started from")
                .isEqualByComparingTo(whatTheBankOpenedAt);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * The rate cut the catalogue already contains, made again over a live account: newer terms on
     * offer, genuinely takeable, and nothing said about them.
     */
    @Test
    @Order(1)
    void a_version_that_cuts_the_rate_is_never_announced() {
        BigDecimal whatTheyAreOn = rateOf(theVersionTheyWereOpenedOn);
        BigDecimal whatTheSeedCutTo = rateOf(THE_VERSION_THE_BANK_CUT_TO);
        assertThat(whatTheSeedCutTo)
                .as("the seeded catalogue really did cut free savings' rate, which is the move this "
                        + "test makes again over an account that is living through it")
                .isLessThan(whatTheyAreOn);

        // Published at the very rate version 2 cut to, from an account sitting on the rate version
        // 1 paid — the same two figures, in the same direction, one version further along.
        TermsVersionView worse = republishFreeSavingsAt(whatTheSeedCutTo.toPlainString(),
                "Rate cut again, and nobody is moved onto it.");
        assertThat(worse.version()).isGreaterThan(theVersionTheyWereOpenedOn);
        assertThat(worse.annualRatePercent()).isLessThan(whatTheyAreOn);

        TheNewerTermsView newer = app.theNewerTermsFor(theirFreeSavings);
        assertThat(newer.newerTermsExist())
                .as("there is genuinely something newer to take, which is what makes the silence a "
                        + "judgement rather than an absence")
                .isTrue();
        assertThat(newer.whatWouldChange())
                .as("and the comparison describes the move without grading it — saying the rate "
                        + "moved is the whole of what it says")
                .anySatisfy(sentence -> assertThat(sentence).contains("rate"));

        app.runJob(THE_NOTIFICATIONS_JOB);

        assertThat(betteredTermsAnnouncedOnTheAccount())
                .as("a worse agreement is offered and nothing is said about it: newer is not "
                        + "better, and nothing in this application decides otherwise on anybody's "
                        + "behalf")
                .isEmpty();
    }

    /**
     * A version that lifts the rate is announced once, with both rates and the comparison's own
     * words.
     */
    @Test
    @Order(2)
    void a_version_that_lifts_the_rate_is_announced_once_with_both_rates_and_the_backends_words() {
        BigDecimal whatTheyAreOn = rateOf(theVersionTheyWereOpenedOn);
        TermsVersionView better = republishFreeSavingsAt(A_RATE_THAT_BEATS_ANYTHING_SEEDED,
                "A better rate for everybody, whether they take these terms or not.");
        assertThat(better.annualRatePercent()).isGreaterThan(whatTheyAreOn);
        List<String> whatTheComparisonSays = app.theNewerTermsFor(theirFreeSavings).whatWouldChange();

        app.runJob(THE_NOTIFICATIONS_JOB);

        List<NotificationView> said = betteredTermsAnnouncedOnTheAccount();
        assertThat(said).hasSize(1);
        NotificationView announced = said.get(0);
        assertThat(announced.savingsAccountId()).isEqualTo(theirFreeSavings);
        assertThat(announced.productCode()).isEqualTo(FREE_SAVINGS);
        assertThat(announced.productName())
                .isEqualTo(app.theSavingsProduct(FREE_SAVINGS).name());
        assertThat(announced.termsVersion())
                .as("it names the version on offer, which is what makes it once-per-version")
                .isEqualTo(better.version());
        assertThat(announced.amount())
                .as("the rate on offer, which is one half of the judgement that was made")
                .isEqualByComparingTo(better.annualRatePercent());
        assertThat(announced.balance())
                .as("and the rate the account is on, which is the other half")
                .isEqualByComparingTo(whatTheyAreOn);
        assertThat(announced.occursOn())
                .as("no day: a published version is not a deadline, which is exactly the promise "
                        + "that nothing moves the account on its holder's behalf")
                .isNull();
        assertThat(announced.whatIsDifferent())
                .as("the sentences are the backend's own, quoted from the one function that words "
                        + "a difference between two agreements — the same words the version "
                        + "history prints")
                .isEqualTo(whatTheComparisonSays);
        assertThat(announced.whatIsDifferent())
                .anySatisfy(sentence -> assertThat(sentence)
                        .contains("rate")
                        .contains(whatTheyAreOn.toPlainString())
                        .contains(better.annualRatePercent().toPlainString()));
        assertThat(announced.whatIsDifferent())
                .as("and not one of them grades the move: better is the reason the notice exists, "
                        + "and it is nowhere in the words")
                .noneSatisfy(sentence -> assertThat(sentence).containsIgnoringCase("better"));

        // A second run on the same night and three more nights with the job running. The offer is
        // still standing on every one of them, and the account is still on the older version.
        app.runJob(THE_NOTIFICATIONS_JOB);
        for (int night = 0; night < NIGHTS_THE_OFFER_GOES_ON_STANDING; night++) {
            app.daysPass(1);
            app.runJob(THE_NOTIFICATIONS_JOB);
        }

        assertThat(betteredTermsAnnouncedOnTheAccount())
                .as("once per version, however many nights the offer stands")
                .hasSize(1);
        assertThat(betteredTermsAnnouncedOnTheAccount().get(0).id()).isEqualTo(announced.id());
        assertThat(app.theAgreementOf(theirFreeSavings).version())
                .as("and nothing moved the account: a notification is a thing to read")
                .isEqualTo(theVersionTheyWereOpenedOn);
    }

    /**
     * An account whose holder has taken the newer terms is not told about them.
     *
     * <p>Staged the hard way round on purpose: the version is published and taken between two runs
     * of the sweep, so the only thing that could stop it being announced is that the account is
     * already on it. Taking a version it had already been told about would have proved the weaker
     * claim — that the record remembers — rather than this one.
     */
    @Test
    @Order(3)
    void an_account_that_has_taken_the_newer_terms_is_not_told_about_them() {
        List<NotificationView> before = betteredTermsAnnouncedOnTheAccount();
        TermsVersionView betterStill = republishFreeSavingsAt(A_RATE_THAT_BEATS_EVEN_THAT,
                "Better again, and this one is taken before anybody is told about it.");

        app.takeTheNewerTerms(theirFreeSavings);
        app.runJob(THE_NOTIFICATIONS_JOB);

        assertThat(app.theAgreementOf(theirFreeSavings).version())
                .as("the button was pressed, so the account is on the version that was on offer")
                .isEqualTo(betterStill.version());
        assertThat(betteredTermsAnnouncedOnTheAccount())
                .as("and nothing is said about terms it is already living under")
                .hasSize(before.size());
        assertThat(betteredTermsAnnouncedOnTheAccount())
                .noneSatisfy(said -> assertThat(said.termsVersion())
                        .isEqualTo(betterStill.version()));
    }

    /** What free savings' version that number pays a year, read off the catalogue's own history. */
    private static BigDecimal rateOf(int version) {
        return app.theVersionOf(FREE_SAVINGS, version).annualRatePercent();
    }

    /**
     * Republishes free savings with one figure changed — the headline rate — and everything else
     * exactly as the version before it said.
     *
     * <p>Through the pre-filled form the support class already keeps, because a version carries
     * nothing over from the one before it and the module refuses an absent figure by name: writing
     * the other ten out here would be ten chances to assert against a shape the API does not have.
     */
    private static TermsVersionView republishFreeSavingsAt(String rate, String whatChanged) {
        List<TermsVersionView> published = app.versionsOfTheSavingsProduct(FREE_SAVINGS);
        Map<String, Object> form = AnApplicationWithAClockToMove.theSameTermsAgain(
                published.get(published.size() - 1), app.theDateTheClockReads(), whatChanged);
        form.put("annualRatePercent", rate);
        return app.publishAVersionOf(FREE_SAVINGS, form);
    }

    /**
     * What has been said to this customer about their product bettering the terms on this account,
     * and nothing else — filtered by reason as well as by account, because one sweep runs every
     * rule over every account.
     */
    private static List<NotificationView> betteredTermsAnnouncedOnTheAccount() {
        return Arrays.stream(app.notificationsOf(whoseAccountItIs))
                .filter(said -> A_PRODUCT_HAS_BETTERED_YOUR_TERMS.equals(said.reason()))
                .filter(said -> said.savingsAccountId() != null
                        && said.savingsAccountId() == theirFreeSavings)
                .toList();
    }
}
