package io.dataroots.savingstreak.simulation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One change a customer would make to the future, as a thing the fold can ask questions of.
 *
 * <p><strong>A kind is a class here rather than a case in a switch, and that is this type's whole
 * opinion.</strong> Four kinds of change are coming — another amount every week, stopping for a
 * while, taking money out, moving a deadline — and each carries fields the other three have no use
 * for and refuses for reasons the other three never raise. Written as one record with every field on
 * it and a switch in the validator, in the fold and again in the web layer, each of those switches
 * would have to be edited by whoever adds the fifth thing this application ever learns to imagine,
 * and three of them would be edited at once by three people who cannot see each other's work. Written
 * as one small type per kind, a new kind is a new file, a filled-in constant and nothing else — and
 * the compiler, rather than a reviewer, is what notices a question that was left unanswered.
 *
 * <p><strong>The questions have do-nothing answers, so a kind only says what it changes.</strong>
 * Every question the walk asks is declared here with a default meaning "this change does not affect
 * that", so moving a deadline says nothing about money and taking money out says nothing about
 * goals. That is what makes the list of questions able to grow without every kind growing with it.
 *
 * <p><strong>How a new kind is added.</strong> In this order, and nothing outside it:
 * <ol>
 * <li><strong>A record of its own in this package</strong>, implementing this interface and carrying
 * exactly the fields that kind needs — {@link SavingMoreEachWeek} is the worked example. The fields
 * are the ones the spec's table gives it; they are nobody else's and no other kind learns about
 * them.</li>
 * <li><strong>The two sentences every kind owes.</strong> {@link #asAsked()}, which is how the
 * change reads in the one DEBUG line a reviewer checks a column of the screen against, and
 * {@link #whyItCannotBeAsked(TheStartingPoint)}, which is that kind's own validation — its own rules,
 * in its own words, asked of the snapshot so that a kind naming a goal can look for it. A day that
 * has to be inside the window is {@link #whyThatDayIsOutsideTheWindow} rather than a fourth wording
 * of one objection.</li>
 * <li><strong>Override only the questions it answers differently.</strong> If the walk does not yet
 * ask the question your kind needs answering — whether the rules are silent this morning, what is
 * taken out today, which day a goal is now wanted by — declare it here as a default method whose
 * default is that nothing happens, and ask it at the one place in {@link TheNightReplayed} that can
 * answer it. Every adjustment on the branch is asked every question, so two changes in one scenario
 * compose rather than the later one replacing the earlier.</li>
 * <li><strong>Fill in your constant's body in {@link AKindOfAdjustment}</strong>, reading the fields
 * your kind needs off {@link AnAdjustmentAsAsked} and leaving the other constants alone. All four
 * constants are already declared, so no two slices are editing the same lines.</li>
 * <li><strong>Nothing else.</strong> Not {@code SimulationService}, which validates and folds
 * whatever it is handed; not {@code SimulationController} or the request records, which read the
 * same five typed fields for every kind; not {@link HowAScenarioTurnsOut}, {@link AMonthOfTheFuture}
 * or {@link AThingThatHappens}; and not the other kinds.</li>
 * </ol>
 *
 * <p>Every one of these is a question and none of them is an instruction: an adjustment never
 * touches the branch's ledger itself. The walk asks, the walk decides, and the walk is the only
 * place the order of a night is written down — which is what keeps a change that pays money in from
 * quietly paying it in at the wrong hour of the morning.
 *
 * <p>Nothing here is validated on the way in. A kind is built out of whatever a customer typed,
 * including a figure that is not an amount and a day outside the window, because the sentence that
 * refuses it is the kind's own and is said once, by {@link #whyItCannotBeAsked(TheStartingPoint)},
 * before a single day is folded. The questions below are therefore only ever asked of an adjustment
 * that has already been found askable.
 */
public interface AnAdjustment {

    /** Which of the four changes this is, for the log line and for the kind a customer typed. */
    AKindOfAdjustment kind();

    /**
     * The change in one short phrase, for the DEBUG line named before a branch is folded.
     *
     * <p>Greppable and in the shape the rest of this application logs in — {@code key=value}, the
     * figures that decided it — because that line is what a reviewer puts beside a column of the
     * screen, and a column they cannot tell from the column next to it explains nothing.
     */
    String asAsked();

    /**
     * Why this change cannot be asked about, in words the person who typed it can act on, or nothing
     * at all if it can.
     *
     * <p>The kind's own rule, in the kind's own words, and never a shared method that has learnt
     * what every kind objects to. Asked of the whole snapshot rather than of the two days, because a
     * kind that names a goal has to be able to look for it on the account — and a kind that does not
     * simply ignores the rest.
     *
     * <p>Where an objection is genuinely one objection, the words are one wording: an amount that is
     * not an amount of money is {@code AmountOfMoney}'s sentence here exactly as it is on a deposit,
     * and a day outside the window is {@link #whyThatDayIsOutsideTheWindow}'s. A customer who has met
     * two refusals should have met one form of words twice.
     */
    Optional<String> whyItCannotBeAsked(TheStartingPoint standing);

    /**
     * What adopting this change does to the plan the customer actually has, and the line the answer
     * carries about it.
     *
     * <p><strong>Answered by every kind and defaulted by none</strong>, which is the one place in
     * this interface where that is deliberate rather than incidental. Two of the four changes are
     * not adopted at all — a withdrawal is a thing a customer does on the day, and pausing is what
     * {@code RulePause} already is — and those two are the ones a default would have swallowed. A
     * fifth kind of change arriving here has to decide, in writing, whether adopting it presses
     * something or hands the customer something to do, and the compiler is what makes it decide
     * rather than inherit.
     *
     * <p><strong>The presses are handed in rather than reached for.</strong> A kind that changes
     * something durable says so by calling {@link ThePressesACustomerWouldHaveMade}, which is the
     * only way into the modules that own the rules; a kind that changes nothing never touches it and
     * writes its own sentence. So the question "does adopting this write anything" is answered by
     * reading one method per kind, and no switch anywhere knows the answer for all four.
     *
     * <p><strong>Nothing here is asked whether it could be asked about.</strong> Adopting does not
     * put a change to {@link #whyItCannotBeAsked} first: those are the simulator's rules about what
     * it will fold — a day has to be inside the twelve months it draws — and they are not the rules
     * about what a plan may be. A deadline is ruled on by Goals, in Goals' words, and a second
     * opinion offered first would be the simulator answering a question that is not its own.
     *
     * @param presses the modules that own the rules, as the presses a customer would have made on
     *                the screens they own
     * @return the one line this change contributes to what adopting changed
     * @throws SimulationRefused if a box this change cannot be adopted without was left empty
     */
    AChangeThePlanNowCarries adoptedThrough(ThePressesACustomerWouldHaveMade presses);

    /**
     * What this change puts into savings on that day, over and above whatever the account's own
     * rules move — nothing, unless the kind says otherwise.
     *
     * <p>Asked of every adjustment on every morning of the walk, after the rules have fired and
     * before the bills are taken, and paid in through the same restated deposit rule a rule's
     * transfer is: the high-water mark first, then the run including the week it may have just
     * secured, then the double flooring. Money a customer decides to put away is not a saving rule
     * and is not judged against the everyday account's balance — that is argued where it is asked, in
     * {@link TheNightReplayed}.
     */
    default BigDecimal whatItAlsoPaysInOn(LocalDate day) {
        return BigDecimal.ZERO;
    }

    /**
     * Whether this change means no money at all goes into savings on that morning — no, unless the
     * kind says otherwise.
     *
     * <p>Asked at both of the two places money reaches a branch's savings and at neither of the
     * others: at two in the morning, where a standing rule would have fired, and beside it, where
     * whatever the customer would put away themselves lands. The rest of the night is deliberately
     * not asked — the salary still arrives, the bills are still taken, batches still reach their
     * twelve months and anniversaries still pay their tenth, because a customer who has stopped
     * saving has not stopped being paid or stopped owing rent.
     *
     * <p><strong>It is asked of every adjustment rather than of the one that raised it</strong>, so
     * a scenario carrying a stop and another amount each week is a customer who intends both: the
     * extra does not land on the mornings they said they were stopping. A branch that paid it in
     * anyway would show a stop that broke no week at all, which is the opposite of the answer that
     * customer came for.
     *
     * <p>The silence is the one a paused rule already produces: the occurrence is passed over
     * outright and the rule's cursor moves over it, so nothing is made up on the morning the stop
     * lifts. Where that is arranged is {@link TheNightReplayed}, which is the only place in this
     * module that knows what a cursor is.
     */
    default boolean itStopsTheSavingOn(LocalDate day) {
        return false;
    }

    /**
     * What this change adds to the weekly figure the goals are funded out of — nothing, unless the
     * kind says otherwise.
     *
     * <p>Separate from the money above, and both are needed. The euros the walk pays in decide the
     * balance, the points and the run; the weekly figure decides the day each goal is reached,
     * because a branch's goals are <em>projected</em> from a capacity rather than allocated out of
     * its deposits — which is the reading {@link TheNightReplayed} argues for at length and the one
     * the goals screen itself uses. A kind that paid money in without saying so here would move every
     * figure on the screen except the one the customer actually came to ask about.
     */
    default BigDecimal whatItAddsToTheWeeklyPlan() {
        return BigDecimal.ZERO;
    }

    /**
     * What this change takes back out of savings on that day — nothing, unless the kind says
     * otherwise.
     *
     * <p>Asked of every change on every morning of the walk, and asked <em>last</em>: after
     * everything the night did and before the week is judged on what went into it. A withdrawal is
     * not a step of the night — there is no scheduled job that takes a customer's money out — it is
     * a thing somebody does at a cashpoint during the day, and a Sunday withdrawal is part of that
     * Sunday's week. Which is the whole of why a week that was going to be secured may not be.
     *
     * <p>What is asked for and what leaves are two figures, and only the second is the walk's. A
     * branch takes what no goal has spoken for and dates a short withdrawal when that is less than
     * was asked, rather than refusing: the reasoning is on {@link TakingMoneyOut}, which is the one
     * kind that answers this, and the arithmetic is in {@link TheNightReplayed}, which is the one
     * place that knows what the branch is holding by then.
     */
    default BigDecimal whatItTakesOutOn(LocalDate day) {
        return BigDecimal.ZERO;
    }

    /**
     * Why that day is outside the year this simulation is drawn over, naming the window, or nothing
     * at all when it is inside it.
     *
     * <p>Here rather than in each kind because every kind carries at least one day and the objection
     * to all of them is the same objection. The sentence names the window rather than merely
     * refusing, for the reason every refusal in this application gives its figures: a customer told
     * "that day will not do" has to guess which days will.
     *
     * @param theDay what the day is to the customer, named back to them — "The day a weekly extra
     *               starts", not "startsOn"
     */
    static Optional<String> whyThatDayIsOutsideTheWindow(String theDay, LocalDate day,
                                                         TheStartingPoint standing) {
        if (day == null) {
            return Optional.of(theDay + " is missing, and a change to a future has to say which day "
                    + "it happens on.");
        }
        if (day.isBefore(standing.asAt()) || day.isAfter(standing.until())) {
            return Optional.of(theDay + " has to be inside the year this simulation is drawn over, "
                    + "which runs from " + standing.asAt() + " to " + standing.until() + ", and "
                    + day + " is not.");
        }
        return Optional.empty();
    }

    /**
     * The day this change wants that goal by — the deadline it already had, unless the kind says
     * otherwise.
     *
     * <p>Asked once per goal where a branch's goals are worked out, and on no morning of the walk,
     * because a branch's goals are <em>projected</em> from a plan rather than allocated out of its
     * deposits: the deadline decides what {@code HowTheWeeklyMoneyIsSpent} funds that goal to before
     * anything is left over for the goals behind it, and what {@code WhenAGoalWillBeReached} compares
     * the projection against. Both of those are one question about the whole year rather than
     * anything that happens in a night, so this is the one question here that no step of the night
     * asks.
     *
     * <p>Handed the deadline as it stands rather than being asked to produce one out of nothing, so
     * that a change which is not about this goal answers by saying nothing has changed — and so that
     * two changes in one scenario compose, each seeing what the one before it answered, rather than
     * the last one to be asked deciding on its own.
     *
     * @param goalId   which goal is being projected
     * @param deadline the day it is wanted by as the branch has it so far, which may be nothing at
     *                 all for a goal whose holder never gave it a date
     */
    default LocalDate theDayItWantsThatGoalBy(long goalId, LocalDate deadline) {
        return deadline;
    }
}
