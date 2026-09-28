package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.InterestPostingView;
import io.dataroots.savingstreak.support.ScheduledJobView;
import io.dataroots.savingstreak.support.TheTermOnAnAccountView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsproducts.ATermThatReachesItsDay.FREE_SAVINGS;
import static io.dataroots.savingstreak.savingsproducts.ATermThatReachesItsDay.THE_INTEREST_SWEEP;
import static io.dataroots.savingstreak.savingsproducts.ATermThatReachesItsDay.THE_MATURITY_SWEEP;
import static io.dataroots.savingstreak.savingsproducts.ATermThatReachesItsDay.THE_ONE_REPUBLISHED_TO_COME_FREE;
import static io.dataroots.savingstreak.savingsproducts.ATermThatReachesItsDay.THE_ONE_REPUBLISHED_TO_WAIT;
import static io.dataroots.savingstreak.savingsproducts.ATermThatReachesItsDay.THE_ROLLING_TERM;
import static io.dataroots.savingstreak.savingsproducts.ATermThatReachesItsDay.TWELVE_MONTHS;
import static io.dataroots.savingstreak.savingsproducts.ATermThatReachesItsDay.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A term that reaches its day is settled by the terms it was opened under: a rolling term goes
 * straight into another of the same length at the rates on offer that day, a term set to move to
 * instant access is free from that morning, and a term set to wait sits where it is earning what
 * free savings earns until somebody acts.
 *
 * <p><strong>The whole arc in one class, in order.</strong> Three accounts are opened under three
 * different endings, the clock is wound past the day all three mature, and one sweep settles all
 * three — then a second sweep settles nothing. They are one story about one morning, and told out of
 * order they would be a handful of unrelated assertions that happened to pass. The clock only moves
 * forward, which is the other reason this class is ordered.
 *
 * <p><strong>An application and a database of its own</strong>, because it winds a year and
 * publishes versions of two seeded products. {@link ATermThatReachesItsDay} argues that, and argues
 * why the two endings the catalogue does not sell are reached by publishing rather than by writing
 * rows behind the API's back.
 *
 * <p><strong>The ending the fourth account is settled by is the one it was opened under, not the one
 * on the shelf.</strong> That is the criterion this whole feature turns on and it is tested by
 * publishing a <em>fourth</em> version over the top of the waiting one, so that the account and the
 * catalogue disagree about what happens at maturity — and then watching the account do what it
 * agreed to. An implementation that read {@code TheTermsOnOfferToday} would pass every other test
 * here and fail this one.
 *
 * <p><strong>Every date is read off the application's own clock</strong> and never off the machine's.
 * A test that wrote a date down would be asserting what day it was compiled on, and this one runs on
 * a clock wound more than a year forward by the time it finishes.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AMaturityIsRolledOverMovedToInstantOrLeftWaitingApiTest extends ApiIntegrationTest {

    private static final Path DATABASE = aDatabaseFileThatDoesNotExistYet("saving-streak-maturity");

    /** What each account is opened with, out of the EUR 1 500 a new current account holds. */
    private static final String WHAT_EACH_ONE_HOLDS = "300.00";

    /** Far enough past a twelve-month maturity to be plainly past it, and no further. */
    private static final int DAYS_WOUND_PAST_MATURITY = 370;

    /**
     * Three more months, so that whole interest periods have ended on the far side of a maturity.
     *
     * <p>Comfortably short of the rolling term's <em>next</em> maturity, which is a year after the
     * first, so that winding this far does not quietly settle a second one and make the test above
     * it about something else.
     */
    private static final int A_FEW_MORE_MONTHS = 100;

    private static ATermThatReachesItsDay theirs;

    /** One account per ending, and a fourth whose product's ending changed after it was opened. */
    private static long theOneThatRollsOver;
    private static long theOneThatComesFree;
    private static long theOneThatWaits;
    private static long theOneWhoseProductChangedItsMind;

    private static LocalDate theDayTheyWereOpened;

    @BeforeAll
    static void openATermUnderEachOfTheThreeEndings() {
        theirs = new ATermThatReachesItsDay(DATABASE, "somebody whose terms are running out");
        theDayTheyWereOpened = theirs.theDateTheClockReads();

        // The one the bank actually sells, left on version 1 for the whole run.
        theOneThatRollsOver = theirs.openAnAccountOn(THE_ROLLING_TERM);

        theirs.republishAsATermThat(THE_ONE_REPUBLISHED_TO_COME_FREE, "MOVE_TO_INSTANT", "30");
        theOneThatComesFree = theirs.openAnAccountOn(THE_ONE_REPUBLISHED_TO_COME_FREE);

        theirs.republishAsATermThat(THE_ONE_REPUBLISHED_TO_WAIT, "HOLD", "30");
        theOneThatWaits = theirs.openAnAccountOn(THE_ONE_REPUBLISHED_TO_WAIT);
        theOneWhoseProductChangedItsMind = theirs.openAnAccountOn(THE_ONE_REPUBLISHED_TO_WAIT);

        // And then the bank changes its mind about the ending, after both of those accounts are
        // already living under the version that says to wait. This is the row that makes the
        // criterion testable: what the product sells today and what those accounts agreed to are
        // now different endings.
        theirs.republishAsATermThat(THE_ONE_REPUBLISHED_TO_WAIT, "ROLL_OVER", "30");

        theirs.payIn(theOneThatRollsOver, WHAT_EACH_ONE_HOLDS);
        theirs.payIn(theOneThatComesFree, WHAT_EACH_ONE_HOLDS);
        theirs.payIn(theOneThatWaits, WHAT_EACH_ONE_HOLDS);
        theirs.payIn(theOneWhoseProductChangedItsMind, WHAT_EACH_ONE_HOLDS);
    }

    @AfterAll
    static void stopIt() {
        if (theirs != null) {
            theirs.close();
        }
    }

    /**
     * The sweep is a job the application has in it, runnable by name, and it runs before the
     * interest sweep.
     *
     * <p>Both halves matter and neither is a formality. A nightly rule nobody can trigger is a rule
     * nobody can demonstrate in an afternoon, which is what the development jobs endpoint exists
     * for; and the order is the reason for the time — a term that matured overnight has to be on its
     * new terms before the month is priced, or an account that rolled onto a different rate at a
     * quarter past three would be paid for the month at the rate it stopped being on.
     */
    @Test
    @Order(1)
    void the_maturity_sweep_is_a_job_that_runs_before_the_interest_sweep() {
        List<ScheduledJobView> jobs = theirs.theJobsThereAreToRun();

        ScheduledJobView maturities = jobs.stream()
                .filter(job -> THE_MATURITY_SWEEP.equals(job.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no job called " + THE_MATURITY_SWEEP + " in " + jobs));
        ScheduledJobView interest = jobs.stream()
                .filter(job -> THE_INTEREST_SWEEP.equals(job.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no job called " + THE_INTEREST_SWEEP + " in " + jobs));

        // The schedule as the endpoint reports it, which is the sentence its author wrote with
        // the kind of schedule in front of it — a quarter past three, and the interest sweep half
        // an hour later.
        assertThat(maturities.schedule()).isEqualTo("cron 0 15 3 * * *");
        assertThat(interest.schedule()).isEqualTo("cron 0 45 3 * * *");
    }

    /**
     * All four accounts are locked into a twelve-month term that matures a year from today, and each
     * one says which ending it agreed to in the words its own terms use.
     *
     * <p>The premise everything below rests on, and the first assertion about the reading this
     * ticket adds: what happens at the end is on the term's own reading, off the version the account
     * was opened under, so a customer deciding whether to break one early is deciding against it.
     */
    @Test
    @Order(2)
    void every_account_is_locked_for_a_year_and_says_which_ending_it_agreed_to() {
        TheTermOnAnAccountView rolling = theirs.theTermOn(theOneThatRollsOver);
        assertThat(rolling.termMonths()).isEqualTo(TWELVE_MONTHS);
        assertThat(rolling.maturesOn()).isEqualTo(theDayTheyWereOpened.plusMonths(TWELVE_MONTHS));
        assertThat(rolling.locked()).isTrue();
        assertThat(rolling.maturityAction()).isEqualTo("ROLL_OVER");
        assertThat(rolling.whatHappensAtMaturity())
                .contains(theDayTheyWereOpened.plusMonths(TWELVE_MONTHS).toString())
                .contains("another " + TWELVE_MONTHS + "-month term")
                .contains("rates on offer that day");

        assertThat(theirs.theTermOn(theOneThatComesFree).maturityAction())
                .isEqualTo("MOVE_TO_INSTANT");
        assertThat(theirs.theTermOn(theOneThatComesFree).whatHappensAtMaturity())
                .contains("free savings")
                .contains("from that morning");

        assertThat(theirs.theTermOn(theOneThatWaits).maturityAction()).isEqualTo("HOLD");
        assertThat(theirs.theTermOn(theOneThatWaits).whatHappensAtMaturity())
                .contains("stays exactly where it is")
                .contains("what free savings earns");

        // An account with no term says nothing about an ending rather than inventing one, which is
        // what lets one panel render every savings account without asking what kind it is first.
        assertThat(theirs.theTermOn(theirs.openAnAccountOn(FREE_SAVINGS)).maturityAction()).isNull();
    }

    /**
     * The sweep settles nothing while the day has not come, however often it is run.
     *
     * <p>Worth its own test because it is the ordinary night, every night, for years: a sweep that
     * rolled a term over early would do it silently and nobody would find out until a withdrawal was
     * refused. The reading afterwards is what proves nothing moved.
     */
    @Test
    @Order(3)
    void a_sweep_before_the_day_has_come_settles_nothing() {
        theirs.run(THE_MATURITY_SWEEP);

        assertThat(theirs.theTermOn(theOneThatRollsOver).maturesOn())
                .isEqualTo(theDayTheyWereOpened.plusMonths(TWELVE_MONTHS));
        assertThat(theirs.theTermOn(theOneThatRollsOver).locked()).isTrue();
        assertThat(theirs.theAgreementOf(theOneThatComesFree).productCode())
                .isEqualTo(THE_ONE_REPUBLISHED_TO_COME_FREE);
    }

    /**
     * A rolling term goes into another term of the same length at the terms on offer that day, with
     * a new maturity a term further out — and the money is locked again.
     *
     * <p><strong>The new maturity is a year after the old one and not a year after the account was
     * opened.</strong> That is the whole of the roll-over: the maturity is derived from the day the
     * term runs from, and a roll-over moves that day. An implementation that left it at the opening
     * date would report a maturity that had already gone, and the account would sit permanently
     * unlocked while nominally serving a twelve-month term.
     *
     * <p>The day the account was opened is asserted to be unchanged in the same breath, because that
     * date anchors the interest periods and re-dating it was the alternative this feature rejected.
     */
    @Test
    @Order(4)
    void a_rolling_term_starts_another_term_of_the_same_length_a_term_further_out() {
        theirs.daysPass(DAYS_WOUND_PAST_MATURITY);
        LocalDate itMaturedOn = theDayTheyWereOpened.plusMonths(TWELVE_MONTHS);

        // And the bank reprices the fixed term two days AFTER that morning, which is a few days
        // before the sweep gets round to running. This is what makes "the version current on its
        // own day" a different sentence from "the version current tonight": an implementation that
        // pinned today's version would put the account on this one, which was not on offer on the
        // morning its term was up.
        theirs.republishAsATermThat(THE_ROLLING_TERM, "ROLL_OVER", "90", itMaturedOn.plusDays(2),
                "1.00");
        int theVersionItWouldHaveHadTonight = theirs.whatIsBeingSoldToday(THE_ROLLING_TERM);
        int theVersionThatWasOnOfferThatMorning = theVersionItWouldHaveHadTonight - 1;

        theirs.run(THE_MATURITY_SWEEP);

        assertThat(theirs.theAgreementOf(theOneThatRollsOver).version())
                .as("pinned to the version on offer on the morning it matured, not to tonight's")
                .isEqualTo(theVersionThatWasOnOfferThatMorning)
                .isNotEqualTo(theVersionItWouldHaveHadTonight);

        TheTermOnAnAccountView rolled = theirs.theTermOn(theOneThatRollsOver);
        assertThat(rolled.termMonths()).isEqualTo(TWELVE_MONTHS);
        assertThat(rolled.maturesOn()).isEqualTo(itMaturedOn.plusMonths(TWELVE_MONTHS));
        assertThat(rolled.matured()).isFalse();
        assertThat(rolled.locked()).isTrue();

        assertThat(theirs.theAgreementOf(theOneThatRollsOver).productCode())
                .isEqualTo(THE_ROLLING_TERM);
        assertThat(theirs.theAgreementOf(theOneThatRollsOver).openedOn())
                .as("the day the account began does not move, because the interest periods are "
                        + "counted from it")
                .isEqualTo(theDayTheyWereOpened);
        assertThat(theirs.theAgreementOf(theOneThatRollsOver).maturesOn())
                .isEqualTo(itMaturedOn.plusMonths(TWELVE_MONTHS));

        // Locked again, which is what a roll-over means when it is said as a refusal rather than as
        // a date. A term that had rolled on paper and not in the gate would let this through.
        ResponseEntity<JsonNode> refused = theirs.tryToTakeOut(theOneThatRollsOver, "1.00");
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains(itMaturedOn.plusMonths(TWELVE_MONTHS).toString());
        assertThat(theirs.balanceOf(theOneThatRollsOver))
                .isEqualByComparingTo(WHAT_EACH_ONE_HOLDS);
    }

    /**
     * A term set to move to instant access is on free savings at that product's current terms, free
     * to withdraw, from the morning of its maturity.
     *
     * <p>The version is asked of the catalogue rather than written down, because an account entering
     * an agreement enters the one on offer — which is the rule a newly opened account follows and the
     * rule breaking a term follows.
     *
     * <p>The withdrawal at the end is the assertion that matters: "free to withdraw" said as money
     * actually leaving rather than as a boolean on a reading.
     */
    @Test
    @Order(5)
    void a_term_set_to_move_to_instant_access_is_on_free_savings_and_free_to_withdraw() {
        assertThat(theirs.theAgreementOf(theOneThatComesFree).productCode())
                .isEqualTo(FREE_SAVINGS);
        assertThat(theirs.theAgreementOf(theOneThatComesFree).version())
                .isEqualTo(theirs.whatIsBeingSoldToday(FREE_SAVINGS));
        assertThat(theirs.theAgreementOf(theOneThatComesFree).maturesOn()).isNull();

        TheTermOnAnAccountView moved = theirs.theTermOn(theOneThatComesFree);
        assertThat(moved.termMonths()).isZero();
        assertThat(moved.locked()).isFalse();
        assertThat(moved.maturityAction()).isNull();

        BigDecimal before = theirs.balanceOf(theOneThatComesFree);
        ResponseEntity<JsonNode> taken = theirs.tryToTakeOut(theOneThatComesFree, "50.00");
        assertThat(taken.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(theirs.balanceOf(theOneThatComesFree))
                .isEqualByComparingTo(before.subtract(new BigDecimal("50.00")));
    }

    /**
     * A term set to wait keeps its product and its version, is not locked any more, and is settled by
     * what it agreed to rather than by what its product sells today.
     *
     * <p>Two claims in one test because they are one fact about the same two accounts: their product
     * now says to roll over, and both of them held. An implementation that read the ending off the
     * catalogue would have locked them for another year here, which is the difference between an
     * account whose money is free this morning and one whose money is not.
     *
     * <p>The maturity date is asserted to have stayed exactly where it is, because holding is the one
     * ending that moves nothing at all — which is precisely why it needs a record saying it was
     * settled.
     */
    @Test
    @Order(6)
    void a_term_set_to_wait_keeps_its_product_and_is_settled_by_what_it_agreed_to() {
        LocalDate itMaturedOn = theDayTheyWereOpened.plusMonths(TWELVE_MONTHS);

        for (long waiting : List.of(theOneThatWaits, theOneWhoseProductChangedItsMind)) {
            assertThat(theirs.theAgreementOf(waiting).productCode())
                    .isEqualTo(THE_ONE_REPUBLISHED_TO_WAIT);
            assertThat(theirs.theAgreementOf(waiting).version())
                    .as("the version it was opened under, while the product sells a later one")
                    .isLessThan(theirs.whatIsBeingSoldToday(THE_ONE_REPUBLISHED_TO_WAIT));

            TheTermOnAnAccountView held = theirs.theTermOn(waiting);
            assertThat(held.termMonths()).isEqualTo(TWELVE_MONTHS);
            assertThat(held.maturesOn()).isEqualTo(itMaturedOn);
            assertThat(held.matured()).isTrue();
            assertThat(held.locked()).isFalse();
            assertThat(held.maturityAction()).isEqualTo("HOLD");
            assertThat(held.whatHappensAtMaturity())
                    .as("said in the past tense once the day has gone")
                    .startsWith("It matured on " + itMaturedOn);
        }

        // And the money is the customer's, which is what "until somebody acts" means from their side.
        ResponseEntity<JsonNode> taken = theirs.tryToTakeOut(theOneThatWaits, "25.00");
        assertThat(taken.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /**
     * A term left waiting earns what free savings earns, not the rate it was paid for locking money
     * away.
     *
     * <p>The months before the maturity are paid at the term's own rate and the months after it at
     * free savings' — read off the postings themselves, which carry the rate each month was actually
     * paid at. An account left sitting on a matured term at the term's rate would be the best
     * instant-access account the bank sells, paid for a promise that expired.
     *
     * <p>The two rates are asked of the catalogue rather than written down, so that this test says
     * "the rate its own terms name" and "the rate free savings names" rather than restating two
     * numbers the seed happens to hold.
     */
    @Test
    @Order(7)
    void a_term_left_waiting_earns_the_free_savings_rate_from_the_morning_it_matured() {
        // Three more months, because a month is only priced once it has ended: at a year and five
        // days there are no whole months on the far side of the maturity to look at, and a test
        // asserting about "every month since" would be asserting about an empty list.
        theirs.daysPass(A_FEW_MORE_MONTHS);
        theirs.run(THE_INTEREST_SWEEP);
        LocalDate itMaturedOn = theDayTheyWereOpened.plusMonths(TWELVE_MONTHS);

        BigDecimal whatItsOwnTermsPay = theirs.versionsOf(THE_ONE_REPUBLISHED_TO_WAIT).stream()
                .filter(version -> version.version()
                        == theirs.theAgreementOf(theOneThatWaits).version())
                .findFirst().orElseThrow().annualRatePercent();
        BigDecimal whatFreeSavingsPays = theirs.versionsOf(FREE_SAVINGS).stream()
                .filter(version -> version.version() == theirs.whatIsBeingSoldToday(FREE_SAVINGS))
                .findFirst().orElseThrow().annualRatePercent();
        assertThat(whatItsOwnTermsPay)
                .as("the premise: a term pays more than instant access, or there is nothing to see")
                .isGreaterThan(whatFreeSavingsPays);

        List<InterestPostingView> months = theirs.theInterestPaidInto(theOneThatWaits);
        assertThat(months).as("a year and a bit of months").hasSizeGreaterThan(TWELVE_MONTHS);
        assertThat(months.stream().filter(month -> month.from().isBefore(itMaturedOn)).toList())
                .as("every month while the term was running")
                .isNotEmpty()
                .allSatisfy(month -> assertThat(month.annualRatePercent())
                        .isEqualByComparingTo(whatItsOwnTermsPay));
        assertThat(months.stream().filter(month -> !month.from().isBefore(itMaturedOn)).toList())
                .as("every month since it matured and was left where it is")
                .isNotEmpty()
                .allSatisfy(month -> assertThat(month.annualRatePercent())
                        .isEqualByComparingTo(whatFreeSavingsPays));
    }

    /**
     * Run twice, the sweep settles nothing the second time.
     *
     * <p><strong>The waiting term is the one that proves it.</strong> A rolled-over term has a
     * maturity in the future again and an account moved to instant access has no term at all, so
     * both would be safe with no record at all; a term left waiting keeps a maturity date that has
     * passed, and without a row saying it was settled it would be settled again every night for the
     * rest of the account's life. Its version and its maturity date being exactly what they were a
     * moment ago is what "settles nothing" means here.
     *
     * <p>The rolling account is asserted too, because the other way to fail this is to roll a term
     * that has just rolled — which would lock the money away for a second year on a morning nobody
     * was looking.
     */
    @Test
    @Order(8)
    void the_sweep_run_twice_settles_nothing_the_second_time() {
        LocalDate itMaturedOn = theDayTheyWereOpened.plusMonths(TWELVE_MONTHS);
        int theVersionTheWaitingOneIsOn = theirs.theAgreementOf(theOneThatWaits).version();

        theirs.run(THE_MATURITY_SWEEP);
        theirs.run(THE_MATURITY_SWEEP);

        assertThat(theirs.theAgreementOf(theOneThatWaits).version())
                .isEqualTo(theVersionTheWaitingOneIsOn);
        assertThat(theirs.theTermOn(theOneThatWaits).maturesOn()).isEqualTo(itMaturedOn);
        assertThat(theirs.theTermOn(theOneThatWaits).locked()).isFalse();

        assertThat(theirs.theTermOn(theOneThatRollsOver).maturesOn())
                .as("a term that has just rolled is not rolled again")
                .isEqualTo(itMaturedOn.plusMonths(TWELVE_MONTHS));

        assertThat(theirs.theAgreementOf(theOneThatComesFree).productCode())
                .isEqualTo(FREE_SAVINGS);
    }
}
