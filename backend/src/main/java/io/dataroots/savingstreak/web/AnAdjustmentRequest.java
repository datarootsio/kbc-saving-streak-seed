package io.dataroots.savingstreak.web;

/**
 * One change to a future as a customer fills it in: which of the changes this application can
 * imagine, and the boxes that change needs filled.
 *
 * <p><strong>One form with every box on it rather than a shape per kind</strong>, for the reason
 * {@code AnAdjustmentAsAsked} gives on the other side of the seam: a form has the same boxes on it
 * whichever kind is ticked, and four request shapes here would make reading a body a choice between
 * four readings — which is exactly the switch the Simulation module's design exists to avoid. Each
 * kind reads the boxes it needs, and the boxes it does not need are nothing to it. The four are the
 * union of what the four kinds carry, so no later kind of change adds one.
 *
 * <p>The amount and the two days arrive as the text that was typed rather than as figures already
 * read for us, exactly as a goal's target and a goal's deadline do. "25,00" is the mistake a Belgian
 * page makes most and it deserves an answer about the amount rather than about the request being
 * unreadable, and text is the only form that still has the characters in it.
 *
 * <p>The goal arrives as an identifier, like the current account a rule draws from, because nobody
 * types it: a page sends back one of the goals it listed.
 */
record AnAdjustmentRequest(String kind, String amount, String on, String until, Long goalId) {
}
