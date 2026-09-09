package io.dataroots.savingstreak.gifting;

import java.time.Instant;

/**
 * A gift that has happened, as the rest of the application sees one: which gift, which way it reads
 * for whoever is being told, both people by identifier and by name, how many points, and when.
 *
 * <p>One shape for both ends. A gift just made is {@link GiftDirection#SENT} to the person who made
 * it and the very same gift is {@link GiftDirection#RECEIVED} in the other person's list, so the
 * only thing that differs between the two readings is that word — nothing rendering a gift has to
 * know which end it is holding in order to render it.
 *
 * <p>Both people rather than only the other one, so that a row is self-describing: whoever shows it
 * can say "you gave Bram 20 points" or "Anke gave you 20 points" from the same record, and does not
 * have to know who it was fetched for in order to work out who the other party was.
 *
 * <p>Names rather than only identifiers, because a list of gifts should read as people. Who is
 * called what is the Accounts module's answer and is looked up there; nothing about a name is stored
 * on the gift, so a customer who is renamed is renamed in every gift they were ever part of.
 *
 * <p>The stored record stays inside the module; this is a statement about something that happened.
 * There is no way to undo one, because a gift is final the moment it is made.
 */
public record GiftGiven(Long id, GiftDirection direction, Long senderId, String senderName,
                        Long recipientId, String recipientName, long points, Instant givenAt) {
}
