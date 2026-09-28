package io.dataroots.savingstreak.accounts;

/**
 * Whatever holds a savings account that no customer holds, asked the one question this module cannot
 * answer for itself: may this customer pay into it?
 *
 * <p><strong>Declared here and implemented elsewhere, which is the whole point of it.</strong> A
 * savings account with an empty holder belongs to something that is not a person — today a shared
 * pot — and who may put money into such a thing is that thing's rule. Accounts owns what an account
 * is and who holds one; it does not own membership, roles, or the idea of a pot at all. An
 * {@code AccountsService} that reached into the Shared Pots module for the answer would make the
 * oldest module in this application depend on the newest, and the two services would then want each
 * other at start-up, because Shared Pots already asks Accounts who exists and what they are called.
 * The dependency runs the only way it can: Accounts states the question, Shared Pots answers it, and
 * the answer arrives in a vocabulary Accounts already has.
 *
 * <p>So the answer is an {@link AccountPairing} rather than a yes or a no. The refusal has to reach
 * whoever asked as a sentence they can act on, and "you are not in that pot" and "you are only
 * watching it" are two different things to do next. Handing back a boolean would move the wording of
 * both into a module that cannot tell them apart.
 *
 * <p>One implementation today and no registry of them. A second kind of thing that could hold an
 * account is a change to make when there is one; until then, the interface is here for the direction
 * of the dependency and not for the plurality.
 */
public interface WhoMayPayIntoAnAccountNobodyHolds {

    /**
     * How a current account held by this customer stands to that savings account.
     *
     * <p>Only ever asked about an account that exists and that no customer holds — both settled by
     * Accounts before the question is put — so an implementation has only its own record to consult.
     *
     * @param savingsAccountId an existing savings account with no holding customer
     * @param payingCustomerId the customer who holds the current account the money would come from
     * @return one of the pot pairings, or {@link AccountPairing#NO_SUCH_SAVINGS_ACCOUNT} when
     *         nothing the implementation knows of holds the account either — which is the record
     *         having gone wrong rather than a state anybody can reach, and is answered as the
     *         absence it looks like from outside
     */
    AccountPairing pairingWith(long savingsAccountId, long payingCustomerId);
}
