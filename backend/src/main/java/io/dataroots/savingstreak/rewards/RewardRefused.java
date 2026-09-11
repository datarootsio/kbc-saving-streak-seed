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
     * The two sorts of mistake a refusal can be about. They are different because the person reading
     * it has a different thing to do next: find the right account, or go and save some more.
     */
    public enum Kind {
        NO_SUCH_CUSTOMER,
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
