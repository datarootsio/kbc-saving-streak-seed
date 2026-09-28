package io.dataroots.savingstreak.savingspolicy;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SchemeView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scheme is written on the first start, at exactly the figures this application has always run
 * on, and never written over afterwards.
 *
 * <p>Three starts against one file, because that is the only way to ask the question this ticket
 * turns on. A published scheme is worth nothing if the application puts it back the way it found it
 * every morning: the seed has to be a floor — write what is missing, read nothing, correct nothing —
 * and "nothing changed on the restart" is a statement about two starts rather than about one. It
 * matters more here than it did for the products catalogue, because what a restart would be
 * overwriting is the scheme somebody's weeks were judged under.
 *
 * <p>Its own file and its own applications, like the other start-up tests. The run's shared file has
 * had its scheme seeded by the application the base class starts, and there is no first start left
 * in it to watch.
 *
 * <p>The edit is made with raw JDBC rather than through an API, because there is no API to change
 * the scheme with — publishing is a later ticket, and the whole point of this one is that there is
 * no door here. What is being asserted is the start-up behaviour, and a row changed by hand is a row
 * changed by hand however it got that way.
 */
class TheSchemeIsSeededOnceAtTodaysFiguresAndNeverResetApiTest extends ApiIntegrationTest {

    private static final Path DATABASE = aDatabaseFileThatDoesNotExistYet("saving-streak-scheme");

    /** What somebody running the bank changed by hand, in the unit the column holds: EUR 80. */
    private static final long THE_NEW_WEEKLY_MINIMUM_IN_CENTS = 8_000L;

    private static WhatAStartSaw onTheFirstStart;
    private static WhatAStartSaw onTheSecondStart;
    private static WhatAStartSaw afterSomebodyChangedTheThreshold;
    private static String whatASecondVersionOneGot;

    @BeforeAll
    static void startTwiceThenChangeARowAndStartAgain() {
        onTheFirstStart = aStart();
        onTheSecondStart = aStart();
        whatASecondVersionOneGot = whatHappensWhenTheSchemePublishesVersionOneTwice();
        raiseTheWeeklyMinimumByHand();
        afterSomebodyChangedTheThreshold = aStart();
    }

    /**
     * A file with nothing in it comes up with exactly one version of the scheme, and it is
     * version 1.
     *
     * <p>One rather than none is the whole of what this ticket adds, and it is asserted on the first
     * start because that is the only start that could have written it. One rather than two is the
     * other half: the scheme has no second seeded version, because nobody is pinned to a version and
     * a second one would be a change nobody made with no honest line explaining it.
     */
    @Test
    void a_first_start_writes_exactly_one_version_of_the_scheme() {
        assertThat(onTheFirstStart.history()).extracting(SchemeView::version).containsExactly(1);
        assertThat(onTheFirstStart.inForce().version()).isEqualTo(1);
    }

    /**
     * And it holds exactly the figures the application has always run on, so that an upgraded
     * database behaves on the morning after as it behaved the night before.
     *
     * <p><strong>The pin used to be against the constants and is now against literals, and that is
     * a loss worth naming rather than a tidy-up.</strong> While {@code NewSavingsThisWeek} and
     * {@code StreakMultiplier} still held their figures, this test compared the seeded row against
     * them, so "version 1 is exactly what the application always did" was a fact the compiler and
     * the assertion held jointly: one of the pair could not be changed without the other, and a
     * half-done repricing failed here. The contract ticket deleted those constants, because a
     * defaulted figure left standing is how two figures start disagreeing — and with them went the
     * other side of the comparison. There is nothing left to pin to but the numbers themselves.
     *
     * <p>So this is now a transcription checked against a transcription: the seed writes the spec's
     * table in one file and this test writes it in another, and what it catches is somebody editing
     * the seed. That is genuinely weaker than what it caught before, and it is weaker in a way that
     * no longer matters as much: there is one place these figures are written down in the
     * application now, so there is no second copy left to drift from. What it still has to catch is
     * the seed being edited without the decision being made, which is why every figure is here and
     * not only the interesting ones.
     */
    @Test
    void the_seeded_version_holds_the_figures_the_week_and_the_ladder_have_always_used() {
        SchemeView seeded = onTheFirstStart.inForce();

        assertThat(seeded.weeklyThreshold())
                .as("EUR 50 a week, which is what secured a week before the scheme had versions")
                .isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(seeded.theOrdinaryRate())
                .as("1,00×, the rate a deposit outside any run of weeks has always been paid")
                .isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(seeded.extraForEachFurtherWeek())
                .as("0,10× a further week")
                .isEqualByComparingTo(new BigDecimal("0.1000"));
        assertThat(seeded.theMostAStreakPays())
                .as("1,50×, reached in the sixth consecutive secured week and held from there on")
                .isEqualByComparingTo(new BigDecimal("1.5000"));
    }

