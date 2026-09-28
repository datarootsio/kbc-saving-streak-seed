package io.dataroots.savingstreak.web;

/**
 * What closing a shared pot carries: the customer doing it, and nothing else.
 *
 * <p>One field, and it is the one this application cannot work out for itself. There is no
 * authentication here — every endpoint trusts the customer identifier it is handed — so a request to
 * end an arrangement between several people has to say on whose behalf it is being made, and
 * whether that person is an owner is Shared Pots' rule rather than this record's.
 *
 * <p>Nothing about where the money should go. Closing returns each member their own euros to the
 * current account their most recent contribution came from, because a single act cannot wait on a
 * field per member — and an owner naming accounts for other people is exactly the request the
 * settlement rules exist to refuse.
 *
 * <p>A {@code POST} with a body rather than a {@code DELETE} with a query parameter, because closing
 * a pot deletes nothing: the pot, its membership, its contributions, its movements and its proposals
 * all stay, and the pot is a record afterwards. {@code POST /close} says "do this thing to it" and
 * is what the spec's own contract carries.
 */
record CloseThePotRequest(Long customerId) {
}
