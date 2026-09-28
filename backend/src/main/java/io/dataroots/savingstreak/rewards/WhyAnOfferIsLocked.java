package io.dataroots.savingstreak.rewards;

/**
 * The one thing standing between a customer and an offer they can see.
 *
 * <p><strong>A lock is not a refusal, and that is why this is not {@link RewardRefused.Kind}.</strong>
 * A refusal is what somebody is told after they pressed the button; a lock is what the card says
 * before they press it, which is a different moment with a different job. The two sets overlap and
 * will go on overlapping — a window that refuses a claim is the window that locks the card — but
 * they are not the same set and never can be. {@code NO_SUCH_OFFER} and {@code NO_SUCH_CUSTOMER}
 * are answers about a request, and an offer being <em>read</em> is by definition one the catalogue
 * has and one somebody is reading; {@code NOT_ON_SALE} cannot appear here either, because a draft
 * and a withdrawn offer are filtered out of the customer's catalogue rather than shown locked. A
 * page switching over the refusal's vocabulary would have to handle four values it can never be
 * given, and the day somebody added a fifth refusal about a request the page would be asked to
 * draw a card for it.
 *
 * <p><strong>At most one of these is ever reported, and which one is decided by an order that is
 * part of the contract.</strong> The spec fixes it: existence, then on sale, then the window, then
 * eligibility, then the limit, then stock, then points — and being short of points is last,
 * always, so that "you are forty points short" is only ever said when being short is genuinely the
 * only thing wrong. A customer told the cheapest reason first would go and save up for something
 * they were never allowed to have. The values below are declared in that order, and the slices
 * that add the rest insert theirs in their place rather than at the end.
 *
 * <p><strong>Being short of points is deliberately not here.</strong> The page has the balance and
 * the price already and has greyed the button out against the two of them since before there was a
 * catalogue to run; a second answer to the same question, arriving from the backend one read
 * later, would let the card disagree with itself while the balance ticks up. What is here is the
 * set of things the page genuinely cannot work out — and every one of them is a rule somebody set
 * in the administration screen.
 *
 * <p>Public, unlike the row it is derived from, for the reason {@link OfferState} is: a card that
 * has to say <em>why</em> it is locked cannot be handed a string whose spellings it then has to
 * know.
 */
public enum WhyAnOfferIsLocked {

    /**
     * The offer has a day it opens on and that day has not arrived. The card says which day, so
     * that "come back for it" is an instruction rather than a mood.
     */
    NOT_OPEN_YET,

    /**
     * The offer had a last day and it has gone past. Inclusive: an offer closing on the thirtieth
     * is claimable all through the thirtieth and locked on the thirty-first, because that is what
     * everybody reading the date assumes and a poster that lied by a day would be worse than no
     * poster.
     */
    CLOSED,

    /**
     * The offer carries rules about who may claim it and this customer does not meet one of them:
     * a run of weeks that is not long enough, a badge they have not won, or a lifetime of points
     * earned they have not reached.
     *
     * <p><strong>One value for three rules, and the sentence carries which.</strong> A value per
     * threshold would have the page switching over a vocabulary that grows every time the scheme
     * learns to ask for something new, in order to draw the same greyed card three ways. What
     * differs between them is the words, and the words already travel beside this — which is the
     * whole reason they travel. What the page has to know is that this card is locked because of
     * something the customer <em>is</em>, rather than because of a date or a shortage, and that
     * is one fact.
     *
     * <p><strong>Shown and never hidden</strong>, and this is the value that decision was written
     * for. An offer a customer does not qualify for is the only thing on the screen that says
     * what the scheme wants from them: hide it and a streak nobody is told about is a streak
     * nobody holds. The frontend already greys a reward somebody cannot afford and says how far
     * short they are; this is that treatment with a different sentence.
     */
    NOT_FOR_YOU,

    /**
     * This customer has had as many of it as one person may — ever, or in the savings week they
     * are reading in.
     *
     * <p><strong>One value for both caps, and that is a decision rather than an
     * oversight.</strong> What a page does with a lock is style the card and shorten the button,
     * and "had your limit" is the same two words whichever cap it was; what the <em>person</em>
     * needs is the difference, and the difference is in the sentence beside this — one of them
     * says the week turns over on Monday and the other does not, because only one of them is
     * something waiting fixes. Splitting the value would give the page two cases to switch on
     * that it would draw identically, and would put the interesting half of the distinction in
     * the half nobody reads.
     *
     * <p><strong>It sits after eligibility and before stock in the order, and the order is the
     * contract.</strong> A customer at their limit is not told the last one has gone, because the
     * last one being there or not is beside the point for somebody who may not have another; and
     * they are told this rather than that they are short of points, because being short is said
     * last, always. It is declared in that place here too, after {@link #NOT_FOR_YOU} and ahead
     * of anything about stock, which is where {@code RewardsService} asks it.
     *
     * <p>This is the first lock whose answer depends on <em>who</em> is reading rather than only
     * on when. The window above is the same for everybody; this one is the reason the
     * customer-shaped reading takes a customer at all, and the reason one person's card going
     * grey has to leave everybody else's exactly as it was.
     */
    YOU_HAVE_HAD_YOUR_LIMIT,

    /**
     * There were only so many of it and they have all gone.
     *
     * <p>Last of the locks and the last thing asked before the points, which is exactly where the
     * order puts it: being sold out is a fact about the offer and being short is a fact about the
     * customer, and telling somebody they are forty points short of something nobody can have any
     * more would send them off to save for it. It is also the one lock that can come back — an
     * administrator raising the stock unlocks every card in the catalogue on the next read — which
     * is why the card carries how many are left beside it rather than only the word.
     *
     * <p>A lock rather than a disappearance, and this one is the hardest case for that rule and
     * the one that most needs it. An offer that vanished the moment the last one went would leave
     * a customer who had been saving for it with no idea what became of it, and would take away
     * the only thing on the page that tells them it might be back.
     */
    NOTHING_LEFT
}
