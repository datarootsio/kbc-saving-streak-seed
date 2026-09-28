package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

/**
 * What somebody wants said differently about a rule that already exists.
 *
 * <p><strong>Absent means "leave it alone", everywhere, without exception.</strong> That is what
 * makes renaming a monthly rule a thing somebody can do without restating its day and its amount,
 * and it is the same reading {@code GoalsService.changeGoal} gives to a null name or target. No
 * field here needs a flag beside it to say it is being cleared: a rule always has a trigger, always
 * has the day that trigger needs and always has one of the two figures, so "there is no longer a day
 * I want this by" — the one sentence a goal's deadline needed a flag for — has no counterpart among
 * them. The one field a rule can genuinely stop having is {@code split}, and a list says that for
 * itself by arriving empty.
 *
 * <p><strong>The current account is deliberately not changeable.</strong> Where the money comes from
 * is half of what a rule is, and a rule that had drawn from one account and then another would have
 * a history whose entries mean different things depending on when they were made. A customer who now
 * saves out of a different account ends this rule and leaves a new one standing, which keeps both
 * records true.
 *
 * <p>{@code howMuchMoves} is changeable, and it is what makes an amount and a floor changeable
 * separately from each other: a customer turning a standing order into a sweep says both, in one
 * request. The rule as it would then read is what is judged — not the fields that arrived — so a
 * change that leaves a trigger without its day is refused as the incomplete sentence it is.
 *
 * <p><strong>A figure or a day the rule would have no use for is refused here, although the same
 * field is quietly dropped when a rule is first left standing.</strong> The asymmetry is deliberate
 * and it is about what the two requests mean. A new rule arrives as a whole form, and a form that
 * carried a box the chosen kind of rule does not use says nothing about what its sender wanted. A
 * change names the fields on purpose, one by one: somebody sending a floor to a rule that moves a
 * fixed amount has said the one thing they came to say, and answering 200 to it would be reporting a
 * change that was never made. Saying too little is already refused as the half a sentence it is, and
 * saying something the rule contradicts is refused for the same reason.
 *
 * <p><strong>{@code split} is the one field here with two ways to be absent, and both of them are
 * things a customer means.</strong> Null is "leave the split alone", like every other field. An
 * empty list is "stop spreading it": the shares are taken away and the rule goes back to depositing
 * unallocated. A list is the new split, which replaces the old one whole rather than being merged
 * into it — a split is a sentence about proportions and half of one does not add to a hundred.
 *
 * <p>What is <em>not</em> refused is a trigger that leaves a stored day behind — turning a rule set
 * for the 15th into one that fires on payday drops the 15, because a payday rule's day is its
 * holder's declaration and the 15 no longer means anything. The customer is told what the rule now
 * says in the answer, and the day that went is on the {@code was…} half of the log line.
 */
public record AChangeToARule(String name, RuleTrigger trigger, DayOfWeek dayOfWeek,
                             Integer dayOfMonth, HowMuchMoves howMuchMoves, BigDecimal amount,
                             BigDecimal floor, List<AShareOfWhatMoves> split) {

    /**
     * Whether this change asks for nothing at all — an empty body, or one whose every field was left
     * out.
     *
     * <p>Worth its own question because "leave it alone" is what absence means field by field, and a
     * request where every field is absent therefore asks for nothing while looking exactly like a
     * request that asked for something. Answering it 200 with the rule unchanged would tell a page
     * its edit went through.
     *
     * <p>An <em>empty</em> split is not nothing. It is a customer saying "stop spreading what this
     * rule moves", which is a change with an effect, and a request carrying only that one asks for
     * something.
     */
    public boolean saysNothing() {
        return name == null && trigger == null && dayOfWeek == null && dayOfMonth == null
                && howMuchMoves == null && amount == null && floor == null && split == null;
    }
}
