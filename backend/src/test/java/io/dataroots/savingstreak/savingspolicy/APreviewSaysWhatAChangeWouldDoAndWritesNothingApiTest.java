package io.dataroots.savingstreak.savingspolicy;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SchemePreviewView;
import io.dataroots.savingstreak.support.SchemeView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingspolicy.ASchemeSomebodyAdministers.reasonGivenBy;
import static io.dataroots.savingstreak.savingspolicy.ASchemeSomebodyAdministers.theSameSchemeAgain;
import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Looking is free: an administrator can post a candidate scheme to a door that tells them what it
 * would do and writes nothing at all.
 *
 * <p><strong>The discriminating claim is the last one in the file, and every other test here is a
 * reason to want it.</strong> A preview that wrote would be a preview nobody could afford to run —
 * it would publish a version, raise notifications at people, expire batches of points and move
 * money, all from a screen whose button says "see what this would do". So this class takes a
 * photograph of everything the application will say about itself, takes four previews of four
 * different candidates, and asserts the photograph is unchanged. Not a check on six named readings:
 * a sweep of the API, because the failure being guarded against is the one nobody thought to name.
 *
 * <p><strong>The figures are counterfactual and the response says so itself.</strong> Because a week
 * is judged by the scheme in force on its own Monday, nothing at all happens to anybody's run on the
 * day a version takes effect — so "what changes on the effective date" is answered by an empty list
 * for every candidate however violent, and the question worth asking is the other one: what would
 * every customer's run read today had this been the rule for the last twenty-six weeks. That
 * sentence is a field of the answer rather than a caption on a page, and this class asserts it is
 * there, because a counterfactual figure that travels unlabelled is read as a forecast.
 *
 * <p><strong>Three weeks of EUR 60, and one.</strong> Anke secures three consecutive weeks at EUR 60
 * and Bram secures the last of them only. Both are comfortably over the EUR 50 a week asks for while
 * they are being lived through, and both are twenty euros short of the EUR 80 the candidate would
 * ask for — so under the counterfactual Anke loses three weeks and Bram loses one, which is what
 * makes "worst first" a claim with two rows to order rather than one.
 *
 * <p><strong>Its own application on its own file</strong>, because winding a clock cannot be undone
 * and a preview must be watched against a ledger this test built. {@link ASchemeSomebodyAdministers}
 * argues both at length.
 *
 * <p>Everything is arranged once, in {@link #threeWeeksSavedAndFourCandidatesLookedAt()}, because
 * the clock only goes forward and the four previews below are four questions asked of one moment.
 */
class APreviewSaysWhatAChangeWouldDoAndWritesNothingApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-a-preview-writes-nothing");

    /** Seven days on from a Monday, which is the next Monday whatever the clocks did in between. */
    private static final long A_WEEK = 7;

    /** Over the EUR 50 a week asks for today, and under the EUR 80 the candidate would ask for. */
    private static final String SIXTY_A_WEEK = "60.00";

    /** What the candidate raises a week to, which is what un-secures every week above. */
    private static final String THE_RAISED_MINIMUM = "80.00";

    /** The five lines the scheme draws, which every preview reports on whether they move or not. */
    private static final List<String> THE_FIVE_LINES = List.of(
            "A_BALANCE_RUNG", "A_BUDGET_RUNNING_LOW", "ARREARS_PILING_UP", "A_MATURITY_COMING_SOON",
            "AN_ANNIVERSARY_COMING_SOON");

    private static ASchemeSomebodyAdministers bank;
    private static SchemeView asItStands;
    private static LocalDate theMondayACandidateWouldStartOn;

    private static SchemePreviewView ofTheVersionInForce;
    private static SchemePreviewView ofARaisedMinimum;
    private static SchemePreviewView ofAMoreGenerousLadder;
    private static SchemePreviewView ofALowerBottomRung;

    private static String everythingBefore;
    private static String everythingAfter;

    @BeforeAll
    static void threeWeeksSavedAndFourCandidatesLookedAt() {
        bank = new ASchemeSomebodyAdministers(DATABASE);
        long anke = bank.savingsAccountOf(ANKE);
        long bram = bank.savingsAccountOf(BRAM);

        // Onto a Monday first, so that which savings week each deposit lands in is not a function of
        // the weekday the test run happened to start on.
        bank.theClockReaches(bank.theNextMondayStillToCome());
        bank.deposit(anke, ANKE, SIXTY_A_WEEK);
        bank.daysPass(A_WEEK);
        bank.deposit(anke, ANKE, SIXTY_A_WEEK);
        bank.daysPass(A_WEEK);
        bank.deposit(anke, ANKE, SIXTY_A_WEEK);
        bank.deposit(bram, BRAM, SIXTY_A_WEEK);

        asItStands = bank.theSchemeInForce();
        theMondayACandidateWouldStartOn = bank.theNextMondayStillToCome();

        everythingBefore = bank.everythingTheApplicationSays();

        ofTheVersionInForce = bank.preview(theSameSchemeAgain(asItStands,
                theMondayACandidateWouldStartOn,
                "Exactly the scheme already in force, republished on a later Monday."));

        Map<String, Object> raisingTheBar = theSameSchemeAgain(asItStands,
                theMondayACandidateWouldStartOn, "A week would ask for EUR 80 instead of EUR 50.");
        raisingTheBar.put("weeklyThreshold", THE_RAISED_MINIMUM);
        ofARaisedMinimum = bank.preview(raisingTheBar);

        Map<String, Object> payingMore = theSameSchemeAgain(asItStands,
                theMondayACandidateWouldStartOn,
                "Each further week would add 0,20 instead of 0,10.");
        payingMore.put("extraForEachFurtherWeek", "0.20");
        ofAMoreGenerousLadder = bank.preview(payingMore);

        Map<String, Object> aLowerBottomRung = theSameSchemeAgain(asItStands,
                theMondayACandidateWouldStartOn,
                "The ladder would start congratulating people at EUR 50 instead of EUR 100.");
        aLowerBottomRung.put("balanceRungs",
                List.of("50", "500", "1000", "2500", "5000", "10000"));
        ofALowerBottomRung = bank.preview(aLowerBottomRung);

        everythingAfter = bank.everythingTheApplicationSays();
    }

    @AfterAll
    static void stopIt() {
        if (bank != null) {
            bank.close();
        }
    }

    /**
     * The claim the whole feature rests on: four previews were taken and nobody can tell.
     *
     * <p>Every reading this application serves, before and after, character for character. A version
     * row written, a notification raised, a batch expired, a point credited or a euro moved would
     * each show up in this one comparison — and so would anything else, which is the point of
     * comparing everything rather than a list somebody wrote down.
     */
    @Test
    void aPreviewWritesNothingAtAll() {
        assertThat(everythingAfter)
                .as("everything the application says, after four previews were taken of it")
                .isEqualTo(everythingBefore);
    }

    /**
     * The known answer the tool is checked against: preview the version already in force and it
     * reports that nothing changes.
     *
     * <p>A preview that could not get this right would be a preview nothing else it said could be
     * trusted. Not one figure differs, not one run reads differently, and not one of the five lines
     * the scheme draws moves anybody across it — although the candidate is dated on a later Monday
     * and carries a different line saying what changed, because neither of those is a figure the
     * bank pays anybody by.
     */
    @Test
    void aPreviewOfTheVersionAlreadyInForceReportsThatNothingChanges() {
        assertThat(ofTheVersionInForce.itWouldChangeNothing()).isTrue();
        assertThat(ofTheVersionInForce.figures())
                .as("the figures of the version in force, against themselves")
                .allSatisfy(figure -> {
                    assertThat(figure.itWouldChange()).as(figure.figure()).isFalse();
                    assertThat(figure.asItWouldRead()).isEqualTo(figure.asItReadsNow());
                });
        assertThat(ofTheVersionInForce.runs().runsThatWouldReadDifferently()).isZero();
        assertThat(ofTheVersionInForce.runs().whoWouldGain()).isZero();
        assertThat(ofTheVersionInForce.runs().whoWouldLose()).isZero();
        assertThat(ofTheVersionInForce.runs().whoAreUntouched())
                .isEqualTo(ofTheVersionInForce.runs().customersExamined());
        assertThat(ofTheVersionInForce.runs().theLargestFallInWeeks()).isZero();
        assertThat(ofTheVersionInForce.theWorstAffected()).isEmpty();
        assertThat(ofTheVersionInForce.notifications())
                .allSatisfy(line -> assertThat(line.wouldBeToldAndIsNot() + line.isToldAndWouldNotBe())
                        .as(line.line()).isZero());
    }

    /**
     * The preview says, in itself, that it is a counterfactual over the last twenty-six weeks and
     * not a prediction of the effective date.
     *
     * <p>In the body rather than on a screen, because a page can forget a caption and a figure
     * pasted into an email has already lost one. The Monday it reaches back to is named, so the
     * claim can be checked rather than believed: twenty-six weeks counting the week running now, so
     * twenty-five Mondays before this one.
     */
    @Test
    void thePreviewSaysOfItselfThatItIsATwentySixWeekCounterfactual() {
        assertThat(ofARaisedMinimum.overHowManyWeeks()).isEqualTo(26);
        assertThat(ofARaisedMinimum.whatThisIs())
                .contains("counterfactual")
                .contains("not a prediction of the effective date")
                .contains("26 weeks")
                .contains(ofARaisedMinimum.asIfItHadBeenTheRuleSince().toString());
        assertThat(ofARaisedMinimum.asIfItHadBeenTheRuleSince())
                .isEqualTo(bank.theDateTheClockReads()
                        .with(java.time.temporal.TemporalAdjusters
                                .previousOrSame(java.time.DayOfWeek.MONDAY))
                        .minusWeeks(25));
    }

    /**
     * The difference from the version in force, figure by figure, so that an administrator can check
     * they changed what they meant to change.
     *
     * <p>Every figure the scheme pays anybody by is on the list whether it moved or not — a table
     * that only showed what changed would be a table nobody could scan for what did not — and the
     * one that moved says so, with both readings beside each other.
     */
    @Test
    void thePreviewReportsTheDifferenceFromTheVersionInForceFigureByFigure() {
        assertThat(ofARaisedMinimum.figures()).extracting(SchemePreviewView.AFigureView::figure)
                .containsExactly("weeklyThreshold", "theOrdinaryRate", "extraForEachFurtherWeek",
                        "theMostAStreakPays", "howLongABatchOfPointsLasts", "balanceRungs",
                        "whatShareOfABudgetIsRunningLow", "howManyOutstandingIsASpiral",
                        "daysBeforeAMaturityIsWorthSaying", "daysBeforeAnAnniversaryIsWorthSaying");
        assertThat(ofARaisedMinimum.figures())
                .filteredOn(SchemePreviewView.AFigureView::itWouldChange)
                .singleElement()
                .satisfies(moved -> {
                    assertThat(moved.figure()).isEqualTo("weeklyThreshold");
                    assertThat(moved.asItReadsNow())
                            .isEqualTo(asItStands.weeklyThreshold().toPlainString());
                    assertThat(moved.asItWouldRead()).isEqualTo(THE_RAISED_MINIMUM);
                });
        assertThat(ofARaisedMinimum.itWouldChangeNothing()).isFalse();
        assertThat(ofARaisedMinimum.theVersionInForce().version())
                .isEqualTo(asItStands.version());
        assertThat(ofARaisedMinimum.theVersionThisWouldBecome().version())
                .as("the number the publish would give it, answered by the module")
                .isEqualTo(asItStands.version() + 1);
        assertThat(ofARaisedMinimum.theVersionThisWouldBecome().effectiveFrom())
                .isEqualTo(theMondayACandidateWouldStartOn);
    }

    /**
     * The size of what publishing it would do: how many runs read differently, how many gain, how
     * many lose, how many are untouched, and the worst single fall of each kind.
     *
     * <p>Anke's three weeks of EUR 60 and Bram's one are all short of the EUR 80 the candidate would
     * ask for, so under the counterfactual neither of them has secured anything: Anke falls three
     * weeks and from the third rung of the ladder to its first, Bram falls one week and stays on the
     * rate a first week pays. Nobody gains, and the largest fall is Anke's rather than an average of
     * the two.
     */
    @Test
    void thePreviewRollsUpHowManyGainHowManyLoseAndTheWorstFall() {
        assertThat(ofARaisedMinimum.runs().customersExamined()).isGreaterThanOrEqualTo(2);
        assertThat(ofARaisedMinimum.runs().runsThatWouldReadDifferently()).isEqualTo(2);
        assertThat(ofARaisedMinimum.runs().whoWouldLose()).isEqualTo(2);
        assertThat(ofARaisedMinimum.runs().whoWouldGain()).isZero();
        assertThat(ofARaisedMinimum.runs().whoAreUntouched())
                .isEqualTo(ofARaisedMinimum.runs().customersExamined() - 2);
        assertThat(ofARaisedMinimum.runs().theLargestFallInWeeks()).isEqualTo(3);
        assertThat(ofARaisedMinimum.runs().theLargestFallInRate())
                .isEqualByComparingTo(new BigDecimal("0.20"));
    }

    /**
     * The number has faces behind it: the worst-affected customers, named, worst first, capped.
     *
     * <p>Anke before Bram, because three weeks is a worse morning than one, and both with the run
     * they are on, the best they have ever had and the rate they are paid — as it reads today and as
     * it would read. "Eleven customers lose weeks" is a figure somebody signs off; "Anke Peeters
     * goes from three weeks to none, and from 1,20x to 1,00x" is a sentence somebody reads twice.
     */
    @Test
    void thePreviewNamesTheWorstAffectedCustomersWorstFirst() {
        assertThat(ofARaisedMinimum.theWorstAffected()).hasSizeLessThanOrEqualTo(20);
        assertThat(ofARaisedMinimum.theWorstAffected())
                .extracting(SchemePreviewView.AnAffectedCustomerView::name)
                .containsExactly(ANKE, BRAM);
        assertThat(ofARaisedMinimum.theWorstAffected().get(0)).satisfies(anke -> {
            assertThat(anke.currentRunNow()).isEqualTo(3);
            assertThat(anke.currentRunWouldRead()).isZero();
            assertThat(anke.bestRunNow()).isEqualTo(3);
            assertThat(anke.bestRunWouldRead()).isZero();
            assertThat(anke.rateNow()).isEqualByComparingTo(new BigDecimal("1.20"));
            assertThat(anke.rateWouldRead()).isEqualByComparingTo(new BigDecimal("1.00"));
            assertThat(anke.theFallInWeeks()).isEqualTo(3);
            assertThat(anke.theFallInRate()).isEqualByComparingTo(new BigDecimal("0.20"));
        });
        assertThat(ofARaisedMinimum.theWorstAffected().get(1)).satisfies(bram -> {
            assertThat(bram.currentRunNow()).isEqualTo(1);
            assertThat(bram.currentRunWouldRead()).isZero();
            assertThat(bram.theFallInWeeks()).isEqualTo(1);
        });
    }

    /**
     * A candidate that pays more is reported as a gain rather than as a change of unknown sign.
     *
     * <p>The same three weeks, a ladder adding 0,20 a week instead of 0,10: Anke's run is the length
     * it was and pays 1,40x instead of 1,20x, which is a customer better off. Bram is on his first
     * week and a first week pays the ordinary rate under either ladder, so he is untouched — the
     * figure has to distinguish "nobody is worse off" from "nothing happened to anybody".
     */
    @Test
    void aCandidateThatPaysMoreIsReportedAsAGain() {
        assertThat(ofAMoreGenerousLadder.runs().whoWouldGain()).isEqualTo(1);
        assertThat(ofAMoreGenerousLadder.runs().whoWouldLose()).isZero();
        assertThat(ofAMoreGenerousLadder.runs().theLargestFallInWeeks()).isZero();
        assertThat(ofAMoreGenerousLadder.runs().theLargestFallInRate())
                .as("a candidate nobody loses under has no largest fall, and never a negative one")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ofAMoreGenerousLadder.theWorstAffected()).singleElement().satisfies(anke -> {
            assertThat(anke.name()).isEqualTo(ANKE);
            assertThat(anke.currentRunWouldRead()).isEqualTo(anke.currentRunNow());
            assertThat(anke.rateNow()).isEqualByComparingTo(new BigDecimal("1.20"));
            assertThat(anke.rateWouldRead()).isEqualByComparingTo(new BigDecimal("1.40"));
            assertThat(anke.theFallInRate()).isEqualByComparingTo(new BigDecimal("-0.20"));
        });
    }

    /**
     * Points: nothing already earned changes its expiry, said in words, and what a batch earned from
     * the effective date would last instead.
     *
     * <p>The first half is an absence of a difference and cannot be read off a table, so it is a
     * sentence. Without it an administrator shortening the lifetime is left to infer that they are
     * not about to kill months of everybody's points tonight, and inferring it is exactly what
     * nobody should have to do.
     */
    @Test
    void thePreviewSaysPointsAlreadyEarnedKeepTheirExpiryAndWhatANewBatchWouldLast() {
        Map<String, Object> shorterPoints = theSameSchemeAgain(asItStands,
                theMondayACandidateWouldStartOn,
                "A batch of points would last six months instead of twelve.");
        shorterPoints.put("howLongABatchOfPointsLasts", "6");
        SchemePreviewView ofShorterPoints = bank.preview(shorterPoints);

        assertThat(ofShorterPoints.points().nothingAlreadyEarnedChangesItsExpiry()).isTrue();
        assertThat(ofShorterPoints.points().howLongABatchLastsNow())
                .isEqualTo(asItStands.howLongABatchOfPointsLasts());
        assertThat(ofShorterPoints.points().howLongABatchWouldLast()).isEqualTo(6);
        assertThat(ofShorterPoints.points().aBatchEarnedOnTheEffectiveDateWouldDieOn())
                .isEqualTo(theMondayACandidateWouldStartOn.plusMonths(6));
        assertThat(ofShorterPoints.points().saidPlainly())
                .contains("No batch of points already earned changes its expiry")
                .contains("would last 6 months instead of "
                        + asItStands.howLongABatchOfPointsLasts());
    }

    /**
     * Per line the scheme draws, how many customers would be told something tonight who are not
     * being told it now, and how many the reverse — counted, and never raised.
     *
     * <p>All five lines are reported whether they move anybody or not, because a line reading nought
     * is a line that was looked at. Dropping the bottom rung to EUR 50 puts Bram's EUR 60 on a rung
     * he stands on none of today, and moves Anke's EUR 180 from the EUR 100 rung to the EUR 50 one —
     * which is both a thing she would be told and a thing she is being told that she would not be,
     * and she is honestly counted on both sides.
     *
     * <p>That nothing was raised by asking is the subject of {@link #aPreviewWritesNothingAtAll()},
     * which compares every customer's notifications before and after.
     */
    @Test
    void thePreviewCountsWhoWouldBeToldSomethingTonightAndWhoWouldNot() {
        assertThat(ofALowerBottomRung.notifications())
                .extracting(SchemePreviewView.ALineView::line)
                .containsExactlyElementsOf(THE_FIVE_LINES);
        assertThat(ofALowerBottomRung.notifications())
                .filteredOn(line -> "A_BALANCE_RUNG".equals(line.line()))
                .singleElement()
                .satisfies(rungs -> {
                    assertThat(rungs.wouldBeToldAndIsNot())
                            .as("Anke onto a lower rung and Bram onto one at all")
                            .isEqualTo(2);
                    assertThat(rungs.isToldAndWouldNotBe())
                            .as("Anke off the EUR 100 rung she stands on today")
                            .isEqualTo(1);
                });
        assertThat(ofALowerBottomRung.notifications())
                .filteredOn(line -> !"A_BALANCE_RUNG".equals(line.line()))
                .allSatisfy(quiet -> assertThat(
                        quiet.wouldBeToldAndIsNot() + quiet.isToldAndWouldNotBe())
                        .as(quiet.line()).isZero());
    }

    /**
     * A candidate that would be refused on publish is refused here in the same words, so that
     * preview and publish never disagree about what is sayable.
     *
     * <p>Word for word, asserted by asking both doors the same question and comparing their answers
     * rather than by quoting a sentence into this test — a test holding its own copy of the refusal
     * would go on passing the day one door's wording moved. Three refusals, one from each family:
     * a figure the scheme cannot carry, a ladder that descends, and a day a version cannot take
     * effect on.
     */
    @Test
    void aCandidateRefusedOnPublishIsRefusedInThePreviewInTheSameWords() {
        Map<String, Object> aDescendingLadder = theSameSchemeAgain(asItStands,
                theMondayACandidateWouldStartOn, "A cap below the ordinary rate.");
        aDescendingLadder.put("theMostAStreakPays", "0.90");

        Map<String, Object> aWednesday = theSameSchemeAgain(asItStands,
                theMondayACandidateWouldStartOn.plusDays(2), "Not a Monday.");

        Map<String, Object> noLineSayingWhatChanged =
                theSameSchemeAgain(asItStands, theMondayACandidateWouldStartOn, "   ");

        for (Map<String, Object> refusable :
                List.of(aDescendingLadder, aWednesday, noLineSayingWhatChanged)) {
            ResponseEntity<JsonNode> previewed = bank.tryToPreview(refusable);
            ResponseEntity<JsonNode> published = bank.tryToPublish(refusable);
            assertThat(previewed.getStatusCode()).as("previewing %s", refusable)
                    .isEqualTo(published.getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(reasonGivenBy(previewed))
                    .as("the preview and the publish refusing the same candidate")
                    .isEqualTo(reasonGivenBy(published));
        }
    }

    /**
     * There is still no read door under the administration prefix, and the preview did not become
     * one.
     *
     * <p>The administration screen is drawn from the customer's own reads: the version in force and
     * the whole published history are served to anybody who asks, and a second read returning an
     * identical answer would be a second shape to keep in agreement with the first for no gain. A
     * GET at the preview's address is a request nobody built a door for.
     */
    @Test
    void thereIsNoReadDoorAtThePreviewsAddress() {
        assertThat(bank.tryTo(HttpMethod.GET, "/api/admin/scheme/preview").getStatusCode())
                .as("GET /api/admin/scheme/preview")
                .isNotEqualTo(HttpStatus.OK);
        for (HttpMethod verb : List.of(HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            assertThat(bank.tryTo(verb, "/api/admin/scheme/preview").getStatusCode())
                    .as("%s /api/admin/scheme/preview", verb)
                    .isNotEqualTo(HttpStatus.OK);
        }
    }
}
