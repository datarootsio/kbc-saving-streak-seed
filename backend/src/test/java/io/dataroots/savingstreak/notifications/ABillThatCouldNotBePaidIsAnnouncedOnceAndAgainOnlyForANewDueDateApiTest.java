package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.NotificationView;
import io.dataroots.savingstreak.support.RecurringBillView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rent that could not be paid is announced once, on the night it went unpaid, and never again for
 * that date — and a new date that goes unpaid months later is announced on its own account.
 *
 * <p>User stories 22 and 23, which are one promise read from two ends. A customer who is never told
 * cannot act before the arrears build; a customer told once a night stops reading anything the
 * application says to them. The feature's own testing decisions name "a bill owed across six runs
 * raising one notification rather than six" as one of the cases most likely to be got wrong, and
 * this is that case written deliberately.
 *
 * <p><strong>The arrear is reached the way the lab says to reach one</strong>: a sweep left standing
 * on the savings account, and the clock wound past the day the rent falls due. The rules run at two
 * and take the money; the bills run at half past finds what is left. The floor is deliberately not
 * nothing — the account is left holding two hundred euros against a nine hundred euro rent — because
 * the notification is supposed to carry <em>the balance that fell short</em>, and a balance of
 * nought would pass whether that figure were read or invented.
 *
 * <p><strong>Six runs over one due date, and the sixth says exactly what the first did.</strong>
 * Nothing moves in between: the rules are left alone, so the only thing touching the account is the
 * billing run itself, and the only thing touching the notifications is the raiser. Then the hole is
 * closed with money brought back from savings and the next month's rent is missed in its turn, which
 * is a different due date, a different row and a new thing to say.
 *
 * <p>One test, because the clock only goes forward and the narrative is a sequence of months. The
 * claim is asserted after every step instead.
 *
 * <p>Its own application and its own customer, for the reason
 * {@link AnApplicationWithAClockToMove#aCustomerOfItsOwn} gives: the seeded pair carry households of
 * their own that the very same nightly runs credit and debit.
 */
class ABillThatCouldNotBePaidIsAnnouncedOnceAndAgainOnlyForANewDueDateApiTest
        extends ApiIntegrationTest {

    private static final String THE_BILLS_JOB = "takeBillsDue";
    private static final String THE_SAVING_RULES_JOB = "fireSavingRulesDue";

    private static final String THE_RENT = "900.00";

    /** What the sweep leaves behind, and therefore what the rent is measured against. */
    private static final String WHAT_THE_SWEEP_LEAVES = "200.00";

    private static final int THE_TWELFTH = 12;

    private static final int HOW_MANY_NIGHTS_THE_RENT_IS_CARRIED = 6;

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseMonthsThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-one-notification-per-due-date"));
        theCustomer = app.aCustomerOfItsOwn("told once when a bill goes unpaid");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_rent_carried_for_six_nights_is_announced_once_and_the_next_months_rent_on_its_own() {
        long savingsAccount = app.savingsAccountOf(theCustomer);
        long currentAccount = app.currentAccountOf(theCustomer);

        assertThat(app.notificationsOf(theCustomer))
                .as("a customer nothing has happened to has been told nothing, or the rest of this "
                        + "test would be counting somebody else's rows")
                .isEmpty();

        // Onto the first of a month before anything is declared, so that the rent's first day is
        // eleven nights off rather than however many the wall clock happens to leave — a weekly
        // sweep that had not come round by then would leave the rent to be paid out of a full
        // account, and the test would pass or fail by the day of the week it ran on.
        windToTheFirstOfTheNextMonth();

        RecurringBillView rent = app.declareABillFor(theCustomer, "Rent", THE_TWELFTH, THE_RENT);
        LocalDate theRentsFirstDay = theNextTwelfthAfter(app.theDateTheClockReads());
        app.leaveARuleStanding(savingsAccount,
                aWeeklySweepDownTo(currentAccount, theRentsFirstDay.minusDays(1)));

        windTo(theRentsFirstDay);

        // The night, in the order the night runs it: the rules at two, the bills at half past, the
        // raiser at four.
        app.runJob(THE_SAVING_RULES_JOB);
        app.runJob(THE_BILLS_JOB);
        app.runJob(TheNotificationSweep.THE_JOB);

        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("the sweep left two hundred euros behind and the rent asked for nine hundred, "
                        + "so nothing at all was taken and the balance is exactly where the sweep "
                        + "left it")
                .isEqualByComparingTo(new BigDecimal(WHAT_THE_SWEEP_LEAVES));

        List<NotificationView> afterTheFirstNight = whatWasSaidAboutABillThatCouldNotBePaid();
        assertThat(afterTheFirstNight)
                .as("the first night the rent went unpaid, the customer is told — naming the bill, "
                        + "what it asked for, the day it was owed and the balance that fell short")
                .singleElement()
                .satisfies(said -> {
                    assertThat(said.billId()).isEqualTo(rent.billId());
                    assertThat(said.billName()).isEqualTo("Rent");
                    assertThat(said.occursOn()).isEqualTo(theRentsFirstDay);
                    assertThat(said.amount()).isEqualByComparingTo(new BigDecimal(THE_RENT));
                    assertThat(said.balance())
                            .as("the balance on the night it fell short, which is what the customer "
                                    + "needs in order to know how much to bring back from savings")
                            .isEqualByComparingTo(new BigDecimal(WHAT_THE_SWEEP_LEAVES));
                    assertThat(said.currentAccountId()).isEqualTo(currentAccount);
                    assertThat(said.savingsAccountId())
                            .as("an unpaid rent is a fact about a current account and there is no "
                                    + "savings account it is about")
                            .isNull();
                    assertThat(said.readAt()).isNull();
                });

        // Five more nights carrying the same rent, with the rules deliberately left alone: nothing
        // arrives, nothing is swept, and the only things that run are the two jobs under test.
        for (int night = 2; night <= HOW_MANY_NIGHTS_THE_RENT_IS_CARRIED; night++) {
            app.daysPass(1);
            app.runJob(THE_BILLS_JOB);
            app.runJob(TheNotificationSweep.THE_JOB);
            assertThat(whatWasSaidAboutABillThatCouldNotBePaid())
                    .as("night " + night + " of carrying one rent says nothing new: an inbox "
                            + "holding six copies of one line is worse than no notification at all")
                    .isEqualTo(afterTheFirstNight);
        }

        assertThat(app.arrearsOf(theCustomer))
                .as("and it is still owed all six of those nights, which is what makes the silence "
                        + "a decision rather than the arrear having quietly gone away")
                .hasSize(1);

        // The way out the application already has: money brought back from savings, and the oldest
        // arrear settled out of it on the next run.
        app.withdraw(savingsAccount, theCustomer, "700.00");
        app.runJob(THE_BILLS_JOB);
        app.runJob(TheNotificationSweep.THE_JOB);

        assertThat(app.arrearsOf(theCustomer))
                .as("the hole is closed")
                .isEmpty();
        assertThat(whatWasSaidAboutABillThatCouldNotBePaid())
                .as("and closing it says nothing new either — the notification is a record of a "
                        + "night that happened, and nothing is ever deleted")
                .isEqualTo(afterTheFirstNight);

        // A new month, a new due date, and the account is empty again.
        LocalDate theRentsSecondDay = theNextTwelfthAfter(app.theDateTheClockReads());
        windTo(theRentsSecondDay);
        app.runJob(THE_BILLS_JOB);
        app.runJob(TheNotificationSweep.THE_JOB);

        assertThat(whatWasSaidAboutABillThatCouldNotBePaid())
                .as("a settled arrear that goes unpaid again on a new due date is a new problem and "
                        + "is said again, newest first")
                .hasSize(2)
                .satisfies(said -> {
                    assertThat(said.get(0).occursOn()).isEqualTo(theRentsSecondDay);
                    assertThat(said.get(0).balance())
                            .as("nothing was left at all this time, and the figure is that night's "
                                    + "rather than the one the first notification carried")
                            .isEqualByComparingTo(BigDecimal.ZERO);
                    assertThat(said.get(1).occursOn()).isEqualTo(theRentsFirstDay);
                });

        assertThat(app.marksTheirNotificationsRead(theCustomer))
                .as("both of them are in the customer's own list and both can be marked read")
                .isNotEmpty()
                .allSatisfy(said -> assertThat(said.readAt()).isNotNull());
    }

    /** Only what has been said about a bill that could not be paid, newest first. */
    private static List<NotificationView> whatWasSaidAboutABillThatCouldNotBePaid() {
        return Arrays.stream(app.notificationsOf(theCustomer))
                .filter(said -> "A_BILL_COULD_NOT_BE_PAID".equals(said.reason()))
                .toList();
    }

    /**
     * A weekly sweep taking everything above two hundred euros, as a customer's form would send it:
     * "I saved too hard", with enough left behind that the rent measures itself against a figure
     * rather than against nothing.
     */
    private static Map<String, Object> aWeeklySweepDownTo(long fromCurrentAccountId,
                                                         LocalDate firing) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("name", "Everything above two hundred, every week");
        rule.put("fromCurrentAccountId", fromCurrentAccountId);
        rule.put("trigger", "WEEKLY");
        rule.put("dayOfWeek", firing.getDayOfWeek().name());
        rule.put("howMuchMoves", "EVERYTHING_ABOVE");
        rule.put("floor", WHAT_THE_SWEEP_LEAVES);
        return rule;
    }

    /** Onto the first of the next month, which is where this test's narrative starts from. */
    private static void windToTheFirstOfTheNextMonth() {
        windTo(YearMonth.from(app.theDateTheClockReads()).plusMonths(1).atDay(1));
    }

    /** Winds the clock to that day, insisting on a move forwards: the clock only goes one way. */
    private static void windTo(LocalDate day) {
        long days = ChronoUnit.DAYS.between(app.theDateTheClockReads(), day);
        assertThat(days)
                .as("this test has to reach " + day + " and the clock cannot be wound backwards")
                .isPositive();
        app.daysPass(days);
    }

    /** The first twelfth that begins after the given day. */
    private static LocalDate theNextTwelfthAfter(LocalDate today) {
        YearMonth month = YearMonth.from(today);
        LocalDate thisMonths = month.atDay(THE_TWELFTH);
        return thisMonths.isAfter(today) ? thisMonths : month.plusMonths(1).atDay(THE_TWELFTH);
    }
}
