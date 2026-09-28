package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.dataroots.savingstreak.accounts.ABillToCome;
import io.dataroots.savingstreak.accounts.AccountHolder;
import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.CustomerAccounts;
import io.dataroots.savingstreak.accounts.DeclaredIncome;
import io.dataroots.savingstreak.accounts.SavingsAccount;
import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.RecordedDeposit;
import io.dataroots.savingstreak.goals.AllocationsOnAnAccount;
import io.dataroots.savingstreak.goals.GoalRefused;
import io.dataroots.savingstreak.goals.GoalsService;
import io.dataroots.savingstreak.goals.RecordedGoal;
import io.dataroots.savingstreak.timeline.TimelineHorizon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.automation.SavingRuleRefused.Kind.AGAINST_THE_RULES;
import static io.dataroots.savingstreak.automation.SavingRuleRefused.Kind.NO_SUCH_GOAL_IN_THE_SPLIT;
import static io.dataroots.savingstreak.automation.SavingRuleRefused.Kind.NO_SUCH_RULE;
import static io.dataroots.savingstreak.automation.SavingRuleRefused.Kind.THE_RULE_IS_ENDED;

/**
 * The Automation module's face to the rest of the application: the instructions a customer has left
 * standing against a savings account, and what this application will and will not accept as one.
 *
 * <p><strong>A rule is a sentence the customer said, and this class owns both halves of it</strong>:
 * what it may say, what it may be changed to, when it is stopped for a while and started again, when
 * it stops being an instruction and becomes a record — and what happens on the nights it falls due.
 *
 * <p><strong>Downtime is always caught up and a pause is never made up</strong>, and telling the two
 * apart is this feature's one real idea. Both are days a rule was due on and did not fire; one is
 * this application's fault and the other is its customer's instruction, so a fortnight of downtime
 * costs nobody a fortnight of saving while a fortnight of pause is honoured to the day. It can tell
 * them apart because a pause is written down as it happens — see {@link #pauseRule},
 * {@link #resumeRule} and {@link RulePause} — rather than inferred afterwards from a state or a
 * cursor, neither of which can say <em>why</em> a day was skipped.
 *
 * <p><strong>When it does move money, it moves it as an ordinary deposit and there is no special
 * case anywhere.</strong> {@link #fireRulesDueBy} goes through {@code DepositsService.deposit} like
 * a customer pressing a button: the deposit earns points at the streak rate, counts toward the week
 * and is subject to every rule about new saving that already exists. A weekly rule at or above the
 * weekly minimum keeps a streak alive by itself, and that is the product rather than a side effect.
 * A special case here would be a defect.
 *
 * <p><strong>It points outward, and nothing points back at it.</strong> This module reads Accounts,
 * and later slices have it read Deposits and Goals to move money and spread it; none of those three
 * reads this one, and that is what lets the application context start. The direction is not
 * negotiable in either place it matters: where the money history wants to mark a deposit as
 * automatic, the web layer asks this module which deposits it made rather than Deposits asking which
 * of its rows were automatic, because the second is the cycle.
 *
 * <p>Four things are asked of Accounts and nothing else is. <em>Whether one customer holds both ends
 * of a rule</em>, which is the one question this module could not answer for itself — it holds two
 * identifiers and no idea whose they are. <em>What that customer declared as their monthly income</em>,
 * which is the day a standing payday rule reports itself as moving on, said once by the customer and
 * not worth asking for twice. <em>Which days a salary actually landed</em>, which is a different
 * question from the one before it and is the only honest answer to when a payday rule falls due — a
 * declaration is what somebody expects and {@code income_paid} is what happened. And <em>the balance
 * of a current account</em>, on the nights a rule fires, because the amount has to be judged before
 * the deposit is asked for rather than after it is refused; a rule being written is still worked out
 * against nothing but what it says.
 *
 * <p><strong>A rule's day lives in whichever field its trigger needs, and in no other.</strong> A
 * weekly rule has a day of the week, a monthly rule a day of the month, and a payday rule neither of
 * them — its day is read from the income declared against the current account it draws from, on
 * every read, so that a customer who moves payday moves it once. The same is true of the two
 * figures: a fixed-amount rule carries an amount and a sweep carries a floor. Every method here that
 * writes a rule judges the rule <em>as it would then read</em> rather than the fields that arrived,
 * which is what makes a change that leaves a trigger without its day refused as the incomplete
 * sentence it is.
 *
 * <p><strong>Ten rules per customer, counted across every savings account they hold.</strong> Per
 * customer rather than per account because both things the limit protects are the customer's: the
 * page they read their rules on, and the catch-up the nightly job has to get through when a trainer
 * winds the clock. A limit per account would be ten times itself for somebody holding ten accounts.
 *
 * <p><strong>Ending a rule is a closing rather than a deletion</strong>, for the reason abandoning a
 * goal is: the deposits a rule made are already in an account, and "what happened to my money last
 * year" has to stay answerable after the rule that did it is gone. An ended rule leaves the list of
 * standing rules, is refused every change, and stays readable for ever.
 */
