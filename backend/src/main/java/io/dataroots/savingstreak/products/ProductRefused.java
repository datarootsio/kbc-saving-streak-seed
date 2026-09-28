package io.dataroots.savingstreak.products;

/**
 * A savings product the application will not answer about, carrying the reason in words the person
 * who asked can act on.
 *
 * <p>A refusal of this module's own rather than one borrowed from Deposits or Rewards, for the
 * reason every module here has its own: the two vocabularies would be tied together and would grow
 * apart badly. This one is about an agreement and the reasons it will grow are about agreements —
 * a product closed to new accounts, an account locked into a term, a version that cannot be taken
 * — and none of those is a thing a deposit could ever be refused for.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer.
 */
public class ProductRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal can be about.
     *
     * <p>A {@code switch} over it in the web layer — like every other refusal in this codebase. The
     * next reason a product can be refused for arrives as a value here and the compiler then asks
     * the web layer what status it deserves, rather than letting it quietly inherit one that may
     * not fit.
     *
     * <p>Five of them are about publishing a version, which used to be the one thing anybody could
     * do to this module. They are separate values rather than one "that version will not do",
     * because each of them is a different thing to go and fix: a box to fill in, a figure to
     * rewrite, an ending to choose from a list, and a date that argues with the history. Only the
     * last of the five is about the state of the catalogue rather than about the form, and that
     * difference is exactly the one the web layer turns into a status.
     *
     * <p>The rest are about an account rather than about the catalogue: a product nobody named, a
     * product nobody may open an account on any more, an account that still has money in it, and an
     * account that was closed already. They sit here rather than in a refusal of their own because
     * they are all the same subject — what a savings account may be living under, and until when —
     * which is the subject this module owns. A second vocabulary for them would be two exceptions a
     * reader has to learn the boundary between, drawn where nobody outside this module can see it.
     */
    public enum Kind {

        /**
         * There is no product with that code, and there never was.
         *
         * <p>A reading of the catalogue rather than of a URL, which is why it is raised here and
         * not by a controller checking a path variable against a list it holds. Whether the bank
         * sells a thing is the catalogue's answer; the web layer's job is to say which status
         * reports it.
         *
         * <p>Deliberately not the answer for a product that has been closed to new accounts. That
         * one exists, a customer may well be holding it, and it reads perfectly well — "there is
         * nothing called that" would send whoever asked off to check their spelling for a product
         * that is sitting right there.
         */
        NO_SUCH_PRODUCT,

        /**
         * One of the numbers is missing, is less than nothing, or is quoted more finely than the
         * agreement can hold.
         *
         * <p><strong>One value for eight figures, and the sentence says which one.</strong> A value
         * per figure would be eight entries in the web layer's {@code switch} that every reader has
         * to check are all answering the same question, in order to draw a distinction nothing
         * downstream makes: they are all a box to go back and fix. The same reading the rewards
         * catalogue's "this offer is not for you" makes about its three thresholds — which of them
         * stopped you is in the sentence, deliberately not in the word.
         *
         * <p><strong>A figure nobody sent is refused rather than read as nought</strong>, because
         * nought is a real and different agreement in six of these eight: no notice, no term, no
         * floor, no penalty, no bonus, no interest at all. An empty box read as nought would let
         * somebody publish a rate cut to nothing by forgetting a field, on a product people are
         * saving into. A version is the whole list of numbers or it is not a version.
         */
        A_FIGURE_THESE_TERMS_CANNOT_CARRY,

        /**
         * No day was named for it to take effect on.
         *
         * <p>Its own value rather than one of the figures above, because the day is not a number
         * that could be wrong by a hundredth: it is the whole of what "from when" means here, it is
         * what an account opened on a particular morning is matched against, and there is no
         * sensible thing to assume in its place. Today would be an assumption — this bank announces
         * rate changes ahead of time and backdates corrections, and both are ordinary.
         */
        A_VERSION_WITH_NO_DAY_IT_TAKES_EFFECT,

        /**
         * No line saying what changed and why.
         *
         * <p>Required, and that is the ticket's whole point: a customer comparing the version they
         * are on with the one on offer should be reading an explanation rather than a diff. This
         * module will produce the diff mechanically; what it cannot produce is the reason. Blank
         * counts as absent, because a space is what a required field gets filled with by somebody
         * who has decided the rule does not apply to them.
         *
         * <p>Not asked of a first version, which says nothing changed because nothing did — but no
         * door here publishes a first version, so every version this refusal ever sees is a second
         * or a later one.
         */
        A_VERSION_THAT_DOES_NOT_SAY_WHAT_CHANGED,

        /**
         * The ending named for the term is not one of the three this bank offers.
         *
         * <p>A closed set that genuinely belongs to the code — each of the three is different
         * behaviour rather than a different number — so the refusal quotes back what was typed and
         * lists what there is to choose from. Raised here rather than by the web layer reading the
         * text into an enum, because which endings exist is this module's vocabulary and a
         * controller that knew the list would be a second place it is written down.
         */
        AN_ENDING_THIS_BANK_DOES_NOT_OFFER,

        /**
         * It would take effect before the version it follows, which would make the history read
         * backwards.
         *
         * <p><strong>The one refusal here that is about the catalogue rather than about the
         * form.</strong> Nothing was typed wrong — the day is a day, the figures are figures — and
         * it is the versions already published that will not have it. Version <em>n+1</em> dated
         * before version <em>n</em> would leave {@link TheTermsOnOfferToday} answering with the
         * higher number on a day the lower one was still being sold, so an account opened that
         * morning would be written under terms the bank had not published yet.
         *
         * <p>The same day is allowed. Two versions taking effect on one morning is a correction
         * published the same day it was announced, the tie is broken by the version number, and
         * {@link TheTermsOnOfferToday} already says so.
         */
        A_VERSION_DATED_BEFORE_THE_ONE_BEFORE_IT,

        /**
         * No product was named at all, on a request whose whole subject is which product to open an
         * account on.
         *
         * <p><strong>Not {@link #NO_SUCH_PRODUCT}, and the difference is what the person does
         * next.</strong> "There is nothing called that" sends somebody off to check their spelling;
         * they typed nothing, there is no spelling to check, and the thing to do is choose from the
         * four cards in front of them. It is also the difference between a 404 and a 400: an empty
         * box is a form to finish rather than a thing that is not there.
         *
         * <p>Blank counts as absent, for the reason a version with no line about what changed does:
         * a space is what a required field gets filled with by somebody who has decided the rule
         * does not apply to them.
         */
        NO_PRODUCT_CHOSEN,

        /**
         * The product is real, and the bank has stopped opening new accounts on it.
         *
         * <p><strong>A conflict rather than a not-found, and deliberately so.</strong> The product
         * exists, it is in the catalogue, customers are holding it, and its card is on the very
         * screen the request came from — saying it does not exist would be a lie the customer could
         * disprove by refreshing. Nothing about what they typed is wrong either, so it is not a
         * request to correct: it is a perfectly good request that the state of the catalogue will
         * not have, which is exactly the distinction
         * {@link #A_VERSION_DATED_BEFORE_THE_ONE_BEFORE_IT} is already drawn at.
         *
         * <p>It says nothing about the accounts already on that product, because closing one does
         * nothing to them. {@code SavingsProduct#closeToNewAccounts} argues that at length: this
         * refusal is about the next account and only the next account.
         */
        A_PRODUCT_CLOSED_TO_NEW_ACCOUNTS,

        /**
         * The account still has money in it, and only an emptied account may be closed.
         *
         * <p><strong>A conflict, because nothing was typed wrong.</strong> The customer asked to
         * close an account they hold, in the only way there is to ask; what will not have it is the
         * balance. The sentence says how much is still in there, because the thing to do next is
         * take it out or move it, and a refusal that made somebody go and look up the figure would
         * be a refusal with the useful half missing.
         *
         * <p><strong>Refused rather than emptied on their behalf.</strong> Closing an account is
         * not a way to move money: which of their accounts the euros should land in is a decision
         * with consequences for a week, a streak and a loyalty clock, and an application that took
         * it silently would be deciding all three. The customer withdraws, and then closes.
         */
        AN_ACCOUNT_THAT_STILL_HOLDS_MONEY,

        /**
         * The account was closed already.
         *
         * <p><strong>Refused, where closing a product a second time is not, and the two are
         * genuinely different.</strong> A product's door to new accounts is a flag with two
         * readings and a way back, so a repeated press gets somebody what they asked for. Closing an
         * account is one-way — there is no reopening it, by design — so a second press is a customer
         * acting on a screen that is out of date about something they cannot undo, and the honest
         * answer is to say it has already happened and when. That is the line an ended bill draws
         * for the same shape of act.
         */
        AN_ACCOUNT_ALREADY_CLOSED,

        /**
         * The account is inside a fixed term, and a locked agreement does not change underneath the
         * lock.
         *
         * <p><strong>This is what being locked in means, said from the other side.</strong> The
         * term already refuses a withdrawal until the day it matures; it refuses a change of terms
         * for the same reason and with the same date in the sentence. A lock that held the money
         * but let the agreement around it be swapped would be half a lock — the rate, the penalty
         * days and the ending the customer agreed to could all move while their money could not
         * leave, which is the worst of both halves.
         *
         * <p><strong>What happens on the day it is up is the ending these terms already
         * name</strong>, and it is not this door. The sentence used to promise that newer terms
         * could be taken on the maturity morning; {@link #A_TERM_WHOSE_DAY_HAS_COME} is the
         * refusal that replaced that promise and argues why, and the two together mean an account
         * on a term never takes newer terms at all. The refusal here is still a date rather than a
         * door — the date is when the lock comes off the money, not when the agreement becomes
         * editable.
         *
         * <p>A conflict rather than a bad request, for the reason
         * {@link #AN_ACCOUNT_THAT_STILL_HOLDS_MONEY} is one: nothing was typed wrong and there is
         * nothing to correct. What will not have it is the state of the agreement, and the thing to
         * do is wait or break the term.
         */
        AN_ACCOUNT_LOCKED_INTO_A_TERM,

        /**
         * The term's day has come, so the version it is living under has stopped moving.
         *
         * <p><strong>The other half of {@link #AN_ACCOUNT_LOCKED_INTO_A_TERM}, and the half that is
         * not obvious.</strong> A matured term reads unlocked, so the door stood open to it — and
         * what came through was never a repricing. A newer version of a product that has a term
         * carries a term of its own, and the day a term is up is worked out from that figure, so
         * taking one moves the maturity: a waiting twelve-month term offered a twenty-four-month
         * version had its free money locked away again for another year, and a waiting term offered
         * a version of the same length that rolls over instead sat for ever on a maturity the sweep
         * had already settled and never rolled. A maturity is settled by the terms it arrived
         * under, and after the day has come there is nothing left to take that is not a new term.
         *
         * <p><strong>The sentence names the day it matured and what to do instead</strong>, and
         * what to do differs by the ending the holder agreed to: money left waiting is free to take
         * out or move today, a term that rolls over is already pinned to the version on offer on the
         * morning it matured, and one that moves to instant access will be on free savings by the
         * morning — where free savings' newer terms are takeable in the ordinary way.
         * {@link WhatEachSavingsAccountIsOn#takeTheNewerTerms} argues the decision and the
         * alternative that was rejected.
         *
         * <p>A conflict for the same reason the lock is: nothing was typed wrong, the press carries
         * no body at all, and what will not have it is the state of the agreement.
         */
        A_TERM_WHOSE_DAY_HAS_COME,

        /**
         * The account is already on the version its product is selling today, so there is nothing
         * newer to take.
         *
         * <p><strong>Refused rather than quietly done again.</strong> Writing the version the
         * account is already on would be a write that changed nothing, answered as though something
         * had happened — and "you are now on version 2" is a sentence a customer would read as a
         * move. The honest answer is that there was nothing to move to, which is also the answer
         * that tells somebody their screen is out of date.
         *
         * <p><strong>A conflict, and the same one a second closing is.</strong> The request was
         * perfectly well formed and arrived from a page that was right when it was drawn; what will
         * not have it is the catalogue, which has published nothing since. It is emphatically not a
         * 404 — the account, the product and the version all exist — and not a 400, because there
         * is no box to go back and fix.
         *
         * <p>The sentence names both version numbers, because the useful half of this refusal is
         * which version the account turned out to be on.
         */
        AN_ACCOUNT_ALREADY_ON_THE_TERMS_ON_OFFER,

        /**
         * The figure the comparison screen was asked to project is not an amount of money: nothing
         * at all, or nought, or less, or quoted more finely than a euro is.
         *
         * <p><strong>A bad request rather than a conflict</strong>, because it is a box to go back
         * and fix and nothing about the catalogue is in an unexpected state. The sentence is
         * {@code AmountOfMoney}'s own, unchanged, so that the words somebody gets for typing "25,00"
         * into the comparison are the words they get for typing it into a deposit — one rule about
         * what an amount of money is, said once, in one voice.
         *
         * <p><strong>Refused rather than projected at nought.</strong> A projection of nothing is
         * twelve months of nought interest and nought points on every card, which reads as four
         * products that pay nothing rather than as a question nobody asked — and the customer would
         * be left comparing a shelf of zeros with no clue that the figure they typed was the
         * problem.
         */
        AN_AMOUNT_NO_PROJECTION_CAN_BE_MADE_ON
    }

    private final Kind kind;

    ProductRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
