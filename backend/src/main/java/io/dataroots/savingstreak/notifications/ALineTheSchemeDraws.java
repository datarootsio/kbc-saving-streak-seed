package io.dataroots.savingstreak.notifications;

/**
 * The five lines the scheme draws across this module: the five places a published figure decides
 * whether this application says something to somebody tonight.
 *
 * <p><strong>Five lines, not five reasons, and the difference is the whole point of this
 * enum.</strong> {@link NotificationReason} is the vocabulary of what was said and there are more
 * than twenty of them — a rule that did not fire, a term bettered, a queue that moved. Only five of
 * the things this module decides are decided by a figure the scheme publishes, and those five are
 * the only ones a change to the scheme can move. Reusing the reasons here would have offered a
 * screen twenty rows of which fifteen would read nought for ever, and would have quietly invited
 * somebody to ask what a repricing does to a waiting list.
 *
 * <p><strong>It is named for the line rather than for the figure.</strong> The balance rungs are a
 * ladder of six figures and the budget share is one, but each of them draws exactly one line, and
 * what an administrator wants to know is how many people are on the far side of it. That is also
 * why an anniversary and a maturity are two lines although both are notice periods in days: they
 * are two figures on the form and a version may move one without the other.
 *
 * <p>Public because it is half of the answer {@link NotificationsService} gives to a preview, and
 * the rules that do the deciding stay package-private behind it. The module's face gained a
 * question; nothing inside it was opened up to be asked directly.
 */
public enum ALineTheSchemeDraws {

    /** The balance rungs a customer is congratulated on reaching, and falling back below. */
    A_BALANCE_RUNG,

    /** The share of a month's allowance at which a category is said to be running low. */
    A_BUDGET_RUNNING_LOW,

    /** How many bills outstanding at once is arrears piling up rather than a bad month. */
    ARREARS_PILING_UP,

    /** How many days before a term matures it is worth saying so. */
    A_MATURITY_COMING_SOON,

    /** How many days before a deposit's anniversary pays it is worth saying so. */
    AN_ANNIVERSARY_COMING_SOON
}
