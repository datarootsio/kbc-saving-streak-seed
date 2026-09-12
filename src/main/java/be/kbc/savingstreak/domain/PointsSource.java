package be.kbc.savingstreak.domain;

public enum PointsSource {
    /** Earned by depositing new savings. */
    DEPOSIT,
    /** Paid on the anniversary of a deposit that stayed put. */
    LOYALTY_BONUS,
    /**
     * Received as a gift from another customer. The batch keeps the expiry date it had in the
     * sender's wallet, so passing points around cannot extend their life.
     */
    GIFT_RECEIVED
}
