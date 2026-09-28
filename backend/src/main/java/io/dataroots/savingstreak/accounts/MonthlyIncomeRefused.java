package io.dataroots.savingstreak.accounts;

/**
 * A declaration of monthly income this application will not keep, carrying the reason in words the
 * person who typed it can act on — an amount that is not an amount of money, or a day that is not a
 * day of the month.
 *
 * <p>Accounts' own refusal rather than {@link CustomerRefused} widened, for the reason
 * {@code NotificationRefused} gives about not borrowing {@code RewardRefused}: opening a customer
 * and declaring what lands in their account are different rules that will grow apart, and a shared
 * exception would tie the web layer's reading of one to the other's. What they do share is the
 * sentence about an amount of money, which {@code AmountOfMoney} owns so that the objection a
 * customer meets on a deposit is word for word the objection they meet here.
 *
 * <p>There is deliberately no kind for an account that is not there. The controller vouches for the
 * current account first and answers in the words {@link AccountsService#noSuchCurrentAccount} owns,
 * the same order and the same refusal every other module's controller uses — so that "there is no
 * such account" is one sentence in this application rather than one per module.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer.
 */
public class MonthlyIncomeRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal here can be about. One so far, and it is a form to fix: the
     * figure or the day that arrived does not describe an income this application can pay.
     *
     * <p>A kind even though there is only one of them, because the kind is what the web layer
     * switches on to pick a status and what the WARN line carries: the next reason an income can be
     * refused for — a customer declaring one against somebody else's account, once there is
     * authentication to tell them apart — arrives as a value here and forces a decision in
     * {@code RefusalsAsHttp} rather than quietly inheriting a 400 that would not fit it.
     */
    public enum Kind {
        AGAINST_THE_RULES
    }

    private final Kind kind;

    MonthlyIncomeRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