@Service
public class AutomationService {

    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);

    /**
     * The outcomes a customer is entitled to hear about: a day the current account could not cover,
     * and a day the savings account would not take. Named here rather than listed at the call site
     * because it is the definition of "could not be honoured" that
     * {@link #occurrencesThatCouldNotBeHonouredIn} promises, and a list written out inside the
     * method would be that definition hidden inside an implementation.
     */
    private static final Set<OccurrenceOutcome> THE_OUTCOMES_THAT_ARE_REFUSALS =
            EnumSet.of(OccurrenceOutcome.NOT_ENOUGH_MONEY, OccurrenceOutcome.THE_ACCOUNT_IS_CLOSED);

    /**
     * The most rules one customer may have standing at once.
     *
     * <p>Ten, and the figure is quoted in the refusal rather than left for the customer to discover.
     * It keeps the page readable, and it bounds the catch-up: a clock wound a century forward turns
     * every standing weekly rule into five thousand occurrences to work through, and the number of
     * rules is the multiplier on that.
     */
    private static final int HOW_MANY_RULES_ONE_CUSTOMER_MAY_LEAVE_STANDING = 10;

    /**
     * The most occurrences one rule catches up in one run.
     *
     * <p>Five hundred, and it is a guard against a demonstration rather than against a customer. The
     * development clock goes a hundred years forward in one move, and a weekly rule over that span
     * is five thousand two hundred transfers: a trainer who winds a century by accident should be
     * told what happened, not watch the application work through five thousand deposits in one
     * transaction while the page waits.
     *
     * <p><strong>It delays a catch-up rather than losing one.</strong> The cursor is left at the day
     * this run stopped at instead of at the moment it ran, so the next run picks the rest up where
     * this one put it down. Nothing is dropped and nothing is fired twice — the record keeps that
     * promise — and the WARN names the days that were left so that the delay is visible rather than
     * inferred from a total that looks low.
     */
    private static final int MOST_OCCURRENCES_ONE_RULE_CATCHES_UP_IN_ONE_RUN = 500;

    /**
     * What the shares in a split add up to. A hundred, because they are whole percentages of what
     * the rule moves, and a split that added to anything else would either leave money the customer
     * meant to place or claim money that was never moved.
     */
    private static final int WHAT_THE_SHARES_ADD_UP_TO = 100;

    /**
     * The smallest share a goal can be given. One percent, because nought percent is not a share:
     * a customer who wants a goal to get nothing leaves it out of the split, and a zero on the list
     * would be a goal that is named, offered nothing, and takes nothing — a line that says nothing.
     */
    private static final int THE_SMALLEST_SHARE = 1;

    /** The first day of a month a rule can be set for. */
    private static final int EARLIEST_DAY_OF_THE_MONTH = 1;

    /**
     * The last. The 31st rather than the 28th, because a customer who saves on the last day of the
     * month says the 31st, and the clamp the firing applies is what makes that mean February.
     */
    private static final int LATEST_DAY_OF_THE_MONTH = 31;

    /**
     * What a rule's fixed amount is called when it is refused for not being an amount of money, so
     * the sentence reads as a sentence: "A saving rule amount has to be an amount of more than zero,
     * and 0.00 is not."
     */
    private static final String WHAT_AN_AMOUNT_IS_CALLED = "saving rule amount";

    private final SavingRuleRepository rules;

    /** What the rules have actually done: one row per rule per day it fell due. */
    private final RuleOccurrenceRepository occurrences;

    /**
     * Who holds what, and what they say they are paid. One of the three modules this one reads, and
     * none of the three reads anything of this one.
     */
    private final AccountsService accounts;

    /**
     * How money moves, and the reason there is no special case anywhere in this feature.
     *
     * <p>A rule that fires makes an <strong>ordinary deposit</strong>: it earns points at the streak
     * rate, it counts toward the week, and it is subject to every rule about new saving that already
     * exists. A weekly rule at or above the weekly minimum therefore keeps a streak alive by itself,
     * and that is the product rather than a side effect. Anything this module did to that deposit to
     * mark it as its own would be the defect.
     *
     * <p>The edge points this way and never back. Deposits stays ignorant that a deposit was
     * automatic; where the money history wants to say so, the web layer asks this module which
     * deposits it made, because a Deposits that asked which of its rows were automatic would be a
     * cycle the application context could not start.
     */
    private final DepositsService deposits;

    /**
     * For the moments this module stamps: when a rule was left standing, and when it was ended.
     *
     * <p>The application's clock, which a trainer can wind, so that a rule left standing against a
     * wound-forward clock is dated where the trainer wound it to rather than where the machine is.
     */
    private final Clock clock;

    /** How each rule spreads what it moves: one row per goal in its split, in the customer's order. */
    private final RuleSplitRepository splits;

    /**
     * When each rule was stopped and when it was started again: one row per pause.
     *
     * <p><strong>The record that tells a pause from downtime</strong>, which is the one distinction
     * this feature turns on. Both are days a rule was due on and did not fire, and they are treated
     * opposite ways — downtime is always caught up and a pause is never made up — so which of the
     * two a day fell in cannot be a question about the rule's state now. It is a question about what
     * happened, and {@link RulePause} is where that is written down.
     */
    private final RulePauseRepository pauses;

    /** Where each firing's money actually went, which is not derivable from the split. See below. */
    private final OccurrenceAllocationRepository whereItWent;

    /**
     * Who is saving for what, and the module that owns where money sits once it has landed.
     *
     * <p><strong>A split is not a third way of working out an amount.</strong> It is where the money
     * goes <em>after</em> it lands, which is this module's answer already: a rule that fires makes an
     * ordinary deposit and then asks Goals to move the money, exactly as a customer allocating by
     * hand would. Nothing about allocation is re-implemented here, and that is deliberate — the
     * rules about what a goal will take, and the invariant that the goals never claim more than the
     * account holds, have one home.
     *
     * <p>The edge points this way and never back, like the other three. Goals reads no other module
     * at all, so it cannot have heard of a saving rule; it is handed a balance and answers about it.
     */
    private final GoalsService goals;

    AutomationService(SavingRuleRepository rules, RuleOccurrenceRepository occurrences,
                      RuleSplitRepository splits, OccurrenceAllocationRepository whereItWent,
                      RulePauseRepository pauses, AccountsService accounts, DepositsService deposits,
                      GoalsService goals, Clock clock) {
        this.rules = rules;
        this.occurrences = occurrences;
        this.splits = splits;
        this.whereItWent = whereItWent;
        this.pauses = pauses;
        this.accounts = accounts;
        this.deposits = deposits;
        this.goals = goals;
        this.clock = clock;
    }

    /**
     * The rules standing against this savings account, in the order the customer wrote them.
     *
     * <p>Standing only. An ended rule is a record rather than an instruction and has no place in a
     * list of what will happen; it is still readable through {@link #endedRulesOn}, which is what
     * keeps "what happened to my money last year" answerable.
     *
     * <p><strong>A paused rule is standing</strong>, and is here with {@link RuleState#PAUSED} on
     * it. Its holder stopped it for a while rather than got rid of it, and a rule that vanished off
     * the page while it was paused would be a rule they could not resume; a page reads the state and
     * says so, which is a different thing from showing a rule with no next day and leaving whoever
     * is looking at it to guess why.
     */
    @Transactional(readOnly = true)
    public List<RecordedSavingRule> rulesOn(long savingsAccountId) {
        List<RecordedSavingRule> standing = asRecorded(
                rules.findBySavingsAccountIdAndStateInOrderByIdAsc(savingsAccountId,
                        RuleState.theOnesStillStanding()));
        log.debug("saving rules read savingsAccountId={} standing={}", savingsAccountId, standing.size());
        return standing;
    }

    /**
     * How far the nightly run has already settled each rule standing on this savings account: the
     * rule's identifier against the moment through which its occurrences are done with.
     *
     * <p><strong>The narrowest fact that answers one question, and the question is "what does this
     * rule still owe".</strong> A forecast that walks forward from today assumes the run has caught
     * up with today, and on this application that is false more often than it is true: {@code
     * MovableClock} moves in whole calendar days, the cron never fires for the days it skipped, and
     * between a wind and a run every rule is behind its cursor. It is also false the other way round
     * on an ordinary morning — the run fired at two and a forecast starting from today would move the
     * same money a second time. Both readings put a projection one morning out of step with the
     * ledger, and one of them does it in the customer's favour, which is the worse of the two.
     *
     * <p><strong>Not on {@link RecordedSavingRule}, deliberately.</strong> That record is what the
     * web layer names and a page draws, and a cursor is this application's own bookkeeping rather
     * than anything about the customer's saving — the same reading {@code ADeclaredBill} and {@code
     * DeclaredIncome} already give about theirs, in the same words. A caller that needs it is
     * predicting the run, not describing a rule, so it asks for it by itself and is handed nothing
     * else. This is the shape {@code DepositsService.mostEverSavedBy} set: one fact, asked for on its
     * own, by the one caller whose arithmetic turns on it.
     *
     * <p>A rule whose cursor was never written — a row from before the column existed — answers with
     * the moment it was left standing, which is where its cursor would have started and is the same
     * fallback {@link #fireRulesDueBy} applies rather than aborting a night over one old row.
     *
     * <p>Standing rules only, which is every rule {@link #rulesOn} answers with: an ended rule is a
     * record rather than an instruction and owes nothing, and a paused one owes nothing until its
     * holder resumes it, but it is standing and a caller reasoning about it needs to know where it
     * would resume from.
     */
    @Transactional(readOnly = true)
    public Map<Long, Instant> howFarEachRuleOnAnAccountIsSettled(long savingsAccountId) {
        Map<Long, Instant> settled = new LinkedHashMap<>();
        for (SavingRule rule : rules.findBySavingsAccountIdAndStateInOrderByIdAsc(savingsAccountId,
                RuleState.theOnesStillStanding())) {
            settled.put(rule.getId(), theMomentItIsSettledThrough(rule));
        }
        log.debug("how far each saving rule is settled savingsAccountId={} rules={} settled={}",
                savingsAccountId, settled.size(), settled);
        return settled;
    }

    /** What was ended, oldest first, so that a rule closed rather than deleted stays readable. */
    @Transactional(readOnly = true)
    public List<RecordedSavingRule> endedRulesOn(long savingsAccountId) {
        List<RecordedSavingRule> ended = asRecorded(
                rules.findBySavingsAccountIdAndStateOrderByIdAsc(savingsAccountId, RuleState.ENDED));
        log.debug("ended saving rules read savingsAccountId={} ended={}", savingsAccountId, ended.size());
        return ended;
    }

    /**
     * Leaves a rule standing against this savings account, and answers with the rule that now
     * exists.
     *
     * <p>Four things are settled before a row is written, in this order: that the rule has a name,
     * that one customer holds both ends of it, that what it says is a rule this application could
     * fire, and that its holder has room for another. The order is the one a customer would want —
     * the objections about what they typed come before the objection about how many they have — and
     * the limit is asked last so that an eleventh rule is refused for being an eleventh rule rather
     * than for a typo it also had.
     *
     * @throws SavingRuleRefused if that is not a rule this application will leave standing
     */
    @Transactional
    public RecordedSavingRule leaveARuleStanding(long savingsAccountId, ARuleAsAsked asked) {
        log.debug("saving rule asked for savingsAccountId={} fromCurrentAccountId={} name={} "
                        + "trigger={} dayOfWeek={} dayOfMonth={} howMuchMoves={} amount={} floor={}",
                savingsAccountId, asked.currentAccountId(), asked.name(), asked.trigger(),
                asked.dayOfWeek(), asked.dayOfMonth(), asked.howMuchMoves(), asked.amount(),
                asked.floor());
        refuseUnlessTheHolderHoldsBothEnds(savingsAccountId, asked.currentAccountId());
        ARuleAsItWouldRead itWouldRead = judged(savingsAccountId, null, asked.name(), asked.trigger(),
                asked.dayOfWeek(), asked.dayOfMonth(), asked.howMuchMoves(), asked.amount(),
                asked.floor());
        List<AShareOfWhatMoves> split = theSplitAsItWouldRead(savingsAccountId, null, asked.split());
        refuseUnlessThereIsRoomForAnother(savingsAccountId);

        SavingRule rule = rules.save(new SavingRule(savingsAccountId, asked.currentAccountId(),
                itWouldRead.name(), itWouldRead.trigger(), itWouldRead.dayOfWeek(),
                itWouldRead.dayOfMonth(), itWouldRead.howMuchMoves(), itWouldRead.amount(),
                itWouldRead.floor(), clock.instant()));
        writeTheSplitOf(rule, split);
        log.info("saving rule left standing savingsAccountId={} ruleId={} name={} "
                        + "fromCurrentAccountId={} trigger={} dayOfWeek={} dayOfMonth={} "
                        + "howMuchMoves={} amount={} floor={} state={} split={}",
                savingsAccountId, rule.getId(), rule.getName(), rule.getCurrentAccountId(),
                rule.getTrigger(), rule.getDayOfWeek(), rule.getDayOfMonth(),
                rule.getHowMuchMoves(), asMoneyOrNothing(rule.getAmount()),
                asMoneyOrNothing(rule.getFloor()), rule.getState(), inWords(split));
        return asRecorded(rule);
    }

    /**
     * Says a standing rule differently — its name, what makes it move, the day it moves on, how much
     * moves, or any combination of those — and answers with the rule as it now reads.
     *
     * <p>Only what was given is changed; anything absent is left exactly as it was, which is what
     * makes renaming a monthly rule a thing somebody can do without restating its day and its
     * figure. What is judged is the rule <em>as it would then read</em> rather than the fields that
     * arrived, so turning a weekly rule into a monthly one without saying which day of the month is
     * refused as the half a sentence it is.
     *
     * <p>A change that says nothing, and a change naming a day or a figure the rule it describes
     * would have no use for, are both refused rather than answered with an untouched rule. See
     * {@link AChangeToARule} for why the same field sent while a rule is being left standing is
     * dropped instead: a form carrying a spare box says nothing about its sender's intent, and a
     * change naming one field says everything about it.
     *
     * <p>A rule that has been ended is refused rather than edited. It is kept so that the deposits it
     * made stay explained, and a record that could be rewritten afterwards is not a record.
     *
     * <p>Nothing is written until every objection has been heard: the new values are worked out and
     * judged before the row is touched, so a refused change leaves the rule exactly as the customer
     * last left it.
     *
     * <p><strong>The cursor is deliberately left where it was, and a change of trigger therefore
     * costs at most the current period.</strong> {@link #dueInPeriodsNotAlreadySettled} reads an
     * occurrence already settled under the old trigger through the shape of the new one: a weekly
     * rule that fired on the 2nd of December and is then made monthly has a December the record
     * already holds, so its 20th of December does not fire and its 20th of January does. That is a
     * turn quietly skipped, and it is a chosen one. The alternatives are worse in the direction that
     * matters — mapping the day through the trigger it was settled under would need the trigger
     * written onto every occurrence, which is a column recording a thing that has already changed,
     * and pushing the cursor forward here skips exactly the same period while also losing any
     * occurrence still owed from before it. So this fails closed: a customer who re-words their
     * instruction may wait one week or one month longer for it, and no customer has money moved
     * twice for having re-worded it. The skip is not silent — the DEBUG line in
     * {@code dueInPeriodsNotAlreadySettled} names the period that was passed over and the stretch it
     * was asked about.
     *
     * @throws SavingRuleRefused if there is no such rule, if it has been ended, or if the change is
     *                           one this module will not make
     */
    @Transactional
    public RecordedSavingRule changeRule(long savingsAccountId, long ruleId, AChangeToARule change) {
        log.debug("saving rule change asked for savingsAccountId={} ruleId={} name={} trigger={} "
                        + "dayOfWeek={} dayOfMonth={} howMuchMoves={} amount={} floor={}",
                savingsAccountId, ruleId, change.name(), change.trigger(), change.dayOfWeek(),
                change.dayOfMonth(), change.howMuchMoves(), change.amount(), change.floor());
        SavingRule rule = theRuleOn(savingsAccountId, ruleId);
        refuseUnlessItHasNotBeenEnded(savingsAccountId, rule, "changed");
        refuseUnlessItAsksForSomething(savingsAccountId, ruleId, change);
        refuseWhatTheRuleWouldHaveNoUseFor(savingsAccountId, ruleId, rule, change);

        List<AShareOfWhatMoves> split = theSplitAsItWouldRead(savingsAccountId, ruleId, change.split());
        ARuleAsItWouldRead itWouldRead = judged(savingsAccountId, ruleId,
                change.name() == null ? rule.getName() : change.name(),
                change.trigger() == null ? rule.getTrigger() : change.trigger(),
                change.dayOfWeek() == null ? rule.getDayOfWeek() : change.dayOfWeek(),
                change.dayOfMonth() == null ? rule.getDayOfMonth() : change.dayOfMonth(),
                change.howMuchMoves() == null ? rule.getHowMuchMoves() : change.howMuchMoves(),
                change.amount() == null ? rule.getAmount() : change.amount(),
                change.floor() == null ? rule.getFloor() : change.floor());

        String wasNamed = rule.getName();
        RuleTrigger wasTriggeredBy = rule.getTrigger();
        DayOfWeek wasOnDayOfWeek = rule.getDayOfWeek();
        Integer wasOnDayOfMonth = rule.getDayOfMonth();
        HowMuchMoves wasMoving = rule.getHowMuchMoves();
        BigDecimal wasWorth = rule.getAmount();
        BigDecimal hadFloor = rule.getFloor();
        List<AShareOfWhatMoves> wasSplit = theSplitOf(rule.getId());
        rule.nowSays(itWouldRead.name(), itWouldRead.trigger(), itWouldRead.dayOfWeek(),
                itWouldRead.dayOfMonth(), itWouldRead.howMuchMoves(), itWouldRead.amount(),
                itWouldRead.floor());
        rules.save(rule);
        if (split != null) {
            // Replaced whole rather than merged into, because a split is a sentence about
            // proportions and half of one does not add to a hundred. An empty list is the customer
            // saying "stop spreading it", and leaves the rule depositing unallocated.
            splits.deleteBySavingRuleId(rule.getId());
            writeTheSplitOf(rule, split);
        }

        log.info("saving rule changed savingsAccountId={} ruleId={} name={} trigger={} dayOfWeek={} "
                        + "dayOfMonth={} howMuchMoves={} amount={} floor={} split={} wasNamed={} "
                        + "wasTriggeredBy={} wasOnDayOfWeek={} wasOnDayOfMonth={} wasMoving={} "
                        + "wasWorth={} hadFloor={} wasSplit={}",
                savingsAccountId, ruleId, rule.getName(), rule.getTrigger(), rule.getDayOfWeek(),
                rule.getDayOfMonth(), rule.getHowMuchMoves(), asMoneyOrNothing(rule.getAmount()),
                asMoneyOrNothing(rule.getFloor()), inWords(split == null ? wasSplit : split),
                wasNamed, wasTriggeredBy, wasOnDayOfWeek, wasOnDayOfMonth, wasMoving,
                asMoneyOrNothing(wasWorth), asMoneyOrNothing(hadFloor), inWords(wasSplit));
        return asRecorded(rule);
    }

    /**
     * Stops a rule until its holder resumes it, and answers with the rule as it now reads.
     *
     * <p><strong>Nothing that falls while it is paused is ever made up.</strong> That is the whole
     * of this and it is the one real idea in this feature: a fortnight of downtime is the
     * application's fault and is caught up in full, while a fortnight of pause is the customer
     * saying "not this month" and is honoured. Resuming a rule paused for two months must not hand
     * its holder three transfers in one morning, and it does not.
     *
     * <p>It can tell the two apart because this writes down <em>when</em>: a {@link RulePause} row
     * carrying the moment the pause began and, later, the moment it ended. Occurrences falling in
     * that window are excluded from the catch-up range outright — never recorded and never
     * considered, rather than recorded and then judged — so the history of a paused rule has nothing
     * in it for those days at all, which is what actually happened.
     *
     * <p>The cursor is deliberately left where it is. A paused rule is not in the set the night
     * walks, so nothing reads it meanwhile, and moving it here would be recording a pause as though
     * it were work already done.
     *
     * <p><strong>Pausing a rule that is already paused is accepted quietly.</strong> A button pressed
     * twice is not a mistake worth a sentence. It is accepted rather than merely tolerated: the
     * second press changes nothing at all, and in particular does not re-stamp the moment the pause
     * began, which would quietly shorten the window of occurrences that are never made up.
     *
     * @throws SavingRuleRefused if there is no such rule on the account, or it has been ended
     */
    @Transactional
    public RecordedSavingRule pauseRule(long savingsAccountId, long ruleId) {
        log.debug("saving rule pause asked for savingsAccountId={} ruleId={}", savingsAccountId, ruleId);
        SavingRule rule = theRuleOn(savingsAccountId, ruleId);
        refuseUnlessItHasNotBeenEnded(savingsAccountId, rule, "paused");
        if (!rule.pause()) {
            Instant alreadyPausedAt = theMomentItWasPausedAt(rule);
            // Said out loud rather than passed over in silence: "accepted quietly" is about what the
            // customer is told, not about what the log says, and a reviewer pressing pause twice has
            // to be able to see that the second press was a no-op rather than a second pause.
            log.info("saving rule pause changed nothing savingsAccountId={} ruleId={} name={} "
                            + "state={} pausedAt={} reason=it was already paused, and a button "
                            + "pressed twice is not a mistake worth a sentence",
                    savingsAccountId, ruleId, rule.getName(), rule.getState(), alreadyPausedAt);
            return asRecorded(rule, theSplitOf(ruleId), alreadyPausedAt,
                    pauses.findBySavingRuleIdOrderByIdAsc(ruleId), new HashMap<>());
        }
        Instant pausedAt = clock.instant();
        rules.save(rule);
        pauses.save(new RulePause(rule.getId(), pausedAt));
        log.info("saving rule paused savingsAccountId={} ruleId={} name={} trigger={} "
                        + "howMuchMoves={} pausedAt={} settledThrough={} state={}",
                savingsAccountId, ruleId, rule.getName(), rule.getTrigger(), rule.getHowMuchMoves(),
                pausedAt, rule.getSettledThrough(), rule.getState());
        return asRecorded(rule, theSplitOf(ruleId), pausedAt,
                pauses.findBySavingRuleIdOrderByIdAsc(ruleId), new HashMap<>());
    }

    /**
     * Starts a paused rule again, and answers with the rule as it now reads.
     *
     * <p><strong>The pause is closed, and closing it is the whole of the work.</strong> The window
     * it leaves behind takes every day inside it out of the catch-up range outright, so a rule
     * paused for two months fires once, on its next due day, rather than three times on the morning
     * it comes back. It is asked of every pause the rule has ever had rather than only the last,
     * because a customer may pause, resume and pause again — and it outlives the cursor on purpose.
     * A payday rule's days are the days a salary actually landed, read out of the record
     * {@code accounts} keeps rather than off the calendar, and that record is written when the
     * salary is <em>credited</em>: a salary credited late — after the resume, for a day that fell
     * inside the pause — reaches the run with nothing about a cursor to exclude it. The window
     * excludes it.
     *
     * <p><strong>The cursor is not moved, and a resume that moved it would be a second, worse
     * bug.</strong> The window already excludes the days inside the pause, so moving the cursor
     * would buy nothing there; what it would cost is every day that fell due <em>before</em> the
     * pause began and had not fired yet. Those are downtime — the application's fault — and downtime
     * is always caught up. Pressing pause and pressing resume is not an instruction to write off the
     * fortnight the job never ran, and a customer who did it would never know what they had lost.
     * See {@link SavingRule#resume}.
     *
     * <p>How many occurrences were passed over is counted here and named in the INFO line, because
     * this is the only moment both ends of the window are in hand and it is the figure a customer
     * would ask about. It is what the record knows at this moment: a salary for a day inside the
     * pause that has not been credited yet is not in it, and will be excluded by the window instead
     * of by this count.
     *
     * <p><strong>Resuming a rule that was already live is accepted quietly</strong>, for the reason
     * pausing a paused one is: a button pressed twice is not a mistake worth a sentence. It leaves
     * nothing behind either — no pause row to close, and nothing counted as passed over.
     *
     * @throws SavingRuleRefused if there is no such rule on the account, or it has been ended
     */
    @Transactional
    public RecordedSavingRule resumeRule(long savingsAccountId, long ruleId) {
        log.debug("saving rule resume asked for savingsAccountId={} ruleId={}",
                savingsAccountId, ruleId);
        SavingRule rule = theRuleOn(savingsAccountId, ruleId);
        refuseUnlessItHasNotBeenEnded(savingsAccountId, rule, "resumed");
        if (!rule.isPaused()) {
            log.info("saving rule resume changed nothing savingsAccountId={} ruleId={} name={} "
                            + "state={} settledThrough={} reason=it was not paused, and a button "
                            + "pressed twice is not a mistake worth a sentence",
                    savingsAccountId, ruleId, rule.getName(), rule.getState(),
                    rule.getSettledThrough());
            return asRecorded(rule, theSplitOf(ruleId), null,
                    pauses.findBySavingRuleIdOrderByIdAsc(ruleId), new HashMap<>());
        }

        Instant resumedAt = clock.instant();
        Optional<RulePause> theOneItIsIn =
                pauses.findFirstBySavingRuleIdAndEndedAtIsNullOrderByIdDesc(ruleId);
        Instant pausedAt = theOneItIsIn.map(RulePause::getBegunAt).orElse(null);
        List<LocalDate> passedOver;
        if (theOneItIsIn.isPresent()) {
            RulePause pause = theOneItIsIn.get();
            // Closed before it is asked what it covered, so that the window answering here is the
            // same closed window the run will ask later. An open one covers everything after it
            // began, and the figure the customer is told has to be the days the run then excludes.
            pause.ended(resumedAt);
            pauses.save(pause);
            passedOver = theDaysAPauseCovered(rule, pause);
        } else {
            passedOver = List.of();
            // Unreachable while the state and the record agree, and worth a line rather than a
            // silent recovery: a paused rule with no open pause row is a rule whose window nothing
            // can name, so the days it was stopped for cannot be excluded by anything but the
            // cursor. The resume still happens — leaving the customer unable to start their rule
            // again would be worse — and this says what was lost.
            log.warn("saving rule resumed with no pause to close savingsAccountId={} ruleId={} "
                            + "name={} resumedAt={} reason=the rule was paused but no open pause is "
                            + "recorded for it, so the window it was stopped for is not known and "
                            + "only the cursor excludes those days",
                    savingsAccountId, ruleId, rule.getName(), resumedAt);
        }
        rule.resume();
        rules.save(rule);
        log.info("saving rule resumed savingsAccountId={} ruleId={} name={} trigger={} "
                        + "pausedAt={} resumedAt={} occurrencesPassedOver={} daysPassedOver={} "
                        + "settledThrough={} state={} reason=the cursor is left where it was, so a "
                        + "day this rule was already owed from before the pause is still caught up, "
                        + "while every day inside the pause is excluded by the window instead",
                savingsAccountId, ruleId, rule.getName(), rule.getTrigger(), pausedAt, resumedAt,
                passedOver.size(), passedOver, rule.getSettledThrough(), rule.getState());
        return asRecorded(rule, theSplitOf(ruleId), null,
                pauses.findBySavingRuleIdOrderByIdAsc(ruleId), new HashMap<>());
    }

    /**
     * The days this rule fell due on inside a pause — the occurrences a resume passed over.
     *
     * <p>The same two questions the run asks, over the pause's own two moments instead of over the
     * cursor and now: the calendar for a weekly or a monthly rule, and the record of salaries
     * actually credited for a payday one. Asking it any other way would let the figure a customer is
     * told disagree with the days the run then excludes.
     *
     * <p><strong>And then filtered through the window itself, which is what makes that true rather
     * than merely intended.</strong> The two bounds handed to {@link #theDaysItFellDueOn} are not
     * the whole of the question for a payday rule: that method asks the record for the salaries
     * <em>credited</em> between them and then bounds the days by the moment the rule was left
     * standing, so a salary credited during a pause for a payday that fell <em>before</em> it began
     * comes back from it — a day {@link RulePause#covers} would not exclude. Reported as passed over
     * it would be a lie in the customer's own figure, and it is precisely the day this ticket is
     * about. Asking the window itself, exactly as {@link #notCoveredByAPause} does, is the only way
     * the two cannot disagree.
     */
    private List<LocalDate> theDaysAPauseCovered(SavingRule rule, RulePause pause) {
        return theDaysItFellDueOn(rule, theDayItMovesOn(rule), pause.getBegunAt(),
                        pause.getEndedAt()).stream()
                .filter(day -> pause.covers(WhichOccurrencesAreDue.theMomentThatDayBegins(day)))
                .toList();
    }

    /**
     * The moment a paused rule was paused at, and nothing at all for a rule that is not paused.
     *
     * <p>Read off the open pause rather than stored on the rule, so that there is one answer to when
     * a pause began rather than two that have to be kept in step.
     */
    private Instant theMomentItWasPausedAt(SavingRule rule) {
        if (!rule.isPaused()) {
            return null;
        }
        return pauses.findFirstBySavingRuleIdAndEndedAtIsNullOrderByIdDesc(rule.getId())
                .map(RulePause::getBegunAt)
                .orElse(null);
    }

    /**
     * Ends a rule for good, and answers with the rule as it now reads.
     *
     * <p>It leaves the list of standing rules and fires nothing ever again, and everything it says
     * stays readable: the name, the trigger, the day and the figure are what explain the deposits it
     * already made, and deleting them would be deleting the explanation for money that is still in
     * the account.
     *
     * <p>Ending a rule that has already been ended is refused rather than accepted quietly, unlike
     * pressing a button twice elsewhere in this application. The difference is that this one has a
     * moment on it: the first ending is when the customer stopped saving this way, and a second that
     * shrugged would invite a caller to believe it had just happened.
     *
     * <p><strong>The day the rule was moving on is written down as it closes</strong>, and this is
     * the moment a payday rule stops following its holder's declaration. Up to here its day is that
     * declaration, read afresh every time, which is what lets somebody move payday once and have
     * every rule waiting for it follow. From here it is a record, and a record has to be able to
     * survive the thing it recorded changing: a customer who moves payday to the 5th next year has
     * not changed the day this rule moved on last year. {@link SavingRule#end} is where it lands.
     *
     * @throws SavingRuleRefused if there is no such rule on the account, or it was already ended
     */
    @Transactional
    public RecordedSavingRule endRule(long savingsAccountId, long ruleId) {
        log.debug("saving rule end asked for savingsAccountId={} ruleId={}", savingsAccountId, ruleId);
        SavingRule rule = theRuleOn(savingsAccountId, ruleId);
        refuseUnlessItHasNotBeenEnded(savingsAccountId, rule, "ended");
        Integer theDayItWasMovingOn = theDayItMovesOn(rule);
        rule.end(clock.instant(), theDayItWasMovingOn);
        rules.save(rule);
        log.info("saving rule ended savingsAccountId={} ruleId={} name={} trigger={} howMuchMoves={} "
                        + "dayOfWeek={} dayItWasMovingOn={} stoodSince={} endedAt={}",
                savingsAccountId, ruleId, rule.getName(), rule.getTrigger(), rule.getHowMuchMoves(),
                rule.getDayOfWeek(), theDayItWasMovingOn, rule.getCreatedAt(), rule.getEndedAt());
        return asRecorded(rule);
    }

    /**
     * One rule's own history: every day it fell due and what became of it, newest first.
     *
     * <p>Readable for an ended rule as well as a standing one, and that is the whole reason ending a
     * rule is a closing rather than a deletion. The deposits it made are still in an account, and
     * "what happened to my money last year" has to stay answerable after the instruction that did it
     * is gone.
     *
     * <p>Scoped by the account, like every read in this module, so a rule identifier somebody
     * guessed answers as a rule that is not there rather than with somebody else's saving.
     *
     * @throws SavingRuleRefused if there is no such rule on that account
     */
    @Transactional(readOnly = true)
    public List<RecordedOccurrence> historyOf(long savingsAccountId, long ruleId) {
        SavingRule rule = theRuleOn(savingsAccountId, ruleId);
        List<RuleOccurrence> written = occurrences.findBySavingRuleIdOrderByDueOnDescIdDesc(rule.getId());
        // Where the money went, for the whole history in one question rather than one per
        // occurrence: a rule caught up over three years is a thousand occurrences, and a question
        // each would be a thousand round trips to draw one page.
        Map<Long, List<WhatAGoalGot>> intoGoals = whereTheMoneyWentOn(written);
        List<RecordedOccurrence> history = written.stream()
                .map(occurrence -> asRecorded(occurrence,
                        intoGoals.getOrDefault(occurrence.getId(), List.of())))
                .toList();
        log.debug("saving rule history read savingsAccountId={} ruleId={} occurrences={} "
                        + "occurrencesThatSpreadTheirMoney={}",
                savingsAccountId, ruleId, history.size(), intoGoals.size());
        return history;
    }

    /**
     * What every rule standing on this savings account will do over the coming twelve months, merged
     * into one list in the order the nights would fire them.
     *
     * <p><strong>A prediction, and it agrees with the firing because it is made of the firing's own
     * parts.</strong> The days come from {@link WhichOccurrencesAreDue}, with the month-end clamp
     * and the rule's own cursor; the figure comes from {@link WhatARuleWouldMove}; the cents of a
     * split come from {@link HowAnAmountIsSplit}; and a day whose period this rule has already had
     * its turn in is dropped by {@link #dueInPeriodsNotAlreadySettled}, which is the same question
     * the run asks. Nothing here re-derives a calendar or an arithmetic that exists elsewhere, for
     * the reason this feature has learned four times: two modules answering one question about money
     * eventually answer it differently, and the copy a customer reads before committing would be the
     * wrong one.
     *
     * <p><strong>Derived on every read and stored nowhere.</strong> A stored projection goes stale
     * the moment a balance, a rule or a goal changes and then needs invalidation rules that are
     * themselves a source of bugs — {@code AReallocationWorthSuggesting} makes the argument at
     * length. Change anything at all and the next read says something else, with nothing to
     * invalidate.
     *
     * <p><strong>Twelve months, quoted from {@code TimelineHorizon} rather than restated.</strong>
     * The two forward-looking parts of this application look equally far by construction: a bar that
     * ended in September beside a list of transfers running to December would be two answers to
     * "what is coming". One constant with no state behind it, borrowed the way
     * {@link WhichOccurrencesAreDue} borrows the zone weeks are counted in.
     *
     * <p><strong>Plus whatever the next run still owes, which can be dated before the window
     * opens.</strong> Each rule is walked from its own cursor rather than from today — see
     * {@link WhichOccurrencesAreDue#daysDueInTheWindow} — so a rule the nightly run is behind on
     * carries the days it is behind by, oldest first, at the head of the list. On a wound clock that
     * is the ordinary state of every rule, because the cron never fires for the days the clock
     * skipped; a forecast that quietly dropped them would deny the very transfers the next run is
     * about to make, which is exactly the disagreement this whole read is built not to have.
     * {@code from} and {@code until} stay the horizon's own two days, because that is the window
     * this looked <em>forward</em> over and it has to be the bar's; the INFO line says how many
     * lines fall before it.
     *
     * <p>A paused rule contributes nothing. Nothing falls due while a rule is paused — the whole of
     * "a pause is never made up" — and a pause has no end for this to guess at, so a forecast that
     * drew its days anyway would be promising transfers its holder has told this application not to
     * make. An ended rule is not in the list this walks at all.
     *
     * <p><strong>And neither do the mornings that fell inside a pause somebody has since resumed
     * from</strong>, which is the other half of the same sentence and the one the widened window
     * made possible. A resume deliberately leaves the cursor where it was, so every day inside a
     * closed pause is back inside the window this counts over; they are taken out again by
     * {@link #notCoveredByAPause}, the night's own predicate, asked here with the same pauses in the
     * same order and <em>before</em> the record is asked about periods. One filter rather than two
     * answers: a preview that promised three Mondays a resumed rule would then fire one of is the
     * disagreement this whole read is built not to have.
     *
     * <p>A rule that fires on payday is forecast from two different sources, and which one answers
     * depends on whether the day has already fallen — see {@link #theDaysAPaydayRuleHasComing}. A
     * payday rule with no declaration behind it and no salary owed contributes nothing.
     *
     * <p><strong>And the bills going the other way over the same twelve months</strong>, asked of
     * Accounts, which owns them. A sweep is a claim on a current account and so is the rent, and a
     * customer reading down the year to decide how much to save has to see both or they are choosing
     * a figure against a balance they only think they have. Nothing about them is worked out here:
     * the dates come from the calendar the 02:30 run walks and the lines say for themselves which
     * are already owed, exactly as the occurrences do. They are kept in a list of their own for the
     * reason {@link WhatTheRulesWillDo} gives — the two are different sentences, and merging them
     * would be a row with half its fields null.
     */
    @Transactional(readOnly = true)
    public WhatTheRulesWillDo whatTheRulesWillDo(long savingsAccountId) {
        Instant now = clock.instant();
        LocalDate from = TimelineHorizon.opensOn(now);
        LocalDate until = TimelineHorizon.closesOn(from);
        List<SavingRule> standing = rules.findBySavingsAccountIdAndStateInOrderByIdAsc(
                savingsAccountId, RuleState.theOnesStillStanding());
        List<Long> theirIds = standing.stream().map(SavingRule::getId).toList();
        Map<Long, List<AShareOfWhatMoves>> splitsByRule = theSplitsOf(theirIds);
        // Every pause every rule here has ever had, in one question, exactly as the night asks it.
        // The forecast counts from each rule's cursor and a resume deliberately leaves that cursor
        // where it was, so the days inside a closed pause are inside the window and the same
        // predicate the run uses is what takes them out again.
        Map<Long, List<RulePause>> pausesByRule = thePausesOf(theirIds);
        Map<Long, BigDecimal> balanceByCurrentAccount = new HashMap<>();
        List<AnOccurrenceToCome> coming = new ArrayList<>();
        for (SavingRule rule : standing) {
            coming.addAll(theOccurrencesToComeOf(rule,
                    splitsByRule.getOrDefault(rule.getId(), List.of()), theDayItMovesOn(rule),
                    pausesByRule.getOrDefault(rule.getId(), List.of()), now,
                    balanceByCurrentAccount));
        }
        // The order the nights would fire them in: by the day, and within a day by the order the
        // customer wrote the rules. The same comparison {@link #fireRulesDueBy} sorts a night by, so
        // that two rules falling on one morning are previewed in the order their money will move.
        coming.sort(Comparator.comparing(AnOccurrenceToCome::dueOn)
                .thenComparing(AnOccurrenceToCome::ruleId));
        // One line per read with the window it was asked over and what it came to, so that a preview
        // a customer says is short can be told apart from an account whose rules really do nothing:
        // the horizon is in it because it is the figure that decides the right-hand edge.
        // owedFromBefore says how many of those lines fall on days already past — transfers the next
        // run owes and has not made yet — because a list that starts before the window it names is
        // otherwise the sort of thing a reader assumes is a bug.
        //
        // The rules are counted in two figures rather than one, because a paused rule is standing
        // and contributes nothing: an account with two paused rules and none live answering
        // "rulesStanding=2 occurrencesToCome=0" reads as a broken forecast rather than as the right
        // answer, and this log is what a reviewer judges the feature from.
        // And what the same twelve months has going the other way. Asked of Accounts, which owns
        // bills and owns the calendar they fall on, so that the rent on this bar and the rent the
        // 02:30 run takes are one answer. Over this bar's own window, both ends of it, rather than
        // over one Accounts works out from a second reading of the clock: the day a bill is marked
        // already-owed against has to be the day this bar says it opens on, or a request that
        // straddled midnight would draw lines flagged against a left-hand edge that is not there.
        // By the holder rather than by the accounts this account's rules happen to draw from: the
        // question is asked hardest by somebody who has written no rule yet, and a forecast that
        // appeared only once a rule existed would arrive after the decision it exists to inform.
        List<ABillToCome> bills = accounts.holderOfSavingsAccount(savingsAccountId)
                .map(holder -> accounts.whatTheBillsOfACustomerWillTake(holder.customerId(), from,
                        until))
                .orElseGet(List::of);
        long paused = standing.stream().filter(SavingRule::isPaused).count();
        log.info("what the saving rules will do savingsAccountId={} between={}..{} horizon={} "
                        + "rulesThatCouldContribute={} rulesPaused={} occurrencesToCome={} "
                        + "owedFromBefore={} billDatesToCome={} billsOwedFromBefore={}",
                savingsAccountId, from, until, TimelineHorizon.HOW_FAR_AHEAD_THE_BAR_LOOKS,
                standing.size() - paused, paused, coming.size(),
                howManyFellBefore(coming.stream().map(AnOccurrenceToCome::dueOn).toList(), from),
                bills.size(),
                howManyFellBefore(bills.stream().map(ABillToCome::dueOn).toList(), from));
        return new WhatTheRulesWillDo(from, until, List.copyOf(coming), bills);
    }

    /**
     * What a rule nobody has saved would move if it fired this minute — and nothing else: no rule,
     * no occurrence, no deposit, no point, no row of any kind.
     *
     * <p><strong>The preview people actually want</strong>, because it is the one they want before
     * pressing save rather than after. It is asked of a rule exactly as it was typed, which is why
     * it takes the same {@link ARuleAsAsked} the save does.
     *
     * <p><strong>It refuses the same things a saved rule would, in the same sentences</strong>, and
     * it does so by asking the same three questions in the same order {@link #leaveARuleStanding}
     * asks them: that one customer holds both ends of it, that what it says is a rule this
     * application could fire, and that its holder has room for another. The last of those is asked
     * on purpose: a dry run that answered happily for a rule the save would then refuse as an
     * eleventh would be a preview of a rule that cannot exist, and the moment to be told is before
     * the customer has filled the form in rather than after. Nothing is a copy of a refusal — every
     * sentence here is the one {@code judged} and its neighbours already own — so the two cannot
     * drift apart.
     *
     * <p><strong>Today, which is what lets a sweep be promised here.</strong> The twelve-month
     * preview has to mark a sweep's figure as an illustration because next March's balance is not
     * knowable; this is asked of the balance that is in the account right now, so the figure is what
     * would actually move and is marked as such. The outcome is the same word the occurrence would
     * carry, out of the same comparison {@link #fire} makes.
     *
     * <p>Read-only, and that is the acceptance criterion rather than an optimisation: asking what a
     * rule would do must write nothing.
     *
     * @throws SavingRuleRefused if that is not a rule this application would leave standing
     */
    @Transactional(readOnly = true)
    public ADryRun whatAnUnsavedRuleWouldDo(long savingsAccountId, ARuleAsAsked asked) {
        log.debug("dry run asked for savingsAccountId={} fromCurrentAccountId={} name={} trigger={} "
                        + "dayOfWeek={} dayOfMonth={} howMuchMoves={} amount={} floor={} split={}",
                savingsAccountId, asked.currentAccountId(), asked.name(), asked.trigger(),
                asked.dayOfWeek(), asked.dayOfMonth(), asked.howMuchMoves(), asked.amount(),
                asked.floor(), inWords(asked.split()));
        refuseUnlessTheHolderHoldsBothEnds(savingsAccountId, asked.currentAccountId());
        ARuleAsItWouldRead itWouldRead = judged(savingsAccountId, null, asked.name(), asked.trigger(),
                asked.dayOfWeek(), asked.dayOfMonth(), asked.howMuchMoves(), asked.amount(),
                asked.floor());
        List<AShareOfWhatMoves> split = theSplitAsItWouldRead(savingsAccountId, null, asked.split());
        refuseUnlessThereIsRoomForAnother(savingsAccountId);

        ADryRun dryRun = theDryRunOf(savingsAccountId, null, asked.currentAccountId(), itWouldRead,
                split == null ? List.of() : split);
        log.info("dry run of an unsaved saving rule savingsAccountId={} fromCurrentAccountId={} "
                        + "name={} trigger={} howMuchMoves={} amount={} floor={} balance={} asAt={} "
                        + "wouldMove={} outcome={} shortfall={} intoGoals={} leftUnallocated={} "
                        + "wrote=nothing",
                savingsAccountId, asked.currentAccountId(), itWouldRead.name(),
                itWouldRead.trigger(), itWouldRead.howMuchMoves(),
                asMoneyOrNothing(itWouldRead.amount()), asMoneyOrNothing(itWouldRead.floor()),
                AmountOfMoney.asMoney(dryRun.balance()), dryRun.asAt(),
                AmountOfMoney.asMoney(dryRun.wouldMove().amount()), dryRun.outcome(),
                asMoneyOrNothing(dryRun.shortfall()),
                inWordsWhatTheGoalsWouldGet(dryRun.wouldMove().intoGoals()),
                AmountOfMoney.asMoney(dryRun.wouldMove().leftUnallocated()));
        return dryRun;
    }

    /**
     * What a rule that already stands would move if it fired this minute with a change applied to
     * it: the same answer {@link #whatAnUnsavedRuleWouldDo} gives, for a rule being edited rather
     * than written for the first time.
     *
     * <p>A way in of its own rather than a flag on that one, because two of the questions it asks
     * do not apply to a rule that already exists.
     *
     * <p><strong>There is no room to make.</strong> Changing a rule leaves the number standing
     * exactly as it was, so asking {@code refuseUnlessThereIsRoomForAnother} here would tell a
     * customer holding the ten this application allows that they cannot see what a change to one of
     * them would move — a refusal about an eleventh rule nobody is asking for, on every keystroke
     * of a form that creates nothing.
     *
     * <p><strong>The account is the rule's own.</strong> A standing rule cannot be moved between
     * current accounts, so the balance this is measured against is the one it already draws from
     * rather than one the caller names.
     *
     * <p>Everything else is the change itself, asked in the order {@link #changeRule} asks it and
     * refused in the same sentences: that there is such a rule, that it has not been ended, that the
     * change says something, that it carries nothing the rule would have no use for, and that the
     * rule it would leave behind is one this application could fire. A change saying nothing about
     * the split is previewed against the split the rule already has, which is what the change itself
     * would leave in place.
     *
     * <p>Read-only, and that is the acceptance criterion rather than an optimisation: asking what a
     * change would do must write nothing.
     *
     * @throws SavingRuleRefused if there is no such rule, if it has been ended, or if the change is
     *                           one this module will not make
     */
    @Transactional(readOnly = true)
    public ADryRun whatAChangedRuleWouldDo(long savingsAccountId, long ruleId,
                                           AChangeToARule change) {
        log.debug("dry run of a change asked for savingsAccountId={} ruleId={} name={} trigger={} "
                        + "dayOfWeek={} dayOfMonth={} howMuchMoves={} amount={} floor={} split={}",
                savingsAccountId, ruleId, change.name(), change.trigger(), change.dayOfWeek(),
                change.dayOfMonth(), change.howMuchMoves(), change.amount(), change.floor(),
                inWords(change.split()));
        SavingRule rule = theRuleOn(savingsAccountId, ruleId);
        refuseUnlessItHasNotBeenEnded(savingsAccountId, rule, "changed");
        refuseUnlessItAsksForSomething(savingsAccountId, ruleId, change);
        refuseWhatTheRuleWouldHaveNoUseFor(savingsAccountId, ruleId, rule, change);
        List<AShareOfWhatMoves> asked = theSplitAsItWouldRead(savingsAccountId, ruleId,
                change.split());
        ARuleAsItWouldRead itWouldRead = judged(savingsAccountId, ruleId,
                change.name() == null ? rule.getName() : change.name(),
                change.trigger() == null ? rule.getTrigger() : change.trigger(),
                change.dayOfWeek() == null ? rule.getDayOfWeek() : change.dayOfWeek(),
                change.dayOfMonth() == null ? rule.getDayOfMonth() : change.dayOfMonth(),
                change.howMuchMoves() == null ? rule.getHowMuchMoves() : change.howMuchMoves(),
                change.amount() == null ? rule.getAmount() : change.amount(),
                change.floor() == null ? rule.getFloor() : change.floor());
        List<AShareOfWhatMoves> split = asked == null ? theSplitOf(rule.getId()) : asked;

        ADryRun dryRun = theDryRunOf(savingsAccountId, ruleId, rule.getCurrentAccountId(),
                itWouldRead, split);
        log.info("dry run of a change to a standing saving rule savingsAccountId={} ruleId={} "
                        + "fromCurrentAccountId={} name={} trigger={} howMuchMoves={} amount={} "
                        + "floor={} balance={} asAt={} wouldMove={} outcome={} shortfall={} "
                        + "intoGoals={} leftUnallocated={} wrote=nothing",
                savingsAccountId, ruleId, rule.getCurrentAccountId(), itWouldRead.name(),
                itWouldRead.trigger(), itWouldRead.howMuchMoves(),
                asMoneyOrNothing(itWouldRead.amount()), asMoneyOrNothing(itWouldRead.floor()),
                AmountOfMoney.asMoney(dryRun.balance()), dryRun.asAt(),
                AmountOfMoney.asMoney(dryRun.wouldMove().amount()), dryRun.outcome(),
                asMoneyOrNothing(dryRun.shortfall()),
                inWordsWhatTheGoalsWouldGet(dryRun.wouldMove().intoGoals()),
                AmountOfMoney.asMoney(dryRun.wouldMove().leftUnallocated()));
        return dryRun;
    }

    /**
     * The arithmetic both dry runs end in: what this rule would move out of that account today, and
     * what would become of it.
     *
     * <p>One body for the two, so that the figure somebody reads before leaving a new rule standing
     * and the figure they read before changing one that already stands are worked out by the same
     * code against the same balance. The two callers differ only in what each of them had to refuse
     * before getting here, and in the line each of them writes about the answer.
     *
     * @param ruleId the rule being changed, or null for one nobody has saved, for the log
     */
    private ADryRun theDryRunOf(long savingsAccountId, Long ruleId, long fromCurrentAccountId,
                                ARuleAsItWouldRead itWouldRead, List<AShareOfWhatMoves> split) {
        Instant now = clock.instant();
        LocalDate asAt = WhichOccurrencesAreDue.theDayItFallsOn(now);
        BigDecimal balance = theBalanceItWouldDrawFrom(fromCurrentAccountId, ruleId,
                new HashMap<>());
        // Not an illustration: a dry run is about today, so a sweep's figure is the one that would
        // actually move rather than a worked example of a balance nobody has yet.
        WhatWouldMove wouldMove = whatItWouldMove(itWouldRead.howMuchMoves(), itWouldRead.amount(),
                itWouldRead.floor(), split, balance, false);
        BigDecimal figure = wouldMove.amount();

        OccurrenceOutcome outcome;
        BigDecimal shortfall = null;
        if (figure.signum() <= 0) {
            outcome = OccurrenceOutcome.NOTHING_TO_MOVE;
        } else if (balance.compareTo(figure) < 0) {
            outcome = OccurrenceOutcome.NOT_ENOUGH_MONEY;
            shortfall = AmountOfMoney.quotedToTheCent(figure.subtract(balance));
            // A refusal in everything but name: this rule would move no money on the day it was
            // saved for, and the three figures it was decided on are what say why.
            log.warn("dry run says the rule could not be honoured savingsAccountId={} ruleId={} "
                            + "fromCurrentAccountId={} name={} asked={} balance={} shortfall={} "
                            + "reason=a fixed amount moves all of itself or none of it, and there "
                            + "is not enough in the account to move",
                    savingsAccountId, ruleId, fromCurrentAccountId, itWouldRead.name(),
                    AmountOfMoney.asMoney(figure), AmountOfMoney.asMoney(balance),
                    AmountOfMoney.asMoney(shortfall));
        } else {
            outcome = OccurrenceOutcome.MOVED;
        }
        return new ADryRun(asAt, AmountOfMoney.quotedToTheCent(balance), outcome, wouldMove,
                shortfall);
    }

    /**
     * Every occurrence on this savings account that could not be honoured and was recorded after the
     * one the caller names, oldest first — the transfers a customer was relying on that did not
     * happen.
     *
     * <p>Written for the nightly notifications sweep and public for it. That sweep is the one
     * producer of notifications in this application and it reads this module rather than being
     * written to by it: a rules job that raised a notification of its own would be a second writer
     * in a second module, and would split the one place this feature logs. So the rules job records
     * what happened and this read is how the four-o'clock sweep finds out about it, an hour after
     * the two-o'clock run has settled.
     *
     * <p><strong>The two outcomes that are refusals, and the two that are not are not an
     * oversight.</strong> {@link OccurrenceOutcome#NOT_ENOUGH_MONEY} is a transfer the customer was
     * relying on that did not happen; {@link OccurrenceOutcome#THE_ACCOUNT_IS_CLOSED} is a rule
     * still pointing at an account that will never take money again, which is arguably the louder of
     * the two because it will go on happening every due day until somebody changes the rule. An
     * occurrence that moved money is exactly what a customer automated their saving in order to stop
     * being told about, and a sweep that found nothing above its floor moved nothing because the
     * arithmetic said nothing — reporting that as a failure would train a customer to ignore the
     * thing that tells them about the real ones.
     *
     * <p>Both in one answer rather than a read each, because they are one walk through one record
     * in one order and the caller keeps one place in it — and because which of the two an occurrence
     * is is written on the occurrence, so a caller that wants to say two different things about them
     * can. This module still decides nothing about what is said: it answers what happened, and what
     * anybody has been told about it lives in the reader's own record.
     *
     * <p><strong>{@code after} is the caller's own place in this record, and nothing this module
     * interprets.</strong> Which failures have already been reported on is the reader's question and
     * lives in the reader's record — this module knows what happened and not what anybody has been
     * told about it — but a reader that walks this history forwards night after night should not
     * have to read the whole of it each time in order to find the last day of it. So it says where
     * it got to and is given what has been written since; zero is the beginning, because identifiers
     * start at one. Nothing is dropped here: an occurrence is only behind a caller's place because
     * that caller put its place in front of it.
     *
     * <p>Not scoped to the rules still standing: the history of an ended rule stays readable, and a
     * transfer that did not happen is not un-happened by the rule later being closed.
     */
    @Transactional(readOnly = true)
    public List<RecordedOccurrence> occurrencesThatCouldNotBeHonouredIn(long savingsAccountId,
                                                                       long after) {
        List<RecordedOccurrence> couldNotBeHonoured = occurrences
                .findOnSavingsAccountWithOutcomeAfter(
                        savingsAccountId, THE_OUTCOMES_THAT_ARE_REFUSALS, after)
                .stream()
                // Nothing moved on any of these, so nothing went into a goal either: an occurrence
                // that could not be honoured moved no money for a split to spread. Reading the
                // allocations back for them would be a query that can only ever answer nothing.
                .map(occurrence -> asRecorded(occurrence, List.of()))
                .toList();
        log.debug("occurrences that could not be honoured read savingsAccountId={} after={} "
                + "occurrences={}", savingsAccountId, after, couldNotBeHonoured.size());
        return couldNotBeHonoured;
    }

    /**
     * Which of these deposits this module made, so that a money history can tell what the customer
     * did from what the application did for them.
     *
     * <p><strong>Asked in this direction on purpose.</strong> The obvious shape would be a deposit
     * that knows it was automatic, and it is exactly the shape this feature is built without:
     * Deposits is the module Automation calls to move money, and a Deposits that asked back which
     * of its deposits were automatic would be a cycle the application context could not start. So
     * the page hands over the identifiers it is about to draw and is told which of them a rule is
     * behind, and {@code deposits} stays ignorant that this module exists.
     *
     * <p>One question for the whole page rather than one per row, and an empty list is answered
     * without asking the database anything: a customer who has moved nothing has nothing for a rule
     * to have made, and {@code in ()} is not a thing to ask SQLite. A deposit no rule made is simply
     * absent from the answer, which is what the caller reads as a button somebody pressed.
     *
     * <p>Not scoped to a rule, an account or a customer, because it answers about deposits the
     * caller is already holding: whoever asks has decided which deposits are theirs to draw, and
     * every one of them reached that page through a read that vouched for its account.
     */
    @Transactional(readOnly = true)
    public Set<Long> whichOfTheseDepositsWereAutomatic(Collection<Long> depositIds) {
        if (depositIds.isEmpty()) {
            log.debug("deposits asked about for automation none, so none of them were automatic");
            return Set.of();
        }
        Set<Long> madeByARule = Set.copyOf(occurrences.whichOfTheseDepositsWereMadeByARule(depositIds));
        log.debug("deposits asked about for automation asked={} madeByARule={}",
                depositIds.size(), madeByARule.size());
        return madeByARule;
    }

    /**
     * Fires every occurrence that has fallen due by the given moment and has not been settled yet,
     * each one recorded against the day it was due.
     *
     * <p>Every occurrence rather than today's, which is what makes a weekly rule demonstrable in an
     * afternoon: winding the clock a month forward and running this once fires the days in between.
     * It is not a nicety but the only path there is — the clock moves in whole calendar days and the
     * cron never fires for the days it skipped, so on a wound clock catching up is the way an
     * occurrence is <em>ever</em> fired.
     *
     * <p><strong>Oldest first, and within a day in the order the customer created the rules.</strong>
     * The whole night is worked out before any of it is fired, so that two rules falling on one
     * morning are dealt with in the order they were written rather than in the order the rules
     * happened to be read. Deterministic, reconstructable from the log, and it needs no priority
     * field anybody would have to maintain. The second rule of a morning may find less than it
     * wanted, and that is recorded honestly rather than papered over.
     *
     * <p><strong>The deposit is an ordinary deposit.</strong> It goes through
     * {@code DepositsService.deposit} like any other, earns points at the streak rate, counts toward
     * the week and obeys every rule about new saving. There is no special case, and adding one would
     * be a defect.
     *
     * <p><strong>A payday rule fires on the days a salary actually landed</strong>, read out of the
     * record {@code accounts} keeps of what it credited rather than worked out from the calendar —
     * see {@link #theDaysItFellDueOn}. That is also why this job runs an hour after the income job
     * rather than beside it: by two in the morning the record says what one o'clock did.
     *
     * <p><strong>At most five hundred occurrences per rule, and the rest are delayed rather than
     * lost.</strong> The development clock goes a hundred years forward in one move and a weekly
     * rule over that span is five thousand two hundred transfers, which is a demonstration hanging
     * the application by accident. A rule that is capped has its cursor left at the day this run
     * stopped at rather than at the moment it ran, so the next run continues from there — see
     * {@link #asManyOfThemAsOneRunCatchesUp}, which also says in a WARN which days were left.
     *
     * <p>Idempotent by construction. Every firing writes a row naming the rule and the day due, the
     * pair is unique in the database ({@link AutomationOnStartUp}), and a second run finds every day
     * already settled — and would in any case find each rule's cursor already past it. A trainer runs
     * this by hand, twice, and a demonstration that doubled every figure in it would be worse than no
     * demonstration.
     *
     * <p>Answers nothing, for the reason the income run gives: its one caller runs on a schedule with
     * nobody waiting on it, and a figure returned to a scheduled method is a figure nothing can read.
     * What the run did is in the INFO lines below.
     *
     * <p>Public, unlike the rows and the repositories, and not because anything outside wants it:
     * {@code @Transactional} is applied by a proxy and a proxy cannot advise a method that is not
     * public, so the annotation would be silently ignored and a half-finished night would commit.
     * The power this leaks is the power to run the nightly job early, which is idempotent and is
     * exactly what the development jobs endpoint offers anyway.
     *
     * <p>The caller says what time it is. This module reads the clock for a rule being written, which
     * has a customer in front of it; a run has to judge every rule against one moment, and taking it
     * once in the job is what guarantees that.
     */
    @Transactional
    public void fireRulesDueBy(Instant now) {
        List<SavingRule> standing = rules.findByStateOrderByIdAsc(RuleState.LIVE);
        List<AnOccurrenceToFire> due = new ArrayList<>();
        // Where each rule's cursor is left once its occurrences have been fired. A rule whose
        // catch-up was capped is settled through the day this run stopped at rather than through
        // the moment it ran, which is the whole of "the next run continues from where this one
        // stopped"; every other rule is settled through the moment, and is not in here at all.
        Map<Long, Instant> settledThroughByRule = new HashMap<>();
        // Every pause every rule in this night has ever had, in one question rather than one per
        // rule. Only rules somebody has actually stopped at some point have any, so on an ordinary
        // night this answers nothing and costs one query.
        Map<Long, List<RulePause>> pausesByRule =
                thePausesOf(standing.stream().map(SavingRule::getId).toList());
        Instant lookedBackTo = now;
        long leftForANextRun = 0;
        for (SavingRule rule : standing) {
            Instant settledThrough = theMomentItIsSettledThrough(rule);
            if (settledThrough != null && settledThrough.isBefore(lookedBackTo)) {
                lookedBackTo = settledThrough;
            }
            List<LocalDate> days = whatIsDueFor(rule, settledThrough, now,
                    pausesByRule.getOrDefault(rule.getId(), List.of()));
            List<LocalDate> thisRun = asManyOfThemAsOneRunCatchesUp(rule, days);
            if (thisRun.size() < days.size()) {
                leftForANextRun += days.size() - thisRun.size();
                settledThroughByRule.put(rule.getId(), WhichOccurrencesAreDue
                        .theMomentThatDayBegins(thisRun.get(thisRun.size() - 1)));
            }
            thisRun.forEach(day -> due.add(new AnOccurrenceToFire(rule, day)));
        }
        // The whole night sorted before any of it is fired: by the day the occurrence fell on, and
        // then by the order the rules were written. Sorting after gathering rather than firing each
        // rule's days as they are found is what makes the order across rules a promise rather than
        // an accident of which rule was read first.
        due.sort(Comparator.comparing(AnOccurrenceToFire::dueOn)
                .thenComparing(occurrence -> occurrence.rule().getId()));

        long fired = 0;
        BigDecimal movedAltogether = BigDecimal.ZERO;
        Set<Long> rulesWithSomethingStillDue = new HashSet<>();
        for (AnOccurrenceToFire occurrence : due) {
            Optional<BigDecimal> moved = fire(occurrence.rule(), occurrence.dueOn(), now);
            if (moved.isEmpty()) {
                rulesWithSomethingStillDue.add(occurrence.rule().getId());
                continue;
            }
            fired++;
            movedAltogether = movedAltogether.add(moved.get());
        }

        for (SavingRule rule : standing) {
            if (rulesWithSomethingStillDue.contains(rule.getId())) {
                // An occurrence that was due and could not be settled stays due. Moving the cursor
                // over it would lose it for good, and the one thing that stops an occurrence being
                // settled is a current account that is no longer there — which is a state somebody
                // could put right, and which the WARN from the firing itself already names.
                log.warn("saving rule cursor left where it was ruleId={} settledThrough={} "
                                + "reason=an occurrence this run could not settle is still due",
                        rule.getId(), rule.getSettledThrough());
                continue;
            }
            rule.settledThrough(settledThroughByRule.getOrDefault(rule.getId(), now));
            rules.save(rule);
        }

        // One line per run with everything that decided it: the stretch of time it covered — which
        // on a wound clock or after downtime is months rather than a night, and is the figure that
        // explains a run that fired six occurrences — how many standing rules it walked, how many
        // occurrences it settled, how many it deliberately left for the next run, and what they came
        // to. A savings account that grew overnight is explainable from this line alone, and a run
        // that walked three rules and fired none can be told from a run that was handed none.
        //
        // The bottom of the range is the oldest cursor of the rules it walked, because that is how
        // far back this run could have reached; it is the moment itself on a run with no rules to
        // walk, which is a range covering nothing and says so.
        log.info("saving rules run asAt={} lookedBetween={}..{} rulesConsidered={} "
                        + "occurrencesFired={} occurrencesLeftForANextRun={} amount={}",
                now, lookedBackTo, now, standing.size(), fired, leftForANextRun,
                AmountOfMoney.asMoney(movedAltogether));
    }

    /**
     * As many of the days a rule is due for as one run will settle, oldest first, saying what it
     * left behind when it leaves anything.
     *
     * <p>The cap is applied to the days that are actually about to fire — after the record has been
     * asked which periods are already settled — so that five hundred is five hundred transfers and
     * not five hundred questions, and a run is never capped over days it was going to drop anyway.
     *
     * <p>The WARN names the days left as well as how many, because "four hundred and seventy-one
     * occurrences were left" is a number somebody has to go and reconstruct a calendar from, while
     * the first and the last day left are the two figures that say when the catch-up will finish.
     */
    private List<LocalDate> asManyOfThemAsOneRunCatchesUp(SavingRule rule, List<LocalDate> days) {
        if (days.size() <= MOST_OCCURRENCES_ONE_RULE_CATCHES_UP_IN_ONE_RUN) {
            return days;
        }
        List<LocalDate> thisRun = days.subList(0, MOST_OCCURRENCES_ONE_RULE_CATCHES_UP_IN_ONE_RUN);
        List<LocalDate> left = days.subList(MOST_OCCURRENCES_ONE_RULE_CATCHES_UP_IN_ONE_RUN,
                days.size());
        log.warn("saving rule catch-up capped ruleId={} name={} savingsAccountId={} trigger={} "
                        + "occurrencesDue={} firedThisRun={} caughtUpTo={} leftForTheNextRun={} "
                        + "firstLeft={} lastLeft={} reason=at most {} occurrences are caught up for "
                        + "one rule in one run, so that a clock wound a century forward cannot hang "
                        + "the application; nothing is lost, and the next run continues from the day "
                        + "this one stopped at",
                rule.getId(), rule.getName(), rule.getSavingsAccountId(), rule.getTrigger(),
                days.size(), thisRun.size(), thisRun.get(thisRun.size() - 1), left.size(),
                left.get(0), left.get(left.size() - 1),
                MOST_OCCURRENCES_ONE_RULE_CATCHES_UP_IN_ONE_RUN);
        return thisRun;
    }

    /**
     * The moment a rule's occurrences are settled through, which is where a run counts its range
     * from.
     *
     * <p>Null only on a row written before the column existed: a rule left standing sets its cursor
     * and nothing ever clears it. Such a row is read from the moment it was written — where its
     * cursor would have started — rather than thrown over, because this run is one transaction over
     * every standing rule and one old row would otherwise abort the night for everybody.
     */
    private Instant theMomentItIsSettledThrough(SavingRule rule) {
        Instant settledThrough = rule.getSettledThrough();
        if (settledThrough != null) {
            return settledThrough;
        }
        log.warn("saving rule cursor missing ruleId={} name={} reason=the moment this rule is "
                        + "settled through is not recorded; it is read from the moment it was "
                        + "left standing instead createdAt={}",
                rule.getId(), rule.getName(), rule.getCreatedAt());
        return rule.getCreatedAt();
    }

    /**
     * The days this rule has fallen due on and not yet been settled for, oldest first.
     *
     * <p>Two things are asked, and they are different questions. The first is which days the rule
     * fell due on — the calendar for a weekly or a monthly rule, and the record of the salaries that
     * actually landed for a payday one ({@link #theDaysItFellDueOn}). The record is then asked
     * whether it already holds an occurrence in the period each of those days falls in — because the
     * cursor is a figure this application writes and the record is what actually happened, and an
     * idempotency that rested on the cursor alone would rest on a number a hand-edited row could
     * move.
     *
     * <p>The cursor is handed in rather than read here, because the run needs it for itself: it is
     * the bottom of the range the run reports having covered, and a second read of it after the
     * fallback for a missing one would be a second chance to disagree about where a rule starts.
     *
     * <p><strong>A third thing is then taken away, and it is taken away first of the two.</strong>
     * Days that fell inside a pause are excluded from the range outright — never recorded, never
     * considered — because they are the customer's instruction rather than the application's fault.
     * Before the record is asked about periods rather than after, so that a day nobody was ever
     * going to fire cannot reserve its week or its month against a day somebody was.
     */
    private List<LocalDate> whatIsDueFor(SavingRule rule, Instant settledThrough, Instant now,
                                         List<RulePause> itsPauses) {
        Integer dayOfMonth = theDayItMovesOn(rule);
        List<LocalDate> days = notCoveredByAPause(rule, itsPauses,
                theDaysItFellDueOn(rule, dayOfMonth, settledThrough, now));
        // The inputs behind the decision: the cursor the range was counted from, the day the trigger
        // needs, and what that came to. A run that fired nothing is explainable from this line
        // without anybody having to reconstruct the calendar by hand.
        log.debug("saving rule considered ruleId={} name={} savingsAccountId={} trigger={} "
                        + "dayOfWeek={} dayOfMonth={} settledThrough={} asAt={} daysDue={}",
                rule.getId(), rule.getName(), rule.getSavingsAccountId(), rule.getTrigger(),
                rule.getDayOfWeek(), dayOfMonth, settledThrough, now, days);
        return dueInPeriodsNotAlreadySettled(rule, days);
    }

    /**
     * Those of the days that did not fall inside a pause — the whole of "a pause is never made up".
     *
     * <p><strong>Excluded from the range outright rather than recorded and then judged.</strong> The
     * history of a rule paused through a fortnight has nothing in it for that fortnight, because
     * nothing happened in it: an occurrence written and marked as skipped would be this application
     * saying it considered moving money on a morning its customer had told it not to.
     *
     * <p><strong>This is the only thing that excludes them, for every trigger.</strong> Nothing
     * about the cursor helps: a resume deliberately leaves it where it was, because the days in
     * front of it include the ones the rule was owed from <em>before</em> the pause — downtime,
     * which is always caught up. So the whole of "a pause is never made up" is this one predicate,
     * and the catch-up range behind it is the whole of "downtime always is".
     *
     * <p><strong>Every pause the rule has ever had, not only the last one.</strong> A closed window
     * goes on mattering, and a payday rule is where that is most obvious: its days come out of the
     * record of salaries actually credited, asked by <em>when they were credited</em> so that a late
     * salary is never lost, so a salary credited two pauses later for a day inside the first of them
     * arrives at the run long after that window closed. A customer who pauses, resumes and pauses
     * again has more than one window for such a day to fall in, so all of them are asked.
     *
     * <p>An INFO line rather than a DEBUG one, because on the nights it says anything it is money
     * that deliberately did not move, and the days it names are days that will never appear in the
     * rule's history to be reconciled against. On every other night it says nothing at all.
     */
    private List<LocalDate> notCoveredByAPause(SavingRule rule, List<RulePause> itsPauses,
                                               List<LocalDate> days) {
        if (days.isEmpty() || itsPauses.isEmpty()) {
            return days;
        }
        List<LocalDate> outsideEveryPause = new ArrayList<>();
        List<LocalDate> passedOver = new ArrayList<>();
        for (LocalDate day : days) {
            Instant theMomentItBegins = WhichOccurrencesAreDue.theMomentThatDayBegins(day);
            if (itsPauses.stream().anyMatch(pause -> pause.covers(theMomentItBegins))) {
                passedOver.add(day);
            } else {
                outsideEveryPause.add(day);
            }
        }
        if (!passedOver.isEmpty()) {
            log.info("saving rule occurrences passed over as falling inside a pause ruleId={} "
                            + "name={} savingsAccountId={} trigger={} daysPassedOver={} "
                            + "daysLeftToFire={} pauses={} reason=a pause is the customer's own "
                            + "instruction and is never made up, unlike downtime, which always is",
                    rule.getId(), rule.getName(), rule.getSavingsAccountId(), rule.getTrigger(),
                    passedOver, outsideEveryPause, inWordsThePauses(itsPauses));
        }
        return outsideEveryPause;
    }

    /**
     * The days this rule fell due on between its cursor and now, before anything is asked about what
     * it has already been settled for.
     *
     * <p><strong>A payday rule's days come from the record and every other rule's from the
     * calendar</strong>, and that difference is the whole of this method. A weekly rule moves on a
     * weekday and a monthly one on a day of the month: those are calendar facts, and
     * {@link WhichOccurrencesAreDue} is the whole of them. A payday is not a calendar fact. It is a
     * salary that landed, or did not — {@code accounts} holds that record in {@code income_paid} and
     * is the only thing that knows — and deriving the days from
     * {@link io.dataroots.savingstreak.accounts.DeclaredIncome#dayOfMonth} instead walks the
     * <em>rule's</em> cursor over a declaration that has a cursor of its own, with nothing to
     * reconcile the two. Every month the salary did not land in but the calendar says it should have
     * is then a firing: real money out of a current account on a morning nobody was paid, and a
     * sweep on such a morning drains the account to its floor.
     *
     * <p>It is the same mistake this feature has now made in three places — the salary paid twice,
     * the rule fired twice in a period, the rule fired in a period with no salary in it — and it is
     * one mistake: <em>asking the calendar a question only the record can answer</em>. The other two
     * were fixed by asking the record, and so is this one.
     *
     * <p><strong>The record is asked by when it credited, not by the day it credited for</strong>,
     * and that is what stops a late salary being lost. A payday is credited with two facts — the day
     * it was <em>due</em> and the moment it was <em>paid</em> — and they part company whenever the
     * two jobs run out of the order the night runs them in: the rules job at two settles a rule
     * through today, the income job then credits today's salary, and a rule asked for the days
     * <em>due</em> since its cursor never sees it again, because the cursor is already past that
     * day and only ever moves forward. The salary sits in the customer's current account and the
     * rule that was supposed to save out of it has quietly lost the month. Asked instead for what
     * has been <em>credited</em> since the cursor, it is found the first time anybody looks after it
     * landed. One query per payday rule per run, whatever the clock has been wound by.
     *
     * <p><strong>Which leaves the rule's own beginning to be applied to the days themselves</strong>,
     * and {@link WhichOccurrencesAreDue#theDaysSalaryLandedOn} applies it: the moment the rule was
     * left standing, which is fixed, rather than the cursor, which moves. A catch-up that credits
     * six months of salary in one go hands back six days, and a rule written last month is entitled
     * to exactly the ones that fell after it existed — the same boundary the calendar path applies,
     * and the same promise, that a new rule is an instruction about the future rather than a bill
     * for the past.
     *
     * <p>Firing a day twice is <em>not</em> what either bound prevents. The record of occurrences is
     * what prevents it, one per rule per period, asked in {@link #dueInPeriodsNotAlreadySettled} —
     * so widening the question from the cursor to the rule's whole life costs no idempotency. It
     * costs a payday already settled being offered again on the run after a late credit, and being
     * dropped there rather than here, which is the trade this feature has already made twice: the
     * cursor bounds the work and the record decides the money.
     *
     * <p><strong>Not flooring the range at when the income was declared</strong>, which is the
     * shortcut that looks like it would do: re-declaring an existing income updates
     * {@code declaredAt} and deliberately leaves {@code paidThrough} alone
     * ({@code AccountsService.declareMonthlyIncome}), so a customer correcting their salary amount
     * would have the firings for paydays already credited suppressed underneath them. The record
     * answers both cases without a special case in either.
     */
    private List<LocalDate> theDaysItFellDueOn(SavingRule rule, Integer dayOfMonth,
                                               Instant settledThrough, Instant now) {
        if (rule.getTrigger() != RuleTrigger.ON_PAYDAY) {
            return WhichOccurrencesAreDue.daysDueBetween(rule.getTrigger(), rule.getDayOfWeek(),
                    dayOfMonth, settledThrough, now);
        }
        if (settledThrough == null) {
            return List.of();
        }
        List<LocalDate> salariesCredited = accounts.paydaysCreditedBetween(
                rule.getCurrentAccountId(), settledThrough, now);
        // The record this rule's days are read out of, said out loud beside the days themselves: a
        // payday rule that fired once where a trainer expected three fired once because one salary
        // landed, and this is the line that shows it without anybody opening the database. Both
        // bounds are here, because they are different questions — what was credited since the
        // cursor, and which of those days the rule was already standing on.
        log.debug("the salaries a payday rule reads its days from ruleId={} name={} "
                        + "fromCurrentAccountId={} creditedBetween={}..{} standingSince={} "
                        + "salariesCredited={}",
                rule.getId(), rule.getName(), rule.getCurrentAccountId(), settledThrough, now,
                rule.getCreatedAt(), salariesCredited);
        return WhichOccurrencesAreDue.theDaysSalaryLandedOn(
                salariesCredited, rule.getCreatedAt(), now);
    }

    /**
     * Those of the days falling in a period this rule has not already been settled in.
     *
     * <p><strong>A period holds one firing.</strong> That is the rule, and it is the record that
     * keeps it rather than the cursor: the cursor is what keeps a run bounded and the record is what
     * makes it idempotent, and they are different guarantees. Asking the record which exact
     * <em>days</em> it holds keeps neither on its own, and the gap is money. A customer whose payday
     * rule fired on the 15th and who then corrects their declared payday to the 20th has a 20th of
     * April that no cursor sitting at the 15th excludes and no row under that day denies, and would
     * have their money moved twice in the month for having told the application when they are paid.
     * A rule PATCHed from the 15th to the 25th, or from Monday to Friday inside one week, is the
     * same hole through a second door: {@link SavingRule#nowSays} rewrites the day and leaves the
     * cursor where it was, on purpose, and the unique index over rule and day due cannot catch two
     * different days. All of them are one question — has this rule already had its turn in that week
     * or that month — and it is asked here, once, for every way the day can move.
     *
     * <p>{@code AccountsService.dueInMonthsNotAlreadyPaid} is this method for a salary and says the
     * same thing about a month; {@link WhichOccurrencesAreDue#thePeriodItMovesInOnce} is where the
     * two triggers' periods are named.
     *
     * <p><strong>Any occurrence counts as the turn, not only one that moved money.</strong> A rule
     * that fell due on the 15th and found nothing to move has been answered for April, and firing it
     * again on the 20th because the first answer was disappointing would be a retry nobody asked
     * for — it is recorded in the history and warned about in the log either way.
     *
     * <p>One query for the whole stretch rather than one per day, so that a clock wound three years
     * forward is a query per rule and not a thousand. The stretch is whole periods — the start of
     * the first to the end of the last — because which day of a period was settled is exactly what
     * must not be assumed, and {@code days} is oldest first, which
     * {@link WhichOccurrencesAreDue#daysDueBetween} promises.
     */
    private List<LocalDate> dueInPeriodsNotAlreadySettled(SavingRule rule, List<LocalDate> days) {
        if (days.isEmpty()) {
            return days;
        }
        RuleTrigger trigger = rule.getTrigger();
        LocalDate fromTheStartOfTheFirstPeriod =
                WhichOccurrencesAreDue.thePeriodItMovesInOnce(trigger, days.get(0));
        LocalDate toTheEndOfTheLastPeriod = WhichOccurrencesAreDue.theLastDayOfThePeriodHolding(
                trigger, days.get(days.size() - 1));
        Set<LocalDate> periodsAlreadySettled = occurrences
                .whichDaysWereSettledBetween(rule.getId(), fromTheStartOfTheFirstPeriod,
                        toTheEndOfTheLastPeriod)
                .stream()
                .map(day -> WhichOccurrencesAreDue.thePeriodItMovesInOnce(trigger, day))
                .collect(Collectors.toSet());
        if (periodsAlreadySettled.isEmpty()) {
            return days;
        }
        // Which periods were passed over and why they were asked about at all, because a run that
        // fired one occurrence fewer than a trainer expected is otherwise a silent subtraction — and
        // a day dropped because its week or its month was already settled is the one subtraction
        // nobody would reconstruct from the calendar alone.
        log.debug("saving rule occurrences passed over as already settled in their period ruleId={} "
                        + "trigger={} periodsAlreadySettled={} lookedBetween={}..{}",
                rule.getId(), trigger, periodsAlreadySettled, fromTheStartOfTheFirstPeriod,
                toTheEndOfTheLastPeriod);
        return days.stream()
                .filter(day -> !periodsAlreadySettled.contains(
                        WhichOccurrencesAreDue.thePeriodItMovesInOnce(trigger, day)))
                .toList();
    }

    /**
     * Settles one occurrence of one rule: works out what it would move, moves it if it can, and
     * writes down what happened either way.
     *
     * <p>The balance is read and judged here rather than left to the deposit to refuse, and that is
     * not a stylistic choice. {@code DepositsService.deposit} refuses by throwing, this run is one
     * transaction over every standing rule, and an exception crossing a transactional boundary marks
     * that transaction rollback-only — so a caught refusal would still take the whole night down at
     * commit. Asking first is what lets one rule that cannot be honoured be recorded as such while
     * every other rule's money still moves.
     *
     * <p><strong>The savings account is asked about first, and for the same reason.</strong> A rule
     * whose savings account has been closed since it was left standing is refused by Deposits in
     * words this run records and does not write; it is asked as a question rather than met as an
     * exception, so that one rule pointing at a closed account cannot abort a night for everybody
     * else. It is settled rather than left due, because unlike a current account that has gone a
     * closed account is not a state anybody can put right.
     *
     * <p><strong>What that covers, and what it does not.</strong> It covers the shortfall, which is
     * the refusal that actually happens: a rule asking for more than the account holds is recorded
     * as {@link OccurrenceOutcome#NOT_ENOUGH_MONEY} and every other rule on the same run still
     * commits. It does not make the pair atomic. The balance is read here and the deposit is made a
     * line later, and {@code DepositsService} re-checks it for itself; a withdrawal committing in
     * between would have {@code deposit} throw after all, and that refusal would abort the run for
     * every customer and roll back the occurrences already written. It is left as it is, knowingly:
     * this application writes to one SQLite file through one writer and the nightly run is one
     * transaction, so there is no committed write for the window to admit, and closing it properly
     * means a transaction per occurrence — a boundary that would also let a half-settled night
     * commit, which is a worse thing to own than a window nothing in the lab can open. It is written
     * down here rather than defended against, so that whoever gives an occurrence its own
     * transaction knows what they are buying.
     *
     * @return what moved, which is nothing for an occurrence that moved nothing — or empty when the
     *         occurrence could not be settled at all and is therefore still due
     */
    private Optional<BigDecimal> fire(SavingRule rule, LocalDate dueOn, Instant now) {
        // Where the money is going, before what it would be. A savings account whose agreement has
        // ended takes no more money, and a rule left standing on one has nowhere to put a cent —
        // so the balance it would draw from and the arithmetic it would do are both beside the
        // point, and asking them first would be working out a figure in order to throw it away.
        //
        // Asked of Deposits rather than judged here, and asked rather than attempted. Deposits is
        // the module that says no to money and owns the sentence; this run is one transaction over
        // every standing rule in the application, and a refusal thrown across it would take every
        // other customer's transfers down with it. That is the same bargain the shortfall below is
        // struck on, for the same reason.
        Optional<String> nowhereToPayItIn =
                deposits.whatStopsMoneyBeingPaidInto(rule.getSavingsAccountId());
        if (nowhereToPayItIn.isPresent()) {
            // The sentence Deposits wrote, logged rather than summarised, so that a rule refused on
            // a night nobody was watching and a customer refused at the keyboard leave the same
            // words behind. The occurrence is settled, not left due: the account cannot reopen, and
            // a day left due would be retried every night from here on.
            log.warn("saving rule occurrence could not be honoured ruleId={} name={} dueOn={} "
                            + "savingsAccountId={} reason={}",
                    rule.getId(), rule.getName(), dueOn, rule.getSavingsAccountId(),
                    nowhereToPayItIn.get());
            return Optional.of(settled(rule, RuleOccurrence.intoAnAccountThatIsClosed(
                    rule.getId(), dueOn, now)).getAmount());
        }
        Optional<BigDecimal> held = accounts.balanceOfCurrentAccount(rule.getCurrentAccountId());
        if (held.isEmpty()) {
            log.warn("saving rule occurrence could not be settled ruleId={} name={} dueOn={} "
                            + "fromCurrentAccountId={} reason=the current account this rule draws "
                            + "from is not there, so there is no balance to judge it against",
                    rule.getId(), rule.getName(), dueOn, rule.getCurrentAccountId());
            return Optional.empty();
        }
        BigDecimal balance = held.get();
        BigDecimal wouldMove = WhatARuleWouldMove.outOfABalanceOf(
                rule.getHowMuchMoves(), rule.getAmount(), rule.getFloor(), balance);
        // The figures the amount was derived from, said out loud because they are what a reviewer
        // redoes the arithmetic from: a sweep that moved 1 680.00 did so because the account held
        // 2 480.00 and the floor was 800.00, and this line is where that subtraction is shown.
        log.debug("what a saving rule would move ruleId={} name={} dueOn={} howMuchMoves={} "
                        + "amount={} floor={} balance={} wouldMove={}",
                rule.getId(), rule.getName(), dueOn, rule.getHowMuchMoves(),
                asMoneyOrNothing(rule.getAmount()), asMoneyOrNothing(rule.getFloor()),
                AmountOfMoney.asMoney(balance), AmountOfMoney.asMoney(wouldMove));

        if (wouldMove.signum() <= 0) {
            return Optional.of(
                    settled(rule, RuleOccurrence.nothingToMove(rule.getId(), dueOn, now)).getAmount());
        }
        if (balance.compareTo(wouldMove) < 0) {
            // What the account would have needed on top of what it held, worked out here because
            // here is the only place both figures exist: the rule's amount is an instruction its
            // holder may change tomorrow, and a balance is never written down anywhere at all.
            BigDecimal shortfall = AmountOfMoney.quotedToTheCent(wouldMove.subtract(balance));
            // The refusal, with the three figures it was decided on and the figure it produced. It
            // moves no money, so this WARN is the only trace of it anywhere outside the record.
            log.warn("saving rule occurrence could not be honoured ruleId={} name={} dueOn={} "
                            + "fromCurrentAccountId={} asked={} balance={} shortfall={} reason=a "
                            + "fixed amount moves all of itself or none of it, and there was not "
                            + "enough to move",
                    rule.getId(), rule.getName(), dueOn, rule.getCurrentAccountId(),
                    AmountOfMoney.asMoney(wouldMove), AmountOfMoney.asMoney(balance),
                    AmountOfMoney.asMoney(shortfall));
            return Optional.of(settled(rule, RuleOccurrence.couldNotBeHonoured(
                    rule.getId(), dueOn, now, shortfall)).getAmount());
        }
        // An ordinary deposit, through the one door every other deposit in this application goes
        // through. It earns points at the streak rate, it counts toward the week, and Deposits never
        // learns that a rule rather than a customer asked for it.
        RecordedDeposit deposit = deposits.deposit(
                rule.getSavingsAccountId(), rule.getCurrentAccountId(), wouldMove);
        RuleOccurrence written = settled(rule, RuleOccurrence.moved(
                rule.getId(), dueOn, now, wouldMove, deposit.id()));
        // And then, and only then, the split: the money is in the savings account before any of it
        // is spoken for, which is what makes the split unable to refuse the transfer.
        spreadAcrossTheGoalsOf(rule, written, wouldMove);
        return Optional.of(written.getAmount());
    }

    /**
     * Offers what a rule just deposited to the goals in its split, in the customer's order, and
     * writes down where every cent of it went.
     *
     * <p><strong>It never refuses and it never throws.</strong> By the time it runs the money is
     * already in the savings account: refusing here would mean rolling back a deposit because a goal
     * had been finished or given up on since the customer wrote the split, and an exception crossing
     * this transaction would take the whole night down with it — every other customer's transfers
     * included. So each share is <em>offered</em> rather than allocated, and what a goal will not
     * take spills to the next goal in the split. What no goal in the split will have is simply left
     * unallocated, which is money in the customer's account either way.
     *
     * <p><strong>Three things stop a goal taking its share</strong>, and they are one thing here: a
     * goal that has been abandoned since is not in the account's live goals at all, a goal that has
     * reached its target still needs nothing, and a goal whose share is more than it still needs
     * takes the part it needs. All three are settled by offering the smaller of what is owed and
     * what the goal still needs, so none of them is a special case and none of them reaches
     * {@code GoalsService} as a refusal.
     *
     * <p>The offer is also capped by what no goal has yet claimed, which is the account's own
     * invariant rather than this rule's. It can only bind if something else has spoken for the money
     * in between; it is asked for anyway, because the alternative is a {@code GoalRefused} thrown
     * inside a night that has already moved somebody else's money.
     *
     * <p><strong>Asking first is the whole of the defence, and catching afterwards is not a second
     * one.</strong> Goals refuses by throwing, this run is one transaction, and an exception crossing
     * that boundary marks the transaction rollback-only — so a refusal caught here and shrugged off
     * would still take the night down at commit. The three caps above are therefore what makes this
     * never refuse, and the {@code catch} below exists only to name the rule, the goal and the reason
     * in a WARN on the way past. It is the same argument {@code fire} makes about reading the balance
     * rather than letting the deposit refuse.
     *
     * <p>Each move goes through {@code GoalsService.moveMoney}, exactly as a customer allocating by
     * hand would, out of what no goal has claimed and into the goal. Nothing about allocation is
     * decided here.
     */
    private void spreadAcrossTheGoalsOf(SavingRule rule, RuleOccurrence occurrence,
                                        BigDecimal deposited) {
        List<RuleSplit> split = splits.findBySavingRuleIdOrderBySpotAsc(rule.getId());
        List<WhatAGoalGot> placed = new ArrayList<>();
        BigDecimal placedAltogether = BigDecimal.ZERO;

        if (!split.isEmpty()) {
            // The balance as it now stands, with the deposit in it, because that is the figure the
            // account's own invariant is judged against. Goals reads no other module and is handed
            // one, the same way the web layer hands it one.
            BigDecimal balance = deposits.moneyBalanceOf(rule.getSavingsAccountId());
            AllocationsOnAnAccount asItStands = goals.allocationsOn(rule.getSavingsAccountId(), balance);
            Map<Long, BigDecimal> stillNeededByGoal = new HashMap<>();
            for (RecordedGoal goal : asItStands.goals()) {
                stillNeededByGoal.put(goal.id(), goal.stillNeeded());
            }
            BigDecimal unclaimed = asItStands.unallocated();
            List<BigDecimal> shares = HowAnAmountIsSplit.ofAmountBy(deposited,
                    split.stream().map(RuleSplit::getShare).toList());
            log.debug("a saving rule spreads what it moved ruleId={} occurrenceId={} deposited={} "
                            + "balance={} unallocated={} shares={} cents={}",
                    rule.getId(), occurrence.getId(), AmountOfMoney.asMoney(deposited),
                    AmountOfMoney.asMoney(balance), AmountOfMoney.asMoney(unclaimed),
                    split.stream().map(RuleSplit::getShare).toList(), shares);

            // What the goal before this one would not take, carried forward. This is the spill, and
            // it is why the offer is the share plus whatever is riding on it rather than the share.
            BigDecimal carried = BigDecimal.ZERO;
            for (int i = 0; i < split.size(); i++) {
                long goalId = split.get(i).getGoalId();
                BigDecimal offered = shares.get(i).add(carried);
                BigDecimal stillNeeded = stillNeededByGoal.get(goalId);
                BigDecimal itWillTake = stillNeeded == null
                        ? BigDecimal.ZERO
                        : offered.min(stillNeeded).max(BigDecimal.ZERO).min(unclaimed.max(BigDecimal.ZERO));
                if (itWillTake.signum() > 0) {
                    try {
                        goals.moveMoney(rule.getSavingsAccountId(), null, goalId, itWillTake, balance);
                    } catch (GoalRefused refused) {
                        // Unreachable by construction, and said out loud rather than swallowed. The
                        // three refusals this could be — the goal is closed, the goal needs less
                        // than this, the account has less spare than this — are all excluded by the
                        // offer above, so a line here means one of those guards has stopped being
                        // true and is a defect worth finding by name.
                        //
                        // It is rethrown, and that is not a choice: Goals refuses by throwing, this
                        // run is one transaction, and an exception crossing that boundary marks the
                        // transaction rollback-only — so a refusal caught and shrugged off here
                        // would still take the whole night down at commit, with nothing in the log
                        // to say why. This turns that into a WARN that names the rule, the goal and
                        // the reason. It is the same argument {@code fire} makes about the balance:
                        // the answer is to ask first, not to catch afterwards.
                        log.warn("a saving rule's share was refused by its goal ruleId={} "
                                        + "occurrenceId={} goalId={} offered={} stillNeeded={} "
                                        + "unallocated={} reason={}",
                                rule.getId(), occurrence.getId(), goalId,
                                AmountOfMoney.asMoney(itWillTake), asMoneyOrNothing(stillNeeded),
                                AmountOfMoney.asMoney(unclaimed), refused.getMessage());
                        throw refused;
                    }
                    placed.add(new WhatAGoalGot(goalId, AmountOfMoney.quotedToTheCent(itWillTake)));
                    placedAltogether = placedAltogether.add(itWillTake);
                    unclaimed = unclaimed.subtract(itWillTake);
                    whereItWent.save(new OccurrenceAllocation(occurrence.getId(), goalId,
                            AmountOfMoney.quotedToTheCent(itWillTake), split.get(i).getSpot()));
                }
                carried = offered.subtract(itWillTake);
                if (carried.signum() > 0) {
                    // Every cent a goal would not take, with the reason it would not, because this
                    // is the one thing about a split that a customer cannot work out from the
                    // percentages: what is on the rule is what was offered, not what was taken.
                    //
                    // Where it went is said too, and the two are different sentences: a share that
                    // spills is still going to a goal, and a share the last line of the split would
                    // not take is money nobody has spoken for. A log that called both of them a
                    // spill would leave a reviewer looking for a goal that does not exist.
                    boolean thereIsANextGoal = i < split.size() - 1;
                    log.warn("a saving rule's share {} ruleId={} occurrenceId={} goalId={} "
                                    + "share={}% offered={} taken={} notTaken={} reason={}",
                            thereIsANextGoal
                                    ? "spills to the next goal in the split"
                                    : "reached the end of the split and is left unallocated",
                            rule.getId(), occurrence.getId(), goalId, split.get(i).getShare(),
                            AmountOfMoney.asMoney(offered), AmountOfMoney.asMoney(itWillTake),
                            AmountOfMoney.asMoney(carried),
                            stillNeeded == null
                                    ? "that goal is no longer live on this account"
                                    : "that goal still needs only "
                                            + AmountOfMoney.asMoney(stillNeeded));
                }
            }
        }

        // One line per firing saying where the money went: what each goal received, and what no goal
        // in the split would have. The two add up to what was deposited, always, and that sum is the
        // promise this feature makes — a balance and a goals page that stop agreeing is the defect a
        // cent going missing would be. A rule with no split reads as an empty list and the whole
        // amount left unallocated, which is exactly what a manual deposit does.
        BigDecimal leftUnallocated = AmountOfMoney.quotedToTheCent(deposited.subtract(placedAltogether));
        log.info("saving rule split placed ruleId={} name={} savingsAccountId={} occurrenceId={} "
                        + "deposited={} intoGoals={} allocated={} leftUnallocated={}",
                rule.getId(), rule.getName(), rule.getSavingsAccountId(), occurrence.getId(),
                AmountOfMoney.asMoney(deposited), inWordsWhatTheGoalsGot(placed),
                AmountOfMoney.asMoney(placedAltogether), AmountOfMoney.asMoney(leftUnallocated));
    }

    /**
     * Writes the occurrence down and says so, whatever became of it.
     *
     * <p>One INFO line per occurrence, in one shape, so that a rule's night can be grepped for
     * without knowing in advance which of the three outcomes to look for — and so that the two that
     * moved nothing are as visible in the log as the one that moved a hundred euros, which is the
     * same argument the record itself is kept under.
     *
     * @return the occurrence as it was written, which the caller needs for its identifier as well as
     *         for the run's own total
     */
    private RuleOccurrence settled(SavingRule rule, RuleOccurrence occurrence) {
        RuleOccurrence written = occurrences.save(occurrence);
        log.info("saving rule occurrence settled ruleId={} name={} savingsAccountId={} "
                        + "fromCurrentAccountId={} dueOn={} settledAt={} daysLate={} outcome={} "
                        + "amount={} shortfall={} occurrenceId={} depositId={}",
                rule.getId(), rule.getName(), rule.getSavingsAccountId(),
                rule.getCurrentAccountId(), written.getDueOn(), written.getSettledAt(),
                howLate(written.getDueOn(), written.getSettledAt()), written.getOutcome(),
                AmountOfMoney.asMoney(written.getAmount()),
                asMoneyOrNothing(written.getShortfall()), written.getId(), written.getDepositId());
        return written;
    }

    /**
     * Whether the rule, as it would read once this change is made, is one this application could
     * fire — and the rule itself, tidied, if it is.
     *
     * <p>One function for leaving a rule standing and for changing one, because the question is the
     * same question: a rule that would be refused if it were typed from scratch should not be
     * reachable by editing one that was not. It answers with the values to write rather than merely
     * saying yes, because judging and tidying are the same pass: the figure a rule does not need is
     * dropped exactly where it was established that it does not need it.
     *
     * @param ruleId the rule being changed, or null while one is being left standing, for the log
     */
    private ARuleAsItWouldRead judged(long savingsAccountId, Long ruleId, String name,
                                      RuleTrigger trigger, DayOfWeek dayOfWeek, Integer dayOfMonth,
                                      HowMuchMoves howMuchMoves, BigDecimal amount,
                                      BigDecimal floor) {
        String itsName = name == null ? "" : name.trim();
        if (itsName.isBlank()) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "Give the rule a name, so you can tell it from the others you have standing.");
        }
        if (trigger == null) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "Say what makes this rule move money: every week, every month, or on payday.");
        }
        if (howMuchMoves == null) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "Say how much this rule moves: a fixed amount, or everything above a floor.");
        }
        return new ARuleAsItWouldRead(itsName, trigger,
                theDayOfTheWeekItMovesOn(savingsAccountId, ruleId, trigger, dayOfWeek),
                theDayOfTheMonthItMovesOn(savingsAccountId, ruleId, trigger, dayOfMonth),
                howMuchMoves, theAmountItMoves(savingsAccountId, ruleId, howMuchMoves, amount),
                theFloorItSweepsTo(savingsAccountId, ruleId, howMuchMoves, floor));
    }

    /**
     * The day of the week a weekly rule moves on, and nothing at all for the other two triggers.
     *
     * <p>Whether the characters somebody typed name a day of the week is settled before this — the
     * day arrives already read as one — so the only objection left is that a weekly rule was left
     * without one. A day of the week sent along with a monthly or a payday rule is dropped rather
     * than refused: there is nothing wrong with a form that carried a field the rule has no use for.
     */
    private DayOfWeek theDayOfTheWeekItMovesOn(long savingsAccountId, Long ruleId,
                                               RuleTrigger trigger, DayOfWeek dayOfWeek) {
        if (trigger != RuleTrigger.WEEKLY) {
            return null;
        }
        if (dayOfWeek == null) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "A weekly saving rule moves on a day of the week. Say which day, like MONDAY.");
        }
        return dayOfWeek;
    }

    /**
     * The day of the month a monthly rule moves on, and nothing at all for the other two.
     *
     * <p>A payday rule is the one worth spelling out: it stores no day, because its day is the one
     * its holder already declared as their income's, and it is read from there on every read. Asking
     * a customer to say it a second time would be asking them to keep two answers in step.
     *
     * <p>1 to 31 as the customer says it, and the 31st is kept as the 31st. Which day a short month
     * lands it on is arithmetic made when the rule fires, so that a rule set in a long month does
     * not carry a clamped day into the next short one.
     */
    private Integer theDayOfTheMonthItMovesOn(long savingsAccountId, Long ruleId, RuleTrigger trigger,
                                              Integer dayOfMonth) {
        if (trigger != RuleTrigger.MONTHLY) {
            return null;
        }
        if (dayOfMonth == null) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "A monthly saving rule moves on a day of the month. Say which day, between "
                            + EARLIEST_DAY_OF_THE_MONTH + " and " + LATEST_DAY_OF_THE_MONTH + ".");
        }
        if (dayOfMonth < EARLIEST_DAY_OF_THE_MONTH || dayOfMonth > LATEST_DAY_OF_THE_MONTH) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "A monthly saving rule moves on a day of the month between "
                            + EARLIEST_DAY_OF_THE_MONTH + " and " + LATEST_DAY_OF_THE_MONTH
                            + ", and " + dayOfMonth + " is not one. A rule set for the 31st moves "
                            + "on the last day of a shorter month.");
        }
        return dayOfMonth;
    }

    /**
     * What a fixed-amount rule moves, and nothing at all on a sweep, whose amount is not a figure
     * anybody can name in advance.
     *
     * <p>An amount of money and nothing else, and what counts as one is {@code AmountOfMoney}'s
     * answer in the same words a deposit, a withdrawal and a goal's target get. Both of its
     * objections land here: nothing or less, and a figure quoted more finely than money is.
     */
    private BigDecimal theAmountItMoves(long savingsAccountId, Long ruleId, HowMuchMoves howMuchMoves,
                                        BigDecimal amount) {
        if (howMuchMoves != HowMuchMoves.A_FIXED_AMOUNT) {
            return null;
        }
        if (amount == null) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "Say how much this rule moves, as an amount of money.");
        }
        AmountOfMoney.whyItIsNotOne(WHAT_AN_AMOUNT_IS_CALLED, amount).ifPresent(reason -> {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES, reason);
        });
        return AmountOfMoney.quotedToTheCent(amount);
    }

    /**
     * The line a sweep stops at, and nothing at all on a fixed-amount rule.
     *
     * <p><strong>A floor of nothing is legal and a floor below nothing is not.</strong> "Sweep
     * everything above zero" is a customer saying they want the account emptied into savings, which
     * is a thing somebody can mean; a negative floor is a line under a balance that cannot go there,
     * so a sweep aimed at it would move a figure nobody could explain. That is why the shared rule
     * about amounts of money is not asked outright here — it refuses zero, rightly, for a deposit.
     *
     * <p>Only that half of it is skipped, though. How finely the figure is quoted is asked of every
     * floor, zero included, through the same {@code AmountOfMoney} sentence every other figure in
     * this application gets: {@code 0.001} and {@code 0.00000} are the same shape of input, and one
     * of them being a zero is no reason for them to get two different answers.
     */
    private BigDecimal theFloorItSweepsTo(long savingsAccountId, Long ruleId,
                                          HowMuchMoves howMuchMoves, BigDecimal floor) {
        if (howMuchMoves != HowMuchMoves.EVERYTHING_ABOVE) {
            return null;
        }
        if (floor == null) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "Say the floor this rule sweeps down to, as an amount of money. A floor of 0.00 "
                            + "sweeps everything.");
        }
        if (floor.signum() < 0) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "A saving rule floor is an amount of nothing or more, and "
                            + floor.toPlainString() + " is not. A balance never goes below zero, so "
                            + "there is nothing under that line to sweep down to.");
        }
        AmountOfMoney.whyItIsNotQuotedToTheCent(floor).ifPresent(reason -> {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES, reason);
        });
        return AmountOfMoney.quotedToTheCent(floor);
    }

    /**
     * That the change asks for something. A request naming none of the six is not a rule left as it
     * was, it is a sentence with no verb in it, and answering it with the untouched rule would tell
     * whoever sent it that their edit went through.
     */
    private void refuseUnlessItAsksForSomething(long savingsAccountId, long ruleId,
                                                AChangeToARule change) {
        if (change.saysNothing()) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "Say what to change about the rule: its name, what makes it move, the day it "
                            + "moves on, how much moves, or how it is spread across your goals.");
        }
    }

    /**
     * That every field the change names is one the rule it describes actually carries.
     *
     * <p>Asked against the rule <em>as it would then read</em>, for the same reason everything else
     * here is: sending {@code EVERYTHING_ABOVE} and a floor together is a customer turning a standing
     * order into a sweep and is exactly right, while sending a floor on its own to a rule that moves
     * a fixed amount is a figure that would be dropped on the way to the database. Dropping it and
     * answering 200 would report a change that was not made; each of these four sentences says both
     * what the rule is and what to say instead.
     *
     * <p>Before {@link #judged} rather than inside it, because it is a question about the request
     * rather than about the rule: what arrived is the only place the difference between "sent and
     * unusable" and "not sent at all" still exists, and by the time the two have been merged it is
     * gone.
     */
    private void refuseWhatTheRuleWouldHaveNoUseFor(long savingsAccountId, long ruleId,
                                                    SavingRule rule, AChangeToARule change) {
        RuleTrigger trigger = change.trigger() == null ? rule.getTrigger() : change.trigger();
        HowMuchMoves howMuchMoves =
                change.howMuchMoves() == null ? rule.getHowMuchMoves() : change.howMuchMoves();
        if (change.dayOfWeek() != null && trigger != RuleTrigger.WEEKLY) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "This rule moves " + inWords(trigger) + ", so it has no day of the week to "
                            + "change. Say that it moves WEEKLY in the same breath if that is what "
                            + "you meant it to become.");
        }
        if (change.dayOfMonth() != null && trigger == RuleTrigger.ON_PAYDAY) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "This rule moves on payday, so its day is the one you declared your income "
                            + "lands on rather than one of its own. Change that declaration, or say "
                            + "that the rule moves MONTHLY in the same breath.");
        }
        if (change.dayOfMonth() != null && trigger == RuleTrigger.WEEKLY) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "This rule moves every week, so it has no day of the month to change. Say that "
                            + "it moves MONTHLY in the same breath if that is what you meant it to "
                            + "become.");
        }
        if (change.amount() != null && howMuchMoves != HowMuchMoves.A_FIXED_AMOUNT) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "This rule moves everything above a floor, so it has no fixed amount to change. "
                            + "Say that it moves A_FIXED_AMOUNT in the same breath if that is what "
                            + "you meant it to become.");
        }
        if (change.floor() != null && howMuchMoves != HowMuchMoves.EVERYTHING_ABOVE) {
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "This rule moves a fixed amount, so it has no floor to change. Say that it "
                            + "moves EVERYTHING_ABOVE in the same breath if that is what you meant "
                            + "it to become.");
        }
    }

    /** How a trigger reads inside a sentence, where its written-out name would not. */
    private static String inWords(RuleTrigger trigger) {
        return switch (trigger) {
            case WEEKLY -> "every week";
            case MONTHLY -> "every month";
            case ON_PAYDAY -> "on payday";
        };
    }

    /**
     * That one customer holds the savings account the rule feeds and the current account it draws
     * from. A rule moves money between one holder's own accounts and nobody else's, which is the same
     * rule a deposit is held to and is asked of Accounts in the same way.
     *
     * <p>Whose the other account is is never named in a refusal, for the reason Deposits gives:
     * whoever asked already knew the identifier they sent, and saying who it belongs to would be
     * telling them something new about a customer who is not them.
     *
     * <p>An absent savings account is answered here as well, although the controller has already
     * vouched for it: the two reads are a moment apart, and an account that has gone in between
     * should be reported as gone rather than have a rule written against it. It is a refusal rather
     * than a kind of its own, because this module has no {@code NO_SUCH_ACCOUNT} to raise — see
     * {@link SavingRuleRefused}.
     *
     * <p>A switch <em>expression</em> answering with the objection rather than a switch statement
     * throwing from each branch, so that the compiler checks it covers every pairing. A statement
     * would let a fifth kind of pairing added to {@code AccountPairing} fall straight through this
     * method and have the rule written — which is the same argument {@code RefusalsAsHttp} makes for
     * switching over this module's own refusal kinds as an expression.
     */
    private void refuseUnlessTheHolderHoldsBothEnds(long savingsAccountId, Long currentAccountId) {
        if (currentAccountId == null) {
            throw refusing(savingsAccountId, null, AGAINST_THE_RULES,
                    "Say which of your current accounts this rule takes the money from.");
        }
        String whyNot = switch (accounts.pairingFor(savingsAccountId, currentAccountId)) {
            // The one pairing a rule can move money across, so the only one with nothing to say.
            case HELD_BY_ONE_CUSTOMER -> null;
            case NO_SUCH_SAVINGS_ACCOUNT -> AccountsService.noSuchSavingsAccount(savingsAccountId);
            case NO_SUCH_CURRENT_ACCOUNT -> AccountsService.noSuchCurrentAccount(currentAccountId);
            case HELD_BY_DIFFERENT_CUSTOMERS -> "A saving rule can only take money from a current "
                    + "account held by the same customer as the savings account it feeds.";
            // A shared pot's savings account, refused whoever is asking and whatever they are to
            // the pot. A rule is one customer's standing instruction to themselves: it counts
            // against their limit, it draws on their current account at two in the morning, and it
            // stops making sense the day they leave the pot. Which member's limit a pot's rule eats
            // and what becomes of it when they go are real questions with a spec of their own, and
            // this is the sentence that says so until somebody writes it.
            case HELD_BY_A_POT_THE_PAYER_MAY_PAY_INTO, HELD_BY_A_POT_THE_PAYER_DOES_NOT_BELONG_TO,
                 HELD_BY_A_POT_THE_PAYER_ONLY_WATCHES,
                 HELD_BY_A_POT_THAT_IS_CLOSED -> "That savings account belongs to a shared "
                    + "pot, and a saving rule cannot be left standing against one. A pot is paid "
                    + "into by hand, by the members who want to pay into it.";
        };
        if (whyNot != null) {
            throw refusing(savingsAccountId, null, AGAINST_THE_RULES, whyNot);
        }
    }

    /**
     * That the holder has not already left as many rules standing as this application will keep for
     * one customer.
     *
     * <p>Counted over every savings account the holder has, because the limit is theirs rather than
     * any one account's. Ended rules are not counted: they are not on the page and they fire
     * nothing, so they cost neither of the things the limit protects.
     *
     * <p><strong>Paused rules are counted.</strong> One is on the page and its holder means to have
     * it back, so it costs the thing the limit mainly protects; not counting them would let a
     * customer hold twenty rules by pausing ten of them, and give them an eleventh rule that the
     * eleventh resume would then have to refuse — which is a refusal arriving a week after the
     * mistake that caused it.
     */
    private void refuseUnlessThereIsRoomForAnother(long savingsAccountId) {
        List<Long> everythingTheyHold = theSavingsAccountsHeldAlongside(savingsAccountId);
        long standing = rules.countBySavingsAccountIdInAndStateIn(everythingTheyHold,
                RuleState.theOnesStillStanding());
        log.debug("saving rules standing for the holder savingsAccountId={} savingsAccounts={} "
                + "standing={} limit={}", savingsAccountId, everythingTheyHold, standing,
                HOW_MANY_RULES_ONE_CUSTOMER_MAY_LEAVE_STANDING);
        if (standing >= HOW_MANY_RULES_ONE_CUSTOMER_MAY_LEAVE_STANDING) {
            throw refusing(savingsAccountId, null, AGAINST_THE_RULES,
                    "You already have " + standing + " saving rules standing, and "
                            + HOW_MANY_RULES_ONE_CUSTOMER_MAY_LEAVE_STANDING
                            + " is the most one customer can leave standing at once. End one you no "
                            + "longer want before adding another.");
        }
    }

    /**
     * Every savings account held by whoever holds this one, for the count the limit is made of.
     *
     * <p>The account itself alone if the holder cannot be found, which can only happen if the
     * account went between the controller vouching for it and this read: the limit is then asked
     * about the one account the request named, which is the strictest honest answer available.
     */
    private List<Long> theSavingsAccountsHeldAlongside(long savingsAccountId) {
        return accounts.holderOfSavingsAccount(savingsAccountId)
                .map(AccountHolder::customerId)
                .flatMap(accounts::accountsOf)
                .map(CustomerAccounts::savingsAccounts)
                .map(held -> held.stream().map(SavingsAccount::getId).toList())
                .orElse(List.of(savingsAccountId));
    }

    private SavingRule theRuleOn(long savingsAccountId, long ruleId) {
        return rules.findByIdAndSavingsAccountId(ruleId, savingsAccountId)
                .orElseThrow(() -> refusing(savingsAccountId, ruleId, NO_SUCH_RULE,
                        "There is no saving rule " + ruleId + " on savings account "
                                + savingsAccountId + "."));
    }

    /**
     * That the rule is still an instruction rather than only a record.
     *
     * <p>Asked of "has it been ended" rather than of "is it firing", and the difference is a paused
     * rule: its holder stopped it for a while and means to have it back, so changing it, resuming it
     * and ending it are all things they are entitled to do. Only an ended rule is over, and only an
     * ended rule is refused — which is why the kind is named after exactly that.
     */
    private void refuseUnlessItHasNotBeenEnded(long savingsAccountId, SavingRule rule,
                                               String whatWasAsked) {
        if (rule.isEnded()) {
            throw refusing(savingsAccountId, rule.getId(), THE_RULE_IS_ENDED,
                    "\"" + rule.getName() + "\" was ended and cannot be " + whatWasAsked
                            + ". Leave a new rule standing if you want to save this way again.");
        }
    }

    /**
     * Every refusal says why in the log as well as to whoever asked, because only one of the two is
     * kept: the reason reaches the person at the keyboard and nowhere else. The account and the rule
     * are on the line because a reviewer tracing "it would not take my rule" needs to know which
     * account's rules were being argued about.
     */
    private SavingRuleRefused refusing(long savingsAccountId, Long ruleId, SavingRuleRefused.Kind kind,
                                       String reason) {
        log.warn("saving rule rejected savingsAccountId={} ruleId={} kind={} reason={}",
                savingsAccountId, ruleId, kind, reason);
        return new SavingRuleRefused(kind, reason);
    }

    /**
     * A list of rows, read out, with every rule's split fetched in one question rather than one per
     * rule. Ten rules are ten round trips otherwise, on a read a page makes on every visit.
     */
    private List<RecordedSavingRule> asRecorded(List<SavingRule> found) {
        List<Long> theirIds = found.stream().map(SavingRule::getId).toList();
        Map<Long, List<AShareOfWhatMoves>> splitsByRule = theSplitsOf(theirIds);
        // Every pause every rule here has ever had, in one question, and both things that need them
        // read out of the one answer: when a paused rule was paused, and which mornings a resumed
        // one is never going to make up. Two queries for one fact are two chances to disagree.
        Map<Long, List<RulePause>> pausesByRule = thePausesOf(theirIds);
        Map<Long, Instant> pausedAtByRule = whenEachPausedOneWasPaused(found, pausesByRule);
        // One balance per current account for the whole list rather than one per rule. Every rule on
        // an account usually draws from the same current account, and each of them needs that
        // balance to say what a sweep would move next.
        Map<Long, BigDecimal> balanceByCurrentAccount = new HashMap<>();
        return found.stream()
                .map(rule -> asRecorded(rule, splitsByRule.getOrDefault(rule.getId(), List.of()),
                        pausedAtByRule.get(rule.getId()),
                        pausesByRule.getOrDefault(rule.getId(), List.of()),
                        balanceByCurrentAccount))
                .toList();
    }

    /**
     * When each of the rules that is paused was paused, out of the pauses already read — a rule that
     * is not paused is not in the answer at all, whatever its record holds, which is the same
     * question {@link #theMomentItWasPausedAt} asks one rule.
     */
    private static Map<Long, Instant> whenEachPausedOneWasPaused(
            List<SavingRule> found, Map<Long, List<RulePause>> pausesByRule) {
        Map<Long, Instant> begunAtByRule = new HashMap<>();
        for (SavingRule rule : found) {
            if (!rule.isPaused()) {
                continue;
            }
            for (RulePause pause : pausesByRule.getOrDefault(rule.getId(), List.of())) {
                if (pause.isStillOn()) {
                    begunAtByRule.put(rule.getId(), pause.getBegunAt());
                }
            }
        }
        return begunAtByRule;
    }

    /**
     * The next day this rule will fire and what it will move when it does, or nothing at all when
     * there is no such day.
     *
     * <p>The first line of the same twelve-month preview {@link #whatTheRulesWillDo} draws, off the
     * same walk, so that a rule's entry in the account's list and the preview cannot disagree about
     * the day it next moves. Only the first is wanted, but the walk is the whole window, because
     * "the next one" is exactly "the first of the ones to come" — and a cheaper walk that stopped at
     * the first day would be a second answer to a question with one.
     *
     * <p><strong>What that costs, knowingly.</strong> Reading an account's rules walks twelve months
     * of calendar and asks the record which periods are already settled, once per live rule; a sweep
     * also reads a balance, and a payday rule also reads the salaries credited since its cursor.
     * An account holds at most ten rules by construction, the walk is a few hundred in-memory steps,
     * the balance is shared across the rules that draw on one account and is not read at all by a
     * rule that would not use one, and the salaries are the only honest source for the days a payday
     * rule is already owed — see {@link #theDaysAPaydayRuleHasComing}. Making it cheaper means
     * either a second, shorter calendar walk — the second answer this paragraph refuses — or storing
     * the day, which is the projection {@link WhatTheRulesWillDo} argues at length must not be
     * stored. The day a rule next fires is also the figure most likely to be wrong when it is stale,
     * because it changes every time the night runs.
     *
     * <p><strong>The next day it fires may be a day already past</strong>, and that is the true
     * answer rather than an awkward one: a rule the nightly run is late with fires next for the
     * oldest day it owes, and saying next week instead would be denying a transfer that is about to
     * happen. See {@link WhichOccurrencesAreDue#daysDueInTheWindow}.
     *
     * <p>Nothing on a paused rule, on an ended one, and on a payday rule whose holder has declared
     * no income. See {@link RecordedSavingRule} for why all four reasons are one null and the state
     * is what tells them apart.
     *
     * @param dayOfMonth the day of the month this rule moves on, worked out once by the caller
     *                   because a payday rule's day is a read of the declared income and the row
     *                   this is being read into needs the same figure
     * @param itsPauses  every pause this rule has ever had, fetched once by the caller for the same
     *                   reason: the mornings that fell inside a closed pause are inside the window
     *                   this walks and the night's own predicate is what takes them out
     */
    private AnOccurrenceToCome theNextOccurrenceOf(SavingRule rule, List<AShareOfWhatMoves> split,
                                                   Integer dayOfMonth, List<RulePause> itsPauses,
                                                   Map<Long, BigDecimal> balanceByCurrentAccount) {
        List<AnOccurrenceToCome> coming = theOccurrencesToComeOf(rule, split, dayOfMonth, itsPauses,
                clock.instant(), balanceByCurrentAccount);
        return coming.isEmpty() ? null : coming.get(0);
    }

    /**
     * Every day one rule will fall due on inside the window, oldest first, each carrying what the
     * rule would move on it.
     *
     * <p><strong>The same questions the night asks, in the same order and out of the same
     * functions.</strong> Which days the trigger falls on — the calendar for a weekly or a monthly
     * rule, {@link #theDaysAPaydayRuleHasComing} for a payday one; then which of those fell inside a
     * pause, taken out by {@link #notCoveredByAPause}; and then which of what is left falls in a
     * period this rule has already had its turn in, dropped by
     * {@link #dueInPeriodsNotAlreadySettled}. The order of the last two is the run's own and
     * matters for the run's own reason: a day nobody was ever going to fire must not reserve its
     * week or its month against a day somebody was. The rule's cursor is the bottom of the window,
     * because it is the bottom of the run's range.
     *
     * <p><strong>Which is why a day already past can be in the answer.</strong> The window is walked
     * from the cursor rather than from today, so a rule the next run still owes days for is forecast
     * from the oldest day it owes — see {@link WhichOccurrencesAreDue#daysDueInTheWindow}. On a
     * wound clock that is the ordinary case rather than the odd one, because the cron never fires
     * for the days the clock skipped, and a preview that quietly dropped those days would deny the
     * very transfers the next run is about to make.
     *
     * <p><strong>And it is why the pause list has to be here at all.</strong> While the window's
     * bottom was yesterday, no closed pause could reach it and this needed no pauses; counted from
     * the cursor it reaches straight through them, and {@link #resumeRule} deliberately does not
     * move the cursor. Nothing else catches those days — a day passed over never gets an occurrence
     * row, so for a weekly rule each pause Monday sits alone in its own unsettled week and
     * {@link #dueInPeriodsNotAlreadySettled} has nothing to find.
     *
     * <p>Every line says which of the two it is, in {@link AnOccurrenceToCome#owedRatherThanStillToCome}:
     * a page has to be able to render "these two already fell and the next run will make them"
     * without a customer having to read a log line.
     *
     * <p>A rule that is not live falls due on nothing. Nothing falls due while a rule is paused — a
     * pause is the customer's own instruction and is never made up — and an ended rule is a record
     * rather than an instruction. Both are the answer the night would give.
     *
     * <p>What it would move is worked out once for the rule rather than once per day, because it is
     * the same answer every time: a fixed amount is its figure, and a sweep is quoted off today's
     * balance and marked as the illustration it is. Twelve months of a sweep are twelve months of
     * one worked example, which is the honest thing to draw and is cheaper besides.
     *
     * <p><strong>The balance is read only by a rule that would use one.</strong> A fixed amount's
     * figure is the instruction itself and never touches a balance, and a rule with nothing coming
     * has no figure to quote at all; reading it anyway made a plain {@code GET} of the rules on an
     * account whose current account has gone WARN once per rule about a figure nobody asked for.
     *
     * @param itsPauses every pause this rule has ever had, oldest first, or an empty list
     * @param now       the moment being forecast from, off the application's clock: the window is
     *                  the horizon's twelve months drawn forward from it
     */
    private List<AnOccurrenceToCome> theOccurrencesToComeOf(SavingRule rule,
                                                            List<AShareOfWhatMoves> split,
                                                            Integer dayOfMonth,
                                                            List<RulePause> itsPauses, Instant now,
                                                            Map<Long, BigDecimal> balanceByCurrentAccount) {
        if (rule.getState() != RuleState.LIVE) {
            log.debug("a saving rule has nothing coming ruleId={} name={} state={} reason=only a "
                    + "live rule falls due; a pause is never made up and an ended rule is a record",
                    rule.getId(), rule.getName(), rule.getState());
            return List.of();
        }
        LocalDate from = TimelineHorizon.opensOn(now);
        LocalDate until = TimelineHorizon.closesOn(from);
        Instant settledThrough = rule.getSettledThrough();
        List<LocalDate> days = rule.getTrigger() == RuleTrigger.ON_PAYDAY
                ? theDaysAPaydayRuleHasComing(rule, dayOfMonth, settledThrough, now, from, until)
                : WhichOccurrencesAreDue.daysDueInTheWindow(rule.getTrigger(), rule.getDayOfWeek(),
                        dayOfMonth, from, until, settledThrough);
        List<LocalDate> outsideEveryPause = notCoveredByAPause(rule, itsPauses, days);
        List<LocalDate> stillToCome = dueInPeriodsNotAlreadySettled(rule, outsideEveryPause);
        // Read only where it is actually used: a sweep's figure comes out of it, and a fixed
        // amount's never does. Null means it was not asked for, which is what the line below says.
        BigDecimal balance = stillToCome.isEmpty()
                || rule.getHowMuchMoves() != HowMuchMoves.EVERYTHING_ABOVE
                ? null
                : theBalanceItWouldDrawFrom(rule.getCurrentAccountId(), rule.getId(),
                        balanceByCurrentAccount);
        // Marked as an illustration on a sweep, because what one moves eleven months out depends on
        // a balance nobody has yet. A fixed amount is never an illustration, whatever the window.
        WhatWouldMove wouldMove = whatItWouldMove(rule.getHowMuchMoves(), rule.getAmount(),
                rule.getFloor(), split, balance == null ? BigDecimal.ZERO : balance, true);
        // The inputs behind the forecast, said out loud the way the run says the inputs behind a
        // firing: the cursor it counted from, the day the trigger needs, the window, and the days it
        // came to at each of the three subtractions. A preview a customer says is missing a morning
        // is reconstructable from this line. owedFromBefore is the count of those days that already
        // fell: the transfers the next run owes, which are in the forecast precisely so that it and
        // the run cannot disagree.
        log.debug("what a saving rule has coming ruleId={} name={} savingsAccountId={} trigger={} "
                        + "dayOfWeek={} dayOfMonth={} settledThrough={} between={}..{} daysDue={} "
                        + "outsideEveryPause={} stillToCome={} owedFromBefore={} balance={} "
                        + "wouldMove={} anIllustration={}",
                rule.getId(), rule.getName(), rule.getSavingsAccountId(), rule.getTrigger(),
                rule.getDayOfWeek(), dayOfMonth, settledThrough, from, until, days.size(),
                outsideEveryPause.size(), stillToCome.size(), howManyFellBefore(stillToCome, from),
                asMoneyOrNothing(balance), AmountOfMoney.asMoney(wouldMove.amount()),
                wouldMove.anIllustrationRatherThanAPromise());
        return stillToCome.stream()
                .map(day -> new AnOccurrenceToCome(rule.getId(), rule.getName(), day,
                        rule.getTrigger(), rule.getHowMuchMoves(), wouldMove, day.isBefore(from)))
                .toList();
    }

    /**
     * The days a rule that fires on payday still has coming inside the window: the ones already
     * owed, out of the record of salaries actually credited, and then the ones still ahead, out of
     * the income its holder has declared.
     *
     * <p><strong>Two halves, because the question changes at today.</strong> For a day still ahead
     * there is no record and there cannot be — the salary has not landed — so the only honest source
     * is the declaration, which is the customer's own statement about when they are paid; that is
     * {@link WhichOccurrencesAreDue#theDaysASalaryIsExpectedOn} and it is a forecast and never a
     * firing. For a day already past the record exists, and it is the authority: the run reads
     * {@code accounts.paydaysCreditedBetween} and fires one occurrence per salary actually credited,
     * so a month nobody was paid in is a month the run passes over however confidently the
     * day-number says otherwise.
     *
     * <p>Deriving the arrears from the day-number instead is the defect this class's own opening
     * warns about — <em>asking the calendar a question only the record can answer</em>. A rule left
     * standing before an income was ever declared then shows a year of paydays for months nobody was
     * paid in, and says it next fires three months ago; the run then fires one. The same picture
     * arises with no late declaration at all: declare, wind three months, and read before running
     * {@code creditMonthlyIncome}.
     *
     * <p>So the arrears half is {@link #theDaysItFellDueOn} itself — the run's own method, with the
     * run's own two bounds — and the forecast half starts strictly after {@code now}, which is where
     * the record stops being able to answer. The two cannot overlap, because that boundary is the
     * one bound they share.
     *
     * <p><strong>One period still holds one transfer across the seam.</strong> A day-number moved
     * after a salary has landed this month can put an owed day and an expected day in the same
     * month; the run would fire the first and then find the month settled. So an expected day whose
     * month an owed day already claims is dropped here, out of
     * {@link WhichOccurrencesAreDue#thePeriodItMovesInOnce} — the same period
     * {@link #dueInPeriodsNotAlreadySettled} asks the record about, which cannot see a day that has
     * not been fired yet.
     */
    private List<LocalDate> theDaysAPaydayRuleHasComing(SavingRule rule, Integer dayOfMonth,
                                                        Instant settledThrough, Instant now,
                                                        LocalDate from, LocalDate until) {
        List<LocalDate> owed = theDaysItFellDueOn(rule, dayOfMonth, settledThrough, now);
        Set<LocalDate> monthsAlreadyOwed = owed.stream()
                .map(day -> WhichOccurrencesAreDue.thePeriodItMovesInOnce(RuleTrigger.ON_PAYDAY, day))
                .collect(Collectors.toSet());
        List<LocalDate> expected = WhichOccurrencesAreDue
                .theDaysASalaryIsExpectedOn(dayOfMonth, from, until, now).stream()
                .filter(day -> !monthsAlreadyOwed.contains(
                        WhichOccurrencesAreDue.thePeriodItMovesInOnce(RuleTrigger.ON_PAYDAY, day)))
                .toList();
        // The seam itself, because it is the one thing a reader of this forecast cannot work out
        // from the days alone: which of them the record answered for and which the declaration did.
        log.debug("what a payday rule has coming ruleId={} name={} fromCurrentAccountId={} "
                        + "dayOfMonth={} settledThrough={} asAt={} owedOutOfTheRecord={} "
                        + "expectedOutOfTheDeclaration={} until={}",
                rule.getId(), rule.getName(), rule.getCurrentAccountId(), dayOfMonth, settledThrough,
                now, owed, expected, until);
        return Stream.concat(owed.stream(), expected.stream()).toList();
    }

    /**
     * How many of those days fall before the window opened — the transfers the next run owes.
     *
     * <p>For the log and for nothing else. A forecast is counted from the rule's cursor, so a rule
     * the nightly run is behind on carries days that have already passed, and a reader looking at a
     * list that starts before the window it names needs to be told that on purpose rather than left
     * to wonder.
     */
    private static long howManyFellBefore(List<LocalDate> days, LocalDate from) {
        return days.stream().filter(day -> day.isBefore(from)).count();
    }

    /**
     * What a rule with these figures would move out of that balance, and where it would land.
     *
     * <p>{@link WhatARuleWouldMove} answers the figure — the one function the firing asks, so that a
     * preview and the transfer it predicts cannot disagree — and {@link HowAnAmountIsSplit} answers
     * the cents of the split, which is the other function the firing asks. Nothing about either is
     * decided here.
     *
     * <p>{@code anIllustration} is true only where the figure is a worked example rather than a
     * promise, and it only ever binds on a sweep: a fixed amount's figure is the instruction itself
     * and is as certain twelve months out as it is this minute. See {@link WhatWouldMove}.
     *
     * <p>Every line of the split is priced out, including one that comes to nothing out of a small
     * figure, because this is the instruction read back rather than a ledger of movements. What no
     * goal in the split would have is {@code leftUnallocated}, and the two add to the figure exactly
     * — which is the promise {@link HowAnAmountIsSplit} makes and this only carries.
     */
    private WhatWouldMove whatItWouldMove(HowMuchMoves howMuchMoves, BigDecimal amount,
                                          BigDecimal floor, List<AShareOfWhatMoves> split,
                                          BigDecimal balance, boolean anIllustration) {
        BigDecimal figure = WhatARuleWouldMove.outOfABalanceOf(howMuchMoves, amount, floor, balance);
        List<WhatAGoalWouldGet> intoGoals = new ArrayList<>();
        BigDecimal spokenFor = BigDecimal.ZERO;
        if (split != null && !split.isEmpty()) {
            List<BigDecimal> shares = HowAnAmountIsSplit.ofAmountBy(figure,
                    split.stream().map(AShareOfWhatMoves::share).toList());
            for (int i = 0; i < split.size(); i++) {
                BigDecimal itsShare = AmountOfMoney.quotedToTheCent(shares.get(i));
                intoGoals.add(new WhatAGoalWouldGet(split.get(i).goalId(), split.get(i).share(),
                        itsShare));
                spokenFor = spokenFor.add(itsShare);
            }
        }
        return new WhatWouldMove(figure, quotedToTheCentOrNothing(floor),
                anIllustration && howMuchMoves == HowMuchMoves.EVERYTHING_ABOVE,
                List.copyOf(intoGoals),
                AmountOfMoney.quotedToTheCent(figure.subtract(spokenFor)));
    }

    /**
     * What the current account a rule draws from holds, asked once per account however many rules
     * draw from it.
     *
     * <p>Nothing, with a WARN, when the account is not there. That is the same thing that stops an
     * occurrence being settled on the night — see {@link #fire} — and a preview is the place a
     * customer could find out about it before a transfer does; answering nothing rather than
     * throwing is what keeps one broken rule from taking the whole account's preview down with it.
     *
     * <p><strong>The balance exactly as {@link #fire} reads it, and deliberately not rounded
     * first.</strong> A preview exists to say what the firing will do, so it has to decide on the
     * number the firing decides on: the night compares the stored balance, because it has to agree
     * with {@code AccountsService.withdrawFrom}, which judges that same stored number and refuses
     * the whole night's deposit rather than one rule's if it disagrees. A dry run that quoted the
     * balance to the cent before comparing would answer {@code MOVED} on a stored
     * {@code 49.999999999999996} against a fixed 50.00 rule and then watch the night record
     * {@code NOT_ENOUGH_MONEY} — which is "a preview quotes the same amount the rule then actually
     * moves" failing, and is worse than the reading it was trying to tidy.
     *
     * <p>What a dry run <em>publishes</em> is this number quoted to the cent, because a body carries
     * money and not a float. The two can only part company over a balance that is not a whole number
     * of cents, which no amount this application writes to an account ever is; where SQLite's float
     * arithmetic has left sub-cent dust behind, the published balance is that dust rounded away and
     * the decision is still the one the night will make. A quote is a reading, and this is the
     * deciding.
     *
     * @param ruleId the rule this was asked for, or null on a rule nobody has saved, for the log
     */
    private BigDecimal theBalanceItWouldDrawFrom(long currentAccountId, Long ruleId,
                                                 Map<Long, BigDecimal> balanceByCurrentAccount) {
        BigDecimal known = balanceByCurrentAccount.get(currentAccountId);
        if (known != null) {
            return known;
        }
        BigDecimal balance = accounts.balanceOfCurrentAccount(currentAccountId).orElse(null);
        if (balance == null) {
            log.warn("a saving rule preview found no balance to quote ruleId={} "
                            + "fromCurrentAccountId={} reason=the current account this rule draws "
                            + "from is not there, so nothing can be said about what a sweep would "
                            + "move; it is quoted as nothing",
                    ruleId, currentAccountId);
            balance = BigDecimal.ZERO;
        }
        balanceByCurrentAccount.put(currentAccountId, balance);
        return balance;
    }

    /**
     * The row, read out. Both figures are quoted to the cent on the way, because they have been
     * through SQLite: that dialect has no decimal type and holds an amount as a float, so 50.00 comes
     * back as 50.0 and would reach a page as a number rather than as money.
     *
     * <p>A payday rule's day is read here rather than stored, from the income declared against the
     * current account it draws from — one read per rule, which is at most ten per account by
     * construction. It is null when nobody has declared an income, and that is the honest answer:
     * nothing yet says when this rule moves.
     */
    private RecordedSavingRule asRecorded(SavingRule rule) {
        return asRecorded(rule, theSplitOf(rule.getId()), theMomentItWasPausedAt(rule),
                pauses.findBySavingRuleIdOrderByIdAsc(rule.getId()), new HashMap<>());
    }

    /**
     * The same, for a caller that has already fetched the split, the pauses and the moment the rule
     * was paused at — see {@link #asRecorded(List)}.
     */
    private RecordedSavingRule asRecorded(SavingRule rule, List<AShareOfWhatMoves> split,
                                          Instant pausedAt, List<RulePause> itsPauses,
                                          Map<Long, BigDecimal> balanceByCurrentAccount) {
        // Once per rule, and handed to the forecast rather than asked again: on a payday rule this
        // is a read of the declared income, and the row and the day it next fires have to be
        // answered off one read or they could answer off two different declarations.
        Integer dayOfMonth = theDayItMovesOn(rule);
        AnOccurrenceToCome next = theNextOccurrenceOf(rule, split, dayOfMonth, itsPauses,
                balanceByCurrentAccount);
        return new RecordedSavingRule(
                rule.getId(),
                rule.getSavingsAccountId(),
                rule.getCurrentAccountId(),
                rule.getName(),
                rule.getTrigger(),
                rule.getDayOfWeek(),
                dayOfMonth,
                rule.getHowMuchMoves(),
                quotedToTheCentOrNothing(rule.getAmount()),
                quotedToTheCentOrNothing(rule.getFloor()),
                split,
                rule.getState(),
                rule.getCreatedAt(),
                pausedAt,
                rule.getEndedAt(),
                next == null ? null : next.dueOn(),
                next == null ? null : next.wouldMove());
    }

    /**
     * The day of the month this rule moves on: its own for a monthly rule, and its holder's declared
     * payday for a rule that is still standing and fires on payday.
     *
     * <p>A standing payday rule's day is read on every read rather than copied onto the rule when it
     * was written, so that a customer who moves payday from the 25th to the 28th moves it once and
     * every rule waiting for it follows. A copy taken while the rule can still fire would be a second
     * answer to a question that has one, and the two would eventually disagree.
     *
     * <p>A <em>paused</em> rule follows the declaration exactly as a firing one does, and that is
     * why the question asked here is whether it was ended rather than whether it is firing: a rule
     * its holder has stopped for a month is still an instruction waiting to fire, and its day is
     * still the day they say they are paid.
     *
     * <p><strong>An ended rule is read from the row instead, and that is the whole point of the
     * question being asked here at all.</strong> An ended rule fires nothing ever again, so there is
     * no declaration left for it to follow — only a record of what it did, and {@link #endRule} wrote
     * the day it was moving on into the row as it closed. Go on deriving it and the record is
     * rewritten afterwards by an event that has nothing to do with it: move payday to the 5th and a
     * rule ended while payday was the 25th claims it moved on the 5th; withdraw the declaration and
     * it claims it moved on no day at all. Neither is what happened to anybody's money.
     */
    private Integer theDayItMovesOn(SavingRule rule) {
        if (rule.getTrigger() != RuleTrigger.ON_PAYDAY || rule.isEnded()) {
            return rule.getDayOfMonth();
        }
        DeclaredIncome income = accounts.monthlyIncomeOn(rule.getCurrentAccountId());
        return income.dayOfMonth();
    }

    /**
     * One occurrence, read out. The amount is quoted to the cent on the way, because it has been
     * through SQLite: that dialect has no decimal type and holds an amount as a float, so 50.00 comes
     * back as 50.0 and would reach a page as a number rather than as money.
     */
    private static RecordedOccurrence asRecorded(RuleOccurrence occurrence,
                                                 List<WhatAGoalGot> intoGoals) {
        BigDecimal amount = quotedToTheCentOrNothing(occurrence.getAmount());
        // What no goal in the split would have, worked out here rather than stored beside what the
        // goals took, for the reason this application derives what no goal has claimed as
        // balance - allocated: two stored figures that have to agree eventually stop agreeing. It is
        // also what makes an occurrence written before splits existed read correctly — no goal took
        // anything from it, so all of what it moved was left unallocated, which is what happened.
        BigDecimal placed = intoGoals.stream()
                .map(WhatAGoalGot::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RecordedOccurrence(
                occurrence.getId(),
                occurrence.getSavingRuleId(),
                occurrence.getDueOn(),
                occurrence.getSettledAt(),
                howLate(occurrence.getDueOn(), occurrence.getSettledAt()),
                occurrence.getOutcome(),
                amount,
                quotedToTheCentOrNothing(occurrence.getShortfall()),
                occurrence.getDepositId(),
                intoGoals,
                AmountOfMoney.quotedToTheCent(amount.subtract(placed)));
    }

    /**
     * How late an occurrence was, in whole days: the gap between the day it was due and the day it
     * was actually settled on.
     *
     * <p><strong>Derived rather than stored</strong>, because it is the subtraction of two facts
     * that are already written down and a stored third copy could disagree with them. Both moments
     * are read in the one calendar this application counts days in ({@code SavingsWeek}'s), so a
     * transfer due on the 10th and made just after midnight on the 11th is one day late rather than
     * a few minutes late — days are the unit an occurrence is due in, and they have to be the unit
     * the lateness is quoted in.
     *
     * <p>Never negative. An occurrence settled on the day it fell due is nought days late, which is
     * every occurrence on a night the application was up, and nothing in this feature can settle one
     * before its day begins: the day is only due once it has begun.
     */
    private static long howLate(LocalDate dueOn, Instant settledAt) {
        if (dueOn == null || settledAt == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(dueOn, WhichOccurrencesAreDue.theDayItFallsOn(settledAt));
        return Math.max(days, 0);
    }

    /**
     * Whether the split, as the rule would then carry it, is one this application will spread money
     * by — and the split itself if it is.
     *
     * <p>One method for leaving a rule standing and for changing one, for the reason
     * {@link #judged} is one method for both: a split that would be refused if it were typed from
     * scratch should not be reachable by editing a rule that has one.
     *
     * <p><strong>What is asked, in the order a person would want to hear it.</strong> First that
     * each line is a line — a goal and a whole percentage of more than nothing — then that no goal
     * is named twice, then that the shares add to a hundred, and last that the goals named are
     * actually on this account. The arithmetic comes before the lookup because it is about what the
     * customer typed and can be answered without leaving this module; the lookup is the one question
     * that needs another module, and it is the last thing left to doubt.
     *
     * <p><strong>The goals are read live, and that is the only moment this is asked.</strong> A
     * split naming a goal that is not live on the account is refused here, and never when the rule
     * fires: by then the money has landed, and refusing would mean rolling back a deposit because a
     * goal had been finished since. A goal abandoned after this point keeps its line and its share
     * spills — see {@link #spreadAcrossTheGoalsOf}.
     *
     * @param ruleId the rule being changed, or null while one is being left standing, for the log
     * @param asked  the split as it arrived: null for "there is no split here to talk about", which
     *               on a change means leave it alone and on a new rule means a rule that deposits
     *               unallocated, and empty for a customer taking a split away
     * @return the split to write, tidied, or null when the caller was not talking about one
     */
    private List<AShareOfWhatMoves> theSplitAsItWouldRead(long savingsAccountId, Long ruleId,
                                                          List<AShareOfWhatMoves> asked) {
        if (asked == null) {
            return null;
        }
        if (asked.isEmpty()) {
            return List.of();
        }
        log.debug("saving rule split asked for savingsAccountId={} ruleId={} split={}",
                savingsAccountId, ruleId, inWords(asked));

        Set<Long> named = new LinkedHashSet<>();
        int addUpTo = 0;
        for (AShareOfWhatMoves share : asked) {
            if (share == null || share.goalId() == null) {
                throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                        "Say which goal each share of this rule goes to.");
            }
            if (share.share() == null) {
                throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                        "Say what share of this rule goal " + share.goalId() + " gets, as a whole "
                                + "percentage between " + THE_SMALLEST_SHARE + " and "
                                + WHAT_THE_SHARES_ADD_UP_TO + ".");
            }
            if (share.share() < THE_SMALLEST_SHARE || share.share() > WHAT_THE_SHARES_ADD_UP_TO) {
                throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                        "A share of what a rule moves is a whole percentage between "
                                + THE_SMALLEST_SHARE + " and " + WHAT_THE_SHARES_ADD_UP_TO + ", and "
                                + share.share() + " is not. Leave a goal out of the split "
                                + "altogether to give it nothing.");
            }
            if (!named.add(share.goalId())) {
                throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                        "Goal " + share.goalId() + " is in this split twice. Say what it gets once, "
                                + "as one share.");
            }
            addUpTo += share.share();
        }
        if (addUpTo != WHAT_THE_SHARES_ADD_UP_TO) {
            // The figure they add to is in the sentence, because a customer looking at three boxes
            // reading 60, 30 and 5 cannot see the 95 — that is the whole of what they have to be
            // told, and a refusal that only said "they must add to a hundred" would send them back
            // to add the boxes up themselves.
            throw refusing(savingsAccountId, ruleId, AGAINST_THE_RULES,
                    "The shares in a split add up to " + WHAT_THE_SHARES_ADD_UP_TO + ", and these "
                            + "add up to " + addUpTo + ".");
        }

        Set<Long> live = goals.goalsOn(savingsAccountId).stream()
                .map(RecordedGoal::id)
                .collect(Collectors.toSet());
        for (AShareOfWhatMoves share : asked) {
            if (!live.contains(share.goalId())) {
                throw refusing(savingsAccountId, ruleId, NO_SUCH_GOAL_IN_THE_SPLIT,
                        "There is no goal " + share.goalId() + " being saved towards on this "
                                + "savings account, so this rule cannot put anything into it.");
            }
        }
        return List.copyOf(asked);
    }

    /**
     * Writes a rule's split down, in the order the customer wrote it.
     *
     * <p>The place in the order is stored rather than left to the row identifiers, for the reason
     * {@link RuleSplit} gives: it settles which goal a leftover cent goes to and which goal a share
     * spills to, and neither of those may depend on what the database happens to hand back.
     */
    private void writeTheSplitOf(SavingRule rule, List<AShareOfWhatMoves> split) {
        if (split == null || split.isEmpty()) {
            return;
        }
        for (int spot = 0; spot < split.size(); spot++) {
            AShareOfWhatMoves share = split.get(spot);
            splits.save(new RuleSplit(rule.getId(), share.goalId(), share.share(), spot));
        }
    }

    /** One rule's split, in the customer's order, and empty on a rule that has none. */
    private List<AShareOfWhatMoves> theSplitOf(long ruleId) {
        return splits.findBySavingRuleIdOrderBySpotAsc(ruleId).stream()
                .map(share -> new AShareOfWhatMoves(share.getGoalId(), share.getShare()))
                .toList();
    }

    /**
     * Every pause on each of these rules, oldest first within each rule, in one question.
     *
     * <p>One query for a whole night rather than one per rule, for the reason the splits are read
     * the same way: a night walks every standing rule in the application, and a question each would
     * be a round trip per rule on every run for an answer that is usually empty.
     */
    private Map<Long, List<RulePause>> thePausesOf(List<Long> ruleIds) {
        if (ruleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<RulePause>> byRule = new HashMap<>();
        for (RulePause pause : pauses.findBySavingRuleIdInOrderBySavingRuleIdAscIdAsc(ruleIds)) {
            byRule.computeIfAbsent(pause.getSavingRuleId(), rule -> new ArrayList<>()).add(pause);
        }
        return byRule;
    }

    /**
     * When each of a rule's pauses began and ended, for a log line: the one still on reads as having
     * no end, which is what it has.
     */
    private static String inWordsThePauses(List<RulePause> itsPauses) {
        return itsPauses.stream()
                .map(pause -> pause.getBegunAt() + ".."
                        + (pause.isStillOn() ? "still on" : pause.getEndedAt()))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /** Several rules' splits in one question, each in the customer's order. */
    private Map<Long, List<AShareOfWhatMoves>> theSplitsOf(List<Long> ruleIds) {
        if (ruleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<AShareOfWhatMoves>> byRule = new HashMap<>();
        for (RuleSplit share : splits.findBySavingRuleIdInOrderBySavingRuleIdAscSpotAsc(ruleIds)) {
            byRule.computeIfAbsent(share.getSavingRuleId(), rule -> new ArrayList<>())
                    .add(new AShareOfWhatMoves(share.getGoalId(), share.getShare()));
        }
        return byRule;
    }

    /**
     * Where the money went on each of these occurrences, in split order within each — and nothing at
     * all for an occurrence that placed none of it, which is every occurrence of a rule with no
     * split and every occurrence that moved no money.
     */
    private Map<Long, List<WhatAGoalGot>> whereTheMoneyWentOn(List<RuleOccurrence> written) {
        if (written.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<WhatAGoalGot>> byOccurrence = new HashMap<>();
        List<Long> occurrenceIds = written.stream().map(RuleOccurrence::getId).toList();
        for (OccurrenceAllocation allocation
                : whereItWent.findByRuleOccurrenceIdInOrderByRuleOccurrenceIdAscSpotAsc(occurrenceIds)) {
            byOccurrence
                    .computeIfAbsent(allocation.getRuleOccurrenceId(), occurrence -> new ArrayList<>())
                    .add(new WhatAGoalGot(allocation.getGoalId(),
                            AmountOfMoney.quotedToTheCent(allocation.getAmount())));
        }
        return byOccurrence;
    }

    /**
     * How a split reads in a log line: the goals and their shares in the customer's order, or the
     * word for a rule that has none, where an empty pair of brackets would read as a fault.
     */
    private static String inWords(List<AShareOfWhatMoves> split) {
        if (split == null || split.isEmpty()) {
            return "none";
        }
        return split.stream()
                .map(share -> share.goalId() + ":" + share.share() + "%")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /** How what the goals actually took reads in a log line, in the order the split offered it. */
    private static String inWordsWhatTheGoalsGot(List<WhatAGoalGot> placed) {
        if (placed.isEmpty()) {
            return "none";
        }
        return placed.stream()
                .map(got -> got.goalId() + ":" + AmountOfMoney.asMoney(got.amount()))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /** How what the goals would get reads in a log line, in the order the customer wrote the split. */
    private static String inWordsWhatTheGoalsWouldGet(List<WhatAGoalWouldGet> shares) {
        if (shares.isEmpty()) {
            return "none";
        }
        return shares.stream()
                .map(share -> share.goalId() + ":" + share.share() + "%="
                        + AmountOfMoney.asMoney(share.amount()))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static BigDecimal quotedToTheCentOrNothing(BigDecimal figure) {
        return figure == null ? null : AmountOfMoney.quotedToTheCent(figure);
    }

    /** How a figure a rule does not carry reads in a log line, where a bare null would say less. */
    private static String asMoneyOrNothing(BigDecimal figure) {
        return figure == null ? "none" : AmountOfMoney.asMoney(figure);
    }

    /**
     * A rule as it would read once what was asked for has been applied: judged, and tidied so that
     * the day and the figure its kind has no use for are empty rather than stale.
     *
     * <p>Private, and it never leaves this class. It is the shape the two writing methods agree on
     * so that neither of them can write a rule the other would have refused.
     */
    private record ARuleAsItWouldRead(String name, RuleTrigger trigger, DayOfWeek dayOfWeek,
                                      Integer dayOfMonth, HowMuchMoves howMuchMoves,
                                      BigDecimal amount, BigDecimal floor) {
    }

    /**
     * One rule and one day it fell due, before anything has been done about it.
     *
     * <p>It exists so that a night can be gathered in full and then sorted: the firing order is by
     * day and then by the order the rules were written, and neither can be honoured by a loop that
     * fires each rule's days as it finds them.
     */
    private record AnOccurrenceToFire(SavingRule rule, LocalDate dueOn) {
    }
}
