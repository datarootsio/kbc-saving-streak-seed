package io.dataroots.savingstreak.scheme;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which version of the scheme is in force on a given day, at the rule rather than over HTTP.
 *
 * <p>Beside {@code TheBalanceRungsTest} and {@code AnAnniversaryComingSoonTest}, and here for the
 * reason they are. Every other test of this feature drives the whole application over HTTP, which
 * keeps it free of the storage decisions later tickets need to change; the boundaries of this rule
 * cannot be reached that way without publishing several versions and winding a clock across each of
 * their Mondays, an application per case. A week the day before a version starts, a week on its own
 * Monday, and two versions racing for one Monday are three of those, and each of them is a single
 * comparison that an API test would spend weeks of clock-winding to arrive at.
 *
 * <p>So the calendar arithmetic is asserted here, and the HTTP tests are about the rules that use
 * it: that the scheme reads as the version whose day has come, and that a week keeps the verdict it
 * got.
 *
 * <p>The versions in this test are built by a helper that varies only the two fields the rule looks
 * at — the number and the Monday — because every other figure is beside the point and a test that
 * spelled all thirteen out five times would bury the one thing each case is about.
 */
class TheSchemeInForceOnTest {

    /** A Monday, and the one the fixtures are laid out around. */
    private static final LocalDate THE_FIRST_MONDAY = LocalDate.of(2024, 1, 1);

    /**
     * A day before the first version exists is still answered, with the lowest version there is.
     *
     * <p><strong>The case this application's two-directional clock makes real.</strong> A trainer
     * who winds the clock backwards would otherwise reach a day on which nothing had been
     * published, and a week judged under no scheme at all has no threshold, no ladder and no answer
     * to "did I secure it". Falling back to the lowest version is the only honest answer, because it
     * is what the scheme was written with — and the seed's literal Monday in the year 2000 is what
     * makes this a safety net rather than a path anybody walks.
     */
    @Test
    void a_day_before_the_first_version_exists_is_answered_with_the_lowest_version() {
        List<TheSchemeAsPublished> history = List.of(
                aVersion(1, THE_FIRST_MONDAY),
                aVersion(2, THE_FIRST_MONDAY.plusWeeks(4)));

        assertThat(TheSchemeInForceOn.outOf(history, THE_FIRST_MONDAY.minusDays(1)).version())
                .isEqualTo(1);
        assertThat(TheSchemeInForceOn.outOf(history, THE_FIRST_MONDAY.minusYears(5)).version())
                .as("however far back the clock is wound, some scheme is in force")
                .isEqualTo(1);
    }

    /**
     * A version is in force on its own Monday, and not on the day before it.
     *
     * <p>The boundary the whole feature turns on. A version takes effect <em>from</em> its Monday,
     * inclusive, so the week beginning that morning is judged under it and the week that ended the
     * night before is not. Off by one day in either direction is a week judged under the wrong
     * scheme, which is precisely the silent repricing this feature exists to prevent.
     */
    @Test
    void a_version_is_in_force_on_its_own_monday_and_not_the_day_before() {
        LocalDate theSecondVersionsMonday = THE_FIRST_MONDAY.plusWeeks(4);
        List<TheSchemeAsPublished> history = List.of(
                aVersion(1, THE_FIRST_MONDAY),
                aVersion(2, theSecondVersionsMonday));

        assertThat(TheSchemeInForceOn.outOf(history, theSecondVersionsMonday).version())
                .as("the Monday it takes effect on is a day it is in force")
                .isEqualTo(2);
        assertThat(TheSchemeInForceOn.outOf(history, theSecondVersionsMonday.minusDays(1)).version())
                .as("the Sunday before it is still the version before")
                .isEqualTo(1);
        assertThat(TheSchemeInForceOn.outOf(history, theSecondVersionsMonday.plusDays(6)).version())
                .as("and it stays in force for every day after, until something supersedes it")
                .isEqualTo(2);
    }

