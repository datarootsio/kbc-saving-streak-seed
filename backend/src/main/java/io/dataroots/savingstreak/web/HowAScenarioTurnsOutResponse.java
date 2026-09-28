package io.dataroots.savingstreak.web;

import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.simulation.HowAScenarioTurnsOut;

/**
 * One branch of a future as the API answers it: what it is called, the two days it is drawn between,
 * and the twelve months it comes to.
 *
 * <p>Named, because four columns on a screen are four arguments and a customer choosing between them
 * is choosing between the words they typed. The branch where nothing changes carries a name too, in
 * the customer's own words for it, decided in the Simulation module rather than written into the
 * markup of a page — a screen that spelled it out would be the second place the application says
 * what carrying on is called.
 *
 * <p>The two days are repeated on every branch although they are the same on all of them and on the
 * answer around them. That is deliberate: a column is a thing a page draws on its own, and a column
 * that had to reach up to its parent for the year it is drawn over is a column that can be drawn
 * over the wrong one.
 *
 * <p>Always twelve rows, in date order, the last closing on {@code until}. A year in which nothing
 * moves is twelve rows of unchanged figures rather than a short list, because a page reading across
 * four columns cannot draw one of them with eleven bars.
 *
 * <p><strong>{@code thingsThatHappen} is the other half of the answer, and it is not a summary of
 * the rows.</strong> The months say what a month came to; this says what happened and when — a goal
 * reached, a deadline missed, a run of weeks lost, points expiring, a bonus paid, money that arrived
 * and earned nothing. A page draws the rows as bars and this as a list with the day and the figure
 * in text, which is also what makes the answer readable by somebody who cannot see the bars.
 *
 * <p>Ordered by day, and within a day in the order the night runs them, so a client never sorts it
 * and can never sort it differently from the next client. Empty rather than absent in a branch where
 * nothing worth dating happens, because a column is drawn either way and a missing field is a
 * client's special case.
 */
record HowAScenarioTurnsOutResponse(String called, LocalDate from, LocalDate until,
                                    List<AMonthOfTheFutureResponse> months,
                                    List<AThingThatHappensResponse> thingsThatHappen) {

    static HowAScenarioTurnsOutResponse of(HowAScenarioTurnsOut scenario) {
        return new HowAScenarioTurnsOutResponse(scenario.called(), scenario.from(), scenario.until(),
                scenario.months().stream().map(AMonthOfTheFutureResponse::of).toList(),
                scenario.thingsThatHappen().stream().map(AThingThatHappensResponse::of).toList());
    }
}
