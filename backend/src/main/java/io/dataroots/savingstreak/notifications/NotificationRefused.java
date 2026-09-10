package io.dataroots.savingstreak.notifications;

/**
 * A read of somebody's notifications the application will not answer, carrying the reason in words
 * the page that asked can show.
 *
 * <p>Its own refusal rather than one borrowed from Rewards or Gifting, for the reason
 * {@code RewardRefused} gives about not sharing with Deposits: the two modules refuse for their own
 * reasons and will grow apart, and a shared exception would tie each module's vocabulary to the
 * other's. What they do share is the sentence — {@code AccountsService.noSuchCustomer} owns what a
 * customer's absence is called, so that four modules saying it are not four wordings one edit away
 * from disagreeing.
 *
 * <p>A kind even though there is only one of them, because the kind is what the web layer switches
 * on to pick a status and what the WARN line carries: the next reason a notification read can be
 * refused for — a customer asking for somebody else's, once there is authentication to tell them
 * apart — arrives as a value here and forces a decision in {@code RefusalsAsHttp} rather than
 * quietly inheriting a 404.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer.
 */
public class NotificationRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal here can be about. One: a customer this application has never
     * heard of. An empty list is what a customer nothing has ever been said to has, and answering a
     * made-up identifier with one would tell whoever asked that that customer exists.
     */
    public enum Kind {
        NO_SUCH_CUSTOMER
    }

    private final Kind kind;

    NotificationRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
