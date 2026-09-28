package io.dataroots.savingstreak.automation;

/**
 * What makes a saving rule move money: a day of the week, a day of the month, or the day its
 * holder's salary lands.
 *
 * <p>Three values and no fourth. A rule that reacted to a balance changing rather than to a date is
 * deliberately not here — everything in this feature is calendar-driven, for the reason the nightly
 * notification sweep gives about having one producer and one place to look — and a customer who
 * wants "save what is left when I get paid" says {@link #ON_PAYDAY} with a floor, which is the same
 * sentence expressed as a day.
 *
 * <p><strong>Each value needs a different day, and that is the whole reason this is an enumeration
 * rather than a pair of nullable columns.</strong> A weekly rule carries a day of the week, a
 * monthly rule a day of the month, and a payday rule carries neither: its day is the one the holder
 * already declared as their income's, and asking the customer for it twice is asking them to keep
 * two answers in step. {@link AutomationService} is what holds a rule to the day its trigger needs.
 *
 * <p>Public, because {@link RecordedSavingRule} carries it out of the module: whoever renders a rule
 * has to be able to say what makes it move.
 */
public enum RuleTrigger {

    /** Every week on a chosen day, which is the rhythm a streak is counted in. */
    WEEKLY,

    /** Every month on a chosen date, clamped to the last day of a shorter month when it has to be. */
    MONTHLY,

    /**
     * On the day the holder's declared income lands in the current account the rule draws from.
     *
     * <p>The day is not a field on the rule while the rule is standing. It is read from the income
     * declared against that current account on every read, so that a customer who moves payday from
     * the 25th to the 28th moves it once rather than once per rule — and so that a rule and the
     * salary it is waiting for can never disagree about which day that is. Ending the rule writes
     * that day down, because a record has to survive the declaration it was following changing.
     */
    ON_PAYDAY
}
