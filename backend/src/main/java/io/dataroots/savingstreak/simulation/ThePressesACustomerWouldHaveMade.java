package io.dataroots.savingstreak.simulation;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.goals.GoalsService;
import io.dataroots.savingstreak.goals.RecordedGoal;
import io.dataroots.savingstreak.goals.SavingCapacityOnAnAccount;

/**
 * Adopting a branch, done the only way it is allowed to be done: as the presses a customer would
 * have made themselves, made once, against the modules that own the rules being changed.
 *
 * <p><strong>This class exists so that adopting cannot invent a write path.</strong> A weekly
 * capacity is a figure Goals keeps and rules on, and a goal's deadline is Goals' too. Nothing in the
 * Simulation module knows what either of them is allowed to be, and nothing in it is going to learn:
 * every method here is a call to {@link GoalsService} with the arguments the customer's own typing
 * would have produced on the weekly plan screen and the goal screen. A simulator that wrote a
 * deadline of its own — validated by its own rule, saved by its own repository — would be a second
 * opinion about what a deadline is, and the day the two opinions differed the customer would get
 * whichever screen they happened to use.
 *
 * <p><strong>So every refusal is the owning module's own sentence, word for word.</strong> A
 * deadline in the past is refused by Goals, in the words Goals already uses, because Goals is the
 * thing being asked. Nothing here reads a day, compares it against today or forms an opinion about
 * an amount: there is no check in this class at all, which is the point of it. The one thing it does
 * guard is a box the customer left empty where the module could not be called at all without one —
 * and it says so in the change's own words rather than in a rule of its own.
 *
 * <p><strong>All of it or none of it is not this class's doing.</strong> Each press here is one
 * write and commits nothing; the transaction that rolls the lot back when a later press is refused
 * is {@link SimulationService#adopt}'s, because that is the method that knows a branch is one
 * decision. This class would be wrong to try: it is handed one change at a time and cannot see the
 * others.
 *
 * <p><strong>Not a Spring bean and not a service.</strong> It is a short-lived thing made around one
 * account for one press, so the account identifier is a field rather than an argument repeated on
 * every method — which is also what stops a change reaching a pot the customer did not adopt onto.
 *
 * <p>Nothing here logs. One line per adoption naming every change applied is the one line a reviewer
 * reads, and it is written where the whole press is known; a line per press would bury it under the
 * three or four that made it up.
 */
final class ThePressesACustomerWouldHaveMade {

    /** What a log line and a sentence call a capacity nobody has declared. */
    private static final String NOTHING_DECLARED = "nothing declared";

    private final GoalsService goals;
    private final long savingsAccountId;

    ThePressesACustomerWouldHaveMade(GoalsService goals, long savingsAccountId) {
        this.goals = goals;
        this.savingsAccountId = savingsAccountId;
    }

    /**
     * Raises the declared weekly saving capacity by this much, and says what it was and what it now
     * is.
     *
     * <p><strong>Raised by, and not set to, because the word is "more".</strong> A branch asking
     * what another twenty-five euros a week would do is a branch about twenty-five euros on top of
     * everything the account is already doing — the fold adds it to the rules that are standing
     * rather than replacing them — and adopting has to mean the same thing the projection meant or
     * the customer gets a plan that is not the one they read. It also gives the second press its
     * honest meaning: pressing adopt twice on the same branch raises the capacity twice, because the
     * second press is a second decision rather than a repeat of the first.
     *
     * <p><strong>An account whose holder has declared nothing is raised from nothing.</strong> Not
     * refused, and not left alone: somebody who has never said what they can put away in a week and
     * has just decided on twenty-five a week has said it now, and the sentence names the absence it
     * was raised from so that nobody reads it as an increase on a figure they had forgotten.
     *
     * <p>The figure the customer typed is passed on rather than examined. Whether it is an amount of
     * money at all is Goals' ruling in Goals' words — an amount that is not one never reaches the
     * addition, because a missing figure is handed straight over and a figure quoted more finely
     * than money is refused by the module that owns the rule.
     */
    AChangeThePlanNowCarries raiseTheWeeklySavingCapacityBy(BigDecimal amount) {
        SavingCapacityOnAnAccount standing = goals.savingCapacityOn(savingsAccountId);
        String was = standing.isDeclared()
                ? "EUR " + AmountOfMoney.asMoney(standing.weeklyCapacity())
                : NOTHING_DECLARED;
        // A missing amount goes over the wall untouched, so that "say the most you can put away in a
        // week" is Goals' sentence rather than a second wording of it invented here. Adding it to
        // anything first would turn a refusal a customer can act on into a mistake in a log.
        BigDecimal raisedTo = amount == null
                ? null
                : (standing.isDeclared() ? standing.weeklyCapacity() : BigDecimal.ZERO).add(amount);
        SavingCapacityOnAnAccount now = goals.declareSavingCapacity(savingsAccountId, raisedTo);
        return AChangeThePlanNowCarries.applied(AKindOfAdjustment.SAVE_MORE_EACH_WEEK,
                "The most you can put away in a week on savings account " + savingsAccountId
                        + " is now EUR " + AmountOfMoney.asMoney(now.weeklyCapacity())
                        + ", raised by EUR " + AmountOfMoney.asMoney(amount) + " from " + was + ".");
    }

    /**
     * Changes a goal's deadline to the day the branch wanted it by, and says which goal, which day,
     * and which day it was.
     *
     * <p>The goal is read first so that the sentence can call it by the customer's own name for it
     * and can say what the day was before. That read is Goals' too, and it is the read that refuses
     * a goal which is not on this account — in the words Goals already uses for one that does not
     * exist, which is deliberately the same sentence, because telling a customer a goal exists on
     * somebody else's account would be telling them about somebody else's account.
     *
     * <p>Only the deadline is changed. The name and the target are handed over as nothing at all,
     * which is how {@code changeGoal} is told to leave them alone, and the flag beside the day is
     * how it is told that the day is an instruction rather than an absence. A branch moves a
     * deadline and moves nothing else, and adopting it must not quietly retarget a goal.
     *
     * <p>Whether the day is one a goal may be wanted by — a day still to come — is not asked here and
     * could not be: that is Goals' rule, Goals reads the clock for it, and a day in the past comes
     * back as Goals' own sentence with the whole adoption rolled back behind it.
     */
    AChangeThePlanNowCarries wantThatGoalBy(long goalId, LocalDate day) {
        RecordedGoal before = goals.goalOn(savingsAccountId, goalId);
        String was = before.deadline() == null ? "no day on it" : String.valueOf(before.deadline());
        RecordedGoal after = goals.changeGoal(savingsAccountId, goalId, null, null, day, true);
        return AChangeThePlanNowCarries.applied(AKindOfAdjustment.MOVE_A_DEADLINE,
                "\"" + after.name() + "\" is now wanted by " + after.deadline() + " instead of "
                        + was + ".");
    }
}