    /**
     * Two versions sharing a Monday are settled by the version number, and the higher one wins.
     *
     * <p><strong>This is not a tie-break for tidiness; it is how a mistake is corrected without an
     * edit door existing.</strong> An administrator who announced the wrong figures for next Monday
     * publishes another version for the same Monday, and version <em>n+1</em> simply wins — nothing
     * is edited, nothing is deleted, and the version that was wrong stays in the history saying
     * what it said. A rule that broke the tie by date, or by whichever row the database handed back
     * first, would make that correction a coin toss.
     */
    @Test
    void two_versions_sharing_a_monday_are_settled_by_the_higher_version_number() {
        LocalDate theContestedMonday = THE_FIRST_MONDAY.plusWeeks(4);
        List<TheSchemeAsPublished> history = List.of(
                aVersion(1, THE_FIRST_MONDAY),
                aVersion(2, theContestedMonday),
                aVersion(3, theContestedMonday));

        assertThat(TheSchemeInForceOn.outOf(history, theContestedMonday).version()).isEqualTo(3);
        assertThat(TheSchemeInForceOn.outOf(history, theContestedMonday.plusWeeks(52)).version())
                .as("and it goes on winning, because nothing later has been published")
                .isEqualTo(3);
    }

    /**
     * The order the history arrives in decides nothing.
     *
     * <p>The rule is stated over a list and the list is whatever a query handed back. It reads the
     * highest number whose day has come rather than the last element, so a history shuffled by a
     * different {@code order by} answers exactly the same — which is what lets the repository
     * choose its own ordering without this rule being part of that decision.
     */
    @Test
    void the_order_the_history_arrives_in_decides_nothing() {
        LocalDate theSecondVersionsMonday = THE_FIRST_MONDAY.plusWeeks(4);
        List<TheSchemeAsPublished> lowestFirst = List.of(
                aVersion(1, THE_FIRST_MONDAY),
                aVersion(2, theSecondVersionsMonday),
                aVersion(3, theSecondVersionsMonday.plusWeeks(4)));
        List<TheSchemeAsPublished> shuffled = List.of(
                lowestFirst.get(2), lowestFirst.get(0), lowestFirst.get(1));

        for (LocalDate day : List.of(THE_FIRST_MONDAY.minusDays(1), THE_FIRST_MONDAY,
                theSecondVersionsMonday, theSecondVersionsMonday.plusWeeks(9))) {
            assertThat(TheSchemeInForceOn.outOf(shuffled, day).version())
                    .as("the version in force on " + day)
                    .isEqualTo(TheSchemeInForceOn.outOf(lowestFirst, day).version());
        }
    }

    /**
     * A history in which nothing has started yet still answers, with its lowest version.
     *
     * <p>The same fallback as the first case and a different shape of history: there, some version
     * had started and the day was before it; here, not one version's Monday has come. It cannot
     * happen to a database the seed has touched, and it is exactly what a clock wound back before
     * the year 2000 would produce — so the answer is the lowest version rather than an exception,
     * because a customer whose week cannot be judged is worse than a scheme that has not officially
     * started.
     */
    @Test
    void a_history_with_nothing_yet_in_force_is_answered_with_its_lowest_version() {
        List<TheSchemeAsPublished> nothingHasStarted = List.of(
                aVersion(2, THE_FIRST_MONDAY.plusWeeks(4)),
                aVersion(3, THE_FIRST_MONDAY.plusWeeks(8)),
                aVersion(1, THE_FIRST_MONDAY));

        assertThat(TheSchemeInForceOn.outOf(nothingHasStarted, THE_FIRST_MONDAY.minusWeeks(1))
                .version())
                .isEqualTo(1);
    }

    /**
     * A history with no versions at all is a broken database rather than a customer's mistake, so it
     * throws rather than refusing in words.
     *
     * <p>There is no sentence to show anybody, because nobody did anything wrong: a bank whose
     * scheme was never written down is a file somebody emptied by hand or a start-up that did not
     * run. The same reading {@code TheTermsOnOfferToday} makes of the same impossible state.
     */
    @Test
    void a_history_with_no_versions_at_all_is_a_broken_database() {
        assertThatThrownBy(() -> TheSchemeInForceOn.outOf(List.of(), THE_FIRST_MONDAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no version of the scheme has been published");
    }

    /**
     * A version carrying only what this rule reads: its number and the Monday it takes effect.
     *
     * <p>The other eleven figures are filled with the seed's, so that the record is a scheme
     * somebody could actually have published rather than a row of noughts — but nothing here
     * asserts on them, because the rule under test cannot see them.
     */
    private static TheSchemeAsPublished aVersion(int version, LocalDate effectiveFrom) {
        return new TheSchemeAsPublished(
                version,
                effectiveFrom,
                new BigDecimal("50.00"),
                new BigDecimal("1.0000"),
                new BigDecimal("0.1000"),
                new BigDecimal("1.5000"),
                12,
                List.of(new BigDecimal("100.00")),
                new BigDecimal("80.00"),
                3,
                30,
                30,
                "version " + version);
    }
}
