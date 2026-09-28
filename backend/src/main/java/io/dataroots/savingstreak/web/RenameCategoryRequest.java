package io.dataroots.savingstreak.web;

/**
 * What a customer sends to say a category differently: the word they meant.
 *
 * <p>Named for renaming rather than for changing, unlike {@link ChangeBillRequest} and
 * {@link ChangeGoalRequest}, because there is nothing else on a category to change. Those two carry
 * several fields and any of them may be left out to mean "leave it alone"; this one carries the
 * only thing a category has, so a request with nothing in it is a request that says nothing at all
 * rather than a request that changes nothing — and it is refused in those words.
 *
 * <p>Its own record rather than {@link NewCategoryRequest} reused, although the two are the same
 * shape today. They are different sentences to the person typing them, and the one place they would
 * be forced to agree is exactly where a later field on one of them would have to be added to the
 * other by accident.
 */
record RenameCategoryRequest(String name) {
}
