package io.dataroots.savingstreak.simulation;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The four changes a customer can ask this application to imagine, and the one place a name typed
 * into a request becomes the kind of thing that can be folded.
 *
 * <p><strong>Four and not five, and that is a decision rather than a stopping point.</strong> They
 * are the four decisions a customer actually faces — is another twenty-five a week worth it, what
 * does stopping for two months cost, what does taking five hundred out really cost, can I move that
 * deadline — and a general rule editor was rejected for them. Four kinds is what keeps two branches
 * comparable, what keeps four columns readable at 320px, and what keeps every figure in a branch
 * derived from a rule already written down somewhere in this codebase. A customer who wants to
 * redesign their automation has the automation screen, which previews itself.
 *
 * <p><strong>All four were declared on the day the shape was, and three of them were built
 * afterwards by people who could not see each other's work.</strong> That was the point of declaring
 * them together: a constant added per slice would have been three edits to the same handful of lines
 * and three merge conflicts, where a body each was three edits to three blocks that do not touch.
 * Until a kind was built its constant refused in a sentence saying so plainly rather than one
 * denying the kind existed, and each slice deleted its own sentence as it wrote the reading. All
 * four read their boxes now, so that scaffolding and the helper behind it are gone; what it bought
 * is the reason the list below can be read straight down without a switch anywhere near it.
 *
 * <p><strong>Each constant reads its own fields.</strong> Turning a form into a change is the one
 * thing that genuinely differs per kind and it is written per kind, here, rather than in a switch in
 * the web layer that every kind would have to be added to. {@link AnAdjustment} carries the numbered
 * list of what adding a kind involves; this file is step four of it.
 */
public enum AKindOfAdjustment {

    /**
     * Another amount put away every week, from a day, for the rest of the window — and added to what
     * the account is already doing rather than put in its place, which is what the word <em>more</em>
     * means.
     */
    SAVE_MORE_EACH_WEEK {
        @Override
        public AnAdjustment asAsked(AnAdjustmentAsAsked asked) {
            return new SavingMoreEachWeek(asked.amount(), asked.on());
        }
    },

    /**
     * A first day and a last day between which nothing is paid in and every standing rule is silent
     * — the same silence a pause already produces, and never made up on the morning it lifts.
     *
     * <p>Two of the four boxes and neither of the other two: the day it begins is {@code on} and the
     * day it ends is {@code until}, which is the one kind of change the second day exists for.
     */
    STOP_FOR_A_WHILE {
        @Override
        public AnAdjustment asAsked(AnAdjustmentAsAsked asked) {
            return new StoppingForAWhile(asked.on(), asked.until());
        }
    },

    /**
     * An amount taken out of savings on a day, drawn from the oldest deposit first and stopped by
     * what the goals have spoken for.
     *
     * <p>The change the whole feature is most worth having for, because five rules move at once and
     * a customer can predict one of them. What it costs them is {@link TakingMoneyOut}'s to say;
     * asking for more than the branch has free is an outcome rather than a refusal, and that is said
     * there too.
     */
    TAKE_MONEY_OUT {
        @Override
        public AnAdjustment asAsked(AnAdjustmentAsAsked asked) {
            return new TakingMoneyOut(asked.amount(), asked.on());
        }
    },

    /**
     * A goal on this account, and the day it is wanted by instead — which changes no money and no
     * rate, and changes what the weekly plan gives every goal behind it in the order.
     *
     * <p>The goal is the {@code goalId} box and the day it would be wanted by is the {@code on} one,
     * which is the same box every other kind counts its own day out of: a customer fills in one form
     * and each kind reads what it needs. {@link MovingADeadline} carries the rest of the argument,
     * including why it is the one change that touches no step of the night.
     */
    MOVE_A_DEADLINE {
        @Override
        public AnAdjustment asAsked(AnAdjustmentAsAsked asked) {
            return new MovingADeadline(asked.goalId(), asked.on());
        }
    };

    private static final Logger log = LoggerFactory.getLogger(AKindOfAdjustment.class);

    /**
     * This kind of change, built out of the boxes a customer filled in.
     *
     * <p>Nothing is validated here and nothing may be: whether the figure is an amount of money and
     * whether the day is inside the window are the kind's own sentences, said once by
     * {@link AnAdjustment#whyItCannotBeAsked} before any branch is folded, so that a request with
     * four bad adjustments in it is answered about the first rather than about whichever the reader
     * happened to reach.
     */
    public abstract AnAdjustment asAsked(AnAdjustmentAsAsked asked);

    /**
     * The kind that goes by that name, or a refusal listing the ones that do.
     *
     * <p>Read here rather than by the request being mapped onto the enum, so that a name nobody
     * recognises comes back as a sentence naming the four changes this application can imagine
     * instead of as a complaint about a request body. The list is the enum's own, so a kind added
     * later is in the sentence without anybody remembering to put it there.
     */
    public static AKindOfAdjustment named(String kind) {
        for (AKindOfAdjustment known : values()) {
            if (known.name().equalsIgnoreCase(String.valueOf(kind).trim())) {
                return known;
            }
        }
        String reason = "\"" + kind + "\" is not a change this application can imagine. The ones it "
                + "can are " + allOfThem() + ".";
        log.warn("adjustment rejected kind={} reason={}", kind, reason);
        throw SimulationRefused.againstTheRules(reason);
    }

    /** The four names as a customer reads them, for the sentence that refuses a fifth. */
    private static String allOfThem() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
