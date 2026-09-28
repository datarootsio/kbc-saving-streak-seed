package io.dataroots.savingstreak.web;

/**
 * What a customer sends to say a standing bill differently: whichever of its name, its day and its
 * amount they are changing, and nothing else.
 *
 * <p><strong>Absent means "leave it alone".</strong> Putting the rent up is one field; a provider
 * moving its collection date is another; and neither should make a customer retype the other two.
 * The same shape {@code ChangeSavingRuleRequest} has, and the module's own
 * {@code AChangeToABill} is where the reading of it is written down.
 *
 * <p>The two figures are text, like the ones a bill is first declared with, so that what comes back
 * about a comma is a sentence about the comma.
 *
 * <p>A body whose every field is absent is refused rather than answered with an untouched bill, and
 * that refusal is Accounts' rather than this layer's: what a change has to say is a rule about
 * bills.
 */
record ChangeBillRequest(String name, String dayOfMonth, String amount) {
}
