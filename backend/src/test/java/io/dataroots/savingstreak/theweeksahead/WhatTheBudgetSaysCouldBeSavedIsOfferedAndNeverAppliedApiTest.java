package io.dataroots.savingstreak.theweeksahead;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.SavingCapacityView;
import io.dataroots.savingstreak.support.SpendingCategoryView;
import io.dataroots.savingstreak.support.WeeksAheadView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a customer's budget says they could save each week is offered beside the figure they
 * declared, is never written to it, and is adopted by one press.
 *
 * <p>User stories 36, 37 and 38, which are three halves of one bargain: a savings plan built on what
 * somebody actually spends rather than beside it; the two halves of the application agreeing without
 * anybody retyping a number; and a declared capacity that stays the customer's unless they say
 * otherwise.
 *
 * <p><strong>The claim worth asserting is the one about what does <em>not</em> happen.</strong>
 * Reading the weekly card works out a weekly figure from the customer's own budgets, and it would be
 * the easiest thing in the world for that read to keep it — at which point the goals engine would be
 * planning on a derived number that changes every time somebody buys petrol, and the sentence the
 * customer said about what they can afford would be gone with nothing saying when. So the capacity
 * is read before and after, and it does not move.
 *
 * <p><strong>Adopting it is an ordinary request to the address that already existed.</strong> There
 * is no endpoint that copies one to the other, because a copy is what a press is for: the two live
 * in modules that do not know about each other, they are put side by side by whoever draws them, and
 * what crosses between them is a figure a person pressed a button to send.
 *
 * <p>Ordered, unusually and deliberately: the claim is about a sequence — not declared, then
 * declared by hand, then adopted — and each step is only meaningful after the one before it.
 *
 * <p>An application of its own, because the figure being offered depends on which weeks the clock is
 * in and winding cannot be undone.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WhatTheBudgetSaysCouldBeSavedIsOfferedAndNeverAppliedApiTest extends ApiIntegrationTest {

    /** What the customer says for themselves, small enough that no derived figure could be it. */
    private static final String WHAT_THE_CUSTOMER_SAID = "25.00";

    private static AnApplicationWithAClockToMove app;

    private static String theCustomer;

    private static long theSavingsAccount;

    @BeforeAll
    static void startAnApplicationWithABudgetWorthSavingOutOf() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-offered-never-applied"));
        theCustomer = app.aCustomerOfItsOwn("offered never applied");
        theSavingsAccount = app.savingsAccountOf(theCustomer);
        windToAMondayThatIsTheFirstOfAMonth();
        app.declareIncomeFor(theCustomer, 11, "2000.00");
        SpendingCategoryView groceries = app.declareACategoryFor(theCustomer, "Groceries");
        app.declareABudgetFor(theCustomer, groceries.categoryId(), "400.00");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    @Order(1)
    void reading_the_weekly_card_declares_nothing_on_an_account_whose_holder_has_said_nothing() {
        assertThat(app.savingCapacityOf(theSavingsAccount).declared())
                .as("nobody has said what they can save yet, which is the state this claim starts "
                        + "from")
                .isFalse();

        WeeksAheadView ahead = app.weeksAheadOf(theCustomer);

        assertThat(ahead.worthOffering())
                .as("two salaries against one budget leaves room, so there is a figure worth "
                        + "offering")
                .isTrue();
        assertThat(ahead.couldSaveWeekly()).isPositive();
        SavingCapacityView after = app.savingCapacityOf(theSavingsAccount);
        assertThat(after.declared())
                .as("and the account still says nobody has declared one. A read that quietly wrote "
                        + "what it had worked out would turn a sentence the customer said into a "
                        + "number the application keeps changing on their behalf")
                .isFalse();
        assertThat(after.weeklyCapacity())
                .as("not declared is a state and not a nought, and reading the offer does not "
                        + "make it one")
                .isNull();
    }

    @Test
    @Order(2)
    void a_figure_the_customer_declared_for_themselves_is_not_overruled_by_the_one_offered() {
        app.declareSavingCapacityFor(theSavingsAccount, WHAT_THE_CUSTOMER_SAID);

        WeeksAheadView ahead = app.weeksAheadOf(theCustomer);

        assertThat(ahead.couldSaveWeekly())
                .as("the offer is worked out from the budget and has nothing to do with what the "
                        + "customer declared, so the two are different figures — which is the "
                        + "whole reason the customer is being shown both")
                .isNotEqualByComparingTo(WHAT_THE_CUSTOMER_SAID);
        assertThat(app.savingCapacityOf(theSavingsAccount).weeklyCapacity())
                .as("and what they said is what the account still says. The application advises "
                        + "them rather than overruling them")
                .isEqualByComparingTo(WHAT_THE_CUSTOMER_SAID);
    }

    @Test
    @Order(3)
    void adopting_it_is_one_press_that_sends_the_offer_to_the_capacity_that_already_existed() {
        BigDecimal offered = app.weeksAheadOf(theCustomer).couldSaveWeekly();

        SavingCapacityView adopted = app.declareSavingCapacityFor(theSavingsAccount,
                offered.toPlainString());

        assertThat(adopted.weeklyCapacity())
                .as("the press sends the figure to the address the goals engine already answers "
                        + "on, so the two halves of the application agree without anybody retyping "
                        + "anything")
                .isEqualByComparingTo(offered);
        assertThat(app.savingCapacityOf(theSavingsAccount).weeklyCapacity())
                .isEqualByComparingTo(offered);
        assertThat(app.weeksAheadOf(theCustomer).couldSaveWeekly())
                .as("and the offer is what it was. It is derived from the budgets and the "
                        + "calendars and from nothing the goals engine holds, so adopting it "
                        + "changes the plan and not the advice")
                .isEqualByComparingTo(offered);
    }

    /** Winds the clock on to the next Monday that is also the first of a month. */
    private static void windToAMondayThatIsTheFirstOfAMonth() {
        LocalDate day = app.theDateTheClockReads();
        long days = 0;
        while (!(day.getDayOfWeek() == DayOfWeek.MONDAY && day.getDayOfMonth() == 1)) {
            day = day.plusDays(1);
            days++;
        }
        if (days > 0) {
            app.daysPass(days);
        }
        assertThat(app.theDateTheClockReads()).isEqualTo(day);
    }
}
