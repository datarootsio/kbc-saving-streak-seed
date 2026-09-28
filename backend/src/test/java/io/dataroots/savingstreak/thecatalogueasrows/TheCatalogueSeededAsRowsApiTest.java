package io.dataroots.savingstreak.thecatalogueasrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RewardView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * The catalogue is rows now, seeded on the first start and never written over afterwards.
 *
 * <p>Three starts against one file, because that is the only way to ask the question this slice
 * turns on. A catalogue somebody runs is worth nothing if the application puts it back the way it
 * found it every morning: the seed has to be a floor — write what is missing, read nothing, correct
 * nothing — and "nothing changed on the restart" is a statement about two starts rather than about
 * one.
 *
 * <p>Its own file and its own applications, like the other start-up tests. The run's shared file has
 * had its catalogue seeded by the application the base class starts, and there is no first start
 * left in it to watch.
 *
 * <p>The edit is made with raw JDBC rather than through an API, because there is no API to edit an
 * offer with yet — that is the next slice. What is being asserted is the start-up behaviour, and a
 * row changed by hand is a row changed by hand however it got that way.
 */
class TheCatalogueSeededAsRowsApiTest extends ApiIntegrationTest {

    private static final Path DATABASE = aDatabaseFileThatDoesNotExistYet("saving-streak-catalogue");

    /** The one offer this test rewrites, and what somebody running the scheme changed it to. */
    private static final String EDITED = "CHARITY_DONATION";
    private static final String THE_NEW_TITLE = "Charity donation to the winter appeal";
    private static final long THE_NEW_PRICE = 15;

    /**
     * Everything the seed fills in. Every other column on the row has to be null, because a null is
     * the absence of a rule — no stock, no window, no cap, no shelf life — and a seeded offer that
     * said anything about itself would be a change to what a customer sees.
     */
    private static final Set<String> FILLED_BY_THE_SEED = Set.of(
            "id", "code", "title", "words", "cost_in_points", "voucher_prefix", "state", "kind");

    private static List<RewardView> onTheFirstStart;
    private static List<RewardView> onTheSecondStart;
    private static List<RewardView> afterSomebodyEditedOne;

    @BeforeAll
    static void startTwiceThenEditOneAndStartAgain() {
        onTheFirstStart = theCatalogueAfterAStart();
        onTheSecondStart = theCatalogueAfterAStart();
        editAnOfferByHand();
        afterSomebodyEditedOne = theCatalogueAfterAStart();
    }

    /**
     * A file with nothing in it comes up offering exactly what this application has always offered:
     * four rewards, four prices, in the order they have always been in.
     *
     * <p>The same assertion the claiming test makes about the shared database, made here against a
     * file the seed has only just written. That is the difference worth having: on a fresh file
     * there is nothing to serve unless this step put it there.
     */
    @Test
    void a_first_start_writes_the_four_rewards_the_application_has_always_offered() {
        assertThat(onTheFirstStart)
                .extracting(RewardView::code, RewardView::costInPoints)
                .containsExactly(
                        tuple("CHARITY_DONATION", 10L),
                        tuple("SNACK_VOUCHER", 40L),
                        tuple("CINEMA_TICKET", 100L),
                        tuple("FAMILY_CINEMA_PACK", 180L));
        assertThat(onTheFirstStart).allSatisfy(offer -> {
            assertThat(offer.title()).isNotBlank();
            assertThat(offer.description()).isNotBlank();
        });
    }

    /**
     * The second start finds them and does nothing at all — not a fifth row, not a reordering, not
     * a word changed. Asserted as the whole list against the whole list, because a seed that wrote
     * a duplicate would still have the right four somewhere in it.
     */
    @Test
    void a_restart_finds_them_and_changes_nothing() {
        assertThat(onTheSecondStart).containsExactlyElementsOf(onTheFirstStart);
    }

    /**
     * And the promise the whole administration surface rests on: what somebody running the scheme
     * changed is still changed after the application has been up and down again. A seed that
     * corrected the row it found would make every edit last until the next restart, which is worse
     * than not being able to edit at all.
     */
    @Test
    void an_edit_to_a_seeded_offer_survives_the_next_start() {
        assertThat(afterSomebodyEditedOne)
                .extracting(RewardView::code)
                .containsExactlyElementsOf(onTheFirstStart.stream().map(RewardView::code).toList());
        assertThat(afterSomebodyEditedOne)
                .filteredOn(offer -> offer.code().equals(EDITED))
                .singleElement()
                .satisfies(offer -> {
                    assertThat(offer.title()).isEqualTo(THE_NEW_TITLE);
                    assertThat(offer.costInPoints()).isEqualTo(THE_NEW_PRICE);
                });
    }

    /**
     * Every seeded offer says nothing about itself beyond what the enum said, which is what makes
     * it behave exactly as the enum did.
     *
     * <p>Read out of the database, because the columns this is about are ones no endpoint reports
     * yet: they are filled by the slices after this one, and the point of asserting them now is
     * that a seed which quietly put a zero in a stock column would change what a customer can claim
     * the moment the slice reading it lands.
     */
    @Test
    void every_seeded_offer_leaves_every_column_the_later_slices_fill_empty() {
        withTheDatabase(statement -> {
            try (ResultSet offers = statement.executeQuery("select * from reward_offer")) {
                ResultSetMetaData columns = offers.getMetaData();
                int rows = 0;
                while (offers.next()) {
                    rows++;
                    for (int column = 1; column <= columns.getColumnCount(); column++) {
                        String name = columns.getColumnName(column);
                        if (FILLED_BY_THE_SEED.contains(name)) {
                            continue;
                        }
                        assertThat(offers.getObject(column))
                                .as("%s on the seeded offer %s", name, offers.getString("code"))
                                .isNull();
                    }
                    assertThat(offers.getString("state")).isEqualTo("PUBLISHED");
                    assertThat(offers.getString("kind")).isEqualTo("ITEM");
                }
                assertThat(rows).as("the seeded offers").isEqualTo(4);
            }
        });
    }

    /** One start, one reading of the catalogue, and the application stopped again. */
    private static List<RewardView> theCatalogueAfterAStart() {
        try (ConfigurableApplicationContext application = startAnApplicationAgainstTheFile()) {
            TestRestTemplate http = new TestRestTemplate();
            http.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                    + application.getEnvironment().getProperty("local.server.port")));
            return List.of(http.getForObject("/api/rewards", RewardView[].class));
        }
    }

    /**
     * What somebody running the scheme does to an offer: a new title and a new price on a row that
     * the seed knows the code of.
     */
    private static void editAnOfferByHand() {
        withTheDatabase(statement -> {
            int edited = statement.executeUpdate("update reward_offer set title = '" + THE_NEW_TITLE
                    + "', cost_in_points = " + THE_NEW_PRICE + " where code = '" + EDITED + "'");
            assertThat(edited).as("the edit this test claims to have made").isEqualTo(1);
        });
    }

    /**
     * Opens the file directly, with no application in the way — the only way to change a row
     * nothing can yet change over the API, and the only way to read a column nothing reports.
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
        // Command-line arguments rather than default properties, for the reason the walking skeleton
        // gives: defaults lose to application.properties, which would point this instance at the
        // real database.
        return new SpringApplicationBuilder(SavingStreakApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + DATABASE,
                        "--spring.profiles.active=dev",
                        "--server.port=0");
    }
}
