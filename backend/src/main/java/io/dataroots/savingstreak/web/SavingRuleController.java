package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.automation.AChangeToARule;
import io.dataroots.savingstreak.automation.AShareOfWhatMoves;
import io.dataroots.savingstreak.automation.ARuleAsAsked;
import io.dataroots.savingstreak.automation.AutomationService;
import io.dataroots.savingstreak.automation.HowMuchMoves;
import io.dataroots.savingstreak.automation.RuleTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The saving rules a customer has left standing against one savings account.
 *
 * <p><strong>This class is what vouches for the savings account.</strong> The Automation module
 * never asks whether one exists, so it cannot tell an account that does from a number somebody made
 * up; every handler here asks Accounts first and refuses in the words Accounts owns. That is the
 * same order and the same refusal the goals, the deposit history and the timeline use, and it is
 * what stops an account nobody has heard of being answered with the empty rule list of an account
 * that simply has none. It is also why {@code SavingRuleRefused} has no kind for a missing account.
 *
 * <p>Beyond that it reads the request and judges nothing. Whether the characters that arrived are a
 * number at all, and whether they name one of the three triggers, one of the two kinds of amount or
 * one of the seven days of the week, are questions about the request and are answered here; whether
 * a rule saying those things is one this application will keep — which day a trigger needs, what an
 * amount of money is, whose current account that is, and how many rules one customer may leave
 * standing — belongs to Automation, which refuses on its own.
 *
 * <p>Reading the words as values here rather than passing strings inward is what keeps the module's
 * own refusals about rules: by the time Automation is called, a trigger is a trigger or it is
 * absent, so the only thing left to say about one is whether the rule it describes makes sense.
 */
@RestController
@RequestMapping("/api/savings-accounts/{savingsAccountId}/saving-rules")
class SavingRuleController {

    private static final Logger log = LoggerFactory.getLogger(SavingRuleController.class);

    private final AccountsService accounts;
    private final AutomationService automation;

    SavingRuleController(AccountsService accounts, AutomationService automation) {
        this.accounts = accounts;
        this.automation = automation;
    }

    /** The rules standing against the account, in the order their holder wrote them. */
    @GetMapping
    List<SavingRuleResponse> rulesOn(@PathVariable long savingsAccountId) {
        vouchFor(savingsAccountId, "saving rules");
        return automation.rulesOn(savingsAccountId).stream().map(SavingRuleResponse::of).toList();
    }

    /**
     * What was ended, so that a rule closed rather than deleted can still be read back.
     *
     * <p>A path of its own rather than a flag on the list above, so that the ordinary read stays the
     * ordinary read — the same shape the abandoned goals have. It is what makes "an ended rule keeps
     * its record" a thing somebody can see rather than a thing they have to take on trust, and it is
     * the half of a rule's history that exists before it has ever fired.
     */
    @GetMapping("/ended")
    List<SavingRuleResponse> endedRulesOn(@PathVariable long savingsAccountId) {
        vouchFor(savingsAccountId, "ended saving rules");
        return automation.endedRulesOn(savingsAccountId).stream().map(SavingRuleResponse::of).toList();
    }

    /**
     * One rule's own history: every day it fell due and what became of it, newest first.
     *
     * <p>Per rule rather than per account, so that somebody auditing one rule does not have to read
     * all of them. It answers for an ended rule as well as a standing one — the record is what
     * explains deposits that are already in the account — and for a rule that has never fired it
     * answers with nothing, which is a different thing from a rule that is not there: that is a 404
     * in Automation's words.
     */
    @GetMapping("/{ruleId}/history")
    List<SavingRuleOccurrenceResponse> historyOf(@PathVariable long savingsAccountId,
                                                 @PathVariable long ruleId) {
        vouchFor(savingsAccountId, "saving rule history");
        return automation.historyOf(savingsAccountId, ruleId).stream()
                .map(SavingRuleOccurrenceResponse::of).toList();
    }

