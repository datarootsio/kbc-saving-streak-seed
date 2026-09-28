package io.dataroots.savingstreak.savingspolicy;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SchemeView;
import io.dataroots.savingstreak.support.SchemeVersionPublishedView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingspolicy.ASchemeSomebodyAdministers.reasonGivenBy;
import static io.dataroots.savingstreak.savingspolicy.ASchemeSomebodyAdministers.theSameSchemeAgain;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repricing the scheme becomes something somebody does rather than something somebody deploys: an
 * administrator publishes the next version, and from its Monday it is the scheme the bank runs on.
 *
 * <p><strong>Its own application on its own file</strong>, because publishing cannot be undone —
 * which is the promise of the module rather than an inconvenience of testing it.
 * {@link ASchemeSomebodyAdministers} argues that at length.
 *
 * <p><strong>Ordered, unusually and deliberately</strong>, for the reason the products'
 * administration test is: nothing here can be put back, and several of these wind the clock forward
 * to watch a Monday arrive. Each test publishes into a scheme the ones before it have already
 * moved, and the day the application thinks it is depends on how far they wound it, so the order
 * these run in is part of what they mean.
 *
 * <p>Every version published here is dated on the next Monday the application's own clock has not
 * reached, because that is the only kind of date this module accepts. The tests that assert the
 * refusal of the other kinds are next door.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnAdministratorPublishesANewVersionOfTheSchemeApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-scheme-publishing");

    /** The shorter life for points the last test publishes, named so the reading is one figure. */
    private static final int THREE_MONTHS = 3;

    private static ASchemeSomebodyAdministers bank;

    @BeforeAll
    static void startABankSomebodyRuns() {
        bank = new ASchemeSomebodyAdministers(DATABASE);
    }

    @AfterAll
    static void stopIt() {
        if (bank != null) {
            bank.close();
        }
    }

    /**
     * The heart of the ticket: the weekly minimum is raised without a release, the version is
     * numbered one higher than the last, and nothing at all changes until its Monday.
     *
     * <p><strong>Both halves matter and the second is the one this whole feature exists for.</strong>
     * The first says the version was written as asked and given the number the module counted. The
     * second says the scheme is dated rather than switched: the morning after somebody publishes,
     * the bank is still running on exactly what it was running on, and the new figure arrives on the
     * Monday it was announced for and not a day earlier.
     *
     * <p>The clock is wound rather than the date being chosen to have already passed, because the
     * claim is about a day arriving. An application that ignored the effective date entirely would
     * fail the first half, and one that never noticed a day arriving would fail the second.
     */
    @Test
    @Order(1)
    void a_new_version_is_numbered_one_higher_and_is_not_in_force_until_its_monday() {
        SchemeView wasInForce = bank.theSchemeInForce();
        LocalDate nextMonday = bank.theNextMondayStillToCome();
        Map<String, Object> form = theSameSchemeAgain(wasInForce, nextMonday,
                "A week asks for EUR 80 instead of EUR 50 from the date shown, because the bank "
                        + "can afford a higher bar and wants the ladder to mean more.");
        form.put("weeklyThreshold", "80.00");

        SchemeVersionPublishedView published = bank.publish(form);

        assertThat(published.published().version()).isEqualTo(wasInForce.version() + 1);
        assertThat(published.published().effectiveFrom()).isEqualTo(nextMonday);
        assertThat(published.published().weeklyThreshold()).isEqualByComparingTo("80.00");
        assertThat(published.itsDayHasCome())
                .as("whether a version announced for next Monday is deciding anything yet")
                .isFalse();
        assertThat(bank.everyVersionPublished())
                .as("the announced version is readable before it starts")
                .contains(published.published());
        assertThat(bank.theSchemeInForce())
                .as("what the bank runs on while the new version is still ahead of today")
                .isEqualTo(wasInForce);

        bank.theClockReaches(nextMonday);

        assertThat(bank.theSchemeInForce())
                .as("what the bank runs on the morning the new version takes effect")
                .isEqualTo(published.published());
    }

    /**
     * Every figure the scheme carries can be set, and what comes back is what was typed in each of
     * them.
     *
     * <p>All eleven moved at once, deliberately. A test that changed one would pass against a
     * backend that read one field and carried the rest over from the version before, and carrying
     * anything over is exactly the design this contract rejects: a version is the complete list of
     * numbers, published outright, so that what a published scheme says never depends on reading
     * the chain behind it.
     *
     * <p>The rungs are a longer ladder than the six seeded ones, because a list that happened to be
     * the same length would pass against a backend that kept the old one.
     */
    @Test
    @Order(2)
    void every_figure_the_scheme_carries_can_be_set() {
        LocalDate nextMonday = bank.theNextMondayStillToCome();
        Map<String, Object> form = theSameSchemeAgain(bank.theSchemeInForce(), nextMonday,
                "The whole scheme restated: a lower bar, a steeper ladder, a shorter life for "
                        + "points, seven rungs instead of six, and earlier warnings.");
        form.put("weeklyThreshold", "25.50");
        form.put("theOrdinaryRate", "1.2500");
        form.put("extraForEachFurtherWeek", "0.2500");
        form.put("theMostAStreakPays", "3.0000");
        form.put("howLongABatchOfPointsLasts", "6");
        form.put("balanceRungs", List.of("50", "250", "750", "1500", "3000", "7500", "20000"));
        form.put("whatShareOfABudgetIsRunningLow", "65.50");
        form.put("howManyOutstandingIsASpiral", "2");
        form.put("daysBeforeAMaturityIsWorthSaying", "14");
        form.put("daysBeforeAnAnniversaryIsWorthSaying", "7");

        SchemeView published = bank.publish(form).published();

        assertThat(published.effectiveFrom()).isEqualTo(nextMonday);
        assertThat(published.weeklyThreshold()).isEqualByComparingTo("25.50");
        assertThat(published.theOrdinaryRate()).isEqualByComparingTo("1.2500");
        assertThat(published.extraForEachFurtherWeek()).isEqualByComparingTo("0.2500");
        assertThat(published.theMostAStreakPays()).isEqualByComparingTo("3.0000");
        assertThat(published.howLongABatchOfPointsLasts()).isEqualTo(6);
        assertThat(published.balanceRungs())
                .extracting(BigDecimal::toPlainString)
                .containsExactly("50.00", "250.00", "750.00", "1500.00", "3000.00", "7500.00",
                        "20000.00");
        assertThat(published.whatShareOfABudgetIsRunningLow()).isEqualByComparingTo("65.50");
        assertThat(published.howManyOutstandingIsASpiral()).isEqualTo(2);
        assertThat(published.daysBeforeAMaturityIsWorthSaying()).isEqualTo(14);
        assertThat(published.daysBeforeAnAnniversaryIsWorthSaying()).isEqualTo(7);
        assertThat(published.whatChanged()).contains("seven rungs");
    }

    /**
     * The version number is the module's answer and never the caller's: a number sent on the form
     * is not read, and the version written is the next one.
     *
     * <p>A number somebody could send is a number two administrators could send at once, and two
     * rows claiming to be version 6 would be two answers to "what was my week judged under". The
     * form carries an outlandish one so that a backend which did read it would be caught by the
     * version, not merely by an off-by-one.
     */
    @Test
    @Order(3)
    void the_version_number_is_the_modules_answer_and_never_the_callers() {
        SchemeView theHighestSoFar = newest(bank.everyVersionPublished());
        Map<String, Object> form = theSameSchemeAgain(bank.theSchemeInForce(),
                bank.theNextMondayStillToCome(),
                "A version sent with a version number on it, which the bank ignores.");
        form.put("version", "9000");

        SchemeView published = bank.publish(form).published();

        assertThat(published.version()).isEqualTo(theHighestSoFar.version() + 1);
        assertThat(bank.everyVersionPublished()).extracting(SchemeView::version)
                .doesNotContain(9000);
    }

    /**
     * A version announced for next Monday is corrected by publishing another one for that same
     * Monday, which wins by being the higher version number.
     *
     * <p><strong>This is the whole of the correction surface, and that is the point.</strong> There
     * is no edit door and no delete door, so a mistake announced in advance is taken back the only
     * way a module that never rewrites anything can take something back: by publishing over it.
     * Both versions stay in the history, each with its own line saying what it is, so the customer
     * reading it can see that the bank changed its mind and why.
     *
     * <p>The clock is wound past the shared Monday, because "wins" is a claim about which one is in
     * force rather than about which one was written last.
     */
    @Test
    @Order(4)
    void a_version_announced_for_next_monday_is_corrected_by_publishing_another_for_that_monday() {
        LocalDate nextMonday = bank.theNextMondayStillToCome();
        Map<String, Object> announced = theSameSchemeAgain(bank.theSchemeInForce(), nextMonday,
                "A week will ask for EUR 90 from the date shown.");
        announced.put("weeklyThreshold", "90.00");
        SchemeView thisMorning = bank.publish(announced).published();

        Map<String, Object> corrected = theSameSchemeAgain(thisMorning, nextMonday,
                "Correcting this morning's announcement: a week will ask for EUR 70, not EUR 90.");
        corrected.put("weeklyThreshold", "70.00");
        SchemeView correction = bank.publish(corrected).published();

        assertThat(correction.effectiveFrom()).isEqualTo(thisMorning.effectiveFrom());
        assertThat(correction.version()).isEqualTo(thisMorning.version() + 1);
        assertThat(bank.everyVersionPublished())
                .as("both of them stay in the history, because nothing is ever deleted")
                .contains(thisMorning, correction);

        bank.theClockReaches(nextMonday);

        assertThat(bank.theSchemeInForce())
                .as("the higher version wins the Monday the two of them share")
                .isEqualTo(correction);
        assertThat(bank.theSchemeInForce().weeklyThreshold()).isEqualByComparingTo("70.00");
    }

    /**
     * Publishing leaves every version already published exactly as it was.
     *
     * <p><strong>The weakest true statement of "publishing rewrites nothing".</strong> The whole of
     * what a publish may do is add a row. The versions before it are the schemes people's weeks
     * were judged under, so the history is read before and after and compared entry for entry — and
     * a publish that reached back into any of them would be the worst bug this feature could have.
     */
    @Test
    @Order(5)
    void publishing_leaves_every_version_already_published_exactly_as_it_was() {
        List<SchemeView> historyBefore = bank.everyVersionPublished();

        Map<String, Object> form = theSameSchemeAgain(bank.theSchemeInForce(),
                bank.theNextMondayStillToCome(),
                "A further change, published over the top of everything already published.");
        form.put("theOrdinaryRate", "1.1000");
        SchemeView published = bank.publish(form).published();

        List<SchemeView> historyAfter = bank.everyVersionPublished();
        assertThat(historyAfter).hasSize(historyBefore.size() + 1);
        assertThat(historyAfter.get(0))
                .as("the history is newest first, so the one just published is at the top")
                .isEqualTo(published);
        assertThat(historyAfter.subList(1, historyAfter.size()))
                .as("every version already published, after a publish")
                .isEqualTo(historyBefore);
    }

    /**
     * Publishing a shorter life for points changes what the scheme says from its Monday, and
     * changes nothing about the versions the weeks before it were judged under.
     *
     * <p><strong>Half of a criterion, and the half this branch can honestly assert.</strong> What
     * the ticket asks for in full is that a batch of points earned after the new Monday is given
     * the new period while everything earned before keeps the twelve months it was promised — and
     * the stamping that makes that true is a sibling ticket landing in parallel, not in this tree.
     * What is asserted here is the part this ticket owns: the figure a publish writes is the figure
     * the scheme reports from that Monday on, the version that was in force over the earlier weeks
     * still reports what it always reported, and both readings are in the published history for
     * anybody to check. The end-to-end assertion about actual batches belongs to whoever merges the
     * two tickets together.
     */
    @Test
    @Order(6)
    void a_shorter_life_for_points_takes_effect_on_its_monday_and_changes_no_version_behind_it() {
        SchemeView judgedTheWeeksBehind = bank.theSchemeInForce();
        int whatPointsUsedToLast = judgedTheWeeksBehind.howLongABatchOfPointsLasts();
        assertThat(whatPointsUsedToLast)
                .as("what a batch of points lasts under the scheme in force before this publish")
                .isNotEqualTo(THREE_MONTHS);
        LocalDate nextMonday = bank.theNextMondayStillToCome();
        Map<String, Object> form = theSameSchemeAgain(judgedTheWeeksBehind, nextMonday,
                "Points earned from the date shown last three months. Nothing already earned "
                        + "moves.");
        form.put("howLongABatchOfPointsLasts", String.valueOf(THREE_MONTHS));

        SchemeView published = bank.publish(form).published();

        assertThat(bank.theSchemeInForce().howLongABatchOfPointsLasts())
                .as("what the scheme says while the new version is still ahead of today")
                .isEqualTo(whatPointsUsedToLast);

        bank.theClockReaches(nextMonday);

        assertThat(bank.theSchemeInForce()).isEqualTo(published);
        assertThat(bank.theSchemeInForce().howLongABatchOfPointsLasts())
                .as("what the scheme says for weeks from its Monday on")
                .isEqualTo(THREE_MONTHS);
        assertThat(bank.everyVersionPublished())
                .as("the version the weeks behind it were judged under, unchanged")
                .contains(judgedTheWeeksBehind);
    }

    /**
     * A Monday before one a version has already been announced for is refused, because publishing
     * it would leave that announcement permanently out of force.
     *
     * <p><strong>A conflict rather than a bad form, and the distinction is the one the products
     * module already draws.</strong> Everything typed is a perfectly good answer — a Monday, still
     * to come, with figures that are figures — and what refuses it is a version somebody has
     * already announced. The scheme in force is the highest version whose day has come, so a
     * version 8 starting a fortnight before an announced version 7 would mean version 7 never
     * decided anything on any morning: published, read by customers, and quietly dead. A module
     * whose promise is that nothing is ever deleted cannot let arithmetic delete something.
     *
     * <p>It lives here rather than with the other refusals because it needs a version to have been
     * published first, and the file next door is the one whose subject is that nothing was.
     */
    @Test
    @Order(7)
    void a_monday_before_one_already_announced_is_refused_and_the_announcement_stands() {
        LocalDate aFortnightOn = bank.theNextMondayStillToCome().plusWeeks(2);
        Map<String, Object> announced = theSameSchemeAgain(bank.theSchemeInForce(), aFortnightOn,
                "A week will ask for EUR 120 from the date shown, announced a fortnight ahead.");
        announced.put("weeklyThreshold", "120.00");
        SchemeView waiting = bank.publish(announced).published();

        Map<String, Object> earlier = theSameSchemeAgain(waiting, bank.theNextMondayStillToCome(),
                "A different figure, starting before the version already announced.");
        earlier.put("weeklyThreshold", "110.00");
        ResponseEntity<JsonNode> refused = bank.tryToPublish(earlier);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(refused))
                .contains(String.valueOf(waiting.version()), aFortnightOn.toString(),
                        bank.theNextMondayStillToCome().toString());
        assertThat(newest(bank.everyVersionPublished()))
                .as("the announcement is untouched, and nothing was written over it")
                .isEqualTo(waiting);
    }

    /** The newest version in a history, which is the first one, because it is served newest first. */
    private static SchemeView newest(List<SchemeView> history) {
        assertThat(history).as("the versions of the scheme the bank has published").isNotEmpty();
        return history.get(0);
    }
}
