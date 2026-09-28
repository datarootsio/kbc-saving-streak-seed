package io.dataroots.savingstreak.savingsproducts;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.SavingsProductView;
import io.dataroots.savingstreak.support.TermsVersionView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * The catalogue of savings products is written on the first start and never written over
 * afterwards.
 *
 * <p>Three starts against one file, because that is the only way to ask the question this ticket
 * turns on. A catalogue of agreements is worth nothing if the application puts it back the way it
 * found it every morning: the seed has to be a floor — write what is missing, read nothing, correct
 * nothing — and "nothing changed on the restart" is a statement about two starts rather than about
 * one. It matters more here than it did for the rewards catalogue, because what a restart would be
 * overwriting is the agreement somebody's money is living under.
 *
 * <p>Its own file and its own applications, like the other start-up tests. The run's shared file
 * has had its catalogue seeded by the application the base class starts, and there is no first
 * start left in it to watch.
 *
 * <p>The edits are made with raw JDBC rather than through an API, because there is no API to change
 * a product with — that is a later ticket, and the whole point of this one is that there is no door
 * here. What is being asserted is the start-up behaviour, and a row changed by hand is a row
 * changed by hand however it got that way.
 */
class TheCatalogueOfProductsIsSeededOnceAndNeverResetApiTest extends ApiIntegrationTest {

    private static final Path DATABASE = aDatabaseFileThatDoesNotExistYet("saving-streak-products");

    /** The zone this application counts its days in, which is the zone the seed anchors to. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    /** What somebody running the bank changed by hand, and to what. */
    private static final int THE_NEW_RATE_ON_FREE_SAVINGS = 45;
    private static final String THE_PRODUCT_THEY_CLOSED = "CORE";

    private static WhatAStartSaw onTheFirstStart;
    private static LocalDate theDayOfTheFirstStart;
    private static WhatAStartSaw onTheSecondStart;
    private static WhatAStartSaw afterSomebodyChangedTwoRows;
    private static String whatASecondVersionTwoGot;

    @BeforeAll
    static void startTwiceThenChangeTwoRowsAndStartAgain() {
        onTheFirstStart = aStart();
        theDayOfTheFirstStart = onTheFirstStart.day();
        onTheSecondStart = aStart();
        whatASecondVersionTwoGot = whatHappensWhenAProductPublishesVersionTwoTwice();
        repriceFreeSavingsAndCloseTheCoreSaverByHand();
        afterSomebodyChangedTwoRows = aStart();
    }

    /**
     * A file with nothing in it comes up selling four savings products, in the order somebody chose
     * and at the rates the spec names.
     *
     * <p>The same assertion the catalogue test makes against the shared database, made here against
     * a file the seed has only just written. That is the difference worth having: on a fresh file
     * there is nothing to serve unless this step put it there.
     */
    @Test
    void a_first_start_writes_the_four_products_this_bank_sells() {
        assertThat(onTheFirstStart.shelf())
                .extracting(SavingsProductView::code, SavingsProductView::kind,
                        product -> product.currentTerms().annualRatePercent().toPlainString())
                .containsExactly(
                        tuple("INSTANT", "INSTANT_ACCESS", "0.50"),
                        tuple("NOTICE32", "NOTICE", "1.60"),
                        tuple("CORE", "MINIMUM_BALANCE", "0.80"),
                        tuple("FIXED12", "FIXED_TERM", "2.40"));
    }

    /**
     * And the five agreements they have published between them: one each, and free savings twice.
     *
     * <p>Five rather than four is the whole of what this ticket adds over a catalogue of products,
     * and it is asserted on the first start because that is the only start that could have written
     * it.
     */
    @Test
    void a_first_start_writes_five_published_versions_between_them() {
        assertThat(onTheFirstStart.freeSavings())
                .extracting(TermsVersionView::version,
                        version -> version.annualRatePercent().toPlainString())
                .containsExactly(tuple(1, "0.60"), tuple(2, "0.50"));
        assertThat(onTheFirstStart.versionsAltogether()).isEqualTo(5);
    }

    /**
     * Free savings was repriced two months before the day the seed ran, and the bank published its
     * opening terms a year before that day — so the history reads forwards and a product's first
     * version is older than any account anybody could have opened.
     *
     * <p>The day is the application's own, read off its clock, rather than the machine's: a fresh
     * file has no clock offset in it, so the two agree, and asking the application is what makes
     * this assertion about the day the seed actually anchored to rather than about the day this
     * test happened to be compiled on.
     */
    @Test
    void the_effective_dates_are_anchored_to_the_day_the_seed_first_ran() {
        assertThat(onTheFirstStart.freeSavings().get(0).effectiveFrom())
                .isEqualTo(theDayOfTheFirstStart.minusMonths(12));
        assertThat(onTheFirstStart.freeSavings().get(1).effectiveFrom())
                .isEqualTo(theDayOfTheFirstStart.minusMonths(2));
    }

