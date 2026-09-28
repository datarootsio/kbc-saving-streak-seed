package io.dataroots.savingstreak.challenges;

/**
 * Where an enrolment stands. Exactly one of four, and only {@link #ACTIVE} is read for progress.
 *
 * <p><strong>Leaving is a state change and never a delete.</strong> A row that is gone cannot be
 * asked about, and this one has to keep answering: the badges a later slice hangs off an enrolment
 * point at it, the trophy case reads them, and a customer who left a challenge did something that
 * nothing but this column records. It is the same argument {@code GoalState.ABANDONED} makes about a
 * goal somebody gave up on, and the same one the points ledger makes about a batch that expired
 * rather than being emptied.
 *
 * <p>All four are now driven, and the set was settled before any of them were: {@link #COMPLETED}
 * when the gold rung is awarded, {@link #ABANDONED} when the customer leaves, {@link #EXPIRED} when
 * the campaign an enrolment belongs to closes before it was finished. Naming them all at once is
 * what let everything that reads a state say <em>live or not</em> once, instead of each reader
 * growing its own list of the states it happened to have heard of.
 *
 * <p><strong>{@link #COMPLETED} and {@link #EXPIRED} are both ends and they are not the same
 * end</strong>, which is why the season closing does not simply complete everything it catches. One
 * says the customer finished the thing; the other says the window ran out while they were part of
 * the way through it. The badges are identical either way — everything reached by the close is
 * kept — and the difference is the sentence the card gets to say, which is the whole of user story
 * 41: a challenge somebody finished and one that lapsed under them must not look alike.
 */
public enum EnrolmentState {

    /** Running, and the only state a reading is taken for. */
    ACTIVE,

    /** Finished: the gold rung was reached. Nothing is judged for it again. */
    COMPLETED,

    /** The customer left it. Everything already awarded to them is kept. */
    ABANDONED,

    /** Its campaign closed before it was finished. The rungs reached by then are kept, the rest lapse. */
    EXPIRED;

    /** Whether this enrolment is still being counted, which is the only question most readers have. */
    public boolean isLive() {
        return this == ACTIVE;
    }
}
