package io.dataroots.savingstreak.sharedpots;

import java.time.Instant;
import java.util.List;

/**
 * A shared pot as the rest of the application sees one: which pot, what it is called, the savings
 * account its euros live in, when it was opened, and who belongs to it with what role.
 *
 * <p>One shape for a pot just opened, a pot read back on its own and a pot in somebody's list, so
 * that nothing rendering a pot has to know which of those it is holding in order to render it.
 *
 * <p>The members come with it rather than only behind a second call, because a pot without them is
 * not a shared pot in any sense a reader would recognise — "who else is in this" is the first
 * question anybody asks of one. They are also readable on their own, for the page that lists only
 * them.
 *
 * <p><strong>What the pot holds is deliberately not here.</strong> This module owns no money: the
 * euros are deposits in the pot's savings account and the balance is the deposits module's answer,
 * derived on every read from the ledger underneath. A figure copied onto this record would be a
 * second answer to the one question everybody saving into a pot is actually asking, and two figures
 * that have to agree eventually stop agreeing. Whoever reports a pot puts the two side by side, the
 * way the goals endpoints already do with the balance goals are claims against.
 *
 * <p>The savings account is named all the same, because a member has to be able to pay into it and
 * the deposit endpoint is the account's. It is the pot's account, held by nobody, and it will not
 * appear in anybody's personal list of accounts.
 *
 * <p><strong>When it was closed is here, and it is null for as long as the pot is open.</strong> A
 * closed pot is a record rather than a thing that disappears: it stays readable, it stays in the
 * list of pots each of its members belongs to, and the only difference anybody can see is that
 * nothing further can be done to it. That difference has to travel on the one shape a pot arrives
 * in, or a list of somebody's pots would show a finished pot as though the group were still saving
 * into it.
 *
 * <p>A moment rather than a flag, for the reason {@code SharedPot} argues about the column
 * underneath: a page can say "closed" from an absence and can also say <em>when</em>, and a boolean
 * beside it would be a second answer to one question.
 */
public record ASharedPot(Long id, String name, Long savingsAccountId, Instant openedAt,
                         Instant closedAt, List<APotMember> members) {
}
