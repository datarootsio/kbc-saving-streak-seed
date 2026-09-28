package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.simulation.AKindOfAdjustment;
import io.dataroots.savingstreak.simulation.AScenarioToAskAbout;
import io.dataroots.savingstreak.simulation.AnAdjustment;
import io.dataroots.savingstreak.simulation.AnAdjustmentAsAsked;
import io.dataroots.savingstreak.simulation.SimulationService;
import io.dataroots.savingstreak.simulation.TheFuturesOfThisAccount;
import io.dataroots.savingstreak.simulation.TheStartingPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The branches of a savings account's future that nobody has taken: asked for here, answered beside
 * the future the customer is already in, and written down nowhere.
 *
 * <p>Its own resource rather than more fields on the account's overview. The overview is read on
 * every visit to every screen; this is wanted by one screen, it is the only caller of a module that
 * exists for it, and a resource that can be asked for on its own is one a trainer can curl while
 * demonstrating what a wound-forward clock does to a projection.
 *
 * <p><strong>A {@code POST} for something that reads, which wants justifying.</strong> The scenarios
 * are a body — four of them, ten adjustments each — and a query string of that is neither readable
 * in a log nor within anything's length guarantees. It is the concession {@code POST
 * /api/savings-accounts/{id}/saving-rules/preview} already makes for a rule as typed, for the same
 * reason and in the same words, and it carries the same promise: the verb says nothing about what
 * this does, and what it does is read.
 *
 * <p><strong>The account is vouched for here, before the module is called.</strong> An account
 * nobody has heard of is refused in the words every other read of a savings account is refused in —
 * the Simulation module cannot draw that distinction itself, both a made-up identifier and an
 * account that has simply never been paid into being a balance of nothing, and who holds which
 * account is the Accounts module's answer. The same order, and the same sentence, as the timeline
 * and the deposit history beside it.
 *
 * <p>No decision about money, points, weeks, rates, anniversaries or how far ahead to look is taken
 * in this class. Assembling an answer is not a rule, and every rule this resource depends on is
 * behind {@link SimulationService}.
 */
@RestController
@RequestMapping("/api/savings-accounts")
class SimulationController {

    private static final Logger log = LoggerFactory.getLogger(SimulationController.class);

    private final AccountsService accounts;
    private final SimulationService simulations;

    SimulationController(AccountsService accounts, SimulationService simulations) {
        this.accounts = accounts;
        this.simulations = simulations;
    }

