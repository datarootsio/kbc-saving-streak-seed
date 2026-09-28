package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One warning per category and month, and one over-committed month per account, are rules the
 * database keeps rather than rules this application merely intends.
 *
 * <p>User stories 30 and 32. The sweep checks what it has already said before it says anything, and
 * that check is what normally does the work; but the check and the guarantee are different things.
 * Two runs of the job at the same moment would both read the same empty set of what had been said
 * and both write, and the once-a-month rule would be an intention. So this test writes to the record
 * directly, past the sweep and past the check, and insists that the write is refused.
 *
 * <p>Which makes it the second test in this feature that reaches below the HTTP seam on purpose
 * rather than for want of an endpoint: its subject <em>is</em> the storage. Everything it sets up
 * first — the category, the budget, the spend, the sweep — goes through the endpoints, and the rows
 * it tries to duplicate are ones the sweep itself wrote.
 *
 * <p><strong>The writes that have to be accepted matter as much as the refusals</strong>, because
 * they are what says the two indexes are about the families they are for. The other budget reason
 * about the same category and month goes in, because running low and having gone over are two
 * different things to say and a later release may want to say both; and two bills that could not be
 * paid on one account on one day go in, which is the case that makes the over-committed index
 * partial over the <em>reason</em> rather than over a column being null — those rows fill the
 * account and the day exactly as an over-committed month does.
 *
 * <p>Its own application, for the reason the anniversary index's test has one, and because this test
 * leaves rows in the record that no rule would have written.
 */
class TheRecordOfBudgetWarningsIsUniquePerCategoryAndMonthApiTest extends ApiIntegrationTest {

    private static final String THE_MONTHLY_BUDGET = "100.00";

    private static final String WHAT_GOES_OVER_IT = "250.00";

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseRecordThisTestWritesToDirectly() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-one-warning-per-budget-month"));
        theCustomer = app.aCustomerOfItsOwn("one warning per budget month");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_indexes_refuse_a_second_warning_about_one_month() {
        long currentAccount = app.currentAccountOf(theCustomer);
        long customerId = app.customerIdOf(theCustomer);
        SpendingCategoryView holiday = app.declareACategoryFor(theCustomer, "Holiday");
        app.declareABudgetFor(theCustomer, holiday.categoryId(), THE_MONTHLY_BUDGET);
        app.spendFor(theCustomer, "Flights", WHAT_GOES_OVER_IT, holiday.categoryId());
        app.runJob(TheNotificationSweep.THE_JOB);

        NotificationRepository record = app.theApplicationsOwn(NotificationRepository.class);
        Instant now = app.theClockReads();
        LocalDate theMonthBegan = app.theDateTheClockReads().withDayOfMonth(1);
        BigDecimal allowed = new BigDecimal(THE_MONTHLY_BUDGET);
        BigDecimal spent = new BigDecimal(WHAT_GOES_OVER_IT);

        assertThat(app.notificationsOf(theCustomer))
                .as("the sweep said the month had gone over, which is the row this test duplicates")
                .anySatisfy(said -> assertThat(said.reason())
                        .isEqualTo("A_BUDGET_HAS_BEEN_OVERSPENT"));

        assertThatThrownBy(() -> record.saveAndFlush(Notification.aBudgetHasBeenOverspent(
                customerId, currentAccount, holiday.categoryId(), holiday.name(), theMonthBegan,
                allowed, spent, now)))
                .as("the same category, the same reason and the same month, written past the "
                        + "sweep's own check — which is what two sweeps racing each other would do")
                .isInstanceOf(DataAccessException.class)
                // SQLite's own words, for the reason the anniversary index's test asserts on them:
                // the dialect hands this back as a JpaSystemException rather than a
                // DataIntegrityViolationException, so the type alone would also be satisfied by a
                // write that failed for some entirely different reason.
                .hasMessageContaining("UNIQUE constraint failed")
                .hasMessageContaining("notification.category_id")
                .hasMessageContaining("notification.reason")
                .hasMessageContaining("notification.occurs_on");

        assertThatCode(() -> record.saveAndFlush(Notification.aBudgetIsRunningLow(
                customerId, currentAccount, holiday.categoryId(), holiday.name(), theMonthBegan,
                allowed, spent, now)))
                .as("the other reason about the same month goes in, because the reason is part of "
                        + "the key: the two are two different things to say and not two spellings "
                        + "of one")
                .doesNotThrowAnyException();

        assertThatCode(() -> record.saveAndFlush(Notification.theMonthIsOverCommitted(
                customerId, currentAccount, theMonthBegan, new BigDecimal("2000.00"),
                new BigDecimal("1500.00"), now)))
                .as("the first over-committed warning about this account's month")
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> record.saveAndFlush(Notification.theMonthIsOverCommitted(
                customerId, currentAccount, theMonthBegan, new BigDecimal("2100.00"),
                new BigDecimal("1500.00"), now)))
                .as("and a second about the same account and the same month is refused, however "
                        + "much worse the figures have got")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("UNIQUE constraint failed")
                .hasMessageContaining("notification.current_account_id")
                .hasMessageContaining("notification.occurs_on");

        assertThatCode(() -> {
            record.saveAndFlush(Notification.aBillCouldNotBePaid(customerId, currentAccount, 1L,
                    "Rent", theMonthBegan, new BigDecimal("900.00"), new BigDecimal("10.00"), now));
            record.saveAndFlush(Notification.aBillCouldNotBePaid(customerId, currentAccount, 2L,
                    "Energy", theMonthBegan, new BigDecimal("95.00"), new BigDecimal("10.00"), now));
        })
                .as("two bills that could not be paid on one account on one day fill the account "
                        + "and the day exactly as an over-committed month does, and they are two "
                        + "honest rows — which is why that index is partial over the reason itself "
                        + "rather than over a column being null")
                .doesNotThrowAnyException();
    }
}
