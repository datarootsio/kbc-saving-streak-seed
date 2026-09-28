package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.MonthlyIncomeView;
import io.dataroots.savingstreak.support.OccurrenceToComeView;
import io.dataroots.savingstreak.support.RuleOccurrenceView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Leave a payday rule standing, wind three months with nothing run, declare an income only then, and
 * read the preview: the months nobody was paid in are not promised, and what <em>is</em> promised is
 * what the run then fires.
 *
 * <p><strong>Where a payday rule's days come from changes at today, and this is the test of the
 * seam.</strong> What a payday rule <em>will</em> do has no record to come out of — the salary has
 * not landed and cannot have — so it comes out of the declaration, which is the customer's own
 * statement about when they are paid. What it <em>did</em> has a record, and the record is the
 * authority: the run reads the salaries actually credited and fires one occurrence per salary, so a
 * month nobody was paid in is a month it passes over. A forecast that walked the declared
 * day-of-the-month back over those months instead promises a year of transfers for mornings nobody
 * was paid on and says the rule next fires three months ago — <em>asking the calendar a question
 * only the record can answer</em>, which is the mistake this feature's own opening warns about.
 *
 * <p>A late declaration is the sharpest way to arrange it, because it makes the months with no
 * salary in them unambiguous. The same picture arises with no late declaration at all: declare,
 * wind three months, and read the preview before running {@code creditMonthlyIncome}.
 *
 * <p><strong>And the other direction is asserted too.</strong> A payday that <em>was</em> credited
 * and has not been fired yet is genuinely owed, and must be promised — a forecast that answered only
 * from today forward would deny the transfer the next run is about to make, which is the defect this
 * one is the mirror of. So the test goes on: credit the salary, let a day pass, read again, and the
 * owed payday is there and marked as owed before the run makes it.
 *
 * <p>Every day is named off the clock and off the figure the declaration itself reports, so nothing
 * here depends on what date it is when the suite runs or on this test redoing the month-end clamp.
 * Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class APreviewOfAPaydayRulePromisesOnlyThePaydaysTheRecordCanStillProduceApiTest
        extends ApiIntegrationTest {

    private static final String THE_RULES_JOB = "fireSavingRulesDue";

    private static final String THE_INCOME_JOB = "creditMonthlyIncome";

    private static final String WHAT_IT_MOVES = "50.00";

    private static final String WHAT_THE_SALARY_IS = "2000.00";

    /** The day of the month the salary is declared to land on, well clear of any month-end clamp. */
    private static final int PAYDAY = 10;

    /**
     * How long the rule stands before anybody says when its holder is paid: three months, so that
     * three paydays would have been derivable from the declared day and none of them exists.
     */
    private static final int DAYS_BEFORE_THE_DECLARATION = 92;

    private static AnApplicationWithAClockToMove app;
    private static String theCustomer;

    @BeforeAll
    static void startAnApplicationWhoseClockThisTestWinds() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-payday-preview-and-the-record"));
        theCustomer = app.aCustomerOfItsOwn("a payday preview and the record");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_months_nobody_was_paid_in_are_not_promised_and_the_payday_that_landed_is() {
        long savingsAccount = app.savingsAccountOf(theCustomer);
        BigDecimal heldBefore = app.currentAccountBalanceOf(theCustomer);

        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountOnPayday(app.currentAccountOf(theCustomer),
                        "Fifty on payday", WHAT_IT_MOVES));
        assertThat(theListed(savingsAccount, rule.id()).nextFiresOn())
                .as("nobody has said when this customer is paid, so nothing yet says when this "
                        + "rule moves")
                .isNull();

        // Three months go by with nothing run and nobody paid, and only then is the income
        // declared. Every month in between is a month the record says no salary landed in.
        app.daysPass(DAYS_BEFORE_THE_DECLARATION);
        LocalDate theDayItWasDeclaredOn = app.theDateTheClockReads();
        MonthlyIncomeView declared = app.declareIncomeFor(theCustomer, PAYDAY, WHAT_THE_SALARY_IS);

        List<LocalDate> promised = app.previewOn(savingsAccount).occurrences().stream()
                .filter(coming -> coming.ruleId() == rule.id())
                .map(OccurrenceToComeView::dueOn)
                .toList();

        assertThat(promised)
                .as("not one line dated before today: every month between this rule being written "
                        + "and the declaration is a month the record says nobody was paid in, and "
                        + "the run passes over every one of them")
                .allSatisfy(day -> assertThat(day).isAfterOrEqualTo(theDayItWasDeclaredOn));
        assertThat(promised.get(0))
                .as("and the first of them is the day the declaration itself says the next salary "
                        + "lands, so the rule and the salary it waits for cannot disagree")
                .isEqualTo(declared.nextPayday());
        assertThat(app.previewOn(savingsAccount).occurrences().stream()
                .filter(coming -> coming.ruleId() == rule.id())
                .filter(OccurrenceToComeView::owedRatherThanStillToCome)
                .toList())
                .as("nothing is owed yet either, because no salary has ever been credited to this "
                        + "account")
                .isEmpty();
        assertThat(theListed(savingsAccount, rule.id()).nextFiresOn())
                .as("which is also what the rule's own entry says, rather than a day three months "
                        + "in the past")
                .isEqualTo(declared.nextPayday());

        // On to the payday itself, and the salary is credited — but the rules job is deliberately
        // not run, so the payday is owed rather than settled.
        app.daysPass(ChronoUnit.DAYS.between(theDayItWasDeclaredOn, declared.nextPayday()));
        app.runJob(THE_INCOME_JOB);
        app.daysPass(1);

        List<OccurrenceToComeView> nowPromised = app.previewOn(savingsAccount).occurrences().stream()
                .filter(coming -> coming.ruleId() == rule.id())
                .toList();
        assertThat(nowPromised.get(0).dueOn())
                .as("a salary that has landed and has not been saved out of yet is a transfer the "
                        + "next run will make, and the forecast owes the customer that line — "
                        + "answering only from today forward would be the opposite mistake")
                .isEqualTo(declared.nextPayday());
        assertThat(nowPromised.get(0).owedRatherThanStillToCome())
                .as("and it says so, because the morning it names has already gone by")
                .isTrue();
        assertThat(nowPromised.stream()
                .filter(OccurrenceToComeView::owedRatherThanStillToCome)
                .toList())
                .as("one owed payday, because one salary was credited")
                .hasSize(1);

        app.runJob(THE_RULES_JOB);

        List<RuleOccurrenceView> happened = app.historyOf(savingsAccount, rule.id());
        assertThat(happened)
                .extracting(RuleOccurrenceView::dueOn)
                .as("the run fired once, for the one payday a salary actually landed on — three "
                        + "promised against one fired is the failure this test exists for")
                .containsExactly(declared.nextPayday());
        assertThat(happened.get(0).outcome()).isEqualTo("MOVED");
        assertThat(happened.get(0).amount()).isEqualByComparingTo(new BigDecimal(WHAT_IT_MOVES));
        assertThat(app.currentAccountBalanceOf(theCustomer))
                .as("and the money agrees: one salary in and one transfer out of it, rather than "
                        + "the four transfers the derived calendar would have promised")
                .isEqualByComparingTo(heldBefore.add(new BigDecimal(WHAT_THE_SALARY_IS))
                        .subtract(new BigDecimal(WHAT_IT_MOVES)));
    }

    /** The rule as its own account lists it, which is where {@code nextFiresOn} is read. */
    private static SavingRuleView theListed(long savingsAccount, long ruleId) {
        return app.rulesOn(savingsAccount).stream()
                .filter(listed -> listed.id() == ruleId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("rule " + ruleId + " is not in the list"));
    }
}
