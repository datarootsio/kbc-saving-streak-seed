package io.dataroots.savingstreak.savingsproducts;

import java.nio.file.Path;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.savingsproducts.ANoticeAccountSomebodyHolds.THE_DAYS_IT_ASKS_FOR;
import static io.dataroots.savingstreak.savingsproducts.ANoticeAccountSomebodyHolds.reasonGivenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The product's condition starts meaning something: money does not leave a notice account until
 * notice has been given on it and that notice has run its thirty-two days — and the refusal says
 * how many are left rather than only that the answer is no.
 *
 * <p><strong>The whole arc in one class, in order.</strong> Refused with no notice, refused while
 * the notice is still running, allowed on the day it is ready: the three are one story about one
 * amount, and told out of order they would be three unrelated assertions that happened to pass. The
 * clock only moves forward, which is the other reason this class is ordered — nothing here can be
 * put back, exactly as the administration tests cannot unpublish a version.
 *
 * <p><strong>An application and a database of its own</strong>, because every test here winds the
 * clock through a month. {@link ANoticeAccountSomebodyHolds} argues that, and argues the way the
 * account comes to be a notice account at all.
 *
 * <p><strong>Every date is read off the application's own clock</strong> and never off the machine's.
 * A test that wrote a date down would be asserting what day it was compiled on, and this one runs
 * on a clock that has been wound sixty-four days forward by the time it finishes.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AWithdrawalFromANoticeAccountWaitsForTheNoticeToRunApiTest extends ApiIntegrationTest {

    private static final Path DATABASE = aDatabaseFileThatDoesNotExistYet("saving-streak-notice");

    /** How far into the notice period the second test stops, leaving eleven days to run. */
    private static final int DAYS_WAITED_BEFORE_ASKING_AGAIN = 21;

    private static ANoticeAccountSomebodyHolds theirs;

    private static LocalDate theDayTheyGaveNotice;

    @BeforeAll
    static void openANoticeAccountWithMoneyInIt() {
        theirs = new ANoticeAccountSomebodyHolds(DATABASE, "somebody giving notice");
        theirs.payIn("500.00");
    }

    @AfterAll
    static void stopIt() {
        if (theirs != null) {
            theirs.close();
        }
    }

    /**
     * The account is on terms that ask for thirty-two days, which is the premise everything below
     * rests on and is worth one assertion of its own.
     *
     * <p>Read off the agreement the account is living under rather than off the product's card,
     * because those are two different readings and the difference is the feature: what the bank is
     * selling today and what this account agreed to are the same sentence only on the day it was
     * opened.
     */
    @Test
    @Order(1)
    void the_account_is_living_under_terms_that_ask_for_thirty_two_days_notice() {
        assertThat(theirs.theAccount().agreement().noticeDays()).isEqualTo(THE_DAYS_IT_ASKS_FOR);
        assertThat(theirs.balance()).isEqualByComparingTo("500.00");
    }

    /**
     * Nothing has been given notice on, so nothing can be taken — and the sentence sends the
     * customer somewhere rather than stopping at no.
     *
     * <p>The balance afterwards is asserted as well as the status, which is the shape every refusal
     * test in this application takes: a refusal that had already moved the money would be a refusal
     * in name only.
     */
    @Test
    @Order(2)
    void a_withdrawal_is_refused_when_no_notice_has_been_given() {
        LocalDate today = theirs.theDateTheClockReads();

        ResponseEntity<JsonNode> refused = theirs.tryToTakeOut("100.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains("asks for 32 days' notice")
                .contains("EUR 0.00 of it is ready to take today")
                .contains("you asked for EUR 100.00")
                .contains("No notice is still running")
                .contains("give notice on EUR 100.00")
                .contains(today.plusDays(THE_DAYS_IT_ASKS_FOR).toString());
        assertThat(theirs.balance()).isEqualByComparingTo("500.00");
    }

    /**
     * Notice is given and three weeks pass, and the refusal counts the eleven days that are left.
     *
     * <p><strong>This is the assertion the ticket is named for.</strong> "You cannot have it" is a
     * refusal; "you cannot have it for another eleven days, and it is yours on the twenty-ninth" is
     * something a person can plan around, and the difference is the whole reason the sentence is
     * built by the module that knows the agreement rather than by the page that draws it.
     *
     * <p>Eleven is arithmetic on figures this test states — thirty-two days asked for, twenty-one
     * waited — rather than a number written down, so that a change to either reads as a change to
     * both.
     */
    @Test
    @Order(3)
    void the_refusal_counts_the_days_left_on_the_notice_already_given() {
        theDayTheyGaveNotice = theirs.theDateTheClockReads();
        theirs.giveNoticeOn("100.00");
        theirs.daysPass(DAYS_WAITED_BEFORE_ASKING_AGAIN);

        ResponseEntity<JsonNode> refused = theirs.tryToTakeOut("100.00");

        int stillToRun = THE_DAYS_IT_ASKS_FOR - DAYS_WAITED_BEFORE_ASKING_AGAIN;
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains("EUR 0.00 of it is ready to take today")
                .contains("The notice you gave on EUR 100.00 on " + theDayTheyGaveNotice)
                .contains("has " + stillToRun + " days left to run")
                .contains("is ready on "
                        + theDayTheyGaveNotice.plusDays(THE_DAYS_IT_ASKS_FOR));
        assertThat(theirs.balance()).isEqualByComparingTo("500.00");
    }

    /**
     * The clock is wound across the rest of the notice period and the same request goes through —
     * one request, refused and then allowed, with nothing changing but the day.
     *
     * <p>The same amount as the test above on purpose. A test that asked for a different figure
     * afterwards would leave open the possibility that the first was refused for its size.
     *
     * <p>On the morning the notice is ready rather than the morning after, because that is the
     * promise the refusal made when it named the date.
     */
    @Test
    @Order(4)
    void winding_the_clock_across_the_notice_period_turns_the_refusal_into_a_withdrawal() {
        theirs.daysPass(THE_DAYS_IT_ASKS_FOR - DAYS_WAITED_BEFORE_ASKING_AGAIN);
        assertThat(theirs.theDateTheClockReads())
                .isEqualTo(theDayTheyGaveNotice.plusDays(THE_DAYS_IT_ASKS_FOR));

        theirs.takeOut("100.00");

        assertThat(theirs.balance()).isEqualByComparingTo("400.00");
        assertThat(theirs.theNotice().readyToTakeToday()).isEqualByComparingTo("0.00");
    }

    /**
     * Notice covers what it was given on and not a cent more: a withdrawal larger than the ready
     * notice is refused, and the sentence says how much is ready and how much more to give notice
     * on.
     *
     * <p>The account plainly holds the money — four hundred euros against a request for a hundred
     * and twenty — so this is the condition refusing and not the balance, which is what makes it
     * worth asserting separately from the two above.
     */
    @Test
    @Order(5)
    void a_withdrawal_larger_than_the_ready_notice_is_refused_for_the_difference() {
        theirs.giveNoticeOn("50.00");
        theirs.daysPass(THE_DAYS_IT_ASKS_FOR);

        ResponseEntity<JsonNode> refused = theirs.tryToTakeOut("120.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused))
                .contains("EUR 50.00 of it is ready to take today")
                .contains("you asked for EUR 120.00")
                .contains("give notice on EUR 70.00");
        assertThat(theirs.balance()).isEqualByComparingTo("400.00");
    }

    /** And exactly what the notice covers goes through, on the same clock and the same account. */
    @Test
    @Order(6)
    void exactly_what_the_ready_notice_covers_goes_through() {
        theirs.takeOut("50.00");

        assertThat(theirs.balance()).isEqualByComparingTo("350.00");
    }
}