    /**
     * The second start finds them and does nothing at all — not a fifth product, not a sixth
     * version, not a figure changed. Asserted as the whole reading against the whole reading,
     * because a seed that wrote a duplicate would still have the right rows somewhere in it.
     */
    @Test
    void a_restart_finds_them_and_changes_nothing() {
        assertThat(onTheSecondStart.shelf()).containsExactlyElementsOf(onTheFirstStart.shelf());
        assertThat(onTheSecondStart.freeSavings())
                .containsExactlyElementsOf(onTheFirstStart.freeSavings());
        assertThat(onTheSecondStart.versionsAltogether())
                .isEqualTo(onTheFirstStart.versionsAltogether());
    }

    /**
     * The promise the whole administration surface will rest on: what somebody running the bank
     * changed is still changed after the application has been up and down again.
     *
     * <p>A seed that corrected the row it found would make every published version last until the
     * next restart, which for an agreement somebody's money is living under is worse than not being
     * able to publish at all.
     */
    @Test
    void a_version_somebody_changed_survives_the_next_start() {
        assertThat(afterSomebodyChangedTwoRows.freeSavings().get(1).annualRatePercent())
                .isEqualByComparingTo("0.45");
        assertThat(afterSomebodyChangedTwoRows.freeSavings())
                .extracting(TermsVersionView::version).containsExactly(1, 2);
        assertThat(afterSomebodyChangedTwoRows.versionsAltogether()).isEqualTo(5);
    }

    /**
     * A product closed to new accounts still reads, marked as closed — it has not gone away, and
     * everything else about it is exactly as it was.
     *
     * <p>Closing is how a product is retired, because nothing here deletes one: an account will
     * point at it and every interest posting will point at one of its versions. Hiding it from the
     * catalogue would leave a customer holding an account whose product the application would not
     * admit to having.
     */
    @Test
    void a_product_closed_to_new_accounts_still_reads_and_says_it_is_closed() {
        assertThat(afterSomebodyChangedTwoRows.shelf())
                .extracting(SavingsProductView::code, SavingsProductView::openToNewAccounts)
                .containsExactly(
                        tuple("INSTANT", true),
                        tuple("NOTICE32", true),
                        tuple(THE_PRODUCT_THEY_CLOSED, false),
                        tuple("FIXED12", true));

        SavingsProductView closed = afterSomebodyChangedTwoRows.shelf().stream()
                .filter(product -> product.code().equals(THE_PRODUCT_THEY_CLOSED))
                .findFirst().orElseThrow();
        SavingsProductView asItWas = onTheFirstStart.shelf().stream()
                .filter(product -> product.code().equals(THE_PRODUCT_THEY_CLOSED))
                .findFirst().orElseThrow();
        assertThat(closed.currentTerms()).isEqualTo(asItWas.currentTerms());
        assertThat(closed.name()).isEqualTo(asItWas.name());
        assertThat(closed.description()).isEqualTo(asItWas.description());
    }

    /**
     * And a restart does not re-open it. Seeding is a floor: a product already carrying the code is
     * left alone, untouched and unread, whatever it now says about itself.
     */
    @Test
    void a_restart_does_not_re_open_a_product_somebody_closed() {
        assertThat(afterSomebodyChangedTwoRows.shelf())
                .filteredOn(product -> product.code().equals(THE_PRODUCT_THEY_CLOSED))
                .singleElement()
                .satisfies(product -> assertThat(product.openToNewAccounts()).isFalse());
    }

    /**
     * One version per product is a rule the database keeps rather than one this application merely
     * intends: a second row claiming to be free savings version 2 is rejected outright.
     *
     * <p>Asserted by trying it, with no application up, because the guarantee is an index and an
     * index is only worth having if it actually refuses something. Two rows under one version would
     * be two answers to "what was I opened under", and the account pointing at one of them could
     * not say which.
     */
    @Test
    void a_product_cannot_publish_the_same_version_twice() {
        assertThat(whatASecondVersionTwoGot)
                .as("what the database said about a second free savings version 2")
                .isNotNull()
                .containsIgnoringCase("unique");
    }

