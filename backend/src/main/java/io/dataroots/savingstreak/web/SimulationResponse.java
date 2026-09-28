package io.dataroots.savingstreak.web;

import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.simulation.TheFuturesOfThisAccount;
import io.dataroots.savingstreak.simulation.TheStartingPoint;

/**
 * The answer to "what if I did this instead": the two days the branches are drawn between, the
 * present they all start from, and one turned-out future per scenario that was asked about.
 *
 * <p><strong>The window is sent because working out what day it is, is not the client's to do.</strong>
 * This application's clock can be wound a year forward for a demonstration, and a page positioning
 * twelve months against the machine's own date would draw a year nobody is in — every column shifted
 * off the edge while the application went on behaving as though it were next March. The backend knows
 * which year it is in and says so, which is the same argument the account's own bar already makes
 * about its own two days, and the same twelve months: the simulator, the bar and the rules' preview
 * all quote one horizon so that the three forward-looking screens look equally far.
 *
 * <p><strong>Both days are plain dates rather than moments</strong>, as every other date on this
 * account's resources is: which calendar day a moment falls on depends on the zone it is read in,
 * and that zone is named once in the backend rather than guessed at by whichever machine is drawing
 * the screen.
 *
 * <p><strong>{@code scenarios} always carries the future the customer is already in, first, and
 * whether or not they asked about anything.</strong> A projection with nothing to compare against
 * answers no question — "you will have EUR 4 210 in September" is a number, and "EUR 4 210 rather
 * than the EUR 3 890 you were heading for" is an argument. So a request carrying no branch at all
 * still comes back with one, named in the customer's own words for carrying on as they are, and a
 * page that has yet to let anybody type a branch still has something to draw.
 *
 * <p><strong>{@code anIllustrationRatherThanAPromise} is said once here rather than on each of
 * ninety fields.</strong> The word is already in this codebase, on {@code WhatWouldMove}, and it
 * means exactly what it means there: a figure that depends on a balance nobody has yet. Every figure
 * under this answer is one — a year of it is a year of worked examples — so it is stated about the
 * answer instead of being repeated down every column, where it would be both noise and, worse,
 * something a reader would start to assume the absence of. It is reused rather than replaced by
 * "estimated", which promises less clearly.
 *
 * <p>Nothing here writes anything. Asking is free, and it is free in the strong sense: no account
 * moves, no deposit is made, no point is earned or expired, no rule is created and no row is written
 * anywhere.
 */
record SimulationResponse(LocalDate from, LocalDate until,
                          boolean anIllustrationRatherThanAPromise,
                          WhereThisAccountStandsResponse whereThisAccountStands,
                          List<HowAScenarioTurnsOutResponse> scenarios) {

    /**
     * The window, the present and the branches, off one asking.
     *
     * <p>The two days come off the snapshot rather than being worked out here, which is the point of
     * their being on it: how far ahead this application looks is a rule, and a controller deciding
     * it would be a rule in the web layer and a calendar reading in a package that has never made
     * one. The branches arrive already folded, in the order the module answered them, with the
     * do-nothing one first.
     */
    static SimulationResponse of(TheFuturesOfThisAccount futures) {
        TheStartingPoint standing = futures.standing();
        return new SimulationResponse(standing.asAt(), standing.until(), true,
                WhereThisAccountStandsResponse.of(standing),
                futures.scenarios().stream().map(HowAScenarioTurnsOutResponse::of).toList());
    }
}
