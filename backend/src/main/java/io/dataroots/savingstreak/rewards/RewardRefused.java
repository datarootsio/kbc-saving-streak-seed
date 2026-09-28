package io.dataroots.savingstreak.rewards;

/**
 * A reward the application will not hand over, carrying the reason in words the person who asked for
 * it can act on — which reward, what it costs, and what they have.
 *
 * <p>Separate from the refusal Deposits raises, rather than shared with it. The two modules refuse
 * for their own reasons and will grow apart: this one already has a kind that has nothing to do with
 * money moving, and a shared exception would tie each module's vocabulary to the other's.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer.
 */
public class RewardRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal can be about. They are separate values because the person
     * reading one has a different thing to do next: find the right account, send a code the
     * catalogue has actually heard of, or go and save some more.
     *
     * <p>{@code NO_SUCH_OFFER} used to be raised by the web layer, which parsed the code the
     * customer typed into an enum constant and turned the failure into a bad request itself. It
     * belongs here now for the reason every other refusal does: whether the catalogue contains a
     * thing is the catalogue's answer, and the web layer's job is to say which status reports it.
     * The sentence is the one that was already sent, word for word, because a page that has been
     * open since before a release is how this refusal actually happens and the code it sent has to
     * come back in it.
     */
    public enum Kind {
        NO_SUCH_CUSTOMER,
        NO_SUCH_OFFER,

        /**
         * The catalogue has this offer and is not selling it: a draft that has never been
         * published, or an offer that has been withdrawn.
         *
         * <p><strong>Deliberately not {@code NO_SUCH_OFFER}.</strong> It would have been less code
         * to leave a draft out of the lookup and let it come back as a reward nobody has heard of,
         * and the sentence would have been a lie: the catalogue has heard of it, somebody is
         * writing it, and "there is nothing called that" sends whoever is reading off to check
         * their spelling. It also matters in the other direction — an offer withdrawn this morning
         * is a thing a customer was looking at yesterday, and they deserve to be told it is gone
         * rather than that it never existed.
         *
         * <p>It is checked second, straight after existence and before anything about the
         * customer, because it is the earliest question in the order this module refuses in: an
         * offer nobody may buy is not one whose price or whose claimant is worth reasoning about.
         */
        NOT_ON_SALE,

        /**
         * The offer is on sale and has a day it opens on, and that day has not arrived.
         *
         * <p><strong>Its own kind rather than a second reading of {@code NOT_ON_SALE}.</strong>
         * "Not on sale at the moment" and "opens on the third of December" are different things to
         * be told: the first is an offer somebody took down and there is nothing to do about it,
         * the second is an instruction to come back, with a date in it. Folding them together
         * would have saved a value here and cost the customer the only part of the sentence they
         * can act on.
         *
         * <p>Checked straight after whether the offer is on sale and before anything about the
         * customer, because that is the order this module refuses in and the order is part of the
         * contract: an offer nobody may claim yet is not one whose claimant or whose balance is
         * worth reasoning about. It is the same place {@link WhyAnOfferIsLocked#NOT_OPEN_YET} sits
         * in the reading a customer is shown, which is what stops the card and the refusal from
         * disagreeing.
         */
        NOT_OPEN_YET,

        /**
         * The offer had a last day and it has gone past — inclusive, so this is never the answer
         * on the closing day itself.
         *
         * <p>Separate from {@code NOT_OPEN_YET} rather than one {@code OUTSIDE_ITS_WINDOW}
         * covering both, because the two are opposite advice. One says come back and the other
         * says do not wait for it, and a customer given the wrong one of those either misses
         * something or saves for something that will never open again.
         */
        CLOSED,

        /**
         * The offer is open and on sale, and it carries a rule about who may claim it that this
         * customer does not meet.
         *
         * <p>Checked immediately after the window and well before the points, because that is
         * the order the spec fixes and the order is the whole of what makes "you are forty points
         * short" honest: a customer told they were short, who then saved for a month, and who was
         * then told the reward was never for them, would have been sent away by this application
         * to do something pointless. A rule about who somebody is does not become false by
         * waiting, which is exactly why it is said first.
         *
         * <p>One kind for all three thresholds, matching {@link WhyAnOfferIsLocked#NOT_FOR_YOU}
         * one for one, because the card and the refusal are the same fact at two moments and a
         * vocabulary that differed between them would be two sets for a page to reconcile. Which
         * of the three it was is in the sentence, which is word for word the sentence the card
         * was locked with.
         */
        NOT_FOR_YOU,

        /**
         * They are already in the queue for this offer, and asked to join it again.
         *
         * <p>Declared here, immediately before the limit, because that is where it is asked: it
         * is the same shape of question as "you are already holding one of these", which is
         * refused under the value below and in the position above the two caps. What stands in
         * the way is this customer's own history rather than anything about the offer, and the
         * whole of what pressing again could achieve has already been achieved — they are in
         * line, and joining twice would give them two places in one queue and two turns ahead
         * of somebody with one.
         *
         * <p><strong>A kind of its own rather than the limit's, which the second hold reuses
         * and which was the obvious thing to do here too.</strong> A limit is a rule somebody
         * set, and the sentence that goes with it is about a cap and a count; this is not a
         * rule at all and there is no cap to name. It is also the one refusal in this enum that
         * is <em>good news</em> — the customer wanted to be in the queue and they are in it,
         * with a position to prove it — and a page that could tell it apart from a cap can say
         * so rather than greying the card as though something had gone wrong. The sentence
         * carries the position for exactly that reason.
         */
        ALREADY_WAITING,

        /**
         * The customer has had as many of this offer as one person may — either in all, or in
         * the savings week they are claiming in.
         *
         * <p><strong>Its own kind rather than a second reading of {@code NOT_ON_SALE}.</strong>
         * The offer is on sale, it is open, and it is on sale <em>to somebody else</em> this
         * minute — the thing standing in the way is this customer's own history, which is the
         * one refusal in this enum that another person would not get for the same request at the
         * same moment. Folding it into anything else would lose exactly that.
         *
         * <p>One kind for the lifetime cap and the weekly one, matching
         * {@link WhyAnOfferIsLocked#YOU_HAVE_HAD_YOUR_LIMIT} value for value so that the card and
         * the refusal are the same fact under the same name. Which cap it was is in the sentence,
         * because that is where the part somebody can act on lives: a weekly allowance comes back
         * on Monday and a lifetime one never does.
         */
        YOU_HAVE_HAD_YOUR_LIMIT,

        /**
         * The offer had a stock figure on it and every one of them has already gone out.
         *
         * <p>Declared before {@code NOT_ENOUGH_POINTS} rather than after it, because these values
         * are written in the order the module asks the questions and being short of points is the
         * last question there is. Somebody told "you are forty points short" of a thing that sold
         * out last week would go away and save forty points, and that is the precise failure the
         * order exists to prevent.
         *
         * <p>Its own kind rather than {@code NOT_ON_SALE}, although both mean "you cannot have
         * this one". Not on sale is somebody taking a reward down; sold out is the scheme working
         * exactly as intended, and it is the one refusal here that an administrator can undo with
         * a number — so a page that could tell them apart can say "check back" for one and
         * nothing of the sort for the other.
         */
        NOTHING_LEFT,

        /**
         * Somebody asked to join the queue for an offer that has not run out.
         *
         * <p><strong>{@link #NOTHING_LEFT}'s exact mirror, and it sits beside it because it is
         * the same question read from the other side.</strong> That one refuses a claim because
         * there are none left; this one refuses a place in a queue because there are some. A
         * waiting list exists so that running out is not the end of it, and a queue for
         * something anybody can walk up and claim is a customer waiting for a thing that is
         * already theirs — with nothing to promote them to and nothing for the sweep to do,
         * because promotion hands out returning stock and no stock is returning.
         *
         * <p>Its own kind rather than a bad request written by the web layer, for the reason
         * every other rule in this module is a kind: whether an offer has run out is the
         * catalogue's answer and it changes between the page being drawn and the button being
         * pressed, which is exactly how this refusal actually happens. The sentence tells them
         * the thing they will want to hear, which is that they can simply claim it.
         *
         * <p>Declared before {@code NOT_ENOUGH_POINTS} like every other value here, because
         * points are asked last and always — and joining a queue never asks about them at all.
         * A waiting list costs nothing and locks nothing in: somebody who cannot afford the
         * thing today is precisely the customer a queue is worth having, because their turn may
         * be weeks away and the price is paid when they convert.
         */
        NOT_SOLD_OUT,

        /**
         * There is no live hold here to convert or to give up.
         *
         * <p><strong>One kind for every way a live hold can be absent, and the sentence says
         * which.</strong> It covers the hold that ran out, the hold that was given up, the hold
         * that has already been converted, and the customer who never took one at all. Naming
         * the commonest of the four is deliberate: lapsing is what actually happens to holds
         * nobody acts on, and it is the one a page will want to phrase differently. The other
         * three are the same fact about the same address — the offer is real, the customer is
         * real, and what they are asking to act on is not there — and giving each its own kind
         * would be three values a page has to learn in order to draw one message it could have
         * read out of the sentence.
         *
         * <p><strong>A refusal rather than a not-found, which is the same argument
         * {@code NOTHING_LEFT} makes.</strong> Nothing about the request is malformed and nothing
         * about the address is wrong: the customer exists, the offer exists, and what says no is
         * the state of the world and the passage of time. It is also the one refusal in this
         * enum a customer can be genuinely upset by, because the thing really was theirs an hour
         * ago, and the sentence carries the moment it stopped being so.
         *
         * <p>Declared before {@code NOT_ENOUGH_POINTS}, like every other lock here, because
         * points are asked last and always. It matters more for this one than for most: a
         * customer converting a hold whose points expired underneath them is told they are
         * short, which is true and is the whole reason a hold takes stock rather than points —
         * and a customer converting a hold that lapsed must be told that instead, because being
         * short would be an answer to a question nobody can act on any more.
         */
        THE_HOLD_HAS_LAPSED,

        /**
         * There is no place of theirs in this queue to leave.
         *
         * <p><strong>The twelfth kind, and the spec did not foresee it.</strong> The spec fixes
         * the eleven above by name, and when leaving a queue was built there was exactly one
         * value on that list meaning "what you are asking to act on is not there" — so leaving a
         * queue nobody was in was refused as {@link #THE_HOLD_HAS_LAPSED}, with an argument
         * written out at the refusal saying that a place in a queue is the step before a hold in
         * the same pipeline. The argument was true about the pipeline and false about the word.
         * The name of that value is about a hold and nothing else: it is the name a page
         * switches on, the name a log line carries, and the name whoever reads either will
         * reason from. A customer leaving a queue has no hold, has never had one, and may never
         * have one; telling the web layer that their hold lapsed is telling it something that
         * did not happen.
         *
         * <p><strong>A new kind is cheap here and the codebase is built for it.</strong> Every
         * exhaustive {@code switch} on this enum breaks compilation the moment a value is added,
         * which is exactly how this module makes sure a refusal reaches a customer with a status
         * somebody chose — so the cost of the twelfth is one deliberate line in the web layer,
         * and the alternative was a name that will mislead every reader of it for as long as it
         * stands. The spec's list is a list of the kinds a slice needed, not a promise that no
         * slice would ever need another.
         *
         * <p>It covers all three absences, the way the hold's value covers its four: the
         * customer who never joined, the customer who left already, and the customer whose
         * place became a hold when their turn came. Which of the three it was is in the
         * sentence, because that is where the part somebody can act on lives — and the third of
         * them is good news carrying a deadline.
         *
         * <p>Declared beside {@link #THE_HOLD_HAS_LAPSED} and before
         * {@code NOT_ENOUGH_POINTS}, because these values are written in the order the module
         * asks the questions: leaving a queue asks whether the offer exists and then whether
         * they are in it, and it never asks about points at all — a queue costs nothing to be
         * in and nothing to get out of.
         */
        YOU_ARE_NOT_IN_THAT_QUEUE,
        NOT_ENOUGH_POINTS
    }

    private final Kind kind;

    RewardRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