    /**
     * What every rule standing on this account will do over the coming twelve months, merged into
     * one list in date order.
     *
     * <p>A literal path beside {@code /ended}, and no rule identifier goes past this handler, so it
     * cannot be mistaken for one — the preview is about all of them at once, which is the question a
     * customer arrives with.
     *
     * <p>Derived on this read and stored nowhere. Ask it twice with a deposit in between and the two
     * answers differ, which is the point: there is nothing to invalidate.
     */
    @GetMapping("/preview")
    SavingRulePreviewResponse preview(@PathVariable long savingsAccountId) {
        vouchFor(savingsAccountId, "saving rule preview");
        return SavingRulePreviewResponse.of(automation.whatTheRulesWillDo(savingsAccountId));
    }

    /**
     * What a rule nobody has saved would move if it fired this minute.
     *
     * <p>A POST because the rule travels in the body — it has eight fields and a split, and a query
     * string carrying all of that is a form nobody can read — and not because anything is written.
     * Nothing is: no rule, no occurrence, no deposit, no point. It answers 200 rather than 201 for
     * exactly that reason, since there is nothing created for a 201 to point at.
     *
     * <p>The same body the POST above takes, read the same way and refused in the same sentences, so
     * that a customer who sends a rule here and then sends it there gets the same answer twice.
     */
    @PostMapping("/preview")
    SavingRuleDryRunResponse dryRun(@PathVariable long savingsAccountId,
                                    @RequestBody(required = false) NewSavingRuleRequest request) {
        vouchFor(savingsAccountId, "saving rule dry run");
        if (request == null) {
            String reason = "A saving rule needs a name, the current account it takes money from, "
                    + "what makes it move and how much moves.";
            log.warn("saving rule dry run rejected savingsAccountId={} reason={}", savingsAccountId,
                    reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return SavingRuleDryRunResponse.of(automation.whatAnUnsavedRuleWouldDo(savingsAccountId,
                new ARuleAsAsked(
                        request.fromCurrentAccountId(),
                        request.name(),
                        triggerIn(savingsAccountId, request.trigger()),
                        dayOfWeekIn(savingsAccountId, request.dayOfWeek()),
                        dayOfMonthIn(savingsAccountId, request.dayOfMonth()),
                        howMuchMovesIn(savingsAccountId, request.howMuchMoves()),
                        amountIn(savingsAccountId, "amount", request.amount()),
                        amountIn(savingsAccountId, "floor", request.floor()),
                        splitIn(savingsAccountId, request.split()))));
    }

    /**
     * What a rule that already stands would move if it fired this minute with a change applied.
     *
     * <p>The same body the PATCH below takes, read the same way and refused in the same sentences,
     * so that a customer who sends a change here and then sends it there gets the same answer twice
     * — and a POST for the same reason the preview above is one: the change travels in the body, and
     * nothing is written by asking.
     *
     * <p>A path of its own rather than the preview above, because the preview above is about a rule
     * nobody has saved and refuses a customer who has no room for another. A change makes no room:
     * see {@code AutomationService.whatAChangedRuleWouldDo}.
     */
    @PostMapping("/{ruleId}/preview")
    SavingRuleDryRunResponse dryRunAChange(@PathVariable long savingsAccountId,
                                           @PathVariable long ruleId,
                                           @RequestBody(required = false) ChangeSavingRuleRequest request) {
        vouchFor(savingsAccountId, "saving rule change dry run");
        if (request == null) {
            String reason = "Say what to change about the rule: its name, what makes it move, the "
                    + "day it moves on, how much moves, or how it is spread across your goals.";
            log.warn("saving rule change dry run rejected savingsAccountId={} ruleId={} reason={}",
                    savingsAccountId, ruleId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return SavingRuleDryRunResponse.of(automation.whatAChangedRuleWouldDo(savingsAccountId,
                ruleId, new AChangeToARule(
                        request.name(),
                        triggerIn(savingsAccountId, request.trigger()),
                        dayOfWeekIn(savingsAccountId, request.dayOfWeek()),
                        dayOfMonthIn(savingsAccountId, request.dayOfMonth()),
                        howMuchMovesIn(savingsAccountId, request.howMuchMoves()),
                        amountIn(savingsAccountId, "amount", request.amount()),
                        amountIn(savingsAccountId, "floor", request.floor()),
                        splitIn(savingsAccountId, request.split()))));
    }

    /** Leaves a rule standing, and answers with the rule that now exists. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SavingRuleResponse leaveARuleStanding(@PathVariable long savingsAccountId,
                                          @RequestBody(required = false) NewSavingRuleRequest request) {
        vouchFor(savingsAccountId, "saving rule");
        if (request == null) {
            String reason = "A saving rule needs a name, the current account it takes money from, "
                    + "what makes it move and how much moves.";
            log.warn("saving rule rejected savingsAccountId={} reason={}", savingsAccountId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return SavingRuleResponse.of(automation.leaveARuleStanding(savingsAccountId, new ARuleAsAsked(
                request.fromCurrentAccountId(),
                request.name(),
                triggerIn(savingsAccountId, request.trigger()),
                dayOfWeekIn(savingsAccountId, request.dayOfWeek()),
                dayOfMonthIn(savingsAccountId, request.dayOfMonth()),
                howMuchMovesIn(savingsAccountId, request.howMuchMoves()),
                amountIn(savingsAccountId, "amount", request.amount()),
                amountIn(savingsAccountId, "floor", request.floor()),
                splitIn(savingsAccountId, request.split()))));
    }

    /**
     * Changes whichever of the six a customer sent, and leaves the rest of the rule alone.
     *
     * <p>The body is optional here and on the POST above so that a request arriving with none at all
     * is answered in this application's own words rather than in Spring's. Required is the default,
     * and the default would have the framework refuse a bodyless request before either handler ran,
     * with a sentence about reading the request that says nothing to the person who sent it. A body
     * that arrived and asked for nothing — {@code &#123;&#125;} — is Automation's refusal rather than
     * this one's: that is a change that says nothing, and what a change has to say is a rule about
     * rules.</p>
     */
    @PatchMapping("/{ruleId}")
    SavingRuleResponse changeRule(@PathVariable long savingsAccountId, @PathVariable long ruleId,
                                  @RequestBody(required = false) ChangeSavingRuleRequest request) {
        vouchFor(savingsAccountId, "saving rule");
        if (request == null) {
            String reason = "Say what to change about the rule: its name, what makes it move, the "
                    + "day it moves on, how much moves, or how it is spread across your goals.";
            log.warn("saving rule change rejected savingsAccountId={} ruleId={} reason={}",
                    savingsAccountId, ruleId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return SavingRuleResponse.of(automation.changeRule(savingsAccountId, ruleId,
                new AChangeToARule(
                        request.name(),
                        triggerIn(savingsAccountId, request.trigger()),
                        dayOfWeekIn(savingsAccountId, request.dayOfWeek()),
                        dayOfMonthIn(savingsAccountId, request.dayOfMonth()),
                        howMuchMovesIn(savingsAccountId, request.howMuchMoves()),
                        amountIn(savingsAccountId, "amount", request.amount()),
                        amountIn(savingsAccountId, "floor", request.floor()),
                        splitIn(savingsAccountId, request.split()))));
    }

    /**
     * Stops a rule until its holder resumes it, and answers with the rule as it now reads.
     *
     * <p>A POST rather than a PATCH on the rule, and it is the same choice abandoning a goal is
     * made under: pausing is a thing a customer does to a rule rather than a field of it they are
     * setting, and a state that could be PATCHed would be a state somebody could PATCH straight to
     * {@code ENDED} past everything ending a rule actually does.
     *
     * <p>Pressing it twice answers 200 with a rule that is paused, because that is what the customer
     * asked for and what is now true. What it does not do is pause it again — see
     * {@code AutomationService.pauseRule}, where the moment the pause began is left alone.
     */
    @PostMapping("/{ruleId}/pause")
    SavingRuleResponse pauseRule(@PathVariable long savingsAccountId, @PathVariable long ruleId) {
        vouchFor(savingsAccountId, "saving rule pause");
        return SavingRuleResponse.of(automation.pauseRule(savingsAccountId, ruleId));
    }

    /**
     * Starts a paused rule again from this moment, and answers with the rule as it now reads.
     *
     * <p>From this moment, which is the half of pausing that a customer would otherwise be afraid
     * of: what fell while the rule was paused is never made up, so resuming is not a lump sum
     * arriving on the morning they press the button.
     */
    @PostMapping("/{ruleId}/resume")
    SavingRuleResponse resumeRule(@PathVariable long savingsAccountId, @PathVariable long ruleId) {
        vouchFor(savingsAccountId, "saving rule resume");
        return SavingRuleResponse.of(automation.resumeRule(savingsAccountId, ruleId));
    }

    /**
     * Ends a rule for good, and answers with the rule as it now reads rather than with nothing.
     *
     * <p>A DELETE, and it is the honest verb for what a customer is doing — this instruction stops
     * existing — while what the application keeps is the record of it, which is read back through
     * {@code /ended}. Abandoning a goal is a POST for the opposite reason: a goal is still a thing
     * being shown on a page afterwards, and a rule is not.
     */
    @DeleteMapping("/{ruleId}")
    SavingRuleResponse endRule(@PathVariable long savingsAccountId, @PathVariable long ruleId) {
        vouchFor(savingsAccountId, "saving rule");
        return SavingRuleResponse.of(automation.endRule(savingsAccountId, ruleId));
    }

    /**
     * Asked before the rules are, so that an account nobody has heard of is refused rather than
     * answered with the empty rule list of an account that simply has none. The sentence is the one
     * Accounts owns, so that five modules saying it are not five wordings one edit away from
     * disagreeing, and it is logged as well as answered because a refusal decided here would
     * otherwise leave no line in the application's log at all.
     */
    private void vouchFor(long savingsAccountId, String whatWasAsked) {
        if (!accounts.savingsAccountExists(savingsAccountId)) {
            String reason = AccountsService.noSuchSavingsAccount(savingsAccountId);
            log.warn("{} rejected savingsAccountId={} reason={}", whatWasAsked, savingsAccountId, reason);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
        }
    }

    /**
     * What makes the rule move, or nothing at all when nobody said — which on a change means "leave
     * it alone" and on a new rule is an objection Automation makes. A word that is none of the three
     * is answered here, with the three listed, because it is a question about the request.
     */
    private RuleTrigger triggerIn(long savingsAccountId, String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return null;
        }
        try {
            return RuleTrigger.valueOf(trigger.trim().toUpperCase());
        } catch (IllegalArgumentException notATrigger) {
            String reason = "Say what makes this rule move: \"WEEKLY\", \"MONTHLY\" or "
                    + "\"ON_PAYDAY\". \"" + trigger + "\" is none of them.";
            log.warn("saving rule rejected savingsAccountId={} trigger={} reason={}",
                    savingsAccountId, trigger, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /** Which of the two kinds of amount it moves, read the same way and refused the same way. */
    private HowMuchMoves howMuchMovesIn(long savingsAccountId, String howMuchMoves) {
        if (howMuchMoves == null || howMuchMoves.isBlank()) {
            return null;
        }
        try {
            return HowMuchMoves.valueOf(howMuchMoves.trim().toUpperCase());
        } catch (IllegalArgumentException notAKind) {
            String reason = "Say how much this rule moves: \"A_FIXED_AMOUNT\" or "
                    + "\"EVERYTHING_ABOVE\". \"" + howMuchMoves + "\" is neither.";
            log.warn("saving rule rejected savingsAccountId={} howMuchMoves={} reason={}",
                    savingsAccountId, howMuchMoves, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * The day of the week, or a refusal naming what could not be read as one, with the seven listed.
     *
     * <p>Read here rather than inside Automation so that the module cannot be handed a day of the
     * week that is not one: past this point the value is a {@link DayOfWeek} or it is absent, and the
     * only thing left to say about it is whether the rule needed one. It is the same division of
     * labour a goal's allocation direction is read under.
     */
    private DayOfWeek dayOfWeekIn(long savingsAccountId, String dayOfWeek) {
        if (dayOfWeek == null || dayOfWeek.isBlank()) {
            return null;
        }
        try {
            return DayOfWeek.valueOf(dayOfWeek.trim().toUpperCase());
        } catch (IllegalArgumentException notADay) {
            String reason = "\"" + dayOfWeek + "\" is not a day of the week. Write it as one of "
                    + Arrays.toString(DayOfWeek.values()) + ".";
            log.warn("saving rule rejected savingsAccountId={} dayOfWeek={} reason={}",
                    savingsAccountId, dayOfWeek, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * The day of the month as a whole number, or a refusal naming what could not be read as one.
     * Whether it is a day a month actually has is Automation's answer: this only says whether the
     * characters are a number at all, which is the division of labour a monthly income's day is read
     * under.
     */
    private Integer dayOfMonthIn(long savingsAccountId, String dayOfMonth) {
        if (dayOfMonth == null || dayOfMonth.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(dayOfMonth.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + dayOfMonth + "\" is not a day of the month. Write it in digits, "
                    + "like 25.";
            log.warn("saving rule rejected savingsAccountId={} dayOfMonth={} reason={}",
                    savingsAccountId, dayOfMonth, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * The split as goals and whole percentages, or nothing at all when nobody sent one — which on a
     * change means "leave it alone" and on a new rule means a rule whose money lands unallocated.
     *
     * <p>An empty list is kept as an empty list rather than turned into nothing, because the two are
     * different sentences on a change: one says leave the split alone and the other says stop
     * spreading it. Which of them a new rule meant does not matter, and Automation reads both as a
     * rule with no split.
     *
     * <p>Only the characters are judged here — whether a share is a whole number at all — in the same
     * division of labour a day of the month is read under. Whether the shares add to a hundred, and
     * whether the goals named are being saved towards on this account, are rules about rules and are
     * Automation's answer.
     */
    private List<AShareOfWhatMoves> splitIn(long savingsAccountId,
                                            List<SavingRuleSplitRequest> split) {
        if (split == null) {
            return null;
        }
        return split.stream()
                .map(share -> new AShareOfWhatMoves(
                        share == null ? null : share.goalId(),
                        shareIn(savingsAccountId, share == null ? null : share.share())))
                .toList();
    }

    /**
     * A share as a whole number, or a refusal naming what could not be read as one. Whether it is a
     * share this application will keep, and whether the shares add to a hundred, is Automation's
     * answer.
     */
    private Integer shareIn(long savingsAccountId, String share) {
        if (share == null || share.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(share.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + share + "\" is not a share of what a rule moves. Write it in "
                    + "digits, as a whole percentage, like 60.";
            log.warn("saving rule rejected savingsAccountId={} share={} reason={}",
                    savingsAccountId, share, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * A figure as a number, or a refusal naming what could not be read as one — the same answer, in
     * the same shape, a goal's target and a monthly income get. Named back to whoever sent it,
     * because a person who typed a comma has to see the comma to see the mistake.
     *
     * <p>One method for both figures, with the field named in the sentence, because the objection is
     * identical and a second copy of it would be a second wording: what differs between an amount
     * and a floor is what this application will accept as one, and that is Automation's answer
     * rather than this one's.
     */
    private BigDecimal amountIn(long savingsAccountId, String whichFigure, String figure) {
        if (figure == null || figure.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(figure.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + figure + "\" is not an amount of money. Write it in digits with a "
                    + "full stop, like 50.00.";
            log.warn("saving rule rejected savingsAccountId={} {}={} reason={}",
                    savingsAccountId, whichFigure, figure, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }
}
