package io.dataroots.savingstreak.web;

/**
 * What a customer sends to change a goal: a new name, a new target, a new deadline, or any
 * combination of the three.
 *
 * <p>Absent means "leave it alone", which is what makes this a patch rather than a replacement: a
 * page that only offers a rename sends a name and nothing else, and does not have to resend a target
 * it never showed.
 *
 * <p>The deadline has two ways of being absent and they mean opposite things, so this contract
 * spells them out. No {@code deadline} field at all, or a null one, leaves the deadline exactly as it
 * was. A {@code deadline} of {@code ""} says there is no longer a day this goal is wanted by, which a
 * customer who added one by mistake otherwise has no way to say. Blank text rather than a second
 * field, because a page that clears the date box sends exactly that, and a contract that matched what
 * the browser already does is one less thing for the page to remember.
 *
 * <p>Both figures arrive as text, for the reason {@link NewGoalRequest} gives.
 */
record ChangeGoalRequest(String name, String target, String deadline) {
}
