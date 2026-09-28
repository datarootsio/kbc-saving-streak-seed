package io.dataroots.savingstreak.rewards;

import java.time.LocalDate;

/**
 * What somebody wants said differently about an offer that already exists.
 *
 * <p><strong>Absent means "leave it alone", everywhere, without exception.</strong> The same
 * reading {@code AChangeToABill} and {@code AChangeToARule} give their own null fields, and for the
 * same reason: a typo in a title, a repricing and a new voucher prefix are three separate edits
 * rather than one form that has to be retyped whole every time one word is wrong.
 *
 * <p><strong>The code is here only so that it can be refused.</strong> An offer's code is its
 * natural key: a claim already made names it, the API is addressed with it, and a row that changed
 * its code would take everything pointing at it away from the person holding a voucher. So it is
 * fixed from the moment the offer exists. It would have been simpler to leave it off this record
 * altogether and silently ignore whatever arrived — and that is exactly the failure worth avoiding,
 * because a screen that sent one and got a 200 back would show an administrator a rename that never
 * happened. A field that can be sent and is answered with a refusal is the only shape that tells
 * the truth. A code naming the offer's own code is not a rename and is let through.
 *
 * <p>The shelf life reads the same way, and the same wart comes with it: absence means "leave it
 * alone", so there is no way here to take a shelf life back off an offer that has one. That is a
 * change nobody has asked for — an offer whose vouchers should last longer is an offer with a
 * bigger number on it — and the alternative is a second meaning for every other field's absence,
 * or a nought that means something different from every other nought. Vouchers already issued are
 * untouched either way: the day on a voucher is written when it is claimed.
 *
 * <p><strong>The three eligibility thresholds read as "leave it alone" too, and that wart is the
 * shelf life's wart again.</strong> Absence means nothing is being asked, so there is no way here
 * to take a rule back off an offer that has one. It is worth saying plainly rather than pretending
 * it does not matter: calling off a restriction is a more ordinary thing to want than calling off
 * a shelf life, and an administrator who wants an offer opened up to everybody has to withdraw it
 * and write another. The alternative is three more booleans beside three fields — the shape the
 * two days carry and which is already the most complicated thing on this record — or a second
 * meaning for every other field's absence. Neither is worth it for a slice, and the day somebody
 * asks for it, the days above are the pattern to copy and the argument for copying it is written
 * out below.
 *
 * <p><strong>The two caps read the same way, and inherit the same wart.</strong> Absence means
 * "leave it alone", so there is no way here to take a cap back off an offer that has one. That
 * is the shelf life's reading above, arrived at for the shelf life's reason: the alternative is
 * a flag beside every field or a nought that means something different from every other nought,
 * and a record where null meant two things depending on which field it was on is a contract
 * nobody can read back. Uncapping an offer somebody capped has not been asked for, and an
 * administrator who wants a limit loosened is an administrator who wants a bigger number — the
 * one case the wart genuinely bites is an offer that should become unlimited again, and the
 * honest answer today is that this record cannot say it.
 *
 * <p><strong>The stock is a restock, and it is the total rather than a difference.</strong>
 * "There are forty of these now" is what somebody types into a box they can read back; "add
 * twelve" is an instruction whose result depends on what the box said when the page was loaded,
 * which is the one thing a form cannot be sure of. Raising it puts a sold-out offer back in the
 * window on the very next read, because what is left is derived and there is no verdict anywhere
 * to clear. Lowering it below what has already gone out is refused in a sentence quoting how many
 * that is, because the alternative is a figure on the screen that is not true of anything.
 *
 * <p>It carries the same wart the shelf life does: absence means "leave it alone", so there is no
 * way here to take a stock figure back off and make an offer unlimited again. That is a change
 * nobody has asked for — an offer that should not run out is an offer with a bigger number on it
 * — and the alternative is a second meaning for every other field's absence, or a nought that
 * would mean something different from the nought an administrator genuinely types when there are
 * none left.
 *
 * <p>The description is the one field a null is genuinely ambiguous about, and it is read as "leave
 * it alone" like all the others. An offer whose words should go away is an offer whose words should
 * be something else; the sentence for a reward with nothing to say about itself has not come up,
 * and inventing a flag to express it would put a second meaning on every other field's absence.
 *
 * <p><strong>The two days are the exception, and each carries a flag saying whether it is an
 * instruction at all.</strong> A window is the one thing on an offer whose <em>removal</em> is a
 * thing somebody genuinely means: a season that was announced and then called off leaves an offer
 * that should go back to being open, and a closing day typed into the wrong box has to be
 * gettable-out-of. Null on its own cannot say that, because null is already how every other field
 * here says "leave it alone", and a record where null meant "leave it" for five fields and "take
 * it away" for two would be a contract nobody could read back. So the day travels with a boolean
 * beside it, exactly as {@code GoalsService.changeGoal} carries {@code theDeadlineIsBeingChanged}
 * beside a deadline, and for the same reason it does there. The boolean false means the day was
 * not mentioned; true with a null day means the offer no longer has one.
 *
 * <p><strong>The promotion's two days carry the same flags, and its price is read like every
 * other field.</strong> Absent leaves the price alone; a figure changes it; the two days each say
 * whether they are an instruction at all, exactly as the window's do, because a promotion called
 * off is the same shape of edit as a season called off and one of them getting a different
 * mechanism would be two contracts for one idea.
 *
 * <p><strong>Emptying both of the promotion's days takes the promotion off, price and all, and
 * that is the only way to take one off.</strong> A promotion is one fact in three parts and the
 * module stores all three or none, so there is no such thing as keeping the price and dropping
 * the dates — a price that applies on no day is not a cheaper offer, it is a figure nobody will
 * ever be charged sitting in a column. Saying so here, rather than refusing an administrator who
 * emptied both boxes and left the price where it was, is the reading that matches what they
 * meant: they called the sale off. A change that empties both days <em>and</em> names a new
 * discounted price is the one shape that cannot be read, because it starts a promotion and ends
 * one in the same sentence, and it is refused rather than guessed at.
 */