    /** One start, one reading of the catalogue and of the history, and the application stopped again. */
    private static WhatAStartSaw aStart() {
        try (ConfigurableApplicationContext application = startAnApplicationAgainstTheFile()) {
            TestRestTemplate http = new TestRestTemplate();
            http.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                    + application.getEnvironment().getProperty("local.server.port")));
            List<SavingsProductView> shelf =
                    List.of(http.getForObject("/api/savings-products", SavingsProductView[].class));
            List<TermsVersionView> freeSavings = versionsOf(http, "INSTANT");
            int altogether = shelf.stream()
                    .mapToInt(product -> versionsOf(http, product.code()).size())
                    .sum();
            LocalDate day = LocalDate.ofInstant(
                    http.getForObject("/api/dev/clock", ClockView.class).now(), BRUSSELS);
            return new WhatAStartSaw(shelf, freeSavings, altogether, day);
        }
    }

    private static List<TermsVersionView> versionsOf(TestRestTemplate http, String code) {
        return List.of(http.getForObject("/api/savings-products/{code}/versions",
                TermsVersionView[].class, code));
    }

    /**
     * What somebody running the bank does by hand: reprices a version that has already been
     * published, and stops offering a product to new customers.
     *
     * <p>Repricing a published version is not something this application will ever let anybody do —
     * that is the promise — which is exactly why the edit is made underneath it. What is under test
     * is whether the next start argues with whatever it finds, and the only way to find out is to
     * leave it something to argue with.
     */
    private static void repriceFreeSavingsAndCloseTheCoreSaverByHand() {
        withTheDatabase(statement -> {
            int repriced = statement.executeUpdate("update product_terms set "
                    + "annual_rate_basis_points = " + THE_NEW_RATE_ON_FREE_SAVINGS
                    + " where product_code = 'INSTANT' and version = 2");
            assertThat(repriced).as("the repricing this test claims to have made").isEqualTo(1);
            int closed = statement.executeUpdate("update savings_product "
                    + "set open_to_new_accounts = 0 where code = '" + THE_PRODUCT_THEY_CLOSED + "'");
            assertThat(closed).as("the closing this test claims to have made").isEqualTo(1);
        });
    }

    /**
     * Tries to write free savings version 2 a second time, and hands back whatever the database
     * said about it.
     *
     * <p>Returned rather than asserted here, so that the assertion reads as a test rather than as a
     * side effect of a fixture — and so that a failure to refuse shows up as a null in one test
     * instead of as an error in {@code @BeforeAll} that takes every other test down with it.
     */
    private static String whatHappensWhenAProductPublishesVersionTwoTwice() {
        try {
            withTheDatabase(statement -> statement.executeUpdate("insert into product_terms ("
                    + "product_code, version, effective_from, annual_rate_basis_points, "
                    + "bonus_rate_basis_points, notice_days, term_months, minimum_balance_cents, "
                    + "early_exit_penalty_days, points_multiplier_basis_points, "
                    + "anniversary_rate_basis_points, maturity_action) values ("
                    + "'INSTANT', 2, '2020-01-01', 1, 0, 0, 0, 0, 0, 10000, 1000, 'HOLD')"));
            return null;
        } catch (AssertionError refused) {
            return refused.getCause() instanceof SQLException failure
                    ? failure.getMessage()
                    : null;
        }
    }

    /**
     * Opens the file directly, with no application in the way — the only way to change a row
     * nothing can change over the API, and the only way to try writing one the application would
     * never write.
     *
     * <p>Always while no application is up. SQLite serialises writers, and a second connection
     * writing to a live database is how a test earns an intermittent SQLITE_BUSY.
     */
    private static void withTheDatabase(SqlWork work) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE);
             Statement statement = connection.createStatement()) {
            work.doIt(statement);
        } catch (SQLException e) {
            throw new AssertionError("could not work on the database this test wrote at " + DATABASE, e);
        }
    }

    private interface SqlWork {

        void doIt(Statement statement) throws SQLException;
    }

    private static ConfigurableApplicationContext startAnApplicationAgainstTheFile() {
        // Command-line arguments rather than default properties, for the reason the walking
        // skeleton gives: defaults lose to application.properties, which would point this instance
        // at the real database.
        return new SpringApplicationBuilder(SavingStreakApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + DATABASE,
                        "--spring.profiles.active=dev",
                        "--server.port=0");
    }

    /**
     * What one start of the application saw: the shelf, free savings' history, how many versions
     * there are altogether, and the day the application thought it was.
     */
    private record WhatAStartSaw(List<SavingsProductView> shelf, List<TermsVersionView> freeSavings,
                                 int versionsAltogether, LocalDate day) {
    }
}
