package be.kbc.savingstreak.domain;

public enum NotificationKind {
    /** An account dropped below the level the customer asked to be warned about. */
    BALANCE_BELOW,
    /** An account climbed above the level the customer asked to be told about. */
    BALANCE_ABOVE,
    /** A loyalty bonus is close enough to its anniversary to be worth protecting. */
    BONUS_VESTING_SOON,
    /** A withdrawal gave up a bonus that was about to vest. */
    BONUS_FORFEITED
}
