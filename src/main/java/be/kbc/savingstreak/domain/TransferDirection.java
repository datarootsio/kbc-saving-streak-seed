package be.kbc.savingstreak.domain;

public enum TransferDirection {
    /** Current account -> savings account. Earns points on the part that is new savings. */
    DEPOSIT,
    /** Savings account -> current account. */
    WITHDRAWAL,
    /**
     * A move between two accounts of the same kind, such as savings to savings. The total
     * saved does not change, so it earns nothing and leaves the streak alone.
     */
    REBALANCE;

    public static TransferDirection between(AccountType from, AccountType to) {
        if (from == to) {
            return REBALANCE;
        }
        return to == AccountType.SAVINGS ? DEPOSIT : WITHDRAWAL;
    }
}
