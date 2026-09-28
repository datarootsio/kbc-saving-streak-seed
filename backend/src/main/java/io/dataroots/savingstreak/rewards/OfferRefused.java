package io.dataroots.savingstreak.rewards;

/**
 * A change to the catalogue this application will not make, carrying the reason in words the person
 * running the scheme can act on.
 *
 * <p><strong>Its own exception, separate from {@link RewardRefused}.</strong> They are about
 * different people doing different things. A customer is refused a reward — they cannot afford it,
 * or it is not on sale — and what they do next is save up or pick something else. An administrator
 * is refused an edit, and what they do next is correct the form in front of them. Folding the two
 * together would mean one {@code switch} in the web layer answering for both, so that a status
 * chosen for a customer's mistake would silently arrive for an administrator's, which is exactly
 * the drift the per-concept refusals in this codebase exist to prevent — the argument
 * {@code SpendingCategoryRefused} makes about not borrowing this module's other one.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API and is answered in the web layer, where a new value in {@link Kind} breaks the switch by
 * design. That break is the mechanism: a refusal cannot reach whoever asked without somebody having
 * chosen what it looks like on the way out.
 */
public class OfferRefused extends RuntimeException {

    /**
     * The sorts of mistake a change to the catalogue can be. They are apart because the person
     * reading one has a different thing to do next: pick another code, fix what they typed, or
     * accept that the offer they are arguing about is over.
     */
    public enum Kind {

        /**
         * An offer already exists under that code.
         *
         * <p>Its own kind rather than one more thing that is against the rules, for the reason
         * {@code ALREADY_A_CATEGORY_HERE} is: nothing about what was typed is malformed, and what
         * the administrator does next is either edit the offer they already have or choose a
         * different code. The sentence names the offer back, so that a catalogue of forty entries
         * does not have to be scrolled to find out which one is in the way.
         */
        CODE_ALREADY_TAKEN,

        /**
         * The request does not describe an offer this application will keep: no code, no title, a
         * price below a single point, no voucher prefix, words longer than the column holding
         * them — or a change trying to rename the code, which is the one thing about an offer that
         * is fixed for its whole life.
         *
         * <p>One kind for all of them, because they are the same thing to whoever is reading: fix
         * the form and send it again. The sentence says which of them it was, and the sentence is
         * the part that has to be right.
         */
        AGAINST_THE_RULES,

        /**
         * The offer named has been withdrawn, and a withdrawn offer is a record rather than a thing
         * still being run.
         *
         * <p>Kept readable and refused to every change, exactly as an ended category and an ended
         * bill are: the whole reason an offer is withdrawn rather than deleted is that the vouchers
         * already issued for it go on meaning something, and a record that could be repriced,
         * retitled or quietly put back on sale afterwards is not a record. Somebody who wants to
         * sell the thing again creates an offer for it, which is honest about it being a new one.
         *
         * <p><strong>It answers for a member of a bundle too, and for the same reason.</strong>
         * An offer that has been taken down cannot be put inside a hamper somebody is about to
         * publish: the whole meaning of withdrawn is that the scheme is no longer handing the
         * thing over, and a bundle that contained one would be a promise to hand over a record.
         * The sentence names the member rather than the bundle, because the member is the part
         * that is over.
         */
        THE_OFFER_IS_WITHDRAWN,

        /**
         * The stock somebody typed is smaller than the number of them that have already been
         * claimed.
         *
         * <p>Its own kind rather than one more thing that is against the rules, and the
         * distinction is the one {@code CODE_ALREADY_TAKEN} draws: nothing about the form is
         * malformed — forty is a perfectly good number of hampers — and what the administrator
         * does next is not fix what they typed but accept a figure they did not know. The
         * sentence quotes how many have gone, because that is the number they are actually being
         * told and the smallest stock they are allowed to set.
         *
         * <p>Refused rather than silently clamped, because what is left is derived: a stock of
         * forty against fifty-one claims would read as nought left, which looks exactly like a
         * sold-out offer and is in fact a catalogue that has issued eleven more of something than
         * it says exists. A figure on the screen that is not true of anything is worse than a
         * refusal somebody has to read.
         */
        STOCK_BELOW_WHAT_IS_ALREADY_OUT,

        /**
         * A bundle names something the catalogue has never heard of.
         *
         * <p>Its own kind rather than one more thing that is against the rules, and the
         * distinction is the one {@code CODE_ALREADY_TAKEN} draws from the other end. Nothing
         * about the form is malformed — {@code WINTER_HAMPER} is a perfectly good code and two
         * of it is a perfectly good quantity — and what the administrator does next is not fix
         * the shape of what they typed but go and find out what the thing they meant is actually
         * called. That is a different afternoon from correcting a blank title, and a refusal
         * that could not be told from one would send them looking for a mistake in a line that
         * has none.
         *
         * <p>It is also the one mistake here that is about a <em>second</em> offer rather than
         * about the one being written, which is why the sentence names the code that could not
         * be found rather than the code of the offer the form is for. A bundle with four lines
         * and one typo in it is otherwise a refusal somebody has to bisect by hand.
         *
         * <p>A bundle naming <em>itself</em> is deliberately not this. The code exists, or is
         * about to, and the mistake is a different one — it is a form that describes something
         * impossible rather than something absent — so it is refused as against the rules, in a
         * sentence that says so.
         */
        NO_SUCH_MEMBER
    }

    private final Kind kind;

    OfferRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
