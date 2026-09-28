package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalShareView;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.MonthlyIncomeView;
import io.dataroots.savingstreak.support.OccurrenceToComeView;
import io.dataroots.savingstreak.support.RulePreviewView;
import io.dataroots.savingstreak.support.SavingRuleView;
import io.dataroots.savingstreak.support.WhatWouldMoveView;
import io.dataroots.savingstreak.timeline.TimelineHorizon;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer can read what every rule on an account will do over the coming twelve months, in date
 * order — and the two kinds of rule are quoted differently, because only one of them can honestly be
 * promised.
 *
 * <p>User stories 43, 44 and 45. A fixed amount quotes a figure and can be added up; a sweep quotes
 * its floor and today's balance <em>marked as an illustration</em>, because what it moves next March
 * depends on a balance nobody has yet. The flag is asserted on every line rather than the figure
 * alone, because a preview that invents figures is worse than no preview and the figure on its own
 * cannot tell the two apart.
 *
 * <p>Weekly rules throughout, and every day counted off the clock this application reads rather than
 * off the calendar this test happens to run on. The month-end clamp is the calendar's business and is
 * asserted where the calendar lives, in {@link WhichOccurrencesAreDueTest}; asserting it again from
 * here would need month arithmetic in a test that runs on the 29th, the 30th and the 31st as
 * cheerfully as on the 1st.
 */
