package io.dataroots.savingstreak.deposits;

/** A withdrawal the application will not make, carrying the reason the customer needs to act on. */
public class WithdrawalRefused extends RuntimeException {

    public enum Kind { NO_SUCH_ACCOUNT, AGAINST_THE_RULES, NOT_ENOUGH_MONEY }

    WithdrawalRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    private final Kind kind;

    public Kind kind() { return kind; }
}
