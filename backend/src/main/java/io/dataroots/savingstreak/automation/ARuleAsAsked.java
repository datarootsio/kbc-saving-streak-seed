package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

/**
 * A rule somebody is asking to leave standing, exactly as they said it, before this module has
 * ruled on any of it.
 *
 * <p>A record rather than eight parameters, because eight parameters of which five are nullable is
 * a call nobody can read at the call site and nobody can get right without counting. It is also what
 * makes the refusals readable: what arrived travels as one thing, so the sentence that comes back
 * can name the field that was wrong rather than the position it was in.
 *
 * <p><strong>Every field is allowed to be absent here, and that is the point.</strong> This is what
 * was asked for and not what will be kept: a trigger nobody named, a fixed amount with no figure,
 * and a weekly rule with no day are all things a customer can send, and each of them has a sentence
 * waiting for it in {@link AutomationService}. A record that could only hold a legal rule would move
 * the refusals into the web layer, which does not own them.
 *
 * <p>The two enumerated fields arrive already read as values rather than as the words that were
 * typed. Whether the characters somebody sent are one of the three triggers at all is a question
 * about the request and is answered in the web layer, in the same division of labour a goal's target
 * is read under; whether a rule with that trigger makes sense is a rule, and it belongs here.
 *
 * <p><strong>{@code split} is optional and is a list rather than a field.</strong> Null and empty
 * both mean a rule with no split, which is the ordinary rule: what it moves lands unallocated,
 * exactly as a manual deposit does. A split that is given has to name goals that are live on this
 * savings account and shares that add to a hundred, and both of those are rules about rules with a
 * sentence waiting for them in {@link AutomationService}.
 *
 * <p>Only one of {@code dayOfWeek} and {@code dayOfMonth} is ever kept, and only one of
 * {@code amount} and {@code floor} — which one follows from the trigger and from
 * {@link HowMuchMoves}. A customer who sends both is not refused for it: the one the rule has no use
 * for is dropped, because there is nothing wrong with a form that carried a field the rule does not
 * need.
 */
public record ARuleAsAsked(Long currentAccountId, String name, RuleTrigger trigger,
                           DayOfWeek dayOfWeek, Integer dayOfMonth, HowMuchMoves howMuchMoves,
                           BigDecimal amount, BigDecimal floor, List<AShareOfWhatMoves> split) {
}