class TheTwelveMonthPreviewQuotesAFigureForAFixedAmountAndAFloorForASweepApiTest
        extends ApiIntegrationTest {

    /** At the weekly minimum, which is what a rule left standing to hold a streak together says. */
    private static final String FIFTY_EUROS = "50.00";

    /** What the sweep is set to leave above its floor, so the figure it quotes is this exactly. */
    private static final BigDecimal THREE_HUNDRED = new BigDecimal("300.00");

    private AnAccountWithRules account;

    @BeforeEach
    void anAccountToLookAheadOn() {
        account = new AnAccountWithRules(http, "what the rules will do");
    }

    @Test
    void every_rule_is_merged_into_one_list_in_date_order_over_the_twelve_months_the_bar_looks_over() {
        LocalDate today = account.theDateTheClockReads();
        LocalDate theFixedAmountsDay = today.plusDays(1);
        LocalDate theSweepsDay = today.plusDays(2);
        account.leaveStanding(account.aFixedAmountEveryWeek("Fifty a week",
                theFixedAmountsDay.getDayOfWeek().name(), FIFTY_EUROS));
        account.leaveStanding(account.everythingAboveAFloorEveryWeek("Sweep the rest",
                theSweepsDay.getDayOfWeek().name(), floorLeavingThreeHundredAbove()));

        RulePreviewView preview = account.preview();

        assertThat(preview.from())
                .as("the window opens on the day the application's clock reads")
                .isEqualTo(today);
        assertThat(preview.until())
                .as("and closes exactly as far ahead as the account's timeline bar looks, which is "
                        + "the horizon quoted rather than a second twelve months of this module's "
                        + "own")
                .isEqualTo(today.plus(TimelineHorizon.HOW_FAR_AHEAD_THE_BAR_LOOKS));

        List<LocalDate> days = preview.occurrences().stream().map(OccurrenceToComeView::dueOn).toList();
        assertThat(days)
                .as("in date order, which is how a customer reads down their year")
                .isSorted();
        assertThat(days)
                .as("and every day of it inside the window the answer itself names")
                .allSatisfy(day -> assertThat(day).isBetween(preview.from(), preview.until()));
        assertThat(preview.occurrences().get(0).dueOn())
                .as("the first line is the first of the two rules' days to come round")
                .isEqualTo(theFixedAmountsDay);
        assertThat(preview.occurrences().get(1).dueOn()).isEqualTo(theSweepsDay);

        assertThat(daysOf(preview, "Fifty a week"))
                .as("a weekly rule falls due on its day every week of the window and not once more")
                .startsWith(theFixedAmountsDay)
                .isEqualTo(everyWeekFrom(theFixedAmountsDay, preview.until()));
        assertThat(daysOf(preview, "Sweep the rest"))
                .isEqualTo(everyWeekFrom(theSweepsDay, preview.until()));
    }

    @Test
    void a_fixed_amount_quotes_its_figure_on_every_occurrence_and_calls_it_a_promise() {
        LocalDate itsDay = account.theDateTheClockReads().plusDays(1);
        account.leaveStanding(account.aFixedAmountEveryWeek("Fifty a week",
                itsDay.getDayOfWeek().name(), FIFTY_EUROS));

        RulePreviewView preview = account.preview();

        assertThat(preview.occurrences()).isNotEmpty();
        assertThat(preview.occurrences()).allSatisfy(coming -> {
            assertThat(coming.howMuchMoves()).isEqualTo("A_FIXED_AMOUNT");
            assertThat(coming.wouldMove().amount())
                    .as("the figure the customer named, on every one of the fifty-odd days it "
                            + "falls due on — which is what makes a year of them addable")
                    .isEqualByComparingTo(FIFTY_EUROS);
            assertThat(coming.wouldMove().anIllustrationRatherThanAPromise())
                    .as("and it is a promise rather than an illustration: a fixed amount is as "
                            + "certain twelve months out as it is this minute")
                    .isFalse();
            assertThat(coming.wouldMove().floor())
                    .as("a fixed amount has no floor to quote")
                    .isNull();
        });
    }

    @Test
    void a_sweep_quotes_its_floor_and_todays_balance_marked_as_an_illustration() {
        LocalDate itsDay = account.theDateTheClockReads().plusDays(1);
        BigDecimal floor = new BigDecimal(floorLeavingThreeHundredAbove());
        account.leaveStanding(account.everythingAboveAFloorEveryWeek("Sweep the rest",
                itsDay.getDayOfWeek().name(), floor.toPlainString()));

        RulePreviewView preview = account.preview();

        assertThat(preview.occurrences()).isNotEmpty();
        assertThat(preview.occurrences()).allSatisfy(coming -> {
            assertThat(coming.howMuchMoves()).isEqualTo("EVERYTHING_ABOVE");
            assertThat(coming.wouldMove().floor())
                    .as("the floor is the half of a sweep that is knowable, because the customer "
                            + "said it")
                    .isEqualByComparingTo(floor);
            assertThat(coming.wouldMove().amount())
                    .as("and the figure beside it is what today's balance would give, which is the "
                            + "only honest thing to show for a day whose balance nobody has yet")
                    .isEqualByComparingTo(THREE_HUNDRED);
            assertThat(coming.wouldMove().anIllustrationRatherThanAPromise())
                    .as("marked as an illustration rather than a promise, because a confident "
                            + "figure for a sweep twelve months out is a fiction")
                    .isTrue();
        });
    }

    /**
     * Where the money would land, for the rule that says. The shares are the customer's own
     * instruction priced out at the figure being quoted, and they add to it to the cent — which is
     * the promise a customer's balance and their goals page agreeing is made of.
     */
    @Test
    void a_rule_with_a_split_says_which_goals_the_money_would_land_in() {
        GoalView bike = account.opensAGoal("Bike", "1000.00");
        GoalView holiday = account.opensAGoal("Holiday", "1000.00");
        LocalDate itsDay = account.theDateTheClockReads().plusDays(1);
        account.leaveStanding(RulesAsSomebodyWouldTypeThem.spreadAcross(
                account.aFixedAmountEveryWeek("Fifty a week, split",
                        itsDay.getDayOfWeek().name(), FIFTY_EUROS),
                RulesAsSomebodyWouldTypeThem.inTurn(
                        RulesAsSomebodyWouldTypeThem.aShareFor(bike.id(), "60"),
                        RulesAsSomebodyWouldTypeThem.aShareFor(holiday.id(), "40"))));

        RulePreviewView preview = account.preview();

        assertThat(preview.occurrences()).isNotEmpty();
        assertThat(preview.occurrences()).allSatisfy(coming -> {
            WhatWouldMoveView wouldMove = coming.wouldMove();
            assertThat(wouldMove.intoGoals())
                    .as("both goals in the split, in the order their holder wrote them")
                    .extracting("goalId", "share", "amount")
                    .containsExactly(
                            Tuple.tuple(bike.id(), 60, new BigDecimal("30.00")),
                            Tuple.tuple(holiday.id(), 40, new BigDecimal("20.00")));
            assertThat(wouldMove.intoGoals().stream()
                    .map(GoalShareView::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .add(wouldMove.leftUnallocated()))
                    .as("and what the goals would get plus what no goal would have comes to exactly "
                            + "what would move — a cent lost here is the cent a balance and a goals "
                            + "page would stop agreeing over")
                    .isEqualByComparingTo(wouldMove.amount());
        });
    }

    /** A rule with no split deposits unallocated, exactly as a manual deposit does. */
    @Test
    void a_rule_with_no_split_says_the_money_would_land_unallocated() {
        LocalDate itsDay = account.theDateTheClockReads().plusDays(1);
        account.leaveStanding(account.aFixedAmountEveryWeek("Fifty a week",
                itsDay.getDayOfWeek().name(), FIFTY_EUROS));

        RulePreviewView preview = account.preview();

        assertThat(preview.occurrences()).isNotEmpty();
        assertThat(preview.occurrences()).allSatisfy(coming -> {
            assertThat(coming.wouldMove().intoGoals()).isEmpty();
            assertThat(coming.wouldMove().leftUnallocated()).isEqualByComparingTo(FIFTY_EUROS);
        });
    }

    /**
     * A rule that fires on payday, which is the one trigger whose days are not a fact about the
     * calendar. What it <em>did</em> comes out of the record of salaries actually credited; what it
     * <em>will</em> do has no record to come out of, so it comes out of the declaration its holder
     * made — and a holder who has declared nothing is told nothing rather than shown invented days.
     *
     * <p>The day is asserted against the figure the income declaration itself reports rather than
     * against one this test works out, which keeps every month's arithmetic where it belongs and
     * keeps this test right on the 29th, the 30th and the 31st.
     */
    @Test
    void a_payday_rule_is_previewed_from_the_income_its_holder_declared_and_from_nothing_otherwise() {
        SavingRuleView rule = account.leaveStanding(
                account.everythingAboveAFloorOnPayday("Sweep on payday", "100.00"));

        assertThat(account.preview().occurrences())
                .as("nobody has said when this customer is paid, so nothing yet says when this "
                        + "rule moves — and an invented day here is a transfer promised for a "
                        + "morning nobody is paid on")
                .noneMatch(coming -> coming.ruleId() == rule.id());
        assertThat(theListed(rule.id()).nextFiresOn()).isNull();
        assertThat(theListed(rule.id()).nextMoves()).isNull();

        MonthlyIncomeView declared = account.declaresAnIncome("25", "2000.00");

        List<LocalDate> itsDays = daysOf(account.preview(), "Sweep on payday");
        assertThat(itsDays)
                .as("a year of paydays, rather than the nothing it had before the declaration")
                .hasSizeGreaterThanOrEqualTo(12);
        assertThat(itsDays.get(0))
                .as("and the first of them is the day the declaration itself says the next salary "
                        + "lands, so that the rule and the salary it waits for cannot disagree")
                .isEqualTo(declared.nextPayday());
        assertThat(theListed(rule.id()).nextFiresOn())
                .as("which is also what the rule's own entry in the list says")
                .isEqualTo(declared.nextPayday());

        account.withdrawsTheIncomeDeclaration();

        assertThat(account.preview().occurrences())
                .as("and taking the declaration back takes the days with it, because there is once "
                        + "again nothing that says when this rule moves")
                .noneMatch(coming -> coming.ruleId() == rule.id());
    }

    /** An account nobody has automated anything on has nothing coming, and still says how far. */
    @Test
    void an_account_with_no_rules_has_nothing_coming_and_says_how_far_it_looked() {
        RulePreviewView preview = account.preview();

        assertThat(preview.occurrences()).isEmpty();
        assertThat(preview.until())
                .as("without which a page could not tell an empty year from a window of no length")
                .isEqualTo(preview.from().plus(TimelineHorizon.HOW_FAR_AHEAD_THE_BAR_LOOKS));
    }

    /**
     * A floor that leaves exactly three hundred above it, whatever this customer's current account
     * happens to have been opened with — so the figure the sweep quotes is one number rather than
     * one this test would have to restate if the opening balance ever changed.
     */
    private String floorLeavingThreeHundredAbove() {
        return account.currentAccountBalance().subtract(THREE_HUNDRED).setScale(2).toPlainString();
    }

    private SavingRuleView theListed(long ruleId) {
        return account.rules().stream()
                .filter(rule -> rule.id() == ruleId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("rule " + ruleId + " is not on the account"));
    }

    private static List<LocalDate> daysOf(RulePreviewView preview, String ruleName) {
        return preview.occurrences().stream()
                .filter(coming -> ruleName.equals(coming.ruleName()))
                .map(OccurrenceToComeView::dueOn)
                .toList();
    }

    /**
     * That weekday, every week, up to and including the day the window closes on — worked out by
     * stepping a week at a time rather than by dividing the window, which is the one arithmetic this
     * test can do without knowing anything about months.
     */
    private static List<LocalDate> everyWeekFrom(LocalDate first, LocalDate until) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(until); day = day.plusWeeks(1)) {
            days.add(day);
        }
        return days;
    }
}