    /**
     * What this account's futures look like: the window, where the account stands today, the year it
     * is already heading for, and one answer per branch that was asked about.
     *
     * <p><strong>The body is read here and turned into changes, and nothing about what a change
     * means is decided here.</strong> All this layer does is what it does for a goal's target and a
     * goal's deadline: read the characters somebody typed and say so when they are not a figure or
     * not a day. Whether a figure is an amount of money, whether a day is inside the year the
     * simulation is drawn over and what a change does to a year are the Simulation module's answers,
     * and each kind of change answers for itself — which is why there is no switch over kinds
     * anywhere in this class and why the next kind of change adds no line to it.
     *
     * <p>A request with no scenarios in it, and a request with no body at all, are the same question:
     * what am I heading for. It is answered with the one branch nobody has to ask for.
     *
     * <p>No read transaction is opened here, because the module opens its own around the several
     * reads that have to describe one instant of the ledger and the folds that walk a year out of
     * them. That is a rule about the answer's consistency rather than about this endpoint, so it
     * lives with the rule.
     */
    @PostMapping("/{savingsAccountId}/simulations")
    SimulationResponse simulate(@PathVariable long savingsAccountId,
                                @RequestBody(required = false) SimulationRequest request) {
        if (!accounts.savingsAccountExists(savingsAccountId)) {
            log.warn("simulation rejected savingsAccountId={} reason={}", savingsAccountId,
                    AccountsService.noSuchSavingsAccount(savingsAccountId));
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, AccountsService.noSuchSavingsAccount(savingsAccountId));
        }
        List<AScenarioToAskAbout> asked = theScenariosIn(savingsAccountId, request);
        TheFuturesOfThisAccount futures = simulations.theFuturesOf(savingsAccountId, asked);
        TheStartingPoint standing = futures.standing();
        // One line per simulation with the account, the window both ends and how much was asked
        // about, so that a screen full of projections can be checked against the log and a request
        // that produced nothing can be told from one nobody made.
        //
        // The scenarios counted are the ones the customer typed, which is the figure the cap on them
        // is about: a line reading scenarios=5 beside a refusal saying at most four can be compared
        // would have a reviewer hunting for a rule that was never broken. The branches beside it are
        // the columns the screen actually draws, which is one more, because the year already under
        // way is a fold that ran and a column somebody reads whether or not anybody asked for it.
        log.info("simulation asked savingsAccountId={} from={} until={} scenarios={} adjustments={} "
                        + "branches={}", savingsAccountId, standing.asAt(), standing.until(),
                asked.size(), howManyChangesIn(asked), futures.scenarios().size());
        return SimulationResponse.of(futures);
    }

    /**
     * Turns one branch into the plan the customer has decided on, and answers with exactly what
     * changed.
     *
     * <p>The body is one scenario in the same shape a column of the comparison was asked about in —
     * a name and a list of changes — because that is the whole point of the press: the branch the
     * customer read is the branch they adopt, and a second shape here would let a page adopt
     * something it never drew.
     *
     * <p><strong>The account is vouched for first, and in the same sentence as everywhere else.</strong>
     * This press writes, so the order matters more here than on the read beside it: an account nobody
     * has heard of must be refused before any module is asked to change anything on it.
     *
     * <p>Nothing about what adopting means is decided here. Which kinds have a durable counterpart,
     * which are the customer's to carry out, what each refusal says and whether any of it is applied
     * when one part is refused are all rules, all of them are behind {@link SimulationService#adopt},
     * and there is no switch over kinds in this class for the same reason there is none on the read.
     *
     * <p>{@code 200} rather than {@code 201}: adopting creates nothing that can be fetched back from
     * a URL of its own. It changes figures that already have their own resources — the weekly plan
     * and the goal — and the answer is an account of what it did to them.
     */
    @PostMapping("/{savingsAccountId}/simulations/adopt")
    WhatAdoptingChangedResponse adopt(@PathVariable long savingsAccountId,
                                      @RequestBody AScenarioRequest chosen) {
        if (!accounts.savingsAccountExists(savingsAccountId)) {
            log.warn("scenario adoption rejected savingsAccountId={} reason={}", savingsAccountId,
                    AccountsService.noSuchSavingsAccount(savingsAccountId));
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, AccountsService.noSuchSavingsAccount(savingsAccountId));
        }
        return WhatAdoptingChangedResponse.of(
                simulations.adopt(savingsAccountId, theScenarioIn(savingsAccountId, chosen)));
    }

    /**
     * The scenarios in the body as the module's own shapes, in the order they were typed.
     *
     * <p>A missing body, a body with no scenarios and a scenario with no changes in it are all
     * perfectly good questions and none of them is refused here. How many scenarios one request may
     * carry, how many changes one scenario may carry and what happens to a scenario nobody named are
     * caps rather than readings: they are rules about what this application is willing to answer,
     * they are written down once in {@code WhatMayBeAskedAtOnce} with the reasoning beside them, and
     * a controller that counted them here would be a second place the numbers live.
     */
    private List<AScenarioToAskAbout> theScenariosIn(long savingsAccountId,
                                                     SimulationRequest request) {
        if (request == null || request.scenarios() == null) {
            return List.of();
        }
        List<AScenarioToAskAbout> asked = new ArrayList<>(request.scenarios().size());
        for (AScenarioRequest scenario : request.scenarios()) {
            asked.add(theScenarioIn(savingsAccountId, scenario));
        }
        return asked;
    }

    /**
     * One scenario in the body as the module's own shape, with its changes in the order they were
     * typed.
     *
     * <p>Shared by the comparison and by the press, which is the one thing that makes "adopt the
     * branch you read" true of the wire and not merely of the intention: a column and the scenario
     * adopted off it are read by the same lines, so a body that folded one way cannot press another.
     *
     * <p>A scenario with no changes in it is read as a scenario with no changes in it. Whether that
     * is a question worth answering, or a press worth making, is not this layer's ruling.
     */
    private AScenarioToAskAbout theScenarioIn(long savingsAccountId, AScenarioRequest scenario) {
        List<AnAdjustmentRequest> changes = scenario.adjustments() == null
                ? List.of()
                : scenario.adjustments();
        List<AnAdjustment> adjustments = new ArrayList<>(changes.size());
        for (AnAdjustmentRequest change : changes) {
            adjustments.add(theChangeIn(savingsAccountId, change));
        }
        return new AScenarioToAskAbout(scenario.called(), adjustments);
    }

    /**
     * One change as the kind of thing the fold can ask questions of.
     *
     * <p>The kind reads its own boxes, which is the one thing that genuinely differs between kinds
     * and is written down once, in the kind. This method reads the characters and knows nothing else:
     * every kind gets the same four boxes read the same way, so a kind added later arrives here
     * needing nothing.
     */
    private AnAdjustment theChangeIn(long savingsAccountId, AnAdjustmentRequest change) {
        return AKindOfAdjustment.named(change.kind()).asAsked(new AnAdjustmentAsAsked(
                amountIn(savingsAccountId, change.amount()),
                dayIn(savingsAccountId, "on", change.on()),
                dayIn(savingsAccountId, "until", change.until()),
                change.goalId()));
    }

    /**
     * The amount as a figure, nothing at all when no amount was given, or a refusal naming what could
     * not be read as one — the same answer, in the same shape and the same words, a goal's target
     * gets. Whether the figure is an amount of money is the change's own answer, not this one's, and
     * a change that needs no amount is not asked for one.
     */
    private BigDecimal amountIn(long savingsAccountId, String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + amount + "\" is not an amount of money. Write it in digits with "
                    + "a full stop, like 25.00.";
            log.warn("simulation rejected savingsAccountId={} amount={} reason={}",
                    savingsAccountId, amount, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * A day as a day, nothing at all when none was given, or a refusal naming what could not be read
     * as one. Whether the day is inside the year the simulation is drawn over is the change's own
     * answer, in words that name the window.
     */
    private LocalDate dayIn(long savingsAccountId, String box, String day) {
        if (day == null || day.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(day.trim());
        } catch (DateTimeParseException notADay) {
            String reason = "\"" + day + "\" is not a day. Write it as a date, like 2026-12-31.";
            log.warn("simulation rejected savingsAccountId={} {}={} reason={}",
                    savingsAccountId, box, day, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /** How many changes were asked about altogether, for the one line per simulation. */
    private static int howManyChangesIn(List<AScenarioToAskAbout> asked) {
        return asked.stream().mapToInt(scenario -> scenario.adjustments().size()).sum();
    }
}
