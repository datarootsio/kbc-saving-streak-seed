package io.dataroots.savingstreak.automation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The bodies a customer's form would send when they leave a rule standing, built once so that a test
 * about one field does not have to restate the other seven.
 *
 * <p>Static and free of any application, because two quite different fixtures need them: the rules
 * that are only ever written and read back stand on the shared application through
 * {@link AnAccountWithRules}, and the rules that have to <em>fire</em> stand on an application of
 * their own whose clock the test winds. One copy of "a weekly rule is a trigger, a day and an
 * amount" keeps those two from drifting into disagreeing about what a rule looks like.
 *
 * <p>Every figure travels as the text a customer would type, the way a deposit's amount does, so a
 * test can hand these whatever somebody could put in the box.
 *
 * <p>{@code HashMap} rather than {@code Map.of}, so that a test about a missing field can send a
 * null, which {@code Map.of} will not hold.
 */
final class RulesAsSomebodyWouldTypeThem {

    private RulesAsSomebodyWouldTypeThem() {
    }

    /** A fixed amount every week on a chosen day, which is the ordinary rule this feature is for. */
    static Map<String, Object> aFixedAmountEveryWeek(long fromCurrentAccountId, String name,
                                                     String dayOfWeek, String amount) {
        Map<String, Object> rule = aRuleDrawnFrom(fromCurrentAccountId, name);
        rule.put("trigger", "WEEKLY");
        rule.put("dayOfWeek", dayOfWeek);
        rule.put("howMuchMoves", "A_FIXED_AMOUNT");
        rule.put("amount", amount);
        return rule;
    }

    /** A fixed amount every month on a chosen date. */
    static Map<String, Object> aFixedAmountEveryMonth(long fromCurrentAccountId, String name,
                                                      String dayOfMonth, String amount) {
        Map<String, Object> rule = aRuleDrawnFrom(fromCurrentAccountId, name);
        rule.put("trigger", "MONTHLY");
        rule.put("dayOfMonth", dayOfMonth);
        rule.put("howMuchMoves", "A_FIXED_AMOUNT");
        rule.put("amount", amount);
        return rule;
    }

    /** Everything above a floor, every week on a chosen day. */
    static Map<String, Object> everythingAboveAFloorEveryWeek(long fromCurrentAccountId, String name,
                                                              String dayOfWeek, String floor) {
        Map<String, Object> rule = aRuleDrawnFrom(fromCurrentAccountId, name);
        rule.put("trigger", "WEEKLY");
        rule.put("dayOfWeek", dayOfWeek);
        rule.put("howMuchMoves", "EVERYTHING_ABOVE");
        rule.put("floor", floor);
        return rule;
    }

    /**
     * A fixed amount on the day the holder's declared income lands.
     *
     * <p>A fixed amount rather than a sweep, for the tests about a payday that moves: a sweep that
     * fired a second time in one month would find the account already at its floor and move nothing,
     * so the very defect those tests exist for would leave the balances looking right. A fixed amount
     * moves its whole self every time it fires, which is what makes a second firing visible as money.
     */
    static Map<String, Object> aFixedAmountOnPayday(long fromCurrentAccountId, String name,
                                                    String amount) {
        Map<String, Object> rule = aRuleDrawnFrom(fromCurrentAccountId, name);
        rule.put("trigger", "ON_PAYDAY");
        rule.put("howMuchMoves", "A_FIXED_AMOUNT");
        rule.put("amount", amount);
        return rule;
    }

    /** Everything above a floor, on the day the holder's declared income lands. */
    static Map<String, Object> everythingAboveAFloorOnPayday(long fromCurrentAccountId, String name,
                                                             String floor) {
        Map<String, Object> rule = aRuleDrawnFrom(fromCurrentAccountId, name);
        rule.put("trigger", "ON_PAYDAY");
        rule.put("howMuchMoves", "EVERYTHING_ABOVE");
        rule.put("floor", floor);
        return rule;
    }

    /**
     * The same rule, spread across goals in the order given — the "60/30/10 across three goals" of
     * the brief.
     *
     * <p>The rule it is handed is one of the four above, so that a test about a split does not have
     * to restate what a weekly rule looks like in order to say what it does with the money.
     *
     * <p>The shares travel as the text a customer would type, like every other figure here, so a
     * test can hand this whatever somebody could put in the box.
     */
    static Map<String, Object> spreadAcross(Map<String, Object> rule,
                                            List<Map<String, Object>> shares) {
        rule.put("split", shares);
        return rule;
    }

    /** One line of a split: which goal, and the whole percentage of what moves that it is offered. */
    static Map<String, Object> aShareFor(Long goalId, String share) {
        Map<String, Object> line = new HashMap<>();
        line.put("goalId", goalId);
        line.put("share", share);
        return line;
    }

    /** Several lines of a split, in the order the customer wrote them. */
    @SafeVarargs
    static List<Map<String, Object>> inTurn(Map<String, Object>... shares) {
        return new ArrayList<>(List.of(shares));
    }

    /**
     * The bones of any rule: a name, and the current account the money comes out of. A test that is
     * about one field fills that field in and leaves the rest of this alone.
     */
    static Map<String, Object> aRuleDrawnFrom(long fromCurrentAccountId, String name) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("name", name);
        rule.put("fromCurrentAccountId", fromCurrentAccountId);
        return rule;
    }
}
