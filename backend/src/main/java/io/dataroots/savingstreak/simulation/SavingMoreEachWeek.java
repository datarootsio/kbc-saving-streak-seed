package io.dataroots.savingstreak.simulation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import io.dataroots.savingstreak.deposits.AmountOfMoney;

/**
 * Another amount put away every week, from the day the customer would start, for the rest of the
 * year the simulation is drawn over.
 *
 * <p><strong>It adds to what the account is already doing rather than replacing it</strong>, which
 * is what the word <em>more</em> means and is the difference between this and an edit to a saving
 * rule. Every rule standing on the account goes on firing exactly as it does in the branch where
 * nothing changes, and this lands beside them. A customer who wants to move a rule's figure is
 * editing a rule, and the automation screen previews that already.
 *
 * <p><strong>It is not judged against the everyday account.</strong> A saving rule moves money out
 * of a current account and moves nothing when the money is not there; this is the customer saying
 * what they could put away, which is the same sentence a weekly saving capacity is and is the reason
 * adopting this change adds to that capacity rather than standing a rule up. A branch that refused to
 * find the extra out of a modelled current-account balance would be answering a question about a
 * salary, and the customer asked a question about a sacrifice. It is also why the spec's table of
 * refusals has nothing about affordability in it.
 *
 * <p><strong>It changes the goals as well as the money, and the two are separate answers.</strong>
 * The euros land in the walk, through the same restated deposit rule a rule's transfer goes through,
 * so they move the balance, earn at the run the branch has climbed to, and secure weeks that were
 * not going to be secured. The weekly figure goes to the plan the goals are funded out of, because a
 * branch's goals are projected from a capacity rather than allocated out of its deposits — and the
 * day a goal is reached is the thing the customer is actually asking about. A version that paid the
 * money in and said nothing to the plan would move every figure on the screen except that one.
 *
 * <p><strong>Where this is knowingly approximate.</strong> The day the goals are projected from is
 * the day the window opens, whatever day the extra starts on: the projection is a rate over a year
 * and {@code WhenAGoalWillBeReached} counts its weeks from the Monday of the day it is handed. So an
 * extra a customer would start in three weeks' time reaches the goals three weeks early, while the
 * euros the walk pays in start on exactly the day they said. The alternative — projecting the goals
 * from the extra's own day — would move every other goal's arithmetic to a day the customer did not
 * ask about, which is a larger wrong for the sake of a smaller one, and every figure on this answer
 * already says it is an illustration rather than a promise.
 *
 * <p>Both fields may be missing or nonsense on the way in, deliberately: a change is built out of
 * whatever was typed and refused by {@link #whyItCannotBeAsked} in one sentence before a single day
 * is folded, so nothing below is ever asked of one that has not been found askable.
 */
public record SavingMoreEachWeek(BigDecimal amount, LocalDate startsOn) implements AnAdjustment {

    /** A week is seven days here as everywhere else, counted from the day the extra starts. */
    private static final long DAYS_IN_A_WEEK = 7;

    @Override
    public AKindOfAdjustment kind() {
        return AKindOfAdjustment.SAVE_MORE_EACH_WEEK;
    }

    @Override
    public String asAsked() {
        return "SAVE_MORE_EACH_WEEK amount=" + (amount == null ? "not said" : amount.toPlainString())
                + " from=" + (startsOn == null ? "not said" : startsOn);
    }

    /**
     * Why another amount every week cannot be asked about: the amount is not an amount of money, or
     * the day it would start on is not a day inside the year this simulation is drawn over.
     *
     * <p>The first sentence is {@code AmountOfMoney}'s own, word for word, because a customer who
     * typed "25,00" into this box deserves the answer they would have got had they typed it into a
     * deposit. The second names the window, because a customer told their day will not do has to be
     * told which days would.
     */
    @Override
    public Optional<String> whyItCannotBeAsked(TheStartingPoint standing) {
        if (amount == null) {
            return Optional.of("Say how much more you would put away each week.");
        }
        Optional<String> notAnAmount = AmountOfMoney.whyItIsNotOne("deposit", amount);
        if (notAnAmount.isPresent()) {
            return notAnAmount;
        }
        return AnAdjustment.whyThatDayIsOutsideTheWindow(
                "The day another amount each week starts", startsOn, standing);
    }

    /**
     * The amount, on the day it starts and on every seventh day after it, and nothing on any other
     * day.
     *
     * <p>Counted in days from the day the customer named rather than on a day of the week, because
     * the customer said "from the twenty-first", not "on Mondays" — and a fold that rounded their
     * day to the nearest Monday would pay the first one on a day they did not choose. The weeks a
     * streak is counted in are still {@code SavingsWeek}'s Mondays; which of them this lands in is
     * the walk's arithmetic, not this method's.
     */
    @Override
    public BigDecimal whatItAlsoPaysInOn(LocalDate day) {
        if (day.isBefore(startsOn)) {
            return BigDecimal.ZERO;
        }
        return ChronoUnit.DAYS.between(startsOn, day) % DAYS_IN_A_WEEK == 0
                ? amount
                : BigDecimal.ZERO;
    }

    /** All of it: a week's extra is a week's extra, and the plan is a weekly figure. */
    @Override
    public BigDecimal whatItAddsToTheWeeklyPlan() {
        return amount;
    }

    /**
     * Adopting another amount each week raises the declared weekly saving capacity by exactly that
     * amount, through the module that owns the figure.
     *
     * <p><strong>The capacity and not a saving rule</strong>, although both are durable and both
     * would move money. A capacity is what the customer says they can put away in a week; it is what
     * the goals are funded out of, what every projection on the goals screen is worked from, and it
     * is a sentence the customer can revise whenever they like. A rule is an instruction to move
     * money out of a named everyday account on a named day, and adopting a branch into one would
     * mean this module choosing the account, the day and the kind of rule on the customer's behalf
     * from a branch that said nothing about any of them. The branch said "another twenty-five a
     * week"; the capacity is the one thing in this application that means that and nothing more.
     *
     * <p>The day the extra was to start on is not carried over, and that is the honest reading
     * rather than a loss. A capacity has no start day — it is what the customer can do from now on —
     * and a plan that quietly diaried itself for March would be a thing the application invented.
     * The branch was a question about a rate, and the rate is what is adopted.
     *
     * <p>The figure goes over untouched: whether it is an amount of money is Goals' ruling, in the
     * words a capacity is already refused in.
     */
    @Override
    public AChangeThePlanNowCarries adoptedThrough(ThePressesACustomerWouldHaveMade presses) {
        return presses.raiseTheWeeklySavingCapacityBy(amount);
    }
}