    /**
     * And the figures the points sweep and the four notification rules have always used.
     *
     * <p>Typed out here for the reason the four above now are, and for one this half always had:
     * every one of these figures lived in a package-private constant in the module that owned it —
     * {@code PointsExpiry}, the balance rungs, {@code WhenABudgetIsRunningOut},
     * {@code WhenArrearsArePilingUp}, {@code AMaturityComingSoon} and {@code AnAnniversaryComingSoon}
     * — so publishing six fields for one test would always have been a worse trade than writing six
     * numbers down. The contract ticket has since deleted all six, which makes the point moot and
     * makes both halves of this test the same kind of assertion: the spec's table, transcribed, held
     * against the seed that transcribed it.
     */
    @Test
    void the_seeded_version_holds_the_figures_points_and_notifications_have_always_used() {
        SchemeView seeded = onTheFirstStart.inForce();

        assertThat(seeded.howLongABatchOfPointsLasts()).isEqualTo(12);
        assertThat(seeded.balanceRungs())
                .as("the six rungs, ascending, as the notifications module has always had them")
                .extracting(BigDecimal::toPlainString)
                .containsExactly("100.00", "500.00", "1000.00", "2500.00", "5000.00", "10000.00");
        assertThat(seeded.whatShareOfABudgetIsRunningLow())
                .as("four fifths, as a percentage rather than as the fraction the rule compares")
                .isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(seeded.howManyOutstandingIsASpiral()).isEqualTo(3);
        assertThat(seeded.daysBeforeAMaturityIsWorthSaying()).isEqualTo(30);
        assertThat(seeded.daysBeforeAnAnniversaryIsWorthSaying()).isEqualTo(30);
    }

    /**
     * Its effective date is a literal Monday long before any data this application can hold.
     *
     * <p><strong>Not a date anchored to the day the seed ran, which is where the scheme parts
     * company with the products catalogue.</strong> This application's clock moves in both
     * directions. A seed date computed from today would sit after some weeks already in the ledger
     * the moment somebody wound the clock back, and those weeks would then be judged against no
     * published scheme at all. Asserted as a fixed date rather than as "before today", because
     * "before today" is exactly what an anchored date also satisfies.
     */
    @Test
    void the_seeded_version_takes_effect_on_a_literal_monday_long_before_any_data() {
        assertThat(onTheFirstStart.inForce().effectiveFrom())
                .isEqualTo(LocalDate.of(2000, 1, 3));
        assertThat(onTheFirstStart.inForce().effectiveFrom().getDayOfWeek())
                .as("every version of the scheme takes effect on a Monday, this one included")
                .isEqualTo(DayOfWeek.MONDAY);
    }

    /**
     * Version 1 carries a line saying what it is, because every version of the scheme owes the
     * customer a sentence.
     *
     * <p>Where a product's first version leaves that line empty — nothing changed, and the customer
     * chose that agreement — nobody chose the scheme, so even the version that changes nothing has
     * to say so in words. A customer reading the history reads an explanation and not a diff.
     */
    @Test
    void the_seeded_version_says_in_words_what_it_is() {
        assertThat(onTheFirstStart.inForce().whatChanged())
                .isNotBlank()
                .contains("always run on");
    }

    /**
     * The second start finds it and does nothing at all — not a second version, not a figure
     * changed. Asserted as the whole reading against the whole reading, because a seed that wrote a
     * duplicate would still have the right row somewhere in it.
     */
    @Test
    void a_restart_finds_it_and_changes_nothing() {
        assertThat(onTheSecondStart.history())
                .containsExactlyElementsOf(onTheFirstStart.history());
        assertThat(onTheSecondStart.inForce()).isEqualTo(onTheFirstStart.inForce());
    }

