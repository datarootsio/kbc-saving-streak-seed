package io.dataroots.savingstreak.accounts;

/**
 * Whatever keeps the record of which agreement a savings account is living under, told that one has
 * just been opened.
 *
 * <p><strong>Declared here and implemented elsewhere, for the same reason as
 * {@link WhoMayPayIntoAnAccountNobodyHolds}.</strong> Every savings account in this application is
 * on a savings product, under one published version of that product's terms, from the moment it
 * exists — and what a product is, which versions have been published and which of them was being
 * sold this morning are all somebody else's answers. Accounts owns what an account is and who holds
 * one; it does not own the catalogue, and an {@code AccountsService} that reached into the Products
 * module for it would make the oldest module in this application depend on one of the newest, which
 * already reads back into this one.
 *
 * <p>So the dependency runs the only way it can: Accounts states that an account was opened, and
 * whoever keeps agreements writes one. The only thing about a product that ever travels is the one
 * word the customer typed, and it travels in one direction, unread — no version, no rate, no
 * condition, no list of what there is to choose from. This module carries that word from the
 * request to the record keeper the way a form carries an answer to the person who files it, and it
 * is still unable to name a single product of its own.
 *
 * <p><strong>There are two sentences here because there are two events.</strong> The bank opened an
 * account for somebody — a new customer's first account, a shared pot's — and nobody chose
 * anything; or a customer chose a product and asked for an account on it. The first has no word to
 * carry and says so by having no parameter for one, which is a stronger statement than a null: a
 * caller that has no choice to pass cannot accidentally pass a wrong one, and the record keeper
 * decides for itself what an account nobody chose for goes on.
 *
 * <p><strong>The second one is a question and may be refused.</strong> That is a change from the
 * slice that first declared this interface, when every account went on the one product this
 * application had ever offered and nothing could go wrong. A product nobody sells and a product
 * closed to new accounts are both real answers to a real choice, and neither of them is something
 * this module could recognise, word or refuse. So the refusal is the record keeper's, in the record
 * keeper's vocabulary, and it travels out through here untouched: nothing in Accounts catches it,
 * names its type, or has an opinion about which HTTP status it deserves.
 *
 * <p><strong>Said inside the transaction that opened the account</strong>, so that an account and
 * the record of what it is on are written together or not at all. An account that existed for a
 * moment with no agreement is an account no rule could answer about, and the moment would be
 * exactly the moment somebody paid into it. It is also what makes the refusal above safe to raise
 * after the row exists: the account that was opened a line earlier goes back with it, so a customer
 * who names a product the bank does not sell is left holding nothing rather than an account on
 * nothing. Asking first and opening afterwards would be the same two statements in the other order,
 * and would need this module to hold a question it cannot ask.
 *
 * <p>One implementation today and no registry of them, for the reason the pairing interface gives:
 * this is here for the direction of the dependency and not for the plurality.
 */
public interface WhoeverRecordsWhatASavingsAccountIsOn {

    /**
     * A savings account has just been opened, nobody having chosen anything, and needs the agreement
     * it is going to live under.
     *
     * <p>Only ever said about an account that exists and has this moment been written, so an
     * implementation has nothing to check and no absence to report. Nothing was chosen, so there is
     * nothing to refuse either: what such an account goes on is the record keeper's own answer, and
     * a caller here has nothing to handle.
     *
     * @param savingsAccountId the account as it now stands in this module's own records
     */
    void aSavingsAccountWasOpened(long savingsAccountId);

    /**
     * A savings account has just been opened because somebody chose a product for it, and needs the
     * agreement that choice writes.
     *
     * <p>The word is passed on exactly as it arrived — untrimmed, uncorrected, unchecked — because
     * every judgement about it belongs to whoever keeps the catalogue: whether the bank sells such a
     * thing, whether it is still open to new accounts, and which version of its terms was being sold
     * this morning. A module that trimmed it here would be a module with half an opinion about a
     * vocabulary it cannot read.
     *
     * @param savingsAccountId the account as it now stands in this module's own records
     * @param theProductChosen what the customer typed, whatever that turns out to be
     * @throws RuntimeException the record keeper's own refusal, in its own words, when no account
     *                          may be opened on what was chosen — which takes the account that was
     *                          just opened back with it, because both are one transaction
     */
    void aSavingsAccountWasOpenedOn(long savingsAccountId, String theProductChosen);
}
