package io.dataroots.savingstreak.automation;

import java.util.Arrays;
import java.util.List;

/**
 * Whether a saving rule is firing, stopped for a while at its holder's word, or ended for good.
 *
 * <p>Three values, and the middle one is the whole of this feature's one real idea. A rule that is
 * {@link #PAUSED} and a rule the application was simply down for both look like "occurrences that
 * did not fire", and they are treated opposite ways: downtime is the application's fault and is
 * always caught up, a pause is the customer's instruction and is honoured. Which of the two a gap
 * was is not guessed from the state — it is read off the record of when each pause began and ended,
 * which is what {@link RulePause} is for.
 *
 * <p>{@link #ENDED} is a closing rather than a deletion — the same shape {@code GoalState} uses, and
 * for a sharper reason: a rule that has moved money is the explanation for deposits that are already
 * in an account, and "what happened to my money last year" has to stay answerable after the rule
 * that did it is gone. An ended rule stops existing for every purpose except its history.
 *
 * <p>Ending is one-way and pausing is not, and the difference is deliberate. A paused rule is still
 * an instruction: it is on the page, it counts against the ten a customer may leave standing, and it
 * can be changed, resumed or ended. An ended rule is a record, and a rule brought back from that
 * would have a gap in the middle of it that nothing here could describe — the honest way to start
 * again is to leave a new rule standing.
 *
 * <p>Public, because {@link RecordedSavingRule} carries it out of the module: whoever renders a rule
 * has to be able to tell one that is firing from one that is stopped and one that was ended, and a
 * paused rule has to be able to say that it is paused rather than merely show no next day.
 */
public enum RuleState {

    /** Standing and firing: it is in the account's list of rules and it is what the night fires. */
    LIVE,

    /**
     * Standing, but stopped at its holder's word until they resume it.
     *
     * <p>It fires nothing while it is in this state, and the occurrences that fall meanwhile are
     * never made up: they are excluded from the catch-up range outright rather than recorded and
     * then judged, so no row is ever written for them. See {@link RulePause}.
     */
    PAUSED,

    /** Ended. It has left the list, and its name, its trigger and its figures remain readable. */
    ENDED;

    /**
     * Whether a rule in this state is still an instruction rather than only a record.
     *
     * <p>A switch <em>expression</em> rather than a comparison against {@link #ENDED}, so that a
     * fourth value added to this enumeration cannot compile until somebody has said what it means
     * here. That is the whole argument for keeping the question in one place: "is this rule still
     * standing" is asked by the list a customer reads, by the limit on how many they may leave
     * standing, and by every refusal about a rule that is over, and three copies of it would be
     * three chances for a new value to be quietly counted as standing.
     */
    boolean isStillStanding() {
        return switch (this) {
            case LIVE, PAUSED -> true;
            case ENDED -> false;
        };
    }

    /**
     * The states a rule that is still standing can be in, for the reads that are about the rules a
     * customer still has rather than about the ones that fire.
     *
     * <p>Derived from {@link #isStillStanding} rather than written out as a pair, so that the
     * question is answered in exactly one place and a new value reaches every one of those reads
     * through the switch above.
     */
    static List<RuleState> theOnesStillStanding() {
        return Arrays.stream(values()).filter(RuleState::isStillStanding).toList();
    }
}
