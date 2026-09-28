package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;

/**
 * What somebody wants said differently about a bill that already stands.
 *
 * <p><strong>Absent means "leave it alone", everywhere, without exception.</strong> That is what
 * makes the three things a bill can be changed by — the rent going up, a provider moving its
 * collection date, a name that was a typo — three separate edits rather than one form that has to be
 * retyped whole. It is the same reading {@code AChangeToARule} and {@code GoalsService.changeGoal}
 * give to their own null fields.
 *
 * <p>No field here needs a flag beside it to say it is being cleared, because a bill has all three
 * of them always: it has a name, it has a day and it is worth something. "There is no longer an
 * amount" is not a sentence about a bill — the sentence for that is ending it.
 *
 * <p><strong>The account is deliberately not changeable.</strong> Which account a bill leaves is
 * half of what the bill is, and one that had gone out of one account and then another would have a
 * history whose rows mean different things depending on when they were written. A customer who now
 * pays the rent out of a different account ends this bill and declares a new one, which keeps both
 * records true — the same argument a saving rule's own current account is fixed under.
 */
public record AChangeToABill(String name, Integer dayOfMonth, BigDecimal amount) {

    /**
     * Whether this change asks for nothing at all — an empty body, or one whose every field was left
     * out.
     *
     * <p>Worth its own question because "leave it alone" is what absence means field by field, and a
     * request where every field is absent therefore asks for nothing while looking exactly like a
     * request that asked for something. Answering it 200 with the bill unchanged would tell a page
     * its edit went through.
     */
    public boolean saysNothing() {
        return name == null && dayOfMonth == null && amount == null;
    }
}