    /**
     * The promise the whole administration surface will rest on: what somebody running the bank
     * changed is still changed after the application has been up and down again.
     *
     * <p>A seed that corrected the row it found would make every published version of the scheme
     * last until the next restart, which for a figure people's weeks are judged against is worse
     * than not being able to publish at all.
     */
    @Test
    void a_scheme_somebody_changed_survives_the_next_start() {
        assertThat(afterSomebodyChangedTheThreshold.inForce().weeklyThreshold())
                .isEqualByComparingTo("80.00");
        assertThat(afterSomebodyChangedTheThreshold.history())
                .extracting(SchemeView::version).containsExactly(1);
    }

    /**
     * One row per version is a rule the database keeps rather than one this application merely
     * intends: a second row claiming to be version 1 is rejected outright.
     *
     * <p>Asserted by trying it, with no application up, because the guarantee is an index and an
     * index is only worth having if it actually refuses something. Two rows under one version would
     * be two answers to "what was my week judged under", and the rule that picks the highest number
     * whose day has come would settle the tie by whichever row came back first.
     */
    @Test
    void the_scheme_cannot_publish_the_same_version_twice() {
        assertThat(whatASecondVersionOneGot)
                .as("what the database said about a second version 1 of the scheme")
                .isNotNull()
                .containsIgnoringCase("unique");
    }

    /** One start, one reading of the scheme and of its history, and the application stopped again. */
    private static WhatAStartSaw aStart() {
        try (ConfigurableApplicationContext application = startAnApplicationAgainstTheFile()) {
            TestRestTemplate http = new TestRestTemplate();
            http.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                    + application.getEnvironment().getProperty("local.server.port")));
            return new WhatAStartSaw(
                    http.getForObject("/api/scheme", SchemeView.class),
                    List.of(http.getForObject("/api/scheme/versions", SchemeView[].class)));
        }
    }

    /**
     * What somebody running the bank does by hand: raises the weekly minimum on a version that has
     * already been published.
     *
     * <p>Editing a published version is not something this application will ever let anybody do —
     * that is the promise — which is exactly why the edit is made underneath it. What is under test
     * is whether the next start argues with whatever it finds, and the only way to find out is to
     * leave it something to argue with.
     */
    private static void raiseTheWeeklyMinimumByHand() {
        withTheDatabase(statement -> {
            int raised = statement.executeUpdate("update scheme_version set "
                    + "weekly_threshold_cents = " + THE_NEW_WEEKLY_MINIMUM_IN_CENTS
                    + " where version = 1");
            assertThat(raised).as("the change this test claims to have made").isEqualTo(1);
        });
    }

    /**
     * Tries to write version 1 a second time, and hands back whatever the database said about it.
     *
     * <p>Returned rather than asserted here, so that the assertion reads as a test rather than as a
     * side effect of a fixture — and so that a failure to refuse shows up as a null in one test
     * instead of as an error in {@code @BeforeAll} that takes every other test down with it.
     */
    private static String whatHappensWhenTheSchemePublishesVersionOneTwice() {
        try {
            withTheDatabase(statement -> statement.executeUpdate("insert into scheme_version ("
                    + "version, effective_from, weekly_threshold_cents, "
                    + "the_ordinary_rate_basis_points, extra_for_each_further_week_basis_points, "
                    + "the_most_a_streak_pays_basis_points, "
                    + "how_long_a_batch_of_points_lasts_in_months, "
                    + "what_share_of_a_budget_is_running_low_basis_points, "
                    + "how_many_outstanding_is_a_spiral, days_before_a_maturity_is_worth_saying, "
                    + "days_before_an_anniversary_is_worth_saying, what_changed) values ("
                    + "1, '2000-01-03', 1, 10000, 1000, 15000, 12, 8000, 3, 30, 30, 'a duplicate')"));
            return null;
        } catch (AssertionError refused) {
            return refused.getCause() instanceof SQLException failure ? failure.getMessage() : null;
        }
    }

    /**
     * Opens the file directly, with no application in the way — the only way to change a row nothing
     * can change over the API, and the only way to try writing one the application would never
     * write.
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

    /** What one start of the application saw: the version in force, and the whole history. */
    private record WhatAStartSaw(SchemeView inForce, List<SchemeView> history) {
    }
}
