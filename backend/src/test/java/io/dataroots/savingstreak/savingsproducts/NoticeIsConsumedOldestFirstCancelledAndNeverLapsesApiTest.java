package io.dataroots.savingstreak.savingsproducts;

import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.NoticeView;
import io.dataroots.savingstreak.support.TheNoticeOnAnAccountView;
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
 * The four rules about a notice once it has been given: several stand at once, a withdrawal spends
 * the oldest ready one first and takes part of one rather than wasting it, a ready notice waits
 * indefinitely for its holder, and cancelling one leaves nothing standing behind it.
 *
 * <p><strong>Ordered and cumulative, because they are one account's history.</strong> Two notices
 * are given, a withdrawal eats the first and half the second, four months pass without the
 * remainder going stale, and the last thing the customer does is change their mind. Each test picks
 * up where the one before it left off, which is what an account actually does and is the only way
 * to assert "does not lapse" at all — it needs a notice that became ready a long time ago.
 *
 * <p><strong>An application and a database of its own</strong>, because it winds five months. The
 * harness argues that, and argues how the account comes to be a notice account.
 *
 * <p>Nothing here reads a table. What is standing, what is ready and what a withdrawal spent are
 * all read back through the same endpoint a screen would use, which is why the consumption rule is
 * asserted as the arithmetic a customer sees rather than as a column.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NoticeIsConsumedOldestFirstCancelledAndNeverLapsesApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-notice-consumed");

    /** Long enough after a notice is ready that "it does not lapse" is a claim about months. */
    private static final int A_LONG_TIME_TO_LEAVE_IT = 120;

    private static ANoticeAccountSomebodyHolds theirs;

    private static long theOlderNotice;
    private static long theNewerNotice;

    @BeforeAll
    static void openANoticeAccountWithMoneyInIt() {
        theirs = new ANoticeAccountSomebodyHolds(DATABASE, "somebody planning two things");
        theirs.payIn("800.00");
    }

    @AfterAll
    static void stopIt() {
        if (theirs != null) {
            theirs.close();
        }
    }

    /**
     * Two amounts, given notice on the same morning, stand as two notices and are never merged into
     * one.
     *
     * <p>Merging would be the tidier row and the wrong answer: two things a customer is saving for
     * are planned separately, and adding the second onto the first would move money they gave
     * notice of first to the back of the queue. The reading lists them in the order they were given,
     * which is the order a withdrawal will spend them in.
     */
    @Test
    @Order(1)
    void notice_can_be_given_on_several_amounts_at_once() {
        theOlderNotice = theirs.giveNoticeOn("200.00").id();
        theNewerNotice = theirs.giveNoticeOn("300.00").id();

        TheNoticeOnAnAccountView standing = theirs.theNotice();

        assertThat(standing.noticeDays()).isEqualTo(THE_DAYS_IT_ASKS_FOR);
        assertThat(standing.readyToTakeToday()).isEqualByComparingTo("0.00");
        assertThat(standing.stillWaiting()).isEqualByComparingTo("500.00");
        assertThat(standing.notices()).extracting(NoticeView::id)
                .containsExactly(theOlderNotice, theNewerNotice);
        assertThat(standing.notices()).allSatisfy(given -> {
            assertThat(given.ready()).isFalse();
            assertThat(given.daysLeft()).isEqualTo(THE_DAYS_IT_ASKS_FOR);
            assertThat(given.readyOn()).isEqualTo(given.givenOn().plusDays(THE_DAYS_IT_ASKS_FOR));
        });
    }

    /**
     * Both notices run their course, and a withdrawal of two hundred and fifty spends the older one
     * whole and fifty euros of the newer.
     *
     * <p><strong>Partly consumed rather than wasted</strong>, which is the half of the rule worth
     * the test: the newer notice was given on three hundred, fifty of it paid for this withdrawal,
     * and two hundred and fifty of it is still good. Throwing the remainder away would be a penalty
     * nobody agreed to and would push customers into giving notice in small pieces to avoid it.
     *
     * <p>The older notice is gone from the reading altogether rather than listed as spent, because
     * what is standing is what the customer still has — its row survives for the sake of a later
     * question, and that is a different question from this one.
     */
    @Test
    @Order(2)
    void a_withdrawal_spends_the_oldest_ready_notice_first_and_partly_spends_the_next() {
        theirs.daysPass(THE_DAYS_IT_ASKS_FOR);
        assertThat(theirs.theNotice().readyToTakeToday()).isEqualByComparingTo("500.00");

        theirs.takeOut("250.00");

        TheNoticeOnAnAccountView standing = theirs.theNotice();
        assertThat(theirs.balance()).isEqualByComparingTo("550.00");
        assertThat(standing.notices()).singleElement().satisfies(left -> {
            assertThat(left.id()).isEqualTo(theNewerNotice);
            assertThat(left.amount()).isEqualByComparingTo("300.00");
            assertThat(left.stillStanding()).isEqualByComparingTo("250.00");
            assertThat(left.ready()).isTrue();
        });
        assertThat(standing.readyToTakeToday()).isEqualByComparingTo("250.00");
        assertThat(standing.stillWaiting()).isEqualByComparingTo("0.00");
    }

    /**
     * Four months pass with the remainder untouched, and it is exactly as good as it was.
     *
     * <p><strong>A ready notice does not lapse</strong>, and a window inside which noticed money had
     * to be taken would be a rule invented in order to have a rule. Missing the day you planned for
     * is not a way to lose the month you have already waited — so the days left stay at nought, the
     * figure stays at two hundred and fifty, and the withdrawal goes through on a day nobody
     * promised anything about.
     */
    @Test
    @Order(3)
    void a_ready_notice_does_not_lapse() {
        theirs.daysPass(A_LONG_TIME_TO_LEAVE_IT);

        TheNoticeOnAnAccountView standing = theirs.theNotice();

        assertThat(standing.readyToTakeToday()).isEqualByComparingTo("250.00");
        assertThat(standing.notices()).singleElement().satisfies(left -> {
            assertThat(left.ready()).isTrue();
            assertThat(left.daysLeft()).isZero();
            assertThat(left.stillStanding()).isEqualByComparingTo("250.00");
        });

        theirs.takeOut("250.00");

        assertThat(theirs.balance()).isEqualByComparingTo("300.00");
        assertThat(theirs.theNotice().notices()).isEmpty();
        assertThat(theirs.theNotice().readyToTakeToday()).isEqualByComparingTo("0.00");
    }

    /**
     * A change of plan leaves nothing standing: the cancelled notice covers nothing, disappears
     * from what is standing, and the withdrawal it would have paid for is refused again.
     *
     * <p>Cancelled after it had become ready, which is the harder case and the one that matters:
     * money that was a press away from leaving stops being so. A cancellation that only worked on a
     * notice still running would be a rule about when you are allowed to change your mind.
     *
     * <p>Cancelling it twice is a conflict rather than a second success, because the second press
     * undoes nothing and answering "done" would tell a customer they had changed something they
     * had not.
     */
    @Test
    @Order(4)
    void cancelling_a_notice_leaves_nothing_standing_behind_it() {
        long changedTheirMind = theirs.giveNoticeOn("100.00").id();
        theirs.daysPass(THE_DAYS_IT_ASKS_FOR);
        assertThat(theirs.theNotice().readyToTakeToday()).isEqualByComparingTo("100.00");

        NoticeView cancelled = theirs.cancel(changedTheirMind);

        assertThat(cancelled.stillStanding()).isEqualByComparingTo("0.00");
        assertThat(theirs.theNotice().notices()).isEmpty();
        assertThat(theirs.theNotice().readyToTakeToday()).isEqualByComparingTo("0.00");

        ResponseEntity<JsonNode> refused = theirs.tryToTakeOut("100.00");
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonGivenBy(refused)).contains("EUR 0.00 of it is ready to take today");
        assertThat(theirs.balance()).isEqualByComparingTo("300.00");

        ResponseEntity<JsonNode> again = theirs.tryToCancel(changedTheirMind);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonGivenBy(again)).contains("nothing left standing to cancel");
    }

    /** A notice numbered for nobody is an absence, in the status an absence answers with. */
    @Test
    @Order(5)
    void a_notice_this_account_never_gave_is_not_found() {
        ResponseEntity<JsonNode> refused = theirs.tryToCancel(987_654L);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonGivenBy(refused)).contains("There is no notice numbered 987654");
    }
}
