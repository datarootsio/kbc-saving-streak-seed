package io.dataroots.savingstreak.rewards;

/**
 * Where an offer is in its life: being written, on sale, or taken down.
 *
 * <p>The only three states stored anywhere about an offer. Whether it is open today, sold out, over
 * somebody's limit or discounted this week is derived when it is read, against the window, the
 * stock, the limits and the clock — the line this application already holds for a points balance
 * and for a goal's status, and for the same reason: two stored figures that must agree eventually
 * stop agreeing.
 *
 * <p>{@code WITHDRAWN} rather than deleted, because a voucher already issued still points at the
 * offer it came from and a row that went away would make somebody's own history unreadable.
 *
 * <p>An enum rather than a row, unlike the catalogue itself, because this genuinely is a closed set
 * the code owns: an administrator publishes and withdraws, and a fourth state would be a change to
 * how the application works rather than to what it sells.
 *
 * <p><strong>Public, unlike the row it sits on.</strong> The entity and its repository stay behind
 * the service because where an offer is stored is nobody else's business, but where an offer is in
 * its life is the first thing the administration screen is about: a list of every offer in every
 * state is unreadable unless it can say which state each one is in. Handing that out as text would
 * be handing out a string the screen then has to know the three spellings of, which is the promise
 * an enum makes properly — and unlike the catalogue itself, this set really is closed, so the
 * promise is one this module can keep.
 */
public enum OfferState {

    /** Written but not on sale. Nothing a customer can see, which is the point of it. */
    DRAFT,

    /** On sale, subject to everything else the offer says about itself. */
    PUBLISHED,

    /** Taken down for good. The end of an offer's life, and never a deletion. */
    WITHDRAWN
}
