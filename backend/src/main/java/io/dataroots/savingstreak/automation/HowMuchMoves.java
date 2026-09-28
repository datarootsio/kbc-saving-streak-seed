package io.dataroots.savingstreak.automation;

/**
 * How much a saving rule moves when it fires: a figure the customer named, or whatever is above a
 * line they drew.
 *
 * <p>Two values, and they are two because the second one is not a figure at all. "Fifty euros a
 * week" is an amount; "sweep whatever is left above eight hundred" is a rule about a balance, and
 * the amount it comes to is only known at the moment it fires. Kept as one nullable amount with a
 * meaning that changed underneath it, the two would be indistinguishable in every figure downstream
 * — and a page could not say whether 50.00 was what will move or what will be left behind.
 *
 * <p>Which of the two figures a rule carries follows from this: {@link #A_FIXED_AMOUNT} has an
 * amount and no floor, {@link #EVERYTHING_ABOVE} a floor and no amount. {@link AutomationService}
 * is what keeps the unused one empty, so that a rule changed from one kind to the other does not go
 * on carrying the figure that no longer means anything.
 *
 * <p>Public, because {@link RecordedSavingRule} carries it out of the module.
 */
public enum HowMuchMoves {

    /**
     * The amount the customer named, all of it or none of it. A standing order never half-happens,
     * which is what makes a shortfall a thing that is recorded rather than a smaller transfer.
     */
    A_FIXED_AMOUNT,

    /**
     * Everything the current account holds above the floor the customer named, and nothing at all
     * when it is already at or under it.
     *
     * <p>A floor of nothing is a legal thing to say and means "sweep the lot"; a floor below nothing
     * is not, because there is no balance under zero for a sweep to aim at.
     */
    EVERYTHING_ABOVE
}