public record AChangeToAnOffer(String code, String title, String description, Long costInPoints,
                               String voucherPrefix,
                               LocalDate opensOn, boolean theOpeningDayIsBeingChanged,
                               LocalDate closesOn, boolean theClosingDayIsBeingChanged,
                               Integer voucherValidForDays,
                               Integer minimumStreakWeeks, String requiresBadge,
                               Long minimumLifetimePointsEarned,
                               Integer maxPerCustomer, Integer maxPerCustomerPerWeek,
                               Integer stock,
                               Long discountedCostInPoints,
                               LocalDate discountOpensOn,
                               boolean theDiscountsOpeningDayIsBeingChanged,
                               LocalDate discountClosesOn,
                               boolean theDiscountsClosingDayIsBeingChanged) {

    /**
     * Whether this change asks for nothing at all — an empty body, or one whose every field was
     * left out.
     *
     * <p>Worth its own question for the reason {@code AChangeToABill.saysNothing} is: absence means
     * "leave it alone" field by field, so a request where every field is absent asks for nothing
     * while looking exactly like a request that asked for something, and answering it 200 with the
     * offer unchanged would tell a page its edit went through.
     */
    public boolean saysNothing() {
        return code == null && title == null && description == null && costInPoints == null
                && voucherPrefix == null && voucherValidForDays == null
                && !theOpeningDayIsBeingChanged && !theClosingDayIsBeingChanged
                && minimumStreakWeeks == null && requiresBadge == null
                && minimumLifetimePointsEarned == null
                && maxPerCustomer == null && maxPerCustomerPerWeek == null
                && stock == null
                && discountedCostInPoints == null
                && !theDiscountsOpeningDayIsBeingChanged
                && !theDiscountsClosingDayIsBeingChanged;
    }
}
