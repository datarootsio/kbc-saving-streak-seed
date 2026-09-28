package io.dataroots.savingstreak.savingspolicy;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SchemeView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scheme in force, and every version of it the bank has ever published, readable by anybody.
 *
 * <p>Two reads and no writes, which is the whole of this ticket's surface. The scheme exists as
 * rows and can be read; nothing yet decides anything from them, and the door that publishes the
 * next version is a later ticket. So what this class asserts is what a customer can see, in the
 * units they see it in, and — just as importantly — what nobody can do.
 *
 * <p>Against the run's shared application rather than one of its own, because none of these reads
 * moves a clock or writes a row: they are the same question asked of whatever the seed left behind.
 * The start-up test beside this one is the one that needs its own file, because a first start is
 * the thing it is about.
 */
class TheSchemeInForceAndEveryVersionEverPublishedApiTest extends ApiIntegrationTest {

    /**
     * Anybody can read what the scheme currently says, in the units the rest of the application
     * speaks.
     *
     * <p>Euros for the weekly threshold, multiples for the three rungs of the ladder, a percentage
     * for the budget share, and plain counts for the rest — and not one basis point anywhere. A
     * customer reading "1.0000" is reading the factor their first week is paid at; a customer
     * reading "10000" would be reading this module's storage.
     */
    @Test
    void anybody_can_read_what_the_scheme_currently_says() {
        SchemeView inForce = http.getForObject("/api/scheme", SchemeView.class);

        assertThat(inForce.version()).isPositive();
        assertThat(inForce.effectiveFrom()).isNotNull();
        assertThat(inForce.weeklyThreshold()).isEqualByComparingTo("50.00");
        assertThat(inForce.theOrdinaryRate()).isEqualByComparingTo("1.00");
        assertThat(inForce.extraForEachFurtherWeek()).isEqualByComparingTo("0.10");
        assertThat(inForce.theMostAStreakPays()).isEqualByComparingTo("1.50");
        assertThat(inForce.howLongABatchOfPointsLasts()).isEqualTo(12);
        assertThat(inForce.whatShareOfABudgetIsRunningLow()).isEqualByComparingTo("80.00");
        assertThat(inForce.howManyOutstandingIsASpiral()).isEqualTo(3);
        assertThat(inForce.daysBeforeAMaturityIsWorthSaying()).isEqualTo(30);
        assertThat(inForce.daysBeforeAnAnniversaryIsWorthSaying()).isEqualTo(30);
        assertThat(inForce.whatChanged())
                .as("every version of the scheme says in words what it is, the first one included")
                .isNotBlank();
    }

    /**
     * The ladder's figures keep the four decimal places the module promises them, through the
     * database and over the wire.
     *
     * <p>Not a formatting preference. The step is the figure that makes the difference: a scheme
     * published with a step of 0,0250 is a ladder that climbs a quarter as fast, and a reading that
     * arrived with two places would quietly turn it into a ladder that does not climb at all. This
     * is the assertion that says the places survive an {@code int} of basis points, a SQLite column
     * and a JSON number.
     */
    @Test
    void the_ladder_is_quoted_to_four_places_all_the_way_out() {
        SchemeView inForce = http.getForObject("/api/scheme", SchemeView.class);

        assertThat(inForce.theOrdinaryRate().toPlainString()).isEqualTo("1.0000");
        assertThat(inForce.extraForEachFurtherWeek().toPlainString()).isEqualTo("0.1000");
        assertThat(inForce.theMostAStreakPays().toPlainString()).isEqualTo("1.5000");
    }

    /**
     * The balance rungs come out as an ordered list of euro amounts, ascending.
     *
     * <p>A list rather than six fields, because that is what it is: an ordered collection of one
     * kind of thing, of a length nobody has fixed. Ascending because the order is the ladder — a
     * version whose rungs arrived the other way round would be a ladder with its rungs in the wrong
     * places rather than a list that needs sorting.
     */
    @Test
    void the_balance_rungs_come_out_as_an_ordered_list_of_euro_amounts() {
        SchemeView inForce = http.getForObject("/api/scheme", SchemeView.class);

        assertThat(inForce.balanceRungs())
                .extracting(BigDecimal::toPlainString)
                .containsExactly("100.00", "500.00", "1000.00", "2500.00", "5000.00", "10000.00");
        assertThat(inForce.balanceRungs()).isSorted();
    }

    /**
     * Every version ever published is readable, newest first, each with its date, its number and its
     * line saying what changed — and the version in force is one of them.
     *
     * <p>Newest first because this is a page somebody scrolls to find out what changed last. That
     * the reading in force appears in the history is the assertion that stops the two ever becoming
     * different lists: a history that left out what is current would make a reader join two
     * responses to see how the scheme has moved.
     */
    @Test
    void every_version_ever_published_is_readable_newest_first() {
        List<SchemeView> history =
                List.of(http.getForObject("/api/scheme/versions", SchemeView[].class));
        SchemeView inForce = http.getForObject("/api/scheme", SchemeView.class);

        assertThat(history).isNotEmpty();
        assertThat(history).extracting(SchemeView::version).isSortedAccordingTo(
                java.util.Comparator.reverseOrder());
        assertThat(history).allSatisfy(version -> {
            assertThat(version.effectiveFrom()).isNotNull();
            assertThat(version.whatChanged()).isNotBlank();
        });
        assertThat(history).contains(inForce);
    }

    /**
     * There is no door that edits a version and none that deletes one, and that is asserted by
     * knocking on them.
     *
     * <p><strong>Not a check that could be loosened — methods that were never written.</strong> A
     * scheme somebody's week was judged under cannot be rewritten behind them, and the only
     * enforceable version of that promise is the absence of a verb. Spring answers a request on a
     * mapped path with an unmapped method as 405, and an unmapped path as 404; either is the same
     * fact about this module, which is why both are accepted here rather than one of them being
     * pinned as the contract.
     *
     * <p><strong>The customer's own two addresses only.</strong> When this was written there was no
     * administration surface at all and {@code /api/admin/scheme/versions} was knocked on here
     * beside them; the door that publishes the next version now exists at exactly that address, and
     * a POST to it is the one thing anybody may do to the scheme. What is asserted here is what a
     * customer's reads are — reads — and the administration surface has a file of its own that
     * knocks on every verb the back office deliberately did not build.
     */
    @Test
    void there_is_no_door_that_edits_a_version_and_none_that_deletes_one() {
        for (HttpMethod verb : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH,
                HttpMethod.DELETE)) {
            for (String door : List.of("/api/scheme", "/api/scheme/versions",
                    "/api/scheme/versions/1")) {
                ResponseEntity<String> knocked =
                        http.exchange(door, verb, null, String.class);
                assertThat(knocked.getStatusCode())
                        .as(verb + " " + door + " is not a door this application has")
                        .isIn(HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED);
            }
        }
    }
}
